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
import static org.junit.Assert.*;

import org.junit.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Tests for the monthly by date repeating interval.
 *
 * @author Trevin Beattie
 */
public class RepeatMonthlyOnDateTests {

    private static final Random RAND = new Random();

    /**
     * Formatter for dates to show the day of the week.
     */
    public static final DateTimeFormatter DAY_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);

    /**
     * Test a monthly repeating interval, allowed on all days (the default),
     * with no specified end date.  The new due date should be the same
     * date regardless of when the item was completed.  This case only
     * looks at the first 28 days of the month.
     */
    @Test
    public void testRepeatMonthlyWithoutRestriction() {
        LocalDate startDate = LocalDate.of(2026, 1, RAND.nextInt(28) + 1);
        RepeatMonthlyOnDate repeat = new RepeatMonthlyOnDate(startDate);
        repeat.setDate(startDate.getDayOfMonth());
        for (int i = 0; i < 10; i++) {
            LocalDate completed = startDate.plusDays(RAND.nextInt(100));
            LocalDate expectedDue = startDate.plusMonths(1);
            LocalDate actualDue = repeat
                    .computeNextDueDate(startDate, completed);
            assertEquals(String.format(
                            "Next due date for task due %s, completed %s",
                            startDate.format(DAY_FORMAT),
                            completed.format(DAY_FORMAT)),
                    expectedDue, actualDue);
            if (actualDue == null)
                // Technically unreachable, but the compiler can't tell
                break;
            startDate = actualDue;
        }
    }

    /**
     * Test a monthly repeating interval with a due date of the 31st
     * and no specified end date.  The new due date should always be the
     * last day of the month regardless of which month it is.
     */
    @Test
    public void testRepeatMonthlyOnLastDay() {
        LocalDate startDate = LocalDate.of(2026, 1, 31);
        RepeatMonthlyOnDate repeat = new RepeatMonthlyOnDate(startDate);
        repeat.setDate(31);
        // Make sure we cover a leap year
        for (int i = 0; i < 50; i++) {
            LocalDate completed = startDate.plusDays(RAND.nextInt(100));
            LocalDate expectedDue = startDate.plusMonths(1);
            expectedDue = expectedDue.withDayOfMonth(expectedDue.lengthOfMonth());
            LocalDate actualDue = repeat
                    .computeNextDueDate(startDate, completed);
            assertEquals(String.format(
                            "Next due date (at the end of the month)"
                                    + " for task due %s, completed %s",
                            startDate.format(DAY_FORMAT),
                            completed.format(DAY_FORMAT)),
                    expectedDue, actualDue);
            if (actualDue == null)
                // Technically unreachable, but the compiler can't tell
                break;
            startDate = actualDue;
        }
    }

    /**
     * Test a monthly repeating interval, allowed on all days (the default),
     * with a specified end date.  The new due date should be the same
     * date regardless of when the item was completed, until it passes
     * the end date at which point the interval should return {@code null}.
     */
    @Test
    public void testRepeatMonthlyWithEnd() {
        LocalDate startDate = LocalDate.of(2026, 1, RAND.nextInt(28) + 1);
        LocalDate endDate = startDate.plusMonths(RAND.nextInt(6) + 4);
        RepeatMonthlyOnDate repeat = new RepeatMonthlyOnDate(startDate);
        repeat.setDate(startDate.getDayOfMonth());
        repeat.setEnd(endDate);
        for (int i = 0; i < 12; i++) {
            LocalDate completed = startDate.plusDays(RAND.nextInt(100));
            LocalDate expectedDue = startDate.plusMonths(1);
            if (expectedDue.isAfter(endDate))
                expectedDue = null;
            LocalDate actualDue = repeat
                    .computeNextDueDate(startDate, completed);
            assertEquals(String.format(
                            "Next due date for task due %s,"
                                    + " completed %s, ending %s",
                            startDate.format(DAY_FORMAT),
                            completed.format(DAY_FORMAT),
                            endDate.format(DAY_FORMAT)),
                    expectedDue, actualDue);
            if (actualDue == null)
                break;
            startDate = actualDue;
        }
    }

    /**
     * Test an every <i>N</i> months repeating interval, allowed on all days,
     * with no specified end date.  The new due date should be the same
     * date regardless of when the item was completed.
     */
    @Test
    public void testRepeatEveryNMonthsWithoutRestriction() {
        LocalDate startDate = LocalDate.of(2026, 1, RAND.nextInt(28) + 1);
        int increment = RAND.nextInt(100) + 2;
        RepeatMonthlyOnDate repeat = new RepeatMonthlyOnDate(startDate);
        repeat.setDate(startDate.getDayOfMonth());
        repeat.setIncrement(increment);
        for (int i = 0; i < 10; i++) {
            LocalDate completed = startDate.plusDays(RAND.nextInt(100));
            LocalDate expectedDue = startDate.plusMonths(increment);
            LocalDate actualDue = repeat
                    .computeNextDueDate(startDate, completed);
            assertEquals(String.format(
                            "Next due date for task due %s,"
                                    + " every %d months, completed %s",
                            startDate.format(DAY_FORMAT), increment,
                            completed.format(DAY_FORMAT)),
                    expectedDue, actualDue);
            if (actualDue == null)
                // Technically unreachable, but the compiler can't tell
                break;
            startDate = actualDue;
        }
    }

    /**
     * Adjust an expected target date by the available days of the week,
     * using the specified direction.
     */
    public static LocalDate adjustTarget(
            LocalDate target,
            Set<WeekDays> allowed,
            WeekdayDirection direction) {
        if (allowed.contains(WeekDays.fromJavaDay(target.getDayOfWeek())))
            return target;
        LocalDate nextAllowed = target.plusDays(1);
        LocalDate previousAllowed = target.minusDays(1);
        while (!allowed.contains(WeekDays.fromJavaDay(
                nextAllowed.getDayOfWeek())))
            nextAllowed = nextAllowed.plusDays(1);
        while (!allowed.contains(WeekDays.fromJavaDay(
                previousAllowed.getDayOfWeek())))
            previousAllowed = previousAllowed.minusDays(1);
        switch (direction) {
            case NEXT: return nextAllowed;
            case PREVIOUS: return previousAllowed;
            case CLOSEST_OR_NEXT:
                if (target.until(nextAllowed).getDays() <=
                        previousAllowed.until(target).getDays())
                    return nextAllowed;
                else
                    return previousAllowed;
            case CLOSEST_OR_PREVIOUS:
                if (previousAllowed.until(target).getDays() <=
                        target.until(nextAllowed).getDays())
                    return previousAllowed;
                else
                    return nextAllowed;
        }
        // Unreachable
        return target;
    }

    /**
     * Test an every <i>N</i> months repeating interval allowed on a
     * subset of days of the week.  The new due date should be on an
     * allowed day roughly near the target due date; the exact day
     * depends on the search direction.  If testing
     * with an end date and the next due date would be after the end date,
     * we expect the interval to return {@code null}.
     *
     * @param date the date of the month that the interval should repeat
     * @param direction the direction to look for the next allowed day
     * @param increment the number of months to advance after the
     * previous due date
     * @param withEnd whether to stop repeating after an end date
     */
    public void testRepeatEveryNMonthsWithRestrictions(
            int date, WeekdayDirection direction,
            int increment, boolean withEnd) {
        Set<WeekDays> allowed = randomDays();
        LocalDate targetDate = LocalDate.of(2026, 1, date);
        // Adjust the initial start date if needed; this should
        // NOT change the target date of the repeat interval.
        LocalDate startDate = adjustTarget(targetDate, allowed, direction);
        RepeatMonthlyOnDate repeat = new RepeatMonthlyOnDate(startDate);
        repeat.setDate(date);
        repeat.setIncrement(increment);
        repeat.setAllowedWeekDays(allowed);
        repeat.setDirection(direction);
        LocalDate endDate = withEnd ? targetDate.plusMonths(
                RAND.nextInt(15) + 10) : null;
        if (withEnd)
            repeat.setEnd(endDate);
        for (int i = 0; i < 50; i++) {
            LocalDate completed = startDate.plusDays(RAND.nextInt(32));
            targetDate = targetDate.plusMonths(increment);
            targetDate = targetDate.withDayOfMonth(Math.min(date,
                    targetDate.lengthOfMonth()));
            LocalDate expectedDue =
                    adjustTarget(targetDate, allowed, direction);
            if (withEnd && expectedDue.isAfter(endDate))
                expectedDue = null;
            LocalDate actualDue = repeat
                    .computeNextDueDate(startDate, completed);
            StringBuffer message = new StringBuffer(
                    "Next due date for task due ")
                    .append(startDate.format(DAY_FORMAT)).append(" every ");
            if (increment > 1)
                message.append(increment).append(" months");
            else
                message.append("month");
            message.append(" on the ").append(direction).append(' ')
                    .append(allowed).append(" to ").append(date);
            message.append(", completed ")
                    .append(completed.format(DAY_FORMAT));
            if (withEnd)
                message.append(", ending ")
                        .append(endDate.format(DAY_FORMAT));
            assertEquals(message.toString(), expectedDue, actualDue);
            if (actualDue == null)
                break;
            startDate = actualDue;
        }
    }

    /**
     * Test a monthly repeating interval targeting the last day of the
     * month, allowed on the next instance of a subset of days of the
     * week, no end date.  There should be instances where the next
     * allowed date falls around the start of the next month, which
     * should not preclude the next due date from occurring on the
     * last day of that same month.
     */
    @Test
    public void testRepeatMonthlyLastDayRestrictedByDaysNext() {
        testRepeatEveryNMonthsWithRestrictions(
                31, WeekdayDirection.NEXT, 1, false);
    }

    /**
     * Test a monthly repeating interval targeting the first day of the
     * month, allowed on the previous instance of a subset of days of the
     * week, no end date.  There should be instances where the next allowed
     * date falls around the end of the previous month; the next due date
     * should still occur on the first day of the month following the
     * (disallowed) first day of the original month.
     */
    @Test
    public void testRepeatMonthlyFirstDayRestrictedByDaysPrevious() {
        testRepeatEveryNMonthsWithRestrictions(
                1, WeekdayDirection.PREVIOUS, 1, false);
    }

    /**
     * Test a monthly repeating interval targeting the middle of the month,
     * allowed on the closest instance of a subset of days of the week
     * (ties broken by next), with an end date.
     */
    @Test
    public void testRepeatMonthlyRestrictedByDaysClosestOrNextWithEnd() {
        testRepeatEveryNMonthsWithRestrictions(RAND.nextInt(14) + 7,
                WeekdayDirection.CLOSEST_OR_NEXT, 1, true);
    }

    /**
     * Test a monthly repeating interval targeting the middle of the month,
     * allowed on the closest instance of a subset of days of the week
     * (ties broken by previous), with an end date.
     */
    @Test
    public void testRepeatMonthlyRestrictedByDaysClosestOrPreviousWithEnd() {
        testRepeatEveryNMonthsWithRestrictions(RAND.nextInt(14) + 7,
                WeekdayDirection.CLOSEST_OR_PREVIOUS, 1, true);
    }

    /**
     * Test an every <i>N</i> months repeating interval targeting the last
     * week of the month, allowed on the next instance of a subset of days
     * of the week, with an end date.
     */
    @Test
    public void testRepeatEveryNMonthsRestrictedByDaysNextWithEnd() {
        testRepeatEveryNMonthsWithRestrictions(RAND.nextInt(7) + 25,
                WeekdayDirection.NEXT, RAND.nextInt(6) + 2, true);
    }

    /**
     * Test an every <i>N</i> months repeating interval targeting the first
     * week of the month, allowed on the next instance of a subset of days
     * of the week, with an end date.
     */
    @Test
    public void testRepeatEveryNMonthsRestrictedByDaysPreviousWithEnd() {
        testRepeatEveryNMonthsWithRestrictions(RAND.nextInt(7) + 1,
                WeekdayDirection.PREVIOUS, RAND.nextInt(6) + 2, true);
    }

    /**
     * Test an every <i>N</i> months repeating interval, allowed on the
     * closest instance of a subset of days of the week (ties broken by
     * next), no end date.
     */
    @Test
    public void testRepeatEveryNMonthsRestrictedByDaysClosestOrNext() {
        testRepeatEveryNMonthsWithRestrictions(RAND.nextInt(31) + 1,
                WeekdayDirection.CLOSEST_OR_NEXT, RAND.nextInt(6) + 2, false);
    }

    /**
     * Test an every <i>N</i> months repeating interval, allowed on the
     * closest instance of a subset of days of the week (ties broken by
     * previous), no end date.
     */
    @Test
    public void testRepeatEveryNMonthsRestrictedByDaysClosestOrPrevious() {
        testRepeatEveryNMonthsWithRestrictions(RAND.nextInt(31) + 1,
                WeekdayDirection.CLOSEST_OR_PREVIOUS, RAND.nextInt(6) + 2, false);
    }

}
