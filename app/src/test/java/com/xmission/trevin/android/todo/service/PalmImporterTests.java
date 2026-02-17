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

import static com.xmission.trevin.android.todo.util.RandomToDoUtils.*;
import static org.junit.Assert.*;

import com.xmission.trevin.android.todo.data.*;
import com.xmission.trevin.android.todo.data.repeat.*;
import com.xmission.trevin.android.todo.provider.MockToDoRepository;
import com.xmission.trevin.android.todo.provider.ToDoCursor;
import com.xmission.trevin.android.todo.provider.ToDoRepositoryImpl;
import com.xmission.trevin.android.todo.provider.ToDoSchema;
import com.xmission.trevin.android.todo.service.PalmImporter.ImportType;
import com.xmission.trevin.android.todo.util.StringEncryption;

import org.apache.commons.lang3.StringUtils;
import org.junit.*;

import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Unit tests for importing To Do data from a Palm backup file.
 */
public class PalmImporterTests {

    private static final Random RAND = new Random();

    private MockToDoRepository mockRepo = null;
    private StringEncryption globalEncryption = null;

    /**
     * The name of the &ldquo;Unfiled&rdquo; category
     * according to the app&rsquo;s string resources
     */
    private String unfiledName;

    @Before
    public void initializeRepository() {
        if (mockRepo == null) {
            mockRepo = MockToDoRepository.getInstance();
        }
        mockRepo.clear();
        globalEncryption = StringEncryption.holdGlobalEncryption();
        if (unfiledName == null)
            unfiledName = mockRepo.getCategoryById(
                    ToDoCategory.UNFILED).getName();
    }

    @After
    public void releaseRepository() {
        StringEncryption.releaseGlobalEncryption();
    }

    /**
     * Run the Palm importer for a given test file.  The operation is
     * expected to run successfully.
     *
     * @param inFileName the name of the Palm &ldquo;<tt>.dat</tt>&rqduo;
     * file to import (relative to the &rdquo;assets/&rdquo; folder).
     * @param importType the type of import to perform.
     * @param importPrivate whether to include private records.
     *
     * @return the progress indicator at the end of the import
     *
     * @throws IOException if there was an error copying the import file
     * from the test context (&ldquo;assets/&rdquo; folder) to the
     * application context&rsquo;s private files directory.
     */
    private MockProgressBar runImportWorker(
            String inFileName, ImportType importType, boolean importPrivate)
            throws IOException {
        String currentPassword = null;
        if (globalEncryption.hasKey())
            currentPassword = new String(globalEncryption.getPassword());
        MockProgressBar progress = new MockProgressBar();
        if (!inFileName.startsWith("/"))
            inFileName = "/" + inFileName;
        InputStream inStream = getClass().getResourceAsStream(inFileName);
        assertNotNull(String.format("Import test file %s not found",
                inFileName), inStream);
        try {
            PalmImporter.importData(mockRepo, inFileName, inStream,
                    importType, importPrivate, currentPassword, progress);
            progress.setEndTime();
        } finally {
            inStream.close();
        }
        return progress;
    }

    /**
     * <b>TEMPORARY TEST &mdash;</b> test reading a backup of To Do
     * data from Tungsten T2.  This backup file <i>must not</i>
     * be committed with the source files.
     */
    //Test
    public void testImportT2() throws IOException {
        runImportWorker("todo-v1.dat", ImportType.TEST, true);
    }

    /**
     * <b>TEMPORARY TEST &mdash;</b> test reading a backup of To Do
     * data from Tungsten E2.  This backup file <i>must not</i>
     * be committed with the source files.
     */
    //Test
    public void testImportE2() throws IOException {
        runImportWorker("todo-v2.dat", ImportType.TEST, true);
    }

    /**
     * Test reading a file with no To Do records.
     * This should result in a failure.
     */
    @Test
    public void testImportEmpty() {
        try {
            runImportWorker("Palm-empty-v1.dat", ImportType.TEST, false);
            fail("Expected StreamCorruptedException,"
                    + " but importer completed successfully");
        }
        catch (StreamCorruptedException scx) {
            assertTrue("Exception message expected:"
                    + " \"No To Do records were found\" but was: \""
                    + scx.getMessage() + "\"",
                    scx.getMessage().contains("No To Do records were found"));
        }
        catch (Exception e) {
            assertEquals("Exception thrown",
                    StreamCorruptedException.class, e.getClass());
        }
    }

    /**
     * Test reading the &ldquo;minimal v1&rdquo; data file generated
     * by {@code PalmTestDataGenerator}.  This is intended to verify
     * the generator is creating valid data files.
     */
    @Test
    public void testImportMinimalV1() throws IOException {
        runImportWorker("Palm-minimal-v1.dat", ImportType.TEST, true);
    }

    /**
     * Test reading the &ldquo;minimal v2&rdquo; data file generated
     * by {@code PalmTestDataGenerator}.  This is intended to verify
     * the generator is creating valid data files.
     */
    @Test
    public void testImportMinimalV2() throws IOException {
        runImportWorker("Palm-minimal-v2.dat", ImportType.TEST, true);
    }

    /**
     * Test reading the &ldquo;minimal PalmSG-wrapped v1&rdquo; data file
     * generated by {@code PalmTestDataGenerator}.  This is intended to
     * verify the generator is creating valid data files.
     */
    @Test
    public void testImportMinimalSGV1() throws IOException {
        runImportWorker("Palm-minimal-SGv1.dat", ImportType.TEST, true);
    }

    /**
     * Test reading the &ldquo;minimal PalmSG-wrapped v2&rdquo; data file
     * generated by {@code PalmTestDataGenerator}.  This is intended to
     * verify the generator is creating valid data files.
     */
    @Test
    public void testImportMinimalSGV2() throws IOException {
        runImportWorker("Palm-minimal-SGv2.dat", ImportType.TEST, true);
    }

    /**
     * Test reading the &ldquo;maximal v1&rdquo; data file generated
     * by {@code PalmTestDataGenerator}.  This is intended to verify
     * the generator is creating valid data files when all optional
     * version 1 fields are present.
     */
    @Test
    public void testImportMaximalV1() throws IOException {
        runImportWorker("Palm-maximal-v1.dat", ImportType.TEST, true);
    }

    /**
     * Test reading the v2 data file generated by
     * {@code PalmTestDataGenerator} which has all scalar version
     * 2 fields set.  (Does not include repeat fields.)
     */
    @Test
    public void testImportAlarmV2() throws IOException {
        runImportWorker("Palm-alarm-v2.dat", ImportType.TEST, true);
    }

    /**
     * Test reading the v2 data file generated by
     * {@code PalmTestDataGenerator} which has a repeat event
     * by day (of week).
     */
    @Test
    public void testImportRepeatByDayV2() throws IOException {
        runImportWorker("Palm-repeat_on_day-v2.dat", ImportType.TEST, true);
    }

    /**
     * Test reading the v2 data file generated by
     * {@code PalmTestDataGenerator} which has a repeat event
     * by week (on given days).
     */
    @Test
    public void testImportRepeatByWeekV2() throws IOException {
        runImportWorker("Palm-repeat_by_week-v2.dat", ImportType.TEST, true);
    }

    /**
     * Test reading the v2 data file generated by
     * {@code PalmTestDataGenerator} which has a repeat event
     * by month (on a day of a week).
     */
    @Test
    public void testImportRepeatByMonthOnDayV2() throws IOException {
        runImportWorker("Palm-repeat_monthly_on_day-v2.dat",
                ImportType.TEST, true);
    }

    /**
     * Test reading the v2 data file generated by
     * {@code PalmTestDataGenerator} which has a repeat event
     * by month (on a date).
     */
    @Test
    public void testImportRepeatByMonthOnDateV2() throws IOException {
        runImportWorker("Palm-repeat_monthly_on_date-v2.dat",
                ImportType.TEST, true);
    }

    /**
     * Test reading the v2 data file generated by
     * {@code PalmTestDataGenerator} which has a repeat event
     * by year (on a month and date).
     */
    @Test
    public void testImportRepeatByYearV2() throws IOException {
        runImportWorker("Palm-repeat_yearly-v2.dat", ImportType.TEST, true);
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
            categoryMap.put(cat.getId(), cat.getName());
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

        MockProgressBar progress = runImportWorker(
                "Palm-categories-v1.dat", ImportType.CLEAN, true);

        SortedMap<Long,String> expectedCategories = new TreeMap<>();
        for (ToDoCategory cat : TEST_CATEGORIES_1)
            expectedCategories.put(cat.getId(), cat.getName());

        assertCategoriesEquals(expectedCategories);

        MockProgressBar.Progress finalProgress = progress.getEndProgress();
        assertNotNull("Progress meter was not set", finalProgress);
        // The test file includes 1 To Do record to avoid a no-data error.
        assertEquals("Total number of records in input file",
                TEST_CATEGORIES_1.size() + 1, finalProgress.total);
        assertEquals("Total number of records processed",
                TEST_CATEGORIES_1.size() + 1, finalProgress.current);

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
            // No usable category was created randomly; force one
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
                    // No imported categories are valid candidates
                    boolean newName = false;
                    for (ToDoCategory cat2 : TEST_CATEGORIES_1) {
                        if (entry.getValue().equals(cat2.getName())) {
                            newName = true;
                            break;
                        }
                    }
                    if (newName) {
                        candidates.remove(entry);
                        continue;
                    }
                    dupCatId = new ToDoCategory();
                    dupCatId.setId(entry.getKey());
                    dupCatId.setName(entry.getValue());
                    break;
                }
            }
        }
        if (dupCatId == null) {
            // No usable category was created randomly; force one
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
            if (cat.getId().equals(dupCatId.getId())) {
                todoDupCatId.setCategoryName(cat.getName());
                break;
            }
        }

        Instant start = Instant.now();
        runImportWorker("Palm-categories-v1.dat", ImportType.OVERWRITE, true);
        Instant stop = Instant.now();

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
        List<String> diffs = compareToDoRecords(
                "To Do item formerly in category " + dupCatName.getName(),
                todoDupCatName, actualItem, start, stop);
        if (!diffs.isEmpty())
            fail(StringUtils.join("\n", diffs));

        // Verify the category of the second To Do item
        // just had its name changed
        actualItem = mockRepo.getItemById(todoDupCatId.getId());
        diffs = compareToDoRecords(
                "To Do item with category #" + dupCatId.getId(),
                todoDupCatId, actualItem, start, stop);
        if (!diffs.isEmpty())
            fail(StringUtils.join("\n", diffs));

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
            // No usable category was created randomly; force one
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
            // No usable category was created randomly; force one
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

        Instant start = Instant.now();
        runImportWorker("Palm-categories-v1.dat", importType, true);
        Instant stop = Instant.now();

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
        List<String> diffs = compareToDoRecords(String.format(
                        "To Do item in category #%d \"%s\"",
                        dupCatName.getId(), dupCatName.getName()),
                todoDupCatName, actualItem, start, stop);
        if (!diffs.isEmpty())
            fail(StringUtils.join("\n", diffs));

        actualItem = mockRepo.getItemById(todoDupCatId.getId());
        diffs = compareToDoRecords(String.format(
                        "To Do item in category #%d \"%s\"",
                        dupCatId.getId(), dupCatId.getName()),
                todoDupCatId, actualItem, start, stop);
        if (!diffs.isEmpty())
            fail(StringUtils.join("\n", diffs));

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
     * Categories used by the &ldquo;Palm-todos-v1.dat&rdquo;
     * and &ldquo;Palm-todos-SGv2.dat&rdquo; files.
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
        list.add(category);

        TEST_CATEGORIES_2 = Collections.unmodifiableList(list);
    }

    /**
     * To Do records expected from a clean import of the
     * &ldquo;Palm-todos-v1.dat&rdquo; file.  The created and
     * last modified times are set to {@link Instant#MIN} as a
     * placeholder; these should be set to the import time.
     */
    public static final List<ToDoItem> TEST_TODOS_1;
    static {
        List<ToDoItem> list = new ArrayList<>(7);

        ToDoItem todo = new ToDoItem();
        todo.setId(1);
        todo.setDescription("Write \"Hello, world\"");
        todo.setCategoryId(ToDoCategory.UNFILED);
        todo.setCategoryName(MockToDoRepository.unfiledCategoryName);
        todo.setPriority(7);
        todo.setCreateTime(Instant.MIN);
        todo.setModTime(Instant.MIN);
        list.add(todo);

        todo = new ToDoItem();
        todo.setId(6);
        todo.setDescription("Milk");
        todo.setDue(LocalDate.of(2026, 1, 14));
        todo.setChecked(true);
        todo.setPriority(1);
        todo.setCategoryId(TEST_CATEGORIES_2.get(1).getId());
        todo.setCategoryName(TEST_CATEGORIES_2.get(1).getName());
        todo.setCreateTime(Instant.MIN);
        todo.setModTime(Instant.MIN);
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
        todo.setCreateTime(Instant.MIN);
        todo.setModTime(Instant.MIN);
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
        todo.setCreateTime(Instant.MIN);
        todo.setModTime(Instant.MIN);
        list.add(todo);

        todo = new ToDoItem();
        todo.setId(34);
        todo.setDescription("File vacation request");
        todo.setPriority(4);
        todo.setCategoryId(TEST_CATEGORIES_2.get(2).getId());
        todo.setCategoryName(TEST_CATEGORIES_2.get(2).getName());
        todo.setCreateTime(Instant.MIN);
        todo.setModTime(Instant.MIN);
        list.add(todo);

        todo = new ToDoItem();
        todo.setId(165);
        todo.setDescription("Find Dr. No");
        todo.setPriority(1);
        todo.setPrivate(StringEncryption.NO_ENCRYPTION);
        todo.setCategoryId(TEST_CATEGORIES_2.get(3).getId());
        todo.setCategoryName(TEST_CATEGORIES_2.get(3).getName());
        todo.setNote("John Strangways and his secretary have been murdered"
                + " in Jamaica.\nHe was helping the CIA investigate radio"
                + " jamming of rocket launches from Cape Canaveral.\n");
        todo.setCreateTime(Instant.MIN);
        todo.setModTime(Instant.MIN);
        list.add(todo);

        todo = new ToDoItem();
        todo.setId(234);
        todo.setDescription("Identify members of SPECTRE");
        todo.setDue(LocalDate.of(2021, 9, 28));
        todo.setChecked(true);
        todo.setPriority(3);
        todo.setPrivate(StringEncryption.NO_ENCRYPTION);
        todo.setCategoryId(TEST_CATEGORIES_2.get(3).getId());
        todo.setCategoryName(TEST_CATEGORIES_2.get(3).getName());
        todo.setNote("Most members presumed killed as of the release of"
                + " nanobots from Cuba.\nBlofeld eliminated at"
                + " Belmarsh.\nRemaining survivors destroyed along"
                + " with nanobots on Safin's island.\nAgent was"
                + " lost in the line of duty.");
        todo.setCreateTime(Instant.MIN);
        todo.setModTime(Instant.MIN);
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
        todo.setCategoryName(MockToDoRepository.unfiledCategoryName);
        todo.setPriority(10);
        todo.setCreateTime(Instant.MIN);
        todo.setModTime(Instant.MIN);
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
        todo.setCreateTime(Instant.MIN);
        todo.setModTime(Instant.MIN);
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
        todo.setCreateTime(Instant.MIN);
        todo.setModTime(Instant.MIN);
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
        todo.setCreateTime(Instant.MIN);
        todo.setModTime(Instant.MIN);
        list.add(todo);

        todo = new ToDoItem();
        todo.setId(133);
        todo.setDescription("Gym workout");
        todo.setCategoryId(ToDoCategory.UNFILED);
        todo.setCategoryName(MockToDoRepository.unfiledCategoryName);
        todo.setDue(LocalDate.of(2026, 1, 3));
        todo.setPriority(5);
        RepeatWeekly rw = new RepeatWeekly();
        rw.setIncrement(1);
        rw.setWeekDays(Set.of(WeekDays.TUESDAY,
                WeekDays.THURSDAY, WeekDays.SATURDAY));
        todo.setRepeatInterval(rw);
        todo.setCreateTime(Instant.MIN);
        todo.setModTime(Instant.MIN);
        list.add(todo);

        todo = new ToDoItem();
        todo.setId(226);
        todo.setDescription("Leap day!");
        todo.setCategoryId(ToDoCategory.UNFILED);
        todo.setCategoryName(MockToDoRepository.unfiledCategoryName);
        todo.setDue(LocalDate.of(2028, 2, 29));
        todo.setPriority(99);
        RepeatYearlyOnDate rydt = new RepeatYearlyOnDate();
        rydt.setIncrement(4);
        rydt.setMonth(Months.FEBRUARY);
        rydt.setDate(29);
        todo.setRepeatInterval(rydt);
        todo.setCreateTime(Instant.MIN);
        todo.setModTime(Instant.MIN);
        list.add(todo);

        todo = new ToDoItem();
        todo.setId(242);
        todo.setPrivate(StringEncryption.NO_ENCRYPTION);
        todo.setDescription("Submit mission report");
        todo.setCategoryId(TEST_CATEGORIES_2.get(3).getId());
        todo.setCategoryName(TEST_CATEGORIES_2.get(3).getName());
        todo.setDue(LocalDate.of(2026, 1, 30));
        todo.setPriority(86);
        RepeatMonthlyOnDay rmdy = new RepeatMonthlyOnDay();
        rmdy.setWeek(-1);
        rmdy.setDay(WeekDays.FRIDAY);
        todo.setRepeatInterval(rmdy);
        todo.setCreateTime(Instant.MIN);
        todo.setModTime(Instant.MIN);
        list.add(todo);

        TEST_TODOS_2 = Collections.unmodifiableList(list);
    }

    /**
     * Add all of the categories from {@link #TEST_CATEGORIES_1} to the
     * mock repository and an assortment of random To Do items, some of
     * which will have conflicting ID&rsquo;s with records in
     * {@link #TEST_TODOS_1} and {@link #TEST_TODOS_2} and some will
     * have categories whose ID&rsquo;s conflict with records in
     * {@link #TEST_CATEGORIES_2}.
     *
     * @return a {@link List} of the To Do records that were added
     */
    private List<ToDoItem> addRandomToDos() {
        for (ToDoCategory cat : TEST_CATEGORIES_1)
            mockRepo.insertCategory(cat);

        List<ToDoItem> newItems = new ArrayList<>();
        Set<Long> usedIds = new HashSet<>();
        for (ToDoItem todo : TEST_TODOS_1)
            usedIds.add(todo.getId());
        for (ToDoItem todo : TEST_TODOS_2)
            usedIds.add(todo.getId());
        ToDoItem todo;
        ToDoCategory cat;

        // Start with some items which don't conflict with anything
        for (int i = RAND.nextInt(3) + 3; i >= 0; --i) {
            todo = randomToDo();
            cat = TEST_CATEGORIES_1.get(
                    RAND.nextInt(TEST_CATEGORIES_1.size()));
            todo.setCategoryId(cat.getId());
            todo.setCategoryName(cat.getName());
            do {
                todo.setId(RAND.nextInt(1024) + 1);
            } while (usedIds.contains(todo.getId()));
            mockRepo.insertItem(todo);
            newItems.add(todo);
            usedIds.add(todo.getId());
        }

        // Add some items whose category ID's conflict with
        // TEST_CATEGORIES_2 (4, 6, or 7)
        for (ToDoCategory inCat : TEST_CATEGORIES_2) {
            if (inCat.getId() - 1 >= TEST_CATEGORIES_1.size())
                continue;
            cat = TEST_CATEGORIES_1.get(inCat.getId().intValue() - 1);
            todo = randomToDo();
            todo.setCategoryId(cat.getId());
            todo.setCategoryName(cat.getName());
            do {
                todo.setId(RAND.nextInt(1024) + 1);
            } while (usedIds.contains(todo.getId()));
            mockRepo.insertItem(todo);
            newItems.add(todo);
            usedIds.add(todo.getId());
        }

        // Add one item from each of the V1 and V2 import files with
        // a conflicting ID
        todo = randomToDo();
        cat = TEST_CATEGORIES_1.get(
                RAND.nextInt(TEST_CATEGORIES_1.size()));
        todo.setCategoryId(cat.getId());
        todo.setCategoryName(cat.getName());
        todo.setId(TEST_TODOS_1.get(RAND.nextInt(TEST_TODOS_1.size())).getId());
        mockRepo.insertItem(todo);
        newItems.add(todo);

        todo = randomToDo();
        cat = TEST_CATEGORIES_1.get(
                RAND.nextInt(TEST_CATEGORIES_1.size()));
        todo.setCategoryId(cat.getId());
        todo.setCategoryName(cat.getName());
        todo.setId(TEST_TODOS_2.get(RAND.nextInt(TEST_TODOS_2.size())).getId());
        mockRepo.insertItem(todo);
        newItems.add(todo);

        return newItems;
    }

    /**
     * Read the current set of To Do records from the mock repository.
     * They should be sorted by ID.
     *
     * @return a {@link List} of To Do records.
     */
    private List<ToDoItem> readToDos() {
        List<ToDoItem> list = new ArrayList<>(TEST_TODOS_1.size());
        ToDoCursor cursor = mockRepo.getItems(
                ToDoPreferences.ALL_CATEGORIES, true, true,
                ToDoRepositoryImpl.TODO_TABLE_NAME + "."
                        + ToDoSchema.ToDoItemColumns._ID);
        try {
            while (cursor.moveToNext())
                list.add(cursor.getItem());
        } finally {
            cursor.close();
        }
        return list;
    }

    /**
     * Arrange a {@link List} of {@link ToDoItem}s into a {@link SortedMap}
     *
     * @param message the assertion message to show if any duplicate
     * item ID&rsquo;s are found
     * @param list the list of records
     *
     * @return the map
     */
    private static SortedMap<Long,ToDoItem> toMap(
            String message, List<ToDoItem> list) {
        SortedMap<Long,ToDoItem> m = new TreeMap<>();
        for (ToDoItem item : list) {
            assertFalse(message, m.containsKey(item.getId()));
            m.put(item.getId(), item);
        }
        return m;
    }

    /**
     * Assert two lists of To Do items are identical.  This makes
     * allowances for item creation and modification times:
     * if the expected item does not have its created and/or modified
     * times set, the actual item&rsquo;s timestamps are checked for
     * whether they&rsquo;re in the range of import start/end times.
     * Otherwise they should be exact.  For all other fields,
     * they&rsquo;re compared for equality but reported individually
     * if they don&rsquo;t match.
     *
     * @param message the message to show in front of the mismatch errors
     * if there are any items that don&rsquo;t match
     * @param expected the list of expected items
     * @param actual the list of actual items
     * @param timeStart the start time of the import
     * @param timeEnd the end time of the import
     *
     * @throws AssertionError if any items don&rsquo;t match
     */
    public static void assertToDoEquals(
            String message, List<ToDoItem> expected, List<ToDoItem> actual,
            Instant timeStart, Instant timeEnd) throws AssertionError {
        Map<Long,ToDoItem> expectedMap = toMap(
                message + " - expected items", expected);
        Map<Long,ToDoItem> actualMap = toMap(
                message + " - actual items", actual);
        assertToDoEquals(message, expectedMap, actualMap, timeStart, timeEnd);
    }

    /**
     * Assert two maps of To Do items are identical.  This makes
     * allowances for item creation and modification times:
     * if the expected item does not have its created and/or modified
     * times set, the actual item&rsquo;s timestamps are checked for
     * whether they&rsquo;re in the range of import start/end times.
     * Otherwise they should be exact.  For all other fields,
     * they&rsquo;re compared for equality but reported individually
     * if they don&rsquo;t match.
     *
     * @param message the message to show in front of the mismatch errors
     * if there are any items that don&rsquo;t match
     * @param expected the list of expected items
     * @param actual the list of actual items
     * @param timeStart the start time of the import
     * @param timeEnd the end time of the import
     *
     * @throws AssertionError if any items don&rsquo;t match
     */
    public static void assertToDoEquals(
            String message, Map<Long,ToDoItem> expected,
            Map<Long,ToDoItem> actual, Instant timeStart, Instant timeEnd)
            throws AssertionError {
        SortedSet<Long> allIds = new TreeSet<>(expected.keySet());
        allIds.addAll(actual.keySet());
        // For items where we don't know the ultimate ID in advance,
        // get a map of descriptions to actual item ID's.
        Map<String,Long> descriptionMap = new HashMap<>();
        for (ToDoItem item : actual.values()) {
            if (!expected.containsKey(item.getId()))
                descriptionMap.put(item.getDescription(), item.getId());
        }
        Set<Long> unknownIds = new HashSet<>(allIds.headSet(0L));
        for (long id : unknownIds) {
            String description = expected.get(id).getDescription();
            if (descriptionMap.containsKey(description)) {
                ToDoItem item = expected.remove(id);
                expected.put(descriptionMap.get(description), item);
                allIds.remove(id);
            }
        }
        List<String> errors = new ArrayList<>();
        for (long id : allIds) {
            ToDoItem expectedItem = expected.get(id);
            ToDoItem actualItem = (id >= 0) ? actual.get(id)
                    : descriptionMap.containsKey(expectedItem.getDescription())
                    ? actual.get(descriptionMap.get(expectedItem.getDescription()))
                    : null;
            if (expectedItem == null) {
                errors.add(String.format("Record #%d expected:<null> but was:%s",
                        id, actualItem));
                continue;
            }
            if (actualItem == null) {
                // If the created and modified times are unset,
                // substitute the import time before stringifying them.
                Duration timeFudge = Duration.between(timeStart, timeEnd)
                        .dividedBy(2);
                Instant expectedImportTime = timeStart.plus(timeFudge);
                ToDoItem printItem = expectedItem.clone();
                if (printItem.getCreateTime().equals(Instant.MIN))
                    printItem.setCreateTime(expectedImportTime);
                if (printItem.getModTime().equals(Instant.MIN))
                    printItem.setModTime(expectedImportTime);
                errors.add(String.format("Record #%d expected:%s but was:<null>",
                        id, printItem));
                continue;
            }
            errors.addAll(compareToDoRecords(String.format("Record #%d", id),
                    expectedItem, actualItem, timeStart, timeEnd));
        }
        if (errors.size() > 1)
            fail(message + ": Multiple errors:\n"
                    + StringUtils.join(errors, "\n"));
        else if (errors.size() == 1)
            fail(message + ": " + errors.get(0));
    }

    /**
     * Compare two To Do records for near equality.  Fields that are set
     * from the import file should be equal.  Created and modified time
     * stamps should be equal if they were set in the expected record,
     * otherwise should be within the range of start and end times.
     *
     * @param message the string to prepend to any error message
     * @param expected the expected record
     * @param actual the actual record
     * @param timeStart the start time of the import
     * @param timeEnd the end time of the import
     *
     * @return a list of any validation errors found
     * (empty if the items are identical).
     */
    private static List<String> compareToDoRecords(
            String message, ToDoItem expected, ToDoItem actual,
            Instant timeStart, Instant timeEnd) {
        List<String> errors = new ArrayList<>();
        Duration timeFudge = Duration.between(timeStart, timeEnd).dividedBy(2);
        Instant expectedImportTime = timeStart.plus(timeFudge);
        String expectedImportStr = expectedImportTime.atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_INSTANT);
        if (!timeFudge.isZero())
            expectedImportStr = String.format("%s \u00b1%fms",
                    expectedImportTime, timeFudge.toMillis() / 1000.0);

        if ((expected.getId() >= 0) &&
                !expected.getId().equals(actual.getId()))
            errors.add(String.format("%s ID expected:%d but was:%d",
                    message, expected.getId(), actual.getId()));
        if (!StringUtils.equals(expected.getDescription(),
                actual.getDescription()))
            errors.add(String.format("%s description expected:\"%s\""
                    + " but was:\"%s\"", message,
                    expected.getDescription(), actual.getDescription()));
        if (!Arrays.equals(expected.getEncryptedDescription(),
                actual.getEncryptedDescription())) {
            if (expected.getEncryptedDescription() == null)
                errors.add(String.format("%s encrypted description"
                                + " expected:null but was:<%d bytes>",
                        message, actual.getEncryptedDescription().length));
            else if (actual.getEncryptedDescription() == null)
                errors.add(String.format("%s encrypted description"
                                + " expected:<%d bytes> but was:null",
                        message, expected.getEncryptedDescription().length));
            else
                errors.add(String.format("%s encrypted descriptions differ",
                        message));
        }
        if (expected.getCreateTime() == Instant.MIN) {
            if (actual.getCreateTime().isBefore(timeStart) ||
                    actual.getCreateTime().isAfter(timeEnd)) {
                errors.add(String.format("%s created time expected:%s but was:%s",
                        message, expectedImportStr, actual.getCreateTime()));
            }
        } else {
            if (!expected.getCreateTime().equals(actual.getCreateTime()))
                errors.add(String.format("%s created time expected:%s but was:%s",
                        message, expected.getCreateTime(), actual.getCreateTime()));
        }
        if (expected.getModTime() == Instant.MIN) {
            if (actual.getModTime().isBefore(timeStart) ||
                    actual.getModTime().isAfter(timeEnd)) {
                errors.add(String.format("%s modified time expected:%s but was:%s",
                        message, expectedImportStr, actual.getModTime()));
            }
        } else {
            if (!expected.getModTime().equals(actual.getModTime()))
                errors.add(String.format("%s modified time expected:%s but was:%s",
                        message, expected.getModTime(), actual.getModTime()));
        }
        if ((expected.getDue() == null) ? (actual.getDue() != null)
                : !expected.getDue().equals(actual.getDue()))
            errors.add(String.format("%s due date expected:%s but was:%s",
                    message, expected.getDue(), actual.getDue()));
        if ((expected.getCompleted() == null) ? (actual.getCompleted() != null)
                : !expected.getCompleted().equals(actual.getCompleted()))
            errors.add(String.format("%s completed time expected:%s but was:%s",
                    message, expected.getCompleted(), actual.getCompleted()));
        if (expected.isChecked() != actual.isChecked())
            errors.add(String.format("%s checked expected:%b but was:%b",
                    message, expected.isChecked(), actual.isChecked()));
        if (expected.getPriority() != actual.getPriority())
            errors.add(String.format("%s priority expected:%d but was:%d",
                    message, expected.getPriority(), actual.getPriority()));
        if (expected.getPrivate() != actual.getPrivate())
            errors.add(String.format("%s privacy level expected:%d but was:%d",
                    message, expected.getPrivate(), actual.getPrivate()));
        if (expected.getCategoryId() != actual.getCategoryId())
            errors.add(String.format("%s category ID expected:%d but was:%d",
                    message, expected.getCategoryId(), actual.getCategoryId()));
        if (expected.getCategoryName() != null &&
                !expected.getCategoryName().equals(actual.getCategoryName()))
            errors.add(String.format("%s category name expected:\"%s\" but was:\"%s\"",
                    message, expected.getCategoryName(), actual.getCategoryName()));
        if ((expected.getNote() == null) ? (actual.getNote() != null) :
                !expected.getNote().equals(actual.getNote()))
            errors.add(String.format("%s note expected:\"%s\" but was:\"%s\"",
                    message, expected.getNote(), actual.getNote()));
        if (!Arrays.equals(expected.getEncryptedNote(),
                actual.getEncryptedNote()))
            errors.add(String.format("%s encrypted notes differ",
                    message));
        if ((expected.getAlarm() == null) ? (actual.getAlarm() != null) :
                !expected.getAlarm().equals(actual.getAlarm()))
            errors.add(String.format("%s alarm expected:%s but was:%s",
                    message, expected.getAlarm(), actual.getAlarm()));
        if ((expected.getRepeatInterval() == null) ?
                (actual.getRepeatInterval() != null) :
                !expected.getRepeatInterval().equals(actual.getRepeatInterval()))
            errors.add(String.format("%s repeat interval expected:%s but was:%s",
                    message, expected.getRepeatInterval(), actual.getRepeatInterval()));
        if ((expected.getHideDaysEarlier() == null) ? (actual.getHideDaysEarlier() != null)
                : !expected.getHideDaysEarlier().equals(actual.getHideDaysEarlier()))
            errors.add(String.format("%s hide days earlier expected:%s but was:%s",
                    message, expected.getHideDaysEarlier(), actual.getHideDaysEarlier()));

        // Limit the returned errors to about half a dozen fields.  Any more
        // than that and we just report the whole record as a mismatch.
        if (errors.size() >= 6) {
            errors.clear();
            ToDoItem printItem = expected.clone();
            if (printItem.getCreateTime().equals(Instant.MIN))
                printItem.setCreateTime(expectedImportTime);
            if (printItem.getModTime().equals(Instant.MIN))
                printItem.setModTime(expectedImportTime);
            errors.add(String.format("%s expected:%s but was:%s",
                    message, printItem, actual));
        }
        return errors;
    }

    /**
     * Test a clean import of To Do records.
     * All existing records must be replaced.
     * This will use the V1 import file.
     */
    @Test
    public void testImportToDosClean() throws IOException {

        addRandomToDos();

        Instant start = Instant.now();
        MockProgressBar progress = runImportWorker(
                "Palm-todos-v1.dat", ImportType.CLEAN, true);
        Instant stop = Instant.now();

        SortedMap<Long,String> expectedCategories = new TreeMap<>();
        for (ToDoCategory cat : TEST_CATEGORIES_2)
            expectedCategories.put(cat.getId(), cat.getName());
        assertCategoriesEquals(expectedCategories);

        List<ToDoItem> expectedToDos = new ArrayList<>(TEST_TODOS_1);
        // Ensure the items are sorted by ID
        Collections.sort(expectedToDos, MockToDoRepository.TODO_ID_COMPARATOR);
        List<ToDoItem> actualTodos = readToDos();

        assertToDoEquals("Imported To Do records",
                expectedToDos, actualTodos, start, stop);

        MockProgressBar.Progress finalProgress = progress.getEndProgress();
        assertNotNull("Progress meter was not set", finalProgress);
        // The test file includes 1 To Do record to avoid a no-data error.
        assertEquals("Total number of records in input file",
                TEST_CATEGORIES_2.size() + TEST_TODOS_1.size(),
                finalProgress.total);
        assertEquals("Total number of records processed",
                TEST_CATEGORIES_2.size() + TEST_TODOS_1.size(),
                finalProgress.current);

    }

    /**
     * Test importing To Do records in {@link ImportType#OVERWRITE OVERWRITE}
     * mode.  Any existing items with the same ID as one from the Palm
     * data file should be overwritten.  This one uses the V2 import file.
     */
    @Test
    public void testImportToDosOverwrite() throws IOException {

        List<ToDoItem> oldItems = addRandomToDos();
        SortedMap<Long,String> expectedCategories = new TreeMap<>();
        for (ToDoCategory cat : TEST_CATEGORIES_1)
            expectedCategories.put(cat.getId(), cat.getName());

        Instant start = Instant.now();
        runImportWorker("Palm-todos-v2.dat", ImportType.OVERWRITE, true);
        Instant stop = Instant.now();

        // For this operation we don't have any conflicting category names;
        // all categories between the two groups have unique names.
        // Categories with conflicting ID's will be replaced without
        // affecting the To Do records using those categories.
        for (ToDoCategory cat : TEST_CATEGORIES_2)
            expectedCategories.put(cat.getId(), cat.getName());
        assertCategoriesEquals(expectedCategories);

        // Update the expectations for To Do records.
        Set<Long> newIds = new HashSet<>();
        List<ToDoItem> expectedToDos = new ArrayList<>(
                oldItems.size() + TEST_TODOS_2.size());
        for (ToDoItem todo : TEST_TODOS_2) {
            expectedToDos.add(todo);
            newIds.add(todo.getId());
        }
        for (ToDoItem todo : oldItems) {
            if (newIds.contains(todo.getId()))
                // This item should have been replaced; leave it out.
                continue;
            // Ensure the remaining old items are using the new category names
            todo.setCategoryName(expectedCategories
                    .get(todo.getCategoryId()));
            expectedToDos.add(todo);
        }
        // Ensure the items are sorted by ID
        Collections.sort(expectedToDos, MockToDoRepository.TODO_ID_COMPARATOR);

        List<ToDoItem> actualToDos = readToDos();

        assertToDoEquals("Imported To Do records",
                expectedToDos, actualToDos, start, stop);

    }

    /**
     * Test importing To Do records in {@link ImportType#MERGE MERGE} mode.
     * If an existing item has the same ID <i>and</i> description <i>and</i>
     * category name as an item being imported, most of it will be replaced
     * by the imported item except its create time which will be preserved.
     * Otherwise the imported item will be assigned a new ID.
     * This test uses the V1 import file.
     */
    @Test
    public void testImportToDosMerge() throws IOException {

        List<ToDoItem> oldItems = addRandomToDos();
        SortedMap<Long,String> expectedCategories = new TreeMap<>();
        for (ToDoCategory cat : TEST_CATEGORIES_1)
            expectedCategories.put(cat.getId(), cat.getName());

        // For this test we need an item which exists
        // in the import file but with different details.
        ToDoItem rewrittenItem = null;
        while (rewrittenItem == null) {
            rewrittenItem = TEST_TODOS_1.get(RAND.nextInt(TEST_TODOS_1.size()));
            if (rewrittenItem.getCategoryId() == ToDoCategory.UNFILED) {
                rewrittenItem = null;
                continue;
            }
            for (ToDoItem todo : oldItems) {
                if (todo.getId().equals(rewrittenItem.getId())) {
                    rewrittenItem = null;
                    break;
                }
            }
        }
        ToDoCategory sameCatName = mockRepo.insertCategory(
                rewrittenItem.getCategoryName());
        expectedCategories.put(sameCatName.getId(), sameCatName.getName());
        // Add a new record with the same ID, category name, and description
        ToDoItem modItem = randomToDo();
        modItem.setId(rewrittenItem.getId());
        modItem.setCategoryId(sameCatName.getId());
        modItem.setCategoryName(sameCatName.getName());
        modItem.setDescription(rewrittenItem.getDescription());
        modItem.setCreateTime(Instant.now().minusSeconds(
                RAND.nextInt(86400) + 86400));
        modItem.setModTime(modItem.getCreateTime());
        mockRepo.insertItem(modItem);
        // Set our expected record to the imported record
        // but having the original creation time and new category ID
        rewrittenItem = rewrittenItem.clone();
        rewrittenItem.setCategoryId(sameCatName.getId());
        rewrittenItem.setCreateTime(modItem.getCreateTime());
        rewrittenItem.setModTime(Instant.MIN);
        oldItems.add(rewrittenItem);

        Instant start = Instant.now();
        runImportWorker("Palm-todos-v1.dat", ImportType.MERGE, true);
        Instant stop = Instant.now();

        // The ID's of imported categories that conflict with existing
        // entries should change; we won't know what they are in advance.
        Map<String,Long> catNameMap = new HashMap<>();
        for (ToDoCategory cat : mockRepo.getCategories())
            catNameMap.put(cat.getName(), cat.getId());
        for (ToDoCategory cat : TEST_CATEGORIES_2) {
            if (cat.getName().equals(sameCatName.getName()))
                continue;
            assertTrue(String.format("Category \"%s\" was not imported",
                    cat.getName()), catNameMap.containsKey(cat.getName()));
            expectedCategories.put(catNameMap.get(cat.getName()), cat.getName());
        }
        assertCategoriesEquals(expectedCategories);

        // Records with a conflicting ID should be assigned a new ID,
        // which we can't determine until we've read the actual data.
        // Start with the non-conflicting items.
        SortedMap<Long,ToDoItem> expectedToDos = new TreeMap<>();
        for (ToDoItem todo : oldItems)
            expectedToDos.put(todo.getId(), todo);
        Map<String,ToDoItem> conflictsByTitle = new HashMap<>();
        for (ToDoItem todo: TEST_TODOS_1) {
            if (todo.getId().equals(rewrittenItem.getId()))
                // The rewritten item was already added to the map
                continue;
            if (expectedToDos.containsKey(todo.getId())) {
                conflictsByTitle.put(todo.getDescription(), todo);
                continue;
            }
            ToDoItem newExpectation = todo.clone();
            newExpectation.setCategoryId(
                    catNameMap.get(todo.getCategoryName()));
            expectedToDos.put(todo.getId(), newExpectation);
        }

        SortedMap<Long,ToDoItem> actualToDos = new TreeMap<>();
        for (ToDoItem todo : readToDos()) {
            actualToDos.put(todo.getId(), todo);
            if (conflictsByTitle.containsKey(todo.getDescription()) &&
                    !expectedToDos.containsKey(todo.getId())) {
                // For our expectation, clone the item
                // and assign the actual ID.
                ToDoItem newExpectation = conflictsByTitle.get(
                        todo.getDescription()).clone();
                newExpectation.setId(todo.getId());
                newExpectation.setCategoryId(
                        catNameMap.get(todo.getCategoryName()));
                expectedToDos.put(todo.getId(), newExpectation);
                conflictsByTitle.remove(todo.getDescription());
            }
        }
        // If we have any remaining conflicts, ignore their ID's.
        long nextAvailableId = -1;
        for (ToDoItem todo : conflictsByTitle.values()) {
            ToDoItem newExpectation = todo.clone();
            newExpectation.setId(--nextAvailableId);
            newExpectation.setCategoryId(
                    catNameMap.get(todo.getCategoryName()));
            expectedToDos.put(newExpectation.getId(), newExpectation);
        }

        assertToDoEquals("Imported To Do records",
                expectedToDos, actualToDos, start, stop);

    }

    /**
     * Test importing To Do records in {@link ImportType#ADD ADD} mode.
     * Any imported item with the same ID as an existing item will be
     * assigned a new ID regardless of its contents.  This test uses
     * the V2 import file.
     */
    @Test
    public void testImportToDosAdd() throws IOException {

        List<ToDoItem> oldItems = addRandomToDos();
        SortedMap<Long,String> expectedCategories = new TreeMap<>();
        for (ToDoCategory cat : TEST_CATEGORIES_1)
            expectedCategories.put(cat.getId(), cat.getName());

        // For this test we need an item which exists
        // in the import file.
        ToDoItem alreadyImportedItem = null;
        while (alreadyImportedItem == null) {
            alreadyImportedItem = TEST_TODOS_1.get(RAND.nextInt(TEST_TODOS_1.size()));
            for (ToDoItem todo : oldItems) {
                if (todo.getId().equals(alreadyImportedItem.getId())) {
                    alreadyImportedItem = null;
                    break;
                }
            }
        }
        ToDoCategory alreadyImportedCategory = mockRepo.insertCategory(
                alreadyImportedItem.getCategoryName());
        expectedCategories.put(alreadyImportedCategory.getId(),
                alreadyImportedCategory.getName());
        alreadyImportedItem = alreadyImportedItem.clone();
        alreadyImportedItem.setCategoryId(alreadyImportedCategory.getId());
        alreadyImportedItem.setCreateTime(Instant.now().minusSeconds(
                RAND.nextInt(86400) + 86400));
        alreadyImportedItem.setModTime(alreadyImportedItem.getCreateTime());
        mockRepo.insertItem(alreadyImportedItem);
        oldItems.add(alreadyImportedItem);

        Instant start = Instant.now();
        runImportWorker("Palm-todos-v2.dat", ImportType.ADD, true);
        Instant stop = Instant.now();

        // The ID's of imported categories that conflict with existing
        // entries should change; we won't know what they are in advance.
        Map<String,Long> catNameMap = new HashMap<>();
        for (ToDoCategory cat : mockRepo.getCategories())
            catNameMap.put(cat.getName(), cat.getId());
        for (ToDoCategory cat : TEST_CATEGORIES_2)
            expectedCategories.put(catNameMap.get(cat.getName()), cat.getName());
        assertCategoriesEquals(expectedCategories);

        // Records with a conflicting ID should be assigned a new ID,
        // which we can't determine until we've read the actual data.
        // Start with the non-conflicting items.
        SortedMap<Long,ToDoItem> expectedToDos = new TreeMap<>();
        for (ToDoItem todo : oldItems)
            expectedToDos.put(todo.getId(), todo);
        Map<Long,ToDoItem> conflictingItems = new HashMap<>();
        for (ToDoItem todo : TEST_TODOS_2) {
            if (todo.getId().equals(alreadyImportedItem.getId()))
                // The already imported item was already added to the map
                continue;
            if (expectedToDos.containsKey(todo.getId())) {
                conflictingItems.put(todo.getId(), todo);
                continue;
            }
            ToDoItem newExpectation = todo.clone();
            newExpectation.setCategoryId(
                    catNameMap.get(todo.getCategoryName()));
            expectedToDos.put(todo.getId(), newExpectation);
        }

        SortedMap<Long,ToDoItem> actualToDos = new TreeMap<>();
        for (ToDoItem todo : readToDos()) {
            actualToDos.put(todo.getId(), todo);
            if (conflictingItems.containsKey(todo.getId()) &&
                    !expectedToDos.containsKey(todo.getId())) {
                ToDoItem newExpectation = conflictingItems.get(
                        todo.getId()).clone();
                newExpectation.setId(todo.getId());
                newExpectation.setCategoryId(
                        catNameMap.get(todo.getCategoryName()));
                expectedToDos.put(todo.getId(), newExpectation);
                conflictingItems.remove(todo.getId());
            }
        }
        // If we have any remaining conflicts, ignore their ID's
        long nextAvailableId = -1;
        for (ToDoItem todo : conflictingItems.values()) {
            ToDoItem newExpectation = todo.clone();
            newExpectation.setId(--nextAvailableId);
            newExpectation.setCategoryId(
                    catNameMap.get(todo.getCategoryName()));
            expectedToDos.put(--nextAvailableId, newExpectation);
        }

        assertToDoEquals("Imported To Do records",
                expectedToDos, actualToDos, start, stop);

    }

}
