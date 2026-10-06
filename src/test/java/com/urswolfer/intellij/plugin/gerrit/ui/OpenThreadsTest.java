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
import com.google.gerrit.extensions.common.CommentInfo;
import org.junit.Assert;
import org.testng.annotations.Test;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class OpenThreadsTest {

    @Test
    public void testOnlyUnresolvedThreadsByFileAndLine() {
        Map<String, List<CommentInfo>> comments = new LinkedHashMap<>();
        comments.put("b.txt", Arrays.asList(comment("b1", null, 7, true, 1)));
        comments.put("a.txt", Arrays.asList(
            comment("a2", null, 9, true, 1),
            comment("a1", null, 3, true, 1),
            comment("done", null, 1, true, 1),
            comment("reply", "done", 1, false, 2)));

        List<OpenThreads.Entry> entries = OpenThreads.collect(new ChangeInfo(), comments);

        Assert.assertEquals(Arrays.asList("a.txt:3", "a.txt:9", "b.txt:7"),
            entries.stream().map(entry -> entry.getPath() + ":" + entry.getThread().getLine())
                .collect(Collectors.toList()));
    }

    @Test
    public void testThreadEndingInAnUnresolvedDraftIsOpen() {
        Map<String, List<CommentInfo>> comments = new LinkedHashMap<>();
        CommentInfo draft = comment("d", "r", 4, true, 2);
        draft.author = null;
        comments.put("a.txt", Arrays.asList(comment("r", null, 4, false, 1), draft));

        Assert.assertEquals(1, OpenThreads.collect(new ChangeInfo(), comments).size());
    }

    private static CommentInfo comment(String id, String inReplyTo, int line, boolean unresolved, long time) {
        CommentInfo comment = new CommentInfo();
        comment.id = id;
        comment.inReplyTo = inReplyTo;
        comment.line = line;
        comment.unresolved = unresolved;
        comment.updated = new Timestamp(time);
        comment.author = new AccountInfo(1);
        return comment;
    }
}
