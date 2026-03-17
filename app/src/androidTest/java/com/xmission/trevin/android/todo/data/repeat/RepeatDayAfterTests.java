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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Tests for the &ldquo;day after&rdquo; repeating interval.
 * This interval always goes a fixed number of days after
 * the item&rsquo;s last completion date rather than its
 * previous due date.
 *
 * @author Trevin Beattie
 */
public class RepeatDayAfterTests {

    private static final Random RAND = new Random();

    /**
     * Formatter for dates to show the day of the week.
     */
    public static final DateTimeFormatter DAY_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);

    /**
     * Test a &ldquo;day after&rdquo; repeating interval, allowed on all
     * days (the default), with no specified end date.  The new due date
     * should always be the date after the item was completed.  We&rsquo;ll
     * only loop a hundred times to ensure this test ends in a reasonable
     * time.
     */
    @Test
    public void testRepeatDayAfterWithoutRestriction() {
        LocalDate startDate = LocalDate.now();
        RepeatDayAfter repeat = new RepeatDayAfter(startDate);
        for (int i = 0; i < 100; i++) {
            LocalDate completed = startDate.plusDays(RAND.nextInt(7));
            LocalDate expectedDue = completed.plusDays(1);
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
     * Test a &ldquo;day after&rdquo; repeating interval, allowed on all
     * days (the default), with a specified end date.  The new due date
     * should always be the date after the item was completed.
     */
    @Test
    public void testRepeatDayAfterWithEnd() {
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusDays(RAND.nextInt(30) + 20);
        RepeatDayAfter repeat = new RepeatDayAfter(startDate);
        repeat.setEnd(endDate);
        for (int i = 0; i < 100; i++) {
            LocalDate completed = startDate.plusDays(RAND.nextInt(7));
            LocalDate expectedDue = completed.plusDays(1);
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
     * Test an <i>N</i> days-after repeating interval, allowed on all days,
     * with no specified end date.  The new due date should be <i>N</i>
     * days after the item was completed.
     */
    @Test
    public void testRepeatNDaysAfterWithoutRestriction() {
        LocalDate startDate = LocalDate.now();
        int increment = RAND.nextInt(100) + 2;
        RepeatDayAfter repeat = new RepeatDayAfter(startDate);
        repeat.setIncrement(increment);
        for (int i = 0; i < 100; i++) {
            LocalDate completed = startDate.plusDays(RAND.nextInt(7));
            LocalDate expectedDue = completed.plusDays(increment);
            LocalDate actualDue = repeat
                    .computeNextDueDate(startDate, completed);
            assertEquals(String.format(
                            "Next due date for task due %s,"
                            + " %d days after completion on %s",
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
     * Test an <i>N</i> days-after repeating interval, allowed on all days,
     * with a specified end date.  The new due date should be <i>N</i>
     * days after the item was completed until that passes the end date.
     */
    @Test
    public void testRepeatNDaysAfterWithEnd() {
        LocalDate startDate = LocalDate.now();
        int increment = RAND.nextInt(100) + 2;
        LocalDate endDate = startDate.plusDays(
                RAND.nextInt(30 * increment) + 20 * increment);
        RepeatDayAfter repeat = new RepeatDayAfter(startDate);
        repeat.setIncrement(increment);
        repeat.setEnd(endDate);
        for (int i = 0; i < 100; i++) {
            LocalDate completed = startDate.plusDays(RAND.nextInt(7));
            LocalDate expectedDue = completed.plusDays(increment);
            if (expectedDue.isAfter(endDate))
                expectedDue = null;
            LocalDate actualDue = repeat
                    .computeNextDueDate(startDate, completed);
            assertEquals(String.format(
                            "Next due date for task due %s,"
                            + " %d days after completion on %s, ending %s",
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
     * Test a &ldquo;day after&rdquo; repeating interval allowed on a subset
     * of days of the week, with no specified end date.  The new due date
     * should always be on the next allowed day following the completion date.
     */
    public void testRepeatDayAfterRestrictedByDays(WeekdayDirection direction) {
        Set<WeekDays> allowed = randomDays();
        LocalDate startDate = LocalDate.now();
        while (!allowed.contains(WeekDays.fromJavaDay(startDate.getDayOfWeek())))
            startDate = startDate.plusDays(1);
        RepeatDayAfter repeat = new RepeatDayAfter(startDate);
        repeat.setAllowedWeekDays(allowed);
        repeat.setDirection(direction);
        for (int i = 0; i < 100; i++) {
            LocalDate completed = startDate.plusDays(RAND.nextInt(7));
            LocalDate expectedDue = completed.plusDays(1);
            while (!allowed.contains(WeekDays.fromJavaDay(expectedDue.getDayOfWeek())))
                expectedDue = expectedDue.plusDays(1);
            LocalDate actualDue = repeat
                    .computeNextDueDate(startDate, completed);
            assertEquals(String.format(
                            "Next due date for task due %s,"
                                    + " next %s after completion on %s",
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
    public void testRepeatDayAfterRestrictedByDaysNext() {
        testRepeatDayAfterRestrictedByDays(WeekdayDirection.NEXT);
    }

    @Test
    public void testRepeatDayAfterRestrictedByDaysPrevious() {
        testRepeatDayAfterRestrictedByDays(WeekdayDirection.PREVIOUS);
    }

    @Test
    public void testRepeatDayAfterRestrictedByDaysClosestOrNext() {
        testRepeatDayAfterRestrictedByDays(WeekdayDirection.CLOSEST_OR_NEXT);
    }

    @Test
    public void testRepeatDayAfterRestrictedByDaysClosestOrPrevious() {
        testRepeatDayAfterRestrictedByDays(WeekdayDirection.CLOSEST_OR_PREVIOUS);
    }

    /**
     * Test a &ldquo;day after&rdquo; repeating interval allowed on a subset
     * of days of the week, with a specified end date.  The new due date
     * should always be on the next allowed day following the completion date.
     */
    public void testRepeatDayAfterRestrictedByDaysWithEnd(WeekdayDirection direction) {
        Set<WeekDays> allowed = randomDays();
        LocalDate startDate = LocalDate.now();
        while (!allowed.contains(WeekDays.fromJavaDay(startDate.getDayOfWeek())))
            startDate = startDate.plusDays(1);
        LocalDate endDate = startDate.plusDays(RAND.nextInt(30) + 20);
        RepeatDayAfter repeat = new RepeatDayAfter(startDate);
        repeat.setAllowedWeekDays(allowed);
        repeat.setDirection(direction);
        repeat.setEnd(endDate);
        for (int i = 0; i < 100; i++) {
            LocalDate completed = startDate.plusDays(RAND.nextInt(7));
            LocalDate expectedDue = completed.plusDays(1);
            while (!allowed.contains(WeekDays.fromJavaDay(expectedDue.getDayOfWeek())))
                expectedDue = expectedDue.plusDays(1);
            if (expectedDue.isAfter(endDate))
                expectedDue = null;
            LocalDate actualDue = repeat
                    .computeNextDueDate(startDate, completed);
            assertEquals(String.format(
                            "Next due date for task due %s, next %s"
                                    + " after completion on %s, ending %s",
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
    public void testRepeatDayAfterRestrictedByDaysWithEndNext() {
        testRepeatDayAfterRestrictedByDaysWithEnd(WeekdayDirection.NEXT);
    }

    @Test
    public void testRepeatDayAfterRestrictedByDaysWithEndPrevious() {
        testRepeatDayAfterRestrictedByDaysWithEnd(WeekdayDirection.PREVIOUS);
    }

    @Test
    public void testRepeatDayAfterRestrictedByDaysWithEndClosestOrNext() {
        testRepeatDayAfterRestrictedByDaysWithEnd(WeekdayDirection.CLOSEST_OR_NEXT);
    }

    @Test
    public void testRepeatDayAfterRestrictedByDaysWithEndClosestOrPrevious() {
        testRepeatDayAfterRestrictedByDaysWithEnd(WeekdayDirection.CLOSEST_OR_PREVIOUS);
    }

    /**
     * Test an <i>N</i> days-after repeating interval, allowed on a subset
     * of days of the week, with no specified end date, using
     * {@link WeekdayDirection#NEXT} logic.
     */
    @Test
    public void testRepeatNDaysAfterRestrictedByDaysNext() {
        Set<WeekDays> allowed = randomDays();
        LocalDate startDate = LocalDate.now();
        int increment = RAND.nextInt(10) + 2;
        while (!allowed.contains(WeekDays.fromJavaDay(startDate.getDayOfWeek())))
            startDate = startDate.plusDays(1);
        RepeatDayAfter repeat = new RepeatDayAfter(startDate);
        repeat.setAllowedWeekDays(allowed);
        repeat.setDirection(WeekdayDirection.NEXT);
        repeat.setIncrement(increment);
        for (int i = 0; i < 30; i++) {
            LocalDate completed = startDate.plusDays(RAND.nextInt(7));
            LocalDate expectedDue = completed.plusDays(increment);
            while (!allowed.contains(WeekDays.fromJavaDay(expectedDue.getDayOfWeek())))
                expectedDue = expectedDue.plusDays(1);
            LocalDate actualDue = repeat
                    .computeNextDueDate(startDate, completed);
            assertEquals(String.format(
                            "Next due date for task due %s, next %s at least"
                                    + " %d days after completion on %s",
                            startDate.format(DAY_FORMAT), allowed,
                            increment, completed.format(DAY_FORMAT)),
                    expectedDue, actualDue);
            if (actualDue == null)
                // Technically unreachable, but the compiler can't tell
                break;
            startDate = actualDue;
        }
    }

    /**
     * Test an <i>N</i> days-after repeating interval, allowed on a subset
     * of days of the week, with no specified end date, using
     * {@link WeekdayDirection#PREVIOUS} logic.  This is more complicated
     * than going by {@code NEXT} due to the possibility of there not being
     * an available previous day of the week following the prior due date;
     * if that&rsquo;s the case, the interval should advance by another
     * <i>N</i> days.  Unlike with the RepeatDaily interval, we can&rsquo;t
     * simply used a fixed gap in the days because the completion date
     * should be at least a day after the due date in order to distinguish
     * this repeat interval from the other.
     */
    @Test
    public void testRepeatNDaysAfterRestrictedByDaysPrevious() {
        Set<WeekDays> allowed = new TreeSet<>();
        // Choose 3 consecutive days of the week to allow
        for (int d = RAND.nextInt(WeekDays.values().length);
             allowed.size() < 3; d++) {
            if (d >= WeekDays.SATURDAY.getValue())
                d = WeekDays.SUNDAY.getValue();
            allowed.add(WeekDays.fromValue(d));
        }
        LocalDate startDate = LocalDate.now();
        int increment = 2;
        while (!allowed.contains(WeekDays.fromJavaDay(startDate.getDayOfWeek())))
            startDate = startDate.plusDays(1);
        RepeatDayAfter repeat = new RepeatDayAfter(startDate);
        repeat.setAllowedWeekDays(allowed);
        repeat.setDirection(WeekdayDirection.PREVIOUS);
        repeat.setIncrement(increment);
        for (int i = 0; i < 30; i++) {
            // Ensure completion date is late and odd so we can distinguish
            // I*N days after completion from I*N days after last due.
            LocalDate completed = startDate.plusDays(2 * RAND.nextInt(2) + 1);
            int incrementMultiple = 1;
            LocalDate expectedDue = completed.plusDays(increment);
            while (!allowed.contains(WeekDays.fromJavaDay(
                    expectedDue.getDayOfWeek()))) {
                // We may need two searches: a backwards search for an
                // allowed day, and a forward jump by additional increments.
                expectedDue = expectedDue.minusDays(1);
                if (!expectedDue.isAfter(completed)) {
                    incrementMultiple++;
                    expectedDue = completed.plusDays((long)
                            increment * incrementMultiple);
                }
            }
            LocalDate actualDue = repeat
                    .computeNextDueDate(startDate, completed);
            assertEquals(String.format(
                            "Next due date for task due %s, previous %s at least"
                                    + " I*%d days after completion on %s",
                            startDate.format(DAY_FORMAT), allowed,
                            increment, completed.format(DAY_FORMAT)),
                    expectedDue, actualDue);
            if (actualDue == null)
                // Technically unreachable, but the compiler can't tell
                break;
            startDate = actualDue;
        }
    }

    /**
     * Test an <i>N</i> days-after repeating interval, allowed on a subset
     * of days of the week, with no specified end date, using
     * {@link WeekdayDirection#CLOSEST_OR_NEXT} logic.  This is more
     * complicated than going by just {@code NEXT} or {@code PREVIOUS}
     * since we need to consider how many days we might adjust the next
     * due date in either direction and it may entail tie breakers, on top
     * of the fact that a prior day of the week may not be after the
     * completion date.
     */
    @Test
    public void testRepeatNDaysAfterRestrictedByDaysClosestOrNext() {
        // Set the allowed days to the weekend
        Set<WeekDays> allowed = new TreeSet<>();
        allowed.add(WeekDays.SUNDAY);
        allowed.add(WeekDays.SATURDAY);
        // Start the first test on Sunday
        LocalDate startDate = LocalDate.now().with(DayOfWeek.SUNDAY);
        RepeatDayAfter repeat = new RepeatDayAfter(startDate);
        repeat.setIncrement(2);
        repeat.setAllowedWeekDays(allowed);
        repeat.setDirection(WeekdayDirection.CLOSEST_OR_NEXT);
        LocalDate completed = startDate.plusDays(1);
        // Wednesday is tied; we expect the next due date
        // to be this coming Saturday.
        LocalDate expectedDue = startDate.plusDays(6);
        LocalDate actualDue = repeat.computeNextDueDate(startDate, completed);
        assertEquals(String.format(
                        "Next due date for task due %s, closest %s 2 days"
                                + " after completion, completed %s",
                        startDate.format(DAY_FORMAT), allowed,
                        completed.format(DAY_FORMAT)),
                expectedDue, actualDue);

        // Now jump again from the next Sunday.  Sunday is the closest
        // available day to the target of Tuesday, but since that was
        // the completion date it should advance again to next Saturday.
        startDate = expectedDue;
        completed = startDate.plusDays(1);
        expectedDue = startDate.plusDays(7);
        actualDue = repeat.computeNextDueDate(startDate, completed);
        assertEquals(String.format(
                        "Next due date for task due %s, closest %s 2 days"
                                + " after completion, completed %s",
                        startDate.format(DAY_FORMAT), allowed,
                        completed.format(DAY_FORMAT)),
                expectedDue, actualDue);

        // Finally, increase the increment to 5 and complete the task
        // on Thursday.  The next due date would be Tuesday and the
        // closest available date to that is Sunday.
        startDate = expectedDue;
        repeat.setIncrement(5);
        completed = startDate.plusDays(5);
        expectedDue = completed.plusDays(3);
        actualDue = repeat.computeNextDueDate(startDate, completed);
        assertEquals(String.format(
                        "Next due date for task due %s, closest %s 5 days"
                                + " after completion, completed %s",
                        startDate.format(DAY_FORMAT), allowed,
                        completed.format(DAY_FORMAT)),
                expectedDue, actualDue);
    }

    /**
     * Test an <i>N</i> days-after repeating interval, allowed on a subset
     * of days of the week, with no specified end date, using
     * {@link WeekdayDirection#CLOSEST_OR_PREVIOUS} logic.  This is more
     * complicated than going by just {@code NEXT} or {@code PREVIOUS}
     * since we need to consider how many days we might adjust the next
     * due date in either direction and it may entail tie breakers, on top
     * of the fact that a prior day of the week may not be after the
     * completion date.
     */
    @Test
    public void testRepeatNDaysAfterRestrictedByDaysClosestOrPrevious() {
        // Set the allowed days to the weekend
        Set<WeekDays> allowed = new TreeSet<>();
        allowed.add(WeekDays.SUNDAY);
        allowed.add(WeekDays.SATURDAY);
        // Start the first test on Saturday
        LocalDate startDate = LocalDate.now().with(DayOfWeek.SATURDAY);
        RepeatDayAfter repeat = new RepeatDayAfter(startDate);
        repeat.setIncrement(3);
        repeat.setAllowedWeekDays(allowed);
        repeat.setDirection(WeekdayDirection.CLOSEST_OR_PREVIOUS);
        LocalDate completed = startDate.plusDays(1);
        // Wednesday is tied.  The direction should go to Sunday but since
        // that was the completion date it should then skip to Saturday.
        LocalDate expectedDue = startDate.plusDays(7);
        LocalDate actualDue = repeat.computeNextDueDate(startDate, completed);
        assertEquals(String.format(
                        "Next due date for task due %s, closest %s 3 days"
                                + " after completion, completed %s",
                        startDate.format(DAY_FORMAT), allowed,
                        completed.format(DAY_FORMAT)),
                expectedDue, actualDue);

        // Now increase the increment to 4 and jump again on the due date
        // (Saturday).  This time, Sunday is the closest.
        startDate = expectedDue;
        repeat.setIncrement(4);
        completed = startDate;
        expectedDue = startDate.plusDays(1);
        actualDue = repeat.computeNextDueDate(startDate, completed);
        assertEquals(String.format(
                        "Next due date for task due %s, closest %s 4 days"
                                + " after completion, completed %s",
                        startDate.format(DAY_FORMAT), allowed,
                        completed.format(DAY_FORMAT)),
                expectedDue, actualDue);

        // Finally, reduce the increment to 2 and try again from Monday.
        // Since the Wednesday is tied and the previous available day Sunday
        // is before the completion date, it should try again from Friday
        // whose closest available day is Saturday.
        startDate = expectedDue;
        repeat.setIncrement(2);
        completed = startDate.plusDays(2);
        expectedDue = startDate.plusDays(6);
        actualDue = repeat.computeNextDueDate(startDate, completed);
        assertEquals(String.format(
                        "Next due date for task due %s, closest %s 2 days"
                                + " after completion, completed %s",
                        startDate.format(DAY_FORMAT), allowed,
                        completed.format(DAY_FORMAT)),
                expectedDue, actualDue);
    }

}
