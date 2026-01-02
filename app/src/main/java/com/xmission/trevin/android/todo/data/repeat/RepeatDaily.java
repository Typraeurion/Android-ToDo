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

import static com.xmission.trevin.android.todo.provider.ToDoSchema.ToDoItemColumns.REPEAT_DAILY;

import androidx.annotation.NonNull;

import java.time.LocalDate;

/**
 * Repeating interval for a daily To Do item.
 *
 * @author Trevin Beattie
 */
public class RepeatDaily extends AbstractAdjustableRepeat {

    public static final int ID = REPEAT_DAILY;

    /**
     * Create a default RepeatDaily object that starts from the current day.
     */
    public RepeatDaily() {
        super(REPEAT_DAILY);
    }

    /**
     * Create a default RepeatDaily object for a given due date.
     * For a daily task, the due date makes no difference.
     *
     * @param due the first date on which this To Do item is due
     */
    public RepeatDaily(@NonNull LocalDate due) {
        super(REPEAT_DAILY, due);
    }

    /**
     * Create a RepeatDaily object with the days of the week and direction
     * given by a bit mask (i.e. from the database).
     *
     * @param bitMask the bit field containing the allowed days
     *                on which this item can be repeated
     *
     * @throws IllegalArgumentException if the bit field
     * (masked by all possible days) is 0
     */
    public RepeatDaily(int bitMask) {
        super(REPEAT_DAILY, bitMask);
    }

    /**
     * Create a RepeatDaily object with the days of the week and direction
     * given by a bit mask (i.e. from the database) and a given due date.
     * For a daily task, the due date makes no difference.
     *
     * @param bitMask the bit field containing the allowed days
     *                on which this item can be repeated
     * @param due the first date on which this To Do item is due
     *
     * @throws IllegalArgumentException if the bit field
     * (masked by all possible days) is 0
     */
    public RepeatDaily(int bitMask, @NonNull LocalDate due) {
        super(REPEAT_DAILY, bitMask, due);
    }

    @Override
    public LocalDate computeNextDueDate(
            @NonNull LocalDate priorDueDate, @NonNull LocalDate completed) {
        int tentativeIncrement = increment;
        LocalDate candiDate = adjustDueDate(
                priorDueDate.plusDays(tentativeIncrement));
        // If the new due date after possible adjustment backwards
        // is not after the previous due date, increase the increment.
        while (!candiDate.isAfter(priorDueDate)) {
            tentativeIncrement += increment;
            candiDate = adjustDueDate(
                    priorDueDate.plusDays(tentativeIncrement));
        }
        return checkEndDate(priorDueDate, candiDate);
    }

    @Override
    @NonNull
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName())
                .append('(');
        if (increment > 1) {
            sb.append("Every ");
            formatOrdinal(sb, increment);
            sb.append(" day");
        } else {
            sb.append("Every day");
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
    public RepeatDaily clone() {
        return (RepeatDaily) super.clone();
    }

}
