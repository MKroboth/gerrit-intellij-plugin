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
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The listed changes grouped by topic, or by the stack their commits form.
 *
 * A stack is found from the listed changes alone: a change whose current commit has the commit of any patch set of
 * another listed change as its parent sits on that change, also when that patch set is no longer the current one.
 */
public final class ChangeGroups {

    private ChangeGroups() {}

    public static final class Group {
        private final String key;
        private final String title;
        private final List<ChangeInfo> changes;
        private final Map<ChangeInfo, String> positions;

        private Group(String key, String title, List<ChangeInfo> changes, Map<ChangeInfo, String> positions) {
            this.key = key;
            this.title = title;
            this.changes = Collections.unmodifiableList(changes);
            this.positions = positions;
        }

        /**
         * Stays the same across reloads, so that a collapsed group stays collapsed.
         */
        @NotNull
        public String getKey() {
            return key;
        }

        @NotNull
        public String getTitle() {
            return title;
        }

        @NotNull
        public List<ChangeInfo> getChanges() {
            return changes;
        }

        /**
         * Where a change of a stack sits, counted from its base: "1/3" for the base of three.
         */
        @Nullable
        public String positionOf(@NotNull ChangeInfo change) {
            return positions.get(change);
        }
    }

    @NotNull
    public static List<Group> byStack(@NotNull List<ChangeInfo> changes) {
        Map<ChangeInfo, ChangeInfo> parents = findParents(changes);
        Map<ChangeInfo, Integer> depths = depths(changes, parents);

        Map<ChangeInfo, List<ChangeInfo>> byBase = new LinkedHashMap<>();
        List<ChangeInfo> alone = new ArrayList<>();
        for (ChangeInfo change : changes) {
            ChangeInfo base = baseOf(change, parents);
            byBase.computeIfAbsent(base, key -> new ArrayList<>()).add(change);
        }

        List<Group> groups = new ArrayList<>();
        for (Map.Entry<ChangeInfo, List<ChangeInfo>> entry : byBase.entrySet()) {
            List<ChangeInfo> members = entry.getValue();
            if (members.size() < 2) {
                alone.addAll(members);
                continue;
            }
            sortTopFirst(members, changes, depths);
            int height = depths.get(members.get(0)) + 1;
            Map<ChangeInfo, String> positions = new IdentityHashMap<>();
            for (ChangeInfo member : members) {
                positions.put(member, (depths.get(member) + 1) + "/" + height);
            }
            ChangeInfo base = entry.getKey();
            String name = sharedTopic(members);
            if (name == null) {
                name = StringUtil.shortenTextWithEllipsis(StringUtil.notNullize(base.subject), 60, 0);
            }
            groups.add(new Group("stack:" + base.id, name + " · stack of " + members.size(), members, positions));
        }
        if (!alone.isEmpty()) {
            groups.add(new Group("stack:", "Not in a stack · " + count(alone.size()), alone, Collections.emptyMap()));
        }
        return groups;
    }

    @NotNull
    public static List<Group> byTopic(@NotNull List<ChangeInfo> changes) {
        Map<ChangeInfo, ChangeInfo> parents = findParents(changes);
        Map<ChangeInfo, Integer> depths = depths(changes, parents);

        Map<String, List<ChangeInfo>> byTopic = new LinkedHashMap<>();
        List<ChangeInfo> withoutTopic = new ArrayList<>();
        for (ChangeInfo change : changes) {
            if (StringUtil.isEmpty(change.topic)) {
                withoutTopic.add(change);
            } else {
                byTopic.computeIfAbsent(change.topic, key -> new ArrayList<>()).add(change);
            }
        }

        List<Group> groups = new ArrayList<>();
        for (Map.Entry<String, List<ChangeInfo>> entry : byTopic.entrySet()) {
            List<ChangeInfo> members = entry.getValue();
            sortTopFirst(members, changes, depths);
            groups.add(new Group("topic:" + entry.getKey(), entry.getKey() + " · " + count(members.size()),
                members, Collections.emptyMap()));
        }
        if (!withoutTopic.isEmpty()) {
            groups.add(new Group("topic:", "No topic · " + count(withoutTopic.size()), withoutTopic,
                Collections.emptyMap()));
        }
        return groups;
    }

    private static Map<ChangeInfo, ChangeInfo> findParents(List<ChangeInfo> changes) {
        Map<String, ChangeInfo> byCommit = new HashMap<>();
        for (ChangeInfo change : changes) {
            if (change.revisions != null) {
                for (String commit : change.revisions.keySet()) {
                    byCommit.put(commit, change);
                }
            }
        }
        Map<ChangeInfo, ChangeInfo> parents = new IdentityHashMap<>();
        for (ChangeInfo change : changes) {
            String parentCommit = parentCommit(change);
            ChangeInfo parent = parentCommit != null ? byCommit.get(parentCommit) : null;
            if (parent != null && parent != change) {
                parents.put(change, parent);
            }
        }
        return parents;
    }

    @Nullable
    private static String parentCommit(ChangeInfo change) {
        if (change.revisions == null || change.currentRevision == null) return null;
        RevisionInfo revision = change.revisions.get(change.currentRevision);
        if (revision == null || revision.commit == null || revision.commit.parents == null
            || revision.commit.parents.isEmpty()) {
            return null;
        }
        CommitInfo parent = revision.commit.parents.get(0);
        return parent != null ? parent.commit : null;
    }

    private static Map<ChangeInfo, Integer> depths(List<ChangeInfo> changes, Map<ChangeInfo, ChangeInfo> parents) {
        Map<ChangeInfo, Integer> depths = new IdentityHashMap<>();
        for (ChangeInfo change : changes) {
            int depth = 0;
            Set<ChangeInfo> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            for (ChangeInfo current = parents.get(change); current != null && seen.add(current);
                 current = parents.get(current)) {
                depth++;
            }
            depths.put(change, depth);
        }
        return depths;
    }

    private static ChangeInfo baseOf(ChangeInfo change, Map<ChangeInfo, ChangeInfo> parents) {
        Set<ChangeInfo> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        ChangeInfo current = change;
        while (parents.containsKey(current) && seen.add(current)) {
            current = parents.get(current);
        }
        return current;
    }

    /**
     * The change furthest from the base first, as Gerrit lists a relation chain; otherwise in the order listed.
     */
    private static void sortTopFirst(List<ChangeInfo> members, List<ChangeInfo> listed,
                                     Map<ChangeInfo, Integer> depths) {
        Map<ChangeInfo, Integer> index = new IdentityHashMap<>();
        for (int i = 0; i < listed.size(); i++) {
            index.put(listed.get(i), i);
        }
        members.sort(Comparator.comparing((ChangeInfo change) -> depths.get(change)).reversed()
            .thenComparing(index::get));
    }

    @Nullable
    private static String sharedTopic(List<ChangeInfo> members) {
        Set<String> topics = new HashSet<>();
        for (ChangeInfo member : members) {
            topics.add(member.topic);
        }
        String topic = topics.size() == 1 ? topics.iterator().next() : null;
        return StringUtil.isEmpty(topic) ? null : Objects.requireNonNull(topic);
    }

    private static String count(int changes) {
        return changes == 1 ? "1 change" : changes + " changes";
    }
}
