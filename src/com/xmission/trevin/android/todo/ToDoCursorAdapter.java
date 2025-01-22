/*
 * $Id: ToDoCursorAdapter.java,v 1.3 2014/04/06 15:07:37 trevin Exp trevin $
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
 * $Log: ToDoCursorAdapter.java,v $
 * Revision 1.3  2014/04/06 15:07:37  trevin
 * Update an item’s modification time when it is (un)checked.
 *
 * Revision 1.2  2014/03/22 19:03:52  trevin
 * Added the copyright notice.
 * Commented out OnFocusChangeListener, which is currently unused.
 *
 * Revision 1.1  2011/01/19 00:32:26  trevin
 * Initial revision
 *
 */
package com.xmission.trevin.android.todo;

import static com.xmission.trevin.android.todo.ToDoListActivity.*;

import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.*;

import com.xmission.trevin.android.todo.ToDo.ToDoItem;

import android.app.Activity;
import android.content.*;
import android.database.Cursor;
//import android.database.CursorIndexOutOfBoundsException;
import android.net.Uri;
import android.util.Log;
import android.view.*;
import android.widget.*;

/**
 * An adapter to map columns from a To Do item cursor to respective
 * widgets and views in the list_item layout.
 *
 * @see android.widget.ResourceCursorAdapter
 */
public class ToDoCursorAdapter extends ResourceCursorAdapter {

    public final static String TAG = "ToDoCursorAdapter";

    private final Activity callingActivity;
    private final SharedPreferences prefs;
    private final ContentResolver contentResolver;
    private final Uri listUri;

    /** Encryption in case we're showing private records */
    private final StringEncryption encryptor;

    /**
     * Keep track of which rows are assigned to which views.
     * Binding occurs over and over again for the same rows,
     * so we want to avoid any unnecessary work.
     */
    private Map<View,Long> bindingMap = new HashMap<View,Long>();

    /** The item whose due date is currently selected */
    Uri selectedItemUri = null;

    /**
     * Constructor
     */
    public ToDoCursorAdapter(Context context, int layout, Cursor cursor,
	    ContentResolver cr, Uri uri, Activity activity,
	    StringEncryption encryption) {
	super(context, layout, cursor);
	callingActivity = activity;
	prefs = context.getSharedPreferences(TODO_PREFERENCES, MODE_PRIVATE);
 	contentResolver = cr;
 	listUri = uri;
 	encryptor = encryption;
    }

    /**
     * @return the URI of the item whose due date was last selected
     */
    public Uri getSelectedItemUri() { return selectedItemUri; }

    /** Clear the URI of the item whose due date was selected after use */
    public void clearSelectedItemUri() { selectedItemUri = null; }

    /**
     * Called when data from a cursor row needs to be displayed in the
     * given view.  This may include temporary display for the purpose of
     * measurement, in which case the same view may be reused multiple
     * times, so we cannot depend on a permanent connection to the data!
     */
    @Override
    public void bindView(View view, Context context, Cursor cursor) {
	int itemID = cursor.getInt(cursor.getColumnIndex(ToDoItem._ID));

	// If this view is already bound to the given row, skip (re-)binding.
	if (bindingMap.containsKey(view) &&
		bindingMap.get(view) == itemID)
	    return;

	Log.d(TAG, ".bindView(" + view + ",context,cursor [item #"
		+ itemID + "])");

	// Remove any existing callbacks to avoid spurious database changes
	removeListeners(view);

	// These are the widgets that need customizing per item
	CheckBox checkBox = (CheckBox) view.findViewById(R.id.ToDoItemChecked);
	TextView priorityText = (TextView) view.findViewById(R.id.ToDoTextPriority);
	// To do: change this back to EditText when you can get it working
	TextView editDescription = (TextView)
		view.findViewById(R.id.ToDoEditDescription);
	ImageView noteImage = (ImageView)
		view.findViewById(R.id.ToDoNoteImage);
	ImageView alarmImage = (ImageView)
		view.findViewById(R.id.ToDoAlarmImage);
	ImageView repeatImage = (ImageView)
		view.findViewById(R.id.ToDoRepeatImage);
	TextView dueDateText = (TextView)
		view.findViewById(R.id.ToDoTextDueDate);
	TextView overdueText = (TextView)
		view.findViewById(R.id.ToDoTextOverdue);
	TextView categText = (TextView) view.findViewById(R.id.ToDoTextCateg);

	/*
	 * Get the item data and set the widgets accordingly.
	 * Note that bindView may be called repeatedly on the same item
	 * which has already been initialized.  In order to avoid
	 * unnecessary callbacks, only set widgets when the value
	 * in the database differs from what is already shown.
	 */
	checkBox.setChecked(cursor.getInt(
		cursor.getColumnIndex(ToDoItem.CHECKED)) != 0);
	priorityText.setText(Integer.toString(cursor.getInt(
		cursor.getColumnIndex(ToDoItem.PRIORITY))));
	priorityText.setVisibility(prefs.getBoolean(TPREF_SHOW_PRIORITY, false)
		? View.VISIBLE : View.GONE);
	String description = context.getString(R.string.PasswordProtected);
	int privacy = cursor.getInt(cursor.getColumnIndex(ToDoItem.PRIVATE));
	if (privacy > 1) {
	    if (encryptor.hasKey()) {
		try {
		    description = encryptor.decrypt(cursor.getBlob(
			    cursor.getColumnIndex(ToDoItem.DESCRIPTION)));
		} catch (GeneralSecurityException gsx) {
		    Log.e(TAG, "Unable to decrypt the description for item "
			    + itemID, gsx);
		}
	    }
	} else {
	    description = cursor.getString(
		    cursor.getColumnIndex(ToDoItem.DESCRIPTION));
	}
	editDescription.setText(description);
	/* // If the description is empty, this is a new item.
	// Give it focus so the user can enter some text.
	// Empty items are removed when the focus is lost.
	if (description.length() == 0)
	    editDescription.requestFocus(); */
	noteImage.setVisibility(cursor.isNull(cursor.getColumnIndex(
		ToDoItem.NOTE)) ? View.GONE : View.VISIBLE);
	alarmImage.setVisibility(cursor.isNull(cursor.getColumnIndex(
		ToDoItem.ALARM_DAYS_EARLIER)) ? View.GONE : View.VISIBLE);
	repeatImage.setVisibility(cursor.getInt(cursor.getColumnIndex(
		ToDoItem.REPEAT_INTERVAL)) == ToDoItem.REPEAT_NONE
		? View.GONE : View.VISIBLE);
	if (cursor.isNull(cursor.getColumnIndex(ToDoItem.DUE_TIME))) {
	    dueDateText.setText("\u2015");
	    overdueText.setText("");
	} else {
	    Date due = new Date(cursor.getLong(cursor.getColumnIndex(
		    ToDoItem.DUE_TIME)));
	    SimpleDateFormat df = new SimpleDateFormat(
		    view.getResources().getString(R.string.ListDueDateFormat));
	    dueDateText.setText(df.format(due));
	    overdueText.setText(due.before(new Date()) ? "!" : "");
	}
	dueDateText.setVisibility(prefs.getBoolean(TPREF_SHOW_DUE_DATE, false)
		? View.VISIBLE : View.GONE);
	categText.setText(cursor.getString(cursor.getColumnIndex(
		ToDoItem.CATEGORY_NAME)));
	categText.setVisibility(prefs.getBoolean(TPREF_SHOW_CATEGORY, false)
		? View.VISIBLE : View.GONE);

	RepeatSettings repeat = new RepeatSettings(cursor);

	// Set callbacks for the widgets
	Uri itemUri = ContentUris.withAppendedId(listUri, itemID);
	installListeners(view, itemUri, repeat);
    }

    /**
     * Remove all listeners from a view.
     * This is necessary before binding to avoid callbacks
     * while we're binding the view.
     */
    void removeListeners(View view) {
	CheckBox checkBox = (CheckBox) view.findViewById(R.id.ToDoItemChecked);
	checkBox.setOnCheckedChangeListener(null);
	TextView editDescription = (TextView)
		view.findViewById(R.id.ToDoEditDescription);
	editDescription.setOnFocusChangeListener(null);
	ImageView noteImage = (ImageView)
		view.findViewById(R.id.ToDoNoteImage);
	noteImage.setOnClickListener(null);
	ImageView alarmImage = (ImageView)
		view.findViewById(R.id.ToDoAlarmImage);
	alarmImage.setOnClickListener(null);
	ImageView repeatImage = (ImageView)
		view.findViewById(R.id.ToDoRepeatImage);
	repeatImage.setOnClickListener(null);
	TextView dueDateText = (TextView)
		view.findViewById(R.id.ToDoTextDueDate);
	dueDateText.setOnClickListener(null);
    }

    /**
     * Install listeners onto a view.
     * This must be done after binding.
     */
    void installListeners(View view, Uri itemUri, RepeatSettings repeat) {
	CheckBox checkBox = (CheckBox) view.findViewById(R.id.ToDoItemChecked);
	checkBox.setOnCheckedChangeListener(
		new OnCheckedChangeListener(itemUri, repeat));

	// Set a long-click listener to bring up the details dialog
	OnDetailsClickListener detailsClickListener =
	    new OnDetailsClickListener(itemUri);
	view.setOnLongClickListener(detailsClickListener);
	TextView editDescription = (TextView)
		view.findViewById(R.id.ToDoEditDescription);
	editDescription.setOnLongClickListener(detailsClickListener);

	// Set a regular click listener to bring up the note dialog
	ImageView noteImage = (ImageView)
		view.findViewById(R.id.ToDoNoteImage);
	noteImage.setOnClickListener(new OnNoteClickListener(itemUri));

	// Set click listeners for the alarm and repeat fields
	ImageView alarmImage = (ImageView)
		view.findViewById(R.id.ToDoAlarmImage);
	alarmImage.setOnClickListener(detailsClickListener);
	ImageView repeatImage = (ImageView)
		view.findViewById(R.id.ToDoRepeatImage);
	repeatImage.setOnClickListener(detailsClickListener);

	// Set a click listener for changing the due date
	TextView dueDateText = (TextView)
		view.findViewById(R.id.ToDoTextDueDate);
	dueDateText.setOnClickListener(new OnDueDateClickListener(itemUri));

	// To do: set a click listener for the category field
    }

    /** Listener for events on the "item completed" checkbox */
    class OnCheckedChangeListener
    implements CompoundButton.OnCheckedChangeListener {
	private final Uri itemUri;
	private final RepeatSettings repeat;

	/** Create a new change listener for a specific To-Do item's checkbox */
	public OnCheckedChangeListener(Uri itemUri, RepeatSettings repeat) {
	    this.itemUri = itemUri;
	    this.repeat = repeat;
	}

	/** Called when the user checks off (or back on) a to-do item */
	@Override
	public void onCheckedChanged(CompoundButton checkBox, boolean isChecked) {
	    Log.d(TAG, ".onCheckedChanged(" + itemUri + "," + isChecked + ")");
	    ContentValues values = new ContentValues();
	    values.put(ToDoItem.CHECKED, isChecked ? 1 : 0);
	    values.put(ToDoItem.MOD_TIME, System.currentTimeMillis());
	    if (isChecked) {
		Date completed = new Date();
		values.put(ToDoItem.COMPLETED_TIME, completed.getTime());
		/*
		 * If the item has a repeat interval,
		 * see if we need to change the due date
		 * and reset the completed checkbox.
		 */
		if (repeat.getIntervalType() != RepeatSettings.IntervalType.NONE) {
		    Date nextDueDate = repeat.computeNextDueDate(completed);
		    if (nextDueDate != null) {
			values.put(ToDoItem.DUE_TIME, nextDueDate.getTime());
			values.put(ToDoItem.CHECKED, 0);
		    }
		}
	    }
	    contentResolver.update(itemUri, values, null, null);
	}
    }

    /* Listener for events on the description text field * /
    class OnFocusChangeListener implements View.OnFocusChangeListener {
	private final Uri itemUri;

	/** Create a new text watcher for a specific To-Do item's description * /
	public OnFocusChangeListener(Uri itemUri) {
	    this.itemUri = itemUri;
	}

	/** Called after the user leaves the to-do item text * /
	@Override
	public void onFocusChange(View v, boolean hasFocus) {
	    if (true)
		// We don't care about gaining focus, just losing it.
		// Unfortunately, we can't rely on focus events. :-(
		return;

	    EditText editText = (EditText) v;
	    String newText = editText.getText().toString();
	    Log.d(TAG, ".onFocusChange: new text = \"" + newText + "\"");

	    // If the user has not entered any text or deleted all of the text,
	    // drop the item from the database.
	    if (newText.length() == 0) {
		contentResolver.delete(itemUri, null, null);
		return;
	    }

	    try {
		// Check the current text and
		// skip writing the database if it is the same
		String[] projection = { ToDoItem.DESCRIPTION };
		Cursor c = contentResolver.query(itemUri, projection, null, null, null);
		c.moveToFirst();
		String oldText = c.getString(c.getColumnIndex(ToDoItem.DESCRIPTION));
		c.close();
		if (newText.equals(oldText))
		    return;
	    } catch (CursorIndexOutOfBoundsException notFound) {
		// This entry must have been deleted while we weren't looking
		return;
	    }

	    // Update the text and modification time in the database
	    ContentValues values = new ContentValues();
	    values.put(ToDoItem.DESCRIPTION, newText);
	    values.put(ToDoItem.MOD_TIME, System.currentTimeMillis());
	    contentResolver.update(itemUri, values, null, null);
	}
    } */

    /** Listener for click events on the note icon */
    class OnNoteClickListener implements View.OnClickListener {
	private final Uri itemUri;

	/** Create a new click listener for a specific To-Do item's note */
	public OnNoteClickListener(Uri itemUri) {
	    this.itemUri = itemUri;
	}

	@Override
	public void onClick(View v) {
	    Log.d(TAG, "ToDoNoteImage.onClick");
	    Intent intent = new Intent(v.getContext(),
		    ToDoNoteActivity.class);
	    intent.setData(itemUri);
	    v.getContext().startActivity(intent);
	}
    }

    /** Listener for click events on the due date */
    class OnDueDateClickListener implements View.OnClickListener {
	private final Uri itemUri;

	/** Create a new click listener for a specific To-Do item's due date */
	public OnDueDateClickListener(Uri itemUri) {
	    this.itemUri = itemUri;
	}

	@Override
	public void onClick(View v) {
	    Log.d(TAG, "ToDoTextDueDate.onClick(" + itemUri + ")");
	    selectedItemUri = itemUri;
	    callingActivity.showDialog(ToDoDetailsActivity.DUEDATE_LIST_ID);
	}
    }

    /** Listener for (long-)click events on the To Do item */
    class OnDetailsClickListener
    implements View.OnLongClickListener, View.OnClickListener {
	private final Uri itemUri;

	/** Create a new detail click listener for a specific To-Do item */
	public OnDetailsClickListener(Uri itemUri) {
	    this.itemUri = itemUri;
	}

	@Override
	public void onClick(View v) {
	    Log.d(TAG, ".onClick(EditText)");
	    Intent intent = new Intent(v.getContext(),
		    ToDoDetailsActivity.class);
	    intent.setData(itemUri);
	    v.getContext().startActivity(intent);
	}

	@Override
	public boolean onLongClick(View v) {
	    Log.d(TAG, ".onLongClick(EditText)");
	    Intent intent = new Intent(v.getContext(),
		    ToDoDetailsActivity.class);
	    intent.setData(itemUri);
	    v.getContext().startActivity(intent);
	    return true;
	}
    }
}
