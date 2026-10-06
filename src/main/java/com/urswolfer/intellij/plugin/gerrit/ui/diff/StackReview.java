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

import com.google.gerrit.extensions.common.ChangeInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A stack being reviewed change by change from its base: the diff of each of its changes offers the next one. Used on
 * the event dispatch thread only.
 */
public final class StackReview {
    @Nullable
    private static StackReview current;

    private final List<ChangeInfo> baseFirst;
    private final Consumer<ChangeInfo> open;

    private StackReview(List<ChangeInfo> baseFirst, Consumer<ChangeInfo> open) {
        this.baseFirst = new ArrayList<>(baseFirst);
        this.open = open;
    }

    /**
     * @param open shows the diff of a change of the stack
     */
    public static void start(@NotNull List<ChangeInfo> baseFirst, @NotNull Consumer<ChangeInfo> open) {
        current = new StackReview(baseFirst, open);
        if (!baseFirst.isEmpty()) {
            open.accept(baseFirst.get(0));
        }
    }

    @Nullable
    static StackReview of(@NotNull ChangeInfo change) {
        StackReview review = current;
        return review != null && review.indexOf(change) >= 0 ? review : null;
    }

    int indexOf(@NotNull ChangeInfo change) {
        for (int i = 0; i < baseFirst.size(); i++) {
            if (baseFirst.get(i).id.equals(change.id)) return i;
        }
        return -1;
    }

    int size() {
        return baseFirst.size();
    }

    void open(int index) {
        if (index >= 0 && index < baseFirst.size()) {
            open.accept(baseFirst.get(index));
        }
    }
}
