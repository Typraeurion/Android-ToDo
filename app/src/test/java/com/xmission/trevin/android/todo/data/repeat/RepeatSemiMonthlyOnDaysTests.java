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
import static com.xmission.trevin.android.todo.data.repeat.RepeatMonthlyOnDayTests.setWeekAndDayForMonth;
import static org.junit.Assert.*;

import org.junit.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Tests for the semimonthly by days of weeks repeating interval.
 *
 * @author Trevin Beattie
 */
public class RepeatSemiMonthlyOnDaysTests {

    private static final Random RAND = new Random();

    /**
     * Formatter for dates to show the day of the week.
     */
    public static final DateTimeFormatter DAY_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);

    /**
     * Test a semimonthly repeating interval for early in the month (two of
     * the first three weeks), with no specified end date.  The new due date
     * should be the same days of the same weeks regardless of when the
     * item was completed.
     */
    @Test
    public void testRepeatSemiMonthlyOnDaysEarly() {
        LocalDate startDate = LocalDate.now();
        if (startDate.getDayOfMonth() > 21)
            startDate = startDate.minusDays(RAND.nextInt(10) + 10);
        WeekDays[] days = new WeekDays[2];
        int[] weeks = new int[2];
        days[0] = WeekDays.fromJavaDay(startDate.getDayOfWeek());
        weeks[0] = (startDate.getDayOfMonth() - 1) / 7;
        LocalDate otherDate;
        // Choose our second date so that it lies on a different day of a
        // different week than our first date, and is at least 4 days apart.
        do {
            do {
                days[1] = WeekDays.values()[RAND.nextInt(WeekDays.values().length)];
            } while (days[1] == days[0]);
            do {
                weeks[1] = RAND.nextInt(3);
            } while (weeks[1] == weeks[0]);
            otherDate = RepeatMonthlyOnDayTests.setWeekAndDayForMonth(
                    startDate, weeks[1], days[1]);
        } while (Math.abs(otherDate.until(startDate).getDays()) < 4);
        RepeatSemiMonthlyOnDays repeat = new RepeatSemiMonthlyOnDays(startDate);
        repeat.setDay(days[0]);
        repeat.setDay2(days[1]);
        repeat.setWeek(weeks[0]);
        repeat.setWeek2(weeks[1]);
        String[] stndrd = new String[] {
                ordinalWeek(weeks[0]), ordinalWeek(weeks[1]) };
        for (int i = 1; i <= 30; i++) {
            LocalDate completed = startDate.plusDays(RAND.nextInt(50));
            LocalDate expectedDue = setWeekAndDayForMonth(
                    (weeks[i%2] > weeks[(i+1)%2]) ?
                            startDate : startDate.plusMonths(1),
                    weeks[i%2], days[i%2]);
            LocalDate actualDue = repeat
                    .computeNextDueDate(startDate, completed);
            assertEquals(String.format(
                            "Next due date for task due %s (%s %s or %s %s),"
                                    + " completed %s",
                            startDate.format(DAY_FORMAT), stndrd[0], days[0],
                            stndrd[1], days[1], completed.format(DAY_FORMAT)),
                    expectedDue, actualDue);
            if (actualDue == null)
                // Technically unreachable, but the compiler can't tell
                break;
            startDate = actualDue;
        }
    }

    /**
     * Test a semimonthly repeating interval where one of the days is in
     * the fourth week of the month, with no specified end date.  Every
     * other due date should always be in the fourth week.
     */
    @Test
    public void testRepeatSemiMonthlyOnDaysFourthWeek() {
        LocalDate startDate = LocalDate.now()
                .withDayOfMonth(RAND.nextInt(7) + 21);
        WeekDays[] days = new WeekDays[2];
        int[] weeks = new int[2];
        days[0] = WeekDays.fromJavaDay(startDate.getDayOfWeek());
        weeks[0] = 3;
        // Choose our second day so that it lies on a different day than
        // the first day, and in one of the first three weeks.
        do {
            days[1] = WeekDays.values()[RAND.nextInt(WeekDays.values().length)];
        } while (days[1] == days[0]);
        weeks[1] = RAND.nextInt(3);
        RepeatSemiMonthlyOnDays repeat = new RepeatSemiMonthlyOnDays(startDate);
        repeat.setDay(days[0]);
        repeat.setDay2(days[1]);
        repeat.setWeek(weeks[0]);
        repeat.setWeek2(weeks[1]);
        String[] stndrd = new String[] {
                ordinalWeek(weeks[0]), ordinalWeek(weeks[1])
        };
        for (int i = 1; i <= 30; i++) {
            LocalDate completed = startDate.plusDays(RAND.nextInt(50));
            LocalDate expectedDue = setWeekAndDayForMonth(
                    (weeks[i%2] > weeks[(i+1)%2]) ?
                            startDate : startDate.plusMonths(1),
                    weeks[i%2], days[i%2]);
            LocalDate actualDue = repeat
                    .computeNextDueDate(startDate, completed);
            assertEquals(String.format(
                            "Next due date for task due %s (%s %s or %s %s),"
                                    + " completed %s",
                            startDate.format(DAY_FORMAT), stndrd[0], days[0],
                            stndrd[1], days[1], completed.format(DAY_FORMAT)),
                    expectedDue, actualDue);
            if (actualDue == null)
                // Technically unreachable, but the compiler can't tell
                break;
            startDate = actualDue;
        }
    }

    /**
     * Test a semimonthly repeating interval where one of the days is in
     * the last week of the month, with no specified end date.  Every
     * other due date should always be in the last week.
     */
    @Test
    public void testRepeatSemiMonthlyOnDaysLastWeek() {
        LocalDate startDate = LocalDate.now();
        startDate = startDate.withDayOfMonth(startDate.lengthOfMonth()
                - RAND.nextInt(7));
        WeekDays[] days = new WeekDays[2];
        int[] weeks = new int[2];
        days[0] = WeekDays.fromJavaDay(startDate.getDayOfWeek());
        weeks[0] = 4;
        // Choose our second day so that it lies on a different day than
        // the first day, and in one of the first three weeks.
        do {
            days[1] = WeekDays.values()[RAND.nextInt(WeekDays.values().length)];
        } while (days[1] == days[0]);
        weeks[1] = RAND.nextInt(3);
        RepeatSemiMonthlyOnDays repeat = new RepeatSemiMonthlyOnDays(startDate);
        repeat.setDay(days[0]);
        repeat.setDay2(days[1]);
        repeat.setWeek(weeks[0]);
        repeat.setWeek2(weeks[1]);
        String[] stndrd = new String[] {
                ordinalWeek(weeks[0]), ordinalWeek(weeks[1])
        };
        for (int i = 1; i <= 30; i++) {
            LocalDate completed = startDate.plusDays(RAND.nextInt(50));
            LocalDate expectedDue = setWeekAndDayForMonth(
                    (weeks[i%2] > weeks[(i+1)%2]) ?
                            startDate : startDate.plusMonths(1),
                    weeks[i%2], days[i%2]);
            LocalDate actualDue = repeat
                    .computeNextDueDate(startDate, completed);
            assertEquals(String.format(
                            "Next due date for task due %s (%s %s or %s %s),"
                                    + " completed %s",
                            startDate.format(DAY_FORMAT), stndrd[0], days[0],
                            stndrd[1], days[1], completed.format(DAY_FORMAT)),
                    expectedDue, actualDue);
            if (actualDue == null)
                // Technically unreachable, but the compiler can't tell
                break;
            startDate = actualDue;
        }
    }

    /**
     * Test a semimonthly repeating interval with a specified end date.
     */
    @Test
    public void testRepeatSemiMonthlyOnDaysWithEnd() {
        LocalDate startDate = LocalDate.now();
        // Choose any other day from 1-3 weeks away from our start date
        LocalDate otherDate = startDate.plusDays((RAND.nextBoolean() ? 1 : -1)
                * (7 + RAND.nextInt(14)));
        LocalDate endDate = startDate.plusMonths(RAND.nextInt(6) + 4);
        WeekDays[] days = new WeekDays[] {
                WeekDays.fromJavaDay(startDate.getDayOfWeek()),
                WeekDays.fromJavaDay(otherDate.getDayOfWeek())
        };
        int[] weeks = new int[] {
                (startDate.getDayOfMonth() - 1) / 7,
                (otherDate.getDayOfMonth() - 1) / 7
        };
        String[] stndrd = new String[] {
                ordinalWeek(weeks[0]), ordinalWeek(weeks[1])
        };
        RepeatSemiMonthlyOnDays repeat = new RepeatSemiMonthlyOnDays(startDate);
        repeat.setDay(days[0]);
        repeat.setDay2(days[1]);
        repeat.setWeek(weeks[0]);
        repeat.setWeek2(weeks[1]);
        repeat.setEnd(endDate);
        for (int i = 1; i <= 30; i++) {
            LocalDate completed = startDate.plusDays(RAND.nextInt(50));
            LocalDate expectedDue = setWeekAndDayForMonth(
                    (weeks[i%2] > weeks[(i+1)%2]) ?
                            startDate : startDate.plusMonths(1),
                    weeks[i%2], days[i%2]);
            if (expectedDue.isAfter(endDate))
                expectedDue = null;
            LocalDate actualDue = repeat
                    .computeNextDueDate(startDate, completed);
            assertEquals(String.format(
                            "Next due date for task due %s (%s %s or %s %s),"
                                    + " completed %s, ending %s",
                            startDate.format(DAY_FORMAT), stndrd[0], days[0],
                            stndrd[1], days[1], completed.format(DAY_FORMAT),
                            endDate.format(DAY_FORMAT)),
                    expectedDue, actualDue);
            if (actualDue == null)
                break;
            startDate = actualDue;
        }
    }

    /**
     * Test an every <i>N</i> months semimonthly repeating interval.
     */
    @Test
    public void testRepeatSemiMonthlyEveryNMonthsOnDays() {
        LocalDate startDate = LocalDate.now();
        // Choose any other day from 1-3 weeks away from our start date
        LocalDate otherDate = startDate.plusDays((RAND.nextBoolean() ? 1 : -1)
                * (7 + RAND.nextInt(14)));
        WeekDays[] days = new WeekDays[] {
                WeekDays.fromJavaDay(startDate.getDayOfWeek()),
                WeekDays.fromJavaDay(otherDate.getDayOfWeek())
        };
        int[] weeks = new int[] {
                (startDate.getDayOfMonth() - 1) / 7,
                (otherDate.getDayOfMonth() - 1) / 7
        };
        String[] stndrd = new String[] {
                ordinalWeek(weeks[0]), ordinalWeek(weeks[1])
        };
        int increment = RAND.nextInt(10) + 2;
        RepeatSemiMonthlyOnDays repeat = new RepeatSemiMonthlyOnDays(startDate);
        repeat.setDay(days[0]);
        repeat.setDay2(days[1]);
        repeat.setWeek(weeks[0]);
        repeat.setWeek2(weeks[1]);
        repeat.setIncrement(increment);
        for (int i = 1; i <= 30; i++) {
            LocalDate completed = startDate.plusDays(RAND.nextInt(50));
            LocalDate expectedDue = setWeekAndDayForMonth(
                    (weeks[i%2] > weeks[(i+1)%2]) ?
                            startDate : startDate.plusMonths(increment),
                    weeks[i%2], days[i%2]);
            LocalDate actualDue = repeat
                    .computeNextDueDate(startDate, completed);
            assertEquals(String.format(
                            "Next due date for task due %s (%s %s or %s %s),"
                                    + " completed %s",
                            startDate.format(DAY_FORMAT), stndrd[0], days[0],
                            stndrd[1], days[1], completed.format(DAY_FORMAT)),
                    expectedDue, actualDue);
            if (actualDue == null)
                // Technically unreachable, but the compiler can't tell
                break;
            startDate = actualDue;
        }
    }

}
