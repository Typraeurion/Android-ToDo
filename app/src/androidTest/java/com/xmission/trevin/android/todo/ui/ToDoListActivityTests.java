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

import static com.xmission.trevin.android.todo.util.LaunchUtils.*;
import static com.xmission.trevin.android.todo.util.ViewActionUtils.*;

import android.content.Context;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.xmission.trevin.android.todo.R;
import com.xmission.trevin.android.todo.data.MockSharedPreferences;
import com.xmission.trevin.android.todo.data.ToDoCategory;
import com.xmission.trevin.android.todo.data.ToDoPreferences;
import com.xmission.trevin.android.todo.provider.MockToDoRepository;
import com.xmission.trevin.android.todo.provider.TestObserver;
import com.xmission.trevin.android.todo.provider.ToDoRepositoryImpl;

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

    private static MockSharedPreferences mockPrefs = null;

    private static MockToDoRepository mockRepo = null;

    @BeforeClass
    public static void getTestContext() {
        testContext = InstrumentationRegistry
                .getInstrumentation().getTargetContext();
        mockPrefs = new MockSharedPreferences();
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
        try (ActivityScenario<ToDoListActivity> scenario =
                     ActivityScenario.launch(ToDoListActivity.class);
                TestObserver repoObserver = new TestObserver(mockRepo)) {
            // Step 1: Verify the "New" button exists
            assertButtonShown(scenario, "New", R.id.ListButtonNew);
            // Step 2: Click the "New" button; wait for idle sync
            pressButton(scenario, R.id.ListButtonNew);
            // Step 3: Verify the ToDoDetailsActivity is launched,
            assertActivityLaunched(ToDoDetailsActivity.class);
            //     ... that it was provided a category ID,
            assertIntentHasLongExtra(ToDoDetailsActivity.class,
                    ToDoListActivity.EXTRA_CATEGORY_ID, ToDoCategory.UNFILED);
            //     ... and that it was NOT given an item ID.
            assertIntentDoesNotHaveExtra(ToDoDetailsActivity.class,
                    ToDoListActivity.EXTRA_ITEM_ID);
            // Step 4: Verify the "Cancel" button exists in the details activity
            assertButtonShown("Cancel", R.id.DetailButtonCancel);
            // Step 5: Click the "Cancel" button; wait for the activity to finish
            pressButton(R.id.DetailButtonCancel);
            // Finished with the UI at this point
            // Step 6: Verify that nothing was added to the repository
            repoObserver.assertNotChanged(
                    "Changes were made to the repository when no item was saved!");
        }
    }

}
