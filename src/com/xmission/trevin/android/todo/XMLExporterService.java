/*
 * $Id: XMLExporterService.java,v 1.3 2014/03/29 19:49:14 trevin Exp trevin $
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
 * $Log: XMLExporterService.java,v $
 * Revision 1.3  2014/03/29 19:49:14  trevin
 * Don’t export the password or private records when the
 *   “export private” option is un-checked.
 *
 * Revision 1.2  2014/03/23 21:43:56  trevin
 * Fixed the file format from DOS to unix.
 *
 * Revision 1.1  2014/03/02 22:21:57  trevin
 * Initial revision
 *
 */
package com.xmission.trevin.android.todo;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.*;

import android.app.IntentService;
import android.content.*;
import android.database.Cursor;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import com.xmission.trevin.android.todo.ToDo.*;

/**
 * This class exports the To Do list to an XML file on external storage.
 */
public class XMLExporterService extends IntentService
	implements ProgressReportingService {

    public static final String LOG_TAG = "XMLExporterService";

    /**
     * The name of the Intent extra data that holds
     * the location of the todo.xml file
     */
    public static final String XML_DATA_FILENAME =
	"com.xmission.trevin.android.todo.XMLDataFileName";

    /**
     * The name of the Intent extra that indicates whether to
     * export private records.
     */
    public static final String EXPORT_PRIVATE =
	"com.xmission.trevin.android.todo.XMLExportPrivate";

    /** The document element name */
    public static final String DOCUMENT_TAG = "ToDoApp";

    /** The preferences element name */
    public static final String PREFERENCES_TAG = "Preferences";

    /** The metadata element name */
    public static final String METADATA_TAG = "Metadata";

    /** The categories element name */
    public static final String CATEGORIES_TAG = "Categories";

    /** The to-do items element name */
    public static final String ITEMS_TAG = "ToDoList";

    /** The location of the todo.xml file */
    private File dataFile;

    /** The current mode of operation */
    public enum OpMode {
	SETTINGS, CATEGORIES, ITEMS
    };
    private OpMode currentMode = OpMode.SETTINGS;

    /** Whether private records should be exported */
    private boolean exportPrivate = true;

    /** The current number of entries exported */
    private int exportCount = 0;

    /** The total number of entries to be exported */
    private int totalCount = 0;

    public class ExportBinder extends Binder {
	XMLExporterService getService() {
	    Log.d(LOG_TAG, "ExportBinder.getService()");
	    return XMLExporterService.this;
	}
    }

    private ExportBinder binder = new ExportBinder();

    /** Create the exporter service with a named worker thread */
    public XMLExporterService() {
	super(XMLExporterService.class.getSimpleName());
	Log.d(LOG_TAG, "created");
	// If we die in the middle of an import, restart the request.
	setIntentRedelivery(true);
    }

    /** @return the current mode of operation */
    public String getCurrentMode() {
	switch (currentMode) {
	case SETTINGS:
	    return getString(R.string.ProgressMessageExportSettings);
	case CATEGORIES:
	    return getString(R.string.ProgressMessageExportCategories);
	case ITEMS:
	    return getString(R.string.ProgressMessageExportItems);
	default:
	    return "";
	}
    }

    /** @return the total number of entries to be changed */
    public int getMaxCount() { return totalCount; }

    /** @return the number of entries changed so far */
    public int getChangedCount() { return exportCount; }

    /** Format a Date for XML output */
    public final static SimpleDateFormat DATE_FORMAT =
	new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    static { DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC")); }

    /** Format a time for XML output (used by the alarm time) */
    public final static SimpleDateFormat TIME_FORMAT =
	new SimpleDateFormat("HH:mm:ss.SSS");
    static { TIME_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC")); }

    /** Called when an activity requests an export */
    @Override
    protected void onHandleIntent(Intent intent) {
	// Get the location of the todo.xml file
	dataFile = new File(intent.getStringExtra(XML_DATA_FILENAME));
	exportPrivate = intent.getBooleanExtra(EXPORT_PRIVATE, true);
	Log.d(LOG_TAG, ".onHandleIntent(\""
		+ dataFile.getAbsolutePath() + "\", " + exportPrivate + ")");
	exportCount = 0;
	totalCount = 0;

	try {
	    if (!dataFile.exists())
		dataFile.createNewFile();
	    PrintStream out = new PrintStream(
		    new FileOutputStream(dataFile, false));
	    out.println("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
	    out.println(String.format(
		    "<" + DOCUMENT_TAG + " db-version=\"%d\" exported=\"%s\">",
		    ToDoProvider.DATABASE_VERSION,
		    DATE_FORMAT.format(new Date())));
	    currentMode = OpMode.SETTINGS;
	    writePreferences(out);
	    writeMetadata(out);
	    currentMode = OpMode.CATEGORIES;
	    writeCategories(out);
	    currentMode = OpMode.ITEMS;
	    writeToDoItems(out);
	    out.println("</" + DOCUMENT_TAG + ">");
	    if (out.checkError()) {
		Toast.makeText(this, getString(R.string.ErrorExportFailed),
			Toast.LENGTH_LONG);
	    }
	    out.close();
	} catch (IOException iofx) {
	    Toast.makeText(this, iofx.getMessage(), Toast.LENGTH_LONG);
	}
    }

    private static final Pattern XML_RESERVED_CHARACTERS =
	Pattern.compile("[\"&'<>]");

    /** Escape a string for XML sequences */
    public static String escapeXML(String raw) {
	Matcher m = XML_RESERVED_CHARACTERS.matcher(raw);
	if (m.find()) {
	    String step1 = raw.replace("&", "&amp;");
	    String step2 = step1.replace("<", "&lt;");
	    String step3 = step2.replace(">", "&gt;");
	    String step4 = step3.replace("\"", "&quot;");
	    String step5 = step4.replace("'", "&apos;");
	    return step5;
	} else {
	    return raw;
	}
    }

    /** Write out the preferences section */
    protected void writePreferences(PrintStream out) {
	SharedPreferences prefs = this.getSharedPreferences(
		ToDoListActivity.TODO_PREFERENCES, MODE_PRIVATE);
	Map<String,?> prefMap = prefs.getAll();
	out.println("    <" + PREFERENCES_TAG + ">");
	for (String key : prefMap.keySet()) {
	    out.println(String.format("\t<%s>%s</%s>",
		    key, escapeXML(prefMap.get(key).toString()), key));
	}
	out.println("    </" + PREFERENCES_TAG + ">");
    }

    /** RFC 3548 sec. 4 */
    private static final char[] BASE64_CHARACTERS = {
	'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P',
	'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f',
	'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v',
	'w', 'x', 'y', 'z', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-', '_',
    };

    /** Convert a stream of bytes to Base64 */
    public static String encodeBase64(byte[] data) {
	StringBuilder sb = new StringBuilder();
	// Process bytes in groups of three
	int i;
	for (i = 0; i + 3 <= data.length; i += 3) {
	    // Insert line breaks every 64 characters
	    if ((i > 0) && (i % 48 == 0))
		sb.append(System.getProperty("line.separator", "\n"));
	    sb.append(BASE64_CHARACTERS[(data[i] >> 2) & 0x3f])
	    .append(BASE64_CHARACTERS[((data[i] & 3) << 4) + ((data[i+1] >> 4) & 0x0f)])
	    .append(BASE64_CHARACTERS[((data[i+1] & 0xf) << 2) + ((data[i+2] >> 6) & 3)])
	    .append(BASE64_CHARACTERS[data[i+2] & 0x3f]);
	}
	// Special handling for the last one or two bytes -- no padding
	if (i < data.length) {
	    sb.append(BASE64_CHARACTERS[(data[i] >> 2) & 0x3f]);
	    if (i + 1 < data.length) {
		sb.append(BASE64_CHARACTERS[((data[i] & 3) << 4) + ((data[i+1] >> 4) & 0x0f)]);
		sb.append(BASE64_CHARACTERS[(data[i+1] & 0xf) << 2]);
	    } else {
		sb.append(BASE64_CHARACTERS[(data[i] & 3) << 4]);
	    }
	}
	return sb.toString();
    }

    /** Write out the metadata */
    protected void writeMetadata(PrintStream out) {
	final String[] PROJECTION = {
		ToDoMetadata._ID,
		ToDoMetadata.NAME,
		ToDoMetadata.VALUE,
	};
	Cursor c = getContentResolver().query(ToDoMetadata.CONTENT_URI,
		PROJECTION, null, null, ToDoMetadata.NAME);
	try {
	    out.println("    <" + METADATA_TAG + ">");
	    while (c.moveToNext()) {
		String name = c.getString(c.getColumnIndex(ToDoMetadata.NAME));
		// Skip the password if we are not exporting private records
		if (StringEncryption.METADATA_PASSWORD_HASH[0].equals(name) &&
			!exportPrivate)
		    continue;
		int ival = c.getColumnIndex(ToDoMetadata.VALUE);
		out.print(String.format("\t<item id=\"%d\" name=\"%s\"",
			c.getLong(c.getColumnIndex(ToDoMetadata._ID)),
			escapeXML(name)));
		if (c.isNull(ival)) {
		    out.println("/>");
		} else {
		    out.println(String.format(">%s</item>",
			    encodeBase64(c.getBlob(ival))));
		}
	    }
	    out.println("    </" + METADATA_TAG + ">");
	} finally {
	    c.close();
	}
    }

    /** Write the category list */
    protected void writeCategories(PrintStream out) {
	final String[] PROJECTION = {
		ToDoCategory._ID,
		ToDoCategory.NAME,
	};
	Cursor c = getContentResolver().query(ToDoCategory.CONTENT_URI,
		PROJECTION, null, null, ToDoCategory.NAME);
	totalCount = c.getCount();
	exportCount = 0;
	try {
	    out.println("    <" + CATEGORIES_TAG + ">");
	    while (c.moveToNext()) {
		String name = c.getString(c.getColumnIndex(ToDoCategory.NAME));
		out.println(String.format("\t<category id=\"%d\">%s</category>",
			c.getLong(c.getColumnIndex(ToDoCategory._ID)),
			escapeXML(name)));
		exportCount++;
	    }
	    out.println("    </" + CATEGORIES_TAG + ">");
	} finally {
	    c.close();
	}
    }

    /** Write the To Do list */
    protected void writeToDoItems(PrintStream out) {
	final String[] PROJECTION = {
		ToDoItem._ID,
		ToDoItem.DESCRIPTION,
		ToDoItem.CREATE_TIME,
		ToDoItem.MOD_TIME,
		ToDoItem.DUE_TIME,
		ToDoItem.COMPLETED_TIME,
		ToDoItem.CHECKED,
		ToDoItem.PRIORITY,
		ToDoItem.PRIVATE,
		ToDoItem.CATEGORY_ID,
		ToDoItem.NOTE,
		ToDoItem.ALARM_DAYS_EARLIER,
		ToDoItem.ALARM_TIME,
		ToDoItem.REPEAT_INTERVAL,
		ToDoItem.REPEAT_INCREMENT,
		ToDoItem.REPEAT_WEEK_DAYS,
		ToDoItem.REPEAT_DAY,
		ToDoItem.REPEAT_DAY2,
		ToDoItem.REPEAT_WEEK,
		ToDoItem.REPEAT_WEEK2,
		ToDoItem.REPEAT_MONTH,
		ToDoItem.REPEAT_END,
		ToDoItem.HIDE_DAYS_EARLIER,
		ToDoItem.NOTIFICATION_TIME,
	};
	Cursor c = getContentResolver().query(ToDoItem.CONTENT_URI,
		PROJECTION, null, null,
		ToDoProvider.TODO_TABLE_NAME + "." + ToDoItem._ID);
	totalCount = c.getCount();
	exportCount = 0;

	try {
	    out.println("    <" + ITEMS_TAG + ">");
	    while (c.moveToNext()) {
		int privacy = c.getInt(c.getColumnIndex(ToDoItem.PRIVATE));
		if (!exportPrivate && (privacy > 0))
		    continue;
		boolean checked = c.getInt(c.getColumnIndex(
			ToDoItem.CHECKED)) != 0;
		out.print(String.format("\t<to-do id=\"%d\" checked=\"",
			c.getLong(c.getColumnIndex(ToDoItem._ID))));
		out.print(checked);
		out.print(String.format("\" category=\"%d\" priority=\"%d\"",
			c.getLong(c.getColumnIndex(ToDoItem.CATEGORY_ID)),
			c.getInt(c.getColumnIndex(ToDoItem.PRIORITY))));
		if (privacy != 0) {
		    out.print(" private=\"true\"");
		    if (privacy > 1) {
			out.print(String.format(" encryption=\"%d\"", privacy));
		    }
		}
		out.println(">");

		out.print("\t    <description>");
		int i = c.getColumnIndex(ToDoItem.DESCRIPTION);
		if (privacy < 2) {
		    String desc = c.getString(i);
		    out.print(escapeXML(desc));
		} else {
		    byte[] desc = c.getBlob(i);
		    out.print(encodeBase64(desc));
		}
		out.println("</description>");

		out.println(String.format("\t    <created time=\"%s\"/>",
			DATE_FORMAT.format(new Date(c.getLong(
				c.getColumnIndex(ToDoItem.CREATE_TIME))))));

		out.println(String.format("\t    <modified time=\"%s\"/>",
			DATE_FORMAT.format(new Date(c.getLong(
				c.getColumnIndex(ToDoItem.MOD_TIME))))));

		i = c.getColumnIndex(ToDoItem.DUE_TIME);
		if (!c.isNull(i)) {
		    out.println(String.format("\t    <due time=\"%s\">",
			    DATE_FORMAT.format(new Date(c.getLong(i)))));

		    i = c.getColumnIndex(ToDoItem.ALARM_DAYS_EARLIER);
		    if (!c.isNull(i)) {
			int j = c.getColumnIndex(ToDoItem.ALARM_TIME);
			out.println(String.format(
				"\t\t<alarm days-earlier=\"%d\" time=\"%d\"/>",
				c.getInt(i), c.getLong(j)));
		    }

		    i = c.getColumnIndex(ToDoItem.REPEAT_INTERVAL);
		    if (!c.isNull(i)) {
			out.print(String.format("\t\t<repeat interval=\"%d\"",
				c.getInt(i)));
			i = c.getColumnIndex(ToDoItem.REPEAT_INCREMENT);
			if (!c.isNull(i)) {
			    out.print(String.format(" increment=\"%d\"",
				    c.getInt(i)));
			}
			i = c.getColumnIndex(ToDoItem.REPEAT_WEEK_DAYS);
			if (!c.isNull(i)) {
			    out.print(String.format(" week-days=\"%s\"",
				    Integer.toBinaryString(c.getInt(i))));
			}
			i = c.getColumnIndex(ToDoItem.REPEAT_DAY);
			if (!c.isNull(i)) {
			    out.print(String.format(" day1=\"%d\"", c.getInt(i)));
			}
			i = c.getColumnIndex(ToDoItem.REPEAT_DAY2);
			if (!c.isNull(i)) {
			    out.print(String.format(" day2=\"%d\"", c.getInt(i)));
			}
			i = c.getColumnIndex(ToDoItem.REPEAT_WEEK);
			if (!c.isNull(i)) {
			    out.print(String.format(" week1=\"%d\"", c.getInt(i)));
			}
			i = c.getColumnIndex(ToDoItem.REPEAT_WEEK2);
			if (!c.isNull(i)) {
			    out.print(String.format(" week2=\"%d\"", c.getInt(i)));
			}
			i = c.getColumnIndex(ToDoItem.REPEAT_MONTH);
			if (!c.isNull(i)) {
			    out.print(String.format(" month=\"%d\"", c.getInt(i)));
			}
			i = c.getColumnIndex(ToDoItem.REPEAT_END);
			if (!c.isNull(i)) {
			    out.print(String.format(" end=\"%d\"", c.getLong(i)));
			}
			out.println("/>");
		    }

		    i = c.getColumnIndex(ToDoItem.HIDE_DAYS_EARLIER);
		    if (!c.isNull(i)) {
			out.println(String.format(
				"\t\t<hide days-earlier=\"%d\"/>",
				c.getInt(i)));
		    }

		    i = c.getColumnIndex(ToDoItem.NOTIFICATION_TIME);
		    if (!c.isNull(i)) {
			out.println(String.format(
				"\t\t<notification time=\"%s\"/>",
				DATE_FORMAT.format(new Date(c.getLong(i)))));
		    }

		    out.println("\t    </due>");
		}

		i = c.getColumnIndex(ToDoItem.NOTE);
		if (!c.isNull(i)) {
		    out.print("\t    <note>");
		    if (privacy < 2) {
			String note = c.getString(i);
			out.print(escapeXML(note));
		    } else {
			byte[] note = c.getBlob(i);
			out.print(encodeBase64(note));
		    }
		    out.println("</note>");
		}
		out.println("\t</to-do>");
		exportCount++;
	    }
	    out.println("    </" + ITEMS_TAG + ">");
	} finally {
	    c.close();
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
