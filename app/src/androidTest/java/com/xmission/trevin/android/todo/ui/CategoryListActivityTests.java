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
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static com.xmission.trevin.android.todo.ui.CategoryEditorTests.CATEGORY_NAME_COMPARATOR;
import static com.xmission.trevin.android.todo.ui.CategoryFilterTests.randomCategoryName;
import static com.xmission.trevin.android.todo.ui.FocusAction.requestFocus;
import static org.hamcrest.CoreMatchers.anything;
import static org.hamcrest.core.AllOf.allOf;
import static org.junit.Assert.*;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Build;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.xmission.trevin.android.todo.R;
import com.xmission.trevin.android.todo.data.ToDoCategory;
import com.xmission.trevin.android.todo.provider.MockDataChangedObserver;
import com.xmission.trevin.android.todo.provider.MockToDoRepository;
import com.xmission.trevin.android.todo.provider.ToDoRepositoryImpl;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests for the {@link CategoryListActivity}
 *
 * @author Trevin Beattie
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class CategoryListActivityTests {

    static Instrumentation instrument = null;

    static Context testContext = null;

    static final Random RAND = new Random();

    private static MockToDoRepository mockRepo = null;

    /**
     * Container for items that need to be preserved between lambda
     * function calls for either Espresso or the instrumentation Scenario.
     */
    private static class CategoryListViewData {
        // The number of items in the list
        int count = -1;
        // The parent list view
        ListView list = null;
        // The index of the item we are using
        int index = 0;
        // The item we are using
        EditText item;
        // The content of the widget
        String text;

        /**
         * Set the index of the item to use.  This allows
         * negative positions in which case it will count
         * from the end of the list.
         *
         * @param position the position of the item
         *
         * @throws AssertionError if {@code position} is greater than
         * or equal to the list size, or if {@code position} is negative
         * and {@code -position} is greater than the list size.
         */
        void setIndex(int position) {
            int minSize = (position < 0) ? -position : (position + 1);
            assertTrue(String.format(Locale.US,
                    "Category list size expected at least %d but was %d",
                    minSize, count), count >= minSize);
            index = (position >= 0) ? position : count + position;
        }
    }

    @BeforeClass
    public static void getTestContext() {
        instrument = InstrumentationRegistry.getInstrumentation();
        testContext = instrument.getTargetContext();
        mockRepo = MockToDoRepository.getInstance();
        ToDoRepositoryImpl.setInstance(mockRepo);
    }

    @Before
    public void initializeRepository() {
        mockRepo.open(testContext);
        mockRepo.clear();
    }

    @After
    public void releaseRepository() {
        mockRepo.release(testContext);
    }

    /**
     * Verify that the category ListView contains the given number of items.
     *
     * @param scenario the scenario in which the test is running
     * @param expectedCount the number of items that should be in the list
     *
     * @throws AssertionError if the ListView is missing or not empty
     */
    private static void assertCategoryListSize(
            ActivityScenario<CategoryListActivity> scenario,
            int expectedCount) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            onView(withId(R.id.CategoryList))
                    .check(matches(hasChildCount(expectedCount)));
        } else {
            scenario.onActivity(activity -> {
                ListView list = activity.findViewById(R.id.CategoryList);
                assertNotNull("Category list is missing", list);
                assertEquals("Category list size",
                        expectedCount, list.getChildCount());
            });
        }
    }

    /**
     * Return the content of an item in the category list.
     *
     * @param scenario the scenario in which the test is running
     * @param position the position of the item to check.
     * If negative, counts back from the end of the list.
     *
     * @return the text of the item at {@code position}
     *
     * @throws AssertionError if the item is missing
     */
    private static String getCategoryText(
            ActivityScenario<CategoryListActivity> scenario, int position) {
        final CategoryListViewData data = new CategoryListViewData();

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            // Get the actual size of the list
            onView(withId(R.id.CategoryList))
                    .check((view, noViewFoundException) -> {
                        if (view instanceof ListView) {
                            data.list = (ListView) view;
                        }
                    });
        } else {
            scenario.onActivity(activity -> {
                data.list = activity.findViewById(R.id.CategoryList);
            });
        }

        assertNotNull("Category list is missing", data.list);
        data.count = data.list.getChildCount();
        data.setIndex(position);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            onData(anything())
                    .inAdapterView(withId(R.id.CategoryList))
                    .atPosition(data.index)
                    .check(matches(isAssignableFrom(EditText.class)))
                    .check((view, noViewFoundException) -> {
                        data.text = ((EditText) view).getText().toString();
                    });
        } else {
            scenario.onActivity(activity -> {
                data.list.setSelection(data.index);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                data.item = (EditText) data.list.getChildAt(
                        data.index - data.list.getFirstVisiblePosition());
                data.text = data.item.getText().toString();
            });
        }

        return data.text;
    }

    /**
     * Verify the content of an item in the category list.
     *
     * @param scenario the scenario in which the test is running
     * @param position the position of the item to check.
     * If negative, counts back from the end of the list.
     * @param expectedText the expected text of the item
     *
     * @throws AssertionError if the item is missing or has the wrong text
     */
    private static void assertCategoryTextEquals(
            ActivityScenario<CategoryListActivity> scenario,
            int position, String expectedText) {
        assertEquals(String.format(Locale.US,
                "List item text at position %d", position),
                expectedText, getCategoryText(scenario, position));
    }

    /**
     * Change the content of an item in the category list.
     *
     * @param scenario the scenario in which the test is running
     * @param position the position of the item to check.
     * If negative, counts back from the end of the list.
     * @param newText the text to enter in the field
     */
    private static void setCategoryText(
            ActivityScenario<CategoryListActivity> scenario,
            int position, String newText) {
        final CategoryListViewData data = new CategoryListViewData();

        // Get the actual size of the list
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            onView(withId(R.id.CategoryList))
                    .check((view, noViewFoundException) -> {
                        if (view instanceof ListView) {
                            data.list = (ListView) view;
                        }
                    });
        } else {
            scenario.onActivity(activity -> {
                data.list = activity.findViewById(R.id.CategoryList);
            });
        }

        assertNotNull("Category list is missing", data.list);
        data.count = data.list.getChildCount();
        data.setIndex(position);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            onData(anything())
                    .inAdapterView(withId(R.id.CategoryList))
                    .atPosition(data.index)
                    .check(matches(isAssignableFrom(EditText.class)))
                    .perform(requestFocus())
                    .perform(replaceText(newText));
        } else {
            scenario.onActivity(activity -> {
                data.list.setSelection(data.index);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                data.item = (EditText) data.list.getChildAt(
                        data.index - data.list.getFirstVisiblePosition());
                data.item.requestFocus();
                /*
                 * Setting the text MUST be done in the same block
                 * as requesting focus, otherwise the widget will
                 * _lose_ focus before our next scenario action.
                 */
                data.item.setText(newText);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        }
    }

    /**
     * Verify that a given button is present (and visible).
     *
     * @param scenario the scenario in which the test is running
     * @param buttonName the name of the button
     * @param buttonId the resource ID of the button to check
     *
     * @throws AssertionError if the button is missing or not visible
     */
    // To Do: Move this to a common test utility class
    private static void assertButtonShown(
            ActivityScenario<CategoryListActivity> scenario,
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
     * Click the designated button.  The test should have already
     * verified that the button exists and is enabled.
     *
     * @param scenario the scenario in which the test is running
     * @param buttonName the name of the button
     * @param buttonId the resource ID of the button to click
     */
    // To Do: Move this to a common test utility class
    private static void pressButton(
            ActivityScenario<CategoryListActivity> scenario,
            String buttonName, int buttonId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            onView(withId(buttonId)).perform(click());
        } else {
            scenario.onActivity(activity -> {
                Button button = activity.findViewById(buttonId);
                button.performClick();
            });
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    /**
     * Press the OK or Cancel button to finish the activity.
     * This may need to wait for a period of time while the activity
     * performs any required background task to save data to the repository.
     * If the &ldquo;Cancel&rdquo; button is pressed, the activity
     * shouldn&rsquo;t change any data so this will wait the maximum
     * amount of time for the data set observer to time out.
     *
     * @param scenario the scenario in which the test is running
     * @param ok whether to press the &ldquo;OK&rdquo; button ({@code true})
     * or &ldquo;Cancel&rdquo; ({@code false}).
     *
     * @throws AssertionError if the activity does not finish
     */
    private static void finishActivity(
            ActivityScenario<CategoryListActivity> scenario,
            boolean ok) {
        final MockDataChangedObserver observer =
                new MockDataChangedObserver(mockRepo);
        mockRepo.registerDataSetObserver(observer);
        pressButton(scenario, ok ? "OK" : "Cancel", ok ?
                R.id.CategoryListButtonOK : R.id.CategoryListButtonCancel);
        try {
            observer.await(ok);
            Lifecycle.State state = scenario.getState();
            long timeLimit = System.nanoTime() + 1000000000L;
            while ((scenario.getState() != Lifecycle.State.DESTROYED) &&
                    System.nanoTime() < timeLimit)
                Thread.sleep(100);
            if (scenario.getState() == Lifecycle.State.DESTROYED)
                return;
            scenario.onActivity(activity -> {
                assertTrue(String.format(Locale.US,
                        "Activity is not finishing; state is %s",
                        state), activity.isFinishing());
            });
        } catch (IllegalStateException | NullPointerException e) {
            assertEquals("Final Activity state",
                    Lifecycle.State.DESTROYED, scenario.getState());
        } catch (InterruptedException e) {
            fail("Interrupted while waiting for the activity to finish");
        }
    }

    /**
     * Test adding a category to an empty list
     */
    @Test
    public void testAddFirstCategory() {
        String expectedCategory = randomCategoryName('A', 'Z');
        try (ActivityScenario<CategoryListActivity> scenario =
                        ActivityScenario.launch(CategoryListActivity.class)) {
            // Step 1: Verify the category ListView is empty
            assertCategoryListSize(scenario, 0);
            // Step 2: Verify the "New" button exists
            assertButtonShown(scenario, "New", R.id.CategoryListButtonNew);
            // Step 3: Verify the "OK" button exists
            assertButtonShown(scenario, "OK", R.id.CategoryListButtonOK);
            // Step 4: Click the "New" button; wait for idle sync
            pressButton(scenario, "OK", R.id.CategoryListButtonNew);
            // Step 5: Verify the ListView contains one EditText item,
            //         and that the text field is empty
            assertCategoryTextEquals(scenario, -1, "");
            // Step 6: Set the EditText field to a random category name
            setCategoryText(scenario, -1, expectedCategory);
            // Step 7: Click the "OK" button; wait for the activity to finish
            finishActivity(scenario, true);
        }
        // Step 8: Verify the mock repository contains the new category
        //         (having the provided name with any ID)
        List<ToDoCategory> allCategories = mockRepo.getCategories();
        assertEquals("Total number of categories in the repository",
                2, allCategories.size());
        for (ToDoCategory category : allCategories) {
            // SKip the "Unfiled" category
            if (category.getId() == ToDoCategory.UNFILED)
                continue;
            assertEquals("New category name", expectedCategory,
                    category.getName());
        }
    }

    /**
     * Test adding a new entry to the category list but then
     * pressing &ldquo;Cancel&rdquo; instead of saving it.
     */
    @Test
    public void testCancelFirstCategory() {
        String unexpectedCategory = randomCategoryName('A', 'Z');
        try (ActivityScenario<CategoryListActivity> scenario =
                        ActivityScenario.launch(CategoryListActivity.class)) {
            // Step 1: Verify the category ListView is empty
            assertCategoryListSize(scenario, 0);
            // Step 2: Verify the "New" button exists
            assertButtonShown(scenario, "New", R.id.CategoryListButtonNew);
            // Step 3: Verify the "Cancel" button exists
            assertButtonShown(scenario, "Cancel", R.id.CategoryListButtonCancel);
            // Step 4: Click the "New" button; wait for idle sync
            pressButton(scenario, "New", R.id.CategoryListButtonNew);
            // Step 5: Verify the ListView contains one EditText item,
            //         and that the text field is empty
            assertCategoryTextEquals(scenario, -1, "");
            // Step 6: Set the EditText field to a random category name
            setCategoryText(scenario, -1, unexpectedCategory);
            // Step 7: Click the "Cancel" button; wait for the activity to finish
            finishActivity(scenario, false);
        }
        // Step 8: Verify the mock repository only contains
        //         the "Unfiled" category
        List<ToDoCategory> allCategories = mockRepo.getCategories();
        ToDoCategory unfiledCategory = new ToDoCategory();
        unfiledCategory.setId(ToDoCategory.UNFILED);
        unfiledCategory.setName(testContext.getString(
                R.string.Category_Unfiled));
        assertEquals("Categories in the repository",
                Collections.singletonList(unfiledCategory), allCategories);
    }

    /**
     * Test removing a category by erasing its text.
     * In this case there are some other categories left.
     */
    @Test
    public void testRemoveCategory() {
        // Start with setting up some test categories
        List<ToDoCategory> testCategories = new ArrayList<>();
        int targetCount = RAND.nextInt(3) + 3;
        for (int i = 0; i < targetCount; i++) {
            ToDoCategory category = mockRepo.insertCategory(
                    randomCategoryName('A', 'Z'));
            testCategories.add(category);
        }
        try (ActivityScenario<CategoryListActivity> scenario =
                        ActivityScenario.launch(CategoryListActivity.class)) {
            // Step 1: Verify the category ListView has the right number of entries
            assertCategoryListSize(scenario, targetCount);
            // Step 2: Verify the "OK" button exists
            assertButtonShown(scenario, "OK", R.id.CategoryListButtonOK);
            // Step 4: Choose a victim and erase its text
            int victimLine = RAND.nextInt(targetCount);
            String victimText = getCategoryText(scenario, victimLine);
            setCategoryText(scenario, victimLine, "");
            // Step 5: Click the "OK" button; wait for the activity to finish
            finishActivity(scenario, true);
            // Step 6: Verify the mock repository no longer contains
            //         the target category.  (But does contain "Unfiled".)
            Iterator<ToDoCategory> iter = testCategories.iterator();
            while (iter.hasNext()) {
                ToDoCategory category = iter.next();
                if (category.getName().equals(victimText))
                    iter.remove();
            }
        }
        testCategories.add(mockRepo.getCategoryById(ToDoCategory.UNFILED));
        Collections.sort(testCategories, CATEGORY_NAME_COMPARATOR);
        List<ToDoCategory> allCategories = mockRepo.getCategories();
        assertEquals("Categories in the repository",
                testCategories, allCategories);
    }

    /**
     * Test removing the last category by erasing its text.
     */
    @Test
    public void testRemoveLastCategory() {
        ToDoCategory victim = mockRepo.insertCategory(
                randomCategoryName('A', 'Z'));
        ToDoCategory unfiledCategory = new ToDoCategory();
        unfiledCategory.setId(ToDoCategory.UNFILED);
        unfiledCategory.setName(testContext.getString(
                R.string.Category_Unfiled));
        try (ActivityScenario<CategoryListActivity> scenario =
                        ActivityScenario.launch(CategoryListActivity.class)) {
            // Step 1: Verify the category ListView has a single entry
            assertCategoryListSize(scenario, 1);
            // Step 2: Verify the entry is for the victim category
            assertCategoryTextEquals(scenario, 0, victim.getName());
            // Step 3: Verify the "OK" button exists
            assertButtonShown(scenario, "OK", R.id.CategoryListButtonOK);
            // Step 4: Erase the text
            setCategoryText(scenario, 0, "");
            // Step 5: Click the "OK" button; wait for the activity to finish
            finishActivity(scenario, true);
        }
        // Step 6: Verify the mock repository only contains the "Unfiled" category
        List<ToDoCategory> allCategories = mockRepo.getCategories();
        assertEquals("Categories in the repository",
                Collections.singletonList(unfiledCategory), allCategories);
    }

}
