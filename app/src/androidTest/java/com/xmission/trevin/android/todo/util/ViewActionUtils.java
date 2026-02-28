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
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import static org.hamcrest.core.AllOf.allOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Activity;
import android.app.Dialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.intent.Intents;
import androidx.test.espresso.intent.matcher.IntentMatchers;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import java.util.Collection;
import java.util.Locale;

/**
 * Common action methods for UI tests
 */
public class ViewActionUtils {

    /**
     * Get the current activity.  This is meant for tests that have
     * one activity launch another and need to interact with the
     * child activity.  Be aware that any interactions with this
     * activity <i>must</i> be done on the activity&rsquo;s UI
     * thread, not the test thread!
     *
     * @return the current activity.  May be {@code null} if no
     * activity has been found in the &ldquo;resumed&rdquo; state.
     */
    @Nullable
    public static Activity getCurrentActivity() {
        final Activity[] currentActivity = new Activity[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                new Runnable() {
                    @Override
                    public void run() {
                        Collection<Activity> activities =
                                ActivityLifecycleMonitorRegistry.getInstance()
                                        .getActivitiesInStage(Stage.RESUMED);
                        if (!activities.isEmpty()) {
                            currentActivity[0] = activities.iterator().next();
                        }
                    }
                });
        return currentActivity[0];
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
                            isDisplayed(),
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
                            isDisplayed(),
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
                            isDisplayed(),
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
                            isDisplayed(),
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
     * Verify that an activity has been launched.  For versions of
     * Android up to (35), this uses Espresso&rsquo;s {@link Intents}
     * framework; for Baklava and up, we have to look at the current
     * activity so the caller <i>must</i> ensure it has called the
     * instrumentation&rsquo;s {@link Instrumentation#waitForIdleSync()}
     * before this.  (The {@link #pressButton} methods do this before
     * returning.)
     *
     * @param expectedActivityClass the class of the activity that should
     * have been launched.
     */
    public static void assertActivityLaunched(
            Class<? extends Activity> expectedActivityClass) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            Intents.intended(hasComponent(expectedActivityClass.getName()));
        } else {
            Activity currentActivity = getCurrentActivity();
            assertNotNull("No activity is running", currentActivity);
            assertEquals("Running activity", expectedActivityClass,
                    currentActivity.getClass());
        }
    }

    /**
     * Verify that the current activity&rsquo;s {@link Intent} has extra
     * data with the given key.
     *
     * @param key the extra key that should be in the {@link Intent}
     */
    public static void assertIntentHasExtra(String key) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            Intents.intended(IntentMatchers.hasExtraWithKey(key));
        } else {
            Activity currentActivity = getCurrentActivity();
            assertNotNull("No activity is running", currentActivity);
            Intent intent = currentActivity.getIntent();
            assertTrue(String.format(Locale.US,
                    "Intent has no extra with key %s", key),
                    intent.hasExtra(key));
        }
    }

    /**
     * Verify that the current activity&rsquo;s {@link Intent} does
     * <i>not</i> have extra data with the given key.
     *
     * @param key the extra key that should be in the {@link Intent}
     */
    public static void assertIntentDoesNotHaveExtra(String key) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            Intents.intended(IntentMatchers.doesNotHaveExtraWithKey(key));
        } else {
            Activity currentActivity = getCurrentActivity();
            assertNotNull("No activity is running", currentActivity);
            Intent intent = currentActivity.getIntent();
            if (intent.hasExtra(key)) {
                Bundle extras = intent.getExtras();
                Object value = extras.get(key);
                String type = (value == null) ? "null"
                        : value.getClass().getSimpleName();
                fail(String.format(Locale.US,
                        "Intent has %s extra with key % and value:%s",
                        type, key, value));
            }
        }
    }

}
