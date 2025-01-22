/*
 * $Id: ToDoNoteActivity.java,v 1.3 2014/04/06 15:12:12 trevin Exp trevin $
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
 * $Log: ToDoNoteActivity.java,v $
 * Revision 1.3  2014/04/06 15:12:12  trevin
 * Update an item’s modification time when its note changes or is deleted.
 *
 * Revision 1.2  2014/03/22 19:03:53  trevin
 * Added the copyright notice
 *
 * Revision 1.1  2011/03/23 03:35:01  trevin
 * Initial revision
 *
 */
package com.xmission.trevin.android.todo;

import java.security.GeneralSecurityException;

import com.xmission.trevin.android.todo.ToDo.*;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDoneException;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.*;
import android.widget.*;

/**
 * Displays the note of a To Do item.  Will display the item from the
 * {@link Uri} provided in the intent, which is required.
 */
public class ToDoNoteActivity extends Activity {

    private static final String TAG = "ToDoNoteActivity";

    /**
     * The columns we are interested in from the item table
     */
    private static final String[] ITEM_PROJECTION = new String[] {
            ToDoItem._ID,
            ToDoItem.DESCRIPTION,
            ToDoItem.NOTE,
            ToDoItem.PRIVATE,
    };

    /** The URI by which we were started for the To-Do item */
    private Uri todoUri = ToDoItem.CONTENT_URI;

    /** The note */
    EditText toDoNote = null;

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

        // Perform a managed query. The Activity will handle closing and
        // requerying the cursor when needed.
        Cursor itemCursor = getContentResolver().query(todoUri,
        	ITEM_PROJECTION, null, null, null);
        if (!itemCursor.moveToFirst())
            throw new SQLiteDoneException();

        // Inflate our view so we can find our field
	setContentView(R.layout.note);

	int isPrivate = itemCursor.getInt(
        	itemCursor.getColumnIndex(ToDoItem.PRIVATE));

	encryptor = StringEncryption.holdGlobalEncryption();
        String description = getResources().getString(R.string.PasswordProtected);
        String note = description;
	int i = itemCursor.getColumnIndex(ToDoItem.DESCRIPTION);
        if (isPrivate > 1) {
            if (encryptor.hasKey()) {
        	try {
        	    description = encryptor.decrypt(itemCursor.getBlob(i));
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
            description = itemCursor.getString(i);
        }
        i = itemCursor.getColumnIndex(ToDoItem.NOTE);
        if (itemCursor.isNull(i)) {
            note = "";
        } else {
            if (isPrivate > 1) {
        	if (encryptor.hasKey()) {
        	    try {
        		note = encryptor.decrypt(itemCursor.getBlob(i));
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
        	note = itemCursor.getString(i);
            }
        }

        setTitle(getResources().getString(R.string.app_name)
		+ " \u2015 " + description);

	toDoNote = (EditText) findViewById(R.id.NoteEditText);
	toDoNote.setText(note);

        // Set callbacks
        Button button = (Button) findViewById(R.id.NoteButtonOK);
        button.setOnClickListener(new OKButtonOnClickListener());

        button = (Button) findViewById(R.id.NoteButtonDelete);
        button.setOnClickListener(new DeleteButtonOnClickListener());

	itemCursor.close();
    }

    @Override
    public void onDestroy() {
	StringEncryption.releaseGlobalEncryption(this);
	super.onDestroy();
    }

    class OKButtonOnClickListener implements View.OnClickListener {
	@Override
	public void onClick(View v) {
	    Log.d(TAG, "NoteButtonOK.onClick");
	    ContentValues values = new ContentValues();
	    String note = toDoNote.getText().toString();
	    if (note.length() == 0) {
		values.putNull(ToDoItem.NOTE);
	    } else {
		/*
		 * Figure out whether to encrypt this record.
		 * We read the database again in case the private
		 * flag has changed.
		 */
		Cursor itemCursor = getContentResolver().query(todoUri,
			ITEM_PROJECTION, null, null, null);
		if (!itemCursor.moveToFirst())
		    throw new SQLiteDoneException();
		int isPrivate = itemCursor.getInt(
			itemCursor.getColumnIndex(ToDoItem.PRIVATE));
		itemCursor.close();
		values.put(ToDoItem.NOTE, note);
		if (isPrivate > 1) {
		    if (encryptor.hasKey()) {
			try {
			    values.put(ToDoItem.NOTE, encryptor.encrypt(note));
			} catch (GeneralSecurityException gsx) {
			    values.put(ToDoItem.PRIVATE, 1);
			}
		    } else {
			values.put(ToDoItem.PRIVATE, 1);
		    }
		}
	    }
	    values.put(ToDoItem.MOD_TIME, System.currentTimeMillis());
	    try {
		getContentResolver().update(todoUri, values, null, null);
		ToDoNoteActivity.this.finish();
	    } catch (SQLException sx) {
		new AlertDialog.Builder(ToDoNoteActivity.this)
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

    class DeleteButtonOnClickListener implements View.OnClickListener {
	@Override
	public void onClick(View v) {
	    Log.d(TAG, "NoteButtonDelete.onClick");
	    AlertDialog.Builder builder =
		new AlertDialog.Builder(ToDoNoteActivity.this);
	    builder.setIcon(android.R.drawable.ic_dialog_alert);
	    builder.setMessage(R.string.ConfirmationTextDeleteNote);
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
			ContentValues values = new ContentValues();
			values.putNull(ToDoItem.NOTE);
			values.put(ToDoItem.MOD_TIME, System.currentTimeMillis());
			ToDoNoteActivity.this.getContentResolver().update(
				ToDoNoteActivity.this.todoUri, values, null, null);
			ToDoNoteActivity.this.finish();
		    } catch (SQLException sx) {
			new AlertDialog.Builder(ToDoNoteActivity.this)
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
