/*
 * $Id: ToDoListActivity.java,v 1.6 2014/06/03 02:33:18 trevin Exp trevin $
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
 * $Log: ToDoListActivity.java,v $
 * Revision 1.6  2014/06/03 02:33:18  trevin
 * Fixes for compatibility with SDK ≥ 11 (Honeycomb):
 * • Use a CursorLoader (via LoaderManager) instead of a managedQuery.
 * • Added overrides for onRestart(), onStart(), onResume(),
 *   onPause(), and onStop().
 * • Added a new menu item, and show it on the action bar when possible.
 * Fixed the class cast for the password dialog root element.
 *
 * Revision 1.5  2014/04/06 15:11:34  trevin
 * Update an item’s modification time when its due date changes.
 *
 * Revision 1.4  2014/03/22 19:03:53  trevin
 * Added the copyright notice.
 * Moved the import function from the menu handler to ImportActivity.
 * Implemented ExportActivity.
 * Replaced the text in the About dialog with its own resource string.
 *
 * Revision 1.3  2011/05/18 05:04:51  trevin
 * Added a preferences item for the alarm sound.
 * Added a content change observer to tell the alarm service to refresh.
 * Run the alarm service on creation, it case it wasn't done at boot.
 *
 * Revision 1.2  2011/05/11 05:19:02  trevin
 * Added a password change dialog and progress dialog.
 *
 * Revision 1.1  2011/02/14 03:44:03  trevin
 * Initial revision
 *
 */
package com.xmission.trevin.android.todo;

import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;

import com.xmission.trevin.android.todo.ToDo.*;

import android.annotation.SuppressLint;
import android.app.*;
import android.content.*;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.database.SQLException;
import android.database.sqlite.SQLiteDoneException;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.InputType;
import android.util.Log;
import android.view.*;
import android.widget.*;

/**
 * Displays a list of To Do items.  Will display items from the {@link Uri}
 * provided in the intent if there is one, otherwise defaults to displaying the
 * contents of the {@link ToDoProvider}.
 *
 * @author Trevin Beattie
 */
@SuppressLint("NewApi")
@SuppressWarnings("deprecation")
public class ToDoListActivity extends ListActivity
implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "ToDoListActivity";

    private static final int ABOUT_DIALOG_ID = 1;
    private static final int DUEDATE_LIST_ID = 2;
    private static final int DUEDATE_DIALOG_ID = 7;
    private static final int PASSWORD_DIALOG_ID = 8;
    private static final int PROGRESS_DIALOG_ID = 9;

    /**
     * The columns we are interested in from the category table
     */
    private static final String[] CATEGORY_PROJECTION = new String[] {
            ToDoCategory._ID,
            ToDoCategory.NAME,
    };

    /**
     * The columns we are interested in from the item table
     */
    private static final String[] ITEM_PROJECTION = new String[] {
            ToDoItem._ID,
            ToDoItem.DESCRIPTION,
            ToDoItem.CHECKED,
            ToDoItem.NOTE,
            ToDoItem.ALARM_DAYS_EARLIER,
            ToDoItem.REPEAT_INTERVAL,
            ToDoItem.DUE_TIME,
            ToDoItem.COMPLETED_TIME,
            ToDoItem.CATEGORY_NAME,
            ToDoItem.PRIVATE,
            ToDoItem.PRIORITY,
            ToDoItem.REPEAT_DAY,
            ToDoItem.REPEAT_DAY2,
            ToDoItem.REPEAT_END,
            ToDoItem.REPEAT_INCREMENT,
            ToDoItem.REPEAT_MONTH,
            ToDoItem.REPEAT_WEEK,
            ToDoItem.REPEAT_WEEK2,
            ToDoItem.REPEAT_WEEK_DAYS,
    };

    /** Shared preferences */
    private SharedPreferences prefs;

    /** Preferences tag for the To Do application */
    public static final String TODO_PREFERENCES = "ToDoPrefs";

    /** Label for the preferences option "Sort order" */
    public static final String TPREF_SORT_ORDER = "SortOrder";

    /** Label for the preferences option "Show completed tasks" */
    public static final String TPREF_SHOW_CHECKED = "ShowChecked";

    /** Label for the preferences option "Show due dates" */
    public static final String TPREF_SHOW_DUE_DATE = "ShowDueDate";

    /** Label for the preferences option "Show priorities" */
    public static final String TPREF_SHOW_PRIORITY = "ShowPriority";

    /** Label for the preferences option "Show private records" */
    public static final String TPREF_SHOW_PRIVATE = "ShowPrivate";

    /** The preferences option for showing encrypted records */
    public static final String TPREF_SHOW_ENCRYPTED = "ShowEncrypted";

    /** Label for the preferences option "Show categories" */
    public static final String TPREF_SHOW_CATEGORY = "ShowCategory";

    /** Label for the preferred notification sound */
    public static final String TPREF_NOTIFICATION_SOUND = "NotificationSound";

    /** Label for the currently selected category */
    public static final String TPREF_SELECTED_CATEGORY = "SelectedCategory";

    /** The URI by which we were started for the To-Do items */
    private Uri todoUri = ToDoItem.CONTENT_URI;

    /** The corresponding URI for the categories */
    private Uri categoryUri = ToDoCategory.CONTENT_URI;

    /** Category filter spinner */
    Spinner categoryList = null;

    // Used to map categories from the database to views
    CategoryFilterCursorAdapter categoryAdapter = null;

    // Used to map To Do entries from the database to views
    ToDoCursorAdapter itemAdapter = null;

    /** Due date list dialog */
    Dialog dueDateListDialog = null;

    /** Due Date dialog box */
    CalendarDatePickerDialog dueDateDialog = null;

    /** Password change dialog */
    Dialog passwordChangeDialog = null;

    /**
     * Text fields in the password change dialog.
     * Field [0] is the old password, [1] and [2] are the new.
     */
    EditText[] passwordChangeEditText = new EditText[3];

    /** Progress reporting service */
    ProgressReportingService progressService = null;

    /** Progress dialog */
    ProgressDialog progressDialog = null;

    /** Encryption for private records */
    StringEncryption encryptor;

    /** Keep track of changes so that we can update any alarms */
    private class ToDoContentObserver extends ContentObserver {
	public ToDoContentObserver() {
	    super(new Handler());
	}

	@Override
	public boolean deliverSelfNotifications() {
	    return false;
	}

	@Override
	public void onChange(boolean selfChange) {
	    Log.d(TAG, "ContentObserver.onChange()");
	    Intent alarmIntent =
		new Intent(ToDoListActivity.this, AlarmService.class);
	    alarmIntent.setAction(Intent.ACTION_EDIT);
	    startService(alarmIntent);
	}
    }
    private final ToDoContentObserver registeredObserver =
	new ToDoContentObserver();

    /**
     * Category Loader callbacks for API ≥ 11.
     * This <b>must</b> be stored in an Object reference
     * because we need to support API’s 8–10 as well.
     */
    private Object categoryLoaderCallbacks = null;

    /**
     * Item Loader callbacks for API ≥ 11.
     * This <b>must</b> be stored in an Object reference
     * because we need to support API’s 8–10 as well.
     */
    private Object itemLoaderCallbacks = null;

    /** Called when the activity is first created. */
    @SuppressWarnings("unchecked")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, ".onCreate");

        setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);

        // If no data was given in the intent (because we were started
	// as a MAIN activity), then use our default content provider.
	Intent intent = getIntent();
	if (intent.getData() == null) {
	    intent.setData(ToDoItem.CONTENT_URI);
	    todoUri = ToDoItem.CONTENT_URI;
	    categoryUri = ToDoCategory.CONTENT_URI;
	} else {
	    todoUri = intent.getData();
	    categoryUri = todoUri.buildUpon().encodedPath("/categories").build();
	}

	encryptor = StringEncryption.holdGlobalEncryption();
	prefs = getSharedPreferences(TODO_PREFERENCES, MODE_PRIVATE);
	prefs.registerOnSharedPreferenceChangeListener(this);
	String whereClause = generateWhereClause();

        int selectedSortOrder = prefs.getInt(TPREF_SORT_ORDER, 0);
        if ((selectedSortOrder < 0) ||
        	(selectedSortOrder >= ToDoItem.USER_SORT_ORDERS.length)) {
            prefs.edit().putInt(TPREF_SORT_ORDER, 0).commit();
	    selectedSortOrder = 0;
	}

	/*
	 * Perform two managed queries. The Activity will handle closing and
	 * requerying the cursor when needed ... on Android 2.x.
	 *
	 * On API level ≥ 11, you need to find a way to re-initialize
	 * the cursor when the activity is restarted!
	 */
	Cursor categoryCursor = null;
	if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
	    Log.d(TAG, ".onCreate: selecting categories");
	    categoryCursor = managedQuery(categoryUri,
		    CATEGORY_PROJECTION, null, null,
		    ToDoCategory.DEFAULT_SORT_ORDER);
	    categoryAdapter = new CategoryFilterCursorAdapter(this, categoryCursor);
	} else {
	    categoryAdapter = new CategoryFilterCursorAdapter(this);
	    Log.d(TAG, ".onCreate: initializing a category loader manager");
	    if (Log.isLoggable(TAG, Log.DEBUG))
		    LoaderManager.enableDebugLogging(true);
	    categoryLoaderCallbacks = new LoaderManager.LoaderCallbacks<Cursor>() {
		public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		    Log.d(TAG, ".LoaderCallbacks$CATEGORY.onCreateLoader");
		    CursorLoader loader = new CursorLoader(ToDoListActivity.this) {
			private Cursor myCursor = null;
			@Override
			public Cursor loadInBackground() {
			    Log.d(TAG, ".LoaderCallbacks$CATEGORY.CursorLoader.loadInBackground");
			    if (myCursor != null)
				myCursor.close();
			    myCursor = getContentResolver().query(categoryUri,
				    CATEGORY_PROJECTION, null, null,
				    ToDoCategory.DEFAULT_SORT_ORDER);
			    // Ensure the cursor window is filled (from super class)
			    myCursor.getCount();
			    return myCursor;
			}
			@Override
			protected void onStartLoading() {
			    Log.d(TAG, ".LoaderCallbacks$CATEGORY.CursorLoader.onStartLoading");
			    super.onStartLoading();
			}
			@Override
			public void onCanceled(Cursor cursor) {
			    Log.d(TAG, "LoaderCallbacks$CATEGORY.CursorLoader.onCanceled");
			    super.onCanceled(cursor);
			}
			@Override
			protected void onStopLoading() {
			    Log.d(TAG, ".LoaderCallbacks$CATEGORY.CursorLoader.onStopLoading");
			    super.onStopLoading();
			    if (myCursor != null)
				myCursor.close();
			    myCursor = null;
			}
		    };
		    return loader;
		}

		public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
		    Log.d(TAG, ".LoaderCallbacks$CATEGORY.onLoadFinished");
		    categoryAdapter.swapCursor(data);
		    setCategorySpinnerByID(prefs.getLong(TPREF_SELECTED_CATEGORY, -1));
		}

		public void onLoaderReset(Loader<Cursor> loader) {
		    Log.d(TAG, ".LoaderCallbacks$CATEGORY.onLoaderReset");
		    categoryAdapter.swapCursor(null);
		}
	    };
	    getLoaderManager().initLoader(ToDoCategory.CONTENT_TYPE.hashCode(),
		    null, (LoaderManager.LoaderCallbacks<Cursor>) categoryLoaderCallbacks);
	}

        Cursor itemCursor = null;
	if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
	    Log.d(TAG, ".onCreate: selecting To Do items where "
		    + whereClause + " ordered by "
		    + ToDoItem.USER_SORT_ORDERS[selectedSortOrder]);
	    itemCursor = managedQuery(todoUri,
		    ITEM_PROJECTION, whereClause, null,
		    ToDoItem.USER_SORT_ORDERS[selectedSortOrder]);
	    itemAdapter = new ToDoCursorAdapter(
		    this, R.layout.list_item, itemCursor,
		    getContentResolver(), todoUri, this, encryptor);
	} else {
	    itemAdapter = new ToDoCursorAdapter(
		    this, R.layout.list_item, null,
		    getContentResolver(), todoUri, this, encryptor);
	    Log.d(TAG, ".onCreate: initializing a To Do item loader manager");
	    itemLoaderCallbacks = new LoaderManager.LoaderCallbacks<Cursor>() {
		public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		    Log.d(TAG, ".LoaderCallbacks$ITEM.onCreateLoader");
		    CursorLoader loader = new CursorLoader(ToDoListActivity.this) {
			private Cursor myCursor = null;
			@Override
			public Cursor loadInBackground() {
			    Log.d(TAG, ".LoaderCallbacks$ITEM.CursorLoader.loadInBackground");
			    if (myCursor != null)
				myCursor.close();
			    int selectedSortOrder = prefs.getInt(TPREF_SORT_ORDER, 0);
			    if ((selectedSortOrder < 0) ||
				    (selectedSortOrder >= ToDoItem.USER_SORT_ORDERS.length)) {
				prefs.edit().putInt(TPREF_SORT_ORDER, 0).commit();
				selectedSortOrder = 0;
			    }
			    myCursor = getContentResolver().query(todoUri,
				    ITEM_PROJECTION, generateWhereClause(), null,
				    ToDoItem.USER_SORT_ORDERS[selectedSortOrder]);
			    return myCursor;
			}
			@Override
			protected void onStartLoading() {
			    Log.d(TAG, ".LoaderCallbacks$ITEM.CursorLoader.onStartLoading");
			    super.onStartLoading();
			}
			@Override
			public void onCanceled(Cursor cursor) {
			    Log.d(TAG, "LoaderCallbacks$ITEM.CursorLoader.onCanceled");
			    super.onCanceled(cursor);
			}
			@Override
			protected void onStopLoading() {
			    Log.d(TAG, ".LoaderCallbacks$ITEM.CursorLoader.onStopLoading");
			    super.onStopLoading();
			    if (myCursor != null)
				myCursor.close();
			    myCursor = null;
			}
		    };
		    return loader;
		}

		public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
		    Log.d(TAG, ".LoaderCallbacks$ITEM.onLoadFinished");
		    itemAdapter.swapCursor(data);
		}

		public void onLoaderReset(Loader<Cursor> loader) {
		    Log.d(TAG, ".LoaderCallbacks$ITEM.onLoaderReset");
		    itemAdapter.swapCursor(null);
		}
	    };
	    getLoaderManager().initLoader(ToDoItem.CONTENT_TYPE.hashCode(),
		    null, (LoaderManager.LoaderCallbacks<Cursor>) itemLoaderCallbacks);
	}

        // Inflate our view so we can find our lists
	setContentView(R.layout.list);

        categoryAdapter.setDropDownViewResource(
        	R.layout.simple_spinner_dropdown_item);
        categoryList = (Spinner) findViewById(R.id.ListSpinnerCategory);
        categoryList.setAdapter(categoryAdapter);
	if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB)
	    setCategorySpinnerByID(prefs.getLong(TPREF_SELECTED_CATEGORY, -1));

	itemAdapter.setViewResource(R.layout.list_item);
	ListView listView = getListView();
	listView.setAdapter(itemAdapter);
	listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
	    @Override
	    public void onItemClick(AdapterView<?> parent,
		    View view, int position, long id) {
		Log.d(TAG, ".onItemClick(parent,view," + position + "," + id + ")");
	    }
	});
	listView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
	    @Override
	    public void onFocusChange(View v, boolean hasFocus) {
		Log.d(TAG, ".onFocusChange(view," + hasFocus + ")");
	    }
	});
	listView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
	    @Override
	    public void onItemSelected(AdapterView<?> parent,
		    View v, int position, long id) {
		Log.d(TAG, ".onItemSelected(parent,view," + position + "," + id + ")");
	    }
	    @Override
	    public void onNothingSelected(AdapterView<?> parent) {
		Log.d(TAG, ".onNothingSelected(parent)");
	    }
	});

	// Set a callback for the New button
	Button newButton = (Button) findViewById(R.id.ListButtonNew);
	newButton.setOnClickListener(new NewButtonListener());

	// Set a callback for the category filter
	categoryList.setOnItemSelectedListener(new CategorySpinnerListener());
	categoryAdapter.registerDataSetObserver(new DataSetObserver() {
	    @Override
	    public void onChanged() {
		Log.d(TAG, ".DataSetObserver.onChanged");
		long selectedCategory =
			prefs.getLong(TPREF_SELECTED_CATEGORY, -1);
		if (categoryList.getSelectedItemId() != selectedCategory) {
		    Log.w(TAG, "The category ID at the selected position has changed!");
		    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB)
			ToDoListActivity.this.setCategorySpinnerByID(selectedCategory);
		}
	    }
	    @Override
	    public void onInvalidated() {
		Log.d(TAG, ".DataSetObserver.onInvalidated");
		categoryList.setSelection(0);
	    }
	});

	// In case this is the first time being run after installation
	// or upgrade, start up the alarm service.
	Intent alarmIntent = new Intent(this, AlarmService.class);
	alarmIntent.setAction(Intent.ACTION_MAIN);
	startService(alarmIntent);

	// Register this service's data set observer
	getContentResolver().registerContentObserver(
		ToDoItem.CONTENT_URI, true, registeredObserver);

	Log.d(TAG, ".onCreate finished.");
    }

    /** Called when the activity is about to be started after having been stopped */
    @SuppressWarnings("unchecked")
    @Override
    public void onRestart() {
	Log.d(TAG, ".onRestart");
	if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) &&
		(categoryLoaderCallbacks instanceof LoaderManager.LoaderCallbacks)) {
	    getLoaderManager().restartLoader(ToDoCategory.CONTENT_TYPE.hashCode(),
		    null, (LoaderManager.LoaderCallbacks<Cursor>) categoryLoaderCallbacks);
	    getLoaderManager().restartLoader(ToDoItem.CONTENT_TYPE.hashCode(),
		    null, (LoaderManager.LoaderCallbacks<Cursor>) itemLoaderCallbacks);
	}
	super.onRestart();
    }

    /** Called when the activity is about to be started */
    @Override
    public void onStart() {
	Log.d(TAG, ".onStart");
	super.onStart();
    }

    /** Called when the activity is ready for user interaction */
    @Override
    public void onResume() {
	Log.d(TAG, ".onResume");
	super.onResume();
    }

    /** Called when the activity has lost focus. */
    @Override
    public void onPause() {
	Log.d(TAG, ".onPause");
	super.onPause();
    }

    /** Called when the activity is obscured by another activity. */
    @Override
    public void onStop() {
	Log.d(TAG, ".onStop");
	super.onStop();
    }

    /** Called when the activity is about to be destroyed */
    @Override
    public void onDestroy() {
	getContentResolver().unregisterContentObserver(registeredObserver);
	StringEncryption.releaseGlobalEncryption(this);
	super.onDestroy();
    }

    /**
     * Generate the WHERE clause for the list query.
     * This is used in both onCreate and onSharedPreferencesChanged.
     */
    private String generateWhereClause() {
	StringBuilder whereClause = new StringBuilder();
	if (!prefs.getBoolean(TPREF_SHOW_CHECKED, false)) {
	    whereClause.append(ToDoItem.CHECKED).append(" = 0")
		.append(" AND (").append(ToDoItem.HIDE_DAYS_EARLIER)
		.append(" IS NULL OR (").append(ToDoItem.DUE_TIME).append(" - ")
		.append(ToDoItem.HIDE_DAYS_EARLIER).append(" * 86400000 < ")
		.append(System.currentTimeMillis()).append("))");
	}
	if (!prefs.getBoolean(TPREF_SHOW_PRIVATE, false)) {
	    if (whereClause.length() > 0)
		whereClause.append(" AND ");
	    whereClause.append(ToDoItem.PRIVATE).append(" = 0");
	}
	long selectedCategory = prefs.getLong(TPREF_SELECTED_CATEGORY, -1);
	if (selectedCategory >= 0) {
	    if (whereClause.length() > 0)
		whereClause.append(" AND ");
	    whereClause.append(ToDoItem.CATEGORY_ID).append(" = ")
		.append(selectedCategory);
        }
	return whereClause.toString();
    }

    /** Event listener for the New button */
    class NewButtonListener implements View.OnClickListener {
	@Override
	public void onClick(View v) {
	    Log.d(TAG, ".NewButtonListener.onClick");
	    ContentValues values = new ContentValues();
	    // This is the only time an empty description is allowed
	    values.put(ToDoItem.DESCRIPTION, "");
	    long selectedCategory = prefs.getLong(
		    TPREF_SELECTED_CATEGORY, ToDoCategory.UNFILED);
	    if (selectedCategory < 0)
		selectedCategory = ToDoCategory.UNFILED;
	    values.put(ToDoItem.CATEGORY_ID, selectedCategory);
	    Uri itemUri = getContentResolver().insert(todoUri, values);

	    // Immediately bring up the details dialog
	    // until I figure out how
	    // To do: proper in-line editing of item descriptions.
	    Intent intent = new Intent(v.getContext(),
		    ToDoDetailsActivity.class);
	    intent.setData(itemUri);
	    v.getContext().startActivity(intent);
	}
    }

    /** Event listener for the category filter */
    class CategorySpinnerListener
	implements AdapterView.OnItemSelectedListener {

	private int lastSelectedPosition = 0;

	/**
	 * Called when a category filter is selected.
	 *
	 * @param parent the Spinner containing the selected item
	 * @param v the drop-down item which was selected
	 * @param position the position of the selected item
	 * @param rowID the ID of the data shown in the selected item
	 */
	@Override
	public void onItemSelected(AdapterView<?> parent, View v,
		int position, long rowID) {
	    Log.d(TAG, ".CategorySpinnerListener.onItemSelected(p="
		    + position + ",id=" + rowID + ")");
	    if (position == 0) {
		prefs.edit().putLong(TPREF_SELECTED_CATEGORY, -1).commit();
	    }
	    else if (position == parent.getCount() - 1) {
		// This must be the "Edit categories..." button.
		// We don't keep this selection; instead, start
		// the EditCategoriesActivity and revert the selection.
		position = lastSelectedPosition;
		parent.setSelection(lastSelectedPosition);
		// To do: Dismiss the spinner
		Intent intent = new Intent(parent.getContext(),
			CategoryListActivity.class);
		// To do: find out why this doesn't do anything.
		parent.getContext().startActivity(intent);
	    }
	    else {
		prefs.edit().putLong(TPREF_SELECTED_CATEGORY, rowID).commit();
	    }
	    lastSelectedPosition = position;
	}

	/** Called when the current selection disappears */
	@Override
	public void onNothingSelected(AdapterView<?> parent) {
	    Log.d(TAG, ".CategorySpinnerListener.onNothingSelected()");
	    /* // Remove the filter
	    lastSelectedPosition = 0;
	    parent.setSelection(0);
	    prefs.edit().putLong(TPREF_SELECTED_CATEGORY, -1).commit(); */
	}
    }

    /** Look up the spinner item corresponding to a category ID and select it. */
    void setCategorySpinnerByID(long id) {
	Log.w(TAG, "Changing category spinner to item " + id
		+ " of " + categoryList.getCount());
	for (int position = 0; position < categoryList.getCount(); position++) {
	    if (categoryList.getItemIdAtPosition(position) == id) {
		categoryList.setSelection(position);
		return;
	    }
	}
	Log.w(TAG, "No spinner item found for category ID " + id);
	categoryList.setSelection(0);
    }

    /** Called when the settings dialog stores a new user setting */
    @Override
    public void onSharedPreferenceChanged(
	    SharedPreferences prefs, String key) {
        Log.d(TAG, ".onSharedPreferenceChanged(\"" + key + "\")");
	if (key.equals(TPREF_SHOW_CHECKED) || key.equals(TPREF_SHOW_PRIVATE) ||
		key.equals(TPREF_SELECTED_CATEGORY) ||
		key.equals(TPREF_SORT_ORDER)) {
	    String whereClause = generateWhereClause();

	    int selectedSortOrder = prefs.getInt(TPREF_SORT_ORDER, 0);
	    if ((selectedSortOrder < 0) ||
		    (selectedSortOrder >= ToDoItem.USER_SORT_ORDERS.length))
		selectedSortOrder = 0;

	    Log.d(TAG, ".onSharedPreferenceChanged: requerying the data where "
		    + whereClause.toString());
	    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
		Cursor itemCursor = managedQuery(todoUri,
			ITEM_PROJECTION, whereClause.toString(), null,
			ToDoItem.USER_SORT_ORDERS[selectedSortOrder]);
		// Change the cursor used by this list
		itemAdapter.changeCursor(itemCursor);
	    } else {
		getLoaderManager().restartLoader(ToDoItem.CONTENT_TYPE.hashCode(),
			null, (LoaderManager.LoaderCallbacks<Cursor>) itemLoaderCallbacks);
	    }
	}
	else if (key.equals(TPREF_SHOW_CATEGORY) ||
		key.equals(TPREF_SHOW_DUE_DATE) ||
		key.equals(TPREF_SHOW_PRIORITY)) {
	    // To do: is there another way to do this?
	    // The data has not actually changed, just the widget visibility.
	    Log.d(TAG, ".onSharedPreferenceChanged: signaling a data change");
	    itemAdapter.notifyDataSetChanged();
	}
	// To do: etc...
    }

    /** Called when the user presses the Menu button. */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        Log.d(TAG, "onCreateOptionsMenu");
	getMenuInflater().inflate(R.menu.main_menu, menu);
	menu.findItem(R.id.menuSettings).setIntent(
		new Intent(this, PreferencesActivity.class));
	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
	    menu.findItem(R.id.menuShowCompleted).setShowAsAction(
		    MenuItem.SHOW_AS_ACTION_IF_ROOM);
        return true;
    }

    /** Called when the user selects a menu item. */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
	switch (item.getItemId()) {
	default:
	    Log.w(TAG, "onOptionsItemSelected(" + item.getItemId()
		    + "): Not handled");
	    return false;

	case R.id.menuShowCompleted:
	    prefs.edit().putBoolean(TPREF_SHOW_CHECKED,
		    !prefs.getBoolean(TPREF_SHOW_CHECKED, false)).commit();
	    return true;

	case R.id.menuInfo:
	    showDialog(ABOUT_DIALOG_ID);
	    return true;

	case R.id.menuExport:
	    Intent intent = new Intent(this, ExportActivity.class);
	    startActivity(intent);
	    return true;

	case R.id.menuImport:
	    intent = new Intent(this, ImportActivity.class);
	    startActivity(intent);
	    return true;

	case R.id.menuPassword:
	    showDialog(PASSWORD_DIALOG_ID);
	    return true;
	}
    }

    /** Called when opening a dialog for the first time */
    @Override
    public Dialog onCreateDialog(int id) {
	switch (id) {
	case ABOUT_DIALOG_ID:
	    AlertDialog.Builder builder = new AlertDialog.Builder(this);
	    builder.setTitle(R.string.about);
	    builder.setMessage(getText(R.string.InfoPopupText));
	    builder.setCancelable(true);
	    builder.setNeutralButton(R.string.InfoButtonOK,
		    new DialogInterface.OnClickListener() {
		public void onClick(DialogInterface dialog, int i) {
		    dialog.dismiss();
		}
	    });
	    return builder.create();

	case DUEDATE_LIST_ID:
	    Resources r = getResources();
	    String[] dueDateOptionFormats =
		r.getStringArray(R.array.DueDateFormatList);
	    String[] dueDateListItems =
		new String[dueDateOptionFormats.length + 2];
	    Calendar c = Calendar.getInstance();
	    for (int i = 0; i < dueDateOptionFormats.length; i++) {
		SimpleDateFormat formatter =
		    new SimpleDateFormat(dueDateOptionFormats[i],
			    Locale.getDefault());
		dueDateListItems[i] = formatter.format(c.getTime());
		c.add(Calendar.DATE, 1);
	    }
	    dueDateListItems[dueDateOptionFormats.length] =
		r.getString(R.string.DueDateNoDate);
	    dueDateListItems[dueDateOptionFormats.length + 1] =
		r.getString(R.string.DueDateOther);
	    builder = new AlertDialog.Builder(this);
	    builder.setItems(dueDateListItems,
		    new DueDateListSelectionListener());
	    dueDateListDialog = builder.create();
	    return dueDateListDialog;

	case DUEDATE_DIALOG_ID:
	    dueDateDialog = new CalendarDatePickerDialog(this,
		    getText(R.string.DatePickerTitleDueDate),
		    new CalendarDatePickerDialog.OnDateSetListener() {
		@Override
		public void onDateSet(CalendarDatePicker dp,
			int year, int month, int day) {
		    Uri todoItemUri = itemAdapter.getSelectedItemUri();
		    Log.d(TAG, "dueDateDialog.onDateSet(" + year + ","
			    + month + "," + day + ")");
		    Calendar c = new GregorianCalendar(year, month, day);
		    c.set(Calendar.HOUR_OF_DAY, 23);
		    c.set(Calendar.MINUTE, 59);
		    c.set(Calendar.SECOND, 59);
		    ContentValues values = new ContentValues();
		    values.put(ToDoItem.DUE_TIME, c.getTimeInMillis());
		    values.put(ToDoItem.MOD_TIME, System.currentTimeMillis());
		    try {
			getContentResolver().update(
				todoItemUri, values, null, null);
		    } catch (SQLException sx) {
			new AlertDialog.Builder(ToDoListActivity.this)
			.setMessage(sx.getMessage())
			.setIcon(android.R.drawable.ic_dialog_alert)
			.setNeutralButton(R.string.ConfirmationButtonCancel,
				new DialogInterface.OnClickListener() {
			    @Override
			    public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			    }
			}).create().show();
		    }
		}
	    });
	    return dueDateDialog;

	case PASSWORD_DIALOG_ID:
	    builder = new AlertDialog.Builder(this);
	    builder.setIcon(R.drawable.ic_menu_login);
	    builder.setTitle(R.string.MenuPasswordSet);
	    View passwordLayout =
		((LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE))
		.inflate(R.layout.password,
			(ScrollView) findViewById(R.id.PasswordLayoutRoot));
	    builder.setView(passwordLayout);
	    DialogInterface.OnClickListener listener =
		new PasswordChangeOnClickListener();
	    builder.setPositiveButton(R.string.ConfirmationButtonOK, listener);
	    builder.setNegativeButton(R.string.ConfirmationButtonCancel, listener);
	    passwordChangeDialog = builder.create();
	    CheckBox showPasswordCheckBox =
		(CheckBox) passwordLayout.findViewById(R.id.CheckBoxShowPassword);
	    passwordChangeEditText[0] =
		(EditText) passwordLayout.findViewById(R.id.EditTextOldPassword);
	    passwordChangeEditText[1] =
		(EditText) passwordLayout.findViewById(R.id.EditTextNewPassword);
	    passwordChangeEditText[2] =
		(EditText) passwordLayout.findViewById(R.id.EditTextConfirmPassword);
	    showPasswordCheckBox.setOnCheckedChangeListener(
		    new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton button,
				boolean state) {
			    int inputType = InputType.TYPE_CLASS_TEXT
				+ (state ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
					 : InputType.TYPE_TEXT_VARIATION_PASSWORD);
			    passwordChangeEditText[0].setInputType(inputType);
			    passwordChangeEditText[1].setInputType(inputType);
			    passwordChangeEditText[2].setInputType(inputType);
			}
	    });
	    int inputType = InputType.TYPE_CLASS_TEXT
		+ (showPasswordCheckBox.isChecked()
			? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
			: InputType.TYPE_TEXT_VARIATION_PASSWORD);
	    passwordChangeEditText[0].setInputType(inputType);
	    passwordChangeEditText[1].setInputType(inputType);
	    passwordChangeEditText[2].setInputType(inputType);
	    return passwordChangeDialog;

	case PROGRESS_DIALOG_ID:
	    progressDialog = new ProgressDialog(this);
	    progressDialog.setCancelable(false);
	    progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
	    progressDialog.setMessage("...");
	    progressDialog.setMax(100);
	    progressDialog.setProgress(0);
	    return progressDialog;

	default:
	    Log.d(TAG, ".onCreateDialog(" + id + "): undefined dialog ID");
	    return null;
	}
    }

    /** Called each time a dialog is shown */
    @Override
    public void onPrepareDialog(int id, Dialog dialog) {
	switch (id) {
	case DUEDATE_DIALOG_ID:
	    final Uri itemUri = itemAdapter.getSelectedItemUri();
	    if (itemUri == null) {
		Log.w(TAG, "Due date dialog being prepared with no item selected");
		return;
	    }
	    Uri todoItemUri = itemAdapter.getSelectedItemUri();
	    Cursor itemCursor = getContentResolver().query(todoItemUri,
		    ITEM_PROJECTION, null, null, null);
	    if (!itemCursor.moveToFirst())
		throw new SQLiteDoneException();
	    int i = itemCursor.getColumnIndex(ToDoItem.DUE_TIME);
	    Calendar c = Calendar.getInstance();
	    if (!itemCursor.isNull(i)) {
		c.setTime(new Date(itemCursor.getLong(i)));
	    }
	    itemCursor.close();
	    dueDateDialog.setDate(c.get(Calendar.YEAR),
		    c.get(Calendar.MONTH), c.get(Calendar.DATE));
	    return;

	case PASSWORD_DIALOG_ID:
	    TableRow tr = (TableRow) passwordChangeDialog.findViewById(
		    R.id.TableRowOldPassword);
	    tr.setVisibility(encryptor.hasPassword(getContentResolver())
		    ? View.VISIBLE : View.GONE);
	    CheckBox showPasswordCheckBox =
		(CheckBox) passwordChangeDialog.findViewById(
			R.id.CheckBoxShowPassword);
	    showPasswordCheckBox.setChecked(false);
	    passwordChangeEditText[0].setText("");
	    passwordChangeEditText[1].setText("");
	    passwordChangeEditText[2].setText("");
	    return;

	case PROGRESS_DIALOG_ID:
	    if (progressService != null) {
		Log.d(TAG, ".onPrepareDialog(PROGRESS_DIALOG_ID):"
			+ " Initializing the progress dialog at "
			+ progressService.getCurrentMode() + " "
			+ progressService.getChangedCount() + "/"
			+ progressService.getMaxCount());
		final String oldMessage = progressService.getCurrentMode();
		final int oldMax = progressService.getMaxCount();
		progressDialog.setMessage(oldMessage);
		if (oldMax > 0) {
		    progressDialog.setIndeterminate(false);
		    progressDialog.setMax(oldMax);
		    progressDialog.setProgress(progressService.getChangedCount());
		} else {
		    progressDialog.setIndeterminate(true);
		}

		// Set up a callback to update the dialog
		final Handler progressHandler = new Handler();
		progressHandler.postDelayed(new Runnable() {
		    @Override
		    public void run() {
			if (progressService != null) {
			    String newMessage = progressService.getCurrentMode();
			    int newMax = progressService.getMaxCount();
			    int newProgress = progressService.getChangedCount();
			    Log.d(TAG, ".onPrepareDialog(PROGRESS_DIALOG_ID).Runnable:"
				    + " Updating the progress dialog to "
				    + newMessage + " " + newProgress + "/" + newMax);
			    if (oldMessage.equals(newMessage) &&
				    ((oldMax > 0) == (newMax > 0))) {
				progressDialog.setMax(newMax);
				progressDialog.setProgress(newProgress);
				progressHandler.postDelayed(this, 100);
			    } else {
				// Work around a bug in ProgressDialog.setMessage
				progressDialog.dismiss();
				showDialog(PROGRESS_DIALOG_ID);
			    }
			}
		    }
		}, 250);
	    } else {
		Log.d(TAG, ".onPrepareDialog(PROGRESS_DIALOG_ID):"
			+ " Password service has disappeared;"
			+ " dismissing the progress dialog");
		progressDialog.dismiss();
	    }
	    return;
	}
    }

    class DueDateListSelectionListener
		implements DialogInterface.OnClickListener {
	@Override
	public void onClick(DialogInterface dialog, int which) {
	    Log.d(TAG, "DueDateListSelectionListener.onClick(" + which + ")");
	    Uri todoItemUri = itemAdapter.getSelectedItemUri();
	    ContentValues values = new ContentValues();
	    switch (which) {
	    default:
		Calendar c = Calendar.getInstance();
		c.add(Calendar.DATE, which);
		c.set(Calendar.HOUR_OF_DAY, 23);
		c.set(Calendar.MINUTE, 59);
		c.set(Calendar.SECOND, 59);
		values.put(ToDoItem.DUE_TIME, c.getTimeInMillis());
		break;

	    case 8:	// No date
		values.putNull(ToDoItem.DUE_TIME);
		break;

	    case 9:	// Other
		showDialog(DUEDATE_DIALOG_ID);
		return;
	    }
	    values.put(ToDoItem.MOD_TIME, System.currentTimeMillis());

	    try {
		getContentResolver().update(todoItemUri, values, null, null);
	    } catch (SQLException sx) {
		new AlertDialog.Builder(ToDoListActivity.this)
		.setMessage(sx.getMessage())
		.setIcon(android.R.drawable.ic_dialog_alert)
		.setNeutralButton(R.string.ConfirmationButtonCancel,
			new DialogInterface.OnClickListener() {
		    @Override
		    public void onClick(DialogInterface dialog, int which) {
			dialog.dismiss();
		    }
		}).create().show();
	    }
	}
    }

    class PasswordChangeOnClickListener
		implements DialogInterface.OnClickListener {
	@Override
	public void onClick(DialogInterface dialog, int which) {
	    Log.d(TAG, "PasswordChangeOnClickListener.onClick(" + which + ")");
	    switch (which) {
	    case DialogInterface.BUTTON_NEGATIVE:
		dialog.dismiss();
		return;

	    case DialogInterface.BUTTON_POSITIVE:
		Intent passwordChangeIntent = new Intent(ToDoListActivity.this,
			PasswordChangeService.class);
		char[] newPassword =
		    new char[passwordChangeEditText[1].length()];
		passwordChangeEditText[1].getText().getChars(
			0, newPassword.length, newPassword, 0);
		char[] confirmedPassword =
		    new char[passwordChangeEditText[2].length()];
		passwordChangeEditText[2].getText().getChars(
			0, confirmedPassword.length, confirmedPassword, 0);
		if (!Arrays.equals(newPassword, confirmedPassword)) {
		    Arrays.fill(confirmedPassword, (char) 0);
		    Arrays.fill(newPassword, (char) 0);
		    AlertDialog.Builder builder =
			new AlertDialog.Builder(ToDoListActivity.this);
		    builder.setIcon(android.R.drawable.ic_dialog_alert);
		    builder.setMessage(R.string.ErrorPasswordMismatch);
		    builder.setNeutralButton(R.string.ConfirmationButtonOK,
			    new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
			    dialog.dismiss();
			}
		    });
		    builder.show();
		    return;
		}
		Arrays.fill(confirmedPassword, (char) 0);
		if (newPassword.length > 0)
		    passwordChangeIntent.putExtra(
			    PasswordChangeService.EXTRA_NEW_PASSWORD,
			    newPassword);

		if (encryptor.hasPassword(getContentResolver())) {
		    char[] oldPassword =
			new char[passwordChangeEditText[0].length()];
		    passwordChangeEditText[0].getText().getChars(
			    0, oldPassword.length, oldPassword, 0);
		    StringEncryption oldEncryptor = new StringEncryption();
		    oldEncryptor.setPassword(oldPassword);
		    try {
			if (!oldEncryptor.checkPassword(getContentResolver())) {
			    Arrays.fill(newPassword, (char) 0);
			    Arrays.fill(oldPassword, (char) 0);
			    AlertDialog.Builder builder =
				new AlertDialog.Builder(ToDoListActivity.this);
			    builder.setIcon(android.R.drawable.ic_dialog_alert);
			    builder.setMessage(R.string.ToastBadPassword);
			    builder.setNeutralButton(R.string.ConfirmationButtonCancel,
				    new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
				    dialog.dismiss();
				}
			    });
			    builder.show();
			    return;
			}
		    } catch (GeneralSecurityException gsx) {
			Arrays.fill(newPassword, (char) 0);
			Arrays.fill(oldPassword, (char) 0);
			new AlertDialog.Builder(ToDoListActivity.this)
			.setMessage(gsx.getMessage())
			.setIcon(android.R.drawable.ic_dialog_alert)
			.setNeutralButton(R.string.ConfirmationButtonCancel,
				new DialogInterface.OnClickListener() {
			    @Override
			    public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			    }
			}).create().show();
			return;
		    }
		    passwordChangeIntent.putExtra(
			    PasswordChangeService.EXTRA_OLD_PASSWORD, oldPassword);
		}

		passwordChangeIntent.setAction(
			PasswordChangeService.ACTION_CHANGE_PASSWORD);
		dialog.dismiss();
		Log.d(TAG, "PasswordChangeOnClickListener.onClick:"
			+ " starting the password change service");
		startService(passwordChangeIntent);
		// Bind to the service
		Log.d(TAG, "PasswordChangeOnClickListener.onClick:"
			+ " binding to the password change service");
		bindService(passwordChangeIntent,
			new PasswordChangeServiceConnection(), 0);
		return;
	    }
	}
    }

    class PasswordChangeServiceConnection implements ServiceConnection {
	public void onServiceConnected(ComponentName name, IBinder service) {
	    try {
		Log.d(TAG, ".onServiceConnected(" + name.getShortClassName()
			+ "," + service.getInterfaceDescriptor() + ")");
	    } catch (RemoteException rx) {}
	    PasswordChangeService.PasswordBinder pbinder =
		(PasswordChangeService.PasswordBinder) service;
	    progressService = pbinder.getService();
	    showDialog(PROGRESS_DIALOG_ID);
	}

	/** Called when a connection to the service has been lost */
	public void onServiceDisconnected(ComponentName name) {
	    Log.d(TAG, ".onServiceDisconnected(" + name.getShortClassName() + ")");
	    if (progressDialog != null)
		progressDialog.dismiss();
	    progressService = null;
	    unbindService(this);
	}
    }
}
