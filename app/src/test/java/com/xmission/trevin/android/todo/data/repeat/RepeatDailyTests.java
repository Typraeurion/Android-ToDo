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

import static org.junit.Assert.*;

import org.junit.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Tests for the daily repeating interval.
 *
 * @author Trevin Beattie
 */
public class RepeatDailyTests {

    private static final Random RAND = new Random();

    /**
     * Formatter for dates to show the day of the week.
     */
    public static final DateTimeFormatter DAY_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);

    /**
     * Test a daily repeating interval, allowed on all days (the default),
     * with no specified end date.  The new due date should be the same
     * regardless of when the item was completed.  We&rqsuo;ll only loop
     * a hundred times to ensure this test ends in a reasonable time.
     */
    @Test
    public void testRepeatDailyWithoutRestriction() {
        LocalDate startDate = LocalDate.now();
        RepeatDaily repeat = new RepeatDaily(startDate);
        for (int i = 0; i < 100; i++) {
            LocalDate completed = startDate.plusDays(RAND.nextInt(7));
            LocalDate expectedDue = startDate.plusDays(1);
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
     * Test a daily repeating interval, allowed on all days (the default),
     * with a specified end date.  The new due date should be the same
     * regardless of when the item was completed.
     */
    @Test
    public void testRepeatDailyWithEnd() {
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusDays(RAND.nextInt(30) + 20);
        RepeatDaily repeat = new RepeatDaily(startDate);
        repeat.setEnd(endDate);
        for (int i = 0; i < 100; i++) {
            LocalDate completed = startDate.plusDays(RAND.nextInt(7));
            LocalDate expectedDue = startDate.plusDays(1);
            if (expectedDue.isAfter(endDate))
                expectedDue = null;
            LocalDate actualDue = repeat
                    .computeNextDueDate(startDate, completed);
            assertEquals(String.format(
                    "Next due date for task due %s, completed %s, ending %s",
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
     * Test an every <i>N</i>-days repeating interval, allowed on all days,
     * with no specified end date.  The new due date should be the same
     * regardless of when the item was completed.  We&rqsuo;ll only loop
     * a hundred times to ensure this test ends in a reasonable time.
     */
    @Test
    public void testRepeatEveryNDaysWithoutRestriction() {
        LocalDate startDate = LocalDate.now();
        int increment = RAND.nextInt(100) + 2;
        RepeatDaily repeat = new RepeatDaily(startDate);
        repeat.setIncrement(increment);
        for (int i = 0; i < 100; i++) {
            LocalDate completed = startDate.plusDays(
                    increment + RAND.nextInt(7));
            LocalDate expectedDue = startDate.plusDays(increment);
            LocalDate actualDue = repeat
                    .computeNextDueDate(startDate, completed);
            assertEquals(String.format(
                    "Next due date for task due %s (every %d days), completed %s",
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
     * Test an every <i>N</i>-days repeating interval, allowed on all days,
     * with a specified end date.  The new due date should be the same
     * regardless of when the item was completed.
     */
    @Test
    public void testRepeatEveryNDaysWithEnd() {
        LocalDate startDate = LocalDate.now();
        int increment = RAND.nextInt(100) + 2;
        LocalDate endDate = startDate.plusDays(
                RAND.nextInt(30 * increment) + 20 * increment);
        RepeatDaily repeat = new RepeatDaily(startDate);
        repeat.setIncrement(increment);
        repeat.setEnd(endDate);
        for (int i = 0; i < 100; i++) {
            LocalDate completed = startDate.plusDays(
                    increment + RAND.nextInt(7));
            LocalDate expectedDue = startDate.plusDays(increment);
            if (expectedDue.isAfter(endDate))
                expectedDue = null;
            LocalDate actualDue = repeat
                    .computeNextDueDate(startDate, completed);
            assertEquals(String.format(
                            "Next due date for task due %s (every %d days),"
                            + " completed %s, ending %s",
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
     * Generate a random subset of days of the week.  This set will
     * contain at least one day but not all seven.  The set is
     * sorted so its order will be predictable (and easily readable)
     * in any assertion messages that may be output.
     *
     * @return a random set of days of the week
     */
    public static SortedSet<WeekDays> randomDays() {
        SortedSet<WeekDays> allowed = new TreeSet<>();
        for (WeekDays day : WeekDays.values()) {
            if (RAND.nextBoolean())
                allowed.add(day);
        }
        // Catch edge cases of empty or full set
        if (allowed.isEmpty())
            allowed.add(WeekDays.values()[
                    RAND.nextInt(WeekDays.values().length)]);
        else if (allowed.size() == WeekDays.values().length)
            allowed.remove(WeekDays.values()[
                    RAND.nextInt(WeekDays.values().length)]);
        return allowed;
    }

    /**
     * Test a daily repeating interval allowed on a subset of days of
     * the week, with no specified end date.  The new due date should be
     * the same regardless of when the item was completed.
     */
    public void testRepeatDailyRestrictedByDays(WeekdayDirection direction) {
        Set<WeekDays> allowed = randomDays();
        LocalDate startDate = LocalDate.now();
        while (!allowed.contains(WeekDays.fromJavaDay(startDate.getDayOfWeek())))
            startDate = startDate.plusDays(1);
        RepeatDaily repeat = new RepeatDaily(startDate);
        repeat.setAllowedWeekDays(allowed);
        repeat.setDirection(direction);
        for (int i = 0; i < 100; i++) {
            LocalDate completed = startDate.plusDays(RAND.nextInt(7));
            LocalDate expectedDue = startDate.plusDays(1);
            while (!allowed.contains(WeekDays.fromJavaDay(expectedDue.getDayOfWeek())))
                expectedDue = expectedDue.plusDays(1);
            LocalDate actualDue = repeat
                    .computeNextDueDate(startDate, completed);
            assertEquals(String.format(
                            "Next due date for task due %s (on %s), completed %s",
                            startDate.format(DAY_FORMAT), allowed,
                            completed.format(DAY_FORMAT)),
                    expectedDue, actualDue);
            if (actualDue == null)
                // Technically unreachable, but the compiler can't tell
                break;
            startDate = actualDue;
        }
    }

    @Test
    public void testRepeatDailyRestrictedByDaysNext() {
        testRepeatDailyRestrictedByDays(WeekdayDirection.NEXT);
    }

    @Test
    public void testRepeatDailyRestrictedByDaysPrevious() {
        testRepeatDailyRestrictedByDays(WeekdayDirection.PREVIOUS);
    }

    @Test
    public void testRepeatDailyRestrictedByDaysClosestOrNext() {
        testRepeatDailyRestrictedByDays(WeekdayDirection.CLOSEST_OR_NEXT);
    }

    @Test
    public void testRepeatDailyRestrictedByDaysClosestOrPrevious() {
        testRepeatDailyRestrictedByDays(WeekdayDirection.CLOSEST_OR_PREVIOUS);
    }

    /**
     * Test a daily repeating interval allowed on a subset of days of
     * the week, with a specified end date.  The new due date should be
     * the same regardless of when the item was completed.
     */
    public void testRepeatingDailyRestrictedByDaysWithEnd(WeekdayDirection direction) {
        Set<WeekDays> allowed = randomDays();
        LocalDate startDate = LocalDate.now();
        while (!allowed.contains(WeekDays.fromJavaDay(startDate.getDayOfWeek())))
            startDate = startDate.plusDays(1);
        LocalDate endDate = startDate.plusDays(RAND.nextInt(30) + 20);
        RepeatDaily repeat = new RepeatDaily(startDate);
        repeat.setAllowedWeekDays(allowed);
        repeat.setDirection(direction);
        repeat.setEnd(endDate);
        for (int i = 0; i < 100; i++) {
            LocalDate completed = startDate.plusDays(RAND.nextInt(7));
            LocalDate expectedDue = startDate.plusDays(1);
            while (!allowed.contains(WeekDays.fromJavaDay(expectedDue.getDayOfWeek())))
                expectedDue = expectedDue.plusDays(1);
            if (expectedDue.isAfter(endDate))
                expectedDue = null;
            LocalDate actualDue = repeat
                    .computeNextDueDate(startDate, completed);
            assertEquals(String.format(
                            "Next due date for task due %s (on %s), completed %s, ending %s",
                            startDate.format(DAY_FORMAT), allowed,
                            completed.format(DAY_FORMAT),
                            endDate.format(DAY_FORMAT)),
                    expectedDue, actualDue);
            if (actualDue == null)
                break;
            startDate = actualDue;
        }
    }

    @Test
    public void testRepeatingDailyRestrictedByDaysWithEndNext() {
        testRepeatingDailyRestrictedByDaysWithEnd(WeekdayDirection.NEXT);
    }

    @Test
    public void testRepeatingDailyRestrictedByDaysWithEndPrevious() {
        testRepeatingDailyRestrictedByDaysWithEnd(WeekdayDirection.PREVIOUS);
    }

    @Test
    public void testRepeatingDailyRestrictedByDaysWithEndClosestOrNext() {
        testRepeatingDailyRestrictedByDaysWithEnd(WeekdayDirection.CLOSEST_OR_NEXT);
    }

    @Test
    public void testRepeatingDailyRestrictedByDaysWithEndClosestOrPrevious() {
        testRepeatingDailyRestrictedByDaysWithEnd(WeekdayDirection.CLOSEST_OR_PREVIOUS);
    }

    /**
     * Test an every <i>N</i>-days repeating interval, allowed on a subset
     * of days of the week, with no specified end date, using
     * {@link WeekdayDirection#NEXT} logic.
     */
    @Test
    public void testRepeatEveryNDaysRestrictedByDaysNext() {
        Set<WeekDays> allowed = randomDays();
        LocalDate startDate = LocalDate.now();
        int increment = RAND.nextInt(10) + 2;
        // Avoid incrementing by an integer multiple of weeks
        if (increment >= 7)
            increment++;
        while (!allowed.contains(WeekDays.fromJavaDay(startDate.getDayOfWeek())))
            startDate = startDate.plusDays(1);
        RepeatDaily repeat = new RepeatDaily(startDate);
        repeat.setAllowedWeekDays(allowed);
        repeat.setDirection(WeekdayDirection.NEXT);
        repeat.setIncrement(increment);
        for (int i = 0; i < 30; i++) {
            LocalDate completed = startDate.plusDays(RAND.nextInt(7));
            LocalDate expectedDue = startDate.plusDays(increment);
            while (!allowed.contains(WeekDays.fromJavaDay(expectedDue.getDayOfWeek())))
                expectedDue = expectedDue.plusDays(1);
            LocalDate actualDue = repeat
                    .computeNextDueDate(startDate, completed);
            assertEquals(String.format(
                            "Next due date for task due %s (every %d days"
                                    + " on the next %s), completed %s",
                            startDate.format(DAY_FORMAT), increment,
                            allowed, completed.format(DAY_FORMAT)),
                    expectedDue, actualDue);
            if (actualDue == null)
                // Technically unreachable, but the compiler can't tell
                break;
            startDate = actualDue;
        }
    }

    /**
     * Test an every <i>N</i>-days repeating interval, allowed on a subset
     * of days of the week, with no specified end date, using
     * {@link WeekdayDirection#PREVIOUS} logic.  This is more complicated
     * than going by {@code NEXT} due to the possibility of there not being
     * an available previous day of the week following the prior due date;
     * if that&rsquo;s the case, the interval should advance by another
     * <i>N</i> days.  To properly test this we need to ensure a gap in
     * the allowed days greater than or equal to the increment.
     * <p>
     *     The most comprehensive test is achieved with 3 consecutive
     *     allowed days an an increment of 3 days.  For example, if we
     *     allow Monday, Tuesday, and Wednesday and start on Monday,
     *     the use cases are:
     *     <ol>
     *         <li>Monday &rarr; Thursday, go back to Wednesday</li>
     *         <li>Tuesday &rarr; Friday, go back to Wednesday</li>
     *         <li>Wednesday &rarr; Saturday &rarr; next Tuesday</li>
     *     </ol>
     *     (Any use case probably devolves to exclude one of the
     *     available days.)
     * </p>
     */
    @Test
    public void testRepeatEvery3DaysRestrictedByDaysPrevious() {
        Set<WeekDays> allowed = new TreeSet<>();
        for (int d = RAND.nextInt(WeekDays.values().length);
             allowed.size() < 3; d++) {
            if (d >= WeekDays.SATURDAY.getValue())
                d = WeekDays.SUNDAY.getValue();
            allowed.add(WeekDays.fromValue(d));
        }
        LocalDate startDate = LocalDate.now();
        int increment = 3;
        while (!allowed.contains(WeekDays.fromJavaDay(startDate.getDayOfWeek())))
            startDate = startDate.plusDays(1);
        RepeatDaily repeat = new RepeatDaily(startDate);
        repeat.setAllowedWeekDays(allowed);
        repeat.setDirection(WeekdayDirection.PREVIOUS);
        repeat.setIncrement(increment);
        for (int i = 0; i < 30; i++) {
            LocalDate completed = startDate.plusDays(RAND.nextInt(7));
            int incrementMultiple = 1;
            LocalDate expectedDue = startDate.plusDays(increment);
            while (!allowed.contains(WeekDays.fromJavaDay(
                    expectedDue.getDayOfWeek()))) {
                // We may need two searches: a backwards search for an
                // allowed day, and a forward jump by additional increments.
                expectedDue = expectedDue.minusDays(1);
                if (!expectedDue.isAfter(startDate)) {
                    incrementMultiple++;
                    expectedDue = startDate.plusDays((long)
                            increment * incrementMultiple);
                }
            }
            LocalDate actualDue = repeat
                    .computeNextDueDate(startDate, completed);
            assertEquals(String.format(
                            "Next due date for task due %s (every %d days"
                                    + " on the previous %s), completed %s",
                            startDate.format(DAY_FORMAT), increment,
                            allowed, completed.format(DAY_FORMAT)),
                    expectedDue, actualDue);
            if (actualDue == null)
                // Technically unreachable, but the compiler can't tell
                break;
            startDate = actualDue;
        }
    }

    /**
     * Test an every <i>N</i>-days repeating interval, allowed on a subset
     * of days of the week, with no specified end date, using
     * {@link WeekdayDirection#CLOSEST_OR_NEXT} logic.  This is more
     * complicated than going by just {@code NEXT} or {@code PREVIOUS}
     * since we need to consider how many days we might adjust the next
     * due date in either direction and it may entail tie breakers, on top
     * of the fact that a prior day of the week may not be after the
     * previous due date.
     */
    @Test
    public void testRepeatEveryNDaysRestrictedByDaysClosestOrNext() {
        // Set the allowed days to the weekend
        Set<WeekDays> allowed = new TreeSet<>();
        allowed.add(WeekDays.SUNDAY);
        allowed.add(WeekDays.SATURDAY);
        // Start the first test on Sunday
        LocalDate startDate = LocalDate.now().with(DayOfWeek.SUNDAY);
        RepeatDaily repeat = new RepeatDaily(startDate);
        repeat.setIncrement(3);
        repeat.setAllowedWeekDays(allowed);
        repeat.setDirection(WeekdayDirection.CLOSEST_OR_NEXT);
        LocalDate completed = startDate.plusDays(RAND.nextInt(7));
        // We expect the next due date to be this coming Saturday
        LocalDate expectedDue = startDate.plusDays(6);
        LocalDate actualDue = repeat.computeNextDueDate(startDate, completed);
        assertEquals(String.format(
                        "Next due date for task due %s (every %d days"
                                + " on the closest %s, ties to next), completed %s",
                        startDate.format(DAY_FORMAT), repeat.getIncrement(),
                        allowed, completed.format(DAY_FORMAT)),
                expectedDue, actualDue);

        // Now jump again from Saturday.  This time, Sunday is
        // the closest available day to the target of Tuesday.
        startDate = expectedDue;
        completed = startDate.plusDays(RAND.nextInt(7));
        expectedDue = startDate.plusDays(1);
        actualDue = repeat.computeNextDueDate(startDate, completed);
        assertEquals(String.format(
                        "Next due date for task due %s (every %d days"
                                + " on the closest %s, ties to next), completed %s",
                        startDate.format(DAY_FORMAT), repeat.getIncrement(),
                        allowed, completed.format(DAY_FORMAT)),
                expectedDue, actualDue);

        // Finally, reduce the increment to 2 and try again from Sunday.
        // Since the closest day is again Sunday, it should jump again
        // from Tuesday to Thursday, then Thursday to Saturday.
        startDate = expectedDue;
        completed = startDate.plusDays(RAND.nextInt(7));
        repeat.setIncrement(2);
        expectedDue = startDate.plusDays(6);
        actualDue = repeat.computeNextDueDate(startDate, completed);
        assertEquals(String.format(
                        "Next due date for task due %s (every %d days"
                                + " on the closest %s, ties to next), completed %s",
                        startDate.format(DAY_FORMAT), repeat.getIncrement(),
                        allowed, completed.format(DAY_FORMAT)),
                expectedDue, actualDue);
    }

    /**
     * Test an every <i>N</i>-days repeating allowed on a subset
     * of days of the week, with no specified end date, using
     * {@link WeekdayDirection#CLOSEST_OR_PREVIOUS} logic.  This is more
     * complicated than going by just {@code NEXT} or {@code PREVIOUS}
     * since we need to consider how many days we might adjust the next
     * due date in either direction and it may entail tie breakers, on top
     * of the fact that a prior day of the week may not be after the
     * previous due date.
     */
    @Test
    public void testRepeatEveryNDaysRestrictedByDaysClosestOrPrevious() {
        // Set the allowed days to the weekend
        Set<WeekDays> allowed = new TreeSet<>();
        allowed.add(WeekDays.SUNDAY);
        allowed.add(WeekDays.SATURDAY);
        // Start the first test on Saturday
        LocalDate startDate = LocalDate.now().with(DayOfWeek.SATURDAY);
        RepeatDaily repeat = new RepeatDaily(startDate);
        repeat.setIncrement(4);
        repeat.setAllowedWeekDays(allowed);
        repeat.setDirection(WeekdayDirection.CLOSEST_OR_PREVIOUS);
        LocalDate completed = startDate.plusDays(RAND.nextInt(7));
        // We expect the next due date to be Sunday (the day after it was last due)
        LocalDate expectedDue = startDate.plusDays(1);
        LocalDate actualDue = repeat.computeNextDueDate(startDate, completed);
        assertEquals(String.format(
                        "Next due date for task due %s (every %d days on"
                                + " the closest %s, ties to previous), completed %s",
                        startDate.format(DAY_FORMAT), repeat.getIncrement(),
                        allowed, completed.format(DAY_FORMAT)),
                expectedDue, actualDue);

        // Now jump again from Sunday.  This time, next Saturday is
        // the closest available day to the target of Thursday.
        startDate = expectedDue;
        completed = startDate.plusDays(RAND.nextInt(7));
        expectedDue = startDate.plusDays(6);
        actualDue = repeat.computeNextDueDate(startDate, completed);
        assertEquals(String.format(
                        "Next due date for task due %s (every %d days"
                                + " on the closest %s, ties to previous), completed %s",
                        startDate.format(DAY_FORMAT), repeat.getIncrement(),
                        allowed, completed.format(DAY_FORMAT)),
                expectedDue, actualDue);

        // Finally, reduce the increment to 3 and try again from Sunday.
        // Since the closest days are tied and the previous (tie breaker)
        // is back to Sunday, it should jump again from Wednesday to Saturday.
        startDate = expectedDue.plusDays(1);
        completed = startDate.plusDays(RAND.nextInt(7));
        repeat.setIncrement(3);
        expectedDue = startDate.plusDays(6);
        actualDue = repeat.computeNextDueDate(startDate, completed);
        assertEquals(String.format(
                        "Next due date for task due %s (every %d days"
                                + " on the closest %s, ties to previous), completed %s",
                        startDate.format(DAY_FORMAT), repeat.getIncrement(),
                        allowed, completed.format(DAY_FORMAT)),
                expectedDue, actualDue);
    }

}
