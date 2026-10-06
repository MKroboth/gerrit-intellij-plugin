/*
 * Copyright 2026 Maximilian Kroboth
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
import com.google.gerrit.extensions.common.CommentInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.JBUI;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.ui.diff.CommentThread;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * The open threads of the selected change, or of its whole stack, each of which opens its diff at the thread.
 */
final class OpenThreadsPanel {
    private static final String PATCH_SET_LEVEL = "/PATCHSET_LEVEL";

    private final GerritUtil gerritUtil = GerritUtil.getInstance();
    private final Project project;
    private final GerritToolWindow toolWindow;
    private final GerritChangeListPanel changeListPanel;
    private final IntConsumer countChanged;
    private final CollectionListModel<OpenThreads.Entry> model = new CollectionListModel<>();
    private final JBList<OpenThreads.Entry> list = new JBList<>(model);
    private final JBCheckBox wholeStack = new JBCheckBox("Whole stack");
    private final JPanel panel = new JPanel(new BorderLayout());
    @Nullable
    private ChangeInfo change;
    private int load;

    OpenThreadsPanel(@NotNull Project project, @NotNull GerritToolWindow toolWindow,
                     @NotNull GerritChangeListPanel changeListPanel, @NotNull IntConsumer countChanged) {
        this.project = project;
        this.toolWindow = toolWindow;
        this.changeListPanel = changeListPanel;
        this.countChanged = countChanged;
        wholeStack.setBorder(JBUI.Borders.empty(2, 6));
        wholeStack.addActionListener(e -> reload());
        list.setCellRenderer(new Renderer());
        list.getEmptyText().setText("No open conversations");
        new DoubleClickListener() {
            @Override
            protected boolean onDoubleClick(@NotNull MouseEvent event) {
                open();
                return true;
            }
        }.installOn(list);
        list.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    open();
                }
            }
        });
        panel.add(wholeStack, BorderLayout.NORTH);
        panel.add(ScrollPaneFactory.createScrollPane(list), BorderLayout.CENTER);
    }

    @NotNull
    JComponent getComponent() {
        return panel;
    }

    void setChange(@Nullable ChangeInfo change) {
        this.change = change;
        reload();
    }

    private void reload() {
        int current = ++load;
        model.removeAll();
        countChanged.accept(0);
        if (change == null) {
            return;
        }
        List<ChangeInfo> changes = new ArrayList<>(wholeStack.isSelected()
            ? changeListPanel.stackOf(change) : Collections.singletonList(change));
        // the stack from its base, the order it is reviewed in
        Collections.reverse(changes);
        Map<ChangeInfo, List<OpenThreads.Entry>> byChange = new HashMap<>();
        for (ChangeInfo listed : changes) {
            gerritUtil.getChangeComments(listed._number, project, comments -> {
                if (current != load) {
                    return;
                }
                byChange.put(listed, OpenThreads.collect(listed, comments));
                if (byChange.size() == changes.size()) {
                    List<OpenThreads.Entry> entries = new ArrayList<>();
                    for (ChangeInfo inOrder : changes) {
                        entries.addAll(byChange.get(inOrder));
                    }
                    model.replaceAll(entries);
                    countChanged.accept(entries.size());
                }
            });
        }
    }

    private void open() {
        OpenThreads.Entry entry = list.getSelectedValue();
        if (entry == null || PATCH_SET_LEVEL.equals(entry.getPath())) {
            return;
        }
        toolWindow.openDiff(entry.getChange(), entry.getPath(), entry.getThread().getRoot().id);
    }

    private final class Renderer extends ColoredListCellRenderer<OpenThreads.Entry> {
        @Override
        protected void customizeCellRenderer(@NotNull JList<? extends OpenThreads.Entry> list,
                                             OpenThreads.Entry entry, int index, boolean selected,
                                             boolean hasFocus) {
            CommentThread thread = entry.getThread();
            CommentInfo root = thread.getRoot();
            if (wholeStack.isSelected()) {
                append(entry.getChange()._number + "  ", SimpleTextAttributes.GRAYED_ATTRIBUTES);
            }
            if (PATCH_SET_LEVEL.equals(entry.getPath())) {
                append("Change", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
            } else {
                append(entry.getPath() + (thread.getLine() > 0 ? ":" + thread.getLine() : ""),
                    SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
            }
            if (root.patchSet != null) {
                append("  PS " + root.patchSet, SimpleTextAttributes.GRAYED_ATTRIBUTES);
            }
            CommentInfo last = thread.getLast();
            String author = CommentThread.isDraft(last) ? "Draft"
                : last.author != null && last.author.name != null ? last.author.name : "";
            String[] lines = StringUtil.splitByLines(StringUtil.notNullize(last.message));
            String summary = lines.length > 0 ? lines[0].replace("**", "").replace("`", "") : "";
            append("   " + author + ": " + StringUtil.shortenTextWithEllipsis(summary, 100, 0));
        }
    }
}
