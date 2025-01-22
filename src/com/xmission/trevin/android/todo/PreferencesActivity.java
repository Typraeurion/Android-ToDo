/*
 * $Id: PreferencesActivity.java,v 1.5 2014/03/29 18:10:15 trevin Exp trevin $
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
 * $Log: PreferencesActivity.java,v $
 * Revision 1.5  2014/03/29 18:10:15  trevin
 * Hide the password field if a password has not been set.
 * Check whether a password has been set before
 *   atempting to verify the password the user may have entered.
 *
 * Revision 1.4  2014/03/22 19:03:52  trevin
 * Added the copyright notice.
 * Encryptor’s password is now returned as a char[] rather than a String.
 * Set our volume control stream for alarm notifications.
 *
 * Revision 1.3  2011/05/19 05:13:34  trevin
 * Implemented the alarm sound selection list.
 *
 * Revision 1.2  2011/05/11 05:16:49  trevin
 * Replaced the password dialog with a password field.
 * Don't use the password field to set or change the password.
 *
 * Revision 1.1  2011/01/18 21:40:13  trevin
 * Initial revision
 *
 */
package com.xmission.trevin.android.todo;

import static com.xmission.trevin.android.todo.ToDoListActivity.*;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore.Audio.AudioColumns;
import android.provider.MediaStore.Audio.Media;
import android.text.InputType;
import android.util.Log;
import android.view.*;
import android.widget.*;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.CompoundButton.OnCheckedChangeListener;

import com.xmission.trevin.android.todo.ToDo.*;

/**
 * The preferences activity manages the user options dialog.
 */
public class PreferencesActivity extends Activity {

    public static final String LOG_TAG = "PreferencesActivity";

    private SharedPreferences prefs;

    CheckBox privateCheckBox = null;
    EditText passwordEditText = null;

    /** The global encryption object */
    StringEncryption encryptor;

    /** Used to play sample alarm sounds */
    MediaPlayer player = null;

    String[] SOUND_PROJECTION = {
	    AudioColumns._ID,
	    AudioColumns.IS_ALARM,
	    AudioColumns.IS_NOTIFICATION,
	    AudioColumns.IS_RINGTONE,
	    AudioColumns.TITLE,
    };

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
	super.onCreate(savedInstanceState);

	Log.d(LOG_TAG, ".onCreate");

	setContentView(R.layout.preferences);

	prefs = getSharedPreferences(TODO_PREFERENCES, MODE_PRIVATE);

	Spinner spinner = (Spinner) findViewById(R.id.PrefsSpinnerSortBy);
	setSpinnerByID(spinner, prefs.getInt(TPREF_SORT_ORDER, 0));
	spinner.setOnItemSelectedListener(new OnItemSelectedListener() {
	    @Override
	    public void onNothingSelected(AdapterView<?> parent) {
		// Do nothing
	    }
	    @Override
	    public void onItemSelected(AdapterView<?> parent, View child,
		    int position, long id) {
		Log.d(LOG_TAG, "spinnerSortBy.onItemSelected("
			+ position + "," + id + ")");
		if (position >= ToDoItem.USER_SORT_ORDERS.length)
		    Log.e(LOG_TAG, "Unknown sort order selected");
		else
		    prefs.edit().putInt(TPREF_SORT_ORDER, position).commit();
	    }
	});

	CheckBox checkBox = (CheckBox) findViewById(R.id.PrefsCheckBoxShowChecked);
	checkBox.setChecked(prefs.getBoolean(TPREF_SHOW_CHECKED, false));
	checkBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
	    @Override
	    public void onCheckedChanged(CompoundButton button, boolean isChecked) {
		Log.d(LOG_TAG, "prefsCheckBoxShowCompleted.onCheckedChanged("
			+ isChecked + ")");
		prefs.edit().putBoolean(TPREF_SHOW_CHECKED, isChecked).commit();
	    }
	});

	checkBox = (CheckBox) findViewById(R.id.PrefsCheckBoxShowDueDate);
	checkBox.setChecked(prefs.getBoolean(TPREF_SHOW_DUE_DATE, false));
	checkBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
	    @Override
	    public void onCheckedChanged(CompoundButton button, boolean isChecked) {
		Log.d(LOG_TAG, "prefsCheckBoxShowDueDate.onCheckedChanged("
			+ isChecked + ")");
		prefs.edit().putBoolean(TPREF_SHOW_DUE_DATE, isChecked).commit();
	    }
	});

	checkBox = (CheckBox) findViewById(R.id.PrefsCheckBoxShowPriority);
	checkBox.setChecked(prefs.getBoolean(TPREF_SHOW_PRIORITY, false));
	checkBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
	    @Override
	    public void onCheckedChanged(CompoundButton button, boolean isChecked) {
		Log.d(LOG_TAG, "prefsCheckBoxShowPriority.onCheckedChanged("
			+ isChecked + ")");
		prefs.edit().putBoolean(TPREF_SHOW_PRIORITY, isChecked).commit();
	    }
	});

	checkBox = (CheckBox) findViewById(R.id.PrefsCheckBoxShowCategory);
	checkBox.setChecked(prefs.getBoolean(TPREF_SHOW_CATEGORY, false));
	checkBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
	    @Override
	    public void onCheckedChanged(CompoundButton button, boolean isChecked) {
		Log.d(LOG_TAG, "prefsCheckBoxShowCategory.onCheckedChanged("
			+ isChecked + ")");
		prefs.edit().putBoolean(TPREF_SHOW_CATEGORY, isChecked).commit();
	    }
	});

	encryptor = StringEncryption.holdGlobalEncryption();
	privateCheckBox = (CheckBox) findViewById(R.id.PrefsCheckBoxShowPrivate);
	privateCheckBox.setChecked(prefs.getBoolean(TPREF_SHOW_PRIVATE, false));
	final TableRow passwordRow =
	    (TableRow) findViewById(R.id.TableRowPassword);
	passwordRow.setVisibility((encryptor.hasPassword(getContentResolver())
		&& privateCheckBox.isChecked()) ? View.VISIBLE : View.GONE);
	passwordEditText =
	    (EditText) findViewById(R.id.PrefsEditTextPassword);
	if (encryptor.hasKey())
	    passwordEditText.setText(encryptor.getPassword(), 0,
		    encryptor.getPassword().length);
	else
	    passwordEditText.setText("");
	privateCheckBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
	    @Override
	    public void onCheckedChanged(CompoundButton button, boolean isChecked) {
		Log.d(LOG_TAG, "prefsCheckBoxShowPrivate.onCheckedChanged("
			+ isChecked + ")");
		passwordRow.setVisibility((isChecked &&
			encryptor.hasPassword(getContentResolver()))
			? View.VISIBLE : View.GONE);
		prefs.edit().putBoolean(TPREF_SHOW_PRIVATE, isChecked).commit();
	    }
	});

	checkBox = (CheckBox) findViewById(R.id.PrefsCheckBoxShowPassword);
	passwordEditText.setInputType(InputType.TYPE_CLASS_TEXT
		+ (checkBox.isChecked()
			? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
			: InputType.TYPE_TEXT_VARIATION_PASSWORD));
	checkBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
	    @Override
	    public void onCheckedChanged(CompoundButton button, boolean isChecked) {
		Log.d(LOG_TAG, "prefsCheckBoxShowPassword.onCheckedChanged("
			+ isChecked + ")");
		passwordEditText.setInputType(InputType.TYPE_CLASS_TEXT
			+ (isChecked
				? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
				: InputType.TYPE_TEXT_VARIATION_PASSWORD));
	    }
	});

	player = new MediaPlayer();
	setVolumeControlStream(AudioManager.STREAM_NOTIFICATION);

	StringBuilder where = new StringBuilder();
	where.append(AudioColumns.IS_NOTIFICATION)
		.append(" OR ").append(AudioColumns.IS_ALARM);
	// where.append(" OR ").append(AudioColumns.IS_RINGTONE);
	Cursor audioCursor = managedQuery(
		Media.INTERNAL_CONTENT_URI, SOUND_PROJECTION,
		where.toString(), null, AudioColumns.TITLE);
	NoSelectionCursorAdapter soundAdapter =
	    new NoSelectionCursorAdapter(this, audioCursor, AudioColumns.TITLE,
		    getString(R.string.PrefTextNoSound));
	spinner = (Spinner) findViewById(R.id.PrefsSpinnerAlarmSound);
	spinner.setAdapter(soundAdapter);
	final long initialSound = prefs.getLong(TPREF_NOTIFICATION_SOUND, -1);
	setSpinnerByID(spinner, initialSound);
	spinner.setOnItemSelectedListener(new OnItemSelectedListener() {
	    long lastSound = initialSound;
	    @Override
	    public void onItemSelected(AdapterView<?> parent, View child,
		    int position, long id) {
		// Play a sample of the sound for the user
		if ((id >= 0) && (id != lastSound)) {
		    try {
			player.reset();
			player.setDataSource(PreferencesActivity.this,
				Uri.withAppendedPath(Media.INTERNAL_CONTENT_URI,
					Long.toString(id)));
			player.prepare();
			player.start();
			lastSound = id;
		    } catch (IOException iox) {
			// Silence.  Oh, well.
		    } catch (Exception anyx) {
			Toast.makeText(PreferencesActivity.this,
				anyx.getMessage(), Toast.LENGTH_LONG);
		    }
		}
		prefs.edit().putLong(TPREF_NOTIFICATION_SOUND, id).commit();
	    }
	    @Override
	    public void onNothingSelected(AdapterView<?> parent) {}
	});
    }

    /** Called when the user presses the Back button */
    @Override
    public void onBackPressed() {
	Log.d(LOG_TAG, ".onBackPressed()");
	if (privateCheckBox.isChecked() &&
		(passwordEditText.length() > 0)) {
	    if (!encryptor.hasPassword(getContentResolver())) {
		// To do: the password field should have been disabled
		Toast.makeText(PreferencesActivity.this,
			R.string.ToastBadPassword, Toast.LENGTH_LONG).show();
	    } else {
		char[] newPassword = new char[passwordEditText.length()];
		passwordEditText.getText().getChars(0, newPassword.length, newPassword, 0);
		encryptor.setPassword(newPassword);
		Arrays.fill(newPassword, (char) 0);
		try {
		    if (encryptor.checkPassword(getContentResolver())) {
			prefs.edit().putBoolean(TPREF_SHOW_ENCRYPTED, true).commit();
			super.onBackPressed();
			return;
		    } else {
			Toast.makeText(PreferencesActivity.this,
				R.string.ToastBadPassword,
				Toast.LENGTH_LONG).show();
		    }
		} catch (GeneralSecurityException gsx) {
		    Toast.makeText(PreferencesActivity.this,
			    gsx.getMessage(), Toast.LENGTH_LONG).show();
		}
	    }
	}
	encryptor.forgetPassword();
	prefs.edit().putBoolean(TPREF_SHOW_ENCRYPTED, false).commit();
	super.onBackPressed();
    }

    /** Called when the activity is about to be destroyed */
    @Override
    public void onDestroy() {
	Log.d(LOG_TAG, ".onDestroy()");
	StringEncryption.releaseGlobalEncryption(this);
	player.release();
	super.onDestroy();
    }

    /** Look up the spinner item corresponding to a category ID and select it. */
    void setSpinnerByID(Spinner spinner, long id) {
	for (int position = 0; position < spinner.getCount(); position++) {
	    if (spinner.getItemIdAtPosition(position) == id) {
		spinner.setSelection(position);
		return;
	    }
	}
	Log.w(LOG_TAG, "No spinner item found for ID " + id);
	spinner.setSelection(0);
    }
}
