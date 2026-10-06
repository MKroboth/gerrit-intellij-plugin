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

package com.urswolfer.intellij.plugin.gerrit.ui.diff;

import com.google.gerrit.extensions.api.projects.CommentLinkInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.IssueNavigationConfiguration;
import com.intellij.openapi.vcs.IssueNavigationLink;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What turns an issue's id in a comment into a link: the "commentlink" sections of the Gerrit project, as Gerrit's
 * web UI applies them, and the IDE's own "Issue Navigation", which links them in the Git log as well.
 */
@Service(Service.Level.APP)
public final class CommentLinks {
    private final Map<String, List<CommentMarkdown.Link>> fromGerrit = new ConcurrentHashMap<>();
    private final Set<String> loading = new HashSet<>();

    public static CommentLinks getInstance() {
        return ApplicationManager.getApplication().getService(CommentLinks.class);
    }

    /**
     * @param whenLoaded run once the Gerrit project's links arrived, if they were not there yet
     * @return null while the Gerrit project's links load, which this starts
     */
    @Nullable
    public List<CommentMarkdown.Link> get(@NotNull Project project, @Nullable String gerritProject,
                                          @NotNull Runnable whenLoaded) {
        List<CommentMarkdown.Link> links = new ArrayList<>();
        for (IssueNavigationLink link : IssueNavigationConfiguration.getInstance(project).getLinks()) {
            try {
                links.add(new CommentMarkdown.Link(link.getIssueRegexp(), link.getLinkRegexp()));
            } catch (RuntimeException ignored) {
                // a pattern the IDE accepted but Java does not compile; the IDE reports it in its own settings
            }
        }
        if (gerritProject == null) {
            return links;
        }
        List<CommentMarkdown.Link> gerrit = fromGerrit.get(gerritProject);
        if (gerrit == null) {
            if (loading.add(gerritProject)) {
                GerritUtil.getInstance().getCommentLinks(gerritProject, project, config -> {
                    fromGerrit.put(gerritProject, toLinks(config));
                    loading.remove(gerritProject);
                    whenLoaded.run();
                });
            }
            return null;
        }
        links.addAll(gerrit);
        return links;
    }

    private static List<CommentMarkdown.Link> toLinks(Map<String, CommentLinkInfo> config) {
        List<CommentMarkdown.Link> links = new ArrayList<>();
        for (CommentLinkInfo info : config.values()) {
            if (Boolean.FALSE.equals(info.enabled) || info.match == null || info.link == null) {
                continue;
            }
            try {
                links.add(new CommentMarkdown.Link(info.match, info.link));
            } catch (RuntimeException ignored) {
                // Gerrit's patterns are JavaScript's; one Java does not compile is left out
            }
        }
        return links;
    }
}
