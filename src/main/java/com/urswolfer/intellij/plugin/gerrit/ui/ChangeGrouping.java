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

import com.urswolfer.intellij.plugin.gerrit.GerritBundle;

/**
 * How the change list groups its changes. Stored by name in the settings.
 */
public enum ChangeGrouping {
    NONE("group.none"),
    STACK("group.stack"),
    TOPIC("group.topic"),
    ISSUE("group.issue"),
    HASHTAG("group.hashtag"),
    OWNER("group.owner");

    private final String labelKey;

    ChangeGrouping(String labelKey) {
        this.labelKey = labelKey;
    }

    public String getLabel() {
        return GerritBundle.message(labelKey);
    }
}
