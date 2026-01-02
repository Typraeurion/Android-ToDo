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

import static com.xmission.trevin.android.todo.provider.ToDoSchema.ToDoItemColumns.REPEAT_YEARLY_ON_DATE;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDate;

/**
 * Repeating interval for a monthly To Do item that occurs on a
 * specific day of a specific week (e.g. the first Monday every month).
 *
 * @author Trevin Beattie
 */
public class RepeatYearlyOnDate extends AbstractDateRepeat {

    private static final long serialVersionUID = 5;

    public static final int ID = REPEAT_YEARLY_ON_DATE;

    /** The month in which this item repeats */
    @NonNull
    private Months month;

    /**
     * Create a default RepeatYearlyOnDate object
     * that starts on the current day.
     */
    public RepeatYearlyOnDate() {
        this(WeekDays.DAYS_BIT_MASK |
                WeekdayDirection.NEXT.getValue(), LocalDate.now());
    }

    /**
     * Create a default RepeatYearlyOnDate object for a given due date.
     *
     * @param due the first date on which this To Do item is due
     */
    public RepeatYearlyOnDate(@NonNull LocalDate due) {
        this(WeekDays.DAYS_BIT_MASK |
                WeekdayDirection.NEXT.getValue(), due);
    }

    /**
     * Create a RepeatYearlyOnDate object with the days of the week
     * and direction given by a bit mask (i.e. from the database).
     *
     * @param bitMask the bit field containing the allowed days
     *                on which this item can be repeated
     *
     * @throws IllegalArgumentException if the bit field
     * (masked by all possible days) is 0
     */
    public RepeatYearlyOnDate(int bitMask) {
        this(bitMask, LocalDate.now());
    }

    /**
     * Create a RepeatYearlyOnDate object with the days of the week
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
    public RepeatYearlyOnDate(int bitMask, @NonNull LocalDate due) {
        super(REPEAT_YEARLY_ON_DATE, bitMask, due);
        month = Months.fromJavaMonth(due.getMonth());
    }

    /** @return the month in which this item repeats */
    @NonNull
    public Months getMonth() {
        return month;
    }

    /**
     * Set the month in which this item repeats
     *
     * @param month the month to set
     */
    public void setMonth(@NonNull Months month) {
        if (month == null)
            throw new IllegalArgumentException(
                    "Month cannot be null");
        this.month = month;
    }

    @Override
    public boolean updateForDueDate(@NonNull LocalDate newDue) {
        Months newMonth = Months.fromJavaMonth(newDue.getMonth());
        boolean monthChanged = (month != newMonth);
        if (monthChanged)
            month = newMonth;
        boolean superChanged = super.updateForDueDate(newDue);
        return monthChanged || superChanged;
    }

    private LocalDate setDateAndAdjust(LocalDate base) {
        // Remember, our months are 0-based while withMonth() is 1-based.
        LocalDate hardDate = base.withMonth(month.getValue() + 1)
                // Cap the date in case it's February 29
                .withDayOfMonth(Math.min(date, base.lengthOfMonth()));
        return adjustDueDate(hardDate);
    }

    @Override
    @Nullable
    public LocalDate computeNextDueDate(
            @NonNull LocalDate priorDueDate, @NonNull LocalDate completed) {
        // tentatively advance by 1 year to check whether
        // an adjustment will cross back to the previous year.
        LocalDate nextYear = priorDueDate.plusYears(1);
        LocalDate candiDate = setDateAndAdjust(nextYear);
        if (candiDate.withDayOfMonth(1)
                .isBefore(nextYear.withDayOfMonth(1))) {
            // Confirmed month crossing; increment
            // 1 year more than the normal increment.
            Log.d(getClass().getSimpleName(), String.format(
                    "Adjustment for %s crossed back to %s;"
                            + " advancing %d years",
                    nextYear, candiDate, increment + 1));
            candiDate = setDateAndAdjust(nextYear.plusYears(increment));
            // To Do: Finish method stub
        } else if (increment > 1) {
            // No year crossing; use the normal increment
            candiDate = setDateAndAdjust(priorDueDate.plusYears(increment));
        }
        return checkEndDate(priorDueDate, candiDate);
    }

    @Override
    @NonNull
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName())
                .append('(');
        if (increment > 1) {
            sb.append("Every ").append(increment).append(" years");
        } else {
            sb.append("Every year");
        }
        sb.append(" on ").append(month).append(' ');
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
    public RepeatYearlyOnDate clone() {
        return (RepeatYearlyOnDate) super.clone();
    }

}
