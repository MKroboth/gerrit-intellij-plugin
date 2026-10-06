/*
 * Copyright 2013 Urs Wolfer
 * Modified 2026 by Maximilian Kroboth: shows comment threads in place in the diff unless the settings turn it off,
 * with those of earlier patch sets carried forward.
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
import com.intellij.codeInsight.highlighting.HighlightManager;
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
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer;
import com.intellij.ui.PopupHandler;
import com.intellij.util.Consumer;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.SelectedRevisions;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.util.GerritUserDataKeys;
import com.urswolfer.intellij.plugin.gerrit.util.PathUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author Urs Wolfer
 *
 * Some parts based on code from:
 * https://github.com/ktisha/Crucible4IDEA
 */
public class CommentsDiffTool implements FrameDiffTool, SuppressiveDiffTool {
    static final Key<AnAction> ADD_COMMENT_ACTION = Key.create("gerrit.AddCommentAction");

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

    private static final TextAttributesKey COMMENT_RANGE_ATTRIBUTES = TextAttributesKey.createTextAttributesKey(
        "GERRIT_COMMENT_RANGE", EditorColors.SEARCH_RESULT_ATTRIBUTES);

    private static final Predicate<Comment> REVISION_COMMENT =
        comment -> comment.side == null || comment.side.equals(Side.REVISION);

    // descending, as icons are added to the left of existing icons
    private static final Comparator<Comment> COMMENT_ORDERING =
        Comparator.comparingLong((Comment comment) -> comment.updated.getTime()).reversed();

    private final GerritUtil gerritUtil = GerritUtil.getInstance();
    private final GerritSettings gerritSettings = GerritSettings.getInstance();
    private final AddCommentActionBuilder addCommentActionBuilder = new AddCommentActionBuilder();

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

        if (gerritSettings.getCommentsInPlace()) {
            showCommentsInPlace(editor1, editor2, project, changeInfo, selectedRevisionId, baseRevision, relativeFilePath);
            return;
        }

        addCommentAction(editor1, editor2, relativeFilePath, changeInfo, selectedRevisionId, baseRevision);

        gerritUtil.getComments(changeInfo._number, selectedRevisionId, project, true, true,
                new Consumer<Map<String, List<CommentInfo>>>() {
                    @Override
                    public void consume(Map<String, List<CommentInfo>> comments) {
                        List<CommentInfo> fileComments = comments.get(relativeFilePath);
                        if (fileComments != null) {
                            addCommentsGutter(
                                    editor2,
                                    relativeFilePath,
                                    selectedRevisionId,
                                    filter(fileComments, REVISION_COMMENT),
                                    changeInfo,
                                    project
                            );
                            if (!baseRevision.isPresent()) {
                                addCommentsGutter(
                                        editor1,
                                        relativeFilePath,
                                        selectedRevisionId,
                                        filter(fileComments, REVISION_COMMENT.negate()),
                                        changeInfo,
                                        project
                                );
                            }
                        }
                    }
                }
        );

        if (baseRevision.isPresent()) {
            gerritUtil.getComments(changeInfo._number, baseRevision.get().getFirst(), project, true, true,
                    new Consumer<Map<String, List<CommentInfo>>>() {
                @Override
                public void consume(Map<String, List<CommentInfo>> comments) {
                    List<CommentInfo> fileComments = comments.get(relativeFilePath);
                    if (fileComments != null) {
                        Collections.sort(fileComments, COMMENT_ORDERING);
                        addCommentsGutter(
                                editor1,
                                relativeFilePath,
                                baseRevision.get().getFirst(),
                                filter(fileComments, REVISION_COMMENT),
                                changeInfo,
                                project
                        );
                    }
                }
            });
        }

        gerritUtil.setReviewed(changeInfo._number, selectedRevisionId,
                relativeFilePath, project);
    }

    private void showCommentsInPlace(@Nullable EditorEx editor1,
                                     EditorEx editor2,
                                     Project project,
                                     ChangeInfo changeInfo,
                                     String selectedRevisionId,
                                     Optional<Pair<String, RevisionInfo>> baseRevision,
                                     String relativeFilePath) {
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

        showEarlierComments(threads2, project, changeInfo, selectedRevisionId, baseRevision, relativeFilePath);

        gerritUtil.setReviewed(changeInfo._number, selectedRevisionId, relativeFilePath, project);
    }

    /**
     * Gerrit lists a comment with the patch set it was made on, and a review often moves on to a new patch set before
     * the threads are answered. Those of earlier patch sets which this diff does not show already appear on the newer
     * side, at the line their line became.
     */
    private void showEarlierComments(EditorCommentThreads threads,
                                     Project project,
                                     ChangeInfo changeInfo,
                                     String revisionId,
                                     Optional<Pair<String, RevisionInfo>> baseRevision,
                                     String path) {
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
                        threads.addEarlierComments(entry.getValue(), entry.getKey(), revision,
                            LineMapping.fromDiff(diff.content));
                    }
                });
            }
        });
    }

    private EditorCommentThreads createThreads(Project project,
                                               EditorEx editor,
                                               ChangeInfo changeInfo,
                                               String revisionId,
                                               String filePath,
                                               Side commentSide) {
        EditorCommentThreads threads = new EditorCommentThreads(
            project, editor, changeInfo, revisionId, filePath, commentSide);
        installAddCommentAction(editor, new AddCommentInPlaceAction(editor, threads));
        return threads;
    }

    private void addCommentAction(EditorEx editor1, EditorEx editor2, String filePath, ChangeInfo changeInfo,
                                  String selectedRevisionId, Optional<Pair<String, RevisionInfo>> baseRevision) {
        if (baseRevision.isPresent()) {
            addCommentActionToEditor(editor1, filePath, changeInfo, baseRevision.get().getFirst(), Side.REVISION);
        } else {
            addCommentActionToEditor(editor1, filePath, changeInfo, selectedRevisionId, Side.PARENT);
        }
        addCommentActionToEditor(editor2, filePath, changeInfo, selectedRevisionId, Side.REVISION);
    }

    private void addCommentActionToEditor(Editor editor,
                                          String filePath,
                                          ChangeInfo changeInfo,
                                          String revisionId,
                                          Side commentSide) {
        if (editor == null) return;

        final AddCommentAction addCommentAction = addCommentActionBuilder
                .create(this, changeInfo, revisionId, editor, filePath, commentSide)
                .withText("Add Comment")
                .withIcon(AllIcons.Toolwindows.ToolWindowMessages)
                .get();
        installAddCommentAction(editor, addCommentAction);
    }

    private static void installAddCommentAction(Editor editor, AnAction addCommentAction) {
        DefaultActionGroup group = new DefaultActionGroup();
        editor.putUserData(ADD_COMMENT_ACTION, addCommentAction);
        addCommentAction.registerCustomShortcutSet(ADD_COMMENT_SHORTCUT_SET, editor.getContentComponent());
        group.add(addCommentAction);
        PopupHandler.installPopupHandler(editor.getContentComponent(), group, "GerritCommentDiffPopup");
    }

    private static List<CommentInfo> filter(List<CommentInfo> comments, Predicate<Comment> predicate) {
        return comments.stream().filter(predicate).collect(Collectors.toList());
    }

    private void addCommentsGutter(Editor editor,
                                   String filePath,
                                   String revisionId,
                                   Iterable<CommentInfo> fileComments,
                                   ChangeInfo changeInfo,
                                   Project project) {
        for (CommentInfo fileComment : fileComments) {
            fileComment.path = PathUtils.ensureSlashSeparators(filePath);
            addComment(editor, changeInfo, revisionId, project, fileComment);
        }
    }

    public void addComment(Editor editor, ChangeInfo changeInfo, String revisionId, Project project, Comment comment) {
        if (editor == null) return;
        MarkupModel markup = editor.getMarkupModel();

        RangeHighlighter rangeHighlighter = null;
        if (comment.range != null) {
            rangeHighlighter = highlightRangeComment(comment.range, editor, project);
        }

        int lineCount = markup.getDocument().getLineCount();

        int line = (comment.line != null ? comment.line : 0) - 1;
        if (line < 0) {
            line = 0;
        }
        if (line > lineCount - 1) {
            line = lineCount - 1;
        }
        if (line >= 0) {
            final RangeHighlighter highlighter = markup.addLineHighlighter(line, HighlighterLayer.ERROR + 1, null);
            CommentGutterIconRenderer iconRenderer = new CommentGutterIconRenderer(
                    this, editor, gerritUtil, gerritSettings, addCommentActionBuilder,
                    comment, changeInfo, revisionId, highlighter, rangeHighlighter);
            highlighter.setGutterIconRenderer(iconRenderer);
        }
    }

    public void removeComment(Project project, Editor editor, RangeHighlighter lineHighlighter, RangeHighlighter rangeHighlighter) {
        editor.getMarkupModel().removeHighlighter(lineHighlighter);
        lineHighlighter.dispose();

        if (rangeHighlighter != null) {
            HighlightManager highlightManager = HighlightManager.getInstance(project);
            highlightManager.removeSegmentHighlighter(editor, rangeHighlighter);
        }
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

    private static RangeHighlighter highlightRangeComment(Comment.Range range, Editor editor, Project project) {
        CharSequence charsSequence = editor.getMarkupModel().getDocument().getCharsSequence();

        RangeUtils.Offset offset = RangeUtils.rangeToTextOffset(charsSequence, range);

        ArrayList<RangeHighlighter> highlighters = new ArrayList<>();
        HighlightManager highlightManager = HighlightManager.getInstance(project);
        highlightManager.addRangeHighlight(editor, offset.start, offset.end, COMMENT_RANGE_ATTRIBUTES, false, highlighters);
        return highlighters.get(0);
    }

}
