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

import java.io.IOException;
import java.util.Arrays;

import android.Manifest;
import android.app.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore.Audio.AudioColumns;
import android.provider.MediaStore.Audio.Media;
import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;
import android.util.Log;
import android.view.*;
import android.widget.*;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.CompoundButton.OnCheckedChangeListener;

import com.xmission.trevin.android.todo.R;
import com.xmission.trevin.android.todo.data.ToDoPreferences;
import com.xmission.trevin.android.todo.util.StringEncryption;
import com.xmission.trevin.android.todo.provider.ToDo.*;

/**
 * The preferences activity manages the user options dialog.
 *
 * @author Trevin Beattie
 */
public class PreferencesActivity extends Activity {

    public static final String LOG_TAG = "PreferencesActivity";

    private ToDoPreferences prefs;

    CheckBox privateCheckBox = null;
    // EditText passwordEditText = null;

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

	prefs = ToDoPreferences.getInstance(this);

	Spinner spinner = (Spinner) findViewById(R.id.PrefsSpinnerSortBy);
	setSpinnerByID(spinner, prefs.getSortOrder());
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
		    prefs.setSortOrder(position);
	    }
	});

	CheckBox checkBox = (CheckBox) findViewById(R.id.PrefsCheckBoxShowChecked);
	checkBox.setChecked(prefs.showChecked());
	checkBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
	    @Override
	    public void onCheckedChanged(CompoundButton button, boolean isChecked) {
		Log.d(LOG_TAG, "prefsCheckBoxShowCompleted.onCheckedChanged("
			+ isChecked + ")");
		prefs.setShowChecked(isChecked);
	    }
	});

	checkBox = (CheckBox) findViewById(R.id.PrefsCheckBoxShowDueDate);
	checkBox.setChecked(prefs.showDueDate());
	checkBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
	    @Override
	    public void onCheckedChanged(CompoundButton button, boolean isChecked) {
		Log.d(LOG_TAG, "prefsCheckBoxShowDueDate.onCheckedChanged("
			+ isChecked + ")");
		prefs.setShowDueDate(isChecked);
	    }
	});

	checkBox = (CheckBox) findViewById(R.id.PrefsCheckBoxShowPriority);
	checkBox.setChecked(prefs.showPriority());
	checkBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
	    @Override
	    public void onCheckedChanged(CompoundButton button, boolean isChecked) {
		Log.d(LOG_TAG, "prefsCheckBoxShowPriority.onCheckedChanged("
			+ isChecked + ")");
		prefs.setShowPriority(isChecked);
	    }
	});

	checkBox = (CheckBox) findViewById(R.id.PrefsCheckBoxShowCategory);
	checkBox.setChecked(prefs.showCategory());
	checkBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
	    @Override
	    public void onCheckedChanged(CompoundButton button, boolean isChecked) {
		Log.d(LOG_TAG, "prefsCheckBoxShowCategory.onCheckedChanged("
			+ isChecked + ")");
		prefs.setShowCategory(isChecked);
	    }
	});

	encryptor = StringEncryption.holdGlobalEncryption();
	privateCheckBox = (CheckBox) findViewById(R.id.PrefsCheckBoxShowPrivate);
	privateCheckBox.setChecked(prefs.showPrivate());
	privateCheckBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
	    @Override
	    public void onCheckedChanged(CompoundButton button, boolean isChecked) {
		Log.d(LOG_TAG, "prefsCheckBoxShowPrivate.onCheckedChanged("
			+ isChecked + ")");
		prefs.setShowPrivate(isChecked);
	    }
	});

	checkBox = (CheckBox) findViewById(R.id.PrefsCheckBoxAlarmVibrate);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            checkBox.setChecked(prefs.notificationVibrate());
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
                    prefs.setNotificationVibrate(isChecked);
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
            final long initialSound = prefs.getNotificationSound();
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
                    prefs.setNotificationSound(id);
                }
                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        } else {
            // As of Oreo, we don't control vibration or sound through
            // our own preferences; the user has to set these in the
            // system settings.  Hide the options instead.
            checkBox.setVisibility(View.GONE);
            ViewParent soundParent = findViewById(
                    R.id.PrefsSpinnerAlarmSound).getParent();
            if (soundParent instanceof View) {
                ((View) soundParent).setVisibility(View.GONE);
            }
        }
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
                    Manifest.permission.VIBRATE) ==
                    PermissionChecker.PERMISSION_GRANTED;

	if (ContextCompat.checkSelfPermission(this,
            Manifest.permission.VIBRATE)
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
            resultNames[i] = name;
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
                    prefs.setNotificationVibrate(true);
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
	super.onBackPressed();
    }

    /** Called when the activity is about to be destroyed */
    @Override
    public void onDestroy() {
	Log.d(LOG_TAG, ".onDestroy()");
	StringEncryption.releaseGlobalEncryption(this);
        if (player != null)
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
