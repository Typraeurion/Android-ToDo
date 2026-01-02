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

import static com.xmission.trevin.android.todo.provider.ToDoSchema.ToDoItemColumns.REPEAT_MONTHLY_ON_DATE;

import android.util.Log;

import androidx.annotation.NonNull;

import java.time.LocalDate;

/**
 * Repeating interval for a monthly To Do item that occurs on a
 * specific day of a specific week (e.g. the first Monday every month).
 *
 * @author Trevin Beattie
 */
public class RepeatMonthlyOnDate extends AbstractDateRepeat {

    private static final long serialVersionUID = 4;

    public static final int ID = REPEAT_MONTHLY_ON_DATE;

    /**
     * Create a default RepeatMonthlyOnDate object
     * that starts on the current day.
     */
    public RepeatMonthlyOnDate() {
        super(REPEAT_MONTHLY_ON_DATE);
    }

    /**
     * Create a default RepeatMonthlyOnDate object for a given due date.
     *
     * @param due the first date on which this To Do item is due
     */
    public RepeatMonthlyOnDate(@NonNull LocalDate due) {
        super(REPEAT_MONTHLY_ON_DATE, due);
    }

    /**
     * Create a RepeatMonthlyOnDate object with the days of the week
     * and direction given by a bit mask (i.e. from the database).
     *
     * @param bitMask the bit field containing the allowed days
     *                on which this item can be repeated
     *
     * @throws IllegalArgumentException if the bit field
     * (masked by all possible days) is 0
     */
    public RepeatMonthlyOnDate(int bitMask) {
        super(REPEAT_MONTHLY_ON_DATE, bitMask);
    }

    /**
     * Create a RepeatMonthlyOnDate object with the days of the week
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
    public RepeatMonthlyOnDate(int bitMask, @NonNull LocalDate due) {
        super(REPEAT_MONTHLY_ON_DATE, bitMask, due);
    }

    private LocalDate setDateAndAdjust(@NonNull LocalDate base) {
        LocalDate hardDate = base.withDayOfMonth(
                Math.min(date, base.lengthOfMonth()));
        return adjustDueDate(hardDate);
    }

    @Override
    public LocalDate computeNextDueDate(
            @NonNull LocalDate priorDueDate, @NonNull LocalDate completed) {
        // tentatively advance by 1 month to check whether
        // an adjustment will cross back to the previous month.
        LocalDate nextMonth = priorDueDate.plusMonths(1);
        LocalDate candiDate = setDateAndAdjust(nextMonth);
        if (candiDate.withDayOfMonth(1)
                .isBefore(nextMonth.withDayOfMonth(1))) {
            // Confirmed month crossing; increment
            // 1 month more than the normal increment.
            Log.d(getClass().getSimpleName(), String.format(
                    "Adjustment for %s crossed back to %s;"
                            + " advancing %d months",
                    nextMonth, candiDate, increment + 1));
            candiDate = setDateAndAdjust(nextMonth.plusMonths(increment));
        } else if (increment > 1) {
            // No month crossing; use the normal increment
            candiDate = setDateAndAdjust(priorDueDate.plusMonths(increment));
        }
        return checkEndDate(priorDueDate, candiDate);
    }

    @Override
    @NonNull
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName())
                .append('(');
        if (increment > 1) {
            sb.append("Every ").append(increment).append(" months");
        } else {
            sb.append("Every month");
        }
        sb.append(" on the ");
        formatOrdinal(sb, date);
        formatDays(sb);
        if (end != null) {
            sb.append(", ending ").append(end);
        }
        sb.append(')');
        return sb.toString();
    }

    @Override
    @NonNull
    public RepeatMonthlyOnDate clone() {
        return (RepeatMonthlyOnDate) super.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (!super.equals(o))
            return false;
        if (!(o instanceof RepeatMonthlyOnDate))
            return false;
        return true;
    }

}
