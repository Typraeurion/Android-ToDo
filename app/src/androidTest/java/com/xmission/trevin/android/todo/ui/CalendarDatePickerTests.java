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
package com.xmission.trevin.android.todo.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static com.xmission.trevin.android.todo.util.ViewActionUtils.*;
import static org.junit.Assert.*;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.view.View;
import android.widget.Button;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.xmission.trevin.android.todo.R;
import com.xmission.trevin.android.todo.data.repeat.WeekDays;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Tests for the {@link CalendarDatePicker}
 *
 * @author Trevin Beattie
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class CalendarDatePickerTests {

    static Instrumentation instrument = null;

    static Context testContext = null;

    public static final Random RAND = new Random();

    @BeforeClass
    public static void getTestContext() {
        instrument = InstrumentationRegistry.getInstrumentation();
        testContext = instrument.getTargetContext();
    }

    /**
     * Dialog listener for date set events
     */
    public static class TestDateSetDialogListener
            implements CalendarDatePickerDialog.OnDateSetListener {
        public CalendarDatePickerDialog dialog = null;
        public CalendarDatePicker widget = null;
        public LocalDate setDate = null;
        @Override
        public void onDateSet(CalendarDatePicker view, LocalDate date) {
            setDate = date;
        }
    }

    /**
     * Widget listener for date set events
     */
    public static class TestDateSetWidgetListener
            implements CalendarDatePicker.OnDateSetListener {
        public CalendarDatePicker widget = null;
        public LocalDate setDate = null;
        @Override
        public void onDateSet(CalendarDatePicker view, LocalDate date) {
            setDate = date;
        }
    }

    /**
     * Show the date picker widget for a given date.  This is used
     * for tests that check UI components in the widget and do
     * not need the dialog around it.
     *
     * @param scenario the scenario in which the test is running
     * @param showDate the date to set in the widget
     * @param today the current date for the purpose of the test
     *
     * @return the listener for the date set event
     */
    private TestDateSetWidgetListener showDatePickerWidget(
            ActivityScenario<ToDoNoteActivity> scenario,
            LocalDate showDate, LocalDate today) {
        final TestDateSetWidgetListener listener =
                new TestDateSetWidgetListener();
        scenario.onActivity(activity -> {
            listener.widget = new CalendarDatePicker(activity);
            listener.widget.setToday(today);
            listener.widget.setDate(showDate);
            activity.setContentView(listener.widget);
            listener.widget.setOnDateSetListener(listener);
        });
        return listener;
    }

    /**
     * Show the date picker dialog for a given date.  This is used
     * for tests that check listener passthroughs and main dialog
     * button actions.
     *
     * @param scenario the scenario in which the test is running
     * @param date the date to set in the dialog
     * @param zone the time zone to use for the dialog
     *
     * @return the listener for the date set event, which also
     * includes a reference to the date picker widget.
     */
    private TestDateSetDialogListener showDatePickerDialog(
            ActivityScenario<ToDoNoteActivity> scenario,
            LocalDate date, ZoneId zone) {
        final TestDateSetDialogListener listener =
                new TestDateSetDialogListener();
        scenario.onActivity(activity -> {
            listener.dialog = new CalendarDatePickerDialog(
                    activity, "Test", listener);
            listener.dialog.setToday(date);
            listener.dialog.setDate(date);
            listener.dialog.setTimeZone(zone);
            listener.dialog.show();
            listener.widget = listener.dialog
                    .findViewById(R.id.CalendarDatePicker);
        });
        /*
         * We need to wait for the dialog to be attached to the window,
         * otherwise Espresso may latch on to the wrong root and fails
         * to find the dialog view.
         */
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        long timeLimit = System.nanoTime() + 5000000000L;
        View decor = listener.dialog.getWindow().getDecorView();
        while (true) {
            boolean attached =
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
                            ? decor.isAttachedToWindow()
                            : (decor.getWindowToken() != null);
            if (attached && decor.isLaidOut() && decor.hasWindowFocus())
                break; // ready

            assertTrue("Dialog did not gain focus within 5 seconds",
                    System.nanoTime() < timeLimit);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            try {
                Thread.sleep(16);
            } catch (InterruptedException ix) {
                // Ignore
            }
        }
        return listener;
    }

    /**
     * Check whether a text-based UI element&rsquo;s text style is bold.
     *
     * @param view the UI element to check
     *
     * @return {@code true} if the text style is bold,
     * {@code false} otherwise.
     */
    public static boolean isBold(TextView view) {
        Typeface tf = view.getTypeface();
        if (tf == null)
            return false;
        return tf.isBold();
    }

    /**
     * Verify the year shown in the widget.
     *
     * @param widget the calendar date picker widget
     * @param expectedYear the expected year shown at the top of the calendar
     * @param boldYear whether the year should be shown in bold
     *
     * @throws AssertionError if the year is not displayed or does not
     * match the expected year or if the typeface is not in the expected
     * state
     */
    void verifyYear(CalendarDatePicker widget,
                    int expectedYear, boolean boldYear) {
        TextView yearText = widget.findViewById(R.id.DatePickerTextYear);
        assertNotNull("The year text box was not found", yearText);
        assertEquals("Displayed year", Integer.toString(expectedYear),
                yearText.getText().toString());
        assertEquals("Year typeface bold", boldYear, isBold(yearText));
    }

    /** Button ID&rsquo;s of month buttons, from January through December */
    public static int MONTH_BUTTON_IDS[] = {
            R.id.DatePickerJanuaryButton, R.id.DatePickerFebruaryButton,
            R.id.DatePickerMarchButton, R.id.DatePickerAprilButton,
            R.id.DatePickerMayButton, R.id.DatePickerJuneButton,
            R.id.DatePickerJulyButton, R.id.DatePickerAugustButton,
            R.id.DatePickerSeptemberButton, R.id.DatePickerOctoberButton,
            R.id.DatePickerNovemberButton, R.id.DatePickerDecemberButton
    };

    /**
     * String resource ID&rsquo;s of (short) month names,
     * from January through December
     */
    public static int MONTH_SHORT_NAME_IDS[] = {
            R.string.MonthJan, R.string.MonthFeb, R.string.MonthMar,
            R.string.MonthApr, R.string.MonthMay, R.string.MonthJun,
            R.string.MonthJul, R.string.MonthAug, R.string.MonthSep,
            R.string.MonthOct, R.string.MonthNov, R.string.MonthDec
    };

    /**
     * Verify the month buttons shown in the widget.
     *
     * @param widget the calendar date picker widget
     * @param boldMonth the month that should be shown in bold, if any;
     * {@code null} if no month should be shown in bold
     *
     * @throws AssertionError if any of the month buttons are missing
     * or disabled or if the current month is not shown in bold
     */
    void verifyMonthButtons(CalendarDatePicker widget, Month boldMonth) {
        for (Month month : Month.values()) {
            int buttonId = MONTH_BUTTON_IDS[month.getValue()
                    - Month.JANUARY.getValue()];
            String expectedText = widget.getResources().getString(
                    MONTH_SHORT_NAME_IDS[month.getValue()
                            - Month.JANUARY.getValue()]);
            Button button = widget.findViewById(buttonId);
            assertNotNull(String.format(Locale.US,
                    "The %s button was not found", month),
                    button);
            assertEquals(String.format(Locale.US,
                    "Visibility of %s", month),
                    View.VISIBLE, button.getVisibility());
            assertEquals(String.format(Locale.US,
                            "%s button text", month),
                    expectedText, button.getText().toString());
            assertTrue(String.format(Locale.US,
                    "The %s button is not enabled", month),
                    button.isEnabled());
            assertTrue(String.format(Locale.US,
                    "The %s button is not clickable", month),
                    button.isClickable());
            assertEquals(String.format(Locale.US,
                            "%s button typeface bold", month),
                    (month == boldMonth), isBold(button));
        }
    }

    /**
     * Label ID&rsquo;s for days of the week, from Sunday through Saturday
     */
    public static int DAY_LABEL_IDS[] = {
            R.id.DatePickerTextSunday, R.id.DatePickerTextMonday,
            R.id.DatePickerTextTuesday, R.id.DatePickerTextWednesday,
            R.id.DatePickerTextThursday, R.id.DatePickerTextFriday,
            R.id.DatePickerTextSaturday
    };

    /**
     * String resource ID&rsquo;s of day initials,
     * from (S)unday through (S)aturday
     */
    public static int DAY_NAME_INITIAL_IDS[] = {
            R.string.DatePickerSun, R.string.DatePickerMon,
            R.string.DatePickerTue, R.string.DatePickerWed,
            R.string.DatePickerThu, R.string.DatePickerFri,
            R.string.DatePickerSat
    };

    /**
     * Verify the day of the week column labels in the widget.
     *
     * @param widget the calendar date picker widget
     *
     * @throws AssertionError if any of the labels are missing
     */
    void verifyDayLabels(CalendarDatePicker widget) {
        String expectedText = "";
        for (WeekDays day : WeekDays.values()) {
            int textId = DAY_LABEL_IDS[day.getValue() - WeekDays.SUNDAY.getValue()];
            expectedText = widget.getResources().getString(
                    DAY_NAME_INITIAL_IDS[day.getValue()
                            - WeekDays.SUNDAY.getValue()]);
            TextView label = widget.findViewById(textId);
            assertNotNull(String.format(Locale.US,
                    "The %s label was not found", day.toString()),
                    label);
            assertEquals(String.format(Locale.US,
                            "%s label text", day.toString()),
                    expectedText, label.getText().toString());
        }
    }

    /** Row ID&rsquo;s of the weeks. */
    public static final int WEEK_ROW_IDS[] = {
            R.id.DatePickerWeekRow0, R.id.DatePickerWeekRow1,
            R.id.DatePickerWeekRow2, R.id.DatePickerWeekRow3,
            R.id.DatePickerWeekRow4, R.id.DatePickerWeekRow5
    };

    /**
     * Button ID&rsquo;s of the date buttons, arranged in a matrix.
     * First index is the week number (from 0) and second is the
     * day of the week (Sunday through Saturday).
     */
    public static int DATE_BUTTON_IDS[][] = {
            { R.id.DatePickerDay01Button, R.id.DatePickerDay02Button,
                    R.id.DatePickerDay03Button, R.id.DatePickerDay04Button,
                    R.id.DatePickerDay05Button, R.id.DatePickerDay06Button,
                    R.id.DatePickerDay07Button },
            { R.id.DatePickerDay11Button, R.id.DatePickerDay12Button,
                    R.id.DatePickerDay13Button, R.id.DatePickerDay14Button,
                    R.id.DatePickerDay15Button, R.id.DatePickerDay16Button,
                    R.id.DatePickerDay17Button },
            { R.id.DatePickerDay21Button, R.id.DatePickerDay22Button,
                    R.id.DatePickerDay23Button, R.id.DatePickerDay24Button,
                    R.id.DatePickerDay25Button, R.id.DatePickerDay26Button,
                    R.id.DatePickerDay27Button },
            { R.id.DatePickerDay31Button, R.id.DatePickerDay32Button,
                    R.id.DatePickerDay33Button, R.id.DatePickerDay34Button,
                    R.id.DatePickerDay35Button, R.id.DatePickerDay36Button,
                    R.id.DatePickerDay37Button },
            { R.id.DatePickerDay41Button, R.id.DatePickerDay42Button,
                    R.id.DatePickerDay43Button, R.id.DatePickerDay44Button,
                    R.id.DatePickerDay45Button, R.id.DatePickerDay46Button,
                    R.id.DatePickerDay47Button },
            { R.id.DatePickerDay51Button, R.id.DatePickerDay52Button,
                    R.id.DatePickerDay53Button, R.id.DatePickerDay54Button,
                    R.id.DatePickerDay55Button, R.id.DatePickerDay56Button,
                    R.id.DatePickerDay57Button }
    };

    /**
     * Verify the date buttons for the first row of the month in the widget.
     * This is used when the row is partial, e.g. there is no Sunday in the
     * first week.
     *
     * @param widget the calendar date picker widget
     * @param firstDay the day of the week for the 1<sup>st</sup> of the month
     * @param currentDate the date of the month that should be
     * highlighted, if any; otherwise {@code null}
     *
     * @throws AssertionError if any of the expected buttons are missing,
     * visibility is incorrect, disabled/enabled is incorrect, or the
     * current date (if in this row) is not shown in bold.
     */
    void verifyFirstRowDates(CalendarDatePicker widget,
                             WeekDays firstDay, Integer currentDate) {
        TableRow row = widget.findViewById(WEEK_ROW_IDS[0]);
        assertNotNull("The first week row was not found", row);
        assertTrue("First week row is not visible",
                row.getVisibility() == View.VISIBLE);
        for (WeekDays day : WeekDays.values()) {
            int buttonId = DATE_BUTTON_IDS[0][day.getValue()
                    - WeekDays.SUNDAY.getValue()];
            int expectedDate = day.getValue() - firstDay.getValue() + 1;
            Button button = widget.findViewById(buttonId);
            assertNotNull(String.format(Locale.US,
                    "The first week's %s button was not found", day), button);
            assertEquals(String.format(Locale.US,
                            "Visibility of %s in the first week", day),
                    (expectedDate <= 0) ? View.INVISIBLE : View.VISIBLE,
                    button.getVisibility());
            if (expectedDate > 0) {
                assertEquals(String.format(Locale.US,
                        "Text of the %s button in the first week", day),
                        Integer.toString(expectedDate),
                        button.getText().toString());
                assertEquals(String.format(Locale.US,
                                "%d button typeface bold", expectedDate),
                        Integer.valueOf(expectedDate).equals(currentDate),
                        isBold(button));
                assertTrue(String.format(Locale.US,
                        "First week's %s button is not enabled", day),
                        button.isEnabled());
                assertTrue(String.format(Locale.US,
                        "First week's %s button is not clickable", day),
                        button.isClickable());
            } else {
                assertFalse(String.format(Locale.US,
                                "First week's %s button is clickable", day),
                        button.isClickable());
            }
        }
    }

    /**
     * Verify the date buttons for a full row of days in the widget.
     *
     * @param widget the calendar date picker widget
     * @param weekRow the row number of the week, starting from 1
     * @param weekStart the date of Sunday in this week
     * @param currentDate the date of the month that should be
     * highlighted, if any; otherwise {@code null}
     *
     * @throws AssertionError if any of the expected buttons are missing,
     * invisible, disabled, or the current date (if in this row) is not
     * shown in bold.
     */
    void verifyNthRowDates(CalendarDatePicker widget,
                           int weekRow, int weekStart,
                           Integer currentDate) {
        TableRow row = widget.findViewById(WEEK_ROW_IDS[weekRow - 1]);
        assertNotNull(String.format(Locale.US,
                "Week %d row was not found", weekRow), row);
        assertTrue(String.format(Locale.US,
                "Week %d row is not visible", weekRow),
                row.getVisibility() == View.VISIBLE);
        for (WeekDays day : WeekDays.values()) {
            int buttonId = DATE_BUTTON_IDS[weekRow - 1][day.getValue()
                    - WeekDays.SUNDAY.getValue()];
            int expectedDate = weekStart + day.getValue()
                    - WeekDays.SUNDAY.getValue();
            Button button = widget.findViewById(buttonId);
            assertNotNull(String.format(Locale.US,
                    "Week %d %s button was not found", weekRow, day), button);
            assertTrue(String.format(Locale.US,
                    "%s in week %d is not visible", day, weekRow),
                    button.getVisibility() == View.VISIBLE);
            assertEquals(String.format(Locale.US,
                    "Text of the %s button in week %d", day, weekRow),
                    Integer.toString(expectedDate),
                    button.getText().toString());
            assertEquals(String.format(Locale.US,
                            "%s %d button typeface bold", day, expectedDate),
                    Integer.valueOf(expectedDate).equals(currentDate),
                    isBold(button));
            assertTrue(String.format(Locale.US,
                            "Week %d %s button is not enabled", weekRow, day),
                    button.isEnabled());
            assertTrue(String.format(Locale.US,
                            "Week %d %s button is not clickable", weekRow, day),
                    button.isClickable());
        }
    }

    /**
     * Verify the date buttons for the last row of the month in the widget.
     * This may also be used to check for an empty row by passing a
     * {@code weekStart} greater than the {@code lastDate}.
     *
     * @param widget the calendar date picker widget
     * @param weekRow the row number of the week, starting from 1
     * @param weekStart the date of Sunday in this week
     * @param lastDate the last date of the month
     * @param currentDate the date of the month that should be
     * highlighted, if any; otherwise {@code null}
     *
     * @throws AssertionError if any of the expected buttons are missing,
     * visibility is incorrect, disabled/enabled is incorrect, or the
     * current date (if in this row) is not shown in bold.
     */
    void verifyLastRowDates(CalendarDatePicker widget,
                            int weekRow, int weekStart, int lastDate,
                            Integer currentDate) {
        TableRow row = widget.findViewById(WEEK_ROW_IDS[weekRow - 1]);
        assertNotNull(String.format(Locale.US,
                "Week %d row was not found", weekRow), row);
        assertEquals(String.format(Locale.US,
                        "Week %d row is visible", weekRow),
                weekStart <= lastDate,
                row.getVisibility() == View.VISIBLE);
        for (WeekDays day : WeekDays.values()) {
            int buttonId = DATE_BUTTON_IDS[weekRow - 1][day.getValue()
                    - WeekDays.SUNDAY.getValue()];
            int expectedDate = weekStart + day.getValue()
                    - WeekDays.SUNDAY.getValue();
            Button button = widget.findViewById(buttonId);
            assertNotNull(String.format(Locale.US,
                    "Week %d %s button was not found", weekRow, day),
                    button);
            assertEquals(String.format(Locale.US,
                            "Visibility of %s in week %d", day, weekRow),
                    expectedDate <= lastDate,
                    button.getVisibility() == View.VISIBLE);
            if (expectedDate <= lastDate) {
                assertEquals(String.format(Locale.US,
                        "Text of the %s button in week %d", day, weekRow),
                        Integer.toString(expectedDate),
                        button.getText().toString());
                assertEquals(String.format(Locale.US,
                                "%d button typeface", expectedDate),
                        Integer.valueOf(expectedDate).equals(currentDate),
                        isBold(button));
                assertTrue(String.format(Locale.US,
                                "Week %d %s button is not enabled",
                                weekRow, day), button.isEnabled());
                assertTrue(String.format(Locale.US,
                                "Week %d %s button is not clickable",
                                weekRow, day), button.isClickable());
            } else {
                assertFalse(String.format(Locale.US,
                                "Week %d %s button is clickable",
                                weekRow, day),
                        button.isClickable());
            }
        }
    }

    /**
     * Test configuring the date picker for Februrary 13, 2026.
     * This is a rare month in which only four week rows are
     * displayed.
     */
    @Test
    public void testCalendarFebruary2026() {
        try (ActivityScenario<ToDoNoteActivity> scenario =
                     ActivityScenario.launch(ToDoNoteActivity.class)) {
            TestDateSetWidgetListener listener = showDatePickerWidget(
                    scenario, LocalDate.of(2026, 2, 13),
                    LocalDate.of(2026, 2, 13));
            verifyYear(listener.widget, 2026, true);
            verifyMonthButtons(listener.widget, Month.FEBRUARY);
            verifyDayLabels(listener.widget);
            for (int week = 1; week <= 4; week++)
                verifyNthRowDates(listener.widget, week,
                        1 + (week - 1) * 7, 13);
            for (int week = 5; week <= 6; week++)
                verifyLastRowDates(listener.widget, week,
                        1 + (week - 1) * 7, 28, 13);
        }
    }

    /**
     * Test configuring the date picker for November 27, 2025.
     * This is a month where the 1<sup>st</sup> is alone on a Saturday
     * and the 30<sup>th</sup> is alone on a Sunday, thus all six week
     * rows are required.
     */
    @Test
    public void testCalendarNovember2025() {
        try (ActivityScenario<ToDoNoteActivity> scenario =
                     ActivityScenario.launch(ToDoNoteActivity.class)) {
            TestDateSetWidgetListener listener = showDatePickerWidget(
                    scenario, LocalDate.of(2025, 11, 27),
                    LocalDate.of(2026, 2, 13));
            verifyYear(listener.widget, 2025, false);
            verifyMonthButtons(listener.widget, null);
            verifyDayLabels(listener.widget);
            verifyFirstRowDates(listener.widget, WeekDays.SATURDAY, null);
            for (int week = 2; week <= 5; week++)
                verifyNthRowDates(listener.widget, week,
                        2 + (week - 2) * 7, null);
            verifyLastRowDates(listener.widget, 6, 30, 30, null);
        }
    }

    /**
     * Test decrementing the year.  This goes from July 4, 2026
     * to July 2025.
     */
    @Test
    public void testJuly2026PriorYear() {
        try (ActivityScenario<ToDoNoteActivity> scenario =
                     ActivityScenario.launch(ToDoNoteActivity.class)) {
            TestDateSetWidgetListener listener = showDatePickerWidget(
                    scenario, LocalDate.of(2026, 7, 4),
                    LocalDate.of(2026, 7, 4));
            verifyYear(listener.widget, 2026, true);
            assertButtonShown(scenario, "Prior year button",
                    R.id.DatePickerPriorYearButton);
            verifyMonthButtons(listener.widget, Month.JULY);
            verifyFirstRowDates(listener.widget, WeekDays.WEDNESDAY, 4);
            verifyLastRowDates(listener.widget, 5, 26, 31, 4);

            pressButton(scenario, R.id.DatePickerPriorYearButton);
            verifyYear(listener.widget, 2025, false);
            verifyMonthButtons(listener.widget, null);
            verifyFirstRowDates(listener.widget, WeekDays.TUESDAY, null);
            verifyLastRowDates(listener.widget, 5, 27, 31, null);
        }
    }

    /**
     * Test incrementing the year.  This goes from November 2026
     * to November 25, 2027.
     */
    @Test
    public void testNovember2026NextYear() {
        try (ActivityScenario<ToDoNoteActivity> scenario =
                     ActivityScenario.launch(ToDoNoteActivity.class)) {
            TestDateSetWidgetListener listener = showDatePickerWidget(
                    scenario, LocalDate.of(2026, 11, 27),
                    LocalDate.of(2027, 11, 25));
            verifyYear(listener.widget, 2026, false);
            assertButtonShown(scenario, "Next year button",
                    R.id.DatePickerNextYearButton);
            verifyMonthButtons(listener.widget, null);
            verifyFirstRowDates(listener.widget, WeekDays.SUNDAY, null);
            verifyLastRowDates(listener.widget, 5, 29, 30, null);

            pressButton(scenario, R.id.DatePickerNextYearButton);
            verifyYear(listener.widget, 2027, true);
            verifyMonthButtons(listener.widget, Month.NOVEMBER);
            verifyFirstRowDates(listener.widget, WeekDays.MONDAY, 25);
            verifyLastRowDates(listener.widget, 5, 28, 30, 25);
        }
    }

    /**
     * Run a test for changing the month.
     *
     * @param fromDate the date from which to start the calendar
     * @param targetMonth the month to change to.  This must be
     * different than the month of {@code fromDate}.
     */
    private void runMonthChangeTest(LocalDate fromDate, Month targetMonth) {
        try (ActivityScenario<ToDoNoteActivity> scenario =
                     ActivityScenario.launch(ToDoNoteActivity.class)) {
            TestDateSetWidgetListener listener = showDatePickerWidget(
                    scenario, fromDate, fromDate);
            verifyYear(listener.widget, fromDate.getYear(), true);
            verifyMonthButtons(listener.widget, fromDate.getMonth());
            LocalDate firstDayOfSource = fromDate.withDayOfMonth(1);
            WeekDays dayOfFirst = WeekDays.fromJavaDay(
                    firstDayOfSource.getDayOfWeek());
            verifyFirstRowDates(listener.widget, dayOfFirst,
                    fromDate.getDayOfMonth());
            int row2Sunday = 8 + WeekDays.SUNDAY.getValue()
                    - dayOfFirst.getValue();
            int highlightWeek = (fromDate.getDayOfMonth()
                    - row2Sunday + 14) / 7;
            int lastWeek = (fromDate.lengthOfMonth() - row2Sunday + 14) / 7;
            if ((highlightWeek > 1) && (highlightWeek < lastWeek))
                verifyNthRowDates(listener.widget, highlightWeek,
                        row2Sunday + 7 * (highlightWeek - 2),
                        fromDate.getDayOfMonth());
            verifyLastRowDates(listener.widget, lastWeek,
                    row2Sunday + 7 * (lastWeek - 2),
                    fromDate.lengthOfMonth(), fromDate.getDayOfMonth());

            pressButton(scenario, MONTH_BUTTON_IDS[targetMonth.getValue()
                    - Month.JANUARY.getValue()]);
            verifyYear(listener.widget, fromDate.getYear(), true);
            verifyMonthButtons(listener.widget, fromDate.getMonth());
            LocalDate firstDayOfTarget = firstDayOfSource
                    .withMonth(targetMonth.getValue());
            dayOfFirst = WeekDays.fromJavaDay(
                    firstDayOfTarget.getDayOfWeek());
            verifyFirstRowDates(listener.widget, dayOfFirst, null);
            row2Sunday = 8 + WeekDays.SUNDAY.getValue()
                    - dayOfFirst.getValue();
            highlightWeek = (fromDate.getDayOfMonth()
                    - row2Sunday + 14) / 7;
            lastWeek = (firstDayOfTarget.lengthOfMonth()
                    - row2Sunday + 14) / 7;
            if ((highlightWeek > 1) && (highlightWeek < lastWeek))
                verifyNthRowDates(listener.widget, highlightWeek,
                        row2Sunday + 7 * (highlightWeek - 2), null);
            verifyLastRowDates(listener.widget, lastWeek,
                    row2Sunday + 7 * (lastWeek - 2),
                    firstDayOfTarget.lengthOfMonth(), null);
        }
    }

    /** Test changing the month from August 10, 2026 to January */
    @Test
    public void testAugust2026ToJanuary() {
        runMonthChangeTest(LocalDate.of(2026, 8, 10), Month.JANUARY);
    }

    /** Test changing the month from September 7, 2026 to February */
    @Test
    public void testSeptember2026ToFebruary() {
        runMonthChangeTest(LocalDate.of(2026, 9, 7), Month.FEBRUARY);
    }

    /** Test changing the month from October 31, 2026 to March */
    @Test
    public void testOctober2026ToMarch() {
        runMonthChangeTest(LocalDate.of(2026, 10, 31), Month.MARCH);
    }

    /** Test changing the month from November 26, 2026 to April */
    @Test
    public void testNovember2026ToApril() {
        runMonthChangeTest(LocalDate.of(2026, 11, 26), Month.APRIL);
    }

    /** Test changing the month from December 25, 2026 to May */
    @Test
    public void testDecember2026ToMay() {
        runMonthChangeTest(LocalDate.of(2026, 12, 25), Month.MAY);
    }

    /** Test changing the month from January 1, 2026 to June */
    @Test
    public void testJanuary2026ToJune() {
        runMonthChangeTest(LocalDate.of(2026, 1, 1), Month.JUNE);
    }

    /** Test changing the month from February 14, 2026 to July */
    @Test
    public void testFebruary2026ToJuly() {
        runMonthChangeTest(LocalDate.of(2026, 2, 14), Month.JULY);
    }

    /** Test changing the month from March 15, 2026 to August */
    @Test
    public void testMarch2026ToAugust() {
        runMonthChangeTest(LocalDate.of(2026, 3, 15), Month.AUGUST);
    }

    /** Test changing the month from April 11, 2026 to September */
    @Test
    public void testApril2026ToSeptember() {
        runMonthChangeTest(LocalDate.of(2026, 4, 11), Month.SEPTEMBER);
    }

    /** Test changing the month from May 1, 2026 to October */
    @Test
    public void testMay2026ToOctober() {
        runMonthChangeTest(LocalDate.of(2026, 5, 1), Month.OCTOBER);
    }

    /** Test changing the month from June 30, 2026 to November */
    @Test
    public void testJune2026ToNovember() {
        runMonthChangeTest(LocalDate.of(2026, 6, 30), Month.NOVEMBER);
    }

    /** Test changing the month from July 4, 2026 to December */
    @Test
    public void testJuly2026ToDecember() {
        runMonthChangeTest(LocalDate.of(2026, 7, 4), Month.DECEMBER);
    }

    /**
     * Run a test for selecting a date from the currently displayed month.
     * This test will display the date picker widget with the expected
     * year and month selected and a random date (not the expected date).
     *
     * @param week the week row from which to select a button
     * @param day the day of the week to press
     * @param expectedDate the year and month to show in the date picker
     * and the date of the month we expect to get back.
     */
    private void runDateSelectTest(int week, WeekDays day,
                                   LocalDate expectedDate) {
        try (ActivityScenario<ToDoNoteActivity> scenario =
                     ActivityScenario.launch(ToDoNoteActivity.class)) {
            LocalDate today;
            do {
                today = expectedDate.withDayOfMonth(
                        RAND.nextInt(expectedDate.lengthOfMonth()) + 1);
            } while (today.equals(expectedDate));
            TestDateSetWidgetListener listener = showDatePickerWidget(
                    scenario, today, today);
            int buttonId = DATE_BUTTON_IDS[week - 1][day.getValue()
                    - WeekDays.SUNDAY.getValue()];
            assertButtonShown(scenario, "Button for "
                    + expectedDate, buttonId);

            pressButton(scenario, buttonId);
            assertNotNull("The date set listener was not called back",
                    listener.setDate);
            assertEquals("Date set by the CalendarDatePicker",
                    expectedDate, listener.setDate);
        }
    }

    /**
     * Exercise the date selection buttons for the first week row.
     * For this we&rsquo;re going to choose the 1st of the first
     * month in 2026 that lands on each day of the week.
     */
    @Test
    public void testDateSelectWeek1() {
        runDateSelectTest(1, WeekDays.SUNDAY,
                LocalDate.of(2026, 2, 1));
        runDateSelectTest(1, WeekDays.MONDAY,
                LocalDate.of(2026, 6, 1));
        runDateSelectTest(1, WeekDays.TUESDAY,
                LocalDate.of(2026, 9, 1));
        runDateSelectTest(1, WeekDays.WEDNESDAY,
                LocalDate.of(2026, 4, 1));
        runDateSelectTest(1, WeekDays.THURSDAY,
                LocalDate.of(2026, 1, 1));
        runDateSelectTest(1, WeekDays.FRIDAY,
                LocalDate.of(2026, 5, 1));
        runDateSelectTest(1, WeekDays.SATURDAY,
                LocalDate.of(2026, 8, 1));
    }

    /**
     * Exercise the date selection buttons for the second through
     * fifth week rows.  This needs a month that has five Saturdays,
     * so we&rsquo;ll do this over January 2026.
     */
    @Test
    public void testDateSelectWeeks2Through5() {
        for (int week = 2; week <= 5; week++) {
            for (WeekDays day : WeekDays.values()) {
                LocalDate targetDate = LocalDate.of(2026, 1,
                        7 * week - 10 + day.getValue()
                                - WeekDays.SUNDAY.getValue());
                runDateSelectTest(week, day, targetDate);
            }
        }
    }

    /**
     * Exercise the date selection buttons for the sixth week row.
     * There are at most only two days possible in this row which
     * happens when a month has 31 days and the 1<sup>st</sup> is
     * on Saturday; so we&rsquo;ll to this in August 2026.
     */
    @Test
    public void testDateSelectWeek6() {
        runDateSelectTest(6, WeekDays.SUNDAY,
                LocalDate.of(2026, 8, 30));
        runDateSelectTest(6, WeekDays.MONDAY,
                LocalDate.of(2026, 8, 31));
    }

    /**
     * Test the dialog configuring the widget
     */
    @Test
    public void testDialogFebruary2026() {
        try (ActivityScenario<ToDoNoteActivity> scenario =
                     ActivityScenario.launch(ToDoNoteActivity.class)) {
            TestDateSetDialogListener listener =
                    showDatePickerDialog(scenario,
                            LocalDate.of(2026, 2, 27),
                            ZoneOffset.UTC);
            verifyYear(listener.widget, 2026, true);
            verifyMonthButtons(listener.widget, Month.FEBRUARY);
            verifyNthRowDates(listener.widget, 4, 22, 27);
        }
    }

    /**
     * Test the dialog's &ldquo;Today&rdquo; button when in Samoa (UTC-11).
     */
    @Test
    public void testDialogTodayInSamoa() {
        try (ActivityScenario<ToDoNoteActivity> scenario =
                     ActivityScenario.launch(ToDoNoteActivity.class)) {
            ZoneId zone = ZoneId.of("US/Samoa");
            TestDateSetDialogListener listener =
                    showDatePickerDialog(scenario,
                            LocalDate.of(2000, 1, 1), zone);
            // "Today" is set as button 2 in the dialog constructor.
            assertButtonShown(scenario, listener.dialog,
                    "Today", android.R.id.button2);
            pressButton(scenario, android.R.id.button2);
            assertNotNull("The date set listener was not called back",
                    listener.setDate);
            assertEquals(String.format(Locale.US,
                            "Date set for Today (in %s)", zone.toString()),
                    LocalDate.now(zone), listener.setDate);
        }
    }

    /**
     * Test the dialog's &ldquo;Today&rdquo; button when in Tongatapu (UTC+13).
     */
    @Test
    public void testDialogTodayInTongatapu() {
        try (ActivityScenario<ToDoNoteActivity> scenario =
                     ActivityScenario.launch(ToDoNoteActivity.class)) {
            ZoneId zone = ZoneId.of("Pacific/Tongatapu");
            TestDateSetDialogListener listener =
                    showDatePickerDialog(scenario,
                            LocalDate.of(2000, 1, 1), zone);
            // "Today" is set as button 2 in the dialog constructor.
            assertButtonShown(scenario, listener.dialog,
                    "Today", android.R.id.button2);
            pressButton(scenario, android.R.id.button2);
            assertNotNull("The date set listener was not called back",
                    listener.setDate);
            assertEquals(String.format(Locale.US,
                            "Date set for Today (in %s)", zone.toString()),
                    LocalDate.now(zone), listener.setDate);
        }
    }

    /**
     * Test the dialog's &ldquo;Cancel&rdquo; button.
     */
    @Test
    public void testDialogCancel() {
        try (ActivityScenario<ToDoNoteActivity> scenario =
                     ActivityScenario.launch(ToDoNoteActivity.class)) {
            TestDateSetDialogListener listener = showDatePickerDialog(
                    scenario, LocalDate.now(), ZoneId.systemDefault());
            // "Cancel" is set as button 1 in the dialog constructor.
            assertButtonShown(scenario, listener.dialog,
                    "Cancel", android.R.id.button1);
            pressButton(scenario, listener.dialog, android.R.id.button1);
            assertNull("The date set listener was called back",
                    listener.setDate);
        }
    }

}
