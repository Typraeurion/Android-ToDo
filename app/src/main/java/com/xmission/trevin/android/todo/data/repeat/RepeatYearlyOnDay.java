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

import static com.xmission.trevin.android.todo.provider.ToDoSchema.ToDoItemColumns.REPEAT_MONTHLY_ON_DAY;
import static com.xmission.trevin.android.todo.provider.ToDoSchema.ToDoItemColumns.REPEAT_YEARLY_ON_DAY;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjuster;
import java.time.temporal.TemporalAdjusters;

/**
 * Repeating interval for a monthly To Do item that occurs on a
 * specific day of a specific week in a certain month every year
 * (e.g. Memorial Day, Thanksgiving).
 *
 * @author Trevin Beattie
 */
public class RepeatYearlyOnDay extends RepeatMonthlyOnDay {

    private static final long serialVersionUID = 5;

    public static final int ID = REPEAT_YEARLY_ON_DAY;

    /** The month in which this item repeats */
    @NonNull
    private Months month;

    /**
     * Create a default RepeatYearlyOnDay object that starts on
     * the current day and week.  It assumes a fixed week number
     * from the start of the month, unless today is the 29<sup>th</sup>
     * through 31<sup>st</sup> in which case it&rsquo;s set for
     * the last week.
     */
    public RepeatYearlyOnDay() {
        this(LocalDate.now());
    }

    /**
     * Create a default RepeatYearlyOnDay object for a given due date.
     * It assumes a fixed week number from the start of the month, unless
     * the due date is the 29<sup>th</sup> through 31<sup>st</sup>
     * in which case it&rsquo;s set for the last week.
     *
     * @param due the first date on which this To Do item is due
     */
    public RepeatYearlyOnDay(@NonNull LocalDate due) {
        super(REPEAT_MONTHLY_ON_DAY, due);
        month = Months.fromJavaMonth(due.getMonth());
    }

    /** @return the month of the year in which this item repeats */
    @NonNull
    public Months getMonth() {
        return month;
    }

    /**
     * Set the month of the year in which this item repeats
     *
     * @param month the month to set
     */
    public void setMonth(Months month) {
        if (month == null)
            throw new IllegalArgumentException(
                    "Month of the year cannot be null");
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

    @Override
    @Nullable
    public LocalDate computeNextDueDate(
            @NonNull LocalDate priorDueDate, @NonNull LocalDate completed) {
        TemporalAdjuster adjustment = TemporalAdjusters.dayOfWeekInMonth(
                (week < 4) ? (week + 1) : -1, day.getJavaDay());
        LocalDate startOfMonth = priorDueDate.withDayOfMonth(1)
                .plusYears(increment);
        return checkEndDate(priorDueDate, startOfMonth.with(adjustment));
    }

    @Override
    @NonNull
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName())
                .append('(');
        if (increment > 1) {
            sb.append("Every ");
            formatOrdinal(sb, increment);
            sb.append(' ');
        }
        formatDay(sb, week, day);
        sb.append(" of ").append(month);
        if (end != null) {
            sb.append(", ending ").append(end);
        }
        sb.append(')');
        return sb.toString();
    }

    @Override
    @NonNull
    public RepeatYearlyOnDay clone() {
        return (RepeatYearlyOnDay) super.clone();
    }

    @Override
    public int hashCode() {
        return super.hashCode() * 31 + month.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (!super.equals(o))
            return false;
        if (!(o instanceof RepeatYearlyOnDay))
            return false;
        RepeatYearlyOnDay other = (RepeatYearlyOnDay) o;
        if (month != other.month)
            return false;
        return true;
    }

}
