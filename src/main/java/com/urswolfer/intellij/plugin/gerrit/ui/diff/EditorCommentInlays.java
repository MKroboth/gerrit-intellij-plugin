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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.EditorEmbeddedComponentManager;
import com.intellij.openapi.editor.impl.view.FontLayoutService;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.ScrollPaneConstants;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.HashSet;
import java.util.Set;

/**
 * Swing components shown between the lines of an editor, as wide as the text area up to the right margin.
 *
 * The platform sizes an embedded component to its preferred size and does not follow the editor's width, so each
 * one sits in a scroll pane which takes its width from here and its height from the component.
 */
final class EditorCommentInlays implements Disposable {
    private final EditorEx editor;
    private final Set<Wrapper> wrappers = new HashSet<>();
    private final WidthWatcher widthWatcher = new WidthWatcher();

    EditorCommentInlays(@NotNull EditorEx editor) {
        this.editor = editor;
        editor.getScrollPane().getViewport().addComponentListener(widthWatcher);
        Disposer.register(this, () -> editor.getScrollPane().getViewport().removeComponentListener(widthWatcher));
        EditorUtil.disposeWithEditor(editor, this);
    }

    /**
     * @param line 0-based; a component for line -1 goes above the first line
     */
    @Nullable
    Disposable insert(int line, @NotNull JComponent component) {
        if (Disposer.isDisposed(this)) return null;
        boolean above = line < 0;
        int offset = above ? 0 : editor.getDocument().getLineEndOffset(line);

        Wrapper wrapper = new Wrapper(component);
        Inlay<?> inlay = EditorEmbeddedComponentManager.getInstance().addComponent(editor, wrapper,
            new EditorEmbeddedComponentManager.Properties(
                EditorEmbeddedComponentManager.ResizePolicy.none(), null, !above, above, 0, offset));
        if (inlay == null) return null;
        wrappers.add(wrapper);
        Disposer.register(inlay, () -> wrappers.remove(wrapper));
        return inlay;
    }

    @Override
    public void dispose() {
    }

    private final class Wrapper extends JBScrollPane {
        private final JComponent component;

        Wrapper(JComponent component) {
            super(component);
            this.component = component;
            setOpaque(false);
            getViewport().setOpaque(false);
            setBorder(JBUI.Borders.empty());
            setViewportBorder(JBUI.Borders.empty());
            setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            getVerticalScrollBar().setPreferredSize(new Dimension(0, 0));
            // the platform resizes the inlay when this component reports a resize
            component.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    dispatchEvent(new ComponentEvent(Wrapper.this, ComponentEvent.COMPONENT_RESIZED));
                }
            });
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(widthWatcher.getWidth(), component.getPreferredSize().height);
        }
    }

    private final class WidthWatcher extends ComponentAdapter {
        private int width;

        int getWidth() {
            if (width == 0) {
                width = calcWidth();
            }
            return width;
        }

        @Override
        public void componentResized(ComponentEvent e) {
            update();
        }

        @Override
        public void componentShown(ComponentEvent e) {
            update();
        }

        private void update() {
            int newWidth = calcWidth();
            if (newWidth == width) return;
            width = newWidth;
            for (Wrapper wrapper : wrappers) {
                wrapper.dispatchEvent(new ComponentEvent(wrapper, ComponentEvent.COMPONENT_RESIZED));
                wrapper.invalidate();
            }
        }

        private int calcWidth() {
            FontMetrics metrics = editor.getContentComponent().getFontMetrics(
                editor.getColorsScheme().getFont(EditorFontType.PLAIN));
            float spaceWidth = FontLayoutService.getInstance().charWidth2D(metrics, ' ');
            int maximum = (int) Math.ceil(spaceWidth * editor.getSettings().getRightMargin(editor.getProject())) - 4;

            JBScrollPane.Flip flip = (JBScrollPane.Flip) editor.getScrollPane().getClientProperty(JBScrollPane.Flip.class);
            boolean flipped = flip == JBScrollPane.Flip.HORIZONTAL || flip == JBScrollPane.Flip.BOTH;
            int scrollBarWidth = editor.getScrollPane().getVerticalScrollBar().getWidth();
            int gutterGap = flipped
                ? editor.getGutterComponentEx().getWidth() - editor.getGutterComponentEx().getWhitespaceSeparatorOffset()
                : 0;
            int visible = editor.getScrollPane().getViewport().getWidth() - (flipped ? scrollBarWidth : scrollBarWidth * 2)
                - gutterGap;
            return Math.min(Math.max(visible, 0), maximum);
        }
    }
}
