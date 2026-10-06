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

import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.ui.JBUI;
import com.urswolfer.intellij.plugin.gerrit.ui.ReviewPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Publishes the drafts of every patch set of a change, with a message and the votes the user may give.
 */
final class PublishDialog extends DialogWrapper {
    private final ReviewPanel reviewPanel;
    private final Map<String, JComboBox<String>> votes = new LinkedHashMap<>();

    PublishDialog(@NotNull Project project, @NotNull ChangeInfo changeInfo) {
        super(project, true);
        setTitle("Publish Drafts");
        setOKButtonText("Publish");
        reviewPanel = new ReviewPanel(project);
        Map<String, Collection<String>> permitted = changeInfo.permittedLabels;
        if (permitted != null) {
            for (Map.Entry<String, Collection<String>> label : permitted.entrySet()) {
                String[] values = label.getValue().stream()
                    .map(String::trim)
                    .sorted(Comparator.comparingInt(PublishDialog::vote).reversed())
                    .toArray(String[]::new);
                JComboBox<String> combo = new JComboBox<>(values);
                for (String value : values) {
                    if (vote(value) == 0) combo.setSelectedItem(value);
                }
                votes.put(label.getKey(), combo);
            }
        }
        init();
    }

    static int vote(String value) {
        String trimmed = value.trim();
        return Integer.parseInt(trimmed.startsWith("+") ? trimmed.substring(1) : trimmed);
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, JBUI.scale(8)));
        if (!votes.isEmpty()) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0));
            for (Map.Entry<String, JComboBox<String>> vote : votes.entrySet()) {
                row.add(new JLabel(vote.getKey() + ":"));
                row.add(vote.getValue());
            }
            panel.add(row, BorderLayout.NORTH);
        }
        panel.add(reviewPanel, BorderLayout.CENTER);
        return panel;
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
        return reviewPanel.getPreferrableFocusComponent();
    }

    /**
     * A vote of 0 is left out rather than sent, so that publishing does not take back an earlier vote.
     */
    @NotNull
    ReviewInput createReviewInput() {
        ReviewInput input = new ReviewInput();
        String message = reviewPanel.getMessage();
        if (message != null && !message.trim().isEmpty()) {
            input.message = message;
        }
        for (Map.Entry<String, JComboBox<String>> vote : votes.entrySet()) {
            int value = vote((String) vote.getValue().getSelectedItem());
            if (value != 0) {
                input.label(vote.getKey(), value);
            }
        }
        input.drafts = ReviewInput.DraftHandling.PUBLISH_ALL_REVISIONS;
        if (!reviewPanel.getDoNotify()) {
            input.notify = NotifyHandling.NONE;
        }
        return input;
    }

    boolean isSubmit() {
        return reviewPanel.getSubmitChange();
    }
}
