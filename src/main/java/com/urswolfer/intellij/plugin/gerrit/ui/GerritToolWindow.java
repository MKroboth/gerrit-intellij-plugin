/*
 * Copyright 2013 Urs Wolfer
 * Copyright 2000-2013 JetBrains s.r.o.
 * Modified 2026 by Maximilian Kroboth: adds "Group by" to the toolbar of the change list, a tab of the
 * selected change's open conversations, and opening a file's diff from code.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.urswolfer.intellij.plugin.gerrit.ui;

import com.google.gerrit.extensions.common.ChangeInfo;
import com.intellij.dvcs.repo.VcsRepositoryManager;
import com.intellij.dvcs.repo.VcsRepositoryMappingListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.Constraints;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.Consumer;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.rest.LoadChangesProxy;
import com.urswolfer.intellij.plugin.gerrit.ui.diff.ThreadReveal;
import com.urswolfer.intellij.plugin.gerrit.ui.filter.ChangesFilter;
import com.urswolfer.intellij.plugin.gerrit.ui.filter.GerritChangesFilters;
import git4idea.GitUtil;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Optional;

/**
 * @author Urs Wolfer
 * @author Konrad Dobrzynski
 */
public class GerritToolWindow implements Disposable {
    /**
     * Provided by the tool window content panel, so that actions can reach the tool window they were invoked from
     * instead of looking it up in a global holder.
     */
    public static final DataKey<GerritToolWindow> GERRIT_TOOL_WINDOW = DataKey.create("Gerrit.ToolWindow");

    private static final Logger LOG = Logger.getInstance(GerritToolWindow.class);

    private final GerritUtil gerritUtil = GerritUtil.getInstance();
    private final GerritSettings gerritSettings = GerritSettings.getInstance();
    private GerritChangeListPanel changeListPanel;
    private final GerritChangesFilters changesFilters = new GerritChangesFilters();
    private final RepositoryChangesBrowserProvider repositoryChangesBrowserProvider = new RepositoryChangesBrowserProvider();

    private GerritChangeDetailsPanel detailsPanel;
    private RepositoryChangesBrowserProvider.GerritRepositoryChangesBrowser repositoryChangesBrowser;
    private int changesLoad;

    /**
     * Nothing to release here: this is the parent the tool window content's listeners are registered against, and
     * the platform disposes it with the content.
     */
    @Override
    public void dispose() {
    }

    public SimpleToolWindowPanel createToolWindowContent(final Project project) {
        changeListPanel = new GerritChangeListPanel(project);

        SimpleToolWindowPanel panel = new SimpleToolWindowPanel(true, true) {
            @Override
            public Object getData(@NotNull String dataId) {
                if (GERRIT_TOOL_WINDOW.is(dataId)) {
                    return GerritToolWindow.this;
                }
                return super.getData(dataId);
            }
        };

        ActionToolbar toolbar = createToolbar(project);
        toolbar.setTargetComponent(changeListPanel);
        panel.setToolbar(toolbar.getComponent());

        repositoryChangesBrowser = repositoryChangesBrowserProvider.get(project, changeListPanel, this);

        JBSplitter detailsSplitter = new OnePixelSplitter(true, 0.6f);
        detailsSplitter.setSplitterProportionKey("Gerrit.ListDetailSplitter.Proportion");
        detailsSplitter.setFirstComponent(changeListPanel);

        detailsPanel = new GerritChangeDetailsPanel(project);
        changeListPanel.addListSelectionListener(new Consumer<ChangeInfo>() {
            @Override
            public void consume(ChangeInfo changeInfo) {
                changeSelected(changeInfo, project);
            }
        });
        changeListPanel.addSelectionClearedListener(detailsPanel::nothingSelected);
        JPanel details = detailsPanel.getComponent();
        JBTabbedPane tabs = new JBTabbedPane();
        tabs.addTab("Details", details);
        OpenThreadsPanel openThreads = new OpenThreadsPanel(project, this, changeListPanel,
            count -> tabs.setTitleAt(1, count > 0 ? "Conversations (" + count + ")" : "Conversations"));
        tabs.addTab("Conversations", openThreads.getComponent());
        changeListPanel.addListSelectionListener(openThreads::setChange);
        changeListPanel.addSelectionClearedListener(() -> openThreads.setChange(null));
        detailsSplitter.setSecondComponent(tabs);

        JBSplitter horizontalSplitter = new OnePixelSplitter(false, 0.7f);
        horizontalSplitter.setSplitterProportionKey("Gerrit.DetailRepositoryChangeBrowser.Proportion");
        horizontalSplitter.setFirstComponent(detailsSplitter);
        horizontalSplitter.setSecondComponent(repositoryChangesBrowser);

        panel.setContent(horizontalSplitter);

        List<GitRepository> repositories = GitUtil.getRepositoryManager(project).getRepositories();
        if (!repositories.isEmpty()) {
            reloadChanges(project, false);
        }

        registerVcsChangeListener(project);

        changeListPanel.showSetupHintWhenRequired(project);

        return panel;
    }

    private void registerVcsChangeListener(final Project project) {
        VcsRepositoryMappingListener vcsListener = new VcsRepositoryMappingListener() {
            @Override
            public void mappingChanged() {
                // published from a pooled thread; loads are only started on the event dispatch thread
                ApplicationManager.getApplication().invokeLater(
                    () -> reloadChanges(project, false), project.getDisposed());
            }
        };
        project.getMessageBus().connect(this).subscribe(VcsRepositoryManager.VCS_REPOSITORY_MAPPING_UPDATED, vcsListener);
    }

    private void changeSelected(ChangeInfo changeInfo, final Project project) {
        gerritUtil.getChangeDetails(changeInfo._number, project, new Consumer<ChangeInfo>() {
            @Override
            public void consume(ChangeInfo changeDetails) {
                // another change may have been selected meanwhile
                if (changeListPanel.getTable().getSelectedObject() == changeInfo) {
                    detailsPanel.setData(changeDetails);
                }
            }
        });
    }

    /**
     * Selects the change, and opens the diff of one of its files once they are listed there, scrolled to a thread.
     *
     * @param path as Gerrit names it; null for the first file
     */
    public void openDiff(@NotNull ChangeInfo change, @Nullable String path, @Nullable String threadRootId) {
        if (path != null && threadRootId != null) {
            ThreadReveal.request(change.id, path, threadRootId);
        }
        if (changeListPanel.selectChange(change.id)) {
            repositoryChangesBrowser.showDiffWhenListed(change.id, path);
        }
    }

    /**
     * Applies a modification Gerrit has confirmed to the listed change, for one which does not justify reloading the
     * list. The change is looked up by its id because a reload replaces every listed instance.
     */
    public void updateChange(String changeId, Consumer<ChangeInfo> update, Project project) {
        Optional<ChangeInfo> listed = changeListPanel.findChange(changeId);
        if (!listed.isPresent()) {
            return;
        }
        update.consume(listed.get());
        changeListPanel.getTable().repaint();
        if (changeListPanel.getTable().getSelectedObject() == listed.get()) {
            changeSelected(listed.get(), project);
        }
    }

    /**
     * Shows what the query finds, whatever the filters were set to, and selects the change if it is the only one.
     */
    public void showChanges(Project project, String query) {
        String host = gerritSettings.getHost();
        if (host == null || host.isEmpty()) { // the filters would show the lookup over a list which never loads
            return;
        }
        changesFilters.showLookup(query);
        reloadChanges(project, false);
    }

    public void reloadChanges(final Project project, boolean requestSettingsIfNonExistent) {
        String apiUrl = gerritSettings.getHost();
        if (apiUrl == null || apiUrl.isEmpty()) {
            if (requestSettingsIfNonExistent) {
                final LoginDialog dialog = new LoginDialog(project, gerritSettings, gerritUtil);
                dialog.show();
                if (!dialog.isOK()) {
                    return;
                }
            } else {
                return;
            }
        }
        int load = ++changesLoad;
        boolean lookup = changesFilters.isShowingLookup();
        String query = changesFilters.getQuery();
        Consumer<LoadChangesProxy> consumer = proxy -> {
            // loads run concurrently; one started earlier must not replace what a later one shows
            if (load == changesLoad) {
                changeListPanel.load(proxy, lookup, query);
            }
        };
        if (lookup) {
            // a full hash is unique, and the projects of the repositories are not always known from their remotes
            gerritUtil.getChanges(query, project, consumer);
        } else {
            gerritUtil.getChangesForProject(query, project, consumer);
        }
    }

    private ActionToolbar createToolbar(final Project project) {
        DefaultActionGroup groupFromConfig = (DefaultActionGroup) ActionManager.getInstance().getAction("Gerrit.Toolbar");
        DefaultActionGroup group = new DefaultActionGroup(groupFromConfig); // copy required (otherwise config action group gets modified)

        DefaultActionGroup filterGroup = new DefaultActionGroup();
        Iterable<ChangesFilter> filters = changesFilters.getFilters();
        for (ChangesFilter filter : filters) {
            filterGroup.add(filter.getAction(project));
        }
        filterGroup.add(new Separator());
        filterGroup.add(new GroupByAction(changeListPanel));
        filterGroup.add(new Separator());
        group.add(filterGroup, Constraints.FIRST);

        changesFilters.addListener(new GerritChangesFilters.Listener() {
            @Override
            public void filtersChanged() {
                reloadChanges(project, true);
            }
        });

        return ActionManager.getInstance().createActionToolbar("Gerrit.Toolbar", group, true);
    }
}
