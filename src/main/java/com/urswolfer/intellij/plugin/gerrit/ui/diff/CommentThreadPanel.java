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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
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

        /**
         * For a thread of an earlier patch set: which one, and whether its line changed since.
         */
        @Nullable
        String originOf(@NotNull CommentThread thread);

        /**
         * Where the unsaved text of an editor is kept, see {@link UnsentComments}.
         */
        @NotNull
        String unsentKey(@NotNull String kind, @NotNull String id);

        /**
         * What turns an issue's id in a comment into a link.
         */
        @NotNull
        List<CommentMarkdown.Link> commentLinks();

        /**
         * Applies a fix a comment suggests to the local copy of its files, if they are those of its patch set.
         */
        void applyFix(@NotNull CommentInfo comment, @NotNull Fixes.Fix fix);
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
    private boolean sendingQuickReply;
    private boolean hasPrevious;
    private boolean hasNext;

    CommentThreadPanel(@NotNull Project project, @NotNull Controller controller, @NotNull CommentThread thread) {
        this.project = project;
        this.controller = controller;
        setThread(thread);
        reopenUnsentEditor();
    }

    /**
     * An answer started before the diff was closed opens again, without taking the focus.
     */
    private void reopenUnsentEditor() {
        UnsentComments unsent = UnsentComments.getInstance();
        if (canReply() && unsent.get(controller.unsentKey("reply", thread.getRoot().id)) != null) {
            openReplyEditor("", false);
            return;
        }
        for (CommentInfo comment : thread.getComments()) {
            if (CommentThread.isDraft(comment) && unsent.get(controller.unsentKey("edit", comment.id)) != null) {
                openDraftEditor(comment, false);
                return;
            }
        }
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

    /**
     * The reply editor, opened and focused; on a thread which ends in the user's draft, that draft's editor, as a
     * second reply on top of it is not possible.
     */
    void startReply() {
        if (replyEditor != null) {
            focus(replyEditor);
        } else if (draftEditor != null) {
            focus(draftEditor);
        } else if (canReply()) {
            expanded = true;
            openReplyEditor("");
        } else if (controller.canComment() && CommentThread.isDraft(thread.getLast())) {
            openDraftEditor(thread.getLast());
        }
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
                card.add(withAvatar(AvatarIcon.self(AvatarIcon.commentSize()), replyEditor));
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
        String origin = controller.originOf(thread);
        String text = "Resolved · " + authorName(root) + ": " + StringUtil.shortenTextWithEllipsis(firstLine, 80, 0)
            + (count > 1 ? " (" + count + " comments)" : "") + (origin != null ? " · " + origin : "");
        LinkLabel<Object> link = new LinkLabel<>(text, AvatarIcon.of(root, JBUI.scale(16)), (source, data) -> expand());
        JComponent reply = null;
        if (canReply()) {
            reply = new LinkLabel<>("Reply", null, (source, data) -> {
                expanded = true;
                openReplyEditor("");
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
        JComponent left = state;
        String origin = controller.originOf(thread);
        if (origin != null) {
            JBLabel originLabel = new JBLabel(origin, UIUtil.ComponentStyle.SMALL);
            originLabel.setForeground(UIUtil.getContextHelpForeground());
            JPanel tags = new JPanel();
            tags.setOpaque(false);
            tags.setLayout(new BoxLayout(tags, BoxLayout.X_AXIS));
            tags.add(state);
            tags.add(Box.createHorizontalStrut(JBUI.scale(8)));
            tags.add(originLabel);
            left = tags;
        }
        JComponent right = null;
        if (thread.isResolved() && !hasDraft()) {
            right = new LinkLabel<>("Collapse", null, (source, data) -> {
                expanded = false;
                rebuild();
            });
        }
        return row(left, right);
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

        JPanel content = new JPanel(new BorderLayout(0, JBUI.scale(2)));
        content.setOpaque(false);
        content.add(row(title, actions), BorderLayout.NORTH);
        if (draftEditor != null && comment.id.equals(editedDraftId)) {
            content.add(draftEditor, BorderLayout.CENTER);
        } else {
            content.add(createBody(comment.message, controller.commentLinks()), BorderLayout.CENTER);
            List<Fixes.Fix> fixes = Fixes.of(comment);
            if (!fixes.isEmpty()) {
                JPanel links = new JPanel();
                links.setOpaque(false);
                links.setLayout(new BoxLayout(links, BoxLayout.X_AXIS));
                for (Fixes.Fix fix : fixes) {
                    if (links.getComponentCount() > 0) links.add(Box.createHorizontalStrut(JBUI.scale(12)));
                    links.add(new LinkLabel<>("Apply fix: " + fix.description, AllIcons.Actions.IntentionBulb,
                        (source, data) -> controller.applyFix(comment, fix)));
                }
                content.add(links, BorderLayout.SOUTH);
            }
        }
        return withAvatar(AvatarIcon.of(comment, AvatarIcon.commentSize()), content);
    }

    /**
     * The avatar in a column of its own, so that text and editors line up under the author's name.
     */
    static JComponent withAvatar(@NotNull Icon avatar, @NotNull JComponent content) {
        JPanel column = new JPanel(new BorderLayout());
        column.setOpaque(false);
        column.add(new JBLabel(avatar), BorderLayout.NORTH);
        JPanel panel = new JPanel(new BorderLayout(JBUI.scale(8), 0));
        panel.setOpaque(false);
        panel.add(column, BorderLayout.WEST);
        panel.add(content, BorderLayout.CENTER);
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
            JButton quote = new JButton("Quote");
            quote.setToolTipText("Reply quoting the last comment");
            quote.addActionListener(e -> openReplyEditor(Drafts.quote(thread.getLast().message)));
            answers.add(quote);
            if (!thread.isResolved()) {
                answers.add(quickReply("Ack", "Reply \"Ack\" and resolve the thread"));
                answers.add(quickReply("Done", "Reply \"Done\" and resolve the thread"));
            }
            // painted as the default button of a dialog, which 2020.3 only does for the root pane's default button
            JButton reply = new JButton("Reply") {
                @Override
                public boolean isDefaultButton() {
                    return true;
                }
            };
            reply.addActionListener(e -> openReplyEditor(""));
            answers.add(reply);
            right = buttons(answers.toArray(new JButton[0]));
        }
        return row(left, right);
    }

    /**
     * A one-word answer which resolves the thread, sent at once.
     */
    private JButton quickReply(String message, String tooltip) {
        JButton button = new JButton(message);
        button.setToolTipText(tooltip);
        button.addActionListener(e -> {
            if (sendingQuickReply) return;
            sendingQuickReply = true;
            controller.reply(thread, message, true, () -> sendingQuickReply = false, () -> sendingQuickReply = false);
        });
        return button;
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

    private void openReplyEditor(@NotNull String text) {
        openReplyEditor(text, true);
    }

    private void openReplyEditor(@NotNull String text, boolean focus) {
        replyEditor = new CommentEditorPanel(project, text, Drafts.isInitiallyResolved(null, thread.getLast()),
            controller.unsentKey("reply", thread.getRoot().id),
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
        if (focus) {
            focus(replyEditor);
        }
    }

    private void openDraftEditor(CommentInfo draft) {
        openDraftEditor(draft, true);
    }

    private void openDraftEditor(CommentInfo draft, boolean focus) {
        editedDraftId = draft.id;
        draftEditor = new CommentEditorPanel(project, StringUtil.notNullize(draft.message),
            Drafts.isInitiallyResolved(draft, null), controller.unsentKey("edit", draft.id),
            new CommentEditorPanel.Listener() {
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
        if (focus) {
            focus(draftEditor);
        }
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

    private static JComponent createBody(@Nullable String message, List<CommentMarkdown.Link> links) {
        JEditorPane pane = new JEditorPane();
        pane.setEditorKit(UIUtil.getHTMLEditorKit());
        pane.setEditable(false);
        pane.setOpaque(false);
        pane.setBorder(JBUI.Borders.empty());
        pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        pane.setFont(UIUtil.getLabelFont());
        pane.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE);
        pane.setText("<html><head><style>" + CommentMarkdown.style() + "</style></head><body>"
            + CommentMarkdown.toHtml(StringUtil.notNullize(message), links) + "</body></html>");
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
