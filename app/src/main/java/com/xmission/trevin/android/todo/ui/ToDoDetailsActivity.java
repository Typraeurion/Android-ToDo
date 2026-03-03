/*
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
 */
package com.xmission.trevin.android.todo.ui;

import static com.xmission.trevin.android.todo.ui.ToDoListActivity.EXTRA_CATEGORY_ID;
import static com.xmission.trevin.android.todo.ui.ToDoListActivity.EXTRA_ITEM_ID;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.xmission.trevin.android.todo.R;
import com.xmission.trevin.android.todo.data.ToDoAlarm;
import com.xmission.trevin.android.todo.data.ToDoCategory;
import com.xmission.trevin.android.todo.data.ToDoItem;
import com.xmission.trevin.android.todo.data.ToDoPreferences;
import com.xmission.trevin.android.todo.data.repeat.*;
import com.xmission.trevin.android.todo.provider.ToDoRepository;
import com.xmission.trevin.android.todo.provider.ToDoRepositoryImpl;
import com.xmission.trevin.android.todo.provider.ToDoSchema.*;
import com.xmission.trevin.android.todo.util.EncryptionException;
import com.xmission.trevin.android.todo.util.StringEncryption;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.DataSetObserver;
import android.database.SQLException;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.*;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

/**
 * Displays the details of a To Do item.  Will display the item from the
 * {@link Uri} provided in the intent, which is required.
 *
 * @author Trevin Beattie
 */
public class ToDoDetailsActivity extends Activity {

    private static final String TAG = "ToDoDetailsActivity";

    // Internal dialog ID's
    private static final int DUEDATE_LIST_ID = 2;
    private static final int HIDEUNTIL_DIALOG_ID = 3;
    private static final int ALARM_DIALOG_ID = 4;
    private static final int ENDDATE_DIALOG_ID = 5;
    private static final int REPEAT_LIST_ID = 6;
    private static final int DUEDATE_DIALOG_ID = 7;
    private static final int REPEAT_DIALOG_ID = 8;

    /**
     * Request code to use when starting the note activity from the
     * details activity.  This lets us know the note should be returned
     * to the details rather than saved directly in the repository.
     */
    public static final int DETAILS_HANDOFF_REQUEST = 14;

    /**
     * The ID of the To Do item we are editing; {@code null} for a new item.
     */
    private Long todoId;

    /**
     * Copy of the To Do item we are editing.  This acts as a container
     * for many of the fields the user can modify in this detail activity,
     * along with preserving fields (like the {@code note}) which are
     * not available in this UI.
     */
    private ToDoItem todo;

    /** Whether we are creating a new To Do item (convenience value) */
    private boolean isNewToDo;

    /** The To Do database */
    ToDoRepository repository = null;

    /**
     * To Do application preferences; we mainly use this
     * to obtain the current time zone.
     */
    ToDoPreferences prefs = null;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    /** Used to check whether we can post notifications */
    NotificationManager notificationManager;

    /** The To Do item text (edit field) */
    EditText toDoDescription = null;

    /**
     * A numeric field to select the priority
     */
    EditText priorityText = null;

    /** The due date button shows the due date */
    Button dueDateButton = null;

    /** Category selection spinner */
    Spinner categoryList = null;

    /** Adapter that provides entries for the category list */
    CategorySelectAdapter categoryAdapter = null;

    /** Adapter that provides a list of due dates in the near future */
    DueDateSelectAdapter dueDateAdapter = null;

    /** The alarm time is also a button */
    Button alarmText = null;

    /** The repeat button shows the repeat interval */
    Button repeatButton = null;

    /**
     * Repeat settings based on the interval.
     * This is shared with the {@link RepeatEditorDialog}.
     */
    RepeatSettings repeatSettings = null;

    /** The hide time is also a button */
    Button hideText = null;

    /** Checkbox for private records */
    CheckBox privateCheckBox = null;

    /** The &ldquoOK&rdquo; button for saving the item */
    Button okButton = null;

    /** The &ldquo;Delete&rdquo; button for existing items */
    Button deleteButton = null;

    /** Due date list dialog */
    Dialog dueDateListDialog = null;

    /** Due Date dialog box */
    CalendarDatePickerDialog dueDateDialog = null;

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

    /**
     * Copy of the item&rqsuo;s alarm in case the user disables it
     * but re-enables it before saving, so we don&rsquo;t lose
     * any extra data (i.e. the last notification time).
     */
    ToDoAlarm alarm = null;

    /**
     * Copy of any original alarm in the item to see whether it
     * has changed before saving, in which case we want to
     * clear the last notification time.
     */
    ToDoAlarm originalAlarm = null;

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
    CalendarDatePickerDialog repeatEndDialog = null;

    /** Repeat dialog box */
    RepeatEditorDialog repeatDialog = null;

    StringEncryption encryptor;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);

        Object savedData = null;
        if (savedInstanceState != null) {
            savedData = savedInstanceState.getSerializable("detailFormData");
        } else {
            savedData = getLastNonConfigurationInstance();
        }
        boolean hasSavedState = (savedData instanceof DetailFormData);

        if (hasSavedState) {
            todoId = ((DetailFormData) savedData).item.getId();
            isNewToDo = (todoId == null);

            Log.d(TAG, String.format(Locale.US,
                    ".onCreate(%s); savedData=%s",
                    savedInstanceState, savedData));
        } else {
            Intent intent = getIntent();
            todoId = null;
            todo = new ToDoItem();
            originalAlarm = null;
            if (intent.hasExtra(EXTRA_ITEM_ID)) {
                todoId = intent.getLongExtra(EXTRA_ITEM_ID, -1);
                todo.setId(todoId);
                isNewToDo = false;
            } else {
                isNewToDo = true;
            }
            todo.setCategoryId(intent.getLongExtra(EXTRA_CATEGORY_ID,
                    ToDoCategory.UNFILED));

            Log.d(TAG, String.format(Locale.US,
                    ".onCreate(%s); id=%s, categoryId=%d",
                    savedInstanceState, todoId, todo.getCategoryId()));
        }

        notificationManager = (NotificationManager)
                getSystemService(NOTIFICATION_SERVICE);

        if (repository == null)
            repository = ToDoRepositoryImpl.getInstance();
        prefs = ToDoPreferences.getInstance(this);
        encryptor = StringEncryption.holdGlobalEncryption();

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

        categoryAdapter = new CategorySelectAdapter(this, repository);
        categoryList.setAdapter(categoryAdapter);
        categoryAdapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                long categoryId = (todo == null) ? ToDoCategory.UNFILED
                        : todo.getCategoryId();
                categoryList.setSelection(categoryAdapter
                        .getCategoryPosition(categoryId));
            }
        });

        // If we are being re-created from a destroyed instance,
        // restore the previous dialog state.
        if (hasSavedState) {
            restoreState((DetailFormData) savedData);
        }

        else {
            // Initialize the default state for now; the OpenRepositoryRunner
            // will load any item and update the form when it's ready.
            privateCheckBox.setChecked(false);
            toDoDescription.setText("");
            toDoDescription.setEnabled(isNewToDo);
            priorityText.setText("1");
            // The data set observer will set the selected category
            // once the adapter has loaded its list.
            categoryList.setSelection(categoryAdapter.getCategoryPosition(
                    todo.getCategoryId()));

            completedDateText.setText("");
            View lastCompletedTableRow = findViewById(R.id.LastCompletedTableRow);
            if (lastCompletedTableRow != null)
                lastCompletedTableRow.setVisibility(View.GONE);

            updateDueDateButton();
            updateHideButton();
            updateAlarmButton();
            repeatSettings = null;
            updateRepeatButton();
        }

        // Set callbacks
        toDoDescription.addTextChangedListener(new DescriptionChangedListener());
        dueDateButton.setOnClickListener(new DueDateButtonOnClickListener());
        hideText.setOnClickListener(new HideButtonOnClickListener());
        alarmText.setOnClickListener(new AlarmButtonOnClickListener());
        repeatButton.setOnClickListener(new RepeatButtonOnClickListener());

        // Disable the Done and Delete buttons until the repository is ready
        okButton = (Button) findViewById(R.id.DetailButtonOK);
        okButton.setEnabled(false);
        deleteButton = (Button) findViewById(R.id.DetailButtonDelete);
        deleteButton.setEnabled(false);

        ImageButton noteButton = (ImageButton) findViewById(R.id.DetailButtonNote);
        noteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "DetailButtonNote.onClick");
                Intent intent = new Intent(v.getContext(),
                        ToDoNoteActivity.class);
                intent.putExtra(ToDoNoteActivity.EXTRA_ITEM_DESCRIPTION,
                        toDoDescription.getText().toString());
                intent.putExtra(ToDoNoteActivity.EXTRA_ITEM_NOTE,
                        (todo.getNote() == null) ? "" : todo.getNote());
                if (todoId != null)
                    intent.putExtra(EXTRA_ITEM_ID, todoId);
                ToDoDetailsActivity.this.startActivityForResult(intent,
                        DETAILS_HANDOFF_REQUEST);
            }
        });

        // Connect to the database (on a non-UI thread) and populate the UI.
        Runnable openRepo = new OpenRepositoryRunner(
                !(isNewToDo || hasSavedState));
        executor.submit(openRepo);
    }

    /**
     * A runner to open the database on a non-UI thread
     * (if on Honeycomb or later) and then load the To Do item into the UI.
     */
    private class OpenRepositoryRunner implements Runnable {
        final boolean loadItem;
        OpenRepositoryRunner(boolean loadItem) {
            this.loadItem = loadItem;
        }
        @Override
        public void run() {
            repository.open(ToDoDetailsActivity.this);
            if (loadItem) {
                todo = repository.getItemById(todoId);
                originalAlarm = todo.getAlarm();
            }
            runOnUiThread(new FinalizeUIRunner(loadItem));
        }
    }

    /**
     * Called (on the UI thread) after we&rsquo;ve established a
     * connection to the databaase and read the To Do item (if any)
     * to populate the UI and enable buttons.
     */
    private class FinalizeUIRunner implements Runnable {
        @Nullable
        final boolean refresh;
        /**
         * @param refreshToDo whether to update the UI elements from the
         * class {@code todo} field.  This should be {@code true} if
         * {@code todo} has been read from the database prior to calling
         * this runner; pass {@code false} if we are creating a new item
         * or restoring a saved state.
         */
        FinalizeUIRunner(boolean refreshToDo) {
            refresh = refreshToDo;
        }
        @Override
        public void run() {
            if (refresh) {
                privateCheckBox.setChecked(todo.isPrivate());
                if (todo.isEncrypted()) {
                    if (encryptor.hasKey()) {
                        try {
                            todo.setDescription(encryptor.decrypt(
                                    todo.getEncryptedDescription()));
                            if (todo.getEncryptedNote() != null)
                                todo.setNote(encryptor.decrypt(
                                        todo.getEncryptedNote()));
                        } catch (EncryptionException e) {
                            Toast.makeText(ToDoDetailsActivity.this,
                                    e.getMessage(), Toast.LENGTH_LONG).show();
                            finish();
                            return;
                        }
                    } else {
                        Toast.makeText(ToDoDetailsActivity.this,
                                R.string.PasswordProtected,
                                Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }
                }
                toDoDescription.setText(todo.getDescription());
                toDoDescription.setEnabled(true);

                priorityText.setText(Integer.toString(todo.getPriority()));
                categoryList.setSelection(categoryAdapter
                        .getCategoryPosition(todo.getCategoryId()));

                TextView completedDateText = (TextView)
                        findViewById(R.id.DetailTextCompletedDate);
                View lastCompletedTableRow =
                        findViewById(R.id.LastCompletedTableRow);
                if (todo.getCompleted() == null) {
                    completedDateText.setText("");
                    if (lastCompletedTableRow != null)
                        lastCompletedTableRow.setVisibility(View.GONE);
                } else {
                    completedDateText.setText(todo.getCompleted()
                            .atZone(prefs.getTimeZone()).format(
                                    DateTimeFormatter.ofLocalizedDateTime(
                                            FormatStyle.MEDIUM)));
                    if (lastCompletedTableRow != null)
                        lastCompletedTableRow.setVisibility(View.VISIBLE);
                }

                updateDueDateButton();
                updateHideButton();
                updateAlarmButton();
                if (todo.getRepeatInterval() != null)
                    repeatSettings = new RepeatSettings(
                            todo.getRepeatInterval(), todo.getDue());
                updateRepeatButton();
            }

            // Set more callbacks and enable the OK button
            okButton.setOnClickListener(new OKButtonOnClickListener());
            okButton.setEnabled(!TextUtils.isEmpty(todo.getDescription()));

            Button button = (Button) findViewById(R.id.DetailButtonCancel);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.d(TAG, "DetailButtonCancel.onClick");
                    ToDoDetailsActivity.this.finish();
                }
            });

            // If this is a new item, remove the Delete button.
            if (isNewToDo) {
                deleteButton.setVisibility(View.GONE);
                deleteButton.setEnabled(false);
            } else {
                deleteButton.setVisibility(View.VISIBLE);
                deleteButton.setOnClickListener(
                        new DeleteButtonOnClickListener());
                deleteButton.setEnabled(true);
            }
        }
    }

    /**
     * Restore the state of the activity from a saved configuration
     *
     * @param data the saved configuration data
     */
    private void restoreState(DetailFormData data) {
        isNewToDo = (todoId == null);
        todo = data.item;
        toDoDescription.setText(todo.getDescription());
        toDoDescription.setEnabled(true);
        priorityText.setText(Integer.toString(todo.getPriority()));
        updateDueDateButton();
        categoryList.setSelection(categoryAdapter
                .getCategoryPosition(todo.getCategoryId()));
        alarm = data.alarm;
        originalAlarm = data.originalAlarm;
        repeatSettings = data.repeatDialogSettings;
        updateAlarmButton();
        updateHideButton();
        updateRepeatButton();
    }

    /**
     * Called when {@link ToDoNoteActivity} returns a result
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, String.format(Locale.US,
                ".onActivityResult(%d,%d)", requestCode, resultCode));
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != DETAILS_HANDOFF_REQUEST) {
            Log.w(TAG, "Received a result from an unknown request; ignoring it.");
            return;
        }
        if (resultCode != RESULT_OK)
            return;
        // Retrieve the note and update our local copy
        String note = data.getStringExtra(ToDoNoteActivity.EXTRA_ITEM_NOTE);
        if ((note != null) && note.length() == 0)
            note = null;
        todo.setNote(note);
    }

    /**
     * Called when the activity is about to be destroyed
     * and then immediately restarted (such as an orientation change).
     *
     * @return the form data needed to restore the state
     */
    @Override
    public DetailFormData onRetainNonConfigurationInstance() {
        Log.d(TAG, ".onRetainNonConfigurationInstance");
        // Save the current dialog state
        DetailFormData data = new DetailFormData();
        data.item = todo;
        todo.setDescription(toDoDescription.getText().toString());
        todo.setPriority(Integer.parseInt(priorityText.getText().toString()));
        // FIXME: These should all be updated as we go!
        todo.setCategoryId(categoryList.getSelectedItemId());
        data.repeatDialogSettings = repeatSettings;
        if ((repeatDialog != null) && repeatDialog.isShowing())
            data.repeatDialogSettings = repeatDialog.getRepeatSettings();
        if ((hideUntilDialog != null) && hideUntilDialog.isShowing()) {
            boolean hideEnabled = hideCheckBox.isChecked();
            String hideDaysText = hideEditDays.getText().toString();
            if (hideEnabled)
                todo.setHideDaysEarlier(Integer.parseInt(hideDaysText));
        }
        data.alarm = todo.getAlarm();
        data.originalAlarm = originalAlarm;
        if ((alarmDialog != null) && alarmDialog.isShowing()) {
            if (data.alarm == null)
                data.alarm = new ToDoAlarm();
            data.alarm.setTime(LocalTime.of(alarmTimePicker.getCurrentHour(),
                    alarmTimePicker.getCurrentMinute()));
            data.alarm.setAlarmDaysEarlier(Integer.parseInt(
                    alarmEditDays.getText().toString()));
            if (alarmCheckBox.isChecked()) {
                if (todo.getAlarm() == null)
                    todo.setAlarm(data.alarm);
            } else {
                todo.setAlarm(null);
            }
        }
        return data;
    }

    /**
     * Called when the activity is about to be destroyed and then
     * restarted at some indefinite point in the future,
     * for example when Android needs to reclaim resources.
     *
     * @param outState a container in which to save the state.
     */
    @Override
    public void onSaveInstanceState(Bundle outState) {
        Log.d(TAG, ".onSaveInstanceState");
        outState.putSerializable("detailFormData",
                onRetainNonConfigurationInstance());
        super.onSaveInstanceState(outState);
    }

    /** Called when the activity is about to be destroyed */
    @Override
    public void onDestroy() {
        repository.release(this);
        StringEncryption.releaseGlobalEncryption(this);
        super.onDestroy();
    }

    /** Set the date in the due date button */
    void updateDueDateButton() {
        if (todo.getDue() == null) {
            dueDateButton.setText(
                    getResources().getString(R.string.DetailNotset));
            alarmText.setVisibility(View.GONE);
            repeatButton.setVisibility(View.GONE);
            hideText.setVisibility(View.GONE);
        } else {
            dueDateButton.setText(todo.getDue().format(DateTimeFormatter
                    .ofLocalizedDate(FormatStyle.MEDIUM)));
            alarmText.setVisibility(View.VISIBLE);
            repeatButton.setVisibility(View.VISIBLE);
            hideText.setVisibility(View.VISIBLE);
        }
    }

    /** Set the hide time in the hide button */
    void updateHideButton() {
        if ((todo.getDue() == null) || (todo.getHideDaysEarlier() == null))
            hideText.setText(getResources().getString(R.string.DetailNotset));
        else
            hideText.setText(String.format(getResources().getQuantityString(
                            R.plurals.DetailTextDaysEarlier,
                            todo.getHideDaysEarlier()),
                    todo.getHideDaysEarlier()));
    }

    /** Set the alarm time in the alarm button */
    void updateAlarmButton() {
        if (todo.getAlarm() == null)
            alarmText.setText(getResources().getString(R.string.DetailNotset));
        else
            alarmText.setText(String.format(getResources().getQuantityString(
                            R.plurals.DetailTextDaysEarlier,
                            todo.getAlarm().getAlarmDaysEarlier()),
                    todo.getAlarm().getAlarmDaysEarlier()));
    }

    /** Set the repeat interval in the repeat button */
    void updateRepeatButton() {
        if ((todo.getRepeatInterval() == null) ||
                (todo.getRepeatInterval().getType() == RepeatType.NONE)) {
            repeatButton.setText(
                    getResources().getString(R.string.RepeatNone));
        } else {
            AbstractRepeat ar = (AbstractRepeat) todo.getRepeatInterval();
            switch (ar.getType()) {
                case DAILY:
                    if (ar.getEnd() == null) {
                        repeatButton.setText(getResources().getString(
                                (ar.getIncrement() == 1)
                                        ? R.string.RepeatDaily
                                        : R.string.RepeatDailyDots));
                    } else {
                        String text = String.format(getResources().getString(
                                        R.string.RepeatDailyUntilWhen),
                                ar.getEnd().format(DateTimeFormatter
                                        .ofLocalizedDate(FormatStyle.SHORT)));
                        repeatButton.setText(text);
                    }
                    break;

                case DAY_AFTER:
                    repeatButton.setText(getResources().getString(
                            R.string.RepeatDailyDots));
                    break;

                case WEEKLY:
                    RepeatWeekly rw = (RepeatWeekly) todo.getRepeatInterval();
                    if ((ar.getIncrement() == 1) &&
                            (rw.getWeekDays().size() == 1))
                        repeatButton.setText(getResources().getString(
                                R.string.RepeatEveryWeek));
                    else if ((ar.getIncrement() == 2) &&
                            (rw.getWeekDays().size() == 1))
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
                    if (ar.getIncrement() == 1)
                        repeatButton.setText(getResources().getString(
                                R.string.RepeatEveryMonth));
                    else if (ar.getIncrement() == 2)
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
                    if (ar.getIncrement() == 1)
                        repeatButton.setText(getResources().getString(
                                R.string.RepeatEveryYear));
                    else if (ar.getIncrement() == 2)
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
        Log.d(TAG, String.format(Locale.US, ".onCreateDialog(%d)", id));
        AlertDialog.Builder builder;
        switch (id) {
            default:
                Log.e(TAG, ".onCreateDialog: undefined dialog ID " + id);
                return null;

            case DUEDATE_LIST_ID:
                if (dueDateAdapter == null)
                    dueDateAdapter = new DueDateSelectAdapter(this, prefs);
                builder = new AlertDialog.Builder(this);
                builder.setAdapter(dueDateAdapter,
                        new DueDateListSelectionListener());
                dueDateListDialog = builder.create();
                return dueDateListDialog;

            case DUEDATE_DIALOG_ID:
                dueDateDialog = new CalendarDatePickerDialog(this,
                        getText(R.string.DatePickerTitleDueDate),
                        new DueDateCalendarSelectionListener());
                return dueDateDialog;

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
                        new HideCheckBoxOnChangeListener());
                hideEditDays.addTextChangedListener(
                        new HideDaysEarlierTextWatcher());
                hideOKButton.setOnClickListener(new HideOKOnClickListener());
                Button hideCancelButton = (Button)
                        hideUntilDialog.findViewById(R.id.HideButtonCancel);
                hideCancelButton.setOnClickListener(
                        new HideCancelOnClickListener());
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
                        new AlarmCheckBoxOnChangeListener());
                alarmEditDays.addTextChangedListener(
                        new AlarmDaysEarlierTextWatcher());
                alarmOKButton.setOnClickListener(new AlarmOKClickListener());
                Button alarmCancelButton = (Button)
                        alarmDialog.findViewById(R.id.AlarmButtonCancel);
                alarmCancelButton.setOnClickListener(
                        new AlarmCancelOnClickListener());
                return alarmDialog;

            case REPEAT_LIST_ID:
                String[] repeatListStrings =
                        getResources().getStringArray(R.array.RepeatList);
                builder = new AlertDialog.Builder(this);
                builder.setItems(repeatListStrings,
                        new RepeatListSelectionListener());
                repeatListDialog = builder.create();
                return repeatListDialog;

            case ENDDATE_DIALOG_ID:
                repeatEndDialog = new CalendarDatePickerDialog(this,
                    getText(R.string.DatePickerTitleEndingOn),
                    new RepeatEndPickListener());
            return repeatEndDialog;

            case REPEAT_DIALOG_ID:
                repeatDialog = new RepeatEditorDialog(this,
                        new RepeatSetListener());
                if (repeatSettings != null)
                    repeatDialog.restoreRepeatSettings(repeatSettings);
                return repeatDialog;
        }
    }

    /** Called when displaying an existing dialog */
    @Override
    public void onPrepareDialog(int id, Dialog dialog) {
        Log.d(TAG, String.format(Locale.US, ".onPrepareDialog(%d)", id));
        final DateTimeFormatter df = DateTimeFormatter
                .ofLocalizedDate(FormatStyle.FULL);
        LocalDate d;
        ZoneId zone = prefs.getTimeZone();
        LocalDate today = LocalDate.now(zone);
        StringBuilder sb;
        switch (id) {

            case DUEDATE_LIST_ID:
                dueDateAdapter.refreshDates();
                break;

            case DUEDATE_DIALOG_ID:
                LocalDate tentativeDueDate = (todo.getDue() == null)
                        ? today : todo.getDue();
                dueDateDialog.setToday(today);
                dueDateDialog.setDate(tentativeDueDate);
                dueDateDialog.setTimeZone(zone);
                break;

            case HIDEUNTIL_DIALOG_ID:
                hideCheckBox.setChecked(todo.getHideDaysEarlier() != null);
                hideEditDays.setText((todo.getHideDaysEarlier() == null)
                        ? "0" : todo.getHideDaysEarlier().toString());
                d = (todo.getDue() == null) ? today : todo.getDue();
                if (todo.getHideDaysEarlier() != null)
                    d = d.minusDays(todo.getHideDaysEarlier());
                sb = new StringBuilder(showTime
                        .getResources().getString(R.string.HideTextShow));
                sb.append('\n');
                sb.append(d.format(df));
                showTime.setText(sb.toString());
                break;

            case ALARM_DIALOG_ID:
                boolean hasAlarm = (todo.getAlarm() != null);
                alarmCheckBox.setChecked(hasAlarm);
                d = (todo.getDue() == null)
                        ? LocalDate.now(zone) : todo.getDue();
                if (hasAlarm) {
                    alarm = todo.getAlarm();
                } else if (alarm == null) {
                    alarm = new ToDoAlarm();
                    alarm.setTime(LocalTime.of(8, 0));
                }
                alarmEditDays.setText(Integer.toString(
                        alarm.getAlarmDaysEarlier()));
                alarmTimePicker.setCurrentHour(alarm.getTime().getHour());
                alarmTimePicker.setCurrentMinute(alarm.getTime().getMinute());
                d = d.minusDays(alarm.getAlarmDaysEarlier());
                sb = new StringBuilder(alarmNextTime
                        .getResources().getString(R.string.AlarmTextNextAlarm));
                sb.append('\n');
                sb.append(d.format(df));
                alarmNextTime.setTag(sb.toString());
                break;

            case REPEAT_DIALOG_ID:
                // Update the repeat settings
                repeatDialog.setTimeZone(zone);
                repeatDialog.setRepeat(todo.getRepeatInterval(), todo.getDue());
                break;

            case ENDDATE_DIALOG_ID:
                LocalDate tentativeEndDate = null;
                if (todo.getRepeatInterval() instanceof AbstractRepeat)
                    tentativeEndDate = ((AbstractRepeat)
                            todo.getRepeatInterval()).getEnd();
                if (tentativeEndDate == null)
                    tentativeEndDate = (todo.getDue() == null)
                            ? today : todo.getDue();
                repeatEndDialog.setToday(today);
                repeatEndDialog.setDate(tentativeEndDate);
                repeatEndDialog.setTimeZone(prefs.getTimeZone());
                break;

            default: break;
        }
    }

    /** Called when the user changes the item description */
    class DescriptionChangedListener implements TextWatcher {
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
            okButton.setEnabled(e.length() > 0);
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

    /** Called when the user toggles the hide checkbox */
    class HideCheckBoxOnChangeListener
            implements CompoundButton.OnCheckedChangeListener {
        @Override
        public void onCheckedChanged(CompoundButton button,
                                     boolean isChecked) {
            hideEditDays.setEnabled(isChecked);
            hideOKButton.setEnabled((hideEditDays.length() > 0)
                    || !isChecked);
            showTime.setVisibility(isChecked
                    ? View.VISIBLE : View.INVISIBLE);
        }
    }

    /**
     * Called when the content of the &ldquo;hide days earlier&rdquo;
     * text entry box changes
     */
    class HideDaysEarlierTextWatcher implements TextWatcher {
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
                LocalDate targetDate = ((todo.getDue() == null)
                        ? LocalDate.now(prefs.getTimeZone()) : todo.getDue())
                        .minusDays(Integer.parseInt(e.toString()));
                sb.append(targetDate.format(DateTimeFormatter
                        .ofLocalizedDate(FormatStyle.FULL)));
                showTime.setText(sb.toString());
            } else {
                showTime.setText("");
            }
        }
    }

    /**
     * Called when the user clicks OK on the &ldquo;hide until&rdquo; dialog.
     */
    class HideOKOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            Log.d(TAG, "hideOKButton.onClick: " +
                    (hideCheckBox.isChecked()
                            ? hideEditDays.getText().toString()
                            : "disable"));
            if (hideCheckBox.isChecked())
                todo.setHideDaysEarlier(
                        Integer.parseInt(hideEditDays.getText().toString()));
            else
                todo.setHideDaysEarlier(null);
            hideUntilDialog.dismiss();
            updateHideButton();
        }
    }

    /** Called when the user cancels the &ldquo;hide until&rdquo; dialog. */
    class HideCancelOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            hideUntilDialog.dismiss();
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

    /** Called when the user clicks the Alarm button */
    class AlarmButtonOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            Log.d(TAG, "DetailButtonAlarm.onClick");
            showDialog(ALARM_DIALOG_ID);
        }
    }

    /** Called when the user toggles the alarm checkbox */
    class AlarmCheckBoxOnChangeListener
            implements CompoundButton.OnCheckedChangeListener {
        @Override
        public void onCheckedChanged(CompoundButton button,
                                     boolean isChecked) {
            alarmEditDays.setEnabled(isChecked);
            alarmTimePicker.setEnabled(isChecked);
            alarmOKButton.setEnabled((alarmEditDays.length() > 0)
                    || !isChecked);
        }
    }

    /**
     * Called when the content of the &ldquo;alarm days earlier&rdquo;
     * text entry box changes
     */
    class AlarmDaysEarlierTextWatcher implements TextWatcher {
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
                LocalDate targetDate = ((todo.getDue() == null)
                        ? LocalDate.now(prefs.getTimeZone()) : todo.getDue())
                        .minusDays(Integer.parseInt(e.toString()));
                sb.append(targetDate.format(DateTimeFormatter
                        .ofLocalizedDate(FormatStyle.FULL)));
                alarmNextTime.setText(sb.toString());
            } else {
                alarmNextTime.setText("");
            }
        }
    }

    /** Called when the user clicks OK on the alarm dialog. */
    class AlarmOKClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            if (alarmCheckBox.isChecked()) {
                checkNotifyPermission();
                alarm.setAlarmDaysEarlier(Integer.parseInt(
                        alarmEditDays.getText().toString()));
                alarm.setTime(LocalTime.of(alarmTimePicker.getCurrentHour(),
                        alarmTimePicker.getCurrentMinute()));
                todo.setAlarm(alarm);
            } else {
                todo.setAlarm(null);
            }
            alarmDialog.dismiss();
            updateAlarmButton();
        }
    }

    /** Called when the user clicks Cancel on the alarm dialog. */
    class AlarmCancelOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            alarmDialog.dismiss();
        }
    }

    /** Called when the user picks an end date for a repeating interval */
    class RepeatEndPickListener
            implements CalendarDatePickerDialog.OnDateSetListener {
        @Override
        public void onDateSet(CalendarDatePicker dp, LocalDate day) {
            if (repeatSettings != null)
                repeatSettings.setEndDate(day);
            if (todo.getRepeatInterval() instanceof AbstractRepeat)
                ((AbstractRepeat) todo.getRepeatInterval()).setEnd(day);
            updateRepeatButton();
        }
    }

    /** Called when the user configures a new repeating interval. */
    class RepeatSetListener implements RepeatEditorDialog.OnRepeatSetListener {
        @Override
        public void onRepeatSet(RepeatEditor re, RepeatInterval r) {
            todo.setRepeatInterval(r);
            updateRepeatButton();
        }
    }

    /** Generic dialog dismissal listener */
    static final DialogInterface.OnClickListener DISMISS_LISTENER =
            new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            };

    /**
     * Check whether the user has granted us permission to show notifications.
     * If not, request the permission if possible.
     *
     * @return true if we are allowed to post notifications
     */
    private boolean checkNotifyPermission() {

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M)
            // In Marshmallow and earlier, permission
            // to post notifications is assumed.
            return true;

        if (notificationManager.areNotificationsEnabled())
            return true;

        Log.d(TAG, "Notifications are not enabled;"
                + " requesting permission from the user");
        // Android does not provide any way to programatically
        // request notification permission until SDK 33 (Tiramisu)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!shouldShowRequestPermissionRationale(
                    Manifest.permission.POST_NOTIFICATIONS)) {
                requestPermissions(new String[] {
                        Manifest.permission.POST_NOTIFICATIONS
                }, R.id.AlarmButtonOK);
                return false;
            }
        }

        showNotificationRationale();
        return false;

    }

    private void showNotificationRationale() {
        new AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setTitle(R.string.PermissionRequiredTitle)
                .setMessage(R.string.PermissionToPostNotificationRationale)
                .setNeutralButton(R.string.ConfirmationButtonOK, DISMISS_LISTENER)
                .create().show();
    }

    /** Called when the user grants or denies permission */
    @Override
    public void onRequestPermissionsResult(
            int code, String[] permissions, int[] results) {

        // This part is all just for debug logging.
        String[] resultNames = new String[results.length];
        for (int i = 0; i < results.length; i++) {
            String name;
            switch (results[i]) {
                case PackageManager.PERMISSION_DENIED:
                    name = "Denied";
                    break;
                case PackageManager.PERMISSION_GRANTED:
                    name = "Granted";
                    break;
                default:
                    name = Integer.toString(results[i]);
            }
            resultNames[i] = name;
        }
        Log.d(TAG, String.format(".onRequestPermissionsResult(%d, %s, %s)",
                code, Arrays.toString(permissions),
                Arrays.toString(resultNames)));

        if (code != R.id.AlarmButtonOK) {
            Log.e(TAG, "Unexpected code from request permissions; ignoring!");
            return;
        }

        if (permissions.length != results.length) {
            Log.e(TAG, String.format(Locale.US,
                    "Number of request permissions (%d) Does not"
                            + " match number of results (%d); ignoring!",
                    permissions.length, results.length));
            return;
        }

        for (int i = 0; i < results.length; i++) {
            if (Manifest.permission.POST_NOTIFICATIONS.equals(permissions[i])) {
                if (results[i] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "Notification permission granted");
                    return;
                } else if (results[i] == PackageManager.PERMISSION_DENIED) {
                    Log.i(TAG, "Notification permission denied!");
                    showNotificationRationale();
                }
            } else {
                Log.w(TAG, String.format(Locale.US,
                        "Ignoring unknown permission %s", permissions[i]));
            }
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

    /**
     * Called when the user selects a new due date
     * from a list of this week&rsquo;s dates
     */
    class DueDateListSelectionListener
            implements DialogInterface.OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            Log.d(TAG, "DueDateListSelectionListener.onClick(" + which + ")");
            switch (which) {
                default:
                    todo.setDue(LocalDate.now(prefs.getTimeZone())
                            .plusDays(which));
                    if (repeatSettings != null)
                        repeatSettings.setDueDate(todo.getDue());
                    updateDueDateButton();
                    break;

                case 8:	// No date
                    todo.setDue(null);
                    updateDueDateButton();
                    break;

                case 9:	// Other
                    showDialog(DUEDATE_DIALOG_ID);
                    break;
            }
        }
    }

    /**
     * Called when the user selects a new due date
     * from the calendar date picker
     */
    class DueDateCalendarSelectionListener
            implements CalendarDatePickerDialog.OnDateSetListener {
        @Override
        public void onDateSet(CalendarDatePicker dp, LocalDate day) {
            todo.setDue(day);
            if (repeatSettings != null)
                repeatSettings.setDueDate(todo.getDue());
            updateDueDateButton();
        }
    }

    /**
     * Called when the user selects a new repeat
     * interval from the pre-defined interval list.
     */
    class RepeatListSelectionListener
            implements DialogInterface.OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            Log.d(TAG, String.format(Locale.US,
                    "RepeatListSelectionListener.onClick(%d)", which));

            LocalDate referenceDate = (todo.getDue() == null)
                    ? LocalDate.now(prefs.getTimeZone())
                    : todo.getDue();

            switch (which) {
                case 0:	// None
                    todo.setRepeatInterval(new RepeatNone());
                    updateRepeatButton();
                    break;

                case 1:	// Daily until...
                    todo.setRepeatInterval(new RepeatDaily());
                    repeatButton.setText(
                            getResources().getString(R.string.RepeatDaily));
                    showDialog(ENDDATE_DIALOG_ID);
                    break;

                case 2:	// Weekly
                    todo.setRepeatInterval(new RepeatWeekly(referenceDate));
                    updateRepeatButton();
                    break;

                case 3:	// Semi-monthly
                    todo.setRepeatInterval(
                            new RepeatSemiMonthlyOnDates(referenceDate));
                    updateRepeatButton();
                    break;

                case 4:	// Monthly
                    todo.setRepeatInterval(
                            new RepeatMonthlyOnDate(referenceDate));
                    updateRepeatButton();
                    break;

                case 5:	// Yearly
                    todo.setRepeatInterval(
                            new RepeatYearlyOnDate(referenceDate));
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
            List<String> validationErrors = new LinkedList<>();

            todo.setDescription(toDoDescription.getText().toString());
            if (todo.getDescription().length() > 0) {
                if (privateCheckBox.isChecked()) {
                    todo.setPrivate(StringEncryption.NO_ENCRYPTION);
                    if (encryptor.hasKey()) {
                        try {
                            todo.setEncryptedDescription(encryptor.encrypt(
                                    todo.getDescription()));
                            if (todo.getNote() != null)
                                todo.setEncryptedNote(encryptor.encrypt(
                                        todo.getEncryptedNote()));
                            todo.setPrivate(StringEncryption.BUNDLED_ENCRYPTION);
                        } catch (EncryptionException e) {
                            Toast.makeText(ToDoDetailsActivity.this,
                                    e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                } else {
                    todo.setPrivate(0);
                }
            } else {
                validationErrors.add(getResources().getString(
                        R.string.ErrorDescriptionBlank));
            }

            try {
                todo.setPriority(Integer.parseInt(
                        priorityText.getText().toString()));
                if (todo.getPriority() <= 0)
                    validationErrors.add(getResources().getString(
                            R.string.ErrorPriority));
            } catch (NumberFormatException nfx) {
                validationErrors.add(getResources().getString(
                        R.string.ErrorPriority));
            }

            todo.setCategoryId(categoryList.getSelectedItemId());
            if (todo.getCategoryId() == AdapterView.INVALID_ROW_ID)
                validationErrors.add(getResources().getString(
                        R.string.ErrorCategoryID));

            if (validationErrors.size() > 0) {
                // Show an alert dialog
                AlertDialog.Builder builder =
                        new AlertDialog.Builder(ToDoDetailsActivity.this);
                builder.setIcon(android.R.drawable.ic_dialog_alert);
                builder.setNeutralButton(R.string.ConfirmationButtonOK,
                        DISMISS_LISTENER);
                StringBuilder sb = new StringBuilder();
                for (String error : validationErrors) {
                    if (sb.length() > 0)
                        sb.append("  ");
                    sb.append(error);
                }
                builder.setMessage(sb.toString());
                builder.create().show();
            } else {

                // Final item cleanup
                if (todo.getDue() == null) {
                    todo.setHideDaysEarlier(null);
                    todo.setAlarm(null);
                    todo.setRepeatInterval(null);
                }
                else if (todo.getAlarm() != null) {
                    // If the alarm time has changed,
                    // clear any prior notification time.
                    if ((originalAlarm == null) ||
                            !todo.getAlarm().getTime().equals(
                                    originalAlarm.getTime()) ||
                            (todo.getAlarm().getAlarmDaysEarlier() !=
                                    originalAlarm.getAlarmDaysEarlier()))
                        todo.getAlarm().setNotificationTime(null);
                }

                // Disable the description field and Done and Delete buttons
                // until the save is finished
                toDoDescription.setEnabled(false);
                okButton.setEnabled(false);
                deleteButton.setEnabled(false);

                // Write and commit the changes
                executor.submit(new SaveToDoItemRunner(todo, isNewToDo));
            }
        }
    }

    /**
     * Saves changes to the To Do item on a non-UI thread.
     * Closes the activity when finished.  If an error occurs,
     * shows an alert (on the UI thread) instead.
     */
    private class SaveToDoItemRunner implements Runnable {
        private final boolean isNew;
        private final ToDoItem toSave;
        SaveToDoItemRunner(@NonNull ToDoItem todo, boolean isNew) {
            toSave = todo;
            this.isNew = isNew;
        }
        @Override
        public void run() {
            try {
                todo.setModTimeNow();
                if (isNew) {
                    todo.setCreateTime(todo.getModTime());
                    repository.insertItem(toSave);
                } else {
                    repository.updateItem(toSave);
                }
                runOnUiThread(SAVE_FINISHED_RUNNER);
            } catch (SQLException sx) {
                runOnUiThread(new SaveExceptionAlertRunner(sx));
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
                    DISMISS_LISTENER);
            builder.setPositiveButton(R.string.ConfirmationButtonOK,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            executor.submit(new DeleteToDoItemRunner(todoId));
                        }
                    });
            builder.create().show();
        }
    }

    /**
     * Delete the To Do item on a non-UI thread.
     * Closes the activity when finished.  If an error occurs,
     * shows an alert (on the UI thread) instead.
     */
    private class DeleteToDoItemRunner implements Runnable {
        private final long itemId;
        DeleteToDoItemRunner(long itemId) {
            this.itemId = itemId;
        }
        @Override
        public void run() {
            try {
                repository.deleteItem(itemId);
                runOnUiThread(SAVE_FINISHED_RUNNER);
            } catch (SQLException sx) {
                runOnUiThread(new SaveExceptionAlertRunner(sx));
            }
        }
    }

    /**
     * A runner to clean up the details activity and finish on the UI thread.
     */
    private final Runnable SAVE_FINISHED_RUNNER = new Runnable() {
        @Override
        public void run() {
            todoId = null;
            todo.setDescription(null);
            todo.setNote(null);
            todo = null;
            ToDoDetailsActivity.this.finish();
        }
    };

    /** A runner to display an exception message on the UI thread. */
    class SaveExceptionAlertRunner implements Runnable {
        private final Exception e;
        SaveExceptionAlertRunner(Exception exception) {
            e = exception;
        }
        @Override
        public void run() {
            new AlertDialog.Builder(ToDoDetailsActivity.this)
                    .setMessage(e.getMessage())
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setNeutralButton(R.string.ConfirmationButtonCancel,
                            DISMISS_LISTENER).create().show();
            toDoDescription.setEnabled(true);
            okButton.setEnabled(true);
            deleteButton.setEnabled(!isNewToDo);
        }
    }

}
