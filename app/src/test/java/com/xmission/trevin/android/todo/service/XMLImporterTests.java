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

import static com.xmission.trevin.android.todo.data.ToDoPreferences.*;
import static com.xmission.trevin.android.todo.service.XMLExporter.*;
import static com.xmission.trevin.android.todo.util.RandomToDoUtils.randomToDo;
import static com.xmission.trevin.android.todo.util.RandomToDoUtils.randomWord;

import static org.junit.Assert.*;

import com.xmission.trevin.android.todo.data.MockSharedPreferences;
import com.xmission.trevin.android.todo.data.ToDoCategory;
import com.xmission.trevin.android.todo.data.ToDoItem;
import com.xmission.trevin.android.todo.data.ToDoPreferences;
import com.xmission.trevin.android.todo.provider.MockToDoRepository;
import com.xmission.trevin.android.todo.provider.ToDoCursor;
import com.xmission.trevin.android.todo.provider.ToDoRepositoryImpl;
import com.xmission.trevin.android.todo.provider.ToDoSchema;
import com.xmission.trevin.android.todo.service.XMLImporter.ImportType;
import com.xmission.trevin.android.todo.util.PasswordMismatchException;
import com.xmission.trevin.android.todo.util.PasswordRequiredException;
import com.xmission.trevin.android.todo.util.StringEncryption;

import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Unit tests for importing To Do data from an XML file.
 */
public class XMLImporterTests {

    public static Random RAND = new Random();

    private static MockSharedPreferences underlyingPrefs = null;
    private static ToDoPreferences mockPrefs = null;
    private static MockToDoRepository mockRepo = null;
    private StringEncryption globalEncryption = null;
    private String unfiledName;

    @Before
    public void initializeRepository() {
        if (mockPrefs == null) {
            underlyingPrefs = new MockSharedPreferences();
            ToDoPreferences.setSharedPreferences(underlyingPrefs);
            mockPrefs = ToDoPreferences.getInstance(null);
        }
        if (mockRepo == null) {
            mockRepo = MockToDoRepository.getInstance();
        }
        underlyingPrefs.resetMock();
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
     * Run the importer for a given test file.  The operation is expected
     * to run successfully.
     *
     * @param inFileName the name of the XML file to import (relative to
     * the &ldquo;resources/&rdquo; directory).
     * @param importType the type of import to perform.
     * @param importPrivate whether to include private records.
     * @param xmlPassword the password with which the XML file was exported,
     * or {@code null}
     *
     * @return the progress indicator at the end of the import
     *
     * @throws IOException if there was an error reading the import file.
     * @throws RuntimeException if the import failed.
     */
    private MockProgressBar runImporter(
            String inFileName, ImportType importType,
            boolean importPrivate, String xmlPassword)
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
            XMLImporter.importData(mockPrefs, mockRepo,
                    inFileName, inStream, importType,
                    importPrivate, xmlPassword, currentPassword,
                    progress);
            progress.setEndTime();
        } finally {
            inStream.close();
        }
        return progress;
    }

    /**
     * <b>TEMPORARY TEST &mdash;</b> test reading a backup of To Do
     * data from an actual phone running an older version of the app
     * (version 1 XML data).  This backup file <i>must not</i>
     * be committed with the source files.  For security, the password
     * must be given as an environment variable.
     */
    @Test
    public void testImportPhoneV1() throws IOException {
        String xmlPassword = System.getenv("TODO_PASSWORD");
        runImporter("todo-phone-1.xml", ImportType.TEST, true, xmlPassword);
    }

    /**
     * Test reading a version 2 XML file with no To Do records.
     */
    @Test
    public void testImportEmpty2() throws IOException {
        runImporter("todo-empty-2.xml", ImportType.TEST, false, null);
    }

    /**
     * Test reading preferences.
     */
    @Test
    public void testImportPreferences() throws IOException {
        /*
         * The import type doesn't matter for this section,
         * as long as it's not TEST.  Since the mock starts out empty,
         * we should have a value for every item in the input file.
         */
        runImporter("todo-preferences.xml", ImportType.CLEAN, false, null);

        assertEquals(TPREF_SORT_ORDER, 18237, mockPrefs.getSortOrder());
        assertEquals(TPREF_SHOW_CHECKED, false, mockPrefs.showChecked());
        assertEquals(TPREF_SHOW_DUE_DATE, true, mockPrefs.showDueDate());
        assertEquals(TPREF_SHOW_PRIORITY, true, mockPrefs.showPriority());
        // The importer should not have changed the "Show Private" flag.
        assertEquals(TPREF_SHOW_PRIVATE, null,
                underlyingPrefs.getPreference(TPREF_SHOW_PRIVATE));
        // The importer MUST NOT have changed the "Show Encrypted" flag.
        assertEquals(TPREF_SHOW_ENCRYPTED, null,
                underlyingPrefs.getPreference(TPREF_SHOW_ENCRYPTED));
        assertEquals(TPREF_SHOW_CATEGORY, true, mockPrefs.showCategory());
        assertEquals(TPREF_LOCAL_TIME_ZONE,
                false, mockPrefs.useLocalTimeZone());
        assertEquals(TPREF_FIXED_TIME_ZONE,
                ZoneId.of("Asia/Pyongyang"), mockPrefs.getTimeZone());
        assertEquals(TPREF_NOTIFICATION_VIBRATE,
                true, mockPrefs.notificationVibrate());
        assertEquals(TPREF_NOTIFICATION_SOUND,
                14570L, mockPrefs.getNotificationSound());
        assertEquals(TPREF_SELECTED_CATEGORY,
                17734L, mockPrefs.getSelectedCategory());
        // We do not expect the importer to change any of the
        // Export / Import preferences.
        assertEquals(TPREF_EXPORT_FILE, null,
                // "/storage/emulated/0/todo-5fpk3XVpLqdDUH5l.xml",
                underlyingPrefs.getPreference(TPREF_EXPORT_FILE));
        assertEquals(TPREF_EXPORT_PRIVATE, null,
                underlyingPrefs.getPreference(TPREF_EXPORT_PRIVATE));
        assertEquals(TPREF_IMPORT_FILE, null,
                // "/storage/emulated/0/todo-cUrpVNRxCyK6e8mq.xml",
                underlyingPrefs.getPreference(TPREF_IMPORT_FILE));
        assertEquals(TPREF_IMPORT_TYPE, null,
                // ImportType.UPDATE,
                underlyingPrefs.getPreference(TPREF_IMPORT_TYPE));
        assertEquals(TPREF_IMPORT_PRIVATE, null,
                underlyingPrefs.getPreference(TPREF_IMPORT_PRIVATE));
    }

    /**
     * Test reading preferences in {@link ImportType#TEST TEST} mode.
     * This should not set any preferences.
     */
    @Test
    public void testImportPreferencesTest() throws IOException {
        runImporter("todo-preferences.xml", ImportType.TEST, false, null);

        assertTrue("Preferences were changed",
                underlyingPrefs.getAll().isEmpty());
    }

    /**
     * Test reading public metadata.
     * This does not include checking the password.
     */
    @Test
    public void testImportPublicMetadata() throws IOException {
        /*
         * The import type doesn't matter for this section;
         * the app doesn't do anything with any metadata
         *  other than the password hash.
         */
        runImporter("todo-metadata.xml", ImportType.TEST, false, null);
    }

    /**
     * Test checking the password against the metadata stored
     * in the XML file.  This relies on a pre-determined password
     * whose hash was computed when the file was created.
     */
    @Test
    public void testCheckXMLPasswordMatch() throws IOException {
        /*
         * The import type doesn't matter for this section;
         * the app doesn't change any existing password
         * whether the export file was encrypted or not.
         */
        runImporter("todo-metadata.xml", ImportType.TEST, true, "Jx4ppbO2t");
    }

    /**
     * Test checking an invalid password hash against the metadata
     * stored in the XML file.
     */
    @Test
    public void testCheckXMLPasswordMismatch() throws IOException {
        try {
            runImporter("todo-metadata.xml", ImportType.TEST, true, "12345");
            fail("Import completed successfully");
        } catch (PasswordMismatchException e) {
            String lmsg = e.getMessage().toLowerCase(Locale.US);
            assertTrue("Exception thrown but does not mention password mismatch",
                    lmsg.contains("password") && lmsg.contains("match"));
        }
    }

    /**
     * Test checking an import which is password-protected but
     * no password is provided.
     */
    @Test
    public void testImportPrivateNoPassword() throws IOException {
        try {
            runImporter("todo-metadata.xml", ImportType.TEST, true, null);
            fail("Import completed successfully");
        } catch (PasswordRequiredException e) {
            String lmsg = e.getMessage().toLowerCase(Locale.US);
            assertTrue("Exception thrown but does not mention password",
                    lmsg.contains("password"));
        }
    }

    /**
     * Categories expected from a clean import of the
     * &ldquo;todo-categories.xml&rdquo; file
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

        runImporter("todo-categories.xml", ImportType.CLEAN, false, null);

        SortedMap<Long,String> expectedCategories = new TreeMap<>();
        for (ToDoCategory cat : TEST_CATEGORIES_1)
            expectedCategories.put((long) cat.getId(), cat.getName());

        assertCategoriesEquals(expectedCategories);

    }

    /**
     * Test importing categories in {@link ImportType#REVERT REVERT}
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
            if (cat.getId().equals(dupCatId.getId())) {
                todoDupCatId.setCategoryName(cat.getName());
                break;
            }
        }

        runImporter("todo-categories.xml", ImportType.REVERT, false, null);

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
                todoDupCatName, actualItem);
        if (!diffs.isEmpty())
            fail(StringUtils.join("\n", diffs));

        // Verify the category of the second To Do item
        // just had its name changed
        actualItem = mockRepo.getItemById(todoDupCatId.getId());
        diffs = compareToDoRecords(
                "To Do item with category #" + dupCatId.getId(),
                todoDupCatId, actualItem);
        if (!diffs.isEmpty())
            fail(StringUtils.join("\n", diffs));

    }

    // To Do: Write the rest of the test cases ...

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
     * Assert two lists of To Do items are exact.  For all fields,
     * they&rsquo;re compared for equality but reported individually
     * if they don&rsquo;t match.
     *
     * @param message the message to show in front of the mismatch errors
     * if there are any items that don&rsquo;t match
     * @param expected the list of expected items
     * @param actual the list of actual items
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
        assertToDoEquals(message, expectedMap, actualMap);
    }

    /**
     * Assert two maps of To Do items are exact.  For all fields,
     * they&rsquo;re compared for equality but reported individually
     * if they don&rsquo;t match.
     *
     * @param message the message to show in front of the mismatch errors
     * if there are any items that don&rsquo;t match
     * @param expected the list of expected items
     * @param actual the list of actual items
     *
     * @throws AssertionError if any items don&rsquo;t match
     */
    public static void assertToDoEquals(
            String message, Map<Long,ToDoItem> expected,
            Map<Long,ToDoItem> actual)
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
                errors.add(String.format("Record #%d expected:%s but was:<null>",
                        id, expectedItem));
                continue;
            }
            errors.addAll(compareToDoRecords(String.format("Record #%d", id),
                    expectedItem, actualItem));
        }
        if (errors.size() > 1)
            fail(message + ": Multiple errors:\n"
                    + StringUtils.join(errors, "\n"));
        else if (errors.size() == 1)
            fail(message + ": " + errors.get(0));
    }

    /**
     * Compare two To Do records for equality.
     *
     * @param message the string to prepend to any error message
     * @param expected the expected record
     * @param actual the actual record
     *
     * @return a list of any validation errors found
     * (empty if the items are identical).
     */
    private static List<String> compareToDoRecords(
            String message, ToDoItem expected, ToDoItem actual) {
        List<String> errors = new ArrayList<>();

        if ((expected.getId() >= 0) &&
                !expected.getId().equals(actual.getId()))
            errors.add(String.format("%s ID expected:%d but was:%d",
                    message, expected.getId(), actual.getId()));
        if (!StringUtils.equals(expected.getDescription(),
                actual.getDescription()))
            errors.add(String.format("%s description expected:\"%s\""
                    + " but was:\"%s\"", message,
                    expected.getDescription(), actual.getDescription()));
        if (!expected.getCreateTime().equals(actual.getCreateTime()))
            errors.add(String.format("%s created time expected:%s but was:%s",
                    message, expected.getCreateTime(), actual.getCreateTime()));
        if (!expected.getModTime().equals(actual.getModTime()))
            errors.add(String.format("%s modified time expected:%s but was:%s",
                    message, expected.getModTime(), actual.getModTime()));
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
            errors.add(String.format("%s expected:%s but was:%s",
                    message, expected, actual));
        }
        return errors;
    }

    // To Do: Write the rest of the test cases ...

}
