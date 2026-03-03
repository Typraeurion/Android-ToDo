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
package com.xmission.trevin.android.todo.util;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.*;

import static com.xmission.trevin.android.todo.ui.FocusAction.requestFocus;
import static com.xmission.trevin.android.todo.util.LaunchUtils.*;
import static org.hamcrest.core.AllOf.allOf;
import static org.junit.Assert.*;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Build;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TimePicker;

import androidx.annotation.NonNull;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.platform.app.InstrumentationRegistry;

import org.hamcrest.Matcher;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Common action methods for UI tests to validate or interact with UI elements.
 */
public class ViewActionUtils {

    /**
     * Wait for an {@link AlertDialog} to be shown.
     *
     * @param scenario the scenario in which the test is running
     * @param expectedTitle the title of the dialog to look for
     *
     * @throws AssertionError if no dialog with the given title is found
     * within 5 seconds
     */
    public static <T extends Activity> void assertAlertDialogShown(
            @NonNull ActivityScenario<T> scenario,
            @NonNull String expectedTitle) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            onView(withText(expectedTitle))
                    .inRoot(isDialog())
                    .check(matches(withEffectiveVisibility(Visibility.VISIBLE)));
        } else {
            final AtomicReference<TextView> title = new AtomicReference<>();
            long timeout = System.nanoTime() + 5000000000L;
            while ((title.get() == null) &&
                    (System.nanoTime() < timeout)) {
                scenario.onActivity(activity -> {
                    TextView found = findViewRecursive(
                            activity.getWindow().getDecorView().getRootView(),
                            expectedTitle);
                    title.set(found);
                });
                if (title.get() == null) try {
                    Thread.sleep(50);
                } catch (InterruptedException ix) {
                    // Ignore
                }
            }
            assertNotNull(String.format(Locale.US,
                    "Dialog \"%s\" was not found", expectedTitle),
                    title.get());
        }
    }

    /**
     * Find the View within a given root that contains specific text
     *
     * @param root the root {@link View}
     * @param hasText the text to look for
     *
     * @return the view if found, or {@code null} if the text was not found
     */
    private static TextView findViewRecursive(View root, String hasText) {
        if (root instanceof TextView) {
            CharSequence cs = ((TextView) root).getText();
            if ((cs != null) && hasText.contentEquals(cs))
                return (TextView) root;
        }

        if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                TextView tv = findViewRecursive(vg.getChildAt(i), hasText);
                if (tv != null)
                    return tv;
            }
        }

        return null;
    }

    /**
     * Verify that a given button is present (and visible).
     * The button must exist within the activity content.
     *
     * @param scenario the scenario in which the test is running
     * @param buttonName the name of the button
     * @param buttonId the resource ID of the button to check
     *
     * @throws AssertionError if the button is missing or not visible
     */
    public static <T extends Activity> void assertButtonShown(
            ActivityScenario<T> scenario,
            String buttonName, int buttonId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            onView(withId(buttonId))
                    .check(matches(allOf(
                            withEffectiveVisibility(Visibility.VISIBLE),
                            isEnabled())));
        } else {
            scenario.onActivity(activity -> {
                Button button = activity.findViewById(buttonId);
                assertNotNull(buttonName + " button is missing", button);
                assertTrue(buttonName + " button is not visible",
                        button.isShown());
                assertTrue(buttonName + " button is disabled",
                        button.isEnabled());
            });
        }
    }

    /**
     * Verify that a given button is present (and visible).
     * in the currently running activity.
     * The button must exist within the activity content.
     *
     * @param buttonName the name of the button
     * @param buttonId the resource ID of the button to check
     *
     * @throws AssertionError if the button is missing or not visible
     */
    public static <T extends Activity> void assertButtonShown(
            String buttonName, int buttonId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            onView(withId(buttonId))
                    .check(matches(allOf(
                            withEffectiveVisibility(Visibility.VISIBLE),
                            isEnabled())));
        } else {
            final Activity activity = getCurrentActivity();
            assertNotNull("No activity is running", activity);
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Button button = activity.findViewById(buttonId);
                    assertNotNull(buttonName + " button is missing", button);
                    assertTrue(buttonName + " button is not visible",
                            button.isShown());
                    assertTrue(buttonName + " button is disabled",
                            button.isEnabled());
                }
            });
        }
    }

    /**
     * Verify that a given dialog button is present (and visible).
     *
     * @param scenario the scenario in which the test is running
     * @param dialog the {@link Dialog} containing the button.
     * @param buttonName the name of the button
     * @param buttonId the resource ID of the button to check
     *
     * @throws AssertionError if the button is missing or not visible
     */
    public static <T extends Activity> void assertButtonShown(
            ActivityScenario<T> scenario, @NonNull final Dialog dialog,
            String buttonName, int buttonId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            onView(withId(buttonId))
                    .check(matches(allOf(
                            withEffectiveVisibility(Visibility.VISIBLE),
                            isEnabled())));
        } else {
            scenario.onActivity(activity -> {
                Button button = dialog.findViewById(buttonId);
                assertNotNull(buttonName + " button is missing", button);
                assertTrue(buttonName + " button is not visible",
                        button.isShown());
                assertTrue(buttonName + " button is disabled",
                        button.isEnabled());
            });
        }
    }

    /**
     * Verify that a given dialog button is present (and visible)
     * in the currently running activity.
     *
     * @param dialog the {@link Dialog} containing the button.
     * @param buttonName the name of the button
     * @param buttonId the resource ID of the button to check
     *
     * @throws AssertionError if the button is missing or not visible
     */
    public static <T extends Activity> void assertButtonShown(
            @NonNull final Dialog dialog,
            String buttonName, int buttonId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            onView(withId(buttonId))
                    .check(matches(allOf(
                            withEffectiveVisibility(Visibility.VISIBLE),
                            isEnabled())));
        } else {
            final Activity activity = getCurrentActivity();
            assertNotNull("No activity is running", activity);
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Button button = dialog.findViewById(buttonId);
                    assertNotNull(buttonName + " button is missing", button);
                    assertTrue(buttonName + " button is not visible",
                            button.isShown());
                    assertTrue(buttonName + " button is disabled",
                            button.isEnabled());
                }
            });
        }
    }

    /**
     * Click the designated button.  The button must exist within the
     * activity content.  The test should have already verified that
     * the button exists and is enabled.
     *
     * @param scenario the scenario in which the test is running
     * @param buttonId the resource ID of the button to click
     */
    public static <T extends Activity> void pressButton(
            ActivityScenario<T> scenario,
            int buttonId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            onView(withId(buttonId)).perform(click());
        } else {
            scenario.onActivity(activity -> {
                Button button = activity.findViewById(buttonId);
                assertNotNull(String.format(Locale.US,
                        "No button found with ID %d", buttonId), button);
                button.performClick();
            });
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    /**
     * Click the designated button in the currently running activity.
     * The button must exist within the activity content.  The test
     * should have already verified that the button exists and is enabled.
     *
     * @param buttonId the resource ID of the button to click
     */
    public static <T extends Activity> void pressButton(int buttonId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            onView(withId(buttonId)).perform(click());
        } else {
            final Activity activity = getCurrentActivity();
            assertNotNull("No activity is running", activity);
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Button button = activity.findViewById(buttonId);
                    assertNotNull(String.format(Locale.US,
                            "No button found with ID %d", buttonId), button);
                    button.performClick();
                }
            });
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    /**
     * Click the designated dialog button.
     *
     * @param scenario the scenario in which the test is running
     * @param dialog the {@link Dialog} containing the button.
     * @param buttonId the resource ID of the button to click
     */
    public static <T extends Activity> void pressButton(
            ActivityScenario<T> scenario, @NonNull final Dialog dialog,
            int buttonId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            onView(withId(buttonId)).perform(click());
        } else {
            scenario.onActivity(activity -> {
                Button button = dialog.findViewById(buttonId);
                assertNotNull(String.format(Locale.US,
                        "No button found with ID %d", buttonId), button);
                button.performClick();
            });
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    /**
     * Click the designated dialog button in the currently running activity.
     *
     * @param dialog the {@link Dialog} containing the button.
     * @param buttonId the resource ID of the button to click
     */
    public static <T extends Activity> void pressButton(
            @NonNull final Dialog dialog, int buttonId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            onView(withId(buttonId)).perform(click());
        } else {
            final Activity activity = getCurrentActivity();
            assertNotNull("No activity is running", activity);
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Button button = dialog.findViewById(buttonId);
                    assertNotNull(String.format(Locale.US,
                            "No button found with ID %d", buttonId), button);
                    button.performClick();
                }
            });
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    /**
     * Click the designated alert dialog button.  The caller should have
     * already asserted that the dialog is shown via
     * {@link #assertAlertDialogShown(ActivityScenario, String)}.
     *
     * @param scenario the scenario in which the test is running
     * @param buttonId one of {@link android.R.id#button1} (positive),
     * {@link android.R.id#button2} (negative), or
     * {@link android.R.id#button3} (neutral).
     * @param buttonText the text that should be shown on this button
     */
    public static <T extends Activity> void pressAlertDialogButton(
            ActivityScenario<T> scenario, int buttonId, String buttonText) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            onView(withText(buttonText))
                    .inRoot(isDialog())
                    .perform(click());
        } else {
            scenario.onActivity(activity -> {
                activity.findViewById(buttonId).performClick();
            });
        }
    }

    /**
     * Press the system &ldquo;Back&rdquo; button.
     */
    public static void pressBackButton() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            pressBack();
        } else {
            InstrumentationRegistry.getInstrumentation()
                    .sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        }
    }

    /**
     * Return the content of a text view.  The text view must exist
     * within the activity content.
     *
     * @param scenario the scenario in which the test is running
     * @param viewName the name of the text view to use for any assertion error
     * @param fieldId the resource ID of the text view
     *
     * @return the text of the UI element
     *
     * @throws AssertionError if the given field is missing
     */
    public static <T extends Activity> String getElementText(
            ActivityScenario<T> scenario,
            String viewName, int fieldId) {
        final String[] text = new String[1];
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            onView(withId(fieldId))
                    .check((view, noViewFoundException) -> {
                        text[0] = ((TextView) view).getText().toString();
                    });
        } else {
            scenario.onActivity(activity -> {
                TextView textField = (TextView) activity.findViewById(fieldId);
                assertNotNull(String.format(Locale.US,
                        "%s is missing", viewName), textField);
                text[0] = textField.getText().toString();
            });
        }
        return text[0];
    }

    /**
     * Return the content of a text view in the currently running activity.
     * The text view must exist within the activity content.
     *
     * @param viewName the name of the text view to use for any assertion error
     * @param fieldId the resource ID of the text view
     *
     * @return the text of the UI element
     *
     * @throws AssertionError if the given field is missing
     */
    public static String getElementText(
            String viewName, int fieldId) {
        final String[] text = new String[1];
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            onView(withId(fieldId))
                    .check((view, noViewFoundException) -> {
                        text[0] = ((TextView) view).getText().toString();
                    });
        } else {
            final Activity activity = getCurrentActivity();
            assertNotNull("No activity is running", activity);
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    TextView textField = (TextView) activity
                            .findViewById(fieldId);
                    assertNotNull(String.format(Locale.US,
                            "%s is missing", viewName), textField);
                    text[0] = textField.getText().toString();
                }
            });
        }
        return text[0];
    }

    /**
     * Return the content of a text view within a dialog.
     *
     * @param scenario the scenario in which the test is running
     * @param dialog the {@link Dialog} containing the text view.
     * @param viewName the name of the text view to use for any assertion error
     * @param fieldId the resource ID of the text view
     *
     * @return the text of the UI element
     *
     * @throws AssertionError if the given field is missing
     */
    public static <T extends Activity> String getElementText(
            ActivityScenario<T> scenario,
            @NonNull final Dialog dialog,
            String viewName, int fieldId) {
        final String[] text = new String[1];
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            onView(withId(fieldId))
                    .check((view, noViewFoundException) -> {
                        text[0] = ((TextView) view).getText().toString();
                    });
        } else {
            scenario.onActivity(activity -> {
                TextView textField = (TextView) dialog.findViewById(fieldId);
                assertNotNull(String.format(Locale.US,
                        "%s is missing", viewName), textField);
                text[0] = textField.getText().toString();
            });
        }
        return text[0];
    }

    /**
     * Return the content of a text view within a dialog of
     * the currently running activity.
     *
     * @param dialog the {@link Dialog} containing the text view.
     * @param viewName the name of the text view to use for any assertion error
     * @param fieldId the resource ID of the text view
     *
     * @return the text of the UI element
     *
     * @throws AssertionError if the given field is missing
     */
    public static String getElementText(@NonNull final Dialog dialog,
                                         String viewName, int fieldId) {
        final String[] text = new String[1];
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            onView(withId(fieldId))
                    .check((view, noViewFoundException) -> {
                        text[0] = ((TextView) view).getText().toString();
                    });
        } else {
            final Activity activity = getCurrentActivity();
            assertNotNull("No activity is running", activity);
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    TextView textField = (TextView) dialog
                            .findViewById(fieldId);
                    assertNotNull(String.format(Locale.US,
                            "%s is missing", viewName), textField);
                    text[0] = textField.getText().toString();
                }
            });
        }
        return text[0];
    }

    /**
     * Change the content of an edit text field.  The field must exist
     * within the activity content.
     *
     * @param scenario the scenario in which the test is running
     * @param fieldName the name of the edit text field to use
     * for any assertion error
     * @param fieldId the resource ID of the edit text field
     * @param newText the text to set in the edit text field
     *
     * @throws AssertionError if the given field is missing
     */
    public static <T extends Activity> void setEditText(
            ActivityScenario<T> scenario,
            String fieldName, int fieldId, final String newText) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            onView(withId(fieldId))
                    .check(matches(isAssignableFrom(EditText.class)))
                    .perform(requestFocus())
                    .perform(replaceText(newText));
        } else {
            scenario.onActivity(activity -> {
                EditText textField = (EditText) activity.findViewById(fieldId);
                assertNotNull(String.format(Locale.US,
                        "%s is missing", fieldName), textField);
                textField.requestFocus();
                textField.setText(newText);
            });
        }
    }

    /**
     * Return the content of a text view in the currently running activity.
     * The text view must exist within the activity content.
     *
     * @param viewName the name of the text view to use for any assertion error
     * @param fieldId the resource ID of the text view
     *
     * @return the text of the UI element
     *
     * @throws AssertionError if the given field is missing
     */
    public static void setEditText(String viewName, int fieldId,
                                    final String newText) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            onView(withId(fieldId))
                    .check(matches(isAssignableFrom(EditText.class)))
                    .perform(requestFocus())
                    .perform(replaceText(newText));
        } else {
            final Activity activity = getCurrentActivity();
            assertNotNull("No activity is running", activity);
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    EditText textField = (EditText) activity
                            .findViewById(fieldId);
                    assertNotNull(String.format(Locale.US,
                            "%s is missing", viewName), textField);
                    textField.requestFocus();
                    textField.setText(newText);
                }
            });
        }
    }

    /**
     * Return the content of a text view within a dialog.
     *
     * @param scenario the scenario in which the test is running
     * @param dialog the {@link Dialog} containing the text view.
     * @param viewName the name of the text view to use for any assertion error
     * @param fieldId the resource ID of the text view
     *
     * @return the text of the UI element
     *
     * @throws AssertionError if the given field is missing
     */
    public static <T extends Activity> void setEditText(
            ActivityScenario<T> scenario,
            @NonNull final Dialog dialog,
            String viewName, int fieldId, final String newText) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            onView(withId(fieldId))
                    .check(matches(isAssignableFrom(EditText.class)))
                    .perform(requestFocus())
                    .perform(replaceText(newText));
        } else {
            scenario.onActivity(activity -> {
                EditText textField = (EditText) dialog.findViewById(fieldId);
                assertNotNull(String.format(Locale.US,
                        "%s is missing", viewName), textField);
                textField.requestFocus();
                textField.setText(newText);
            });
        }
    }

    /**
     * Change the content of an edit text field within a dialog of
     * the currently running activity.
     *
     * @param dialog the {@link Dialog} containing the text view.
     * @param viewName the name of the text view to use for any assertion error
     * @param fieldId the resource ID of the text view
     * @param newText the text to set in the edit text field
     *
     * @throws AssertionError if the given field is missing
     */
    public static void setEditText(
            @NonNull final Dialog dialog,
            String viewName, int fieldId, final String newText) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            onView(withId(fieldId))
                    .check(matches(isAssignableFrom(EditText.class)))
                    .perform(requestFocus())
                    .perform(replaceText(newText));
        } else {
            final Activity activity = getCurrentActivity();
            assertNotNull("No activity is running", activity);
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    EditText textField = (EditText) dialog
                            .findViewById(fieldId);
                    assertNotNull(String.format(Locale.US,
                            "%s is missing", viewName), textField);
                    textField.requestFocus();
                    textField.setText(newText);
                }
            });
        }
    }

    /**
     * Return the state of a check box.  The check box must exist
     * within the activity content.
     *
     * @param scenario the scenario in which the test is running
     * @param viewName the name of the check box to use for any assertion error
     * @param fieldId the resource ID of the check box
     *
     * @return the text of the UI element
     *
     * @throws AssertionError if the given field is missing
     */
    public static <T extends Activity> boolean getCheckboxState(
            ActivityScenario<T> scenario,
            String viewName, int fieldId) {
        final boolean[] state = new boolean[1];
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            onView(withId(fieldId))
                    .check((view, noViewFoundException) -> {
                        state[0] = ((CheckBox) view).isChecked();
                    });
        } else {
            scenario.onActivity(activity -> {
                CheckBox box = (CheckBox) activity.findViewById(fieldId);
                assertNotNull(String.format(Locale.US,
                        "%s is missing", viewName), box);
                state[0] = box.isChecked();
            });
        }
        return state[0];
    }

    /**
     * Return the state of a check box in the currently running activity.
     * The check box must exist within the activity content.
     *
     * @param viewName the name of the check box to use for any assertion error
     * @param fieldId the resource ID of the check box
     *
     * @return the text of the UI element
     *
     * @throws AssertionError if the given field is missing
     */
    public static boolean getCheckboxState(String viewName, int fieldId) {
        final boolean[] state = new boolean[1];
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            onView(withId(fieldId))
                    .check((view, noViewFoundException) -> {
                        state[0] = ((CheckBox) view).isChecked();
                    });
        } else {
            final Activity activity = getCurrentActivity();
            assertNotNull("No activity is running", activity);
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    CheckBox box = (CheckBox) activity
                            .findViewById(fieldId);
                    assertNotNull(String.format(Locale.US,
                            "%s is missing", viewName), box);
                    state[0] = box.isChecked();
                }
            });
        }
        return state[0];
    }

    /**
     * Return the state of a check box within a dialog.
     *
     * @param scenario the scenario in which the test is running
     * @param dialog the {@link Dialog} containing the check box.
     * @param viewName the name of the check box to use for any assertion error
     * @param fieldId the resource ID of the check box
     *
     * @return the text of the UI element
     *
     * @throws AssertionError if the given field is missing
     */
    public static <T extends Activity> boolean getCheckboxState(
            ActivityScenario<T> scenario,
            @NonNull final Dialog dialog,
            String viewName, int fieldId) {
        final boolean[] state = new boolean[1];
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            onView(withId(fieldId))
                    .check((view, noViewFoundException) -> {
                        state[0] = ((CheckBox) view).isChecked();
                    });
        } else {
            scenario.onActivity(activity -> {
                CheckBox box = (CheckBox) dialog.findViewById(fieldId);
                assertNotNull(String.format(Locale.US,
                        "%s is missing", viewName), box);
                state[0] = box.isChecked();
            });
        }
        return state[0];
    }

    /**
     * Return the state of a check box view within a dialog of
     * the currently running activity.
     *
     * @param dialog the {@link Dialog} containing the check box.
     * @param viewName the name of the check box to use for any assertion error
     * @param fieldId the resource ID of the check box
     *
     * @return the text of the UI element
     *
     * @throws AssertionError if the given field is missing
     */
    public static boolean getCheckboxState(@NonNull final Dialog dialog,
                                            String viewName, int fieldId) {
        final boolean[] state = new boolean[1];
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            onView(withId(fieldId))
                    .check((view, noViewFoundException) -> {
                        state[0] = ((CheckBox) view).isChecked();
                    });
        } else {
            final Activity activity = getCurrentActivity();
            assertNotNull("No activity is running", activity);
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    CheckBox box = (CheckBox) dialog
                            .findViewById(fieldId);
                    assertNotNull(String.format(Locale.US,
                            "%s is missing", viewName), box);
                    state[0] = box.isChecked();
                }
            });
        }
        return state[0];
    }

    /**
     * Set the time on a {@link TimePicker} widget.
     *
     * @param scenario the scenario in which the test is running
     * @param dialog the {@link Dialog} containing the time picker
     * @param pickerId the resource ID of the {@link TimePicker}
     * @param hour the hour to set (0-23)
     * @param minute the minute to set (0-59)
     */
    public static <T extends Activity> void setTime(
            ActivityScenario<T> scenario, @NonNull final Dialog dialog,
            int pickerId, final int hour, final int minute) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            onView(withId(pickerId))
                    .perform(new ViewAction() {
                @Override
                public Matcher<View> getConstraints() {
                    return isAssignableFrom(TimePicker.class);
                }

                @Override
                public String getDescription() {
                    return "set time on TimePicker";
                }

                @Override
                public void perform(UiController uiController, View view) {
                    TimePicker tp = (TimePicker) view;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        tp.setHour(hour);
                        tp.setMinute(minute);
                    } else {
                        tp.setCurrentHour(hour);
                        tp.setCurrentMinute(minute);
                    }
                }
            });
        } else {
            scenario.onActivity(activity -> {
                TimePicker tp = dialog.findViewById(pickerId);
                assertNotNull("TimePicker is missing", tp);
                tp.setHour(hour);
                tp.setMinute(minute);
            });
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

}
