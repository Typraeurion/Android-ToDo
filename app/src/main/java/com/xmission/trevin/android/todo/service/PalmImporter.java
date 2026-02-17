/*
 * Copyright © 2011–2026 Trevin Beattie
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

import static com.xmission.trevin.android.todo.data.palm.ToDoEntry.*;

import android.util.Log;

import com.xmission.trevin.android.todo.data.ToDoAlarm;
import com.xmission.trevin.android.todo.data.ToDoCategory;
import com.xmission.trevin.android.todo.data.ToDoItem;
import com.xmission.trevin.android.todo.data.ToDoPreferences;
import com.xmission.trevin.android.todo.data.palm.*;
import com.xmission.trevin.android.todo.data.repeat.*;
import com.xmission.trevin.android.todo.provider.ToDoCursor;
import com.xmission.trevin.android.todo.provider.ToDoRepository;
import com.xmission.trevin.android.todo.provider.ToDoRepositoryImpl;
import com.xmission.trevin.android.todo.provider.ToDoSchema;
import com.xmission.trevin.android.todo.util.EncryptionException;
import com.xmission.trevin.android.todo.util.PasswordMismatchException;
import com.xmission.trevin.android.todo.util.StringEncryption;

import java.io.*;
import java.time.*;
import java.util.*;

/**
 * This class imports To Do data from a Palm database (todo.dat).
 *
 * @author Trevin Beattie
 */
public class PalmImporter implements Runnable {

    /** Tag for the debug logger */
    public static final String TAG = "PalmImporter";

    /** todo.dat file header */
    public static final int TD10_MAGIC = 0x54440100;
    public static final int TD20_MAGIC = 0x54440200;
    /** todo.dat pre-file header (huh?) */
    public static final int MAGIC = 0xcafebabe;

    /**
     * Flag indicating how to merge items from the Palm database
     * with those in the Android database.
     */
    public enum ImportType {
        /**
         * The Android database should be cleared
         * before importing the Palm database.
         */
        CLEAN(1),

        /**
         * Any items in the Android database with the same internal ID
         * as an item in the Palm database should be overwritten.
         */
        OVERWRITE(2),

        /**
         * Any items in the Android database with the same internal ID
         * as an item in the Palm database should be overwritten only if
         * the two have the same category and description; otherwise,
         * change the ID of the Android item to an unused value and
         * add the Palm item as a new item.
         */
        MERGE(4),

        /**
         * Any items in the Palm database with the same internal ID as an
         * item in the Android database should be added as a new item
         * with a newly assigned ID.  Will result in duplicates if the
         * database had been imported before, but is the safest option
         * if importing a different database.
         */
        ADD(5),

        /**
         * Don't actually write anything to the android database.
         * Just read the Palm database to verify the integrity of the data.
         */
        TEST(0);

        private final int intValue;

        ImportType(int value) {
            intValue = value;
        }

        /**
         * @return the value of this enum that can be passed
         * as input data to the {@link PalmImportWorker}
         */
        public int getIntValue() {
            return intValue;
        }

        /**
         * Find the ImportType corresponding to an integer value that
         * was passed in {@link PalmImportWorker}&rsquo;s input data
         *
         * @param value the numerical value of the ImportType
         *
         * @return the matching {@link ImportType}
         *
         * @throws IllegalArgumentException if {@code value} does not
         * match any {@link ImportType} value.
         */
        public static ImportType fromInt(int value) {
            for (ImportType type : values()) {
                if (type.intValue == value)
                    return type;
            }
            throw new IllegalArgumentException(
                    "Unknown ImportType value: " + value);
        }

    }

    /** Modes of operation, for progress bar updates */
    public enum OpMode {
        START, CATEGORIES, ITEMS, FINISH
    }
    /**
     * Text to pass to the {@link ProgressBarUpdater} for each
     * mode of operation.  This may be overridden when the class
     * is initialized.
     */
    private static final Map<OpMode,String> modeText = new HashMap<>();
    static {
        modeText.put(OpMode.START, "Starting\u2026");
        modeText.put(OpMode.CATEGORIES, "Importing categories\u2026");
        modeText.put(OpMode.ITEMS, "Importing To-Do items\u2026");
        modeText.put(OpMode.FINISH, "Finishing\u2026");
    }

    /** Repository passed to the {@link #importData} method */
    private final ToDoRepository repository;

    /** The name of the data file being read, if known */
    private final String datFileName;

    /** Input stream passed to the {@link #importData} method */
    private final InputStream inStream;

    /** The type of import this worker will be doing */
    private final ImportType importType;

    /** Whether to import private records */
    private final boolean importPrivate;

    /**
     * The encryption object used to encrypt private records for the database
     */
    private final StringEncryption encryptor;

    /** Progress updater passed to the {@link #importData} method */
    private final ProgressBarUpdater progressUpdater;

    /** Data file version */
    private int tdVersion;

    /** Number of fields per ToDoSchema entry */
    private int dataFieldsPerEntry;

    /** Next free record ID (counting both the Palm and Android databases) */
    private long nextFreeRecordID;

    /** Table of categories from the data file */
    private CategoryEntry[] dataCategories = null;
    /** Lookup map from the Palm category ID to the category entry */
    HashMap<Integer,CategoryEntry> categoryMap;

    /** Field types in the schema */
    private int[] dataFieldTypes = null;

    /** Total number of To Do records in the data file */
    private int numToDoEntries = 0;

    /** The total number of entries to be imported */
    private int totalCount = -1;

    /** The current number of entries imported */
    private int importCount = 0;

    /**
     * Change the text associated with a mode of operation.
     *
     * @param mode the mode whose text to change
     * @param text the new text to use
     */
    static void setModeText(OpMode mode, String text) {
        modeText.put(mode, text);
    }

    /**
     * Create a new importer instance with the provided parameters.
     * This will be passed to the repository to run in a single
     * transaction.
     *
     * @param repository The repository to which we should write records.
     * @param datFileName the name of the data file being read, if known
     * (may be {@code null}).
     * @param inStream the stream from which we should read the Palm data.
     * @param importType how to merge items from the Palm DB file
     * with those in the database.
     * @param importPrivate whether to include private records in the import.
     * @param encryptor an encryption object used to encrypt private
     * records before writing to the database, or {@code null} to leave
     * private records unencrypted or if not importing private records.
     * @param progressUpdater a class to call back while we are processing
     * the data to mark our progress.
     */
    private PalmImporter(ToDoRepository repository,
                         String datFileName,
                         InputStream inStream,
                         ImportType importType,
                         boolean importPrivate,
                         StringEncryption encryptor,
                         ProgressBarUpdater progressUpdater) {
        this.repository = repository;
        this.datFileName = datFileName;
        this.inStream = inStream;
        this.importType = importType;
        this.importPrivate = importPrivate;
        this.encryptor = encryptor;
        this.progressUpdater = progressUpdater;
    }

    /**
     * Import the categories and To Do records from a Palm database
     * to our To Do database.
     * @param repository The repository to which we should write records.
     * It should have already been opened by the caller.
     * @param fileName the name of the XML file being read, if known
     * (may be {@code null}).
     * @param inStream the stream from which we should read the Palm data.
     * @param importType how to merge items from the Palm DB file
     * with those in the database.
     * @param importPrivate whether to include private records in the import.
     * @param currentPassword the password with which to encrypt any private
     * records imported, or {@code null} to leave them unencrypted or if
     * we are not importing any private records.
     * @param progressUpdater a class to call back while we are processing
     * the data to mark our progress.
     */
    public static void importData(ToDoRepository repository,
                                  String fileName,
                                  InputStream inStream,
                                  ImportType importType,
                                  boolean importPrivate,
                                  String currentPassword,
                                  ProgressBarUpdater progressUpdater)
            throws IOException {
        StringEncryption encryptor = null;
        if (currentPassword != null) {
            encryptor = new StringEncryption();
            encryptor.setPassword(currentPassword.toCharArray());
        }
        progressUpdater.updateProgress(modeText.get(OpMode.START),
                0, -1, true);
        try {
            PalmImporter importer = new PalmImporter(
                    repository, fileName, inStream, importType,
                    importPrivate, encryptor, progressUpdater);
            /*
             * For the first parts of the file we're just
             * reading and not touching the database yet,
             * so these can be done outside the transaction.
             */
            importer.readDataFileHeader();
            importer.readCategoryList();
            importer.readToDoEntriesHeader();
            repository.runInTransaction(importer);
            // Final update of the progress meter (unthrottled)
            progressUpdater.updateProgress(modeText.get(OpMode.FINISH),
                    importer.importCount, importer.totalCount, false);
        } catch (UncaughtIOException ui) {
            throw ui.getCause();
        } finally {
            inStream.close();
        }
    }

    @Override
    public void run() {
        try {
            if ((encryptor != null) && !encryptor.checkPassword(repository))
                throw new PasswordMismatchException(
                        "Current password is incorrect");

            mergeCategories();

            // Find the highest available record ID
            ToDoCursor cursor = repository.getItems(
                    ToDoPreferences.ALL_CATEGORIES, true, true,
                    ToDoRepositoryImpl.TODO_TABLE_NAME + "."
                            + ToDoSchema.ToDoItemColumns._ID + " desc");
            try {
                if (cursor.moveToFirst()) {
                    ToDoItem lastItem = cursor.getItem();
                    nextFreeRecordID = lastItem.getId() + 1;
                } else {
                    nextFreeRecordID = 1;
                }
            } finally {
                cursor.close();
            }

            if (importType == ImportType.CLEAN) {
                // Wipe them all out
                Log.d(TAG, "Removing all existing To Do items");
                repository.deleteAllItems();
            }

            // Stream import the To Do records one at a time
            for (int i = 0; i < numToDoEntries; i++) {
                ToDoEntry dataToDo = readToDoEntry(inStream);
//                Log.d(TAG, String.format(".readDataFile: Entry #%d: %s",
//                        i, dataToDo.toString()));
                if (importPrivate || !dataToDo.isPrivate)
                    mergeToDo(dataToDo);
                importCount++;
                progressUpdater.updateProgress(modeText.get(OpMode.ITEMS),
                        importCount, totalCount, true);
            }
            if (inStream.available() > 0) {
                Log.w(TAG, String.format(Locale.US,
                        ".readDataFile: excess data at end of stream (at least %d bytes)",
                        inStream.available()));
                throw new UncaughtIOException(new StreamCorruptedException(
                        "Excess data at end of stream"));
            }
        } catch (IOException iox) {
            throw new UncaughtIOException(iox);
        }
    }

    /**
     * Read the data file header.  We expect a magic value of
     * &ldquo;TD&rdquo; 1.0 or &ldquo;TD&rdquo; 2.0 to indicate the
     * data version; this may be preceded by a &ldquo;PalmSG
     * Database&rdquo; wrapper.
     *
     * @throws StreamCorruptedException if the file
     * is not a valid To Do data file.
     */
    public void readDataFileHeader() throws IOException {
        Log.d(TAG, String.format(Locale.US,
                ".readDataFileHeader: reading %s", datFileName));

        while (tdVersion == 0) {
            int magic = readInteger(inStream);
            switch (magic) {

                case MAGIC:
                    // This file has some additional headers
                    // that need reading first
                    String palmTag = readString(inStream);
                    Log.d(TAG, ".readDataFile: Data file identifier = "
                            + palmTag);
                    magic = readInteger(inStream);
                    Log.d(TAG, String.format(".readDataFile: revision %c%c%c%c",
                            magic & 0xff, (magic >> 8) & 0xff,
                            (magic >> 16) & 0xff, (magic >> 24) & 0xff));
                    break;

                case TD20_MAGIC:
                    // There are a bunch of unknown bytes here.  Mostly zero.
                    skipZeroes(inStream, 12);
                    readInteger(inStream);	// This is not zero.  It's 0x1165.
                    skipZeroes(inStream, 8);
                    String v2FileName = readString(inStream);
                    Log.d(TAG, ".readDataFile: Saved file name = \""
                            + v2FileName + "\"");
                    // There are an odd number of zero bytes following
                    skipZeroes(inStream, 43);
                    readInteger(inStream); // This looks like a category ID,
                                         // but we're not there yet, and it's
                                         // not greater than the highest ID.
                    tdVersion = 2;
                    return;

                case TD10_MAGIC:
                    String v1FileName = readString(inStream);
                    Log.d(TAG, ".readDataFile: Saved file name = \""
                            + v1FileName + "\"");
                    // Skip the "custom show header" (?)
                    String showHeader = readString(inStream);
                    Log.d(TAG, ".readDataFile: skipping show header \""
                            + showHeader + "\"");
                    readInteger(inStream);	// data: Next Free Category ID
                    tdVersion = 1;
                    return;

                default:
                    throw new StreamCorruptedException(String.format(
                        "Magic file header mismatch: expected %08X or %08X, got %08X",
                        TD10_MAGIC, TD20_MAGIC, magic));

            }
        }
    }

    /**
     * Read the list of categories from the data file.
     * These are all held in memory until we get to the To Do
     * metadata.
     *
     * @throws StreamCorruptedException if the count of categories
     * is greater than 255, which likely indicates a corrupted stream.
     */
    public void readCategoryList() throws IOException {
        // Now read in the category list
        int catCount = readInteger(inStream);
        Log.d(TAG, ".readCategoryList: " + catCount + " categories");
        if (catCount >= 256)
            throw new StreamCorruptedException(
                    "Suspect category count (" + catCount + ")");
        dataCategories = new CategoryEntry[catCount];
        int i;
        categoryMap = new HashMap<>();
        // There is an implicit category entry for Unfiled;
        // not included in the category count above.
        CategoryEntry unfiled = new CategoryEntry();
        unfiled.ID = (int) ToDoSchema.ToDoCategoryColumns.UNFILED;
        unfiled.longName = "Unfiled";
        unfiled.shortName = "Unfiled";
        unfiled.newID = ToDoSchema.ToDoCategoryColumns.UNFILED;
        categoryMap.put(unfiled.ID, unfiled);
        for (i = 0; i < catCount; i++) {
            dataCategories[i] = readCategoryEntry(inStream);
            if (categoryMap.get(dataCategories[i].index) != null)
                throw new StreamCorruptedException(
                        "Duplicate category index " + dataCategories[i].index);
            categoryMap.put(dataCategories[i].index, dataCategories[i]);
        }
    }

    /**
     * Read the To Do entries metadata.  This includes the position of
     * a few key fields, but the position of the rest of the fields
     * has to be inferred from the file version so that part doesn&rsquo;t
     * make much sense.  It includes the data types of all fields,
     * which makes even less sense since the fields themselves have to
     * be assumed.  Provided those match our expectations, the one
     * important piece of information here is the number of records
     * which allows us to finally determine the total record count
     * for the progress meter.
     *
     * @throws StreamCorruptedException if any of the metadata does not
     * match our expectations as per the file version, if the total number
     * of fields in the data set is not a whole multiple of the number
     * of fields per record, or if the total number of records is greater
     * than 100,000 which is unlikely.
     * @throws UnsupportedOperationException if any of the field types
     * are {@code FLOAT}, {@code ALPHA}, or {@code BITFLAG} which this
     * reader does not support (and doesn&rsquo;t match any of our
     * expected field types anyway.)
     */
    public void readToDoEntriesHeader() throws IOException {
        // Read in more metadata
        int dataResourceID = readInteger(inStream);
        Log.d(TAG, ".readDataFile: resource ID = " + dataResourceID);
        dataFieldsPerEntry = readInteger(inStream);
        Log.d(TAG, String.format(".readDataFile: %d fields per entry",
                dataFieldsPerEntry));

        /*
         * Position of the record ID in the ToDoSchema entry.
         * This is effectively an index into the data structure
         * as an array of 4-byte C {@code long}s, which includes
         * one element for the field type and one for the field
         * value.  Since the record ID should be the first field,
         * its position is 0.
         */
        int dataRecordIDPosition = readInteger(inStream);
        /* Position of the status field in the ToDoSchema entry */
        int dataRecordStatusPosition = readInteger(inStream);
        /* Position of the placement field in the ToDoSchema entry */
        int dataRecordPlacementPosition = readInteger(inStream);
        if ((dataRecordIDPosition >= dataFieldsPerEntry) ||
                (dataRecordStatusPosition >= dataFieldsPerEntry) ||
                (dataRecordPlacementPosition >= dataFieldsPerEntry))
            throw new StreamCorruptedException(String.format(Locale.US,
                    "Invalid field position: ID[%d], Status[%d],"
                            + " Placement[%d], total fields = %d",
                    dataRecordIDPosition, dataRecordStatusPosition,
                    dataRecordPlacementPosition, dataFieldsPerEntry));
        Log.d(TAG, String.format(Locale.US, "Field positions: ID[%d],"
                + " Status[%d], Placement[%d]",
                dataRecordIDPosition, dataRecordStatusPosition,
                dataRecordPlacementPosition));
        int fieldCount = readShort(inStream);
        if (fieldCount != dataFieldsPerEntry)
            throw new StreamCorruptedException(String.format(Locale.US,
                    "Mismatched field count: was %d, now %d",
                    dataFieldsPerEntry, fieldCount));
        if ((fieldCount < 9) || (fieldCount >= 41))
            throw new StreamCorruptedException(
                    "Suspect field count (" + fieldCount + ")");
        dataFieldTypes = new int[fieldCount];
        for (int i = 0; i < fieldCount; i++) {
            dataFieldTypes[i] = readShort(inStream);
            switch (dataFieldTypes[i]) {
                case TYPE_INTEGER:
                case TYPE_DATE:
                case TYPE_CSTRING:
                case TYPE_BOOLEAN:
                case TYPE_REPEAT:
                case TYPE_UNKNOWN40:
                case TYPE_UNKNOWN41:
                case TYPE_UNKNOWN42:
                case TYPE_UNKNOWN43:
                    break;
                case TYPE_FLOAT:
                    throw new UnsupportedOperationException(
                            "Unhandled field data type: FLOAT");
                case TYPE_ALPHA:
                    throw new UnsupportedOperationException(
                            "Unhandled field data type: ALPHA");
                case TYPE_BITFLAG:
                    throw new UnsupportedOperationException(
                            "Unhandled field data type: BITFLAG");
                default:
                    throw new StreamCorruptedException(
                            "Unknown field data type: " + dataFieldTypes[i]);
            }
            switch (tdVersion) {
                case 1:
                    if ((i < ToDoEntry.expectedFieldTypesV1.length) &&
                            (dataFieldTypes[i] != ToDoEntry.expectedFieldTypesV1[i]))
                        throw new StreamCorruptedException(String.format(Locale.US,
                                "Field %d type mismatch in header:"
                                        + " expected %d, found %d", i,
                                ToDoEntry.expectedFieldTypesV1[i], dataFieldTypes[i]));
                    break;
                case 2:
                    if ((i < ToDoEntry.expectedFieldTypesV2.length) &&
                            (dataFieldTypes[i] != ToDoEntry.expectedFieldTypesV2[i]))
                        throw new StreamCorruptedException(String.format(Locale.US,
                                "Field %d type mismatch in header:"
                                        + " expected %d, found %d", i,
                                ToDoEntry.expectedFieldTypesV2[i], dataFieldTypes[i]));
                    break;
            }
        }
        Log.d(TAG, ".readDataFile: field list = "
                + Arrays.toString(dataFieldTypes));

        // Finally, we get to the actual To Do items!
        numToDoEntries = readInteger(inStream);
        if (numToDoEntries % fieldCount != 0)
            throw new StreamCorruptedException(String.format(Locale.US,
                    "Number of fields in the database %d is not"
                            + " evenly divisible by the number of"
                            + " fields per entry %d",
                    numToDoEntries, fieldCount));
        numToDoEntries /= fieldCount;
        if ((numToDoEntries >= 100000) || (numToDoEntries < 0))
            throw new StreamCorruptedException(
                    "Suspect record count (" + numToDoEntries + ")");
        if (numToDoEntries <= 0)
            throw new StreamCorruptedException(String.format(Locale.US,
                    "No To Do records were found in %s",
                    datFileName));
        Log.d(TAG, String.format(Locale.US,
                ".readDataFile: %d total records", numToDoEntries));
        totalCount = dataCategories.length + numToDoEntries;
    }

    /**
     * Read a single category entry from the given file.
     * @return the entry.
     * @throws StreamCorruptedException if the category entry
     * is not in the expected format
     */
    CategoryEntry readCategoryEntry(InputStream stream) throws IOException {
        CategoryEntry entry = new CategoryEntry();
        entry.index = readInteger(stream);
        entry.ID = readInteger(stream);
        entry.dirty = readInteger(stream);
        entry.longName = readString(stream);
        entry.shortName = readString(stream);
        entry.newID = entry.ID;
        Log.d(TAG, ".readCategoryEntry: " + entry);
        return entry;
    }

    /**
     * Read a single To-Do entry from the given file.
     * @return the entry.
     * @throws StreamCorruptedException if the to do entry
     * is not in the expected format
     */
    ToDoEntry readToDoEntry(InputStream stream) throws IOException {
        ToDoEntry entry = new ToDoEntry();
        entry.unknownFields = new Object[dataFieldsPerEntry];
        for (int j = 0; j < dataFieldsPerEntry; j++) {
            int fieldType = readInteger(stream);
            if ((fieldType != dataFieldTypes[j]) &&
                    (dataFieldTypes[j] < TYPE_UNKNOWN40))
                throw new StreamCorruptedException(String.format(Locale.US,
                        "Field type #%d mismatch in record:"
                        + " expected %d, found %d",
                        j + 1, dataFieldTypes[j], fieldType));
            // We already checked the expected field types in the header,
            // so for the known fields just fill in the structure members.
            int field = ToDoEntry.FIELD_UNKNOWN;
            switch (tdVersion) {
                case 1:
                    if (j < ToDoEntry.fieldOrderV1.length)
                        field = ToDoEntry.fieldOrderV1[j];
                    break;
                case 2:
                    if (j < ToDoEntry.fieldOrderV2.length)
                        field = ToDoEntry.fieldOrderV2[j];
                    break;
            }
            switch (field) {
                case ToDoEntry.FIELD_ID:
                    entry.ID = readInteger(stream);
                    entry.unknownFields[j] = entry.ID;
                    // Keep track of the highest record ID;
                    // this is not stored in the Palm database metadata.
                    if (entry.ID >= nextFreeRecordID)
                        nextFreeRecordID = entry.ID + 1;
                    // Log.d(LOG_TAG, ".readToDoEntry: record ID = " + entry.ID);
                    break;
                case ToDoEntry.FIELD_STATUS:
                    entry.status = readInteger(stream);
                    entry.unknownFields[j] = entry.status;
                    break;
                case ToDoEntry.FIELD_OFFSET:
                    entry.offset = readInteger(stream);
                    entry.unknownFields[j] = entry.offset;
                    break;
                case ToDoEntry.FIELD_POSITION:
                    entry.position = readInteger(stream);
                    entry.unknownFields[j] = entry.position;
                    break;
                case ToDoEntry.FIELD_CATEGORY:
                    entry.categoryIndex = readInteger(stream);
                    if (categoryMap.get(entry.categoryIndex) == null)
                        throw new StreamCorruptedException(
                                String.format(Locale.US,
                                        "Record %d has an undefined"
                                                + " category index %d",
                                        entry.ID, entry.categoryIndex));
                    entry.unknownFields[j] = entry.categoryIndex;
                    break;
                case ToDoEntry.FIELD_PRIVATE:
                    entry.isPrivate = readInteger(stream) != 0;
                    entry.unknownFields[j] = entry.isPrivate;
                    break;
                case ToDoEntry.FIELD_DESCRIPTION:
                    skipZeroes(stream, 4);
                    entry.description = readString(stream);
                    entry.unknownFields[j] = entry.description;
                    // Log.d(LOG_TAG, ".readToDoEntry: \""
                    //	+ entry.description.replace("\\", "\\\\")
                    //	.replace("\r", "\\r").replace("\n", "\\n") + "\"");
                    break;
                case ToDoEntry.FIELD_DUE_DATE:
                    entry.dueDate = (long) readInteger(stream);
                    entry.unknownFields[j] = new Date(entry.dueDate * 1000);
                    break;
                case ToDoEntry.FIELD_COMPLETED:
                    entry.completed = readInteger(stream) != 0;
                    entry.unknownFields[j] = entry.completed;
                    break;
                case ToDoEntry.FIELD_PRIORITY:
                    entry.priority = readInteger(stream);
                    entry.unknownFields[j] = entry.priority;
                    break;
                case ToDoEntry.FIELD_NOTE:
                    skipZeroes(stream, 4);
                    entry.note = readString(stream);
                    entry.unknownFields[j] = entry.note;
                    break;
                case ToDoEntry.FIELD_REPEAT_AFTER_COMPLETE:
                    entry.repeatAfterCompleted = readInteger(stream) != 0;
                    entry.unknownFields[j] = entry.repeatAfterCompleted;
                    break;
                case ToDoEntry.FIELD_COMPLETION_DATE:
                    entry.completionDate = (long) readInteger(stream);
                    entry.unknownFields[j] = new Date(entry.completionDate * 1000);
                    break;
                case ToDoEntry.FIELD_HAS_ALARM:
                    entry.hasAlarm = readInteger(stream) != 0;
                    entry.unknownFields[j] = entry.hasAlarm;
                    break;
                case ToDoEntry.FIELD_ALARM_TIME:
                    entry.alarmTime = (long) readInteger(stream);
                    entry.unknownFields[j] = entry.alarmTime;
                    break;
                case ToDoEntry.FIELD_ALARM_DAYS_IN_ADVANCE:
                    entry.alarmDaysInAdvance = readInteger(stream);
                    entry.unknownFields[j] = entry.alarmDaysInAdvance;
                    break;
                case ToDoEntry.FIELD_REPEAT:
                    entry.repeat = readRepeatEvent(stream);
                    entry.unknownFields[j] = entry.repeat;
                    break;

                case ToDoEntry.FIELD_UNKNOWN:
                default:
                    // For all remaining fields, decode whatever the
                    // field type is and store it in the generic object array.
                    switch (fieldType) {
                        case TYPE_INTEGER:
                        case TYPE_UNKNOWN40:
                        case TYPE_UNKNOWN41:
                        case TYPE_UNKNOWN42:
                        case TYPE_UNKNOWN43:
                            entry.unknownFields[j] = readInteger(stream);
                            break;
                        case TYPE_REPEAT:
                            entry.unknownFields[j] = readRepeatEvent(stream);
                            break;
                        case TYPE_BOOLEAN:
                            entry.unknownFields[j] = (readInteger(stream) != 0)
                                    ? Boolean.TRUE : Boolean.FALSE;
                            break;
                        case TYPE_DATE:
                            entry.unknownFields[j] =
                                    new Date(readInteger(stream) * 1000L);
                            break;
                        case TYPE_CSTRING:
                            skipZeroes(stream, 4);
                            entry.unknownFields[j] = readString(stream);
                            break;
                    }
            }
        }
        return entry;
    }

    /**
     * Read a repeat event from the given file.
     * @return the repeat event
     * @throws StreamCorruptedException if the repeat entry
     * is not in the expected format
     */
    RepeatEvent readRepeatEvent(InputStream stream) throws IOException {
        RepeatEvent event = new RepeatEvent();
        skipZeroes(stream, 2);
        event.tag = readShort(stream);
        int dummy;
        if (event.tag == 0)	// No repetition
            return null;
        if (event.tag == -1) {
            dummy = readShort(stream);
            if (dummy != 1)
                throw new StreamCorruptedException("Error reading repeat"
                        + " event; expected 1 after tag, got " + dummy);
            event.typeName = readString(stream, readShort(stream));
            if (!event.typeName.equals(RepeatEvent.NAME_REPEAT_BY_DAY) &&
                    !event.typeName.equals(RepeatEvent.NAME_REPEAT_BY_WEEK) &&
                    !event.typeName.equals(RepeatEvent.NAME_REPEAT_BY_MONTH_DATE) &&
                    !event.typeName.equals(RepeatEvent.NAME_REPEAT_BY_MONTH_DAY) &&
                    !event.typeName.equals(RepeatEvent.NAME_REPEAT_BY_YEAR))
                throw new StreamCorruptedException("Unhandled repeat type"
                        + " name \"" + event.typeName + "\"");
        }
        event.type = readInteger(stream);
        event.interval = readInteger(stream);
        event.repeatUntil = readInteger(stream);
        skipZeroes(stream, 4);
        switch (event.type) {

            case RepeatEvent.TYPE_REPEAT_BY_DAY:
                event.dayOfWeek = readInteger(stream);
                if ((event.dayOfWeek < 0) || (event.dayOfWeek > 6))
                    throw new StreamCorruptedException(
                            "Invalid day of week: " + event.dayOfWeek);
                break;

            case RepeatEvent.TYPE_REPEAT_BY_WEEK:
                dummy = readInteger(stream);
                if (dummy != 1)
                    throw new StreamCorruptedException("Unfamiliar value"
                            + " for repeat weekly event: " + dummy);
                event.dayOfWeekBitmap = readByte(stream);
                if ((event.dayOfWeekBitmap & 0x80) != 0)
                    throw new StreamCorruptedException(
                            "Eighth bit set in a day of week bitmap");
                break;

            case RepeatEvent.TYPE_REPEAT_BY_MONTH_DATE:
                event.dateOfMonth = readInteger(stream);
                if ((event.dateOfMonth < 1) || (event.dateOfMonth > 31))
                    throw new StreamCorruptedException(
                            "Invalid date of month: " + event.dateOfMonth);
                break;

            case RepeatEvent.TYPE_REPEAT_BY_MONTH_DAY:
                event.dayOfWeek = readInteger(stream);
                if ((event.dayOfWeek < 0) || (event.dayOfWeek > 6))
                    throw new StreamCorruptedException(
                            "Invalid day of week: " + event.dayOfWeek);
                event.weekOfMonth = readInteger(stream);
                if ((event.weekOfMonth < 0) || (event.weekOfMonth > 4))
                    throw new StreamCorruptedException(
                            "Invalid week of month: " + event.weekOfMonth);
                break;

            case RepeatEvent.TYPE_REPEAT_BY_YEAR:
                event.dateOfMonth = readInteger(stream);
                if ((event.dateOfMonth < 1) || (event.dateOfMonth > 31))
                    throw new StreamCorruptedException(
                            "Invalid date of month: " + event.dateOfMonth);
                event.monthOfYear = readInteger(stream);
                if ((event.monthOfYear < 0) || (event.monthOfYear > 11))
                    throw new StreamCorruptedException(
                            "Invalid month of year: " + event.monthOfYear);
                break;

        }
        return event;
    }

    /** Read a single byte from the given file. */
    public byte readByte(InputStream stream) throws IOException {
        int b = stream.read();
        if (b < 0)
            throw new EOFException("Expected another byte, got nothing");
        return (byte) b;
    }

    /**
     * Read a 2-byte unsigned number from the given file.
     * The number is interpreted in little-endian order.
     */
    public short readShort(InputStream stream) throws IOException {
        byte[] data = new byte[2];
        int c = stream.read(data, 0, data.length);
        if (c != data.length)
            throw new EOFException(
                    "Expected " + data.length + " bytes, got " + c);
        short value = 0;
        for (int i = 0; i < data.length; i++)
            value |= (short) ((data[i] & 0xff) << (i * 8));
        return value;
    }

    /**
     * Read a 4-byte unsigned number from the given file.
     * The number is interpreted in little-endian order.
     */
    public int readInteger(InputStream stream) throws IOException {
        byte[] data = new byte[4];
        int c = stream.read(data, 0, data.length);
        if (c != data.length)
            throw new EOFException(
                    "Expected " + data.length + " bytes, got " + c);
        int value = 0;
        for (int i = 0; i < data.length; i++)
            value |= (data[i] & 0xff) << (i * 8);
        return value;
    }

    /**
     * Skip a given number of bytes from the file.
     * The bytes are expected to all be zero.
     */
    public void skipZeroes(InputStream stream, int length) throws IOException {
        byte[] data = new byte[length];
        int c = stream.read(data, 0, data.length);
        if (c != data.length)
            throw new EOFException(
                    "Expected " + data.length + " bytes, got " + c);
        for (int i = 0; i < data.length; i++) {
            if (data[i] != 0)
                throw new StreamCorruptedException(
                    "Expected 0, got " + (data[i] & 0xff));
        }
    }

    /**
     * Read a character sequence from the given file.
     * The first byte contains the length if less than 255.
     * If the first byte is 255, the second two bytes contain the length.
     */
    public String readString(InputStream stream) throws IOException {
        int length = stream.read();
        if (length < 0)
            throw new EOFException("Expected 1 byte, got 0");
        if (length == 0xff)
            length = readShort(stream);
        return readString(stream, length);
    }

    /**
     * Read a character sequence from the given file.
     * The first two bytes contains the length.
     */
    public String readLongString(InputStream stream) throws IOException {
        return readString(stream, readShort(stream));
    }

    /**
     * Read a character sequence from the given file.
     * The length has already been read.
     */
    public String readString(InputStream stream, int length) throws IOException {
        byte[] data = new byte[length];
        int c = stream.read(data, 0, length);
        if (c != length)
            throw new EOFException(
                    "Expected " + length + " bytes, got " + c);
        try {
            return new String(data, "Cp1252");
        } catch (UnsupportedEncodingException uex) {
            Log.e(TAG, "Error interpreting string value using Cp1252 encoding", uex);
            throw new StreamCorruptedException(uex.getMessage());
        }
    }

    /**
     * Merge the category list from the Palm database
     * with the Android database.
     *
     * @throws IllegalStateException if the Palm database has not been read.
     */
    private void mergeCategories() {
        // Need to read in the current list of categories
        // regardless of the import type
        Map<Long,String> categoryIDMap = new HashMap<>();
        Map<String,Long> categoryNameMap = new HashMap<>();
        for (ToDoCategory category : repository.getCategories()) {
            categoryIDMap.put(category.getId(), category.getName());
            categoryNameMap.put(category.getName(), category.getId());
        }

        int i;
        switch (importType) {
            case CLEAN:
                Log.d(TAG, ".mergeCategories: removing all existing categories");
                repository.deleteAllCategories();
                for (i = 0; i < dataCategories.length; i++) {
                    Log.d(TAG, ".mergeCategories: adding \""
                            + dataCategories[i].longName + "\"");
                    ToDoCategory newCat = new ToDoCategory();
                    newCat.setId(dataCategories[i].newID);
                    newCat.setName(dataCategories[i].longName);
                    repository.insertCategory(newCat);
                    importCount++;
                    progressUpdater.updateProgress(
                            modeText.get(OpMode.CATEGORIES),
                            importCount, totalCount, true);
                }
                break;

            case OVERWRITE:
                /*
                 * First remove all conflicting names.
                 * DO NOT add new categories in the same loop,
                 * as that may lead to inconsistencies between
                 * what's in the database and our maps.
                 */
                for (i = 0; i < dataCategories.length; i++) {
                    if (categoryNameMap.containsKey(dataCategories[i].longName)) {
                        long oldId = categoryNameMap.get(dataCategories[i].longName);
                        Log.d(TAG, String.format(".mergeCategories: \"%s\""
                                + " already exists with ID %d; deleting it.",
                                dataCategories[i].longName, oldId));
                        repository.deleteCategory(oldId);
                        categoryIDMap.remove(oldId);
                        categoryNameMap.remove(dataCategories[i].longName);
                    }
                }
                for (i = 0; i < dataCategories.length; i++) {
                    if (categoryIDMap.containsKey(dataCategories[i].newID)) {
                        if (!categoryIDMap.get(dataCategories[i].newID)
                                .equals(dataCategories[i].longName)) {
                            Log.d(TAG, ".mergeCategories: replacing \""
                                    + categoryIDMap.get(dataCategories[i].newID)
                                    + "\" with \""
                                    + dataCategories[i].longName + "\"");
                            repository.updateCategory(dataCategories[i].newID,
                                    dataCategories[i].longName);
                        }
                    } else {
                        Log.d(TAG, ".mergeCategories: adding \""
                                + dataCategories[i].longName + "\"");
                        ToDoCategory newCat = new ToDoCategory();
                        newCat.setId(dataCategories[i].newID);
                        newCat.setName(dataCategories[i].longName);
                        repository.insertCategory(newCat);
                    }
                    importCount++;
                    progressUpdater.updateProgress(
                            modeText.get(OpMode.CATEGORIES),
                            importCount, totalCount, true);
                }
                break;

            case MERGE:
                // Since we can't have duplicate category names,
                // adding is the same as merging.
            case ADD:
                for (i = 0; i < dataCategories.length; i++) {
                    if (categoryNameMap.containsKey(dataCategories[i].longName)) {
                        dataCategories[i].newID =
                                categoryNameMap.get(dataCategories[i].longName);
                        if (dataCategories[i].newID != dataCategories[i].ID)
                            Log.d(TAG, ".mergeCategories: changing the ID of \""
                                    + dataCategories[i].longName + "\" from "
                                    + dataCategories[i].ID + " to "
                                    + dataCategories[i].newID);
                    } else if (categoryIDMap.containsKey(dataCategories[i].newID)) {
                        // Use a new ID when there is a conflict
                        Log.d(TAG, ".mergeCategories: adding \""
                                + dataCategories[i].longName + "\"");
                        ToDoCategory newCat = repository.insertCategory(
                                dataCategories[i].longName);
                        dataCategories[i].newID = newCat.getId();
                    } else {
                        ToDoCategory newCat = new ToDoCategory();
                        newCat.setId(dataCategories[i].ID);
                        newCat.setName(dataCategories[i].longName);
                        Log.d(TAG, String.format(
                                ".mergeCategories: adding %s", newCat));
                        repository.insertCategory(newCat);
                        dataCategories[i].newID = newCat.getId();
                    }
                    importCount++;
                    progressUpdater.updateProgress(
                            modeText.get(OpMode.CATEGORIES),
                            importCount, totalCount, true);
                }
                break;

        }
    }

    /**
     * Merge the next To Do item from the Palm database
     * with the Android database.
     *
     * @param dataToDo the record to merge
     *
     * @throws StreamCorruptedException if we encounter a repeat interval
     * type that isn&rsquo;t handled.
     */
    private void mergeToDo(ToDoEntry dataToDo) throws IOException {

        ToDoItem newRecord = new ToDoItem();
        ToDoItem existingRecord = null;
        if (importType != ImportType.CLEAN) {
            /*
             * Check whether a record with the same ID already exists.
             */
            existingRecord = repository.getItemById(dataToDo.ID);
        }
        newRecord.setCreateTimeNow();
        switch (importType) {
            case OVERWRITE:
                if (existingRecord != null) {
                    // Debug individual items only if the number is small
                    if (importCount < 64) {
                        Log.d(TAG, String.format(
                                ".mergeToDos: replacing existing record"
                                        + " %d [%s] \"%s\" with [%s] \"%s\"",
                                dataToDo.ID,
                                existingRecord.getCategoryName(),
                                existingRecord.getDescription(),
                                categoryMap.get(dataToDo.categoryIndex).longName,
                                dataToDo.description));
                    }
                    repository.deleteItem(dataToDo.ID);
                }
                // fall through

            case CLEAN:
                newRecord.setId(dataToDo.ID);
                break;

            case MERGE:
                if (existingRecord == null)
                    newRecord.setId(dataToDo.ID);
                else if (existingRecord.getCategoryName().equals(
                        categoryMap.get(dataToDo.categoryIndex).longName) &&
                        existingRecord.getDescription().equals(
                                dataToDo.description)) {
                    if (importCount < 64) {
                        Log.d(TAG, String.format(
                                ".mergeToDos: updating record %d [%s] \"%s\"",
                                dataToDo.ID,
                                existingRecord.getCategoryName(),
                                existingRecord.getDescription()));
                    }
                    newRecord.setCreateTime(
                            existingRecord.getCreateTime());
                    repository.deleteItem(dataToDo.ID);
                    newRecord.setId(dataToDo.ID);
                } else {
                    if (importCount < 64) {
                        Log.d(TAG, String.format(
                                ".mergeToDos: changing ID of record [%s] \"%s\" from %d to %d",
                                categoryMap.get(dataToDo.categoryIndex).longName,
                                dataToDo.description, dataToDo.ID,
                                nextFreeRecordID));
                    }
                    newRecord.setId(nextFreeRecordID++);
                }
                break;

            case ADD:
                if (existingRecord == null)
                    newRecord.setId(dataToDo.ID);
                else {
                    if (importCount < 64) {
                        Log.d(TAG, String.format(
                                ".mergeToDos: changing ID of record [%s] \"%s\" from %d to %d",
                                categoryMap.get(dataToDo.categoryIndex).longName,
                                dataToDo.description, dataToDo.ID,
                                nextFreeRecordID));
                    }
                    newRecord.setId(nextFreeRecordID++);
                }
                break;

        }

        // Set all of the other values
        newRecord.setPrivate(dataToDo.isPrivate ?
                (((encryptor != null) && encryptor.hasKey()) ?
                        StringEncryption.encryptionType()
                        : StringEncryption.NO_ENCRYPTION) : 0);
        newRecord.setDescription(dataToDo.description
                .replace("\r", ""));
        if ((dataToDo.note != null) &&
                (dataToDo.note.length() > 0))
            newRecord.setNote(dataToDo.note
                    .replace("\r", ""));
        if (newRecord.isEncrypted()) try {
            newRecord.setEncryptedDescription(encryptor.encrypt(
                    newRecord.getDescription()));
            if (newRecord.getNote() != null)
                newRecord.setEncryptedNote(encryptor.encrypt(
                        newRecord.getNote()));
        } catch (EncryptionException gsx) {
            newRecord.setPrivate(StringEncryption.NO_ENCRYPTION);
        }
        newRecord.setModTimeNow();
        if ((dataToDo.dueDate >= 0) &&
                (dataToDo.dueDate <= ToDoEntry.MAX_DATE))
            newRecord.setDue(LocalDate.ofInstant(
                    Instant.ofEpochSecond(dataToDo.dueDate),
                    ZoneOffset.UTC));
        if ((dataToDo.completionDate != null) &&
                (dataToDo.completionDate >= 0) &&
                (dataToDo.completionDate <= ToDoEntry.MAX_DATE))
            newRecord.setCompleted(Instant.ofEpochSecond(
                    dataToDo.completionDate));
        newRecord.setChecked(dataToDo.completed);
        newRecord.setPriority(dataToDo.priority);
        newRecord.setCategoryId(categoryMap
                .get(dataToDo.categoryIndex).newID);
        if (Boolean.TRUE.equals(dataToDo.hasAlarm)) {
            ToDoAlarm alarm = new ToDoAlarm();
            alarm.setAlarmDaysEarlier(
                    dataToDo.alarmDaysInAdvance);
            alarm.setTime(LocalTime.ofSecondOfDay(
                    dataToDo.alarmTime % 86400));
            newRecord.setAlarm(alarm);
        }
        if (dataToDo.repeat != null) {
            AbstractRepeat repeat = null;
            switch (dataToDo.repeat.type) {

                case RepeatEvent.TYPE_REPEAT_BY_DAY:
                    repeat = /* dataToDo.repeatAfterCompleted
                            ? new RepeatDayAfter() : */ new RepeatDaily();
                    break;

                case RepeatEvent.TYPE_REPEAT_BY_WEEK:
                    /* if (dataToDo.repeatAfterCompleted)
                        repeat = new RepeatWeekAfter();
                    else */ {
                    RepeatWeekly rw = new RepeatWeekly();
                    rw.setWeekDays(WeekDays.fromBitMap(
                            dataToDo.repeat.dayOfWeekBitmap));
                    repeat = rw;
                }
                break;

                case RepeatEvent.TYPE_REPEAT_BY_MONTH_DAY:
                    RepeatMonthlyOnDay rmdy = new RepeatMonthlyOnDay();
                    rmdy.setDay(WeekDays.fromValue(
                            dataToDo.repeat.dayOfWeek
                                    // Need to adjust Sunday base
                                    // value; Palm used 0 for Sunday.
                                    + WeekDays.SUNDAY.getValue()));
                    rmdy.setWeek((dataToDo.repeat.weekOfMonth < 4)
                            // Need to adjust week base value;
                            // Palm used 0 for the first week
                            // while we use 1
                            ? dataToDo.repeat.weekOfMonth + 1
                            // and Palm used 4 for the last week
                            // while we use -1.
                            : -1);
                    repeat = rmdy;
                    break;

                case RepeatEvent.TYPE_REPEAT_BY_MONTH_DATE:
                    /* if (dataToDo.repeatAfterCompleted)
                        repeat = new RepeatMonthAfter();
                    else */ {
                    RepeatMonthlyOnDate rmdt =
                            new RepeatMonthlyOnDate();
                    rmdt.setDate(dataToDo.repeat.dateOfMonth);
                    repeat = rmdt;
                }
                break;

                case RepeatEvent.TYPE_REPEAT_BY_YEAR:
                    /* if (dataToDo.repeatAfterCompleted)
                        repeat = new RepeatYearAfter();
                    else */ {
                    RepeatYearlyOnDate rydt = new RepeatYearlyOnDate();
                    rydt.setDate(dataToDo.repeat.dateOfMonth);
                    rydt.setMonth(Months.fromValue(
                            dataToDo.repeat.monthOfYear));
                    repeat = rydt;
                }
                break;

                default:
                    throw new StreamCorruptedException(String.format(Locale.US,
                            "Unhandled repeat type %d",
                            dataToDo.repeat.type));
            }
            repeat.setIncrement(dataToDo.repeat.interval);
            if ((dataToDo.repeat.repeatUntil >= 0) &&
                    (dataToDo.repeat.repeatUntil <=
                            ToDoEntry.MAX_DATE))
                repeat.setEnd(LocalDate.ofInstant(
                        Instant.ofEpochSecond(
                                dataToDo.repeat.repeatUntil),
                        ZoneOffset.UTC));
            newRecord.setRepeatInterval(repeat);
        }

        if (importType != ImportType.TEST)
            repository.insertItem(newRecord);
    }

}
