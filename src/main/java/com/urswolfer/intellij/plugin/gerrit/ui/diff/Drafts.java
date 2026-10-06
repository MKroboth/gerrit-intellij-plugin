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

import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.client.Comment;
import com.google.gerrit.extensions.client.Side;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static java.lang.Boolean.TRUE;

/**
 * The drafts the inline comment editor saves.
 */
final class Drafts {

    private Drafts() {}

    @NotNull
    static DraftInput newComment(@NotNull String path, @NotNull Side side, int line, @Nullable Comment.Range range,
                                 @NotNull String message, boolean resolved) {
        DraftInput draft = new DraftInput();
        draft.path = path;
        draft.side = side;
        draft.line = line;
        draft.range = range;
        draft.message = message;
        draft.unresolved = !resolved;
        return draft;
    }

    /**
     * The state is always sent: left out, Gerrit copies the one of the comment replied to, and "Done" would keep the
     * thread unresolved.
     */
    @NotNull
    static DraftInput reply(@NotNull Comment parent, @NotNull String message, boolean resolved) {
        DraftInput draft = new DraftInput();
        draft.inReplyTo = parent.id;
        draft.path = parent.path;
        draft.side = parent.side;
        draft.line = parent.line;
        draft.range = parent.range;
        draft.message = message;
        draft.unresolved = !resolved;
        return draft;
    }

    @NotNull
    static DraftInput edit(@NotNull Comment draft, @NotNull String message, boolean resolved) {
        DraftInput edited = reply(draft, message, resolved);
        edited.id = draft.id;
        edited.inReplyTo = draft.inReplyTo;
        return edited;
    }

    /**
     * A reply starts out in the state of the comment replied to: unchecked, it reopened every resolved thread
     * somebody answered.
     */
    static boolean isInitiallyResolved(@Nullable Comment commentToEdit, @Nullable Comment replyToComment) {
        if (commentToEdit != null) {
            return !TRUE.equals(commentToEdit.unresolved);
        }
        return replyToComment != null && !TRUE.equals(replyToComment.unresolved);
    }
}
