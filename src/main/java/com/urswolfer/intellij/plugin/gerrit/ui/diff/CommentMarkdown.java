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

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.util.ui.UIUtil;
import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.html.HtmlWriter;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Comments as Gerrit's web UI shows them: Markdown, with GitHub's tables, strikethrough and bare links.
 */
public final class CommentMarkdown {
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
        .nodeRendererFactory(CodeBlockLines::new)
        .build();

    private CommentMarkdown() {}

    @NotNull
    public static String toHtml(@NotNull String markdown) {
        return RENDERER.render(PARSER.parse(markdown));
    }

    /**
     * The style sheet for the rendered HTML, built per use, so that it follows a change of theme or editor font.
     */
    @NotNull
    public static String style() {
        return "p { margin-top: 0; margin-bottom: 4px; }"
            + " code, tt { font-family: " + EditorColorsManager.getInstance().getGlobalScheme().getEditorFontName() + "; }"
            + " blockquote { color: " + hex(UIUtil.getContextHelpForeground()) + "; margin: 0 0 4px 8px; }"
            + " ul, ol { margin-top: 0; margin-bottom: 4px; }";
    }

    private static String hex(Color color) {
        return String.format("#%06x", color.getRGB() & 0xffffff);
    }

    /**
     * A block of code as its lines in a {@code pre}, a {@code <br />} after each, as {@code TextToHtml} writes code:
     * Swing never wraps a {@code pre}, so the thread under a line turns those lines into ones which wrap, see
     * {@link CommentThreadPanel#wrapping}.
     */
    private static final class CodeBlockLines implements NodeRenderer {
        private final HtmlNodeRendererContext context;

        CodeBlockLines(HtmlNodeRendererContext context) {
            this.context = context;
        }

        @Override
        public Set<Class<? extends Node>> getNodeTypes() {
            return new HashSet<>(Arrays.asList(FencedCodeBlock.class, IndentedCodeBlock.class));
        }

        @Override
        public void render(Node node) {
            String literal = node instanceof FencedCodeBlock
                ? ((FencedCodeBlock) node).getLiteral()
                : ((IndentedCodeBlock) node).getLiteral();
            if (literal.endsWith("\n")) {
                literal = literal.substring(0, literal.length() - 1);
            }
            HtmlWriter writer = context.getWriter();
            writer.line();
            writer.tag("pre");
            for (String line : literal.split("\n", -1)) {
                writer.text(line);
                writer.tag("br", Collections.emptyMap(), true);
            }
            writer.tag("/pre");
            writer.line();
        }
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
