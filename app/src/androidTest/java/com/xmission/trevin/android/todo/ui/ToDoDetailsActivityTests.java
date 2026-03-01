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

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static com.xmission.trevin.android.todo.ui.CategoryFilterTests.randomCategoryName;
import static com.xmission.trevin.android.todo.util.RandomToDoUtils.randomSentence;
import static com.xmission.trevin.android.todo.util.ViewActionUtils.*;
import static org.hamcrest.CoreMatchers.anything;
import static org.junit.Assert.*;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Spinner;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.xmission.trevin.android.todo.R;
import com.xmission.trevin.android.todo.data.MockSharedPreferences;
import com.xmission.trevin.android.todo.data.ToDoAlarm;
import com.xmission.trevin.android.todo.data.ToDoCategory;
import com.xmission.trevin.android.todo.data.ToDoItem;
import com.xmission.trevin.android.todo.data.ToDoPreferences;
import com.xmission.trevin.android.todo.data.repeat.RepeatInterval;
import com.xmission.trevin.android.todo.data.repeat.RepeatMonthlyOnDate;
import com.xmission.trevin.android.todo.data.repeat.RepeatSemiMonthlyOnDates;
import com.xmission.trevin.android.todo.data.repeat.RepeatWeekly;
import com.xmission.trevin.android.todo.data.repeat.RepeatYearlyOnDate;
import com.xmission.trevin.android.todo.provider.MockToDoRepository;
import com.xmission.trevin.android.todo.provider.TestObserver;
import com.xmission.trevin.android.todo.provider.ToDoCursor;
import com.xmission.trevin.android.todo.provider.ToDoRepositoryImpl;
import com.xmission.trevin.android.todo.provider.ToDoSchema.ToDoItemColumns;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Random;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class ToDoDetailsActivityTests {

    static final Instrumentation instrument =
            InstrumentationRegistry.getInstrumentation();
    static Context testContext = null;

    static final Random RAND = new Random();

    private static MockSharedPreferences sharedPrefs = null;
    private static MockToDoRepository mockRepo = null;

    @BeforeClass
    public static void getTestContext() {
        testContext = instrument.getTargetContext();
        sharedPrefs = new MockSharedPreferences();
        ToDoPreferences.setSharedPreferences(sharedPrefs);
        mockRepo = MockToDoRepository.getInstance();
        ToDoRepositoryImpl.setInstance(mockRepo);
    }

    @Before
    public void initializeRepository() {
        sharedPrefs.resetMock();
        // Initialize the time zone to UTC for these tests
        sharedPrefs.initializePreference(ToDoPreferences
                .TPREF_LOCAL_TIME_ZONE, false);
        sharedPrefs.initializePreference(ToDoPreferences
                .TPREF_FIXED_TIME_ZONE, ZoneOffset.UTC.getId());
        mockRepo.open(testContext);
        mockRepo.clear();
    }

    @After
    public void releaseRepository() {
        mockRepo.release(testContext);
    }

    /**
     * Check that the category selection drop-down has been populated,
     * waiting for it if necessary (up to 1 second).
     *
     * @param scenario the scenario in which the test is running
     *
     * @return the category selection drop-down element
     */
    private static Spinner getCategorySpinner(
            ActivityScenario<ToDoDetailsActivity> scenario) {
        Spinner[] spinner = new Spinner[1];
        CategorySelectAdapter[] adapter = new CategorySelectAdapter[1];
        scenario.onActivity(activity -> {
            spinner[0] = (Spinner) activity.findViewById(
                    R.id.DetailSpinnerCategory);
            assertNotNull("Category drop-down was not found", spinner[0]);
            adapter[0] = (CategorySelectAdapter) spinner[0].getAdapter();
        });
        // Step 2: If the adapter hasn't been populated yet,
        //         we have to wait for a data update...
        //         and then for the activity to change the selection.
        if (adapter[0].isEmpty()) {
            TestObserver observer = new TestObserver();
            try {
                adapter[0].registerDataSetObserver(observer);
                observer.assertChanged("Timed out waiting for the category"
                        + " selection drop-down to be populated");
            } finally {
                adapter[0].unregisterDataSetObserver(observer);
            }
        }
        instrument.waitForIdleSync();
        return spinner[0];
    }

    /**
     * Verify that when the details form is shown for a new item,
     * the category selection drop-down is set to the category
     * passed in the intent.
     */
    @Test
    public void testNewItemDefaultCategory() {
        for (int i = RAND.nextInt(3) + 3; i >= 0; --i)
            mockRepo.insertCategory(randomCategoryName('A', 'Z'));
        ToDoCategory expectedCategory = mockRepo.insertCategory(
                randomCategoryName('A', 'Z'));
        Intent intent = new Intent(testContext, ToDoDetailsActivity.class);
        intent.putExtra(ToDoListActivity.EXTRA_CATEGORY_ID,
                expectedCategory.getId());
        try (ActivityScenario<ToDoDetailsActivity> scenario =
                ActivityScenario.launch(intent)) {
            Spinner categorySelect = getCategorySpinner(scenario);
            long actualCategoryId = categorySelect.getSelectedItemId();
            ToDoCategory actualCategory =
                    mockRepo.getCategoryById(actualCategoryId);
            assertEquals("Selected category",
                    expectedCategory, actualCategory);
        }
    }

    /**
     * Verify the details form retains its state for a new item
     * when the activity is restarted due to a configuration change
     * (e.g. screen rotation, keyboard (a/de)tachment).  When the
     * item is saved, everything entered in the form before rotation
     * should be saved.
     */
    @Test
    public void testRestoreFormNewItem() {
        final String expectedDescription = randomSentence();
        final int expectedPriority = RAND.nextInt(9) + 2;
        for (int i = RAND.nextInt(3) + 3; i >= 0; --i)
            mockRepo.insertCategory(randomCategoryName('A', 'Z'));
        final ToDoCategory expectedCategory = mockRepo.insertCategory(
                randomCategoryName('A', 'Z'));
        Intent intent = new Intent(testContext, ToDoDetailsActivity.class);
        intent.putExtra(ToDoListActivity.EXTRA_CATEGORY_ID,
                ToDoCategory.UNFILED);
        final int dueDaysOffset = RAND.nextInt(8);
        LocalDate expectedDue = LocalDate.now().plusDays(dueDaysOffset);
        final int expectedHide = RAND.nextInt(100);
        final int expectedAlarmAdvance = RAND.nextInt(100);
        final LocalTime expectedAlarmTime = LocalTime.of(
                RAND.nextInt(24), RAND.nextInt(60));
        // There are four repeat intervals on the initial pop-up list
        // which don't require going through another dialog.
        final int repeatTypeOffset = RAND.nextInt(4) + 2;
        RepeatInterval expectedRepeat = null;
        switch (repeatTypeOffset) {
            case 2: expectedRepeat = new RepeatWeekly(expectedDue); break;
            case 3: expectedRepeat = new RepeatSemiMonthlyOnDates(expectedDue); break;
            case 4: expectedRepeat = new RepeatMonthlyOnDate(expectedDue); break;
            case 5: expectedRepeat = new RepeatYearlyOnDate(expectedDue); break;
            default: fail("Unexpected repeat type offset; should be 2-5 but was "
                    + repeatTypeOffset);
        }

        TestObserver saveObserver = new TestObserver();
        try (ActivityScenario<ToDoDetailsActivity> scenario =
                ActivityScenario.launch(intent)) {
            // Step 1: Check the default field values
            assertEquals("Initial description is not empty", "",
                    getElementText(scenario, "Description",
                            R.id.DetailEditTextDescription));
            assertEquals("Initial priority", "1",
                    getElementText(scenario, "Priority",
                            R.id.DetailEditTextPriority));
            Spinner categorySelect = getCategorySpinner(scenario);
            assertEquals("Selected category ID", ToDoCategory.UNFILED,
                    categorySelect.getSelectedItemId());
            assertEquals("Due date",
                    testContext.getString(R.string.DetailNotset),
                    getElementText(scenario, "Due date",
                            R.id.DetailButtonDueDate));
            assertFalse("Private", getCheckboxState(scenario,
                    "Private", R.id.DetailCheckBoxPrivate));

            // Step 2: Fill in our custom details
            setEditText(scenario, "Description",
                    R.id.DetailEditTextDescription, expectedDescription);
            setEditText(scenario, "Priority",
                    R.id.DetailEditTextPriority,
                    Integer.toString(expectedPriority));
            final CategorySelectAdapter categoryAdapter = (CategorySelectAdapter)
                    categorySelect.getAdapter();
            scenario.onActivity(activity -> {
                categorySelect.setSelection(categoryAdapter
                        .getCategoryPosition(expectedCategory.getId()));
            });
            pressButton(R.id.DetailButtonDueDate);
            // Press the `dueDaysOffset'th item in the pop-up list
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
                onData(anything()).atPosition(dueDaysOffset).perform(click());
            } else {
                scenario.onActivity(activity -> {
                    AlertDialog dialog = (AlertDialog) activity.dueDateListDialog;
                    ListView listView = dialog.getListView();
                    ListAdapter listAdapter = listView.getAdapter();
                    View itemView = listAdapter.getView(dueDaysOffset,
                            null, listView);
                    listView.performItemClick(itemView, dueDaysOffset,
                            listAdapter.getItemId(dueDaysOffset));
                });
            }
            // Having a due date should display and enable the
            // "Hide until", "Alarm", and "Repeat" buttons.
            pressButton(scenario, R.id.DetailButtonHideUntil);
            pressButton(R.id.HideCheckBox);
            setEditText("Hide days earlier", R.id.HideEditDaysEarlier,
                    Integer.toString(expectedHide));
            pressButton(R.id.HideButtonOK);
            pressButton(scenario, R.id.DetailButtonAlarm);
            pressButton(R.id.AlarmCheckBox);
            setEditText("Alarm days earlier", R.id.AlarmEditDaysEarlier,
                    Integer.toString(expectedAlarmAdvance));
            final Dialog[] alarmDialog = new Dialog[1];
            scenario.onActivity(activity -> {
                alarmDialog[0] = activity.alarmDialog;
            });
            setTime(scenario, alarmDialog[0], R.id.AlarmTimePicker,
                    expectedAlarmTime.getHour(), expectedAlarmTime.getMinute());
            pressButton(R.id.AlarmButtonOK);
            /*
             * Setting the alarm may trigger an alert dialog to ask the user
             * to grant notification permission.  If present, we need to
             * dismiss this dialog.  Dismissing the system permissions diaog
             * may in turn cause our activity to display its rationale
             * dialog, which also needs to be dismissed.
             */
            for (int i = 0; i < 2; i++) {
                final boolean[] hasFocus = { true };
                // Give the activity a brief moment to react
                try { Thread.sleep(500); }
                catch (InterruptedException ie) {}
                instrument.waitForIdleSync();
                scenario.onActivity(activity -> {
                    hasFocus[0] = activity.hasWindowFocus();
                });
                if (hasFocus[0]) break;
                instrument.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            }
            pressButton(scenario, R.id.DetailButtonRepeat);
            // Press the `repeatTypeOffset'th item in the pop-up list
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
                onData(anything()).atPosition(repeatTypeOffset).perform(click());
            } else {
                scenario.onActivity(activity -> {
                    AlertDialog dialog = (AlertDialog) activity.repeatListDialog;
                    ListView listView = dialog.getListView();
                    ListAdapter listAdapter = listView.getAdapter();
                    View itemView = listAdapter.getView(repeatTypeOffset,
                            null, listView);
                    listView.performItemClick(itemView, repeatTypeOffset,
                            listAdapter.getItemId(repeatTypeOffset));
                });
            }
            pressButton(scenario, R.id.DetailCheckBoxPrivate);

            // Step 3: Restart the activity
            scenario.recreate();

            // Step 4: Save the item; we're going to wait for this change.
            mockRepo.registerDataSetObserver(saveObserver);
            pressButton(scenario, R.id.DetailButtonOK);
        }

        try {
            // Step 5: Verify the saved item
            saveObserver.assertChanged(
                    "Timed out waiting for the item to be saved");
        } finally {
            mockRepo.unregisterDataSetObserver(saveObserver);
        }
        // The trick here is we don't know the new item ID yet.
        // It should be the last ID in the repository though.
        ToDoItem newItem = null;
        ToDoCursor cursor = mockRepo.getItems(
                ToDoPreferences.ALL_CATEGORIES, true, LocalDate.now(),
                true, true, ToDoRepositoryImpl.TODO_TABLE_NAME
                        + "." + ToDoItemColumns._ID + " desc");
        if (cursor.moveToNext())
            newItem = cursor.getItem();
        cursor.close();
        assertNotNull("No item was saved", newItem);
        assertEquals("Saved item description",
                expectedDescription, newItem.getDescription());
        assertEquals("Saved item priority",
                expectedPriority, newItem.getPriority());
        ToDoCategory actualCategory =
                mockRepo.getCategoryById(newItem.getCategoryId());
        assertEquals("Saved item category",
                expectedCategory, actualCategory);
        assertTrue("Saved item is private", newItem.isPrivate());
        assertEquals("Saved item due date", expectedDue, newItem.getDue());
        assertEquals("Saved item hide until", Integer.valueOf(expectedHide),
                newItem.getHideDaysEarlier());
        ToDoAlarm expectedAlarm = new ToDoAlarm();
        expectedAlarm.setTime(expectedAlarmTime);
        expectedAlarm.setAlarmDaysEarlier(expectedAlarmAdvance);
        assertEquals("Saved item alarm", expectedAlarm, newItem.getAlarm());
        assertEquals("Saved item repeat", expectedRepeat,
                newItem.getRepeatInterval());
    }

}
