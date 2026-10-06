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
import org.junit.Assert;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class LineMappingTest {

    @Test
    public void testUnchangedLinesKeepTheirNumber() {
        LineMapping mapping = LineMapping.fromDiff(Collections.singletonList(same(5)));

        Assert.assertEquals(3, mapping.map(3).line);
        Assert.assertTrue(mapping.map(3).exact);
    }

    @Test
    public void testLinesBelowAnInsertionMoveDown() {
        LineMapping mapping = LineMapping.fromDiff(Arrays.asList(same(2), changed(0, 3), same(4)));

        Assert.assertEquals(2, mapping.map(2).line);
        Assert.assertEquals(7, mapping.map(4).line);
        Assert.assertTrue(mapping.map(4).exact);
    }

    @Test
    public void testLinesBelowARemovalMoveUp() {
        LineMapping mapping = LineMapping.fromDiff(Arrays.asList(same(1), changed(2, 0), same(3)));

        Assert.assertEquals(2, mapping.map(4).line);
    }

    @Test
    public void testChangedLineGoesWhereItsReplacementStarts() {
        LineMapping mapping = LineMapping.fromDiff(Arrays.asList(same(2), changed(2, 1), same(2)));

        LineMapping.Mapped mapped = mapping.map(4);
        Assert.assertEquals(3, mapped.line);
        Assert.assertFalse(mapped.exact);
    }

    @Test
    public void testRemovedLineAtTheEndStaysInTheFile() {
        LineMapping mapping = LineMapping.fromDiff(Arrays.asList(same(3), changed(2, 0)));

        Assert.assertEquals(3, mapping.map(5).line);
        Assert.assertFalse(mapping.map(5).exact);
    }

    @Test
    public void testSkippedLinesCountOnBothSides() {
        DiffInfo.ContentEntry skip = new DiffInfo.ContentEntry();
        skip.skip = 10;

        LineMapping mapping = LineMapping.fromDiff(Arrays.asList(changed(0, 1), skip, same(1)));

        Assert.assertEquals(12, mapping.map(11).line);
    }

    @Test
    public void testFileCommentStaysOnTheFile() {
        Assert.assertEquals(0, LineMapping.fromDiff(Collections.singletonList(same(3))).map(0).line);
    }

    private static DiffInfo.ContentEntry same(int lines) {
        DiffInfo.ContentEntry entry = new DiffInfo.ContentEntry();
        entry.ab = lines(lines);
        return entry;
    }

    private static DiffInfo.ContentEntry changed(int removed, int added) {
        DiffInfo.ContentEntry entry = new DiffInfo.ContentEntry();
        entry.a = removed > 0 ? lines(removed) : null;
        entry.b = added > 0 ? lines(added) : null;
        return entry;
    }

    private static List<String> lines(int count) {
        return Collections.nCopies(count, "x");
    }
}
