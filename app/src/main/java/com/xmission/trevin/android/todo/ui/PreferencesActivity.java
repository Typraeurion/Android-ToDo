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
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.Arrays;
import java.util.Locale;

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
import com.xmission.trevin.android.todo.provider.ToDoSchema.*;

/**
 * The preferences activity manages the user options dialog.
 *
 * @author Trevin Beattie
 */
public class PreferencesActivity extends Activity {

    public static final String LOG_TAG = "PreferencesActivity";

    /** ID of the time zone dialog */
    private static final int TIMEZONE_LIST_DIALOG_ID = 20;

    private ToDoPreferences prefs;

    CheckBox privateCheckBox = null;
    Button timeZoneButton = null;

    /** Adapter which provides the grouped list of time zones */
    TimeZoneSelectAdapter timeZoneAdapter = null;
    ExpandableListView timeZoneListView = null;
    Dialog timeZoneDialog = null;

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

        Spinner spinner = findViewById(R.id.PrefsSpinnerSortBy);
        setSpinnerByID(spinner, prefs.getSortOrder());
        spinner.setOnItemSelectedListener(SORT_ORDER_SELECTED_LISTENER);

        CheckBox checkBox = findViewById(R.id.PrefsCheckBoxShowChecked);
        checkBox.setChecked(prefs.showChecked());
        checkBox.setOnCheckedChangeListener(SHOW_CHECKED_CHANGED_LISTENER);

        checkBox = findViewById(R.id.PrefsCheckBoxShowDueDate);
        checkBox.setChecked(prefs.showDueDate());
        checkBox.setOnCheckedChangeListener(SHOW_DUE_CHANGED_LISTENER);

        checkBox = findViewById(R.id.PrefsCheckBoxShowPriority);
        checkBox.setChecked(prefs.showPriority());
        checkBox.setOnCheckedChangeListener(SHOW_PRIORITIES_CHANGED_LISTENER);

        checkBox = findViewById(R.id.PrefsCheckBoxShowCategory);
        checkBox.setChecked(prefs.showCategory());
        checkBox.setOnCheckedChangeListener(SHOW_CATEGORIES_CHANGED_LISTENER);

        privateCheckBox = findViewById(R.id.PrefsCheckBoxShowPrivate);
        privateCheckBox.setChecked(prefs.showPrivate());
        privateCheckBox.setOnCheckedChangeListener(SHOW_PRIVATE_CHANGED_LISTENER);

        checkBox = findViewById(R.id.PrefsCheckBoxAlarmVibrate);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            checkBox.setChecked(prefs.notificationVibrate());
            checkBox.setOnCheckedChangeListener(ALARM_VIBRATE_CHANGED_LISTENER);

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
                    new NoSelectionCursorAdapter(this,
                            audioCursor, AudioColumns.TITLE,
                            getString(R.string.PrefTextNoSound));
            spinner = findViewById(R.id.PrefsSpinnerAlarmSound);
            spinner.setAdapter(soundAdapter);
            final long initialSound = prefs.getNotificationSound();
            setSpinnerByID(spinner, initialSound);
            spinner.setOnItemSelectedListener(SOUND_SELECTED_LISTENER);
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

        checkBox = findViewById(R.id.PrefsCheckBoxLocalZone);
        checkBox.setChecked(prefs.useLocalTimeZone());
        checkBox.setOnCheckedChangeListener(LOCAL_ZONE_CHANGED_LISTENER);
        timeZoneButton = findViewById(R.id.PrefsButtonTimeZone);
        timeZoneButton.setText(prefs.getTimeZone().getDisplayName(
                TextStyle.FULL, Locale.getDefault()));
        timeZoneButton.setEnabled(!prefs.useLocalTimeZone());
        timeZoneButton.setOnClickListener(TIME_ZONE_CLICK_LISTENER);

    }

    /** Called when opening a dialog for the first time. */
    @Override
    public Dialog onCreateDialog(int id) {
        Log.d(LOG_TAG, String.format(Locale.US, ".onCreateDialog(%d)", id));
        if (id != TIMEZONE_LIST_DIALOG_ID) {
            // We don't support any other dialogs from here
            Log.e(LOG_TAG, "Undefined dialog ID " + id);
            return null;
        }

        if (timeZoneDialog != null) {
            Log.w(LOG_TAG, "Dialog was already created!");
            return timeZoneDialog;
        }
        if (timeZoneAdapter == null)
            timeZoneAdapter = new TimeZoneSelectAdapter(this);
        View rootView = getLayoutInflater().inflate(
                R.layout.timezone_dialog, null);
        if (rootView == null) {
            Log.e(LOG_TAG, ".onCreateDialog: failed to inflate"
                    + " timezone dialog view!");
            return null;
        }
        timeZoneListView = rootView.findViewById(R.id.TimeZoneList);
        timeZoneListView.setAdapter(timeZoneAdapter);
        timeZoneListView.setOnChildClickListener(TIME_ZONE_LIST_LISTENER);
        timeZoneListView.setOnGroupExpandListener(TIME_ZONE_EXPAND_LISTENER);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.PrefTextTimeZone);
        builder.setView(rootView);
        timeZoneDialog = builder.create();
        return timeZoneDialog;
    }

    /** Called when an item is selected in the sort order list. */
    private final OnItemSelectedListener SORT_ORDER_SELECTED_LISTENER =
            new OnItemSelectedListener() {
                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    // Do nothing
                }
                @Override
                public void onItemSelected(AdapterView<?> parent, View child,
                                           int position, long id) {
                    Log.d(LOG_TAG, String.format(Locale.US,
                            "SortOrderSelectedListener.onItemSelected(%d,%d)",
                            position, id));
                    if (position >= ToDoItemColumns.USER_SORT_ORDERS.length)
                        Log.e(LOG_TAG, "Unknown sort order selected");
                    else
                        prefs.setSortOrder(position);
                }
            };

    /**
     * Called when the user toggles the &ldquo;Show completed
     * and hidden tasks&rdquo; preference.
     */
    private final OnCheckedChangeListener SHOW_CHECKED_CHANGED_LISTENER
            = new OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton button, boolean isChecked) {
            Log.d(LOG_TAG, String.format(Locale.US,
                    "ShowCheckedChangedListener.onCheckedChanged(%s)",
                    isChecked));
            prefs.setShowChecked(isChecked);
        }
    };

    /**
     * Called when the user toggles the &ldquo;Show due dates&rdquo;
     * preference.
     */
    private final OnCheckedChangeListener SHOW_DUE_CHANGED_LISTENER
            = new OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton button, boolean isChecked) {
            Log.d(LOG_TAG, String.format(Locale.US,
                    "ShowDueChangedListener.onCheckedChanged(%s)",
                    isChecked));
            prefs.setShowDueDate(isChecked);
        }
    };

    /**
     * Called when the user toggles the &ldquo;Show priorities&rdquo;
     * preference.
     */
    private final OnCheckedChangeListener SHOW_PRIORITIES_CHANGED_LISTENER
            = new OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton button, boolean isChecked) {
            Log.d(LOG_TAG, String.format(Locale.US,
                    "ShowPrioritiesChangedListener.onCheckedChanged(%s)",
                    isChecked));
            prefs.setShowPriority(isChecked);
        }
    };

    /**
     * Called when the user toggles the &ldquo;Show categories&rdquo;
     * preference.
     */
    private final OnCheckedChangeListener SHOW_CATEGORIES_CHANGED_LISTENER
            = new OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton button, boolean isChecked) {
            Log.d(LOG_TAG, String.format(Locale.US,
                    "ShowCategoriesChangedListener.onCheckedChanged(%s)",
                    isChecked));
            prefs.setShowCategory(isChecked);
        }
    };

    /**
     * Called when the user toggles the &ldquo;Show private records&rdquo;
     * preference.
     */
    private final OnCheckedChangeListener SHOW_PRIVATE_CHANGED_LISTENER
            = new OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton button, boolean isChecked) {
            Log.d(LOG_TAG, String.format(Locale.US,
                    "ShowPrivateChangedListener.onCheckedChanged(%s)",
                    isChecked));
            prefs.setShowPrivate(isChecked);
        }
    };

    /**
     * Called when the user toggles the &ldquo;Alarm vibrate&rdquo;
     * preference.
     */
    private final OnCheckedChangeListener ALARM_VIBRATE_CHANGED_LISTENER
            = new OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton button, boolean isChecked) {
            Log.d(LOG_TAG, String.format(Locale.US,
                    "AlarmVibrateChangedListener.onCheckedChanged(%s)",
                    isChecked));
            if (!checkVibratePermission()) {
                // Warn the user that vibration is not granted
                Toast.makeText(PreferencesActivity.this,
                        R.string.ToastVibrateNotPermitted, Toast.LENGTH_LONG).show();
                button.setChecked(false);
                return;
            }
            prefs.setNotificationVibrate(isChecked);
        }
    };

    /** Called when the user selects a notification sound. */
    private final OnItemSelectedListener SOUND_SELECTED_LISTENER
            = new OnItemSelectedListener() {
        long lastSound = 0;
        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
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
    };

    /**
     * Called when the user toggles the &ldquo;Use system
     * time zone&rdquo; preference.
     */
    private final OnCheckedChangeListener LOCAL_ZONE_CHANGED_LISTENER
            = new OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton button, boolean isChecked) {
            Log.d(LOG_TAG, String.format(Locale.US,
                    "LocalZoneChangedListener.onCheckedChanged(%s)",
                    isChecked));
            // Toggling this option always reverts us to the local time zone
            ZoneId localZone = ZoneId.systemDefault();
            if (isChecked)
                prefs.setTimeZoneLocal();
            else
                prefs.setTimeZone(localZone);
            timeZoneButton.setText(localZone.getDisplayName(
                    TextStyle.FULL, Locale.getDefault()));
            timeZoneButton.setEnabled(!isChecked);
        }
    };

    /**
     * Called when the user clicks the time zone button.
     */
    private final View.OnClickListener TIME_ZONE_CLICK_LISTENER
            = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Log.d(LOG_TAG, "TimeZoneClickListener.onClick()");
            showDialog(TIMEZONE_LIST_DIALOG_ID);
        }
    };

    /**
     * Called when the user expands a time zone region.
     * We automatically collapse any other open regions.
     */
    private final ExpandableListView.OnGroupExpandListener TIME_ZONE_EXPAND_LISTENER
            = new ExpandableListView.OnGroupExpandListener() {
        @Override
        public void onGroupExpand(int groupPosition) {
            for (int i = 0; i < timeZoneAdapter.getGroupCount(); i++) {
                if (i != groupPosition)
                    timeZoneListView.collapseGroup(i);
            }
        }
    };
    /** Called when the user selects a new time zone */
    private final ExpandableListView.OnChildClickListener TIME_ZONE_LIST_LISTENER
            = new ExpandableListView.OnChildClickListener() {
        @Override
        public boolean onChildClick(ExpandableListView parent,
                                 View v, int groupPosition,
                                 int childPosition, long childId) {
            Log.d(LOG_TAG, String.format(Locale.US,
                    "TimeZoneClickListener.onChildClick(%d, %d, %s)",
                    groupPosition, childPosition, Long.toHexString(childId)));
            ZoneId zone = timeZoneAdapter.getChild(
                    groupPosition, childPosition);
            prefs.setTimeZone(zone);
            timeZoneButton.setText(zone.getDisplayName(
                    TextStyle.FULL, Locale.getDefault()));
            timeZoneDialog.dismiss();
            return true;
        }
    };

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
            Log.e(LOG_TAG, String.format(Locale.US,
                    "Number of request permissions (%d) does not"
                            + " match number of results (%d); ignoring!",
                    permissions.length, results.length));
            return;
        }

        for (int i = 0; i < results.length; i++) {
            if (Manifest.permission.VIBRATE.equals(permissions[i])) {
                if (results[i] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(LOG_TAG, "Vibrate permission granted, enabling vibrating alarm");
                    prefs.setNotificationVibrate(true);
                    CheckBox checkBox =
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
        if (player != null)
            player.release();
        super.onDestroy();
    }

    /**
     * Look up the spinner item corresponding to a row ID and select it.
     */
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
