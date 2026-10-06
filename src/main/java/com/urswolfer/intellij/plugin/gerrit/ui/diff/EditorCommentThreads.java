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

import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.client.Comment;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The comment threads of one side of a diff, each shown below the line it is on.
 */
final class EditorCommentThreads implements CommentThreadPanel.Controller {
    private static final TextAttributesKey COMMENT_RANGE_ATTRIBUTES = TextAttributesKey.createTextAttributesKey(
        "GERRIT_COMMENT_RANGE", EditorColors.SEARCH_RESULT_ATTRIBUTES);

    private final GerritUtil gerritUtil = GerritUtil.getInstance();
    private final GerritSettings gerritSettings = GerritSettings.getInstance();

    private final Project project;
    private final EditorEx editor;
    private final ChangeInfo changeInfo;
    private final String revisionId;
    private final String filePath;
    private final Side side;
    private final EditorCommentInlays inlays;

    private final Map<String, CommentInfo> comments = new LinkedHashMap<>();
    private final Map<String, ShownThread> shownThreads = new HashMap<>();
    private final Map<Integer, NewThread> newThreads = new HashMap<>();

    EditorCommentThreads(@NotNull Project project,
                         @NotNull EditorEx editor,
                         @NotNull ChangeInfo changeInfo,
                         @NotNull String revisionId,
                         @NotNull String filePath,
                         @NotNull Side side) {
        this.project = project;
        this.editor = editor;
        this.changeInfo = changeInfo;
        this.revisionId = revisionId;
        this.filePath = filePath;
        this.side = side;
        this.inlays = new EditorCommentInlays(editor);
        AddCommentGutterIcon.install(editor, this::canComment, line -> startThread(line, null));
    }

    void setComments(@NotNull Collection<CommentInfo> fileComments) {
        comments.clear();
        for (CommentInfo comment : fileComments) {
            comment.path = filePath;
            comments.put(comment.id, comment);
        }
        refresh();
    }

    /**
     * @param line 1-based
     */
    void startThread(int line, @Nullable Comment.Range range) {
        NewThread open = newThreads.get(line);
        if (open != null) {
            open.editor.getPreferredFocusedComponent().requestFocusInWindow();
            return;
        }
        NewThread newThread = new NewThread();
        newThread.editor = new CommentEditorPanel(project, "", false, new CommentEditorPanel.Listener() {
            @Override
            public void save(@NotNull String text, boolean resolved) {
                DraftInput draft = Drafts.newComment(filePath, side, line, range, text, resolved);
                EditorCommentThreads.this.save(draft, () -> closeNewThread(line), newThread.editor::saveFailed);
            }

            @Override
            public void cancel() {
                closeNewThread(line);
            }
        });
        CommentCard card = new CommentCard();
        card.getContent().add(newThread.editor);
        newThread.inlay = inlays.insert(toLineIndex(line), card);
        if (newThread.inlay == null) return;
        newThreads.put(line, newThread);
        newThread.editor.getPreferredFocusedComponent().requestFocusInWindow();
    }

    private void closeNewThread(int line) {
        NewThread newThread = newThreads.remove(line);
        if (newThread != null) {
            Disposer.dispose(newThread.inlay);
        }
    }

    @Override
    public boolean canComment() {
        return gerritSettings.isLoginAndPasswordAvailable();
    }

    @Override
    public void reply(@NotNull CommentThread thread, @NotNull String text, boolean resolved,
                      @NotNull Runnable onSaved, @NotNull Runnable onFailed) {
        save(Drafts.reply(thread.getLast(), text, resolved), onSaved, onFailed);
    }

    @Override
    public void edit(@NotNull CommentInfo draft, @NotNull String text, boolean resolved,
                     @NotNull Runnable onSaved, @NotNull Runnable onFailed) {
        save(Drafts.edit(draft, text, resolved), onSaved, onFailed);
    }

    @Override
    public void delete(@NotNull CommentInfo draft) {
        gerritUtil.deleteDraftComment(changeInfo._number, revisionId, draft.id, project, ignored -> {
            comments.remove(draft.id);
            refresh();
        });
    }

    private void save(DraftInput draft, Runnable onSaved, Runnable onFailed) {
        gerritUtil.saveDraftComment(changeInfo._number, revisionId, draft, project, saved -> {
            saved.path = filePath;
            comments.put(saved.id, saved);
            onSaved.run();
            refresh();
        }, onFailed);
    }

    private void refresh() {
        Set<String> rootIds = new HashSet<>();
        for (CommentThread thread : CommentThread.group(comments.values())) {
            String rootId = thread.getRoot().id;
            rootIds.add(rootId);
            ShownThread shown = shownThreads.get(rootId);
            if (shown == null) {
                show(thread);
            } else if (!shown.signature.equals(signature(thread))) {
                shown.signature = signature(thread);
                shown.panel.setThread(thread);
            }
        }
        for (Iterator<Map.Entry<String, ShownThread>> it = shownThreads.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, ShownThread> entry = it.next();
            if (!rootIds.contains(entry.getKey())) {
                hide(entry.getValue());
                it.remove();
            }
        }
    }

    private void show(CommentThread thread) {
        CommentThreadPanel panel = new CommentThreadPanel(project, this, thread);
        Disposable inlay = inlays.insert(toLineIndex(thread.getLine()), panel);
        if (inlay == null) return;
        ShownThread shown = new ShownThread();
        shown.panel = panel;
        shown.inlay = inlay;
        shown.signature = signature(thread);
        Comment.Range range = thread.getRoot().range;
        if (range != null) {
            shown.rangeHighlighter = highlightRange(range);
        }
        shownThreads.put(thread.getRoot().id, shown);
    }

    private void hide(ShownThread shown) {
        Disposer.dispose(shown.inlay);
        if (shown.rangeHighlighter != null) {
            HighlightManager.getInstance(project).removeSegmentHighlighter(editor, shown.rangeHighlighter);
        }
    }

    /**
     * What the panel shows, so that it is only rebuilt when that changes: a rebuild takes the focus from an open
     * editor.
     */
    private static List<String> signature(CommentThread thread) {
        return thread.getComments().stream()
            .map(comment -> comment.id + '|' + comment.updated + '|' + comment.unresolved + '|' + comment.message)
            .collect(Collectors.toList());
    }

    /**
     * @param line 1-based, 0 for the whole file
     */
    private int toLineIndex(int line) {
        int lineCount = editor.getDocument().getLineCount();
        if (line <= 0 || lineCount == 0) return -1;
        return Math.min(line, lineCount) - 1;
    }

    @Nullable
    private RangeHighlighter highlightRange(Comment.Range range) {
        RangeUtils.Offset offset = RangeUtils.rangeToTextOffset(editor.getDocument().getCharsSequence(), range);
        List<RangeHighlighter> highlighters = new ArrayList<>();
        HighlightManager.getInstance(project).addRangeHighlight(
            editor, offset.start, offset.end, COMMENT_RANGE_ATTRIBUTES, false, highlighters);
        return highlighters.isEmpty() ? null : highlighters.get(0);
    }

    private static final class ShownThread {
        CommentThreadPanel panel;
        Disposable inlay;
        List<String> signature;
        @Nullable
        RangeHighlighter rangeHighlighter;
    }

    private static final class NewThread {
        CommentEditorPanel editor;
        Disposable inlay;
    }
}
