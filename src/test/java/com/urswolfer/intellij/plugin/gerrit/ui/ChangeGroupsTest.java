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
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.common.LabelInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import org.junit.Assert;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

public class ChangeGroupsTest {

    @Test
    public void testStackIsListedTopFirstWithPositions() {
        ChangeInfo base = change(1, "base", null, "a1");
        ChangeInfo middle = change(2, "middle", null, "b1", "a1");
        ChangeInfo top = change(3, "top", null, "c1", "b1");

        List<ChangeGroups.Group> groups = ChangeGroups.byStack(Arrays.asList(middle, base, top));

        Assert.assertEquals(1, groups.size());
        ChangeGroups.Group stack = groups.get(0);
        Assert.assertEquals(Arrays.asList(3, 2, 1), numbers(stack));
        Assert.assertEquals("3/3", stack.positionOf(top));
        Assert.assertEquals("1/3", stack.positionOf(base));
    }

    @Test
    public void testChangeOnAnOlderPatchSetOfItsParentStaysInTheStack() {
        ChangeInfo parent = change(1, "parent", null, "a2");
        parent.revisions.put("a1", revision(1, "a1", "root"));
        ChangeInfo child = change(2, "child", null, "b1", "a1");

        List<ChangeGroups.Group> groups = ChangeGroups.byStack(Arrays.asList(child, parent));

        Assert.assertEquals(Arrays.asList(2, 1), numbers(groups.get(0)));
    }

    @Test
    public void testChangesOutsideAStackComeLast() {
        ChangeInfo alone = change(9, "alone", null, "z1", "elsewhere");
        ChangeInfo base = change(1, "base", null, "a1");
        ChangeInfo top = change(2, "top", null, "b1", "a1");

        List<ChangeGroups.Group> groups = ChangeGroups.byStack(Arrays.asList(alone, top, base));

        Assert.assertEquals(2, groups.size());
        Assert.assertEquals(Arrays.asList(2, 1), numbers(groups.get(0)));
        Assert.assertEquals(Collections.singletonList(9), numbers(groups.get(1)));
        Assert.assertEquals("Not in a stack · 1 change", groups.get(1).getTitle());
        Assert.assertNull(groups.get(1).positionOf(alone));
    }

    @Test
    public void testStackIsNamedAfterItsTopicOrItsBase() {
        ChangeInfo base = change(1, "base subject", "RL-132", "a1");
        ChangeInfo top = change(2, "top subject", "RL-132", "b1", "a1");
        Assert.assertEquals("RL-132 · stack of 2", ChangeGroups.byStack(Arrays.asList(top, base)).get(0).getTitle());

        top.topic = "other";
        Assert.assertEquals("base subject · stack of 2", ChangeGroups.byStack(Arrays.asList(top, base)).get(0).getTitle());
    }

    @Test
    public void testStackKeyStaysWhenTheStackGrows() {
        ChangeInfo base = change(1, "base", null, "a1");
        ChangeInfo top = change(2, "top", null, "b1", "a1");
        String key = ChangeGroups.byStack(Arrays.asList(top, base)).get(0).getKey();

        ChangeInfo newTop = change(3, "new top", null, "c1", "b1");

        Assert.assertEquals(key, ChangeGroups.byStack(Arrays.asList(newTop, top, base)).get(0).getKey());
    }

    @Test
    public void testChangeWithoutCommitIsNotInAStack() {
        ChangeInfo bare = change(1, "bare", null, "a1");
        bare.revisions.get("a1").commit = null;

        List<ChangeGroups.Group> groups = ChangeGroups.byStack(Collections.singletonList(bare));

        Assert.assertEquals("Not in a stack · 1 change", groups.get(0).getTitle());
    }

    @Test
    public void testTopicsInOrderOfAppearanceAndTheRestLast() {
        ChangeInfo noTopic = change(5, "none", null, "e1");
        ChangeInfo first = change(1, "first", "beta", "a1");
        ChangeInfo second = change(2, "second", "alpha", "b1");
        ChangeInfo third = change(3, "third", "beta", "c1");

        List<ChangeGroups.Group> groups = ChangeGroups.byTopic(Arrays.asList(noTopic, first, second, third));

        Assert.assertEquals(Arrays.asList("beta · 2 changes", "alpha · 1 change", "No topic · 1 change"),
            groups.stream().map(ChangeGroups.Group::getTitle).collect(Collectors.toList()));
        Assert.assertEquals(Arrays.asList(1, 3), numbers(groups.get(0)));
    }

    @Test
    public void testTopicKeepsItsStackInOrder() {
        ChangeInfo base = change(1, "base", "RL-132", "a1");
        ChangeInfo top = change(2, "top", "RL-132", "b1", "a1");

        Assert.assertEquals(Arrays.asList(2, 1), numbers(ChangeGroups.byTopic(Arrays.asList(base, top)).get(0)));
    }

    @Test
    public void testIssueComesFromTheCommitMessageTrailer() {
        ChangeInfo first = change(1, "first", null, "a1");
        first.revisions.get("a1").commit.message = "first\n\nBody.\n\nIssue: RL-132\nChange-Id: I1\n";
        ChangeInfo second = change(2, "second", null, "b1");
        second.revisions.get("b1").commit.message = "second\n\nIssue: RL-132\n";
        ChangeInfo none = change(3, "none", null, "c1");
        none.revisions.get("c1").commit.message = "none\n\nNo trailer here.\n";

        List<ChangeGroups.Group> groups = ChangeGroups.byIssue(Arrays.asList(first, none, second));

        Assert.assertEquals(Arrays.asList("RL-132 · 2 changes", "No issue · 1 change"),
            groups.stream().map(ChangeGroups.Group::getTitle).collect(Collectors.toList()));
    }

    @Test
    public void testIssueInTheBodyIsNotATrailer() {
        ChangeInfo change = change(1, "subject", null, "a1");
        change.revisions.get("a1").commit.message = "subject\n\nSee Issue: RL-1 for why.\n";

        Assert.assertEquals("No issue · 1 change", ChangeGroups.byIssue(Collections.singletonList(change)).get(0).getTitle());
    }

    @Test
    public void testHashtagIsTheFirstInOrder() {
        ChangeInfo change = change(1, "subject", null, "a1");
        change.hashtags = Arrays.asList("zeta", "alpha");
        ChangeInfo untagged = change(2, "untagged", null, "b1");

        List<ChangeGroups.Group> groups = ChangeGroups.byHashtag(Arrays.asList(change, untagged));

        Assert.assertEquals(Arrays.asList("#alpha · 1 change", "No hashtag · 1 change"),
            groups.stream().map(ChangeGroups.Group::getTitle).collect(Collectors.toList()));
    }

    @Test
    public void testOwnerIsTheOwnersName() {
        ChangeInfo rita = change(1, "one", null, "a1");
        rita.owner = new AccountInfo("Rita Reviewer", "rita@example.org");
        ChangeInfo max = change(2, "two", null, "b1");
        max.owner = new AccountInfo("Max", "max@example.org");

        Assert.assertEquals(Arrays.asList("Rita Reviewer · 1 change", "Max · 1 change"),
            ChangeGroups.byOwner(Arrays.asList(rita, max)).stream().map(ChangeGroups.Group::getTitle)
                .collect(Collectors.toList()));
    }

    @Test
    public void testStatusCountsApprovalsFailuresAndOpenThreads() {
        ChangeInfo approved = change(1, "approved", null, "a1");
        approved.labels = Collections.singletonMap("Code-Review", label(true, false));
        ChangeInfo failing = change(2, "failing", null, "b1");
        failing.labels = Collections.singletonMap("Verified", label(false, true));
        failing.unresolvedCommentCount = 2;
        ChangeInfo plain = change(3, "plain", null, "c1");
        plain.unresolvedCommentCount = 1;

        Assert.assertEquals("1/3 approved · 1 failing · 3 open threads",
            ChangeGroups.statusOf(Arrays.asList(approved, failing, plain)));
    }

    @Test
    public void testStatusLeavesOutWhatIsZero() {
        Assert.assertEquals("0/1 approved", ChangeGroups.statusOf(Collections.singletonList(change(1, "x", null, "a1"))));
    }

    private static LabelInfo label(boolean approved, boolean rejected) {
        LabelInfo label = new LabelInfo();
        label.approved = approved ? new AccountInfo(1) : null;
        label.rejected = rejected ? new AccountInfo(1) : null;
        return label;
    }

    private static List<Integer> numbers(ChangeGroups.Group group) {
        return group.getChanges().stream().map(change -> change._number).collect(Collectors.toList());
    }

    private static ChangeInfo change(int number, String subject, String topic, String revision, String... parents) {
        ChangeInfo change = new ChangeInfo();
        change.id = "change-" + number;
        change._number = number;
        change.subject = subject;
        change.topic = topic;
        change.currentRevision = revision;
        change.revisions = new LinkedHashMap<>();
        change.revisions.put(revision, revision(2, revision, parents));
        return change;
    }

    private static RevisionInfo revision(int number, String sha, String... parents) {
        RevisionInfo revision = new RevisionInfo();
        revision._number = number;
        revision.commit = new CommitInfo();
        revision.commit.commit = sha;
        revision.commit.parents = Arrays.stream(parents).map(parent -> {
            CommitInfo commit = new CommitInfo();
            commit.commit = parent;
            return commit;
        }).collect(Collectors.toList());
        return revision;
    }
}
