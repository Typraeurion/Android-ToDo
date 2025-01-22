/*
 * $Id: ToDoDetailsActivity.java,v 1.3 2017/07/25 17:52:27 trevin Exp trevin $
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
 * $Log: ToDoDetailsActivity.java,v $
 * Revision 1.3  2017/07/25 17:52:27  trevin
 * Fixed a bug that caused the application to crash when attempting
 *   to open an item's details in landscape orientation.
 *
 * Revision 1.2  2014/03/22 19:03:53  trevin
 * Added the copyright notice.
 * Implemented restoring the activity from saved data.  (Not yet tested.)
 *
 * Revision 1.1  2011/01/19 00:35:59  trevin
 * Initial revision
 *
 */
package com.xmission.trevin.android.todo;

import java.security.GeneralSecurityException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import com.xmission.trevin.android.todo.RepeatSettings.IntervalType;
import com.xmission.trevin.android.todo.ToDo.*;

import android.app.*;
import android.content.*;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDoneException;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.*;
import android.widget.*;

/**
 * Displays the details of a To Do item.  Will display the item from the
 * {@link Uri} provided in the intent, which is required.
 *
 * @author Trevin Beattie
 */
public class ToDoDetailsActivity extends Activity {

    private static final String TAG = "ToDoDetailsActivity";

    static final int DUEDATE_LIST_ID = 2;
    private static final int HIDEUNTIL_DIALOG_ID = 3;
    private static final int ALARM_DIALOG_ID = 4;
    private static final int ENDDATE_DIALOG_ID = 5;
    private static final int REPEAT_LIST_ID = 6;
    private static final int DUEDATE_DIALOG_ID = 7;
    private static final int REPEAT_DIALOG_ID = 8;

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
            ToDoItem.ALARM_TIME,
            ToDoItem.HIDE_DAYS_EARLIER,
            ToDoItem.REPEAT_INTERVAL,
            ToDoItem.REPEAT_INCREMENT,
            ToDoItem.REPEAT_WEEK_DAYS,
            ToDoItem.REPEAT_DAY,
            ToDoItem.REPEAT_DAY2,
            ToDoItem.REPEAT_WEEK,
            ToDoItem.REPEAT_WEEK2,
            ToDoItem.REPEAT_MONTH,
            ToDoItem.REPEAT_END,
            ToDoItem.DUE_TIME,
            ToDoItem.COMPLETED_TIME,
            ToDoItem.CATEGORY_ID,
            ToDoItem.PRIORITY,
            ToDoItem.PRIVATE,
    };

    /** These columns are used if we need to decrypt or encrypt the note */
    private static final String[] ITEM_NOTE_PROJECTION = new String[] {
	ToDoItem._ID,
	ToDoItem.NOTE,
	ToDoItem.PRIVATE,
    };

    /** The URI by which we were started for the To-Do item */
    private Uri todoUri = ToDoItem.CONTENT_URI;

    /** The corresponding URI for the categories */
    private Uri categoryUri = ToDoCategory.CONTENT_URI;

    /** The To Do item text */
    EditText toDoDescription = null;

    /**
     * A rating bar to select the priority
     * (by inverting number of stars)
     */
    EditText priorityText = null;

    /** The due date button shows the due date */
    Button dueDateButton = null;

    /** The due date */
    Date dueDate = null;

    /** Category filter spinner */
    Spinner categoryList = null;

    /** The alarm time is also a button */
    Button alarmText = null;

    /** The alarm details */
    Integer alarmDaysInAdvance = null;
    Long alarmTime = null;

    /** The repeat button shows the repeat interval */
    Button repeatButton = null;

    /** The repeat details */
    RepeatSettings repeatSettings = null;

    /** The hide time is also a button */
    Button hideText = null;

    /** The hiding details */
    Integer hideDaysInAdvance = null;

    /** Checkbox for private records */
    CheckBox privateCheckBox = null;

    /** Due date list dialog */
    Dialog dueDateListDialog = null;

    /** Due Date dialog box */
    Dialog dueDateDialog = null;

    /** Hide Until dialog box */
    Dialog hideUntilDialog = null;

    /** Checkbox on the Hide Until dialog box */
    CheckBox hideCheckBox = null;

    /** Text edit field for the Hide Until dialog box */
    EditText hideEditDays = null;

    /**
     * Text which displays the time the item will be shown
     * in the Hide Until dialog box
     */
    TextView showTime = null;

    /** OK button for the Hide Until dialog box */
    Button hideOKButton = null;

    /** Alarm dialog box */
    Dialog alarmDialog = null;

    /** Checkbox on the Alarm dialog box */
    CheckBox alarmCheckBox = null;

    /** Text edit field for the Alarm dialog box */
    EditText alarmEditDays = null;

    /** Alarm time on the Alarm dialog box */
    TimePicker alarmTimePicker = null;

    /**
     * Text which displays the time the alarm will go off
     * in the Alarm dialog box
     */
    TextView alarmNextTime = null;

    /** OK button for the Alarm dialog box */
    Button alarmOKButton = null;

    /** Repeat list dialog box */
    Dialog repeatListDialog = null;

    /** Repeat End Date dialog box */
    Dialog repeatEndDialog = null;

    /** Repeat dialog box */
    RepeatEditorDialog repeatDialog = null;

    /** Container for dialog form data when saving the instance state */
    private class FormData {
	String description;
	String priority;
	String dueDateText;
	Date dueDate;
	boolean dueDateDialogIsShowing;
	int categorySpinnerPosition;
	String alarmText;
	Integer alarmDaysInAdvance;
	Long alarmTime;
	String repeatText;
	RepeatSettings repeatSettings;
	RepeatSettings repeatDialogSettings;
	boolean endDateDialogIsShowing;
	String hideText;
	Integer hideDaysInAdvance;
	boolean hideDialogIsShowing;
	Boolean hideEnabled;
	String hideDaysText;
	String showTimeText;
	boolean alarmDialogIsShowing;
	Boolean alarmEnabled;
	String alarmDaysText;
	Integer alarmHours;
	Integer alarmMinutes;
	boolean isPrivate;
    }

    StringEncryption encryptor;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, ".onCreate");

	setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);

        Intent intent = getIntent();
        if (intent.getData() == null)
            throw new NullPointerException("No data provided with the intent");
        todoUri = intent.getData();
        categoryUri = todoUri.buildUpon().encodedPath("/categories").build();

        // Inflate our view so we can find our fields
        setContentView(R.layout.details);

        toDoDescription = (EditText) findViewById(R.id.DetailEditTextDescription);
        priorityText = (EditText) findViewById(R.id.DetailEditTextPriority);
        categoryList = (Spinner) findViewById(R.id.DetailSpinnerCategory);
        TextView completedDateText = (TextView)
        findViewById(R.id.DetailTextCompletedDate);
        dueDateButton = (Button) findViewById(R.id.DetailButtonDueDate);
        alarmText = (Button) findViewById(R.id.DetailButtonAlarm);
        repeatButton = (Button) findViewById(R.id.DetailButtonRepeat);
        hideText = (Button) findViewById(R.id.DetailButtonHideUntil);
        privateCheckBox = (CheckBox) findViewById(R.id.DetailCheckBoxPrivate);

        // Perform a managed query. The Activity will handle closing and
        // requerying the cursor when needed.
        Cursor categoryCursor = managedQuery(categoryUri,
        	CATEGORY_PROJECTION, null, null,
                ToDoCategory.DEFAULT_SORT_ORDER);

        // If we are being re-created from a destroyed instance,
        // restore the previous dialog state.
        Object savedData = getLastNonConfigurationInstance();
        if (savedData instanceof FormData) {
            FormData data = (FormData) savedData;
            toDoDescription.setText(data.description);
            priorityText.setText(data.priority);
            dueDateButton.setText(data.dueDateText);
            dueDate = data.dueDate;
            categoryList.setSelection(data.categorySpinnerPosition);
            alarmText.setText(data.alarmText);
            alarmDaysInAdvance = data.alarmDaysInAdvance;
            alarmTime = data.alarmTime;
            repeatButton.setText(data.repeatText);
            hideText.setText(data.hideText);
            hideDaysInAdvance = data.hideDaysInAdvance;
            if (data.dueDateDialogIsShowing) {
        	// To do: pop up and fill in the due date dialog
            }
            if (data.repeatDialogSettings != null) {
        	// To do: pop up and fill in the repeat dialog
            }
            if (data.endDateDialogIsShowing) {
        	// To do: pop up and fill in the end date dialog
            }
            if (data.hideDialogIsShowing) {
        	// To do: pop up and fill in the hide dialog
            }
            if (data.alarmDialogIsShowing) {
        	// To do: pop up and fill in the alarm dialog
            }
        }

        else {
            // Read the item details
            Cursor itemCursor = getContentResolver().query(todoUri,
        	    ITEM_PROJECTION, null, null, null);
            if (!itemCursor.moveToFirst())
        	throw new SQLiteDoneException();

            int isPrivate = itemCursor.getInt(
        	    itemCursor.getColumnIndex(ToDoItem.PRIVATE));
            privateCheckBox.setChecked(isPrivate != 0);

            encryptor = StringEncryption.holdGlobalEncryption();
            int i = itemCursor.getColumnIndex(ToDoItem.DESCRIPTION);
            if (isPrivate > 1) {
        	if (encryptor.hasKey()) {
        	    try {
        		toDoDescription.setText(encryptor.decrypt(itemCursor.getBlob(i)));
        	    } catch (GeneralSecurityException gsx) {
        		Toast.makeText(this, gsx.getMessage(),
        			Toast.LENGTH_LONG).show();
        		finish();
        	    }
        	} else {
        	    Toast.makeText(this, R.string.PasswordProtected,
        		    Toast.LENGTH_LONG).show();
        	    finish();
        	}
            } else {
        	toDoDescription.setText(itemCursor.getString(i));
            }

            i = itemCursor.getColumnIndex(ToDoItem.PRIORITY);
            priorityText.setText(Integer.toString(itemCursor.getInt(i)));

            // Used to map To Do categories from the database to a spinner
            SimpleCursorAdapter categoryAdapter =
        	new SimpleCursorAdapter(this,
        		android.R.layout.simple_spinner_item,
        		categoryCursor, new String[] { ToDoCategory.NAME },
        		new int[] { android.R.id.text1 });
            categoryAdapter.setDropDownViewResource(
        	    R.layout.simple_spinner_dropdown_item);
            categoryList.setAdapter(categoryAdapter);
            // Select the item's current category
            i = itemCursor.getColumnIndex(ToDoItem.CATEGORY_ID);
            setCategorySpinnerByID(itemCursor.getLong(i));

            i = itemCursor.getColumnIndex(ToDoItem.COMPLETED_TIME);
            View lastCompletedTableRow = findViewById(R.id.LastCompletedTableRow);
            if (itemCursor.isNull(i)) {
        	completedDateText.setText("");
        	if (lastCompletedTableRow != null)
        	    lastCompletedTableRow.setVisibility(View.GONE);
            } else {
        	DateFormat df = DateFormat.getDateInstance(DateFormat.MEDIUM);
        	completedDateText.setText(df.format(
        		new Date(itemCursor.getLong(i))));
        	if (lastCompletedTableRow != null)
        	    lastCompletedTableRow.setVisibility(View.VISIBLE);
            }

            /*
             * The due date pop-up has ten items:
             * Today, Tomorrow, Sun-Sat (next 5 days), "In 1 week" (the first 8
             * all prefixed with the short date), "No Date", and "Choose Date..."
             */
            i = itemCursor.getColumnIndex(ToDoItem.DUE_TIME);
            if (itemCursor.isNull(i)) {
        	dueDate = null;
            } else {
        	dueDate = new Date(itemCursor.getLong(i));
            }
            updateDueDateButton();
            dueDateButton.setOnClickListener(new DueDateButtonOnClickListener());

            i = itemCursor.getColumnIndex(ToDoItem.ALARM_DAYS_EARLIER);
            if (itemCursor.isNull(i)) {
        	alarmDaysInAdvance = null;
            } else {
        	alarmDaysInAdvance = itemCursor.getInt(i);
        	i = itemCursor.getColumnIndex(ToDoItem.ALARM_TIME);
        	alarmTime = itemCursor.getLong(i);
            }
            updateAlarmButton();

            /*
             * The repeat pop-up has seven items:
             * "None", "Daily until...", "Every Week", "Semi Monthly",
             * "Every month", "Every year", "Other..."
             */
            repeatSettings = new RepeatSettings(itemCursor);
            repeatButton.setOnClickListener(new RepeatButtonOnClickListener());
            updateRepeatButton();

            i = itemCursor.getColumnIndex(ToDoItem.HIDE_DAYS_EARLIER);
            if (itemCursor.isNull(i))
        	hideDaysInAdvance = null;
            else
        	hideDaysInAdvance = itemCursor.getInt(i);
            updateHideButton();

            // Finished with the item ... for now
            itemCursor.close();
        }

        // Set callbacks
	Button button = (Button) findViewById(R.id.DetailButtonHideUntil);
	button.setOnClickListener(new HideButtonOnClickListener());

	button = (Button) findViewById(R.id.DetailButtonAlarm);
	button.setOnClickListener(new AlarmButtonOnClickListener());

	button = (Button) findViewById(R.id.DetailButtonOK);
	button.setOnClickListener(new OKButtonOnClickListener());

	button = (Button) findViewById(R.id.DetailButtonCancel);
	button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
        	Log.d(TAG, "DetailButtonCancel.onClick");
        	ToDoDetailsActivity.this.finish();
            }
        });

        button = (Button) findViewById(R.id.DetailButtonDelete);
        button.setOnClickListener(new DeleteButtonOnClickListener());

        ImageButton noteButton = (ImageButton) findViewById(R.id.DetailButtonNote);
        noteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
        	Log.d(TAG, "DetailButtonNote.onClick");
		Intent intent = new Intent(v.getContext(),
			ToDoNoteActivity.class);
		intent.setData(todoUri);
		v.getContext().startActivity(intent);
            }
        });
    }

    /**
     * Called when the activity is about to be destroyed
     * and then immediately restarted (such as an orientation change).
     */
    @Override
    public Object onRetainNonConfigurationInstance() {
	// Save the current dialog state
	FormData data = new FormData();
	data.description = toDoDescription.getText().toString();
	data.priority = priorityText.getText().toString();
	data.dueDateText = dueDateButton.getText().toString();
	data.dueDate = dueDate;
	data.dueDateDialogIsShowing =
	    (dueDateDialog != null) && dueDateDialog.isShowing();
	data.categorySpinnerPosition = categoryList.getSelectedItemPosition();
	data.alarmText = alarmText.getText().toString();
	data.alarmDaysInAdvance = alarmDaysInAdvance;
	data.alarmTime = alarmTime;
	data.repeatText = repeatButton.getText().toString();
	data.repeatSettings = repeatSettings;
	if ((repeatDialog != null) && repeatDialog.isShowing())
	    data.repeatDialogSettings = repeatDialog.getRepeatSettings();
	data.endDateDialogIsShowing =
	    (repeatEndDialog != null) && repeatEndDialog.isShowing();
	data.hideText = hideText.getText().toString();
	data.hideDaysInAdvance = hideDaysInAdvance;
	data.hideDialogIsShowing =
	    (hideUntilDialog != null) && hideUntilDialog.isShowing();
	if (data.hideDialogIsShowing) {
	    data.hideEnabled = hideCheckBox.isChecked();
	    data.hideDaysText = hideEditDays.getText().toString();
	    data.showTimeText = showTime.getText().toString();
	}
	data.alarmDialogIsShowing =
	    (alarmDialog != null) && alarmDialog.isShowing();
	if (data.alarmDialogIsShowing) {
	    data.alarmEnabled = alarmCheckBox.isChecked();
	    data.alarmDaysText = alarmEditDays.getText().toString();
	    data.alarmHours = alarmTimePicker.getCurrentHour();
	    data.alarmMinutes = alarmTimePicker.getCurrentMinute();
	}
	return data;
    }

    /** Called when the activity is about to be destroyed */
    @Override
    public void onDestroy() {
	StringEncryption.releaseGlobalEncryption(this);
	super.onDestroy();
    }

    /** Look up the spinner item corresponding to a category ID and select it. */
    void setCategorySpinnerByID(long id) {
	for (int position = 0; position < categoryList.getCount(); position++) {
	    if (categoryList.getItemIdAtPosition(position) == id) {
		categoryList.setSelection(position);
		return;
	    }
	}
	Log.w(TAG, "No spinner item found for category ID " + id);
	if (id != ToDoCategory.UNFILED)
	    setCategorySpinnerByID(ToDoCategory.UNFILED);
	else
	    categoryList.setSelection(0);
    }

    /** Set the date in the due date button */
    void updateDueDateButton() {
	if (dueDate == null) {
	    dueDateButton.setText(
		    getResources().getString(R.string.DetailNotset));
	    alarmText.setVisibility(View.GONE);
	    repeatButton.setVisibility(View.GONE);
	    hideText.setVisibility(View.GONE);
	} else {
	    // Find the number of days between today and the due date
	    Calendar c1 = Calendar.getInstance();
	    c1.setTime(dueDate);
	    c1.set(Calendar.HOUR_OF_DAY, 13);
	    c1.set(Calendar.MINUTE, 0);
	    c1.set(Calendar.SECOND, 0);
	    Calendar c0 = Calendar.getInstance();
	    c0.set(Calendar.HOUR_OF_DAY, 11);
	    c0.set(Calendar.MINUTE, 0);
	    c0.set(Calendar.SECOND, 0);
	    DateFormat df = DateFormat.getDateInstance(DateFormat.MEDIUM);
	    dueDateButton.setText(df.format(dueDate));
	    alarmText.setVisibility(View.VISIBLE);
	    repeatButton.setVisibility(View.VISIBLE);
	    hideText.setVisibility(View.VISIBLE);
	}
    }

    /** Set the hide time in the hide button */
    void updateHideButton() {
	if ((dueDate == null) || (hideDaysInAdvance == null))
	    hideText.setText(getResources().getString(R.string.DetailNotset));
	else
	    hideText.setText(String.format(getResources().getQuantityString(
		    R.plurals.DetailTextDaysEarlier, hideDaysInAdvance),
		    hideDaysInAdvance));
    }

    /** Set the alarm time in the alarm button */
    void updateAlarmButton() {
	if ((dueDate == null) || (alarmDaysInAdvance == null))
	    alarmText.setText(getResources().getString(R.string.DetailNotset));
	else
	    alarmText.setText(String.format(getResources().getQuantityString(
		    R.plurals.DetailTextDaysEarlier, alarmDaysInAdvance),
		    alarmDaysInAdvance));
    }

    /** Set the repeat interval in the repeat button */
    void updateRepeatButton() {
	if ((repeatSettings == null) ||
		(repeatSettings.getIntervalType() == IntervalType.NONE)) {
	    repeatButton.setText(
		    getResources().getString(R.string.RepeatNone));
	} else {
	    switch (repeatSettings.getIntervalType()) {
	    case DAILY:
		if (repeatSettings.getEndDate() == null) {
		    repeatButton.setText(getResources().getString(
			    (repeatSettings.getIncrement() == 1)
			    ? R.string.RepeatDaily
			    : R.string.RepeatDailyDots));
		} else {
		    final DateFormat df =
			DateFormat.getDateInstance(DateFormat.SHORT);
		    String text = String.format(getResources().getString(
			    R.string.RepeatDailyUntilWhen),
			    df.format(repeatSettings.getEndDate()));
		    repeatButton.setText(text);
		}
		break;

	    case DAY_AFTER:
		repeatButton.setText(getResources().getString(
			R.string.RepeatDailyDots));
		break;

	    case WEEKLY:
		if ((repeatSettings.getIncrement() == 1) &&
			(repeatSettings.getFixedWeekDays().size() == 1))
		    repeatButton.setText(getResources().getString(
			    R.string.RepeatEveryWeek));
		else if ((repeatSettings.getIncrement() == 2) &&
			(repeatSettings.getFixedWeekDays().size() == 1))
		    repeatButton.setText(getResources().getString(
			    R.string.RepeatEveryOtherWeek));
		else
		    repeatButton.setText(getResources().getString(
			    R.string.RepeatWeeklyDots));
		break;

	    case WEEK_AFTER:
		repeatButton.setText(getResources().getString(
			R.string.RepeatWeeklyDots));
		break;

	    case SEMI_MONTHLY_ON_DATES:
	    case SEMI_MONTHLY_ON_DAYS:
		repeatButton.setText(getResources().getString(
			R.string.RepeatSemiMonthlyDots));
		break;

	    case MONTHLY_ON_DATE:
	    case MONTHLY_ON_DAY:
		if (repeatSettings.getIncrement() == 1)
		    repeatButton.setText(getResources().getString(
			    R.string.RepeatEveryMonth));
		else if (repeatSettings.getIncrement() == 2)
		    repeatButton.setText(getResources().getString(
			    R.string.RepeatEveryOtherMonth));
		else
		    repeatButton.setText(getResources().getString(
			    R.string.RepeatMonthlyDots));
		break;

	    case MONTH_AFTER:
		repeatButton.setText(getResources().getString(
			R.string.RepeatMonthlyDots));
		break;

	    case YEARLY_ON_DATE:
	    case YEARLY_ON_DAY:
		if (repeatSettings.getIncrement() == 1)
		    repeatButton.setText(getResources().getString(
			    R.string.RepeatEveryYear));
		else if (repeatSettings.getIncrement() == 2)
		    repeatButton.setText(getResources().getString(
			    R.string.RepeatEveryOtherYear));
		else
		    repeatButton.setText(getResources().getString(
			    R.string.RepeatYearlyDots));
		break;

	    case YEAR_AFTER:
		repeatButton.setText(getResources().getString(
			R.string.RepeatYearlyDots));
		break;
	    }
	}
    }

    /** Called when opening one of the dialogs for the first time */
    @Override
    public Dialog onCreateDialog(int id) {
	Log.d(TAG, ".onCreateDialog(" + id + ")");
	switch (id) {
	default:
	    Log.e(TAG, ".onCreateDialog: undefined dialog ID " + id);
	    return null;

	case DUEDATE_LIST_ID:
	    Resources r = getResources();
	    String[] dueDateOptionFormats =
		r.getStringArray(R.array.DueDateFormatList);
	    String[] dueDateListItems =
		new String[dueDateOptionFormats.length + 2];
	    Calendar c = Calendar.getInstance();
	    for (int i = 0; i < dueDateOptionFormats.length; i++) {
		SimpleDateFormat formatter =
		    new SimpleDateFormat(dueDateOptionFormats[i]);
		dueDateListItems[i] = formatter.format(c.getTime());
		c.add(Calendar.DATE, 1);
	    }
	    dueDateListItems[dueDateOptionFormats.length] =
		r.getString(R.string.DueDateNoDate);
	    dueDateListItems[dueDateOptionFormats.length + 1] =
		r.getString(R.string.DueDateOther);
	    AlertDialog.Builder builder = new AlertDialog.Builder(this);
	    builder.setItems(dueDateListItems,
		    new DueDateListSelectionListener());
	    dueDateListDialog = builder.create();
	    return dueDateListDialog;

	case HIDEUNTIL_DIALOG_ID:
	    hideUntilDialog = new Dialog(this);
	    hideUntilDialog.setContentView(R.layout.hide_time);
	    hideUntilDialog.setTitle(R.string.HideTitle);
	    hideCheckBox = (CheckBox)
		hideUntilDialog.findViewById(R.id.HideCheckBox);
	    hideEditDays = (EditText)
		hideUntilDialog.findViewById(R.id.HideEditDaysEarlier);
	    showTime = (TextView)
		hideUntilDialog.findViewById(R.id.HideTextTime);
	    hideOKButton = (Button)
		hideUntilDialog.findViewById(R.id.HideButtonOK);
	    hideCheckBox.setOnCheckedChangeListener(
		    new CompoundButton.OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton button,
			boolean isChecked) {
		    hideEditDays.setEnabled(isChecked);
		    hideOKButton.setEnabled((hideEditDays.length() > 0)
			    || !isChecked);
		    showTime.setVisibility(isChecked
			    ? View.VISIBLE : View.INVISIBLE);
		}
	    });
	    hideEditDays.addTextChangedListener(new TextWatcher() {
		@Override
		public void beforeTextChanged(CharSequence s,
			int start, int count, int after) {
		}
		@Override
		public void onTextChanged(CharSequence s,
			int start, int before, int count) {
		}
		@Override
		public void afterTextChanged(Editable e) {
		    boolean hasText = e.length() > 0;
		    hideOKButton.setEnabled(hasText);
		    if (hasText) {
			StringBuilder sb = new StringBuilder(showTime
				.getResources().getString(R.string.HideTextShow));
			sb.append('\n');
			Calendar c = Calendar.getInstance();
			if (dueDate != null)
			    c.setTime(dueDate);
			c.add(Calendar.DATE, -Integer.parseInt(e.toString()));
			DateFormat df = DateFormat.getDateInstance(
				DateFormat.FULL);
			sb.append(df.format(c.getTime()));
			showTime.setText(sb.toString());
		    } else {
			showTime.setText("");
		    }
		}
	    });
	    hideOKButton.setOnClickListener(new View.OnClickListener() {
		@Override
		public void onClick(View v) {
		    Log.d(TAG, "hideOKButton.onClick: " +
			    (hideCheckBox.isChecked()
				    ? hideEditDays.getText().toString()
				    : "disable"));
		    if (hideCheckBox.isChecked())
			hideDaysInAdvance =
			    Integer.parseInt(hideEditDays.getText().toString());
		    else
			hideDaysInAdvance = null;
		    hideUntilDialog.dismiss();
		    updateHideButton();
		}
	    });
	    Button hideCancelButton = (Button)
		hideUntilDialog.findViewById(R.id.HideButtonCancel);
	    hideCancelButton.setOnClickListener(new View.OnClickListener() {
		@Override
		public void onClick(View v) {
		    hideUntilDialog.dismiss();
		}
	    });
	    return hideUntilDialog;

	case ALARM_DIALOG_ID:
	    alarmDialog = new Dialog(this);
	    alarmDialog.setContentView(R.layout.alarm_time);
	    alarmDialog.setTitle(R.string.AlarmTitle);
	    alarmCheckBox = (CheckBox)
		alarmDialog.findViewById(R.id.AlarmCheckBox);
	    alarmEditDays = (EditText)
		alarmDialog.findViewById(R.id.AlarmEditDaysEarlier);
	    alarmTimePicker = (TimePicker)
		alarmDialog.findViewById(R.id.AlarmTimePicker);
	    alarmNextTime = (TextView)
		alarmDialog.findViewById(R.id.AlarmTextTime);
	    alarmOKButton = (Button)
		alarmDialog.findViewById(R.id.AlarmButtonOK);
	    alarmCheckBox.setOnCheckedChangeListener(
		    new CompoundButton.OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton button,
			boolean isChecked) {
		    alarmEditDays.setEnabled(isChecked);
		    alarmTimePicker.setEnabled(isChecked);
		    alarmOKButton.setEnabled((alarmEditDays.length() > 0)
			    || !isChecked);
		}
	    });
	    alarmEditDays.addTextChangedListener(new TextWatcher() {
		@Override
		public void beforeTextChanged(CharSequence s,
			int start, int count, int after) {
		}
		@Override
		public void onTextChanged(CharSequence s,
			int start, int before, int count) {
		}
		@Override
		public void afterTextChanged(Editable e) {
		    boolean hasText = e.length() > 0;
		    alarmOKButton.setEnabled(hasText);
		    if (hasText) {
			StringBuilder sb = new StringBuilder(
				alarmNextTime.getResources().getString(
					R.string.AlarmTextNextAlarm));
			sb.append('\n');
			Calendar c = Calendar.getInstance();
			if (dueDate != null)
			    c.setTime(dueDate);
			c.add(Calendar.DATE, -Integer.parseInt(e.toString()));
			DateFormat df = DateFormat.getDateInstance(
				DateFormat.FULL);
			sb.append(df.format(c.getTime()));
			alarmNextTime.setText(sb.toString());
		    } else {
			alarmNextTime.setText("");
		    }
		}
	    });
	    alarmOKButton.setOnClickListener(new View.OnClickListener() {
		@Override
		public void onClick(View v) {
		    if (alarmCheckBox.isChecked()) {
			alarmDaysInAdvance =
			    Integer.parseInt(alarmEditDays.getText().toString());
			alarmTime = (alarmTimePicker.getCurrentHour() * 60
				+ alarmTimePicker.getCurrentMinute()) * 60000L;
		    } else {
			alarmDaysInAdvance = null;
			alarmTime = null;
		    }
		    alarmDialog.dismiss();
		    updateAlarmButton();
		}
	    });
	    Button alarmCancelButton = (Button)
		alarmDialog.findViewById(R.id.AlarmButtonCancel);
	    alarmCancelButton.setOnClickListener(new View.OnClickListener() {
		@Override
		public void onClick(View v) {
		    alarmDialog.dismiss();
		}
	    });
	    return alarmDialog;

	case REPEAT_LIST_ID:
	    r = getResources();
	    String[] repeatListStrings = r.getStringArray(R.array.RepeatList);
	    builder = new AlertDialog.Builder(this);
	    builder.setItems(repeatListStrings,
		    new RepeatListSelectionListener());
	    repeatListDialog = builder.create();
	    return repeatListDialog;

	case ENDDATE_DIALOG_ID:
	    c = Calendar.getInstance();
	    c.setTime((repeatSettings.getEndDate() == null) ? ((dueDate == null)
		    ? new Date() : dueDate) : repeatSettings.getEndDate());
	    repeatEndDialog = new CalendarDatePickerDialog(this,
		    getText(R.string.DatePickerTitleEndingOn),
		    new CalendarDatePickerDialog.OnDateSetListener() {
		@Override
		public void onDateSet(CalendarDatePicker dp,
			int year, int month, int day) {
		    Calendar c = new GregorianCalendar(year, month, day);
		    c.set(Calendar.HOUR_OF_DAY, 0);
		    c.set(Calendar.MINUTE, 0);
		    c.set(Calendar.SECOND, 0);
		    repeatSettings.setEndDate(c.getTime());
		    updateRepeatButton();
		}
	    }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DATE));
	    return repeatEndDialog;

	case DUEDATE_DIALOG_ID:
	    c = Calendar.getInstance();
	    c.setTime((dueDate == null) ? new Date() : dueDate);
	    dueDateDialog = new CalendarDatePickerDialog(this,
		    getText(R.string.DatePickerTitleDueDate),
		    new CalendarDatePickerDialog.OnDateSetListener() {
		@Override
		public void onDateSet(CalendarDatePicker dp,
			int year, int month, int day) {
		    Calendar c = new GregorianCalendar(year, month, day);
		    c.set(Calendar.HOUR_OF_DAY, 23);
		    c.set(Calendar.MINUTE, 59);
		    c.set(Calendar.SECOND, 59);
		    dueDate = c.getTime();
		    if (repeatSettings != null)
			repeatSettings.setDueDate(dueDate);
		    updateDueDateButton();
		}
	    }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DATE));
	    return dueDateDialog;

	case REPEAT_DIALOG_ID:
	    repeatDialog = new RepeatEditorDialog(this,
		    new RepeatEditorDialog.OnRepeatSetListener() {
		@Override
		public void onRepeatSet(RepeatEditor re, RepeatSettings s) {
		    repeatSettings = s.clone();
		    updateRepeatButton();
		}
	    });
	    return repeatDialog;
	}
    }

    /** Called when displaying an existing dialog */
    @Override
    public void onPrepareDialog(int id, Dialog dialog) {
	final DateFormat df = DateFormat.getDateInstance(DateFormat.FULL);
	Calendar c;
	switch (id) {
	default: break;

	case DUEDATE_LIST_ID:
	    // To do: We can't actually replace the dialog here.
	    // How does one update the existing dialog text?
	    break;

	case HIDEUNTIL_DIALOG_ID:
	    hideCheckBox.setChecked(hideDaysInAdvance != null);
	    hideEditDays.setText((hideDaysInAdvance == null)
		    ? "0" : hideDaysInAdvance.toString());
	    c = Calendar.getInstance();
	    if (dueDate != null)
		c.setTime(dueDate);
	    if (hideDaysInAdvance != null)
		c.add(Calendar.DATE, -hideDaysInAdvance);
	    StringBuilder sb = new StringBuilder(showTime
		    .getResources().getString(R.string.HideTextShow));
	    sb.append('\n');
	    sb.append(df.format(c.getTime()));
	    showTime.setText(sb.toString());
	    break;

	case ALARM_DIALOG_ID:
	    alarmCheckBox.setChecked(alarmDaysInAdvance != null);
	    alarmEditDays.setText((alarmDaysInAdvance == null)
		    ? "0" : alarmDaysInAdvance.toString());
	    if (alarmTime != null) {
		alarmTimePicker.setCurrentHour((int) (alarmTime / 3600000) % 60);
		alarmTimePicker.setCurrentMinute((int) (alarmTime / 60000) % 60);
	    } else {
		alarmTimePicker.setCurrentHour(8);
		alarmTimePicker.setCurrentMinute(0);
	    }
	    c = Calendar.getInstance();
	    if (dueDate != null)
		c.setTime(dueDate);
	    if (alarmDaysInAdvance != null)
		c.add(Calendar.DATE, -alarmDaysInAdvance);
	    sb = new StringBuilder(alarmNextTime
		    .getResources().getString(R.string.AlarmTextNextAlarm));
	    sb.append('\n');
	    sb.append(df.format(c.getTime()));
	    alarmNextTime.setTag(sb.toString());
	    break;

	case REPEAT_DIALOG_ID:
	    // Update the repeat settings
	    repeatDialog.setRepeatSettings(repeatSettings);
	    break;
	}
    }

    /** Called when the user clicks the due date button */
    class DueDateButtonOnClickListener implements View.OnClickListener {
	@Override
	public void onClick(View v) {
	    Log.d(TAG, "DetailButtonDueDate.onClick");
	    showDialog(DUEDATE_LIST_ID);
	}
    }

    /** Called when the user clicks the Hide button */
    class HideButtonOnClickListener implements View.OnClickListener {
	@Override
	public void onClick(View v) {
	    Log.d(TAG, "DetailButtonHideUntil.onClick");
	    showDialog(HIDEUNTIL_DIALOG_ID);
	}
    }

    /** Called when the user clicks the Alarm button */
    class AlarmButtonOnClickListener implements View.OnClickListener {
	@Override
	public void onClick(View v) {
	    Log.d(TAG, "DetailButtonAlarm.onClick");
	    showDialog(ALARM_DIALOG_ID);
	}
    }

    /** Called when the user clicks the repeat button */
    class RepeatButtonOnClickListener implements View.OnClickListener {
	@Override
	public void onClick(View v) {
	    Log.d(TAG, "DetailButtonRepeat.onClick");
	    showDialog(REPEAT_LIST_ID);
	}
    }

    /** Called when the user selects a new due date */
    class DueDateListSelectionListener
    implements DialogInterface.OnClickListener {
	@Override
	public void onClick(DialogInterface dialog, int which) {
	    Log.d(TAG, "DueDateListSelectionListener.onClick(" + which + ")");
	    switch (which) {
	    default:
		Calendar c = Calendar.getInstance();
		c.add(Calendar.DATE, which);
		c.set(Calendar.HOUR_OF_DAY, 23);
		c.set(Calendar.MINUTE, 59);
		c.set(Calendar.SECOND, 59);
		dueDate = c.getTime();
		if (repeatSettings != null)
		    repeatSettings.setDueDate(dueDate);
		updateDueDateButton();
		break;

	    case 8:	// No date
		dueDate = null;
		updateDueDateButton();
		break;

	    case 9:	// Other
		showDialog(DUEDATE_DIALOG_ID);
		break;
	    }
	    // I don't see any other way to clear up its resources.
	    dueDateDialog = null;
	}
    }

    /** Called when the user selects a new due date */
    class RepeatListSelectionListener
    implements DialogInterface.OnClickListener {
	@Override
	public void onClick(DialogInterface dialog, int which) {
	    Log.d(TAG, "RepeatListSelectionListener.onClick(" + which + ")");

	    final int[] ALL_WEEK_DAYS = { Calendar.SUNDAY,
		    Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
		    Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY };

	    switch (which) {
	    case 0:	// None
		repeatSettings.setIntervalType(IntervalType.NONE);
		updateRepeatButton();
		break;

	    case 1:	// Daily until...
		repeatSettings.setIntervalType(IntervalType.DAILY);
		repeatSettings.setIncrement(1);
		repeatSettings.setOnAllowedWeekdays(ALL_WEEK_DAYS);
		repeatButton.setText(
			getResources().getString(R.string.RepeatDaily));
		showDialog(ENDDATE_DIALOG_ID);
		break;

	    case 2:	// Weekly
		Calendar c = Calendar.getInstance();
		if (dueDate != null)
		    c.setTime(dueDate);
		repeatSettings.setIntervalType(IntervalType.WEEKLY);
		repeatSettings.setIncrement(1);
		repeatSettings.setOnFixedWeekday(
			c.get(Calendar.DAY_OF_WEEK), true);
		updateRepeatButton();
		break;

	    case 3:	// Semi-monthly
		c = Calendar.getInstance();
		if (dueDate != null)
		    c.setTime(dueDate);
		repeatSettings.setIntervalType(
			IntervalType.SEMI_MONTHLY_ON_DATES);
		repeatSettings.setIncrement(1);
		repeatSettings.setOnAllowedWeekdays(ALL_WEEK_DAYS);
		int d1 = c.get(Calendar.DATE);
		repeatSettings.setDate(0, d1);
		if (d1 < 15)
		    repeatSettings.setDate(1, d1 + 15);
		else if (d1 == 15)
		    repeatSettings.setDate(1, 31);
		else if (d1 < 30)
		    repeatSettings.setDate(1, d1 - 15);
		else
		    repeatSettings.setDate(1, 15);
		updateRepeatButton();
		break;

	    case 4:	// Monthly
		c = Calendar.getInstance();
		if (dueDate != null)
		    c.setTime(dueDate);
		repeatSettings.setIntervalType(IntervalType.MONTHLY_ON_DATE);
		repeatSettings.setIncrement(1);
		repeatSettings.setOnAllowedWeekdays(ALL_WEEK_DAYS);
		repeatSettings.setDate(c.get(Calendar.DATE));
		updateRepeatButton();
		break;

	    case 5:	// Yearly
		c = Calendar.getInstance();
		if (dueDate != null)
		    c.setTime(dueDate);
		repeatSettings.setIntervalType(IntervalType.YEARLY_ON_DATE);
		repeatSettings.setIncrement(1);
		repeatSettings.setOnAllowedWeekdays(Calendar.SUNDAY,
			Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
			Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY);
		repeatSettings.setDate(c.get(Calendar.DATE));
		repeatSettings.setMonth(c.get(Calendar.MONTH));
		updateRepeatButton();
		break;

	    case 6:	// Other...
		showDialog(REPEAT_DIALOG_ID);
		break;
	    }
	}
    }

    /** Called when the user clicks OK to save all changes */
    class OKButtonOnClickListener implements View.OnClickListener {
	@Override
	public void onClick(View v) {
	    Log.d(TAG, "DetailButtonOK.onClick");
	    // Collect the current values
	    ContentValues values = new ContentValues();
	    List<String> validationErrors = new LinkedList<String>();

	    /*
	     * We need to compare the new value of the privacy
	     * checkbox with the old private flag in the record.
	     * If it has changed, we'll have to change the
	     * encryption of the note as well as the description.
	     */
	    Cursor itemCursor = getContentResolver().query(todoUri,
		    ITEM_NOTE_PROJECTION, null, null, null);
	    if (!itemCursor.moveToFirst())
		throw new SQLiteDoneException();
	    int wasPrivate = itemCursor.getInt(
		    itemCursor.getColumnIndex(ToDoItem.PRIVATE));
	    String note = null;
	    if (((wasPrivate > 1) != privateCheckBox.isChecked()) &&
		    !itemCursor.isNull(itemCursor.getColumnIndex(ToDoItem.NOTE))) {
		if (wasPrivate > 1) {
		    if (encryptor.hasKey()) {
			try {
			    note = encryptor.decrypt(itemCursor.getBlob(
				    itemCursor.getColumnIndex(ToDoItem.NOTE)));
			} catch (GeneralSecurityException gsx) {
			    itemCursor.close();
			    Toast.makeText(ToDoDetailsActivity.this,
				    gsx.getMessage(), Toast.LENGTH_LONG).show();
			    return;
			}
		    } else {
			Toast.makeText(ToDoDetailsActivity.this,
				R.string.PasswordProtected, Toast.LENGTH_LONG).show();
		    }
		} else {
		    note = itemCursor.getString(
			    itemCursor.getColumnIndex(ToDoItem.NOTE));
		}
	    }
	    itemCursor.close();

	    String description = toDoDescription.getText().toString();
	    if (description.length() > 0) {
		int privacy = 0;
		values.put(ToDoItem.DESCRIPTION, description);
		if (note != null)
		    values.put(ToDoItem.NOTE, note);
		if (privateCheckBox.isChecked()) {
		    privacy = 1;
		    if (encryptor.hasKey()) {
			try {
			    values.put(ToDoItem.DESCRIPTION,
				    encryptor.encrypt(description));
			    if (note != null)
				values.put(ToDoItem.NOTE,
					encryptor.encrypt(note));
			    privacy = 2;
			} catch (GeneralSecurityException gsx) {
			    Toast.makeText(ToDoDetailsActivity.this,
				    gsx.getMessage(), Toast.LENGTH_LONG).show();
			}
		    }
		}
		values.put(ToDoItem.PRIVATE, privacy);
	    } else {
		validationErrors.add(getResources().getString(
			R.string.ErrorDescriptionBlank));
	    }

	    int priority = -1;
	    try {
		priority = Integer.parseInt(priorityText.getText().toString());
	    } catch (NumberFormatException nfx) {}
	    if (priority > 0)
		values.put(ToDoItem.PRIORITY, priority);
	    else
		validationErrors.add(getResources().getString(
			R.string.ErrorPriority));

	    long categoryID = categoryList.getSelectedItemId();
	    if (categoryID != AdapterView.INVALID_ROW_ID)
		values.put(ToDoItem.CATEGORY_ID, categoryID);
	    else
		validationErrors.add(getResources().getString(
			R.string.ErrorCategoryID));

	    if (dueDate == null)
		values.putNull(ToDoItem.DUE_TIME);
	    else
		values.put(ToDoItem.DUE_TIME, dueDate.getTime());

	    if ((dueDate == null) || (hideDaysInAdvance == null))
		values.putNull(ToDoItem.HIDE_DAYS_EARLIER);
	    else
		values.put(ToDoItem.HIDE_DAYS_EARLIER, hideDaysInAdvance);

	    if ((dueDate == null) || (alarmDaysInAdvance == null)) {
		values.putNull(ToDoItem.ALARM_DAYS_EARLIER);
		values.putNull(ToDoItem.ALARM_TIME);
		values.putNull(ToDoItem.NOTIFICATION_TIME);
	    } else {
		values.put(ToDoItem.ALARM_DAYS_EARLIER, alarmDaysInAdvance);
		values.put(ToDoItem.ALARM_TIME, alarmTime);
		/*
		 * We don't overwrite any previous notification time here.
		 * If the user was previously notified, and subsequently
		 * changes the alarm, it does not take effect until the
		 * next occurrence of that alarm time.
		 */
	    }

	    if (dueDate == null) {
		new RepeatSettings(RepeatSettings.IntervalType.NONE)
			.store(values);
	    } else {
		repeatSettings.store(values);
	    }

	    values.put(ToDoItem.MOD_TIME, System.currentTimeMillis());

	    if (validationErrors.size() > 0) {
		// Show an alert dialog
		AlertDialog.Builder builder =
		    new AlertDialog.Builder(ToDoDetailsActivity.this);
		builder.setIcon(android.R.drawable.ic_dialog_alert);
		builder.setNeutralButton(R.string.ConfirmationButtonOK,
			new DialogInterface.OnClickListener() {
			    @Override
			    public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			    }
			});
		StringBuilder sb = new StringBuilder();
		for (String error : validationErrors) {
		    if (sb.length() > 0)
			sb.append("  ");
		    sb.append(error);
		}
		builder.setMessage(sb.toString());
		builder.create().show();
	    } else {
		// Write and commit the changes
		try {
		    getContentResolver().update(todoUri, values, null, null);
		    ToDoDetailsActivity.this.finish();
		} catch (SQLException sx) {
		    new AlertDialog.Builder(ToDoDetailsActivity.this)
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
    }

    /** Called when the users clicks Delete... */
    class DeleteButtonOnClickListener implements View.OnClickListener {
	@Override
	public void onClick(View v) {
	    Log.d(TAG, "DetailButtonDelete.onClick");
	    AlertDialog.Builder builder =
		new AlertDialog.Builder(ToDoDetailsActivity.this);
	    builder.setIcon(android.R.drawable.ic_dialog_alert);
	    builder.setMessage(R.string.ConfirmationTextDeleteToDo);
	    builder.setNegativeButton(R.string.ConfirmationButtonCancel,
		    new DialogInterface.OnClickListener() {
		@Override
		public void onClick(DialogInterface dialog, int which) {
		    dialog.dismiss();
		}
	    });
	    builder.setPositiveButton(R.string.ConfirmationButtonOK,
		    new DialogInterface.OnClickListener() {
		@Override
		public void onClick(DialogInterface dialog, int which) {
		    dialog.dismiss();
		    try {
			ToDoDetailsActivity.this.getContentResolver().delete(
				ToDoDetailsActivity.this.todoUri, null, null);
			ToDoDetailsActivity.this.finish();
		    } catch (SQLException sx) {
			new AlertDialog.Builder(ToDoDetailsActivity.this)
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
	    builder.create().show();
	}
    }
}
