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

import com.xmission.trevin.android.todo.data.ToDoAlarm;
import com.xmission.trevin.android.todo.data.ToDoItem;

import java.io.Serializable;

/**
 * Container for the current state of UI elements in
 * {@link ToDoDetailsActivity}.  This is used to preserve the activity
 * state during a configuration change or putting the app in the
 * background.
 *
 * @author Trevin Beattie
 */
class DetailFormData implements Serializable {

    private static final long serialVersionUID = 12;

    Long todoId;
    @NonNull
    ToDoItem item;
    boolean dueDateDialogIsShowing;
    ToDoAlarm alarm;
    ToDoAlarm originalAlarm;
    RepeatSettings repeatDialogSettings;
    boolean endDateDialogIsShowing;
    boolean hideDialogIsShowing;
    Boolean hideEnabled;
    String hideDaysText;
    String showTimeText;
    boolean alarmDialogIsShowing;

    @Override
    public int hashCode() {
        int hash = 0;
        if (todoId != null)
            hash += todoId.hashCode();
        hash *= 31;
        if (item != null)
            hash += item.hashCode();
        hash *= 31;
        hash += Boolean.hashCode(dueDateDialogIsShowing);
        hash *= 31;
        if (alarm != null)
            hash += alarm.hashCode();
        hash *= 31;
        if (originalAlarm != null)
            hash += originalAlarm.hashCode();
        hash *= 31;
        if (repeatDialogSettings != null)
            hash += repeatDialogSettings.hashCode();
        hash *= 31;
        hash += Boolean.hashCode(endDateDialogIsShowing);
        hash *= 31;
        hash += Boolean.hashCode(hideDialogIsShowing);
        hash *= 31;
        if (hideEnabled != null)
            hash += hideEnabled.hashCode();
        hash *= 31;
        if (hideDaysText != null)
            hash += hideDaysText.hashCode();
        hash *= 31;
        if (showTimeText != null)
            hash += showTimeText.hashCode();
        hash *= 31;
        hash += Boolean.hashCode(alarmDialogIsShowing);
        return hash;
    }

    @Override
    @NonNull
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName());
        sb.append('[');
        if (item != null)
            sb.append("item=").append(item).append(", ");
        else if (todoId != null)
            sb.append("todoId=").append(todoId).append(", ");
        // First non-nullable field; from this point commas switch to the front.
        sb.append("dueDateDialogIsShowing=").append(dueDateDialogIsShowing);
        if (alarm != null)
            sb.append(", alarm=").append(alarm);
        if (alarm != null)
            sb.append(", originalAlarm=").append(alarm);
        if (repeatDialogSettings != null)
            sb.append(", repeatDialogSettings=").append(repeatDialogSettings);
        sb.append(", endDateDialogIsShowing=").append(endDateDialogIsShowing);
        sb.append(", hideDialogIsShowing=").append(hideDialogIsShowing);
        if (hideEnabled != null)
            sb.append(", hideEnabled=").append(hideEnabled);
        if (showTimeText != null)
            sb.append(", showTimeText=\"").append(showTimeText).append('"');
        sb.append(", alarmDialogIsShowing=").append(alarmDialogIsShowing);
        sb.append(']');
        return sb.toString();
    }

}
