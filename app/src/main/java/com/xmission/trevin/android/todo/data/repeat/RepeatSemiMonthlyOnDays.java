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

import static com.xmission.trevin.android.todo.provider.ToDoSchema.ToDoItemColumns.REPEAT_SEMI_MONTHLY_ON_DAYS;

import androidx.annotation.NonNull;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjuster;
import java.time.temporal.TemporalAdjusters;

/**
 * Repeating interval for a monthly To Do item that occurs on a
 * specific day of the week twice a month.
 *
 * @author Trevin Beattie
 */
public class RepeatSemiMonthlyOnDays extends RepeatMonthlyOnDay {

    private static final long serialVersionUID = 6;

    public static final int ID = REPEAT_SEMI_MONTHLY_ON_DAYS;

    /**
     * The second week day on which this item repeats
     */
    @NonNull
    protected WeekDays day2;

    /**
     * The second week of the month on which this item repeats.
     * 0 is the first week, 4 is the <i>last</i> week
     * (regardless of the actual number of weeks in a month).
     */
    protected int week2;

    /**
     * Create a default RepeatSemiMonthlyOnDays object that starts on
     * the current day and week.  It assumes a fixed week number
     * from the start of the month, unless today is the 29<sup>th</sup>
     * through 31<sup>st</sup> in which case it&rsquo;s set for
     * the last week.  The second week will be 2 weeks after the first.
     */
    public RepeatSemiMonthlyOnDays() {
        this(LocalDate.now());
    }

    /**
     * Create a default RepeatSemiMonthlyOnDays object for a given due date.
     * It assumes a fixed week number from the start of the month, unless
     * the due date is the 29<sup>th</sup> through 31<sup>st</sup>
     * in which case it&rsquo;s set for the last week.  The second week
     * will be 2 weeks after the first.
     *
     * @param due the first date on which this To Do item is due
     */
    public RepeatSemiMonthlyOnDays(@NonNull LocalDate due) {
        super(REPEAT_SEMI_MONTHLY_ON_DAYS, due);
        day2 = day;
        if (week < 2) {
            week2 = week + 2;
        } else {
            week2 = week;
            week = week2 - 2;
        }
    }

    /** @return the second day of the week on which this item repeats */
    @NonNull
    public WeekDays getDay2() {
        return day2;
    }

    /**
     * Set the day of the week on which this item repeats
     *
     * @param day the day of the week to set
     *
     * @throws IllegalArgumentException if {@code day} is {@code null}
     */
    public void setDay2(@NonNull WeekDays day) {
        if (day == null)
            throw new IllegalArgumentException(
                    "Day of the week cannot be null");
        this.day2 = day;
    }

    /**
     * @return the second week of the month on which this item repeats;
     * 0 is the first week, 4 is the <i>last</i> week.
     */
    public int getWeek2() {
        return week2;
    }

    /**
     * Set the second week of the month on which this item repeats.
     *
     * @param weekNum the week number (from 0)
     *
     * @throws IllegalArgumentException if {@code weekNum} is
     * less than 0 or greater than 4
     */
    public void setWeek2(int weekNum) {
        if (weekNum < 0)
            throw new IllegalArgumentException(
                    "Week of the month cannot be negative");
        if (weekNum > 4)
            throw new IllegalArgumentException(
                    "Week of the month cannot be greater than 4");
        week2 = weekNum;
    }

    @Override
    public boolean updateForDueDate(@NonNull LocalDate newDue) {
        // We have two dates to possibly match; check both.
        WeekDays newDay = WeekDays.fromJavaDay(newDue.getDayOfWeek());
        int newWeek1 = (newDue.getDayOfMonth() - 1) / 7;
        // If this is in the last week but not the fifth week,
        // allow matching either 3 or 4.
        if ((newDue.getDayOfMonth() > newDue.lengthOfMonth() - 7) &&
                newWeek1 == 3) {
            if (week == 4)
                newWeek1 = 4;
        }
        boolean dayChanged = true;
        if ((newDay == day) && (newWeek1 == week)) {
            dayChanged = false;
        } else if ((newDay == day2) && (newWeek1 == week2)) {
            // No change needed; accept the new due date
            // as the second day/week.
            dayChanged = false;
        } else {
            // Need to change.  Set the other week to 2 weeks before
            // or after the due date's, then use the earliest one
            // for our new week.  Both days will be the same.
            int newWeek2 = (newWeek1 < 2) ? (newWeek1 + 2) : (newWeek1 - 2);
            day = newDay;
            day2 = newDay;
            week = Math.min(newWeek1, newWeek2);
            week2 = Math.max(newWeek1, newWeek2);
        }
        return dayChanged;
    }

    /**
     * Compute a potential due date based on one of the dates set for
     * this repeat interval.  Each date needs the same calculation
     * before we decide which one to use.  The logic is:
     * <ol>
     *     <li>Start from the given {@code tentativeDay} of the
     *     {@code tentativeWeek} of  the same month as
     *     {@code priorDueDate}.</li>
     *     <li>If the result is not after {@code priorDueDate},
     *     add {@code increment} months to the {@code priorDueDate}
     *     and set the day of the week again.
     * </ol>
     *
     * @param tentativeDay the day of the week of the next due date
     * @param tentativeWeek the week of the month of the next due date
     * @param priorDueDate the due date that this interval is starting from
     *
     * @return the next due date based on {@code tentativeDay} and
     * {@code tentativeWeek}
     */
    private LocalDate computeNextDueDate(
            WeekDays tentativeDay, int tentativeWeek, LocalDate priorDueDate) {
        TemporalAdjuster adjustment = TemporalAdjusters.dayOfWeekInMonth(
                (tentativeWeek < 4) ? (tentativeWeek + 1) : -1,
                tentativeDay.getJavaDay());
        LocalDate startOfMonth = priorDueDate.withDayOfMonth(1);
        LocalDate candiDate = startOfMonth.with(adjustment);
        if (candiDate.isAfter(priorDueDate))
            return candiDate;
        return startOfMonth.plusMonths(increment).with(adjustment);
    }

    @Override
    public LocalDate computeNextDueDate(
            @NonNull LocalDate priorDueDate, @NonNull LocalDate completed) {
        // Compute the next due date from each of the month's days
        // of weeks independently.
        LocalDate candiDate1 = computeNextDueDate(day, week, priorDueDate);
        LocalDate candiDate2 = computeNextDueDate(day2, week2, priorDueDate);
        // Return the next due date in order from between these two
        return checkEndDate(priorDueDate,
                candiDate1.isBefore(candiDate2) ? candiDate1 : candiDate2);
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
        sb.append(" and ");
        formatDay(sb, week2, day2);
        sb.append(" of the month");
        if (end != null) {
            sb.append(", ending ").append(end);
        }
        sb.append(')');
        return sb.toString();
    }

    @Override
    @NonNull
    public RepeatSemiMonthlyOnDays clone() {
        return (RepeatSemiMonthlyOnDays) super.clone();
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
        if (!(o instanceof RepeatSemiMonthlyOnDays))
            return false;
        RepeatSemiMonthlyOnDays other = (RepeatSemiMonthlyOnDays) o;
        if (day2 != other.day2)
            return false;
        if (week2 != other.week2)
            return false;
        return true;
    }

}
