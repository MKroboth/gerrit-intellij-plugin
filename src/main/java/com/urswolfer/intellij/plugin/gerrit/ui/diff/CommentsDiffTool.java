/*
 * Copyright 2013 Urs Wolfer
 * Modified 2026 by Maximilian Kroboth: shows comment threads in place in the diff instead of gutter icons.
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
import com.intellij.diff.DiffContext;
import com.intellij.diff.DiffTool;
import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.SuppressiveDiffTool;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.fragmented.UnifiedDiffTool;
import com.intellij.diff.tools.simple.SimpleDiffTool;
import com.intellij.diff.tools.simple.SimpleDiffViewer;
import com.intellij.diff.tools.simple.SimpleOnesideDiffViewer;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer;
import com.intellij.ui.PopupHandler;
import com.urswolfer.intellij.plugin.gerrit.SelectedRevisions;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.util.GerritUserDataKeys;
import com.urswolfer.intellij.plugin.gerrit.util.PathUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author Urs Wolfer
 *
 * Some parts based on code from:
 * https://github.com/ktisha/Crucible4IDEA
 */
public class CommentsDiffTool implements FrameDiffTool, SuppressiveDiffTool {
    static final Key<AddCommentAction> ADD_COMMENT_ACTION = Key.create("gerrit.AddCommentAction");

    private static final String ADD_COMMENT_ACTION_ID = "Gerrit.AddComment";

    private static final Shortcut[] DEFAULT_ADD_COMMENT_SHORTCUTS = CustomShortcutSet.fromString("C").getShortcuts();

    /**
     * A bare "C" bound to the editor component competes with typing and gets swallowed by the diff
     * viewer in newer IDE versions (issue #410), so it only serves as the default. Once the user has
     * picked a shortcut for {@code Gerrit.AddComment}, the keymap dispatches it and a second binding
     * on a bare letter would just bring the conflict back. Resolved per keystroke, so that a keymap
     * change also reaches the diffs which are open already.
     */
    private static final ShortcutSet ADD_COMMENT_SHORTCUT_SET = () ->
        KeymapUtil.getActiveKeymapShortcuts(ADD_COMMENT_ACTION_ID).getShortcuts().length > 0
            ? Shortcut.EMPTY_ARRAY
            : DEFAULT_ADD_COMMENT_SHORTCUTS;

    private static final Predicate<Comment> REVISION_COMMENT =
        comment -> comment.side == null || comment.side.equals(Side.REVISION);

    private final GerritUtil gerritUtil = GerritUtil.getInstance();

    @NotNull
    @Override
    public String getName() {
        return SimpleDiffTool.INSTANCE.getName();
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Class<? extends DiffTool>> getSuppressedTools() {
        return Arrays.<Class<? extends DiffTool>>asList(
            UnifiedDiffTool.INSTANCE.getClass(),
            SimpleDiffTool.INSTANCE.getClass()
        );
    }

    @Override
    public boolean canShow(@NotNull DiffContext context, @NotNull DiffRequest request) {
        if (context.getUserData(GerritUserDataKeys.CHANGE) == null) return false;
        if (context.getUserData(GerritUserDataKeys.BASE_REVISION) == null) return false;
        if (request.getUserData(ChangeDiffRequestProducer.CHANGE_KEY) == null) return false;
        return SimpleDiffViewer.canShowRequest(context, request)
            || SimpleOnesideDiffViewer.canShowRequest(context, request);
    }

    @NotNull
    @Override
    public DiffViewer createComponent(@NotNull DiffContext context, @NotNull DiffRequest request) {
        if (SimpleDiffViewer.canShowRequest(context, request)) {
            return new SimpleCommentsDiffViewer(context, request);
        } else {
            return new SimpleOnesideCommentsDiffViewer(context, request);
        }
    }

    private void handleComments(@Nullable final EditorEx editor1,
                                final EditorEx editor2,
                                Change change,
                                final Project project,
                                final ChangeInfo changeInfo,
                                final String selectedRevisionId,
                                final Optional<Pair<String, RevisionInfo>> baseRevision) {
        FilePath filePath = ChangesUtil.getFilePath(change);
        final String relativeFilePath = PathUtils.ensureSlashSeparators(getRelativeOrAbsolutePath(project, filePath.getPath(), changeInfo));

        EditorCommentThreads threads2 = createThreads(
            project, editor2, changeInfo, selectedRevisionId, relativeFilePath, Side.REVISION);
        EditorCommentThreads threads1 = null;
        if (editor1 != null) {
            threads1 = baseRevision.isPresent()
                ? createThreads(project, editor1, changeInfo, baseRevision.get().getFirst(), relativeFilePath, Side.REVISION)
                : createThreads(project, editor1, changeInfo, selectedRevisionId, relativeFilePath, Side.PARENT);
        }

        final EditorCommentThreads parentThreads = baseRevision.isPresent() ? null : threads1;
        gerritUtil.getComments(changeInfo._number, selectedRevisionId, project, true, true,
            comments -> {
                List<CommentInfo> fileComments = comments.getOrDefault(relativeFilePath, Collections.emptyList());
                threads2.setComments(filter(fileComments, REVISION_COMMENT));
                if (parentThreads != null) {
                    parentThreads.setComments(filter(fileComments, REVISION_COMMENT.negate()));
                }
            });

        if (threads1 != null && baseRevision.isPresent()) {
            final EditorCommentThreads baseThreads = threads1;
            gerritUtil.getComments(changeInfo._number, baseRevision.get().getFirst(), project, true, true,
                comments -> baseThreads.setComments(filter(
                    comments.getOrDefault(relativeFilePath, Collections.emptyList()), REVISION_COMMENT)));
        }

        gerritUtil.setReviewed(changeInfo._number, selectedRevisionId,
                relativeFilePath, project);
    }

    private EditorCommentThreads createThreads(Project project,
                                               EditorEx editor,
                                               ChangeInfo changeInfo,
                                               String revisionId,
                                               String filePath,
                                               Side commentSide) {
        EditorCommentThreads threads = new EditorCommentThreads(
            project, editor, changeInfo, revisionId, filePath, commentSide);

        AddCommentAction addCommentAction = new AddCommentAction(editor, threads);
        editor.putUserData(ADD_COMMENT_ACTION, addCommentAction);
        addCommentAction.registerCustomShortcutSet(ADD_COMMENT_SHORTCUT_SET, editor.getContentComponent());
        DefaultActionGroup group = new DefaultActionGroup();
        group.add(addCommentAction);
        PopupHandler.installPopupHandler(editor.getContentComponent(), group, "GerritCommentDiffPopup");
        return threads;
    }

    private static List<CommentInfo> filter(List<CommentInfo> comments, Predicate<Comment> predicate) {
        return comments.stream().filter(predicate).collect(Collectors.toList());
    }

    private void handleDiffViewer(DiffContext diffContext, ContentDiffRequest diffRequest,
                                  @Nullable EditorEx editor1, EditorEx editor2) {
        ChangeInfo changeInfo = diffContext.getUserData(GerritUserDataKeys.CHANGE);
        Optional<Pair<String, RevisionInfo>> baseRevision = diffContext.getUserData(GerritUserDataKeys.BASE_REVISION);
        String selectedRevisionId = changeInfo != null
            ? SelectedRevisions.getInstance(diffContext.getProject()).get(changeInfo) : null;
        Change change = diffRequest.getUserData(ChangeDiffRequestProducer.CHANGE_KEY);
        handleComments(editor1, editor2, change, diffContext.getProject(), changeInfo, selectedRevisionId, baseRevision);
    }

    private class SimpleCommentsDiffViewer extends SimpleDiffViewer {
        public SimpleCommentsDiffViewer(@NotNull DiffContext context, @NotNull DiffRequest request) {
            super(context, request);
        }

        @Override
        protected void onInit() {
            super.onInit();
            handleDiffViewer(myContext, myRequest, getEditor1(), getEditor2());
        }
    }

    private class SimpleOnesideCommentsDiffViewer extends SimpleOnesideDiffViewer {
        public SimpleOnesideCommentsDiffViewer(@NotNull DiffContext context, @NotNull DiffRequest request) {
            super(context, request);
        }

        @Override
        protected void onInit() {
            super.onInit();
            handleDiffViewer(myContext, myRequest, null, getEditor());
        }
    }

    private String getRelativeOrAbsolutePath(Project project, String absoluteFilePath, ChangeInfo changeInfo) {
        return PathUtils.getRelativeOrAbsolutePath(project, absoluteFilePath, changeInfo);
    }
}
