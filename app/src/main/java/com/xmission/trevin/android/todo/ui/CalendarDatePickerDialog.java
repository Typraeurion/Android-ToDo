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

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.*;
import android.content.DialogInterface.OnClickListener;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.xmission.trevin.android.todo.R;
import com.xmission.trevin.android.todo.ui.CalendarDatePicker.OnDateSetListener;

/**
 * A simple dialog containing a {@link CalendarDatePicker}.
 *
 * @author Trevin Beattie
 */
public class CalendarDatePickerDialog extends AlertDialog
        implements OnClickListener, CalendarDatePicker.OnDateSetListener {
    private static final String LOG_TAG = "CalDatePickerDialog";

    /**
     * The &ldquo;Cancel&rdquo; button for this dialog.
     * Historically this was AlertDialog button #1; we keep it
     * in the {@code BUTTON_POSITIVE} spot which is positioned
     * on the right side of the dialog.
     * <p>
     * This is <i>not</i> the button&rsquo;s resource ID; use
     * {@link #getButton}{@code (BUTTON_CANCEL).getId()} for that.
     * </p>
     */
    public static final int BUTTON_CANCEL = DialogInterface.BUTTON_POSITIVE;

    /**
     * The &ldquo;Today&rdquo; button for this dialog.
     * Historically this was AlertDialog button #2; we keep it
     * in the {@code BUTTON_NEGATIVE} spot which is positioned
     * to the left of the &ldquo;Cancel&rdquo; button
     * (just right of center.)
     * <p>
     * This is <i>not</i> the button&rsquo;s resource ID; use
     * {@link #getButton}{@code (BUTTON_TODAY).getId()} for that.
     * </p>
     */
    public static final int BUTTON_TODAY = DialogInterface.BUTTON_NEGATIVE;

    /**
     * The &ldquo;No Date&rdquo; button for this dialog, which is optional.
     * Historically this was AlertDialog button #3; it is positioned
     * on the left side of the dialog.
     * <p>
     * This is <i>not</i> the button&rsquo;s resource ID; use
     * {@link #getButton}{@code (BUTTON_NO_DATE).getId()} for that.
     * </p>
     */
    public static final int BUTTON_NO_DATE = DialogInterface.BUTTON_NEUTRAL;

    private ZoneId zone;
    private final CalendarDatePicker datePicker;
    private final OnDateSetListener callback;

    /**
     * Create a new calendar date picker dialog.
     *
     * @param context the context in which this dialog is being shown
     * @param title the title of the dialog indicating which date is being set
     * @param callback the callback used when the user has selected a date
     */
    @SuppressLint("InflateParams")
    public CalendarDatePickerDialog(
            @NonNull Context context, @NonNull CharSequence title,
            @Nullable OnDateSetListener callback) {
        super(context);
        this.callback = callback;
        setTitle(title);

        setButton(BUTTON_CANCEL, context.getText(
                R.string.DatePickerCancel), this);
        setButton(BUTTON_TODAY, context.getText(
                R.string.DatePickerToday), this);
        setButton(BUTTON_NO_DATE, context.getText(
                R.string.DueDateNoDate), this);
        setIcon(R.drawable.ic_dialog_time);

        LayoutInflater inflater = (LayoutInflater)
                context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.date_picker_dialog, null);
        setView(view);
        datePicker = view.findViewById(R.id.CalendarDatePicker);
        datePicker.setOnDateSetListener(this);
    }

    /**
     * Set today&rsquo;s date for the date picker.  This determines
     * which buttons are highlighted when {@link #setDate(LocalDate)}
     * is called and when the user selects different months
     * and years.
     *
     * @param today the date considered to be today
     * for the purpose of display.
     */
    public void setToday(@NonNull LocalDate today) {
        datePicker.setToday(today);
    }

    /**
     * Set the date displayed in the date picker dialog.
     * {@link #setToday(LocalDate)} must be called first in
     * order for the current date to be highlighted correctly.
     *
     * @param date the date to jump to in the date picker
     */
    public void setDate(@NonNull LocalDate date) {
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

    /**
     * Show or hide the &ldquo;No Date&rdquo; button.
     * This must only be called <i>after</i> the dialog
     * has been {@link #show}n; otherwise the dialog may
     * show it regardless.
     *
     * @param show whether to show {@code true} or hide {@code false}
     * the &ldquo;No Date&rdquo; button.
     */
    public void setNoDateShown(boolean show) {
        getButton(BUTTON_NO_DATE).setVisibility(
                show ? View.VISIBLE : View.GONE);
    }

    /** Called when the user clicks a dialog button */
    @Override
    public void onClick(DialogInterface dialog, int which) {
        Log.d(LOG_TAG, String.format(Locale.US,
                ".onClick(dialog,%s)",
                ((which == BUTTON_CANCEL) ? "Cancel"
                        : ((which == BUTTON_TODAY) ? "Today"
                        : ((which == BUTTON_NO_DATE) ? "No Date"
                        : Integer.toString(which))))));
        if (callback != null) {
            switch (which) {
                case BUTTON_TODAY:
                    callback.onDateSet(datePicker, LocalDate.now(zone));
                    break;
                case BUTTON_NO_DATE:
                    callback.onDateSet(datePicker, null);
                    break;
                default:
                    break;
            }
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
