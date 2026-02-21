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

import static com.xmission.trevin.android.todo.data.ToDoPreferences.*;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.xmission.trevin.android.todo.R;
import com.xmission.trevin.android.todo.data.ToDoCategory;
import com.xmission.trevin.android.todo.data.ToDoItem;
import com.xmission.trevin.android.todo.data.ToDoPreferences;
import com.xmission.trevin.android.todo.provider.ToDoRepositoryImpl;
import com.xmission.trevin.android.todo.service.AlarmWorker;
import com.xmission.trevin.android.todo.service.ProgressBarUpdater;
import com.xmission.trevin.android.todo.util.AuthenticationException;
import com.xmission.trevin.android.todo.util.PasswordMismatchException;
import com.xmission.trevin.android.todo.util.StringEncryption;
import com.xmission.trevin.android.todo.provider.ItemLoaderCallbacks;
import com.xmission.trevin.android.todo.provider.ToDoRepository;
import com.xmission.trevin.android.todo.provider.ToDoSchema.*;
//import com.xmission.trevin.android.todo.service.AlarmService;
//import com.xmission.trevin.android.todo.service.PasswordChangeService;
import com.xmission.trevin.android.todo.service.PasswordChangeWorker;
//import com.xmission.trevin.android.todo.service.ProgressReportingService;

import android.annotation.SuppressLint;
import android.app.*;
import android.content.*;
import android.database.DataSetObserver;
import android.database.SQLException;
import android.net.Uri;
import android.os.Bundle;
//import android.os.IBinder;
//import android.os.RemoteException;
import android.text.InputType;
import android.util.Log;
import android.view.*;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

/**
 * Displays a list of To Do items.  Will display items from the {@link Uri}
 * provided in the intent if there is one, otherwise defaults to displaying the
 * contents of the {@link ToDoRepository}.
 *
 * @author Trevin Beattie
 */
@SuppressLint("NewApi")
@SuppressWarnings("deprecation")
public class ToDoListActivity extends ListActivity {

    private static final String TAG = "ToDoListActivity";

    /**
     * The name of the Intent extra data that holds the item category ID.
     * This is a {@code long} value.
     */
    public static final String EXTRA_CATEGORY_ID =
            "com.xmission.trevin.android.todo.CategoryId";
    /**
     * The name of the Intent extra data that holds the item ID.
     * This is a {@code long} value.
     */
    public static final String EXTRA_ITEM_ID =
            "com.xmission.trevin.android.todo.ItemId";

    private static final int ABOUT_DIALOG_ID = 1;
    static final int DUEDATE_LIST_ID = 2;
    private static final int DUEDATE_DIALOG_ID = 7;
    private static final int PASSWORD_DIALOG_ID = 8;
    private static final int PROGRESS_DIALOG_ID = 9;
    private static final int UNLOCK_DIALOG_ID = 10;

    /**
     * The columns we are interested in from the category table
     */
    static final String[] CATEGORY_PROJECTION = new String[] {
            ToDoCategoryColumns._ID,
            ToDoCategoryColumns.NAME,
    };

    /**
     * The columns we are interested in from the item table
     *
     * @deprecated
     */
    static final String[] ITEM_PROJECTION = new String[] {
            ToDoItemColumns._ID,
            ToDoItemColumns.DESCRIPTION,
            ToDoItemColumns.CHECKED,
            ToDoItemColumns.NOTE,
            ToDoItemColumns.ALARM_DAYS_EARLIER,
            ToDoItemColumns.REPEAT_INTERVAL,
            ToDoItemColumns.DUE_TIME,
            ToDoItemColumns.COMPLETED_TIME,
            ToDoItemColumns.CATEGORY_NAME,
            ToDoItemColumns.PRIVATE,
            ToDoItemColumns.PRIORITY,
            ToDoItemColumns.REPEAT_DAY,
            ToDoItemColumns.REPEAT_DAY2,
            ToDoItemColumns.REPEAT_END,
            ToDoItemColumns.REPEAT_INCREMENT,
            ToDoItemColumns.REPEAT_MONTH,
            ToDoItemColumns.REPEAT_WEEK,
            ToDoItemColumns.REPEAT_WEEK2,
            ToDoItemColumns.REPEAT_WEEK_DAYS,
    };

    /** Shared preferences */
    private ToDoPreferences prefs;

    /** The URI by which we were started for the To-Do items */
    private Uri todoUri = ToDoItemColumns.CONTENT_URI;

    /** The corresponding URI for the categories */
    private Uri categoryUri = ToDoCategoryColumns.CONTENT_URI;

    /** Category filter spinner */
    Spinner categoryList = null;

    /** The To Do database */
    ToDoRepository repository = null;

    /** Used to map categories from the database to views */
    CategoryFilterAdapter categoryAdapter = null;

    /** Used to map To Do entries from the database to views */
    ToDoCursorAdapter itemAdapter = null;

    /** Used to map the next week's dates to a list */
    DueDateSelectAdapter dueDateAdapter = null;

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

    /*
     * Progress reporting service
     * @deprecated replace with the {@link PasswordChangeProgressObserver}
     */
    // ProgressReportingService progressService = null;

    /** Progress dialog */
    ProgressDialog progressDialog = null;

    /** Live data for the progress dialog */
    LiveData<WorkInfo> progressLiveData = null;

    /** Progress observer */
    PasswordChangeProgressObserver progressObserver = null;

    /** Encryption for private records */
    StringEncryption encryptor;

    private final ToDoDataObserver registeredObserver =
            new ToDoDataObserver();

    /** Item Loader callbacks */
    private ItemLoaderCallbacks itemLoaderCallbacks = null;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private WorkManager workManager;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, ".onCreate");

        setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);

        /*
         * If no data was given in the intent (because we were started
         * as a MAIN activity), then use our default content URI.
         */
        Intent intent = getIntent();
        if (intent.getData() == null) {
            Log.d(TAG, String.format("No intent data; defaulting to %s",
                    ToDoItemColumns.CONTENT_URI.toString()));
            intent.setData(ToDoItemColumns.CONTENT_URI);
            todoUri = ToDoItemColumns.CONTENT_URI;
            categoryUri = ToDoCategoryColumns.CONTENT_URI;
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
                (selectedSortOrder >= ToDoItemColumns.USER_SORT_ORDERS.length)) {
            prefs.setSortOrder(0);
        }

        workManager = WorkManager.getInstance(this);

        if (repository == null)
            repository = ToDoRepositoryImpl.getInstance();
        // Establish a connection to the database (on a non-UI thread)
        Runnable openRepo = new OpenRepositoryRunner();
        executor.submit(openRepo);

        categoryAdapter = new CategoryFilterAdapter(this, repository);
        itemAdapter = new ToDoCursorAdapter(
                this, null, repository, encryptor,
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE));
        Log.d(TAG, ".onCreate: initializing a To Do item loader manager");
        itemLoaderCallbacks = new ItemLoaderCallbacks(this,
                prefs, itemAdapter, repository);
        getLoaderManager().initLoader(ToDoItemColumns.CONTENT_TYPE.hashCode(),
                null, itemLoaderCallbacks);

        repository.registerDataSetObserver(registeredObserver);

        // Inflate our view so we can find our lists
        setContentView(R.layout.list);

        categoryList = (Spinner) findViewById(R.id.ListSpinnerCategory);
        categoryList.setAdapter(categoryAdapter);

        ListView listView = getListView();
        listView.setAdapter(itemAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent,
                                    View view, int position, long id) {
                Log.d(TAG, String.format(Locale.US,
                        ".onItemClick(parent,view,%d,%d)",
                        position, id));
            }
        });
        listView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                Log.d(TAG, String.format(Locale.US,
                        ".onFocusChange(view,%s)", hasFocus));
            }
        });
        listView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent,
                                       View v, int position, long id) {
                Log.d(TAG, String.format(Locale.US,
                        ".onItemSelected(parent,view,%d,%d)",
                        position, id));
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

        Log.d(TAG, ".onCreate finished.");
    }

    /**
     * A runner to open the database on a non-UI thread (if on Honeycomb
     * or later) and then check whether a password has been set.
     */
    private class OpenRepositoryRunner implements Runnable {
        @Override
        public void run() {
            repository.open(ToDoListActivity.this);
            checkForPassword.run();
        }
    }

    /**
     * Keep track of changes so that we can
     * update any alarms and update UI elements
     * related to setting or clearing the password.
     */
    private class ToDoDataObserver extends DataSetObserver {
        @Override
        public void onChanged() {
            Log.d(TAG, "ToDoDataObserver.onChanged()");
            // Check whether the category has changed
            long selectedCategory = prefs.getSelectedCategory();
            if (categoryList.getSelectedItemId() != selectedCategory) {
                Log.w(TAG, "The category ID at the selected position has changed!");
                setCategorySpinnerByID(selectedCategory);
            }

            // Update our alarms
            OneTimeWorkRequest req = new OneTimeWorkRequest
                    .Builder(AlarmWorker.class)
                    .setConstraints(new Constraints.Builder()
                            .setRequiresDeviceIdle(true)
                            .build())
                    // Delay a minute to allow contents to settle
                    .setInitialDelay(1, TimeUnit.MINUTES)
                    .addTag("ChangeObserver")
                    .build();
            workManager.enqueueUniqueWork("AlarmChangeWork",
                    ExistingWorkPolicy.REPLACE, req);

            // Update UI elements related to the password
            checkForPassword.run();
        }
    }
    /** Called when the activity is about to be started after having been stopped */
    @Override
    public void onRestart() {
        Log.d(TAG, ".onRestart");
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
        Log.d(TAG, String.format(Locale.US, ".onNewIntent(%s, extras=%s)",
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
        if (intent.hasExtra(EXTRA_ITEM_ID)) {
            long categoryId = intent.getLongExtra(EXTRA_CATEGORY_ID, -1);
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

        ListView listView = getListView();
        Log.d(TAG, String.format("Target item for display is %d", itemId));
        int position = itemAdapter.getItemPosition(itemId);
        listView.smoothScrollToPosition(position);
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
        repository.unregisterDataSetObserver(registeredObserver);
        repository.release(this);
        StringEncryption.releaseGlobalEncryption(this);
        if (progressObserver != null) {
            progressLiveData.removeObserver(progressObserver);
            progressObserver = null;
            progressLiveData = null;
        }
        super.onDestroy();
    }

    /**
     * Generate the WHERE clause for the list query.
     * This is only used for logging; the repository does the
     * actualy query generation when called by ToDoCursorLoader.
     */
    public String generateWhereClause() {
        StringBuilder whereClause = new StringBuilder();
        if (!prefs.showChecked()) {
            whereClause.append(ToDoItemColumns.CHECKED).append(" = 0")
                    .append(" AND (")
                    .append(ToDoItemColumns.HIDE_DAYS_EARLIER)
                    .append(" IS NULL OR (")
                    .append(ToDoItemColumns.DUE_TIME).append(" - ")
                    .append(ToDoItemColumns.HIDE_DAYS_EARLIER)
                    .append(" * 86400000 < ")
                    .append(System.currentTimeMillis()).append("))");
        }
        if (!prefs.showPrivate()) {
            if (whereClause.length() > 0)
                whereClause.append(" AND ");
            whereClause.append(ToDoItemColumns.PRIVATE).append(" = 0");
        }
        long selectedCategory = prefs.getSelectedCategory();
        if (selectedCategory >= 0) {
            if (whereClause.length() > 0)
                whereClause.append(" AND ");
            whereClause.append(ToDoItemColumns.CATEGORY_ID).append(" = ")
                    .append(selectedCategory);
        }
        return whereClause.toString();
    }

    /** Event listener for the New button */
    class NewButtonListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            Log.d(TAG, ".NewButtonListener.onClick");
            long selectedCategory = prefs.getSelectedCategory();
            if (selectedCategory < ToDoCategory.UNFILED)
                selectedCategory = ToDoCategory.UNFILED;

            // Immediately bring up the details dialog
            Intent intent = new Intent(v.getContext(),
                    ToDoDetailsActivity.class);
            intent.putExtra(EXTRA_CATEGORY_ID, selectedCategory);
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
        int position = categoryAdapter.getCategoryPosition(id);
        categoryList.setSelection(position);
    }

    /**
     * Called when the settings dialog changes a preference related to
     * filtering the To Do list
     */
    public void updateListFilter() {
        int selectedSortOrder = prefs.getSortOrder();
        if ((selectedSortOrder < 0) ||
                (selectedSortOrder >= ToDoItemColumns.USER_SORT_ORDERS.length))
            selectedSortOrder = 0;

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, String.format(Locale.US, ".updateListFilter:"
                    + " requerying the data where %s ordered by %s",
                    generateWhereClause(),
                    ToDoItemColumns.USER_SORT_ORDERS[selectedSortOrder]));
        }
        getLoaderManager().restartLoader(ToDoItemColumns.CONTENT_TYPE.hashCode(),
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
            if (dueDateAdapter == null) {
                dueDateAdapter = new DueDateSelectAdapter(this, prefs);
            }
//	    Resources r = getResources();
//	    String[] dueDateOptionFormats =
//		r.getStringArray(R.array.DueDateFormatList);
//	    String[] dueDateListItems =
//		new String[dueDateOptionFormats.length + 2];
//	    Calendar c = Calendar.getInstance();
//	    for (int i = 0; i < dueDateOptionFormats.length; i++) {
//		SimpleDateFormat formatter =
//		    new SimpleDateFormat(dueDateOptionFormats[i],
//			    Locale.getDefault());
//		dueDateListItems[i] = formatter.format(c.getTime());
//		c.add(Calendar.DATE, 1);
//	    }
//	    dueDateListItems[dueDateOptionFormats.length] =
//		r.getString(R.string.DueDateNoDate);
//	    dueDateListItems[dueDateOptionFormats.length + 1] =
//		r.getString(R.string.DueDateOther);
            builder = new AlertDialog.Builder(this);
            builder.setAdapter(dueDateAdapter,
                    new DueDateListSelectionListener());
//	    builder.setItems(dueDateListItems,
//		    new DueDateListSelectionListener());
            dueDateListDialog = builder.create();
            return dueDateListDialog;

            case DUEDATE_DIALOG_ID:
                dueDateDialog = new CalendarDatePickerDialog(this,
                        getText(R.string.DatePickerTitleDueDate),
                        new CalendarPickerDateSetListener());
                return dueDateDialog;

            case UNLOCK_DIALOG_ID:
                builder = new AlertDialog.Builder(this);
                builder.setIcon(R.drawable.ic_menu_login);
                builder.setTitle(R.string.MenuUnlock);
                View unlockLayout =
                        ((LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE))
                                .inflate(R.layout.unlock, (ScrollView)
                                        findViewById(R.id.UnlockLayoutRoot));
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
                                .inflate(R.layout.password, (ScrollView)
                                        findViewById(R.id.PasswordLayoutRoot));
                builder.setView(passwordLayout);
                DialogInterface.OnClickListener listener2 =
                        new PasswordChangeOnClickListener();
                builder.setPositiveButton(R.string.ConfirmationButtonOK, listener2);
                builder.setNegativeButton(R.string.ConfirmationButtonCancel, listener2);
                passwordChangeDialog = builder.create();
                CheckBox showPasswordCheckBox2 = (CheckBox)
                        passwordLayout.findViewById(R.id.CheckBoxShowPassword);
                passwordChangeEditText[0] = (EditText)
                        passwordLayout.findViewById(R.id.EditTextOldPassword);
                passwordChangeEditText[1] = (EditText)
                        passwordLayout.findViewById(R.id.EditTextNewPassword);
                passwordChangeEditText[2] = (EditText)
                        passwordLayout.findViewById(R.id.EditTextConfirmPassword);
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
            case DUEDATE_LIST_ID:
                dueDateAdapter.refreshDates();
                return;

            case DUEDATE_DIALOG_ID:
                dueDateDialog.setTimeZone(prefs.getTimeZone());
                final long itemId = itemAdapter.getSelectedItemId();
                executor.submit(new Runnable() {
                    @Override
                    public void run() {
                        ToDoItem item = repository.getItemById(itemId);
                        if (item == null) {
                            Log.w(TAG, String.format(Locale.US,
                                    "Due date dialog prepared by item %d not found",
                                    itemId));
                            return;
                        }
                        final LocalDate due = item.getDue();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                dueDateDialog.setDate(due);
                            }
                        });
                    }
                });
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
                if (progressObserver != null) {
                    Log.d(TAG, ".onPrepareDialog(PROGRESS_DIALOG_ID):"
                            + " Initializing the progress dialog at "
                            + getString(R.string.ProgressMessageStart) + " 0/1");
                    progressDialog.setMessage(getString(R.string.ProgressMessageStart));
                    progressDialog.setIndeterminate(true);

                } else {
                    Log.d(TAG, ".onPrepareDialog(PROGRESS_DIALOG_ID):"
                            + " Password observer has disappeared;"
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
            hasPassword = encryptor.hasPassword(repository);
            if (hasPassword != oldHasPassword)
                runOnUiThread(updatePasswordVisibility);
        }
    };

    /**
     * A runner which displays an alert on the UI thread when a repository
     * operation on a non-UI thread throws an exception.
     */
    private class RepositoryExceptionAlertRunner implements Runnable {

        private final Exception exception;

        RepositoryExceptionAlertRunner(Exception e) {
            exception = e;
        }

        @Override
        public void run() {
            new AlertDialog.Builder(ToDoListActivity.this)
                    .setMessage(exception.getMessage())
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setNeutralButton(R.string.ConfirmationButtonCancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            })
                    .create()
                    .show();
        }

    }

    class DueDateListSelectionListener
            implements DialogInterface.OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            long todoItemId = itemAdapter.getSelectedItemId();
            Log.d(TAG, String.format(Locale.US,
                    "DueDateListSelectionListener.onClick(%d), for item %d",
                    which, todoItemId));
            final LocalDate selectedDate;
            switch (which) {
                default:
                    selectedDate = LocalDate.now(prefs.getTimeZone())
                            .plusDays(which);
                    break;

                case 8:	// No date
                    selectedDate = null;
                    break;

                case 9:	// Other
                    showDialog(DUEDATE_DIALOG_ID);
                    return;
            }

            executor.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        ToDoItem todo = repository.getItemById(todoItemId);
                        todo.setDue(selectedDate);
                        todo.setModTimeNow();
                        repository.updateItem(todo);
                    } catch (SQLException sx) {
                        runOnUiThread(new RepositoryExceptionAlertRunner(sx));
                    }
                }
            });
        }
    }

    class CalendarPickerDateSetListener
            implements CalendarDatePickerDialog.OnDateSetListener {
        @Override
        public void onDateSet(CalendarDatePicker dp, final LocalDate date) {
            final long todoItemId = itemAdapter.getSelectedItemId();
            Log.d(TAG, String.format(Locale.US,
                    "dueDateDialog.onDateSet(%s); item=%d",
                    date.toString(), todoItemId));
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        ToDoItem todo = repository.getItemById(todoItemId);
                        todo.setDue(date);
                        todo.setModTimeNow();
                        repository.updateItem(todo);
                    } catch (SQLException sx) {
                        runOnUiThread(new RepositoryExceptionAlertRunner(sx));
                    }
                }
            });
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
                if (encryptor.checkPassword(repository)) {
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
            } catch (PasswordMismatchException px) {
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
                    // Intent passwordChangeIntent = new Intent(ToDoListActivity.this,
                    //        PasswordChangeService.class);
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
                                    public void onClick(DialogInterface dialog,
                                                        int which) {
                                        dialog.dismiss();
                                        passwordChangeDialog.show();
                                    }
                                });
                        builder.show();
                        return;
                    }
                    Arrays.fill(confirmedPassword, (char) 0);
                    Data.Builder inputDataBuilder = new Data.Builder();
                    if (newPassword.length > 0)
                        // passwordChangeIntent.putExtra(
                        //    PasswordChangeService.EXTRA_NEW_PASSWORD,
                        //        newPassword);
                        inputDataBuilder = inputDataBuilder.putString(
                                PasswordChangeWorker.DATA_NEW_PASSWORD,
                                new String(newPassword));

                    if (encryptor.hasPassword(repository)) {
                        char[] oldPassword =
                                new char[passwordChangeEditText[0].length()];
                        passwordChangeEditText[0].getText().getChars(
                                0, oldPassword.length, oldPassword, 0);
                        StringEncryption oldEncryptor = new StringEncryption();
                        oldEncryptor.setPassword(oldPassword);
                        try {
                            if (!oldEncryptor.checkPassword(repository)) {
                                Arrays.fill(newPassword, (char) 0);
                                Arrays.fill(oldPassword, (char) 0);
                                AlertDialog.Builder builder =
                                        new AlertDialog.Builder(
                                                ToDoListActivity.this);
                                builder.setIcon(android.R.drawable.ic_dialog_alert);
                                builder.setMessage(R.string.ToastBadPassword);
                                builder.setNeutralButton(R.string.ConfirmationButtonCancel,
                                        new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog,
                                                                int which) {
                                                dialog.dismiss();
                                            }
                                        });
                                builder.show();
                                return;
                            }
                        } catch (AuthenticationException gsx) {
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
                        inputDataBuilder = inputDataBuilder.putString(
                                PasswordChangeWorker.DATA_OLD_PASSWORD,
                                new String(oldPassword));
                        // passwordChangeIntent.putExtra(
                        //        PasswordChangeService.EXTRA_OLD_PASSWORD, oldPassword);
                    }
                    WorkRequest changeRequest = new OneTimeWorkRequest
                            .Builder(PasswordChangeWorker.class)
                            .setInputData(inputDataBuilder.build())
                            .addTag("PasswordChange")
                            .build();
                    workManager.enqueue(changeRequest);

                    // Sanity checks
                    if ((progressLiveData != null) && (progressObserver != null))
                        progressLiveData.removeObserver(progressObserver);

                    showDialog(PROGRESS_DIALOG_ID);
                    progressObserver = new PasswordChangeProgressObserver();
                    progressLiveData = workManager.getWorkInfoByIdLiveData(
                            changeRequest.getId());
                    progressLiveData.observeForever(progressObserver);

                    // passwordChangeIntent.setAction(
                    //        PasswordChangeService.ACTION_CHANGE_PASSWORD);
                    dialog.dismiss();
                    //Log.d(TAG, "PasswordChangeOnClickListener.onClick:"
                    //        + " starting the password change service");
                    // startService(passwordChangeIntent);
                    // Bind to the service
                    //Log.d(TAG, "PasswordChangeOnClickListener.onClick:"
                    //        + " binding to the password change service");
                    // bindService(passwordChangeIntent,
                    //        new PasswordChangeServiceConnection(), 0);
                    return;
            }
        }
    }

//    class PasswordChangeServiceConnection implements ServiceConnection {
//	public void onServiceConnected(ComponentName name, IBinder service) {
//	    try {
//		Log.d(TAG, ".onServiceConnected(" + name.getShortClassName()
//			+ "," + service.getInterfaceDescriptor() + ")");
//	    } catch (RemoteException rx) {}
//	    PasswordChangeService.PasswordBinder pbinder =
//		(PasswordChangeService.PasswordBinder) service;
//	    progressService = pbinder.getService();
//	    showDialog(PROGRESS_DIALOG_ID);
//	}
//
//	/** Called when a connection to the service has been lost */
//	public void onServiceDisconnected(ComponentName name) {
//	    Log.d(TAG, ".onServiceDisconnected(" + name.getShortClassName() + ")");
//	    if (progressDialog != null)
//		progressDialog.dismiss();
//	    progressService = null;
//	    unbindService(this);
//	}
//    }

    /**
     * Observer of the password change worker&rsquo;s progress.
     */
    private class PasswordChangeProgressObserver implements Observer<WorkInfo> {
        @Override
        public void onChanged(@NonNull WorkInfo workInfo) {
            if (progressDialog == null)
                return;
            if (workInfo.getState().isFinished()) {
                progressDialog.dismiss();
                progressLiveData.removeObserver(progressObserver);
                progressObserver = null;
                progressLiveData = null;
                return;
            }
            Data progress = workInfo.getProgress();
            int max = progress.getInt(
                    ProgressBarUpdater.PROGRESS_MAX_COUNT, 0);
            if (max <= 0) {
                progressDialog.setIndeterminate(true);
            } else {
                progressDialog.setIndeterminate(false);
                progressDialog.setMax(max);
                progressDialog.setProgress(progress.getInt(
                        ProgressBarUpdater.PROGRESS_CURRENT_COUNT, 0));
            }
            progressDialog.setMessage(progress.getString(
                    ProgressBarUpdater.PROGRESS_CURRENT_MODE));
        }
    }

}
