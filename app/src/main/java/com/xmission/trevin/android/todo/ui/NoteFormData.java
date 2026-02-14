/*
 * Copyright © 2026 Trevin Beattie
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.xmission.trevin.android.todo.ui;

import androidx.annotation.NonNull;

import java.io.Serializable;

/**
 * Container for the current state of UI elements in
 * {@link ToDoNoteActivity}.  This is used to preserve the activity
 * state during a configuration change or putting the app in the
 * background.
 *
 * @author Trevin Beattie
 */
public class NoteFormData implements Serializable {

    private static final long serialVersionUID = 3;

    Long todoId;
    boolean isDetailHandoff;
    String description;
    String oldNoteText;
    String currentNoteText;

    @Override
    public int hashCode() {
        int hash = 0;
        if (todoId != null)
            hash += todoId.hashCode();
        hash *= 31;
        hash += Boolean.hashCode(isDetailHandoff);
        hash *= 31;
        if (description != null)
            hash += description.hashCode();
        hash *= 31;
        if (oldNoteText != null)
            hash += oldNoteText.hashCode();
        hash *= 31;
        if (currentNoteText != null)
            hash += currentNoteText.hashCode();
        return hash;
    }

    @Override
    @NonNull
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName());
        sb.append('[');
        if (todoId != null)
            sb.append("todoId=").append(todoId).append(", ");
        sb.append("isDetailHandoff=").append(isDetailHandoff);
        sb.append(", description=\"").append(description);
        sb.append("\", oldNoteText=\"").append(oldNoteText);
        sb.append("\", currentNoteText=\"").append(currentNoteText);
        sb.append("\"]");
        return sb.toString();
    }

}
