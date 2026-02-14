/*
 * Copyright © 2011–2026 Trevin Beattie
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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;

import android.app.AlertDialog;
import android.content.*;
import android.content.DialogInterface.OnClickListener;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;

import com.xmission.trevin.android.todo.R;

/**
 * A simple dialog containing a {@link CalendarDatePicker}.
 *
 * @author Trevin Beattie
 */
public class CalendarDatePickerDialog extends AlertDialog
        implements OnClickListener, CalendarDatePicker.OnDateSetListener {
    private static final String LOG_TAG = "CalDatePickerDialog";

    private ZoneId zone;
    private final CalendarDatePicker datePicker;
    private final OnDateSetListener callback;

    /** The callback used to indicate the user has selected a date. */
    public interface OnDateSetListener {
        /**
         * @param view The view associated with this listener.
         * @param date The date that was set.
         */
        void onDateSet(CalendarDatePicker view, LocalDate date);
    }

    /**
     * Create a new calendar date picker dialog for the current date.
     *
     * @param context the context in which this dialog is being shown
     * @param title the title of the dialog indicating which date is being set
     * @param callback the callback used when the user has selected a date
     */
    public CalendarDatePickerDialog(
            Context context, CharSequence title,
            OnDateSetListener callback) {
        super(context);
        this.callback = callback;
        setTitle(title);

        setButton(context.getText(R.string.DatePickerCancel), this);
        setButton2(context.getText(R.string.DatePickerToday), this);
        setIcon(R.drawable.ic_dialog_time);

        LayoutInflater inflater = (LayoutInflater)
                context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.date_picker_dialog, null);
        setView(view);
        datePicker = (CalendarDatePicker)
                view.findViewById(R.id.CalendarDatePicker);
        datePicker.setOnDateSetListener(this);
    }

    /**
     * Set the date displayed in the date picker dialog.
     *
     * @param date the date to highlight
     */
    public void setDate(LocalDate date) {
        datePicker.setDate(date);
    }

    /**
     * Set the time zone used for determining the current date
     *
     * @param zoneId the time zone to use
     */
    public void setTimeZone(ZoneId zoneId) {
        zone = zoneId;
    }

    /** Called when the user clicks either the Cancel or the Today button */
    @Override
    public void onClick(DialogInterface dialog, int which) {
        Log.d(LOG_TAG, String.format(Locale.US,
                ".onClick(dialog,%s)",
                ((which == DialogInterface.BUTTON1) ? "Cancel"
                        : ((which == DialogInterface.BUTTON2) ? "Today"
                        : Integer.toString(which)))));
        if ((which == DialogInterface.BUTTON2) && (callback != null)) {
            callback.onDateSet(datePicker, LocalDate.now(zone));
        }
        dismiss();
    }

    /** Called when the user clicks a date in the date picker */
    @Override
    public void onDateSet(CalendarDatePicker view, LocalDate date) {
        Log.d(LOG_TAG, String.format(Locale.US, ".onDateSet(view,%s)", date));
        if (callback != null)
            callback.onDateSet(view, date);
        dismiss();
    }

}
