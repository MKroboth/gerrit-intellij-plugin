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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.LogicalPosition;
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

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
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
    /** Comments of earlier patch sets shown here, by comment id; a reply saved into their thread joins them. */
    private final Map<String, Origin> origins = new HashMap<>();
    private final Map<String, ShownThread> shownThreads = new HashMap<>();
    /** The shown threads from the top of the file to the bottom. */
    private final List<ShownThread> order = new ArrayList<>();
    @Nullable
    private ShownThread lastRevealed;
    private final Map<Integer, NewThread> newThreads = new HashMap<>();
    private Runnable draftsChanged = () -> {};
    private List<CommentMarkdown.Link> commentLinks = Collections.emptyList();

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
        ThreadKeys.install(editor, this);
    }

    void setCommentLinks(@NotNull List<CommentMarkdown.Link> commentLinks) {
        this.commentLinks = commentLinks;
    }

    @NotNull
    @Override
    public List<CommentMarkdown.Link> commentLinks() {
        return commentLinks;
    }

    void onDraftsChanged(@NotNull Runnable draftsChanged) {
        this.draftsChanged = draftsChanged;
    }

    void setComments(@NotNull Collection<CommentInfo> fileComments) {
        comments.keySet().removeIf(id -> !origins.containsKey(id));
        for (CommentInfo comment : fileComments) {
            comment.path = filePath;
            comments.put(comment.id, comment);
        }
        refresh();
        reopenUnsentThreads();
    }

    /**
     * New comments started before the diff was closed open again where they were, without taking the focus.
     */
    private void reopenUnsentThreads() {
        String prefix = unsentKey("new", newThreadId(""));
        for (String line : UnsentComments.getInstance().suffixesOf(prefix)) {
            try {
                startThread(Integer.parseInt(line), null, false);
            } catch (NumberFormatException ignored) {
                // not a key of this kind
            }
        }
    }

    private String newThreadId(String line) {
        return revisionId + ':' + side + ':' + filePath + ':' + line;
    }

    @NotNull
    @Override
    public String unsentKey(@NotNull String kind, @NotNull String id) {
        return kind + ':' + changeInfo.id + ':' + id;
    }

    /**
     * Comments made on an earlier patch set of this file, shown where their lines are now.
     */
    void addEarlierComments(@NotNull Collection<CommentInfo> earlier, int patchSet, @NotNull String revision,
                            @NotNull LineMapping mapping) {
        for (CommentInfo comment : earlier) {
            comment.path = filePath;
            comments.put(comment.id, comment);
            LineMapping.Mapped mapped = mapping.map(comment.line != null ? comment.line : 0);
            origins.put(comment.id, new Origin(patchSet, revision, mapped.line, mapped.exact));
        }
        refresh();
    }

    /**
     * @param line 1-based
     */
    void startThread(int line, @Nullable Comment.Range range) {
        startThread(line, range, true);
    }

    private void startThread(int line, @Nullable Comment.Range range, boolean focus) {
        NewThread open = newThreads.get(line);
        if (open != null) {
            if (focus) {
                open.editor.getPreferredFocusedComponent().requestFocusInWindow();
            }
            return;
        }
        NewThread newThread = new NewThread();
        String unsentKey = unsentKey("new", newThreadId(Integer.toString(line)));
        newThread.editor = new CommentEditorPanel(project, "", false, unsentKey, new CommentEditorPanel.Listener() {
            @Override
            public void save(@NotNull String text, boolean resolved) {
                DraftInput draft = Drafts.newComment(filePath, side, line, range, text, resolved);
                EditorCommentThreads.this.save(draft, null, () -> closeNewThread(line), newThread.editor::saveFailed);
            }

            @Override
            public void cancel() {
                closeNewThread(line);
            }
        });
        CommentCard card = new CommentCard();
        card.getContent().add(CommentThreadPanel.withAvatar(AvatarIcon.self(AvatarIcon.commentSize()), newThread.editor));
        newThread.inlay = inlays.insert(toLineIndex(line), card);
        if (newThread.inlay == null) return;
        newThreads.put(line, newThread);
        if (focus) {
            newThread.editor.getPreferredFocusedComponent().requestFocusInWindow();
        }
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
        save(Drafts.reply(thread.getLast(), text, resolved), origins.get(thread.getRoot().id), onSaved, onFailed);
    }

    @Override
    public void edit(@NotNull CommentInfo draft, @NotNull String text, boolean resolved,
                     @NotNull Runnable onSaved, @NotNull Runnable onFailed) {
        save(Drafts.edit(draft, text, resolved), origins.get(draft.id), onSaved, onFailed);
    }

    @Override
    public void delete(@NotNull CommentInfo draft) {
        gerritUtil.deleteDraftComment(changeInfo._number, revisionOf(origins.get(draft.id)), draft.id, project,
            ignored -> {
                comments.remove(draft.id);
                origins.remove(draft.id);
                refresh();
                draftsChanged.run();
            });
    }

    /**
     * Into the patch set of the thread it belongs to: Gerrit keeps a reply on the patch set of what it answers.
     */
    private void save(DraftInput draft, @Nullable Origin origin, Runnable onSaved, Runnable onFailed) {
        gerritUtil.saveDraftComment(changeInfo._number, revisionOf(origin), draft, project, saved -> {
            saved.path = filePath;
            comments.put(saved.id, saved);
            if (origin != null) {
                origins.put(saved.id, origin);
            }
            onSaved.run();
            refresh();
            draftsChanged.run();
        }, onFailed);
    }

    private String revisionOf(@Nullable Origin origin) {
        return origin != null ? origin.revision : revisionId;
    }

    @Nullable
    @Override
    public String originOf(@NotNull CommentThread thread) {
        Origin origin = origins.get(thread.getRoot().id);
        if (origin == null) return null;
        return "Patch set " + origin.patchSet + (origin.exact ? "" : " · line changed");
    }

    private int lineOf(CommentThread thread) {
        Origin origin = origins.get(thread.getRoot().id);
        return origin != null ? origin.line : thread.getLine();
    }

    private void refresh() {
        Set<String> rootIds = new HashSet<>();
        order.clear();
        for (CommentThread thread : CommentThread.group(comments.values())) {
            String rootId = thread.getRoot().id;
            rootIds.add(rootId);
            ShownThread shown = shownThreads.get(rootId);
            if (shown == null) {
                shown = show(thread);
            } else if (!shown.signature.equals(signature(thread))) {
                shown.signature = signature(thread);
                shown.panel.setThread(thread);
            }
            if (shown != null) {
                order.add(shown);
            }
        }
        for (Iterator<Map.Entry<String, ShownThread>> it = shownThreads.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, ShownThread> entry = it.next();
            if (!rootIds.contains(entry.getKey())) {
                hide(entry.getValue());
                it.remove();
            }
        }
        order.sort(Comparator.comparingInt(shown -> shown.line));
        for (int i = 0; i < order.size(); i++) {
            order.get(i).panel.setNeighbours(i > 0, i < order.size() - 1);
        }
        String asked = ThreadReveal.take(changeInfo.id, filePath);
        ShownThread askedFor = asked != null ? shownThreads.get(asked) : null;
        if (askedFor != null) {
            ThreadReveal.done();
            // after the new inlays are laid out, which the scroll position depends on
            ApplicationManager.getApplication().invokeLater(() -> show(askedFor));
        }
    }

    /**
     * The next thread below the caret, or above it; after one was revealed, the one after it. The caret moves to the
     * revealed thread's line, so that the next step goes on from there.
     */
    void revealFromCaret(int direction) {
        int caretLine = editor.getCaretModel().getLogicalPosition().line + 1;
        int target = -1;
        if (lastRevealed != null && order.contains(lastRevealed) && lastRevealed.line == caretLine) {
            target = order.indexOf(lastRevealed) + direction;
        } else if (direction > 0) {
            for (int i = 0; i < order.size() && target < 0; i++) {
                if (order.get(i).line >= caretLine) target = i;
            }
        } else {
            for (int i = order.size() - 1; i >= 0 && target < 0; i--) {
                if (order.get(i).line < caretLine) target = i;
            }
        }
        if (target >= 0 && target < order.size()) {
            show(order.get(target));
        }
    }

    /**
     * Opens the reply of the thread on the caret's line, or of the next one below it.
     */
    void replyAtCaret() {
        int caretLine = editor.getCaretModel().getLogicalPosition().line + 1;
        for (ShownThread shown : order) {
            if (shown.line >= caretLine) {
                show(shown);
                shown.panel.startReply();
                return;
            }
        }
    }

    @Override
    public void reveal(@NotNull CommentThreadPanel from, int direction) {
        int index = -1;
        for (int i = 0; i < order.size(); i++) {
            if (order.get(i).panel == from) index = i;
        }
        int target = index + direction;
        if (index < 0 || target < 0 || target >= order.size()) return;
        show(order.get(target));
    }

    private void show(ShownThread shown) {
        lastRevealed = shown;
        shown.panel.expand();
        editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(Math.max(shown.line - 1, 0), 0));
        Rectangle bounds = shown.inlay.getBounds();
        if (bounds != null) {
            // the line the thread is on stays in view above it
            editor.getScrollingModel().scrollVertically(Math.max(0, bounds.y - 2 * editor.getLineHeight()));
        }
    }

    @Nullable
    private ShownThread show(CommentThread thread) {
        CommentThreadPanel panel = new CommentThreadPanel(project, this, thread);
        int line = lineOf(thread);
        Inlay<?> inlay = inlays.insert(toLineIndex(line), panel);
        if (inlay == null) return null;
        ShownThread shown = new ShownThread();
        shown.panel = panel;
        shown.inlay = inlay;
        shown.line = line;
        shown.signature = signature(thread);
        Comment.Range range = thread.getRoot().range;
        // the range of an earlier patch set is not where it was in this one
        if (range != null && !origins.containsKey(thread.getRoot().id)) {
            shown.rangeHighlighter = highlightRange(range);
        }
        shownThreads.put(thread.getRoot().id, shown);
        return shown;
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
        Inlay<?> inlay;
        int line;
        List<String> signature;
        @Nullable
        RangeHighlighter rangeHighlighter;
    }

    private static final class Origin {
        final int patchSet;
        final String revision;
        final int line;
        final boolean exact;

        Origin(int patchSet, String revision, int line, boolean exact) {
            this.patchSet = patchSet;
            this.revision = revision;
            this.line = line;
            this.exact = exact;
        }
    }

    private static final class NewThread {
        CommentEditorPanel editor;
        Disposable inlay;
    }
}
