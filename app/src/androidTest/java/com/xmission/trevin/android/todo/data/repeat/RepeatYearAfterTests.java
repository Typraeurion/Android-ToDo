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
 * Tests for the &ldquo;year after&rdquo; repeating interval.
 * This interval always goes a fixed number of years after
 * the item&rsquo;s last completion date rather than its
 * previous due date.
 *
 * @author Trevin Beattie
 */
public class RepeatYearAfterTests {

    private static final Random RAND = new Random();

    /**
     * Formatter for dates to show the day of the week.
     */
    public static final DateTimeFormatter DAY_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);

    /**
     * Run a &ldquo;year after&rdquo; test allowed on all days (the default).
     * The new due date should always be the year after the item was
     * completed.
     *
     * @param increment the number of years to advance after completion
     * @param withEnd whether to stop repeating after an end date
     */
    private void runRepeatYearAfterTest(int increment, boolean withEnd) {
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = withEnd ?
                startDate.plusYears(RAND.nextInt(16) + 4) : null;
        RepeatYearAfter repeat = new RepeatYearAfter(startDate);
        repeat.setIncrement(increment);
        if (withEnd)
            repeat.setEnd(endDate);
        for (int i = 0; i < 40; i++) {
            LocalDate completed = startDate.plusDays(RAND.nextInt(370));
            LocalDate expectedDue = completed.plusYears(increment);
            if (withEnd && expectedDue.isAfter(endDate))
                expectedDue = null;
            LocalDate actualDue = repeat
                    .computeNextDueDate(startDate, completed);
            StringBuffer message = new StringBuffer(
                    "Next due date for task due ")
                    .append(startDate.format(DAY_FORMAT)).append(", ");
            if (increment > 1)
                message.append(increment).append(" years after completion on ");
            else
                message.append(" completed ");
            message.append(completed.format(DAY_FORMAT));
            if (withEnd)
                message.append(", ending ").append(endDate.format(DAY_FORMAT));
            assertEquals(message.toString(), expectedDue, actualDue);
            if (actualDue == null)
                break;
            startDate = actualDue;
        }
    }

    /**
     * Test a &ldquo;year after&rdquo; repeating interval, allowed on all
     * days (the default), with no specified end date.  The new due date
     * should always be the year after the item was completed.
     */
    @Test
    public void testRepeatYearlyAfterWithoutRestriction() {
        runRepeatYearAfterTest(1, false);
    }

    /**
     * Test a &ldquo;year after&rdquo; repeating interval, allowed on all
     * days (the default), with a specified end date.  The new due date
     * should always be the year after the item was completed.
     */
    @Test
    public void testRepeatYearlyAfterWithEnd() {
        runRepeatYearAfterTest(1, true);
    }

    /**
     * Test an <i>N</i> years-after repeating interval, allowed on all
     * days (the default), with no specified end date.  The new due date
     * should always be <i>N</i> years after the item was completed.
     */
    @Test
    public void testRepeatNYearsAfterWithoutRestriction() {
        runRepeatYearAfterTest(RAND.nextInt(10) + 2, false);
    }

    /**
     * Test an <i>N</i> years-after repeating interval, allowed on all
     * days (the default), with a specified end date.  The new due date
     * should always be <i>N</i> years after the item was completed.
     */
    @Test
    public void testRepeatNYearsAfterWithEnd() {
        runRepeatYearAfterTest(RAND.nextInt(10) + 2, true);
    }

    /**
     * Run an <i>N</i> &ldquo;year(s) after&rdquo; repeating interval
     * allowed on a subset of days of the week.  The new due date should
     * be on an allowed day roughly <i>N</i> years following the completion
     * date; the exact day depends on the search direction.  If testing
     * with an end date and the next due date would be after the end date,
     * we expect the interval to return {@code null}.
     *
     * @param direction the direction to look for the next allowed day
     * @param increment the number of years to advance after completion
     * @param withEnd whether to stop repeating after an end date
     */
    private void testRepeatNYearsAfterWithRestrictions(
            WeekdayDirection direction, int increment, boolean withEnd) {
        Set<WeekDays> allowed = randomDays();
        LocalDate startDate = LocalDate.now();
        while (!allowed.contains(WeekDays.fromJavaDay(startDate.getDayOfWeek())))
            startDate = startDate.plusDays(1);
        LocalDate endDate = withEnd ?
                startDate.plusYears(RAND.nextInt(16) + 4) : null;
        RepeatYearAfter repeat = new RepeatYearAfter(startDate);
        repeat.setIncrement(increment);
        repeat.setAllowedWeekDays(allowed);
        repeat.setDirection(direction);
        if (withEnd)
            repeat.setEnd(endDate);
        for (int i = 0; i < 40; i++) {
            LocalDate completed = startDate.plusDays(RAND.nextInt(370));
            LocalDate expectedDue = completed.plusYears(increment);
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
            message.append(direction).append(' ').append(allowed).append(", ");
            if (increment > 1)
                message.append(increment)
                        .append(" years after completion on ");
            else
                message.append("completed ");
            message.append(completed.format(DAY_FORMAT));
            if (withEnd)
                message.append(", ending ").append(endDate.format(DAY_FORMAT));
            assertEquals(message.toString(), expectedDue, actualDue);
            if (actualDue == null)
                break;
            startDate = actualDue;
        }
    }

    @Test
    public void testRepeatYearAfterRestrictedByDaysNext() {
        testRepeatNYearsAfterWithRestrictions(
                WeekdayDirection.NEXT, 1, false);
    }

    @Test
    public void testRepeatYearAfterRestrictedByDaysPrevious() {
        testRepeatNYearsAfterWithRestrictions(
                WeekdayDirection.PREVIOUS, 1, false);
    }

    @Test
    public void testRepeatYearAfterRestrictedByDaysClosestOrNext() {
        testRepeatNYearsAfterWithRestrictions(
                WeekdayDirection.CLOSEST_OR_NEXT, 1, false);
    }

    @Test
    public void testRepeatYearAfterRestrictedByDaysClosestOrPrevious() {
        testRepeatNYearsAfterWithRestrictions(
                WeekdayDirection.CLOSEST_OR_PREVIOUS, 1, false);
    }

    @Test
    public void testRepeatYearAfterRestrictedByDaysWithEndNext() {
        testRepeatNYearsAfterWithRestrictions(
                WeekdayDirection.NEXT, 1, true);
    }

    @Test
    public void testRepeatYearAfterRestrictedByDaysWithEndPrevious() {
        testRepeatNYearsAfterWithRestrictions(
                WeekdayDirection.PREVIOUS, 1, true);
    }

    @Test
    public void testRepeatYearAfterRestrictedByDaysWithEndClosestOrNext() {
        testRepeatNYearsAfterWithRestrictions(
                WeekdayDirection.CLOSEST_OR_NEXT, 1, true);
    }

    @Test
    public void testRepeatYearAfterRestrictedByDaysWithEndClosestOrPrevious() {
        testRepeatNYearsAfterWithRestrictions(
                WeekdayDirection.CLOSEST_OR_PREVIOUS, 1, true);
    }

    @Test
    public void testRepeatNYearsAfterRestrictedByDaysNext() {
        testRepeatNYearsAfterWithRestrictions(
                WeekdayDirection.NEXT, RAND.nextInt(10) + 2, false);
    }

    @Test
    public void testRepeatNYearsAfterRestrictedByDaysPrevious() {
        testRepeatNYearsAfterWithRestrictions(
                WeekdayDirection.PREVIOUS, RAND.nextInt(10) + 2, false);
    }

    @Test
    public void testRepeatNYearsAfterRestrictedByDaysClosestOrNext() {
        testRepeatNYearsAfterWithRestrictions(
                WeekdayDirection.CLOSEST_OR_NEXT,
                RAND.nextInt(10) + 2, false);
    }

    @Test
    public void testRepeatNYearsAfterRestrictedByDaysClosestOrPrevious() {
        testRepeatNYearsAfterWithRestrictions(
                WeekdayDirection.CLOSEST_OR_PREVIOUS,
                RAND.nextInt(10) + 2, false);
    }

}
