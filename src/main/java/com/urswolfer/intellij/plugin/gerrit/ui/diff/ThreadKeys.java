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

import com.intellij.diff.tools.util.DiffDataKeys;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * The keyboard of a diff's threads, as in Gerrit's web UI: N and P walk them, R answers the one at the caret.
 *
 * Like "C" for a new comment, a bare letter is only bound to the diff's editor while the keymap has no shortcut of
 * its own for the action, and is off while the focus is in a comment being written.
 */
final class ThreadKeys {
    private static final Key<EditorCommentThreads> THREADS = Key.create("gerrit.EditorCommentThreads");

    private ThreadKeys() {}

    static void install(@NotNull EditorEx editor, @NotNull EditorCommentThreads threads) {
        editor.putUserData(THREADS, threads);
        register(editor, "Gerrit.NextThread", "N", NextThread.RUN);
        register(editor, "Gerrit.PreviousThread", "P", PreviousThread.RUN);
        register(editor, "Gerrit.ReplyToThread", "R", ReplyToThread.RUN);
    }

    private static void register(EditorEx editor, String actionId, String key, Consumer<EditorCommentThreads> run) {
        Shortcut[] defaults = CustomShortcutSet.fromString(key).getShortcuts();
        ShortcutSet shortcuts = () -> KeymapUtil.getActiveKeymapShortcuts(actionId).getShortcuts().length > 0
            ? Shortcut.EMPTY_ARRAY
            : defaults;
        new DumbAwareAction() {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                run.accept(editor.getUserData(THREADS));
            }

            @Override
            public void update(@NotNull AnActionEvent e) {
                Editor focused = e.getData(CommonDataKeys.EDITOR);
                e.getPresentation().setEnabled(focused == null || focused == editor);
            }
        }.registerCustomShortcutSet(shortcuts, editor.getContentComponent());
    }

    @Nullable
    private static EditorCommentThreads threadsOf(AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (editor == null) {
            editor = e.getData(DiffDataKeys.CURRENT_EDITOR);
        }
        return editor != null ? editor.getUserData(THREADS) : null;
    }

    /**
     * The keymap's entry for one of the keys, so that it can be bound in "Settings | Keymap".
     */
    abstract static class InDiff extends AnAction implements DumbAware {
        private final Consumer<EditorCommentThreads> run;

        InDiff(Consumer<EditorCommentThreads> run) {
            this.run = run;
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            EditorCommentThreads threads = threadsOf(e);
            if (threads != null) {
                run.accept(threads);
            }
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            boolean available = threadsOf(e) != null;
            e.getPresentation().setEnabledAndVisible(available);
        }
    }

    public static final class NextThread extends InDiff {
        static final Consumer<EditorCommentThreads> RUN = threads -> threads.revealFromCaret(1);

        public NextThread() {
            super(RUN);
        }
    }

    public static final class PreviousThread extends InDiff {
        static final Consumer<EditorCommentThreads> RUN = threads -> threads.revealFromCaret(-1);

        public PreviousThread() {
            super(RUN);
        }
    }

    public static final class ReplyToThread extends InDiff {
        static final Consumer<EditorCommentThreads> RUN = EditorCommentThreads::replyAtCaret;

        public ReplyToThread() {
            super(RUN);
        }
    }
}
