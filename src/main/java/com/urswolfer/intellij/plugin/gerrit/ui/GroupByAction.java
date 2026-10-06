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

package com.urswolfer.intellij.plugin.gerrit.ui;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

/**
 * "Group by" in the toolbar of the change list. Not a filter: it changes how the listed changes are shown, not which.
 */
final class GroupByAction extends BasePopupAction {
    private final GerritChangeListPanel changeListPanel;

    GroupByAction(@NotNull GerritChangeListPanel changeListPanel) {
        super("Group by");
        this.changeListPanel = changeListPanel;
        updateFilterValueLabel(changeListPanel.getGrouping().getLabel());
    }

    @Override
    protected void createActions(Consumer<AnAction> actionConsumer) {
        for (ChangeGrouping grouping : ChangeGrouping.values()) {
            actionConsumer.consume(new DumbAwareAction(grouping.getLabel()) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e) {
                    changeListPanel.setGrouping(grouping);
                    updateFilterValueLabel(grouping.getLabel());
                }
            });
        }
    }
}
