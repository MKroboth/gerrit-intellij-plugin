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
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/**
 * A "+" in the gutter of the line under the mouse, which starts a comment on that line.
 */
final class AddCommentGutterIcon implements EditorMouseMotionListener, EditorMouseListener {
    private final EditorEx editor;
    private final BooleanSupplier enabled;
    private final IntConsumer startComment;
    @Nullable
    private RangeHighlighter highlighter;
    private int line = -1;

    /**
     * @param startComment takes the 1-based line
     */
    static void install(@NotNull EditorEx editor, @NotNull BooleanSupplier enabled, @NotNull IntConsumer startComment) {
        AddCommentGutterIcon icon = new AddCommentGutterIcon(editor, enabled, startComment);
        Disposable disposable = Disposer.newDisposable();
        EditorUtil.disposeWithEditor(editor, disposable);
        editor.addEditorMouseMotionListener(icon, disposable);
        editor.addEditorMouseListener(icon, disposable);
    }

    private AddCommentGutterIcon(EditorEx editor, BooleanSupplier enabled, IntConsumer startComment) {
        this.editor = editor;
        this.enabled = enabled;
        this.startComment = startComment;
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
     * Only over the lines themselves: over a comment shown between them, the position is that of a line next to it.
     */
    private int lineUnder(EditorMouseEvent e) {
        if (e.getInlay() != null || !enabled.getAsBoolean()) return -1;
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
            highlighter.setGutterIconRenderer(new Renderer(newLine + 1));
        }
    }

    private final class Renderer extends GutterIconRenderer {
        private final int commentLine;

        Renderer(int commentLine) {
            this.commentLine = commentLine;
        }

        @NotNull
        @Override
        public Icon getIcon() {
            return AllIcons.General.InlineAdd;
        }

        @Override
        public String getTooltipText() {
            return "Add comment";
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
                    startComment.accept(commentLine);
                }
            };
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Renderer && ((Renderer) o).commentLine == commentLine;
        }

        @Override
        public int hashCode() {
            return commentLine;
        }
    }
}
