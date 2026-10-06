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

import org.junit.Assert;
import org.testng.annotations.Test;

public class CommentMarkdownTest {

    @Test
    public void testEmphasisAndCodeAreRendered() {
        String html = CommentMarkdown.toHtml("**Division by zero:** `image_height` is not checked.");

        Assert.assertTrue(html, html.contains("<strong>Division by zero:</strong>"));
        Assert.assertTrue(html, html.contains("<code>image_height</code>"));
    }

    @Test
    public void testListsAndCodeBlocksAreRendered() {
        String html = CommentMarkdown.toHtml("Two things:\n\n- one\n- two\n\n```\nint x;\n```");

        Assert.assertTrue(html, html.contains("<li>one</li>"));
        Assert.assertTrue(html, html.contains("<pre><code>int x;"));
    }

    @Test
    public void testLineBreaksOfPlainTextAreKept() {
        Assert.assertTrue(CommentMarkdown.toHtml("first\nsecond").contains("first<br"));
    }

    @Test
    public void testHtmlInACommentIsShownAsText() {
        String html = CommentMarkdown.toHtml("<b>bold</b> <script>alert(1)</script>");

        Assert.assertFalse(html, html.contains("<b>"));
        Assert.assertFalse(html, html.contains("<script>"));
        Assert.assertTrue(html, html.contains("&lt;b&gt;"));
    }

    @Test
    public void testLinksAreKeptAndBareUrlsLinked() {
        String html = CommentMarkdown.toHtml("[docs](https://example.org/a) and https://example.org/b");

        Assert.assertTrue(html, html.contains("href=\"https://example.org/a\">docs</a>"));
        Assert.assertTrue(html, html.contains("href=\"https://example.org/b\">https://example.org/b</a>"));
    }

    @Test
    public void testScriptLinksAreDropped() {
        Assert.assertFalse(CommentMarkdown.toHtml("[x](javascript:alert(1))").contains("javascript:"));
    }

    /**
     * The comment pane would load an image from wherever the comment points, on every repaint of the diff.
     */
    @Test
    public void testImagesBecomeLinks() {
        String html = CommentMarkdown.toHtml("![screenshot](https://example.org/s.png)");

        Assert.assertFalse(html, html.contains("<img"));
        Assert.assertTrue(html, html.contains("<a href=\"https://example.org/s.png\">screenshot</a>"));
    }
}
