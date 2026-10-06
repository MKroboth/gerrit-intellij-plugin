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
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The edits a comment suggests: Gerrit's fix suggestions, and the "suggestion" code blocks its web UI writes for the
 * lines a comment is on when those are switched off, as they are by default.
 */
final class Fixes {
    private static final Pattern SUGGESTION = Pattern.compile("```suggestion\\r?\\n(.*?)```", Pattern.DOTALL);

    static final class Replacement {
        final String path;
        /** 1-based */
        final int startLine;
        /** 0-based, in the line */
        final int startCharacter;
        final int endLine;
        final int endCharacter;
        final String text;

        Replacement(String path, int startLine, int startCharacter, int endLine, int endCharacter, String text) {
            this.path = path;
            this.startLine = startLine;
            this.startCharacter = startCharacter;
            this.endLine = endLine;
            this.endCharacter = endCharacter;
            this.text = text;
        }
    }

    static final class Fix {
        final String description;
        final List<Replacement> replacements;

        Fix(String description, List<Replacement> replacements) {
            this.description = description;
            this.replacements = replacements;
        }
    }

    private Fixes() {}

    @NotNull
    static List<Fix> of(@NotNull CommentInfo comment) {
        List<Fix> fixes = new ArrayList<>();
        // a comment on the base names lines of the old file, which a fix cannot change
        if (comment.side == Side.PARENT) return fixes;
        if (comment.fixSuggestions != null) {
            for (FixSuggestionInfo suggestion : comment.fixSuggestions) {
                List<Replacement> replacements = new ArrayList<>();
                for (FixReplacementInfo info : suggestion.replacements) {
                    replacements.add(new Replacement(info.path, info.range.startLine, info.range.startCharacter,
                        info.range.endLine, info.range.endCharacter, info.replacement));
                }
                fixes.add(new Fix(suggestion.description != null ? suggestion.description : "Suggested fix",
                    replacements));
            }
        }
        if (comment.message != null && comment.line != null && comment.line > 0) {
            Matcher matcher = SUGGESTION.matcher(comment.message);
            while (matcher.find()) {
                String text = matcher.group(1);
                fixes.add(new Fix("Suggested edit", Collections.singletonList(
                    wholeLines(comment, text.endsWith("\n") ? text : text + "\n"))));
            }
        }
        return fixes;
    }

    /**
     * The lines the comment is on, whole: those of its range, or its line.
     */
    private static Replacement wholeLines(CommentInfo comment, String text) {
        Comment.Range range = comment.range;
        int first = range != null ? range.startLine : comment.line;
        int last = range == null ? comment.line
            : range.endCharacter == 0 && range.endLine > range.startLine ? range.endLine - 1 : range.endLine;
        return new Replacement(comment.path, first, 0, last + 1, 0, text);
    }

    /**
     * @param replacements of one file, which do not overlap
     */
    @NotNull
    static String apply(@NotNull String content, @NotNull List<Replacement> replacements) {
        List<Integer> lineStarts = new ArrayList<>();
        lineStarts.add(0);
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == '\n') lineStarts.add(i + 1);
        }
        List<Replacement> fromTheEnd = new ArrayList<>(replacements);
        fromTheEnd.sort(Comparator.comparingInt((Replacement r) -> r.startLine)
            .thenComparingInt(r -> r.startCharacter).reversed());
        StringBuilder result = new StringBuilder(content);
        for (Replacement replacement : fromTheEnd) {
            int start = offset(lineStarts, content.length(), replacement.startLine, replacement.startCharacter);
            int end = offset(lineStarts, content.length(), replacement.endLine, replacement.endCharacter);
            result.replace(start, end, replacement.text);
        }
        return result.toString();
    }

    private static int offset(List<Integer> lineStarts, int length, int line, int character) {
        if (line - 1 >= lineStarts.size()) return length;
        return Math.min(lineStarts.get(Math.max(line - 1, 0)) + character, length);
    }
}
