/*
 * Copyright 2013 Urs Wolfer
 * Modified 2026 by Maximilian Kroboth: split from AddCommentAction, starts the comment in place in the diff.
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
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.UpdateInBackground;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.DumbAware;
import com.urswolfer.intellij.plugin.gerrit.GerritSettings;
import org.jetbrains.annotations.NotNull;

/**
 * Starts a comment on the caret's line, or on the selection.
 *
 * @author Urs Wolfer
 *
 * Some parts based on code from:
 * https://github.com/ktisha/Crucible4IDEA
 */
@SuppressWarnings("ComponentNotRegistered") // added with code
public class AddCommentInPlaceAction extends AnAction implements DumbAware, UpdateInBackground {

    private final GerritSettings gerritSettings = GerritSettings.getInstance();
    private final Editor editor;
    private final EditorCommentThreads threads;

    AddCommentInPlaceAction(@NotNull Editor editor, @NotNull EditorCommentThreads threads) {
        super("Add Comment", null, AllIcons.Toolwindows.ToolWindowMessages);
        this.editor = editor;
        this.threads = threads;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        SelectionModel selectionModel = editor.getSelectionModel();
        if (selectionModel.hasSelection()) {
            Comment.Range range = RangeUtils.textOffsetToRange(editor.getDocument().getCharsSequence(),
                selectionModel.getBlockSelectionStarts()[0], selectionModel.getBlockSelectionEnds()[0]);
            threads.startThread(range.endLine, range); // end line as per specification
        } else {
            threads.startThread(editor.getDocument().getLineNumber(editor.getCaretModel().getOffset()) + 1, null);
        }
    }

    /**
     * Disabled while a comment is being written in the diff: its shortcut may be a bare letter, which has to reach
     * the comment's editor.
     */
    @Override
    public void update(@NotNull AnActionEvent e) {
        Editor focused = e.getData(CommonDataKeys.EDITOR);
        e.getPresentation().setEnabled(gerritSettings.isLoginAndPasswordAvailable()
            && (focused == null || focused == editor));
    }
}
