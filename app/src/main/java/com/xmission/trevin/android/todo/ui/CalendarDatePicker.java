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

import java.time.*;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;

import androidx.annotation.NonNull;

import com.xmission.trevin.android.todo.R;

/**
 * A view for selecting a year / month / day based on a calendar layout.
 *
 * @author Trevin Beattie
 */
public class CalendarDatePicker extends FrameLayout {
    private OnDateSetListener onDateSetListener;

    private LocalDate selectedDate;
    private int year;
    private int todaysYear;
    private Month month;
    private Month todaysMonth;
    private int date;
    private int todaysDate;

    /**
     * The offset of the first day of the currently shown month.
     * This can be used along with the day of the week and week
     * of the month indices to determine the date.
     */
    private int firstDay;

    /** Resource identifiers for the month buttons */
    static final int[] monthButtonIDs = {
            R.id.DatePickerJanuaryButton,   R.id.DatePickerFebruaryButton,
            R.id.DatePickerMarchButton,	    R.id.DatePickerAprilButton,
            R.id.DatePickerMayButton,	    R.id.DatePickerJuneButton,
            R.id.DatePickerJulyButton,	    R.id.DatePickerAugustButton,
            R.id.DatePickerSeptemberButton, R.id.DatePickerOctoberButton,
            R.id.DatePickerNovemberButton,  R.id.DatePickerDecemberButton
    };

    /** Resource identifiers for the week buttons */
    static final int[] weekButtonIDs = {
            R.id.DatePickerWeekRow0,    R.id.DatePickerWeekRow1,
            R.id.DatePickerWeekRow2,    R.id.DatePickerWeekRow3,
            R.id.DatePickerWeekRow4,    R.id.DatePickerWeekRow5
    };

    /** Resource identifiers for the date buttons */
    static final int[][] dateButtonIDs = {
            { R.id.DatePickerDay01Button, R.id.DatePickerDay02Button,
              R.id.DatePickerDay03Button, R.id.DatePickerDay04Button,
              R.id.DatePickerDay05Button, R.id.DatePickerDay06Button,
              R.id.DatePickerDay07Button },
            { R.id.DatePickerDay11Button, R.id.DatePickerDay12Button,
              R.id.DatePickerDay13Button, R.id.DatePickerDay14Button,
              R.id.DatePickerDay15Button, R.id.DatePickerDay16Button,
              R.id.DatePickerDay17Button },
            { R.id.DatePickerDay21Button, R.id.DatePickerDay22Button,
              R.id.DatePickerDay23Button, R.id.DatePickerDay24Button,
              R.id.DatePickerDay25Button, R.id.DatePickerDay26Button,
              R.id.DatePickerDay27Button },
            { R.id.DatePickerDay31Button, R.id.DatePickerDay32Button,
              R.id.DatePickerDay33Button, R.id.DatePickerDay34Button,
              R.id.DatePickerDay35Button, R.id.DatePickerDay36Button,
              R.id.DatePickerDay37Button },
            { R.id.DatePickerDay41Button, R.id.DatePickerDay42Button,
              R.id.DatePickerDay43Button, R.id.DatePickerDay44Button,
              R.id.DatePickerDay45Button, R.id.DatePickerDay46Button,
              R.id.DatePickerDay47Button },
            { R.id.DatePickerDay51Button, R.id.DatePickerDay52Button,
              R.id.DatePickerDay53Button, R.id.DatePickerDay54Button,
              R.id.DatePickerDay55Button, R.id.DatePickerDay56Button,
              R.id.DatePickerDay57Button }
    };

    /** The edit box for the year */
    private final TextView yearText;

    /** Buttons for months */
    private final RadioButton[] monthButtons = new RadioButton[monthButtonIDs.length];

    /** Rows for weeks */
    private final TableRow[] weekRows = new TableRow[weekButtonIDs.length];

    /** Buttons for dates */
    private final Button[][] dateButtons = new Button[dateButtonIDs.length][];

    /** The callback used to indicate the user has selected the date. */
    public interface OnDateSetListener {
        /**
         * @param view The view associated with this listener.
         * @param date The date that was set.
         */
        void onDateSet(CalendarDatePicker view, LocalDate date);
    }

    /**
     * Create a new calendar date picker set to the current date
     */
    public CalendarDatePicker(Context context) {
        this(context, null);
    }

    public CalendarDatePicker(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CalendarDatePicker(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        LayoutInflater inflater = (LayoutInflater)
                context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.date_picker, this, true);

        yearText = findViewById(R.id.DatePickerTextYear);

        Button button = findViewById(R.id.DatePickerPriorYearButton);
        button.setOnClickListener(new YearOnClickListener(-1));

        button = findViewById(R.id.DatePickerNextYearButton);
        button.setOnClickListener(new YearOnClickListener(1));

        for (int month = 0; month < monthButtonIDs.length; month++) {
            monthButtons[month] = findViewById(monthButtonIDs[month]);
            monthButtons[month].setOnClickListener(
                    new MonthOnClickListener(month));
        }

        for (int week = 0; week < dateButtonIDs.length; week++) {
            weekRows[week] = findViewById(weekButtonIDs[week]);
            dateButtons[week] = new Button[dateButtonIDs[week].length];
            for (int day = 0; day < dateButtonIDs[week].length; day++) {
                dateButtons[week][day] =
                        findViewById(dateButtonIDs[week][day]);
                dateButtons[week][day].setOnClickListener(
                        new DateOnClickListener(week, day));
            }
        }

        // Initialize defaults for today and the selected date.
        // These need to be overridden by the caller.
        selectedDate = LocalDate.now();
        setToday(selectedDate);
        setDate(selectedDate);
    }

    /**
     * Set today&rsquo;s date in the calendar.  This determines
     * whether and which month and date are highlighted (shown
     * in bold) as the user scrolls through the months and years.
     *
     * @param today today&rsquo;s date
     */
    public void setToday(@NonNull LocalDate today) {
        todaysYear = today.getYear();
        // Clear the typeface of the previous month if it was set
        if (todaysMonth != null)
            monthButtons[todaysMonth.getValue() - Month.JANUARY.getValue()]
                    .setTypeface(Typeface.DEFAULT);
        todaysMonth = today.getMonth();
        // Mark the current month if we are in the current year
        monthButtons[todaysMonth.getValue() - Month.JANUARY.getValue()]
                .setTypeface((year == todaysYear) ? Typeface.DEFAULT_BOLD
                        : Typeface.DEFAULT);
        todaysDate = today.getDayOfMonth();
    }

    /**
     * Set the selected date of this calendar date picker.
     * This will configure the date buttons with the appropriate
     * days for the selected month.  It also highlights the
     * current month if the selected date is in the current year
     * and the current date if the selected month is in the
     * current year, so the current date should be set with
     * {@link #setToday} before making this call.
     *
     * @param selectedDate the date to jump to in the date picker
     */
    public void setDate(@NonNull LocalDate selectedDate) {
        this.selectedDate = selectedDate;
        year = selectedDate.getYear();
        month = selectedDate.getMonth();
        date = selectedDate.getDayOfMonth();
        initDates();
    }

    /**
     * Set the date set listener for this date picker
     */
    public void setOnDateSetListener(OnDateSetListener listener) {
        onDateSetListener = listener;
    }

    /**
     * Rewrite the date buttons of the calendar after changing the
     * year or month.
     */
    void initDates() {
        yearText.setText(Integer.toString(year));
        yearText.setTypeface((year == todaysYear)
                ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);

        RadioGroup monthGroup = findViewById(R.id.DatePickerMonthRadioGroup);
        monthGroup.check(monthButtonIDs[month.getValue()
                - Month.JANUARY.getValue()]);
        // Mark the current month if we are in the current year
        monthButtons[todaysMonth.getValue() - Month.JANUARY.getValue()]
                .setTypeface((year == todaysYear) ? Typeface.DEFAULT_BOLD
                        : Typeface.DEFAULT);

        selectedDate = LocalDate.of(year, month, 1);
        firstDay = (selectedDate.getDayOfWeek().getValue()
                + DayOfWeek.SUNDAY.getValue()) % 7;
        int maxDate = selectedDate.lengthOfMonth();
        for (int week = 0; week < dateButtonIDs.length; week++) {
            for (int day = 0; day < dateButtonIDs[week].length; day++) {
                int date = 7 * week + day - firstDay + 1;
                if ((date < 1) || (date > maxDate)) {
                    dateButtons[week][day].setText("");
                    dateButtons[week][day].setVisibility(View.INVISIBLE);
                    dateButtons[week][day].setClickable(false);
                    dateButtons[week][day].setTypeface(Typeface.DEFAULT);
                } else {
                    dateButtons[week][day].setText(Integer.toString(date));
                    if ((date < 7) || (date > 22)) {
                        dateButtons[week][day].setVisibility(View.VISIBLE);
                        dateButtons[week][day].setClickable(true);
                    }
                    // Mark the current date when we come across it
                    dateButtons[week][day].setTypeface(((year == todaysYear) &&
                            (month == todaysMonth) &&
                            (date == todaysDate))
                            ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                    // Mark the selected date when we come across it
                    dateButtons[week][day].setSelected((date == this.date) ||
                            ((date == maxDate) && (this.date > maxDate)));
                }
            }
            /*
             * Remove the last row if the month is only 5 rows long.
             * It's also possible the calendar may need only 4 rows if
             * it's February in a non-leap year and the 1st is on Sunday.
             */
            if ((week > 0) &&
                    ((dateButtons[week][0].getVisibility() == VISIBLE) !=
                            (weekRows[week].getVisibility() == VISIBLE))) {
                weekRows[week].setVisibility(
                        dateButtons[week][0].getVisibility() == VISIBLE
                                ? VISIBLE : GONE);
            }
        }
    }

    /**
     * Changes the year when the previous or next year button is pressed
     */
    class YearOnClickListener implements View.OnClickListener {
        private final int increment;
        YearOnClickListener(int add) {
            increment = add;
        }
        @Override
        public void onClick(View v) {
            year += increment;
            initDates();
        }
    }

    /**
     * Changes the month when any month button is pressed
     */
    class MonthOnClickListener implements View.OnClickListener {
        private final int monthIndex;
        MonthOnClickListener(int month) {
            monthIndex = month;
        }
        @Override
        public void onClick(View v) {
            month = Month.of(monthIndex + Month.JANUARY.getValue());
            initDates();
        }
    }

    /**
     * Selects a date in the displayed year and month
     */
    class DateOnClickListener implements View.OnClickListener {
        private final int weekRow;
        private final int dayColumn;
        DateOnClickListener(int week, int day) {
            weekRow = week;
            dayColumn = day;
        }
        @Override
        public void onClick(View v) {
            date = 7 * weekRow + dayColumn - firstDay + 1;
            if (onDateSetListener != null)
                onDateSetListener.onDateSet(CalendarDatePicker.this,
                        LocalDate.of(year, month, date));
        }
    }

    private static class SavedState extends BaseSavedState {
        private final int year;
        private final Month month;
        private final int date;

        /** Constructor called from
         *  {@link CalendarDatePicker#onSaveInstanceState()}
         */
        private SavedState(Parcelable superState,
                           int year, Month month, int date) {
            super(superState);
            this.year = year;
            this.month = month;
            this.date = date;
        }

        /** Constructor called from {@link #CREATOR} */
        private SavedState(Parcel in) {
            super(in);
            this.year = in.readInt();
            this.month = Month.valueOf(in.readString());
            this.date = in.readInt();
        }

        public int getYear() { return year; }
        public Month getMonth() { return month; }
        public int getDate() { return date; }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(year);
            dest.writeString(month.name());
            dest.writeInt(date);
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Creator<SavedState>() {

                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }

                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
    }

    @Override
    protected void dispatchRestoreInstanceState(SparseArray<Parcelable> container) {
        dispatchThawSelfOnly(container);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();

        return new SavedState(superState, year, month, date);
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        year = ss.getYear();
        month = ss.getMonth();
        date = ss.getDate();
    }

}
