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
import com.urswolfer.intellij.plugin.gerrit.GerritBundle;
import com.urswolfer.intellij.plugin.gerrit.GerritProjectAccount;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import com.urswolfer.intellij.plugin.gerrit.ui.action.ActionUtil;
import com.urswolfer.intellij.plugin.gerrit.ui.action.SubmitAction;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationBuilder;
import com.urswolfer.intellij.plugin.gerrit.util.NotificationService;

import javax.swing.*;
import java.util.List;

/**
 * Above the diff while the change has unpublished drafts: how many, and publishing them with a message and votes.
 * The review menu of the change list publishes those of the patch set selected there only, so a reply on a thread
 * of an earlier patch set would stay a draft.
 */
final class DraftsBar {
    private final EditorNotificationPanel panel = new EditorNotificationPanel();
    private final Project project;
    private final ChangeInfo changeInfo;
    private final String revisionId;

    /**
     * @param revisionId the patch set the votes go to
     */
    DraftsBar(Project project, ChangeInfo changeInfo, String revisionId) {
        this.project = project;
        this.changeInfo = changeInfo;
        this.revisionId = revisionId;
        panel.createActionLabel(GerritBundle.message("drafts.bar.publish"), this::publish);
        panel.setVisible(false);
    }

    JComponent getComponent() {
        return panel;
    }

    void refresh() {
        if (!GerritProjectAccount.getInstance(project).isLoginAndPasswordAvailable()) {
            panel.setVisible(false);
            return;
        }
        GerritUtil.getInstance().getChangeDraftsQuietly(changeInfo._number, project, drafts -> {
            int count = drafts.values().stream().mapToInt(List::size).sum();
            panel.setText(GerritBundle.message("drafts.bar.text", count, String.valueOf(count)));
            panel.setVisible(count > 0);
        });
    }

    /** Those of every patch set of the change; the diffs and the list load again, which brings them as published. */
    private void publish() {
        PublishDialog dialog = new PublishDialog(project, changeInfo);
        if (!dialog.showAndGet()) {
            return;
        }
        ReviewInput input = dialog.createReviewInput();
        boolean submit = dialog.isSubmit();
        GerritUtil.getInstance().postReview(changeInfo.id, revisionId, input, project, ignored -> {
            NotificationService.getInstance().notifyInformation(new NotificationBuilder(project,
                GerritBundle.message("review.posted.title"),
                GerritBundle.message("drafts.published", StringUtil.escapeXmlEntities(changeInfo.subject)))
                .hideBalloon());
            ActionUtil.reloadChanges(project);
            if (submit) {
                new SubmitAction().submit(changeInfo, project);
            }
        });
    }
}
