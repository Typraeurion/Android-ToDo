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
 * Tests for the &ldquo;month after&rdquo; repeating interval.
 * This interval always goes a fixed number of months after
 * the item&rsquo;s last completion date rather than its
 * previous due date.
 *
 * @author Trevin Beattie
 */
public class RepeatMonthAfterTests {

    private static final Random RAND = new Random();

    /**
     * Formatter for dates to show the day of the week.
     */
    public static final DateTimeFormatter DAY_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);

    /**
     * Test a &ldquo;month after&rdquo; repeating interval, allowed on all
     * days (the default), with no specified end date.  The new due date
     * should always be the month after the item was completed, with
     * possible adjustments for different month lengths.
     */
    @Test
    public void testRepeatMonthAfterWithoutRestriction() {
        LocalDate startDate = LocalDate.now();
        RepeatMonthAfter repeat = new RepeatMonthAfter(startDate);
        for (int i = 0; i < 100; i++) {
            LocalDate completed = startDate.plusDays(RAND.nextInt(32));
            LocalDate expectedDue = completed.plusMonths(1);
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
     * Test a &ldquo;month after&rdquo; repeating interval, allowed on all
     * days (the default), with a specified end date.  The new due date
     * should always be the month after the item was completed, with
     * possible adjustments for different month lengths.
     */
    @Test
    public void testRepeatMonthAfterWithEnd() {
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusMonths(RAND.nextInt(30) + 20);
        RepeatMonthAfter repeat = new RepeatMonthAfter(startDate);
        repeat.setEnd(endDate);
        for (int i = 0; i < 100; i++) {
            LocalDate completed = startDate.plusDays(RAND.nextInt(32));
            LocalDate expectedDue = completed.plusMonths(1);
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
     * Test an <i>N</i> months-after repeating interval, allowed on all days,
     * with no specified end date.  The new due date should always be
     * <i>N</i> months after the item was completed, with possible
     * adjustments for different month lengths.
     */
    @Test
    public void testRepeatNMonthsAfterWithoutRestriction() {
        LocalDate startDate = LocalDate.now();
        int increment = RAND.nextInt(100) + 2;
        RepeatMonthAfter repeat = new RepeatMonthAfter(startDate);
        repeat.setIncrement(increment);
        for (int i = 0; i < 100; i++) {
            LocalDate completed = startDate.plusDays(RAND.nextInt(32));
            LocalDate expectedDue = completed.plusMonths(increment);
            LocalDate actualDue = repeat
                    .computeNextDueDate(startDate, completed);
            assertEquals(String.format(
                            "Next due date for task due %s,"
                                    + " %d months after completion on %s",
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
     * Test an <i>N</i> months-after repeating interval, allowed on all days,
     * with a specified end date.  The new due date should always be
     * <i>N</i> months after the item was completed, with possible
     * adjustments for different month lengths.
     */
    @Test
    public void testRepeatNMonthsAfterWithEnd() {
        LocalDate startDate = LocalDate.now();
        int increment = RAND.nextInt(100) + 2;
        LocalDate endDate = startDate.plusMonths(RAND.nextInt(30) + 20);
        RepeatMonthAfter repeat = new RepeatMonthAfter(startDate);
        repeat.setIncrement(increment);
        repeat.setEnd(endDate);
        for (int i = 0; i < 100; i++) {
            LocalDate completed = startDate.plusDays(RAND.nextInt(32));
            LocalDate expectedDue = completed.plusMonths(increment);
            if (expectedDue.isAfter(endDate))
                expectedDue = null;
            LocalDate actualDue = repeat
                    .computeNextDueDate(startDate, completed);
            assertEquals(String.format(
                            "Next due date for task due %s, %d months"
                                    + " after completion on %s, ending %s",
                            startDate.format(DAY_FORMAT), increment,
                            completed.format(DAY_FORMAT),
                            endDate.format(DAY_FORMAT)),
                    expectedDue, actualDue);
            if (actualDue == null)
                break;
            startDate = actualDue;
        }
    }

    /**
     * Test an <i>N</i> &ldquo;month(s) after&rdquo; repeating interval
     * allowed on a subset of days of the week.  The new due date should
     * be on an allowed day roughly <i>N</i> months following the completion
     * date; the exact day depends on the search direction.  If testing
     * with an end date and the next due date would be after the end date,
     * we expect the interval to return {@code null}.
     *
     * @param direction the direction to look for the next allowed day
     * @param increment the number of months to advance after completion
     * @param withEnd whether to stop repeating after an end date
     */
    public void testRepeatNMonthsAfterWithRestrictions(
            WeekdayDirection direction, int increment, boolean withEnd) {
        Set<WeekDays> allowed = randomDays();
        LocalDate startDate = LocalDate.now();
        while (!allowed.contains(WeekDays.fromJavaDay(startDate.getDayOfWeek())))
            startDate = startDate.plusDays(1);
        RepeatMonthAfter repeat = new RepeatMonthAfter(startDate);
        repeat.setIncrement(increment);
        repeat.setAllowedWeekDays(allowed);
        repeat.setDirection(direction);
        LocalDate endDate = withEnd ? startDate.plusMonths(
                RAND.nextInt(30) + 20) : null;
        if (withEnd)
            repeat.setEnd(endDate);
        for (int i = 0; i < 100; i++) {
            LocalDate completed = startDate.plusDays(RAND.nextInt(32));
            LocalDate expectedDue = completed.plusMonths(increment);
            if (!allowed.contains(WeekDays.fromJavaDay(
                    expectedDue.getDayOfWeek()))) {
                // Look for both the next and previous days, and count
                LocalDate nextAllowed = expectedDue.plusDays(1);
                LocalDate previousAllowed = expectedDue.minusDays(1);
                while (!allowed.contains(WeekDays.fromJavaDay(
                        nextAllowed.getDayOfWeek())))
                    nextAllowed = nextAllowed.plusDays(1);
                while (!allowed.contains(WeekDays.fromJavaDay(
                        previousAllowed.getDayOfWeek())))
                    previousAllowed = previousAllowed.minusDays(1);
                switch (direction) {
                    case NEXT: expectedDue = nextAllowed; break;
                    case PREVIOUS: expectedDue = previousAllowed; break;
                    case CLOSEST_OR_NEXT:
                        if (expectedDue.until(nextAllowed).getDays() <=
                                previousAllowed.until(expectedDue).getDays())
                            expectedDue = nextAllowed;
                        else
                            expectedDue = previousAllowed;
                        break;
                    case CLOSEST_OR_PREVIOUS:
                        if (previousAllowed.until(expectedDue).getDays() <=
                                expectedDue.until(nextAllowed).getDays())
                            expectedDue = previousAllowed;
                        else
                            expectedDue = nextAllowed;
                        break;
                }
            }
            if (withEnd && expectedDue.isAfter(endDate))
                expectedDue = null;
            LocalDate actualDue = repeat
                    .computeNextDueDate(startDate, completed);
            StringBuffer message = new StringBuffer(
                    "Next due date for task due ")
                    .append(startDate.format(DAY_FORMAT)).append(", ");
            message.append(direction).append(' ').append(allowed);
            message.append(' ').append(increment)
                    .append((increment > 1) ? " months" : " month");
            message.append(" after completion on ")
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

    @Test
    public void testRepeatMonthAfterRestrictedByDaysNext() {
        testRepeatNMonthsAfterWithRestrictions(
                WeekdayDirection.NEXT, 1, false);
    }

    @Test
    public void testRepeatMonthAfterRestrictedByDaysPrevious() {
        testRepeatNMonthsAfterWithRestrictions(
                WeekdayDirection.PREVIOUS, 1, false);
    }

    @Test
    public void testRepeatMonthAfterRestrictedByDaysClosestOrNext() {
        testRepeatNMonthsAfterWithRestrictions(
                WeekdayDirection.CLOSEST_OR_NEXT, 1, false);
    }

    @Test
    public void testRepeatMonthAfterRestrictedByDaysClosestOrPrevious() {
        testRepeatNMonthsAfterWithRestrictions(
                WeekdayDirection.CLOSEST_OR_PREVIOUS, 1, false);
    }

    @Test
    public void testRepeatMonthAfterRestrictedByDaysWithEndNext() {
        testRepeatNMonthsAfterWithRestrictions(
                WeekdayDirection.NEXT, 1, true);
    }

    @Test
    public void testRepeatMonthAfterRestrictedByDaysWithEndPrevious() {
        testRepeatNMonthsAfterWithRestrictions(
                WeekdayDirection.PREVIOUS, 1, true);
    }

    @Test
    public void testRepeatMonthAfterRestrictedByDaysWithEndClosestOrNext() {
        testRepeatNMonthsAfterWithRestrictions(
                WeekdayDirection.CLOSEST_OR_NEXT, 1, true);
    }

    @Test
    public void testRepeatMonthAfterRestrictedByDaysWithEndClosestOrPrevious() {
        testRepeatNMonthsAfterWithRestrictions(
                WeekdayDirection.CLOSEST_OR_PREVIOUS, 1, true);
    }

    @Test
    public void testRepeatNMonthsAfterRestrictedByDaysNext() {
        testRepeatNMonthsAfterWithRestrictions(
                WeekdayDirection.NEXT, RAND.nextInt(10) + 2, false);
    }

    @Test
    public void testRepeatNMonthsAfterRestrictedByDaysPrevious() {
        testRepeatNMonthsAfterWithRestrictions(
                WeekdayDirection.PREVIOUS, RAND.nextInt(10) + 2, false);
    }

    @Test
    public void testRepeatNMonthsAfterRestrictedByDaysClosestOrNext() {
        testRepeatNMonthsAfterWithRestrictions(
                WeekdayDirection.CLOSEST_OR_NEXT,
                RAND.nextInt(10) + 2, false);
    }

    @Test
    public void testRepeatNMonthsAfterRestrictedByDaysClosestOrPrevious() {
        testRepeatNMonthsAfterWithRestrictions(
                WeekdayDirection.CLOSEST_OR_PREVIOUS,
                RAND.nextInt(10) + 2, false);
    }

}
