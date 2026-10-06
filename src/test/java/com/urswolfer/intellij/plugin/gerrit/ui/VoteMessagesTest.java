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
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import org.junit.Assert;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;

public class VoteMessagesTest {
    private static final AccountInfo CI = new AccountInfo(1000002);

    @Test
    public void testTheVotersLatestMessageOnThePatchSetWithItsLink() {
        VoteMessages.Result result = VoteMessages.find(Arrays.asList(
            message(CI, 1, "Patch Set 1: Verified-1\n\nverify #89: Tests failed: 2 https://ci.example.org/build/4240"),
            message(new AccountInfo(7), 2, "Patch Set 2: Code-Review+2"),
            message(CI, 2, "Patch Set 2: Verified+1\n\nverify #90: Tests passed: 2509 https://ci.example.org/build/4241.")
        ), CI, 2);

        Assert.assertEquals("verify #90: Tests passed: 2509 https://ci.example.org/build/4241.", result.text);
        Assert.assertEquals("https://ci.example.org/build/4241", result.url);
    }

    @Test
    public void testAMessageOfAnotherPatchSetServesWhenThereIsNone() {
        VoteMessages.Result result = VoteMessages.find(Collections.singletonList(
            message(CI, 1, "Patch Set 1: Verified+1\n\nok")), CI, 2);

        Assert.assertEquals("ok", result.text);
        Assert.assertNull(result.url);
    }

    @Test
    public void testNoMessageOfTheVoter() {
        Assert.assertNull(VoteMessages.find(Collections.singletonList(
            message(new AccountInfo(7), 1, "Patch Set 1: Code-Review+1")), CI, 1));
    }

    private static ChangeMessageInfo message(AccountInfo author, int patchSet, String text) {
        ChangeMessageInfo message = new ChangeMessageInfo();
        message.author = author;
        message._revisionNumber = patchSet;
        message.message = text;
        return message;
    }
}
