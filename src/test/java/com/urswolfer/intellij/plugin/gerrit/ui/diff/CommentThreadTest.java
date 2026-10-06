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

import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import org.junit.Assert;
import org.testng.annotations.Test;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class CommentThreadTest {

    @Test
    public void testRepliesJoinTheirRootInTimeOrder() {
        CommentInfo root = comment("a", null, 10, 1);
        CommentInfo second = comment("c", "a", 10, 3);
        CommentInfo first = comment("b", "a", 10, 2);

        List<CommentThread> threads = CommentThread.group(Arrays.asList(second, root, first));

        Assert.assertEquals(1, threads.size());
        Assert.assertEquals(Arrays.asList("a", "b", "c"), ids(threads.get(0)));
    }

    @Test
    public void testReplyToReplyStaysInTheThread() {
        CommentInfo root = comment("a", null, 10, 1);
        CommentInfo reply = comment("b", "a", 10, 2);
        CommentInfo replyToReply = comment("c", "b", 10, 3);

        List<CommentThread> threads = CommentThread.group(Arrays.asList(replyToReply, reply, root));

        Assert.assertEquals(1, threads.size());
        Assert.assertSame(root, threads.get(0).getRoot());
    }

    @Test
    public void testReplyWithoutItsParentStartsAThread() {
        CommentInfo orphan = comment("b", "gone", 10, 2);

        List<CommentThread> threads = CommentThread.group(Collections.singletonList(orphan));

        Assert.assertEquals(1, threads.size());
        Assert.assertSame(orphan, threads.get(0).getRoot());
    }

    @Test
    public void testThreadsAreOrderedByLine() {
        CommentInfo lower = comment("a", null, 20, 1);
        CommentInfo upper = comment("b", null, 5, 2);
        CommentInfo fileComment = comment("c", null, null, 3);

        List<CommentThread> threads = CommentThread.group(Arrays.asList(lower, upper, fileComment));

        Assert.assertEquals(Arrays.asList("c", "b", "a"),
            threads.stream().map(thread -> thread.getRoot().id).collect(Collectors.toList()));
    }

    @Test
    public void testLastCommentDecidesTheState() {
        CommentInfo root = comment("a", null, 10, 1);
        root.unresolved = true;
        CommentInfo done = comment("b", "a", 10, 2);
        done.unresolved = false;

        Assert.assertFalse(CommentThread.group(Collections.singletonList(root)).get(0).isResolved());
        Assert.assertTrue(CommentThread.group(Arrays.asList(root, done)).get(0).isResolved());
    }

    @Test
    public void testCommentWithoutStateIsResolved() {
        Assert.assertTrue(CommentThread.group(Collections.singletonList(comment("a", null, 1, 1))).get(0).isResolved());
    }

    @Test
    public void testDraftHasNoAuthor() {
        CommentInfo published = comment("a", null, 10, 1);
        CommentInfo draft = comment("b", "a", 10, 2);
        draft.author = null;

        Assert.assertFalse(CommentThread.isDraft(published));
        Assert.assertTrue(CommentThread.isDraft(draft));
    }

    @Test
    public void testDraftWithoutTimeComesLast() {
        CommentInfo root = comment("a", null, 10, 1);
        CommentInfo draft = comment("b", "a", 10, 0);
        draft.updated = null;
        CommentInfo reply = comment("c", "a", 10, 2);

        Assert.assertEquals(Arrays.asList("a", "c", "b"), ids(CommentThread.group(Arrays.asList(draft, reply, root)).get(0)));
    }

    private static List<String> ids(CommentThread thread) {
        return thread.getComments().stream().map(comment -> comment.id).collect(Collectors.toList());
    }

    private static CommentInfo comment(String id, String inReplyTo, Integer line, long time) {
        CommentInfo comment = new CommentInfo();
        comment.id = id;
        comment.inReplyTo = inReplyTo;
        comment.line = line;
        comment.updated = new Timestamp(time);
        comment.author = new AccountInfo(1);
        return comment;
    }
}
