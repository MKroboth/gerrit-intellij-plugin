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

import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.EditorNotificationPanel;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.ui.action.SubmitAction;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationBuilder;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationService;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import java.util.List;

/**
 * Above the diff while the change has unpublished drafts: how many, and publishing them with a message and votes.
 */
final class DraftsBar {
    private final GerritUtil gerritUtil = GerritUtil.getInstance();
    private final EditorNotificationPanel panel = new EditorNotificationPanel();
    private final Project project;
    private final ChangeInfo changeInfo;
    private final String revisionId;
    private final Runnable afterPublish;

    DraftsBar(@NotNull Project project, @NotNull ChangeInfo changeInfo, @NotNull String revisionId,
              @NotNull Runnable afterPublish) {
        this.project = project;
        this.changeInfo = changeInfo;
        this.revisionId = revisionId;
        this.afterPublish = afterPublish;
        panel.createActionLabel("Publish…", this::publish);
        panel.setVisible(false);
    }

    @NotNull
    JComponent getComponent() {
        return panel;
    }

    void refresh() {
        if (!GerritSettings.getInstance().isLoginAndPasswordAvailable()) {
            return;
        }
        gerritUtil.getChangeDrafts(changeInfo._number, project, drafts -> {
            int count = drafts.values().stream().mapToInt(List::size).sum();
            panel.setText(count == 1 ? "1 draft not yet published" : count + " drafts not yet published");
            panel.setVisible(count > 0);
        });
    }

    private void publish() {
        PublishDialog dialog = new PublishDialog(project, changeInfo);
        if (!dialog.showAndGet()) {
            return;
        }
        ReviewInput input = dialog.createReviewInput();
        boolean submit = dialog.isSubmit();
        gerritUtil.postReview(changeInfo.id, revisionId, input, project, ignored -> {
            NotificationService.getInstance().notifyInformation(new NotificationBuilder(project, "Review posted",
                String.format("Drafts of '%s' published", StringUtil.escapeXmlEntities(changeInfo.subject)))
                .hideBalloon());
            refresh();
            afterPublish.run();
            if (submit) {
                new SubmitAction().submit(changeInfo, project, null);
            }
        });
    }
}
