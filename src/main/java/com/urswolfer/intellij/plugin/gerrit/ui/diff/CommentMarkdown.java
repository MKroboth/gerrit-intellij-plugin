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
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.html.HtmlWriter;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        .build();

    private CommentMarkdown() {}

    /**
     * The style sheet for the rendered HTML, built per use, so that it follows a change of theme or editor font.
     */
    @NotNull
    public static String style() {
        EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
        return "p { margin-top: 0; margin-bottom: 4px; }"
            + " code, pre { font-family: " + scheme.getEditorFontName() + "; }"
            + " pre { background-color: " + hex(scheme.getDefaultBackground()) + "; padding: 4px; margin: 2px 0 6px 0; }"
            + " blockquote { color: " + hex(UIUtil.getContextHelpForeground()) + "; margin: 0 0 4px 8px; }"
            + " ul, ol { margin-top: 0; margin-bottom: 4px; }";
    }

    private static String hex(Color color) {
        return String.format("#%06x", color.getRGB() & 0xffffff);
    }

    /**
     * A Gerrit "commentlink": text matching the pattern becomes a link built from the template, "$1" standing for its
     * first group.
     */
    public static final class Link {
        final Pattern pattern;
        final String template;

        public Link(@NotNull String match, @NotNull String template) {
            this.pattern = Pattern.compile(match);
            this.template = template;
        }
    }

    @NotNull
    public static String toHtml(@NotNull String markdown) {
        return RENDERER.render(PARSER.parse(markdown));
    }

    @NotNull
    public static String toHtml(@NotNull String markdown, @NotNull List<Link> links) {
        if (links.isEmpty()) {
            return toHtml(markdown);
        }
        HtmlRenderer renderer = HtmlRenderer.builder()
            .extensions(EXTENSIONS)
            .escapeHtml(true)
            .sanitizeUrls(true)
            .softbreak("<br>")
            .nodeRendererFactory(ImageAsLink::new)
            .nodeRendererFactory(context -> new LinkedText(context, links))
            .build();
        return renderer.render(PARSER.parse(markdown));
    }

    /**
     * Text with what the comment links match made into links; not inside a link, and code is not text.
     */
    private static final class LinkedText implements NodeRenderer {
        private static final Pattern GROUP = Pattern.compile("\\$(\\d)");

        private final HtmlNodeRendererContext context;
        private final List<Link> links;

        LinkedText(HtmlNodeRendererContext context, List<Link> links) {
            this.context = context;
            this.links = links;
        }

        @Override
        public Set<Class<? extends Node>> getNodeTypes() {
            return Collections.singleton(Text.class);
        }

        @Override
        public void render(Node node) {
            String literal = ((Text) node).getLiteral();
            HtmlWriter writer = context.getWriter();
            if (insideLink(node)) {
                writer.text(literal);
                return;
            }
            int position = 0;
            while (true) {
                Matcher first = null;
                Link firstLink = null;
                for (Link link : links) {
                    Matcher matcher = link.pattern.matcher(literal);
                    if (matcher.find(position) && matcher.end() > matcher.start()
                        && (first == null || matcher.start() < first.start())) {
                        first = matcher;
                        firstLink = link;
                    }
                }
                if (first == null) {
                    break;
                }
                writer.text(literal.substring(position, first.start()));
                String url = context.urlSanitizer().sanitizeLinkUrl(expand(firstLink.template, first));
                writer.tag("a", Collections.singletonMap("href", context.encodeUrl(url)));
                writer.text(first.group());
                writer.tag("/a");
                position = first.end();
            }
            writer.text(literal.substring(position));
        }

        private static boolean insideLink(Node node) {
            for (Node parent = node.getParent(); parent != null; parent = parent.getParent()) {
                if (parent instanceof org.commonmark.node.Link) return true;
            }
            return false;
        }

        private static String expand(String template, Matcher match) {
            Matcher group = GROUP.matcher(template);
            StringBuilder url = new StringBuilder();
            int position = 0;
            while (group.find()) {
                int number = Integer.parseInt(group.group(1));
                url.append(template, position, group.start());
                if (number <= match.groupCount() && match.group(number) != null) {
                    url.append(match.group(number));
                }
                position = group.end();
            }
            return url.append(template.substring(position)).toString();
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
