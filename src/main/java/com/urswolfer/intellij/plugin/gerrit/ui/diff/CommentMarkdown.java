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

import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Image;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Comments as Gerrit's web UI shows them: Markdown, with GitHub's tables, strikethrough and bare links.
 */
final class CommentMarkdown {
    private static final List<Extension> EXTENSIONS = Arrays.asList(
        AutolinkExtension.create(), StrikethroughExtension.create(), TablesExtension.create());

    private static final Parser PARSER = Parser.builder().extensions(EXTENSIONS).build();

    // A newline in a comment is a line break, as in Gerrit: most comments are written as plain text.
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder()
        .extensions(EXTENSIONS)
        .escapeHtml(true)
        .sanitizeUrls(true)
        .softbreak("<br>")
        .nodeRendererFactory(ImageAsLink::new)
        .build();

    private CommentMarkdown() {}

    @NotNull
    static String toHtml(@NotNull String markdown) {
        return RENDERER.render(PARSER.parse(markdown));
    }

    /**
     * A Swing text pane loads an image from its URL while painting, so an image is shown as a link to it.
     */
    private static final class ImageAsLink implements NodeRenderer {
        private final HtmlNodeRendererContext context;

        ImageAsLink(HtmlNodeRendererContext context) {
            this.context = context;
        }

        @Override
        public Set<Class<? extends Node>> getNodeTypes() {
            return Collections.singleton(Image.class);
        }

        @Override
        public void render(Node node) {
            String url = context.urlSanitizer().sanitizeLinkUrl(((Image) node).getDestination());
            context.getWriter().tag("a", Collections.singletonMap("href", context.encodeUrl(url)));
            for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
                context.render(child);
            }
            context.getWriter().tag("/a");
        }
    }
}
