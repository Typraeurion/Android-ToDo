/*
 * $Id: PalmImporterService.java,v 1.2 2014/03/22 19:03:52 trevin Exp trevin $
 * Copyright © 2011 Trevin Beattie
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
 *
 * $Log: PalmImporterService.java,v $
 * Revision 1.2  2014/03/22 19:03:52  trevin
 * Added the copyright notice.
 * Changed from running as part of the main application to a service.
 * Added test import mode.
 *
 * Revision 1.1  2010/11/20 21:58:18  trevin
 * Initial revision
 *
 */
package com.xmission.trevin.android.todo;

import java.io.*;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.*;

import com.xmission.trevin.android.todo.ToDo.ToDoCategory;
import com.xmission.trevin.android.todo.ToDo.ToDoItem;

import android.app.IntentService;
import android.content.*;
import android.database.Cursor;
import android.database.SQLException;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

/**
 * This class imports To Do data from a Palm database (todo.dat).
 */
public class PalmImporterService extends IntentService implements
        ProgressReportingService {

    public static final String LOG_TAG = "PalmImporterService";
    /**
     * The name of the Intent extra data that holds
     * the location of the todo.dat file
     */
    public static final String PALM_DATA_FILENAME =
	"com.xmission.trevin.android.todo.PalmDataFileName";
    /** The name of the Intent extra data that holds the import type */
    public static final String PALM_IMPORT_TYPE =
	"com.xmission.trevin.android.todo.PalmImportType";

    /**
     * Flag indicating how to merge items from the Palm database
     * with those in the Android database.
     */
    public static enum ImportType {
	/**
	 * The Android database should be cleared
	 * before importing the Palm database.
	 */
	CLEAN,

	/**
	 * Any items in the Android database with the same internal ID
	 * as an item in the Palm database should be overwritten.
	 */
	OVERWRITE,

	/**
	 * Any items in the Android database with the same internal ID
	 * as an item in the Palm database should be overwritten only if
	 * the two have the same category and description; otherwise,
	 * change the ID of the Android item to an unused value and
	 * add the Palm item as a new item.
	 */
	MERGE,

	/**
	 * Any items in the Palm database with the same internal ID as an
	 * item in the Android database should be added as a new item
	 * with a newly assigned ID.  Will result in duplicates if the
	 * database had been imported before, but is the safest option
	 * if importing a different database.
	 */
	ADD,

	/**
	 * Don't actually write anything to the android database.
	 * Just read the Palm database to verify the integrity of the data.
	 */
	TEST,
    }

    /** todo.dat file header */
    public static final int TD10_MAGIC = 0x54440100;
    public static final int TD20_MAGIC = 0x54440200;
    /** todo.dat pre-file header (huh?) */
    public static final int MAGIC = 0xcafebabe;

    /** The location of the todo.dat file */
    private File dataFile;

    /** Internal file name stored in the data file */
    private String dataFileName;

    /* Next free category ID in the data file */
    // private long dataNextFreeCatID;

    /** Category entry as stored in the data file */
    public static class CategoryEntry {
	/** The category key used by To Do entries in the Palm database */
	int index;
	/** The original category ID in the Palm database */
	int ID;
	int dirty;
	String longName;
	String shortName;
	/** If merging or adding, the new category ID in the Android database */
	long newID;

	@Override
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
    Map<Integer,CategoryEntry> categoryMap;

    /** Data file schema resource ID */
    private int dataResourceID;

    /** Number of fields per ToDo entry */
    private int dataFieldsPerEntry;

    /** Position of the record ID in the ToDo entry */
    private int dataRecordIDPosition;

    /** Position of the status field in the ToDo entry */
    private int dataRecordStatusPosition;

    /** Position of the placement field in the ToDo entry */
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
    // 

    /** Next free record ID (counting both the Palm and Android databases) */
    private long nextFreeRecordID = 1;

    /** To Do entry as stored in the data file */
    public static class ToDoEntry {
	/** Expected fields */
	static final int[] expectedFieldTypes = {
	    TYPE_INTEGER, TYPE_INTEGER, TYPE_INTEGER, TYPE_INTEGER,
	    TYPE_INTEGER, TYPE_INTEGER, TYPE_BOOLEAN, TYPE_UNKNOWN40,
	    TYPE_UNKNOWN41, TYPE_UNKNOWN42, TYPE_UNKNOWN43, TYPE_CSTRING,
	    TYPE_CSTRING, TYPE_CSTRING, TYPE_DATE, TYPE_BOOLEAN, TYPE_INTEGER,
	    TYPE_CSTRING, TYPE_BOOLEAN, TYPE_DATE, TYPE_BOOLEAN,
	    TYPE_DATE, TYPE_INTEGER, TYPE_DATE, TYPE_REPEAT };
	// Field positions (0-base)
	static final int FIELD_ID = 0;
	/** Record ID: field 1 */
	Integer ID;
	static final int FIELD_STATUS = 1;
	/** Status flags (bitmask): field 2 */
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
	// Field 3 is never non-zero in my To Do list
	// The "position" has nothing to do with the To Do item itself;
	// it is the number of fields we are into the database file,
	// or 25 * the record index.
	static final int FIELD_POSITION = 3;
	Integer position;	// field 4
	// Field 5 is never anything but 1 in my To Do list
	static final int FIELD_CATEGORY = 5;
	Integer categoryIndex;	// field 6
	static final int FIELD_PRIVATE = 6;
	Boolean isPrivate;	// field 7
	// Field 8 is never non-zero in my To Do list
	// Field 9 is never non-zero in my To Do list
	// Field 10 is never non-zero in my To Do list
	// Field 11 is never non-zero in my To Do list
	static final int FIELD_DESCRIPTION = 13;
	// Field 12 is never set in my To Do list.
	// Field 13 is never set in my To Do list.
	String description;	// field 14
	static final int FIELD_DUE_DATE = 14;
	/*
	 * Note: the due date is set to Dec. 31, 2031 22(?):59:59
	 * for tasks which have have no due date.
	 * Times appear to be set to midnight *local* time at the start
	 * of the date due, not the end.  Very strange -- is there
	 * something in the data file that provides the base time zone?
	 */
	Long dueDate;		// field 15
	static final long MAX_DATE = 1956528000L;
	static final int FIELD_COMPLETED = 15;
	Boolean completed;	// field 16
	static final int FIELD_PRIORITY = 16;
	Integer priority;	// field 17
	static final int FIELD_NOTE = 17;
	String note;		// field 18
	static final int FIELD_REPEAT_AFTER_COMPLETE = 18;
	Boolean repeatAfterCompleted;	// field 19
	static final int FIELD_COMPLETION_DATE = 19;
	// "Unset" completion dates are also set to Dec. 31, 2031
	Long completionDate;	// field 20
	static final int FIELD_HAS_ALARM = 20;
	Boolean hasAlarm;	// Field 21
	// The alarm time is given in UTC on January 1/2, 1971*
	// if the alarm is set.  Otherwise the time is -1 (1s before 00:00 UTC).
	static final int FIELD_ALARM_TIME = 21;
	// Unset alarm times have the value -1
	Long alarmTime; 	// Field 22
	static final int FIELD_ALARM_DAYS_IN_ADVANCE = 22;
	Integer alarmDaysInAdvance;	// Field 23
	// Field 24 is never anything but Dec 31, 2031 in my To Do list
	// The repeat interval is a variable-length field!
	static final int FIELD_REPEAT = 24;
	RepeatEvent repeat;
	/** Store any unknown fields we encounter */
	Object[] unknownFields;

	@Override
	public String toString() {
	    StringBuilder sb = new StringBuilder("ToDoEntry[");
	    if (ID != null)
		sb.append(String.format("ID=%8d,", ID));
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
		sb.append(String.format("position=%5d,", position));
	    if (categoryIndex != null)
		sb.append(String.format("categoryIndex=%2d,", categoryIndex));
	    if (isPrivate != null)
		sb.append(isPrivate ? "priv" : "publ").append(',');
	    if (description != null)
		sb.append("description=\"").append(description
			.replace("\\", "\\\\")
			.replace("\r", "\\r")
			.replace("\n", "\\n")
			).append("\",");
	    final SimpleDateFormat sdf =
		new SimpleDateFormat("EEE, MMM d, yyyy HH:mm zzz");
	    if (dueDate != null) {
		Date due = new Date(dueDate * 1000);
		sb.append("dueDate=\"").append(sdf.format(due)).append("\",");
	    }
	    if (completed != null)
		sb.append("completed=").append(completed
			? "yes" : " no").append(',');
	    if (priority != null)
		sb.append("priority=").append(priority).append(',');
	    if (note != null)
		sb.append("note=\"").append(note).append("\",");
	    if (completionDate != null)
		sb.append("completionDate=\"").append(sdf.format(
			new Date(completionDate * 1000))).append("\",");
	    if (hasAlarm != null) {
		sb.append("hasAlarm=").append(hasAlarm).append(',');
		sb.append("alarmTime=\"").append(sdf.format(
			new Date(alarmTime * 1000))).append("\",");
		sb.append("alarmDaysInAdvance=").append(alarmDaysInAdvance).append(',');
	    }
	    if (repeat != null) {
		sb.append(repeat);
		if (repeatAfterCompleted)
		    sb.append("(after last completed)");
		sb.append(',');
	    }
	    sb.append("\n\t");
	    for (int j = 0; j < unknownFields.length; j++) {
		switch (j) {
		case FIELD_ID:
		case FIELD_STATUS:
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
			sb.append(String.format("unknown%02d=", j + 1));
			if ((unknownFields[j] instanceof Number) ||
				(unknownFields[j] instanceof Boolean))
			    sb.append(unknownFields[j]).append(',');
			else
			    sb.append('"').append(unknownFields[j]).append("\",");
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

	/** @return a String representation of this repeat event */
	@Override
	public String toString() {
	    StringBuilder sb = new StringBuilder("RepeatEvent[");
	    sb.append(String.format("tag=%04x,", tag));
	    if (typeName != null)
		sb.append("typeName=\"").append(typeName).append("\",");
	    sb.append("type=");
	    switch (type) {
	    case TYPE_REPEAT_BY_DAY: sb.append("daily"); break;
	    case TYPE_REPEAT_BY_WEEK: sb.append("weekly"); break;
	    case TYPE_REPEAT_BY_MONTH_DATE: sb.append("monthly(date)"); break;
	    case TYPE_REPEAT_BY_MONTH_DAY: sb.append("monthly(day)"); break;
	    case TYPE_REPEAT_BY_YEAR: sb.append("yearly"); break;
	    default: sb.append(type); break;
	    }
	    sb.append(",interval=").append(interval).append(',');
	    final SimpleDateFormat sdf = new SimpleDateFormat("EEE, MMM d, yyyy");
	    sb.append("repeatUntil=\"").append(sdf.format(
		    new Date(repeatUntil * 1000))).append('"');
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

    public class ImportBinder extends Binder {
	PalmImporterService getService() {
	    Log.d(LOG_TAG, "ImportBinder.getService()");
	    return PalmImporterService.this;
	}
    }

    private ImportBinder binder = new ImportBinder();

    /** Create the importer service with a named worker thread */
    public PalmImporterService() {
	super(PalmImporterService.class.getSimpleName());
	Log.d(LOG_TAG, "created");
	// If we die in the middle of an import, restart the request.
	setIntentRedelivery(true);
    }

    /** The current mode of operation */
    public enum OpMode {
	READING, CATEGORIES, ITEMS
    };
    private OpMode currentMode = OpMode.READING;

    /** The total number of entries to be imported */
    private int totalCount = 0;

    /** The current number of entries imported */
    private int importCount = 0;

    /**
     * For the import binder:
     * @return the stage of import we're working on
     */
    @Override
    public String getCurrentMode() {
	switch (currentMode) {
	case READING:
	    return getString(R.string.ProgressMessageImportReading);
	case CATEGORIES:
	    return getString(R.string.ProgressMessageImportCategories);
	case ITEMS:
	    return getString(R.string.ProgressMessageImportItems);
	default:
	    return "";
	}
    }

    /**
     * For the import binder:
     * @return the total number of items in the current stage
     */
    @Override
    public int getMaxCount() {	
	return totalCount;
    }

    /**
     * For the import binder:
     * @return the number of items in the current stage we've imported so far
     */
    @Override
    public int getChangedCount() {
	return importCount;
    }

    /** Called when an activity requests an import */
    protected void onHandleIntent(Intent intent) {
	// Get the location of the todo.dat file
	dataFile = new File(intent.getStringExtra(PALM_DATA_FILENAME));
	// Get the import type
	ImportType importType = (ImportType)
		intent.getSerializableExtra(PALM_IMPORT_TYPE);

	// Start the import
	try {
	    currentMode = OpMode.READING;
	    readDataFile();
	    if (size() == 0) {
		Toast.makeText(this, R.string.ErrorNoRecordsImported,
			Toast.LENGTH_LONG);
		return;
	    }
	    mergeToDos(importType);
	} catch (IOException iox) {
	    Log.e(LOG_TAG, "Unable to read " + dataFile.getAbsolutePath(), iox);
	    Toast.makeText(this, iox.getMessage(), Toast.LENGTH_LONG);
	} catch (SQLException sqlx) {
	    Log.e(LOG_TAG, "Error importing To Do items", sqlx);
	    Toast.makeText(this, sqlx.getMessage(), Toast.LENGTH_LONG);
	}
    }

    /* Obsoleted: Initialize the importer for a given context
    public PalmImporterService (Context context, Uri todoUri) {
	dataFile = new File(Environment.getExternalStorageDirectory(),
		"todo.dat");
    } */

    /** Set the location of the todo.dat file
     * @deprecated use the Intent instead */
    public void setDataFile(File filePath) {
	if (filePath == null)
	    throw new NullPointerException();
	dataFile = filePath;
    }

    /**
     * @return the number of items read from the Palm database,
     * or 0 if no items have (successfully) bean read.
     */
    public int size() {
	if (dataToDos == null)
	    return 0;
	else
	    return dataToDos.length;
    }

    /**
     * Read the data file.  The file location must
     * have been previously set with {@link #setDataFile(String)}.
     *
     * @return the number of records in the database
     * if the data file is readable and valid.
     * @throws FileNotFoundException if the file does not exist.
     * @throws SecurityException if the file is not readable.
     * @throws StreamCorruptedException if the file
     * is not a valid To Do data file.
     */
    public int readDataFile() throws IOException, SecurityException {
	Log.d(LOG_TAG, ".readDataFile: reading " + dataFile.getCanonicalPath());
	// Clear up some data just in case we're called more than once
	hasReadPalmDB = false;
	dataCategories = null;
	dataFieldTypes = null;
	dataToDos = null;
	totalCount = 0;
	importCount = 0;

	InputStream stream = new BufferedInputStream(
		new FileInputStream(dataFile));

	// Start with the metadata
	int magic = readInteger(stream);
	switch (magic) {
	default:
	    throw new StreamCorruptedException(String.format(
		    "Magic file header mismatch: expected %08X, got %08X",
		    MAGIC, magic));
	case MAGIC:
	    // This file has some additional headers that need reading first
	    String palmTag = readString(stream);
	    Log.d(LOG_TAG, ".readDataFile: Data file identifier = " + palmTag);
	    magic = readInteger(stream);
	    Log.d(LOG_TAG, String.format(".readDataFile: revision %c%c%c%c",
		    magic & 0xff, (magic >> 8) & 0xff,
		    (magic >> 16) & 0xff, (magic >> 24), 0xff));
	    magic = readInteger(stream);
	    if (magic != TD20_MAGIC)
		throw new StreamCorruptedException(String.format(
			"Magic file header mismatch: expected %08X, got %08X",
			TD20_MAGIC, magic));
	case TD20_MAGIC:
	    // There are a bunch of unknown bytes here.  Mostly zero.
	    skipZeroes(stream, 12);
	    readInteger(stream);	// This is not zero.  It's 0x1165.
	    skipZeroes(stream, 8);
	    dataFileName = readString(stream);
	    // There are an odd number of zero bytes following
	    skipZeroes(stream, 43);
	    readInteger(stream);	// This looks like a category ID,
					// but we're not there yet, and it's
					// not greater than the highest ID.
	    break;

	case TD10_MAGIC:
	    dataFileName = readString(stream);
	    Log.d(LOG_TAG, ".readDataFile: Saved file name = " + dataFileName);
	    // Skip the "custom show header" (?)
	    String showHeader = readString(stream);
	    Log.d(LOG_TAG, ".readDataFile: skipping show header \""
		    + showHeader + "\"");
	    readInteger(stream);	// dataNextFreeCatID
	    break;
	}

	// Now read in the category list
	int catCount = readInteger(stream);
	Log.d(LOG_TAG, ".readDataFile: " + catCount + " categories");
	if (catCount >= 256)
	    throw new StreamCorruptedException(
		    "Suspect category count (" + catCount + ")");
	totalCount = catCount * 2;
	importCount = 0;
	dataCategories = new CategoryEntry[catCount];
	int i;
	categoryMap = new HashMap<Integer,CategoryEntry>();
	// There is an implicit category entry for Unfiled
	CategoryEntry unfiled = new CategoryEntry();
	unfiled.ID = (int) ToDoCategory.UNFILED;
	unfiled.longName = "Unfiled";
	unfiled.shortName = "Unfiled";
	unfiled.newID = ToDoCategory.UNFILED;
	categoryMap.put(unfiled.ID, unfiled);
	for (i = 0; i < catCount; i++) {
	    dataCategories[i] = readCategoryEntry(stream);
	    if (categoryMap.containsKey(dataCategories[i].index))
		throw new StreamCorruptedException(
			"Duplicate category index " + dataCategories[i].index);
	    categoryMap.put(dataCategories[i].index, dataCategories[i]);
	    importCount = i + 1;
	}

	// Read in more metadata
	dataResourceID = readInteger(stream);
	Log.d(LOG_TAG, ".readDataFile: resource ID = " + dataResourceID);
	dataFieldsPerEntry = readInteger(stream);
	Log.d(LOG_TAG, ".readDataFile: " + dataFieldsPerEntry + " fields per entry");
	dataRecordIDPosition = readInteger(stream);
	dataRecordStatusPosition = readInteger(stream);
	dataRecordPlacementPosition = readInteger(stream);
	if ((dataRecordIDPosition >= dataFieldsPerEntry) ||
		(dataRecordStatusPosition >= dataFieldsPerEntry) ||
		(dataRecordPlacementPosition >= dataFieldsPerEntry))
	    throw new StreamCorruptedException(String.format(
		    "Invalid field position: ID[%d], Status[%d],"
		    + " Placement[%d], total fields = %d",
		    dataRecordIDPosition, dataRecordStatusPosition,
		    dataRecordPlacementPosition, dataFieldsPerEntry));
	int fieldCount = readShort(stream);
	if (fieldCount != dataFieldsPerEntry)
	    throw new StreamCorruptedException(String.format(
		    "Mismatched field count: was %d, now %d",
		    dataFieldsPerEntry, fieldCount));
	if ((fieldCount < 9) || (fieldCount >= 41))
	    throw new StreamCorruptedException(
		    "Suspect field count (" + fieldCount + ")");
	dataFieldTypes = new int[fieldCount];
	for (i = 0; i < fieldCount; i++) {
	    dataFieldTypes[i] = readShort(stream);
	    switch (dataFieldTypes[i]) {
	    default:
		throw new StreamCorruptedException(
			"Unknown field data type: " + dataFieldTypes[i]);
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
	    }
	    if ((i < ToDoEntry.expectedFieldTypes.length) &&
		    (dataFieldTypes[i] != ToDoEntry.expectedFieldTypes[i]))
		throw new StreamCorruptedException(String.format(
			"Field type mismatch in header: expected %d, found %d",
			ToDoEntry.expectedFieldTypes[i], dataFieldTypes[i]));
	}
	Log.d(LOG_TAG, ".readDataFile: field list = "
		+ Arrays.toString(dataFieldTypes));

	// Finally, we get to the actual To Do items!
	int numEntries = readInteger(stream);
	if (numEntries % fieldCount != 0)
	    throw new StreamCorruptedException(String.format(
		    "Number of fields in the database %u is not evenly"
		    + " divisible by the number of fields per entry %d",
		    numEntries, fieldCount));
	numEntries /= fieldCount;
	if (numEntries >= 100000)
	    throw new StreamCorruptedException(
		    "Suspect record count (" + numEntries + ")");
	dataToDos = new ToDoEntry[(int) numEntries];
	totalCount = 2 * (catCount + (int) numEntries);
	for (i = 0; i < dataToDos.length; i++) {
	    dataToDos[i] = readToDoEntry(stream);
	    // Log.d(LOG_TAG, ".readDataFile: Entry #" + i + ": "
	    //	    + dataToDos[i].toString());
	    importCount = catCount + i + 1;
	}

	if (stream.available() > 0)
	    Log.w(LOG_TAG, ".readDataFile: excess data at end of stream (at least"
		    + stream.available() + " bytes)");
	stream.close();
	hasReadPalmDB = true;
	return dataToDos.length;
    }

    /**
     * Read a single category entry from the given file.
     * @return the entry.
     * @throws StreamCorruptedException
     */
    CategoryEntry readCategoryEntry(InputStream stream) throws IOException {
	CategoryEntry entry = new CategoryEntry();
	entry.index = readInteger(stream);
	entry.ID = readInteger(stream);
	entry.dirty = readInteger(stream);
	entry.longName = readString(stream);
	entry.shortName = readString(stream);
	entry.newID = entry.ID;
	return entry;
    }

    /**
     * Read a single To-Do entry from the given file.
     * @return the entry.
     * @throws StreamCorruptedException
     */
    ToDoEntry readToDoEntry(InputStream stream) throws IOException {
	ToDoEntry entry = new ToDoEntry();
	entry.unknownFields = new Object[dataFieldsPerEntry];
	for (int j = 0; j < dataFieldsPerEntry; j++) {
	    int fieldType = readInteger(stream);
	    if ((fieldType != dataFieldTypes[j]) &&
		    (dataFieldTypes[j] < TYPE_UNKNOWN40))
		throw new StreamCorruptedException(String.format(
			"Field type #%d mismatch in record: expected %d, found %d",
			j + 1, dataFieldTypes[j], fieldType));
	    // We already checked the expected field types in the header,
	    // so for the known fields just fill in the structure members.
	    switch (j) {
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
	    case ToDoEntry.FIELD_POSITION:
		entry.position = readInteger(stream);
		entry.unknownFields[j] = entry.position;
		break;
	    case ToDoEntry.FIELD_CATEGORY:
		entry.categoryIndex = readInteger(stream);
		if (!categoryMap.containsKey(entry.categoryIndex))
		    throw new StreamCorruptedException(String.format(
			    "Record %d has an undefined category index %d",
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
     * @throws StreamCorruptedException
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
		throw new StreamCorruptedException(
			"Error reading repeat event; expected 1 after tag, got "
			+ dummy);
	    event.typeName = readString(stream, readShort(stream));
	    if (!event.typeName.equals(RepeatEvent.NAME_REPEAT_BY_DAY) &&
		    !event.typeName.equals(RepeatEvent.NAME_REPEAT_BY_WEEK) &&
		    !event.typeName.equals(RepeatEvent.NAME_REPEAT_BY_MONTH_DATE) &&
		    !event.typeName.equals(RepeatEvent.NAME_REPEAT_BY_MONTH_DAY) &&
		    !event.typeName.equals(RepeatEvent.NAME_REPEAT_BY_YEAR))
		throw new StreamCorruptedException(
			"Unhandled repeat type name \"" + event.typeName + "\"");
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
		throw new StreamCorruptedException(
			"Unfamiliar value for repeat weekly event: " + dummy);
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
	    value |= (data[i] & 0xff) << (i * 8);
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
	    Log.e(LOG_TAG, "Error interpreting string value using Cp1252 encoding", uex);
	    throw new StreamCorruptedException(uex.getMessage());
	}
    }

    /**
     * Return the category list from the Palm database.
     * Primarily useful for testing.
     */
    public CategoryEntry[] getCategories() {
	return dataCategories;
    }

    /**
     * Merge the category list from the Palm database
     * with the Android database.
     *
     * @throws IllegalStateException if the Palm database has not been read.
     */
    void mergeCategories(ImportType importType) {
	if (!hasReadPalmDB)
	    throw new IllegalStateException(
		    "The To Do database file has not been read");

	// Need to read in the current list of categories
	// regardless of the import type
	Map<Long,String> categoryIDMap = new HashMap<Long,String>();
	Map<String,Long> categoryNameMap = new HashMap<String,Long>();
	ContentResolver resolver = getContentResolver();
	Cursor c = resolver.query(ToDoCategory.CONTENT_URI, new String[] {
		ToDoCategory._ID, ToDoCategory.NAME }, null, null, null);
	while (c.moveToNext()) {
	    long id = c.getLong(c.getColumnIndex(ToDoCategory._ID));
	    String name = c.getString(c.getColumnIndex(ToDoCategory.NAME));
	    categoryIDMap.put(id, name);
	    categoryNameMap.put(name, id);
	}
	c.close();

	int i;
	ContentValues values = new ContentValues();
	switch (importType) {
	case CLEAN:
	    Log.d(LOG_TAG, ".mergeCategories: removing all existing categories");
	    resolver.delete(ToDoCategory.CONTENT_URI, null, null);
	    for (i = 0; i < dataCategories.length; i++) {
		Log.d(LOG_TAG, ".mergeCategories: adding \""
			+ dataCategories[i].longName + "\"");
		values.put(ToDoCategory._ID, dataCategories[i].newID);
		values.put(ToDoCategory.NAME, dataCategories[i].longName);
		resolver.insert(ToDoCategory.CONTENT_URI, values);
		importCount = dataCategories.length + dataToDos.length + i + 1;
	    }
	    break;
	case OVERWRITE:
	    for (i = 0; i < dataCategories.length; i++) {
		if (categoryIDMap.containsKey(dataCategories[i].newID)) {
		    if (!categoryIDMap.get(dataCategories[i].newID
			    ).equals(dataCategories[i].longName)) {
			Log.d(LOG_TAG, ".mergeCategories: replacing \""
				+ categoryIDMap.get(dataCategories[i].newID)
				+ "\" with \"" + dataCategories[i].longName + "\"");
			values.remove(ToDoCategory._ID);
			values.put(ToDoCategory.NAME, dataCategories[i].longName);
			resolver.update(ContentUris.withAppendedId(
				ToDoCategory.CONTENT_URI, dataCategories[i].ID),
				values, null, null);
		    }
		} else {
		    Log.d(LOG_TAG, ".mergeCategories: adding \""
			    + dataCategories[i].longName + "\"");
		    values.put(ToDoCategory._ID, dataCategories[i].newID);
		    values.put(ToDoCategory.NAME, dataCategories[i].longName);
		    resolver.insert(ToDoCategory.CONTENT_URI, values);
		}
		importCount = dataCategories.length + dataToDos.length + i + 1;
	    }
	    break;
	case MERGE:
	    // Since we can't have duplicate category names,
	    // adding is the same as merging.
	case ADD:
	    values.remove(ToDoCategory._ID);
	    for (i = 0; i < dataCategories.length; i++) {
		if (categoryNameMap.containsKey(dataCategories[i].longName)) {
		    dataCategories[i].newID =
			categoryNameMap.get(dataCategories[i].longName);
		    if (dataCategories[i].newID != dataCategories[i].ID)
			Log.d(LOG_TAG, ".mergeCategories: changing the ID of \""
				+ dataCategories[i].longName + "\" from "
				+ dataCategories[i].ID + " to "
				+ dataCategories[i].newID);
		} else {
		    Log.d(LOG_TAG, ".mergeCategories: adding \""
			    + dataCategories[i].longName + "\"");
		    values.put(ToDoCategory.NAME, dataCategories[i].longName);
		    Uri newItem = resolver.insert(ToDoCategory.CONTENT_URI, values);
		    dataCategories[i].newID = Long.parseLong(
			    newItem.getPathSegments().get(1));
		}
		importCount = dataCategories.length + dataToDos.length + i + 1;
	    }
	    break;
	}
    }

    /**
     * Return the To Do item list from the Palm database.
     * Primarily useful for testing.
     */
    public ToDoEntry[] getToDos() {
	return dataToDos;
    }

    /**
     * Merge the To Do items from the Palm database
     * with the Android database.
     *
     * @throws IllegalStateException if the Palm database has not been read.
     */
    public void mergeToDos(ImportType importType) {
	if (!hasReadPalmDB)
	    throw new IllegalStateException(
		    "The To Do database file has not been read");

	ContentResolver resolver = getContentResolver();
	StringEncryption newCrypt = StringEncryption.holdGlobalEncryption();

	try {
	    if (importType == ImportType.CLEAN) {
		// Wipe them all out
		Log.d(LOG_TAG, ".mergeToDos: removing all existing To Do items");
		resolver.delete(ToDoItem.CONTENT_URI, null, null);
	    }

	    // Merge the categories first
	    currentMode = OpMode.CATEGORIES;
	    mergeCategories(importType);
	    currentMode = OpMode.ITEMS;

	    final String[] EXISTING_ITEM_PROJECTION = {
		    ToDoItem._ID, ToDoItem.CATEGORY_NAME, ToDoItem.DESCRIPTION,
		    ToDoItem.CREATE_TIME };

	    // Find the highest available record ID
	    Cursor c = resolver.query(ToDoItem.CONTENT_URI,
		    EXISTING_ITEM_PROJECTION, null, null,
		    // The table prefix is required here because
		    // the provider joins the to-do table with the category table.
		    ToDoProvider.TODO_TABLE_NAME + "." + ToDoItem._ID + " DESC");
	    if (c.moveToFirst()) {
		long nextID = c.getLong(c.getColumnIndex(ToDoItem._ID));
		if (nextID >= nextFreeRecordID)
		    nextFreeRecordID = nextID + 1;
	    }
	    c.close();

	    ContentValues values = new ContentValues();
	    ContentValues existingRecord = new ContentValues();
	    for (int i = 0; i < dataToDos.length; i++) {
		values.clear();
		// Set the ID and creation time of the new record
		if (importType != ImportType.CLEAN) {
		    /*
		     * Check whether a record with the same ID already exists.
		     * for some import types we don't actually care what data
		     * it contains; for others, we check these fields.
		     */
		    existingRecord.clear();
		    c = resolver.query(ContentUris.withAppendedId(ToDoItem.CONTENT_URI,
			    dataToDos[i].ID), EXISTING_ITEM_PROJECTION,
			    null, null, null);
		    if (c.moveToFirst()) {
			existingRecord.put(ToDoItem.DESCRIPTION,
				c.getString(c.getColumnIndex(ToDoItem.DESCRIPTION)));
			existingRecord.put(ToDoItem.CATEGORY_NAME,
				c.getString(c.getColumnIndex(ToDoItem.CATEGORY_NAME)));
		    }
		    c.close();
		}
		values.put(ToDoItem.CREATE_TIME, System.currentTimeMillis());
		switch (importType) {
		case OVERWRITE:
		    if (existingRecord.size() > 0) {
			// Debug individual items only if the number is small
			if (dataToDos.length < 64) {
			    Log.d(LOG_TAG, String.format(
				    ".mergeToDos: replacing existing record"
				    + " %d [%s] \"%s\" with [%s] \"%s\"",
				    dataToDos[i].ID,
				    existingRecord.getAsString(ToDoItem.CATEGORY_NAME),
				    existingRecord.getAsString(ToDoItem.DESCRIPTION),
				    categoryMap.get(dataToDos[i].categoryIndex).longName,
				    dataToDos[i].description));
			}
			resolver.delete(ContentUris.withAppendedId(ToDoItem.CONTENT_URI,
				dataToDos[i].ID), null, null);
		    }
		    // Fall through
		case CLEAN:
		    values.put(ToDoItem._ID, dataToDos[i].ID);
		    break;
		case MERGE:
		    if ((existingRecord.size() > 0) &&
			    existingRecord.getAsString(ToDoItem.CATEGORY_NAME).equals(
				    categoryMap.get(dataToDos[i].categoryIndex).longName) &&
				    existingRecord.getAsString(ToDoItem.DESCRIPTION).equals(
					    dataToDos[i].description)) {
			if (dataToDos.length < 64) {
			    Log.d(LOG_TAG, String.format(
				    ".mergeToDos: updating record %d [%s] \"%s\"",
				    dataToDos[i].ID,
				    existingRecord.getAsString(ToDoItem.CATEGORY_NAME),
				    existingRecord.getAsString(ToDoItem.DESCRIPTION)));
			}
			values.put(ToDoItem.CREATE_TIME,
				existingRecord.getAsLong(ToDoItem.CREATE_TIME));
			resolver.delete(ContentUris.withAppendedId(ToDoItem.CONTENT_URI,
				dataToDos[i].ID), null, null);
			values.put(ToDoItem._ID, dataToDos[i].ID);
		    } else {
			if (dataToDos.length < 64) {
			    Log.d(LOG_TAG, String.format(
				    ".mergeToDos: changing ID of record [%s] \"%s\" from %d to %d",
				    categoryMap.get(dataToDos[i].categoryIndex).longName,
				    dataToDos[i].description, dataToDos[i].ID,
				    nextFreeRecordID));
			}
			values.put(ToDoItem._ID, nextFreeRecordID++);
		    }
		    break;
		case ADD:
		    if (existingRecord.size() == 0)
			values.put(ToDoItem._ID, dataToDos[i].ID);
		    else {
			if (dataToDos.length < 64) {
			    Log.d(LOG_TAG, String.format(
				    ".mergeToDos: changing ID of record [%s] \"%s\" from %d to %d",
				    categoryMap.get(dataToDos[i].categoryIndex).longName,
				    dataToDos[i].description, dataToDos[i].ID,
				    nextFreeRecordID));
			}
			values.put(ToDoItem._ID, nextFreeRecordID++);
		    }
		    break;
		}

		// Set all of the other values
		int privacy = dataToDos[i].isPrivate ?
			(newCrypt.hasKey() ? 2 : 1) : 0;
		values.put(ToDoItem.DESCRIPTION,
			dataToDos[i].description.replace("\r", ""));
		if ((dataToDos[i].note != null) &&
			(dataToDos[i].note.length() > 0))
		    values.put(ToDoItem.NOTE, dataToDos[i].note.replace("\r", ""));
		if (privacy == 2) {
		    try {
			byte[] encryptedDescription = newCrypt.encrypt(
				values.getAsString(ToDoItem.DESCRIPTION));
			if (values.containsKey(ToDoItem.NOTE)) {
			    byte[] encryptedNote = newCrypt.encrypt(
				    values.getAsString(ToDoItem.NOTE));
			    values.put(ToDoItem.NOTE, encryptedNote);
			}
			values.put(ToDoItem.DESCRIPTION, encryptedDescription);
		    } catch (GeneralSecurityException gsx) {
			privacy = 1;
		    }
		} else {
		}
		values.put(ToDoItem.MOD_TIME, System.currentTimeMillis());
		if ((dataToDos[i].dueDate < 0) ||
			(dataToDos[i].dueDate > ToDoEntry.MAX_DATE))
		    values.putNull(ToDoItem.DUE_TIME);
		else
		    // Add 24 hours - 1 second to the due date so that
		    // it doesn't show as overdue until the end of the day.
		    values.put(ToDoItem.DUE_TIME,
			    (dataToDos[i].dueDate + 86399) * 1000);
		if ((dataToDos[i].completionDate < 0) ||
			(dataToDos[i].completionDate > ToDoEntry.MAX_DATE))
		    values.putNull(ToDoItem.COMPLETED_TIME);
		else
		    values.put(ToDoItem.COMPLETED_TIME,
			    dataToDos[i].completionDate * 1000);
		values.put(ToDoItem.CHECKED, dataToDos[i].completed ? 1 : 0);
		values.put(ToDoItem.PRIORITY, dataToDos[i].priority);
		values.put(ToDoItem.PRIVATE, privacy);
		values.put(ToDoItem.CATEGORY_ID,
			categoryMap.get(dataToDos[i].categoryIndex).newID);
		if (dataToDos[i].hasAlarm) {
		    values.put(ToDoItem.ALARM_DAYS_EARLIER,
			    dataToDos[i].alarmDaysInAdvance);
		    Calendar alarmShift = Calendar.getInstance();
		    alarmShift.setTimeInMillis(dataToDos[i].alarmTime * 1000);
		    int secondsAfterMidnight = (
			    alarmShift.get(Calendar.HOUR_OF_DAY) * 3600
			    + alarmShift.get(Calendar.MINUTE) * 60);
		    values.put(ToDoItem.ALARM_TIME, secondsAfterMidnight * 1000);
		} else {
		    values.putNull(ToDoItem.ALARM_DAYS_EARLIER);
		    values.putNull(ToDoItem.ALARM_TIME);
		}
		if (dataToDos[i].repeat == null)
		    values.put(ToDoItem.REPEAT_INTERVAL, ToDoItem.REPEAT_NONE);
		else {
		    switch (dataToDos[i].repeat.type) {
		    case RepeatEvent.TYPE_REPEAT_BY_DAY:
			values.put(ToDoItem.REPEAT_INTERVAL,
				dataToDos[i].repeatAfterCompleted
				? ToDoItem.REPEAT_DAY_AFTER
					: ToDoItem.REPEAT_DAILY);
			values.putNull(ToDoItem.REPEAT_WEEK_DAYS);
			values.putNull(ToDoItem.REPEAT_DAY);
			values.putNull(ToDoItem.REPEAT_WEEK);
			values.putNull(ToDoItem.REPEAT_MONTH);
			break;
		    case RepeatEvent.TYPE_REPEAT_BY_WEEK:
			if (dataToDos[i].repeatAfterCompleted) {
			    values.put(ToDoItem.REPEAT_INTERVAL,
				    ToDoItem.REPEAT_WEEK_AFTER);
			    values.putNull(ToDoItem.REPEAT_WEEK_DAYS);
			} else {
			    values.put(ToDoItem.REPEAT_INTERVAL,
				    ToDoItem.REPEAT_WEEKLY);
			    values.put(ToDoItem.REPEAT_WEEK_DAYS,
				    dataToDos[i].repeat.dayOfWeekBitmap);
			}
			values.putNull(ToDoItem.REPEAT_DAY);
			values.putNull(ToDoItem.REPEAT_WEEK);
			values.putNull(ToDoItem.REPEAT_MONTH);
			break;
		    case RepeatEvent.TYPE_REPEAT_BY_MONTH_DAY:
			// These are never "after last completed"
			values.put(ToDoItem.REPEAT_INTERVAL,
				ToDoItem.REPEAT_MONTHLY_ON_DAY);
			values.putNull(ToDoItem.REPEAT_WEEK_DAYS);
			values.put(ToDoItem.REPEAT_DAY,
				dataToDos[i].repeat.dayOfWeek);
			values.put(ToDoItem.REPEAT_WEEK,
				dataToDos[i].repeat.weekOfMonth);
			break;
		    case RepeatEvent.TYPE_REPEAT_BY_MONTH_DATE:
			values.put(ToDoItem.REPEAT_INTERVAL,
				dataToDos[i].repeatAfterCompleted
				? ToDoItem.REPEAT_MONTH_AFTER
					: ToDoItem.REPEAT_MONTHLY_ON_DATE);
			values.put(ToDoItem.REPEAT_WEEK_DAYS,
				ToDoItem.REPEAT_ALL_WEEK);
			values.put(ToDoItem.REPEAT_DAY,
				dataToDos[i].repeat.dateOfMonth);
			values.putNull(ToDoItem.REPEAT_WEEK);
			values.putNull(ToDoItem.REPEAT_MONTH);
			break;
		    case RepeatEvent.TYPE_REPEAT_BY_YEAR:
			values.put(ToDoItem.REPEAT_INTERVAL,
				dataToDos[i].repeatAfterCompleted
				? ToDoItem.REPEAT_YEAR_AFTER
					: ToDoItem.REPEAT_YEARLY_ON_DATE);
			values.put(ToDoItem.REPEAT_WEEK_DAYS,
				ToDoItem.REPEAT_ALL_WEEK);
			values.put(ToDoItem.REPEAT_DAY,
				dataToDos[i].repeat.dateOfMonth);
			values.putNull(ToDoItem.REPEAT_WEEK);
			values.put(ToDoItem.REPEAT_MONTH,
				dataToDos[i].repeat.monthOfYear);
			break;
		    }
		    values.putNull(ToDoItem.REPEAT_DAY2);
		    values.putNull(ToDoItem.REPEAT_WEEK2);
		    values.put(ToDoItem.REPEAT_INCREMENT,
			    dataToDos[i].repeat.interval);
		    if ((dataToDos[i].repeat.repeatUntil < 0) ||
			    (dataToDos[i].repeat.repeatUntil > ToDoEntry.MAX_DATE))
			values.putNull(ToDoItem.REPEAT_END);
		    else
			values.put(ToDoItem.REPEAT_END,
				dataToDos[i].repeat.repeatUntil * 1000);
		}

		if (importType != ImportType.TEST)
		    resolver.insert(ToDoItem.CONTENT_URI, values);

		importCount = 2 * dataCategories.length + dataToDos.length + i + 1;
	    }
	} finally {
	    StringEncryption.releaseGlobalEncryption();
	}
    }

    /**
     * Called when the service is created.
     */
    @Override
    public void onCreate() {
        Log.d(LOG_TAG, ".onCreate");
        super.onCreate();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(LOG_TAG, ".onBind");
	return binder;
    }
}
