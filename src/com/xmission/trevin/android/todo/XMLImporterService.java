/*
 * $Id: XMLImporterService.java,v 1.4 2014/04/06 21:59:39 trevin Exp trevin $
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
 * $Log: XMLImporterService.java,v $
 * Revision 1.4  2014/04/06 21:59:39  trevin
 * When reverting, only overwrite if the item creation time is the same;
 *   otherwise consider it to be a different item.
 * When updating, only overwrite if the item creating time is the same
 *   and the modification time is newer; otherwise consider it to be
 *   a different item.
 * When merging, only overwrite if the item creation time is the same
 *   (in addition to the update checks); otherwise write a new item.
 *
 * Revision 1.3  2014/04/03 01:06:07  trevin
 * Bug fix: update/merge was comparing the item creation time
 *   rather than the last modification time.
 *
 * Revision 1.2  2014/03/23 21:43:56  trevin
 * Fixed the file format from DOS to unix.
 *
 * Revision 1.1  2014/03/22 19:03:53  trevin
 * Initial revision
 *
 */
package com.xmission.trevin.android.todo;

import static com.xmission.trevin.android.todo.ToDoListActivity.*;
import static com.xmission.trevin.android.todo.XMLExporterService.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.text.ParseException;
import java.util.*;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.*;
import org.xml.sax.SAXException;

import com.xmission.trevin.android.todo.ToDo.ToDoCategory;
import com.xmission.trevin.android.todo.ToDo.ToDoItem;

import android.app.IntentService;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

/**
 * This class imports the To Do list from an XML file on external storage.
 * There are several modes of import available; see the enumeration
 * {@link ImportType} for details on each mode.
 *
 * If a password was used for the exported XML file or on the database,
 * the password <b>must be the same</b> in both in order to import any
 * encrypted records, and the password must have been provided to the
 * application.  If they are not, the unencrypted records may be
 * imported but all encrypted records will be skipped.
 */
public class XMLImporterService extends IntentService implements
        ProgressReportingService {

    public static final String LOG_TAG = "XMLImporterService";

    /** The name of the Intent extra data that holds the import type */
    public static final String XML_IMPORT_TYPE =
	"com.xmission.trevin.android.todo.XMLImportType";

    /**
     * The name of the Intent extra that indicates whether to
     * import private records.
     */
    public static final String IMPORT_PRIVATE =
	"com.xmission.trevin.android.todo.XMLImportPrivate";

    /**
     * The name of the Intent extra data that holds
     * the password for the backup
     */
    public static final String OLD_PASSWORD =
	"com.xmission.trevin.android.todo.XMLImportPassword";

    /**
     * Flag indicating how to merge items from the XML file
     * with those in the database.
     */
    public static enum ImportType {
	/** The database should be cleared before importing the XML file. */
	CLEAN,

	/**
	 * Any items in the database with the same internal ID
	 * as an item in the XML file should be overwritten,
	 * regardless of which one is newer.
	 */
	REVERT,

	/**
	 * Any item is the database with the same internal ID
	 * as an item in the XML file should be overwritten if
	 * the modification time of the item in the XML file is newer.
	 */
	UPDATE,

	/**
	 * Any items in the database with the same category and description
	 * as an item in the XML file should be overwritten if
	 * the item in the XML file has a newer modification time than
	 * the matching item in the database.  If the XML item does not
	 * match any item in the database but has the same ID as another
	 * one, change the ID of the XML item to an unused value.
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

    /** The location of the todo.xml file */
    private File dataFile;

    /** The current mode of operation */
    public enum OpMode {
	PARSING, SETTINGS, CATEGORIES, ITEMS
    };
    private OpMode currentMode = OpMode.PARSING;

    /** The current number of entries imported */
    private int importCount = 0;

    /** The total number of entries to be imported */
    private int totalCount = 0;

    /** Category entry from the XML file */
    protected static class CategoryEntry {
	/** The category ID in the XML file */
	long id;
	String name;
	/** If merging or adding, the new category ID in the Android database */
	long newID;
    }

    /** Categories from the XML file, mapped by the XML id */
    protected Map<Long,CategoryEntry> categoriesByID =
	new HashMap<Long,CategoryEntry>();

    /** Next free record ID (counting both the Palm and Android databases) */
    private long nextFreeRecordID = 1;

    /**
     * To Do entry as stored in the XML file.
     * This should mirror the schema in the database.
     *
    static class ToDoEntry {
	/**
	 * The item ID is initialized from the XML data,
	 * but may be changed by the merge operation.
	 *
	long id;
	/**
	 * The plain text description is only used for unencrypted entries
	 * or if the entry needs to be decoded due to a password change.
	 *
	String description;
	byte[] encryptedDescription;
	long created;
	long modified;
	Long due;
	Long completed;
	boolean checked;
	int priority = 1;
	int privacy;
	long categoryID;
	/**
	 * The plain text note is only used for unencrypted entries
	 * or if the entry needs to be decoded due to a password change.
	 *
	String note;
	byte[] encryptedNote;
	Integer alarmDaysEarlier;
	Long alarmTime;
	static class RepeatData {
	    int interval;
	    Integer increment;
	    Integer weekDays;
	    Integer[] day = new Integer[2];
	    Integer[] week = new Integer[2];
	    Integer month;
	    Long end;
	}
	RepeatData repeat;
	Integer hideDaysEarlier;
	Long notificationTime;
    } /* */

    /** To-do items from the XML file */
    // private ToDoEntry[] dataToDos = null;

    public class ImportBinder extends Binder {
	XMLImporterService getService() {
	    Log.d(LOG_TAG, "ImportBinder.getService()");
	    return XMLImporterService.this;
	}
    }

    private ImportBinder binder = new ImportBinder();

    /** Create the exporter service with a named worker thread */
    public XMLImporterService() {
	super(XMLImporterService.class.getSimpleName());
	Log.d(LOG_TAG, "created");
	// If we die in the middle of an import, restart the request.
	setIntentRedelivery(true);
    }

    /**
     * For the import binder:
     * @return the stage of import we're working on
     */
    @Override
    public String getCurrentMode() {
	switch (currentMode) {
	case PARSING:
	    return getString(R.string.ProgressMessageImportParsing);
	case SETTINGS:
	    return getString(R.string.ProgressMessageImportSettings);
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
    @Override
    protected void onHandleIntent(Intent intent) {
	// Get the location of the todo.xml file
	dataFile = new File(intent.getStringExtra(XML_DATA_FILENAME));
	// Get the import type
	ImportType importType = (ImportType)
		intent.getSerializableExtra(XML_IMPORT_TYPE);
	boolean importPrivate = Boolean.TRUE.equals((Boolean)
		intent.getSerializableExtra(IMPORT_PRIVATE));
	Log.d(LOG_TAG, ".onHandleIntent(" + importType + ",\""
		+ dataFile.getAbsolutePath() + "\")");
	importCount = 0;
	totalCount = 0;

	if (!dataFile.exists()) {
	    Toast.makeText(this, String.format(
		    getString(R.string.ErrorImportNotFound),
		    dataFile.getAbsolutePath()), Toast.LENGTH_LONG);
	    return;
	}
	if (!dataFile.canRead()) {
	    Toast.makeText(this, String.format(
		    getString(R.string.ErrorImportCantRead),
		    dataFile.getAbsolutePath()), Toast.LENGTH_LONG);
	    return;
	}

	try {
	    // To do: Disable the DB content change listener until after importing
	    // Start parsing
	    currentMode = OpMode.PARSING;
	    DocumentBuilder builder =
		DocumentBuilderFactory.newInstance().newDocumentBuilder();
	    Document document = builder.parse(dataFile);
	    Element docRoot = document.getDocumentElement();
	    if (!docRoot.getTagName().equals(DOCUMENT_TAG))
		throw new SAXException("Document root is not " + DOCUMENT_TAG);
	    // To do: what are we doing with this value?
	    /*
	    String s = docRoot.getAttribute("exported");
	    if (s != null)
		Date exportDate = DATE_FORMAT.parse(s);
	    */

	    // Gather and count all of the child elements of the major headings
	    Map<String,Element> headers = mapChildren(docRoot);
	    Map<String,Element> prefs = null;
	    if (headers.containsKey(PREFERENCES_TAG)) {
		prefs = mapChildren(headers.get(PREFERENCES_TAG));
		totalCount += prefs.size();
	    }
	    List<Element> metadata = null;
	    if (headers.containsKey(METADATA_TAG))
		metadata = listChildren(headers.get(METADATA_TAG), "item");
	    List<Element> categories = null;
	    if (headers.containsKey(CATEGORIES_TAG)) {
		categories = listChildren(headers.get(CATEGORIES_TAG), "category");
		totalCount += categories.size();
	    }
	    List<Element> todos = null;
	    if (headers.containsKey(ITEMS_TAG)) {
		todos = listChildren(headers.get(ITEMS_TAG), "to-do");
		totalCount += todos.size();
	    }

	    StringEncryption oldCrypt = null;
	    if (importPrivate && (metadata != null)) {
		for (Element e : metadata) {
		    if (e.getAttribute("name").equals(
			    StringEncryption.METADATA_PASSWORD_HASH[0])) {
			byte[] oldHash = decodeBase64(getText(e));
			// Get the password
			char[] oldPassword =
			    intent.getCharArrayExtra(OLD_PASSWORD);
			if (oldPassword != null) {
			    oldCrypt = new StringEncryption();
			    oldCrypt.setPassword(oldPassword);
			    Arrays.fill(oldPassword, (char) 0);
			    // Check the old password
			    if (!oldCrypt.checkPassword(oldHash)) {
				Toast.makeText(this, getResources().getString(
					R.string.ToastBadPassword), Toast.LENGTH_LONG);
				Log.d(LOG_TAG, "Password does not match hash in the XML file");
				return;
			    }
			} else {
			    Toast.makeText(this, getResources().getString(
				    R.string.ToastPasswordProtected), Toast.LENGTH_LONG);
			    Log.d(LOG_TAG, "XML file is password protected");
			    return;
			}
			break;
		    }
		}
	    }

	    if (categories != null) {
		currentMode = OpMode.CATEGORIES;
		mergeCategories(importType, categories);
	    }

	    /*
	     * Import the preferences after importing categories
	     * in case we're importing a selected category which is new.
	     */
	    if (prefs != null) {
		currentMode = OpMode.SETTINGS;
		switch (importType) {
		case CLEAN:
		case REVERT:
		case UPDATE:
		    // To do: Import preferences
		    /*
		     * We have a problem here.  Setting preferences triggers the
		     * onSharedPreferenceChanged callbacks, which in turn
		     * manipulate the views, which results in a
		     * CalledFromWrongThreadException!
		     */
		    //setPreferences(prefs);
		    break;

		default:
		    // Ignore the preferences
		    break;
		}
		importCount += prefs.size();
	    }

	    if (todos != null) {
		currentMode = OpMode.ITEMS;
		mergeToDos(importType, todos, importPrivate, oldCrypt);
	    }

	    Toast.makeText(this, getString(R.string.ProgressMessageImportFinished),
		    Toast.LENGTH_LONG);

	} catch (Exception x) {
	    Log.e(LOG_TAG, "XML Import Error at item " + importCount
		    + "/" + totalCount, x);
	    Toast.makeText(this, x.getMessage(), Toast.LENGTH_LONG);
	}
	// To do: re-enable the DB content change listener
    }

    /**
     * Gather children of an XML element into a Map.
     * All children are expected to have different names.
     *
     * @param parentNode The parent element
     *
     * @return a {@link Map} of {@link Element}s keyed by the
     * element name.
     *
     * @throws SAXException if any two child elements have the same name.
     */
    protected static Map<String,Element> mapChildren(Element parentNode)
	throws SAXException {
	NodeList nl = parentNode.getChildNodes();
	Map<String,Element> map = new HashMap<String,Element>(nl.getLength());
	for (int i = 0; i < nl.getLength(); i++) {
	    Node n = nl.item(i);
	    // The document may contain Text (whitespace); ignore it.
	    if (n instanceof Element) {
		Element e = (Element) n;
		String tag = e.getTagName();
		if (map.containsKey(tag))
		    throw new SAXException(parentNode.getTagName()
			    + " has multiple " + tag + " children");
		map.put(tag, e);
	    }
	}
	return map;
    }

    /**
     * Gather children of an XML element into a List.
     *
     * @param parentNode The parent element
     * @param childName The expected name of the child elements.
     *
     * @return a {@link List} of {@link Element}s, in the same
     * order in which they were parsed from the XML document.
     *
     * @throws SAXException if any child has a different element name
     * than we expected.
     */
    protected static List<Element> listChildren(Element parentNode,
	    String childName) throws SAXException {
	NodeList nl = parentNode.getChildNodes();
	List<Element> list = new ArrayList<Element>(nl.getLength());
	for (int i = 0; i < nl.getLength(); i++) {
	    Node n = nl.item(i);
	    if (n instanceof Element) {
		Element e = (Element) n;
		if (!e.getTagName().equals(childName))
		    throw new SAXException("Child " + (i + 1) + " of "
			    + parentNode.getTagName() + " is not " + childName);
		list.add(e);
	    }
	}
	return list;
    }

    /**
     * Gather the text nodes from an element.
     *
     * @return a String containing the text from the element,
     * or null if the element is null or does not contain any text nodes.
     */
    protected static String getText(Element e) {
	if (e == null)
	    return null;

	StringBuffer sb = null;
	NodeList nl = e.getChildNodes();
	for (int i = 0; i < nl.getLength(); i++) {
	    Node n = nl.item(i);
	    if (n instanceof Text) {
		if (sb == null)
		    sb = new StringBuffer();
		sb.append(n.getNodeValue());
	    }
	    else if (n instanceof Element) {
		String s = getText((Element) n);
		if (n != null) {
		    if (sb == null)
			sb = new StringBuffer();
		    sb.append(s);
		}
	    }
	}
	return (sb == null) ? null : sb.toString();
    }

    /**
     * RFC 3548 sec. 3 and 4 compatible,
     * reversed ASCII value to Base64 value.
     * Entries with -1 are not valid Base64 characters.
     * Entries with -2 are skipped whitespace.
     */
    private static final byte[] BASE64_VALUES = {
	-1, -1, -1, -1, -1, -1, -1, -1, -1, -2, -2, -1, -1, -2, -1, -1,
	-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
	-2, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 62, -1, 62, -1, 63,
	52, 53, 54, 55, 56, 57, 58, 59, 60, 61, -1, -1, -1, -2, -1, -1,
	-1,  0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13, 14,
	15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, -1, -1, -1, -1, 63,
	-1, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40,
	41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, -1, -1, -1, -1, -1 };

    /** Convert a Base64 string to a stream of bytes */
    public static byte[] decodeBase64(String text) {
	ByteBuffer bb = ByteBuffer.allocate(text.length());
	int temp = 0;
	int bits = 0;
	for (int i = 0; i < text.length(); i++) {
	    char c = text.charAt(i);
	    if ((c > BASE64_VALUES.length) ||
		    (BASE64_VALUES[c] == -1))
		throw new IllegalArgumentException(
			"Invalid Base64 character: " + c);
	    if (BASE64_VALUES[c] == -2)
		continue;
	    temp = (temp << 6) + BASE64_VALUES[c];
	    bits += 6;
	    // Store bytes once we have three
	    if (bits >= 24) {
		bb.put((byte) (temp >> 16));
		bb.put((byte) (temp >> 8));
		bb.put((byte) temp);
		temp = 0;
		bits = 0;
	    }
	}
	// Special handling for the last byte(s).  The encoder would
	// have emitted characters to cover full bytes.
	switch (bits) {
	case 12:
	    bb.put((byte) (temp >> 4));
	    break;
	case 18:
	    bb.put((byte) (temp >> 10));
	    bb.put((byte) (temp >> 2));
	    break;
	}
	byte[] result = new byte[bb.position()];
	System.arraycopy(bb.array(), 0, result, 0, result.length);
	return result;
    }

    /**
     * Set the current preferences by the ones read from the XML file.
     */
    void setPreferences(Map<String,Element> prefsMap) {
	Log.d(LOG_TAG, ".setPreferences(" + prefsMap.keySet() + ")");
	SharedPreferences.Editor prefsEditor =
	    getSharedPreferences(TODO_PREFERENCES, MODE_PRIVATE).edit();
	if (prefsMap.containsKey(TPREF_SORT_ORDER)) {
	    try {
		prefsEditor.putInt(TPREF_SORT_ORDER,
			Integer.parseInt(getText(prefsMap.get(TPREF_SORT_ORDER))));
	    } catch (NumberFormatException x) {
		Log.e(LOG_TAG, "Invalid sort order index: "
			+ getText(prefsMap.get(TPREF_SORT_ORDER)), x);
		// Ignore this change
	    }
	}
	if (prefsMap.containsKey(TPREF_SHOW_CHECKED))
	    prefsEditor.putBoolean(TPREF_SHOW_CHECKED,
		    Boolean.parseBoolean(getText(prefsMap.get(TPREF_SHOW_CHECKED))));
	if (prefsMap.containsKey(TPREF_SHOW_DUE_DATE))
	    prefsEditor.putBoolean(TPREF_SHOW_DUE_DATE,
		    Boolean.parseBoolean(getText(prefsMap.get(TPREF_SHOW_DUE_DATE))));
	if (prefsMap.containsKey(TPREF_SHOW_PRIORITY))
	    prefsEditor.putBoolean(TPREF_SHOW_PRIORITY,
		    Boolean.parseBoolean(getText(prefsMap.get(TPREF_SHOW_PRIORITY))));
	if (prefsMap.containsKey(TPREF_SHOW_CATEGORY))
	    prefsEditor.putBoolean(TPREF_SHOW_CATEGORY,
		    Boolean.parseBoolean(getText(prefsMap.get(TPREF_SHOW_CATEGORY))));
	/*
	 * Note that we are not changing whether private/encrypted records
	 * are shown.  If the user wanted encrypted records, he should have
	 * set the password in the PreferencesActivity both when exporting
	 * and importing the file.
	 */
	if (prefsMap.containsKey(TPREF_NOTIFICATION_SOUND)) {
	    try {
		prefsEditor.putLong(TPREF_NOTIFICATION_SOUND,
			Long.parseLong(getText(prefsMap.get(TPREF_NOTIFICATION_SOUND))));
	    } catch (NumberFormatException x) {
		Log.e(LOG_TAG, "Invalid notificationt sound index: "
			+ getText(prefsMap.get(TPREF_NOTIFICATION_SOUND)), x);
		// Ignore this change
	    }
	}
	if (prefsMap.containsKey(TPREF_SELECTED_CATEGORY)) {
	    try {
		prefsEditor.putLong(TPREF_SELECTED_CATEGORY,
			Long.parseLong(getText(prefsMap.get(TPREF_SELECTED_CATEGORY))));
	    } catch (NumberFormatException x) {
		Log.e(LOG_TAG, "Invalid category index: "
			+ getText(prefsMap.get(TPREF_SELECTED_CATEGORY)), x);
		// Ignore this change
	    }
	}
	prefsEditor.commit();
    }

    /**
     * Merge the category list from the XML file
     * with the Android database.
     */
    void mergeCategories(ImportType importType, List<Element> categories) {
	Log.d(LOG_TAG, ".mergeCategories(" + importType + ")");
	// Read in the current list of categories
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

	if (importType == ImportType.CLEAN) {
	    Log.d(LOG_TAG, ".mergeCategories: removing all existing categories");
	    resolver.delete(ToDoCategory.CONTENT_URI, null, null);
	    categoryIDMap.clear();
	    categoryNameMap.clear();
	}

	ContentValues values = new ContentValues();
	for (Element categorE : categories) {
	    CategoryEntry entry = new CategoryEntry();
	    entry.name = getText(categorE);
	    entry.id = Integer.parseInt(categorE.getAttribute("id"));
	    // Skip the ToDoCategory.UNFILED
	    if (entry.id == ToDoCategory.UNFILED) {
		importCount++;
		continue;
	    }

	    entry.newID = entry.id;
	    categoriesByID.put(entry.id, entry);

	    switch (importType) {
	    case CLEAN:
		// There are no pre-existing categories
		Log.d(LOG_TAG, ".mergeCategories: adding " + entry.id
			+ " \"" + entry.name + "\"");
		values.put(ToDoCategory._ID, (long) entry.id);
		values.put(ToDoCategory.NAME, entry.name);
		resolver.insert(ToDoCategory.CONTENT_URI, values);
		break;

	    case REVERT:
		// Always overwrite
		if (categoryNameMap.containsKey(entry.name) &&
			(categoryNameMap.get(entry.name) != entry.id)) {
		    long oldId = categoryNameMap.get(entry.name);
		    Log.d(LOG_TAG, ".mergeCategories: \"" + entry.name
			    + "\" already exists with ID " + oldId
			    + "; deleting it.");
		    // Change the category of all items using the old ID
		    values.clear();
		    values.put(ToDoItem.CATEGORY_ID, oldId);
		    resolver.update(ToDoItem.CONTENT_URI, values,
			    ToDoItem.CATEGORY_ID + "=" + oldId, null);
		    values.clear();
		    values.put(ToDoCategory._ID, oldId);
		    resolver.delete(ContentUris.withAppendedId(
			    ToDoCategory.CONTENT_URI, oldId), null, null);
		    categoryIDMap.remove(oldId);
		    categoryNameMap.remove(entry.name);
		}
		if (categoryIDMap.containsKey(entry.id)) {
		    if (!categoryIDMap.get(entry.id).equals(entry.name)) {
			Log.d(LOG_TAG, ".mergeCategories: replacing \""
				+ categoryIDMap.get(entry.id)
				+ "\" with \"" + entry.name + "\"");
			values.remove(ToDoCategory._ID);
			values.put(ToDoCategory.NAME, entry.name);
			resolver.update(ContentUris.withAppendedId(
				ToDoCategory.CONTENT_URI, entry.id),
				values, null, null);
		    }
		}
		else {
		    Log.d(LOG_TAG, ".mergeCategories: adding \""
			    + entry.name + "\"");
		    values.put(ToDoCategory._ID, entry.id);
		    values.put(ToDoCategory.NAME, entry.name);
		    resolver.insert(ToDoCategory.CONTENT_URI, values);
		}
		break;

	    case UPDATE:
		/*
		 * Overwrite if newer.  But since categories
		 * have no time stamp, this item acts like merge.
		 */
	    case MERGE:
	    case ADD:
		if (categoryNameMap.containsKey(entry.name)) {
		    if (entry.id != categoryNameMap.get(entry.name))
			entry.newID = categoryNameMap.get(entry.name);
		} else {
		    Log.d(LOG_TAG, ".mergeCategories: adding \""
			    + entry.name + "\"");
		    // Use a new ID if there is a conflict
		    if (categoryIDMap.containsKey(entry.id)) {
			values.remove(ToDoCategory._ID);
			values.put(ToDoCategory.NAME, entry.name);
			Uri newItem = resolver.insert(
				ToDoCategory.CONTENT_URI, values);
			entry.newID = Long.parseLong(
				newItem.getPathSegments().get(1));
		    } else {
			values.put(ToDoCategory._ID, entry.id);
			values.put(ToDoCategory.NAME, entry.name);
			resolver.insert(ToDoCategory.CONTENT_URI, values);
		    }
		}
		break;

	    case TEST:
		// Do nothing.
		break;
	    }

	    importCount++;
	}
    }

    private static enum Operation { INSERT, UPDATE, SKIP };

    private static final Pattern DATE_PATTERN =
	Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z");
    private static final Pattern NUMBER_PATTERN =
	Pattern.compile("-?\\d+(\\.\\d*)?");

    /**
     * Earlier exports did not use the ISO date format,
     * so we need to check for both. :(
     *
     * @throws ParseException if the string does not look like a date or a number.
     */
    Date parseDate(String str) throws ParseException {
	if (DATE_PATTERN.matcher(str).matches())
	    return DATE_FORMAT.parse(str);
	if (NUMBER_PATTERN.matcher(str).matches())
	    return new Date(Long.parseLong(str));
	throw new ParseException("Cannot interpret " + str + " as a date", 0);
    }

    /** Quick implementation of StringUtils.isEmpty(String) */
    boolean isEmpty(String s) {
	return (s == null) || (s.length() == 0);
    }

    /**
     * Merge the To Do items from the XML file
     * with the Android database.
     */
    void mergeToDos(ImportType importType, List<Element> items,
	    boolean importPrivate, StringEncryption oldCrypt)
		throws GeneralSecurityException, ParseException, SAXException {
	Log.d(LOG_TAG, ".mergeToDos(" + importType + ")");
	ContentResolver resolver = getContentResolver();
	StringEncryption newCrypt = StringEncryption.holdGlobalEncryption();

	try {
	    if (importType == ImportType.CLEAN) {
		Log.d(LOG_TAG, ".mergeToDos: removing all existing To Do items");
		resolver.delete(ToDoItem.CONTENT_URI, null, null);
	    }

	    final String[] EXISTING_ITEM_PROJECTION = {
		    ToDoItem._ID, ToDoItem.CATEGORY_ID, ToDoItem.CATEGORY_NAME,
		    ToDoItem.PRIVATE, ToDoItem.DESCRIPTION,
		    ToDoItem.CREATE_TIME, ToDoItem.MOD_TIME };

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
	    for (Element itemE : items) {
		Map<String,Element> itemMap = mapChildren(itemE);
		values.clear();
		String value = itemE.getAttribute("id");
		values.put(ToDoItem._ID, Long.parseLong(value));
		value = itemE.getAttribute("checked");
		values.put(ToDoItem.CHECKED, Boolean.parseBoolean(value) ? 1 : 0);
		value = itemE.getAttribute("category");
		long categoryID = Integer.parseInt(value);
		if (categoriesByID.containsKey(categoryID))
		    categoryID = categoriesByID.get(categoryID).newID;
		else
		    categoryID = ToDoCategory.UNFILED;
		values.put(ToDoItem.CATEGORY_ID, (int) categoryID);
		value = itemE.getAttribute("priority");
		values.put(ToDoItem.PRIORITY, Integer.parseInt(value));

		value = itemE.getAttribute("private");
		int privacy = 0;
		if (Boolean.parseBoolean(value)) {
		    value = itemE.getAttribute("encryption");
		    if (!isEmpty(value))
			privacy = Integer.parseInt(value);
		    else
			privacy = 1;
		}

		String description = getText(itemMap.get("description"));
		String note = (itemMap.containsKey("note")) ?
			getText(itemMap.get("note")) : null;
		if (privacy > 0) {
		    if (!importPrivate) {
			importCount++;
			continue;
		    }
		    byte[] encryptedDescription;
		    byte[] encryptedNote;
		    if (privacy >= 2) {
			// Decrypt first — Base64 in XML
			encryptedDescription = decodeBase64(description);
			description = oldCrypt.decrypt(encryptedDescription);
			if (note != null) {
			    encryptedNote = decodeBase64(note);
			    note = oldCrypt.decrypt(encryptedNote);
			}
		    }
		    // Re-encrypt if possible — binary in DB
		    if (newCrypt.hasKey()) {
			encryptedDescription = newCrypt.encrypt(description);
			values.put(ToDoItem.DESCRIPTION, encryptedDescription);
			if (itemMap.containsKey("note")) {
			    encryptedNote = newCrypt.encrypt(note);
			    values.put(ToDoItem.NOTE, encryptedNote);
			}
			privacy = 2;
		    } else {
			privacy = 1;
		    }
		}
		if (privacy < 2) {
		    values.put(ToDoItem.DESCRIPTION, description);
		    if (itemMap.containsKey("note"))
			values.put(ToDoItem.NOTE, note);
		}
		values.put(ToDoItem.PRIVATE, privacy);

		Element child = itemMap.get("created");
		value = child.getAttribute("time");
		// Earlier exports did not use the ISO date format,
		// so we need to check for both. :(
		values.put(ToDoItem.CREATE_TIME, parseDate(value).getTime());
		child = itemMap.get("modified");
		value = child.getAttribute("time");
		values.put(ToDoItem.MOD_TIME, parseDate(value).getTime());
		child = itemMap.get("due");
		if (child != null) {
		    Map<String,Element> childMap = mapChildren(child);
		    value = child.getAttribute("time");
		    values.put(ToDoItem.DUE_TIME, parseDate(value).getTime());
		    Element grandchild = childMap.get("alarm");
		    if (grandchild != null) {
			value = grandchild.getAttribute("days-earlier");
			values.put(ToDoItem.ALARM_DAYS_EARLIER,
				Integer.parseInt(value));
			value = grandchild.getAttribute("time");
			values.put(ToDoItem.ALARM_TIME, Long.parseLong(value));
		    }

		    grandchild = childMap.get("repeat");
		    if (grandchild != null) {
			value = grandchild.getAttribute("interval");
			values.put(ToDoItem.REPEAT_INTERVAL,
				Integer.parseInt(value));
			value= grandchild.getAttribute("increment");
			if (!isEmpty(value))
			    values.put(ToDoItem.REPEAT_INCREMENT,
				    Integer.parseInt(value));
			value = grandchild.getAttribute("week-days");
			if (!isEmpty(value))
			    values.put(ToDoItem.REPEAT_WEEK_DAYS,
				    Integer.parseInt(value, 2));
			value = grandchild.getAttribute("day1");
			if (!isEmpty(value))
			    values.put(ToDoItem.REPEAT_DAY,
				    Integer.parseInt(value));
			value = grandchild.getAttribute("day2");
			if (!isEmpty(value))
			    values.put(ToDoItem.REPEAT_DAY2,
				    Integer.parseInt(value));
			value = grandchild.getAttribute("week1");
			if (!isEmpty(value))
			    values.put(ToDoItem.REPEAT_WEEK,
				    Integer.parseInt(value));
			value = grandchild.getAttribute("week2");
			if (!isEmpty(value))
			    values.put(ToDoItem.REPEAT_WEEK2,
				    Integer.parseInt(value));
			value = grandchild.getAttribute("month");
			if (!isEmpty(value))
			    values.put(ToDoItem.REPEAT_MONTH,
				    Integer.parseInt(value));
			value = grandchild.getAttribute("end");
			if (!isEmpty(value))
			    values.put(ToDoItem.REPEAT_END, Long.parseLong(value));
		    }

		    grandchild = childMap.get("hide");
		    if (grandchild != null) {
			value = grandchild.getAttribute("days-earlier");
			values.put(ToDoItem.HIDE_DAYS_EARLIER,
				Integer.parseInt(value));
		    }

		    grandchild = childMap.get("notification");
		    if (grandchild != null) {
			value = grandchild.getAttribute("time");
			values.put(ToDoItem.NOTIFICATION_TIME,
				parseDate(value).getTime());
		    }
		}

		if (importType != ImportType.CLEAN) {
		    existingRecord.clear();
		    c = resolver.query(ContentUris.withAppendedId(
			    ToDoItem.CONTENT_URI, values.getAsLong(ToDoItem._ID)),
			    EXISTING_ITEM_PROJECTION, null, null, null);
		    if (c.moveToFirst()) {
			int oldPrivacy =
			    c.getInt(c.getColumnIndex(ToDoItem.PRIVATE));
			existingRecord.put(ToDoItem.PRIVATE, oldPrivacy);
			if (oldPrivacy < 2) {
			    existingRecord.put(ToDoItem.DESCRIPTION,
				    c.getString(c.getColumnIndex(ToDoItem.DESCRIPTION)));
			} else {
			    if (newCrypt == null)
				newCrypt = StringEncryption.holdGlobalEncryption();
			    byte[] encryptedDescription =
				c.getBlob(c.getColumnIndex(ToDoItem.DESCRIPTION));
			    if (newCrypt.hasKey())
				// Decode it
				existingRecord.put(ToDoItem.DESCRIPTION,
					newCrypt.decrypt(encryptedDescription));
			    else
				/*
				 * Since we don’t know the description,
				 * assume it’s different from anything else.
				 */
				existingRecord.put(ToDoItem.DESCRIPTION,
					UUID.nameUUIDFromBytes(
						encryptedDescription).toString());
			}
			existingRecord.put(ToDoItem.CATEGORY_ID,
				c.getLong(c.getColumnIndex(ToDoItem.CATEGORY_ID)));
			existingRecord.put(ToDoItem.CATEGORY_NAME,
				c.getString(c.getColumnIndex(ToDoItem.CATEGORY_NAME)));
			existingRecord.put(ToDoItem.CREATE_TIME,
				c.getLong(c.getColumnIndex(ToDoItem.CREATE_TIME)));
			existingRecord.put(ToDoItem.MOD_TIME,
				c.getLong(c.getColumnIndex(ToDoItem.MOD_TIME)));
		    }
		    c.close();
		}

		Operation op = Operation.INSERT;
		switch (importType) {
		case CLEAN:
		    // All items are new
		    break;

		case REVERT:
		    // Overwrite if it’s the same item
		    if (existingRecord.size() > 0) {
			if (values.getAsLong(ToDoItem.CREATE_TIME).equals(
				existingRecord.getAsLong(ToDoItem.CREATE_TIME)))
			    op = Operation.UPDATE;
			else
			    // Not the same item!
			    values.put(ToDoItem._ID, nextFreeRecordID++);
		    }
		    break;

		case UPDATE:
		    // Overwrite if it’s the same item and newer
		    if (existingRecord.size() > 0) {
			if (values.getAsLong(ToDoItem.CREATE_TIME).equals(
				existingRecord.getAsLong(ToDoItem.CREATE_TIME))) {
			    if (values.getAsLong(ToDoItem.MOD_TIME) >
			    existingRecord.getAsLong(ToDoItem.MOD_TIME))
				op = Operation.UPDATE;
			    else
				op = Operation.SKIP;
			} else {
			    // Not the same item!
			    values.put(ToDoItem._ID, nextFreeRecordID++);
			}
		    }
		    break;

		case MERGE:
		    // Overwrite if newer and the same category and description;
		    // make a new entry if the category or description differ.
		    if (existingRecord.size() > 0) {
			if (values.getAsLong(ToDoItem.CREATE_TIME).equals(
				existingRecord.getAsLong(ToDoItem.CREATE_TIME)) &&
			    existingRecord.getAsString(ToDoItem.CATEGORY_ID)
				.equals(values.getAsString(ToDoItem.CATEGORY_ID)) &&
			    existingRecord.getAsString(ToDoItem.DESCRIPTION)
				.equals(values.getAsString(ToDoItem.DESCRIPTION))) {
			    if (values.getAsLong(ToDoItem.MOD_TIME) >
				existingRecord.getAsLong(ToDoItem.MOD_TIME))
				op = Operation.UPDATE;
			    else
				op = Operation.SKIP;
			} else {
			    // Conflict; change the ID
			    values.put(ToDoItem._ID, nextFreeRecordID++);
			}
		    }
		    break;

		case ADD:
		    // All items are new, but may need a new ID
		    if (existingRecord.size() > 0)
			values.put(ToDoItem._ID, nextFreeRecordID++);
		    break;

		case TEST:
		    // Do nothing
		    op = Operation.SKIP;
		    break;
		}
		switch (op) {
		case INSERT:
		    if (items.size() < 64) {
			Log.d(LOG_TAG, ".mergeToDos: adding "
				+ values.getAsLong(ToDoItem._ID) + " \""
				+ (values.getAsInteger(ToDoItem.PRIVATE) > 0
					? "[private]"
					: values.getAsString(ToDoItem.DESCRIPTION))
				+ "\"");
		    }
		    resolver.insert(ToDoItem.CONTENT_URI, values);
		    break;

		case UPDATE:
		    if (items.size() < 64) {
			Log.d(LOG_TAG, ".mergeToDos: replacing existing record "
				+ values.getAsLong(ToDoItem._ID) + " \""
				+ ((existingRecord.getAsInteger(ToDoItem.PRIVATE) > 0)
					? "[private]"
					: existingRecord.getAsString(ToDoItem.DESCRIPTION))
				+ (values.getAsInteger(ToDoItem.PRIVATE) > 0
					? "[private]"
					: values.getAsString(ToDoItem.DESCRIPTION))
				+ "\"");
		    }
		    resolver.update(ContentUris.withAppendedId(ToDoItem.CONTENT_URI,
			    values.getAsLong(ToDoItem._ID)), values, null, null);
		    break;
		}
		importCount++;
	    }
	}
	finally {
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
