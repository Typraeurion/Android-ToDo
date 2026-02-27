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
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import static org.hamcrest.core.AllOf.allOf;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Build;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;

import java.util.Locale;

/**
 * Common action methods for UI tests
 */
public class ViewActionUtils {

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

}
