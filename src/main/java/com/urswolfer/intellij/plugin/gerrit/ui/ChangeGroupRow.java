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

import com.google.gerrit.extensions.common.ChangeInfo;
import org.jetbrains.annotations.NotNull;

/**
 * The row heading a group in the change list. It is a {@link ChangeInfo} only so that the list's table can hold it;
 * the table never selects it, so no action sees one.
 */
public final class ChangeGroupRow extends ChangeInfo {
    private final ChangeGroups.Group group;
    private final boolean collapsed;

    ChangeGroupRow(@NotNull ChangeGroups.Group group, boolean collapsed) {
        this.group = group;
        this.collapsed = collapsed;
    }

    @NotNull
    ChangeGroups.Group getGroup() {
        return group;
    }

    boolean isCollapsed() {
        return collapsed;
    }
}
