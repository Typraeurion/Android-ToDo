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
import static com.xmission.trevin.android.todo.util.RandomToDoUtils.randomToDo;
import static com.xmission.trevin.android.todo.util.RandomToDoUtils.randomWord;

import static org.junit.Assert.*;

import com.xmission.trevin.android.todo.data.*;
import com.xmission.trevin.android.todo.data.repeat.*;
import com.xmission.trevin.android.todo.provider.MockToDoRepository;
import com.xmission.trevin.android.todo.provider.ToDoCursor;
import com.xmission.trevin.android.todo.provider.ToDoRepositoryImpl;
import com.xmission.trevin.android.todo.provider.ToDoSchema;
import com.xmission.trevin.android.todo.provider.ToDoSchema.ToDoItemColumns;
import com.xmission.trevin.android.todo.service.XMLImporter.ImportType;
import com.xmission.trevin.android.todo.util.PasswordMismatchException;
import com.xmission.trevin.android.todo.util.PasswordRequiredException;
import com.xmission.trevin.android.todo.util.StringEncryption;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.*;
import java.util.*;

/**
 * Unit tests for importing To Do data from an XML file.
 */
public class XMLImporterTests {

    public static final Random RAND = new Random();
    public static final RandomStringUtils SRAND = RandomStringUtils.insecure();

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
        // With rare exceptions, all time and date conversions
        // should use UTC.
        mockPrefs.setTimeZone(ZoneOffset.UTC);
        mockRepo.clear();
        globalEncryption = StringEncryption.holdGlobalEncryption();
        if (unfiledName == null)
            unfiledName = mockRepo.getCategoryById(
                    ToDoCategory.UNFILED).getName();
    }

    @After
    public void releaseRepository() {
        globalEncryption.forgetPassword();
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
    //Test
    public void testImportPhoneV1() throws IOException {
        String xmlPassword = System.getenv("TODO_PASSWORD");
        runImporter("todo-phone-1.xml", ImportType.TEST, true, xmlPassword);
    }

    /**
     * Test reading a version 2 XML file with no To Do records.
     */
    @Test
    public void testImportEmpty2() throws IOException {
        MockProgressBar progress = runImporter("todo-empty-2.xml",
                ImportType.TEST, false, null);

        MockProgressBar.Progress endProgress = progress.getEndProgress();
        assertNotNull("Progress meter after import", endProgress);
        assertEquals("Total records in file", 0, endProgress.total);
        assertEquals("Number of records processed", 0, endProgress.current);
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
        MockProgressBar progress = runImporter("todo-preferences.xml",
                ImportType.CLEAN, false, null);

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

        MockProgressBar.Progress endProgress = progress.getEndProgress();
        assertNotNull("Progress meter after import", endProgress);
        assertEquals("Total records in file", 18, endProgress.total);
        assertEquals("Number of records processed", 18, endProgress.current);
    }

    /**
     * Test reading preferences in {@link ImportType#TEST TEST} mode.
     * This should not set any preferences.
     */
    @Test
    public void testImportPreferencesTest() throws IOException {
        // Make sure we clear the UTC time zone that most tests use by default
        underlyingPrefs.resetMock();

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

        MockProgressBar progress = runImporter("todo-categories.xml",
                ImportType.CLEAN, false, null);

        SortedMap<Long,String> expectedCategories = new TreeMap<>();
        for (ToDoCategory cat : TEST_CATEGORIES_1)
            expectedCategories.put(cat.getId(), cat.getName());

        assertCategoriesEquals(expectedCategories);

        MockProgressBar.Progress endProgress = progress.getEndProgress();
        assertNotNull("Progress meter after import", endProgress);
        assertEquals("Total records in file", 11, endProgress.total);
        assertEquals("Number of records processed", 11, endProgress.current);

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

        MockProgressBar progress = runImporter("todo-categories.xml",
                ImportType.REVERT, false, null);

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

        MockProgressBar.Progress endProgress = progress.getEndProgress();
        assertNotNull("Progress meter after import", endProgress);
        assertEquals("Total records in file", 11, endProgress.total);
        assertEquals("Number of records processed", 11, endProgress.current);

    }

    /**
     * Test importing categories in {@link ImportType#UPDATE UPDATE},
     * {@link ImportType#MERGE}, or {@link ImportType#ADD} mode; the
     * effect of all of these are the same.  If we already have a category
     * of the same name but a different ID, the importer should use the
     * existing ID.  If we have another category with the same ID as
     * one being imported, the imported category will get a new ID.
     * Otherwise we&rsquo;ll try to add the category with its given ID.
     */
    private void testImportCategoriesUMA(ImportType importType)
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

        MockProgressBar progress = runImporter("todo-categories.xml",
                importType, false, null);

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
                todoDupCatName, actualItem);
        if (!diffs.isEmpty())
            fail(StringUtils.join("\n", diffs));

        actualItem = mockRepo.getItemById(todoDupCatId.getId());
        diffs = compareToDoRecords(String.format(
                        "To Do item in category #%d \"%s\"",
                        dupCatId.getId(), dupCatId.getName()),
                todoDupCatId, actualItem);
        if (!diffs.isEmpty())
            fail(StringUtils.join("\n", diffs));

        MockProgressBar.Progress endProgress = progress.getEndProgress();
        assertNotNull("Progress meter after import", endProgress);
        assertEquals("Total records in file", 11, endProgress.total);
        assertEquals("Number of records processed", 11, endProgress.current);

    }

    @Test
    public void testImportCategoriesUpdate() throws IOException {
        testImportCategoriesUMA(ImportType.UPDATE);
    }

    @Test
    public void testImportCategoriesMerge() throws IOException {
        testImportCategoriesUMA(ImportType.MERGE);
    }

    @Test
    public void testImportCategoriesAdd() throws IOException {
        testImportCategoriesUMA(ImportType.ADD);
    }

    /**
     * Categories used by a variety of To Do XML import file.
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
     * &ldquo;todo-misc-v1.xml&rdquo; file.  This file is not
     * encrypted and has no alarms or repeats, but may have due dates.
     * The first version did not support exporting completion times.
     */
    public static final List<ToDoItem> TEST_TODOS_1;
    static {
        List<ToDoItem> list = new ArrayList<>(7);

        ToDoItem todo = new ToDoItem();
        todo.setId(1);
        todo.setDescription("Write \"Hello, world\"");
        todo.setCategoryId(ToDoCategory.UNFILED);
        todo.setCategoryName("Unfiled");
        todo.setPriority(7);
        todo.setCreateTime(Instant.ofEpochMilli(1467924157354L));
        todo.setModTime(Instant.ofEpochMilli(1632449855547L));
        list.add(todo);

        todo = new ToDoItem();
        todo.setId(6);
        todo.setDescription("Milk");
        todo.setDue(LocalDate.of(2018, 8, 5));
        todo.setChecked(true);
        todo.setPriority(1);
        todo.setCategoryId(TEST_CATEGORIES_2.get(1).getId());
        todo.setCategoryName(TEST_CATEGORIES_2.get(1).getName());
        todo.setCreateTime(Instant.ofEpochMilli(1345168026140L));
        todo.setModTime(Instant.ofEpochMilli(1483874432396L));
        list.add(todo);

        todo = new ToDoItem();
        todo.setId(13);
        todo.setDescription("Batteries");
        todo.setDue(LocalDate.of(2021, 7, 30));
        todo.setChecked(true);
        todo.setPriority(2);
        todo.setCategoryId(TEST_CATEGORIES_2.get(1).getId());
        todo.setCategoryName(TEST_CATEGORIES_2.get(1).getName());
        todo.setNote("8x AA, 6x AAA, 4x C, 2x D");
        todo.setCreateTime(Instant.ofEpochMilli(1504390565939L));
        todo.setModTime(Instant.ofEpochMilli(1745982011840L));
        list.add(todo);

        todo = new ToDoItem();
        todo.setId(18);
        todo.setDescription("TPS Report");
        todo.setDue(LocalDate.of(2020, 12, 15));
        todo.setChecked(false);
        todo.setPriority(5);
        todo.setCategoryId(TEST_CATEGORIES_2.get(2).getId());
        todo.setCategoryName(TEST_CATEGORIES_2.get(2).getName());
        todo.setNote("Bill needs you to rewrite the cover sheet.");
        todo.setCreateTime(Instant.ofEpochMilli(1375228772299L));
        todo.setModTime(Instant.ofEpochMilli(1583111077967L));
        list.add(todo);

        todo = new ToDoItem();
        todo.setId(34);
        todo.setDescription("File vacation request");
        todo.setPriority(4);
        todo.setCategoryId(TEST_CATEGORIES_2.get(2).getId());
        todo.setCategoryName(TEST_CATEGORIES_2.get(2).getName());
        todo.setCreateTime(Instant.ofEpochMilli(1315849159277L));
        todo.setModTime(Instant.ofEpochMilli(1505157031746L));
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
        todo.setCreateTime(Instant.ofEpochMilli(1436536203703L));
        todo.setModTime(Instant.ofEpochMilli(1539938198639L));
        list.add(todo);

        todo = new ToDoItem();
        todo.setId(234);
        todo.setDescription("Identify members of SPECTRE");
        todo.setDue(LocalDate.of(2016, 8, 3));
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
        todo.setCreateTime(Instant.ofEpochMilli(1371332518656L));
        todo.setModTime(Instant.ofEpochMilli(1410356787781L));
        list.add(todo);

        TEST_TODOS_1 = Collections.unmodifiableList(list);
    }

    /**
     * To Do records expected from a clean import of the
     * &ldquo;todo-misc-v2.xml&rdquo; file.  This file is
     * not encrypted and has no alarms or repeats.  The
     * second version supports exporting completion times.
     */
    public static final List<ToDoItem> TEST_TODOS_2;
    static {
        List<ToDoItem> list = new ArrayList<>(1);

        ToDoItem todo = new ToDoItem();
        todo.setId(2);
        todo.setDescription("Define categories");
        todo.setCategoryId(ToDoCategory.UNFILED);
        todo.setCategoryName("Unfiled");
        todo.setPriority(10);
        todo.setCreateTime(Instant.ofEpochMilli(1423718160907L));
        todo.setModTime(Instant.ofEpochMilli(1636621929446L));
        list.add(todo);

        todo = new ToDoItem();
        todo.setId(7);
        todo.setDescription("Change smoke detector batteries");
        todo.setCategoryId(TEST_CATEGORIES_2.get(0).getId());
        todo.setCategoryName(TEST_CATEGORIES_2.get(0).getName());
        todo.setDue(LocalDate.of(2018, 12, 8));
        todo.setChecked(false);
        todo.setPriority(2);
        todo.setCompleted(Instant.ofEpochMilli(1362954545151L));
        todo.setCreateTime(Instant.ofEpochMilli(1348684314464L));
        todo.setModTime(Instant.ofEpochMilli(1438448134686L));
        list.add(todo);

        todo = new ToDoItem();
        todo.setId(42);
        todo.setDescription("Sell shares of PAQB");
        todo.setCategoryId(TEST_CATEGORIES_2.get(2).getId());
        todo.setCategoryName(TEST_CATEGORIES_2.get(2).getName());
        todo.setDue(LocalDate.of(2025, 6, 7));
        todo.setPriority(4);
        todo.setCreateTime(Instant.ofEpochMilli(1389074585598L));
        todo.setModTime(Instant.ofEpochMilli(1395933968933L));
        list.add(todo);

        todo = new ToDoItem();
        todo.setId(67);
        todo.setDescription("Check all pressure gauges");
        todo.setCategoryId(TEST_CATEGORIES_2.get(2).getId());
        todo.setCategoryName(TEST_CATEGORIES_2.get(2).getName());
        todo.setDue(LocalDate.of(2024, 4, 16));
        todo.setPriority(3);
        todo.setCompleted(Instant.ofEpochMilli(1681449765749L));
        todo.setCreateTime(Instant.ofEpochMilli(1507171489831L));
        todo.setModTime(Instant.ofEpochMilli(1559250442602L));
        list.add(todo);

        todo = new ToDoItem();
        todo.setId(133);
        todo.setDescription("Gym workout");
        todo.setCategoryId(ToDoCategory.UNFILED);
        todo.setCategoryName("Unfiled");
        todo.setDue(LocalDate.of(2023, 3, 21));
        todo.setPriority(5);
        todo.setCreateTime(Instant.ofEpochMilli(1371497393319L));
        todo.setModTime(Instant.ofEpochMilli(1677846149491L));
        list.add(todo);

        todo = new ToDoItem();
        todo.setId(226);
        todo.setDescription("Leap day!");
        todo.setCategoryId(ToDoCategory.UNFILED);
        todo.setCategoryName("Unfiled");
        todo.setDue(LocalDate.of(2028, 2, 29));
        todo.setPriority(99);
        todo.setCreateTime(Instant.ofEpochMilli(1353819726947L));
        todo.setModTime(Instant.ofEpochMilli(1415365334110L));
        list.add(todo);

        todo = new ToDoItem();
        todo.setId(242);
        todo.setPrivate(StringEncryption.NO_ENCRYPTION);
        todo.setDescription("Submit mission report");
        todo.setCategoryId(TEST_CATEGORIES_2.get(3).getId());
        todo.setCategoryName(TEST_CATEGORIES_2.get(3).getName());
        todo.setDue(LocalDate.of(2019, 10, 17));
        todo.setPriority(86);
        todo.setCreateTime(Instant.ofEpochMilli(1418435346980L));
        todo.setModTime(Instant.ofEpochMilli(1478288293696L));
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
        ToDoItem conflict = TEST_TODOS_1.get(RAND.nextInt(TEST_TODOS_1.size()));
        todo.setId(conflict.getId());
        todo.setCreateTime(conflict.getCreateTime());
        mockRepo.insertItem(todo);
        newItems.add(todo);

        todo = randomToDo();
        cat = TEST_CATEGORIES_1.get(
                RAND.nextInt(TEST_CATEGORIES_1.size()));
        todo.setCategoryId(cat.getId());
        todo.setCategoryName(cat.getName());
        conflict = TEST_TODOS_2.get(RAND.nextInt(TEST_TODOS_2.size()));
        todo.setId(conflict.getId());
        todo.setCreateTime(conflict.getCreateTime());
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
                ToDoPreferences.ALL_CATEGORIES, true, LocalDate.now(),
                true, true, ToDoRepositoryImpl.TODO_TABLE_NAME + "."
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
            String message, List<ToDoItem> expected, List<ToDoItem> actual)
            throws AssertionError {
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
        if (!Arrays.equals(expected.getEncryptedNote(),
                actual.getEncryptedNote())) {
            if (expected.getEncryptedNote() == null)
                errors.add(String.format("%s encrypted note"
                                + " expected:null but was:<%d bytes>",
                        message, actual.getEncryptedNote().length));
            else if (actual.getEncryptedNote() == null)
                errors.add(String.format("%s encrypted note"
                                + " expected:<%d bytes> but was:null",
                        message, expected.getEncryptedNote().length));
            else
                errors.add(String.format("%s encrypted notes differ",
                    message));
        }
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

    /**
     * Test a clean import of public To Do records from a version 1
     * import file.  All existing records must be replaced.
     */
    @Test
    public void testImportToDosClean1Public() throws IOException {

        addRandomToDos();

        MockProgressBar progress = runImporter("todo-misc-v1.xml",
                ImportType.CLEAN, false, null);

        SortedMap<Long,String> expectedCategories = new TreeMap<>();
        for (ToDoCategory cat : TEST_CATEGORIES_2)
            expectedCategories.put(cat.getId(), cat.getName());
        assertCategoriesEquals(expectedCategories);

        List<ToDoItem> expectedToDos = new ArrayList<>(TEST_TODOS_1.size());
        for (ToDoItem item : TEST_TODOS_1) {
            if (!item.isPrivate())
                expectedToDos.add(item);
        }
        // Ensure the items are sorted by ID
        Collections.sort(expectedToDos, MockToDoRepository.TODO_ID_COMPARATOR);
        List<ToDoItem> actualTodos = readToDos();

        assertToDoEquals("Imported To Do records",
                expectedToDos, actualTodos);

        MockProgressBar.Progress endProgress = progress.getEndProgress();
        assertNotNull("Progress meter after import", endProgress);
        assertEquals("Total records in file", -1, endProgress.total);
        assertEquals("Number of records processed", 12, endProgress.current);

    }

    /**
     * Test a clean import of private (not password-protected) To Do
     * records from a version 2 import file.  All existing records must
     * be replaced.
     */
    @Test
    public void testImportToDosClean2Private() throws IOException {

        addRandomToDos();

        MockProgressBar progress = runImporter("todo-misc-v2.xml",
                ImportType.CLEAN, true, null);

        SortedMap<Long,String> expectedCategories = new TreeMap<>();
        for (ToDoCategory cat : TEST_CATEGORIES_2)
            expectedCategories.put(cat.getId(), cat.getName());
        assertCategoriesEquals(expectedCategories);

        List<ToDoItem> expectedToDos = new ArrayList<>(TEST_TODOS_2);
        // Ensure the items are sorted by ID
        Collections.sort(expectedToDos, MockToDoRepository.TODO_ID_COMPARATOR);
        List<ToDoItem> actualTodos = readToDos();

        assertToDoEquals("Imported To Do records",
                expectedToDos, actualTodos);

        MockProgressBar.Progress endProgress = progress.getEndProgress();
        assertNotNull("Progress meter after import", endProgress);
        assertEquals("Total records in file", 12, endProgress.total);
        assertEquals("Number of records processed", 12, endProgress.current);

    }

    /**
     * Test importing private (not password-protected) To Do records in
     * {@link ImportType#REVERT REVERT} mode.  Any existing items with
     * the same ID as one from the XML import file should be overwritten.
     * This one uses the version 1 import file.
     */
    @Test
    public void testImportToDosRevert1Private() throws IOException {

        List<ToDoItem> oldItems = addRandomToDos();
        SortedMap<Long,String> expectedCategories = new TreeMap<>();
        for (ToDoCategory cat : TEST_CATEGORIES_1)
            expectedCategories.put(cat.getId(), cat.getName());

        runImporter("todo-misc-v1.xml", ImportType.REVERT, true, null);

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
                oldItems.size() + TEST_TODOS_1.size());
        for (ToDoItem todo : TEST_TODOS_1) {
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
                expectedToDos, actualToDos);

    }

    /**
     * Test importing public To Do records in {@link ImportType#REVERT REVERT}
     * mode.  Any existing items with the same ID as one from the XML
     * import file should be overwritten.  This one uses the version 2
     * import file.
     */
    @Test
    public void testImportToDosRevert2Public() throws IOException {

        List<ToDoItem> oldItems = addRandomToDos();
        SortedMap<Long,String> expectedCategories = new TreeMap<>();
        for (ToDoCategory cat : TEST_CATEGORIES_1)
            expectedCategories.put(cat.getId(), cat.getName());

        runImporter("todo-misc-v2.xml", ImportType.REVERT, false, null);

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
            if (todo.isPrivate())
                continue;
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
                expectedToDos, actualToDos);

    }

    /**
     * Test importing public To Do records in {@link ImportType#MERGE MERGE}
     * mode.  If an existing item has the same ID <i>and</i> description
     * <i>and</i> category name as an item being imported, most of it will
     * be replaced by the imported item except its create time which will be
     * preserved.  Otherwise the imported item will be assigned a new ID.
     * This one uses the version 1 import file.
     */
    @Test
    public void testImportToDosMerge1Public() throws IOException {

        List<ToDoItem> oldItems = addRandomToDos();
        SortedMap<Long,String> expectedCategories = new TreeMap<>();
        for (ToDoCategory cat : TEST_CATEGORIES_1)
            expectedCategories.put(cat.getId(), cat.getName());

        // For this test we need an item which exists
        // in the import file but with different details.
        ToDoItem rewrittenItem = null;
        while (rewrittenItem == null) {
            rewrittenItem = TEST_TODOS_1.get(RAND.nextInt(TEST_TODOS_1.size()));
            if ((rewrittenItem.getCategoryId() == ToDoCategory.UNFILED) ||
                    rewrittenItem.isPrivate()) {
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
        // Add a new record with the same ID, creation time,
        // category name, and description; and an earlier modification time.
        ToDoItem modItem = randomToDo();
        modItem.setId(rewrittenItem.getId());
        modItem.setCategoryId(sameCatName.getId());
        modItem.setCategoryName(sameCatName.getName());
        modItem.setDescription(rewrittenItem.getDescription());
        modItem.setCreateTime(rewrittenItem.getCreateTime());
        modItem.setModTime(rewrittenItem.getModTime().plusSeconds(
                -604800 + RAND.nextInt(86400)));
        mockRepo.insertItem(modItem);
        // Set our expected record to the imported record
        // but having a new category ID
        rewrittenItem = rewrittenItem.clone();
        rewrittenItem.setCategoryId(sameCatName.getId());
        oldItems.add(rewrittenItem);

        runImporter("todo-misc-v1.xml", ImportType.MERGE, false, null);

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
            if (todo.isPrivate() || (todo.getId() == rewrittenItem.getId()))
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
                expectedToDos, actualToDos);

    }

    /**
     * Test importing private (but not encrypted To Do records in
     * {@link ImportType#MERGE MERGE} mode.  If an existing item has the
     * same ID <i>and</i> description <i>and</i> category name as an item
     * being imported, most of it will be replaced by the imported item
     * except its create time which will be preserved.  Otherwise the
     * imported item will be assigned a new ID.  This one uses the
     * version 2 import file.
     */
    @Test
    public void testImportToDosMerge2Public() throws IOException {

        List<ToDoItem> oldItems = addRandomToDos();
        SortedMap<Long,String> expectedCategories = new TreeMap<>();
        for (ToDoCategory cat : TEST_CATEGORIES_1)
            expectedCategories.put(cat.getId(), cat.getName());

        // For this test we need an item which exists
        // in the import file but with different details.
        ToDoItem rewrittenItem = null;
        while (rewrittenItem == null) {
            rewrittenItem = TEST_TODOS_2.get(RAND.nextInt(TEST_TODOS_2.size()));
            if ((rewrittenItem.getCategoryId() == ToDoCategory.UNFILED) ||
                    rewrittenItem.isPrivate()) {
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
        // Add a new record with the same ID, creation time,
        // category name, and description; and an earlier modification time.
        ToDoItem modItem = randomToDo();
        modItem.setId(rewrittenItem.getId());
        modItem.setCategoryId(sameCatName.getId());
        modItem.setCategoryName(sameCatName.getName());
        modItem.setDescription(rewrittenItem.getDescription());
        modItem.setCreateTime(rewrittenItem.getCreateTime());
        modItem.setModTime(rewrittenItem.getModTime().plusSeconds(
                -604800 + RAND.nextInt(86400)));
        mockRepo.insertItem(modItem);
        // Set our expected record to the imported record
        // but having a new category ID
        rewrittenItem = rewrittenItem.clone();
        rewrittenItem.setCategoryId(sameCatName.getId());
        oldItems.add(rewrittenItem);

        runImporter("todo-misc-v2.xml", ImportType.MERGE, true, null);

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
        for (ToDoItem todo: TEST_TODOS_2) {
            if (todo.getId() == rewrittenItem.getId())
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
                expectedToDos, actualToDos);

    }

    /**
     * Test importing private (but not encrypted) To Do records in
     * {@link ImportType#ADD ADD} mode.  Any imported item with the same
     * ID as an existing item will be  assigned a new ID regardless of its
     * contents.  This test uses the version 1 import file.
     */
    @Test
    public void testImportToDosAdd1Private() throws IOException {

        List<ToDoItem> oldItems = addRandomToDos();
        SortedMap<Long,String> expectedCategories = new TreeMap<>();
        for (ToDoCategory cat : TEST_CATEGORIES_1)
            expectedCategories.put(cat.getId(), cat.getName());

        // For this test we need an item which exists
        // in the import file.
        ToDoItem alreadyImportedItem = null;
        while (alreadyImportedItem == null) {
            alreadyImportedItem = TEST_TODOS_1.get(RAND.nextInt(TEST_TODOS_1.size()));
            if (alreadyImportedItem.getCategoryId() == ToDoCategory.UNFILED) {
                alreadyImportedItem = null;
                break;
            }
            for (ToDoItem todo : oldItems) {
                if (todo.getId().equals(alreadyImportedItem.getId()) ||
                        (todo.getCategoryId() == ToDoCategory.UNFILED)) {
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

        runImporter("todo-misc-v1.xml", ImportType.ADD, true, null);

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
        for (ToDoItem todo : TEST_TODOS_1) {
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
                expectedToDos, actualToDos);

    }

    /**
     * Test importing public To Do records in {@link ImportType#ADD ADD}
     * mode.  Any imported item with the same ID as an existing item will
     * be  assigned a new ID regardless of its contents.  This test uses
     * the version 2 import file.
     */
    @Test
    public void testImportToDosAdd2Public() throws IOException {

        List<ToDoItem> oldItems = addRandomToDos();
        SortedMap<Long,String> expectedCategories = new TreeMap<>();
        for (ToDoCategory cat : TEST_CATEGORIES_1)
            expectedCategories.put(cat.getId(), cat.getName());

        // For this test we need an item which exists
        // in the import file.
        ToDoItem alreadyImportedItem = null;
        while (alreadyImportedItem == null) {
            alreadyImportedItem = TEST_TODOS_2.get(RAND.nextInt(TEST_TODOS_2.size()));
            if ((alreadyImportedItem.getCategoryId() == ToDoCategory.UNFILED) ||
                    alreadyImportedItem.isPrivate()) {
                alreadyImportedItem = null;
                continue;
            }
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

        runImporter("todo-misc-v2.xml", ImportType.ADD, false, null);

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
            if (todo.isPrivate())
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
                expectedToDos, actualToDos);

    }

    /**
     * Test importing a private, encrypted To Do record in version 1 format.
     */
    @Test
    public void testImportEncrypted1() throws IOException {

        ToDoItem expectedItem = new ToDoItem();
        expectedItem.setId(209);
        expectedItem.setCreateTime(Instant.ofEpochMilli(1472508699880L));
        expectedItem.setModTime(Instant.ofEpochMilli(1604598740959L));
        expectedItem.setPriority(1);
        expectedItem.setPrivate(StringEncryption.encryptionType());

        final String newPassword = SRAND.nextAlphanumeric(12, 16);
        globalEncryption.setPassword(newPassword.toCharArray());
        globalEncryption.addSalt();
        globalEncryption.storePassword(mockRepo);
        assertTrue("Current password was not set", globalEncryption.hasKey());

        expectedItem.setEncryptedDescription(
                globalEncryption.encrypt("Secret"));
        expectedItem.setEncryptedNote(
                globalEncryption.encrypt("Things haven\u2019t been the same\n"
                + "Since you came into my life\n"
                + "You found a way to touch my soul\n"
                + "And I\u2019m never, ever, ever gonna let it go\n"));

        runImporter("todo-encrypted-v1.xml", ImportType.CLEAN,
                true, "Jx4ppbO2t");

        assertToDoEquals("Imported To Do record",
                Collections.singletonList(expectedItem), readToDos());

    }

    /**
     * Test importing a private, encrypted To Do record in version 2 format.
     */
    @Test
    public void testImportEncrypted2() throws IOException {

        ToDoItem expectedItem = new ToDoItem();
        expectedItem.setId(699);
        expectedItem.setCreateTime(Instant.ofEpochMilli(1544970899405L));
        expectedItem.setModTime(Instant.ofEpochMilli(1551701350690L));
        expectedItem.setPriority(1);
        expectedItem.setPrivate(StringEncryption.encryptionType());

        final String newPassword = SRAND.nextAlphanumeric(12, 16);
        globalEncryption.setPassword(newPassword.toCharArray());
        globalEncryption.addSalt();
        globalEncryption.storePassword(mockRepo);
        assertTrue("Current password was not set", globalEncryption.hasKey());

        expectedItem.setEncryptedDescription(
                globalEncryption.encrypt("Secret Agent Man"));
        expectedItem.setEncryptedNote(
                globalEncryption.encrypt(
                        "There's a man who leads a life of danger\n"
                                + "To everyone he meets he stays a stranger\n"
                                + "With every move he makes\n"
                                + "Another chance he takes\n"
                                + "Odds are he won't live to see tomorrow\n"));

        runImporter("todo-encrypted-v2.xml", ImportType.CLEAN,
                true, "Jx4ppbO2t");

        assertToDoEquals("Imported To Do record",
                Collections.singletonList(expectedItem), readToDos());

    }

    /**
     * Test importing a To Do record with an alarm and last notification
     * time in version 1 format.  This format stored the alarm time in
     * milliseconds after midnight.
     */
    @Test
    public void testImportAlarmV1() throws IOException {

        ToDoItem expectedItem = new ToDoItem();
        expectedItem.setId(25561);
        expectedItem.setDescription("Take the garbage out");
        expectedItem.setCreateTime(Instant.ofEpochMilli(1305889877569L));
        expectedItem.setModTime(Instant.ofEpochMilli(1346383988429L));
        expectedItem.setPriority(1);
        expectedItem.setDue(LocalDate.of(2014, 7, 2));
        ToDoAlarm alarm = new ToDoAlarm();
        alarm.setAlarmDaysEarlier(10);
        alarm.setTime(LocalTime.of(14, 17, 54, 802000000));
        alarm.setNotificationTime(Instant.ofEpochMilli(1316867412706L));
        expectedItem.setAlarm(alarm);

        runImporter("todo-alarm-v1.xml", ImportType.CLEAN, false, null);

        assertToDoEquals("Imported To Do record",
                Collections.singletonList(expectedItem), readToDos());

    }

    /**
     * Test importing a To Do record with an alarm and last notification
     * time in version 2 format.  This format stored the alarm time as
     * a time string.
     */
    @Test
    public void testImportAlarmV2() throws IOException {

        ToDoItem expectedItem = new ToDoItem();
        expectedItem.setId(1782);
        expectedItem.setDescription("Call your mother");
        expectedItem.setCreateTime(Instant.ofEpochMilli(1578732812614L));
        expectedItem.setModTime(Instant.ofEpochMilli(1673031168362L));
        expectedItem.setPriority(1);
        expectedItem.setDue(LocalDate.of(2025, 2, 1));
        ToDoAlarm alarm = new ToDoAlarm();
        alarm.setAlarmDaysEarlier(191);
        alarm.setTime(LocalTime.of(22, 52, 25));
        alarm.setNotificationTime(Instant.ofEpochMilli(1612089767401L));
        expectedItem.setAlarm(alarm);

        runImporter("todo-alarm-v2.xml", ImportType.CLEAN, false, null);

        assertToDoEquals("Imported To Do record",
                Collections.singletonList(expectedItem), readToDos());

    }

    /**
     * Test importing a To Do record with a &ldquo;None&rdquo;
     * repeat interval in version 1 format.  This is just a repeat
     * interval with an ID of {@value ToDoItemColumns#REPEAT_NONE} and
     * no additional attributes.
     */
    @Test
    public void testImportRepeatNone1() throws IOException {

        ToDoItem expectedItem = new ToDoItem();
        expectedItem.setId(22778);
        expectedItem.setDescription("Don\u2019t repeat this");
        expectedItem.setCreateTime(Instant.ofEpochMilli(1599740666097L));
        expectedItem.setModTime(Instant.ofEpochMilli(1678647053074L));
        expectedItem.setPriority(1);
        expectedItem.setDue(LocalDate.of(2014, 8, 25));
        expectedItem.setRepeatInterval(new RepeatNone());

        runImporter("todo-repeatNone-v1.xml",
                ImportType.CLEAN, false, null);

        assertToDoEquals("Imported To Do record",
                Collections.singletonList(expectedItem), readToDos());

    }

    /**
     * Test importing a To Do record with a &ldquo;None&rdquo;
     * repeat interval in version 1 format.  This is just a repeat
     * interval with a type of {@link RepeatType#NONE NONE} and
     * no additional attributes.
     */
    @Test
    public void testImportRepeatNone2() throws IOException {

        ToDoItem expectedItem = new ToDoItem();
        expectedItem.setId(5832);
        expectedItem.setDescription("Don\u2019t repeat this");
        expectedItem.setCreateTime(Instant.ofEpochMilli(1346155929489L));
        expectedItem.setModTime(Instant.ofEpochMilli(1381501721393L));
        expectedItem.setPriority(1);
        expectedItem.setDue(LocalDate.of(2024, 3, 23));
        expectedItem.setRepeatInterval(new RepeatNone());

        runImporter("todo-repeatNone-v2.xml",
                ImportType.CLEAN, false, null);

        assertToDoEquals("Imported To Do record",
                Collections.singletonList(expectedItem), readToDos());

    }

    /**
     * Test importing a To Do record with a &ldquo;Daily&rdquo;
     * repeat interval in version 1 format.  This is a repeat interval
     * with an ID of {@value ToDoItemColumns#REPEAT_DAILY} and a set
     * of allowed weekdays along with global repeat attributes.
     */
    @Test
    public void testImportRepeatDaily1() throws IOException {

        ToDoItem expectedItem = new ToDoItem();
        expectedItem.setId(30408);
        expectedItem.setDescription("Repeat every Monday, Tuesday, and Thursday");
        expectedItem.setCreateTime(Instant.ofEpochMilli(1433545578370L));
        expectedItem.setModTime(Instant.ofEpochMilli(1728528754806L));
        expectedItem.setPriority(1);
        expectedItem.setDue(LocalDate.of(2018, 1, 12));
        RepeatDaily repeat = new RepeatDaily();
        repeat.setIncrement(1);
        repeat.setAllowedWeekDays(Set.of(WeekDays.MONDAY, WeekDays.TUESDAY,
                WeekDays.THURSDAY));
        repeat.setDirection(WeekdayDirection.CLOSEST_OR_NEXT);
        repeat.setEnd(LocalDate.of(2015, 6, 2));
        expectedItem.setRepeatInterval(repeat);

        runImporter("todo-repeatDaily-v1.xml",
                ImportType.CLEAN, false, null);

        assertToDoEquals("Imported To Do record",
                Collections.singletonList(expectedItem), readToDos());

    }

    /**
     * Test importing a To Do record with a &ldquo;Daily&rdquo;
     * repeat interval in version 2 format.  This is a repeat interval
     * with a type of {@link RepeatType#DAILY DAILY} and a set
     * of allowed weekdays along with global repeat attributes.
     */
    @Test
    public void testImportRepeatDaily2() throws IOException {

        ToDoItem expectedItem = new ToDoItem();
        expectedItem.setId(6804);
        expectedItem.setDescription("Repeat every 6 days except Thursday");
        expectedItem.setCreateTime(Instant.ofEpochMilli(1368544597277L));
        expectedItem.setModTime(Instant.ofEpochMilli(1741125707450L));
        expectedItem.setPriority(1);
        expectedItem.setDue(LocalDate.of(2011, 11, 1));
        RepeatDaily repeat = new RepeatDaily();
        repeat.setIncrement(6);
        repeat.setAllowedWeekDays(Set.of(WeekDays.SUNDAY,
                WeekDays.MONDAY, WeekDays.TUESDAY, WeekDays.WEDNESDAY,
                WeekDays.FRIDAY, WeekDays.SATURDAY));
        repeat.setDirection(WeekdayDirection.CLOSEST_OR_NEXT);
        repeat.setEnd(LocalDate.of(2019, 3, 5));
        expectedItem.setRepeatInterval(repeat);

        runImporter("todo-repeatDaily-v2.xml",
                ImportType.CLEAN, false, null);

        assertToDoEquals("Imported To Do record",
                Collections.singletonList(expectedItem), readToDos());

    }

    /**
     * Test importing a To Do record with a &ldquo;Day After&rdquo;
     * repeat interval in version 1 format.  This is a repeat interval
     * with an ID of {@value ToDoItemColumns#REPEAT_DAY_AFTER} and a set
     * of allowed weekdays along with global repeat attributes.
     */
    @Test
    public void testImportRepeatDayAfter1() throws IOException {

        ToDoItem expectedItem = new ToDoItem();
        expectedItem.setId(721);
        expectedItem.setDescription("Repeat 5 days after on"
                + " Tuesday, Wednesday, or Saturday");
        expectedItem.setCreateTime(Instant.ofEpochMilli(1366676389328L));
        expectedItem.setModTime(Instant.ofEpochMilli(1448502477450L));
        expectedItem.setPriority(1);
        expectedItem.setDue(LocalDate.of(2018, 3, 25));
        RepeatDayAfter repeat = new RepeatDayAfter();
        repeat.setIncrement(6);
        repeat.setAllowedWeekDays(Set.of(WeekDays.TUESDAY,
                WeekDays.WEDNESDAY, WeekDays.SATURDAY));
        repeat.setDirection(WeekdayDirection.PREVIOUS);
        repeat.setEnd(LocalDate.of(2013, 1, 7));
        expectedItem.setRepeatInterval(repeat);

        runImporter("todo-repeatDayAfter-v1.xml",
                ImportType.CLEAN, false, null);

        assertToDoEquals("Imported To Do record",
                Collections.singletonList(expectedItem), readToDos());

    }

    /**
     * Test importing a To Do record with a &ldquo;Day After&rdquo;
     * repeat interval in version 2 format.  This is a repeat interval
     * with a type of {@link RepeatType#DAY_AFTER DAY_AFTER} and a set
     * of allowed weekdays along with global repeat attributes.
     */
    @Test
    public void testImportRepeatDayAfter2() throws IOException {

        ToDoItem expectedItem = new ToDoItem();
        expectedItem.setId(1030);
        expectedItem.setDescription("Repeat 7 days after on Sunday or Tuesday");
        expectedItem.setCreateTime(Instant.ofEpochMilli(1337583969478L));
        expectedItem.setModTime(Instant.ofEpochMilli(1558733607465L));
        expectedItem.setPriority(1);
        expectedItem.setDue(LocalDate.of(2015, 6, 25));
        RepeatDayAfter repeat = new RepeatDayAfter();
        repeat.setIncrement(7);
        repeat.setAllowedWeekDays(Set.of(WeekDays.SUNDAY, WeekDays.TUESDAY));
        repeat.setDirection(WeekdayDirection.PREVIOUS);
        repeat.setEnd(LocalDate.of(2016, 7, 5));
        expectedItem.setRepeatInterval(repeat);

        runImporter("todo-repeatDayAfter-v2.xml",
                ImportType.CLEAN, false, null);

        assertToDoEquals("Imported To Do record",
                Collections.singletonList(expectedItem), readToDos());

    }

    /**
     * Test importing a To Do record with a &ldquo;Weekly&rdquo;
     * repeat interval in version 1 format.  This is a repeat interval
     * with an ID of {@value ToDoItemColumns#REPEAT_WEEKLY} and a set
     * of weekdays along with global repeat attributes.
     */
    @Test
    public void testImportRepeatWeekly1() throws IOException {

        ToDoItem expectedItem = new ToDoItem();
        expectedItem.setId(29991);
        expectedItem.setDescription("Repeat every 52 weeks on"
                + " Monday, Tuesday, Friday, and Saturday");
        expectedItem.setCreateTime(Instant.ofEpochMilli(1347785300589L));
        expectedItem.setModTime(Instant.ofEpochMilli(1757052971682L));
        expectedItem.setPriority(1);
        expectedItem.setDue(LocalDate.of(2012, 12, 4));
        RepeatWeekly repeat = new RepeatWeekly();
        repeat.setIncrement(52);
        repeat.setWeekDays(Set.of(WeekDays.MONDAY, WeekDays.TUESDAY,
                WeekDays.FRIDAY, WeekDays.SATURDAY));
        repeat.setEnd(LocalDate.of(2015, 11, 11));
        expectedItem.setRepeatInterval(repeat);

        runImporter("todo-repeatWeekly-v1.xml",
                ImportType.CLEAN, false, null);

        assertToDoEquals("Imported To Do record",
                Collections.singletonList(expectedItem), readToDos());

    }

    /**
     * Test importing a To Do record with a &ldquo;Weekly&rdquo;
     * repeat interval in version 2 format.  This is a repeat interval
     * with a type of {@link RepeatType#WEEKLY WEEKLY} and a set
     * of weekdays along with global repeat attributes.
     */
    @Test
    public void testImportRepeatWeekly2() throws IOException {

        ToDoItem expectedItem = new ToDoItem();
        expectedItem.setId(24065);
        expectedItem.setDescription("Repeat every 29 weeks"
                + " on Sunday, Monday, Tuesday, and Friday");
        expectedItem.setCreateTime(Instant.ofEpochMilli(1329422040162L));
        expectedItem.setModTime(Instant.ofEpochMilli(1391480968611L));
        expectedItem.setPriority(1);
        expectedItem.setDue(LocalDate.of(2024, 9, 8));
        RepeatWeekly repeat = new RepeatWeekly();
        repeat.setIncrement(29);
        repeat.setWeekDays(Set.of(WeekDays.SUNDAY, WeekDays.MONDAY,
                WeekDays.TUESDAY, WeekDays.FRIDAY));
        repeat.setEnd(LocalDate.of(2025, 7, 16));
        expectedItem.setRepeatInterval(repeat);

        runImporter("todo-repeatWeekly-v2.xml",
                ImportType.CLEAN, false, null);

        assertToDoEquals("Imported To Do record",
                Collections.singletonList(expectedItem), readToDos());

    }

    /**
     * Test importing a To Do record with a &ldquo;Week After&rdquo;
     * repeat interval in version 1 format.  This is a repeat interval
     * with an ID of {@value ToDoItemColumns#REPEAT_WEEK_AFTER} and a set
     * of allowed weekdays along with global repeat attributes.
     */
    @Test
    public void testImportRepeatWeekAfter1() throws IOException {

        ToDoItem expectedItem = new ToDoItem();
        expectedItem.setId(27022);
        expectedItem.setDescription("Repeat 4 weeks after on Sunday"
                + " or Tuesday through Friday");
        expectedItem.setCreateTime(Instant.ofEpochMilli(1313305067862L));
        expectedItem.setModTime(Instant.ofEpochMilli(1364671080436L));
        expectedItem.setPriority(1);
        expectedItem.setDue(LocalDate.of(2015, 10, 11));
        RepeatWeekAfter repeat = new RepeatWeekAfter();
        repeat.setIncrement(10);
        repeat.setAllowedWeekDays(Set.of(WeekDays.SUNDAY, WeekDays.TUESDAY,
                WeekDays.WEDNESDAY, WeekDays.THURSDAY, WeekDays.FRIDAY));
        repeat.setDirection(WeekdayDirection.NEXT);
        repeat.setEnd(LocalDate.of(2018, 4, 6));
        expectedItem.setRepeatInterval(repeat);

        runImporter("todo-repeatWeekAfter-v1.xml",
                ImportType.CLEAN, false, null);

        assertToDoEquals("Imported To Do record",
                Collections.singletonList(expectedItem), readToDos());

    }

    /**
     * Test importing a To Do record with a &ldquo;Week After&rdquo;
     * repeat interval in version 2 format.  This is a repeat interval
     * with a type of {@link RepeatType#WEEK_AFTER WEEK_AFTER} and a set
     * of allowed weekdays along with global repeat attributes.
     */
    @Test
    public void testImportRepeatWeekAfter2() throws IOException {

        ToDoItem expectedItem = new ToDoItem();
        expectedItem.setId(13167);
        expectedItem.setDescription("Repeat 4 weeks after on Sunday");
        expectedItem.setCreateTime(Instant.ofEpochMilli(1347954121924L));
        expectedItem.setModTime(Instant.ofEpochMilli(1487207699850L));
        expectedItem.setPriority(1);
        expectedItem.setDue(LocalDate.of(2021, 6, 17));
        RepeatWeekAfter repeat = new RepeatWeekAfter();
        repeat.setIncrement(6);
        repeat.setAllowedWeekDays(Set.of(WeekDays.SUNDAY));
        repeat.setDirection(WeekdayDirection.CLOSEST_OR_NEXT);
        repeat.setEnd(LocalDate.of(2023, 7, 16));
        expectedItem.setRepeatInterval(repeat);

        runImporter("todo-repeatWeekAfter-v2.xml",
                ImportType.CLEAN, false, null);

        assertToDoEquals("Imported To Do record",
                Collections.singletonList(expectedItem), readToDos());

    }

    /**
     * Test importing a To Do record with a &ldquo;Semi-monthly On Days&rdquo;
     * repeat interval in version 1 format.  This is a repeat interval
     * with an ID of {@value ToDoItemColumns#REPEAT_SEMI_MONTHLY_ON_DAYS} and
     * a pair of days and weeks along with global repeat attributes.
     */
    @Test
    public void testImportRepeatSemiMonthlyOnDays1() throws IOException {

        ToDoItem expectedItem = new ToDoItem();
        expectedItem.setId(1029);
        expectedItem.setDescription("Repeat every month on"
                + " the first Saturday and last Sunday");
        expectedItem.setCreateTime(Instant.ofEpochMilli(1326520904370L));
        expectedItem.setModTime(Instant.ofEpochMilli(1495664711576L));
        expectedItem.setPriority(1);
        expectedItem.setDue(LocalDate.of(2020, 12, 27));
        RepeatSemiMonthlyOnDays repeat = new RepeatSemiMonthlyOnDays();
        repeat.setIncrement(1);
        repeat.setDay(WeekDays.SATURDAY);
        repeat.setWeek(1);
        repeat.setDay2(WeekDays.SUNDAY);
        repeat.setWeek2(-1);
        repeat.setEnd(LocalDate.of(2025, 1, 31));
        expectedItem.setRepeatInterval(repeat);

        runImporter("todo-repeatSemiMonthlyOnDays-v1.xml",
                ImportType.CLEAN, false, null);

        assertToDoEquals("Imported To Do record",
                Collections.singletonList(expectedItem), readToDos());

    }

    /**
     * Test importing a To Do record with a &ldquo;Semi-monthly On Days&rdquo;
     * repeat interval in version 2 format.  This is a repeat interval with
     * a type of {@link RepeatType#SEMI_MONTHLY_ON_DAYS SEMI_MONTHLY_ON_DAYS}
     * and a pair of days and weeks along with global repeat attributes.
     */
    @Test
    public void testImportRepeatSemiMonthlyOnDays2() throws IOException {

        ToDoItem expectedItem = new ToDoItem();
        expectedItem.setId(11603);
        expectedItem.setDescription("Repeat every 10 months on"
                + " the second Monday and fourth Friday");
        expectedItem.setCreateTime(Instant.ofEpochMilli(1354106087863L));
        expectedItem.setModTime(Instant.ofEpochMilli(1365183274239L));
        expectedItem.setPriority(1);
        expectedItem.setDue(LocalDate.of(2016, 7, 11));
        RepeatSemiMonthlyOnDays repeat = new RepeatSemiMonthlyOnDays();
        repeat.setIncrement(10);
        repeat.setDay(WeekDays.MONDAY);
        repeat.setWeek(2);
        repeat.setDay2(WeekDays.FRIDAY);
        repeat.setWeek2(4);
        repeat.setEnd(LocalDate.of(2017, 7, 31));
        expectedItem.setRepeatInterval(repeat);

        runImporter("todo-repeatSemiMonthlyOnDays-v2.xml",
                ImportType.CLEAN, false, null);

        assertToDoEquals("Imported To Do record",
                Collections.singletonList(expectedItem), readToDos());

    }

    /**
     * Test importing a To Do record with a &ldquo;Semi-monthly On Dates&rdquo;
     * repeat interval in version 1 format.  This is a repeat interval
     * with an ID of {@value ToDoItemColumns#REPEAT_SEMI_MONTHLY_ON_DATES},
     * a set of allowed weekdays, and a pair of dates along with global
     * repeat attributes.
     */
    @Test
    public void testImportRepeatSemiMonthlyOnDates1() throws IOException {

        ToDoItem expectedItem = new ToDoItem();
        expectedItem.setId(30611);
        expectedItem.setDescription("Repeat every 10 months"
                + " on the next Monday or Tuesday after the 6th and 23rd");
        expectedItem.setCreateTime(Instant.ofEpochMilli(1326538698269L));
        expectedItem.setModTime(Instant.ofEpochMilli(1513419246918L));
        expectedItem.setPriority(1);
        expectedItem.setDue(LocalDate.of(2019, 2, 6));
        RepeatSemiMonthlyOnDates repeat = new RepeatSemiMonthlyOnDates();
        repeat.setIncrement(10);
        repeat.setDate(6);
        repeat.setDate2(23);
        repeat.setAllowedWeekDays(Set.of(WeekDays.MONDAY, WeekDays.TUESDAY));
        repeat.setDirection(WeekdayDirection.NEXT);
        repeat.setEnd(LocalDate.of(2020, 1, 18));
        expectedItem.setRepeatInterval(repeat);

        runImporter("todo-repeatSemiMonthlyOnDates-v1.xml",
                ImportType.CLEAN, false, null);

        assertToDoEquals("Imported To Do record",
                Collections.singletonList(expectedItem), readToDos());

    }

    /**
     * Test importing a To Do record with a &ldquo;Semi-monthly On Dates&rdquo;
     * repeat interval in version 2 format.  This is a repeat interval with
     * a type of {@link RepeatType#SEMI_MONTHLY_ON_DATES SEMI_MONTHLY_ON_DATES},
     * a set of allowed weekdays, and a pair of dates along with global
     * repeat attributes.
     */
    @Test
    public void testImportRepeatSemiMonthlyOnDates2() throws IOException {

        ToDoItem expectedItem = new ToDoItem();
        expectedItem.setId(12001);
        expectedItem.setDescription("Repeat every 10 months on the"
                + " next Tuesday through Friday after the 10th and 22nd");
        expectedItem.setCreateTime(Instant.ofEpochMilli(1339970767209L));
        expectedItem.setModTime(Instant.ofEpochMilli(1536728892020L));
        expectedItem.setPriority(1);
        expectedItem.setDue(LocalDate.of(2020, 10, 10));
        RepeatSemiMonthlyOnDates repeat = new RepeatSemiMonthlyOnDates();
        repeat.setIncrement(10);
        repeat.setDate(10);
        repeat.setDate2(22);
        repeat.setAllowedWeekDays(Set.of(WeekDays.TUESDAY,
                WeekDays.WEDNESDAY, WeekDays.THURSDAY, WeekDays.FRIDAY));
        repeat.setDirection(WeekdayDirection.NEXT);
        repeat.setEnd(LocalDate.of(2021, 6, 9));
        expectedItem.setRepeatInterval(repeat);

        runImporter("todo-repeatSemiMonthlyOnDates-v2.xml",
                ImportType.CLEAN, false, null);

        assertToDoEquals("Imported To Do record",
                Collections.singletonList(expectedItem), readToDos());

    }

    /**
     * Test importing a To Do record with a &ldquo;Monthly On Day&rdquo;
     * repeat interval in version 1 format.  This is a repeat interval with
     * an ID of {@value ToDoItemColumns#REPEAT_MONTHLY_ON_DAY}
     * and a day and week along with global repeat attributes.
     */
    @Test
    public void testImportRepeatMonthlyOnDay1() throws IOException {

        ToDoItem expectedItem = new ToDoItem();
        expectedItem.setId(7831);
        expectedItem.setDescription("Repeat every month on the last Friday");
        expectedItem.setCreateTime(Instant.ofEpochMilli(1330525077491L));
        expectedItem.setModTime(Instant.ofEpochMilli(1487550484461L));
        expectedItem.setPriority(1);
        expectedItem.setDue(LocalDate.of(2017, 9, 29));
        RepeatMonthlyOnDay repeat = new RepeatMonthlyOnDay();
        repeat.setIncrement(1);
        repeat.setDay(WeekDays.FRIDAY);
        repeat.setWeek(-1);
        repeat.setEnd(LocalDate.of(2025, 12, 2));
        expectedItem.setRepeatInterval(repeat);

        runImporter("todo-repeatMonthlyOnDay-v1.xml",
                ImportType.CLEAN, false, null);

        assertToDoEquals("Imported To Do record",
                Collections.singletonList(expectedItem), readToDos());

    }

    /**
     * Test importing a To Do record with a &ldquo;Monthly On Day&rdquo;
     * repeat interval in version 2 format.  This is a repeat interval with
     * a type of {@link RepeatType#MONTHLY_ON_DAY MONTHLY_ON_DAY}
     * and a day and week along with global repeat attributes.
     */
    @Test
    public void testImportRepeatMonthlyOnDay2() throws IOException {

        ToDoItem expectedItem = new ToDoItem();
        expectedItem.setId(28328);
        expectedItem.setDescription("Repeat every 5 months"
                + " on the third Thursday");
        expectedItem.setCreateTime(Instant.ofEpochMilli(1520444719696L));
        expectedItem.setModTime(Instant.ofEpochMilli(1567221905403L));
        expectedItem.setPriority(1);
        expectedItem.setDue(LocalDate.of(2022, 9, 15));
        RepeatMonthlyOnDay repeat = new RepeatMonthlyOnDay();
        repeat.setIncrement(5);
        repeat.setDay(WeekDays.THURSDAY);
        repeat.setWeek(3);
        repeat.setEnd(LocalDate.of(2024, 4, 27));
        expectedItem.setRepeatInterval(repeat);

        runImporter("todo-repeatMonthlyOnDay-v2.xml",
                ImportType.CLEAN, false, null);

        assertToDoEquals("Imported To Do record",
                Collections.singletonList(expectedItem), readToDos());

    }

    /**
     * Test importing a To Do record with a &ldquo;Monthly On Date&rdquo;
     * repeat interval in version 1 format.  This is a repeat interval
     * with an ID of {@value ToDoItemColumns#REPEAT_MONTHLY_ON_DATE},
     * a set of allowed weekdays, and a date along with global
     * repeat attributes.
     */
    @Test
    public void testImportRepeatMonthlyOnDate1() throws IOException {

        ToDoItem expectedItem = new ToDoItem();
        expectedItem.setId(416);
        expectedItem.setDescription("Repeat every 8 months on the 25th"
                + " on Sunday, Monday, Thursday, or Saturday");
        expectedItem.setCreateTime(Instant.ofEpochMilli(1332151913543L));
        expectedItem.setModTime(Instant.ofEpochMilli(1505950508464L));
        expectedItem.setPriority(1);
        expectedItem.setDue(LocalDate.of(2018, 6, 25));
        RepeatMonthlyOnDate repeat = new RepeatMonthlyOnDate();
        repeat.setIncrement(9);
        repeat.setDate(25);
        repeat.setAllowedWeekDays(Set.of(WeekDays.SUNDAY, WeekDays.MONDAY,
                WeekDays.THURSDAY, WeekDays.SATURDAY));
        repeat.setDirection(WeekdayDirection.PREVIOUS);
        repeat.setEnd(LocalDate.of(2024, 9, 8));
        expectedItem.setRepeatInterval(repeat);

        runImporter("todo-repeatMonthlyOnDate-v1.xml",
                ImportType.CLEAN, false, null);

        assertToDoEquals("Imported To Do record",
                Collections.singletonList(expectedItem), readToDos());

    }

    /**
     * Test importing a To Do record with a &ldquo;Monthly On Date&rdquo;
     * repeat interval in version 2 format.  This is a repeat interval with
     * a type of {@link RepeatType#MONTHLY_ON_DATE MONTHLY_ON_DATE},
     * a set of allowed weekdays, and a date along with global repeat
     * attributes.
     */
    @Test
    public void testImportRepeatMonthlyOnDate2() throws IOException {

        ToDoItem expectedItem = new ToDoItem();
        expectedItem.setId(5750);
        expectedItem.setDescription("Repeat every 10 months on the 29th"
                + " on Monday, Wednesday, or Friday");
        expectedItem.setCreateTime(Instant.ofEpochMilli(1560863694183L));
        expectedItem.setModTime(Instant.ofEpochMilli(1646457337176L));
        expectedItem.setPriority(1);
        expectedItem.setDue(LocalDate.of(2022, 9, 29));
        RepeatMonthlyOnDate repeat = new RepeatMonthlyOnDate();
        repeat.setIncrement(10);
        repeat.setDate(29);
        repeat.setAllowedWeekDays(Set.of(WeekDays.MONDAY, WeekDays.WEDNESDAY,
                WeekDays.FRIDAY));
        repeat.setDirection(WeekdayDirection.PREVIOUS);
        repeat.setEnd(LocalDate.of(2022, 12, 6));
        expectedItem.setRepeatInterval(repeat);

        runImporter("todo-repeatMonthlyOnDate-v2.xml",
                ImportType.CLEAN, false, null);

        assertToDoEquals("Imported To Do record",
                Collections.singletonList(expectedItem), readToDos());

    }

    /**
     * Test importing a To Do record with a &ldquo;Month After&rdquo;
     * repeat interval in version 1 format.  This is a repeat interval
     * with an ID of {@value ToDoItemColumns#REPEAT_MONTH_AFTER} and
     * a set of allowed weekdays along with global repeat attributes.
     */
    @Test
    public void testImportRepeatMonthAfter1() throws IOException {

        ToDoItem expectedItem = new ToDoItem();
        expectedItem.setId(15101);
        expectedItem.setDescription("Repeat 4 months later"
                + " except for Wednesday");
        expectedItem.setCreateTime(Instant.ofEpochMilli(1323430741741L));
        expectedItem.setModTime(Instant.ofEpochMilli(1378197078334L));
        expectedItem.setPriority(1);
        expectedItem.setDue(LocalDate.of(2015, 3, 31));
        RepeatMonthAfter repeat = new RepeatMonthAfter();
        repeat.setIncrement(4);
        repeat.setAllowedWeekDays(Set.of(WeekDays.SUNDAY, WeekDays.MONDAY,
                WeekDays.TUESDAY, WeekDays.THURSDAY,
                WeekDays.FRIDAY, WeekDays.SATURDAY));
        repeat.setDirection(WeekdayDirection.NEXT);
        repeat.setEnd(LocalDate.of(2021, 8, 5));
        expectedItem.setRepeatInterval(repeat);

        runImporter("todo-repeatMonthAfter-v1.xml",
                ImportType.CLEAN, false, null);

        assertToDoEquals("Imported To Do record",
                Collections.singletonList(expectedItem), readToDos());

    }

    /**
     * Test importing a To Do record with a &ldquo;Month After&rdquo;
     * repeat interval in version 2 format.  This is a repeat interval with
     * a type of {@link RepeatType#MONTH_AFTER MONTH_AFTER} and
     * a set of allowed weekdays along with global repeat attributes.
     */
    @Test
    public void testImportRepeatMonthAfter2() throws IOException {

        ToDoItem expectedItem = new ToDoItem();
        expectedItem.setId(8714);
        expectedItem.setDescription("Repeat 9 months later"
                + " except for Thursday");
        expectedItem.setCreateTime(Instant.ofEpochMilli(1385784413559L));
        expectedItem.setModTime(Instant.ofEpochMilli(1491943650251L));
        expectedItem.setPriority(1);
        expectedItem.setDue(LocalDate.of(2018, 8, 11));
        RepeatMonthAfter repeat = new RepeatMonthAfter();
        repeat.setIncrement(10);
        repeat.setAllowedWeekDays(Set.of(WeekDays.SUNDAY, WeekDays.MONDAY,
                WeekDays.TUESDAY, WeekDays.WEDNESDAY,
                WeekDays.FRIDAY, WeekDays.SATURDAY));
        repeat.setDirection(WeekdayDirection.CLOSEST_OR_NEXT);
        repeat.setEnd(LocalDate.of(2023, 12, 19));
        expectedItem.setRepeatInterval(repeat);

        runImporter("todo-repeatMonthAfter-v2.xml",
                ImportType.CLEAN, false, null);

        assertToDoEquals("Imported To Do record",
                Collections.singletonList(expectedItem), readToDos());

    }

    /**
     * Test importing a To Do record with a &ldquo;Yearly On Day&rdquo;
     * repeat interval in version 1 format.  This is a repeat interval with
     * an ID of {@value ToDoItemColumns#REPEAT_YEARLY_ON_DAY}
     * and a day, week, and month along with global repeat attributes.
     */
    @Test
    public void testImportRepeatYearlyOnDay1() throws IOException {

        ToDoItem expectedItem = new ToDoItem();
        expectedItem.setId(29016);
        expectedItem.setDescription("Repeat every 10 years on"
                + " the fourth Tuesday of September");
        expectedItem.setCreateTime(Instant.ofEpochMilli(1309933258387L));
        expectedItem.setModTime(Instant.ofEpochMilli(1351205899851L));
        expectedItem.setPriority(1);
        expectedItem.setDue(LocalDate.of(2018, 9, 25));
        RepeatYearlyOnDay repeat = new RepeatYearlyOnDay();
        repeat.setIncrement(10);
        repeat.setDay(WeekDays.TUESDAY);
        repeat.setWeek(4);
        repeat.setMonth(Months.SEPTEMBER);
        repeat.setEnd(LocalDate.of(2024, 12, 20));
        expectedItem.setRepeatInterval(repeat);

        runImporter("todo-repeatYearlyOnDay-v1.xml",
                ImportType.CLEAN, false, null);

        assertToDoEquals("Imported To Do record",
                Collections.singletonList(expectedItem), readToDos());

    }

    /**
     * Test importing a To Do record with a &ldquo;Yearly On Day&rdquo;
     * repeat interval in version 2 format.  This is a repeat interval with
     * a type of {@link RepeatType#YEARLY_ON_DAY YEARLY_ON_DAY}
     * and a day, week, and month along with global repeat attributes.
     */
    @Test
    public void testImportRepeatYearlyOnDay2() throws IOException {

        ToDoItem expectedItem = new ToDoItem();
        expectedItem.setId(13749);
        expectedItem.setDescription("Repeat every 8 years"
                + " on the second Sunday of September");
        expectedItem.setCreateTime(Instant.ofEpochMilli(1364584604306L));
        expectedItem.setModTime(Instant.ofEpochMilli(1496175535699L));
        expectedItem.setPriority(1);
        expectedItem.setDue(LocalDate.of(2018, 9, 4));
        RepeatYearlyOnDay repeat = new RepeatYearlyOnDay();
        repeat.setIncrement(8);
        repeat.setDay(WeekDays.SUNDAY);
        repeat.setWeek(2);
        repeat.setMonth(Months.SEPTEMBER);
        repeat.setEnd(LocalDate.of(2023, 12, 9));
        expectedItem.setRepeatInterval(repeat);

        runImporter("todo-repeatYearlyOnDay-v2.xml",
                ImportType.CLEAN, false, null);

        assertToDoEquals("Imported To Do record",
                Collections.singletonList(expectedItem), readToDos());

    }

    /**
     * Test importing a To Do record with a &ldquo;Yearly On Date&rdquo;
     * repeat interval in version 1 format.  This is a repeat interval
     * with an ID of {@value ToDoItemColumns#REPEAT_YEARLY_ON_DATE},
     * a set of allowed weekdays, a date, and a month along with global
     * repeat attributes.
     */
    @Test
    public void testImportRepeatYearlyOnDate1() throws IOException {

        ToDoItem expectedItem = new ToDoItem();
        expectedItem.setId(14973);
        expectedItem.setDescription("Repeat every 3 years"
                + " on February 13 except for Friday");
        expectedItem.setCreateTime(Instant.ofEpochMilli(1356927551215L));
        expectedItem.setModTime(Instant.ofEpochMilli(1387705376580L));
        expectedItem.setPriority(1);
        expectedItem.setDue(LocalDate.of(2022, 2, 13));
        RepeatYearlyOnDate repeat = new RepeatYearlyOnDate();
        repeat.setIncrement(3);
        repeat.setDate(13);
        repeat.setMonth(Months.FEBRUARY);
        repeat.setAllowedWeekDays(Set.of(WeekDays.SUNDAY, WeekDays.MONDAY,
                WeekDays.TUESDAY, WeekDays.WEDNESDAY,
                WeekDays.THURSDAY, WeekDays.SATURDAY));
        repeat.setDirection(WeekdayDirection.CLOSEST_OR_NEXT);
        repeat.setEnd(LocalDate.of(2023, 9, 13));
        expectedItem.setRepeatInterval(repeat);

        runImporter("todo-repeatYearlyOnDate-v1.xml",
                ImportType.CLEAN, false, null);

        assertToDoEquals("Imported To Do record",
                Collections.singletonList(expectedItem), readToDos());

    }

    /**
     * Test importing a To Do record with a &ldquo;Yearly On Date&rdquo;
     * repeat interval in version 2 format.  This is a repeat interval with
     * a type of {@link RepeatType#YEARLY_ON_DATE YEARLY_ON_DATE},
     * a set of allowed weekdays, a date, and a month along with global
     * repeat attributes.
     */
    @Test
    public void testImportRepeatYearlyOnDate2() throws IOException {

        ToDoItem expectedItem = new ToDoItem();
        expectedItem.setId(16459);
        expectedItem.setDescription("Repeat every 13 years on September 8"
                + " on Monday or Thursday");
        expectedItem.setCreateTime(Instant.ofEpochMilli(1479128631196L));
        expectedItem.setModTime(Instant.ofEpochMilli(1527642737368L));
        expectedItem.setPriority(1);
        expectedItem.setDue(LocalDate.of(2020, 9, 8));
        RepeatYearlyOnDate repeat = new RepeatYearlyOnDate();
        repeat.setIncrement(13);
        repeat.setDate(8);
        repeat.setMonth(Months.SEPTEMBER);
        repeat.setAllowedWeekDays(Set.of(WeekDays.MONDAY, WeekDays.THURSDAY));
        repeat.setDirection(WeekdayDirection.CLOSEST_OR_PREVIOUS);
        repeat.setEnd(LocalDate.of(2023, 1, 11));
        expectedItem.setRepeatInterval(repeat);

        runImporter("todo-repeatYearlyOnDate-v2.xml",
                ImportType.CLEAN, false, null);

        assertToDoEquals("Imported To Do record",
                Collections.singletonList(expectedItem), readToDos());

    }

    /**
     * Test importing a To Do record with a &ldquo;Year After&rdquo;
     * repeat interval in version 1 format.  This is a repeat interval
     * with an ID of {@value ToDoItemColumns#REPEAT_YEAR_AFTER} and
     * a set of allowed weekdays along with global repeat attributes.
     */
    @Test
    public void testImportRepeatYearAfter1() throws IOException {

        ToDoItem expectedItem = new ToDoItem();
        expectedItem.setId(2534);
        expectedItem.setDescription("Repeat 12 years later"
                + " on Tuesday or Thursday");
        expectedItem.setCreateTime(Instant.ofEpochMilli(1356169712234L));
        expectedItem.setModTime(Instant.ofEpochMilli(1374215342913L));
        expectedItem.setPriority(1);
        expectedItem.setDue(LocalDate.of(2021, 8, 13));
        RepeatYearAfter repeat = new RepeatYearAfter();
        repeat.setIncrement(12);
        repeat.setAllowedWeekDays(Set.of(WeekDays.TUESDAY, WeekDays.THURSDAY));
        repeat.setDirection(WeekdayDirection.CLOSEST_OR_NEXT);
        repeat.setEnd(LocalDate.of(2022, 11, 28));
        expectedItem.setRepeatInterval(repeat);

        runImporter("todo-repeatYearAfter-v1.xml",
                ImportType.CLEAN, false, null);

        assertToDoEquals("Imported To Do record",
                Collections.singletonList(expectedItem), readToDos());

    }

    /**
     * Test importing a To Do record with a &ldquo;Year After&rdquo;
     * repeat interval in version 2 format.  This is a repeat interval with
     * a type of {@link RepeatType#YEAR_AFTER YEAR_AFTER} and
     * a set of allowed weekdays along with global repeat attributes.
     */
    @Test
    public void testImportRepeatYearAfter2() throws IOException {

        ToDoItem expectedItem = new ToDoItem();
        expectedItem.setId(32159);
        expectedItem.setDescription("Repeat next year on"
                + " an earlier Sunday, Tuesday, or Thursday");
        expectedItem.setCreateTime(Instant.ofEpochMilli(1317247849701L));
        expectedItem.setModTime(Instant.ofEpochMilli(1331427632532L));
        expectedItem.setPriority(1);
        expectedItem.setDue(LocalDate.of(2017, 3, 21));
        RepeatYearAfter repeat = new RepeatYearAfter();
        repeat.setIncrement(1);
        repeat.setAllowedWeekDays(Set.of(WeekDays.SUNDAY,
                WeekDays.TUESDAY, WeekDays.THURSDAY));
        repeat.setDirection(WeekdayDirection.PREVIOUS);
        repeat.setEnd(LocalDate.of(2021, 6, 17));
        expectedItem.setRepeatInterval(repeat);

        runImporter("todo-repeatYearAfter-v2.xml",
                ImportType.CLEAN, false, null);

        assertToDoEquals("Imported To Do record",
                Collections.singletonList(expectedItem), readToDos());

    }

    // To Do: Write the rest of the test cases ...

}
