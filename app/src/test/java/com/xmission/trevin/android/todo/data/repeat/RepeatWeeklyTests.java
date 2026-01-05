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
 * Tests for the weekly repeating interval.
 *
 * @author Trevin Beattie
 */
public class RepeatWeeklyTests {

    private static final Random RAND = new Random();

    /**
     * Formatter for dates to show the day of the week.
     */
    public static final DateTimeFormatter DAY_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);

    /**
     * Run a weekly repeating interval test for a single day of the week.
     *
     * @param increment the number of weeks between due dates
     * @param withEnd whether the repeat should have an end date
     */
    private void runOnceWeeklyTest(int increment, boolean withEnd) {
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = withEnd ?
                startDate.plusDays(RAND.nextInt(240) + 120) : null;
        RepeatWeekly repeat = new RepeatWeekly(startDate);
        repeat.setIncrement(increment);
        WeekDays day = WeekDays.fromJavaDay(startDate.getDayOfWeek());
        repeat.setWeekDays(Collections.singleton(day));
        if (withEnd)
            repeat.setEnd(endDate);
        for (int i = 0; i < 50; i++) {
            LocalDate completed = startDate.plusDays(RAND.nextInt(7));
            LocalDate expectedDue = startDate.plusWeeks(increment);
            if (withEnd && expectedDue.isAfter(endDate))
                expectedDue = null;
            LocalDate actualDue = repeat
                    .computeNextDueDate(startDate, completed);
            StringBuffer message = new StringBuffer(
                    "Next due date for task due ");
            message.append(startDate.format(DAY_FORMAT)).append(", ");
            if (increment > 1)
                message.append("every ").append(increment).append(" weeks ");
            message.append("on ").append(day).append("s, completed ")
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
     * Test a weekly repeating interval for a single day of the week
     * with no specified end date.  The new due date should be the same
     * regardless of when the item was completed.
     */
    @Test
    public void testRepeatOnceWeekly() {
        runOnceWeeklyTest(1, false);
    }

    /**
     * Test a weekly repeating interval for a single day of the week
     * with a specified end date.  The new due date should be the same
     * regardless of when the item was completed.
     */
    @Test
    public void testRepeatOnceWeeklyWithEnd() {
        runOnceWeeklyTest(1, true);
    }

    /**
     * Test an every <i>N</i>-weeks repeating interval for a single day
     * of the week, with no specified end date.  The new due date should be
     * the same regardless of when the item was completed.
     */
    @Test
    public void testRepeatOnceEveryNWeeks() {
        runOnceWeeklyTest(RAND.nextInt(15) + 2, false);
    }

    /**
     * Test an every <i>N</i>-weeks repeating interval for a single day
     * of the week with a specified end date.  The new due date should be
     * the same regardless of when the item was completed.
     */
    @Test
    public void testRepeatOnceEveryNWeeksWithEnd() {
        runOnceWeeklyTest(RAND.nextInt(15) + 2, true);
    }

    /**
     * Run a weekly repeating interval test for multiple days of the week.
     *
     * @param increment the number of weeks between runs of dates
     * @param withEnd whether the repeat should have an end date
     */
    private void runMultidayWeeklyTest(int increment, boolean withEnd) {
        LocalDate startDate = LocalDate.now();
        Set<WeekDays> allowed = randomDays();
        while (!allowed.contains(WeekDays.fromJavaDay(
                startDate.getDayOfWeek())))
            startDate = startDate.plusDays(1);
        // Ensure there are at least two days in the set
        while (allowed.size() <= 1)
            allowed.add(WeekDays.values()[
                    RAND.nextInt(WeekDays.values().length)]);
        LocalDate endDate = withEnd ?
                startDate.plusDays(RAND.nextInt(50) + 25) : null;
        RepeatWeekly repeat = new RepeatWeekly(startDate);
        repeat.setIncrement(increment);
        repeat.setWeekDays(allowed);
        if (withEnd)
            repeat.setEnd(endDate);
        for (int i = 0; i < 100; i++) {
            LocalDate completed = startDate.plusDays(RAND.nextInt(7));
            LocalDate expectedDue = startDate.plusDays(1);
            while (!allowed.contains(WeekDays.fromJavaDay(
                    expectedDue.getDayOfWeek())))
                expectedDue = expectedDue.plusDays(1);
            // If we crossed a week boundary, account for the weeks increment.
            // Our week goes from Sunday to Saturday.
            if (WeekDays.fromJavaDay(expectedDue.getDayOfWeek()).getValue() <=
                    WeekDays.fromJavaDay(startDate.getDayOfWeek()).getValue()) {
                if (increment > 1)
                    expectedDue = expectedDue.plusWeeks(increment - 1);
            }
            if (withEnd && expectedDue.isAfter(endDate))
                expectedDue = null;
            LocalDate actualDue = repeat
                    .computeNextDueDate(startDate, completed);
            StringBuffer message = new StringBuffer(
                    "Next due date for task due ");
            message.append(startDate.format(DAY_FORMAT)).append(", ");
            message.append(allowed).append(" every ");
            if (increment > 1)
                message.append(increment).append(" weeks, ");
            else
                message.append("week, ");
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
     * Test a weekly repeating interval for multiple days of the week
     * with no specified end date.
     */
    @Test
    public void testRepeatMultidayWeekly() {
        runMultidayWeeklyTest(1, false);
    }

    /**
     * Test a weekly repeating interval for multiple days of the week
     * with a specified end date.
     */
    @Test
    public void testRepeatMultidayWeeklyWithEnd() {
        runMultidayWeeklyTest(1, true);
    }

    /**
     * Test an every <i>N</i>-weeks repeating interval for multiple days of
     * the week with no specified end date.
     */
    @Test
    public void testRepeatMultidayEveryNWeeks() {
        runMultidayWeeklyTest(RAND.nextInt(15) + 2, false);
    }

    /**
     * Test an every <i>N</i>-weeks repeating interval for multiple days of
     * the week with a specified end date.
     */
    @Test
    public void testRepeatMultidayEveryNWeeksWithEnd() {
        runMultidayWeeklyTest(RAND.nextInt(15) + 2, true);
    }

}
