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
package com.xmission.trevin.android.todo.provider;

import static com.xmission.trevin.android.todo.provider.MockToDoRepository.*;
import static com.xmission.trevin.android.todo.util.RandomToDoUtils.randomWeek;
import static org.junit.Assert.*;

import android.content.Context;
import android.database.DataSetObserver;
import android.database.SQLException;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.xmission.trevin.android.todo.R;
import com.xmission.trevin.android.todo.data.AlarmInfo;
import com.xmission.trevin.android.todo.data.ToDoAlarm;
import com.xmission.trevin.android.todo.data.ToDoCategory;
import com.xmission.trevin.android.todo.data.ToDoItem;
import com.xmission.trevin.android.todo.data.ToDoMetadata;
import com.xmission.trevin.android.todo.data.ToDoPreferences;
import com.xmission.trevin.android.todo.data.repeat.Months;
import com.xmission.trevin.android.todo.data.repeat.RepeatSemiMonthlyOnDays;
import com.xmission.trevin.android.todo.data.repeat.RepeatYearlyOnDate;
import com.xmission.trevin.android.todo.data.repeat.WeekDays;
import com.xmission.trevin.android.todo.data.repeat.WeekdayDirection;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests for the ToDoRepository implementation
 *
 * @author Trevin Beattie
 */
@RunWith(AndroidJUnit4.class)
public class ToDoRepositoryTests {

    static Context testContext = null;

    static ToDoRepository repo = ToDoRepositoryImpl.getInstance();

    static final Random RAND = new Random();

    /**
     * Test observer which records which observer method was called.
     * Since observers run on the UI thread while repository calls
     * must be done on a non-UI thread, the observer includes a
     * synchronization mechanism: a {@link CountDownLatch} with a
     * count of 1, on the presumption that the observer will be
     * called just once before the test case checks its status.
     */
    public static class TestObserver extends DataSetObserver {
        private CountDownLatch latch = new CountDownLatch(1);
        private boolean changed = false;
        private boolean invalidated = false;

        @Override
        public void onChanged() {
            changed = true;
            latch.countDown();
        }
        @Override
        public void onInvalidated() {
            invalidated = true;
            latch.countDown();
        }

        public void reset() {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            changed = false;
            invalidated = false;
            latch = new CountDownLatch(1);
        }

        /**
         * Wait for the latch to show the observer was notified.
         * Times out after a second.  This is called internally
         * before every {@code assert}&hellip; method; test cases
         * only need to call it if an exception prevents them
         * from checking the observer before unregistering it.
         *
         * @throws AssertionError if the latch times out
         * @throws RuntimeException (wrapping an {@link InterruptedException})
         * if the wait was interrupted
         */
        private void await() throws AssertionError {
            try {
                assertTrue("Timed out waiting for the observer to be notified",
                        latch.await(1, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted while waiting"
                        + " for the observer to be notified", e);
            }
        }

        /**
         * Wait for the latch; times out in a quarter second.
         * This call does not throw any exception if the latch
         * times out.  It&rsquo;s intended to ensure synchronization
         * of any outstanding notifications prior to unregistering
         * the observer during test case clean-up.
         */
        private void waitToClear() {
            try {
                latch.await(250, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                // Ignore
            }
        }

        public void assertChanged() {
            assertChanged("Data observer onChanged was not called");
        }
        public void assertChanged(String message) {
            await();
            assertTrue(message, changed);
        }
        public void assertNotChanged() {
            assertNotChanged("Data observer onChanged was called");
        }
        public void assertNotChanged(String message) {
            waitToClear();
            assertFalse(message, changed);
        }
        public void assertInvalidated() {
            assertInvalidated("Data observer onInvalidated was not called");
        }
        public void assertInvalidated(String message) {
            await();
            assertTrue(message, invalidated);
        }
        public void assertNotInvalidated() {
            assertNotInvalidated("Data observer onInvalidated was called");
        }
        public void assertNotInvalidated(String message) {
            waitToClear();
            assertFalse(message, invalidated);
        }
    }

    @BeforeClass
    public static void openDatabase() {
        testContext = InstrumentationRegistry.getInstrumentation()
                .getTargetContext();
        repo.open(testContext);
    }

    @AfterClass
    public static void closeDatabase() {
        repo.release(testContext);
    }

    /**
     * Test that the database is pre-populated with the
     * &ldquo;Unfiled&rdquo; category
     */
    @Test
    public void testUnfiledCategory() {
        String expectedName = testContext.getString(R.string.Category_Unfiled);
        ToDoCategory category = repo.getCategoryById(ToDoCategory.UNFILED);
        assertNotNull(String.format("Category %d not found in the repository",
                ToDoCategory.UNFILED), category);
        assertEquals("Unfiled category name",
                expectedName, category.getName());
    }

    /**
     * Test counting the number of categories in the database.
     * Because the database may contain pre-test data, we don&rsquo;t
     * depend on a specific value; but it must be at least 1
     * (for the &ldquo;Unfiled&rdquo; category).
     */
    @Test
    public void testCountCategories() {
        int count = repo.countCategories();
        assertTrue(String.format("Expected at least 1 category, got %d",
                count), count > 0);
    }

    /**
     * Test getting the maximum category ID.
     */
    @Test
    public void testGetMaxCategoryId() {
        long maxId = repo.getMaxCategoryId();
        assertTrue(String.format("Expected a positive category ID, got %d",
                maxId), maxId > 0);
    }

    /**
     * Test inserting a new category, reading it,
     * updating it, and then deleting it.
     * <p>
     * There&rsquo;s a tiny chance this may result in a duplicate value error
     * since the category name is generated from random letters, but since the
     * minimum length of the name is 12 characters it shouldn&rsquo;t conflict
     * with &ldquo;Unfiled&rdquo; so would only be an issue if the database
     * already contains any additional categories with long names (no spaces),
     * and even then the odds of collision are less than one in 10<sup>20</sup>.
     * </p>
     */
    @Test
    public void testCategoryCRUD() {
        int targetLen = RAND.nextInt(20) + 12;
        String firstExpectedName = RandomStringUtils.randomAlphabetic(targetLen);
        targetLen = RAND.nextInt(20) + 12;
        String secondExpectedName = RandomStringUtils.randomAlphabetic(targetLen);
        TestObserver observer = new TestObserver();
        repo.registerDataSetObserver(observer);
        try {
            ToDoCategory newCategory = repo.insertCategory(firstExpectedName);
            assertNotNull("No ToDoCategory returned from insert",
                    newCategory);
            assertNotNull("New category is missing its ID",
                    newCategory.getId());
            try {
                observer.assertChanged(
                        "Registered observer was not called after insert");
                ToDoCategory readCategory =
                        repo.getCategoryById(newCategory.getId());
                assertNotNull(String.format("New category %d not found",
                        newCategory.getId()), readCategory);
                assertEquals("Category name read back",
                        firstExpectedName, readCategory.getName());
                observer.reset();
                readCategory = repo.updateCategory(
                        newCategory.getId(), secondExpectedName);
                assertNotNull(String.format("Updated category %d not found",
                        newCategory.getId()), readCategory);
                assertEquals("Category name read back after update",
                        secondExpectedName, readCategory.getName());
                observer.assertChanged(
                        "Registered observer was not called after update");
            } finally {
                observer.reset();
                assertTrue(
                        "Repository did not indicate the category was deleted",
                        repo.deleteCategory(newCategory.getId()));
                assertNull(String.format("Category %d was not deleted",
                                newCategory.getId()),
                        repo.getCategoryById(newCategory.getId()));
                observer.assertChanged(
                        "Registered observer was not called after delete");
            }
        } finally {
            observer.waitToClear();
            repo.unregisterDataSetObserver(observer);
        }
    }

    /**
     * Test inserting a ToDoCategory object (used by the importer service).
     * We expect such an insert to fail if the provided category ID is
     * already in use.
     */
    @Test
    public void testCategoryObjectCRUD() {
        TestObserver observer = new TestObserver();
        repo.registerDataSetObserver(observer);
        try {
            // First we'll need to find an unused row ID
            ToDoCategory originalCategory = new ToDoCategory();
            originalCategory.setId(99);
            while (repo.getCategoryById(originalCategory.getId()) != null)
                originalCategory.setId(originalCategory.getId() + 1);
            int targetLen = RAND.nextInt(20) + 12;
            originalCategory.setName(
                    RandomStringUtils.randomAlphabetic(targetLen));
            ToDoCategory insertedCategory =
                    repo.insertCategory(originalCategory.clone());
            assertNotNull("No NoteCategory returned from insert",
                    insertedCategory);
            try {
                assertEquals("Inserted category",
                        originalCategory, insertedCategory);
                observer.assertChanged(
                        "Registered observer was not called after insert");
                observer.reset();
                ToDoCategory conflictingCategory = new ToDoCategory();
                conflictingCategory.setId(insertedCategory.getId());
                targetLen = RAND.nextInt(20) + 12;
                conflictingCategory.setName(
                        RandomStringUtils.randomAlphabetic(targetLen));
                try {
                    ToDoCategory returnCategory =
                            repo.insertCategory(conflictingCategory);
                    if ((returnCategory != null) && (!returnCategory.getId()
                            .equals(insertedCategory.getId())))
                        repo.deleteCategory(returnCategory.getId());
                    fail(String.format("Repository overwrote %s with %s",
                            insertedCategory, conflictingCategory));
                } catch (SQLException e) {
                    // Success
                }
                observer.assertNotChanged("Registered observer was called"
                        + " in spite of conflicting insert");

                // Now try again with a duplicate name, no specified ID.
                try {
                    ToDoCategory returnCategory =
                            repo.insertCategory(originalCategory.getName());
                    if (returnCategory != null)
                        repo.deleteCategory(returnCategory.getId());
                    fail(String.format("Repository added duplicate category:"
                            + " original = %s, conflicting = %s",
                            insertedCategory, returnCategory));
                } catch (SQLException e) {
                    // Success
                }
            } finally {
                observer.reset();
                assertTrue(
                        "Repository did not indicate the category was deleted",
                        repo.deleteCategory(insertedCategory.getId()));
                assertNull(String.format("Category %d was not deleted",
                        insertedCategory.getId()),
                        repo.getCategoryById(insertedCategory.getId()));
                observer.assertChanged(
                        "Registered observer was not called after delete");
            }
        } finally {
            observer.waitToClear();
            repo.unregisterDataSetObserver(observer);
        }
    }

    /**
     * Test reading all categories.  This entails inserting some new
     * categories in order to ensure we get back multiple values.
     * We delete the newly inserted categories afterward.
     * <p>
     * There&rsquo;s a tiny chance this may result in a duplicate value error
     * since the category name is generated from random letters, but since the
     * minimum length of the name is 12 characters it shouldn&rsquo;t conflict
     * with &ldquo;Unfiled&rdquo; so would only be an issue if the database
     * already contains any additional categories with long names (no spaces),
     * and even then the odds of collision are less than one in 10<sup>20</sup>.
     * </p>
     */
    @Test
    public void testGetCategories() {
        List<ToDoCategory> testCategories = new ArrayList<>();
        Set<Long> expectedIds = new TreeSet<>();
        expectedIds.add((long) ToDoCategory.UNFILED);
        int target = RAND.nextInt(3) + 3;
        try {
            for (int i = 0; i < target; i++) {
                int targetLen = RAND.nextInt(20) + 12;
                String name = RandomStringUtils.randomAlphabetic(targetLen);
                ToDoCategory newCategory = repo.insertCategory(name);
                assertNotNull("Failed to insert new category " + name,
                        newCategory);
                assertNotNull(String.format(
                        "New category %s is missing its ID", name),
                        newCategory.getId());
                testCategories.add(newCategory);
                expectedIds.add(newCategory.getId());
            }

            List<ToDoCategory> allCategories = repo.getCategories();
            assertNotNull("Repo did not return a list of categories",
                    allCategories);
            assertTrue(String.format("Expected at least %d categories"
                                    + " in the repository; got %d",
                            testCategories.size() + 1, allCategories.size()),
                    allCategories.size() > testCategories.size());
            Set<Long> actualIds = new TreeSet<>();
            for (ToDoCategory category : allCategories) {
                assertNotNull(String.format("Category %s is missing its ID",
                        category.getName()), category.getId());
                actualIds.add(category.getId());
            }
            assertTrue(String.format("Expected categories %s, but got %s",
                    expectedIds, actualIds),
                    actualIds.containsAll(expectedIds));
        } finally {
            for (ToDoCategory testCategory : testCategories) {
                repo.deleteCategory(testCategory.getId());
            }
        }
    }

    /**
     * Test deleting <i>all</i> categories.  <b>This test is destructive!</b>
     * We first insert a few new categories to ensure the table is not empty
     * (save for the &ldquo;Unfiled&rdquo; category), then delete all and
     * re-read the table to ensure that <i>only</i> the &ldquo;Unfiled&rdquo;
     * category remains.
     */
    @Test
    public void testDeleteAllCategories() {
        List<ToDoCategory> testCategories = new ArrayList<>();
        int target = RAND.nextInt(3) + 3;
        TestObserver observer = new TestObserver();
        repo.registerDataSetObserver(observer);
        try {
            while (testCategories.size() < target) {
                int targetLen = RAND.nextInt(20) + 12;
                String name = RandomStringUtils.randomAlphabetic(targetLen);
                ToDoCategory newCategory = repo.insertCategory(name);
                assertNotNull("Failed to insert new category " + name,
                        newCategory);
                assertNotNull(String.format(
                                "New category %s is missing its ID", name),
                        newCategory.getId());
                testCategories.add(newCategory);
            }

            List<ToDoCategory> allCategories = repo.getCategories();
            assertNotNull("Repo did not return a list of categories",
                    allCategories);
            assertTrue(String.format("Expected at least %d categories"
                                    + " in the repository; got %d",
                            testCategories.size() + 1, allCategories.size()),
                    allCategories.size() > testCategories.size());

            observer.reset();
            repo.deleteAllCategories();
            allCategories = repo.getCategories();
            assertNotNull("Repo did not return a list of categories",
                    allCategories);
            assertEquals("Number of categories remaining after deleting all",
                    1, allCategories.size());
            ToDoCategory lastCategory = allCategories.get(0);
            assertEquals("Last remaining category ID",
                    Long.valueOf(ToDoCategory.UNFILED), lastCategory.getId());
            observer.assertChanged(
                    "Observer not called after deleting all categories");
        } catch (RuntimeException e) {
            for (ToDoCategory testCategory : testCategories) {
                repo.deleteCategory(testCategory.getId());
            }
            throw e;
        } finally {
            observer.waitToClear();
            repo.unregisterDataSetObserver(observer);
        }
    }

    /**
     * Test inserting new metadata, reading it,
     * updating it, and then deleting it (by name).
     * <p>
     * There&rsquo;s a tiny chance this may result in a duplicate value error
     * since the metadata name is generated from random letters, but since the
     * minimum length of the name is 12 characters it would only be an issue
     * if the database already contains any additional metadata with long
     * names, and even then the odds of collision are less than one in
     * 10<sup>20</sup>.
     * </p>
     */
    @Test
    public void testMetadataCRUD() {
        int targetLen = RAND.nextInt(20) + 12;
        String name = RandomStringUtils.randomAlphabetic(targetLen);
        byte[] firstValue = new byte[10 + RAND.nextInt(20)];
        byte[] secondValue = new byte[10 + RAND.nextInt(20)];
        TestObserver observer = new TestObserver();
        repo.registerDataSetObserver(observer);
        try {
            ToDoMetadata newMetadata = repo.upsertMetadata(name, firstValue);
            assertNotNull("No ToDoMetadata returned from insert",
                    newMetadata);
            try {
                assertNotNull("New metadata is missing its ID",
                        newMetadata.getId());
                long metaId = newMetadata.getId();
                assertEquals("Metadata name returned from insert",
                        name, newMetadata.getName());
                assertArrayEquals("Metadata value returned from insert",
                        firstValue, newMetadata.getValue());
                observer.assertChanged("Observer not called after insert");
                ToDoMetadata readMetadata = repo.getMetadataByName(name);
                assertNotNull(String.format(
                                "Newly inserted metadata \"%s\" not found", name),
                        readMetadata);
                assertEquals("ID read back from metadata",
                        (Long) metaId, readMetadata.getId());
                assertEquals("Name read back from metadata",
                        name, readMetadata.getName());
                assertArrayEquals(
                        "Value read back from metadata after insert",
                        firstValue, readMetadata.getValue());

                observer.reset();
                newMetadata = repo.upsertMetadata(name, secondValue);
                assertNotNull("No ToDoMetadata returned from update",
                        newMetadata);
                assertEquals("Updated metadata ID",
                        (Long) metaId, newMetadata.getId());
                assertEquals("Metadata name returned from update",
                        name, newMetadata.getName());
                assertArrayEquals("Metadata value returned from update",
                        secondValue, newMetadata.getValue());
                observer.assertChanged("Observer not called after update");
                readMetadata = repo.getMetadataById(metaId);
                assertNotNull(String.format("Updated metadata #%d not found",
                        metaId), readMetadata);
                assertEquals("ID read back from updated metadata",
                        (Long) metaId, readMetadata.getId());
                assertEquals("Name read back from updated metadata",
                        name, readMetadata.getName());
                assertArrayEquals("Value read back from metadata after update",
                        secondValue, readMetadata.getValue());

            } finally {
                observer.reset();
                assertTrue("Repository did not indicate the metadata was deleted",
                        repo.deleteMetadata(name));
                assertNull(String.format("Metadata \"%s\" was not deleted", name),
                        repo.getMetadataByName(name));
                observer.assertChanged("Observer not called after delete");
            }
        } finally {
            observer.waitToClear();
            repo.unregisterDataSetObserver(observer);
        }
    }

    /**
     * Test reading and counting all metadata.  This entails inserting
     * some new metadata in order to ensure we get back multiple values.
     * We delete the newly inserted metadata afterward by their ID.
     * <p>
     * There&rsquo;s a tiny chance this may result in a duplicate value error
     * since the metadata name is generated from random letters, but since the
     * minimum length of the name is 12 characters it would only be an issue
     * if the database already contains any additional metadata with long
     * names, and even then the odds of collision are less than one in
     * 10<sup>20</sup>.
     * </p>
     */
    @Test
    public void testGetMetadata() {
        int target = RAND.nextInt(3) + 3;
        Map<String,String> expectedData = new TreeMap<>();
        try {
            while (expectedData.size() < target) {
                int targetLen = RAND.nextInt(20) + 12;
                String name = RandomStringUtils.randomAlphabetic(targetLen);
                // For simplicity in logging any errors,
                // the values will be encoded strings.
                targetLen = RAND.nextInt(20) + 12;
                String sValue = RandomStringUtils.randomAscii(targetLen);
                ToDoMetadata datum = repo.upsertMetadata(name,
                        sValue.getBytes(StandardCharsets.UTF_8));
                assertNotNull("No ToDoMetadata returned from insert", datum);
                assertNotNull("New metadata has no ID", datum.getId());
                expectedData.put(name, sValue);
            }

            int count = repo.countMetadata();
            assertTrue(String.format("Expected a metadata count of"
                    + " at least %d, but got %d", expectedData.size(), count),
                    count >= expectedData.size());

            List<ToDoMetadata> allMetadata = repo.getMetadata();
            assertNotNull("No list returned from getMetadata", allMetadata);
            assertTrue(String.format("Expected at least %d metadata"
                                    + " in the repository, but got %d",
                            expectedData.size(), allMetadata.size()),
                    allMetadata.size() >= expectedData.size());
            Map<String,String> actualData = new TreeMap<>();
            for (ToDoMetadata datum: allMetadata)
                actualData.put(datum.getName(),
                        new String(datum.getValue(), StandardCharsets.UTF_8));
            assertTrue(String.format("Expected metadata to include %s,"
                                    + " but got %s",
                            expectedData.keySet(), actualData.keySet()),
                    actualData.keySet().containsAll(expectedData.keySet()));
            if (actualData.size() > expectedData.size()) {
                // Discard any metadata we didn't create
                Iterator<Map.Entry<String,String>> it =
                        actualData.entrySet().iterator();
                while (it.hasNext()) {
                    if (expectedData.containsKey(it.next().getKey()))
                        continue;
                    it.remove();
                }
            }
            assertEquals("Metadata values", expectedData, actualData);
        } finally {
            for (String name : expectedData.keySet()) {
                repo.deleteMetadata(name);
            }
        }
    }

    /**
     * Test deleting <i>all</i> metadata.  <b>This test is destructive!</b>
     * We first insert a few new metadata to ensure the table is not empty,
     * then delete all and re-read the table to ensure that nothing remains.
     */
    @Test
    public void testDeleteAllMetadata() {
        int target = RAND.nextInt(3) + 3;
        Set<String> testNames = new TreeSet<>();
        TestObserver observer = new TestObserver();
        repo.registerDataSetObserver(observer);
        try {
            while (testNames.size() < target) {
                int targetLen = RAND.nextInt(20) + 12;
                String name = RandomStringUtils.randomAlphabetic(targetLen);
                byte[] value = new byte[10 + RAND.nextInt(20)];
                ToDoMetadata datum = repo.upsertMetadata(name, value);
                assertNotNull("No ToDoMetadata returned from insert", datum);
                assertNotNull("New metadata has no ID", datum.getId());
                testNames.add(name);
            }

            List<ToDoMetadata> allMetadata = repo.getMetadata();
            assertNotNull("No list returned from getMetadata", allMetadata);
            assertFalse("No metadata returned after inserts",
                    allMetadata.isEmpty());

            observer.reset();
            assertTrue("Response from deleteAllMetadata",
                    repo.deleteAllMetadata());
            observer.assertChanged(
                    "Observer not called after deleting all metadata");

            allMetadata = repo.getMetadata();
            assertNotNull("No list returned from getMetadata"
                    + " (after deleteAllMetadata)", allMetadata);
            assertEquals("Remaining metadata after deleteAllMetadata",
                    Collections.emptyList(), allMetadata);
        } finally {
            observer.waitToClear();
            repo.unregisterDataSetObserver(observer);
            for (String name : testNames) {
                repo.deleteMetadata(name);
            }
        }
    }

    /**
     * Test getting the maximum To Do item ID.
     */
    @Test
    public void testGetMaxItemId() {
        long maxId = repo.getMaxItemId();
        assertTrue(String.format("Expected a positive item ID, got %d",
                maxId), maxId > 0);
    }

    /**
     * Test inserting, reading, updating, and deleting a simple To Do item.
     */
    @Test
    public void testToDoSimpleCRUD() {
        ToDoItem expectedToDo = new ToDoItem();
        expectedToDo.setCategoryId(ToDoCategory.UNFILED);
        expectedToDo.setPrivate(0);
        expectedToDo.setCreateTimeNow();
        expectedToDo.setModTime(expectedToDo.getCreateTime());
        int targetLen = RAND.nextInt(20) + 8;
        expectedToDo.setDescription(RandomStringUtils.randomAscii(targetLen));

        TestObserver observer = new TestObserver();
        repo.registerDataSetObserver(observer);

        try {
            ToDoItem returnToDo = repo.insertItem(expectedToDo);
            assertNotNull("No item returned from insert", returnToDo);
            assertNotNull("No ID returned with inserted item",
                    returnToDo.getId());
            observer.assertChanged("Observer not called after insert");
            long itemId = returnToDo.getId();

            try {
                returnToDo = repo.getItemById(itemId);
                assertNotNull("Failed to read back item " + itemId,
                        returnToDo);
                expectedToDo.setId(itemId);
                expectedToDo.setCategoryName(testContext
                        .getString(R.string.Category_Unfiled));
                assertEquals("Item read back from the repository after insert",
                        expectedToDo, returnToDo);

                observer.reset();
                targetLen = RAND.nextInt(20) + 8;
                expectedToDo.setDescription(
                        RandomStringUtils.randomAscii(targetLen));
                expectedToDo.setModTimeNow();
                returnToDo = repo.updateItem(expectedToDo);
                assertNotNull("No item returned from update", returnToDo);
                observer.assertChanged("Observer not called after update");
                returnToDo = repo.getItemById(itemId);
                assertNotNull("Failed to read back item " + itemId,
                        returnToDo);
                assertEquals("Item read back from the repository after update",
                        expectedToDo, returnToDo);
            } finally {
                observer.reset();
                assertTrue("Repository did not indicate the item was deleted",
                        repo.deleteItem(itemId));
                assertNull("New item was not deleted",
                        repo.getItemById(itemId));
                observer.assertChanged("Observer not called after delete");
            }
        } finally {
            observer.waitToClear();
            repo.unregisterDataSetObserver(observer);
        }
    }

    /**
     * Test inserting, reading, updating, and deleting a To Do item
     * with all possible fields set.
     */
    @Test
    public void testToDoFullCRUD() {
        ToDoItem expectedToDo = new ToDoItem();
        expectedToDo.setCategoryId(ToDoCategory.UNFILED);
        expectedToDo.setPrivate(1);
        expectedToDo.setCreateTimeNow();
        expectedToDo.setModTime(expectedToDo.getCreateTime());
        int targetLen = RAND.nextInt(20) + 8;
        expectedToDo.setDescription(RandomStringUtils.randomAscii(targetLen));
        expectedToDo.setDue(LocalDate.now().plusDays(RAND.nextInt(31) + 1));
        expectedToDo.setCompleted(Instant.ofEpochMilli(
                RAND.nextLong() % 3155760000L + 946684800L));
        expectedToDo.setPriority(RAND.nextInt(10) + 1);
        targetLen = RAND.nextInt(80) + 16;
        expectedToDo.setNote(RandomStringUtils.randomAscii(targetLen));
        ToDoAlarm alarm = new ToDoAlarm();
        alarm.setTime(LocalTime.ofSecondOfDay(RAND.nextInt(86400)));
        alarm.setAlarmDaysEarlier(RAND.nextInt(31) + 1);
        alarm.setNotificationTime(Instant.ofEpochMilli(
                RAND.nextLong() % 3155760000L + 946684800L));
        expectedToDo.setAlarm(alarm);
        // There is no single repeat interval that uses all
        // repeat fields in the database, but we can cover
        // the gaps in this one with a yearly interval.
        RepeatSemiMonthlyOnDays repeat = new RepeatSemiMonthlyOnDays();
        repeat.setIncrement(RAND.nextInt(12) + 1);
        repeat.setDay(WeekDays.values()[RAND.nextInt(
                WeekDays.values().length - 1) + 1]);
        repeat.setDay2(WeekDays.values()[RAND.nextInt(
                WeekDays.values().length - 1) + 1]);
        repeat.setWeek(randomWeek());
        repeat.setWeek2(randomWeek());
        repeat.setEnd(expectedToDo.getDue()
                .plusMonths(RAND.nextInt(255) + 1));
        expectedToDo.setRepeatInterval(repeat);
        expectedToDo.setHideDaysEarlier(RAND.nextInt(31) + 7);

        TestObserver observer = new TestObserver();
        repo.registerDataSetObserver(observer);

        try {
            ToDoItem returnToDo = repo.insertItem(expectedToDo);
            assertNotNull("No item returned from insert", returnToDo);
            assertNotNull("No ID returned with inserted item",
                    returnToDo.getId());
            observer.assertChanged("Observer not called after insert");
            long itemId = returnToDo.getId();

            try {
                returnToDo = repo.getItemById(itemId);
                assertNotNull("Failed to read back item " + itemId,
                        returnToDo);
                expectedToDo.setId(itemId);
                expectedToDo.setCategoryName(testContext
                        .getString(R.string.Category_Unfiled));
                assertEquals("Item read back from the repository after insert",
                        expectedToDo, returnToDo);

                observer.reset();
                targetLen = RAND.nextInt(20) + 8;
                expectedToDo.setDescription(
                        RandomStringUtils.randomAscii(targetLen));
                expectedToDo.setDue(LocalDate.now().plusDays(
                        RAND.nextInt(31) + 1));
                expectedToDo.setCompleted(Instant.ofEpochMilli(
                        RAND.nextLong() % 3155760000L + 946684800L));
                expectedToDo.setPriority(RAND.nextInt(10) + 1);
                targetLen = RAND.nextInt(80) + 16;
                expectedToDo.setNote(RandomStringUtils.randomAscii(targetLen));
                alarm.setTime(LocalTime.ofSecondOfDay(RAND.nextInt(86400)));
                alarm.setAlarmDaysEarlier(RAND.nextInt(31) + 1);
                alarm.setNotificationTime(Instant.ofEpochMilli(
                        RAND.nextLong() % 3155760000L + 946684800L));
                expectedToDo.setAlarm(alarm);
                RepeatYearlyOnDate repeat2 = new RepeatYearlyOnDate();
                repeat2.setIncrement(RAND.nextInt(12) + 1);
                repeat2.setMonth(Months.values()[RAND.nextInt(
                        Months.values().length - 1) + 1]);
                repeat2.setDate(RAND.nextInt(31) + 1);
                repeat2.setAllowedWeekDays(WeekDays.fromBitMap(
                        RAND.nextInt(127) + 1));
                repeat2.setDirection(WeekdayDirection.values()[RAND.nextInt(
                        WeekdayDirection.values().length)]);
                repeat2.setEnd(repeat.getEnd().plusYears(
                        RAND.nextInt(10) + 1));
                expectedToDo.setRepeatInterval(repeat2);
                expectedToDo.setModTimeNow();
                returnToDo = repo.updateItem(expectedToDo);
                assertNotNull("No item returned from update", returnToDo);
                observer.assertChanged("Observer not called after update");
                returnToDo = repo.getItemById(itemId);
                assertNotNull("Failed to read back item " + itemId,
                        returnToDo);
                assertEquals("Item read back from the repository after update",
                        expectedToDo, returnToDo);
            } finally {
                observer.reset();
                assertTrue("Repository did not indicate the item was deleted",
                        repo.deleteItem(itemId));
                assertNull("New item was not deleted",
                        repo.getItemById(itemId));
                observer.assertChanged("Observer not called after delete");
            }
        } finally {
            observer.waitToClear();
            repo.unregisterDataSetObserver(observer);
        }
    }

    /**
     * Test inserting, reading, and deleting a To Do item which has a
     * predetermined ID (e.g. from importing notes from a backup file)
     */
    @Test
    public void testInsertToDoWithId() {
        long expectedId = (RAND.nextInt() & 0xffff) + 1024L;
        while (repo.getItemById(expectedId) != null)
            expectedId++;
        ToDoItem expectedToDo = new ToDoItem();
        expectedToDo.setId(expectedId);
        expectedToDo.setCategoryId(ToDoCategory.UNFILED);
        expectedToDo.setPrivate(0);
        expectedToDo.setCreateTimeNow();
        expectedToDo.setModTime(expectedToDo.getCreateTime());
        int targetLen = RAND.nextInt(20) + 8;
        expectedToDo.setDescription(RandomStringUtils.randomAscii(targetLen));

        TestObserver observer = new TestObserver();
        repo.registerDataSetObserver(observer);

        try {
            ToDoItem returnToDo = repo.insertItem(expectedToDo);
            assertNotNull("No item returned from insert", returnToDo);
            assertEquals("ID of inserted To Do item",
                    Long.valueOf(expectedId), returnToDo.getId());
            observer.assertChanged("Observer not called after insert");

            try {
                returnToDo = repo.getItemById(expectedId);
                assertNotNull("Failed to read back item " + expectedId,
                        returnToDo);
                expectedToDo.setCategoryName(testContext
                        .getString(R.string.Category_Unfiled));
                assertEquals("To Do item read back from the repository after insert",
                        expectedToDo, returnToDo);
            } finally {
                observer.reset();
                assertTrue("Repository did not indicate the item was deleted",
                        repo.deleteItem(expectedId));
                assertNull("New item was not deleted",
                        repo.getItemById(expectedId));
                observer.assertChanged("Observer not called after delete");
            }
        } finally {
            observer.waitToClear();
            repo.unregisterDataSetObserver(observer);
        }
    }

    /**
     * Test inserting, reading, updating, and deleting an encrypted To Do item.
     */
    @Test
    public void testEncryptedToDoCRUD() {
        ToDoItem expectedToDo = new ToDoItem();
        expectedToDo.setCategoryId(ToDoCategory.UNFILED);
        expectedToDo.setPrivate(2);
        expectedToDo.setCreateTimeNow();
        expectedToDo.setModTime(expectedToDo.getCreateTime());
        byte[] descriptionEncryption = new byte[64 + 32 * RAND.nextInt(8)];
        RAND.nextBytes(descriptionEncryption);
        expectedToDo.setEncryptedDescription(descriptionEncryption);
        byte[] noteEncryption = new byte[128 + 32 * RAND.nextInt(16)];
        RAND.nextBytes(noteEncryption);
        expectedToDo.setEncryptedNote(noteEncryption);

        ToDoItem returnToDo = repo.insertItem(expectedToDo);
        assertNotNull("No item returned from insert", returnToDo);
        assertNotNull("No ID returned with inserted item",
                returnToDo.getId());
        long itemId = returnToDo.getId();

        try {
            returnToDo = repo.getItemById(itemId);
            assertNotNull("Failed to read back To Do item " + itemId,
                    returnToDo);
            // assertEquals may not work with byte array fields,
            // so we'll compare that separately first.
            assertArrayEquals("Encrypted description read back after insert",
                    descriptionEncryption, returnToDo.getEncryptedDescription());
            assertArrayEquals("Encrypted note read back after insert",
                    noteEncryption, returnToDo.getEncryptedNote());
            expectedToDo.setId(itemId);
            expectedToDo.setCategoryName(testContext
                    .getString(R.string.Category_Unfiled));
            expectedToDo.setEncryptedDescription(
                    returnToDo.getEncryptedDescription());
            expectedToDo.setEncryptedNote(returnToDo.getEncryptedNote());
            assertEquals("To Do item read back from the repository after insert",
                    expectedToDo, returnToDo);

            RAND.nextBytes(descriptionEncryption);
            RAND.nextBytes(noteEncryption);
            expectedToDo.setEncryptedDescription(descriptionEncryption);
            expectedToDo.setEncryptedNote(noteEncryption);
            expectedToDo.setModTimeNow();
            returnToDo = repo.updateItem(expectedToDo);
            assertNotNull("No To Do item returned from update", returnToDo);
            returnToDo = repo.getItemById(itemId);
            assertNotNull("Failed to read back To Do item " + itemId,
                    returnToDo);
            assertArrayEquals("Encrypted content read back after update",
                    descriptionEncryption, returnToDo.getEncryptedDescription());
            assertArrayEquals("Encrypted note read back after update",
                    noteEncryption, returnToDo.getEncryptedNote());
            expectedToDo.setEncryptedDescription(
                    returnToDo.getEncryptedDescription());
            expectedToDo.setEncryptedNote(returnToDo.getEncryptedNote());
            assertEquals("To Do item read back from the repository after update",
                    expectedToDo, returnToDo);
        } finally {
            assertTrue("Repository did not indicate the item was deleted",
                    repo.deleteItem(itemId));
            assertNull("New item was not deleted", repo.getItemById(itemId));
        }
    }

    /**
     * Test that the repository refuses to insert a To Do item with no
     * {@code description}.  We expect it to throw an
     * {@link IllegalArgumentException}.
     */
    @Test
    public void testInsertEmptyDescription() {
        ToDoItem emptyToDo = new ToDoItem();
        emptyToDo.setCategoryId(ToDoCategory.UNFILED);
        emptyToDo.setPrivate(0);
        emptyToDo.setCreateTimeNow();
        emptyToDo.setModTime(emptyToDo.getCreateTime());
        // Setting encryptedDescription should have no bearing
        // on a public To Do item.
        emptyToDo.setEncryptedDescription(new byte[] { 1, 2, 3, 4 });

        try {
            ToDoItem returnToDo = repo.insertItem(emptyToDo);
            if ((returnToDo != null) && (returnToDo.getId() != null)) {
                repo.deleteItem(returnToDo.getId());
                fail("Empty To Do item was inserted");
            } else {
                fail("Repository did not throw an exception for an empty"
                        + " description, but didn't return an item ID either");
            }
        } catch (IllegalArgumentException e) {
            // Success
        }
    }

    /**
     * Test that the repository refuses to insert an &ldquo;encrypted&rdquo;
     * To Do item with no {@code encryptedDescription}.  We expect it to
     * throw an {@link IllegalArgumentException}.
     */
    @Test
    public void testInsertEmptyEncryptedDescription() {
        ToDoItem emptyToDo = new ToDoItem();
        emptyToDo.setCategoryId(ToDoCategory.UNFILED);
        emptyToDo.setPrivate(2);
        emptyToDo.setCreateTimeNow();
        emptyToDo.setModTime(emptyToDo.getCreateTime());
        // Setting `description' should have no bearing
        // on an encrypted To Do item.
        emptyToDo.setDescription("Abcdefg");

        try {
            ToDoItem returnToDo = repo.insertItem(emptyToDo);
            if ((returnToDo != null) && (returnToDo.getId() != null)) {
                repo.deleteItem(returnToDo.getId());
                fail("Empty To Do item was inserted");
            } else {
                fail("Repository did not throw an exception for an empty"
                        + " description, but didn't return an item ID either");
            }
        } catch (IllegalArgumentException e) {
            // Success
        }
    }

    /**
     * Test setting the last notification time of an alarm.
     */
    @Test
    public void testUpdateAlarmNotificationTime() {
        ToDoItem expectedToDo = new ToDoItem();
        expectedToDo.setCategoryId(ToDoCategory.UNFILED);
        expectedToDo.setPrivate(0);
        expectedToDo.setCreateTimeNow();
        expectedToDo.setModTime(expectedToDo.getCreateTime());
        int targetLen = RAND.nextInt(20) + 8;
        expectedToDo.setDescription(RandomStringUtils.randomAscii(targetLen));

        ToDoAlarm alarm = new ToDoAlarm();
        alarm.setTime(LocalTime.ofSecondOfDay(RAND.nextInt(86400)));
        alarm.setAlarmDaysEarlier(RAND.nextInt(31) + 1);
        expectedToDo.setAlarm(alarm);

        TestObserver observer = new TestObserver();
        repo.registerDataSetObserver(observer);

        try {
            ToDoItem returnToDo = repo.insertItem(expectedToDo);
            assertNotNull("No item returned from insert", returnToDo);
            assertNotNull("No ID returned with inserted item",
                    returnToDo.getId());
            observer.assertChanged("Observer not called after insert");
            long itemId = returnToDo.getId();

            try {
                returnToDo = repo.getItemById(itemId);
                assertNotNull("Failed to read back item " + itemId,
                        returnToDo);
                expectedToDo.setId(itemId);
                expectedToDo.setCategoryName(testContext
                        .getString(R.string.Category_Unfiled));
                assertEquals("Item read back from the repository after insert",
                        expectedToDo, returnToDo);

                observer.reset();
                Instant expectedInstant = Instant.ofEpochMilli(
                        RAND.nextLong() % 3155760000L + 946684800L);
                repo.updateAlarmNotificationTime(itemId, expectedInstant);
                alarm.setNotificationTime(expectedInstant);
                returnToDo = repo.getItemById(itemId);
                assertNotNull(String.format("Failed to read back item %d"
                                + " after updating last notification time",
                        itemId), returnToDo);
                assertEquals("Item read back from the repository after update",
                        expectedToDo, returnToDo);
            } finally {
                observer.reset();
                repo.deleteItem(itemId);
            }
        } finally {
            observer.waitToClear();
            repo.unregisterDataSetObserver(observer);
        }
    }

    /**
     * Test that when an item&rsquo;s category is deleted,
     * the item is reassigned to the &ldquo;Unfiled&rdquo; category.
     */
    @Test
    public void testDeleteToDoCategory() {
        int targetLen = RAND.nextInt(20) + 12;
        String testCategoryName = RandomStringUtils.randomAlphabetic(targetLen);
        ToDoCategory testCategory = repo.insertCategory(testCategoryName);
        assertNotNull("Repository did not return the new category",
                testCategory);
        assertNotNull("Repository did not return the category ID",
                testCategory.getId());
        ToDoItem testToDo = new ToDoItem();
        testToDo.setCategoryId(testCategory.getId());
        testToDo.setPriority(0);
        testToDo.setCreateTimeNow();
        testToDo.setModTime(testToDo.getCreateTime());
        targetLen = RAND.nextInt(10) + 4;
        testToDo.setDescription(RandomStringUtils.randomAscii(targetLen));
        ToDoItem actualToDo = null;
        try {
            actualToDo = repo.insertItem(testToDo);
            assertNotNull("Repository did not return the inserted To Do item",
                    actualToDo);
            assertNotNull("Repository did not return the item ID",
                    actualToDo.getId());
            long itemId = actualToDo.getId();
            actualToDo = repo.getItemById(itemId);
            assertNotNull(String.format("Newly created To Do item %d was lost",
                    itemId), actualToDo);
            assertEquals("Original note category returned by the repository",
                    testCategoryName, actualToDo.getCategoryName());

            repo.deleteCategory(testCategory.getId());
            actualToDo = repo.getItemById(itemId);
            assertNotNull(String.format("Newly created item %d was lost",
                    itemId), actualToDo);
            assertEquals("To Do category ID after deleting"
                    + " its original category",
                    ToDoCategory.UNFILED, actualToDo.getCategoryId());
            assertEquals("To Do category name after deleting"
                    + " its original category",
                    testContext.getString(R.string.Category_Unfiled),
                    actualToDo.getCategoryName());
        } finally {
            if ((actualToDo != null) && (actualToDo.getId() != null))
                repo.deleteItem(actualToDo.getId());
            // Just in case we failed before the middle of the test
            repo.deleteCategory(testCategory.getId());
        }
    }

    /**
     * Test counting all To Do items, private To Do items, encrypted
     * To Do items, and To Do items in a category.  This requires
     * inserting multiple categories and To Do items.  While we&rsquo;re
     * at it, also test returning the ID&rsquo;s of all private items.
     */
    @Test
    public void testCountToDoItems() {
        long[] testCategoryIds = new long[2 + RAND.nextInt(4)];
        Arrays.fill(testCategoryIds, ToDoCategory.UNFILED);
        int[] expectedCounts = new int[testCategoryIds.length];
        Arrays.fill(expectedCounts, 0);
        long[] testItemIds = new long[2 * testCategoryIds.length
                + RAND.nextInt(16)];
        SortedSet<Long> expectedPrivateItemIds = new TreeSet<>();

        // Count how many items were in the database when we
        // started this test.  We don't rely on a clean database.
        int baseFullCount = repo.countItems();
        int basePrivateCount = repo.countPrivateItems();
        int baseEncryptedCount = repo.countEncryptedItems();
        int baseUnfiledCount = repo.countItemsInCategory(ToDoCategory.UNFILED);
        int expectedPrivateCount = basePrivateCount;
        int expectedEncryptedCount = baseEncryptedCount;

        long[] basePrivateIds = repo.getPrivateItemIds();
        for (long id : basePrivateIds)
            expectedPrivateItemIds.add(id);

        try {
            for (int i = 1; i < testCategoryIds.length; i++) {
                int targetLen = RAND.nextInt(12) + 10;
                String categoryName =
                        RandomStringUtils.randomAlphabetic(targetLen);
                ToDoCategory newCategory = repo.insertCategory(categoryName);
                assertNotNull("Repository did not return the"
                        + " newly inserted category", newCategory);
                assertNotNull("New category did not get an ID",
                        newCategory.getId());
                testCategoryIds[i] = newCategory.getId();
            }

            for (int i = 0; i < testItemIds.length; i++) {
                ToDoItem newToDo = new ToDoItem();
                int catIndex = RAND.nextInt(testCategoryIds.length);
                newToDo.setCategoryId(testCategoryIds[catIndex]);
                int targetLen = RAND.nextInt(20) + 8;
                String description = RandomStringUtils.randomAscii(targetLen);
                newToDo.setPriority(RAND.nextInt(3));
                if (newToDo.isEncrypted()) {
                    newToDo.setEncryptedDescription(description.getBytes());
                    expectedEncryptedCount++;
                } else {
                    newToDo.setDescription(description);
                }
                if (newToDo.isPrivate())
                    expectedPrivateCount++;
                newToDo.setCreateTimeNow();
                newToDo.setModTime(newToDo.getCreateTime());
                newToDo = repo.insertItem(newToDo);
                assertNotNull("Repository did not return the"
                        + " newly inserted To Do item", newToDo);
                assertNotNull("New To Do item did not get an ID",
                        newToDo.getId());
                testItemIds[i] = newToDo.getId();
                expectedCounts[catIndex]++;
                if (newToDo.isPrivate())
                    expectedPrivateItemIds.add(newToDo.getId());
            }

            int actualCount = repo.countItems();
            int expectedCount = baseFullCount;
            for (int i = 0; i < expectedCounts.length; i++)
                expectedCount += expectedCounts[i];
            assertEquals("Total To Do items in the database",
                    expectedCount, actualCount);

            actualCount = repo.countPrivateItems();
            assertEquals("Total private To Do items in the database",
                    expectedPrivateCount, actualCount);

            actualCount = repo.countEncryptedItems();
            assertEquals("Total encrypted To Do items in the database",
                    expectedEncryptedCount, actualCount);

            for (int i = 0; i < expectedCounts.length; i++) {
                expectedCount = ((testCategoryIds[i] == ToDoCategory.UNFILED)
                        ? baseUnfiledCount : 0) + expectedCounts[i];
                actualCount = repo.countItemsInCategory(testCategoryIds[i]);
                assertEquals("Number of To Do items in category "
                        + testCategoryIds[i], expectedCount, actualCount);
            }

            long[] actualPrivateIds = repo.getPrivateItemIds();
            SortedSet<Long> actualPrivateIdSet = new TreeSet<>();
            for (long id : actualPrivateIds)
                actualPrivateIdSet.add(id);
            assertEquals("Private To Do item ID's",
                    expectedPrivateItemIds, actualPrivateIdSet);
        } finally {
            for (long id : testItemIds)
                repo.deleteItem(id);
            for (long id : testCategoryIds) {
                if (id != ToDoCategory.UNFILED)
                    repo.deleteCategory(id);
            }
        }
    }

    /**
     * Run the assertion part of a test for getting a cursor over To Do
     * items in the database.  This relies on the database being
     * pre-populated with the test items.  As the database may contain
     * other items not created by the test, we need to allow for these in
     * verifying the results.
     *
     * @param categoryId the ID of the category whose items to count,
     * or {@link ToDoPreferences#ALL_CATEGORIES} to include all items.
     * @param includePrivate whether to include private items.
     * @param includeEncrypted whether to include encrypted items.
     * If {@code true}, {@code includePrivate} must also be {@code true}.
     * The provider will <i>not</i> decrypt the items; decryption must be
     * done by the UI.
     * @param sortOrder the order in which to return matching To Do items,
     *                  expressed as one or more field names optionally
     *                  followed by &ldquo;desc&rdquo; suitable for use
     *                  as the object of an {@code ORDER BY} clause.
     * @param expectedItems a LinkedHashMap of the To Do items which we expect
     *                      the cursor to return, keyed by ID, in the order
     *                      they should be read.  If the cursor returns
     *                      any other items, they will be allowed so long
     *                      as they match the category and privacy settings.
     *
     * @throws AssertionError if any part of the test failed
     */
    private void runGetItemsTest(long categoryId,
                                 boolean includePrivate,
                                 boolean includeEncrypted,
                                 String sortOrder,
                                 LinkedHashMap<Long, ToDoItem> expectedItems) {
        ToDoCursor cursor = repo.getItems(categoryId,
                includePrivate, includeEncrypted, sortOrder);
        assertNotNull("Repository returned a null cursor", cursor);
        LinkedHashMap<Long, ToDoItem> actualItems = new LinkedHashMap<>();
        List<Long> actualItemIds = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        try {
            while (cursor.moveToNext()) {
                ToDoItem item = cursor.getItem();
                assertNotNull("Cursor returned null instead of a"
                        + " To Do item at position" + cursor.getPosition(),
                        item);
                actualItems.put(item.getId(), item);
            }
        } finally {
            cursor.close();
        }

        // Check for items which shouldn't be in the results
        for (ToDoItem item : actualItems.values()) {
            if (categoryId != ToDoPreferences.ALL_CATEGORIES) {
                if (item.getCategoryId() != categoryId)
                    errors.add(String.format(
                            "Item %d: Category (ID) expected:%d but was:%d",
                            item.getId(), categoryId, item.getCategoryId()));
            }
            if (!includeEncrypted) {
                if (!includePrivate) {
                    if (item.getPrivate() > 0)
                        errors.add(String.format(
                                "Item %d: Private expected:0 but was:%d",
                                item.getId(), item.getPrivate()));
                } else {
                    if (item.getPrivate() > 1)
                        errors.add(String.format(
                                "Item %d: Private expected:\u22641 but was:%d",
                                item.getId(), item.getPrivate()));
                }
            }
            if (expectedItems.containsKey(item.getId()))
                actualItemIds.add(item.getId());
        }

        try {
            assertEquals(new ArrayList<>(expectedItems.keySet()),
                    actualItemIds);
        } catch (AssertionError ae) {
            errors.add(String.format("Order of the returned items (%s)"
                    + " expected:\n%s\n\nbut was:\n%s", sortOrder,
                    StringUtils.join(expectedItems.values(), "\n"),
                    StringUtils.join(actualItems.values(), "\n")));
        }

        if (errors.isEmpty())
            return;

        if (errors.size() == 1)
            throw new AssertionError(errors.get(0));
        throw new AssertionError("Multiple failures:\n"
                + StringUtils.join(errors, "\n"));
    }

    /**
     * Run the assertion part of the test for
     * {@link ToDoRepository#getPendingAlarms(ZoneId)}.  This relies on
     * the database being pre-populated with test items with or without
     * alarms.  As the database may contain other items not created by
     * the test, we need to allow for these in verifying the results.
     *
     * @param expectedItems a Collection of the To Do items for which
     *                      we expect the repository to return alarms.
     * @throws AssertionError if any part of the test failed
     */
    private void runGetPendingAlarmsTest(Map<Long,ToDoItem> expectedItems) {
        SortedSet<AlarmInfo> expectedAlarms = new TreeSet<>();
        ZoneId timeZone = ZoneOffset.ofHours(RAND.nextInt(24) - 12);
        for (ToDoItem item : expectedItems.values()) {
            AlarmInfo alarm = new AlarmInfo(item);
            alarm.setTimeZone(timeZone);
            expectedAlarms.add(alarm);
        }

        SortedSet<AlarmInfo> allAlarms = repo.getPendingAlarms(timeZone);
        // Check for alarms which shouldn't be in the results;
        // ignore any alarms which are valid but not in the test set.
        SortedSet<AlarmInfo> actualAlarms = new TreeSet<>();
        List<String> errors = new ArrayList<>();
        for (AlarmInfo alarm : allAlarms) {
            if (expectedItems.containsKey(alarm.getId())) {
                actualAlarms.add(alarm);
                continue;
            }
            // Look up the original To Do item
            ToDoItem unexpected = repo.getItemById(alarm.getId());
            if (unexpected == null) {
                errors.add(String.format("Repository returned alarm #%d"
                        + " but has no matching To Do item", alarm.getId()));
                continue;
            }
            if (unexpected.isChecked())
                errors.add(String.format("Repository returned alarm for item"
                        + " %d which is marked done", alarm.getId()));
            if (unexpected.getDue() == null)
                errors.add(String.format("Repository returned alarm for item"
                        + " %d which has no due date", alarm.getId()));
            if (unexpected.getAlarm() == null)
                errors.add(String.format("Repository return alarm for item"
                        + " %d which has no alarm", alarm.getId()));
        }

        try {
            assertEquals("Set of pending alarms",
                    expectedAlarms, actualAlarms);
        } catch (AssertionError ae) {
            errors.add(ae.getMessage());
        }

        if (errors.isEmpty())
            return;

        if (errors.size() == 1)
            throw new AssertionError(errors.get(0));
        throw new AssertionError("Multiple failures:\n"
                + StringUtils.join(errors, "\n"));
    }

    /**
     * Run tests for getting To Do items with a variety of selection
     * parameters.  In order to avoid a lot of time-consuming inserts and
     * deletes, we&rsquo;ll populate the database with test items just
     * once, run all tests for getting items in this one method, then
     * throw any accumulated errors all at once while cleaning up.
     */
    @Test
    public void testGetToDoItems() {
        long[] testCategoryIds = new long[2];
        Arrays.fill(testCategoryIds, ToDoCategory.UNFILED);
        String unfiledName = testContext.getString(R.string.Category_Unfiled);
        int targetLen = RAND.nextInt(12) + 10;
        ToDoCategory testCategory = repo.insertCategory(
                RandomStringUtils.randomAlphabetic(targetLen));
        assertNotNull("Newly inserted category", testCategory);
        assertNotNull("New category ID", testCategory.getId());
        testCategoryIds[1] = testCategory.getId();
        final List<ToDoItem> testToDos = new ArrayList<>();

        try {
            int targetCount = 10 + RAND.nextInt(10);
            while (testToDos.size() < targetCount) {
                ToDoItem item = new ToDoItem();
                item.setCategoryId(testCategoryIds[
                        RAND.nextInt(testCategoryIds.length)]);
                item.setPrivate(RAND.nextInt(4));
                if (item.getPrivate() <= 1) {
                    targetLen = RAND.nextInt(16) + 5;
                    item.setDescription(RandomStringUtils
                            .randomAscii(targetLen));
                } else {
                    byte[] encrypted = new byte[32];
                    RAND.nextBytes(encrypted);
                    item.setEncryptedDescription(encrypted);
                }
                ToDoAlarm alarm = new ToDoAlarm();
                alarm.setTime(LocalTime.ofSecondOfDay(RAND.nextInt(86400)));
                alarm.setAlarmDaysEarlier(RAND.nextInt(10));
                if (RAND.nextBoolean())
                    alarm.setNotificationTime(Instant.now().minusSeconds
                            (RAND.nextInt(7*86400)));
                // There are three conditions for the getPendingAlarms query;
                // about half of the items should satisfy all three,
                // the rest should miss one or more.
                int alarmCheck =RAND.nextInt(15) + 1;
                item.setChecked((alarmCheck & 0x9) == 1);
                if ((alarmCheck & 0xa) != 2)
                    item.setDue(LocalDate.now().plusDays(
                            RAND.nextInt(15) - 7));
                if ((alarmCheck & 0xc) != 4)
                    item.setAlarm(alarm);
                item.setCreateTime(Instant.now()
                        // Make sure items have a variety of
                        // "creation" times for sorting purposes
                        .minusMillis(60000 * RAND.nextInt(7 * 24 * 60)));
                item.setModTime(item.getCreateTime());
                item = repo.insertItem(item);
                assertNotNull("Newly inserted To Do item", item);
                assertNotNull("New item ID", item.getId());
                testToDos.add(item);
                // Ensure we have the category name in the item
                item.setCategoryName((item.getCategoryId() == ToDoCategory.UNFILED)
                        ? unfiledName : testCategory.getName());
            }

            // First, get all items ordered by description (case-sensitive)
            Collections.sort(testToDos, TODO_DESCRIPTION_COMPARATOR);
            LinkedHashMap<Long, ToDoItem> expectedItems = new LinkedHashMap<>();
            for (ToDoItem item : testToDos)
                expectedItems.put(item.getId(), item);
            runGetItemsTest(ToDoPreferences.ALL_CATEGORIES, true, true,
                    ToDoSchema.ToDoItemColumns.DESCRIPTION,
                    expectedItems);

            // Second, get all public items ordered by description
            // (case-insensitive)
            Collections.sort(testToDos, TODO_DESCRIPTION_COMPARATOR_IGNORE_CASE);
            expectedItems.clear();
            for (ToDoItem item : testToDos) {
                if (item.getPrivate() <= 0)
                    expectedItems.put(item.getId(), item);
            }
            runGetItemsTest(ToDoPreferences.ALL_CATEGORIES, false, false,
                    "lower(" + ToDoSchema.ToDoItemColumns.DESCRIPTION + ")",
                    expectedItems);

            // Third, get all unfiled items including private but not encrypted
            // ordered by most recent modification time first
            Collections.sort(testToDos, Collections.reverseOrder(
                    TODO_MOD_TIME_COMPARATOR));
            expectedItems.clear();
            for (ToDoItem item : testToDos) {
                if ((item.getPrivate() <= 1) &&
                        (item.getCategoryId() == ToDoCategory.UNFILED))
                    expectedItems.put(item.getId(), item);
            }
            runGetItemsTest(ToDoCategory.UNFILED, true, false,
                    ToDoSchema.ToDoItemColumns.MOD_TIME + " desc",
                    expectedItems);

            // Fourth, get all items ordered by category name then by
            // creation time (oldest first).
            Collections.sort(testToDos, TODO_CATEGORY_COMPARATOR_IGNORE_CASE
                    .thenComparing(TODO_CREATE_TIME_COMPARATOR));
            expectedItems.clear();
            for (ToDoItem item : testToDos)
                expectedItems.put(item.getId(), item);
            runGetItemsTest(ToDoPreferences.ALL_CATEGORIES, true, true,
                    "lower(" + ToDoSchema.ToDoItemColumns.CATEGORY_NAME
                            + "), " + ToDoSchema.ToDoItemColumns.CREATE_TIME,
                    expectedItems);

            // Fifth, get all items with a pending alarm
            expectedItems.clear();
            for (ToDoItem item : testToDos) {
                if (!item.isChecked() && (item.getDue() != null) &&
                        (item.getAlarm() != null))
                    expectedItems.put(item.getId(), item);
            }
            runGetPendingAlarmsTest(expectedItems);
        }
        finally {
            for (ToDoItem item : testToDos)
                repo.deleteItem(item.getId());
            repo.deleteCategory(testCategoryIds[1]);
        }
    }

    /**
     * Test running a successful transaction.
     * This does a simple insert and ensures that the data is committed.
     */
    @Test
    public void testRunInTransactionSuccessful() {
        int targetLen = RAND.nextInt(20) + 12;
        final String name = RandomStringUtils.randomAlphabetic(targetLen);
        final byte[] value = new byte[10 + RAND.nextInt(20)];
        repo.runInTransaction(new Runnable() {
            @Override
            public void run() {
                repo.upsertMetadata(name, value);
            }
        });
        ToDoMetadata newMetadata = repo.getMetadataByName(name);
        assertNotNull(String.format(
                "Newly inserted metadata \"%s\" not found", name),
                newMetadata);
        repo.deleteMetadata(name);
    }

    /**
     * Test running a failed transaction.
     * This does a simple insert and then throws an exception,
     * and checks that the data is <i>not</i> committed.
     */
    @Test
    public void testRunInTransactionRolledBack() {
        int targetLen = RAND.nextInt(20) + 12;
        final String name = RandomStringUtils.randomAlphabetic(targetLen);
        final byte[] value = new byte[10 + RAND.nextInt(20)];
        SQLException expectedException = new SQLException("Test exception");
        try {
            repo.runInTransaction(new Runnable() {
                @Override
                public void run() {
                    repo.upsertMetadata(name, value);
                    throw expectedException;
                }
            });
        ToDoMetadata newMetadata = repo.getMetadataByName(name);
            assertNull(String.format(
                    "Metadata \"%s\" was committed despite an exception"
                            + " in the transaction", name), newMetadata);
        } catch (AssertionError ae) {
            repo.deleteMetadata(name);
            throw ae;
        } catch (SQLException sx) {
            assertEquals("Thrown exception", expectedException, sx);
        }
    }

}
