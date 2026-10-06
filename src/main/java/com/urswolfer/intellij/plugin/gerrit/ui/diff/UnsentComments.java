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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What was typed into a comment's editor in a diff and not saved, so that closing the diff does not lose it. Kept
 * until the IDE closes.
 */
@Service(Service.Level.APP)
public final class UnsentComments {
    private final Map<String, String> texts = new ConcurrentHashMap<>();

    public static UnsentComments getInstance() {
        return ApplicationManager.getApplication().getService(UnsentComments.class);
    }

    @Nullable
    String get(@NotNull String key) {
        return texts.get(key);
    }

    void put(@NotNull String key, @NotNull String text) {
        if (text.trim().isEmpty()) {
            texts.remove(key);
        } else {
            texts.put(key, text);
        }
    }

    void remove(@NotNull String key) {
        texts.remove(key);
    }

    /**
     * @return the keys which start with the prefix, without it
     */
    @NotNull
    List<String> suffixesOf(@NotNull String prefix) {
        List<String> suffixes = new ArrayList<>();
        for (String key : texts.keySet()) {
            if (key.startsWith(prefix)) {
                suffixes.add(key.substring(prefix.length()));
            }
        }
        return suffixes;
    }
}
