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

import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.xmission.trevin.android.todo.R;
import com.xmission.trevin.android.todo.data.ToDoCategory;
import com.xmission.trevin.android.todo.data.ToDoPreferences;
import com.xmission.trevin.android.todo.provider.MockToDoRepository;
import com.xmission.trevin.android.todo.provider.ToDoRepositoryTests.TestObserver;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.*;

/**
 * Tests for the {@link CategoryFilterAdapter}
 * as used by {@link ToDoListActivity}.
 *
 * @author Trevin Beattie
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class CategoryFilterTests {

    static Context testContext = null;

    static MockToDoRepository repository = null;

    static final Random RAND = new Random();

    @BeforeClass
    public static void initializeRepository() {
        testContext = InstrumentationRegistry.getInstrumentation()
                .getTargetContext();
        repository = (MockToDoRepository) MockToDoRepository.getInstance();
        repository.open(testContext);
    }

    @Before
    public void clearData() {
        repository.clear();
    }

    @AfterClass
    public static void releaseRepository() {
        repository.release(testContext);
    }

    /**
     * When using a clean database, the category filter should contain
     * three entries: &ldquo;All&rdquo;, &ldquo;Unfiled&rdquo;, and
     * &ldquo;Edit categories&hellip;&rdquo;.
     */
    @Test
    public void testDefaultFilterSet() {
        CategoryFilterAdapter adapter =
                new CategoryFilterAdapter(testContext, repository);
        assertEquals(3, adapter.getCount());

        ToDoCategory expectedCategory = new ToDoCategory();
        expectedCategory.setId(ToDoPreferences.ALL_CATEGORIES);
        expectedCategory.setName(testContext.getString(R.string.Category_All));
        assertEquals("All categories entry", expectedCategory,
                adapter.getItem(0));

        expectedCategory.setId(ToDoCategory.UNFILED);
        expectedCategory.setName(testContext.getString(R.string.Category_Unfiled));
        assertEquals("Unfiled category entry", expectedCategory,
                adapter.getItem(1));

        expectedCategory.setId(ToDoPreferences.ALL_CATEGORIES - 1);
        expectedCategory.setName(testContext.getString(R.string.Category_Edit));
        assertEquals("Edit categories entry", expectedCategory,
                adapter.getItem(2));
    }

    /**
     * Generate a random category name.  The first letter of the name will be
     * in the range specified by the arguments; the rest will be lower case.
     *
     * @param minLetter the earliest the first letter may be
     * @param maxLetter the latest the first letter may be
     *
     * @return the category name
     */
    public static String randomCategoryName(char minLetter, char maxLetter) {
        char firstLetter = (char) (minLetter +
                RAND.nextInt(maxLetter - minLetter + 1));
        int targetLen = RAND.nextInt(6) + 5;
        String rest = RandomStringUtils.randomAlphabetic(targetLen)
                .toLowerCase(Locale.US);
        return firstLetter + rest;
    }

    /**
     * When the database contains user-defined categories, the category
     * filter should contain these categories in alphabetical order
     * between the &ldquo;All&rdquo; and &ldquo;Edit categories&hellip;&rdquo;
     * entries, with &ldquo;Unfiled&rdquo; in the proper order.
     */
    @Test
    public void testUserCategories() {
        CategoryFilterAdapter adapter =
                new CategoryFilterAdapter(testContext, repository);

        List<ToDoCategory> testCategories = new ArrayList<>();
        ToDoCategory expectedCategory = new ToDoCategory();
        expectedCategory.setId(ToDoCategory.UNFILED);
        expectedCategory.setName(
                testContext.getString(R.string.Category_Unfiled));
        testCategories.add(expectedCategory);

        expectedCategory = repository.insertCategory(
                randomCategoryName('A', 'T'));
        testCategories.add(expectedCategory);

        expectedCategory =
                repository.insertCategory(randomCategoryName('V', 'Z'));
        testCategories.add(expectedCategory);

        expectedCategory =
                repository.insertCategory(randomCategoryName('A', 'Z'));
        testCategories.add(expectedCategory);

        Collections.sort(testCategories, new Comparator<ToDoCategory>() {
            @Override
            public int compare(ToDoCategory c1, ToDoCategory c2) {
                String s1 = c1.getName() == null ? "" : c1.getName();
                String s2 = c2.getName() == null ? "" : c2.getName();
                return s1.compareTo(s2);
            }
        });

        assertEquals(testCategories.size() + 2, adapter.getCount());

        expectedCategory = new ToDoCategory();
        expectedCategory.setId(ToDoPreferences.ALL_CATEGORIES);
        expectedCategory.setName(testContext.getString(R.string.Category_All));
        assertEquals("All categories entry", expectedCategory,
                adapter.getItem(0));

        for (int i = 0; i < testCategories.size(); i++) {
            expectedCategory.setId(testCategories.get(i).getId());
            expectedCategory.setName(testCategories.get(i).getName());
            assertEquals("Database category entry for position " + (i + 1),
                    testCategories.get(i), adapter.getItem(i + 1));
        }

        expectedCategory.setId(ToDoPreferences.ALL_CATEGORIES - 1);
        expectedCategory.setName(testContext.getString(R.string.Category_Edit));
        assertEquals("Edit category entry", expectedCategory,
                adapter.getItem(testCategories.size() + 1));
    }

    /**
     * When a new category is added to the database, any
     * data observers on the adapter should be notified.
     */
    @Test
    public void testObserveInsertCategory() {
        CategoryFilterAdapter adapter =
                new CategoryFilterAdapter(testContext, repository);
        TestObserver observer = new TestObserver();
        adapter.registerDataSetObserver(observer);
        repository.insertCategory(randomCategoryName('A', 'Z'));
        observer.assertChanged();
    }

    /**
     * When an existing category is modified in the database,
     * any data observers on the adapter should be notified.
     */
    @Test
    public void testObserveUpdateCategory() {
        CategoryFilterAdapter adapter =
                new CategoryFilterAdapter(testContext, repository);
        ToDoCategory userCategory = repository.insertCategory(
                randomCategoryName('A', 'Z'));
        TestObserver observer = new TestObserver();
        adapter.registerDataSetObserver(observer);
        repository.updateCategory(userCategory.getId(),
                randomCategoryName('A', 'Z'));
        observer.assertChanged();
    }

    /**
     * When a category is deleted from the database, any
     * data observers on the adapter should be notified.
     */
    @Test
    public void testObserveDeleteCategory() {
        CategoryFilterAdapter adapter =
                new CategoryFilterAdapter(testContext, repository);
        ToDoCategory userCategory = repository.insertCategory(
                randomCategoryName('A', 'Z'));
        TestObserver observer = new TestObserver();
        adapter.registerDataSetObserver(observer);
        repository.deleteCategory(userCategory.getId());
        observer.assertChanged();
    }

}
