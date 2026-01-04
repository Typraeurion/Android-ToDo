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

import static com.xmission.trevin.android.todo.provider.ToDoSchema.ToDoItemColumns.REPEAT_SEMI_MONTHLY_ON_DATES;

import android.util.Log;

import androidx.annotation.NonNull;

import java.time.LocalDate;

/**
 * Repeating interval for a monthly To Do item that occurs on two
 * specific days of the month (e.g. the 1st and 15th of every month,
 * or alternatively those two days every {@code increment} months).
 *
 * @author Trevin Beattie
 */
public class RepeatSemiMonthlyOnDates extends AbstractDateRepeat {

    private static final long serialVersionUID = 5;

    public static final int ID = REPEAT_SEMI_MONTHLY_ON_DATES;

    /** The second date on which this item repeats */
    protected int date2;

    /**
     * Create a default RepeatSemiMonthlyOnDates object
     * for the current day and a second date &plusmn; 15 days away.
     */
    public RepeatSemiMonthlyOnDates() {
        this(WeekDays.DAYS_BIT_MASK |
                WeekdayDirection.NEXT.getValue(), LocalDate.now());
    }

    /**
     * Create a default RepeatSemiMonthlyOnDates object for a given
     * due date and a second date &plusmn; 15 days from that.
     *
     * @param due the first date on which this To Do item is due
     */
    public RepeatSemiMonthlyOnDates(@NonNull LocalDate due) {
        this(WeekDays.DAYS_BIT_MASK |
                WeekdayDirection.NEXT.getValue(), due);
    }

    /**
     * Create a RepeatSemiMonthlyOnDates object with the days of the week
     * and direction given by a bit mask (i.e. from the database).
     * The due dates will be based on today and the date &plusmn;
     * 15 days from today (in order).
     *
     * @param bitMask the bit field containing the allowed days
     *                on which this item can be repeated
     *
     * @throws IllegalArgumentException if the bit field
     * (masked by all possible days) is 0
     */
    public RepeatSemiMonthlyOnDates(int bitMask) {
        this(bitMask, LocalDate.now());
    }

    /**
     * Create a RepeatSemiMonthlyOnDates object with the days of the week
     * and direction given by a bit mask (i.e. from the database)
     * and a given due date.  The dates will be based on this due date
     * and a date &plusmn; 15 days from that, in order.
     *
     * @param bitMask the bit field containing the allowed days
     *                on which this item can be repeated
     * @param due the first date on which this To Do item is due
     *
     * @throws IllegalArgumentException if the bit field
     * (masked by all possible days) is 0
     */
    public RepeatSemiMonthlyOnDates(int bitMask, @NonNull LocalDate due) {
        super(REPEAT_SEMI_MONTHLY_ON_DATES, bitMask, due);
        if (date < 16) {
            date2 = date + 15;
        } else {
            date2 = date;
            date = date2 - 15;
        }
    }

    /** @return the second date on which this item repeats */
    public int getDate2() {
        return date2;
    }

    /**
     * Set the second date on which this item repeats.  The date must be
     * in the range 1–31; this class does not check whether the date
     * is valid for any particular month nor whether it conflicts
     * with the first date; that needs to be handled in the UI.
     *
     * @param date the date to set
     *
     * @throws IllegalArgumentException if {@code date} is less than 1
     * or greater than 31
     */
    public void setDate2(int date) {
        if ((date < 1) || (date > 31))
            throw new IllegalArgumentException("Invalid date: " + date);
        date2 = date;
    }

    @Override
    public boolean updateForDueDate(@NonNull LocalDate newDue) {
        // We have two dates to possibly match; check both.
        int newDate1 = newDue.getDayOfMonth();
        int newDate2;
        if (newDate1 == date) {
            newDate2 = date2;
        } else if (newDate1 == date2) {
            // We're reversing the dates; necessary in order for the
            // super class to properly update the allowed week days.
            newDate2 = date;
        } else {
            newDate2 = (newDate1 < 16) ? (newDate1 + 15) : (newDate1 - 15);
        }
        boolean datesChanged = (newDate1 != date);
        if (datesChanged) {
            date = newDate1;
            date2 = newDate2;
        }
        boolean superChanged = super.updateForDueDate(newDue);
        return datesChanged || superChanged;
    }

    /**
     * Given a prior due date, find the matching target date.
     * If the date matches one of our configured dates (allowing
     * for the number of days in the month), it is returned unchanged.
     * Otherwise we need to find the most likely date we would have
     * come from given the search direction for allowed dates.
     */
    private LocalDate targetDateOf(LocalDate priorDueDate) {
        int date = priorDueDate.getDayOfMonth();
        int lastDay = priorDueDate.lengthOfMonth();
        if ((date == Math.min(this.date, lastDay)) ||
                (date == Math.min(date2, lastDay)))
            return priorDueDate;
        switch (direction) {
            case NEXT:
                // Use the closest date preceding the due date
                if (date > this.date) {
                    if (date > date2)
                        return priorDueDate.withDayOfMonth(
                                Math.max(this.date, date2));
                    return priorDueDate.withDayOfMonth(this.date);
                } else {
                    if (date > date2)
                        return priorDueDate.withDayOfMonth(date2);
                    // Target date should be in the prior month
                    LocalDate targetMonth = priorDueDate.minusMonths(1);
                    return targetMonth.withDayOfMonth(
                            Math.min(Math.max(this.date, date2),
                                    targetMonth.lengthOfMonth()));
                }
            case PREVIOUS:
                // Use the closest date following the due date
                if (date < this.date) {
                    if (date < date2)
                        return priorDueDate.withDayOfMonth(
                                Math.min(Math.min(this.date, date2),
                                        lastDay));
                    return priorDueDate.withDayOfMonth(
                            Math.min(this.date, lastDay));
                } else {
                    if (date < date2)
                        return priorDueDate.withDayOfMonth(
                                Math.min(date2, lastDay));
                    // Target date should be in the next month
                    LocalDate targetMonth = priorDueDate.plusMonths(1);
                    lastDay = targetMonth.lengthOfMonth();
                    return targetMonth.withDayOfMonth(
                            Math.min(Math.min(this.date, date2), lastDay));
                }
            case CLOSEST_OR_NEXT:
            case CLOSEST_OR_PREVIOUS:
                // Don't bother checking for ties in this case;
                // just find the closest date in either direction.
                // It can't be more than 3 days away.
                LocalDate target1 = priorDueDate.withDayOfMonth(
                        Math.min(this.date, lastDay));
                LocalDate target2 = priorDueDate.withDayOfMonth(
                        Math.min(date2, lastDay));
                int delta1 = Math.abs(priorDueDate.until(target1).getDays());
                if (delta1 > 15) {
                    if (date < 15)
                        target1 = target1.minusMonths(1);
                    else
                        target1 = target1.plusMonths(1);
                    lastDay = target1.lengthOfMonth();
                    target1 = target1.withDayOfMonth(
                            Math.min(this.date, lastDay));
                    delta1 = Math.abs(priorDueDate.until(target1).getDays());
                }
                int delta2 = Math.abs(priorDueDate.until(target2).getDays());
                if (delta2 > 15) {
                    if (date < 15)
                        target2 = target2.minusMonths(1);
                    else
                        target2 = target2.plusMonths(1);
                    lastDay = target2.lengthOfMonth();
                    target2 = target2.withDayOfMonth(
                            Math.min(date2, lastDay));
                    delta2 = Math.abs(priorDueDate.until(target2).getDays());
                }
                return (delta1 <= delta2) ? target1 : target2;
        }
        // Unreachable
        return priorDueDate;
    }

    @Override
    public LocalDate computeNextDueDate(
            @NonNull LocalDate priorDueDate, @NonNull LocalDate completed) {
        // Find the actual target date for the prior due date
        // without consideration of allowed days
        LocalDate oldTargetDate = targetDateOf(priorDueDate);
        int lastDay = oldTargetDate.lengthOfMonth();
        int minDay = Math.min(Math.min(date, date2), lastDay);
        int maxDay = Math.min(Math.max(date, date2), lastDay);
        LocalDate newTargetDate;
        // If it matches the earlier of the dates in this interval,
        // target the next date.
        if (oldTargetDate.getDayOfMonth() == minDay) {
            newTargetDate = oldTargetDate.withDayOfMonth(maxDay);
        }
        // If it matches the latter date, advance by our increment months.
        else {
            newTargetDate = oldTargetDate.plusMonths(increment);
            lastDay = newTargetDate.lengthOfMonth();
            minDay = Math.min(Math.min(date, date2), lastDay);
            newTargetDate = newTargetDate.withDayOfMonth(minDay);
        }
        return checkEndDate(priorDueDate, adjustDueDate(newTargetDate));
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
        sb.append(" and ");
        formatOrdinal(sb, date2);
        formatDays(sb);
        if (end != null) {
            sb.append(", ending ").append(end);
        }
        sb.append(')');
        return sb.toString();
    }

}
