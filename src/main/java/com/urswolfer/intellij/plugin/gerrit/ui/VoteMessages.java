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

import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.intellij.openapi.project.Project;
import com.urswolfer.intellij.plugin.gerrit.rest.GerritUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a vote's voter wrote with it, as a CI server writes its build's result and link: loaded for a change the first
 * time it is asked for, and kept for its current patch set.
 */
final class VoteMessages {
    private static final Pattern URL = Pattern.compile("https?://[^\\s<>\"]+");

    static final class Result {
        final String text;
        @Nullable
        final String url;

        Result(String text, @Nullable String url) {
            this.text = text;
            this.url = url;
        }
    }

    private final GerritUtil gerritUtil = GerritUtil.getInstance();
    private final Project project;
    private final Runnable loaded;
    private final Map<String, List<ChangeMessageInfo>> messages = new HashMap<>();
    private final Set<String> loading = new HashSet<>();

    /**
     * @param loaded run when a change's messages arrived, so that what shows them can ask again
     */
    VoteMessages(@NotNull Project project, @NotNull Runnable loaded) {
        this.project = project;
        this.loaded = loaded;
    }

    /**
     * @return null while the change's messages load, which this starts
     */
    @Nullable
    Result get(@NotNull ChangeInfo change, @NotNull AccountInfo voter) {
        String key = change.id + ':' + change.currentRevision;
        List<ChangeMessageInfo> known = messages.get(key);
        if (known == null) {
            if (loading.add(key)) {
                gerritUtil.getChangeMessages(change._number, project, list -> {
                    loading.remove(key);
                    messages.put(key, list);
                    loaded.run();
                });
            }
            return null;
        }
        return find(known, voter, patchSetOf(change));
    }

    /**
     * Passes the result on once the change's messages are there; nothing when the voter wrote none.
     */
    void whenLoaded(@NotNull ChangeInfo change, @NotNull AccountInfo voter, @NotNull Consumer<Result> consumer) {
        Result result = get(change, voter);
        if (result != null) {
            consumer.accept(result);
            return;
        }
        if (isLoaded(change)) {
            return;
        }
        gerritUtil.getChangeMessages(change._number, project, list -> {
            messages.put(change.id + ':' + change.currentRevision, list);
            Result loadedResult = find(list, voter, patchSetOf(change));
            if (loadedResult != null) {
                consumer.accept(loadedResult);
            }
        });
    }

    boolean isLoaded(@NotNull ChangeInfo change) {
        return messages.containsKey(change.id + ':' + change.currentRevision);
    }

    private static int patchSetOf(ChangeInfo change) {
        RevisionInfo revision = change.revisions != null ? change.revisions.get(change.currentRevision) : null;
        return revision != null ? revision._number : 0;
    }

    /**
     * The voter's latest message on the patch set, or on any when there is none, without the "Patch Set n: ..." line
     * Gerrit puts first, and the first link in it.
     */
    @Nullable
    static Result find(@NotNull List<ChangeMessageInfo> messages, @NotNull AccountInfo voter, int patchSet) {
        ChangeMessageInfo onPatchSet = null;
        ChangeMessageInfo any = null;
        for (ChangeMessageInfo message : messages) {
            if (message.author == null || !Objects.equals(message.author._accountId, voter._accountId)) {
                continue;
            }
            any = message;
            if (message._revisionNumber != null && message._revisionNumber == patchSet) {
                onPatchSet = message;
            }
        }
        ChangeMessageInfo chosen = onPatchSet != null ? onPatchSet : any;
        if (chosen == null || chosen.message == null) {
            return null;
        }
        String text = chosen.message.trim();
        if (text.startsWith("Patch Set")) {
            int lineEnd = text.indexOf('\n');
            text = lineEnd >= 0 ? text.substring(lineEnd + 1).trim() : "";
        }
        Matcher matcher = URL.matcher(text);
        String url = matcher.find() ? matcher.group().replaceAll("[.,;:)]+$", "") : null;
        return new Result(text, url);
    }
}
