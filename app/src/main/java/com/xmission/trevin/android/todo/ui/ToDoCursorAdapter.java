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

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.xmission.trevin.android.todo.R;
import com.xmission.trevin.android.todo.data.ToDoItem;
import com.xmission.trevin.android.todo.data.ToDoPreferences;
import com.xmission.trevin.android.todo.data.repeat.RepeatNone;
import com.xmission.trevin.android.todo.provider.ToDoCursor;
import com.xmission.trevin.android.todo.provider.ToDoRepository;
import com.xmission.trevin.android.todo.util.EncryptionException;
import com.xmission.trevin.android.todo.util.StringEncryption;

import android.app.Activity;
import android.app.LoaderManager;
import android.app.NotificationManager;
import android.content.*;
import android.util.Log;
import android.view.*;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * An adapter to map columns from a To Do item cursor to respective
 * widgets and views in the list_item layout.
 *
 * @see android.widget.ResourceCursorAdapter
 *
 * @author Trevin Beattie
 */
public class ToDoCursorAdapter extends BaseAdapter {

    public final static String TAG = "ToDoCursorAdapter";

    private final Activity activity;

    /** The cursor providing our To Do data */
    private ToDoCursor cursor;

    private final LayoutInflater inflater;

    private final ToDoPreferences prefs;

    private final ToDoRepository repo;

    /** Encryption in case we're showing private records */
    private final StringEncryption encryptor;

    /** The notification manager for clearing notifications of completed items */
    private final NotificationManager notificationManager;

    /** An executor for running repository operations on a non-UI thread */
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    /** The item whose due date is currently selected */
    long selectedItemId = -1;

    /**
     * Constructor
     *
     * @param activity the Android Activity in which this adapter is running
     * @param cursor the cursor that provides the To Do items for this
     * adapter.  May be {@code null} if the data will be loaded by a
     * {@link LoaderManager}, in which case it must be set by calling
     * {@link #swapCursor}.
     * @param repository the To Do repository, needed for updating an
     * item&rsquo;s checked state and potentially changing the due date
     * when the user taps the check box.
     * @param encryption a {@link StringEncryption} object used to decrypt
     * encrypted notes.  This may be uninitialized (i.e. no password set)
     * in which case views for encrypted items will just display
     * &ldquo;[Locked]&rdquo;.
     * @param notificationManager the notification manager for clearing
     * notifications of completed items
     */
    public ToDoCursorAdapter(Activity activity,
                             @Nullable ToDoCursor cursor,
                             @NonNull ToDoRepository repository,
                             StringEncryption encryption,
                             NotificationManager notificationManager) {
        this.activity = activity;
        this.cursor = cursor;
        this.repo = repository;
        prefs = ToDoPreferences.getInstance(activity);
        inflater = (LayoutInflater) activity.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        encryptor = encryption;
        this.notificationManager = notificationManager;
    }

    /**
     * Close any existing ToDoCursor used by this adapter and replace ti
     * with the given ToDoCursor.
     *
     * @param newCursor the new ToDoCursor to use.  (May be {@code null}.)
     */
    public void swapCursor(@Nullable ToDoCursor newCursor) {
        Log.d(TAG, String.format(Locale.US, ".swapCursor(%s)", newCursor));
        if (cursor != null)
            cursor.close();
        cursor = newCursor;
        notifyDataSetChanged();
    }

    /**
     * Get the number of items in the data set managed by this adapter
     *
     * @return the number of items in the data set
     */
    @Override
    public int getCount() {
        if (cursor == null) {
            Log.w(TAG, ".getCount: The cursor has not been set!");
            return 0;
        }
        Log.d(TAG, ".getCount()");
        return cursor.getCount();
    }

    /**
     * @return the ID of the item whose due date was last selected
     */
    public long getSelectedItemId() { return selectedItemId; }

    /**
     * Get the To Do item associated with the specified position
     * in the data set.
     *
     * @param position Position of the item whose data we want
     *                 within the adapter&rsquo;s data set
     *
     * @return The item at the specified position
     */
    @Override
    public ToDoItem getItem(int position) {
        if (cursor == null) {
            Log.w(TAG, ".getItem: The cursor has not been set!");
            return null;
        }
        Log.d(TAG, String.format(Locale.US, ".getItem(%d)", position));
        cursor.moveToPosition(position);
        return cursor.getItem();
    }

    /**
     * Get the row ID associated with the specified position in the list.
     *
     * @param position The position of the item within the adapter&rsquo;s
     *                 data set whose row ID we want.
     *
     * @return the ID of the item at the specified position
     */
    @Override
    public long getItemId(int position) {
        if (cursor == null) {
            Log.w(TAG, ".getItemId: The cursor has not been set!");
            return -1;
        }
        Log.d(TAG, String.format(Locale.US, ".getItemId(%d)", position));
        cursor.moveToPosition(position);
        return cursor.getItem().getId();
    }

    /**
     * Get the position of a To Do item from its ID.
     * If there is no such item, returns the first position.
     *
     * @param itemId the ID of the item to find
     *
     * @return the position of the item
     */
    public int getItemPosition(long itemId) {
        if (cursor == null) {
            Log.w(TAG, ".getItemPosition: The cursor has not been set!");
            return 0;
        }
        for (int i = 0; i < cursor.getCount(); i++) {
            if (cursor.moveToPosition(i)) {
                if (cursor.getItem().getId() == itemId)
                    return i;
            }
        }
        return 0;
    }

    /**
     * Get a view that displays the To Do data at the specified position
     * in the data set.  The parent view will apply the default layout
     * parameters.
     *
     * @param position The position of the item within the adapter&rsquo;s
     *                 data set.
     * @param convertView The old view to use, if possible.  If it is not
     *                    possible to convert this view to display the
     *                    correct data, this method creates a new view.
     * @param parent The parent that this view will eventually be attached to.
     *
     * @return a {@link View} corresponding to the item
     * at the specified position.
     */
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Log.d(TAG, String.format(Locale.US, ".getView(%d, %s, %s)",
                position, (convertView == null) ? null
                        : convertView.getClass().getSimpleName(),
                parent.getClass().getSimpleName()));

        if (cursor == null) {
            Log.e(TAG, ".getView: The cursor has not been set!");
            return null;
        }

        ToDoItem todo = getItem(position);
        View itemView = convertView;
        if (itemView == null) {
            Log.d(TAG, "Creating a new list item view");
            itemView = inflater.inflate(R.layout.todo_list_item, parent, false);
        }

        // Remove any existing callbacks to avoid spurious database changes
        removeListeners(itemView);

        // These are the widgets that need customizing per item:
        CheckBox checkBox = itemView.findViewById(R.id.ToDoItemChecked);
        TextView priorityText =itemView.findViewById(R.id.ToDoTextPriority);
        TextView editDescription = itemView.findViewById(R.id.ToDoEditDescription);
        ImageView noteImage = itemView.findViewById(R.id.ToDoNoteImage);
        ImageView alarmImage = itemView.findViewById(R.id.ToDoAlarmImage);
        ImageView repeatImage = itemView.findViewById(R.id.ToDoRepeatImage);
        TextView dueDateText = itemView.findViewById(R.id.ToDoTextDueDate);
        TextView overdueText = itemView.findViewById(R.id.ToDoTextOverdue);
        TextView categoryText = itemView.findViewById(R.id.ToDoTextCateg);

        /*
         * Get the item data and set the widgets accordingly.
         * Note that bindView may be called repeatedly on the same item
         * which has already been initialized.
         */
        checkBox.setChecked(todo.isChecked());
        NumberFormat numFormat = NumberFormat
                .getIntegerInstance(Locale.getDefault());
        priorityText.setText(numFormat.format(todo.getPriority()));
        priorityText.setVisibility(prefs.showPriority()
                ? View.VISIBLE : View.GONE);
        String description = activity.getString(R.string.PasswordProtected);
        if (todo.isEncrypted()) {
            if (encryptor.hasKey()) try {
                description = encryptor.decrypt(todo.getEncryptedDescription());
            } catch (EncryptionException e) {
                Log.e(TAG, String.format(Locale.US,
                        "Unable to decrypt the description for item %d",
                        todo.getId()), e);
            }
        } else {
            description = todo.getDescription();
        }
        editDescription.setText(description);
        noteImage.setVisibility(((todo.isEncrypted() ? todo.getEncryptedNote()
                : todo.getNote()) == null) ? View.GONE : View.VISIBLE);
        alarmImage.setVisibility((todo.getAlarm() == null)
                ? View.GONE : View.VISIBLE);
        repeatImage.setVisibility(((todo.getRepeatInterval() == null) ||
                (todo.getRepeatInterval() instanceof RepeatNone))
                ? View.GONE : View.VISIBLE);
        if (todo.getDue() == null) {
            dueDateText.setText("\u2015");      // em dash
            overdueText.setText("");
        } else {
            DateTimeFormatter df = DateTimeFormatter.ofPattern(
                    activity.getString(R.string.ListDueDateFormat));
            dueDateText.setText(df.format(todo.getDue()));
            overdueText.setText(todo.getDue().isBefore(
                    LocalDate.now(prefs.getTimeZone())) ? "!" : "");
        }
        dueDateText.setVisibility(prefs.showDueDate()
                ? View.VISIBLE : View.GONE);
        categoryText.setText(todo.getCategoryName());
        categoryText.setVisibility(prefs.showCategory()
                ? View.VISIBLE : View.GONE);

        // Set callbacks for the widgets
        installListeners(itemView, todo.getId());

        return itemView;
    }

    /**
     * Install listeners onto a view.
     * This must be done after binding.
     */
    void installListeners(View view, long itemId) {
        CheckBox checkBox = view.findViewById(R.id.ToDoItemChecked);
        checkBox.setOnCheckedChangeListener(
                new OnCheckedChangeListener(itemId));

        // Set a long-click listener to bring up the details dialog
        OnDetailsClickListener detailsClickListener =
                new OnDetailsClickListener(itemId);
        view.setOnLongClickListener(detailsClickListener);
        TextView editDescription = view.findViewById(R.id.ToDoEditDescription);
        editDescription.setOnLongClickListener(detailsClickListener);

        // Set a regular click listener to bring up the note dialog
        ImageView noteImage = view.findViewById(R.id.ToDoNoteImage);
        noteImage.setOnClickListener(new OnNoteClickListener(itemId));

        // Set click listeners for the alarm and repeat fields
        ImageView alarmImage = view.findViewById(R.id.ToDoAlarmImage);
        alarmImage.setOnClickListener(detailsClickListener);
        ImageView repeatImage = view.findViewById(R.id.ToDoRepeatImage);
        repeatImage.setOnClickListener(detailsClickListener);

        // Set a click listener for changing the due date
        TextView dueDateText = view.findViewById(R.id.ToDoTextDueDate);
        dueDateText.setOnClickListener(new OnDueDateClickListener(itemId));

        // To do: set a click listener for the category field
    }

    /**
     * Remove all listeners from a view.
     * This is necessary before binding to avoid callbacks
     * while we're binding the view.
     */
    void removeListeners(View view) {
        CheckBox checkBox = view.findViewById(R.id.ToDoItemChecked);
        checkBox.setOnCheckedChangeListener(null);
        TextView editDescription = view.findViewById(R.id.ToDoEditDescription);
        editDescription.setOnFocusChangeListener(null);
        ImageView noteImage = view.findViewById(R.id.ToDoNoteImage);
        noteImage.setOnClickListener(null);
        ImageView alarmImage = view.findViewById(R.id.ToDoAlarmImage);
        alarmImage.setOnClickListener(null);
        ImageView repeatImage = view.findViewById(R.id.ToDoRepeatImage);
        repeatImage.setOnClickListener(null);
        TextView dueDateText = view.findViewById(R.id.ToDoTextDueDate);
        dueDateText.setOnClickListener(null);
    }

    /** Listener for events on the "item completed" checkbox */
    class OnCheckedChangeListener
    implements CompoundButton.OnCheckedChangeListener {
        private final long itemId;

        /** Create a new change listener for a specific To-Do item's checkbox */
        public OnCheckedChangeListener(long itemId) {
            this.itemId = itemId;
        }

        /** Called when the user checks off (or back on) a to-do item */
        @Override
        public void onCheckedChanged(CompoundButton checkBox, boolean isChecked) {
            Log.d(TAG, String.format(Locale.US,
                    ".onCheckedChanged(ToDoItem(id=%d),isChecked=%s",
                    itemId, isChecked));
            executor.submit(new ToggleCheckedRunner(itemId, isChecked));
        }
    }

    /**
     * Runner which toggles the {@code checked} state of a To Do item,
     * updating it in the repository on a non-UI thread.
     */
    class ToggleCheckedRunner implements Runnable {
        private final long itemId;
        private final boolean isChecked;
        public ToggleCheckedRunner(long itemId, boolean isChecked) {
            this.itemId = itemId;
            this.isChecked = isChecked;
        }
        @Override
        public void run() {
            Log.d(TAG, "ToggleCheckedRunner.run()");
            ToDoItem todo = repo.getItemById(itemId);
            todo.setChecked(isChecked);
            if (isChecked) {
                todo.setCompletedNow();
                /*
                 * If the item has a repeat interval,
                 * see if we need to change the due date
                 * and reset the completed checkbox.
                 */
                if ((todo.getRepeatInterval() != null) &&
                        !(todo.getRepeatInterval() instanceof RepeatNone)) {
                    LocalDate oldDue = todo.getDue();
                    LocalDate today = LocalDate.now(prefs.getTimeZone());
                    // Sanity check in case the due date was cleared
                    if (oldDue == null)
                        oldDue = today;
                    LocalDate nextDueDate = todo.getRepeatInterval()
                            .computeNextDueDate(oldDue, today);
                    if (nextDueDate != null) {
                        Log.d(TAG, String.format(Locale.US,
                                "Changing the next due date for item %d from %s to %s",
                                itemId, oldDue.format(DateTimeFormatter.ISO_LOCAL_DATE),
                                nextDueDate.format(DateTimeFormatter.ISO_LOCAL_DATE)));
                        todo.setDue(nextDueDate);
                        todo.setChecked(false);
                    } else {
                        Log.d(TAG, String.format(Locale.US,
                                "No more repeats for item %d", itemId));
                    }
                }
                if (todo.getAlarm() != null) {
                    // Clear any related notification
                    Log.d(TAG, String.format(Locale.US,
                            "Clearing any notification for item %d",
                            todo.getId()));
                    notificationManager.cancel(todo.getId().intValue());
                }
            }
            todo.setModTimeNow();
            repo.updateItem(todo);
        }
    }

    /** Listener for click events on the note icon */
    static class OnNoteClickListener implements View.OnClickListener {
        private final long itemId;

        /** Create a new click listener for a specific To-Do item's note */
        public OnNoteClickListener(long itemId) {
            this.itemId = itemId;
        }

        @Override
        public void onClick(View v) {
            Log.d(TAG, "ToDoNoteImage.onClick");
            Intent intent = new Intent(v.getContext(),
                    ToDoNoteActivity.class);
            intent.putExtra(ToDoListActivity.EXTRA_ITEM_ID, itemId);
            v.getContext().startActivity(intent);
        }
    }

    /** Listener for click events on the due date */
    class OnDueDateClickListener implements View.OnClickListener {
        private final long itemId;

        /** Create a new click listener for a specific To-Do item's due date */
        public OnDueDateClickListener(long itemId) {
            this.itemId = itemId;
        }

        @Override
        public void onClick(View v) {
            Log.d(TAG, String.format(Locale.US,
                    "ToDoTextDueDate.onClick(%d)", itemId));
            selectedItemId = itemId;
            activity.showDialog(ToDoListActivity.DUEDATE_LIST_ID);
        }
    }

    /** Listener for (long-)click events on the To Do item */
    static class OnDetailsClickListener
    implements View.OnLongClickListener, View.OnClickListener {
        private final long itemId;

        /** Create a new detail click listener for a specific To-Do item */
        public OnDetailsClickListener(long itemId) {
            this.itemId = itemId;
        }

        @Override
        public void onClick(View v) {
            Log.d(TAG, ".onClick(EditText)");
            Intent intent = new Intent(v.getContext(),
                    ToDoDetailsActivity.class);
            intent.putExtra(ToDoListActivity.EXTRA_ITEM_ID, itemId);
            v.getContext().startActivity(intent);
        }

        @Override
        public boolean onLongClick(View v) {
            Log.d(TAG, ".onLongClick(EditText)");
            Intent intent = new Intent(v.getContext(),
                    ToDoDetailsActivity.class);
            intent.putExtra(ToDoListActivity.EXTRA_ITEM_ID, itemId);
            v.getContext().startActivity(intent);
            return true;
        }
    }
}
