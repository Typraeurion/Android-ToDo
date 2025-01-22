/*
 * $Id: CalendarDatePickerDialog.java,v 1.2 2011/07/18 00:47:22 trevin Exp trevin $
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
 * $Log: CalendarDatePickerDialog.java,v $
 * Revision 1.2  2011/07/18 00:47:22  trevin
 * Added the copyright notice
 *
 * Revision 1.1  2011/01/04 06:00:37  trevin
 * Initial revision
 *
 */
package com.xmission.trevin.android.todo;

import java.util.Calendar;

import android.app.AlertDialog;
import android.content.*;
import android.content.DialogInterface.OnClickListener;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;

/**
 * A simple dialog containing a {@link CalendarDatePicker}.
 */
public class CalendarDatePickerDialog extends AlertDialog
	implements OnClickListener, CalendarDatePicker.OnDateSetListener {
    private static final String LOG_TAG = "CalendarDatePickerDialog";

    private final CalendarDatePicker datePicker;
    private final OnDateSetListener callback;

    /** The callback used to indicate the user has selected a date. */
    public interface OnDateSetListener {
	/**
	 * @param view The view associated with this listener.
	 * @param year The year that was set.
	 * @param monthOfYear The month that was set
	 *  (Calendar.JANUARY - Calendar.DECEMBER).
	 * @param dayOfMonth The day of the month that was set.
	 */
	void onDateSet(CalendarDatePicker view,
		int year, int monthOfYear, int dayOfMonth);
    }

    /** Create a new calendar date picker dialog for the given date */
    public CalendarDatePickerDialog(Context context, CharSequence title,
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

    /** Create a new calendar date picker dialog for the given date */
    public CalendarDatePickerDialog(Context context, CharSequence title,
	    OnDateSetListener callback,
	    int year, int monthOfYear, int dayOfMonth) {
	this(context, title, callback);
	setDate(year, monthOfYear, dayOfMonth);
    }

    /** Set the date displayed in the date picker dialog */
    public void setDate(int year, int monthOfYear, int dayOfMonth) {
	Calendar c = Calendar.getInstance();
	c.set(year, monthOfYear, dayOfMonth);
	datePicker.setDate(c.getTime());
    }

    /** Called when the user clicks either the Cancel or the Today button */
    @Override
    public void onClick(DialogInterface dialog, int which) {
	Log.d(LOG_TAG, ".onClick(dialog,"
		+ ((which == DialogInterface.BUTTON1) ? "Cancel"
			: ((which == DialogInterface.BUTTON2) ? "Today"
				: Integer.toString(which))) + ")");
	if ((which == DialogInterface.BUTTON2) && (callback != null)) {
	    Calendar c = Calendar.getInstance();
	    callback.onDateSet(datePicker, c.get(Calendar.YEAR),
		    c.get(Calendar.MONTH), c.get(Calendar.DATE));
	}
	dismiss();
    }

    /** Called when the user clicks a date in the date picker */
    @Override
    public void onDateSet(CalendarDatePicker view,
		int year, int monthOfYear, int dayOfMonth) {
	Log.d(LOG_TAG, ".onDateSet(view," + year + "," + monthOfYear
		+ "," + dayOfMonth + ")");
	if (callback != null)
	    callback.onDateSet(view, year, monthOfYear, dayOfMonth);
	dismiss();
    }
}
