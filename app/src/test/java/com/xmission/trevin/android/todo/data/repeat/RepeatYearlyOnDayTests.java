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

import static com.xmission.trevin.android.todo.data.repeat.RepeatMonthlyOnDayTests.ordinalWeek;
import static com.xmission.trevin.android.todo.util.RandomToDoUtils.randomWeek;
import static org.junit.Assert.*;

import org.junit.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Tests for the yearly by day of week of month repeating interval.
 *
 * @author Trevin Beattie
 */
public class RepeatYearlyOnDayTests {

    private static final Random RAND = new Random();

    /**
     * Formatter for dates to show the day of the week.
     */
    public static final DateTimeFormatter DAY_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);

    /**
     * Compute the target month and date for a given year
     * based on a day of a week.
     *
     * @param baseDate the date whose year should be held constant
     * @param month the month of the year to go to
     * @param week the week of the month to go to (0 is the first week,
     * 4 is the last week)
     * @param day the day of the week to go to
     *
     * @return the target date
     */
    public static LocalDate setMonthWeekAndDayForYear(
            LocalDate baseDate, Months month, int week, WeekDays day) {
        LocalDate target = baseDate.withMonth(
                month.getJavaMonth().getValue());
        if (week > 0) {
            // Start from the beginning of the month and go forward
            target = target.withDayOfMonth(1).plusWeeks(week - 1);
            while (target.getDayOfWeek() != day.getJavaDay())
                target = target.plusDays(1);
        } else {
            // Start from the end of the month and go backward
            target = target.withDayOfMonth(baseDate.lengthOfMonth());
            while (target.getDayOfWeek() != day.getJavaDay())
                target = target.minusDays(1);
        }
        return target;
    }

    /**
     * Run a test for a yearly repeating interval.  Caller specifies the
     * week of the month; the month and day of the week will be
     * chosen randomly.
     *
     * @param increment the number of years between repeating due dates
     * @param week the week of the month to test
     * @param withEnd whether the interval should have an end date
     */
    private void runYearlyOnDayTest(int increment, int week, boolean withEnd) {
        Months month = Months.values()[RAND.nextInt(Months.values().length)];
        WeekDays day = WeekDays.values()[RAND.nextInt(WeekDays.values().length)];
        LocalDate startDate = setMonthWeekAndDayForYear(
                // Start with the middle of the month,
                LocalDate.of(2000 + RAND.nextInt(50),
                        month.getJavaMonth().getValue(), 15),
                // then adjust from there
                month, week, day);
        LocalDate endDate = withEnd ?
                startDate.plusYears(RAND.nextInt(25) + 10) : null;
        RepeatYearlyOnDay repeat = new RepeatYearlyOnDay(startDate);
        repeat.setIncrement(increment);
        repeat.setMonth(month);
        repeat.setWeek(week);
        repeat.setDay(day);
        if (withEnd)
            repeat.setEnd(endDate);
        for (int i = 0; i < 50; i++) {
            LocalDate completed = startDate.plusDays(RAND.nextInt(100));
            LocalDate expectedDue = setMonthWeekAndDayForYear(
                    startDate.plusYears(increment), month, week, day);
            if (withEnd && expectedDue.isAfter(endDate))
                expectedDue = null;
            LocalDate actualDue = repeat
                    .computeNextDueDate(startDate, completed);
            StringBuffer message = new StringBuffer(
                    "Next due date for task due ");
            message.append(startDate.format(DAY_FORMAT)).append(" (");
            message.append(ordinalWeek(week)).append(' ').append(day)
                    .append(" of ").append(month);
            message.append(", completed ").append(completed.format(DAY_FORMAT));
            if (withEnd)
                message.append(", ending ").append(endDate.format(DAY_FORMAT));
            assertEquals(message.toString(), expectedDue, actualDue);
            if (actualDue == null)
                break;
            startDate = actualDue;
        }
    }

    /**
     * Test a yearly repeating interval for early in the month (one of the
     * first three weeks), with no specified end date.  The new due date
     * should be the same day of the same week of the same month regardless
     * of when the item was completed.
     */
    @Test
    public void testRepeatYearlyEarly() {
        runYearlyOnDayTest(1, RAND.nextInt(3) + 1, false);
    }

    /**
     * Test a yearly repeating interval for the fourth week of the month
     * with no specified end date.  The new due date should always be in
     * the fourth week.
     */
    @Test
    public void testRepeatYearlyFourthWeek() {
        runYearlyOnDayTest(1, 4, false);
    }

    /**
     * Test a yearly repeating interval for the <i>last</i> week of the month
     * with no specified end date.  The new due date should always be in
     * the last week.
     */
    @Test
    public void testRepeatYearlyLastWeek() {
        runYearlyOnDayTest(1, -1, false);
    }

    /**
     * Test a yearly repeating interval with a specified end date.
     */
    @Test
    public void testRepeatYearlyWithEnd() {
        runYearlyOnDayTest(1, randomWeek(), true);
    }

    /**
     * Test an every <i>N</i> years repeating interval with no end date.
     */
    @Test
    public void testRepeatEveryNYears() {
        runYearlyOnDayTest(RAND.nextInt(10) + 2, randomWeek(), false);
    }

    /**
     * Test an every <i>N</i> years repeating interval with an end date.
     */
    @Test
    public void testRepeatEveryNYearsWithEnd() {
        runYearlyOnDayTest(RAND.nextInt(10) + 2, randomWeek(), true);
    }

}
