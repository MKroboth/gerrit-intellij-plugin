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
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.FixReplacementInfo;
import com.google.gerrit.extensions.common.FixSuggestionInfo;
import org.junit.Assert;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.List;

public class FixesTest {

    @Test
    public void testSuggestionBlockReplacesTheCommentedLine() {
        CommentInfo comment = comment(2, null, "Use the constant:\n\n```suggestion\nint limit = MAX;\n```\n");

        List<Fixes.Fix> fixes = Fixes.of(comment);

        Assert.assertEquals(1, fixes.size());
        Fixes.Replacement replacement = fixes.get(0).replacements.get(0);
        Assert.assertEquals("a.txt", replacement.path);
        Assert.assertEquals(2, replacement.startLine);
        Assert.assertEquals(0, replacement.startCharacter);
        Assert.assertEquals(3, replacement.endLine);
        Assert.assertEquals(0, replacement.endCharacter);
        Assert.assertEquals("int limit = MAX;\n", replacement.text);
    }

    @Test
    public void testSuggestionBlockOnARangeReplacesItsWholeLines() {
        Comment.Range range = new Comment.Range();
        range.startLine = 3;
        range.startCharacter = 4;
        range.endLine = 5;
        range.endCharacter = 2;
        CommentInfo comment = comment(5, range, "```suggestion\nx\ny\n```");

        Fixes.Replacement replacement = Fixes.of(comment).get(0).replacements.get(0);

        Assert.assertEquals(3, replacement.startLine);
        Assert.assertEquals(6, replacement.endLine);
        Assert.assertEquals("x\ny\n", replacement.text);
    }

    @Test
    public void testFixSuggestionsOfGerritAreTakenAsTheyAre() {
        CommentInfo comment = comment(1, null, "Say which step.");
        FixReplacementInfo replacementInfo = new FixReplacementInfo();
        replacementInfo.path = "b.txt";
        replacementInfo.range = new Comment.Range();
        replacementInfo.range.startLine = 1;
        replacementInfo.range.startCharacter = 5;
        replacementInfo.range.endLine = 1;
        replacementInfo.range.endCharacter = 6;
        replacementInfo.replacement = "three";
        FixSuggestionInfo suggestion = new FixSuggestionInfo();
        suggestion.description = "Name the step";
        suggestion.replacements = Collections.singletonList(replacementInfo);
        comment.fixSuggestions = Collections.singletonList(suggestion);

        Fixes.Fix fix = Fixes.of(comment).get(0);

        Assert.assertEquals("Name the step", fix.description);
        Assert.assertEquals("b.txt", fix.replacements.get(0).path);
        Assert.assertEquals(5, fix.replacements.get(0).startCharacter);
        Assert.assertEquals("three", fix.replacements.get(0).text);
    }

    @Test
    public void testOtherCodeBlocksAreNoFix() {
        Assert.assertTrue(Fixes.of(comment(1, null, "```java\nint x;\n```")).isEmpty());
    }

    @Test
    public void testCommentOnTheBaseIsNoFix() {
        CommentInfo comment = comment(1, null, "```suggestion\nnew\n```");
        comment.side = Side.PARENT;

        Assert.assertTrue(Fixes.of(comment).isEmpty());
    }

    @Test
    public void testReplacementsApplyFromTheEndSoOffsetsHold() {
        Fixes.Replacement first = new Fixes.Replacement("a.txt", 1, 0, 1, 1, "A");
        Fixes.Replacement second = new Fixes.Replacement("a.txt", 2, 0, 2, 1, "B");

        Assert.assertEquals("Ane\nBwo\n", Fixes.apply("one\ntwo\n", java.util.Arrays.asList(first, second)));
    }

    private static CommentInfo comment(int line, Comment.Range range, String message) {
        CommentInfo comment = new CommentInfo();
        comment.path = "a.txt";
        comment.line = line;
        comment.range = range;
        comment.message = message;
        return comment;
    }
}
