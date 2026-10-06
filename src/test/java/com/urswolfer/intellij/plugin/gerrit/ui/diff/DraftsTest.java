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

import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.client.Comment;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.CommentInfo;
import org.junit.Assert;
import org.testng.annotations.Test;

public class DraftsTest {

    @Test
    public void testDoneReplyResolvesTheThread() {
        CommentInfo unresolved = comment(true);
        unresolved.id = "abc";
        unresolved.path = "src/Main.java";
        unresolved.line = 12;
        unresolved.side = Side.PARENT;

        DraftInput reply = Drafts.reply(unresolved, "Done", true);

        Assert.assertEquals(Boolean.FALSE, reply.unresolved);
        Assert.assertEquals("abc", reply.inReplyTo);
        Assert.assertEquals("Done", reply.message);
        Assert.assertEquals("src/Main.java", reply.path);
        Assert.assertEquals(Integer.valueOf(12), reply.line);
        Assert.assertEquals(Side.PARENT, reply.side);
    }

    @Test
    public void testReplyKeepsTheRangeOfItsParent() {
        CommentInfo parent = comment(true);
        parent.range = range(3, 5);

        Assert.assertSame(parent.range, Drafts.reply(parent, "text", false).range);
    }

    @Test
    public void testEditedDraftKeepsItsPlace() {
        CommentInfo draft = comment(true);
        draft.id = "draft";
        draft.inReplyTo = "parent";
        draft.path = "a.txt";
        draft.line = 7;
        draft.range = range(6, 7);

        DraftInput edited = Drafts.edit(draft, "new text", true);

        Assert.assertEquals("draft", edited.id);
        Assert.assertEquals("parent", edited.inReplyTo);
        Assert.assertEquals("a.txt", edited.path);
        Assert.assertEquals(Integer.valueOf(7), edited.line);
        Assert.assertSame(draft.range, edited.range);
        Assert.assertEquals("new text", edited.message);
        Assert.assertEquals(Boolean.FALSE, edited.unresolved);
    }

    @Test
    public void testNewCommentIsOnItsLineAndSide() {
        Comment.Range range = range(2, 4);

        DraftInput comment = Drafts.newComment("a.txt", Side.PARENT, 4, range, "text", false);

        Assert.assertNull(comment.id);
        Assert.assertNull(comment.inReplyTo);
        Assert.assertEquals("a.txt", comment.path);
        Assert.assertEquals(Side.PARENT, comment.side);
        Assert.assertEquals(Integer.valueOf(4), comment.line);
        Assert.assertSame(range, comment.range);
        Assert.assertEquals(Boolean.TRUE, comment.unresolved);
    }

    @Test
    public void testNewCommentStartsUnresolved() {
        Assert.assertFalse(Drafts.isInitiallyResolved(null, null));
    }

    @Test
    public void testReplyKeepsResolvedThreadResolved() {
        Assert.assertTrue(Drafts.isInitiallyResolved(null, comment(false)));
    }

    @Test
    public void testReplyKeepsUnresolvedThreadUnresolved() {
        Assert.assertFalse(Drafts.isInitiallyResolved(null, comment(true)));
    }

    @Test
    public void testEditedDraftKeepsItsOwnState() {
        Assert.assertTrue(Drafts.isInitiallyResolved(comment(false), comment(true)));
        Assert.assertFalse(Drafts.isInitiallyResolved(comment(true), comment(false)));
    }

    private static CommentInfo comment(boolean unresolved) {
        CommentInfo comment = new CommentInfo();
        comment.unresolved = unresolved;
        return comment;
    }

    private static Comment.Range range(int startLine, int endLine) {
        Comment.Range range = new Comment.Range();
        range.startLine = startLine;
        range.endLine = endLine;
        return range;
    }
}
