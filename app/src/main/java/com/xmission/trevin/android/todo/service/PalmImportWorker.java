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

import android.content.Context;
import android.database.SQLException;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.xmission.trevin.android.todo.R;
import com.xmission.trevin.android.todo.data.*;
import com.xmission.trevin.android.todo.data.repeat.*;
import com.xmission.trevin.android.todo.provider.ToDoCursor;
import com.xmission.trevin.android.todo.provider.ToDoRepository;
import com.xmission.trevin.android.todo.provider.ToDoRepositoryImpl;
import com.xmission.trevin.android.todo.provider.ToDoSchema;
import com.xmission.trevin.android.todo.util.StringEncryption;

import java.io.*;
import java.security.GeneralSecurityException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class PalmImportWorker extends Worker {

    public static final String TAG = "PalmImportWorker";

    /**
     * The key of the input data that holds the location
     * of the &ldquo;todo.dat&rdquo; file
     */
    public static final String PALM_DATA_FILENAME = "PalmDataFilename";
    /** The key of the input data that holds the import type */
    public static final String PALM_IMPORT_TYPE = "PalmImportType";

    /**
     * The key of the progress data that holds the current mode
     * of operation
     */
    public static final String PROGRESS_CURRENT_MODE = "ProgressCurrentMode";
    /**
     * The key of the progress data that holds the
     * total number of categories or items to be imported
     */
    public static final String PROGRESS_MAX_COUNT = "ProgressMaxCount";
    /**
     * The key of the progress data that holds the number of
     * categories or items that have been imported so far
     */
    public static final String PROGRESS_CURRENT_COUNT = "ProgressCurrentCount";

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
        MERGE(3),

        /**
         * Any items in the Palm database with the same internal ID as an
         * item in the Android database should be added as a new item
         * with a newly assigned ID.  Will result in duplicates if the
         * database had been imported before, but is the safest option
         * if importing a different database.
         */
        ADD(4),

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

    /** todo.dat file header */
    public static final int TD10_MAGIC = 0x54440100;
    public static final int TD20_MAGIC = 0x54440200;
    /** todo.dat pre-file header (huh?) */
    public static final int MAGIC = 0xcafebabe;

    /** The location of the todo.dat file */
    private File dataFile;

    /** The type of import this worker will be doing */
    private ImportType importType;

    /** Category entry as stored in the data file */
    public static class CategoryEntry {
        /** The category key used by To Do entries in the Palm database */
        int index;
        /** The original category ID in the Palm database */
        int ID;
        int dirty;
        String longName;
        /** Short version of the category name; limited to 8 characters */
        String shortName;
        /** If merging or adding, the new category ID in the Android database */
        long newID;

        @Override
        @NonNull
        public String toString() {
            StringBuilder sb = new StringBuilder("CategoryEntry[#");
            sb.append(index).append(",ID=");
            sb.append(ID).append(',');
            if (dirty != 0)
                sb.append("dirty,");
            sb.append("name=\"").append(longName).append("\",abbr=\"");
            sb.append(shortName).append("\"]");
            return sb.toString();
        }
    }
    /** Table of categories from the data file */
    private CategoryEntry[] dataCategories;
    /** Lookup map from the Palm category ID to the category entry */
    SparseArray<CategoryEntry> categoryMap;

    /** Data file version */
    private int tdVersion;

    /** Data file schema resource ID */
    private int dataResourceID;

    /** Number of fields per ToDoSchema entry */
    private int dataFieldsPerEntry;

    /**
     * Position of the record ID in the ToDoSchema entry.
     * This is effectively an index into the data structure
     * as an array of 4-byte C {@code long}s, which includes
     * one element for the field type and one for the field
     * value.  Since the record ID should be the first field,
     * its position is 0.
     */
    private int dataRecordIDPosition;

    /** Position of the status field in the ToDoSchema entry */
    private int dataRecordStatusPosition;

    /** Position of the placement field in the ToDoSchema entry */
    private int dataRecordPlacementPosition;

    /** Field types in the schema */
    private int[] dataFieldTypes;
    /** Integer field type identifier */
    static final int TYPE_INTEGER = 1;
    /** Float field type identifier */
    static final int TYPE_FLOAT = 2;
    /** Date field type identifier (seconds since the epoch) */
    static final int TYPE_DATE = 3;
    /** Alpha field type identifier (?) */
    static final int TYPE_ALPHA = 4;
    /** C string field type identifier (length prefix) */
    static final int TYPE_CSTRING = 5;
    /** Boolean field type identifier (integer ?= 0) */
    static final int TYPE_BOOLEAN = 6;
    /** Bit flag field type identifier */
    static final int TYPE_BITFLAG = 7;
    /** Repeat event field type identifier */
    static final int TYPE_REPEAT = 8;
    // Unknown data type; appears to be 4 bytes
    static final int TYPE_UNKNOWN40 = 64;
    // Also unknown; appears to be 4 bytes
    static final int TYPE_UNKNOWN41 = 65;
    // Also also unknown; appears to be 4 bytes
    static final int TYPE_UNKNOWN42 = 66;
    // Differs from the data type in the records!  Appears to be 4 bytes
    static final int TYPE_UNKNOWN43 = 67;

    /** Next free record ID (counting both the Palm and Android databases) */
    private long nextFreeRecordID;

    /** To Do entry as stored in the data file */
    public static class ToDoEntry {
        /** Expected field types for version 1 */
        static final int[] expectedFieldTypesV1 = {
                TYPE_INTEGER, TYPE_INTEGER, TYPE_INTEGER, TYPE_CSTRING,
                TYPE_DATE, TYPE_BOOLEAN, TYPE_INTEGER, TYPE_BOOLEAN,
                TYPE_INTEGER, TYPE_CSTRING
        };
        /** Expected fields for version 2 */
        static final int[] expectedFieldTypesV2 = {
                TYPE_INTEGER, TYPE_INTEGER, TYPE_INTEGER, TYPE_INTEGER,
                TYPE_INTEGER, TYPE_INTEGER, TYPE_BOOLEAN, TYPE_UNKNOWN40,
                TYPE_UNKNOWN41, TYPE_UNKNOWN42, TYPE_UNKNOWN43,
                TYPE_CSTRING, TYPE_CSTRING, TYPE_CSTRING, TYPE_DATE,
                TYPE_BOOLEAN, TYPE_INTEGER, TYPE_CSTRING, TYPE_BOOLEAN,
                TYPE_DATE, TYPE_BOOLEAN, TYPE_DATE, TYPE_INTEGER,
                TYPE_DATE, TYPE_REPEAT };
        // Internal field identifiers; these were originally based on
        // 0-based field positions in a V2 data file, but there are
        // many unknown fields.
        static final int FIELD_ID = 0;
        /** Record ID */
        Integer ID;
        // In version 2 files, this field appears to be 0x1000
        // plus the index of the record.
        static final int FIELD_PLUSK_INDEX = 1;
        static final int FIELD_STATUS = 2;
        /** Status flags (bitmask) */
        Integer status;
        /** Bit for the "add" flag */
        static final int STATUS_ADD = 1;
        /** Bit for the "update" flag */
        static final int STATUS_UPDATE = 2;
        /**
         * @deprecated This was supposed to be the bit
         * for the "delete" flag, but it isn't
         */
        static final int STATUS_DELETE = 4;
        /** Bit for the "pending" flag */
        static final int STATUS_PENDING = 8;
        /** Bit for the "archive" flag */
        static final int STATUS_ARCHIVE = 0x80;
        /*
         * The "offset" has nothing to do with the To Do item itself;
         * it is the number of fields we are into the database file
         * as of the start of the record, or 25 * the record index.
         */
        static final int FIELD_OFFSET = 3;
        Integer offset;
        // The "position" field appears to be a constant:
        // INT_MAX (2^32-1) for v1 files, 1 for v2 files.
        static final int FIELD_POSITION = 4;
        Integer position;
        // Field 5 is never anything but 1 in my To Do list
        static final int FIELD_CATEGORY = 5;
        Integer categoryIndex;
        static final int FIELD_PRIVATE = 6;
        Boolean isPrivate;	// field 7
        // Field 8 is never non-zero in my To Do list
        // Field 9 is never non-zero in my To Do list
        // Field 10 is never non-zero in my To Do list
        // Field 11 is never non-zero in my To Do list
        static final int FIELD_DESCRIPTION = 13;
        // Field 12 is never set in my To Do list.
        // Field 13 is never set in my To Do list.
        String description;
        static final int FIELD_DUE_DATE = 14;
        /*
         * Note: the due date is set to Dec. 31, 2031 22(?):59:59
         * for tasks which have have no due date.
         * Times appear to be set to midnight *local* time at the start
         * of the date due, not the end.  Very strange -- is there
         * something in the data file that provides the base time zone?
         */
        Long dueDate;
        /**
         * Maximum date allowed: 2032-01-01 00:00:00
         * (in seconds since the epoch)
         */
        static final long MAX_DATE = 1956528000L;
        static final int FIELD_COMPLETED = 15;
        Boolean completed;
        static final int FIELD_PRIORITY = 16;
        Integer priority;
        static final int FIELD_NOTE = 17;
        String note;
        static final int FIELD_REPEAT_AFTER_COMPLETE = 18;
        /*
         * FIXME: I can't find any documentation on whether this flag
         * simply means the event repeats (the same as a non-null
         * `repeat' field) or if it affects _how_ the next repeat
         * is calculated (e.g. must follow the completion date).
         * For now we're going to assume this is just redundant
         * with a non-null `repeat` field.
         */
        Boolean repeatAfterCompleted;
        static final int FIELD_COMPLETION_DATE = 19;
        // "Unset" completion dates are also set to Dec. 31, 2031
        Long completionDate;
        static final int FIELD_HAS_ALARM = 20;
        Boolean hasAlarm;
        // The alarm time is given in UTC on January 1/2, 1971*
        // if the alarm is set.  Otherwise the time is -1 (1s before 00:00 UTC).
        static final int FIELD_ALARM_TIME = 21;
        // Unset alarm times have the value -1
        Long alarmTime;
        static final int FIELD_ALARM_DAYS_IN_ADVANCE = 22;
        Integer alarmDaysInAdvance;
        // Field 24 is never anything but Dec 31, 2031 in my To Do list
        // The repeat interval is a variable-length field!
        static final int FIELD_REPEAT = 24;
        // Placeholder for unknown fields in version 2
        static final int FIELD_UNKNOWN = -1;
        RepeatEvent repeat;
        /** Store any unknown fields we encounter */
        Object[] unknownFields;

        /** Field order for version 1 */
        static final int[] fieldOrderV1 = {
                FIELD_ID, FIELD_STATUS, FIELD_POSITION,
                FIELD_DESCRIPTION, FIELD_DUE_DATE, FIELD_COMPLETED,
                FIELD_PRIORITY, FIELD_PRIVATE, FIELD_CATEGORY,
                FIELD_NOTE
        };
        /** Field order for version 2 */
        static final int[] fieldOrderV2 = {
                FIELD_ID, FIELD_PLUSK_INDEX, FIELD_STATUS, FIELD_OFFSET,
                FIELD_POSITION, FIELD_CATEGORY, FIELD_PRIVATE, FIELD_UNKNOWN,
                FIELD_UNKNOWN, FIELD_UNKNOWN, FIELD_UNKNOWN, FIELD_UNKNOWN,
                FIELD_UNKNOWN, FIELD_DESCRIPTION, FIELD_DUE_DATE,
                FIELD_COMPLETED, FIELD_PRIORITY, FIELD_NOTE,
                FIELD_REPEAT_AFTER_COMPLETE, FIELD_COMPLETION_DATE,
                FIELD_HAS_ALARM, FIELD_ALARM_TIME,
                FIELD_ALARM_DAYS_IN_ADVANCE, FIELD_UNKNOWN, FIELD_REPEAT
        };

        @Override
        @NonNull
        public String toString() {
            StringBuilder sb = new StringBuilder("ToDoEntry[");
            if (ID != null) {
                sb.append(String.format(Locale.US, "ID=%8d,", ID));
            }
            if (status != null) {
                sb.append("status=[");
                sb.append(((status & STATUS_ADD) != 0) ? '+' : ' ');
                sb.append(((status & STATUS_UPDATE) != 0) ? '^' : ' ');
                sb.append(((status & STATUS_DELETE) != 0) ? '-' : ' ');
                sb.append(((status & STATUS_PENDING) != 0) ? '.' : ' ');
                sb.append(((status & 0x10) != 0) ? '?' : ' ');
                sb.append(((status & 0x20) != 0) ? '?' : ' ');
                sb.append(((status & 0x40) != 0) ? '?' : ' ');
                sb.append(((status & STATUS_ARCHIVE) != 0) ? 'A' : ' ');
                sb.append(((status & 0x100) != 0) ? '?' : ' ');
                sb.append(((status & 0x1000) != 0) ? '?' : ' ');
                // I haven't seen any other bits set in this field.
                sb.append("],");
            }
            if (position != null)
                sb.append(String.format(Locale.US,
                        "position=%5d,", position));
            if (categoryIndex != null)
                sb.append(String.format(Locale.US,
                        "categoryIndex=%2d,", categoryIndex));
            if (isPrivate != null)
                sb.append(isPrivate ? "priv" : "publ").append(',');
            if (description != null)
                sb.append("description=\"").append(description
                                .replace("\\", "\\\\")
                                .replace("\r", "\\r")
                                .replace("\n", "\\n")
                ).append("\",");
            if (dueDate != null) {
                ZonedDateTime due = ZonedDateTime.ofInstant(
                        Instant.ofEpochSecond(dueDate), ZoneOffset.UTC);
                sb.append("dueDate=\"").append(due.format(
                        DateTimeFormatter.RFC_1123_DATE_TIME)).append("\",");
            }
            if (completed != null)
                sb.append("completed=").append(completed
                        ? "yes" : " no").append(',');
            if (priority != null)
                sb.append("priority=").append(priority).append(',');
            if (note != null)
                sb.append("note=\"").append(note).append("\",");
            if (completionDate != null) {
                ZonedDateTime completed = ZonedDateTime.ofInstant(
                        Instant.ofEpochSecond(completionDate),
                        ZoneOffset.UTC);
                sb.append("completionDate=\"").append(completed.format(
                        DateTimeFormatter.RFC_1123_DATE_TIME)).append("\",");
            }
            if (hasAlarm != null) {
                sb.append("hasAlarm=").append(hasAlarm).append(',');
                ZonedDateTime alarm = ZonedDateTime.ofInstant(
                        Instant.ofEpochSecond(alarmTime), ZoneOffset.UTC);
                sb.append("alarmTime=\"").append(alarm.format(
                        DateTimeFormatter.RFC_1123_DATE_TIME)).append("\",");
                sb.append("alarmDaysInAdvance=").append(alarmDaysInAdvance).append(',');
            }
            if (repeat != null) {
                sb.append(repeat);
//                if (repeatAfterCompleted)
//                    sb.append("(after last completed)");
                sb.append(',');
            }
            sb.append("\n\t");
            int[] fields = (unknownFields.length < expectedFieldTypesV2.length)
                    ? fieldOrderV1 : fieldOrderV2;
            for (int j = 0; j < fields.length; j++) {
                switch (fields[j]) {
                    case FIELD_ID:
                    case FIELD_STATUS:
                    case FIELD_OFFSET:
                    case FIELD_POSITION:
                    case FIELD_CATEGORY:
                    case FIELD_PRIVATE:
                    case FIELD_DESCRIPTION:
                    case FIELD_DUE_DATE:
                    case FIELD_COMPLETED:
                    case FIELD_PRIORITY:
                    case FIELD_NOTE:
                    case FIELD_REPEAT_AFTER_COMPLETE:
                    case FIELD_COMPLETION_DATE:
                    case FIELD_HAS_ALARM:
                    case FIELD_ALARM_TIME:
                    case FIELD_ALARM_DAYS_IN_ADVANCE:
                    case FIELD_REPEAT:
                        break;
                    default:
                        if (unknownFields[j] != null) {
                            sb.append(String.format(Locale.US,
                                    "unknown%02d=", j + 1));
                            if ((unknownFields[j] instanceof Number) ||
                                    (unknownFields[j] instanceof Boolean))
                                sb.append(unknownFields[j]).append(',');
                            else
                                sb.append('"').append(unknownFields[j])
                                        .append("\",");
                        }
                        break;
                }
            }
            sb.append(']');
            return sb.toString();
        }
    }
    private ToDoEntry[] dataToDos;

    /** Structure of a repeat event */
    static class RepeatEvent {
        // The tag may be a short following a zero short.
        // The high (15th) bit is always set.  I do not know
        // what it means; my initial assessment was incorrect.
        short tag;
        static final short TAG_UNKNOWN_01 = (short) 0x8001;
        static final short TAG_UNKNOWN_03 = (short) 0x8003;
        static final short TAG_UNKNOWN_0F = (short) 0x800f;
        static final short TAG_UNKNOWN_1B = (short) 0x801b;
        static final short TAG_UNKNOWN_24 = (short) 0x8024;
        static final short TAG_UNKNOWN_30 = (short) 0x8030;
        // If the tag is 0xffff, it is followed by a short of 1,
        // another short string length, and a string with the repeat type.
        String typeName;
        static final String NAME_REPEAT_BY_DAY = "CDayName";
        static final String NAME_REPEAT_BY_WEEK = "CWeekly";
        static final String NAME_REPEAT_BY_MONTH_DATE = "CDateOfMonth";
        static final String NAME_REPEAT_BY_MONTH_DAY = "CDayOfMonth";
        static final String NAME_REPEAT_BY_YEAR = "CDateOfYear";
        int type; 	// integer
        // These values are in the first int past the optional type tag
        static final int TYPE_REPEAT_BY_DAY = 1;
        static final int TYPE_REPEAT_BY_WEEK = 2;
        static final int TYPE_REPEAT_BY_MONTH_DAY = 3;
        static final int TYPE_REPEAT_BY_MONTH_DATE = 4;
        static final int TYPE_REPEAT_BY_YEAR = 5;
        int interval; 	// "every N days/weeks/months/years"; integer
        // Indefinite repeats have the last date set to Dec. 31, 2031
        long repeatUntil;	// date
        // I have no idea what's in the next field; it's always zero.
        // I think the rest of the fields depend on the repeat type.
        // The last field for daily repeats is the day of the week
        // (Sun=0, Mon=1, ..., Fri=5, Sat=6).
        Integer dayOfWeek;
        // The next field for weekly repeats is always 1, but it is followed
        // by a single byte containing which days of the week the event
        // occurs on: Sun=1, Mon=2, Tue=4, Wed=8, Thu=16, Fri=32, Sat=64.
        Byte dayOfWeekBitmap;
        // For monthly by date events, the last field is the date.
        Integer dateOfMonth;
        // For monthly by day events, the fields after the zero appears to be
        // the day of the week (Sun=0, Sat=6),
        // followed by the week of the month (1st=0, last=4).
        Integer weekOfMonth;
        // For annual events, the fields after the zero appears to be the
        // date of the month followed by the month of the year (Jan=0, Dec=11).
        Integer monthOfYear;
        // There is no distinction made in the entire repeat field
        // between events on a fixed schedule and events after last completed!

        /**
         * RFC-1123-like date-only formatter
         * for the {@link #toString()} method
         */
        public static final DateTimeFormatter RFC_1123_DATE =
                DateTimeFormatter.ofPattern("EEE, dd MMM yyyy");

        /** @return a String representation of this repeat event */
        @Override
        @NonNull
        public String toString() {
            StringBuilder sb = new StringBuilder("RepeatEvent[");
            sb.append(String.format("tag=%04x,", tag));
            if (typeName != null)
                sb.append("typeName=\"").append(typeName).append("\",");
            sb.append("type=");
            switch (type) {
                case TYPE_REPEAT_BY_DAY:
                    sb.append("daily");
                    break;
                case TYPE_REPEAT_BY_WEEK:
                    sb.append("weekly");
                    break;
                case TYPE_REPEAT_BY_MONTH_DATE:
                    sb.append("monthly(date)");
                    break;
                case TYPE_REPEAT_BY_MONTH_DAY:
                    sb.append("monthly(day)");
                    break;
                case TYPE_REPEAT_BY_YEAR: sb.append("yearly"); break;
                default: sb.append(type); break;
            }
            sb.append(",interval=").append(interval).append(',');
            LocalDate until = ZonedDateTime.ofInstant(
                    Instant.ofEpochSecond(repeatUntil), ZoneOffset.UTC)
                    .toLocalDate();
            sb.append("repeatUntil=\"").append(until.format(RFC_1123_DATE))
                    .append('"');
            if (dayOfWeekBitmap != null) {
                sb.append(",days=");
                if ((dayOfWeekBitmap & 1) != 0)
                    sb.append("Sun,");
                if ((dayOfWeekBitmap & 2) != 0)
                    sb.append("Mon,");
                if ((dayOfWeekBitmap & 4) != 0)
                    sb.append("Tue,");
                if ((dayOfWeekBitmap & 8) != 0)
                    sb.append("Wed,");
                if ((dayOfWeekBitmap & 16) != 0)
                    sb.append("Thu,");
                if ((dayOfWeekBitmap & 32) != 0)
                    sb.append("Fri,");
                if ((dayOfWeekBitmap & 64) != 0)
                    sb.append("Sat,");
                if (dayOfWeekBitmap != 0)
                    sb.deleteCharAt(sb.length() - 1);
            }
            if (dateOfMonth != null)
                sb.append(",date=").append(dateOfMonth);
            if (dayOfWeek != null) {
                sb.append(",day=");
                switch (dayOfWeek) {
                    case 0: sb.append("Sunday"); break;
                    case 1: sb.append("Monday"); break;
                    case 2: sb.append("Tuesday"); break;
                    case 3: sb.append("Wednesday"); break;
                    case 4: sb.append("Thursday"); break;
                    case 5: sb.append("Friday"); break;
                    case 6: sb.append("Saturday"); break;
                    default: sb.append(dayOfWeek); break;
                }
            }
            if (weekOfMonth != null) {
                sb.append(",week=");
                if (weekOfMonth == 4)
                    sb.append("last");
                else
                    sb.append(weekOfMonth + 1);
            }
            if (monthOfYear != null) {
                sb.append(",month=");
                switch (monthOfYear) {
                    case 0: sb.append("January"); break;
                    case 1: sb.append("February"); break;
                    case 2: sb.append("March"); break;
                    case 3: sb.append("April"); break;
                    case 4: sb.append("May"); break;
                    case 5: sb.append("June"); break;
                    case 6: sb.append("July"); break;
                    case 7: sb.append("August"); break;
                    case 8: sb.append("September"); break;
                    case 9: sb.append("October"); break;
                    case 10: sb.append("November"); break;
                    case 11: sb.append("December"); break;
                    default: sb.append(monthOfYear); break;
                }
            }
            sb.append(']');
            return sb.toString();
        }
    }

    /** Flag whether the Palm database has been successfully read in */
    private boolean hasReadPalmDB = false;

    /** The current mode of operation */
    public enum OpMode {
	READING, CATEGORIES, ITEMS
    }
    private OpMode currentMode = OpMode.READING;

    /** The total number of entries to be imported */
    private int totalCount = 0;

    /** The current number of entries imported */
    private int importCount = 0;

    /** Internal time at which we started the import, for statistics */
    private long startTimeNano;
    /** Internal time when we last updated the async progress */
    private long lastProgressTimeNano;

    @NonNull
    private final Context context;

    @NonNull
    private final ToDoRepository repository;

    /** Handler for making calls involving the UI */
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    /**
     * Initialize the PalmImportWorker using the standard system services
     * and app class instances.
     *
     * @param context the application context
     * @param params Parameters to set up the internal state of this worker
     *
     * @throws IllegalArgumentException if the input data is invalid.
     */
    public PalmImportWorker(@NonNull Context context,
                            @NonNull WorkerParameters params)
        throws IllegalArgumentException, IOException {
        super(context, params);
        Log.d(TAG, String.format("Default initialization for %s",
                context.getClass().getName()));
        this.context = context;
        repository = ToDoRepositoryImpl.getInstance();
        getInputData(params);
    }

    /**
     * Initialize the PalmImportWorker using designated services and
     * app class instances.  This is intended for testing purposes
     * to provide mock services.
     *
     * @param context the application context.
     * @param params Parameters to set up the internal state of this worker.
     * @param repository the To Do data repository.
     *
     * @throws IllegalArgumentException if the input data is invalid.
     */
    public PalmImportWorker(@NonNull Context context,
                            @NonNull WorkerParameters params,
                       @NonNull ToDoRepository repository)
            throws IllegalArgumentException, IOException {
        super(context, params);
        Log.d(TAG, String.format(
                "Custom initialization for (%s, %s)",
                context.getClass().getName(),
                repository.getClass().getName()));
        this.context = context;
        this.repository = repository;
        getInputData(params);
    }

    /**
     * Extract our input data from the {@link WorkerParameters}.
     *
     * @throws FileNotFoundException if the Palm data file does not exist
     * @throws IllegalArgumentException if the input data is invalid.
     * @throws IOException if the Palm data file cannot be read.
     */
    private void getInputData(@NonNull WorkerParameters params)
            throws IllegalArgumentException, IOException {
        if (!params.getInputData().hasKeyWithValueOfType(
                PALM_DATA_FILENAME, String.class))
            throw new IllegalArgumentException(
                    "No Palm data file provided");
        dataFile = new File(params.getInputData()
                .getString(PALM_DATA_FILENAME));
        if (!params.getInputData().hasKeyWithValueOfType(
                PALM_IMPORT_TYPE, Integer.class))
            throw new IllegalArgumentException("Import type not provided");
        importType = ImportType.fromInt(params.getInputData()
                .getInt(PALM_IMPORT_TYPE, -1));
        if (!dataFile.canRead()) {
            if (!dataFile.exists())
                throw new FileNotFoundException(dataFile.getName()
                        + " does not exist");
            throw new IOException("Cannot read " + dataFile.getName());
        }
    }

    /**
     * @return a text description of the current mode of operation
     */
    private String getCurrentMode() {
        switch (currentMode) {
            case READING:
                return context.getString(R.string.ProgressMessageImportReading);
            case CATEGORIES:
                return context.getString(R.string.ProgressMessageImportCategories);
            case ITEMS:
                return context.getString(R.string.ProgressMessageImportItems);
            default:
                // This should be unreachable!
                return context.getString(R.string.ProgressMessageStart);
        }
    }

    /**
     * Main entry point of the worker.
     */
    @Override
    @NonNull
    public Result doWork() {
        Log.d(TAG, ".doWork");
        currentMode = OpMode.READING;
        importCount = 0;
        // Don't set this to 0, or it will mess up the progress bar.
        totalCount = 1;
        startTimeNano = System.nanoTime();
        lastProgressTimeNano = startTimeNano;
        updateProgress(context.getString(
                R.string.ProgressMessageImportReading), false);
        repository.open(context);
        try {
            readDataFile();
            if ((dataToDos == null) || (dataToDos.length == 0)) {
                showToast(context.getString(
                        R.string.ErrorNoRecordsImported));
                return Result.failure(new Data.Builder()
                                .putString("Error", "No items read from "
                                        + dataFile.getName())
                                .putString("message", "No records were imported")
                        .build());
            }
            repository.runInTransaction(new ImportTransactionRunner());
            updateProgress(context.getString(
                    R.string.ProgressMessageFinish), false);
            return Result.success();
        }

        catch (Exception e) {
            Log.e(TAG, "Error importing Palm data!", e);
            showToast(e.getMessage());
            return Result.failure(new Data.Builder()
                    .putString("Exception", e.getClass().getCanonicalName())
                    .putString("message", e.getMessage())
                    .build());
        }

        finally {
            long now = System.nanoTime();
            Log.d(TAG, String.format("Finished work in %.4f seconds",
                    (now - startTimeNano) / 1.0e+9));
            repository.release(context);
        }
    }

    /**
     * All changes to the database are done in this transaction runner
     * so if the the import fails in the middle of a CLEAN or OVERWRITE
     * import type we won&rsquo;t lose any old data.
     */
    private class ImportTransactionRunner implements Runnable {
        @Override
        public void run() throws SQLException {
            mergeToDos();
        }
    }

    /**
     * Update the async progress indicator.
     *
     * @param modeString the current mode of operation (reading,
     *                   adding categories, adding items)
     * @param throttle if {@code true}, skip updating the progress
     *                 if it&rsquo;s been less than 250 ms since
     *                 we last posted our progress.
     */
    private void updateProgress(String modeString, boolean throttle) {
        if (throttle) {
            long now = System.nanoTime();
            if ((now - lastProgressTimeNano) < 250000000L)
                return;
        }
        Data progressData = new Data.Builder()
                .putString(PROGRESS_CURRENT_MODE, modeString)
                .putInt(PROGRESS_MAX_COUNT, totalCount)
                .putInt(PROGRESS_CURRENT_COUNT, importCount)
                .build();
        setProgressAsync(progressData);
    }

    /**
     * Read the data file.
     *
     * @return the number of records in the database
     * if the data file is readable and valid.
     * @throws FileNotFoundException if the file does not exist.
     * @throws SecurityException if the file is not readable.
     * @throws StreamCorruptedException if the file
     * is not a valid To Do data file.
     */
    public int readDataFile() throws IOException, SecurityException {
        Log.d(TAG, ".readDataFile: reading " + dataFile.getCanonicalPath());
        // Clear up some data just in case we're called more than once
        hasReadPalmDB = false;
        dataCategories = null;
        dataFieldTypes = null;
        dataToDos = null;
        totalCount = 0;
        importCount = 0;
        final String progressMode = context.getString(
                R.string.ProgressMessageImportReading);

        InputStream stream = new BufferedInputStream(
                new FileInputStream(dataFile));

        // Start with the metadata
        while (tdVersion == 0) {
            int magic = readInteger(stream);
            switch (magic) {

                case MAGIC:
                    // This file has some additional headers
                    // that need reading first
                    String palmTag = readString(stream);
                    Log.d(TAG, ".readDataFile: Data file identifier = "
                            + palmTag);
                    magic = readInteger(stream);
                    Log.d(TAG, String.format(".readDataFile: revision %c%c%c%c",
                            magic & 0xff, (magic >> 8) & 0xff,
                            (magic >> 16) & 0xff, (magic >> 24) & 0xff));
                    break;
//                magic = readInteger(stream);
//                if (magic != TD20_MAGIC)
//                    throw new StreamCorruptedException(String.format(
//                            "Magic file header mismatch:"
//                            + " expected %08X, got %08X",
//                            TD20_MAGIC, magic));
                case TD20_MAGIC:
                    // There are a bunch of unknown bytes here.  Mostly zero.
                    skipZeroes(stream, 12);
                    readInteger(stream);	// This is not zero.  It's 0x1165.
                    skipZeroes(stream, 8);
                    String v2FileName = readString(stream);
                    Log.d(TAG, ".readDataFile: Saved file name = \""
                            + v2FileName + "\"");
                    // There are an odd number of zero bytes following
                    skipZeroes(stream, 43);
                    readInteger(stream); // This looks like a category ID,
                                         // but we're not there yet, and it's
                                         // not greater than the highest ID.
                    tdVersion = 2;
                    break;

                case TD10_MAGIC:
                    String v1FileName = readString(stream);
                    Log.d(TAG, ".readDataFile: Saved file name = \""
                            + v1FileName + "\"");
                    // Skip the "custom show header" (?)
                    String showHeader = readString(stream);
                    Log.d(TAG, ".readDataFile: skipping show header \""
                            + showHeader + "\"");
                    readInteger(stream);	// data: Next Free Category ID
                    tdVersion = 1;
                    break;

                default:
                    throw new StreamCorruptedException(String.format(
                        "Magic file header mismatch: expected %08X or %08X, got %08X",
                        TD10_MAGIC, TD20_MAGIC, magic));

            }
        }

        // Now read in the category list
        int catCount = readInteger(stream);
        Log.d(TAG, ".readDataFile: " + catCount + " categories");
        if (catCount >= 256)
            throw new StreamCorruptedException(
                    "Suspect category count (" + catCount + ")");
        // Initial estimate of the total work
        // (read + write * number of categories)
        totalCount = catCount * 2;
        importCount = 0;
        dataCategories = new CategoryEntry[catCount];
        int i;
        categoryMap = new SparseArray<>();
        // There is an implicit category entry for Unfiled;
        // not included in the category count above.
        CategoryEntry unfiled = new CategoryEntry();
        unfiled.ID = (int) ToDoSchema.ToDoCategoryColumns.UNFILED;
        unfiled.longName = "Unfiled";
        unfiled.shortName = "Unfiled";
        unfiled.newID = ToDoSchema.ToDoCategoryColumns.UNFILED;
        categoryMap.put(unfiled.ID, unfiled);
        for (i = 0; i < catCount; i++) {
            dataCategories[i] = readCategoryEntry(stream);
            if (categoryMap.get(dataCategories[i].index) != null)
                throw new StreamCorruptedException(
                        "Duplicate category index " + dataCategories[i].index);
            categoryMap.put(dataCategories[i].index, dataCategories[i]);
            importCount = i + 1;
            updateProgress(progressMode, true);
        }

        // Read in more metadata
        dataResourceID = readInteger(stream);
        Log.d(TAG, ".readDataFile: resource ID = " + dataResourceID);
        dataFieldsPerEntry = readInteger(stream);
        Log.d(TAG, String.format(".readDataFile: %d fields per entry",
                dataFieldsPerEntry));
        dataRecordIDPosition = readInteger(stream);
        dataRecordStatusPosition = readInteger(stream);
        dataRecordPlacementPosition = readInteger(stream);
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
        int fieldCount = readShort(stream);
        if (fieldCount != dataFieldsPerEntry)
            throw new StreamCorruptedException(String.format(Locale.US,
                    "Mismatched field count: was %d, now %d",
                    dataFieldsPerEntry, fieldCount));
        if ((fieldCount < 9) || (fieldCount >= 41))
            throw new StreamCorruptedException(
                    "Suspect field count (" + fieldCount + ")");
        dataFieldTypes = new int[fieldCount];
        for (i = 0; i < fieldCount; i++) {
            dataFieldTypes[i] = readShort(stream);
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
        int numEntries = readInteger(stream);
        if (numEntries % fieldCount != 0)
            throw new StreamCorruptedException(String.format(Locale.US,
                    "Number of fields in the database %d is not"
                            + " evenly divisible by the number of"
                            + " fields per entry %d",
                    numEntries, fieldCount));
        numEntries /= fieldCount;
        if (numEntries >= 100000)
            throw new StreamCorruptedException(
                    "Suspect record count (" + numEntries + ")");
        Log.d(TAG, String.format(".readDataFile: %d total records", numEntries));
        dataToDos = new ToDoEntry[numEntries];
        totalCount = 2 * (catCount + numEntries);
        boolean sawAlarm = false;
        boolean sawRepeat = false;
        for (i = 0; i < dataToDos.length; i++) {
            dataToDos[i] = readToDoEntry(stream);
            if ((i < 10) || (!sawAlarm && Boolean.TRUE.equals(
                    dataToDos[i].hasAlarm)) ||
                    (!sawRepeat && dataToDos[i].repeat != null)) {
                Log.d(TAG, String.format(".readDataFile: Entry #%d: %s",
                        i, dataToDos[i].toString()));
                if (Boolean.TRUE.equals(dataToDos[i].hasAlarm))
                    sawAlarm = true;
                if (dataToDos[i].repeat != null)
                    sawRepeat = true;
            }
            importCount = catCount + i + 1;
            updateProgress(progressMode, true);
        }

        if (stream.available() > 0)
            Log.w(TAG, ".readDataFile: excess data at end of stream (at least"
                    + stream.available() + " bytes)");
        stream.close();
        hasReadPalmDB = true;
        return dataToDos.length;
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
        final String progressMode = context.getString(
                R.string.ProgressMessageImportCategories);
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
                    importCount = dataCategories.length
                            + dataToDos.length + i + 1;
                    updateProgress(progressMode, true);
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
                    importCount = dataCategories.length + dataToDos.length + i + 1;
                    updateProgress(progressMode, true);
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
                    importCount = dataCategories.length + dataToDos.length + i + 1;
                    updateProgress(progressMode, true);
                }
                break;

        }
    }

    /**
     * Merge the To Do items from the Palm database
     * with the Android database.
     *
     * @throws IllegalStateException if the Palm database has not been read.
     */
    private void mergeToDos() {
        final String progressMode = context.getString(
                R.string.ProgressMessageImportItems);
        StringEncryption newCrypt = StringEncryption.holdGlobalEncryption();

        try {
            if (importType == ImportType.CLEAN) {
                // Wipe them all out
                Log.d(TAG, ".mergeToDos: removing all existing To Do items");
                repository.deleteAllItems();
            }

            // Merge the categories first
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

            ToDoItem newRecord;
            ToDoItem existingRecord;
            for (int i = 0; i < dataToDos.length; i++) {
                newRecord = new ToDoItem();
                existingRecord = null;
                if (importType != ImportType.CLEAN) {
                    /*
                     * Check whether a record with the same ID already exists.
                     */
                    existingRecord = repository.getItemById(dataToDos[i].ID);
                }
                newRecord.setCreateTimeNow();
                switch (importType) {
                    case OVERWRITE:
                        if (existingRecord != null) {
                            // Debug individual items only if the number is small
                            if (dataToDos.length < 64) {
                                Log.d(TAG, String.format(
                                        ".mergeToDos: replacing existing record"
                                        + " %d [%s] \"%s\" with [%s] \"%s\"",
                                        dataToDos[i].ID,
                                        existingRecord.getCategoryName(),
                                        existingRecord.getDescription(),
                                        categoryMap.get(dataToDos[i].categoryIndex).longName,
                                        dataToDos[i].description));
                            }
                            repository.deleteItem(dataToDos[i].ID);
                        }
                        // fall through

                    case CLEAN:
                        newRecord.setId(dataToDos[i].ID);
                        break;

                    case MERGE:
                        if (existingRecord == null)
                            newRecord.setId(dataToDos[i].ID);
                        else if (existingRecord.getCategoryName().equals(
                                categoryMap.get(dataToDos[i].categoryIndex).longName) &&
                                existingRecord.getDescription().equals(
                                        dataToDos[i].description)) {
                            if (dataToDos.length < 64) {
                                Log.d(TAG, String.format(
                                        ".mergeToDos: updating record %d [%s] \"%s\"",
                                        dataToDos[i].ID,
                                        existingRecord.getCategoryName(),
                                        existingRecord.getDescription()));
                            }
                            newRecord.setCreateTime(
                                    existingRecord.getCreateTime());
                            repository.deleteItem(dataToDos[i].ID);
                            newRecord.setId(dataToDos[i].ID);
                        } else {
                            if (dataToDos.length < 64) {
                                Log.d(TAG, String.format(
                                        ".mergeToDos: changing ID of record [%s] \"%s\" from %d to %d",
                                        categoryMap.get(dataToDos[i].categoryIndex).longName,
                                        dataToDos[i].description, dataToDos[i].ID,
                                        nextFreeRecordID));
                            }
                            newRecord.setId(nextFreeRecordID++);
                        }
                        break;

                    case ADD:
                        if (existingRecord == null)
                            newRecord.setId(dataToDos[i].ID);
                        else {
                            if (dataToDos.length < 64) {
                                Log.d(TAG, String.format(
                                        ".mergeToDos: changing ID of record [%s] \"%s\" from %d to %d",
                                        categoryMap.get(dataToDos[i].categoryIndex).longName,
                                        dataToDos[i].description, dataToDos[i].ID,
                                        nextFreeRecordID));
                            }
                            newRecord.setId(nextFreeRecordID++);
                        }
                        break;

                }

                // Set all of the other values
                newRecord.setPrivate(dataToDos[i].isPrivate ?
                        (newCrypt.hasKey() ? 2 : 1) : 0);
                newRecord.setDescription(dataToDos[i].description
                        .replace("\r", ""));
                if ((dataToDos[i].note != null) &&
                        (dataToDos[i].note.length() > 0))
                    newRecord.setNote(dataToDos[i].note
                            .replace("\r", ""));
                if (newRecord.isEncrypted()) try {
                    newRecord.setEncryptedDescription(newCrypt.encrypt(
                            newRecord.getDescription()));
                    if (newRecord.getNote() != null)
                        newRecord.setEncryptedNote(newCrypt.encrypt(
                                newRecord.getNote()));
                } catch (GeneralSecurityException gsx) {
                    newRecord.setPrivate(1);
                }
                newRecord.setModTimeNow();
                if ((dataToDos[i].dueDate >= 0) &&
                        (dataToDos[i].dueDate <= ToDoEntry.MAX_DATE))
                    newRecord.setDue(LocalDate.ofInstant(
                            Instant.ofEpochSecond(dataToDos[i].dueDate),
                            ZoneOffset.UTC));
                if ((dataToDos[i].completionDate != null) &&
                        (dataToDos[i].completionDate >= 0) &&
                        (dataToDos[i].completionDate <= ToDoEntry.MAX_DATE))
                    newRecord.setCompleted(Instant.ofEpochSecond(
                            dataToDos[i].completionDate));
                newRecord.setChecked(dataToDos[i].completed);
                newRecord.setPriority(dataToDos[i].priority);
                newRecord.setCategoryId(categoryMap
                        .get(dataToDos[i].categoryIndex).newID);
                if (Boolean.TRUE.equals(dataToDos[i].hasAlarm)) {
                    ToDoAlarm alarm = new ToDoAlarm();
                    alarm.setAlarmDaysEarlier(
                            dataToDos[i].alarmDaysInAdvance);
                    alarm.setTime(LocalTime.ofSecondOfDay(
                            dataToDos[i].alarmTime % 86400));
                    newRecord.setAlarm(alarm);
                }
                if (dataToDos[i].repeat != null) {
                    AbstractRepeat repeat = null;
                    switch (dataToDos[i].repeat.type) {

                        case RepeatEvent.TYPE_REPEAT_BY_DAY:
                            repeat = /* dataToDos[i].repeatAfterCompleted
                                    ? new RepeatDayAfter() : */ new RepeatDaily();
                            break;

                        case RepeatEvent.TYPE_REPEAT_BY_WEEK:
                            /* if (dataToDos[i].repeatAfterCompleted)
                                repeat = new RepeatWeekAfter();
                            else */ {
                                RepeatWeekly rw = new RepeatWeekly();
                                rw.setWeekDays(WeekDays.fromBitMap(
                                        dataToDos[i].repeat.dayOfWeekBitmap));
                                repeat = rw;
                            }
                            break;

                        case RepeatEvent.TYPE_REPEAT_BY_MONTH_DAY:
                            RepeatMonthlyOnDay rmdy = new RepeatMonthlyOnDay();
                            rmdy.setDay(WeekDays.fromValue(
                                    dataToDos[i].repeat.dayOfWeek));
                            rmdy.setWeek(dataToDos[i].repeat.weekOfMonth);
                            repeat = rmdy;
                            break;

                        case RepeatEvent.TYPE_REPEAT_BY_MONTH_DATE:
                            /* if (dataToDos[i].repeatAfterCompleted)
                                repeat = new RepeatMonthAfter();
                            else */ {
                                RepeatMonthlyOnDate rmdt =
                                        new RepeatMonthlyOnDate();
                                rmdt.setDate(dataToDos[i].repeat.dateOfMonth);
                                repeat = rmdt;
                            }
                            break;

                        case RepeatEvent.TYPE_REPEAT_BY_YEAR:
                            /* if (dataToDos[i].repeatAfterCompleted)
                                repeat = new RepeatYearAfter();
                            else */ {
                                RepeatYearlyOnDate rydt = new RepeatYearlyOnDate();
                                rydt.setDate(dataToDos[i].repeat.dateOfMonth);
                                rydt.setMonth(Months.fromValue(
                                        dataToDos[i].repeat.monthOfYear));
                                repeat = rydt;
                            }
                            break;

                    }
                    repeat.setIncrement(dataToDos[i].repeat.interval);
                    if ((dataToDos[i].repeat.repeatUntil >= 0) &&
                            (dataToDos[i].repeat.repeatUntil <=
                                    ToDoEntry.MAX_DATE))
                        repeat.setEnd(LocalDate.ofInstant(
                                Instant.ofEpochSecond(
                                        dataToDos[i].repeat.repeatUntil),
                                ZoneOffset.UTC));
                    newRecord.setRepeatInterval(repeat);
                }

                if (importType != ImportType.TEST)
                    repository.insertItem(newRecord);

                importCount = 2 * dataCategories.length
                        + dataToDos.length + i + 1;
                updateProgress(progressMode, true);
            }
        } finally {
            StringEncryption.releaseGlobalEncryption(context);
        }
    }

    /**
     * Show a toast message.  This must be done on the UI thread.
     *
     * @param message the message to toast
     */
    private void showToast(String message) {
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, message,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

}
