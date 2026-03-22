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

import static com.xmission.trevin.android.todo.util.LaunchUtils.hideKeyboard;
import static com.xmission.trevin.android.todo.util.ViewActionUtils.*;
import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.xmission.trevin.android.todo.R;
import com.xmission.trevin.android.todo.data.MockSharedPreferences;
import com.xmission.trevin.android.todo.data.ToDoPreferences;
import com.xmission.trevin.android.todo.data.repeat.*;
import com.xmission.trevin.android.todo.provider.MockToDoRepository;
import com.xmission.trevin.android.todo.provider.ToDoRepositoryImpl;
import com.xmission.trevin.android.todo.util.ActivityScenarioResultsWrapper;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoField;
import java.util.*;

/**
 * Tests for the {@link RepeatEditor}
 *
 * @author Trevin Beattie
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class RepeatEditorTests
        implements RepeatEditorDialog.OnRepeatSetListener {

    public static final Random RAND = new Random();

    static Instrumentation instrument = null;

    static Context testContext = null;

    private boolean onRepeatSetCalled;
    private RepeatInterval setRepeat;

    @BeforeClass
    public static void getTestContext() {
        instrument = InstrumentationRegistry.getInstrumentation();
        testContext = instrument.getTargetContext();
        /*
         * Although we don't use the preferences or repository directly
         * in this test, they are obtained indirectly when we launch
         * ToDoNoteActivity where we display the widget.  Using the
         * mock services here prevents errors in other activity tests
         * which rely on the mocks.
         */
        MockSharedPreferences mockPrefs = MockSharedPreferences.getInstance();
        ToDoPreferences.setSharedPreferences(mockPrefs);
        MockToDoRepository mockRepo = MockToDoRepository.getInstance();
        ToDoRepositoryImpl.setInstance(mockRepo);
    }

    @Before
    public void resetRepeat() {
        onRepeatSetCalled = false;
        setRepeat = null;
    }

    /** Called when the repeat interval is set in the dialog */
    @Override
    public void onRepeatSet(RepeatEditor editor, RepeatInterval repeat) {
        onRepeatSetCalled = true;
        setRepeat = repeat;
    }

    /**
     * Show the repeat editor widget for a given repeat interval.
     * This is used for tests that check UI components in the widget
     * and do not need the dialog around it.
     *
     * @param scenario the scenario in which the test is running
     * @param repeat the repeat interval to set in the widget
     * @param dueDate the due date to save in the widget&rsquo;s settings
     * @param zone the time zone to use in determining the end date boundary
     *
     * @return the repeat editor widget
     */
    private <T extends Activity> RepeatEditor showRepeatEditorWidget(
            ActivityScenario<T> scenario,
            RepeatInterval repeat, LocalDate dueDate, ZoneId zone) {
        hideKeyboard(scenario);
        final RepeatEditor[] editor = new RepeatEditor[1];
        scenario.onActivity(activity -> {
            editor[0] = new RepeatEditor(activity);
            editor[0].setRepeat(repeat, dueDate, zone);
            activity.setContentView(editor[0]);
        });
        return editor[0];
    }

    /**
     * Show the repeat editor widget for a given settings object.
     * This is used for tests that check UI components for when
     * the widget is restored from a saved state.
     *
     * @param scenario the scenario in which the test is running
     * @param settings the repeat settings to set in the widget
     *
     * @return the repeat editor widget
     */
    private <T extends Activity> RepeatEditor showRepeatEditorWidget(
            ActivityScenario<T> scenario, RepeatSettings settings) {
        hideKeyboard(scenario);
        final RepeatEditor[] editor = new RepeatEditor[1];
        scenario.onActivity(activity -> {
            editor[0] = new RepeatEditor(activity);
            editor[0].restoreSettings(settings);
            activity.setContentView(editor[0]);
        });
        return editor[0];
    }

    /**
     * Show the repeat editor dialog for a given repeat interval.
     * This is used for tests that check the dialog&rsquo;s main button
     * actions (&ldquo;OK&rsquo; or &ldquo;Cancel&rdquo;).
     *
     * @param scenario the scenario in which the test is running
     * @param repeat the repeat interval to set in the widget
     * @param dueDate the due date to save in the widget&rsquo;s settings
     * @param zone the time zone to use in determining the end date boundary
     *
     * @return the repeat editor dialog
     */
    private RepeatEditorDialog showRepeatEditorDialog(
            ActivityScenario<ToDoNoteActivity> scenario,
            RepeatInterval repeat, LocalDate dueDate, ZoneId zone) {
        hideKeyboard(scenario);
        final RepeatEditorDialog[] dialog = new RepeatEditorDialog[1];
        scenario.onActivity(activity -> {
            dialog[0] = new RepeatEditorDialog(activity, this);
            dialog[0].setRepeat(repeat, dueDate, zone);
            dialog[0].show();
        });
        /*
         * We need to wait for the dialog to be attached to the window,
         * otherwise Espresso may latch on to the wrong root and fails
         * to find the dialog view.
         */
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        long timeLimit = System.nanoTime() + 5000000000L;
        View decor = dialog[0].getWindow().getDecorView();
        while (true) {
            boolean attached = ViewCompat.isAttachedToWindow(decor);
            boolean laidOut = ViewCompat.isLaidOut(decor);
            if (attached && laidOut && decor.hasWindowFocus())
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
        return dialog[0];
    }

    /** Button ID&rsquo;s of the base interval types in this widget */
    public static int[] BASE_TYPE_BUTTON_IDS = {
            R.id.RepeatRadioButtonNone, R.id.RepeatRadioButtonDaily,
            R.id.RepeatRadioButtonWeekly, R.id.RepeatRadioButtonSemiMonthly,
            R.id.RepeatRadioButtonMonthly, R.id.RepeatRadioButtonYearly
    };

    /** String resource ID&rsquo;s of the base interval types */
    public static int[] BASE_TYPE_NAME_IDS = {
            R.string.RepeatTabNone, R.string.RepeatTabDaily,
            R.string.RepeatTabWeekly, R.string.RepeatTabSemiMonthly,
            R.string.RepeatTabMonthly, R.string.RepeatTabYearly
    };

    /** Button ID&rsquo;s of the days of the week in this widget */
    public static int[] DAY_BUTTON_IDS = {
            R.id.RepeatToggleSunday, R.id.RepeatToggleMonday,
            R.id.RepeatToggleTuesday, R.id.RepeatToggleWednesday,
            R.id.RepeatToggleThursday, R.id.RepeatToggleFriday,
            R.id.RepeatToggleSaturday
    };

    /**
     * String resource ID&rsquo;s of the day initials,
     * from Sunday through Saturday
     */
    public static int[] DAY_NAME_INITIAL_IDS = {
            R.string.DatePickerSun, R.string.DatePickerMon,
            R.string.DatePickerTue, R.string.DatePickerWed,
            R.string.DatePickerThu, R.string.DatePickerFri,
            R.string.DatePickerSat
    };

    /**
     * Verify the visibility of a view in the repeat editor widget.
     *
     * @param scenario the scenario in which the test is running
     * @param widget the repeat editor widget
     * @param viewName the name of the view being tested
     * for use in any assertion messages
     * @param viewId the resource ID of the view to find
     * @param viewClass the expected class of the view
     * @param expectedVisible whether we expect the view to be visible
     *
     * @return the view
     *
     * @throws AssertionError if the view is missing or its visibility
     * is not in the expected state
     */
    @SuppressWarnings("unchecked")
    private <T extends Activity, V extends View> V assertViewVisibility(
            @NonNull ActivityScenario<T> scenario,
            @NonNull RepeatEditor widget,
            @NonNull String viewName,
            int viewId,
            @NonNull Class<V> viewClass,
            boolean expectedVisible) {
        View[] view = new View[1];
        scenario.onActivity(activity -> {
            view[0] = widget.findViewById(viewId);
        });
        assertNotNull(viewName + " view not found", view[0]);
        assertEquals(viewName + " visibility",
                expectedVisible, view[0].isShown());
        assertTrue(String.format(Locale.US, "%s class expected:%s but was:%s",
                viewName, viewClass.getSimpleName(),
                view[0].getClass().getSimpleName()),
                viewClass.isInstance(view[0]));
        return (V) view[0];
    }

    /**
     * Verify which of the base interval type buttons is toggled.
     *
     * @param scenario the scenario in which the test is running
     * @param widget the repeat editor widget
     * @param expectedButtonId the resource ID of the button that
     * should be toggled on
     *
     * @throws AssertionError if any of the type buttons are missing,
     * not visible, or in the wrong state
     */
    private <T extends Activity> void assertRepeatTypeButtons(
            @NonNull ActivityScenario<T> scenario,
            @NonNull RepeatEditor widget,
            int expectedButtonId) {
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < BASE_TYPE_BUTTON_IDS.length; i++) {
            String expectedLabel = testContext.getString(
                    BASE_TYPE_NAME_IDS[i]);
            String buttonName = expectedLabel + " button";
            RadioButton button;
            try {
                button = assertViewVisibility(scenario, widget, buttonName,
                        BASE_TYPE_BUTTON_IDS[i], RadioButton.class, true);
            } catch (AssertionError ae) {
                errors.add(ae.getMessage());
                continue;
            }
            String actualLabel = button.getText().toString();
            if (!expectedLabel.equals(actualLabel))
                errors.add(String.format(Locale.US,
                        "%s label expected:%s but was:%s",
                        buttonName, expectedLabel, actualLabel));
            boolean isExpectedOn =
                    (BASE_TYPE_BUTTON_IDS[i] == expectedButtonId);
            if (button.isChecked() != isExpectedOn)
                errors.add(String.format(Locale.US,
                        "%s state expected:%s but was:%s",
                        buttonName, isExpectedOn ? "on" : "off",
                        button.isChecked() ? "on" : "off"));
        }
        if (!errors.isEmpty()) {
            if (errors.size() == 1)
                fail(errors.get(0));
            else
                fail("Multiple errors:\n" + String.join("\n", errors));
        }
    }

    /**
     * Verify which of the &ldquo;fixed schedule&rdquo; or &ldquo;after
     * completed&rdquo; buttons is toggled.
     *
     * @param scenario the scenario in which the test is running
     * @param widget the repeat editor widget
     * @param expectedButtonId the resource ID of the button that
     * should be toggled on, or -1 if the whole group should be hidden.
     *
     * @throws AssertionError if any of the type buttons are missing,
     * not visible when one is expected (or visible when neither one is),
     * or in the wrong state
     */
    private <T extends Activity> void assertResetTypeButtons(
            @NonNull ActivityScenario<T> scenario,
            @NonNull RepeatEditor widget,
            int expectedButtonId) {
        assertViewVisibility(scenario, widget,
                "Fixed/After radio group", R.id.RepeatRadioGroupReset,
                ViewGroup.class, expectedButtonId > 0);
        if (expectedButtonId <= 0)
            return;
        List<String> errors = new ArrayList<>();
        String expectedFixedText = testContext.getString(
                R.string.RepeatOptionFixedSchedule);
        String expectedAfterText = testContext.getString(
                R.string.RepeatOptionAfterCompleted);
        try {
            RadioButton fixedButton = assertViewVisibility(scenario, widget,
                    expectedFixedText + " button", R.id.RepeatRadioButtonFixedSchedule,
                    RadioButton.class, true);
            String actualFixedText = fixedButton.getText().toString();
            if (!expectedFixedText.equals(actualFixedText))
                errors.add(String.format(Locale.US,
                        "%s button label expected:%s but was:%s",
                        expectedFixedText, expectedFixedText, actualFixedText));
            if (fixedButton.isChecked() != (expectedButtonId
                    == R.id.RepeatRadioButtonFixedSchedule))
                errors.add(String.format(Locale.US,
                        "%s button state expected:%s but was:%s",
                        expectedFixedText,
                        expectedButtonId == R.id.RepeatRadioButtonFixedSchedule,
                        fixedButton.isChecked()));
        } catch (AssertionError ae) {
            errors.add(ae.getMessage());
        }
        try {
            RadioButton afterButton = assertViewVisibility(scenario, widget,
                    expectedAfterText + " button", R.id.RepeatRadioButtonAfterCompleted,
                    RadioButton.class, true);
            String actualAfterText = afterButton.getText().toString();
            if (!expectedAfterText.equals(actualAfterText))
                errors.add(String.format(Locale.US,
                        "%s button label expected:%s but was:%s",
                        expectedAfterText, expectedAfterText, actualAfterText));
            if (afterButton.isChecked() != (expectedButtonId
                    == R.id.RepeatRadioButtonAfterCompleted))
                errors.add(String.format(Locale.US,
                        "%s button state expected:%s but was:%s",
                        expectedAfterText,
                        expectedButtonId == R.id.RepeatRadioButtonAfterCompleted,
                        afterButton.isChecked()));
        } catch (AssertionError ae) {
            errors.add(ae.getMessage());
        }
        if (!errors.isEmpty()) {
            if (errors.size() == 1)
                fail(errors.get(0));
            else
                fail("Multiple errors:\n" + String.join("\n", errors));
        }
    }

    /**
     * Verify the repeat interval
     *
     * @param scenario the scenario in which the test is running
     * @param widget the repeat editor widget
     * @param expectedIncrement the expected value of the
     * repeat increment in the edit text box
     * @param expectedUnit the text that should be shown after the edit box
     *
     * @throws AssertionError if the edit text does not contain the
     * specified value or the label does not show the correct units
     */
    private <T extends Activity> void assertIntervalText(
            @NonNull ActivityScenario<T> scenario,
            @NonNull RepeatEditor widget,
            int expectedIncrement,
            String expectedUnit) {
        List<String> errors = new ArrayList<>();
        try {
            EditText inputBox = assertViewVisibility(scenario, widget,
                    "Interval input", R.id.RepeatEditTextEvery,
                    EditText.class, true);
            String actualIncrement = inputBox.getText().toString();
            if (!Integer.toString(expectedIncrement).equals(actualIncrement))
                errors.add(String.format(Locale.US,
                        "Interval input expected:%d but was:%s",
                        expectedIncrement, actualIncrement));
        } catch (AssertionError ae) {
            errors.add(ae.getMessage());
        }
        try {
            TextView unitLabel = assertViewVisibility(scenario, widget,
                    "Interval unit", R.id.RepeatTextPeriod,
                    TextView.class, true);
            String actualLabel = unitLabel.getText().toString();
            if (!expectedUnit.equals(actualLabel))
                errors.add(String.format(Locale.US,
                        "Interval unit label expected:%s but was:%s",
                        expectedUnit, actualLabel));
        } catch (AssertionError ae) {
            errors.add(ae.getMessage());
        }
        if (!errors.isEmpty()) {
            if (errors.size() == 1)
                fail(errors.get(0));
            else
                fail("Multiple errors:\n" + String.join("\n", errors));
        }
    }

    /**
     * Verify the end date button
     *
     * @param scenario the scenario in which the test is running
     * @param widget the repeat editor widget
     * @param expectedDate the expected date (may be {@code null})
     *
     * @throws AssertionError if the end date button is missing or
     * its text does not match the expected end date
     */
    private <T extends Activity> void assertEndDateButton(
            @NonNull ActivityScenario<T> scenario,
            @NonNull RepeatEditor widget,
            @Nullable LocalDate expectedDate) {
        Button endButton = assertViewVisibility(scenario, widget,
                "End date button", R.id.RepeatButtonEndDate,
                Button.class, true);
        if (expectedDate == null) {
            assertEquals("End date button text",
                    testContext.getString(R.string.RepeatButtonNoEndDate),
                    endButton.getText().toString());
        } else {
            // The end date may be presented in any of several different
            // formats, so we'll need to parse it to see if the date matches.
            DateTimeFormatter[] formats = {
                    // These first few formats are not actually used
                    // in the widget, but the ISO format is standard.
                    DateTimeFormatter.ISO_DATE,
                    // One of these next three probably should be used?
                    DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL),
                    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM),
                    DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT),
                    // The following three are actually used;
                    // the selection depends on how far out the date is.
                    // They really should have a localized form.
                    new DateTimeFormatterBuilder()
                            .appendPattern("EEEE, MMM d")
                            .parseDefaulting(ChronoField.YEAR,
                                    expectedDate.getYear())
                            .parseDefaulting(ChronoField.YEAR_OF_ERA,
                                    expectedDate.getYear())
                            .toFormatter(),
                    new DateTimeFormatterBuilder()
                            .appendPattern("EEE, MMMM d")
                            .parseDefaulting(ChronoField.YEAR,
                                    expectedDate.getYear())
                            .parseDefaulting(ChronoField.YEAR_OF_ERA,
                            expectedDate.getYear())
                            .toFormatter(),
                    DateTimeFormatter.ofPattern("MMMM d, yyyy"),
            };
            LocalDate parsedDate = null;
            String buttonText = endButton.getText().toString();
            for (DateTimeFormatter format : formats) try {
                parsedDate = LocalDate.from(format.parse(buttonText));
                break;
            } catch (DateTimeParseException dpe) {
                // Continue to the next format
            }
            assertNotNull(String.format(Locale.US,
                    "Could not parse the end date button: \"%s\"",
                    buttonText), parsedDate);
            assertEquals("End date shown on the button",
                    expectedDate, parsedDate);
        }
    }

    /**
     * Verify the toggle buttons for the days of the week in the widget.
     *
     * @param scenario the scenario in which the test is running
     * @param widget the repeat editor widget
     * @param expectedDays the set of days that are expected to be
     * toggled on, or {@code null} if we expect the entire row to
     * not be shown.
     *
     * @throws AssertionError if the state of the toggle buttons does not
     * match the expected state
     */
    private <T extends Activity> void assertWeekDayToggles(
            @NonNull ActivityScenario<T> scenario,
            @NonNull RepeatEditor widget,
            @Nullable Set<WeekDays> expectedDays) {
        assertViewVisibility(scenario, widget, "Weekdays row",
                R.id.RepeatRowWeekdays, ViewGroup.class,
                expectedDays != null);
        if (expectedDays == null)
            return;
        List<String> errors = new ArrayList<>();
        for (WeekDays day : WeekDays.values()) {
            int buttonId = DAY_BUTTON_IDS[day.getValue()
                    - WeekDays.SUNDAY.getValue()];
            String expectedLabel = testContext.getString(
                    DAY_NAME_INITIAL_IDS[day.getValue()
                            - WeekDays.SUNDAY.getValue()]);
            String buttonName = day + " button";
            ToggleButton button;
            try {
                button = assertViewVisibility(
                        scenario, widget, buttonName, buttonId,
                        ToggleButton.class, true);
            } catch (AssertionError ae) {
                errors.add(ae.getMessage());
                continue;
            }
            String actualLabel = button.getText().toString();
            if (!expectedLabel.equals(actualLabel))
                errors.add(String.format(Locale.US,
                        "%s label expected:%s but was:%s",
                        buttonName, expectedLabel, actualLabel));
            if (expectedDays.contains(day) != button.isChecked())
                errors.add(String.format(Locale.US,
                        "%s toggle expected:%s but was:%s",
                        buttonName, expectedDays.contains(day),
                        button.isChecked()));
        }
        if (!errors.isEmpty()) {
            if (errors.size() == 1)
                fail(errors.get(0));
            else
                fail("Multiple errors:\n" + String.join("\n", errors));
        }
    }

    /**
     * Verify which of the &ldquo;From actual&rdquo; toggle and radio
     * buttons are selected.
     *
     * @param scenario the scenario in which the test is running
     * @param widget the repeat editor widget
     * @param expectedDirection the {@link WeekdayDirection} which
     * these buttons should reflect, or {@code null} if this entire
     * row should be hidden.
     *
     * @throws AssertionError if any of these buttons are missing, not
     * visible when expected (or visible when not), or in the wrong state.
     */
    private <T extends Activity> void assertDirectionButtons(
            @NonNull ActivityScenario<T> scenario,
            @NonNull RepeatEditor widget,
            @Nullable WeekdayDirection expectedDirection) {
        assertViewVisibility(scenario, widget, "From actual row",
                R.id.RepeatRowAlternateDirection, ViewGroup.class,
                expectedDirection != null);
        if (expectedDirection == null)
            return;
        List<String> errors = new ArrayList<>();
        try {
            ToggleButton nearestButton = assertViewVisibility(
                    scenario, widget, "Nearest button",
                    R.id.RepeatToggleNearest, ToggleButton.class, true);
            String expectedLabel = testContext.getString(
                    R.string.RepeatOptionNearest);
            String actualLabel = nearestButton.getText().toString();
            if (!expectedLabel.equals(actualLabel))
                errors.add(String.format(Locale.US,
                        "Nearest button label expected:%s but was:%s",
                        expectedLabel, actualLabel));
            boolean expectedState = (expectedDirection ==
                    WeekdayDirection.CLOSEST_OR_NEXT) || (expectedDirection ==
                    WeekdayDirection.CLOSEST_OR_PREVIOUS);
            if (nearestButton.isChecked() != expectedState)
                errors.add(String.format(Locale.US,
                        "Nearest button state expected:%s but was:%s",
                        expectedState ? "on" : "off",
                        nearestButton.isChecked() ? "on" : "off"));
        } catch (AssertionError ae) {
            errors.add(ae.getMessage());
        }
        try {
            RadioButton previousButton = assertViewVisibility(
                    scenario, widget, "Previous button",
                    R.id.RepeatRadioButtonPrevious, RadioButton.class, true);
            String expectedLabel = testContext.getString(
                    R.string.RepeatOptionPrevious);
            String actualLabel = previousButton.getText().toString();
            if (!expectedLabel.equals(actualLabel))
                errors.add(String.format(Locale.US,
                        "Previous button label expected:%s but was:%s",
                        expectedLabel, actualLabel));
            boolean expectedState = (expectedDirection ==
                    WeekdayDirection.CLOSEST_OR_PREVIOUS) ||
                    (expectedDirection == WeekdayDirection.PREVIOUS);
            if (previousButton.isChecked() != expectedState)
                errors.add(String.format(Locale.US,
                        "Previous button state expected:%s but was:%s",
                        expectedState ? "on" : "off",
                        previousButton.isChecked() ? "on" : "off"));
        } catch (AssertionError ae) {
            errors.add(ae.getMessage());
        }
        try {
            RadioButton nextButton = assertViewVisibility(
                    scenario, widget, "Next button",
                    R.id.RepeatRadioButtonNext, RadioButton.class, true);
            String expectedLabel = testContext.getString(
                    R.string.RepeatOptionNext);
            String actualLabel = nextButton.getText().toString();
            if (!expectedLabel.equals(actualLabel))
                errors.add(String.format(Locale.US,
                        "Next button label expected:%s but was:%s",
                        expectedLabel, actualLabel));
            boolean expectedState = (expectedDirection ==
                    WeekdayDirection.CLOSEST_OR_NEXT) || (expectedDirection ==
                    WeekdayDirection.NEXT);
            if (nextButton.isChecked() != expectedState)
                errors.add(String.format(Locale.US,
                        "Next button state expected:%s but was:%s",
                        expectedState ? "on" : "off",
                        nextButton.isChecked() ? "on" : "off"));
        } catch (AssertionError ae) {
            errors.add(ae.getMessage());
        }
        if (!errors.isEmpty()) {
            if (errors.size() == 1)
                fail(errors.get(0));
            else
                fail("Multiple errors:\n" + String.join("\n", errors));
        }
    }

    /** Enum used for the {@link #assertDayDateButtons} method */
    private enum DayDate {
        DAY,
        DATE
    }

    /**
     * Verify which of the &ldquo;Repeat By&ldquo; radio buttons is
     * selected.
     *
     * @param scenario the scenario in which the test is running
     * @param widget the repeat editor widget
     * @param expectedDayte which of the &ldquo;Day&rdquo; or
     * &ldquo;Date&rdquo; button should be selected, or {@code null}
     * if the entire row should be hidden.
     *
     * @throws AssertionError if either of these buttons are missing,
     * not visible, or in the wrong state
     */
    private <T extends Activity> void assertDayDateButtons(
            @NonNull ActivityScenario<T> scenario,
            @NonNull RepeatEditor widget,
            @Nullable DayDate expectedDayte) {
        assertViewVisibility(scenario, widget, "Day/Date row",
                R.id.RepeatRowDayDate, ViewGroup.class,
                expectedDayte != null);
        if (expectedDayte == null)
            return;
        List<String> errors = new ArrayList<>();
        try {
            RadioButton dayButton = assertViewVisibility(
                    scenario, widget, "Day button",
                    R.id.RepeatRadioButtonByDay, RadioButton.class, true);
            String expectedLabel = testContext.getString(
                    R.string.RepeatOptionByDay);
            String actualLabel = dayButton.getText().toString();
            if (!expectedLabel.equals(actualLabel))
                errors.add(String.format(Locale.US,
                        "Day button label expected:%s but was:%s",
                        expectedLabel, actualLabel));
            boolean expectedState = (expectedDayte == DayDate.DAY);
            if (dayButton.isChecked() != expectedState)
                errors.add(String.format(Locale.US,
                        "Day button state expected:%s but was:%s",
                        expectedState ? "on" : "off",
                        dayButton.isChecked() ? "on" : "off"));
        } catch (AssertionError ae) {
            errors.add(ae.getMessage());
        }
        try {
            RadioButton dateButton = assertViewVisibility(
                    scenario, widget, "Date button",
                    R.id.RepeatRadioButtonByDate, RadioButton.class, true);
            String expectedLabel = testContext.getString(
                    R.string.RepeatOptionByDate);
            String actualLabel = dateButton.getText().toString();
            if (!expectedLabel.equals(actualLabel))
                errors.add(String.format(Locale.US,
                        "Date button label expected:%s but was:%s",
                        expectedLabel, actualLabel));
            boolean expectedState = (expectedDayte == DayDate.DATE);
            if (dateButton.isChecked() != expectedState)
                errors.add(String.format(Locale.US,
                        "Date button state expected:%s but was:%s",
                        expectedState ? "on" : "off",
                        dateButton.isChecked() ? "on" : "off"));
        } catch (AssertionError ae) {
            errors.add(ae.getMessage());
        }
        if (!errors.isEmpty()) {
            if (errors.size() == 1)
                fail(errors.get(0));
            else
                fail("Multiple errors:\n" + String.join("\n", errors));
        }
    }

    private <T extends Activity> void runRepeatNoneChecks(
            ActivityScenario<T> scenario, RepeatEditor widget) {
        assertRepeatTypeButtons(scenario, widget, R.id.RepeatRadioButtonNone);
        assertViewVisibility(scenario, widget,
                "Repeat type-specific settings",
                R.id.RepeatLayout, ViewGroup.class, false);
        assertViewVisibility(scenario, widget, "Instructional text",
                R.id.RepeatTextNone, TextView.class, true);
    }

    /**
     * When opening the editor for {@link RepeatNone}, verify that
     * the &ldquo;None&rdquo; tab is selected and no other repeat
     * options are visible.
     */
    @Test
    public void testOpenRepeatWidgetNone() {
        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(ToDoNoteActivity.class)) {
            RepeatEditor widget = showRepeatEditorWidget(
                    wrapper.getScenario(), new RepeatNone(),
                    LocalDate.now(ZoneOffset.UTC), ZoneOffset.UTC);
            runRepeatNoneChecks(wrapper.getScenario(), widget);
            assertEquals("Repeat description",
                    testContext.getString(R.string.RepeatDescriptionNone),
                    getElementText(wrapper.getScenario(),
                            "Repeat description", R.id.RepeatTextDescription));
        }
    }

    /**
     * When opening the dialog for {@link RepeatNone}, verify that
     * the &ldquo;None&rdquo; tab is selected and no other repeat
     * options are visible.
     */
    @Test
    public void testOpenRepeatDialogNone() {
        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(ToDoNoteActivity.class)) {
            RepeatEditorDialog dialog = showRepeatEditorDialog(
                    wrapper.getScenario(), new RepeatNone(),
                    LocalDate.now(ZoneOffset.UTC), ZoneOffset.UTC);
            assertDialogShown(wrapper.getScenario(),
                    testContext.getString(R.string.RepeatTitle));
            runRepeatNoneChecks(wrapper.getScenario(), dialog.repeatEditor);
            assertEquals("Repeat description",
                    testContext.getString(R.string.RepeatDescriptionNone),
                    getElementText(wrapper.getScenario(), dialog,
                            "Repeat description", R.id.RepeatTextDescription));
        }
    }

    /**
     * Generate a set of random days of the week for use in tests.
     * The set will contain at least one element.
     */
    private Set<WeekDays> randomDays() {
        Set<WeekDays> weekDays = new HashSet<>();
        for (int i = RAND.nextInt(7); i >= 0; --i) {
            weekDays.add(WeekDays.values()[RAND.nextInt(7)]);
        }
        return weekDays;
    }

    /**
     * Give the provided adjustable repeat interval a randomized set
     * of allowed days of the week and a random direction.
     */
    private void setRandomAdjustment(AbstractAdjustableRepeat repeat) {
        repeat.setAllowedWeekDays(randomDays());
        repeat.setDirection(WeekdayDirection.values()[RAND.nextInt(
                WeekdayDirection.values().length)]);
    }

    private <T extends Activity> void runRepeatDailyChecks(
            ActivityScenario<T> scenario, RepeatEditor widget,
            RepeatDaily repeat) {
        assertRepeatTypeButtons(scenario, widget, R.id.RepeatRadioButtonDaily);
        assertResetTypeButtons(scenario, widget,
                R.id.RepeatRadioButtonFixedSchedule);
        assertIntervalText(scenario, widget, repeat.getIncrement(),
                testContext.getString(R.string.RepeatTextDays));
        assertEndDateButton(scenario, widget, repeat.getEnd());
        assertWeekDayToggles(scenario, widget, repeat.getAllowedWeekDays());
        assertDirectionButtons(scenario, widget, repeat.getDirection());
        assertDayDateButtons(scenario, widget, null);
        assertViewVisibility(scenario, widget, "Instructional text",
                R.id.RepeatTextNone, TextView.class, false);
    }

    /**
     * When opening the editor for {@link RepeatDaily}, verify that
     * the &ldquo;Day&rdquo; tab is selected, the &ldquo;Fixed
     * Schedule&rdquo; option is selected, and the rest of the
     * fields match the daily repeat settings.
     */
    @Test
    public void testOpenRepeatWidgetDaily() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatDaily repeat = new RepeatDaily();
        repeat.setIncrement(RAND.nextInt(100) + 1);
        repeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusDays(RAND.nextInt(384)));
        setRandomAdjustment(repeat);
        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(ToDoNoteActivity.class)) {
            RepeatEditor widget = showRepeatEditorWidget(
                    wrapper.getScenario(), repeat, due, ZoneOffset.UTC);
            runRepeatDailyChecks(wrapper.getScenario(), widget, repeat);
            // To Do (maybe): verify the repeat description.
            // This could get complicated though.
        }
    }

    /**
     * When opening the dialog for {@link RepeatDaily}, verify that
     * the &ldquo;Day&rdquo; tab is selected, the &ldquo;Fixed
     * Schedule&rdquo; option is selected, and the rest of the
     * fields match the daily repeat settings.
     */
    @Test
    public void testOpenRepeatDialogDaily() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatDaily repeat = new RepeatDaily();
        repeat.setIncrement(RAND.nextInt(100) + 1);
        repeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusDays(RAND.nextInt(384)));
        setRandomAdjustment(repeat);
        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(ToDoNoteActivity.class)) {
            RepeatEditorDialog dialog = showRepeatEditorDialog(
                    wrapper.getScenario(), repeat, due, ZoneOffset.UTC);
            assertDialogShown(wrapper.getScenario(),
                    testContext.getString(R.string.RepeatTitle));
            runRepeatDailyChecks(wrapper.getScenario(),
                    dialog.repeatEditor, repeat);
            // To Do (maybe): verify the repeat description.
            // This could get complicated though.
        }
    }

    private <T extends Activity> void runRepeatDayAfterChecks(
            ActivityScenario<T> scenario, RepeatEditor widget,
            RepeatDayAfter repeat) {
        assertRepeatTypeButtons(scenario, widget, R.id.RepeatRadioButtonDaily);
        assertResetTypeButtons(scenario, widget,
                R.id.RepeatRadioButtonAfterCompleted);
        assertIntervalText(scenario, widget, repeat.getIncrement(),
                testContext.getString(R.string.RepeatTextDays));
        assertEndDateButton(scenario, widget, repeat.getEnd());
        assertWeekDayToggles(scenario, widget, repeat.getAllowedWeekDays());
        assertDirectionButtons(scenario, widget, repeat.getDirection());
        assertDayDateButtons(scenario, widget, null);
        assertViewVisibility(scenario, widget, "Instructional text",
                R.id.RepeatTextNone, TextView.class, false);
    }

    /**
     * When opening the editor for {@link RepeatDayAfter}, verify that
     * the &ldquo;Day&rdquo; tab is selected, the &ldquo;After
     * Completed&rdquo; option is selected, and the rest of the
     * fields match the daily repeat settings.
     */
    @Test
    public void testOpenRepeatWidgetDayAfter() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatDayAfter repeat = new RepeatDayAfter();
        repeat.setIncrement(RAND.nextInt(100) + 1);
        repeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusDays(RAND.nextInt(384)));
        setRandomAdjustment(repeat);
        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(ToDoNoteActivity.class)) {
            RepeatEditor widget = showRepeatEditorWidget(
                    wrapper.getScenario(), repeat, due, ZoneOffset.UTC);
            runRepeatDayAfterChecks(wrapper.getScenario(), widget, repeat);
        }
    }

    /**
     * When opening the dialog for {@link RepeatDayAfter}, verify that
     * the &ldquo;Day&rdquo; tab is selected, the &ldquo;After
     * Completed&rdquo; option is selected, and the rest of the
     * fields match the daily repeat settings.
     */
    @Test
    public void testOpenRepeatDialogDayAfter() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatDayAfter repeat = new RepeatDayAfter();
        repeat.setIncrement(RAND.nextInt(100) + 1);
        repeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusDays(RAND.nextInt(384)));
        setRandomAdjustment(repeat);
        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(ToDoNoteActivity.class)) {
            RepeatEditorDialog dialog = showRepeatEditorDialog(
                    wrapper.getScenario(), repeat, due, ZoneOffset.UTC);
            assertDialogShown(wrapper.getScenario(),
                    testContext.getString(R.string.RepeatTitle));
            runRepeatDayAfterChecks(wrapper.getScenario(),
                    dialog.repeatEditor, repeat);
        }
    }

    private <T extends Activity> void runRepeatWeeklyChecks(
            ActivityScenario<T> scenario, RepeatEditor widget,
            RepeatWeekly repeat) {
        assertRepeatTypeButtons(scenario, widget, R.id.RepeatRadioButtonWeekly);
        assertResetTypeButtons(scenario, widget,
                R.id.RepeatRadioButtonFixedSchedule);
        assertIntervalText(scenario, widget, repeat.getIncrement(),
                testContext.getString(R.string.RepeatTextWeeks));
        assertEndDateButton(scenario, widget, repeat.getEnd());
        assertWeekDayToggles(scenario, widget, repeat.getWeekDays());
        assertDirectionButtons(scenario, widget, null);
        assertDayDateButtons(scenario, widget, null);
        assertViewVisibility(scenario, widget, "Instructional text",
                R.id.RepeatTextNone, TextView.class, false);
    }

    /**
     * When opening the editor for {@link RepeatWeekly}, verify that
     * the &ldquo;Week&rdquo; tab is selected, the &ldquo;Fixed
     * Schedule&rdquo; option is selected, and the rest of the
     * fields match the weekly repeat settings.
     */
    @Test
    public void testOpenRepeatWidgetWeekly() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatWeekly repeat = new RepeatWeekly();
        repeat.setIncrement(RAND.nextInt(52) + 1);
        repeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusWeeks(RAND.nextInt(64)));
        repeat.setWeekDays(randomDays());
        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(ToDoNoteActivity.class)) {
            RepeatEditor widget = showRepeatEditorWidget(
                    wrapper.getScenario(), repeat, due, ZoneOffset.UTC);
            runRepeatWeeklyChecks(wrapper.getScenario(), widget, repeat);
        }
    }

    /**
     * When opening the dialog for {@link RepeatWeekly}, verify that
     * the &ldquo;Week&rdquo; tab is selected, the &ldquo;Fixed
     * Schedule&rdquo; option is selected, and the rest of the
     * fields match the weekly repeat settings.
     */
    @Test
    public void testOpenRepeatDialogWeekly() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatWeekly repeat = new RepeatWeekly();
        repeat.setIncrement(RAND.nextInt(52) + 1);
        repeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusWeeks(RAND.nextInt(64)));
        repeat.setWeekDays(randomDays());
        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(ToDoNoteActivity.class)) {
            RepeatEditorDialog dialog = showRepeatEditorDialog(
                    wrapper.getScenario(), repeat, due, ZoneOffset.UTC);
            assertDialogShown(wrapper.getScenario(),
                    testContext.getString(R.string.RepeatTitle));
            runRepeatWeeklyChecks(wrapper.getScenario(),
                    dialog.repeatEditor, repeat);
        }
    }

    private <T extends Activity> void runRepeatWeekAfterChecks(
            ActivityScenario<T> scenario, RepeatEditor widget,
            RepeatWeekAfter repeat) {
        assertRepeatTypeButtons(scenario, widget, R.id.RepeatRadioButtonWeekly);
        assertResetTypeButtons(scenario, widget,
                R.id.RepeatRadioButtonAfterCompleted);
        assertIntervalText(scenario, widget, repeat.getIncrement(),
                testContext.getString(R.string.RepeatTextWeeks));
        assertEndDateButton(scenario, widget, repeat.getEnd());
        assertWeekDayToggles(scenario, widget, repeat.getAllowedWeekDays());
        assertDirectionButtons(scenario, widget, repeat.getDirection());
        assertDayDateButtons(scenario, widget, null);
        assertViewVisibility(scenario, widget, "Instructional text",
                R.id.RepeatTextNone, TextView.class, false);
    }

    /**
     * When opening the editor for {@link RepeatWeekAfter}, verify that
     * the &ldquo;Week&rdquo; tab is selected, the &ldquo;After
     * Completed&rdquo; option is selected, and the rest of the
     * fields match the weekly repeat settings.
     */
    @Test
    public void testOpenRepeatWidgetWeekAfter() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatWeekAfter repeat = new RepeatWeekAfter();
        repeat.setIncrement(RAND.nextInt(52) + 1);
        repeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusWeeks(RAND.nextInt(64)));
        setRandomAdjustment(repeat);
        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(ToDoNoteActivity.class)) {
            RepeatEditor widget = showRepeatEditorWidget(
                    wrapper.getScenario(), repeat, due, ZoneOffset.UTC);
            runRepeatWeekAfterChecks(wrapper.getScenario(), widget, repeat);
        }
    }

    /**
     * When opening the dialog for {@link RepeatWeekAfter}, verify that
     * the &ldquo;Week&rdquo; tab is selected, the &ldquo;After
     * Completed&rdquo; option is selected, and the rest of the
     * fields match the weekly repeat settings.
     */
    @Test
    public void testOpenRepeatDialogWeekAfter() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatWeekAfter repeat = new RepeatWeekAfter();
        repeat.setIncrement(RAND.nextInt(52) + 1);
        repeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusWeeks(RAND.nextInt(64)));
        setRandomAdjustment(repeat);
        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(ToDoNoteActivity.class)) {
            RepeatEditorDialog dialog = showRepeatEditorDialog(
                    wrapper.getScenario(), repeat, due, ZoneOffset.UTC);
            assertDialogShown(wrapper.getScenario(),
                    testContext.getString(R.string.RepeatTitle));
            runRepeatWeekAfterChecks(wrapper.getScenario(),
                    dialog.repeatEditor, repeat);
        }
    }

    private <T extends Activity> void runRepeatSemiMonthlyOnDaysChecks(
            ActivityScenario<T> scenario, RepeatEditor widget,
            RepeatSemiMonthlyOnDays repeat) {
        assertRepeatTypeButtons(scenario, widget,
                R.id.RepeatRadioButtonSemiMonthly);
        assertResetTypeButtons(scenario, widget, -1);
        assertIntervalText(scenario, widget, repeat.getIncrement(),
                testContext.getString(R.string.RepeatTextMonths));
        assertEndDateButton(scenario, widget, repeat.getEnd());
        assertWeekDayToggles(scenario, widget, null);
        assertDirectionButtons(scenario, widget, null);
        assertDayDateButtons(scenario, widget, DayDate.DAY);
        assertViewVisibility(scenario, widget, "Instructional text",
                R.id.RepeatTextNone, TextView.class, false);
    }

    /**
     * When opening the editor for {@link RepeatSemiMonthlyOnDays},
     * verify that the &ldquo;Semi&rdquo; tab is selected, the &ldquo;Fixed
     * Schedule&rdquo; / &ldquo;After Completed&rdquo; group is hidden,
     * the Repeat by &ldquo;Day&rdquo; option is selected, and the rest
     * of the fields match the semi-monthly repeat settings.
     */
    @Test
    public void testOpenRepeatWidgetSemiMonthlyOnDays() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatSemiMonthlyOnDays repeat = new RepeatSemiMonthlyOnDays(due);
        repeat.setIncrement(RAND.nextInt(12) + 1);
        repeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusMonths(RAND.nextInt(16)));
        repeat.setDay(WeekDays.values()[RAND.nextInt(WeekDays.values().length)]);
        repeat.setWeek(RAND.nextInt(3) + 1);
        repeat.setDay2(WeekDays.values()[RAND.nextInt(WeekDays.values().length)]);
        repeat.setWeek2(RAND.nextBoolean() ? 4 : -1);
        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(ToDoNoteActivity.class)) {
            RepeatEditor widget = showRepeatEditorWidget(
                    wrapper.getScenario(), repeat, due, ZoneOffset.UTC);
            runRepeatSemiMonthlyOnDaysChecks(wrapper.getScenario(), widget, repeat);
        }
    }

    /**
     * When opening the dialog for {@link RepeatSemiMonthlyOnDays},
     * verify that the &ldquo;Semi&rdquo; tab is selected, the &ldquo;Fixed
     * Schedule&rdquo; / &ldquo;After Completed&rdquo; group is hidden,
     * the Repeat by &ldquo;Day&rdquo; option is selected, and the rest
     * of the fields match the semi-monthly repeat settings.
     */
    @Test
    public void testOpenRepeatDialogSemiMonthlyOnDays() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatSemiMonthlyOnDays repeat = new RepeatSemiMonthlyOnDays(due);
        repeat.setIncrement(RAND.nextInt(12) + 1);
        repeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusMonths(RAND.nextInt(16)));
        repeat.setDay(WeekDays.values()[RAND.nextInt(WeekDays.values().length)]);
        repeat.setWeek(RAND.nextInt(3) + 1);
        repeat.setDay2(WeekDays.values()[RAND.nextInt(WeekDays.values().length)]);
        repeat.setWeek2(RAND.nextBoolean() ? 4 : -1);
        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(ToDoNoteActivity.class)) {
            RepeatEditorDialog dialog = showRepeatEditorDialog(
                    wrapper.getScenario(), repeat, due, ZoneOffset.UTC);
            assertDialogShown(wrapper.getScenario(),
                    testContext.getString(R.string.RepeatTitle));
            runRepeatSemiMonthlyOnDaysChecks(wrapper.getScenario(),
                    dialog.repeatEditor, repeat);
        }
    }

    private <T extends Activity> void runRepeatSemiMonthlyOnDatesChecks(
            ActivityScenario<T> scenario, RepeatEditor widget,
            RepeatSemiMonthlyOnDates repeat) {
        assertRepeatTypeButtons(scenario, widget,
                R.id.RepeatRadioButtonSemiMonthly);
        assertResetTypeButtons(scenario, widget, -1);
        assertIntervalText(scenario, widget, repeat.getIncrement(),
                testContext.getString(R.string.RepeatTextMonths));
        assertEndDateButton(scenario, widget, repeat.getEnd());
        assertWeekDayToggles(scenario, widget, repeat.getAllowedWeekDays());
        assertDirectionButtons(scenario, widget, repeat.getDirection());
        assertDayDateButtons(scenario, widget, DayDate.DATE);
        assertViewVisibility(scenario, widget, "Instructional text",
                R.id.RepeatTextNone, TextView.class, false);
    }

    /**
     * When opening the editor for {@link RepeatSemiMonthlyOnDates},
     * verify that the &ldquo;Semi&rdquo; tab is selected, the &ldquo;Fixed
     * Schedule&rdquo; / &ldquo;After Completed&rdquo; group is hidden,
     * the Repeat by &ldquo;Date&rdquo; option is selected, and the rest
     * of the fields match the semi-monthly repeat settings.
     */
    @Test
    public void testOpenRepeatWidgetSemiMonthlyOnDates() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatSemiMonthlyOnDates repeat = new RepeatSemiMonthlyOnDates(due);
        repeat.setIncrement(RAND.nextInt(12) + 1);
        repeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusMonths(RAND.nextInt(16)));
        repeat.setDate(RAND.nextInt(15) + 1);
        repeat.setDate2(RAND.nextInt(16) + 16);
        setRandomAdjustment(repeat);
        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(ToDoNoteActivity.class)) {
            RepeatEditor widget = showRepeatEditorWidget(
                    wrapper.getScenario(), repeat, due, ZoneOffset.UTC);
            runRepeatSemiMonthlyOnDatesChecks(wrapper.getScenario(), widget, repeat);
        }
    }

    /**
     * When opening the dialog for {@link RepeatSemiMonthlyOnDates},
     * verify that the &ldquo;Semi&rdquo; tab is selected, the &ldquo;Fixed
     * Schedule&rdquo; / &ldquo;After Completed&rdquo; group is hidden,
     * the Repeat by &ldquo;Date&rdquo; option is selected, and the rest
     * of the fields match the semi-monthly repeat settings.
     */
    @Test
    public void testOpenRepeatDialogSemiMonthlyOnDates() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatSemiMonthlyOnDates repeat = new RepeatSemiMonthlyOnDates(due);
        repeat.setIncrement(RAND.nextInt(12) + 1);
        repeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusMonths(RAND.nextInt(16)));
        repeat.setDate(RAND.nextInt(15) + 1);
        repeat.setDate2(RAND.nextInt(16) + 16);
        setRandomAdjustment(repeat);
        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(ToDoNoteActivity.class)) {
            RepeatEditorDialog dialog = showRepeatEditorDialog(
                    wrapper.getScenario(), repeat, due, ZoneOffset.UTC);
            assertDialogShown(wrapper.getScenario(),
                    testContext.getString(R.string.RepeatTitle));
            runRepeatSemiMonthlyOnDatesChecks(wrapper.getScenario(),
                    dialog.repeatEditor, repeat);
        }
    }

    private <T extends Activity> void runRepeatMonthlyOnDayChecks(
            ActivityScenario<T> scenario, RepeatEditor widget,
            RepeatMonthlyOnDay repeat) {
        assertRepeatTypeButtons(scenario, widget,
                R.id.RepeatRadioButtonMonthly);
        assertResetTypeButtons(scenario, widget,
                R.id.RepeatRadioButtonFixedSchedule);
        assertIntervalText(scenario, widget, repeat.getIncrement(),
                testContext.getString(R.string.RepeatTextMonths));
        assertEndDateButton(scenario, widget, repeat.getEnd());
        assertWeekDayToggles(scenario, widget, null);
        assertDirectionButtons(scenario, widget, null);
        assertDayDateButtons(scenario, widget, DayDate.DAY);
        assertViewVisibility(scenario, widget, "Instructional text",
                R.id.RepeatTextNone, TextView.class, false);
    }

    /**
     * When opening the editor for {@link RepeatMonthlyOnDay},
     * verify that the &ldquo;Month&rdquo; tab is selected, the &ldquo;Fixed
     * Schedule&rdquo; option is selected, the Repeat by &ldquo;Day&rdquo;
     * option is selected, and the rest of the fields match the monthly
     * repeat settings.
     */
    @Test
    public void testOpenRepeatWidgetMonthlyOnDay() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatMonthlyOnDay repeat = new RepeatMonthlyOnDay(due);
        repeat.setIncrement(RAND.nextInt(12) + 1);
        repeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusMonths(RAND.nextInt(16)));
        int[] weekNums = {1, 2, 3, 4, -1};
        repeat.setDay(WeekDays.values()[RAND.nextInt(WeekDays.values().length)]);
        repeat.setWeek(weekNums[RAND.nextInt(weekNums.length)]);
        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(ToDoNoteActivity.class)) {
            RepeatEditor widget = showRepeatEditorWidget(
                    wrapper.getScenario(), repeat, due, ZoneOffset.UTC);
            runRepeatMonthlyOnDayChecks(wrapper.getScenario(), widget, repeat);
        }
    }

    /**
     * When opening the dialog for {@link RepeatMonthlyOnDay},
     * verify that the &ldquo;Month&rdquo; tab is selected, the &ldquo;Fixed
     * Schedule&rdquo; option is selected, the Repeat by &ldquo;Day&rdquo;
     * option is selected, and the rest of the fields match the monthly
     * repeat settings.
     */
    @Test
    public void testOpenRepeatDialogMonthlyOnDay() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatMonthlyOnDay repeat = new RepeatMonthlyOnDay(due);
        repeat.setIncrement(RAND.nextInt(12) + 1);
        repeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusMonths(RAND.nextInt(16)));
        int[] weekNums = {1, 2, 3, 4, -1};
        repeat.setDay(WeekDays.values()[RAND.nextInt(WeekDays.values().length)]);
        repeat.setWeek(weekNums[RAND.nextInt(weekNums.length)]);
        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(ToDoNoteActivity.class)) {
            RepeatEditorDialog dialog = showRepeatEditorDialog(
                    wrapper.getScenario(), repeat, due, ZoneOffset.UTC);
            assertDialogShown(wrapper.getScenario(),
                    testContext.getString(R.string.RepeatTitle));
            runRepeatMonthlyOnDayChecks(wrapper.getScenario(),
                    dialog.repeatEditor, repeat);
        }
    }

    private <T extends Activity> void runRepeatMonthlyOnDateChecks(
            ActivityScenario<T> scenario, RepeatEditor widget,
            RepeatMonthlyOnDate repeat) {
        assertRepeatTypeButtons(scenario, widget,
                R.id.RepeatRadioButtonMonthly);
        assertResetTypeButtons(scenario, widget,
                R.id.RepeatRadioButtonFixedSchedule);
        assertIntervalText(scenario, widget, repeat.getIncrement(),
                testContext.getString(R.string.RepeatTextMonths));
        assertEndDateButton(scenario, widget, repeat.getEnd());
        assertWeekDayToggles(scenario, widget, repeat.getAllowedWeekDays());
        assertDirectionButtons(scenario, widget, repeat.getDirection());
        assertDayDateButtons(scenario, widget, DayDate.DATE);
        assertViewVisibility(scenario, widget, "Instructional text",
                R.id.RepeatTextNone, TextView.class, false);
    }

    /**
     * When opening the editor for {@link RepeatMonthlyOnDate},
     * verify that the &ldquo;Month&rdquo; tab is selected, the &ldquo;Fixed
     * Schedule&rdquo; option is selected, the Repeat by &ldquo;Date&rdquo;
     * option is selected, and the rest of the fields match the monthly
     * repeat settings.
     */
    @Test
    public void testOpenRepeatWidgetMonthlyOnDate() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatMonthlyOnDate repeat = new RepeatMonthlyOnDate(due);
        repeat.setIncrement(RAND.nextInt(12) + 1);
        repeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusMonths(RAND.nextInt(16)));
        repeat.setDate(RAND.nextInt(31) + 1);
        setRandomAdjustment(repeat);
        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(ToDoNoteActivity.class)) {
            RepeatEditor widget = showRepeatEditorWidget(
                    wrapper.getScenario(), repeat, due, ZoneOffset.UTC);
            runRepeatMonthlyOnDateChecks(wrapper.getScenario(), widget, repeat);
        }
    }

    /**
     * When opening the dialog for {@link RepeatMonthlyOnDate},
     * verify that the &ldquo;Month&rdquo; tab is selected, the &ldquo;Fixed
     * Schedule&rdquo; option is selected, the Repeat by &ldquo;Date&rdquo;
     * option is selected, and the rest of the fields match the monthly
     * repeat settings.
     */
    @Test
    public void testOpenRepeatDialogMonthlyOnDate() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatMonthlyOnDate repeat = new RepeatMonthlyOnDate(due);
        repeat.setIncrement(RAND.nextInt(12) + 1);
        repeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusMonths(RAND.nextInt(16)));
        repeat.setDate(RAND.nextInt(31) + 1);
        setRandomAdjustment(repeat);
        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(ToDoNoteActivity.class)) {
            RepeatEditorDialog dialog = showRepeatEditorDialog(
                    wrapper.getScenario(), repeat, due, ZoneOffset.UTC);
            assertDialogShown(wrapper.getScenario(),
                    testContext.getString(R.string.RepeatTitle));
            runRepeatMonthlyOnDateChecks(wrapper.getScenario(),
                    dialog.repeatEditor, repeat);
        }
    }

    private <T extends Activity> void runRepeatMonthAfterChecks(
            ActivityScenario<T> scenario, RepeatEditor widget,
            RepeatMonthAfter repeat) {
        assertRepeatTypeButtons(scenario, widget,
                R.id.RepeatRadioButtonMonthly);
        assertResetTypeButtons(scenario, widget,
                R.id.RepeatRadioButtonAfterCompleted);
        assertIntervalText(scenario, widget, repeat.getIncrement(),
                testContext.getString(R.string.RepeatTextMonths));
        assertEndDateButton(scenario, widget, repeat.getEnd());
        assertWeekDayToggles(scenario, widget, repeat.getAllowedWeekDays());
        assertDirectionButtons(scenario, widget, repeat.getDirection());
        assertDayDateButtons(scenario, widget, null);
        assertViewVisibility(scenario, widget, "Instructional text",
                R.id.RepeatTextNone, TextView.class, false);
    }

    /**
     * When opening the editor for {@link RepeatMonthAfter},
     * verify that the &ldquo;Month&rdquo; tab is selected, the &ldquo;After
     * Completed&rdquo; option is selected, the Repeat by group is not
     * shown, and the rest of the fields match the monthly repeat settings.
     */
    @Test
    public void testOpenRepeatWidgetMonthAfter() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatMonthAfter repeat = new RepeatMonthAfter();
        repeat.setIncrement(RAND.nextInt(12) + 1);
        repeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusMonths(RAND.nextInt(16)));
        setRandomAdjustment(repeat);
        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(ToDoNoteActivity.class)) {
            RepeatEditor widget = showRepeatEditorWidget(
                    wrapper.getScenario(), repeat, due, ZoneOffset.UTC);
            runRepeatMonthAfterChecks(wrapper.getScenario(), widget, repeat);
        }
    }

    /**
     * When opening the dialog for {@link RepeatMonthAfter},
     * verify that the &ldquo;Month&rdquo; tab is selected, the &ldquo;After
     * Completed&rdquo; option is selected, the Repeat by group is not
     * shown, and the rest of the fields match the monthly repeat settings.
     */
    @Test
    public void testOpenRepeatDialogMonthAfter() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatMonthAfter repeat = new RepeatMonthAfter();
        repeat.setIncrement(RAND.nextInt(12) + 1);
        repeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusMonths(RAND.nextInt(16)));
        setRandomAdjustment(repeat);
        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(ToDoNoteActivity.class)) {
            RepeatEditorDialog dialog = showRepeatEditorDialog(
                    wrapper.getScenario(), repeat, due, ZoneOffset.UTC);
            assertDialogShown(wrapper.getScenario(),
                    testContext.getString(R.string.RepeatTitle));
            runRepeatMonthAfterChecks(wrapper.getScenario(),
                    dialog.repeatEditor, repeat);
        }
    }

    private <T extends Activity> void runRepeatYearlyOnDayChecks(
            ActivityScenario<T> scenario, RepeatEditor widget,
            RepeatYearlyOnDay repeat) {
        assertRepeatTypeButtons(scenario, widget,
                R.id.RepeatRadioButtonYearly);
        assertResetTypeButtons(scenario, widget,
                R.id.RepeatRadioButtonFixedSchedule);
        assertIntervalText(scenario, widget, repeat.getIncrement(),
                testContext.getString(R.string.RepeatTextYears));
        assertEndDateButton(scenario, widget, repeat.getEnd());
        assertWeekDayToggles(scenario, widget, null);
        assertDirectionButtons(scenario, widget, null);
        assertDayDateButtons(scenario, widget, DayDate.DAY);
        assertViewVisibility(scenario, widget, "Instructional text",
                R.id.RepeatTextNone, TextView.class, false);
    }

    /**
     * When opening the editor for {@link RepeatYearlyOnDay},
     * verify that the &ldquo;Year&rdquo; tab is selected, the &ldquo;Fixed
     * Schedule&rdquo; option is selected, the Repeat by &ldquo;Day&rdquo;
     * option is selected, and the rest of the fields match the yearly
     * repeat settings.
     */
    @Test
    public void testOpenRepeatWidgetYearlyOnDay() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatYearlyOnDay repeat = new RepeatYearlyOnDay(due);
        repeat.setIncrement(RAND.nextInt(10) + 1);
        repeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusYears(RAND.nextInt(4)));
        int[] weekNums = {1, 2, 3, 4, -1};
        repeat.setDay(WeekDays.values()[RAND.nextInt(WeekDays.values().length)]);
        repeat.setWeek(weekNums[RAND.nextInt(weekNums.length)]);
        repeat.setMonth(Months.values()[RAND.nextInt(Months.values().length)]);
        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(ToDoNoteActivity.class)) {
            RepeatEditor widget = showRepeatEditorWidget(
                    wrapper.getScenario(), repeat, due, ZoneOffset.UTC);
            runRepeatYearlyOnDayChecks(wrapper.getScenario(), widget, repeat);
        }
    }

    /**
     * When opening the dialog for {@link RepeatYearlyOnDay},
     * verify that the &ldquo;Year&rdquo; tab is selected, the &ldquo;Fixed
     * Schedule&rdquo; option is selected, the Repeat by &ldquo;Day&rdquo;
     * option is selected, and the rest of the fields match the yearly
     * repeat settings.
     */
    @Test
    public void testOpenRepeatDialogYearlyOnDay() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatYearlyOnDay repeat = new RepeatYearlyOnDay(due);
        repeat.setIncrement(RAND.nextInt(10) + 1);
        repeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusYears(RAND.nextInt(4)));
        int[] weekNums = {1, 2, 3, 4, -1};
        repeat.setDay(WeekDays.values()[RAND.nextInt(WeekDays.values().length)]);
        repeat.setWeek(weekNums[RAND.nextInt(weekNums.length)]);
        repeat.setMonth(Months.values()[RAND.nextInt(Months.values().length)]);
        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(ToDoNoteActivity.class)) {
            RepeatEditorDialog dialog = showRepeatEditorDialog(
                    wrapper.getScenario(), repeat, due, ZoneOffset.UTC);
            assertDialogShown(wrapper.getScenario(),
                    testContext.getString(R.string.RepeatTitle));
            runRepeatYearlyOnDayChecks(wrapper.getScenario(),
                    dialog.repeatEditor, repeat);
        }
    }

    private <T extends Activity> void runRepeatYearlyOnDateChecks(
            ActivityScenario<T> scenario, RepeatEditor widget,
            RepeatYearlyOnDate repeat) {
        assertRepeatTypeButtons(scenario, widget,
                R.id.RepeatRadioButtonYearly);
        assertResetTypeButtons(scenario, widget,
                R.id.RepeatRadioButtonFixedSchedule);
        assertIntervalText(scenario, widget, repeat.getIncrement(),
                testContext.getString(R.string.RepeatTextYears));
        assertEndDateButton(scenario, widget, repeat.getEnd());
        assertWeekDayToggles(scenario, widget, repeat.getAllowedWeekDays());
        assertDirectionButtons(scenario, widget, repeat.getDirection());
        assertDayDateButtons(scenario, widget, DayDate.DATE);
        assertViewVisibility(scenario, widget, "Instructional text",
                R.id.RepeatTextNone, TextView.class, false);
    }

    /**
     * When opening the editor for {@link RepeatYearlyOnDate},
     * verify that the &ldquo;Year&rdquo; tab is selected, the &ldquo;Fixed
     * Schedule&rdquo; option is selected, the Repeat by &ldquo;Date&rdquo;
     * option is selected, and the rest of the fields match the yearly
     * repeat settings.
     */
    @Test
    public void testOpenRepeatWidgetYearlyOnDate() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatYearlyOnDate repeat = new RepeatYearlyOnDate(due);
        repeat.setIncrement(RAND.nextInt(10) + 1);
        repeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusYears(RAND.nextInt(4)));
        repeat.setMonth(Months.values()[RAND.nextInt(Months.values().length)]);
        repeat.setDate(RAND.nextInt(31) + 1);
        setRandomAdjustment(repeat);
        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(ToDoNoteActivity.class)) {
            RepeatEditor widget = showRepeatEditorWidget(
                    wrapper.getScenario(), repeat, due, ZoneOffset.UTC);
            runRepeatYearlyOnDateChecks(wrapper.getScenario(), widget, repeat);
        }
    }

    /**
     * When opening the dialog for {@link RepeatYearlyOnDate},
     * verify that the &ldquo;Year&rdquo; tab is selected, the &ldquo;Fixed
     * Schedule&rdquo; option is selected, the Repeat by &ldquo;Date&rdquo;
     * option is selected, and the rest of the fields match the yearly
     * repeat settings.
     */
    @Test
    public void testOpenRepeatDialogYearlyOnDate() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatYearlyOnDate repeat = new RepeatYearlyOnDate(due);
        repeat.setIncrement(RAND.nextInt(10) + 1);
        repeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusYears(RAND.nextInt(4)));
        repeat.setMonth(Months.values()[RAND.nextInt(Months.values().length)]);
        repeat.setDate(RAND.nextInt(31) + 1);
        setRandomAdjustment(repeat);
        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(ToDoNoteActivity.class)) {
            RepeatEditorDialog dialog = showRepeatEditorDialog(
                    wrapper.getScenario(), repeat, due, ZoneOffset.UTC);
            assertDialogShown(wrapper.getScenario(),
                    testContext.getString(R.string.RepeatTitle));
            runRepeatYearlyOnDateChecks(wrapper.getScenario(),
                    dialog.repeatEditor, repeat);
        }
    }

    private <T extends Activity> void runRepeatYearAfterChecks(
            ActivityScenario<T> scenario, RepeatEditor widget,
            RepeatYearAfter repeat) {
        assertRepeatTypeButtons(scenario, widget,
                R.id.RepeatRadioButtonYearly);
        assertResetTypeButtons(scenario, widget,
                R.id.RepeatRadioButtonAfterCompleted);
        assertIntervalText(scenario, widget, repeat.getIncrement(),
                testContext.getString(R.string.RepeatTextYears));
        assertEndDateButton(scenario, widget, repeat.getEnd());
        assertWeekDayToggles(scenario, widget, repeat.getAllowedWeekDays());
        assertDirectionButtons(scenario, widget, repeat.getDirection());
        assertDayDateButtons(scenario, widget, null);
        assertViewVisibility(scenario, widget, "Instructional text",
                R.id.RepeatTextNone, TextView.class, false);
    }

    /**
     * When opening the editor for {@link RepeatYearAfter},
     * verify that the &ldquo;Year&rdquo; tab is selected, the &ldquo;After
     * Completed&rdquo; option is selected, the Repeat by group is not
     * shown, and the rest of the fields match the yearly repeat settings.
     */
    @Test
    public void testOpenRepeatWidgetYearAfter() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatYearAfter repeat = new RepeatYearAfter();
        repeat.setIncrement(RAND.nextInt(10) + 1);
        repeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusYears(RAND.nextInt(4)));
        setRandomAdjustment(repeat);
        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(ToDoNoteActivity.class)) {
            RepeatEditor widget = showRepeatEditorWidget(
                    wrapper.getScenario(), repeat, due, ZoneOffset.UTC);
            runRepeatYearAfterChecks(wrapper.getScenario(), widget, repeat);
        }
    }

    /**
     * When opening the dialog for {@link RepeatYearAfter},
     * verify that the &ldquo;Year&rdquo; tab is selected, the &ldquo;After
     * Completed&rdquo; option is selected, the Repeat by group is not
     * shown, and the rest of the fields match the yearly repeat settings.
     */
    @Test
    public void testOpenRepeatDialogYearAfter() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatYearAfter repeat = new RepeatYearAfter();
        repeat.setIncrement(RAND.nextInt(10) + 1);
        repeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusYears(RAND.nextInt(4)));
        setRandomAdjustment(repeat);
        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(ToDoNoteActivity.class)) {
            RepeatEditorDialog dialog = showRepeatEditorDialog(
                    wrapper.getScenario(), repeat, due, ZoneOffset.UTC);
            assertDialogShown(wrapper.getScenario(),
                    testContext.getString(R.string.RepeatTitle));
            runRepeatYearAfterChecks(wrapper.getScenario(),
                    dialog.repeatEditor, repeat);
        }
    }

    /**
     * Common method for settings save tests: create an editor
     * with the given repeat interval, retrieve the settings,
     * create a new editor, restore the saved settings to it,
     * get a repeat interval from it and check that it matches.
     *
     * @param expectedRepeat the repeat settings to initialize and compare
     * @param dueDate the due date on which the repeat is based
     */
    private void runSaveAndRestoreSettingsTest(
            RepeatInterval expectedRepeat, LocalDate dueDate) {
        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                ActivityScenarioResultsWrapper.launch(ToDoNoteActivity.class)) {
            RepeatEditor oldWidget = showRepeatEditorWidget(
                    wrapper.getScenario(), expectedRepeat,
                    dueDate, ZoneOffset.UTC);
            RepeatSettings savedSettings = oldWidget.getSettings();
            RepeatEditor newWidget = showRepeatEditorWidget(
                    wrapper.getScenario(), savedSettings);
            RepeatInterval actualRepeat = newWidget.getSettings().getRepeat();
            // In this particular case, the returned repeat may be null;
            // treat this as equivalant to RepeatNone.
            if (actualRepeat != null)
                assertEquals("Restored repeat", expectedRepeat, actualRepeat);
        }
    }

    /**
     * Test saving and restoring the settings for {@link RepeatNone}
     */
    @Test
    public void testSaveAndRestoreRepeatNone() {
        runSaveAndRestoreSettingsTest(new RepeatNone(),
                LocalDate.now(ZoneOffset.UTC));
    }

    /**
     * Test saving and restoring the settings for {@link RepeatDaily}
     */
    @Test
    public void testSaveAndRestoreRepeatDaily() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatDaily repeat = new RepeatDaily();
        repeat.setIncrement(RAND.nextInt(100) + 1);
        repeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusDays(RAND.nextInt(384)));
        setRandomAdjustment(repeat);
        runSaveAndRestoreSettingsTest(repeat, due);
    }

    /**
     * Test saving and restoring the settings for {@link RepeatDayAfter}
     */
    @Test
    public void testSaveAndRestoreRepeatDayAfter() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatDayAfter repeat = new RepeatDayAfter();
        repeat.setIncrement(RAND.nextInt(100) + 1);
        repeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusDays(RAND.nextInt(384)));
        setRandomAdjustment(repeat);
        runSaveAndRestoreSettingsTest(repeat, due);
    }

    /**
     * Test saving and restoring the settings for {@link RepeatWeekly}
     */
    @Test
    public void testSaveAndRestoreRepeatWeekly() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatWeekly repeat = new RepeatWeekly();
        repeat.setIncrement(RAND.nextInt(52) + 1);
        repeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusWeeks(RAND.nextInt(64)));
        repeat.setWeekDays(randomDays());
        runSaveAndRestoreSettingsTest(repeat, due);
    }

    /**
     * Test saving and restoring the settings for {@link RepeatWeekAfter}
     */
    @Test
    public void testSaveAndRestoreRepeatWeekAfter() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatWeekAfter repeat = new RepeatWeekAfter();
        repeat.setIncrement(RAND.nextInt(52) + 1);
        repeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusWeeks(RAND.nextInt(64)));
        setRandomAdjustment(repeat);
        runSaveAndRestoreSettingsTest(repeat, due);
    }

    /**
     * Test saving and restoring the settings for {@link RepeatSemiMonthlyOnDays}
     */
    @Test
    public void testSaveAndRestoreRepeatSemiMonthlyOnDays() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatSemiMonthlyOnDays repeat = new RepeatSemiMonthlyOnDays(due);
        repeat.setIncrement(RAND.nextInt(12) + 1);
        repeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusMonths(RAND.nextInt(16)));
        repeat.setDay(WeekDays.values()[RAND.nextInt(WeekDays.values().length)]);
        repeat.setWeek(RAND.nextInt(3) + 1);
        repeat.setDay2(WeekDays.values()[RAND.nextInt(WeekDays.values().length)]);
        repeat.setWeek2(RAND.nextBoolean() ? 4 : -1);
        runSaveAndRestoreSettingsTest(repeat, due);
    }

    /**
     * Test saving and restoring the settings for {@link RepeatSemiMonthlyOnDates}
     */
    @Test
    public void testSaveAndRestoreRepeatSemiMonthlyOnDates() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatSemiMonthlyOnDates repeat = new RepeatSemiMonthlyOnDates(due);
        repeat.setIncrement(RAND.nextInt(12) + 1);
        repeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusMonths(RAND.nextInt(16)));
        repeat.setDate(RAND.nextInt(15) + 1);
        repeat.setDate2(RAND.nextInt(16) + 16);
        setRandomAdjustment(repeat);
        runSaveAndRestoreSettingsTest(repeat, due);
    }

    /**
     * Test saving and restoring the settings for {@link RepeatMonthlyOnDay}
     */
    @Test
    public void testSaveAndRestoreRepeatMonthlyOnDay() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatMonthlyOnDay repeat = new RepeatMonthlyOnDay(due);
        repeat.setIncrement(RAND.nextInt(12) + 1);
        repeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusMonths(RAND.nextInt(16)));
        int[] weekNums = {1, 2, 3, 4, -1};
        repeat.setDay(WeekDays.values()[RAND.nextInt(WeekDays.values().length)]);
        repeat.setWeek(weekNums[RAND.nextInt(weekNums.length)]);
        runSaveAndRestoreSettingsTest(repeat, due);
    }

    /**
     * Test saving and restoring the settings for {@link RepeatMonthlyOnDate}
     */
    @Test
    public void testSaveAndRestoreRepeatMonthlyOnDate() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatMonthlyOnDate repeat = new RepeatMonthlyOnDate(due);
        repeat.setIncrement(RAND.nextInt(12) + 1);
        repeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusMonths(RAND.nextInt(16)));
        repeat.setDate(RAND.nextInt(31) + 1);
        setRandomAdjustment(repeat);
        runSaveAndRestoreSettingsTest(repeat, due);
    }

    /**
     * Test saving and restoring the settings for {@link RepeatMonthAfter}
     */
    @Test
    public void testSaveAndRestoreRepeatMonthAfter() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatMonthAfter repeat = new RepeatMonthAfter();
        repeat.setIncrement(RAND.nextInt(12) + 1);
        repeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusMonths(RAND.nextInt(16)));
        setRandomAdjustment(repeat);
        runSaveAndRestoreSettingsTest(repeat, due);
    }

    /**
     * Test saving and restoring the settings for {@link RepeatYearlyOnDay}
     */
    @Test
    public void testSaveAndRestoreRepeatYearlyOnDay() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatYearlyOnDay repeat = new RepeatYearlyOnDay(due);
        repeat.setIncrement(RAND.nextInt(10) + 1);
        repeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusYears(RAND.nextInt(4)));
        int[] weekNums = {1, 2, 3, 4, -1};
        repeat.setDay(WeekDays.values()[RAND.nextInt(WeekDays.values().length)]);
        repeat.setWeek(weekNums[RAND.nextInt(weekNums.length)]);
        repeat.setMonth(Months.values()[RAND.nextInt(Months.values().length)]);
        runSaveAndRestoreSettingsTest(repeat, due);
    }

    /**
     * Test saving and restoring the settings for {@link RepeatYearlyOnDate}
     */
    @Test
    public void testSaveAndRestoreRepeatYearlyOnDate() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatYearlyOnDate repeat = new RepeatYearlyOnDate(due);
        repeat.setIncrement(RAND.nextInt(10) + 1);
        repeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusYears(RAND.nextInt(4)));
        repeat.setMonth(Months.values()[RAND.nextInt(Months.values().length)]);
        repeat.setDate(RAND.nextInt(31) + 1);
        setRandomAdjustment(repeat);
        runSaveAndRestoreSettingsTest(repeat, due);
    }

    /**
     * Test saving and restoring the settings for {@link RepeatYearAfter}
     */
    @Test
    public void testSaveAndRestoreRepeatYearAfter() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatYearAfter repeat = new RepeatYearAfter();
        repeat.setIncrement(RAND.nextInt(10) + 1);
        repeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusYears(RAND.nextInt(4)));
        setRandomAdjustment(repeat);
        runSaveAndRestoreSettingsTest(repeat, due);
    }

    /**
     * Test switching the editor from another repeat interval type
     * to &ldquo;None&rdquo;.
     */
    @Test
    public void testChangeRepeatToNone() {
        // We need an initial non-none repeat interval
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatMonthlyOnDate initialRepeat = new RepeatMonthlyOnDate(due);
        initialRepeat.setIncrement(RAND.nextInt(12) + 1);
        initialRepeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusMonths(RAND.nextInt(16)));
        initialRepeat.setDate(RAND.nextInt(31) + 1);
        setRandomAdjustment(initialRepeat);
        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(ToDoNoteActivity.class)) {
            RepeatEditorDialog dialog = showRepeatEditorDialog(
                    wrapper.getScenario(), initialRepeat, due, ZoneOffset.UTC);
            assertRepeatTypeButtons(wrapper.getScenario(), dialog.repeatEditor,
                    R.id.RepeatRadioButtonMonthly);
            pressDialogButton(wrapper.getScenario(), dialog,
                    R.id.RepeatRadioButtonNone);
            // Make sure the editor layout has changed
            runRepeatNoneChecks(wrapper.getScenario(), dialog.repeatEditor);
            pressDialogButton(wrapper.getScenario(), dialog,
                    android.R.id.button1);
        }
        assertTrue("The dialog did not call the onRepeatSet callback",
                onRepeatSetCalled);
        // For no repeat, the dialog may return null
        // which we'll consider the same as RepeatNone.
        if (setRepeat != null)
            assertEquals("Repeat set by the dialog",
                    new RepeatNone(), setRepeat);
    }

    /**
     * Press the week day and direction buttons until they are
     * configured to match a given state.  Assumes the widget
     * has been set as the activity&rsquo;s content view.
     * Turning off the last weekday button <i>should</i> automatically
     * turn on one of the other buttons since at least one is required,
     * so this will turn on all buttons that should be set before
     * turning any of the others off.
     *
     * @param scenario the scenario in which the test is running
     * @param weekDays the week days that should be turned on
     * @param direction the direction that should be configured,
     * or {@code null} to ignore the direction buttons.
     */
    private <T extends Activity> void setWeekdayButtons(
            @NonNull ActivityScenario<T> scenario,
            @NonNull Set<WeekDays> weekDays,
            @Nullable WeekdayDirection direction) {
        for (WeekDays day : weekDays) {
            int buttonId = DAY_BUTTON_IDS[day.getValue()
                    - WeekDays.SUNDAY.getValue()];
            if (!getToggleButtonState(scenario, day.toString(), buttonId))
                pressButton(scenario, buttonId);
        }
        for (WeekDays offDay : WeekDays.values()) {
            if (weekDays.contains(offDay))
                continue;
            int buttonId = DAY_BUTTON_IDS[offDay.getValue()
                    - WeekDays.SUNDAY.getValue()];
            if (getToggleButtonState(scenario, offDay.toString(), buttonId))
                pressButton(scenario, buttonId);
        }
        if (direction != null)
            setDirectionButtons(scenario, direction);
    }

    private <T extends Activity> void setDirectionButtons(
            @NonNull ActivityScenario<T> scenario,
            @NonNull WeekdayDirection direction) {
        boolean nearest = (direction == WeekdayDirection.CLOSEST_OR_NEXT) ||
                (direction == WeekdayDirection.CLOSEST_OR_PREVIOUS);
        boolean isNext = (direction == WeekdayDirection.CLOSEST_OR_NEXT) ||
                (direction == WeekdayDirection.NEXT);
        if (getToggleButtonState(scenario,
                "Nearest", R.id.RepeatToggleNearest) != nearest)
            pressButton(scenario, R.id.RepeatToggleNearest);
        pressButton(scenario, isNext ? R.id.RepeatRadioButtonNext
                : R.id.RepeatRadioButtonPrevious);
    }

    /**
     * Test switching the editor from another repeat interval type
     * to daily with a fixed schedule.
     */
    @Test
    public void testChangeRepeatToDaily() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatMonthAfter initialRepeat = new RepeatMonthAfter();
        initialRepeat.setIncrement(RAND.nextInt(12) + 1);
        initialRepeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusMonths(RAND.nextInt(16)));
        setRandomAdjustment(initialRepeat);

        RepeatDaily expectedRepeat = new RepeatDaily();
        expectedRepeat.setIncrement(initialRepeat.getIncrement());
        expectedRepeat.setEnd(initialRepeat.getEnd());
        expectedRepeat.setAllowedWeekDays(initialRepeat.getAllowedWeekDays());
        expectedRepeat.setDirection(initialRepeat.getDirection());

        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(ToDoNoteActivity.class)) {
            RepeatEditorDialog dialog = showRepeatEditorDialog(
                    wrapper.getScenario(), initialRepeat, due, ZoneOffset.UTC);
            assertRepeatTypeButtons(wrapper.getScenario(), dialog.repeatEditor,
                    R.id.RepeatRadioButtonMonthly);
            pressDialogButton(wrapper.getScenario(), dialog,
                    R.id.RepeatRadioButtonDaily);
            pressDialogButton(wrapper.getScenario(), dialog,
                    R.id.RepeatRadioButtonFixedSchedule);
            assertDayDateButtons(wrapper.getScenario(),
                    dialog.repeatEditor, null);
            pressDialogButton(wrapper.getScenario(), dialog,
                    android.R.id.button1);
        }
        assertTrue("The dialog did not call the onRepeatSet callback",
                onRepeatSetCalled);
        assertEquals("Repeat set by the dialog", expectedRepeat, setRepeat);
    }

    /**
     * Test switching the editor from another repeat interval type
     * to day-after.
     */
    @Test
    public void testChangeRepeatToDayAfter() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatSemiMonthlyOnDays initialRepeat = new RepeatSemiMonthlyOnDays(due);
        initialRepeat.setIncrement(RAND.nextInt(12) + 1);
        initialRepeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusMonths(RAND.nextInt(16)));
        initialRepeat.setDay(WeekDays.values()[RAND.nextInt(
                WeekDays.values().length)]);
        initialRepeat.setWeek(RAND.nextInt(3) + 1);
        initialRepeat.setDay2(WeekDays.values()[RAND.nextInt(
                WeekDays.values().length)]);
        initialRepeat.setWeek2(RAND.nextBoolean() ? 4 : -1);

        RepeatDayAfter expectedRepeat = new RepeatDayAfter();
        expectedRepeat.setIncrement(initialRepeat.getIncrement());
        expectedRepeat.setEnd(initialRepeat.getEnd());
        // The repeat-on-days interval doesn't initialize the week days;
        // it should default to all days, but we'll want to set these explicitly.
        // Ditto for direction; the default is "Next"
        setRandomAdjustment(expectedRepeat);

        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(ToDoNoteActivity.class)) {
            RepeatEditorDialog dialog = showRepeatEditorDialog(
                    wrapper.getScenario(), initialRepeat, due, ZoneOffset.UTC);
            assertRepeatTypeButtons(wrapper.getScenario(), dialog.repeatEditor,
                    R.id.RepeatRadioButtonSemiMonthly);
            pressDialogButton(wrapper.getScenario(), dialog,
                    R.id.RepeatRadioButtonDaily);
            pressDialogButton(wrapper.getScenario(), dialog,
                    R.id.RepeatRadioButtonAfterCompleted);
            assertDayDateButtons(wrapper.getScenario(),
                    dialog.repeatEditor, null);
            setWeekdayButtons(wrapper.getScenario(),
                    expectedRepeat.getAllowedWeekDays(),
                    expectedRepeat.getDirection());
            pressDialogButton(wrapper.getScenario(), dialog,
                    android.R.id.button1);
        }
        assertTrue("The dialog did not call the onRepeatSet callback",
                onRepeatSetCalled);
        assertEquals("Repeat set by the dialog", expectedRepeat, setRepeat);
    }

    /**
     * Test switching the editor from another repeat interval type
     * to weekly.
     */
    @Test
    public void testChangeRepeatToWeekly() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatYearAfter initialRepeat = new RepeatYearAfter();
        initialRepeat.setIncrement(RAND.nextInt(10) + 1);
        initialRepeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusYears(RAND.nextInt(4)));
        setRandomAdjustment(initialRepeat);

        RepeatWeekly expectedRepeat = new RepeatWeekly();
        expectedRepeat.setIncrement(initialRepeat.getIncrement());
        expectedRepeat.setEnd(initialRepeat.getEnd());
        expectedRepeat.setWeekDays(initialRepeat.getAllowedWeekDays());

        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(ToDoNoteActivity.class)) {
            RepeatEditorDialog dialog = showRepeatEditorDialog(
                    wrapper.getScenario(), initialRepeat, due, ZoneOffset.UTC);
            assertRepeatTypeButtons(wrapper.getScenario(), dialog.repeatEditor,
                    R.id.RepeatRadioButtonYearly);
            pressDialogButton(wrapper.getScenario(), dialog,
                    R.id.RepeatRadioButtonWeekly);
            pressDialogButton(wrapper.getScenario(), dialog,
                    R.id.RepeatRadioButtonFixedSchedule);
            assertDayDateButtons(wrapper.getScenario(),
                    dialog.repeatEditor, null);
            pressDialogButton(wrapper.getScenario(), dialog,
                    android.R.id.button1);
        }
        assertTrue("The dialog did not call the onRepeatSet callback",
                onRepeatSetCalled);
        assertEquals("Repeat set by the dialog", expectedRepeat, setRepeat);
    }

    /**
     * Test switching the editor from another repeat interval type
     * to week-after.
     */
    @Test
    public void testChangeRepeatToWeekAfter() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatMonthlyOnDay initialRepeat = new RepeatMonthlyOnDay(due);
        initialRepeat.setIncrement(RAND.nextInt(12) + 1);
        initialRepeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusMonths(RAND.nextInt(16)));
        int[] weekNums = {1, 2, 3, 4, -1};
        initialRepeat.setDay(WeekDays.values()[RAND.nextInt(WeekDays.values().length)]);
        initialRepeat.setWeek(weekNums[RAND.nextInt(weekNums.length)]);

        RepeatWeekAfter expectedRepeat = new RepeatWeekAfter();
        expectedRepeat.setIncrement(initialRepeat.getIncrement());
        expectedRepeat.setEnd(initialRepeat.getEnd());
        // The repeat-on-day interval doesn't initialize the week days;
        // it should default to all days, but we'll want to set these explicitly.
        // Ditto for direction; the default is "Next"
        setRandomAdjustment(expectedRepeat);

        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(ToDoNoteActivity.class)) {
            RepeatEditorDialog dialog = showRepeatEditorDialog(
                    wrapper.getScenario(), initialRepeat, due, ZoneOffset.UTC);
            assertRepeatTypeButtons(wrapper.getScenario(), dialog.repeatEditor,
                    R.id.RepeatRadioButtonMonthly);
            pressDialogButton(wrapper.getScenario(), dialog,
                    R.id.RepeatRadioButtonWeekly);
            pressDialogButton(wrapper.getScenario(), dialog,
                    R.id.RepeatRadioButtonAfterCompleted);
            assertDayDateButtons(wrapper.getScenario(),
                    dialog.repeatEditor, null);
            setWeekdayButtons(wrapper.getScenario(),
                    expectedRepeat.getAllowedWeekDays(),
                    expectedRepeat.getDirection());
            pressDialogButton(wrapper.getScenario(), dialog,
                    android.R.id.button1);
        }
        assertTrue("The dialog did not call the onRepeatSet callback",
                onRepeatSetCalled);
        assertEquals("Repeat set by the dialog", expectedRepeat, setRepeat);
    }

    /**
     * Test switching the editor from another repeat interval type
     * to semi-monthly on days.
     */
    @Test
    public void testChangeRepeatToSemiMonthlyOnDays() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatDayAfter initialRepeat = new RepeatDayAfter();
        initialRepeat.setIncrement(RAND.nextInt(100) + 1);
        initialRepeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusDays(RAND.nextInt(384)));
        setRandomAdjustment(initialRepeat);

        RepeatSemiMonthlyOnDays expectedRepeat = new RepeatSemiMonthlyOnDays();
        expectedRepeat.setIncrement(initialRepeat.getIncrement());
        expectedRepeat.setEnd(initialRepeat.getEnd());
        /*
         * The day-after interval doesn't initialize the days or weeks
         * of the month; the default in uncertain (could either be
         * fixed days/weeks or set according to the due date.)
         * Moreover the editor currently doesn't have any way to
         * change these settings!  So we won't have any concrete
         * expectation about them.
         */
        RepeatSettings settings;

        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(ToDoNoteActivity.class)) {
            RepeatEditorDialog dialog = showRepeatEditorDialog(
                    wrapper.getScenario(), initialRepeat, due, ZoneOffset.UTC);
            assertRepeatTypeButtons(wrapper.getScenario(),
                    dialog.repeatEditor, R.id.RepeatRadioButtonDaily);
            pressDialogButton(wrapper.getScenario(), dialog,
                    R.id.RepeatRadioButtonSemiMonthly);
            assertResetTypeButtons(wrapper.getScenario(),
                    dialog.repeatEditor, -1);
            pressDialogButton(wrapper.getScenario(), dialog,
                    R.id.RepeatRadioButtonByDay);
            assertWeekDayToggles(wrapper.getScenario(),
                    dialog.repeatEditor,null);
            assertDirectionButtons(wrapper.getScenario(),
                    dialog.repeatEditor, null);
            settings = dialog.getRepeatSettings();
            pressDialogButton(wrapper.getScenario(), dialog,
                    android.R.id.button1);
        }
        assertTrue("The dialog did not call the onRepeatSet callback",
                onRepeatSetCalled);
        expectedRepeat.setDay(settings.getDayOfWeek(0));
        expectedRepeat.setWeek(settings.getWeek(0));
        expectedRepeat.setDay2(settings.getDayOfWeek(1));
        expectedRepeat.setWeek2(settings.getWeek(1));
        assertEquals("Repeat set by the dialog", expectedRepeat, setRepeat);
    }

    /**
     * Test switching the editor from another repeat interval type
     * to semi-monthly on dates.
     */
    @Test
    public void testChangeRepeatToSemiMonthlyOnDates() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatYearlyOnDay initialRepeat = new RepeatYearlyOnDay(due);
        initialRepeat.setIncrement(RAND.nextInt(10) + 1);
        initialRepeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusYears(RAND.nextInt(4)));
        int[] weekNums = {1, 2, 3, 4, -1};
        initialRepeat.setDay(WeekDays.values()[RAND.nextInt(WeekDays.values().length)]);
        initialRepeat.setWeek(weekNums[RAND.nextInt(weekNums.length)]);
        initialRepeat.setMonth(Months.values()[RAND.nextInt(Months.values().length)]);

        RepeatSemiMonthlyOnDates expectedRepeat = new RepeatSemiMonthlyOnDates();
        expectedRepeat.setIncrement(initialRepeat.getIncrement());
        expectedRepeat.setEnd(initialRepeat.getEnd());
        // The repeat-on-day interval doesn't initialize the week days;
        // it should default to all days, but we'll want to set these explicitly.
        // Ditto for direction; the default is "Next"
        setRandomAdjustment(expectedRepeat);
        /*
         * Ditto for the dates; the default is uncertain.
         * Moreover the editor currently doesn't have any way to
         * change these settings!  So we won't have any concrete
         * expectation about them.
         */
        RepeatSettings settings;

        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(ToDoNoteActivity.class)) {
            RepeatEditorDialog dialog = showRepeatEditorDialog(
                    wrapper.getScenario(), initialRepeat, due, ZoneOffset.UTC);
            assertRepeatTypeButtons(wrapper.getScenario(),
                    dialog.repeatEditor, R.id.RepeatRadioButtonYearly);
            pressDialogButton(wrapper.getScenario(), dialog,
                    R.id.RepeatRadioButtonSemiMonthly);
            assertResetTypeButtons(wrapper.getScenario(),
                    dialog.repeatEditor, -1);
            pressDialogButton(wrapper.getScenario(), dialog,
                    R.id.RepeatRadioButtonByDate);
            setWeekdayButtons(wrapper.getScenario(),
                    expectedRepeat.getAllowedWeekDays(),
                    expectedRepeat.getDirection());
            settings = dialog.getRepeatSettings();
            pressDialogButton(wrapper.getScenario(), dialog,
                    android.R.id.button1);
        }
        assertTrue("The dialog did not call the onRepeatSet callback",
                onRepeatSetCalled);
        expectedRepeat.setDate(settings.getDate(0));
        expectedRepeat.setDate2(settings.getDate(1));
        assertEquals("Repeat set by the dialog", expectedRepeat, setRepeat);
    }

    /**
     * Test switching the editor from another repeat interval type
     * to monthly on day.
     */
    @Test
    public void testChangeRepeatToMonthlyOnDay() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatWeekAfter initialRepeat = new RepeatWeekAfter();
        initialRepeat.setIncrement(RAND.nextInt(52) + 1);
        initialRepeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusWeeks(RAND.nextInt(64)));
        setRandomAdjustment(initialRepeat);

        RepeatMonthlyOnDay expectedRepeat = new RepeatMonthlyOnDay();
        expectedRepeat.setIncrement(initialRepeat.getIncrement());
        expectedRepeat.setEnd(initialRepeat.getEnd());
        /*
         * The week-after interval doesn't initialize the day or week
         * of the month; the default in uncertain (could either be
         * a fixed day/week or set according to the due date.)
         * Moreover the editor currently doesn't have any way to
         * change these settings!  So we won't have any concrete
         * expectation about them.
         */
        RepeatSettings settings;

        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(ToDoNoteActivity.class)) {
            RepeatEditorDialog dialog = showRepeatEditorDialog(
                    wrapper.getScenario(), initialRepeat, due, ZoneOffset.UTC);
            assertRepeatTypeButtons(wrapper.getScenario(),
                    dialog.repeatEditor, R.id.RepeatRadioButtonWeekly);
            pressDialogButton(wrapper.getScenario(), dialog,
                    R.id.RepeatRadioButtonMonthly);
            pressDialogButton(wrapper.getScenario(), dialog,
                    R.id.RepeatRadioButtonFixedSchedule);
            pressDialogButton(wrapper.getScenario(), dialog,
                    R.id.RepeatRadioButtonByDay);
            assertWeekDayToggles(wrapper.getScenario(),
                    dialog.repeatEditor,null);
            settings = dialog.getRepeatSettings();
            pressDialogButton(wrapper.getScenario(), dialog,
                    android.R.id.button1);
        }
        assertTrue("The dialog did not call the onRepeatSet callback",
                onRepeatSetCalled);
        expectedRepeat.setDay(settings.getDayOfWeek(0));
        expectedRepeat.setWeek(settings.getWeek(0));
        assertEquals("Repeat set by the dialog", expectedRepeat, setRepeat);
    }

    /**
     * Test switching the editor from another repeat interval type
     * to monthly on date.
     */
    @Test
    public void testChangeRepeatToMonthlyOnDate() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatSemiMonthlyOnDays initialRepeat = new RepeatSemiMonthlyOnDays(due);
        initialRepeat.setIncrement(RAND.nextInt(12) + 1);
        initialRepeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusMonths(RAND.nextInt(16)));
        initialRepeat.setDay(WeekDays.values()[RAND.nextInt(WeekDays.values().length)]);
        initialRepeat.setWeek(RAND.nextInt(3) + 1);
        initialRepeat.setDay2(WeekDays.values()[RAND.nextInt(WeekDays.values().length)]);
        initialRepeat.setWeek2(RAND.nextBoolean() ? 4 : -1);

        RepeatMonthlyOnDate expectedRepeat = new RepeatMonthlyOnDate();
        expectedRepeat.setIncrement(initialRepeat.getIncrement());
        expectedRepeat.setEnd(initialRepeat.getEnd());
        // The repeat-on-days interval doesn't initialize the week days;
        // it should default to all days, but we'll want to set these explicitly.
        // Ditto for direction; the default is "Next"
        setRandomAdjustment(expectedRepeat);
        /*
         * Ditto for the date; the default is uncertain.
         * Moreover the editor currently doesn't have any way to
         * change this setting!  So we won't have any concrete
         * expectation about it.
         */
        RepeatSettings settings;

        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(ToDoNoteActivity.class)) {
            RepeatEditorDialog dialog = showRepeatEditorDialog(
                    wrapper.getScenario(), initialRepeat, due, ZoneOffset.UTC);
            assertRepeatTypeButtons(wrapper.getScenario(),
                    dialog.repeatEditor, R.id.RepeatRadioButtonSemiMonthly);
            pressDialogButton(wrapper.getScenario(), dialog,
                    R.id.RepeatRadioButtonMonthly);
            pressDialogButton(wrapper.getScenario(), dialog,
                    R.id.RepeatRadioButtonFixedSchedule);
            pressDialogButton(wrapper.getScenario(), dialog,
                    R.id.RepeatRadioButtonByDate);
            setWeekdayButtons(wrapper.getScenario(),
                    expectedRepeat.getAllowedWeekDays(),
                    expectedRepeat.getDirection());
            settings = dialog.getRepeatSettings();
            pressDialogButton(wrapper.getScenario(), dialog,
                    android.R.id.button1);
        }
        assertTrue("The dialog did not call the onRepeatSet callback",
                onRepeatSetCalled);
        expectedRepeat.setDate(settings.getDate(0));
        assertEquals("Repeat set by the dialog", expectedRepeat, setRepeat);
    }

    /**
     * Test switching the editor from another repeat interval type
     * to month-after.
     */
    @Test
    public void testChangeRepeatToMonthAfter() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatSemiMonthlyOnDays initialRepeat = new RepeatSemiMonthlyOnDays(due);
        initialRepeat.setIncrement(RAND.nextInt(12) + 1);
        initialRepeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusMonths(RAND.nextInt(16)));
        initialRepeat.setDay(WeekDays.values()[RAND.nextInt(WeekDays.values().length)]);
        initialRepeat.setWeek(RAND.nextInt(3) + 1);
        initialRepeat.setDay2(WeekDays.values()[RAND.nextInt(WeekDays.values().length)]);
        initialRepeat.setWeek2(RAND.nextBoolean() ? 4 : -1);

        RepeatMonthAfter expectedRepeat = new RepeatMonthAfter();
        expectedRepeat.setIncrement(initialRepeat.getIncrement());
        expectedRepeat.setEnd(initialRepeat.getEnd());
        setRandomAdjustment(expectedRepeat);

        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(ToDoNoteActivity.class)) {
            RepeatEditorDialog dialog = showRepeatEditorDialog(
                    wrapper.getScenario(), initialRepeat, due, ZoneOffset.UTC);
            assertRepeatTypeButtons(wrapper.getScenario(),
                    dialog.repeatEditor, R.id.RepeatRadioButtonSemiMonthly);
            pressDialogButton(wrapper.getScenario(), dialog,
                    R.id.RepeatRadioButtonMonthly);
            pressDialogButton(wrapper.getScenario(), dialog,
                    R.id.RepeatRadioButtonAfterCompleted);
            assertDayDateButtons(wrapper.getScenario(),
                    dialog.repeatEditor, null);
            setWeekdayButtons(wrapper.getScenario(),
                    expectedRepeat.getAllowedWeekDays(),
                    expectedRepeat.getDirection());
            pressDialogButton(wrapper.getScenario(), dialog,
                    android.R.id.button1);
        }
        assertTrue("The dialog did not call the onRepeatSet callback",
                onRepeatSetCalled);
        assertEquals("Repeat set by the dialog", expectedRepeat, setRepeat);
    }

    /**
     * Test switching the editor from another repeat interval type
     * to yearly by day.
     */
    @Test
    public void testChangeRepeatToYearlyByDay() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatDayAfter initialRepeat = new RepeatDayAfter();
        initialRepeat.setIncrement(RAND.nextInt(100) + 1);
        initialRepeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusDays(RAND.nextInt(384)));
        setRandomAdjustment(initialRepeat);

        RepeatYearlyOnDay expectedRepeat = new RepeatYearlyOnDay();
        expectedRepeat.setIncrement(initialRepeat.getIncrement());
        expectedRepeat.setEnd(initialRepeat.getEnd());
        /*
         * The day-after interval doesn't initialize the day or week
         * of the month or the month; the default in uncertain (could
         * either be a fixed day/week or set according to the due date.)
         * Moreover the editor currently doesn't have any way to
         * change these settings!  So we won't have any concrete
         * expectation about them.
         */
        RepeatSettings settings;

        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(ToDoNoteActivity.class)) {
            RepeatEditorDialog dialog = showRepeatEditorDialog(
                    wrapper.getScenario(), initialRepeat, due, ZoneOffset.UTC);
            assertRepeatTypeButtons(wrapper.getScenario(),
                    dialog.repeatEditor, R.id.RepeatRadioButtonDaily);
            pressDialogButton(wrapper.getScenario(), dialog,
                    R.id.RepeatRadioButtonYearly);
            pressDialogButton(wrapper.getScenario(), dialog,
                    R.id.RepeatRadioButtonFixedSchedule);
            pressDialogButton(wrapper.getScenario(), dialog,
                    R.id.RepeatRadioButtonByDay);
            assertWeekDayToggles(wrapper.getScenario(),
                    dialog.repeatEditor,null);
            settings = dialog.getRepeatSettings();
            pressDialogButton(wrapper.getScenario(), dialog,
                    android.R.id.button1);
        }
        assertTrue("The dialog did not call the onRepeatSet callback",
                onRepeatSetCalled);
        expectedRepeat.setDay(settings.getDayOfWeek(0));
        expectedRepeat.setWeek(settings.getWeek(0));
        expectedRepeat.setMonth(settings.getMonth());
        assertEquals("Repeat set by the dialog", expectedRepeat, setRepeat);
    }

    /**
     * Test switching the editor from another repeat interval type
     * to yearly by date.
     */
    @Test
    public void testChangeRepeatToYearlyByDate() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatWeekly initialRepeat = new RepeatWeekly();
        initialRepeat.setIncrement(RAND.nextInt(52) + 1);
        initialRepeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusWeeks(RAND.nextInt(64)));
        initialRepeat.setWeekDays(randomDays());

        RepeatYearlyOnDate expectedRepeat = new RepeatYearlyOnDate();
        expectedRepeat.setIncrement(initialRepeat.getIncrement());
        expectedRepeat.setEnd(initialRepeat.getEnd());
        expectedRepeat.setAllowedWeekDays(initialRepeat.getWeekDays());
        expectedRepeat.setDirection(WeekdayDirection.values()[RAND.nextInt(
                WeekdayDirection.values().length)]);
        /*
         * The weekly interval doesn't initialize the date or month;
         * the default in uncertain (could either be a fixed date and
         * month or set according to the due date.)  Moreover the editor
         * currently doesn't have any way to change these settings!  So
         * we won't have any concrete expectation about them.
         */
        RepeatSettings settings;

        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(ToDoNoteActivity.class)) {
            RepeatEditorDialog dialog = showRepeatEditorDialog(
                    wrapper.getScenario(), initialRepeat, due, ZoneOffset.UTC);
            assertRepeatTypeButtons(wrapper.getScenario(),
                    dialog.repeatEditor, R.id.RepeatRadioButtonWeekly);
            pressDialogButton(wrapper.getScenario(), dialog,
                    R.id.RepeatRadioButtonYearly);
            pressDialogButton(wrapper.getScenario(), dialog,
                    R.id.RepeatRadioButtonFixedSchedule);
            pressDialogButton(wrapper.getScenario(), dialog,
                    R.id.RepeatRadioButtonByDate);
            setDirectionButtons(wrapper.getScenario(),
                    expectedRepeat.getDirection());
            settings = dialog.getRepeatSettings();
            pressDialogButton(wrapper.getScenario(), dialog,
                    android.R.id.button1);
        }
        assertTrue("The dialog did not call the onRepeatSet callback",
                onRepeatSetCalled);
        expectedRepeat.setDate(settings.getDate(0));
        expectedRepeat.setMonth(settings.getMonth());
        assertEquals("Repeat set by the dialog", expectedRepeat, setRepeat);
    }

    /**
     * Test switching the editor from another repeat interval type
     * to year-after.
     */
    @Test
    public void testChangeRepeatToYearAfter() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(30));
        RepeatMonthlyOnDay initialRepeat = new RepeatMonthlyOnDay(due);
        initialRepeat.setIncrement(RAND.nextInt(12) + 1);
        initialRepeat.setEnd((RAND.nextFloat() < 0.215f) ? null
                : LocalDate.now(ZoneOffset.UTC).plusMonths(RAND.nextInt(16)));
        int[] weekNums = {1, 2, 3, 4, -1};
        initialRepeat.setDay(WeekDays.values()[RAND.nextInt(WeekDays.values().length)]);
        initialRepeat.setWeek(weekNums[RAND.nextInt(weekNums.length)]);

        RepeatYearAfter expectedRepeat = new RepeatYearAfter();
        expectedRepeat.setIncrement(initialRepeat.getIncrement());
        expectedRepeat.setEnd(initialRepeat.getEnd());
        setRandomAdjustment(expectedRepeat);

        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(ToDoNoteActivity.class)) {
            RepeatEditorDialog dialog = showRepeatEditorDialog(
                    wrapper.getScenario(), initialRepeat, due, ZoneOffset.UTC);
            assertRepeatTypeButtons(wrapper.getScenario(), dialog.repeatEditor,
                    R.id.RepeatRadioButtonMonthly);
            pressDialogButton(wrapper.getScenario(), dialog,
                    R.id.RepeatRadioButtonYearly);
            pressDialogButton(wrapper.getScenario(), dialog,
                    R.id.RepeatRadioButtonAfterCompleted);
            assertDayDateButtons(wrapper.getScenario(),
                    dialog.repeatEditor, null);
            setWeekdayButtons(wrapper.getScenario(),
                    expectedRepeat.getAllowedWeekDays(),
                    expectedRepeat.getDirection());
            pressDialogButton(wrapper.getScenario(), dialog,
                    android.R.id.button1);
        }
        assertTrue("The dialog did not call the onRepeatSet callback",
                onRepeatSetCalled);
        assertEquals("Repeat set by the dialog", expectedRepeat, setRepeat);
    }

    /**
     * Check that when the only day of the week toggle that is set is
     * turned off, the toggle for the due date&rsquo;s day of the week
     * automatically turns back on.
     */
    @Test
    public void testClearLastWeekDay() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(7));
        WeekDays targetDay = WeekDays.fromJavaDay(due.getDayOfWeek());
        RepeatDayAfter repeat = new RepeatDayAfter(due);
        setRandomAdjustment(repeat);
        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(ToDoNoteActivity.class)) {
            RepeatEditor widget = showRepeatEditorWidget(
                    wrapper.getScenario(), repeat, due, ZoneOffset.UTC);
            // First, randomly set the weekday buttons
            // except for the target day which should be turned off.
            Set<WeekDays> initialSet = randomDays();
            initialSet.remove(targetDay);
            if (initialSet.isEmpty()) {
                WeekDays alternate;
                do {
                    alternate = WeekDays.values()[RAND.nextInt(
                        WeekDays.values().length)];
                } while (alternate == targetDay);
                initialSet.add(alternate);
            }
            setWeekdayButtons(wrapper.getScenario(), initialSet, null);
            // Now try turning all the days off
            setWeekdayButtons(wrapper.getScenario(),
                    Collections.emptySet(), null);
            // Check that the target day is turned back on
            assertWeekDayToggles(wrapper.getScenario(), widget,
                    Collections.singleton(targetDay));
        }
    }

    /**
     * Check that when the only day of the week toggle that is set is
     * turned off and that is the due date&rsquo;s day of the week,
     * is is automatically turned back on.
     */
    @Test
    public void testDontClearSoleTargetWeekDay() {
        LocalDate due = LocalDate.now(ZoneOffset.UTC)
                .plusDays(RAND.nextInt(7));
        WeekDays targetDay = WeekDays.fromJavaDay(due.getDayOfWeek());
        RepeatDayAfter repeat = new RepeatDayAfter(due);
        Set<WeekDays> singleDaySet = Collections.singleton(targetDay);
        repeat.setAllowedWeekDays(singleDaySet);
        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(ToDoNoteActivity.class)) {
            RepeatEditor widget = showRepeatEditorWidget(
                    wrapper.getScenario(), repeat, due, ZoneOffset.UTC);
            // Check the pre-condition that the target is the only day set
            assertWeekDayToggles(wrapper.getScenario(), widget, singleDaySet);
            int buttonId = DAY_BUTTON_IDS[targetDay.getValue()
                    - WeekDays.SUNDAY.getValue()];
            // Try turning it off
            pressButton(wrapper.getScenario(), buttonId);
            // Check the post-condition that the target is still set
            assertWeekDayToggles(wrapper.getScenario(), widget, singleDaySet);
        }
    }

}
