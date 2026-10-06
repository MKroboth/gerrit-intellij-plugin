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
import com.google.gerrit.extensions.common.CommentInfo;
import com.urswolfer.intellij.plugin.gerrit.ui.diff.CommentThread;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The threads of a change which wait for an answer: those whose last comment, a draft included, is unresolved.
 */
public final class OpenThreads {

    private OpenThreads() {}

    public static final class Entry {
        private final ChangeInfo change;
        private final String path;
        private final CommentThread thread;

        Entry(ChangeInfo change, String path, CommentThread thread) {
            this.change = change;
            this.path = path;
            this.thread = thread;
        }

        @NotNull
        public ChangeInfo getChange() {
            return change;
        }

        /**
         * As Gerrit names it; "/PATCHSET_LEVEL" for a thread on the patch set rather than on a file.
         */
        @NotNull
        public String getPath() {
            return path;
        }

        @NotNull
        public CommentThread getThread() {
            return thread;
        }
    }

    /**
     * @param comments the comments and drafts of every patch set of the change, by file
     */
    @NotNull
    public static List<Entry> collect(@NotNull ChangeInfo change, @NotNull Map<String, List<CommentInfo>> comments) {
        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<String, List<CommentInfo>> file : comments.entrySet()) {
            for (CommentThread thread : CommentThread.group(file.getValue())) {
                if (!thread.isResolved()) {
                    entries.add(new Entry(change, file.getKey(), thread));
                }
            }
        }
        entries.sort(Comparator.comparing(Entry::getPath).thenComparingInt(entry -> entry.thread.getLine()));
        return entries;
    }
}
