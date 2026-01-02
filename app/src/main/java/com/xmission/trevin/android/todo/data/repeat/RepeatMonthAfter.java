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

import static com.xmission.trevin.android.todo.provider.ToDoSchema.ToDoItemColumns.REPEAT_MONTH_AFTER;

import androidx.annotation.NonNull;

import java.time.LocalDate;

/**
 * Repeating interval for a To Do item that repeats the next
 * month after its last completion (within the constraints of
 * the available days of the week and days of the month).
 *
 * @author Trevin Beattie
 */
public class RepeatMonthAfter extends AbstractAdjustableRepeat {

    private static final long serialVersionUID = 4;

    public static final int ID = REPEAT_MONTH_AFTER;

    /**
     * Create a default RepeatMonthAfter object
     * that starts on the current day.
     */
    public RepeatMonthAfter() {
        super(REPEAT_MONTH_AFTER);
    }

    /**
     * Create a default RepeatMonthAfter object for a given due date.
     *
     * @param due the first date on which this To Do item is due
     */
    public RepeatMonthAfter(@NonNull LocalDate due) {
        super(REPEAT_MONTH_AFTER, due);
    }

    /**
     * Create a RepeatMonthAfter object with the days of the week
     * and direction given by a bit mask (i.e. from the database).
     *
     * @param bitMask the bit field containing the allowed days
     *                on which this item can be repeated
     *
     * @throws IllegalArgumentException if the bit field
     * (masked by all possible days) is 0
     */
    public RepeatMonthAfter(int bitMask) {
        super(REPEAT_MONTH_AFTER, bitMask);
    }

    /**
     * Create a RepeatMonthAfter object with the days of the week
     * and direction given by a bit mask (i.e. from the database)
     * and a given due date.
     *
     * @param bitMask the bit field containing the allowed days
     *                on which this item can be repeated
     * @param due the first date on which this To Do item is due
     *
     * @throws IllegalArgumentException if the bit field
     * (masked by all possible days) is 0
     */
    public RepeatMonthAfter(int bitMask, @NonNull LocalDate due) {
        super(REPEAT_MONTH_AFTER, bitMask, due);
    }

    @Override
    public LocalDate computeNextDueDate(
            @NonNull LocalDate priorDueDate, @NonNull LocalDate completed) {
        LocalDate nextDueDate = adjustDueDate(
                priorDueDate.plusMonths(increment));
        return checkEndDate(priorDueDate, nextDueDate);
    }

    @Override
    @NonNull
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName())
                .append('(');
        if (increment > 1) {
            sb.append("The ");
            formatOrdinal(sb, increment);
            sb.append(" month after completion");
        } else {
            sb.append("The next month after completion");
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
    public RepeatMonthAfter clone() {
        return (RepeatMonthAfter) super.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (!super.equals(o))
            return false;
        if (!(o instanceof RepeatMonthAfter))
            return false;
        return true;
    }

}
