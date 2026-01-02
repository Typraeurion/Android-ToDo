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

    private LocalDate setDateAndAdjust(LocalDate base, int d) {
        LocalDate hardDate = base.withDayOfMonth(
                Math.min(d, base.lengthOfMonth()));
        return adjustDueDate(hardDate);
    }

    /**
     * Compute a potential due date based on one of the dates set for
     * this repeat interval.  Each date needs the same calculation
     * before we decide which one to use.  The logic is:
     * <ol>
     *     <li>Start from the given {@code tentativeDate}
     *     in the same month as {@code priorDueDate}.</li>
     *     <li>Adjust the date based on the available days of the week
     *     and adjustment direction.</li>
     *     <li>If the result is not after {@code priorDueDate},
     *     re-compute it based on the next month after it
     *     and compare the <i>months</i> before and after
     *     the day-of-week adjustment.</li>
     *     <ul>
     *         <li>If the adjustment put the result in a different
     *         month, add {@code increment + 1} months to the
     *         {@code priorDueDate} and then re-compute the new date.</li>
     *         <li>If the adjustment stays in the same month, add the
     *         normal {@code increment} months to the {@code priorDueDate}
     *         and re-compute to get the next due date.</li>
     *     </ul>
     * </ol>
     *
     * @param tentativeDate the date for which to compute the next due date
     * @param priorDueDate the due date that this interval is starting from
     *
     * @return the next due date based on {@code tentativeDate}
     */
    private LocalDate computeNextDueDate(
            int tentativeDate, LocalDate priorDueDate) {
        LocalDate candiDate = adjustDueDate(priorDueDate.withDayOfMonth(
                Math.min(tentativeDate, priorDueDate.lengthOfMonth())));
        if (candiDate.isAfter(priorDueDate))
            return candiDate;
        // If the candidate is before the prior due date,
        // tentatively advance by 1 month to check whether
        // an adjustment will cross back to the previous month.
        LocalDate nextMonth = priorDueDate.plusMonths(1);
        candiDate = setDateAndAdjust(nextMonth, tentativeDate);
        if (candiDate.withDayOfMonth(1)
                .isBefore(nextMonth.withDayOfMonth(1))) {
            // Confirmed month crossing; increment
            // 1 month more than the normal increment.
            Log.d(getClass().getSimpleName(), String.format(
                    "Adjustment for %s crossed back to %s;"
                            + " advancing %d months",
                    nextMonth, candiDate, increment + 1));
            candiDate = setDateAndAdjust(
                    nextMonth.plusMonths(increment), tentativeDate);
        } else if (increment > 1) {
            // No month crossing; use the normal increment
            candiDate = setDateAndAdjust(
                    priorDueDate.plusMonths(increment), tentativeDate);
        }
        return checkEndDate(priorDueDate, candiDate);
    }

    @Override
    public LocalDate computeNextDueDate(
            @NonNull LocalDate priorDueDate, @NonNull LocalDate completed) {
        // Compute the next due date from each of the month's dates
        // independently, including any adjustments for day of the week.
        LocalDate candiDate1 = computeNextDueDate(date, priorDueDate);
        LocalDate candiDate2 = computeNextDueDate(date2, priorDueDate);
        // Return the next due date in order from between these two
        return candiDate1.isBefore(candiDate2) ? candiDate1 : candiDate2;
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
