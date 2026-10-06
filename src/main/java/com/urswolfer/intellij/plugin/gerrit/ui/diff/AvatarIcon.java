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

import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * The initials of a comment's author on a coloured circle, which stays the same for an account. Gerrit sends
 * avatar images only with an avatar plugin installed, and loading them from elsewhere would send who is looked at.
 */
final class AvatarIcon implements Icon {
    private static final Color[] COLORS = {
        new JBColor(0x3D7DCA, 0x3D7DCA), new JBColor(0x8E5AC7, 0x8E5AC7), new JBColor(0x2F9A7E, 0x2F9A7E),
        new JBColor(0xC2603D, 0xC2603D), new JBColor(0xB2457A, 0xB2457A), new JBColor(0x5E7F2E, 0x5E7F2E),
        new JBColor(0x7A6A2B, 0x7A6A2B), new JBColor(0x3A6F8F, 0x3A6F8F),
    };

    private final String initials;
    private final Color color;
    private final int size;

    private AvatarIcon(String name, int seed, int size) {
        this.initials = initials(name);
        this.color = COLORS[Math.floorMod(seed, COLORS.length)];
        this.size = size;
    }

    @NotNull
    static AvatarIcon of(@NotNull CommentInfo comment, int size) {
        AccountInfo author = comment.author;
        if (author == null) {
            return self(size);
        }
        String name = StringUtil.notNullize(author.name, StringUtil.notNullize(author.username, "?"));
        int seed = author._accountId != null ? author._accountId : name.hashCode();
        return new AvatarIcon(name, seed, size);
    }

    /**
     * Drafts carry no author; they are written by the configured login.
     */
    @NotNull
    static AvatarIcon self(int size) {
        String login = StringUtil.notNullize(GerritSettings.getInstance().getLogin(), "?");
        return new AvatarIcon(login, login.hashCode(), size);
    }

    static String initials(String name) {
        String[] parts = name.trim().split("[\\s._-]+");
        StringBuilder initials = new StringBuilder();
        for (String part : new String[]{parts[0], parts.length > 1 ? parts[parts.length - 1] : ""}) {
            if (!part.isEmpty()) {
                initials.appendCodePoint(Character.toUpperCase(part.codePointAt(0)));
            }
        }
        return initials.length() > 0 ? initials.toString() : "?";
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillOval(x, y, size, size);
            g2.setColor(Color.WHITE);
            g2.setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD, size * (initials.length() > 1 ? 0.38f : 0.48f)));
            FontMetrics metrics = g2.getFontMetrics();
            int textX = x + (size - metrics.stringWidth(initials)) / 2;
            int textY = y + (size - metrics.getHeight()) / 2 + metrics.getAscent();
            g2.drawString(initials, textX, textY);
        } finally {
            g2.dispose();
        }
    }

    @Override
    public int getIconWidth() {
        return size;
    }

    @Override
    public int getIconHeight() {
        return size;
    }

    static int commentSize() {
        return JBUI.scale(20);
    }
}
