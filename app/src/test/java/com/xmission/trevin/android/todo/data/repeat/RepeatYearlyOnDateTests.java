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
 * Tests for the yearly by date repeating interval.
 *
 * @author Trevin Beattie
 */
public class RepeatYearlyOnDateTests {

    private static final Random RAND = new Random();

    /**
     * Formatter for dates to show the day of the week.
     */
    public static final DateTimeFormatter DAY_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);

    /**
     * Run a yearly repeating interval test.  The new due date should be
     * roughly the same day of the year regardless of when the item was
     * completed, possibly adjusted for allowed days of the week.
     *
     * @param increment the number of years between intervals
     * @param direction if present, to restrict the due date to a subset
     * of days of the week and choose an available day based on this
     * direction; or {@code null} to allow all days of the week.
     * @param withEnd whether to stop repeating after an end date
     */
    private void runYearlyByDateTest(
            int increment, WeekdayDirection direction, boolean withEnd) {
        // For these tests, we do NOT risk a leap day.
        LocalDate targetDate = LocalDate.ofYearDay(2001,
                RAND.nextInt(365) + 1);
        LocalDate startDate = targetDate;
        Set<WeekDays> allowed = (direction != null)
                ? randomDays() : WeekDays.ALL;
        LocalDate endDate = withEnd ?
                startDate.plusYears(RAND.nextInt(50) + 25) : null;
        RepeatYearlyOnDate repeat = new RepeatYearlyOnDate(targetDate);
        repeat.setDate(targetDate.getDayOfMonth());
        repeat.setMonth(Months.fromJavaMonth(targetDate.getMonth()));
        repeat.setIncrement(increment);
        if (direction != null) {
            repeat.setAllowedWeekDays(allowed);
            repeat.setDirection(direction);
            startDate = adjustTarget(startDate, allowed, direction);
        }
        if (withEnd)
            repeat.setEnd(endDate);
        for (int i = 0; i < 100; i++) {
            LocalDate completed = startDate.plusDays(RAND.nextInt(400));
            targetDate = targetDate.plusYears(increment);
            targetDate = targetDate.withMonth(repeat.getMonth()
                    .getJavaMonth().getValue());
            targetDate = targetDate.withDayOfMonth(Math.min(repeat.getDate(),
                    targetDate.lengthOfMonth()));
            LocalDate expectedDue = targetDate;
            if (direction != null)
                expectedDue = adjustTarget(expectedDue, allowed, direction);
            if (withEnd && expectedDue.isAfter(endDate))
                expectedDue = null;
            LocalDate actualDue = repeat
                    .computeNextDueDate(startDate, completed);
            StringBuffer message = new StringBuffer(
                    "Next due date for task due ")
                    .append(startDate.format(DAY_FORMAT)).append(", ");
            if (direction != null)
                message.append(direction).append(' ')
                        .append(allowed).append(" to ")
                        .append(repeat.getDate()).append(' ')
                        .append(repeat.getMonth()).append(", ");
            if (increment > 1)
                message.append("every ").append(increment).append(" years, ");
            message.append("completed ").append(completed.format(DAY_FORMAT));
            if (withEnd)
                message.append(", ending ").append(endDate.format(DAY_FORMAT));
            assertEquals(message.toString(), expectedDue, actualDue);
            if (actualDue == null)
                break;
            startDate = actualDue;
        }
    }

    /**
     * Test a yearly repeating interval, allowed on all days, with no
     * specified end date.
     */
    @Test
    public void testRepeatYearlyWithoutRestriction() {
        runYearlyByDateTest(1, null, false);
    }

    /**
     * Test a yearly repeating interval, allowed on all days, with a
     * specified end date.
     */
    @Test
    public void testRepeatYearlyWithEnd() {
        runYearlyByDateTest(1, null, true);
    }

    /**
     * Test an every <i>N</i> years repeating interval, allowed on all
     * days, with no specified end date.
     */
    @Test
    public void testRepeatEveryNYearsWithoutRestriction() {
        runYearlyByDateTest(RAND.nextInt(10) + 2, null, false);
    }

    /**
     * Test an every <i>N</i> years repeating interval, allowed on all
     * days, with a specified end date.
     */
    @Test
    public void testRepeatEveryNYearsWithEnd() {
        runYearlyByDateTest(RAND.nextInt(10) + 2, null, true);
    }

    /**
     * Test a yearly repeating interval allowed on the next of a subset
     * of days of the week, with no specified end date.
     */
    @Test
    public void testRepeatYearlyRestrictedByDayNext() {
        runYearlyByDateTest(1, WeekdayDirection.NEXT, false);
    }

    /**
     * Test a yearly repeating interval allowed on the previous of a subset
     * of days of the week, with no specified end date.
     */
    @Test
    public void testRepeatYearlyRestrictedByDayPrevious() {
        runYearlyByDateTest(1, WeekdayDirection.PREVIOUS, false);
    }

    /**
     * Test a yearly repeating interval allowed on the closest of a subset
     * of days of the week (ties go to next), with no specified end date.
     */
    @Test
    public void testRepeatYearlyRestrictedByDayClosestOrNext() {
        runYearlyByDateTest(1, WeekdayDirection.CLOSEST_OR_NEXT, false);
    }

    /**
     * Test a yearly repeating interval allowed on the closest of a subset
     * of days of the week (ties go to previous), with no specified end date.
     */
    @Test
    public void testRepeatYearlyRestrictedByDayClosestOrPrevious() {
        runYearlyByDateTest(1, WeekdayDirection.CLOSEST_OR_PREVIOUS, false);
    }

    /**
     * Test an every <i>N</i> years repeating interval, allowed on the
     * next of a subset of days of the week, with an end date.
     */
    @Test
    public void testRepeatEveryNYearsRestrictedByDayWithEndNext() {
        runYearlyByDateTest(RAND.nextInt(10) + 2,
                WeekdayDirection.NEXT, true);
    }

    /**
     * Test an every <i>N</i> years repeating interval, allowed on the
     * previous of a subset of days of the week, with an end date.
     */
    @Test
    public void testRepeatEveryNYearsRestrictedByDayWithEndPrevious() {
        runYearlyByDateTest(RAND.nextInt(10) + 2,
                WeekdayDirection.PREVIOUS, true);
    }

    /**
     * Test an every <i>N</i> years repeating interval, allowed on the
     * closest of a subset of days of the week, with an end date.
     */
    @Test
    public void testRepeatEveryNYearsRestrictedByDayWithEndClosestOrNext() {
        runYearlyByDateTest(RAND.nextInt(10) + 2,
                WeekdayDirection.CLOSEST_OR_NEXT, true);
    }

    /**
     * Test an every <i>N</i> years repeating interval, allowed on the
     * closest of a subset of days of the week, with an end date.
     */
    @Test
    public void testRepeatEveryNYearsRestrictedByDayWithEndClosestOrPrevious() {
        runYearlyByDateTest(RAND.nextInt(10) + 2,
                WeekdayDirection.CLOSEST_OR_PREVIOUS, true);
    }

    /**
     * Test a yearly repeating interval for February 29.  The new due date
     * should be leap day on leap years, the last day of February in all
     * other years.
     */
    @Test
    public void testRepeatYearlyWithLeapDays() {
        // This is going to start in a non-leap year.
        LocalDate startDate = LocalDate.of(1970, 2, 28);
        RepeatYearlyOnDate repeat = new RepeatYearlyOnDate(startDate);
        repeat.setDate(29);
        repeat.setMonth(Months.FEBRUARY);
        // Be sure we include both the century exception and
        // divisible-by-400 exception (in other words, years 2000 and 2100).
        for (int i = 0; i < 160; i++) {
            LocalDate completed = startDate.plusDays(RAND.nextInt(400));
            LocalDate expectedDue = startDate.plusYears(1)
                    .withMonth(2);
            expectedDue = expectedDue.withDayOfMonth(
                    expectedDue.lengthOfMonth());
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

}
