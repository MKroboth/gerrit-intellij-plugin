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

package com.urswolfer.intellij.plugin.gerrit.ui;

import com.google.gerrit.extensions.common.ChangeInfo;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.util.Comparator;
import java.util.function.Function;

/**
 * A column of the change list while it is grouped: the group rows get the expand arrow in the first column and their
 * title in the subject column, and nothing else; the change rows are indented under them.
 */
final class GroupedColumn<A> extends ColumnInfo<ChangeInfo, A> {
    enum Role { ARROW, TITLE, OTHER }

    private static final TableCellRenderer EMPTY = new DefaultTableCellRenderer();

    private final ColumnInfo<ChangeInfo, A> delegate;
    private final Role role;
    private final Function<ChangeInfo, String> positionOf;

    GroupedColumn(@NotNull ColumnInfo<ChangeInfo, A> delegate, @NotNull Role role,
                  @NotNull Function<ChangeInfo, String> positionOf) {
        super(delegate.getName());
        this.delegate = delegate;
        this.role = role;
        this.positionOf = positionOf;
    }

    @NotNull
    ColumnInfo<ChangeInfo, A> getDelegate() {
        return delegate;
    }

    @Nullable
    @Override
    public A valueOf(ChangeInfo item) {
        return item instanceof ChangeGroupRow ? null : delegate.valueOf(item);
    }

    @Nullable
    @Override
    public TableCellRenderer getRenderer(ChangeInfo item) {
        if (item instanceof ChangeGroupRow) {
            ChangeGroupRow groupRow = (ChangeGroupRow) item;
            switch (role) {
                case ARROW:
                    return new DefaultTableCellRenderer() {
                        @Override
                        public java.awt.Component getTableCellRendererComponent(JTable table, Object value,
                                boolean isSelected, boolean hasFocus, int row, int column) {
                            super.getTableCellRendererComponent(table, null, false, false, row, column);
                            setIcon(groupRow.isCollapsed() ? AllIcons.General.ArrowRight : AllIcons.General.ArrowDown);
                            setHorizontalAlignment(CENTER);
                            return this;
                        }
                    };
                case TITLE:
                    return new ColoredTableCellRenderer() {
                        @Override
                        protected void customizeCellRenderer(JTable table, @Nullable Object value, boolean selected,
                                                             boolean hasFocus, int row, int column) {
                            append(groupRow.getGroup().getTitle(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
                            append("   " + ChangeGroups.statusOf(groupRow.getGroup().getChanges()),
                                SimpleTextAttributes.GRAYED_ATTRIBUTES);
                        }
                    };
                default:
                    return EMPTY;
            }
        }
        if (role == Role.TITLE) {
            return new ColoredTableCellRenderer() {
                @Override
                protected void customizeCellRenderer(JTable table, @Nullable Object value, boolean selected,
                                                     boolean hasFocus, int row, int column) {
                    setIpad(JBUI.insetsLeft(16));
                    String position = positionOf.apply(item);
                    if (position != null) {
                        append(position + "  ", SimpleTextAttributes.GRAYED_ATTRIBUTES);
                    }
                    append(StringUtil.notNullize(item.subject));
                }
            };
        }
        return delegate.getRenderer(item);
    }

    @Override
    public boolean isCellEditable(ChangeInfo item) {
        return !(item instanceof ChangeGroupRow) && delegate.isCellEditable(item);
    }

    @Nullable
    @Override
    public TableCellEditor getEditor(ChangeInfo item) {
        return delegate.getEditor(item);
    }

    @Nullable
    @Override
    public String getMaxStringValue() {
        return delegate.getMaxStringValue();
    }

    @Nullable
    @Override
    public String getPreferredStringValue() {
        return delegate.getPreferredStringValue();
    }

    @Override
    public int getAdditionalWidth() {
        return delegate.getAdditionalWidth();
    }

    @Override
    public int getWidth(JTable table) {
        return delegate.getWidth(table);
    }

    @Nullable
    @Override
    public String getTooltipText() {
        return delegate.getTooltipText();
    }

    @Nullable
    @Override
    public Comparator<ChangeInfo> getComparator() {
        return null;
    }
}
