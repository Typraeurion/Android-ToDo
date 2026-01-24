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
        super(RepeatType.MONTHLY_ON_DATE);
    }

    /**
     * Create a default RepeatMonthlyOnDate object for a given due date.
     *
     * @param due the first date on which this To Do item is due
     */
    public RepeatMonthlyOnDate(@NonNull LocalDate due) {
        super(RepeatType.MONTHLY_ON_DATE, due);
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
        super(RepeatType.MONTHLY_ON_DATE, bitMask);
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
        super(RepeatType.MONTHLY_ON_DATE, bitMask, due);
    }

    private LocalDate setDateAndAdjust(@NonNull LocalDate base) {
        LocalDate hardDate = base.withDayOfMonth(
                Math.min(date, base.lengthOfMonth()));
        return adjustDueDate(hardDate);
    }

    @Override
    public LocalDate computeNextDueDate(
            @NonNull LocalDate priorDueDate, @NonNull LocalDate completed) {
        LocalDate nextMonth;
        // If the prior due date is not the target date of the month,
        // the target date was probably a disallowed day of the week.
        if (priorDueDate.getDayOfMonth() != date) {
            // Move to the target date if it's
            // within a week of the prior due date.
            LocalDate priorTarget = priorDueDate.withDayOfMonth(
                    Math.min(date, priorDueDate.lengthOfMonth()));
            int daysApart = priorTarget.until(priorDueDate).getDays();
            if (daysApart < -14) {
                // Target must have been in the previous month
                Log.d("RepeatMonthlyOnDate", String.format(
                        "Prior due date appears to be adjusted from the previous"
                                + " month; adding %d months for the next repeat",
                        increment - 1));
                nextMonth = priorDueDate.plusMonths(increment - 1);

            } else if (daysApart > 14) {
                // Target must have been in the next month
                Log.d("RepeatMonthlyOnDate", String.format(
                        "Prior due date appears to be adjusted from the next"
                                + " month; adding %d months for the next repeat",
                        increment - 1));
                nextMonth = priorDueDate.plusMonths(increment + 1);
            } else {
                nextMonth = priorDueDate.plusMonths(increment);
            }
        } else {
            nextMonth = priorDueDate.plusMonths(increment);
        }
        LocalDate candiDate = setDateAndAdjust(nextMonth);
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
