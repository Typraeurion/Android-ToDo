/*
 * Copyright © 2011–2025 Trevin Beattie
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
package com.xmission.trevin.android.todo.ui;

import static com.xmission.trevin.android.todo.service.AlarmService.EXTRA_ITEM_CATEGORY_ID;
import static com.xmission.trevin.android.todo.service.AlarmService.EXTRA_ITEM_ID;

import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.xmission.trevin.android.todo.R;
import com.xmission.trevin.android.todo.data.ToDoPreferences;
import com.xmission.trevin.android.todo.util.StringEncryption;
import com.xmission.trevin.android.todo.provider.ToDo.*;
import com.xmission.trevin.android.todo.provider.ToDoProvider;
import com.xmission.trevin.android.todo.service.AlarmService;
import com.xmission.trevin.android.todo.service.PasswordChangeService;
import com.xmission.trevin.android.todo.service.ProgressReportingService;

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
public class ToDoListActivity extends ListActivity {

    private static final String TAG = "ToDoListActivity";

    private static final int ABOUT_DIALOG_ID = 1;
    private static final int DUEDATE_LIST_ID = 2;
    private static final int DUEDATE_DIALOG_ID = 7;
    private static final int PASSWORD_DIALOG_ID = 8;
    private static final int PROGRESS_DIALOG_ID = 9;
    private static final int UNLOCK_DIALOG_ID = 10;

    /**
     * The columns we are interested in from the category table
     */
    static final String[] CATEGORY_PROJECTION = new String[] {
            ToDoCategory._ID,
            ToDoCategory.NAME,
    };

    /**
     * The columns we are interested in from the item table
     */
    static final String[] ITEM_PROJECTION = new String[] {
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
    private ToDoPreferences prefs;

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

    /** Label for the preferences option "Alarm vibrate" */
    public static final String TPREF_NOTIFICATION_VIBRATE = "NotificationVibrate";

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

    /**
     * Local copy of whether a password has been set.
     * This needs to be updated whenever the password hash
     * metadata is added or removed.
     */
    boolean hasPassword = false;

    /** Our main menu */
    Menu menu = null;

    /** &ldquo;Unlock Encrypted&rdquo; dialog */
    Dialog unlockDialog = null;

    /** Text field of the unlock dialog */
    EditText unlockPasswordEditText = null;

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

    /**
     * Keep track of changes so that we can
     * update any alarms and update UI elements
     * related to setting or clearing the password.
     */
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
            checkForPassword.run();
	}
    }
    private final ToDoContentObserver registeredObserver =
	new ToDoContentObserver();

    /** Category Loader callbacks */
    private CategoryLoaderCallbacks categoryLoaderCallbacks = null;

    /** Item Loader callbacks for */
    private ItemLoaderCallbacks itemLoaderCallbacks = null;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, ".onCreate");

        setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);

        // If no data was given in the intent (because we were started
	// as a MAIN activity), then use our default content provider.
	Intent intent = getIntent();
	if (intent.getData() == null) {
            Log.d(TAG, String.format("No intent data; defaulting to %s",
                    ToDoItem.CONTENT_URI.toString()));
	    intent.setData(ToDoItem.CONTENT_URI);
	    todoUri = ToDoItem.CONTENT_URI;
	    categoryUri = ToDoCategory.CONTENT_URI;
	} else {
            Log.d(TAG, String.format("intent data = %s: %s",
                    intent.getAction(), intent.getDataString()));
            // Fix Me: what are the other actions that could lead here?
	    todoUri = intent.getData();
	    categoryUri = todoUri.buildUpon().encodedPath("/categories").build();
	}

	encryptor = StringEncryption.holdGlobalEncryption();
	prefs = ToDoPreferences.getInstance(this);
        prefs.registerOnToDoPreferenceChangeListener(
                new ToDoPreferences.OnToDoPreferenceChangeListener() {
                    @Override
                    public void onToDoPreferenceChanged(ToDoPreferences prefs) {
                        updateListFilter();
                        if (menu != null) {
                            MenuItem unlockItem =
                                    menu.findItem(R.id.menuUnlock);
                            unlockItem.setTitle(encryptor.hasKey()
                                    ? R.string.MenuLock
                                    : R.string.MenuUnlock);
                            unlockItem.setVisible(
                                    hasPassword && prefs.showPrivate());
                        }
                    }
                },
                TPREF_SHOW_CHECKED,
                TPREF_SHOW_ENCRYPTED, TPREF_SHOW_PRIVATE,
                TPREF_SELECTED_CATEGORY, TPREF_SORT_ORDER);
        prefs.registerOnToDoPreferenceChangeListener(
                new ToDoPreferences.OnToDoPreferenceChangeListener() {
                    @Override
                    public void onToDoPreferenceChanged(ToDoPreferences prefs) {
                        updateListView();
                    }
                },
                TPREF_SHOW_CATEGORY, TPREF_SHOW_DUE_DATE, TPREF_SHOW_PRIORITY);

        int selectedSortOrder = prefs.getSortOrder();
        if ((selectedSortOrder < 0) ||
        	(selectedSortOrder >= ToDoItem.USER_SORT_ORDERS.length)) {
            prefs.setSortOrder(0);
	}

        // To Do: When we switch from Content Resolver to a direct repository,
        // replace this with checkForPassword on a separate thread.
        hasPassword = encryptor.hasPassword(getContentResolver());

	/*
	 * Perform two managed queries.
	 * On API level ≥ 11, you need to find a way to re-initialize
	 * the cursor when the activity is restarted!
	 */
        categoryAdapter = new CategoryFilterCursorAdapter(this, 0);
        Log.d(TAG, ".onCreate: initializing a category loader manager");
        if (Log.isLoggable(TAG, Log.DEBUG))
            LoaderManager.enableDebugLogging(true);
        categoryLoaderCallbacks = new CategoryLoaderCallbacks(this,
                prefs, categoryAdapter, categoryUri);
        getLoaderManager().initLoader(ToDoCategory.CONTENT_TYPE.hashCode(),
                null, categoryLoaderCallbacks);

        itemAdapter = new ToDoCursorAdapter(
                this, R.layout.list_item, null,
                getContentResolver(), todoUri, this, encryptor,
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE));
        Log.d(TAG, ".onCreate: initializing a To Do item loader manager");
        itemLoaderCallbacks = new ItemLoaderCallbacks(this,
                prefs, itemAdapter, todoUri);
        getLoaderManager().initLoader(ToDoItem.CONTENT_TYPE.hashCode(),
                null, itemLoaderCallbacks);

        // Inflate our view so we can find our lists
	setContentView(R.layout.list);

        categoryAdapter.setDropDownViewResource(
        	R.layout.simple_spinner_dropdown_item);
        categoryList = (Spinner) findViewById(R.id.ListSpinnerCategory);
        categoryList.setAdapter(categoryAdapter);

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
		long selectedCategory = prefs.getSelectedCategory();
		if (categoryList.getSelectedItemId() != selectedCategory) {
		    Log.w(TAG, "The category ID at the selected position has changed!");
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
    @Override
    public void onRestart() {
	Log.d(TAG, ".onRestart");
	if (categoryLoaderCallbacks != null) {
            getLoaderManager().restartLoader(ToDoCategory.CONTENT_TYPE.hashCode(),
		    null, categoryLoaderCallbacks);
	    getLoaderManager().restartLoader(ToDoItem.CONTENT_TYPE.hashCode(),
		    null, itemLoaderCallbacks);
	}
	super.onRestart();
    }

    /** Called when the activity is about to be started */
    @Override
    public void onStart() {
	Log.d(TAG, ".onStart");
	super.onStart();

        // Check whether we were called with a specific item,
        // e.g. by the user clicking on an alarm notification.
        showItemFromIntent(getIntent());
    }

    /**
     * Called when the activity is already active but is being called
     * again with another {@link Intent}.  This may be due to starting
     * it from an alarm notification, in which case we need to update
     * the activity&rsquo;s Intent; otherwise {@link #getIntent()}
     * will still return the original Intent instead of using the new
     * one.
     * <p>
     * See also:
     * <a href="https://stackoverflow.com/a/34086257/13442812">Android
     * intent extra data is lost</a> on StackOverflow.
     * </p>
     */
    @Override
    protected void onNewIntent(Intent newIntent) {
        Log.d(TAG, String.format(".onNewIntent(%s, extras=%s)",
                newIntent.getAction(), newIntent.getExtras()));
        super.onNewIntent(newIntent);
        setIntent(newIntent);
        showItemFromIntent(newIntent);
    }

    /**
     * Update the view if necessary to ensure a particular To Do item is shown,
     * if the given {@link Intent} includes a category and item ID.
     *
     * @param intent the {link Intent} by which this activity was called
     */
    private void showItemFromIntent(Intent intent) {
        if (intent.hasExtra(AlarmService.EXTRA_ITEM_ID)) {
            long categoryId = intent.getLongExtra(EXTRA_ITEM_CATEGORY_ID, -1);
            long itemId = intent.getLongExtra(EXTRA_ITEM_ID, -1);
            if ((categoryId >= 0) && (itemId >= 0))
                showItemIfNeeded(categoryId, itemId);
        }
    }

    /**
     * Update the view if necessary to ensure a particular To Do item is shown.
     *
     * @param categoryId the ID of the category which should be shown.  If
     *        the current category is &ldquo;All&rdquo; or this category,
     *        we do nothing; otherwise change the category to this value.
     * @param itemId the ID of the To Do item which should be visible.
     *        <i>(Currently does nothing; Fix Me)</i>
     */
    private void showItemIfNeeded(long categoryId, long itemId) {
        long selectedCategory = prefs.getSelectedCategory();
        if ((selectedCategory >= 0) && (categoryId != selectedCategory)) {
            Log.d(TAG, String.format("Changing the selected category from %d to %d",
                    selectedCategory, categoryId));
            // Switch to the category this item is in
            setCategorySpinnerByID(categoryId);
            prefs.setSelectedCategory(categoryId);
        }

        //ListView listView = getListView();
        Log.d(TAG, String.format("Target item for display is %d", itemId));
        // Fix Me: We ought to scroll to the item that was in the
        // notification, but we have to make sure the cursor's WHERE
        // clause is update first.
        //listView.smoothScrollToPosition(?);
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
    // FIX ME: SQL should be moved to the data layer
    public String generateWhereClause() {
	StringBuilder whereClause = new StringBuilder();
	if (!prefs.showChecked()) {
	    whereClause.append(ToDoItem.CHECKED).append(" = 0")
		.append(" AND (").append(ToDoItem.HIDE_DAYS_EARLIER)
		.append(" IS NULL OR (").append(ToDoItem.DUE_TIME).append(" - ")
		.append(ToDoItem.HIDE_DAYS_EARLIER).append(" * 86400000 < ")
		.append(System.currentTimeMillis()).append("))");
	}
	if (!prefs.showPrivate()) {
	    if (whereClause.length() > 0)
		whereClause.append(" AND ");
	    whereClause.append(ToDoItem.PRIVATE).append(" = 0");
	}
	long selectedCategory = prefs.getSelectedCategory();
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
	    long selectedCategory = prefs.getSelectedCategory();
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
		prefs.setSelectedCategory(ToDoPreferences.ALL_CATEGORIES);
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
		prefs.setSelectedCategory(rowID);
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

    /**
     * Called when the settings dialog changes a preference related to
     * filtering the To Do list
     */
    public void updateListFilter() {
        String whereClause = generateWhereClause();

        int selectedSortOrder = prefs.getSortOrder();
        if ((selectedSortOrder < 0) ||
                (selectedSortOrder >= ToDoItem.USER_SORT_ORDERS.length))
            selectedSortOrder = 0;

        Log.d(TAG, ".updateListFilter: requerying the data where "
                + whereClause + " ordered by "
                + ToDoItem.USER_SORT_ORDERS[selectedSortOrder]);
        getLoaderManager().restartLoader(ToDoItem.CONTENT_TYPE.hashCode(),
                null, itemLoaderCallbacks);
        itemAdapter.notifyDataSetChanged();
    }

    /**
     * Called when the settings dialog changes whether categories,
     * priorities, and/or due dates are displayed alongside the To Do items.
     */
    public void updateListView() {
        // To do: is there another way to do this?
        // The data has not actually changed, just the widget visibility.
        Log.d(TAG, ".updateListView: signaling a data change");
        itemAdapter.notifyDataSetChanged();
    }

    /** Called when the user presses the Menu button. */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        Log.d(TAG, "onCreateOptionsMenu");
	getMenuInflater().inflate(R.menu.menu_main, menu);
        MenuItem unlockItem = menu.findItem(R.id.menuUnlock);
        unlockItem.setVisible(hasPassword && prefs.showPrivate());
        unlockItem.setTitle(encryptor.hasKey()
                ? R.string.MenuLock
                : R.string.MenuUnlock);
        menu.findItem(R.id.menuPassword).setTitle(hasPassword
                ? R.string.MenuPasswordChange : R.string.MenuPasswordSet);
	menu.findItem(R.id.menuSettings).setIntent(
		new Intent(this, PreferencesActivity.class));
        menu.findItem(R.id.menuShowCompleted).setShowAsAction(
                MenuItem.SHOW_AS_ACTION_IF_ROOM);
        this.menu = menu;
        return true;
    }

    /** Called when the user selects a menu item. */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
	if (item.getItemId() == R.id.menuShowCompleted) {
	    prefs.setShowChecked(!prefs.showChecked());
	    return true;
        }

        if (item.getItemId() == R.id.menuInfo) {
	    showDialog(ABOUT_DIALOG_ID);
	    return true;
        }

        if (item.getItemId() == R.id.menuUnlock) {
            if (encryptor.hasKey()) {
                prefs.setShowEncrypted(false);
                encryptor.forgetPassword();
                if (unlockPasswordEditText != null)
                    unlockPasswordEditText.setText("");
                menu.findItem(R.id.menuUnlock).setTitle(R.string.MenuUnlock);
            } else {
                showDialog(UNLOCK_DIALOG_ID);
            }
            return true;
        }

        if (item.getItemId() == R.id.menuExport) {
	    Intent intent = new Intent(this, ExportActivity.class);
	    startActivity(intent);
	    return true;
        }

        if (item.getItemId() == R.id.menuImport) {
	    Intent intent = new Intent(this, ImportActivity.class);
	    startActivity(intent);
	    return true;
        }

        if (item.getItemId() == R.id.menuPassword) {
	    showDialog(PASSWORD_DIALOG_ID);
	    return true;
        }

        Log.w(TAG, "onOptionsItemSelected(" + item.getItemId()
                + "): Not handled");
        return false;
    }

    private final CompoundButton.OnCheckedChangeListener unlockShowPasswordListener =
            new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton button,
                        boolean state) {
                    int inputType = InputType.TYPE_CLASS_TEXT
                        + (state ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                            : InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    unlockPasswordEditText.setInputType(inputType);
                }
    };

    private final CompoundButton.OnCheckedChangeListener changeShowPasswordListener =
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
    };

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

        case UNLOCK_DIALOG_ID:
            builder = new AlertDialog.Builder(this);
	    builder.setIcon(R.drawable.ic_menu_login);
	    builder.setTitle(R.string.MenuUnlock);
            View unlockLayout =
                ((LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE))
                .inflate(R.layout.unlock,
                        (ScrollView) findViewById(R.id.UnlockLayoutRoot));
            builder.setView(unlockLayout);
            final DialogInterface.OnClickListener listener1 =
                    new UnlockOnClickListener();
            builder.setPositiveButton(R.string.ConfirmationButtonOK, listener1);
            builder.setNegativeButton(R.string.ConfirmationButtonCancel, listener1);
            unlockDialog = builder.create();
            CheckBox showPasswordCheckBox1 =
                    (CheckBox) unlockLayout.findViewById(R.id.CheckBoxShowPassword);
            unlockPasswordEditText =
                    (EditText) unlockLayout.findViewById(R.id.EditTextPassword);
            showPasswordCheckBox1.setOnCheckedChangeListener(unlockShowPasswordListener);
            return unlockDialog;

	case PASSWORD_DIALOG_ID:
	    builder = new AlertDialog.Builder(this);
	    builder.setIcon(R.drawable.ic_menu_login);
	    builder.setTitle(hasPassword ? R.string.MenuPasswordChange
                    : R.string.MenuPasswordSet);
	    View passwordLayout =
		((LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE))
		.inflate(R.layout.password,
			(ScrollView) findViewById(R.id.PasswordLayoutRoot));
	    builder.setView(passwordLayout);
	    DialogInterface.OnClickListener listener2 =
		new PasswordChangeOnClickListener();
	    builder.setPositiveButton(R.string.ConfirmationButtonOK, listener2);
	    builder.setNegativeButton(R.string.ConfirmationButtonCancel, listener2);
	    passwordChangeDialog = builder.create();
            CheckBox showPasswordCheckBox2 =
                (CheckBox) passwordLayout.findViewById(R.id.CheckBoxShowPassword);
            passwordChangeEditText[0] =
                (EditText) passwordLayout.findViewById(R.id.EditTextOldPassword);
            passwordChangeEditText[1] =
                (EditText) passwordLayout.findViewById(R.id.EditTextNewPassword);
            passwordChangeEditText[2] =
                (EditText) passwordLayout.findViewById(R.id.EditTextConfirmPassword);
            showPasswordCheckBox2.setOnCheckedChangeListener(changeShowPasswordListener);
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

        case UNLOCK_DIALOG_ID:
            CheckBox showPasswordCheckBox1 =
                (CheckBox) unlockDialog.findViewById(
                        R.id.CheckBoxShowPassword);
            showPasswordCheckBox1.setChecked(false);
            unlockShowPasswordListener.onCheckedChanged(
                    showPasswordCheckBox1, false);
            if (encryptor.hasKey())
                unlockPasswordEditText.setText(encryptor.getPassword(), 0,
                        encryptor.getPassword().length);
            else
                unlockPasswordEditText.setText("");
            return;

	case PASSWORD_DIALOG_ID:
            passwordChangeDialog.setTitle(hasPassword
                    ? R.string.MenuPasswordChange : R.string.MenuPasswordSet);
	    TableRow tr = (TableRow) passwordChangeDialog.findViewById(
		    R.id.TableRowOldPassword);
            tr.setVisibility(hasPassword ? View.VISIBLE : View.GONE);
	    CheckBox showPasswordCheckBox2 =
		(CheckBox) passwordChangeDialog.findViewById(
			R.id.CheckBoxShowPassword);
	    showPasswordCheckBox2.setChecked(false);
            changeShowPasswordListener.onCheckedChanged(showPasswordCheckBox2,
                    false);
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
	}
    }

    /**
     * A runner which updates the visibility of the old password row
     * on the Set/Change Password dialog and the Lock/Unlock menu entry.
     */
    private final Runnable updatePasswordVisibility = new Runnable() {
        @Override
        public void run() {
            if (menu != null) {
                menu.findItem(R.id.menuUnlock).setVisible(hasPassword);
                menu.findItem(R.id.menuPassword).setTitle(hasPassword
                        ? R.string.MenuPasswordChange
                        : R.string.MenuPasswordSet);
            }
            if (passwordChangeDialog != null) {
                TableRow tr = (TableRow) passwordChangeDialog.findViewById(
                        R.id.TableRowOldPassword);
                tr.setVisibility(hasPassword ? View.VISIBLE : View.GONE);
            }
        }
    };

    /**
     * A runner which checks the content resolver for a password hash,
     * updating the visibility of the &ldquo;Old Password&rdquo; row
     * on the Set/Change Password dialog and the Lock/Unlock menu entry
     * accordingly.  If the value has changed since last checked, update
     * the UI elements on the UI thread.
     */
    private final Runnable checkForPassword = new Runnable() {
        @Override
        public void run() {
            boolean oldHasPassword = hasPassword;
            hasPassword = encryptor.hasPassword(getContentResolver());
            if (hasPassword != oldHasPassword)
                runOnUiThread(updatePasswordVisibility);
        }
    };

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

    class UnlockOnClickListener implements DialogInterface.OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            Log.d(TAG, "UnlockOnClickListener.onClick(" + which + ")");
            switch (which) {
                case DialogInterface.BUTTON_NEGATIVE:
                    dialog.dismiss();
                    return;

                case DialogInterface.BUTTON_POSITIVE:
                    if (unlockPasswordEditText.length() > 0) {
                        char[] password = new char[unlockPasswordEditText.length()];
                        unlockPasswordEditText.getText().getChars(0,
                                password.length, password, 0);
                        encryptor.setPassword(password);
                        executor.submit(checkPasswordForUnlock);
                    } else {
                        encryptor.forgetPassword();
                        prefs.setShowEncrypted(false);
                        menu.findItem(R.id.menuUnlock)
                                .setTitle(R.string.MenuUnlock);
                    }
                    return;
            }
        }
    }

    /**
     * Check the password that the user entered in
     * {@link UnlockOnClickListener} against the database.
     * This must be done on the non-UI thread.
     */
    private final Runnable checkPasswordForUnlock = new Runnable() {
        @Override
        public void run() {
            try {
                if (encryptor.checkPassword(getContentResolver())) {
                    prefs.setShowEncrypted(true);
                    menu.findItem(R.id.menuUnlock)
                            .setTitle(R.string.MenuLock);
                    unlockDialog.dismiss();
                } else {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(ToDoListActivity.this,
                                    R.string.ToastBadPassword,
                                    Toast.LENGTH_LONG).show();
                            unlockDialog.show();
                        }
                    });
                }
            } catch (GeneralSecurityException gsx) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(ToDoListActivity.this,
                                R.string.ToastBadPassword,
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        }
    };
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
                            passwordChangeDialog.show();
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
