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

import static com.xmission.trevin.android.todo.ui.CategoryFilterTests.randomCategoryName;
import static com.xmission.trevin.android.todo.util.LaunchUtils.*;
import static com.xmission.trevin.android.todo.util.RandomToDoUtils.randomToDo;
import static com.xmission.trevin.android.todo.util.ViewActionUtils.*;
import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.view.Menu;
import android.view.MenuItem;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.xmission.trevin.android.todo.R;
import com.xmission.trevin.android.todo.data.MockSharedPreferences;
import com.xmission.trevin.android.todo.data.ToDoCategory;
import com.xmission.trevin.android.todo.data.ToDoItem;
import com.xmission.trevin.android.todo.data.ToDoPreferences;
import com.xmission.trevin.android.todo.provider.MockToDoRepository;
import com.xmission.trevin.android.todo.provider.TestObserver;
import com.xmission.trevin.android.todo.provider.TestPreferencesObserver;
import com.xmission.trevin.android.todo.provider.ToDoRepositoryImpl;
import com.xmission.trevin.android.todo.util.ActivityScenarioResultsWrapper;
import com.xmission.trevin.android.todo.util.StringEncryption;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.*;

/**
 * Tests for the {@link ToDoListActivity}
 *
 * @author Trevin Beattie
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class ToDoListActivityTests {

    static Context testContext = null;

    static final Random RAND = new Random();

    private static Instrumentation instrument = null;

    private static MockSharedPreferences mockPrefs = null;

    private static MockToDoRepository mockRepo = null;

    @BeforeClass
    public static void getTestContext() {
        instrument = InstrumentationRegistry.getInstrumentation();
        testContext = instrument.getTargetContext();
        mockPrefs = MockSharedPreferences.getInstance();
        ToDoPreferences.setSharedPreferences(mockPrefs);
        mockRepo = MockToDoRepository.getInstance();
        ToDoRepositoryImpl.setInstance(mockRepo);
    }

    @Before
    public void initializeRepository() {
        mockRepo.open(testContext);
        mockRepo.clear();
        mockPrefs.resetMock();
        initializeIntents();
    }

    @After
    public void releaseRepository() {
        releaseIntents();
        mockRepo.release(testContext);
    }

    /**
     * Test creating a new To Do item.  We only go as far as verifying
     * the ToDoDetailsActivity is started, then we cancel that activity
     * and verify that no changes were made to the database.
     */
    @Test
    public void testNewThenCancel() {
        ToDoCategory testCategory = mockRepo.insertCategory(
                randomCategoryName('A', 'T'));
        try (ActivityScenarioResultsWrapper<ToDoListActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(ToDoListActivity.class);
             TestObserver repoObserver = new TestObserver(mockRepo);
             TestPreferencesObserver prefsObserver =
                     new TestPreferencesObserver(testContext,
                             ToDoPreferences.TPREF_SELECTED_CATEGORY)) {
            // Step 1: Verify the category drop-down and "New" button exist
            assertSpinnerShown(wrapper.getScenario(), "Category filter",
                    R.id.ListSpinnerCategory);
            assertButtonShown(wrapper.getScenario(), "New", R.id.ListButtonNew);
            // Step 2: Select the test category
            selectFromSpinner(wrapper.getScenario(),
                    R.id.ListSpinnerCategory, 1);
            prefsObserver.assertChanged(
                    "The selected category was not saved to preferences");
            assertEquals("Selected category ID saved to preferences",
                    testCategory.getId(), mockPrefs.getPreference(
                            ToDoPreferences.TPREF_SELECTED_CATEGORY));
            // Step 3: Click the "New" button; wait for idle sync
            pressButton(wrapper.getScenario(), R.id.ListButtonNew);
            // Step 4: Verify the ToDoDetailsActivity is launched,
            assertActivityLaunched(ToDoDetailsActivity.class);
            //     ... that it was provided a category ID,
            assertIntentHasLongExtra(ToDoDetailsActivity.class,
                    ToDoListActivity.EXTRA_CATEGORY_ID, ToDoCategory.UNFILED);
            //     ... and that it was NOT given an item ID.
            assertIntentDoesNotHaveExtra(ToDoDetailsActivity.class,
                    ToDoListActivity.EXTRA_ITEM_ID);
            waitForFocus();
            hideKeyboard(wrapper.getScenario());
            // Step 5: Verify the "Cancel" button exists in the details activity
            assertButtonShown("Cancel", R.id.DetailButtonCancel);
            // Step 6: Click the "Cancel" button; wait for the activity to finish
            pressButton(R.id.DetailButtonCancel);
            // Finished with the UI at this point
            // Step 7: Verify that nothing was added to the repository
            repoObserver.assertNotChanged(
                    "Changes were made to the repository when no item was saved!");
        }
    }

    /*
     * To Do: Test editing an existing note.  We only go as far as verifying the
     * {@link NoteEditorActivity} is started, then we cancel that activity
     * and verify that no changes were made to the database.
     */
    //Test
    public void testEditThenCancel() {
        fail("Not yet implemented");
    }

    /**
     * Verify that private records are hidden when the &ldquo;Show
     * private records&rdquo; preference is off, and then displayed
     * when this preference is turned on.
     */
    @Test
    public void testToggleShowPrivate() {
        List<ToDoItem> testToDos = new ArrayList<>();
        List<ToDoItem> publicToDos = new ArrayList<>();
        List<ToDoItem> privateToDos = new ArrayList<>();
        for (int i = RAND.nextInt(5) + 7; (i >= 0)
             || publicToDos.isEmpty() || privateToDos.isEmpty(); --i) {
            ToDoItem todo = randomToDo();
            todo.setPrivate(RAND.nextBoolean()
                    ? StringEncryption.NO_ENCRYPTION : 0);
            testToDos.add(mockRepo.insertItem(todo));
            if (todo.isPrivate())
                privateToDos.add(todo);
            else
                publicToDos.add(todo);
        }
        ToDoPreferences prefs = ToDoPreferences.getInstance(testContext);
        try (ActivityScenarioResultsWrapper<ToDoListActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(ToDoListActivity.class)) {
            ToDoCursorAdapter[] adapter = new ToDoCursorAdapter[1];
            wrapper.onActivity(activity -> {
                adapter[0] = activity.itemAdapter;
            });
            assertNotNull("Item cursor adapter has not been set",
                    adapter[0]);
            // Wait if necessary for the adapter to be populated
            if (adapter[0].getCount() <= 0) {
                long timeLimit = System.nanoTime() + 5000000000L;
                try (TestObserver adapterObserver = new TestObserver(adapter[0])) {
                    while (System.nanoTime() < timeLimit) {
                        adapterObserver.waitToClear();
                        if (adapter[0].getCount() > 0)
                            break;
                        adapterObserver.reset();
                    }
                }
            }
            // By default, private notes should not be shown
            assertEquals("Number of To Do items listed",
                    publicToDos.size(), adapter[0].getCount());

            // Now toggle the "Show private" flag, and wait for the callback
            try (TestPreferencesObserver prefsObserver =
                    new TestPreferencesObserver(testContext,
                            ToDoPreferences.TPREF_SHOW_PRIVATE)) {
                prefs.setShowPrivate(true);
                prefsObserver.assertChanged("Show private preference was not changed");
            }
            // It may take a little more time
            // for the adapter to get a new cursor...
            try {
                Thread.sleep(250);
            } catch (InterruptedException ix) {
                // Ignore
            }
            instrument.waitForIdleSync();
            assertEquals("Number of To Do items listed",
                    testToDos.size(), adapter[0].getCount());

            // Do it again in reverse
            try (TestPreferencesObserver prefsObserver =
                    new TestPreferencesObserver(testContext,
                            ToDoPreferences.TPREF_SHOW_PRIVATE)) {
                prefs.setShowPrivate(false);
                prefsObserver.assertChanged("Show private preference was not changed");
            }
            // It may take a little more time
            // for the adapter to get a new cursor...
            try {
                Thread.sleep(250);
            } catch (InterruptedException ix) {
                // Ignore
            }
            instrument.waitForIdleSync();
            assertEquals("Number of To Do items listed",
                    publicToDos.size(), adapter[0].getCount());
        }
    }

    /**
     * Open the activity&rsquo;s options menu, verify that a specific
     * item is shown, and select it.
     *
     * @param scenario the scenario in which the test is running
     * @param expectedText the expected text of the menu item
     * @param itemId the resource ID of the menu item to select
     *
     * @throws AssertionError if the options menu could not be opened,
     * if it does not contain the given item, if the item is not visible,
     * if the item text does not match the expected label, or if the
     * item is disabled.
     */
    static void pressOptionsMenuItem(
            ActivityScenario<ToDoListActivity> scenario,
            String expectedText, int itemId) {
        scenario.onActivity(Activity::openOptionsMenu);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        final Menu[] menuRef = new Menu[1];
        scenario.onActivity(activity -> {
            menuRef[0] = activity.menu;
        });
        assertNotNull("The options menu has not been set", menuRef[0]);
        final MenuItem[] itemRef = new MenuItem[1];
        final boolean[] visibility = new boolean[1];
        final String[] itemText = new String[1];
        scenario.onActivity(activity -> {
            itemRef[0] = menuRef[0].findItem(itemId);
            if (itemRef[0] == null)
                return;
            visibility[0] = itemRef[0].isVisible();
            itemText[0] = itemRef[0].getTitle().toString();
        });
        assertNotNull(String.format(Locale.US,
                "Menu item \"%s\" with resource ID %d is missing",
                expectedText, itemId), itemRef[0]);
        assertTrue(String.format(Locale.US,
                "Menu item \"%s\" with resource ID %d is not visible",
                expectedText, itemId), visibility[0]);
        assertEquals(String.format(Locale.US,
                "Text of menu item #%d", itemId), expectedText, itemText[0]);
        scenario.onActivity(activity -> {
            activity.onOptionsItemSelected(itemRef[0]);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    /*
     * To Do: Verify that encrypted records display as &ldquo;[Locked]&rdquo; when
     * a password has not been provided; then after unlocking encrypted
     * records, their plain text should be shown.
     */
    //Test
    public void testUnlockEncryptedItems() {
        fail("Not yet implemented");
    }

}
