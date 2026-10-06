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

import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.urswolfer.intellij.plugin.gerrit.util.TextToHtml;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A comment thread shown in place in the diff, with its replies and the editor to answer it.
 */
final class CommentThreadPanel extends CommentCard {

    interface Controller {
        boolean canComment();

        void reply(@NotNull CommentThread thread, @NotNull String text, boolean resolved,
                   @NotNull Runnable onSaved, @NotNull Runnable onFailed);

        void edit(@NotNull CommentInfo draft, @NotNull String text, boolean resolved,
                  @NotNull Runnable onSaved, @NotNull Runnable onFailed);

        void delete(@NotNull CommentInfo draft);

        /**
         * @param direction -1 for the thread above, 1 for the one below
         */
        void reveal(@NotNull CommentThreadPanel from, int direction);
    }

    private final Project project;
    private final Controller controller;

    private CommentThread thread;
    private boolean expanded;
    @Nullable
    private CommentEditorPanel replyEditor;
    @Nullable
    private String editedDraftId;
    @Nullable
    private CommentEditorPanel draftEditor;
    private boolean sendingDone;
    private boolean hasPrevious;
    private boolean hasNext;

    CommentThreadPanel(@NotNull Project project, @NotNull Controller controller, @NotNull CommentThread thread) {
        this.project = project;
        this.controller = controller;
        setThread(thread);
    }

    void setThread(@NotNull CommentThread thread) {
        this.thread = thread;
        if (editedDraftId != null && thread.getComments().stream().noneMatch(c -> c.id.equals(editedDraftId))) {
            closeDraftEditor();
        }
        rebuild();
    }

    void setNeighbours(boolean hasPrevious, boolean hasNext) {
        if (hasPrevious == this.hasPrevious && hasNext == this.hasNext) return;
        this.hasPrevious = hasPrevious;
        this.hasNext = hasNext;
        rebuild();
    }

    void expand() {
        if (!expanded) {
            expanded = true;
            rebuild();
        }
    }

    private void rebuild() {
        JPanel card = getContent();
        card.removeAll();
        if (isCollapsed()) {
            card.add(createCollapsedRow());
        } else {
            card.add(createHeader());
            for (CommentInfo comment : thread.getComments()) {
                card.add(createComment(comment));
            }
            if (replyEditor != null) {
                card.add(replyEditor);
            } else {
                card.add(createFooter());
            }
        }
        card.revalidate();
        card.repaint();
    }

    /**
     * A thread with a draft stays open: the draft is still to be published, and collapsing it right after "Done"
     * would hide what was just written.
     */
    private boolean isCollapsed() {
        return thread.isResolved() && !expanded && replyEditor == null && draftEditor == null && !hasDraft();
    }

    private boolean hasDraft() {
        return thread.getComments().stream().anyMatch(CommentThread::isDraft);
    }

    private JComponent createCollapsedRow() {
        CommentInfo root = thread.getRoot();
        String[] lines = StringUtil.splitByLines(StringUtil.notNullize(root.message));
        // Gerrit renders comments as Markdown; its emphasis would show as stray characters in one line
        String firstLine = lines.length > 0 ? lines[0].replace("**", "").replace("`", "") : "";
        int count = thread.getComments().size();
        String text = "Resolved · " + authorName(root) + ": " + StringUtil.shortenTextWithEllipsis(firstLine, 80, 0)
            + (count > 1 ? " (" + count + " comments)" : "");
        LinkLabel<Object> link = new LinkLabel<>(text, null, (source, data) -> expand());
        JComponent reply = null;
        if (canReply()) {
            reply = new LinkLabel<>("Reply", null, (source, data) -> {
                expanded = true;
                openReplyEditor();
            });
        }
        return row(link, reply);
    }

    private JComponent createHeader() {
        JBLabel state = new JBLabel(thread.isResolved() ? " Resolved " : " Unresolved ", UIUtil.ComponentStyle.SMALL);
        state.setOpaque(true);
        state.setForeground(UIUtil.getContextHelpForeground());
        state.setBackground(thread.isResolved()
            ? UIUtil.getPanelBackground().darker()
            : JBUI.CurrentTheme.Validator.warningBackgroundColor());
        JComponent right = null;
        if (thread.isResolved() && !hasDraft()) {
            right = new LinkLabel<>("Collapse", null, (source, data) -> {
                expanded = false;
                rebuild();
            });
        }
        return row(state, right);
    }

    private JComponent createComment(CommentInfo comment) {
        boolean draft = CommentThread.isDraft(comment);
        JPanel title = new JPanel();
        title.setOpaque(false);
        title.setLayout(new BoxLayout(title, BoxLayout.X_AXIS));
        JBLabel author = new JBLabel(authorName(comment));
        author.setFont(author.getFont().deriveFont(Font.BOLD));
        title.add(author);
        title.add(Box.createHorizontalStrut(JBUI.scale(8)));
        JBLabel date = new JBLabel(draft ? "Draft" : formatDate(comment), UIUtil.ComponentStyle.SMALL);
        date.setForeground(UIUtil.getContextHelpForeground());
        title.add(date);

        JComponent actions = null;
        if (draft && draftEditor == null && replyEditor == null) {
            JPanel links = new JPanel();
            links.setOpaque(false);
            links.setLayout(new BoxLayout(links, BoxLayout.X_AXIS));
            links.add(new LinkLabel<>("Edit", null, (source, data) -> openDraftEditor(comment)));
            links.add(Box.createHorizontalStrut(JBUI.scale(8)));
            links.add(new LinkLabel<>("Delete", null, (source, data) -> controller.delete(comment)));
            actions = links;
        }

        JPanel panel = new JPanel(new BorderLayout(0, JBUI.scale(2)));
        panel.setOpaque(false);
        panel.add(row(title, actions), BorderLayout.NORTH);
        if (draftEditor != null && comment.id.equals(editedDraftId)) {
            panel.add(draftEditor, BorderLayout.CENTER);
        } else {
            panel.add(createBody(comment.message), BorderLayout.CENTER);
        }
        return panel;
    }

    /**
     * Previous and Next walk the threads of this side of the diff; Done answers and resolves in one step.
     */
    private JComponent createFooter() {
        JButton previous = new JButton("Previous", AllIcons.Actions.PreviousOccurence);
        previous.setEnabled(hasPrevious);
        previous.addActionListener(e -> controller.reveal(this, -1));
        JButton next = new JButton("Next", AllIcons.Actions.NextOccurence);
        next.setEnabled(hasNext);
        next.addActionListener(e -> controller.reveal(this, 1));
        JComponent left = buttons(previous, next);

        JComponent right = null;
        if (canReply()) {
            List<JButton> answers = new ArrayList<>();
            if (!thread.isResolved()) {
                JButton done = new JButton("Done");
                done.setToolTipText("Reply \"Done\" and resolve the thread");
                done.addActionListener(e -> {
                    if (sendingDone) return;
                    sendingDone = true;
                    controller.reply(thread, "Done", true, () -> sendingDone = false, () -> sendingDone = false);
                });
                answers.add(done);
            }
            // painted as the default button of a dialog, which 2020.3 only does for the root pane's default button
            JButton reply = new JButton("Reply") {
                @Override
                public boolean isDefaultButton() {
                    return true;
                }
            };
            reply.addActionListener(e -> openReplyEditor());
            answers.add(reply);
            right = buttons(answers.toArray(new JButton[0]));
        }
        return row(left, right);
    }

    private boolean canReply() {
        return controller.canComment() && !CommentThread.isDraft(thread.getLast());
    }

    private static JComponent buttons(JButton... buttons) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        for (int i = 0; i < buttons.length; i++) {
            if (i > 0) panel.add(Box.createHorizontalStrut(JBUI.scale(4)));
            panel.add(buttons[i]);
        }
        return panel;
    }

    private void openReplyEditor() {
        replyEditor = new CommentEditorPanel(project, "", Drafts.isInitiallyResolved(null, thread.getLast()),
            new CommentEditorPanel.Listener() {
                @Override
                public void save(@NotNull String text, boolean resolved) {
                    CommentEditorPanel editor = Objects.requireNonNull(replyEditor);
                    controller.reply(thread, text, resolved, () -> {
                        replyEditor = null;
                        rebuild();
                    }, editor::saveFailed);
                }

                @Override
                public void cancel() {
                    replyEditor = null;
                    rebuild();
                }
            });
        rebuild();
        focus(replyEditor);
    }

    private void openDraftEditor(CommentInfo draft) {
        editedDraftId = draft.id;
        draftEditor = new CommentEditorPanel(project, StringUtil.notNullize(draft.message),
            Drafts.isInitiallyResolved(draft, null), new CommentEditorPanel.Listener() {
                @Override
                public void save(@NotNull String text, boolean resolved) {
                    CommentEditorPanel editor = Objects.requireNonNull(draftEditor);
                    controller.edit(draft, text, resolved, () -> {
                        closeDraftEditor();
                        rebuild();
                    }, editor::saveFailed);
                }

                @Override
                public void cancel() {
                    closeDraftEditor();
                    rebuild();
                }
            });
        rebuild();
        focus(draftEditor);
    }

    private void closeDraftEditor() {
        editedDraftId = null;
        draftEditor = null;
    }

    private static void focus(@Nullable CommentEditorPanel editor) {
        if (editor != null) {
            editor.getPreferredFocusedComponent().requestFocusInWindow();
        }
    }

    private static JComponent createBody(@Nullable String message) {
        JEditorPane pane = new JEditorPane();
        pane.setEditorKit(UIUtil.getHTMLEditorKit());
        pane.setEditable(false);
        pane.setOpaque(false);
        pane.setBorder(JBUI.Borders.empty());
        pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        pane.setFont(UIUtil.getLabelFont());
        pane.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE);
        pane.setText("<html><body>" + TextToHtml.textToHtml(StringUtil.notNullize(message)) + "</body></html>");
        return pane;
    }

    private static JComponent row(@NotNull JComponent left, @Nullable JComponent right) {
        JPanel row = new JPanel(new BorderLayout(JBUI.scale(8), 0));
        row.setOpaque(false);
        row.add(left, BorderLayout.WEST);
        if (right != null) {
            row.add(right, BorderLayout.EAST);
        }
        return row;
    }

    private static String authorName(CommentInfo comment) {
        if (CommentThread.isDraft(comment)) return "Myself";
        AccountInfo author = comment.author;
        if (author.name != null) return author.name;
        if (author.username != null) return author.username;
        return author.email != null ? author.email : "Unknown";
    }

    private static String formatDate(CommentInfo comment) {
        return comment.updated != null ? DateFormatUtil.formatPrettyDateTime(comment.updated) : "";
    }
}
