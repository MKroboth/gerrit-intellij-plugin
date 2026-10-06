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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.urswolfer.intellij.plugin.gerrit.GerritBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * A "+" in the gutter of the line under the mouse, which starts a comment on that line, as in the review tools of
 * GitHub and GitLab. It goes by the add-comment action the editor holds, and shows only while there is one.
 */
final class AddCommentGutterIcon implements EditorMouseMotionListener, EditorMouseListener {
    private final EditorEx editor;
    private final Project project;
    @Nullable
    private RangeHighlighter highlighter;
    private int line = -1;

    /** Until the editor is released or the returned disposable is disposed. */
    static Disposable install(EditorEx editor, Project project) {
        AddCommentGutterIcon icon = new AddCommentGutterIcon(editor, project);
        Disposable disposable = Disposer.newDisposable();
        EditorUtil.disposeWithEditor(editor, disposable);
        editor.addEditorMouseMotionListener(icon, disposable);
        editor.addEditorMouseListener(icon, disposable);
        Disposer.register(disposable, () -> {
            if (!editor.isDisposed()) {
                icon.show(-1);
            }
        });
        return disposable;
    }

    private AddCommentGutterIcon(EditorEx editor, Project project) {
        this.editor = editor;
        this.project = project;
    }

    @Nullable
    private AddCommentAction action() {
        AddCommentAction action = editor.getUserData(GerritCommentsDiffExtension.ADD_COMMENT_ACTION);
        return action != null && AddCommentAction.canComment(project) ? action : null;
    }

    @Override
    public void mouseMoved(@NotNull EditorMouseEvent e) {
        show(lineUnder(e));
    }

    @Override
    public void mouseExited(@NotNull EditorMouseEvent e) {
        show(-1);
    }

    /**
     * Only over the lines themselves: over a thread shown between them, the position is that of a line next to it.
     */
    private int lineUnder(EditorMouseEvent e) {
        if (e.getInlay() != null || action() == null) return -1;
        EditorMouseEventArea area = e.getArea();
        if (area != EditorMouseEventArea.EDITING_AREA && area != EditorMouseEventArea.LINE_MARKERS_AREA
            && area != EditorMouseEventArea.LINE_NUMBERS_AREA) return -1;
        int line = e.getLogicalPosition().line;
        return line < editor.getDocument().getLineCount() ? line : -1;
    }

    private void show(int newLine) {
        if (newLine == line) return;
        if (highlighter != null) {
            editor.getMarkupModel().removeHighlighter(highlighter);
            highlighter = null;
        }
        line = newLine;
        if (newLine >= 0) {
            highlighter = editor.getMarkupModel().addLineHighlighter(newLine, HighlighterLayer.LAST, null);
            highlighter.setGutterIconRenderer(new Renderer(newLine));
        }
    }

    /** As the action does at the caret, which the form opens next to; a selection would make it a range. */
    private void startComment(int editorLine) {
        AddCommentAction action = action();
        if (action == null || editorLine >= editor.getDocument().getLineCount()) return;
        editor.getSelectionModel().removeSelection();
        editor.getCaretModel().moveToOffset(editor.getDocument().getLineStartOffset(editorLine));
        action.addVersionedComment(project);
    }

    private final class Renderer extends GutterIconRenderer {
        private final int editorLine;

        Renderer(int editorLine) {
            this.editorLine = editorLine;
        }

        @NotNull
        @Override
        public Icon getIcon() {
            return AllIcons.General.InlineAdd;
        }

        @Override
        public String getTooltipText() {
            return GerritBundle.message("diff.addComment");
        }

        @Override
        public boolean isNavigateAction() {
            return true;
        }

        @Override
        public AnAction getClickAction() {
            return new DumbAwareAction() {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e) {
                    startComment(editorLine);
                }
            };
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Renderer && ((Renderer) o).editorLine == editorLine;
        }

        @Override
        public int hashCode() {
            return editorLine;
        }
    }
}
