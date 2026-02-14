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

import android.app.AlertDialog;
import android.content.*;
import android.content.DialogInterface.OnClickListener;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;

import com.xmission.trevin.android.todo.R;
import com.xmission.trevin.android.todo.data.repeat.RepeatInterval;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;

/**
 * A simple dialog containing a complex {@link RepeatEditor}.
 *
 * @author Trevin Beattie
 */
public class RepeatEditorDialog extends AlertDialog implements OnClickListener {
    private static final String LOG_TAG = "RepeatEditorDialog";

    private final RepeatEditor repeatEditor;
    private final OnRepeatSetListener callback;

    /**
     * The callback used to indicate the user has finished setting the repeat.
     */
    public interface OnRepeatSetListener {
        /**
         * @param view The view associated with this listener.
         * @param repeat The repeat settings.
         */
        void onRepeatSet(RepeatEditor view, RepeatInterval repeat);
    }

    /** Create a new repeat editor dialog */
    public RepeatEditorDialog(Context context, OnRepeatSetListener callback) {
        super(context);
        this.callback = callback;
        Log.d(LOG_TAG, "creating");
        setTitle(context.getResources().getString(R.string.RepeatTitle));

        setButton(context.getText(R.string.ConfirmationButtonOK), this);
        setButton2(context.getText(R.string.ConfirmationButtonCancel), this);
        setIcon(R.drawable.ic_dialog_time);

        LayoutInflater inflater = (LayoutInflater)
                context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.repeat_dialog, null);
        setView(view);
        repeatEditor = (RepeatEditor)
                view.findViewById(R.id.RepeatEditor);
        Log.d(LOG_TAG, "created");
    }

    /**
     * Update the repeat settings in the dialog
     * according to the given interval.
     *
     * @param repeat the repeat interval from which to populate the dialog
     * @param dueDate the due date on which to base the repeat settings
     */
    public void setRepeat(RepeatInterval repeat, LocalDate dueDate) {
        repeatEditor.setRepeat(repeat, dueDate);
    }

    /**
     * Update the time zone used by the editor.
     *
     * @param zoneId the time zone to use
     */
    public void setTimeZone(ZoneId zoneId) {
        repeatEditor.setTimeZone(zoneId);
    }

    /**
     * Populate the repeat editor with a set of repeat settings.
     * This is used when restoring the saved state after a
     * configuration change or a period of sleep.  This should not
     * trigger any callbacks.
     *
     * @param settings the previously saved settings
     */
    void restoreRepeatSettings(@NonNull RepeatSettings settings) {
        repeatEditor.restoreSettings(settings);
    }

    /** @return the repeat settings currently set in this dialog */
    RepeatSettings getRepeatSettings() {
        return repeatEditor.getSettings();
    }

    /** Called when the user clicks either the Cancel or the OK button */
    @Override
    public void onClick(DialogInterface dialog, int which) {
        Log.d(LOG_TAG, String.format(Locale.US,
                ".onClick(dialog,%s)",
                ((which == DialogInterface.BUTTON1) ? "OK"
                        : ((which == DialogInterface.BUTTON2) ? "Cancel"
                        : Integer.toString(which)))));
        if ((which == DialogInterface.BUTTON1) && (callback != null)) {
            callback.onRepeatSet(repeatEditor,
                    repeatEditor.getSettings().getRepeat());
        }
        dismiss();
    }

}
