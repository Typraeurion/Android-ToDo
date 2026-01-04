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

import static com.xmission.trevin.android.todo.data.repeat.RepeatDailyTests.randomDays;
import static com.xmission.trevin.android.todo.data.repeat.RepeatMonthlyOnDateTests.adjustTarget;
import static org.junit.Assert.*;

import org.junit.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Tests for the semimonthly by date repeating interval.
 *
 * @author Trevin Beattie
 */
public class RepeatSemiMonthlyOnDatesTests {

    private static final Random RAND = new Random();

    /**
     * Formatter for dates to show the day of the week.
     */
    public static final DateTimeFormatter DAY_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);

    /**
     * Test a semimonthly repeating interval, allowed on all days (the
     * default), with no specified end date.  The new due date should be
     * either of the configured dates regardless of when the item was
     * completed.  This case only looks at the first 28 days of the month.
     */
    @Test
    public void testRepeatSemiMonthlyWithoutRestriction() {
        int[] dates = new int[2];
        dates[0] = RAND.nextInt(28) + 1;
        dates[1] = dates[0] + ((dates[0] <= 14) ? 14 : -14);
        LocalDate startDate = LocalDate.of(2026, 1, dates[0]);
        RepeatSemiMonthlyOnDates repeat =
                new RepeatSemiMonthlyOnDates(startDate);
        repeat.setDate(dates[0]);
        repeat.setDate2(dates[1]);
        for (int i = 1; i <= 25; i++) {
            LocalDate completed = startDate.plusDays(RAND.nextInt(100));
            LocalDate expectedDue = startDate.withDayOfMonth(dates[i%2]);
            if (dates[i%2] < dates[(i+1)%2])
                expectedDue = expectedDue.plusMonths(1);
            LocalDate actualDue = repeat
                    .computeNextDueDate(startDate, completed);
            assertEquals(String.format(
                            "Next due date for task due %s (alternating"
                                    + " %d - %d), completed %s",
                            startDate.format(DAY_FORMAT), dates[0],
                            dates[1], completed.format(DAY_FORMAT)),
                    expectedDue, actualDue);
            if (actualDue == null)
                // Technically unreachable, but the compiler can't tell
                break;
            startDate = actualDue;
        }
    }

    /**
     * Test a semimonthly repeating interval with one of its due dates on
     * the 31st (end of each month) and no specified end date.  Every other
     * new due date should always be the last day of the month regardless
     * of which month it is.
     */
    @Test
    public void testRepeatSemiMonthlyAlternatingLastDay() {
        int[] dates = new int[2];
        dates[0] = RAND.nextInt(21) + 4;
        dates[1] = 31;
        LocalDate startDate = LocalDate.of(2026, 1, dates[0]);
        RepeatSemiMonthlyOnDates repeat =
                new RepeatSemiMonthlyOnDates(startDate);
        repeat.setDate(dates[0]);
        repeat.setDate2(dates[1]);
        for (int i = 1; i <= 25; i++) {
            LocalDate completed = startDate.plusDays(RAND.nextInt(100));
            LocalDate expectedDue = startDate.withDayOfMonth(Math.min(
                    dates[i%2], startDate.lengthOfMonth()));
            if (dates[i%2] < dates[(i+1)%2])
                expectedDue = expectedDue.plusMonths(1);
            LocalDate actualDue = repeat
                    .computeNextDueDate(startDate, completed);
            assertEquals(String.format(
                            "Next due date for task due %s (alternating"
                                    + " %d - %d), completed %s",
                            startDate.format(DAY_FORMAT), dates[0],
                            dates[1], completed.format(DAY_FORMAT)),
                    expectedDue, actualDue);
            if (actualDue == null)
                // Technically unreachable, but the compiler can't tell
                break;
            startDate = actualDue;
        }
    }

    /**
     * Test a semimonthly repeating interval, allowed on all days (the
     * default), with a specified end date.  The new due date should be
     * either of the configured dates regardless of when the item was
     * completed until it passes the end date at which point the interval
     * should return {@code null}.
     */
    @Test
    public void testRepeatSemiMonthlyWithEnd() {
        int[] dates = new int[2];
        dates[0] = RAND.nextInt(28) + 1;
        dates[1] = dates[0] + ((dates[0] <= 14)
                ? (RAND.nextInt(7) + 7) : -(RAND.nextInt(7) + 7));
        LocalDate startDate = LocalDate.of(2026, 1, dates[0]);
        LocalDate endDate = startDate.plusMonths(RAND.nextInt(6) + 4);
        RepeatSemiMonthlyOnDates repeat =
                new RepeatSemiMonthlyOnDates(startDate);
        repeat.setDate(dates[0]);
        repeat.setDate2(dates[1]);
        repeat.setEnd(endDate);
        for (int i = 1; i <= 25; i++) {
            LocalDate completed = startDate.plusDays(RAND.nextInt(100));
            LocalDate expectedDue = startDate.withDayOfMonth(dates[i%2]);
            if (dates[i%2] < dates[(i+1)%2])
                expectedDue = expectedDue.plusMonths(1);
            if (expectedDue.isAfter(endDate))
                expectedDue = null;
            LocalDate actualDue = repeat
                    .computeNextDueDate(startDate, completed);
            assertEquals(String.format(
                            "Next due date for task due %s (alternating"
                                    + " %d - %d), completed %s, ending %s",
                            startDate.format(DAY_FORMAT), dates[0],
                            dates[1], completed.format(DAY_FORMAT),
                            endDate.format(DAY_FORMAT)),
                    expectedDue, actualDue);
            if (actualDue == null)
                break;
            startDate = actualDue;
        }
    }

    /**
     * Test an every <i>N</i> months semimonthly interval, allowed on all
     * days, with no specified end date.  The new due date should use
     * each of the configured dates (in order) in the same month
     * before advancing <i>N</i> months ahead, regardless of when the item
     * was completed.
     */
    @Test
    public void testRepeatSemiMonthlyEveryNMonths() {
        int[] dates = new int[2];
        dates[0] = RAND.nextInt(28) + 1;
        dates[1] = dates[0] + ((dates[0] <= 14)
                ? (RAND.nextInt(7) + 7) : -(RAND.nextInt(7) + 7));
        LocalDate startDate = LocalDate.of(2026, 1, dates[0]);
        int increment = RAND.nextInt(6) + 2;
        RepeatSemiMonthlyOnDates repeat =
                new RepeatSemiMonthlyOnDates(startDate);
        repeat.setDate(dates[0]);
        repeat.setDate2(dates[1]);
        repeat.setIncrement(increment);
        for (int i = 1; i <= 25; i++) {
            LocalDate completed = startDate.plusDays(RAND.nextInt(100));
            LocalDate expectedDue = startDate.withDayOfMonth(dates[i%2]);
            if (dates[i%2] < dates[(i+1)%2])
                expectedDue = expectedDue.plusMonths(increment);
            LocalDate actualDue = repeat
                    .computeNextDueDate(startDate, completed);
            assertEquals(String.format(
                            "Next due date for task due %s (alternating"
                                    + " %d - %d every %d months),"
                                    + " completed %s",
                            startDate.format(DAY_FORMAT),
                            Math.min(dates[0], dates[1]),
                            Math.max(dates[0], dates[1]), increment,
                            completed.format(DAY_FORMAT)),
                    expectedDue, actualDue);
            if (actualDue == null)
                break;
            startDate = actualDue;
        }
    }

    /**
     * Test an every <i>N</i> months repeating interval allowed on a subset
     * of days of the week.  The new due date should be on an allowed day
     * roughly near the target due date; the exact day depends on the
     * search direction.  If testing with an end date and the next due
     * date would be after the end date, we expect the interval to
     * return {@code null}.
     *
     * @param dates the dates of the month that the interval should repeat
     * @param direction the direction to look for the next allowed day
     * @param increment the number of months to advance after the
     * previous due date (if it&rsquo;s the second due date in the month)
     * @param withEnd whether to stop repeating after an end date
     */
    public void testRepeatSemiMonthlyEveryNMonthsWithRestrictions(
            int[] dates, WeekdayDirection direction,
            int increment, boolean withEnd) {
        Set<WeekDays> allowed = randomDays();
        LocalDate targetDate = LocalDate.of(2026, 1, dates[0]);
        LocalDate startDate = adjustTarget(targetDate, allowed, direction);
        RepeatSemiMonthlyOnDates repeat =
                new RepeatSemiMonthlyOnDates(startDate);
        repeat.setDate(dates[0]);
        repeat.setDate2(dates[1]);
        repeat.setIncrement(increment);
        repeat.setAllowedWeekDays(allowed);
        repeat.setDirection(direction);
        LocalDate endDate = withEnd ? startDate.plusMonths(
                RAND.nextInt(15) + 10) : null;
        if (withEnd)
            repeat.setEnd(endDate);
        for (int i = 1; i <= 50; i++) {
            LocalDate completed = startDate.plusDays(RAND.nextInt(32));
            targetDate = targetDate.withDayOfMonth(Math.min(dates[i%2],
                    targetDate.lengthOfMonth()));
            if (dates[i%2] < dates[(i+1)%2])
                targetDate = targetDate.plusMonths(increment);
            LocalDate expectedDate =
                    adjustTarget(targetDate, allowed, direction);
            if (withEnd && expectedDate.isAfter(endDate))
                expectedDate = null;
            LocalDate actualDue = repeat
                    .computeNextDueDate(startDate, completed);
            StringBuffer message = new StringBuffer(
                    "Next due date for task due ")
                    .append(startDate.format(DAY_FORMAT));
            message.append(" (").append(direction)
                    .append(' ').append(allowed);
            message.append(" alternating ")
                    .append(Math.min(dates[0], dates[1])).append(" - ")
                    .append(Math.max(dates[0], dates[1]));
            message.append(" every ").append(increment).append(" months)");
            message.append(", completed ")
                    .append(completed.format(DAY_FORMAT));
            if (withEnd)
                message.append(", ending ")
                        .append(endDate.format(DAY_FORMAT));
            assertEquals(message.toString(), expectedDate, actualDue);
            if (actualDue == null)
                break;
            startDate = actualDue;
        }
    }

    /**
     * Test a semimonthly repeating interval targeting the last day of the
     * month (and some other random day), allowed on the next instance of
     * a subset of days of the week, no end date.  There should be instances
     * where the next allowed date falls around the start of the next month,
     * which should not preclude the next due date from occurring on the
     * first <i>designated</i> day of that same month.
     */
    @Test
    public void testRepeatSemiMonthlyLastDayRestrictedByDaysNext() {
        testRepeatSemiMonthlyEveryNMonthsWithRestrictions(
                new int[]{ 31, RAND.nextInt(21) + 1 },
                WeekdayDirection.NEXT, 1, false);
    }

    /**
     * Test a semimonthly repeating interval targeting the first day of the
     * month (and some other random day), allowed on the previous instance
     * of a subset of days of the week, no end date.  There should be
     * instances where the next allowed date falls around the end of the
     * previous month; the next due date should still occur on the
     * <i>second</i> designated day of the original month.
     */
    @Test
    public void testRepeatSemiMonthlyFirstDayRestrictedByDaysPrevious() {
        testRepeatSemiMonthlyEveryNMonthsWithRestrictions(
                new int[]{ 1, RAND.nextInt(21) + 8 },
                WeekdayDirection.PREVIOUS, 1, false);
    }

    /**
     * Test a semimonthly repeating interval targeting two days around the
     * middle of the month, allowed on the closest instance of a subset
     * of days of the week (ties broken by next), with an end date.
     */
    @Test
    public void testRepeatSemiMonthlyRestrictedByDaysClosestOrNextWithEnd() {
        testRepeatSemiMonthlyEveryNMonthsWithRestrictions(
                new int[]{ RAND.nextInt(7) + 8, RAND.nextInt(7) + 21 },
                WeekdayDirection.CLOSEST_OR_NEXT, 1, true);
    }

    /**
     * Test a semimonthly repeating interval targeting two days around the
     * middle of the month, allowed on the closest instance of a subset
     * of days of the week (ties broken by previous), with an end date.
     */
    @Test
    public void testRepeatSemiMonthlyRestrictedByDaysClosestOrPreviousWithEnd() {
        testRepeatSemiMonthlyEveryNMonthsWithRestrictions(
                new int[]{ RAND.nextInt(7) + 8, RAND.nextInt(7) + 21 },
                WeekdayDirection.CLOSEST_OR_PREVIOUS, 1, true);
    }

    /**
     * Test an every <i>N</i> months semimonthly repeating interval
     * targeting the first and last weeks of the month, allowed on the next
     * instance of a subset of days of the week, with an end date.
     */
    @Test
    public void testRepeatSemiMonthlyEveryNMonthsRestrictedByDaysNextWithEnd() {
        testRepeatSemiMonthlyEveryNMonthsWithRestrictions(
                new int[]{ RAND.nextInt(7) + 1, RAND.nextInt(7) + 25 },
                WeekdayDirection.NEXT, RAND.nextInt(6) + 2, true);
    }

    /**
     * Test an every <i>N</i> months semimonthly repeating interval
     * targeting the first and last weeks of the month, allowed on the
     * previous instance of a subset of days of the week, with an end date.
     */
    @Test
    public void testRepeatSemiMonthlyEveryNMonthsRestrictedByDaysPreviousWithEnd() {
        testRepeatSemiMonthlyEveryNMonthsWithRestrictions(
                new int[]{ RAND.nextInt(7) + 1, RAND.nextInt(7) + 25 },
                WeekdayDirection.PREVIOUS, RAND.nextInt(6) + 2, true);
    }

    /**
     * Test an every <i>N</i> months semimonthly repeating interval, allowed
     * on the closest instance of a subset of days of the week (ties broken
     * by next), no end date.
     */
    @Test
    public void testRepeatSemiMonthlyEveryNMonthsRestrictedByDaysClosestOrNext() {
        int[] dates = new int[2];
        dates[0] = RAND.nextInt(31) + 1;
        do {
            dates[1] = RAND.nextInt(31) + 1;
            // Make sure the dates are more than a week apart,
            // in either direction.
        } while ((Math.abs(dates[0] - dates[1]) < 7) ||
                (Math.abs(dates[0] - dates[1]) > 21));
        testRepeatSemiMonthlyEveryNMonthsWithRestrictions(dates,
                WeekdayDirection.CLOSEST_OR_NEXT, RAND.nextInt(6) + 2, false);
    }

    /**
     * Test an every <i>N</i> months semimonthly repeating interval, allowed
     * on the closest instance of a subset of days of the week (ties broken
     * by previous), no end date.
     */
    @Test
    public void testRepeatSemiMonthlyEveryNMonthsRestrictedByDaysClosestOrPrevious() {
        int[] dates = new int[2];
        dates[0] = RAND.nextInt(31) + 1;
        do {
            dates[1] = RAND.nextInt(31) + 1;
            // Make sure the dates are more than a week apart,
            // in either direction.
        } while ((Math.abs(dates[0] - dates[1]) < 7) ||
                (Math.abs(dates[0] - dates[1]) > 21));
        testRepeatSemiMonthlyEveryNMonthsWithRestrictions(dates,
                WeekdayDirection.CLOSEST_OR_PREVIOUS, RAND.nextInt(6) + 2, false);
    }

}
