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

import com.intellij.ui.JBColor;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;

/**
 * The frame of what is shown between the lines of a diff.
 *
 * Tracks the width of the scroll pane it sits in, so that text wraps instead of being cut off.
 */
class CommentCard extends JPanel implements Scrollable {
    private final JPanel content = new JPanel(new VerticalLayout(JBUI.scale(8)));

    CommentCard() {
        super(new BorderLayout());
        setOpaque(false);
        setBorder(JBUI.Borders.empty(4, 0));
        content.setOpaque(true);
        content.setBackground(UIUtil.getPanelBackground());
        content.setBorder(BorderFactory.createCompoundBorder(
            JBUI.Borders.customLine(JBColor.border(), 1), JBUI.Borders.empty(6, 8)));
        add(content, BorderLayout.CENTER);
    }

    /**
     * Stacks its children, each as wide as the card.
     */
    @NotNull
    final JPanel getContent() {
        return content;
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return JBUI.scale(16);
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return orientation == SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }
}
