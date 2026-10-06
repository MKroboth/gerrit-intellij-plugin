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

import com.google.gerrit.extensions.client.Comment;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.urswolfer.intellij.plugin.gerrit.GerritBundle;

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
        private final CommentThreads.Thread<CommentInfo> thread;

        private Entry(ChangeInfo change, String path, CommentThreads.Thread<CommentInfo> thread) {
            this.change = change;
            this.path = path;
            this.thread = thread;
        }

        public ChangeInfo getChange() {
            return change;
        }

        /** As Gerrit names it; "/PATCHSET_LEVEL" for a thread on the patch set rather than on a file. */
        public String getPath() {
            return path;
        }

        /** Counted from 1, 0 for a thread on the file. */
        public int getLine() {
            return thread.root.line != null ? thread.root.line : 0;
        }

        public String getRootId() {
            return thread.root.id;
        }

        public Integer getPatchSet() {
            return thread.root.patchSet;
        }

        /** Of the last comment, which is the user's own where it is a draft. */
        public String getAuthor() {
            Comment last = thread.last();
            return CommentThreads.isDraft(last)
                ? GerritBundle.message("diff.thread.draft") : CommentThreads.authorName(last);
        }

        public String getExcerpt() {
            return CommentThreads.excerpt(thread.last().message);
        }
    }

    /**
     * @param comments the comments and drafts of every patch set of the change, by file
     */
    public static List<Entry> collect(ChangeInfo change, Map<String, List<CommentInfo>> comments) {
        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<String, List<CommentInfo>> file : comments.entrySet()) {
            for (CommentThreads.Thread<CommentInfo> thread : CommentThreads.group(file.getValue(), comment -> comment)) {
                if (thread.isUnresolved()) {
                    entries.add(new Entry(change, file.getKey(), thread));
                }
            }
        }
        entries.sort(Comparator.comparing(Entry::getPath).thenComparingInt(Entry::getLine));
        return entries;
    }
}
