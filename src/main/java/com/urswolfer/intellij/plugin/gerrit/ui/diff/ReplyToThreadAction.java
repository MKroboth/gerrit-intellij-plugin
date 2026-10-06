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

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.diff.tools.util.DiffDataKeys;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.actionSystem.UpdateInBackground;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.urswolfer.intellij.plugin.gerrit.GerritBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Answers the comment thread at the caret, or else the next one below it, as R does in Gerrit's web UI. On a thread
 * which ends in the user's draft it edits that draft, as a second reply on top of it is not possible.
 *
 * <p>
 * Registered for the keymap, and on each diff editor with a bare "R" while the keymap has no shortcut for it, as
 * "C" is for a new comment, see {@link GerritCommentsDiffExtension}.
 */
public class ReplyToThreadAction extends DumbAwareAction implements UpdateInBackground {
    static final String ID = "Gerrit.ReplyToThread";

    private static final Shortcut[] DEFAULT_SHORTCUTS = CustomShortcutSet.fromString("R").getShortcuts();

    /** Resolved per keystroke, so that a keymap change also reaches the diffs which are open already. */
    static final ShortcutSet DIFF_SHORTCUT_SET = () ->
        KeymapUtil.getActiveKeymapShortcuts(ID).getShortcuts().length > 0
            ? Shortcut.EMPTY_ARRAY
            : DEFAULT_SHORTCUTS;

    @Override
    public void update(@NotNull AnActionEvent e) {
        EditorEx editor = findEditor(e);
        boolean available = editor != null && DiffComments.of(editor) != null;
        e.getPresentation().setVisible(available);
        e.getPresentation().setEnabled(available && AddCommentAction.canComment(e.getProject())
            && !AddCommentInDiffAction.isTypedInFileEditor(e) && !AddCommentAction.isInThread(e));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        EditorEx editor = findEditor(e);
        DiffComments comments = editor != null ? DiffComments.of(editor) : null;
        if (comments != null && !comments.replyAtCaret(editor)) {
            HintManager.getInstance().showInformationHint(editor, GerritBundle.message("diff.noThread"));
        }
    }

    @Nullable
    private static EditorEx findEditor(AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (editor == null) {
            editor = e.getData(DiffDataKeys.CURRENT_EDITOR);
        }
        return editor instanceof EditorEx ? (EditorEx) editor : null;
    }
}
