/*
 * Copyright © 2025 Trevin Beattie
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
package com.xmission.trevin.android.todo.data;

import static com.xmission.trevin.android.todo.data.ToDoPreferences.*;
import static org.junit.Assert.*;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Tests for reading and writing Note Pad preferences
 *
 * @author Trevin Beattie
 */
public class ToDoPreferencesTests
        implements ToDoPreferences.OnToDoPreferenceChangeListener {

    /** Random number generator for some tests */
    final Random RAND = new Random();
    /** Random string generator for use in the tests */
    final RandomStringUtils STRING_GEN = RandomStringUtils.insecure();

    static MockSharedPreferences mockPrefs;
    static ToDoPreferences toDoPrefs;

    boolean listenerWasCalled;

    @BeforeClass
    public static void initializeMock() {
        mockPrefs = new MockSharedPreferences();
        ToDoPreferences.setSharedPreferences(mockPrefs);
        // ToDoPreferences doesn't actually use the Context if we
        // set a custom SharedPreferences first, so it can be null.
        toDoPrefs = ToDoPreferences.getInstance(null);
    }

    @Before
    public void resetMock() {
        mockPrefs.resetMock();
        toDoPrefs.unregisterOnToDoPreferenceChangeListener(this);
        listenerWasCalled = false;
    }

    /** Test getting the current sort order */
    @Test
    public void testGetSortOrder() {
        int expectedId = RAND.nextInt(1000);
        mockPrefs.initializePreference(TPREF_SORT_ORDER, expectedId);
        assertEquals("Sort order", expectedId, toDoPrefs.getSortOrder());
    }

    /** Test getting the default sort order */
    @Test
    public void testGetDefaultSortOrder() {
        assertEquals("Default sort order", 0, toDoPrefs.getSortOrder());
    }

    /** Test changing the sort order */
    @Test
    public void testSetSortOrder() {
        int expectedId = RAND.nextInt(1000);
        toDoPrefs.setSortOrder(expectedId);
        assertFalse("setSortOrder did not close the editor!",
                mockPrefs.isEditorOpen());
        assertEquals("Sort order", expectedId,
                mockPrefs.getPreference(TPREF_SORT_ORDER));
    }

    /**
     * Run a test for getting a boolean value.
     *
     * @param displayName the name of the preference for display
     * @param prefName the internal name of the boolean preference
     * @param getDefault whether to get the default value rather than
     *                   a stored value
     * @param expectedValue whether to test for a true or false setting
     * @param getter a lamba function which makes the NotePreferences call
     * to get the boolean value
     */
    private void runGetBooleanPreferenceTest(
            String displayName, String prefName,
            boolean getDefault, boolean expectedValue,
            Supplier<Boolean> getter) {
        if (!getDefault)
            mockPrefs.initializePreference(prefName, expectedValue);
        assertEquals(displayName, expectedValue, getter.get());
    }

    /**
     * Run a test for setting a boolean value.
     *
     * @param displayName the name of the preference for display
     * @param prefName the internal name of the boolean preference
     * @param methodName the name of the setter method being called
     * @param expectedValue whether to test for a true or false setting
     * @param setter a lambda function which makes the NotePreferences call
     * to set the boolean value
     */
    private void runSetBooleanPreferenceTest(
            String displayName, String prefName, String methodName,
            boolean expectedValue, Consumer<Boolean> setter) {
        setter.accept(expectedValue);
        assertFalse(methodName + " did not close the editor!",
                mockPrefs.isEditorOpen());
        assertEquals(displayName, expectedValue,
                mockPrefs.getPreference(prefName));
    }

    @Test
    public void testShowCheckedTrue() {
        runGetBooleanPreferenceTest("Show Completed Tasks",
                TPREF_SHOW_CHECKED, false, true,
                () -> toDoPrefs.showChecked());
    }

    @Test
    public void testShowCheckedFalse() {
        runGetBooleanPreferenceTest("Show Completed Tasks",
                TPREF_SHOW_CHECKED, false, false,
                () -> toDoPrefs.showChecked());
    }

    @Test
    public void testShowCheckedDefault() {
        runGetBooleanPreferenceTest("Show Completed Tasks",
                TPREF_SHOW_CHECKED, true, false,
                () -> toDoPrefs.showChecked());
    }

    @Test
    public void testSetShowCheckedTrue() {
        runSetBooleanPreferenceTest("Show Completed Tasks",
                TPREF_SHOW_CHECKED, "setShowChecked",
                true, (b) -> toDoPrefs.setShowChecked(b));
    }

    @Test
    public void testSetShowCheckedFalse() {
        runSetBooleanPreferenceTest("Show Completed Tasks",
                TPREF_SHOW_CHECKED, "setShowChecked",
                false, (b) -> toDoPrefs.setShowChecked(b));
    }

    @Test
    public void testShowDueDateTrue() {
        runGetBooleanPreferenceTest("Show Due Dates",
                TPREF_SHOW_DUE_DATE, false, true,
                () -> toDoPrefs.showDueDate());
    }

    @Test
    public void testShowDueDateFalse() {
        runGetBooleanPreferenceTest("Show Due Dates",
                TPREF_SHOW_DUE_DATE, false, false,
                () -> toDoPrefs.showDueDate());
    }

    @Test
    public void testShowDueDateDefault() {
        runGetBooleanPreferenceTest("Show Due Dates",
                TPREF_SHOW_DUE_DATE, true, false,
                () -> toDoPrefs.showDueDate());
    }

    @Test
    public void testSetShowDueDateTrue() {
        runSetBooleanPreferenceTest("Show Due Dates",
                TPREF_SHOW_DUE_DATE, "setShowDueDate",
                true, (b) -> toDoPrefs.setShowDueDate(b));
    }

    @Test
    public void testSetShowDueDateFalse() {
        runSetBooleanPreferenceTest("Show Due Dates",
                TPREF_SHOW_DUE_DATE, "setShowDueDate",
                false, (b) -> toDoPrefs.setShowDueDate(b));
    }

    @Test
    public void testShowPriorityTrue() {
        runGetBooleanPreferenceTest("Show Priorities",
                TPREF_SHOW_PRIORITY, false, true,
                () -> toDoPrefs.showPriority());
    }

    @Test
    public void testShowPriorityFalse() {
        runGetBooleanPreferenceTest("Show Priorities",
                TPREF_SHOW_PRIORITY, false, false,
                () -> toDoPrefs.showPriority());
    }

    @Test
    public void testShowPriorityDefault() {
        runGetBooleanPreferenceTest("Show Priorities",
                TPREF_SHOW_PRIORITY, true, false,
                () -> toDoPrefs.showPriority());
    }

    @Test
    public void testSetShowPriorityTrue() {
        runSetBooleanPreferenceTest("Show Priorities",
                TPREF_SHOW_PRIORITY, "setShowPriority",
                true, (b) -> toDoPrefs.setShowPriority(b));
    }

    @Test
    public void testSetShowPriorityFalse() {
        runSetBooleanPreferenceTest("Show Priorities",
                TPREF_SHOW_PRIORITY, "setShowPriority",
                false, (b) -> toDoPrefs.setShowPriority(b));
    }

    @Test
    public void testShowPrivateTrue() {
        runGetBooleanPreferenceTest("Show Private",
                TPREF_SHOW_PRIVATE, false, true,
                () -> toDoPrefs.showPrivate());
    }

    @Test
    public void testShowPrivateFalse() {
        runGetBooleanPreferenceTest("Show Private",
                TPREF_SHOW_PRIVATE, false, false,
                () -> toDoPrefs.showPrivate());
    }

    @Test
    public void testShowPrivateDefault() {
        runGetBooleanPreferenceTest("Show Private",
                TPREF_SHOW_PRIVATE, true, false,
                () -> toDoPrefs.showPrivate());
    }

    @Test
    public void testSetShowPrivateTrue() {
        runSetBooleanPreferenceTest("Show Private",
                TPREF_SHOW_PRIVATE, "setShowPrivate",
                true, (b) -> toDoPrefs.setShowPrivate(b));
    }

    @Test
    public void testSetShowPrivateFalse() {
        runSetBooleanPreferenceTest("Show Private",
                TPREF_SHOW_PRIVATE, "setShowPrivate",
                false, (b) -> toDoPrefs.setShowPrivate(b));
    }

    @Test
    public void testShowEncryptedTrue() {
        runGetBooleanPreferenceTest("Show Encrypted",
                TPREF_SHOW_ENCRYPTED, false, true,
                () -> toDoPrefs.showEncrypted());
    }

    @Test
    public void testShowEncryptedFalse() {
        runGetBooleanPreferenceTest("Show Encrypted",
                TPREF_SHOW_ENCRYPTED, false, false,
                () -> toDoPrefs.showEncrypted());
    }

    @Test
    public void testShowEncryptedDefault() {
        runGetBooleanPreferenceTest("Show Encrypted",
                TPREF_SHOW_ENCRYPTED, true, false,
                () -> toDoPrefs.showEncrypted());
    }

    @Test
    public void testSetShowEncryptedTrue() {
        runSetBooleanPreferenceTest("Show Encrypted",
                TPREF_SHOW_ENCRYPTED, "setShowEncrypted",
                true, (b) -> toDoPrefs.setShowEncrypted(b));
    }

    @Test
    public void testSetShowEncryptedFalse() {
        runSetBooleanPreferenceTest("Show Encrypted",
                TPREF_SHOW_ENCRYPTED, "setShowEncrypted",
                false, (b) -> toDoPrefs.setShowEncrypted(b));
    }

    @Test
    public void testShowCategoryTrue() {
        runGetBooleanPreferenceTest("Show Category",
                TPREF_SHOW_CATEGORY, false, true,
                () -> toDoPrefs.showCategory());
    }

    @Test
    public void testShowCategoryFalse() {
        runGetBooleanPreferenceTest("Show Category",
                TPREF_SHOW_CATEGORY, false, false,
                () -> toDoPrefs.showCategory());
    }

    @Test
    public void testShowCategoryDefault() {
        runGetBooleanPreferenceTest("Show Category",
                TPREF_SHOW_CATEGORY, true, false,
                () -> toDoPrefs.showCategory());
    }

    @Test
    public void testSetShowCategoryTrue() {
        runSetBooleanPreferenceTest("Show Category",
                TPREF_SHOW_CATEGORY, "setShowCategory",
                true, (b) -> toDoPrefs.setShowCategory(b));
    }

    @Test
    public void testSetShowCategoryFalse() {
        runSetBooleanPreferenceTest("Show Category",
                TPREF_SHOW_CATEGORY, "setShowCategory",
                false, (b) -> toDoPrefs.setShowCategory(b));
    }

    @Test
    public void testNotificationVibrateTrue() {
        runGetBooleanPreferenceTest("Alarm Vibrate",
                TPREF_NOTIFICATION_VIBRATE, false, true,
                () -> toDoPrefs.notificationVibrate());
    }

    @Test
    public void testNotificationVibrateFalse() {
        runGetBooleanPreferenceTest("Alarm Vibrate",
                TPREF_NOTIFICATION_VIBRATE, false, false,
                () -> toDoPrefs.notificationVibrate());
    }

    @Test
    public void testNotificationVibrateDefault() {
        runGetBooleanPreferenceTest("Alarm Vibrate",
                TPREF_NOTIFICATION_VIBRATE, true, false,
                () -> toDoPrefs.notificationVibrate());
    }

    @Test
    public void testSetNotificationVibrateTrue() {
        runSetBooleanPreferenceTest("Alarm Vibrate",
                TPREF_NOTIFICATION_VIBRATE, "setNotificationVibrate",
                true, (b) -> toDoPrefs.setNotificationVibrate(b));
    }

    @Test
    public void testSetNotificationVibrateFalse() {
        runSetBooleanPreferenceTest("Alarm Vibrate",
                TPREF_NOTIFICATION_VIBRATE, "setNotificationVibrate",
                false, (b) -> toDoPrefs.setNotificationVibrate(b));
    }

    /** Test getting the notification sound ID */
    @Test
    public void testGetNotificationSound() {
        long expectedId = RAND.nextInt(1000);
        mockPrefs.initializePreference(TPREF_NOTIFICATION_SOUND, expectedId);
        assertEquals("Sound ID", expectedId, toDoPrefs.getNotificationSound());
    }

    /** Test getting the default notification sound ID */
    @Test
    public void testGetDefaultNotificationSound() {
        assertEquals("Sound ID", -1, toDoPrefs.getNotificationSound());
    }

    /** Test changing the notification sound ID */
    @Test
    public void testSetNotificationSound() {
        long expectedId = RAND.nextInt(1000);
        toDoPrefs.setNotificationSound(expectedId);
        assertFalse("setNotificationSound did not close the editor!",
                mockPrefs.isEditorOpen());
        assertEquals("Notification Sound ID", expectedId,
                mockPrefs.getPreference(TPREF_NOTIFICATION_SOUND));
    }

    /** Test getting the selected category */
    @Test
    public void testGetSelectedCategory() {
        long expectedId = RAND.nextInt(1000);
        mockPrefs.initializePreference(TPREF_SELECTED_CATEGORY, expectedId);
        assertEquals("Selected category", expectedId,
                toDoPrefs.getSelectedCategory());
    }

    /** Test getting the default category */
    @Test
    public void testGetDefaultCategory() {
        assertEquals("Default selected category", ALL_CATEGORIES,
                toDoPrefs.getSelectedCategory());
    }

    /** Test setting the selected category */
    @Test
    public void testSetSelectedCategory() {
        long expectedId = RAND.nextInt(1001) - 1;
        toDoPrefs.setSelectedCategory(expectedId);
        assertFalse("setSelectedCategory did not close the editor!",
                mockPrefs.isEditorOpen());
        assertEquals("Selected category", expectedId,
                mockPrefs.getPreference(TPREF_SELECTED_CATEGORY));
    }

    /** Test getting the export file name */
    @Test
    public void testGetExportFile() {
        String defaultFile = STRING_GEN.nextAscii(10, 33);
        String expectedFile = STRING_GEN.nextAscii(10, 33);
        mockPrefs.initializePreference(TPREF_EXPORT_FILE, expectedFile);
        assertEquals("Export file", expectedFile,
                toDoPrefs.getExportFile(defaultFile));
    }

    /** Test getting the default export file name */
    @Test
    public void testGetDefaultExportFile() {
        String defaultFile = STRING_GEN.nextAscii(10, 33);
        assertEquals("Default export file", defaultFile,
                toDoPrefs.getExportFile(defaultFile));
    }

    /** Test setting the export file name */
    @Test
    public void testSetExportFile() {
        String expectedFile = STRING_GEN.nextAscii(10, 33);
        toDoPrefs.setExportFile(expectedFile);
        assertFalse("setExportFile did not close the editor!",
                mockPrefs.isEditorOpen());
        assertEquals("Export file", expectedFile,
                mockPrefs.getPreference(TPREF_EXPORT_FILE));
    }

    @Test
    public void testExportPrivateTrue() {
        runGetBooleanPreferenceTest("Export Private",
                TPREF_EXPORT_PRIVATE, false, true,
                () -> toDoPrefs.exportPrivate());
    }

    @Test
    public void testExportPrivateFalse() {
        runGetBooleanPreferenceTest("Export Private",
                TPREF_EXPORT_PRIVATE, false, false,
                () -> toDoPrefs.exportPrivate());
    }

    @Test
    public void testExportPrivateDefault() {
        runGetBooleanPreferenceTest("Export Private",
                TPREF_EXPORT_PRIVATE, true, false,
                () -> toDoPrefs.exportPrivate());
    }

    @Test
    public void testSetExportPrivateTrue() {
        runSetBooleanPreferenceTest("Export Private",
                TPREF_EXPORT_PRIVATE, "setExportPrivate",
                true, (b) -> toDoPrefs.setExportPrivate(b));
    }

    @Test
    public void testSetExportPrivateFalse() {
        runSetBooleanPreferenceTest("Export Private",
                TPREF_EXPORT_PRIVATE, "setExportPrivate",
                false, (b) -> toDoPrefs.setExportPrivate(b));
    }

    /** Test getting the import file name */
    @Test
    public void testGetImportFile() {
        String defaultFile = STRING_GEN.nextAscii(10, 33);
        String expectedFile = STRING_GEN.nextAscii(10, 33);
        mockPrefs.initializePreference(TPREF_IMPORT_FILE, expectedFile);
        assertEquals("Import file", expectedFile,
                toDoPrefs.getImportFile(defaultFile));
    }

    /** Test getting the default export file name */
    @Test
    public void testGetDefaultImportFile() {
        String defaultFile = STRING_GEN.nextAscii(10, 33);
        assertEquals("Default import file", defaultFile,
                toDoPrefs.getImportFile(defaultFile));
    }

    /** Test setting the export file name */
    @Test
    public void testSetImportFile() {
        String expectedFile = STRING_GEN.nextAscii(10, 33);
        toDoPrefs.setImportFile(expectedFile);
        assertFalse("setImportFile did not close the editor!",
                mockPrefs.isEditorOpen());
        assertEquals("Import file", expectedFile,
                mockPrefs.getPreference(TPREF_IMPORT_FILE));
    }

    /** Test getting the import type */
    @Test
    public void testGetImportType() {
        ImportType expectedType = ImportType.values()[
                RAND.nextInt(ImportType.values().length)];
        mockPrefs.initializePreference(TPREF_IMPORT_TYPE, expectedType.ordinal());
        assertEquals("Import type", expectedType, toDoPrefs.getImportType());
    }

    /** Test getting the default import type */
    @Test
    public void testGetDefaultImportType() {
        assertEquals("Default import type", ImportType.UPDATE,
                toDoPrefs.getImportType());
    }

    /** Test setting the import type */
    @Test
    public void testSetImportType() {
        ImportType expectedType = ImportType.values()[
                RAND.nextInt(ImportType.values().length)];
        toDoPrefs.setImportType(expectedType);
        assertFalse("setImportType did not close the editor!",
                mockPrefs.isEditorOpen());
        assertEquals("Import type (stored value)", expectedType.ordinal(),
                mockPrefs.getPreference(TPREF_IMPORT_TYPE));
    }

    @Test
    public void testImportPrivateTrue() {
        runGetBooleanPreferenceTest("Import Private",
                TPREF_IMPORT_PRIVATE, false, true,
                () -> toDoPrefs.importPrivate());
    }

    @Test
    public void testImportPrivateFalse() {
        runGetBooleanPreferenceTest("Import Private",
                TPREF_IMPORT_PRIVATE, false, false,
                () -> toDoPrefs.importPrivate());
    }

    @Test
    public void testImportPrivateDefault() {
        runGetBooleanPreferenceTest("Import Private",
                TPREF_IMPORT_PRIVATE, true, false,
                () -> toDoPrefs.importPrivate());
    }

    @Test
    public void testSetImportPrivateTrue() {
        runSetBooleanPreferenceTest("Import Private",
                TPREF_IMPORT_PRIVATE, "setImportPrivate",
                true, (b) -> toDoPrefs.setImportPrivate(b));
    }

    @Test
    public void testSetImportPrivateFalse() {
        runSetBooleanPreferenceTest("Import Private",
                TPREF_IMPORT_PRIVATE, "setImportPrivate",
                false, (b) -> toDoPrefs.setImportPrivate(b));
    }

    /**
     * Called when a preference has been changed
     * (if we register this test class as a listener)
     */
    @Override
    public void onToDoPreferenceChanged(ToDoPreferences prefs) {
        listenerWasCalled = true;
    }

    /** The set of preference keys managed by ToDoPreferences */
    private static final String[] TPREF_KEYS = {
            TPREF_EXPORT_FILE, TPREF_EXPORT_PRIVATE,
            TPREF_IMPORT_FILE, TPREF_IMPORT_PRIVATE, TPREF_IMPORT_TYPE,
            TPREF_NOTIFICATION_SOUND, TPREF_NOTIFICATION_VIBRATE,
            TPREF_SELECTED_CATEGORY, TPREF_SHOW_CATEGORY,
            TPREF_SHOW_CHECKED, TPREF_SHOW_DUE_DATE,
            TPREF_SHOW_ENCRYPTED, TPREF_SHOW_PRIORITY,
            TPREF_SHOW_PRIVATE, TPREF_SORT_ORDER
    };

    /**
     * Test that when a preference is set, a listener
     * for that preference is called.
     *
     * @param prefKey the key for the preference we@rsquo;re listening for
     * @param methodName the name of the method that should result in
     *                   listener notification
     * @param setter a function for setting the preference.
     *               (Caller supplies the value.)
     */
    private void runListenerCalledTest(
            String prefKey, String methodName, Runnable setter) {
        toDoPrefs.registerOnToDoPreferenceChangeListener(this, prefKey);
        setter.run();
        assertTrue("Listener was not called for " + methodName,
                listenerWasCalled);
    }

    /**
     * Test that when a preference is set, listeners for all other
     * preferences are <i>not</i> called.
     *
     * @param prefKey the key for the preference we@rsquo;re <i>not</i>
     *                listening for
     * @param methodName the name of the method that could result in
     *                   listener notification
     * @param setter a function for setting the preference.
     *               (Caller supplies the value.)
     */
    private void runListenerNotCalledTest(
            String prefKey, String methodName, Runnable setter) {
        List<String> otherPrefs = new ArrayList<>(TPREF_KEYS.length - 1);
        for (String key : TPREF_KEYS) {
            if (!key.equals(prefKey))
                otherPrefs.add(key);
        }
        toDoPrefs.registerOnToDoPreferenceChangeListener(this,
                otherPrefs.toArray(new String[otherPrefs.size()]));
        setter.run();
        assertFalse("Unassociated listener was called for " + methodName,
                listenerWasCalled);
    }

    @Test
    public void testSortOrderListener() {
        runListenerCalledTest(TPREF_SORT_ORDER, "setSortOrder",
                () -> toDoPrefs.setSortOrder(RAND.nextInt(100)));
    }

    @Test
    public void testSortOrderIgnored() {
        runListenerNotCalledTest(TPREF_SORT_ORDER, "setSortOrder",
                () -> toDoPrefs.setSortOrder(RAND.nextInt(100)));
    }

    @Test
    public void testShowCheckedListener() {
        runListenerCalledTest(TPREF_SHOW_CHECKED, "setShowChecked",
                () -> toDoPrefs.setShowChecked(RAND.nextBoolean()));
    }

    @Test
    public void testShowCheckedIgnored() {
        runListenerNotCalledTest(TPREF_SHOW_CHECKED, "setShowChecked",
                () -> toDoPrefs.setShowChecked(RAND.nextBoolean()));
    }

    @Test
    public void testShowDueDateListener() {
        runListenerCalledTest(TPREF_SHOW_DUE_DATE, "setShowDueDate",
                () -> toDoPrefs.setShowDueDate(RAND.nextBoolean()));
    }

    @Test
    public void testShowDueDateIgnored() {
        runListenerNotCalledTest(TPREF_SHOW_DUE_DATE, "setShowDueDate",
                () -> toDoPrefs.setShowDueDate(RAND.nextBoolean()));
    }

    @Test
    public void testShowPriorityListener() {
        runListenerCalledTest(TPREF_SHOW_PRIORITY, "setShowPriority",
                () -> toDoPrefs.setShowPriority(RAND.nextBoolean()));
    }

    @Test
    public void testShowPriorityIgnored() {
        runListenerNotCalledTest(TPREF_SHOW_PRIORITY, "setShowPriority",
                () -> toDoPrefs.setShowPriority(RAND.nextBoolean()));
    }

    @Test
    public void testShowPrivateListener() {
        runListenerCalledTest(TPREF_SHOW_PRIVATE, "setShowPrivate",
                () -> toDoPrefs.setShowPrivate(RAND.nextBoolean()));
    }

    @Test
    public void testShowPrivateIgnored() {
        runListenerNotCalledTest(TPREF_SHOW_PRIVATE, "setShowPrivate",
                () -> toDoPrefs.setShowPrivate(RAND.nextBoolean()));
    }

    @Test
    public void testShowEncryptedListener() {
        runListenerCalledTest(TPREF_SHOW_ENCRYPTED, "setShowEncrypted",
                () -> toDoPrefs.setShowEncrypted(RAND.nextBoolean()));
    }

    @Test
    public void testShowEncryptedIgnored() {
        runListenerNotCalledTest(TPREF_SHOW_ENCRYPTED, "setShowEncrypted",
                () -> toDoPrefs.setShowEncrypted(RAND.nextBoolean()));
    }

    @Test
    public void testShowCategoryListener() {
        runListenerCalledTest(TPREF_SHOW_CATEGORY, "setShowCategory",
                () -> toDoPrefs.setShowCategory(RAND.nextBoolean()));
    }

    @Test
    public void testShowCategoryIgnored() {
        runListenerNotCalledTest(TPREF_SHOW_CATEGORY, "setShowCategory",
                () -> toDoPrefs.setShowCategory(RAND.nextBoolean()));
    }

    @Test
    public void testNotificationVibrateListener() {
        runListenerCalledTest(TPREF_NOTIFICATION_VIBRATE, "setNotificationVibrate",
                () -> toDoPrefs.setNotificationVibrate(RAND.nextBoolean()));
    }

    @Test
    public void testNotificationVibrateIgnored() {
        runListenerNotCalledTest(TPREF_NOTIFICATION_VIBRATE, "setNotificationVibrate",
                () -> toDoPrefs.setNotificationVibrate(RAND.nextBoolean()));
    }

    @Test
    public void testNotificationSoundListener() {
        runListenerCalledTest(TPREF_NOTIFICATION_SOUND, "setNotificationSound",
                () -> toDoPrefs.setNotificationSound(RAND.nextInt(100)));
    }

    @Test
    public void testNotificationSoundIgnored() {
        runListenerNotCalledTest(TPREF_NOTIFICATION_SOUND, "setNotificationSound",
                () -> toDoPrefs.setNotificationSound(RAND.nextInt(100)));
    }

    @Test
    public void testSelectedCategoryListener() {
        runListenerCalledTest(TPREF_SELECTED_CATEGORY, "setSelectedCategory",
                () -> toDoPrefs.setSelectedCategory(RAND.nextInt(100)));
    }

    @Test
    public void testSelectedCategoryIgnored() {
        runListenerNotCalledTest(TPREF_SELECTED_CATEGORY, "setSelectedCategory",
                () -> toDoPrefs.setSelectedCategory(RAND.nextInt(100)));
    }

    @Test
    public void testExportFileListener() {
        runListenerCalledTest(TPREF_EXPORT_FILE, "setExportFile",
                () -> toDoPrefs.setExportFile(STRING_GEN.nextAscii(10, 33)));
    }

    @Test
    public void testExportFileIgnored() {
        runListenerNotCalledTest(TPREF_EXPORT_FILE, "setExportFile",
                () -> toDoPrefs.setExportFile(STRING_GEN.nextAscii(10, 33)));
    }

    @Test
    public void testExportPrivateListener() {
        runListenerCalledTest(TPREF_EXPORT_PRIVATE, "setExportPrivate",
                () -> toDoPrefs.setExportPrivate(RAND.nextBoolean()));
    }

    @Test
    public void testExportPrivateIgnored() {
        runListenerNotCalledTest(TPREF_EXPORT_PRIVATE, "setImportPrivate",
                () -> toDoPrefs.setExportPrivate(RAND.nextBoolean()));
    }

    @Test
    public void testImportFileListener() {
        runListenerCalledTest(TPREF_IMPORT_FILE, "setImportFile",
                () -> toDoPrefs.setImportFile(STRING_GEN.nextAscii(10, 33)));
    }

    @Test
    public void testImportFileIgnored() {
        runListenerNotCalledTest(TPREF_IMPORT_FILE, "setImportFile",
                () -> toDoPrefs.setImportFile(STRING_GEN.nextAscii(10, 33)));
    }

    @Test
    public void testImportTypeListener() {
        runListenerCalledTest(TPREF_IMPORT_TYPE, "setImportType",
                () -> toDoPrefs.setImportType(ImportType.values()[
                        RAND.nextInt(ImportType.values().length)]));
    }

    @Test
    public void testImportTypeIgnored() {
        runListenerNotCalledTest(TPREF_IMPORT_TYPE, "setImportType",
                () -> toDoPrefs.setImportType(ImportType.values()[
                        RAND.nextInt(ImportType.values().length)]));
    }

    @Test
    public void testImportPrivateListener() {
        runListenerCalledTest(TPREF_IMPORT_PRIVATE, "setImportPrivate",
                () -> toDoPrefs.setImportPrivate(RAND.nextBoolean()));
    }

    @Test
    public void testImportPrivateIgnored() {
        runListenerNotCalledTest(TPREF_IMPORT_PRIVATE, "setImportPrivate",
                () -> toDoPrefs.setImportPrivate(RAND.nextBoolean()));
    }

}
