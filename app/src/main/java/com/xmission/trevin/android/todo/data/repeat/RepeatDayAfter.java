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

import static com.xmission.trevin.android.todo.provider.ToDoSchema.ToDoItemColumns.REPEAT_DAY_AFTER;

import androidx.annotation.NonNull;

import java.time.LocalDate;

/**
 * Repeating interval for a To Do item that repeats the next
 * (available) day after its last completion.
 *
 * @author Trevin Beattie
 */
public class RepeatDayAfter extends AbstractAdjustableRepeat {

    private static final long serialVersionUID = 4;

    public static final int ID = REPEAT_DAY_AFTER;

    /**
     * Create a default RepeatDayAfter object that starts on the current day.
     */
    public RepeatDayAfter() {
        super(RepeatType.DAY_AFTER);
    }

    /**
     * Create a default RepeatDayAfter object for a given due date.
     * For a daily task, the due date makes no difference.
     *
     * @param due the first date on which this To Do item is due
     */
    public RepeatDayAfter(@NonNull LocalDate due) {
        super(RepeatType.DAY_AFTER, due);
    }

    @Override
    public LocalDate computeNextDueDate(
            @NonNull LocalDate priorDueDate, @NonNull LocalDate completed) {
        int tentativeIncrement = increment;
        LocalDate candiDate = adjustDueDate(
                completed.plusDays(tentativeIncrement));
        // If the new due date after possible adjustment backwards
        // is not after the completion date, increase the increment.
        while (!candiDate.isAfter(completed)) {
            tentativeIncrement += increment;
            candiDate = adjustDueDate(
                    completed.plusDays(tentativeIncrement));
        }
        return checkEndDate(priorDueDate, candiDate);
    }

    @Override
    @NonNull
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName())
                .append('(');
        if (increment > 1) {
            sb.append("The ");
            formatOrdinal(sb, increment);
            sb.append(" day after completion");
        } else {
            sb.append("The next day after completion");
        }
        formatDays(sb);
        if (end != null) {
            sb.append(", ending ").append(end);
        }
        sb.append(')');
        return sb.toString();
    }

    @Override
    @NonNull
    public RepeatDayAfter clone() {
        return (RepeatDayAfter) super.clone();
    }

}
