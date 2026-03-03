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
import static androidx.test.espresso.action.ViewActions.click;
import static com.xmission.trevin.android.todo.ui.CategoryFilterTests.randomCategoryName;
import static com.xmission.trevin.android.todo.util.LaunchUtils.*;
import static com.xmission.trevin.android.todo.util.RandomToDoUtils.*;
import static com.xmission.trevin.android.todo.util.ViewActionUtils.*;
import static org.hamcrest.CoreMatchers.anything;
import static org.junit.Assert.*;

import android.app.Activity;
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
import com.xmission.trevin.android.todo.data.*;
import com.xmission.trevin.android.todo.data.repeat.*;
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

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;

/**
 * Tests for the {@link ToDoDetailsActivity}
 *
 * @author Trevin Beattie
 */
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
        initializeIntents();
    }

    @After
    public void releaseRepository() {
        releaseIntents();
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
            try (TestObserver observer = new TestObserver(adapter[0])) {
                observer.assertChanged("Timed out waiting for the category"
                        + " selection drop-down to be populated");
            }
        }
        instrument.waitForIdleSync();
        return spinner[0];
    }

    /**
     * Verify that when the details form is shown for a new item,
     * the category selection drop-down is set to the category
     * passed in the intent and the rest of the fields are set
     * to their default values.
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
            hideKeyboard(scenario);
            // Get the category spinner first;
            // this call will wait for its data to be ready.
            Spinner categorySelect = getCategorySpinner(scenario);
            assertEquals("Initial description is not empty", "",
                    getElementText(scenario, "Description",
                            R.id.DetailEditTextDescription));
            assertEquals("Initial priority", "1",
                    getElementText(scenario, "Priority",
                            R.id.DetailEditTextPriority));
            long actualCategoryId = categorySelect.getSelectedItemId();
            ToDoCategory actualCategory =
                    mockRepo.getCategoryById(actualCategoryId);
            assertEquals("Selected category",
                    expectedCategory, actualCategory);
            final String notSet = testContext.getString(R.string.DetailNotset);
            assertEquals("Due date", notSet,
                    getElementText(scenario, "Due date",
                            R.id.DetailButtonDueDate));
            assertEquals("Hide until", notSet,
                    getElementText(scenario, "Hide until",
                            R.id.DetailButtonHideUntil));
            assertEquals("Alarm", notSet,
                    getElementText(scenario, "Alarm",
                            R.id.DetailButtonAlarm));
            assertEquals("Repeat", notSet,
                    getElementText(scenario, "Repeat",
                            R.id.DetailButtonRepeat));
            assertFalse("Private", getCheckboxState(scenario,
                    "Private", R.id.DetailCheckBoxPrivate));
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

        try (ActivityScenario<ToDoDetailsActivity> scenario =
                ActivityScenario.launch(intent)) {
            hideKeyboard(scenario);
            // Step 1: Check the default category first;
            // this call will wait for its data to be ready.
            Spinner categorySelect = getCategorySpinner(scenario);
            assertEquals("Selected category ID", ToDoCategory.UNFILED,
                    categorySelect.getSelectedItemId());

            // Step 2: Fill in our custom details
            setEditText(scenario, "Description",
                    R.id.DetailEditTextDescription, expectedDescription);
            // The "OK" button should be enabled now that the description is set
            assertButtonShown(scenario, "OK", R.id.DetailButtonOK);
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

            try (TestObserver saveObserver = new TestObserver(mockRepo)) {
                // Step 4: Save the item; we're going to wait for this change.
                pressButton(scenario, R.id.DetailButtonOK);
                // Step 5: Verify the saved item
                saveObserver.assertChanged(
                        "Timed out waiting for the item to be saved");
            }
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

    /**
     * Verify that when the details form is shown for an existing item,
     * it’s populated with that item&rsquo;s data from the repository.
     */
    @Test
    public void testEditItem() {
        for (int i = RAND.nextInt(3) + 3; i >= 0; --i)
            mockRepo.insertCategory(randomCategoryName('A', 'Z'));
        ToDoCategory expectedCategory = mockRepo.insertCategory(
                randomCategoryName('A', 'Z'));
        ToDoItem newItem = randomToDo();
        newItem.setCategoryId(expectedCategory.getId());
        newItem.setCategoryName(expectedCategory.getName());
        if (newItem.getDue() == null)
            newItem.setDue(LocalDate.now().plusDays(RAND.nextInt(366)));
        if (newItem.getHideDaysEarlier() == null)
            newItem.setHideDaysEarlier(RAND.nextInt(31));
        newItem.setAlarm(randomAlarm());
        RepeatDaily repeat = new RepeatDaily();
        repeat.setIncrement(RAND.nextInt(20) + 1);
        repeat.setEnd(newItem.getDue().plusDays(repeat.getIncrement() *
                (RAND.nextInt(12) + 1)));
        newItem.setRepeatInterval(repeat);
        newItem = mockRepo.insertItem(newItem);
        Intent intent = new Intent(testContext, ToDoDetailsActivity.class);
        intent.putExtra(ToDoListActivity.EXTRA_CATEGORY_ID,
                expectedCategory.getId());
        intent.putExtra(ToDoListActivity.EXTRA_ITEM_ID, newItem.getId());
        try (ActivityScenario<ToDoDetailsActivity> scenario =
                ActivityScenario.launch(intent)) {
            hideKeyboard(scenario);
            // Get the category spinner first;
            // this call will wait for its data to be ready.
            Spinner categorySelect = getCategorySpinner(scenario);
            assertEquals("Description", newItem.getDescription(),
                    getElementText(scenario, "Description",
                            R.id.DetailEditTextDescription));
            assertEquals("Priority", Integer.toString(newItem.getPriority()),
                    getElementText(scenario, "Priority",
                            R.id.DetailEditTextPriority));
            long actualCategoryId = categorySelect.getSelectedItemId();
            ToDoCategory actualCategory =
                    mockRepo.getCategoryById(actualCategoryId);
            assertEquals("Selected category",
                    expectedCategory, actualCategory);
            // The date text should be localized
            String expectedButtonText = newItem.getDue().format(
                    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM));
            assertEquals("Due date", expectedButtonText,
                    getElementText(scenario, "Due date",
                            R.id.DetailButtonDueDate));
            // The "hide until" text should be pluralized
            expectedButtonText = testContext.getResources().getQuantityString(
                    R.plurals.DetailTextDaysEarlier,
                    newItem.getHideDaysEarlier(),
                    newItem.getHideDaysEarlier());
            assertEquals("Hide until", expectedButtonText,
                    getElementText(scenario, "Hide until",
                            R.id.DetailButtonHideUntil));
            // The "Alarm" text should indicate the "days earlier" field
            expectedButtonText = testContext.getResources().getQuantityString(
                    R.plurals.DetailTextDaysEarlier,
                    newItem.getAlarm().getAlarmDaysEarlier(),
                    newItem.getAlarm().getAlarmDaysEarlier());
            assertEquals("Alarm", expectedButtonText,
                    getElementText(scenario, "Alarm",
                            R.id.DetailButtonAlarm));
            // The "Repeat" text has multiple possibilities
            // depending on the repeat type, which is one
            // reason we set up a specific repeat interval.
            expectedButtonText = testContext.getString(
                    R.string.RepeatDailyUntilWhen, repeat.getEnd().format(
                            DateTimeFormatter.ofLocalizedDate(
                                    FormatStyle.SHORT)));
            assertEquals("Repeat", expectedButtonText,
                    getElementText(scenario, "Repeat",
                            R.id.DetailButtonRepeat));
            assertEquals("Private", newItem.isPrivate(),
                    getCheckboxState(scenario, "Private",
                            R.id.DetailCheckBoxPrivate));
        }
    }

    /**
     * Verify the details form retains its state for an existing item in the
     * middle of being edited when the activity is restarted due to a
     * configuration change (e.g. screen rotation, keyboard (a/de)tachment).
     * When the item is saved, everything entered in the form before rotation
     * should be saved.
     */
    @Test
    public void testRestoreFormEditItem() {
        List<ToDoCategory> testCategories = new ArrayList<>();
        for (int i = RAND.nextInt(3) + 3; i >= 0; --i) {
            ToDoCategory category = mockRepo.insertCategory(
                    randomCategoryName('A', 'Z'));
            testCategories.add(category);
        }
        ToDoItem newItem = randomToDo();
        ToDoCategory category = testCategories.get(
                RAND.nextInt(testCategories.size()));
        newItem.setCategoryId(category.getId());
        newItem.setCategoryName(category.getName());
        if (newItem.getDue() == null)
            newItem.setDue(LocalDate.now().plusDays(RAND.nextInt(366)));
        if (newItem.getHideDaysEarlier() == null)
            newItem.setHideDaysEarlier(RAND.nextInt(31));
        newItem.setAlarm(randomAlarm());
        RepeatDaily repeat = new RepeatDaily();
        repeat.setIncrement(RAND.nextInt(20) + 1);
        repeat.setEnd(newItem.getDue().plusDays(repeat.getIncrement() *
                (RAND.nextInt(12) + 1)));
        newItem.setRepeatInterval(repeat);
        newItem = mockRepo.insertItem(newItem);

        final String expectedDescription = randomSentence();
        int expectedPriority;
        do {
            expectedPriority = RAND.nextInt(9) + 2;
        } while (expectedPriority == newItem.getPriority());
        ToDoCategory expectedCategoryCandidate;
        do {
            expectedCategoryCandidate = testCategories.get(
                    RAND.nextInt(testCategories.size()));
        } while (expectedCategoryCandidate == category);
        final ToDoCategory expectedCategory = expectedCategoryCandidate;
        // We're going to clear all of the other fields by clearing the due date.
        final boolean expectPrivate = !newItem.isPrivate();

        Intent intent = new Intent(testContext, ToDoDetailsActivity.class);
        intent.putExtra(ToDoListActivity.EXTRA_CATEGORY_ID,
                category.getId());
        intent.putExtra(ToDoListActivity.EXTRA_ITEM_ID, newItem.getId());
        try (ActivityScenario<ToDoDetailsActivity> scenario =
                ActivityScenario.launch(intent)) {
            hideKeyboard(scenario);
            // Step 1: Check the initial category first;
            // this call will wait for its data to be ready.
            Spinner categorySelect = getCategorySpinner(scenario);
            assertEquals("Selected category ID (before changes)", category,
                    mockRepo.getCategoryById(categorySelect.getSelectedItemId()));

            // Step 2: Edit the details
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
            // Press the "No Date" item; this should be the one
            // immediately following the formatted dates for the next week.
            int noDatePosition = testContext.getResources()
                    .getStringArray(R.array.DueDateFormatList).length;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
                onData(anything()).atPosition(noDatePosition).perform(click());
            } else {
                scenario.onActivity(activity -> {
                    AlertDialog dialog = (AlertDialog) activity.dueDateListDialog;
                    ListView listView = dialog.getListView();
                    ListAdapter listAdapter = listView.getAdapter();
                    View itemView = listAdapter.getView(noDatePosition,
                            null, listView);
                    listView.performItemClick(itemView, noDatePosition,
                            listAdapter.getItemId(noDatePosition));
                });
            }
            pressButton(R.id.DetailCheckBoxPrivate);
            assertButtonShown(scenario, "OK", R.id.DetailButtonOK);

            // Step 3: Restart the activity
            scenario.recreate();

            try (TestObserver saveObserver = new TestObserver(mockRepo)) {
                // Step 4: Save the item; we're going to wait for this change.
                pressButton(scenario, R.id.DetailButtonOK);
                // Step 5: Verify the saved item
                saveObserver.assertChanged(
                        "Timed out waiting for the item to be saved");
            }
        }

        ToDoItem updatedItem = mockRepo.getItemById(newItem.getId());
        assertNotNull("The To Do item was deleted!", updatedItem);
        assertEquals("Updated item description",
                expectedDescription, updatedItem.getDescription());
        assertEquals("Updated item priority",
                expectedPriority, updatedItem.getPriority());
        assertEquals("Updated item category", expectedCategory,
                mockRepo.getCategoryById(updatedItem.getCategoryId()));
        assertEquals("Updated item private",
                expectPrivate, updatedItem.isPrivate());
        assertNull("Updated item due date", updatedItem.getDue());
        assertNull("Updated item hide", updatedItem.getHideDaysEarlier());
        assertNull("Updated item alarm", updatedItem.getAlarm());
        assertNull("Updated item repeat", updatedItem.getRepeatInterval());
    }

    /**
     * Test adding a note to a new item.  The note isn&rsquo;t visible
     * in the details activity until we save the item.
     */
    @Test
    public void testNewItemNote() {
        final String expectedNote = randomParagraph();
        Intent intent = new Intent(testContext, ToDoDetailsActivity.class);
        intent.putExtra(ToDoListActivity.EXTRA_CATEGORY_ID, ToDoCategory.UNFILED);

        try (ActivityScenario<ToDoDetailsActivity> scenario =
                ActivityScenario.launch(intent)) {
            hideKeyboard(scenario);
            // Get the category spinner first;
            // this call will wait for its data to be ready.
            Spinner categorySelect = getCategorySpinner(scenario);
            assertButtonShown(scenario, "Note", R.id.DetailButtonNote);
            String description = randomSentence();
            setEditText(scenario, "Description",
                    R.id.DetailEditTextDescription, description);
            // The "OK" button should be enabled now that the description is set
            assertButtonShown(scenario, "OK", R.id.DetailButtonOK);
            pressButton(scenario, R.id.DetailButtonNote);
            assertActivityLaunched(ToDoNoteActivity.class);
            assertIntentDoesNotHaveExtra(ToDoNoteActivity.class,
                    ToDoListActivity.EXTRA_ITEM_ID);
            assertIntentHasStringExtra(ToDoNoteActivity.class,
                    ToDoNoteActivity.EXTRA_ITEM_DESCRIPTION, description);
            assertButtonShown("OK", R.id.NoteButtonOK);
            setEditText(scenario, "Note", R.id.NoteEditText, expectedNote);

            try (TestObserver saveObserver = new TestObserver(mockRepo)) {
                // Save everything;
                pressButton(R.id.NoteButtonOK);
                saveObserver.assertNotChanged(
                        "Note was saved before the item was created!");
                // we're going to wait for this change.
                pressButton(scenario, R.id.DetailButtonOK);
                // Step 5: Verify the saved item
                saveObserver.assertChanged(
                        "Timed out waiting for the item to be saved");
            }
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
        assertEquals("Saved item note", expectedNote, newItem.getNote());
    }

    /**
     * Test adding a note to a new item when the note activity is
     * restarted in the middle of writing the note.
     */
    @Test
    public void testRestoreNewItemNote() {
        final String expectedNote = randomParagraph();
        Intent intent = new Intent(testContext, ToDoDetailsActivity.class);
        intent.putExtra(ToDoListActivity.EXTRA_CATEGORY_ID, ToDoCategory.UNFILED);

        try (ActivityScenario<ToDoDetailsActivity> scenario =
                ActivityScenario.launch(intent)) {
            hideKeyboard(scenario);
            // Get the category spinner first;
            // this call will wait for its data to be ready.
            Spinner categorySelect = getCategorySpinner(scenario);
            assertButtonShown(scenario, "Note", R.id.DetailButtonNote);
            String description = randomSentence();
            setEditText(scenario, "Description",
                    R.id.DetailEditTextDescription, description);
            // The "OK" button should be enabled now that the description is set
            assertButtonShown(scenario, "OK", R.id.DetailButtonOK);
            pressButton(scenario, R.id.DetailButtonNote);
            assertActivityLaunched(ToDoNoteActivity.class);
            assertIntentDoesNotHaveExtra(ToDoNoteActivity.class,
                    ToDoListActivity.EXTRA_ITEM_ID);
            assertIntentHasStringExtra(ToDoNoteActivity.class,
                    ToDoNoteActivity.EXTRA_ITEM_DESCRIPTION, description);
            assertButtonShown("OK", R.id.NoteButtonOK);
            setEditText(scenario, "Note", R.id.NoteEditText, expectedNote);

            // Restart the current activity at this point
            // (not the launch activity)
            final Activity noteActivity = getCurrentActivity();
            instrument.runOnMainSync(() -> noteActivity.recreate());

            // Now finish the note and the item;
            try (TestObserver saveObserver = new TestObserver(mockRepo)) {
                pressButton(R.id.NoteButtonOK);
                saveObserver.assertNotChanged(
                        "Note was saved before the item was created!");
                // we're going to wait for this change.
                pressButton(scenario, R.id.DetailButtonOK);
                // Step 5: Verify the saved item
                saveObserver.assertChanged(
                        "Timed out waiting for the item to be saved");
            }
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
        assertEquals("Saved item note", expectedNote, newItem.getNote());
    }

    /**
     * Test adding a note to an item being edited.  The note <i>must not</i>
     * be saved to the repository until the details activity has saved the
     * whole item.
     */
    @Test
    public void testEditItemNote() {
        ToDoItem newItem = randomToDo();
        final String oldNote = randomParagraph();
        newItem.setNote(oldNote);
        newItem = mockRepo.insertItem(newItem);
        final String expectedNote = randomParagraph();
        Intent intent = new Intent(testContext, ToDoDetailsActivity.class);
        intent.putExtra(ToDoListActivity.EXTRA_CATEGORY_ID,
                newItem.getCategoryId());
        intent.putExtra(ToDoListActivity.EXTRA_ITEM_ID, newItem.getId());

        try (ActivityScenario<ToDoDetailsActivity> scenario =
                ActivityScenario.launch(intent)) {
            hideKeyboard(scenario);
            // Get the category spinner first;
            // this call will wait for its data to be ready.
            Spinner categorySelect = getCategorySpinner(scenario);
            assertButtonShown(scenario, "Note", R.id.DetailButtonNote);
            assertButtonShown(scenario, "OK", R.id.DetailButtonOK);
            pressButton(scenario, R.id.DetailButtonNote);
            assertActivityLaunched(ToDoNoteActivity.class);
            assertIntentHasLongExtra(ToDoNoteActivity.class,
                    ToDoListActivity.EXTRA_ITEM_ID, newItem.getId());
            assertIntentHasStringExtra(ToDoNoteActivity.class,
                    ToDoNoteActivity.EXTRA_ITEM_DESCRIPTION,
                    newItem.getDescription());
            assertIntentHasStringExtra(ToDoNoteActivity.class,
                    ToDoNoteActivity.EXTRA_ITEM_NOTE, oldNote);
            assertButtonShown("OK", R.id.NoteButtonOK);
            setEditText(scenario, "Note", R.id.NoteEditText, expectedNote);

            // Save everything;
            try (TestObserver saveObserver = new TestObserver(mockRepo)) {
                pressButton(R.id.NoteButtonOK);
                saveObserver.assertNotChanged(
                        "Note was saved before the item!");
                // we're going to wait for this change.
                pressButton(scenario, R.id.DetailButtonOK);
                // Step 5: Verify the saved item
                saveObserver.assertChanged(
                        "Timed out waiting for the item to be saved");
            }
        }
        ToDoItem updatedItem = mockRepo.getItemById(newItem.getId());
        assertNotNull("The To Do item was deleted!", updatedItem);
        assertEquals("Updated item note", expectedNote, updatedItem.getNote());
    }

    /**
     * Test adding a note to an item being edited when the note activity is
     * restarted in the middle of writing the note.  The note <i>must not</i>
     * be saved to the repository until the details activity has saved the
     * whole item.
     */
    @Test
    public void testRestoreEditItemNote() {
        ToDoItem newItem = randomToDo();
        final String oldNote = randomParagraph();
        newItem.setNote(oldNote);
        newItem = mockRepo.insertItem(newItem);
        final String expectedNote = randomParagraph();
        Intent intent = new Intent(testContext, ToDoDetailsActivity.class);
        intent.putExtra(ToDoListActivity.EXTRA_CATEGORY_ID,
                newItem.getCategoryId());
        intent.putExtra(ToDoListActivity.EXTRA_ITEM_ID, newItem.getId());

        try (ActivityScenario<ToDoDetailsActivity> scenario =
                ActivityScenario.launch(intent)) {
            hideKeyboard(scenario);
            // Get the category spinner first;
            // this call will wait for its data to be ready.
            Spinner categorySelect = getCategorySpinner(scenario);
            assertButtonShown(scenario, "Note", R.id.DetailButtonNote);
            assertButtonShown(scenario, "OK", R.id.DetailButtonOK);
            pressButton(scenario, R.id.DetailButtonNote);
            assertActivityLaunched(ToDoNoteActivity.class);
            assertIntentHasLongExtra(ToDoNoteActivity.class,
                    ToDoListActivity.EXTRA_ITEM_ID, newItem.getId());
            assertIntentHasStringExtra(ToDoNoteActivity.class,
                    ToDoNoteActivity.EXTRA_ITEM_DESCRIPTION,
                    newItem.getDescription());
            assertIntentHasStringExtra(ToDoNoteActivity.class,
                    ToDoNoteActivity.EXTRA_ITEM_NOTE, oldNote);
            assertButtonShown("OK", R.id.NoteButtonOK);
            setEditText(scenario, "Note", R.id.NoteEditText, expectedNote);

            // Restart the current activity at this point
            // (not the launch activity)
            final Activity noteActivity = getCurrentActivity();
            instrument.runOnMainSync(() -> noteActivity.recreate());

            // Now finish the note and the item;
            try (TestObserver saveObserver = new TestObserver(mockRepo)) {
                pressButton(R.id.NoteButtonOK);
                saveObserver.assertNotChanged(
                        "Note was saved before the item!");
                // we're going to wait for this change.
                pressButton(scenario, R.id.DetailButtonOK);
                // Step 5: Verify the saved item
                saveObserver.assertChanged(
                        "Timed out waiting for the item to be saved");
            }
        }
        ToDoItem updatedItem = mockRepo.getItemById(newItem.getId());
        assertNotNull("The To Do item was deleted!", updatedItem);
        assertEquals("Updated item note", expectedNote, updatedItem.getNote());
    }

    /**
     * Test deleting a note from an item being edited.  When the user
     * clicks the &ldquo;Delete&rdquo; button, we expect a confirmation
     * dialog to show.  The note <i>must not</i> be removed in the
     * repository until the details activity has saved the whole item.
     */
    @Test
    public void testDeleteItemNote() {
        ToDoItem newItem = randomToDo();
        final String oldNote = randomParagraph();
        newItem.setNote(oldNote);
        newItem = mockRepo.insertItem(newItem);
        Intent intent = new Intent(testContext, ToDoDetailsActivity.class);
        intent.putExtra(ToDoListActivity.EXTRA_CATEGORY_ID,
                newItem.getCategoryId());
        intent.putExtra(ToDoListActivity.EXTRA_ITEM_ID, newItem.getId());

        try (ActivityScenario<ToDoDetailsActivity> scenario =
                ActivityScenario.launch(intent)) {
            hideKeyboard(scenario);
            // Get the category spinner first;
            // this call will wait for its data to be ready.
            Spinner categorySelect = getCategorySpinner(scenario);
            assertButtonShown(scenario, "Note", R.id.DetailButtonNote);
            assertButtonShown(scenario, "OK", R.id.DetailButtonOK);
            pressButton(scenario, R.id.DetailButtonNote);
            assertActivityLaunched(ToDoNoteActivity.class);
            assertIntentHasLongExtra(ToDoNoteActivity.class,
                    ToDoListActivity.EXTRA_ITEM_ID, newItem.getId());
            assertIntentHasStringExtra(ToDoNoteActivity.class,
                    ToDoNoteActivity.EXTRA_ITEM_DESCRIPTION,
                    newItem.getDescription());
            assertIntentHasStringExtra(ToDoNoteActivity.class,
                    ToDoNoteActivity.EXTRA_ITEM_NOTE, oldNote);
            assertButtonShown("Delete", R.id.NoteButtonDelete);

            try (TestObserver saveObserver = new TestObserver(mockRepo)) {
                pressButton(R.id.NoteButtonDelete);
                assertAlertDialogShown(scenario, testContext
                        .getString(R.string.ConfirmationTextDeleteNote));
                pressAlertDialogButton(scenario, android.R.id.button1,
                        testContext.getString(R.string.ConfirmationButtonOK));
                saveObserver.assertNotChanged(
                        "Note was deleted before the item was saved!");
                // we're going to wait for this change.
                pressButton(scenario, R.id.DetailButtonOK);
                // Step 5: Verify the saved item
                saveObserver.assertChanged(
                        "Timed out waiting for the item to be saved");
            }
        }
        ToDoItem updatedItem = mockRepo.getItemById(newItem.getId());
        assertNotNull("The To Do item was deleted!", updatedItem);
        assertNull("Updated item note", updatedItem.getNote());
    }

}
