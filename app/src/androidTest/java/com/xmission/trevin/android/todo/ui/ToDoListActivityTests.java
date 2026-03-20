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
import static com.xmission.trevin.android.todo.util.RandomToDoUtils.*;
import static com.xmission.trevin.android.todo.util.ViewActionUtils.*;
import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Dialog;
import android.app.Instrumentation;
import android.content.Context;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.widget.SearchView;
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

import org.apache.commons.lang3.RandomStringUtils;
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

    private StringEncryption globalEncryption = null;

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
        globalEncryption = StringEncryption.holdGlobalEncryption();
        globalEncryption.forgetPassword();
        initializeIntents();
    }

    @After
    public void releaseRepository() {
        releaseIntents();
        globalEncryption.forgetPassword();
        StringEncryption.releaseGlobalEncryption(testContext);
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
                    ToDoListActivity.EXTRA_CATEGORY_ID, testCategory.getId());
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

    /**
     * Wait for the list adapter to be populated, then return it.
     *
     * @param scenario the scenario in which the test is running
     *
     * @return the {@link ToDoCursorAdapter}
     *
     * @throws AssertionError if the adapter is not set or is not populated
     * within 5 seconds
     */
    private ToDoCursorAdapter waitForAdapter(
            ActivityScenario<ToDoListActivity> scenario) {
        ToDoCursorAdapter[] adapter = new ToDoCursorAdapter[1];
        scenario.onActivity(activity -> {
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
        assertNotEquals("Item cursor adapter is not populated",
                0, adapter[0].getCount());
        return adapter[0];
    }

    /*
     * Test editing an existing note.  We only go as far as verifying the
     * {@link NoteEditorActivity} is started, then we cancel that activity
     * and verify that no changes were made to the database.
     */
    //Test
    public void testEditNoteThenCancel() {
        ToDoItem todo = randomToDo();
        // Make sure the item is incomplete, public, and has a note.
        todo.setChecked(false);
        todo.setPrivate(0);
        if (todo.getNote() == null)
            todo.setNote(randomParagraph());
        mockRepo.insertItem(todo);
        try (ActivityScenarioResultsWrapper<ToDoListActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(ToDoListActivity.class);
             TestObserver repoObserver = new TestObserver(mockRepo)) {
            // Step 1: Verify the note list is populated with our test item
            ToDoCursorAdapter adapter = waitForAdapter(wrapper.getScenario());
            assertEquals("Number of To Do items listed",
                    1, adapter.getCount());
            // Step 2: Verify the item has a (visible) note button
            ImageView[] noteButton = new ImageView[1];
            wrapper.onActivity(activity -> {
                View itemLine = adapter.getView(0, null, null);
                if (itemLine == null)
                    return;
                noteButton[0] = itemLine.findViewById(R.id.ToDoNoteImage);
            });
            assertNotNull("Note button not found", noteButton[0]);
            assertTrue("Note button is not visible", noteButton[0].isShown());
            // Step 3: Click the note button; wait for idle sync
            noteButton[0].performClick();
            instrument.waitForIdleSync();
            // Step 4: Verify that the NoteEditorActivity is launched,
            assertActivityLaunched(ToDoNoteActivity.class);
            //     ... and that it was provided the item ID.
            assertIntentHasLongExtra(ToDoNoteActivity.class,
                    ToDoListActivity.EXTRA_ITEM_ID, todo.getId());
            waitForFocus();
            hideKeyboard(wrapper.getScenario());
            // Step 5: Verify the note edit box contains the note
            String actualText = "";
            long timeLimit = System.nanoTime() + 5000000000L;
            while (System.nanoTime() < timeLimit) {
                instrument.waitForIdleSync();
                actualText = getElementText(
                        "Note edit box", R.id.NoteEditText);
                if (actualText.length() > 0)
                    break;
                try {
                    Thread.sleep(125);
                } catch (InterruptedException ix) {
                    // Ignore
                }
            }
            assertEquals("Note text", todo.getNote(), actualText);
            // Step 6: Press the Back button to cancel the note activity
            pressBackButton();
            // Step 7: Verify that the repository was not updated
            repoObserver.assertNotChanged(
                    "Changes were made to the repository when the note was not saved!");
        }
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
        // For this test to work, we need to be showing
        // all completed and hidden items.
        mockPrefs.initializePreference(ToDoPreferences.TPREF_SHOW_CHECKED, true);
        ToDoPreferences prefs = ToDoPreferences.getInstance(testContext);
        try (ActivityScenarioResultsWrapper<ToDoListActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(ToDoListActivity.class)) {
            ToDoCursorAdapter adapter = waitForAdapter(wrapper.getScenario());
            // By default, private To Do items should not be shown
            assertEquals("Number of To Do items listed",
                    publicToDos.size(), adapter.getCount());

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
                    testToDos.size(), adapter.getCount());

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
                    publicToDos.size(), adapter.getCount());
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

    /**
     * Obtain the text of a To Do item from the list adapter.
     *
     * @param scenario the scenario in which the test is running
     * @param adapter the adapter which provides the To Do list
     * @param itemId the ID of the To Do item whose entry to return
     *
     * @return the text of the item, or {@code null} if the list
     * does not contain an item with that {@code itemId}
     */
    private String getListItemText(
            ActivityScenario<ToDoListActivity> scenario,
            ToDoCursorAdapter adapter, long itemId) {
        String[] itemText = new String[1];
        scenario.onActivity(activity -> {
            int position = adapter.getItemPosition(itemId);
            if (adapter.getItemId(position) != itemId)
                return;
            View itemLine = adapter.getView(position, null, null);
            if (itemLine == null)
                return;
            TextView tv = itemLine.findViewById(R.id.ToDoEditDescription);
            if (tv == null)
                return;
            itemText[0] = tv.getText().toString();
        });
        return itemText[0];
    }

    /*
     * Verify that encrypted records display as &ldquo;[Locked]&rdquo; when
     * a password has not been provided; then after unlocking encrypted
     * records, their plain text should be shown.
     */
    @Test
    public void testUnlockEncryptedItems() {
        List<ToDoItem> testToDos = new ArrayList<>();
        final String password = RandomStringUtils.randomAlphanumeric(12);
        StringEncryption se = new StringEncryption();
        se.setPassword(password.toCharArray());
        se.addSalt();
        se.storePassword(mockRepo);
        boolean haveEncrypted = false;
        for (int i = RAND.nextInt(5) + 7; (i >= 0) || !haveEncrypted; --i) {
            ToDoItem todo = randomToDo();
            todo.setPrivate(RAND.nextBoolean()
                    ? StringEncryption.encryptionType() : 0);
            if (todo.isEncrypted()) {
                todo.setEncryptedDescription(se.encrypt(todo.getDescription()));
                todo.setDescription(null);
                if (todo.getNote() != null) {
                    todo.setEncryptedNote(se.encrypt(todo.getNote()));
                    todo.setNote(null);
                }
                haveEncrypted = true;
            }
            testToDos.add(mockRepo.insertItem(todo));
        }
        // For this test to work, we need to be showing
        // all completed, hidden, and private items.
        mockPrefs.initializePreference(ToDoPreferences.TPREF_SHOW_CHECKED, true);
        mockPrefs.initializePreference(ToDoPreferences.TPREF_SHOW_PRIVATE, true);
        ToDoPreferences prefs = ToDoPreferences.getInstance(testContext);
        try (ActivityScenarioResultsWrapper<ToDoListActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(ToDoListActivity.class)) {
            ToDoCursorAdapter adapter = waitForAdapter(wrapper.getScenario());
            assertEquals("Number of To Do items listed",
                    testToDos.size(), adapter.getCount());
            final String lockedText = testContext.getString(
                    R.string.PasswordProtected);
            for (ToDoItem item : testToDos) {
                String actualText = getListItemText(
                        wrapper.getScenario(), adapter, item.getId());
                assertNotNull(String.format(Locale.US,
                        "Entry for To Do item #%d was not found",
                        item.getId()), actualText);
                assertEquals(String.format(Locale.US,
                                "Text of %s item #%d (before unlock)",
                                item.isEncrypted()
                                        ? "encrypted" : "public",
                                item.getId()), item.isEncrypted()
                                ? lockedText : item.getDescription(),
                        actualText);
            }

            // Now unlock the encrypted items; we'll need to wait
            // for the next adapter data set change.
            try (TestObserver adapterObserver = new TestObserver(adapter);
                 TestPreferencesObserver prefsObserver = new TestPreferencesObserver(
                         testContext, ToDoPreferences.TPREF_SHOW_ENCRYPTED)) {
                final String unlockText = testContext.getString(
                        R.string.MenuUnlock);
                pressOptionsMenuItem(wrapper.getScenario(),
                        unlockText, R.id.menuUnlock);
                assertDialogShown(wrapper.getScenario(), unlockText);
                Dialog[] dialog = new Dialog[1];
                wrapper.onActivity(activity -> {
                    dialog[0] = activity.unlockDialog;
                });
                setEditText(wrapper.getScenario(), dialog[0], "Password",
                        R.id.EditTextPassword, password);
                pressDialogButton(wrapper.getScenario(),
                        dialog[0], android.R.id.button1);
                prefsObserver.assertChanged("The Show Encrypted preference was not changed");
                adapterObserver.assertChanged("The To Do list was not updated");
            }

            for (ToDoItem item : testToDos) {
                String actualText = getListItemText(
                        wrapper.getScenario(), adapter, item.getId());
                assertNotNull(String.format(Locale.US,
                        "Entry for To Do item #%d was not found",
                        item.getId()), actualText);
                String expectedText = item.getDescription();
                if (item.isEncrypted())
                    expectedText = se.decrypt(item.getEncryptedDescription());
                assertEquals(String.format(Locale.US,
                        "Text of %s item #%d", item.isEncrypted()
                                ? "decrypted" : "public",
                        item.getId()), expectedText, actualText);
            }

            // Lock the encrypted items back up
            try (TestObserver adapterObserver = new TestObserver(adapter);
                 TestPreferencesObserver prefsObserver = new TestPreferencesObserver(
                         testContext, ToDoPreferences.TPREF_SHOW_ENCRYPTED)) {
                final String lockText = testContext.getString(
                        R.string.MenuLock);
                pressOptionsMenuItem(wrapper.getScenario(),
                        lockText, R.id.menuUnlock);
                prefsObserver.assertChanged("The Show Encrypted preference was not changed");
                adapterObserver.assertChanged("The To Do list was not updated");
            }

            for (ToDoItem item : testToDos) {
                String actualText = getListItemText(
                        wrapper.getScenario(), adapter, item.getId());
                assertNotNull(String.format(Locale.US,
                        "Entry for To Do item #%d was not found",
                        item.getId()), actualText);
                assertEquals(String.format(Locale.US,
                                "Text of %s item #%d (after lock)",
                                item.isEncrypted()
                                        ? "encrypted" : "public",
                                item.getId()), item.isEncrypted()
                                ? lockedText : item.getDescription(),
                        actualText);
            }
        }
    }

    /**
     * Insert a given string into another string at a random position.
     *
     * @param original the original string to modify
     * @param newText the text to insert
     *
     * @return the modified string
     */
    private String insertText(String original, String newText) {
        int pos = RAND.nextInt(original.length());
        return original.substring(0, pos) + newText + original.substring(pos);
    }

    /** Test the text search filter */
    @Test
    public void testTextSearch() {
        List<ToDoItem> testToDos = new ArrayList<>();
        Map<Long,String> word1ToDos = new TreeMap<>();
        Map<Long,String> word2ToDos = new TreeMap<>();
        final String keyWord1 = randomWord();
        final String keyWord2 = randomWord();
        for (int i = RAND.nextInt(8) + 16; (i >= 0) ||
                word1ToDos.isEmpty() || word2ToDos.isEmpty(); --i) {
            ToDoItem todo = randomToDo();
            if (RAND.nextFloat() < 0.25f)
                todo.setDescription(insertText(todo.getDescription(), keyWord1));
            if (RAND.nextFloat() < 0.25f)
                todo.setDescription(insertText(todo.getDescription(), keyWord2));
            if (todo.getNote() != null) {
                if (RAND.nextFloat() < 0.25f)
                    todo.setNote(insertText(todo.getNote(), keyWord1));
                if (RAND.nextFloat() < 0.25f)
                    todo.setNote(insertText(todo.getNote(), keyWord2));
            }
            testToDos.add(mockRepo.insertItem(todo));
            if (todo.getDescription().contains(keyWord1) ||
                    ((todo.getNote() != null) &&
                            (todo.getNote().contains(keyWord1))))
                word1ToDos.put(todo.getId(), todo.getDescription());
            if (todo.getDescription().contains(keyWord2) ||
                    ((todo.getNote() != null) &&
                            (todo.getNote().contains(keyWord2))))
                word2ToDos.put(todo.getId(), todo.getDescription());
        }
        // For this test to work, we need to be showing
        // all completed, hidden, and private items.
        mockPrefs.initializePreference(ToDoPreferences.TPREF_SHOW_CHECKED, true);
        mockPrefs.initializePreference(ToDoPreferences.TPREF_SHOW_PRIVATE, true);
        try (ActivityScenarioResultsWrapper<ToDoListActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(ToDoListActivity.class)) {
            ToDoCursorAdapter adapter = waitForAdapter(wrapper.getScenario());
            // By default, all items should be shown
            assertEquals("Number of To Do items listed",
                    testToDos.size(), adapter.getCount());

            final SearchView[] searchBox = new SearchView[1];
            final MenuItem[] searchItem = new MenuItem[1];
            wrapper.onActivity(activity -> {
                searchItem[0] = activity.menu.findItem(R.id.menuSearch);
                if (searchItem[0] != null)
                    searchBox[0] = (SearchView) searchItem[0].getActionView();
            });
            assertNotNull("Search box not found", searchBox[0]);
            try (TestObserver adapterObserver = new TestObserver(adapter)) {
                // Expand the search menu item to open the search text box
                wrapper.onActivity(activity -> {
                    searchItem[0].expandActionView();
                });
                instrument.waitForIdleSync();
                assertFalse("Search box is not open",
                        searchBox[0].isIconified());
                wrapper.onActivity(activity -> {
                    searchBox[0].setQuery(keyWord1, true);
                });
                instrument.waitForIdleSync();
                adapterObserver.assertChanged(
                        "List was not updated for the first search");
            }

            // Read all of the items in the list
            Map<Long,String> actualItems = new TreeMap<>();
            wrapper.onActivity(activity -> {
                for (int i = 0; i < adapter.getCount(); i++) {
                    View itemLine = adapter.getView(i, null, null);
                    if (itemLine == null)
                        continue;
                    TextView tv = itemLine.findViewById(R.id.ToDoEditDescription);
                    if (tv == null)
                        continue;
                    actualItems.put(adapter.getItemId(i),
                            tv.getText().toString());
                }
            });
            assertEquals(String.format(Locale.US,
                    "Items listed when searching for \"%s\"", keyWord1),
                    word1ToDos, actualItems);

            try (TestObserver adapterObserver = new TestObserver(adapter)) {
                // The search box should still be open;
                // change it to the second keyword
                assertFalse("Search box has been closed",
                        searchBox[0].isIconified());
                wrapper.onActivity(activity -> {
                    searchBox[0].setQuery(keyWord2, true);
                });
                instrument.waitForIdleSync();
                adapterObserver.assertChanged(
                        "List was not updated for the second search");
            }

            actualItems.clear();
            wrapper.onActivity(activity -> {
                for (int i = 0; i < adapter.getCount(); i++) {
                    View itemLine = adapter.getView(i, null, null);
                    if (itemLine == null)
                        continue;
                    TextView tv = itemLine.findViewById(R.id.ToDoEditDescription);
                    if (tv == null)
                        continue;
                    actualItems.put(adapter.getItemId(i),
                            tv.getText().toString());
                }
            });
            assertEquals(String.format(Locale.US,
                    "Items listed when searching for \"%s\"", keyWord2),
                    word2ToDos, actualItems);

            try (TestObserver adapterObserver = new TestObserver(adapter)) {
                // Collapse the search action view to close the search box
                wrapper.onActivity(activity -> {
                    searchItem[0].collapseActionView();
                });
                instrument.waitForIdleSync();
                adapterObserver.assertChanged(
                        "List was not updated after closing the search");
            }
            assertEquals("Number of To Do items listed",
                    testToDos.size(), adapter.getCount());
        }
    }

}
