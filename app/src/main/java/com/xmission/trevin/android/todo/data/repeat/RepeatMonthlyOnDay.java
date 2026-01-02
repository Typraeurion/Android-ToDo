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

import androidx.annotation.NonNull;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjuster;
import java.time.temporal.TemporalAdjusters;

/**
 * Repeating interval for a monthly To Do item that occurs on a
 * specific day of a specific week (e.g. the first Monday every month).
 *
 * @author Trevin Beattie
 */
public class RepeatMonthlyOnDay extends AbstractRepeat {

    private static final long serialVersionUID = 4;

    public static final int ID = REPEAT_MONTHLY_ON_DAY;

    /**
     * The week day on which this item repeats
     */
    @NonNull
    protected WeekDays day;

    /**
     * The week of the month on which this item repeats.
     * 0 is the first week, 4 is the <i>last</i> week
     * (regardless of the actual number of weeks in a month).
     */
    protected int week;

    /** Bypass the {@code id} field for our subclasses */
    protected RepeatMonthlyOnDay(int typeId) {
        this(typeId, LocalDate.now());
    }

    /** Bypass the {@code id} field for our subclasses */
    protected RepeatMonthlyOnDay(int typeId, @NonNull LocalDate due) {
        super(typeId, due);
        day = WeekDays.fromJavaDay(due.getDayOfWeek());
        week = (due.getDayOfMonth() - 1) / 7;
    }

    /**
     * Create a default RepeatMonthlyOnDay object that starts on
     * the current day and week.  It assumes a fixed week number
     * from the start of the month, unless today is the 29<sup>th</sup>
     * through 31<sup>st</sup> in which case it&rsquo;s set for
     * the last week.
     */
    public RepeatMonthlyOnDay() {
        this(REPEAT_MONTHLY_ON_DAY, LocalDate.now());
    }

    /**
     * Create a default RepeatMonthlyOnDay object for a given due date.
     * It assumes a fixed week number from the start of the month, unless
     * the due date is the 29<sup>th</sup> through 31<sup>st</sup>
     * in which case it&rsquo;s set for the last week.
     *
     * @param due the first date on which this To Do item is due
     */
    public RepeatMonthlyOnDay(@NonNull LocalDate due) {
        this(REPEAT_MONTHLY_ON_DAY, due);
    }

    /** @return the day of the week on which this item repeats */
    public WeekDays getDay() {
        return day;
    }

    /**
     * Set the day of the week on which this item repeats
     *
     * @param day the day of the week to set
     *
     * @throws IllegalArgumentException if {@code day} is {@code null}
     */
    public void setDay(@NonNull WeekDays day) {
        if (day == null)
            throw new IllegalArgumentException(
                    "Day of the week cannot be null");
        this.day = day;
    }

    /**
     * @return the week of the month on which this item repeats;
     * 0 is the first week, 4 is the <i>last</i> week.
     */
    public int getWeek() {
        return week;
    }

    /**
     * Set the week of the month on which this item repeats.
     *
     * @param weekNum the week number (from 0)
     *
     * @throws IllegalArgumentException if {@code weekNum} is
     * less than 0 or greater than 4
     */
    public void setWeek(int weekNum) {
        if (weekNum < 0)
            throw new IllegalArgumentException(
                    "Week of the month cannot be negative");
        if (weekNum > 4)
            throw new IllegalArgumentException(
                    "Week of the month cannot be greater than 4");
        week = weekNum;
    }

    @Override
    public boolean updateForDueDate(@NonNull LocalDate newDue) {
        WeekDays newDay = WeekDays.fromJavaDay(newDue.getDayOfWeek());
        int newWeek = (newDue.getDayOfMonth() - 1) / 7;
        // If this is in the last week but not the fifth week,
        // allow matching either 3 or 4.
        if ((newDue.getDayOfMonth() > newDue.lengthOfMonth() - 7) &&
                newWeek == 3) {
            if (week == 4)
                newWeek = 4;
        }
        boolean dayChanged = (day != newDay) || (week != newWeek);
        if (dayChanged) {
            day = newDay;
            week = newWeek;
        }
        return dayChanged;
    }

    @Override
    public LocalDate computeNextDueDate(
            @NonNull LocalDate priorDueDate, @NonNull LocalDate completed) {
        TemporalAdjuster adjustment = TemporalAdjusters.dayOfWeekInMonth(
                (week < 4) ? (week + 1) : -1, day.getJavaDay());
        LocalDate startOfMonth = priorDueDate.withDayOfMonth(1)
                .plusMonths(increment);
        return checkEndDate(priorDueDate, startOfMonth.with(adjustment));
    }

    /**
     * Helper method for formatting a day of the month for use in the
     * {@link #toString()} method.  This class and subclasses may need
     * to use it in different places or multiple times.
     */
    protected void formatDay(StringBuilder sb, int week, WeekDays day) {
        switch (week) {
            case 0: sb.append("first "); break;
            case 1: sb.append("second "); break;
            case 2: sb.append("third "); break;
            case 3: sb.append("fourth "); break;
            case 4: sb.append("last "); break;
        }
        sb.append(day.toString());
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
        sb.append(" of the month");
        if (end != null) {
            sb.append(", ending ").append(end);
        }
        sb.append(')');
        return sb.toString();
    }

    @Override
    @NonNull
    public RepeatMonthlyOnDay clone() {
        return (RepeatMonthlyOnDay) super.clone();
    }

    @Override
    public int hashCode() {
        int hash = super.hashCode() * 31 + day.hashCode();
        hash = hash * 31 + Integer.hashCode(week);
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (!super.equals(o))
            return false;
        if (!(o instanceof RepeatMonthlyOnDay))
            return false;
        RepeatMonthlyOnDay other = (RepeatMonthlyOnDay) o;
        if (day != other.day)
            return false;
        if (week != other.week)
            return false;
        return true;
    }

}
