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

import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;

/**
 * Writes a comment in place in the diff: a new one, a reply, or a draft being edited.
 */
final class CommentEditorPanel extends JPanel {
    private static final int MIN_LINES = 3;

    interface Listener {
        void save(@NotNull String text, boolean resolved);

        void cancel();
    }

    private final EditorTextField textField;
    private final JCheckBox resolvedCheckBox = new JCheckBox("Resolved");
    private final JButton saveButton = new JButton("Save");
    private final Listener listener;
    private final String unsentKey;

    /**
     * @param unsentKey where the text is kept while it is not saved, and taken from when the editor opens again
     */
    CommentEditorPanel(@NotNull Project project, @NotNull String text, boolean resolved, @NotNull String unsentKey,
                       @NotNull Listener listener) {
        super(new BorderLayout(0, JBUI.scale(4)));
        this.listener = listener;
        this.unsentKey = unsentKey;
        setOpaque(false);
        String unsent = UnsentComments.getInstance().get(unsentKey);
        if (unsent != null) {
            text = unsent;
        }

        textField = new EditorTextField(text, project, FileTypes.PLAIN_TEXT) {
            @Override
            protected EditorEx createEditor() {
                EditorEx editor = super.createEditor();
                editor.getSettings().setUseSoftWraps(true);
                editor.setHorizontalScrollbarVisible(false);
                editor.setBorder(BorderFactory.createEmptyBorder());
                return editor;
            }

            @Override
            public Dimension getPreferredSize() {
                Dimension size = super.getPreferredSize();
                int minHeight = getFontMetrics(getFont()).getHeight() * MIN_LINES + JBUI.scale(8);
                return new Dimension(size.width, Math.max(size.height, minHeight));
            }
        };
        textField.setOneLineMode(false);
        textField.addDocumentListener(new DocumentListener() {
            @Override
            public void documentChanged(@NotNull DocumentEvent event) {
                UnsentComments.getInstance().put(unsentKey, textField.getText());
            }
        });
        textField.setBorder(BorderFactory.createCompoundBorder(
            JBUI.Borders.customLine(JBColor.border(), 1), JBUI.Borders.empty(2, 4)));
        add(textField, BorderLayout.CENTER);

        resolvedCheckBox.setSelected(resolved);
        resolvedCheckBox.setOpaque(false);
        saveButton.addActionListener(e -> save());
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> cancel());

        JPanel buttons = new JPanel();
        buttons.setOpaque(false);
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.add(saveButton);
        buttons.add(Box.createHorizontalStrut(JBUI.scale(4)));
        buttons.add(cancelButton);
        buttons.add(Box.createHorizontalStrut(JBUI.scale(8)));
        buttons.add(resolvedCheckBox);
        buttons.add(Box.createHorizontalGlue());
        JBLabel hint = new JBLabel(KeymapUtil.getShortcutsText(CommonShortcuts.CTRL_ENTER.getShortcuts())
            + " to save. Drafts are published with your next review.", UIUtil.ComponentStyle.SMALL);
        hint.setForeground(UIUtil.getContextHelpForeground());
        buttons.add(hint);
        add(buttons, BorderLayout.SOUTH);

        DumbAwareAction.create(e -> save()).registerCustomShortcutSet(CommonShortcuts.CTRL_ENTER, textField);
        DumbAwareAction.create(e -> cancel()).registerCustomShortcutSet(CommonShortcuts.ESCAPE, textField);
    }

    private void save() {
        String text = textField.getText().trim();
        if (text.isEmpty() || !saveButton.isEnabled()) return;
        saveButton.setEnabled(false);
        UnsentComments.getInstance().remove(unsentKey);
        listener.save(text, resolvedCheckBox.isSelected());
    }

    private void cancel() {
        UnsentComments.getInstance().remove(unsentKey);
        listener.cancel();
    }

    /**
     * After a failed save, so that it can be tried again, and is kept until it is.
     */
    void saveFailed() {
        saveButton.setEnabled(true);
        UnsentComments.getInstance().put(unsentKey, textField.getText());
    }

    @NotNull
    JComponent getPreferredFocusedComponent() {
        return textField;
    }
}
