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

import com.google.gerrit.extensions.common.DiffInfo;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a line of one patch set of a file is in another, from the diff Gerrit computes between them.
 */
final class LineMapping {

    static final class Mapped {
        /** 1-based, 0 for a comment on the whole file */
        final int line;
        /** false when the line itself changed and only the place where it was is known */
        final boolean exact;

        Mapped(int line, boolean exact) {
            this.line = line;
            this.exact = exact;
        }
    }

    private static final class Block {
        final int fromA;
        final int lengthA;
        final int fromB;
        final boolean same;

        Block(int fromA, int lengthA, int fromB, boolean same) {
            this.fromA = fromA;
            this.lengthA = lengthA;
            this.fromB = fromB;
            this.same = same;
        }
    }

    private final List<Block> blocks;
    private final int linesB;

    private LineMapping(List<Block> blocks, int linesB) {
        this.blocks = blocks;
        this.linesB = linesB;
    }

    @NotNull
    static LineMapping fromDiff(@NotNull List<DiffInfo.ContentEntry> content) {
        List<Block> blocks = new ArrayList<>();
        int a = 1;
        int b = 1;
        for (DiffInfo.ContentEntry entry : content) {
            int same = entry.ab != null ? entry.ab.size() : entry.skip != null ? entry.skip : 0;
            if (same > 0) {
                blocks.add(new Block(a, same, b, true));
                a += same;
                b += same;
            }
            int removed = entry.a != null ? entry.a.size() : 0;
            int added = entry.b != null ? entry.b.size() : 0;
            if (removed > 0 || added > 0) {
                blocks.add(new Block(a, removed, b, false));
                a += removed;
                b += added;
            }
        }
        return new LineMapping(blocks, b - 1);
    }

    @NotNull
    Mapped map(int line) {
        if (line <= 0) {
            return new Mapped(0, true);
        }
        for (Block block : blocks) {
            if (line >= block.fromA && line < block.fromA + block.lengthA) {
                return block.same
                    ? new Mapped(block.fromB + line - block.fromA, true)
                    : new Mapped(clamp(block.fromB), false);
            }
        }
        return new Mapped(clamp(line), false);
    }

    private int clamp(int line) {
        return Math.max(1, Math.min(line, Math.max(linesB, 1)));
    }
}
