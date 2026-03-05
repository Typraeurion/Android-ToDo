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
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
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
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
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

    private static final String LOG_TAG = "ViewActionUtils";

    /**
     * Wait for a {@link Dialog} with specified text to be shown.
     * Normally this text would be the title, but this will work
     * with text anywhere in the view hierarchy so it should be unique.
     *
     * @param scenario the scenario in which the test is running
     * @param expectedText the text within the dialog to look for
     *
     * @throws AssertionError if no dialog with the given title is found
     * within 5 seconds
     */
    public static <T extends Activity> void assertDialogShown(
            @NonNull ActivityScenario<T> scenario,
            @NonNull String expectedText) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            onView(withText(expectedText))
                    .inRoot(isDialog())
                    .check(matches(withEffectiveVisibility(Visibility.VISIBLE)));
        } else {
            final AtomicReference<TextView> title = new AtomicReference<>();
            long timeout = System.nanoTime() + 5000000000L;
            while ((title.get() == null) &&
                    (System.nanoTime() < timeout)) {
                scenario.onActivity(activity -> {
                    View root = activity.getWindow().getDecorView().getRootView();
                    TextView found = findViewRecursive(root, expectedText);
                    title.set(found);
                });
                if (title.get() == null) try {
                    Thread.sleep(50);
                } catch (InterruptedException ix) {
                    // Ignore
                }
            }
            assertNotNull(String.format(Locale.US,
                            "Dialog \"%s\" was not found", expectedText),
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
     * This is the Espresso version.
     *
     * @param buttonId the resource ID of the button to check
     */
    private static void esAssertButtonShown(int buttonId) {
        onView(withId(buttonId))
                .check(matches(allOf(
                        withEffectiveVisibility(Visibility.VISIBLE),
                        isEnabled())));
    }

    /**
     * Verify that a given view is a button that is visible.
     * It may be a text {@link Button} or an {@link ImageButton}.
     * The caller is responsible for finding the view.
     *
     * @param view the {@link View} to check
     * @param buttonName the name of the button for any assertion message
     * @param buttonId the resource ID of the button for any assertion message
     *
     * @throws AssertionError if the button is missing, not visible,
     * or is neither a {@link Button} nor {@link ImageButton}.
     */
    private static void assertViewIsVisibleButton(
            View view, String buttonName, int buttonId) {
            assertNotNull(buttonName + " button is missing", view);
            if (!(view instanceof Button) &&
                    !(view instanceof ImageButton)) {
                fail(String.format(Locale.US,
                        "%s view with ID %d is neither a Button"
                                + " nor an ImageButton",
                        buttonName, buttonId));
            }
            assertTrue(buttonName + " button is not visible",
                    view.isShown());
            assertTrue(buttonName + " button is disabled",
                    view.isEnabled());
    }

    /**
     * Verify that a given button is present (and visible).
     * The button must exist within the activity content.
     * It may be a text {@link Button} or an {@link ImageButton}.
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
            esAssertButtonShown(buttonId);
        } else {
            final View[] buttonRef = new View[1];
            scenario.onActivity(activity -> {
                buttonRef[0] = activity.findViewById(buttonId);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertViewIsVisibleButton(buttonRef[0], buttonName, buttonId);
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
            esAssertButtonShown(buttonId);
        } else {
            final Activity activity = getCurrentActivity();
            assertNotNull("No activity is running", activity);
            final View[] buttonRef = new View[1];
            activity.runOnUiThread(() -> {
                buttonRef[0] = activity.findViewById(buttonId);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertViewIsVisibleButton(buttonRef[0], buttonName, buttonId);
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
            esAssertButtonShown(buttonId);
        } else {
            final View[] buttonRef = new View[1];
            scenario.onActivity(activity -> {
                buttonRef[0] = dialog.findViewById(buttonId);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertViewIsVisibleButton(buttonRef[0], buttonName, buttonId);
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
            esAssertButtonShown(buttonId);
        } else {
            final Activity activity = getCurrentActivity();
            assertNotNull("No activity is running", activity);
            final View[] buttonRef = new View[1];
            activity.runOnUiThread(() -> {
                buttonRef[0] = dialog.findViewById(buttonId);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertViewIsVisibleButton(buttonRef[0], buttonName, buttonId);
        }
    }

    /**
     * Click the designated button.  This is the Espresso-only version.
     *
     * @param buttonId the resource ID of the button to click
     *
     * @throws AssertionError if the button is not visible or enabled
     */
    private static void esPressButton(int buttonId) {
        // Check whether the button is visible; otherwise click() will fail.
        boolean isVisible;
        try {
            onView(withId(buttonId))
                    .check(matches(isCompletelyDisplayed()));
            isVisible = true;
        } catch (AssertionError | Exception e) {
            isVisible = false;
        }
        // Try scrolling to the button if necessary
        if (!isVisible) try {
            onView(withId(buttonId))
                    .perform(scrollTo());
        } catch (AssertionError | Exception e) {
            // Ignore
            Log.w(LOG_TAG, String.format(Locale.US,
                    "Button %d is not completedy visible"
                            + " and we failed to scroll to it.",
                    buttonId));
        }
        onView(withId(buttonId))
                .check(matches(allOf(isEnabled(),
                        isDisplayingAtLeast(90))))
                .perform(click());
    }

    /**
     * Click the designated button in a dialog.
     * This is the Espresso-only version.
     *
     * @param buttonId the resource ID of the button to click
     *
     * @throws AssertionError if the button is not visible or enabled
     */
    private static void esPressDialogButton(int buttonId) {
        // Check whether the button is visible; otherwise click() will fail.
        boolean isVisible;
        try {
            onView(withId(buttonId))
                    .inRoot(isDialog())
                    .check(matches(isCompletelyDisplayed()));
            isVisible = true;
        } catch (AssertionError | Exception e) {
            isVisible = false;
        }
        // Try scrolling to the button if necessary
        if (!isVisible) try {
            onView(withId(buttonId))
                    .inRoot(isDialog())
                    .perform(scrollTo());
        } catch (AssertionError | Exception e) {
            // Ignore
            Log.w(LOG_TAG, String.format(Locale.US,
                    "Button %d is not completedy visible"
                            + " and we failed to scroll to it.",
                    buttonId));
        }
        onView(withId(buttonId))
                .inRoot(isDialog())
                .check(matches(allOf(isEnabled(),
                        isDisplayingAtLeast(90))))
                .perform(click());
    }

    /**
     * Click the designated button.  The button must exist within the
     * activity content.  The test should have already verified that
     * the button exists and is enabled.  It may be either a text
     * {@link Button} or an {@link ImageButton}.
     *
     * @param scenario the scenario in which the test is running
     * @param buttonId the resource ID of the button to click
     */
    public static <T extends Activity> void pressButton(
            ActivityScenario<T> scenario,
            int buttonId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            esPressButton(buttonId);
        } else {
            final View[] buttonRef = new View[1];
            scenario.onActivity(activity -> {
                buttonRef[0] = activity.findViewById(buttonId);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertNotNull(String.format(Locale.US,
                    "No button found with ID %d", buttonId), buttonRef[0]);
            if (!(buttonRef[0] instanceof Button) &&
                    !(buttonRef[0] instanceof ImageButton))
                fail(String.format(Locale.US,
                        "View with ID %d is neither a Button nor ImageButton",
                        buttonId));
            scenario.onActivity(activity -> {
                buttonRef[0].performClick();
            });
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    /**
     * Click the designated button in the currently running activity.
     * The button must exist within the activity content.  The test
     * should have already verified that the button exists and is enabled.
     * It may be either a text {@link Button} or an {@link ImageButton}.
     *
     * @param buttonId the resource ID of the button to click
     */
    public static <T extends Activity> void pressButton(int buttonId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            esPressButton(buttonId);
        } else {
            final Activity activity = getCurrentActivity();
            assertNotNull("No activity is running", activity);
            final View[] buttonRef = new View[1];
            activity.runOnUiThread(() -> {
                buttonRef[0] = activity.findViewById(buttonId);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertNotNull(String.format(Locale.US,
                    "No button found with ID %d", buttonId), buttonRef[0]);
            if (!(buttonRef[0] instanceof Button) &&
                    !(buttonRef[0] instanceof ImageButton))
                fail(String.format(Locale.US,
                        "View with ID %d is neither a Button nor ImageButton",
                        buttonId));
            activity.runOnUiThread(() -> {
                    buttonRef[0].performClick();
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
    public static <T extends Activity> void pressDialogButton(
            ActivityScenario<T> scenario, @NonNull final Dialog dialog,
            int buttonId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            esPressDialogButton(buttonId);
        } else {
            View[] buttonRef = new View[1];
            scenario.onActivity(activity -> {
                buttonRef[0] = dialog.findViewById(buttonId);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertNotNull(String.format(Locale.US,
                    "No button found with ID %d", buttonId), buttonRef[0]);
            assertTrue(String.format(Locale.US,
                            "View with ID %d is not a Button", buttonId),
                    buttonRef[0] instanceof Button);
            scenario.onActivity(activity -> {
                buttonRef[0].performClick();
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
    public static <T extends Activity> void pressDialogButton(
            @NonNull final Dialog dialog, int buttonId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            esPressDialogButton(buttonId);
        } else {
            final Activity activity = getCurrentActivity();
            assertNotNull("No activity is running", activity);
            final View[] buttonRef = new View[1];
            activity.runOnUiThread(() -> {
                buttonRef[0] = dialog.findViewById(buttonId);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertNotNull(String.format(Locale.US,
                    "No button found with ID %d", buttonId), buttonRef[0]);
            assertTrue(String.format(Locale.US,
                            "View with ID %d is not a Button", buttonId),
                    buttonRef[0] instanceof Button);
            activity.runOnUiThread(() -> {
                    buttonRef[0].performClick();
            });
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    /**
     * Press the system &ldquo;Back&rdquo; button.
     */
    public static void pressBackButton() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            pressBack();
        } else {
            InstrumentationRegistry.getInstrumentation()
                    .sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
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
        final View[] viewRef = new View[1];
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            onView(withId(fieldId))
                    .check((view, noViewFoundException) -> {
                        viewRef[0] = view;
                    });
        } else {
            scenario.onActivity(activity -> {
                viewRef[0] = activity.findViewById(fieldId);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        }
        assertNotNull(String.format(Locale.US,
                "%s is missing", viewName), viewRef[0]);
        assertTrue(String.format(Locale.US,
                        "%s with ID %d is not a TextView", viewName, fieldId),
                viewRef[0] instanceof TextView);
        return ((TextView) viewRef[0]).getText().toString();
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
        final View[] viewRef = new View[1];
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            onView(withId(fieldId))
                    .check((view, noViewFoundException) -> {
                        viewRef[0] = view;
                    });
        } else {
            final Activity activity = getCurrentActivity();
            assertNotNull("No activity is running", activity);
            activity.runOnUiThread(() -> {
                viewRef[0] = activity.findViewById(fieldId);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        }
        assertNotNull(String.format(Locale.US,
                "%s is missing", viewName), viewRef[0]);
        assertTrue(String.format(Locale.US,
                        "%s with ID %d is not a TextView", viewName, fieldId),
                viewRef[0] instanceof TextView);
        return ((TextView) viewRef[0]).getText().toString();
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
        final View[] viewRef = new View[1];
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            onView(withId(fieldId))
                    .check((view, noViewFoundException) -> {
                        viewRef[0] = view;
                    });
        } else {
            scenario.onActivity(activity -> {
                viewRef[0] = dialog.findViewById(fieldId);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        }
        assertNotNull(String.format(Locale.US,
                "%s is missing", viewName), viewRef[0]);
        assertTrue(String.format(Locale.US,
                        "%s with ID %d is not a TextView", viewName, fieldId),
                viewRef[0] instanceof TextView);
        return ((TextView) viewRef[0]).getText().toString();
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
        final View[] viewRef = new View[1];
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            onView(withId(fieldId))
                    .check((view, noViewFoundException) -> {
                        viewRef[0] = view;
                    });
        } else {
            final Activity activity = getCurrentActivity();
            assertNotNull("No activity is running", activity);
            activity.runOnUiThread(() -> {
                viewRef[0] = dialog.findViewById(fieldId);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        }
        assertNotNull(String.format(Locale.US,
                "%s is missing", viewName), viewRef[0]);
        assertTrue(String.format(Locale.US,
                        "%s with ID %d is not a TextView", viewName, fieldId),
                viewRef[0] instanceof TextView);
        return ((TextView) viewRef[0]).getText().toString();
    }

    /**
     * Change the content of an edit text field.  The field must exist
     * within the activity content.  This is the Espresso version.
     *
     * @param fieldId the resource ID of the edit text field
     * @param newText the text to set in the edit text field
     */
    private static void esSetEditText(int fieldId, final String newText) {
        onView(withId(fieldId))
                .check(matches(isAssignableFrom(EditText.class)))
                .perform(scrollTo(),
                        requestFocus(),
                        replaceText(newText),
                        closeSoftKeyboard());
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            esSetEditText(fieldId, newText);
        } else {
            final View[] viewRef = new View[1];
            scenario.onActivity(activity -> {
                viewRef[0] = activity.findViewById(fieldId);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertNotNull(String.format(Locale.US,
                    "%s is missing", fieldName), viewRef[0]);
            assertTrue(String.format(Locale.US,
                "%s with ID %d is not an EditText", fieldName, fieldId),
                    viewRef[0] instanceof EditText);
            scenario.onActivity(activity -> {
                viewRef[0].requestFocus();
                ((EditText) viewRef[0]).setText(newText);
            });
        }
    }

    /**
     * Change the content of an edit text field in the currently running
     * activity.  The field must exist within the activity content.
     *
     * @param fieldName the name of the edit text field to use
     * for any assertion error
     * @param fieldId the resource ID of the edit text field
     * @param newText the text to set in the edit text field
     *
     * @throws AssertionError if the given field is missing
     */
    public static void setEditText(String fieldName, int fieldId,
                                    final String newText) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            esSetEditText(fieldId, newText);
        } else {
            final Activity activity = getCurrentActivity();
            assertNotNull("No activity is running", activity);
            final View[] viewRef = new View[1];
            activity.runOnUiThread(() -> {
                viewRef[0] = activity.findViewById(fieldId);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertNotNull(String.format(Locale.US,
                    "%s is missing", fieldName), viewRef[0]);
            assertTrue(String.format(Locale.US,
                "%s with ID %d is not an EditText", fieldName, fieldId),
                    viewRef[0] instanceof EditText);
            activity.runOnUiThread(() -> {
                viewRef[0].requestFocus();
                ((EditText) viewRef[0]).setText(newText);
            });
        }
    }

    /**
     * Change the content of an edit text view within a dialog.
     *
     * @param scenario the scenario in which the test is running
     * @param dialog the {@link Dialog} containing the text view.
     * @param fieldName the name of the edit text field to use
     * for any assertion error
     * @param fieldId the resource ID of the edit text field
     * @param newText the text to set in the edit text field
     *
     * @throws AssertionError if the given field is missing
     */
    public static <T extends Activity> void setEditText(
            ActivityScenario<T> scenario,
            @NonNull final Dialog dialog,
            String fieldName, int fieldId, final String newText) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            esSetEditText(fieldId, newText);
        } else {
            final View[] viewRef = new View[1];
            scenario.onActivity(activity -> {
                viewRef[0] = dialog.findViewById(fieldId);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertNotNull(String.format(Locale.US,
                    "%s is missing", fieldName), viewRef[0]);
            assertTrue(String.format(Locale.US,
                "%s with ID %d is not an EditText", fieldName, fieldId),
                    viewRef[0] instanceof EditText);
            scenario.onActivity(activity -> {
                viewRef[0].requestFocus();
                ((EditText) viewRef[0]).setText(newText);
            });
        }
    }

    /**
     * Change the content of an edit text field within a dialog of
     * the currently running activity.
     *
     * @param dialog the {@link Dialog} containing the edit text field.
     * @param fieldName the name of the edit text field to use
     * for any assertion error
     * @param fieldId the resource ID of the edit text field
     * @param newText the text to set in the edit text field
     *
     * @throws AssertionError if the given field is missing
     */
    public static void setEditText(
            @NonNull final Dialog dialog,
            String fieldName, int fieldId, final String newText) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            esSetEditText(fieldId, newText);
        } else {
            final Activity activity = getCurrentActivity();
            assertNotNull("No activity is running", activity);
            final View[] viewRef = new View[1];
            activity.runOnUiThread(() -> {
                viewRef[0] = dialog.findViewById(fieldId);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertNotNull(String.format(Locale.US,
                    "%s is missing", fieldName), viewRef[0]);
            assertTrue(String.format(Locale.US,
                            "%s with ID %d is not an EditText",
                            fieldName, fieldId),
                    viewRef[0] instanceof EditText);
            activity.runOnUiThread(() -> {
                viewRef[0].requestFocus();
                ((EditText) viewRef[0]).setText(newText);
            });
        }
    }

    /**
     * Return the state of a check box.  The check box must exist
     * within the activity content.  This is the Espresso-only version.
     *
     * @param fieldId the resource ID of the check box
     *
     * @return whether the box is checked
     *
     * @throws AssertionError if the given field is missing or is not a
     * {@link CheckBox}
     */
    private static boolean esGetCheckboxState(int fieldId) {
        final boolean[] state = new boolean[1];
        onView(withId(fieldId))
                .check(matches(isAssignableFrom(CheckBox.class)))
                .check((view, noViewFoundException) -> {
                    state[0] = ((CheckBox) view).isChecked();
                });
        return state[0];
    }

    /**
     * Return the state of the given check box.  This is the
     * non-Espresso version; the caller is responsible for obtaining
     * the check box&rsquo; {@link View}.
     *
     * @param view the view of the check box (will be checked for {@code null}
     * @param viewName the name of the check box to use for any assertion error
     * @param fieldId the resource ID of the check box for any assertion error
     *
     * @return whether the box is checked
     *
     * @throws AssertionError if the {@code view} is {@code null} or
     * is not a {@link CheckBox}.
     */
    private static boolean getCheckboxState(
            View view, String viewName, int fieldId) {
        assertNotNull(String.format(Locale.US,
                "Checkbox %s with resource ID %d was not found",
                viewName, fieldId), view);
        assertTrue(String.format(Locale.US,
                "%s with ID %d is not a CheckBox", viewName, fieldId),
                view instanceof CheckBox);
        return ((CheckBox) view).isChecked();
    }

    /**
     * Return the state of a check box.  The check box must exist
     * within the activity content.
     *
     * @param scenario the scenario in which the test is running
     * @param viewName the name of the check box to use for any assertion error
     * @param fieldId the resource ID of the check box
     *
     * @return whether the box is checked
     *
     * @throws AssertionError if the given field is missing
     */
    public static <T extends Activity> boolean getCheckboxState(
            ActivityScenario<T> scenario,
            String viewName, int fieldId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            return esGetCheckboxState(fieldId);
        } else {
            final View[] viewRef = new View[1];
            scenario.onActivity(activity -> {
                viewRef[0] = activity.findViewById(fieldId);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            return getCheckboxState(viewRef[0], viewName, fieldId);
        }
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
            return esGetCheckboxState(fieldId);
        } else {
            final Activity activity = getCurrentActivity();
            assertNotNull("No activity is running", activity);
            final View[] viewRef = new View[1];
            activity.runOnUiThread(() -> {
                viewRef[0] = activity.findViewById(fieldId);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            return getCheckboxState(viewRef[0], viewName, fieldId);
        }
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
            return esGetCheckboxState(fieldId);
        } else {
            final View[] viewRef = new View[1];
            scenario.onActivity(activity -> {
                viewRef[0] = dialog.findViewById(fieldId);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            return getCheckboxState(viewRef[0], viewName, fieldId);
        }
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
            return esGetCheckboxState(fieldId);
        } else {
            final Activity activity = getCurrentActivity();
            assertNotNull("No activity is running", activity);
            final View[] viewRef = new View[1];
            activity.runOnUiThread(() -> {
                viewRef[0] = dialog.findViewById(fieldId);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            return getCheckboxState(viewRef[0], viewName, fieldId);
        }
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
            final View[] viewRef = new View[1];
            scenario.onActivity(activity -> {
                viewRef[0] = dialog.findViewById(pickerId);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertNotNull(String.format(Locale.US,
                    "TimePicker with resource ID %d was not found",
                    pickerId), viewRef[0]);
            assertTrue(String.format(Locale.US,
                            "View with ID %d is not a TimePicker", pickerId),
                    viewRef[0] instanceof TimePicker);
            scenario.onActivity(activity -> {
                TimePicker tp = (TimePicker) viewRef[0];
                tp.setHour(hour);
                tp.setMinute(minute);
            });
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

}
