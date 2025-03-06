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
package com.xmission.trevin.android.todo;

import static com.xmission.trevin.android.todo.ToDoListActivity.*;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore.Audio.AudioColumns;
import android.provider.MediaStore.Audio.Media;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.PermissionChecker;
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
		if (position >= ToDoItem.USER_SORT_ORDERS.length) {
                    Log.e(LOG_TAG, "Unknown sort order selected");
                } else {
                    SharedPreferences.Editor editor = prefs.edit()
                            .putInt(TPREF_SORT_ORDER, position);
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD)
                        editor.commit();
                    else
                        editor.apply();
                }
	    }
	});

	CheckBox checkBox = (CheckBox) findViewById(R.id.PrefsCheckBoxShowChecked);
	checkBox.setChecked(prefs.getBoolean(TPREF_SHOW_CHECKED, false));
	checkBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
	    @Override
	    public void onCheckedChanged(CompoundButton button, boolean isChecked) {
		Log.d(LOG_TAG, "prefsCheckBoxShowCompleted.onCheckedChanged("
			+ isChecked + ")");
                SharedPreferences.Editor editor = prefs.edit()
                        .putBoolean(TPREF_SHOW_CHECKED, isChecked);
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD)
                    editor.commit();
                else
                    editor.apply();
	    }
	});

	checkBox = (CheckBox) findViewById(R.id.PrefsCheckBoxShowDueDate);
	checkBox.setChecked(prefs.getBoolean(TPREF_SHOW_DUE_DATE, false));
	checkBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
	    @Override
	    public void onCheckedChanged(CompoundButton button, boolean isChecked) {
		Log.d(LOG_TAG, "prefsCheckBoxShowDueDate.onCheckedChanged("
			+ isChecked + ")");
                SharedPreferences.Editor editor = prefs.edit()
                        .putBoolean(TPREF_SHOW_DUE_DATE, isChecked);
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD)
                    editor.commit();
                else
                    editor.apply();
	    }
	});

	checkBox = (CheckBox) findViewById(R.id.PrefsCheckBoxShowPriority);
	checkBox.setChecked(prefs.getBoolean(TPREF_SHOW_PRIORITY, false));
	checkBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
	    @Override
	    public void onCheckedChanged(CompoundButton button, boolean isChecked) {
		Log.d(LOG_TAG, "prefsCheckBoxShowPriority.onCheckedChanged("
			+ isChecked + ")");
                SharedPreferences.Editor editor = prefs.edit()
                        .putBoolean(TPREF_SHOW_PRIORITY, isChecked);
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD)
                    editor.commit();
                else
                    editor.apply();
	    }
	});

	checkBox = (CheckBox) findViewById(R.id.PrefsCheckBoxShowCategory);
	checkBox.setChecked(prefs.getBoolean(TPREF_SHOW_CATEGORY, false));
	checkBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
	    @Override
	    public void onCheckedChanged(CompoundButton button, boolean isChecked) {
		Log.d(LOG_TAG, "prefsCheckBoxShowCategory.onCheckedChanged("
			+ isChecked + ")");
                SharedPreferences.Editor editor = prefs.edit()
                        .putBoolean(TPREF_SHOW_CATEGORY, isChecked);
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD)
                    editor.commit();
                else
                    editor.apply();
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
                SharedPreferences.Editor editor = prefs.edit()
                        .putBoolean(TPREF_SHOW_PRIVATE, isChecked);
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD)
                    editor.commit();
                else
                    editor.apply();
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

	checkBox = (CheckBox) findViewById(R.id.PrefsCheckBoxAlarmVibrate);
        checkBox.setChecked(prefs.getBoolean(TPREF_NOTIFICATION_VIBRATE, false));
	checkBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
	    @Override
	    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		Log.d(LOG_TAG, "prefsCheckBoxAlarmVibrate.onCheckChanged("
			+ isChecked + ")");
		if (!checkVibratePermission()) {
		    // Warn the user that vibration is not granted
		    Toast.makeText(PreferencesActivity.this,
			R.string.ToastVibrateNotPermitted, Toast.LENGTH_LONG).show();
		    buttonView.setChecked(false);
		    return;
		}
                SharedPreferences.Editor editor = prefs.edit()
                        .putBoolean(TPREF_NOTIFICATION_VIBRATE, isChecked);
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD)
                    editor.commit();
                else
                    editor.apply();
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
				anyx.getMessage(), Toast.LENGTH_LONG).show();
		    }
		}
                SharedPreferences.Editor editor = prefs.edit()
                        .putLong(TPREF_NOTIFICATION_SOUND, id);
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD)
                    editor.commit();
                else
                    editor.apply();
	    }
	    @Override
	    public void onNothingSelected(AdapterView<?> parent) {}
	});
    }

    /**
     * Check whether the user has granted us permission to vibrate.
     * If not, request the permission if possible (Marshmallow API 23
     * or higher).
     *
     * @return true if we are allowed to vibrate the device
     */
    private boolean checkVibratePermission() {

	if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
	    // In Lollipop and earlier, permissions are granted at install time.
	    return PermissionChecker.checkSelfPermission(this,
			    Manifest.permission.VIBRATE) == PackageManager.PERMISSION_GRANTED;

	if (ContextCompat.checkSelfPermission(this, Manifest.permission.VIBRATE)
	    == PackageManager.PERMISSION_GRANTED)
	    return true;

	// To Do: Request the vibrate permission
	requestPermissions(new String[] { Manifest.permission.VIBRATE },
			R.id.PrefsCheckBoxAlarmVibrate);

	return false;

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
        }
        Log.d(LOG_TAG, String.format(".onRequestPermissionsResult(%d, %s, %s)",
                code, Arrays.toString(permissions),
                Arrays.toString(resultNames)));

        if (code != R.id.PrefsCheckBoxAlarmVibrate) {
            Log.e(LOG_TAG, "Unexpected code from request permissions; ignoring!");
            return;
        }

        if (permissions.length != results.length) {
            Log.e(LOG_TAG, String.format("Number of request permissions (%d"
                    + ") does not match number of results (%d); ignoring!",
                    permissions.length, results.length));
            return;
        }

        for (int i = 0; i < results.length; i++) {
            if (Manifest.permission.VIBRATE.equals(permissions[i])) {
                if (results[i] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(LOG_TAG, "Vibrate permission granted, enabling vibrating alarm");
                    SharedPreferences.Editor editor = prefs.edit()
                            .putBoolean(TPREF_NOTIFICATION_VIBRATE, true);
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD)
                        editor.commit();
                    else
                        editor.apply();
                    CheckBox checkBox = (CheckBox)
                            findViewById(R.id.PrefsCheckBoxAlarmVibrate);
                    checkBox.setChecked(true);
                }
                else if (results[i] == PackageManager.PERMISSION_DENIED) {
                    Log.i(LOG_TAG, "Vibrate permission denied!");
                }
            } else {
                Log.w(LOG_TAG, "Ignoring unknown permission " + permissions[i]);
            }
        }

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
                        SharedPreferences.Editor editor = prefs.edit()
                                .putBoolean(TPREF_SHOW_ENCRYPTED, true);
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD)
                            editor.commit();
                        else
                            editor.apply();
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
        SharedPreferences.Editor editor = prefs.edit()
                .putBoolean(TPREF_SHOW_ENCRYPTED, false);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD)
            editor.commit();
        else
            editor.apply();
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
