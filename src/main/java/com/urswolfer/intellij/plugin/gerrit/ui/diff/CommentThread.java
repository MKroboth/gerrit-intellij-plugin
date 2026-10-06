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
import com.google.gerrit.extensions.common.CommentInfo;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.lang.Boolean.TRUE;

/**
 * A comment and the replies to it, as Gerrit threads them through {@code in_reply_to}.
 */
public final class CommentThread {

    private static final Comparator<Comment> BY_TIME = Comparator.comparing(
        (Comment comment) -> comment.updated, Comparator.nullsLast(Comparator.naturalOrder()));

    private static final Comparator<CommentThread> BY_LINE = Comparator
        .comparingInt((CommentThread thread) -> thread.getLine())
        .thenComparing(CommentThread::getRoot, BY_TIME);

    private final List<CommentInfo> comments;

    private CommentThread(List<CommentInfo> comments) {
        this.comments = Collections.unmodifiableList(comments);
    }

    @NotNull
    public static List<CommentThread> group(@NotNull Collection<CommentInfo> comments) {
        Map<String, CommentInfo> byId = new HashMap<>();
        for (CommentInfo comment : comments) {
            byId.put(comment.id, comment);
        }

        List<CommentInfo> sorted = new ArrayList<>(comments);
        sorted.sort(BY_TIME);
        Map<CommentInfo, List<CommentInfo>> byRoot = new LinkedHashMap<>();
        for (CommentInfo comment : sorted) {
            CommentInfo root = rootOf(comment, byId);
            byRoot.computeIfAbsent(root, key -> new ArrayList<>()).add(comment);
        }

        List<CommentThread> threads = new ArrayList<>();
        for (Map.Entry<CommentInfo, List<CommentInfo>> entry : byRoot.entrySet()) {
            List<CommentInfo> threadComments = entry.getValue();
            // a reply can be older than its root when clocks disagree
            threadComments.remove(entry.getKey());
            threadComments.add(0, entry.getKey());
            threads.add(new CommentThread(threadComments));
        }
        threads.sort(BY_LINE);
        return threads;
    }

    private static CommentInfo rootOf(CommentInfo comment, Map<String, CommentInfo> byId) {
        Set<String> seen = new HashSet<>();
        CommentInfo current = comment;
        while (current.inReplyTo != null && seen.add(current.id)) {
            CommentInfo parent = byId.get(current.inReplyTo);
            if (parent == null) {
                break;
            }
            current = parent;
        }
        return current;
    }

    /**
     * Gerrit sends drafts without an author; they are always the current user's.
     */
    public static boolean isDraft(@NotNull Comment comment) {
        return !(comment instanceof CommentInfo) || ((CommentInfo) comment).author == null;
    }

    @NotNull
    public CommentInfo getRoot() {
        return comments.get(0);
    }

    @NotNull
    public CommentInfo getLast() {
        return comments.get(comments.size() - 1);
    }

    @NotNull
    public List<CommentInfo> getComments() {
        return comments;
    }

    /**
     * The 1-based line the thread is on, 0 for a comment on the whole file.
     */
    public int getLine() {
        Integer line = getRoot().line;
        return line != null ? line : 0;
    }

    public boolean isResolved() {
        return !TRUE.equals(getLast().unresolved);
    }
}
