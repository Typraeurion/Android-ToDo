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
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/**
 * Tests for the monthly by day of week repeating interval.
 *
 * @author Trevin Beattie
 */
public class RepeatMonthlyOnDayTests {

    private static final Random RAND = new Random();

    /**
     * Formatter for dates to show the day of the week.
     */
    public static final DateTimeFormatter DAY_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);

    /**
     * Compute the target date for a given month based on a day of a week.
     *
     * @param baseDate the date whose month should be held constant
     * @param week the week of the month to go to (0 is the first week,
     * 4 is the last week)
     * @param day the day of the week to go to
     *
     * @return the target date
     */
    public static LocalDate setWeekAndDayForMonth(
            LocalDate baseDate, int week, WeekDays day) {
        LocalDate target;
        if (week < 4) {
            // Start from the beginning of the month and go forward
            target = baseDate.withDayOfMonth(1).plusWeeks(week);
            while (target.getDayOfWeek() != day.getJavaDay())
                target = target.plusDays(1);
        } else {
            // Start from the end of the month and go backward
            target = baseDate.withDayOfMonth(baseDate.lengthOfMonth());
            while (target.getDayOfWeek() != day.getJavaDay())
                target = target.minusDays(1);
        }
        return target;
    }

    /**
     * Test a monthly repeating interval for early in the month (one of
     * the first three weeks), with no specified end date.  The new due date
     * should be the same day of the same week regardless of when the item
     * was completed.
     */
    @Test
    public void testRepeatMonthlyEarly() {
        LocalDate startDate = LocalDate.now();
        if (startDate.getDayOfMonth() > 21)
            startDate = startDate.minusDays(RAND.nextInt(10) + 10);
        RepeatMonthlyOnDay repeat = new RepeatMonthlyOnDay(startDate);
        // The repeat should have initialized its own day and week according
        // to our start day, but make it explicit here.
        WeekDays targetDay = WeekDays.fromJavaDay(startDate.getDayOfWeek());
        int targetWeek = (startDate.getDayOfMonth() - 1) / 7;
        String stndrd = (targetWeek == 0) ? "st" :
                (targetWeek == 1) ? "nd" : (targetWeek == 2) ? "rd" : "th";
        repeat.setDay(targetDay);
        repeat.setWeek(targetWeek);
        for (int i = 0; i < 15; i++) {
            LocalDate completed = startDate.plusDays(RAND.nextInt(100));
            LocalDate expectedDue = setWeekAndDayForMonth(
                    startDate.plusMonths(1), targetWeek, targetDay);
            LocalDate actualDue = repeat
                    .computeNextDueDate(startDate, completed);
            assertEquals(String.format(
                            "Next due date for task due %s (%d%s %s),"
                                    + " completed %s",
                            startDate.format(DAY_FORMAT),
                            targetWeek + 1, stndrd, targetDay,
                            completed.format(DAY_FORMAT)),
                    expectedDue, actualDue);
            if (actualDue == null)
                // Technically unreachable, but the compiler can't tell
                break;
            startDate = actualDue;
        }
    }

    /**
     * Test a monthly repeating interval for the fourth week of the month
     * with no specified end date.  The new due date should always be in
     * the fourth week.
     */
    @Test
    public void testRepeatMonthlyFourthWeek() {
        LocalDate startDate = LocalDate.now()
                .withDayOfMonth(RAND.nextInt(7) + 21);
        RepeatMonthlyOnDay repeat = new RepeatMonthlyOnDay(startDate);
        WeekDays targetDay = WeekDays.fromJavaDay(startDate.getDayOfWeek());
        repeat.setDay(targetDay);
        repeat.setWeek(3);
        for (int i = 0; i < 15; i++) {
            LocalDate completed = startDate.plusDays(RAND.nextInt(100));
            LocalDate expectedDue = setWeekAndDayForMonth(
                    startDate.plusMonths(1), 3, targetDay);
            LocalDate actualDue = repeat
                    .computeNextDueDate(startDate, completed);
            assertEquals(String.format(
                            "Next due date for task due %s (4th %s),"
                                    + " completed %s",
                            startDate.format(DAY_FORMAT), targetDay,
                            completed.format(DAY_FORMAT)),
                    expectedDue, actualDue);
            if (actualDue == null)
                // Technically unreachable, but the compiler can't tell
                break;
            startDate = actualDue;
        }
    }

    /**
     * Test a monthly repeating interval for the <i>last</i> week of the
     * month with no specified end date.  The new due date should always
     * be in the fourth week.
     */
    @Test
    public void testRepeatMonthlyLastWeek() {
        LocalDate startDate = LocalDate.now();
        startDate = startDate.withDayOfMonth(startDate.lengthOfMonth()
                - RAND.nextInt(7));
        RepeatMonthlyOnDay repeat = new RepeatMonthlyOnDay(startDate);
        WeekDays targetDay = WeekDays.fromJavaDay(startDate.getDayOfWeek());
        repeat.setDay(targetDay);
        repeat.setWeek(4);
        for (int i = 0; i < 15; i++) {
            LocalDate completed = startDate.plusDays(RAND.nextInt(100));
            LocalDate expectedDue = setWeekAndDayForMonth(
                    startDate.plusMonths(1), 4, targetDay);
            LocalDate actualDue = repeat
                    .computeNextDueDate(startDate, completed);
            assertEquals(String.format(
                            "Next due date for task due %s (last %s),"
                                    + " completed %s",
                            startDate.format(DAY_FORMAT), targetDay,
                            completed.format(DAY_FORMAT)),
                    expectedDue, actualDue);
            if (actualDue == null)
                // Technically unreachable, but the compiler can't tell
                break;
            startDate = actualDue;
        }
    }

    /**
     * Test a monthly repeating interval with a specified end date.
     */
    @Test
    public void testRepeatMonthlyWithEnd() {
        LocalDate startDate = LocalDate.now();
        // Start on a random day of this month
        startDate = startDate.withDayOfMonth(
                RAND.nextInt(startDate.lengthOfMonth()) + 1);
        LocalDate endDate = startDate.plusMonths(RAND.nextInt(6) + 4);
        RepeatMonthlyOnDay repeat = new RepeatMonthlyOnDay(startDate);
        WeekDays targetDay = WeekDays.fromJavaDay(startDate.getDayOfWeek());
        int targetWeek = (startDate.getDayOfMonth() - 1) / 7;
        // If we're starting within the last week, randomly switch
        // from targeting the fourth week to the last week.
        if ((startDate.getDayOfMonth() > startDate.lengthOfMonth() - 7) &&
                RAND.nextBoolean())
            targetWeek = 4;
        String stndrdth;
        switch (targetWeek) {
            case 0: stndrdth = "1st"; break;
            case 1: stndrdth = "2nd"; break;
            case 2: stndrdth = "3rd"; break;
            case 4: stndrdth = "last"; break;
            default: stndrdth = String.format("%dth", targetWeek + 1); break;
        }
        repeat.setDay(targetDay);
        repeat.setWeek(targetWeek);
        repeat.setEnd(endDate);
        for (int i = 0; i < 15; i++) {
            LocalDate completed = startDate.plusDays(RAND.nextInt(100));
            LocalDate expectedDue = setWeekAndDayForMonth(
                    startDate.plusMonths(1), targetWeek, targetDay);
            if (expectedDue.isAfter(endDate))
                expectedDue = null;
            LocalDate actualDue = repeat
                    .computeNextDueDate(startDate, completed);
            assertEquals(String.format(
                            "Next due date for task due %s (%s %s),"
                                    + " completed %s, ending %s",
                            startDate.format(DAY_FORMAT), stndrdth,
                            targetDay, completed.format(DAY_FORMAT),
                            endDate.format(DAY_FORMAT)),
                    expectedDue, actualDue);
            if (actualDue == null)
                // Technically unreachable, but the compiler can't tell
                break;
            startDate = actualDue;
        }
    }

}
