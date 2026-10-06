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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * A thread to scroll to in the next diff which shows it, asked for where the diff is opened from, before the diff
 * exists. Used on the event dispatch thread only.
 */
public final class ThreadReveal {
    @Nullable
    private static String changeId;
    @Nullable
    private static String path;
    @Nullable
    private static String rootId;

    private ThreadReveal() {}

    public static void request(@NotNull String changeId, @NotNull String path, @NotNull String rootId) {
        ThreadReveal.changeId = changeId;
        ThreadReveal.path = path;
        ThreadReveal.rootId = rootId;
    }

    /**
     * @return the root of the thread asked for in this file of this change, which is then no longer asked for
     */
    @Nullable
    static String take(@NotNull String changeId, @NotNull String path) {
        if (!Objects.equals(ThreadReveal.changeId, changeId) || !Objects.equals(ThreadReveal.path, path)) {
            return null;
        }
        return rootId;
    }

    static void done() {
        changeId = null;
        path = null;
        rootId = null;
    }
}
