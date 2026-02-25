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
import static org.junit.Assert.*;

import android.app.Instrumentation;
import android.content.Context;
import android.widget.EditText;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.xmission.trevin.android.todo.data.ToDoCategory;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.*;

/**
 * Tests for the {@link CategoryEditorAdapter}
 * as used by {@link CategoryListActivity}
 *
 * @author Trevin Beattie
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class CategoryEditorTests {

    static Instrumentation instrument = null;

    static Context testContext = null;

    static final Random RAND = new Random();

    /** Compare categories by name (case-insensitive) */
    public static final Comparator<ToDoCategory> CATEGORY_NAME_COMPARATOR
            = new Comparator<ToDoCategory>() {
        @Override
        public int compare(ToDoCategory c1, ToDoCategory c2) {
            return c1.getName().compareTo(c2.getName());
        }
    };

    /**
     * Generate a list of random category names.
     *
     * @return the categories in alphabetical order
     */
    public static List<ToDoCategory> getRandomCategoryList() {
        List<ToDoCategory> categories = new ArrayList<>();
        for (int i = RAND.nextInt(4) + 4; i >= 0; --i) {
            ToDoCategory category = new ToDoCategory();
            category.setId(categories.size() + 1);
            category.setName(randomCategoryName('A', 'Z'));
            categories.add(category);
        }
        Collections.sort(categories, CATEGORY_NAME_COMPARATOR);
        return categories;
    }

    @BeforeClass
    public static void getTestContext() {
        instrument = InstrumentationRegistry.getInstrumentation();
        testContext = instrument.getTargetContext();
    }

    private static void runOnUiAndWait(Runnable runnable) {
        instrument.runOnMainSync(runnable);
        instrument.waitForIdleSync();
    }

    @Test
    public void testEditCategory() {
        final List<ToDoCategory> categories = getRandomCategoryList();
        final CategoryEditorAdapter adapter =
                new CategoryEditorAdapter(testContext, categories);
        final int targetCategory = RAND.nextInt(adapter.getCount());
        final EditText textBox = (EditText) adapter.getView(
                targetCategory, null, null);
        runOnUiAndWait(() -> textBox.requestFocus());
        ToDoCategory oldCategory = categories.get(targetCategory).clone();
        String expectedName = randomCategoryName('A', 'Z');
        runOnUiAndWait(() -> textBox.setText(expectedName));

        // Changing the text alone shouldn't change the category ... yet
        ToDoCategory actualCategory = adapter.getItem(targetCategory);
        assertEquals("Category after edit; still focused",
                oldCategory, actualCategory);

        // After focus changes, the category should have changed
        runOnUiAndWait(() -> textBox.clearFocus());
        ToDoCategory expectedCategory = oldCategory.clone();
        expectedCategory.setName(expectedName);
        actualCategory = adapter.getItem(targetCategory);
        assertEquals("Category after focus change",
                expectedCategory, actualCategory);
    }

}
