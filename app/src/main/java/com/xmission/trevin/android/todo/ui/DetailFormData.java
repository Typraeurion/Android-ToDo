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

    private static final long serialVersionUID = 4;

    @NonNull
    ToDoItem item = new ToDoItem();
    ToDoAlarm alarm;
    ToDoAlarm originalAlarm;
    RepeatSettings repeatDialogSettings;

    @Override
    public int hashCode() {
        int hash = item.hashCode();
        hash *= 31;
        if (alarm != null)
            hash += alarm.hashCode();
        hash *= 31;
        if (originalAlarm != null)
            hash += originalAlarm.hashCode();
        hash *= 31;
        if (repeatDialogSettings != null)
            hash += repeatDialogSettings.hashCode();
        return hash;
    }

    @Override
    @NonNull
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName());
        sb.append("[item=").append(item);
        if (alarm != null)
            sb.append(", alarm=").append(alarm);
        if (alarm != null)
            sb.append(", originalAlarm=").append(alarm);
        if (repeatDialogSettings != null)
            sb.append(", repeatDialogSettings=").append(repeatDialogSettings);
        sb.append(']');
        return sb.toString();
    }

}
