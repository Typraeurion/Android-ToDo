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
package com.xmission.trevin.android.todo.service;

import static com.xmission.trevin.android.todo.service.PalmImportWorker.*;
import static com.xmission.trevin.android.todo.util.RandomToDoUtils.*;
import static org.junit.Assert.*;

import android.content.Context;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.Data;
import androidx.work.ListenableWorker.Result;
import androidx.work.testing.TestListenableWorkerBuilder;

import com.xmission.trevin.android.todo.R;
import com.xmission.trevin.android.todo.data.ToDoAlarm;
import com.xmission.trevin.android.todo.data.ToDoCategory;
import com.xmission.trevin.android.todo.data.ToDoItem;
import com.xmission.trevin.android.todo.data.repeat.Months;
import com.xmission.trevin.android.todo.data.repeat.RepeatDaily;
import com.xmission.trevin.android.todo.data.repeat.RepeatMonthlyOnDate;
import com.xmission.trevin.android.todo.data.repeat.RepeatMonthlyOnDay;
import com.xmission.trevin.android.todo.data.repeat.RepeatWeekly;
import com.xmission.trevin.android.todo.data.repeat.RepeatYearlyOnDate;
import com.xmission.trevin.android.todo.data.repeat.WeekDays;
import com.xmission.trevin.android.todo.provider.MockToDoRepository;
import com.xmission.trevin.android.todo.provider.ToDoRepositoryImpl;
import com.xmission.trevin.android.todo.util.StringEncryption;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.*;
import org.junit.runner.RunWith;

import java.io.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Unit tests for importing To Do data from a Palm backup file.
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class PalmImportWorkerTests {

    private static final Random RAND = new Random();

    private Context testContext = null;
    private MockToDoRepository mockRepo = null;
    private StringEncryption globalEncryption = null;

    /**
     * The name of the &ldquo;Unfiled&rdquo; category
     * according to the app&rsquo;s string resources
     */
    private String unfiledName;

    /**
     * Cache our import test files, since a single file may be used
     * in many import tests.
     */
    private Map<String,File> installedFiles = new HashMap<>();

    @Before
    public void initializeRepository() {
        if (testContext == null)
            testContext = InstrumentationRegistry.getInstrumentation()
                    .getTargetContext();
        if (mockRepo == null) {
            mockRepo = MockToDoRepository.getInstance();
            ToDoRepositoryImpl.setInstance(mockRepo);
        }
        mockRepo.open(testContext);
        mockRepo.clear();
        globalEncryption = StringEncryption.holdGlobalEncryption();
        if (unfiledName == null)
            unfiledName = InstrumentationRegistry.getInstrumentation()
                    .getContext().getString(R.string.Category_Unfiled);
    }

    @After
    public void releaseRepository() {
        StringEncryption.releaseGlobalEncryption(testContext);
        mockRepo.release(testContext);
    }

    /**
     * Copy a test Palm Database file from our test assets directory
     * into the app&rsquo;s private file storage.  The file will be
     * marked for deletion at the end of the test.
     *
     * @param sourceFileName the name of the test file to copy
     *
     * @return a {@link File} object referencing the copy that the
     * app can use
     *
     * @throws IOException if there is any error reading the source file
     * or copying it to the destination file
     */
    public File copyTestFile(String sourceFileName) throws IOException {
        if (installedFiles.containsKey(sourceFileName))
            return installedFiles.get(sourceFileName);

        Context testContext = InstrumentationRegistry.getInstrumentation()
                .getContext();
        Context targetContext = InstrumentationRegistry.getInstrumentation()
                .getTargetContext();
        // Generate a random name for the copied file to ensure it
        // doesn't interfere with any prior test or regular app files
        String destFileName = sourceFileName.replaceFirst("\\.xml$", "-")
                + RandomStringUtils.randomAlphanumeric(8) + ".xml";
        File destFile = new File(targetContext.getFilesDir(), destFileName);
        destFile.deleteOnExit();
        InputStream in = testContext.getAssets().open(sourceFileName);
        try {
            OutputStream out = new FileOutputStream(destFile);
            byte[] buffer = new byte[16384];
            try {
                int length = in.read(buffer);
                while (length > 0) {
                    out.write(buffer, 0, length);
                    length = in.read(buffer);
                }
                out.flush();
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
        installedFiles.put(sourceFileName, destFile);
        return destFile;
    }

    /**
     * Run the Palm import worker for a given test file.
     *
     * @param inFileName the name of the Palm &ldquo;<tt>.dat</tt>&rqduo;
     * file to import (relative to the &rdquo;assets/&rdquo; folder).
     * @param importType the type of import to perform.
     * @param expectedResult the class of {@link Result} expected from
     * the import worker.
     *
     * @return the actual {@link Result} returned by the worker
     *
     * @throws AssertionError if the actual result class is not
     * {@code expectedResult}.
     * @throws IOException if there was an error copying the import file
     * from the test context (&ldquo;assets/&rdquo; folder) to the
     * application context&rsquo;s private files directory.
     */
    private Result runImportWorker(String inFileName, ImportType importType,
                                   Class<? extends Result> expectedResult)
            throws AssertionError, IOException {
        File inFile = copyTestFile(inFileName);
        PalmImportWorker worker = TestListenableWorkerBuilder.from(
                        testContext, PalmImportWorker.class)
                .setInputData(new Data.Builder()
                        .putString(PALM_DATA_FILENAME, inFile.getAbsolutePath())
                        .putInt(PALM_IMPORT_TYPE, importType.getIntValue())
                        .build())
                .setRunAttemptCount(1)
                .build();
        try {
            Result result = worker.startWork().get(30, TimeUnit.SECONDS);
            Map<String,Object> data = result.getOutputData().getKeyValueMap();
            if (!data.isEmpty())
                Log.i("Tests", "PalmImportWorker return data: " + data);
            assertEquals("Result of Palm import worker call",
                    expectedResult, result.getClass());
            return result;
        } catch (ExecutionException ee) {
            Log.e("Tests", "Worker threw an exception", ee);
            fail("Worker was aborted: " + ee.getMessage());
            // Unreachable
            return null;
        } catch (InterruptedException ie) {
            Log.e("Tests", "Worker was interrupted", ie);
            fail("Worker was interrupted");
            // Unreachable
            return null;
        } catch (TimeoutException te) {
            Log.e("Tests", "Worker timed out", te);
            fail("Worker timed out after 10 seconds");
            // Unreachable
            return null;
        }
    }

    /**
     * <b>TEMPORARY TEST &mdash;</b> test reading a backup of To Do
     * data from Tungsten T2.  This backup file <i>must not</i>
     * be committed with the source files.
     */
    //Test
    public void testImportT2() throws IOException {
        runImportWorker("todo-v1.dat",
                ImportType.TEST, Result.Success.class);
    }

    /**
     * <b>TEMPORARY TEST &mdash;</b> test reading a backup of To Do
     * data from Tungsten E2.  This backup file <i>must not</i>
     * be committed with the source files.
     */
    //Test
    public void testImportE2() throws IOException {
        runImportWorker("todo-v2.dat",
                ImportType.TEST, Result.Success.class);
    }

    /**
     * Test reading the &ldquo;minimal v1&rdquo; data file generated
     * by {@code PalmTestDataGenerator}.  This is intended to verify
     * the generator is creating valid data files.
     */
    @Test
    public void testImportMinimalV1() throws IOException {
        runImportWorker("Palm-minimal-v1.dat",
                ImportType.TEST, Result.Success.class);
    }

    /**
     * Test reading the &ldquo;minimal v2&rdquo; data file generated
     * by {@code PalmTestDataGenerator}.  This is intended to verify
     * the generator is creating valid data files.
     */
    @Test
    public void testImportMinimalV2() throws IOException {
        runImportWorker("Palm-minimal-v2.dat",
                ImportType.TEST, Result.Success.class);
    }

    /**
     * Test reading the &ldquo;minimal PalmSG-wrapped v1&rdquo; data file
     * generated by {@code PalmTestDataGenerator}.  This is intended to
     * verify the generator is creating valid data files.
     */
    @Test
    public void testImportMinimalSGV1() throws IOException {
        runImportWorker("Palm-minimal-SGv1.dat",
                ImportType.TEST, Result.Success.class);
    }

    /**
     * Test reading the &ldquo;minimal PalmSG-wrapped v2&rdquo; data file
     * generated by {@code PalmTestDataGenerator}.  This is intended to
     * verify the generator is creating valid data files.
     */
    @Test
    public void testImportMinimalSGV2() throws IOException {
        runImportWorker("Palm-minimal-SGv2.dat",
                ImportType.TEST, Result.Success.class);
    }

    /**
     * Categories expected from a clean import of the
     * &ldquo;Palm-categories-v1.dat&rdquo; file
     */
    public static final List<ToDoCategory> TEST_CATEGORIES_1;
    static {
        List<ToDoCategory> list = new ArrayList<>(10);

        ToDoCategory category = new ToDoCategory();
        category.setId(1);
        category.setName("Alpha");
        list.add(category);

        category = new ToDoCategory();
        category.setId(2);
        category.setName("Beta");
        list.add(category);

        category = new ToDoCategory();
        category.setId(3);
        category.setName("Gamma");
        list.add(category);

        category = new ToDoCategory();
        category.setId(4);
        category.setName("Delta");
        list.add(category);

        category = new ToDoCategory();
        category.setId(5);
        category.setName("Epsilon");
        list.add(category);

        category = new ToDoCategory();
        category.setId(6);
        category.setName("Zeta");
        list.add(category);

        category = new ToDoCategory();
        category.setId(7);
        category.setName("Eta");
        list.add(category);

        category = new ToDoCategory();
        category.setId(8);
        category.setName("Theta");
        list.add(category);

        category = new ToDoCategory();
        category.setId(9);
        category.setName("Iota");
        list.add(category);

        category = new ToDoCategory();
        category.setId(10);
        category.setName("Kappa");
        list.add(category);

        TEST_CATEGORIES_1 = Collections.unmodifiableList(list);
    }

    /**
     * Add some random categories to the mock repository.
     * This uses some of the same ID&rsquo;s and names as those
     * in {@link #TEST_CATEGORIES_1}, but not necessarily
     * the same mapping in order to check potential replacements
     * or reassignments.
     *
     * @return a new {@link Map} of the categories that were added
     */
    private SortedMap<Long,String> addRandomCategories() {
        SortedMap<Long,String> testCategories = new TreeMap<>();
        for (int i = 8 + RAND.nextInt(5); i >= 0; --i) {
            ToDoCategory cat = new ToDoCategory();
            do {
                cat.setId(RAND.nextInt(20) + 1);
                if (RAND.nextBoolean())
                    cat.setName(TEST_CATEGORIES_1.get(
                            RAND.nextInt(TEST_CATEGORIES_1.size()))
                            .getName());
                else
                    cat.setName(randomWord() + " " + randomWord());
            }
            // Verify that there are no duplicates within this set
            while (testCategories.containsKey(cat.getId()) ||
                    testCategories.containsValue(cat.getName()));
            mockRepo.insertCategory(cat);
            testCategories.put(cat.getId(), cat.getName());
        }
        return testCategories;
    }

    /**
     * Return the key corresponding to a given value from a {@link Map}
     *
     * @param map the map to search
     * @param value the value to look for
     *
     * @return the key, or {@code null} if {@code value} was not found
     * in {@code map}.
     */
    public static <K,V> K getKey(Map<K,V> map, V value) {
        for (Map.Entry<K,V> entry : map.entrySet())
            if (entry.getValue().equals(value))
                return entry.getKey();
        return null;
    }

    /**
     * Read all categories from the mock repository into a {@link Map}
     *
     * @return the category map
     */
    private SortedMap<Long,String> readCategories() {
        SortedMap<Long,String> categoryMap = new TreeMap<>();
        List<ToDoCategory> categories = mockRepo.getCategories();
        for (ToDoCategory cat : categories)
            categoryMap.put((long) cat.getId(), cat.getName());
        return categoryMap;
    }

    /**
     * Verify the imported categories.
     *
     * @param expectedCategories the categories we expect to be in the
     * database.  This method will add the default &ldquo;Unfiled&rdquo;
     * category.
     *
     * @throws AssertionError if the actual categories do not match
     */
    private void assertCategoriesEquals(Map<Long,String> expectedCategories)
            throws AssertionError {
        SortedMap<Long,String> actualCategories = readCategories();
        assertCategoriesEquals(expectedCategories, actualCategories);
    }

    private void assertCategoriesEquals(
            Map<Long,String> expectedCategories,
            Map<Long,String> actualCategories) throws AssertionError {
        expectedCategories.put((long) ToDoCategory.UNFILED, unfiledName);
        assertEquals("Imported categories",
                expectedCategories, actualCategories);
    }

    /**
     * Test a clean import of categories.
     * All existing categories must be replaced.
     */
    @Test
    public void testImportCategoriesClean() throws IOException {

        addRandomCategories();

        runImportWorker("Palm-categories-v1.dat",
                ImportType.CLEAN, Result.Success.class);

        SortedMap<Long,String> expectedCategories = new TreeMap<>();
        for (ToDoCategory cat : TEST_CATEGORIES_1)
            expectedCategories.put((long) cat.getId(), cat.getName());

        assertCategoriesEquals(expectedCategories);

    }

    /**
     * Test importing categories in {@link ImportType#OVERWRITE OVERWRITE}
     * mode.  If a name conflicts with an existing entry in the database
     * all To Do items using the current category ID will be moved to
     * &ldquo;Unfiled&rdquo;.  If an ID conflicts with an existing entry
     * in the database, the category&rsquo;s name will be replaced.
     */
    @Test
    public void testImportCategoriesOverwrite() throws IOException {

        SortedMap<Long,String> expectedCategories = addRandomCategories();

        // Add one To Do record using a category name that will be replaced
        ToDoCategory dupCatName = null;
        List<Map.Entry<Long,String>> candidates =
                new ArrayList<>(expectedCategories.entrySet());
        while ((dupCatName == null) && !candidates.isEmpty()) {
            Map.Entry<Long,String> entry = candidates.get(
                    RAND.nextInt(candidates.size()));
            for (ToDoCategory cat : TEST_CATEGORIES_1) {
                if (entry.getValue().equals(cat.getName()) &&
                        !entry.getKey().equals(cat.getId())) {
                    dupCatName = new ToDoCategory();
                    dupCatName.setId(entry.getKey());
                    dupCatName.setName(entry.getValue());
                    break;
                }
            }
            if (dupCatName == null)
                candidates.remove(entry);
        }
        if (dupCatName == null) {
            // No useable category was created randomly; force one
            dupCatName = new ToDoCategory();
            dupCatName.setId(RAND.nextInt(10) + 21);
            do {
                dupCatName.setName(TEST_CATEGORIES_1.get(
                        RAND.nextInt(TEST_CATEGORIES_1.size())).getName());
            } while (expectedCategories.containsValue(dupCatName.getName()));
            mockRepo.insertCategory(dupCatName);
            expectedCategories.put(dupCatName.getId(), dupCatName.getName());
        }

        ToDoItem todoDupCatName = randomToDo();
        todoDupCatName.setCategoryId(dupCatName.getId());
        todoDupCatName.setCategoryName(dupCatName.getName());
        mockRepo.insertItem(todoDupCatName);

        // Update our expectation for the item's revision.
        // (The mock repo contains a clone of the item, not our original.)
        todoDupCatName.setCategoryId(ToDoCategory.UNFILED);
        todoDupCatName.setCategoryName(unfiledName);

        // Add one To Do record using a category ID whose name will be changed
        ToDoCategory dupCatId = null;
        candidates.clear();
        candidates.addAll(expectedCategories.entrySet());
        while ((dupCatId == null) && !candidates.isEmpty()) {
            Map.Entry<Long,String> entry = candidates.get(
                    RAND.nextInt(candidates.size()));
            for (ToDoCategory cat : TEST_CATEGORIES_1) {
                if (entry.getKey().equals(cat.getId()) &&
                        !entry.getValue().equals(cat.getName())) {
                    dupCatId = new ToDoCategory();
                    dupCatId.setId(entry.getKey());
                    dupCatId.setName(entry.getValue());
                    break;
                }
            }
        }
        if (dupCatId == null) {
            // No useable category was created randomly; force one
            dupCatId = new ToDoCategory();
            do {
                dupCatId.setId(RAND.nextInt(10) + 1);
                dupCatId.setName(randomWord() + " " + randomWord());
            } while (expectedCategories.containsKey(dupCatId.getId()) ||
                    expectedCategories.containsValue(dupCatId.getName()));
            mockRepo.insertCategory(dupCatId);
            expectedCategories.put(dupCatId.getId(), dupCatId.getName());
        }

        ToDoItem todoDupCatId = randomToDo();
        todoDupCatId.setCategoryId(dupCatId.getId());
        todoDupCatId.setCategoryName(dupCatId.getName());
        mockRepo.insertItem(todoDupCatId);

        for (ToDoCategory cat : TEST_CATEGORIES_1) {
            if (cat.getId().equals(todoDupCatId.getId())) {
                todoDupCatId.setCategoryName(cat.getName());
                break;
            }
        }

        runImportWorker("Palm-categories-v1.dat",
                ImportType.OVERWRITE, Result.Success.class);

        // Set our category expectations
        for (ToDoCategory cat : TEST_CATEGORIES_1) {
            if (expectedCategories.containsValue(cat.getName())) {
                Long id = getKey(expectedCategories, cat.getName());
                expectedCategories.remove(id);
            }
            expectedCategories.put(cat.getId(), cat.getName());
        }

        assertCategoriesEquals(expectedCategories);

        // Verify the category of the first To Do item
        // was changed to Unfiled
        ToDoItem actualItem = mockRepo.getItemById(todoDupCatName.getId());
        assertEquals("To Do item formerly in category "
                + dupCatName.getName(), todoDupCatName, actualItem);

        // Verify the category of the second To Do item
        // just had its name changed
        actualItem = mockRepo.getItemById(todoDupCatId.getId());
        assertEquals("To To item with category #" + dupCatId.getId(),
                todoDupCatId, actualItem);

    }

    /**
     * Test importing categories in {@link ImportType#MERGE} or
     * {@link ImportType#ADD} mode; the effect of both of these are
     * the same.  If we already have a category of the same name but a
     * different ID, the importer should use the existing ID.  If we have
     * another category with the same ID as one being imported, the
     * imported category will get a new ID.  Otherwise we&rsquo;ll try
     * to add the category with its given ID.
     */
    private void testImportCategoriesMergeOrAdd(ImportType importType)
        throws IOException {

        SortedMap<Long,String> expectedCategories = addRandomCategories();

        // Add one To Do record using a category name that has a different ID
        ToDoCategory dupCatName = null;
        List<Map.Entry<Long,String>> candidates =
                new ArrayList<>(expectedCategories.entrySet());
        while ((dupCatName == null) && !candidates.isEmpty()) {
            Map.Entry<Long,String> entry = candidates.get(
                    RAND.nextInt(candidates.size()));
            for (ToDoCategory cat : TEST_CATEGORIES_1) {
                if (entry.getValue().equals(cat.getName()) &&
                        !entry.getKey().equals(cat.getId())) {
                    dupCatName = new ToDoCategory();
                    dupCatName.setId(entry.getKey());
                    dupCatName.setName(entry.getValue());
                    break;
                }
            }
            if (dupCatName == null)
                candidates.remove(entry);
        }
        if (dupCatName == null) {
            // No useable category was created randomly; force one
            dupCatName = new ToDoCategory();
            dupCatName.setId(RAND.nextInt(10) + 21);
            do {
                dupCatName.setName(TEST_CATEGORIES_1.get(
                        RAND.nextInt(TEST_CATEGORIES_1.size())).getName());
            } while (expectedCategories.containsValue(dupCatName.getName()));
            mockRepo.insertCategory(dupCatName);
            expectedCategories.put(dupCatName.getId(), dupCatName.getName());
        }

        ToDoItem todoDupCatName = randomToDo();
        todoDupCatName.setCategoryId(dupCatName.getId());
        todoDupCatName.setCategoryName(dupCatName.getName());
        mockRepo.insertItem(todoDupCatName);

        // Add one To Do record using a category ID that conflicts
        ToDoCategory dupCatId = null;
        candidates.clear();
        candidates.addAll(expectedCategories.entrySet());
        while ((dupCatId == null) && !candidates.isEmpty()) {
            Map.Entry<Long,String> entry = candidates.get(
                    RAND.nextInt(candidates.size()));
            for (ToDoCategory cat : TEST_CATEGORIES_1) {
                if (entry.getKey().equals(cat.getId()) &&
                        !entry.getValue().equals(cat.getName())) {
                    dupCatId = new ToDoCategory();
                    dupCatId.setId(entry.getKey());
                    dupCatId.setName(entry.getValue());
                    break;
                }
            }
        }
        if (dupCatId == null) {
            // No useable category was created randomly; force one
            dupCatId = new ToDoCategory();
            do {
                dupCatId.setId(RAND.nextInt(10) + 1);
                dupCatId.setName(randomWord() + " " + randomWord());
            } while (expectedCategories.containsKey(dupCatId.getId()) ||
                    expectedCategories.containsValue(dupCatId.getName()));
            mockRepo.insertCategory(dupCatId);
            expectedCategories.put(dupCatId.getId(), dupCatId.getName());
        }

        ToDoItem todoDupCatId = randomToDo();
        todoDupCatId.setCategoryId(dupCatId.getId());
        todoDupCatId.setCategoryName(dupCatId.getName());
        mockRepo.insertItem(todoDupCatId);

        // We won't know the ID's to expect for conflicting category
        // ID's until after the import.  Save these for later.
        Map<Long,String> expectedMoves = new TreeMap<>();
        for (ToDoCategory cat : TEST_CATEGORIES_1) {
            if (expectedCategories.containsValue(cat.getName()))
                continue;  // This should not change any existing categories
            if (expectedCategories.containsKey(cat.getId())) {
                expectedMoves.put(cat.getId(), cat.getName());
                continue;
            }
            expectedCategories.put(cat.getId(), cat.getName());
        }

        runImportWorker("Palm-categories-v1.dat",
                importType, Result.Success.class);

        // Find the new ID's of categories which should have been moved
        SortedMap<Long,String> actualCategories = readCategories();
        for (Long id : expectedMoves.keySet()) {
            String catName = expectedMoves.get(id);
            assertTrue(String.format("Category \"%s\" was not imported"
                    + " (conflicting with category #%d)", catName, id),
                    actualCategories.containsValue(catName));
            // Add this to our full map of expected categories
            expectedCategories.put(
                    getKey(actualCategories, catName), catName);
        }

        // Now we can compare the full category maps
        assertCategoriesEquals(expectedCategories, actualCategories);

        // The To Do items should not have been changed
        ToDoItem actualItem = mockRepo.getItemById(todoDupCatName.getId());
        assertEquals(String.format("To Do item in category #%d \"%s\"",
                dupCatName.getId(), dupCatName.getName()),
                todoDupCatName, actualItem);

        actualItem = mockRepo.getItemById(todoDupCatId.getId());
        assertEquals(String.format("To Do item in category #%d \"%s\"",
                dupCatId.getId(), dupCatId.getName()),
                todoDupCatId, actualItem);

    }

    @Test
    public void testImportCategoriesMerge() throws IOException {
        testImportCategoriesMergeOrAdd(ImportType.MERGE);
    }

    @Test
    public void testImportCategoriesAdd() throws IOException {
        testImportCategoriesMergeOrAdd(ImportType.ADD);
    }

    /**
     * Categories used by the &ldquo;Palm-todos-v1.dat&rdquo; file
     */
    public static final List<ToDoCategory> TEST_CATEGORIES_2;
    static {
        List<ToDoCategory> list = new ArrayList<>(3);

        ToDoCategory category = new ToDoCategory();
        category.setId(4);
        category.setName("Maintenance");
        list.add(category);

        category = new ToDoCategory();
        category.setId(6);
        category.setName("Shopping List");
        list.add(category);

        category = new ToDoCategory();
        category.setId(7);
        category.setName("Work Tasks");
        list.add(category);

        category = new ToDoCategory();
        category.setId(16);
        category.setName("Top Secret Missions");

        TEST_CATEGORIES_2 = Collections.unmodifiableList(list);
    }

    /**
     * To Do records expected from a clean import of the
     * &ldquo;Palm-todos-v1.dat&rdquo; file
     */
    public static final List<ToDoItem> TEST_TODOS_1;
    static {
        List<ToDoItem> list = new ArrayList<>(7);

        ToDoItem todo = new ToDoItem();
        todo.setId(1);
        todo.setDescription("Write \"Hello, world\"");
        todo.setCategoryId(ToDoCategory.UNFILED);
        todo.setCategoryName(InstrumentationRegistry.getInstrumentation()
                .getContext().getString(R.string.Category_Unfiled));
        todo.setPriority(7);
        list.add(todo);

        todo = new ToDoItem();
        todo.setId(6);
        todo.setDescription("Milk");
        todo.setDue(LocalDate.of(2026, 1, 14));
        todo.setChecked(true);
        todo.setPriority(1);
        todo.setCategoryId(TEST_CATEGORIES_2.get(1).getId());
        todo.setCategoryName(TEST_CATEGORIES_2.get(1).getName());
        list.add(todo);

        todo = new ToDoItem();
        todo.setId(13);
        todo.setDescription("Batteries");
        todo.setDue(LocalDate.of(2025, 12, 13));
        todo.setChecked(true);
        todo.setPriority(2);
        todo.setCategoryId(TEST_CATEGORIES_2.get(1).getId());
        todo.setCategoryName(TEST_CATEGORIES_2.get(1).getName());
        todo.setNote("8x AA, 6x AAA, 4x C, 2x D");
        list.add(todo);

        todo = new ToDoItem();
        todo.setId(18);
        todo.setDescription("TPS Report");
        todo.setDue(LocalDate.of(1999, 2, 13));
        todo.setChecked(false);
        todo.setPriority(5);
        todo.setCategoryId(TEST_CATEGORIES_2.get(2).getId());
        todo.setCategoryName(TEST_CATEGORIES_2.get(2).getName());
        todo.setNote("Bill needs you to rewrite the cover sheet.");
        list.add(todo);

        todo = new ToDoItem();
        todo.setId(34);
        todo.setDescription("File vacation request");
        todo.setPriority(4);
        todo.setCategoryId(TEST_CATEGORIES_2.get(2).getId());
        todo.setCategoryName(TEST_CATEGORIES_2.get(2).getName());
        list.add(todo);

        todo = new ToDoItem();
        todo.setId(165);
        todo.setDescription("Find Dr. No");
        todo.setPriority(1);
        todo.setPrivate(1);
        todo.setCategoryId(TEST_CATEGORIES_2.get(3).getId());
        todo.setCategoryName(TEST_CATEGORIES_2.get(3).getName());
        todo.setNote("John Strangways and his secretary have been murdered"
                + " in Jamaica.\nHe was helping the CIA investigate radio"
                + " jamming of rocket launches from Cape Canaveral.\n");
        list.add(todo);

        todo = new ToDoItem();
        todo.setId(234);
        todo.setDescription("Identify members of SPECTRE");
        todo.setDue(LocalDate.of(2021, 9, 28));
        todo.setChecked(true);
        todo.setPriority(3);
        todo.setPrivate(1);
        todo.setCategoryId(TEST_CATEGORIES_2.get(3).getId());
        todo.setCategoryName(TEST_CATEGORIES_2.get(3).getName());
        todo.setNote("Most members presumed killed as of the release of"
                + " nanobots from Cuba.\nBlofeld eliminated at"
                + " Belmarsh.\nRemaining survivors destroyed along"
                + " with nanobots on Safin's island.\nAgent was"
                + " lost in the line of duty.");
        list.add(todo);

        TEST_TODOS_1 = Collections.unmodifiableList(list);
    }

    /**
     * To Do records expected from a clean import of the
     * &ldquo;Palm-todos-SGv2.dat&rdquo; file
     */
    public static final List<ToDoItem> TEST_TODOS_2;
    static {
        List<ToDoItem> list = new ArrayList<>(1);

        ToDoItem todo = new ToDoItem();
        todo.setId(2);
        todo.setDescription("Define categories");
        todo.setCategoryId(ToDoCategory.UNFILED);
        todo.setCategoryName(InstrumentationRegistry.getInstrumentation()
                .getContext().getString(R.string.Category_Unfiled));
        todo.setPriority(10);
        list.add(todo);

        todo = new ToDoItem();
        todo.setId(7);
        todo.setDescription("Change smoke detector batteries");
        todo.setCategoryId(TEST_CATEGORIES_2.get(0).getId());
        todo.setCategoryName(TEST_CATEGORIES_2.get(0).getName());
        todo.setDue(LocalDate.of(2026, 4, 10));
        todo.setChecked(false);
        todo.setPriority(2);
        todo.setCompleted(LocalDateTime.of(2025, 10, 10, 15, 33)
                .toInstant(ZoneOffset.UTC));
        RepeatMonthlyOnDate rmdt = new RepeatMonthlyOnDate();
        rmdt.setIncrement(6);
        rmdt.setDate(10);
        todo.setRepeatInterval(rmdt);
        list.add(todo);

        todo = new ToDoItem();
        todo.setId(42);
        todo.setDescription("Sell shares of PAQB");
        todo.setCategoryId(TEST_CATEGORIES_2.get(2).getId());
        todo.setCategoryName(TEST_CATEGORIES_2.get(2).getName());
        todo.setDue(LocalDate.of(2026, 7, 7));
        todo.setPriority(4);
        ToDoAlarm alarm = new ToDoAlarm();
        alarm.setTime(LocalTime.of(17, 50));
        alarm.setAlarmDaysEarlier(3);
        todo.setAlarm(alarm);
        list.add(todo);

        todo = new ToDoItem();
        todo.setId(67);
        todo.setDescription("Check all pressure gauges");
        todo.setCategoryId(TEST_CATEGORIES_2.get(2).getId());
        todo.setCategoryName(TEST_CATEGORIES_2.get(2).getName());
        todo.setDue(LocalDate.of(2026, 1, 15));
        todo.setPriority(3);
        todo.setCompleted(LocalDateTime.of(2026, 1, 14, 11, 0)
                .toInstant(ZoneOffset.UTC));
        RepeatDaily rd = new RepeatDaily();
        rd.setIncrement(1);
        rd.setEnd(LocalDate.of(2026, 12, 24));
        todo.setRepeatInterval(rd);
        list.add(todo);

        todo = new ToDoItem();
        todo.setId(133);
        todo.setDescription("Gym workout");
        todo.setCategoryId(ToDoCategory.UNFILED);
        todo.setCategoryName(InstrumentationRegistry.getInstrumentation()
                .getContext().getString(R.string.Category_Unfiled));
        todo.setDue(LocalDate.of(2026, 1, 3));
        todo.setPriority(5);
        RepeatWeekly rw = new RepeatWeekly();
        rw.setIncrement(1);
        rw.setWeekDays(Set.of(WeekDays.TUESDAY,
                WeekDays.THURSDAY, WeekDays.SATURDAY));
        todo.setRepeatInterval(rw);
        list.add(todo);

        todo = new ToDoItem();
        todo.setId(226);
        todo.setDescription("Leap day!");
        todo.setCategoryId(ToDoCategory.UNFILED);
        todo.setCategoryName(InstrumentationRegistry.getInstrumentation()
                .getContext().getString(R.string.Category_Unfiled));
        todo.setDue(LocalDate.of(2028, 2, 29));
        todo.setPriority(99);
        RepeatYearlyOnDate rydt = new RepeatYearlyOnDate();
        rydt.setIncrement(4);
        rydt.setMonth(Months.FEBRUARY);
        rydt.setDate(29);
        todo.setRepeatInterval(rydt);
        list.add(todo);

        todo = new ToDoItem();
        todo.setId(242);
        todo.setPrivate(1);
        todo.setDescription("Submit mission report");
        todo.setDue(LocalDate.of(2026, 1, 30));
        todo.setPriority(86);
        RepeatMonthlyOnDay rmdy = new RepeatMonthlyOnDay();
        rmdy.setWeek(4);
        rmdy.setDay(WeekDays.FRIDAY);
        todo.setRepeatInterval(rmdy);
        list.add(todo);

        TEST_TODOS_2 = Collections.unmodifiableList(list);
    }

}
