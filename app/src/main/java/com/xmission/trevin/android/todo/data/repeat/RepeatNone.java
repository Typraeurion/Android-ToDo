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
package com.xmission.trevin.android.todo.data.repeat;

import static com.xmission.trevin.android.todo.provider.ToDoSchema.ToDoItemColumns.REPEAT_NONE;

import android.util.Log;

import androidx.annotation.NonNull;

import java.time.LocalDate;

/**
 * Placeholder interval type for a non-repeating item.
 * This should effectively be treated the same as {@code null}.
 *
 * @author Trevin Beattie
 */
public class RepeatNone implements RepeatInterval {

    private static final long serialVersionUID = 0;

    public static final int ID = REPEAT_NONE;

    /**
     * Create a default no-repeat instance
     */
    public RepeatNone() {
    }

    /**
     * Create a no-repeat instance with a due date
     * (which has no effect here).
     */
    public RepeatNone(LocalDate due) {
    }

    @Override
    public int getId() {
        return ID;
    }

    @Override
    public boolean updateForDueDate(@NonNull LocalDate newDue) {
        return false;
    }

    @Override
    public LocalDate computeNextDueDate(
            @NonNull LocalDate priorDueDate, @NonNull LocalDate completed) {
        // No interval, never repeats
        return null;
    }

    @Override
    @NonNull
    public String toString() {
        return getClass().getSimpleName();
    }

    @Override
    @NonNull
    public RepeatNone clone() {
        try {
            return (RepeatNone) super.clone();
        } catch (CloneNotSupportedException e) {
            Log.e("RepeatNone", "Clone not supported", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public int hashCode() {
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof RepeatNone);
    }

}
