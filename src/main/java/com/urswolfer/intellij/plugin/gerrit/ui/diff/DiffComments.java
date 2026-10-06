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

import com.google.gerrit.extensions.client.Comment;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.components.panels.VerticalLayout;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The comments of one diff of a change shown in place: the threads of both sides, those of earlier patch sets on the
 * newer side, and the bar which publishes the drafts.
 */
final class DiffComments {
    private static final Predicate<Comment> REVISION_COMMENT =
        comment -> comment.side == null || comment.side.equals(Side.REVISION);

    private final GerritUtil gerritUtil = GerritUtil.getInstance();
    private final Project project;
    private final ChangeInfo changeInfo;
    private final String revisionId;
    private final Optional<Pair<String, RevisionInfo>> baseRevision;
    private final String path;
    private final EditorCommentThreads threads2;
    @Nullable
    private final EditorCommentThreads threads1;
    private final DraftsBar draftsBar;

    DiffComments(@NotNull Project project,
                 @NotNull ChangeInfo changeInfo,
                 @NotNull String revisionId,
                 @NotNull Optional<Pair<String, RevisionInfo>> baseRevision,
                 @NotNull String path,
                 @Nullable EditorEx editor1,
                 @NotNull EditorEx editor2) {
        this.project = project;
        this.changeInfo = changeInfo;
        this.revisionId = revisionId;
        this.baseRevision = baseRevision;
        this.path = path;
        draftsBar = new DraftsBar(project, changeInfo, revisionId, this::load);
        threads2 = createThreads(editor2, revisionId, Side.REVISION);
        threads1 = editor1 == null ? null : baseRevision.isPresent()
            ? createThreads(editor1, baseRevision.get().getFirst(), Side.REVISION)
            : createThreads(editor1, revisionId, Side.PARENT);
    }

    private EditorCommentThreads createThreads(EditorEx editor, String revision, Side side) {
        EditorCommentThreads threads = new EditorCommentThreads(project, editor, changeInfo, revision, path, side);
        threads.onDraftsChanged(draftsBar::refresh);
        CommentsDiffTool.installAddCommentAction(editor, new AddCommentInPlaceAction(editor, threads));
        return threads;
    }

    /**
     * The bars above the diff: the drafts to publish, and where the change is in a stack being reviewed.
     */
    @NotNull
    JComponent getBars() {
        JPanel bars = new JPanel(new VerticalLayout(0));
        bars.add(draftsBar.getComponent());
        StackReview review = StackReview.of(changeInfo);
        if (review != null) {
            int index = review.indexOf(changeInfo);
            EditorNotificationPanel stackBar = new EditorNotificationPanel();
            stackBar.setText("Stack review: change " + (index + 1) + " of " + review.size());
            if (index > 0) {
                stackBar.createActionLabel("Previous change", () -> review.open(index - 1));
            }
            if (index < review.size() - 1) {
                stackBar.createActionLabel("Next change", () -> review.open(index + 1));
            }
            bars.add(stackBar);
        }
        return bars;
    }

    /**
     * Also after publishing, which turns the drafts into comments under the same ids.
     */
    void load() {
        EditorCommentThreads parentThreads = baseRevision.isPresent() ? null : threads1;
        gerritUtil.getComments(changeInfo._number, revisionId, project, true, true, comments -> {
            List<CommentInfo> fileComments = comments.getOrDefault(path, Collections.emptyList());
            threads2.setComments(filter(fileComments, REVISION_COMMENT));
            if (parentThreads != null) {
                parentThreads.setComments(filter(fileComments, REVISION_COMMENT.negate()));
            }
        });
        if (threads1 != null && baseRevision.isPresent()) {
            EditorCommentThreads baseThreads = threads1;
            gerritUtil.getComments(changeInfo._number, baseRevision.get().getFirst(), project, true, true,
                comments -> baseThreads.setComments(filter(
                    comments.getOrDefault(path, Collections.emptyList()), REVISION_COMMENT)));
        }
        showEarlierComments();
        draftsBar.refresh();
    }

    private static List<CommentInfo> filter(List<CommentInfo> comments, Predicate<Comment> predicate) {
        return comments.stream().filter(predicate).collect(Collectors.toList());
    }

    /**
     * Gerrit lists a comment with the patch set it was made on, and a review often moves on to a new patch set before
     * the threads are answered. Those of earlier patch sets which this diff does not show already appear on the newer
     * side, at the line their line became.
     */
    private void showEarlierComments() {
        RevisionInfo shown = changeInfo.revisions != null ? changeInfo.revisions.get(revisionId) : null;
        if (shown == null) {
            return;
        }
        Integer comparedWith = baseRevision.map(base -> base.getSecond()._number).orElse(null);
        gerritUtil.getChangeComments(changeInfo._number, project, all -> {
            Map<Integer, List<CommentInfo>> byPatchSet = new TreeMap<>();
            for (CommentInfo comment : all.getOrDefault(path, Collections.emptyList())) {
                if (comment.patchSet == null || comment.patchSet >= shown._number
                    || comment.patchSet.equals(comparedWith) || !REVISION_COMMENT.test(comment)) {
                    continue;
                }
                byPatchSet.computeIfAbsent(comment.patchSet, key -> new ArrayList<>()).add(comment);
            }
            for (Map.Entry<Integer, List<CommentInfo>> entry : byPatchSet.entrySet()) {
                String revision = changeInfo.revisions.entrySet().stream()
                    .filter(candidate -> entry.getKey().equals(candidate.getValue()._number))
                    .map(Map.Entry::getKey)
                    .findFirst().orElse(null);
                if (revision == null) {
                    continue;
                }
                gerritUtil.getFileDiff(changeInfo._number, revisionId, path, entry.getKey(), project, diff -> {
                    if (diff != null && diff.content != null) {
                        threads2.addEarlierComments(entry.getValue(), entry.getKey(), revision,
                            LineMapping.fromDiff(diff.content));
                    }
                });
            }
        });
    }
}
