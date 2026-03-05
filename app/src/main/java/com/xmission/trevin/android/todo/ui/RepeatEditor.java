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

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

import com.xmission.trevin.android.todo.R;
import com.xmission.trevin.android.todo.data.repeat.*;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.*;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A view for selecting a repeat interval.
 *
 * @author Trevin Beattie
 */
public class RepeatEditor extends FrameLayout
        implements RepeatSettings.OnRepeatChangeListener {

    private static final String LOG_TAG = "RepeatEditor";

    private RepeatSettings repeatSettings = null;

    boolean isUpdating = true;

    private TextView noRepeatText = null;
    private ViewGroup yesRepeatLayout = null;

    private RadioGroup intervalGroup = null;
    private RadioGroup resetGroup = null;
    private RadioGroup alternateGroup = null;
    private RadioGroup dayOrDateGroup = null;

    private EditText incrementEditText = null;

    private Button endDateButton = null;
    CalendarDatePickerDialog endDateDialog = null;

    private TableRow weekdayRow = null;
    private TableRow alternateRow = null;
    private TableRow dayteRow = null;

    private TextView periodText = null;
    private TextView weekdayLabelText = null;
    private TextView descriptionText = null;

    private static final int[] WEEKDAY_TOGGLE_IDs = {
            R.id.RepeatToggleSunday, R.id.RepeatToggleMonday,
            R.id.RepeatToggleTuesday, R.id.RepeatToggleWednesday,
            R.id.RepeatToggleThursday, R.id.RepeatToggleFriday,
            R.id.RepeatToggleSaturday };
    private static final int DAYS_IN_WEEK = WEEKDAY_TOGGLE_IDs.length;
    private final ToggleButton[] weekdayToggle =
            new ToggleButton[WEEKDAY_TOGGLE_IDs.length];
    private ToggleButton nearestToggle = null;

    private final String[] monthNames;
    private final String[] weekdayNames;

    private ZoneId timeZone;

    /**
     * Create a new repeat editor set to the current date
     */
    public RepeatEditor(Context context) {
        this(context, null);
    }

    /**
     * Create a new repeat editor
     *
     * @param context the context in which this editor is being shown
     */
    public RepeatEditor(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /**
     * Create a new repeat editor
     */
    public RepeatEditor(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        Log.d(LOG_TAG, "creating");

        monthNames = getResources().getStringArray(R.array.MonthList);
        weekdayNames = getResources().getStringArray(R.array.WeekdayList);

        LayoutInflater inflater = (LayoutInflater)
                context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.repeat, this, true);

        noRepeatText = findViewById(R.id.RepeatTextNone);
        yesRepeatLayout = findViewById(R.id.RepeatLayout);
        intervalGroup = findViewById(R.id.RepeatRadioGroupInterval);
        resetGroup =  findViewById(R.id.RepeatRadioGroupReset);
        alternateGroup = findViewById(R.id.RepeatRadioGroupAlternateDirection);
        dayOrDateGroup = findViewById(R.id.RepeatRadioGroupDayte);
        incrementEditText = findViewById(R.id.RepeatEditTextEvery);
        endDateButton = findViewById(R.id.RepeatButtonEndDate);
        for (int i = 0; i < DAYS_IN_WEEK; i++)
            weekdayToggle[i] = findViewById(WEEKDAY_TOGGLE_IDs[i]);
        weekdayRow = findViewById(R.id.RepeatRowWeekdays);
        alternateRow = findViewById(R.id.RepeatRowAlternateDirection);
        nearestToggle = findViewById(R.id.RepeatToggleNearest);
        dayteRow = findViewById(R.id.RepeatRowDayDate);
        periodText = findViewById(R.id.RepeatTextPeriod);
        weekdayLabelText = findViewById(R.id.RepeatTextRepeatOn);
        descriptionText = findViewById(R.id.RepeatTextDescription);

        noRepeatText.setVisibility(INVISIBLE);	// May change later
        yesRepeatLayout.setVisibility(VISIBLE);
        intervalGroup.check(R.id.RepeatRadioButtonMonthly);
        resetGroup.check(R.id.RepeatRadioButtonFixedSchedule);
        alternateGroup.check(R.id.RepeatRadioButtonNext);
        dayOrDateGroup.check(R.id.RepeatRadioButtonByDay);

        intervalGroup.setOnCheckedChangeListener(new RepeatRadioChangeListener());
        resetGroup.setOnCheckedChangeListener(new ResetRadioChangeListener());
        incrementEditText.addTextChangedListener(new IncrementTextWatcher());
        endDateButton.setOnClickListener(new EndDateOnClickListener());
        for (int i = 0; i < DAYS_IN_WEEK; i++)
            weekdayToggle[i].setOnCheckedChangeListener(
                    new WeekdayOnCheckedChangeListener(WeekDays.values()[i]));
        nearestToggle.setOnCheckedChangeListener(new AlternateChangeListener());
        alternateGroup.setOnCheckedChangeListener(new AlternateChangeListener());
        dayOrDateGroup.setOnCheckedChangeListener(new DayteRadioChangeListener());
    }

    /**
     * Get the repeat settings object used to populate this widget.
     * This is meant for saving the dialog state if the activity
     * shuts down or is reconfigured.
     *
     * @return the repeat settings object used to populate this widget.
     */
    RepeatSettings getSettings() { return repeatSettings; }

    /** Set the time zone this editor uses to determine date boundaries */
    public void setTimeZone(ZoneId zone) {
        timeZone = zone;
    }

    /**
     * Populate this widget for the given repeat interval.
     *
     * @param repeat the repeat interval to use
     */
    public void setRepeat(RepeatInterval repeat, LocalDate dueDate) {
        isUpdating = true;
        if (repeatSettings != null)
            repeatSettings.removeOnRepeatChangeListener(this);

        repeatSettings = new RepeatSettings(repeat, dueDate);
        updateWidgets();

        // Add callbacks to the settings
        repeatSettings.addOnRepeatChangeListener(this);
        isUpdating = false;
    }

    /**
     * Populate this widget with previously saved settings.
     *
     * @param settings the repeat settings to use
     */
    void restoreSettings(@NonNull RepeatSettings settings) {
        isUpdating = true;
        if (repeatSettings != null)
            repeatSettings.removeOnRepeatChangeListener(this);

        repeatSettings = settings;
        updateWidgets();

        // Add callbacks to the settings
        repeatSettings.addOnRepeatChangeListener(this);
        isUpdating = false;
    }

    private void updateWidgets() {
        switch (repeatSettings.getRepeatType()) {
            case NONE:
                intervalGroup.check(R.id.RepeatRadioButtonNone);
                break;

            case DAILY:
            case DAY_AFTER:
                intervalGroup.check(R.id.RepeatRadioButtonDaily);
                break;

            case WEEKLY:
            case WEEK_AFTER:
                intervalGroup.check(R.id.RepeatRadioButtonWeekly);
                break;

            case SEMI_MONTHLY_ON_DATES:
            case SEMI_MONTHLY_ON_DAYS:
                intervalGroup.check(R.id.RepeatRadioButtonSemiMonthly);
                break;

            case MONTHLY_ON_DATE:
            case MONTHLY_ON_DAY:
            case MONTH_AFTER:
                intervalGroup.check(R.id.RepeatRadioButtonMonthly);
                break;

            case YEARLY_ON_DATE:
            case YEARLY_ON_DAY:
            case YEAR_AFTER:
                intervalGroup.check(R.id.RepeatRadioButtonYearly);
                break;
        }
        switch (repeatSettings.getRepeatType()) {
            case DAILY:
            case WEEKLY:
            case SEMI_MONTHLY_ON_DATES:
            case SEMI_MONTHLY_ON_DAYS:
            case MONTHLY_ON_DATE:
            case MONTHLY_ON_DAY:
            case YEARLY_ON_DATE:
            case YEARLY_ON_DAY:
                resetGroup.check(R.id.RepeatRadioButtonFixedSchedule);
                break;

            case DAY_AFTER:
            case WEEK_AFTER:
            case MONTH_AFTER:
            case YEAR_AFTER:
                resetGroup.check(R.id.RepeatRadioButtonAfterCompleted);
                break;
        }
        switch (repeatSettings.getRepeatType()) {
            case SEMI_MONTHLY_ON_DATES:
            case MONTHLY_ON_DATE:
            case YEARLY_ON_DATE:
                dayOrDateGroup.check(R.id.RepeatRadioButtonByDate);
                break;

            case SEMI_MONTHLY_ON_DAYS:
            case MONTHLY_ON_DAY:
            case YEARLY_ON_DAY:
                dayOrDateGroup.check(R.id.RepeatRadioButtonByDay);
                break;
        }
        switch (repeatSettings.getDirection()) {
            case CLOSEST_OR_NEXT:
                nearestToggle.setChecked(true);
                alternateGroup.check(R.id.RepeatRadioButtonNext);
                break;

            case CLOSEST_OR_PREVIOUS:
                nearestToggle.setChecked(true);
                alternateGroup.check(R.id.RepeatRadioButtonPrevious);
                break;

            case PREVIOUS:
                nearestToggle.setChecked(false);
                alternateGroup.check(R.id.RepeatRadioButtonPrevious);
                break;

            case NEXT:
                nearestToggle.setChecked(false);
                alternateGroup.check(R.id.RepeatRadioButtonNext);
                break;
        }
        incrementEditText.setText(Integer.toString(
                repeatSettings.getIncrement()));
        updateRepeatInterval();
        updateEndDateButton();
        updateRepeatDescription();
    }

    /**
     * Update widgets in the dialog according to a change of the interval type.
     */
    private void updateRepeatInterval() {
        RepeatType type = repeatSettings.getRepeatType();
        boolean enabled = (type != RepeatType.NONE);
        if ((noRepeatText.getVisibility() == INVISIBLE) != enabled) {
            noRepeatText.setVisibility(enabled ? INVISIBLE : VISIBLE);
            yesRepeatLayout.setVisibility(enabled ? VISIBLE : INVISIBLE);
        }

        switch (type) {
            case NONE:
                break;

            case SEMI_MONTHLY_ON_DATES:
            case SEMI_MONTHLY_ON_DAYS:
                if (resetGroup.getVisibility() != GONE)
                    resetGroup.setVisibility(GONE);
                break;

            default:
                if (resetGroup.getVisibility() != VISIBLE)
                    resetGroup.setVisibility(VISIBLE);
                break;
        }

        switch (type) {
            case DAILY:
                periodText.setText(getResources().getString(R.string.RepeatTextDays));
                break;

            case WEEKLY:
            case WEEK_AFTER:
                periodText.setText(getResources().getString(R.string.RepeatTextWeeks));
                break;

            case SEMI_MONTHLY_ON_DATES:
            case SEMI_MONTHLY_ON_DAYS:
            case MONTHLY_ON_DATE:
            case MONTHLY_ON_DAY:
            case MONTH_AFTER:
                periodText.setText(getResources().getString(R.string.RepeatTextMonths));
                break;

            case YEARLY_ON_DATE:
            case YEARLY_ON_DAY:
                periodText.setText(getResources().getString(R.string.RepeatTextYears));
                break;
        }

        switch (type) {
            case NONE:
                break;

            case SEMI_MONTHLY_ON_DAYS:
            case MONTHLY_ON_DAY:
            case YEARLY_ON_DAY:
                if (weekdayRow.getVisibility() != GONE)
                    weekdayRow.setVisibility(GONE);
                break;

            default:
                if (weekdayRow.getVisibility() != VISIBLE)
                    weekdayRow.setVisibility(VISIBLE);
                break;
        }

        switch (type) {
            case NONE:
                break;

            case WEEKLY:
            case SEMI_MONTHLY_ON_DAYS:
            case MONTHLY_ON_DAY:
            case YEARLY_ON_DAY:
                if (alternateRow.getVisibility() != GONE)
                    alternateRow.setVisibility(GONE);
                break;

            default:
                if (alternateRow.getVisibility() != VISIBLE)
                    alternateRow.setVisibility(VISIBLE);
                break;
        }

        switch (type) {
            case DAILY:
            case DAY_AFTER:
            case WEEKLY:
            case WEEK_AFTER:
            case MONTH_AFTER:
            case YEAR_AFTER:
                if (dayteRow.getVisibility() != GONE)
                    dayteRow.setVisibility(GONE);
                break;

            case SEMI_MONTHLY_ON_DAYS:
            case SEMI_MONTHLY_ON_DATES:
            case MONTHLY_ON_DAY:
            case MONTHLY_ON_DATE:
            case YEARLY_ON_DAY:
            case YEARLY_ON_DATE:
                if (dayteRow.getVisibility() != VISIBLE)
                    dayteRow.setVisibility(VISIBLE);
                break;
        }

        int i;
        switch (type) {
            case NONE:
            case SEMI_MONTHLY_ON_DAYS:
            case MONTHLY_ON_DAY:
            case YEARLY_ON_DAY:
                break;

            default:
                weekdayLabelText.setText(getResources().getString(
                        (type == RepeatType.WEEKLY)
                                ?  R.string.RepeatTextRepeatOn
                                : R.string.RepeatTextOnlyOn));
                for (i = 0; i < DAYS_IN_WEEK; i++)
                    weekdayToggle[i].setChecked(repeatSettings.getWeekDays()
                            .contains(WeekDays.values()[i]));
                break;
        }
    }

    /** Update the text of the End Date button */
    private void updateEndDateButton() {
        LocalDate d = repeatSettings.getEndDate();
        if (d == null) {
            endDateButton.setText(R.string.RepeatButtonNoEndDate);
        } else {
            LocalDate today = LocalDate.now();
            long daysFromNow = ChronoUnit.DAYS.between(today, d);
            DateTimeFormatter format;
            // If the end date is within -1 to 2 weeks,
            // format it as "Friday, Jan 31".
            if ((daysFromNow > -7) && (daysFromNow < 14))
                format = DateTimeFormatter.ofPattern("EEEE, MMM d");
            // If the end date is within -2 weeks to a month,
            // format it as "Fri, January 31".
            else if ((daysFromNow > -14) && (daysFromNow < 60))
                format = DateTimeFormatter.ofPattern("EEE, MMMM d");
            // Otherwise format it as "January 31, 2025".
            else
                format = DateTimeFormatter.ofPattern("MMMM d, yyyy");
            endDateButton.setText(d.format(format));
        }
    }

    /**
     * If a given number <i>N</i> is &ge; 0, return a string representing
     * the ordinal of the number; for example: "1st", "2nd", "3rd", "4th", etc.
     * If <i>N</i> &lt; 0, return a string representing "last".
     * (This does not work for "second-to-last", e.g. -2.)
     */
    private String getLastOrdinal(int n) {
        if (n < 0)
            return getResources().getString(R.string.OrdinalLast);
        else
            return getOrdinal(n);
    }

    /**
     * If a given number <i>N</i> is greater than 2, return a string
     * representing the ordinal of the number; for example: "3rd", "4th", etc.
     * For <i>N</i> = 2, return "other" (as in "every other.")
     * For <i>N</i> &le; 1, return an empty string.
     */
    private String getOtherOrdinal(int n) {
        String[] ordinals =
                getResources().getStringArray(R.array.OtherOrdinalList);
        if (n < 1)
            n = 1;
        if (n - 1 < ordinals.length)
            return String.format(ordinals[n-1], n);
        else
            return getOrdinal(n);
    }

    /**
     * For a given number <i>N</i>, return a string representing the
     * ordinal of the number; for example: "1st", "2nd", "3rd", "4th", etc.
     * If the number is &le; 0, returns the same string as "1st".
     */
    private String getOrdinal(int n) {
        String[] ordinals = getResources().getStringArray(R.array.OrdinalList);
        if (n < 1)
            n = 1;
        if (n - 1 < ordinals.length)
            return String.format(ordinals[n-1], n);
        else
            return String.format(ordinals[(n-1) % 10], n);
    }

    /**
     * For a given day of the week (as a {@link WeekDays}),
     * return its string resource
     *
     * @param day the day of the week
     *
     * @return its name from string resources
     */
    private String getWeekdayName(WeekDays day) {
        return weekdayNames[day.getValue() - WeekDays.SUNDAY.getValue()];
    }

    /**
     * For a given month of the year (as a {@link Months}),
     * return its string resource.
     *
     * @param month the month of the year
     *
     * @return its name from string resources
     */
    private String getMonthName(Months month) {
        return monthNames[month.getValue() - Months.JANUARY.getValue()];
    }

    /**
     * Update the text at the bottom area of the dialog
     * which shows a human-readable description of the
     * chosen repeat settings.
     */
    private void updateRepeatDescription() {
        int incr = repeatSettings.getIncrement();
        StringBuilder sb = new StringBuilder();

        // First step: the basic days/dates and increment */
        switch (repeatSettings.getRepeatType()) {
            case NONE:
                sb.append(getResources().getString(
                        R.string.RepeatDescriptionNone));
                break;

            case DAILY:
                sb.append(getResources().getQuantityString(
                        R.plurals.RepeatDescriptionDaily, incr,
                        getOtherOrdinal(incr)));
                break;

            case DAY_AFTER:
                sb.append(getResources().getQuantityString(
                        R.plurals.RepeatDescriptionDayAfter, incr, incr));
                break;

            case WEEKLY:
                sb.append(getResources().getQuantityString(
                        R.plurals.RepeatDescriptionWeekly, incr,
                        getOtherOrdinal(incr)));
                break;

            case WEEK_AFTER:
                sb.append(getResources().getQuantityString(
                        R.plurals.RepeatDescriptionWeekAfter, incr, incr));
                break;

            case SEMI_MONTHLY_ON_DAYS:
                sb.append(getResources().getQuantityString(
                        R.plurals.RepeatDescriptionSemiMonthlyOnDays, incr,
                        getLastOrdinal(repeatSettings.getWeek(0)),
                        getWeekdayName(repeatSettings.getDayOfWeek(0)),
                        getLastOrdinal(repeatSettings.getWeek(1)),
                        getWeekdayName(repeatSettings.getDayOfWeek(1)),
                        getOtherOrdinal(incr)));
                break;

            case SEMI_MONTHLY_ON_DATES:
                sb.append(getResources().getQuantityString(
                        R.plurals.RepeatDescriptionSemiMonthlyOnDates, incr,
                        getOrdinal(repeatSettings.getDate(0)),
                        getOrdinal(repeatSettings.getDate(1)),
                        getOtherOrdinal(incr)));
                break;

            case MONTHLY_ON_DAY:
                sb.append(getResources().getQuantityString(
                        R.plurals.RepeatDescriptionMonthlyOnDay, incr,
                        getLastOrdinal(repeatSettings.getWeek(0)),
                        getWeekdayName(repeatSettings.getDayOfWeek(0)),
                        getOtherOrdinal(incr)));
                break;

            case MONTHLY_ON_DATE:
                sb.append(getResources().getQuantityString(
                        R.plurals.RepeatDescriptionMonthlyOnDate, incr,
                        getOrdinal(repeatSettings.getDate(0)),
                        getOtherOrdinal(incr)));
                break;

            case MONTH_AFTER:
                sb.append(getResources().getQuantityString(
                        R.plurals.RepeatDescriptionMonthAfter, incr, incr));
                break;

            case YEARLY_ON_DAY:
                sb.append(getResources().getQuantityString(
                        R.plurals.RepeatDescriptionYearlyOnDay, incr,
                        getLastOrdinal(repeatSettings.getWeek(0)),
                        getWeekdayName(repeatSettings.getDayOfWeek(0)),
                        getMonthName(repeatSettings.getMonth()),
                        getOtherOrdinal(incr)));
                break;

            case YEARLY_ON_DATE:
                sb.append(getResources().getQuantityString(
                        R.plurals.RepeatDescriptionYearlyOnDate, incr,
                        getMonthName(repeatSettings.getMonth()),
                        getOrdinal(repeatSettings.getDate(0)),
                        getOtherOrdinal(incr)));
                break;

            case YEAR_AFTER:
                sb.append(getResources().getQuantityString(
                        R.plurals.RepeatDescriptionYearAfter, incr, incr));
                break;
        }

        // Second step: the fixed or allowed days
        SortedSet<WeekDays> days;
        switch (repeatSettings.getRepeatType()) {
            case NONE:
            case SEMI_MONTHLY_ON_DAYS:
            case MONTHLY_ON_DAY:
            case YEARLY_ON_DAY:
                break;

            case WEEKLY:
                days = repeatSettings.getWeekDays();
                switch (days.size()) {
                    case 1:
                        sb.append(getResources().getString(
                                R.string.RepeatDescriptionOnDay,
                                getWeekdayName(days.first())));
                        break;

                    case 2:
                        sb.append(getResources().getString(
                                R.string.RepeatDescriptionOn2Days,
                                getWeekdayName(days.first()),
                                getWeekdayName(days.last())));
                        break;

                    case 3:
                    case 4:
                    case 5:
                    case 6:
                    case 7:
                        StringBuffer sb2 = new StringBuffer();
                        for (WeekDays d : days) {
                            if (d == days.last())
                                break;
                            sb2.append(getWeekdayName(d));
                            sb2.append(", ");
                        }
                        sb2.deleteCharAt(sb2.length() - 1);
                        sb.append(getResources().getString(
                                R.string.RepeatDescriptionOn2Days,
                                sb2.toString(),
                                getWeekdayName(days.last())));
                        break;
                }
                break;

            default:
                days = repeatSettings.getWeekDays();
                switch (days.size()) {
                    case 1:
                        sb.append(getResources().getString(
                                R.string.RepeatDescriptionOnDay,
                                getWeekdayName(days.first())));
                        break;

                    case 2:
                        sb.append(getResources().getString(
                                R.string.RepeatDescriptionOn2DaysAllowed,
                                getWeekdayName(days.first()),
                                getWeekdayName(days.last())));
                        break;

                    case 3:
                    case 4:
                    case 5:
                    case 6:
                        StringBuffer sb2 = new StringBuffer();
                        for (WeekDays d : days) {
                            if (d == days.last())
                                break;
                            sb2.append(getWeekdayName(d));
                            sb2.append(", ");
                        }
                        sb2.deleteCharAt(sb2.length() - 1);
                        sb.append(getResources().getString(
                                R.string.RepeatDescriptionOn2DaysAllowed,
                                sb2.toString(),
                                getWeekdayName(days.last())));
                        break;
                }
                break;
        }
        descriptionText.setText(sb.toString());
    }

    /** This callback handles switching the basic interval type. */
    class RepeatRadioChangeListener implements RadioGroup.OnCheckedChangeListener {
        /** Called when the radio button has changed. */
        @Override
        public void onCheckedChanged(RadioGroup rg, int id) {
            if (isUpdating)
                return;
            if (id == R.id.RepeatRadioButtonNone) {
                repeatSettings.setRepeatType(RepeatType.NONE);
            }

            else if (id == R.id.RepeatRadioButtonDaily) {
                repeatSettings.setRepeatType(
                        (resetGroup.getCheckedRadioButtonId() ==
                                R.id.RepeatRadioButtonAfterCompleted)
                                ? RepeatType.DAY_AFTER : RepeatType.DAILY);
            }

            else if (id == R.id.RepeatRadioButtonWeekly) {
                repeatSettings.setRepeatType(
                        (resetGroup.getCheckedRadioButtonId() ==
                                R.id.RepeatRadioButtonAfterCompleted)
                                ? RepeatType.WEEK_AFTER : RepeatType.WEEKLY);
            }

            else if (id == R.id.RepeatRadioButtonSemiMonthly) {
                repeatSettings.setRepeatType(
                        (dayOrDateGroup.getCheckedRadioButtonId() ==
                                R.id.RepeatRadioButtonByDate)
                                ? RepeatType.SEMI_MONTHLY_ON_DATES
                                : RepeatType.SEMI_MONTHLY_ON_DAYS);
            }

            else if (id == R.id.RepeatRadioButtonMonthly) {
                repeatSettings.setRepeatType(
                        (resetGroup.getCheckedRadioButtonId() ==
                                R.id.RepeatRadioButtonAfterCompleted)
                                ? RepeatType.MONTH_AFTER
                                : (dayOrDateGroup.getCheckedRadioButtonId() ==
                                R.id.RepeatRadioButtonByDate)
                                ? RepeatType.MONTHLY_ON_DATE
                                : RepeatType.MONTHLY_ON_DAY);
            }

            else if (id == R.id.RepeatRadioButtonYearly) {
                repeatSettings.setRepeatType(
                        (resetGroup.getCheckedRadioButtonId() ==
                                R.id.RepeatRadioButtonAfterCompleted)
                                ? RepeatType.YEAR_AFTER
                                : (dayOrDateGroup.getCheckedRadioButtonId() ==
                                R.id.RepeatRadioButtonByDate)
                                ? RepeatType.YEARLY_ON_DATE
                                : RepeatType.YEARLY_ON_DAY);
            }

            else if (id == -1) {	// This shouldn't happen
                Log.e(LOG_TAG, "No radio button selected in repeat group!");
                return;
            }

            else {	// This shouldn't happen either
                Log.e(LOG_TAG, "Unknown radio button selected in repeat group!");
                return;
            }

            updateRepeatInterval();
            updateRepeatDescription();
        }
    }

    /** This callback handles switching the reset type. */
    class ResetRadioChangeListener implements RadioGroup.OnCheckedChangeListener {
        /** Called when the radio button has changed. */
        @Override
        public void onCheckedChanged(RadioGroup rg, int id) {
            if (isUpdating)
                return;
            switch (repeatSettings.getRepeatType()) {
                case NONE:	// This shouldn't happen
                    Log.e(LOG_TAG, "Radio button selected in the reset group"
                            + " when no repeat interval is selected");
                    return;

                case DAILY:
                    if (id == R.id.RepeatRadioButtonAfterCompleted)
                        repeatSettings.setRepeatType(RepeatType.DAY_AFTER);
                    else
                        return;
                    break;

                case DAY_AFTER:
                    if (id == R.id.RepeatRadioButtonFixedSchedule)
                        repeatSettings.setRepeatType(RepeatType.DAILY);
                    else
                        return;
                    break;

                case WEEKLY:
                    if (id == R.id.RepeatRadioButtonAfterCompleted)
                        repeatSettings.setRepeatType(RepeatType.WEEK_AFTER);
                    else
                        return;
                    break;

                case WEEK_AFTER:
                    if (id == R.id.RepeatRadioButtonFixedSchedule)
                        repeatSettings.setRepeatType(RepeatType.WEEKLY);
                    else
                        return;
                    break;

                case SEMI_MONTHLY_ON_DATES:	// Neither of these should happen
                case SEMI_MONTHLY_ON_DAYS:
                    Log.e(LOG_TAG, "Radio button selected in reset group"
                            + " when semi-monthly repeat interval is selected");
                    return;

                case MONTHLY_ON_DATE:
                case MONTHLY_ON_DAY:
                    if (id == R.id.RepeatRadioButtonAfterCompleted)
                        repeatSettings.setRepeatType(RepeatType.MONTH_AFTER);
                    else
                        return;
                    break;

                case MONTH_AFTER:
                    if (id == R.id.RepeatRadioButtonFixedSchedule)
                        repeatSettings.setRepeatType(
                                dayOrDateGroup.getCheckedRadioButtonId() ==
                                        R.id.RepeatRadioButtonByDate
                                        ? RepeatType.MONTHLY_ON_DATE
                                        : RepeatType.MONTHLY_ON_DAY);
                    else
                        return;
                    break;

                case YEARLY_ON_DATE:
                case YEARLY_ON_DAY:
                    if (id == R.id.RepeatRadioButtonAfterCompleted)
                        repeatSettings.setRepeatType(RepeatType.YEAR_AFTER);
                    else
                        return;
                    break;

                case YEAR_AFTER:
                    if (id == R.id.RepeatRadioButtonFixedSchedule)
                        repeatSettings.setRepeatType(
                                dayOrDateGroup.getCheckedRadioButtonId() ==
                                        R.id.RepeatRadioButtonByDate
                                        ? RepeatType.YEARLY_ON_DATE
                                        : RepeatType.YEARLY_ON_DAY);
                    else
                        return;
                    break;
            }
            updateRepeatInterval();
            updateRepeatDescription();
        }
    }

    /** This callback just listens for changes to the repeat increment */
    class IncrementTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s,
                                      int start, int count, int after) {
        }
        @Override
        public void onTextChanged(CharSequence s,
                                  int start, int before, int count) {
        }
        @Override
        public void afterTextChanged(Editable s) {
            if (isUpdating)
                return;
            int n = 1;
            if (s.length() > 0) {
                try {
                    n = Integer.parseInt(s.toString());
                } catch (NumberFormatException nfx) {
                    return;
                }
            }
            repeatSettings.setIncrement(n);
            updateRepeatDescription();
        }
    }

    /**
     * This callback handles setting the end date.
     * To do: This needs to be a drop-down list that includes "No Date".
     */
    class EndDateOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            LocalDate d = repeatSettings.getEndDate();
            if (endDateDialog == null) {
                endDateDialog = new CalendarDatePickerDialog(v.getContext(),
                        v.getResources().getString(
                                R.string.DatePickerTitleEndingOn),
                        new EndDateOnDateSetListener());
            }
            LocalDate today = LocalDate.now(timeZone);
            endDateDialog.setToday(today);
            endDateDialog.setDate((d == null) ? today : d);
            endDateDialog.setTimeZone(timeZone);
            endDateDialog.show();
        }
    }

    class EndDateOnDateSetListener
            implements CalendarDatePicker.OnDateSetListener {
        @Override
        public void onDateSet(CalendarDatePicker picker,
                              LocalDate date) {
            repeatSettings.setEndDate(date);
            //    updateEndDateButton();
        }
    }

    /** This callback handles the weekday toggles. */
    class WeekdayOnCheckedChangeListener
            implements CompoundButton.OnCheckedChangeListener {
        /** The day of the week which this particular toggle sets */
        final WeekDays weekday;

        public WeekdayOnCheckedChangeListener(WeekDays weekday) {
            this.weekday = weekday;
        }

        @Override
        public void onCheckedChanged(CompoundButton button, boolean state) {
            if (isUpdating)
                return;
            switch (repeatSettings.getRepeatType()) {
                case NONE:	// These should not happen
                case SEMI_MONTHLY_ON_DAYS:
                case MONTHLY_ON_DAY:
                case YEARLY_ON_DAY:
                    Log.e(LOG_TAG, String.format(Locale.US,
                            "Weekday toggle selected when the repeat interval is %s",
                            repeatSettings.getRepeatType()));
                    return;

                case DAILY:
                case DAY_AFTER:
                case WEEKLY:
                case WEEK_AFTER:
                case SEMI_MONTHLY_ON_DATES:
                case MONTHLY_ON_DATE:
                case MONTH_AFTER:
                case YEARLY_ON_DATE:
                case YEAR_AFTER:
                    repeatSettings.setWeekdayMember(weekday, state);
                    break;
            }
            updateRepeatDescription();
        }
    }

    /** This callback handles changing the direction of alternate dates */
    class AlternateChangeListener
            implements CompoundButton.OnCheckedChangeListener,
            RadioGroup.OnCheckedChangeListener {

        /** Called when changing between nearest and absolute */
        @Override
        public void onCheckedChanged(CompoundButton button, boolean state) {
            if (isUpdating)
                return;
            setWeekdayDirection(state, alternateGroup.getCheckedRadioButtonId());
        }

        /** Called when changing between previous and next */
        @Override
        public void onCheckedChanged(RadioGroup rg, int id) {
            if (isUpdating)
                return;
            setWeekdayDirection(nearestToggle.isChecked(), id);
        }

        /** Use the combined settings to choose the direction */
        private void setWeekdayDirection(boolean nearest, int directionID) {
            if (directionID == R.id.RepeatRadioButtonNext) {
                repeatSettings.setDirection(nearest
                        ? WeekdayDirection.CLOSEST_OR_NEXT
                        : WeekdayDirection.NEXT);
            }

            else if (directionID == R.id.RepeatRadioButtonPrevious) {
                repeatSettings.setDirection(nearest
                        ? WeekdayDirection.CLOSEST_OR_PREVIOUS
                        : WeekdayDirection.PREVIOUS);
            }
        }
    }

    /** This callback handles switching between by day or by date. */
    class DayteRadioChangeListener implements RadioGroup.OnCheckedChangeListener {
        /** Called when the radio button has changed. */
        @Override
        public void onCheckedChanged(RadioGroup rg, int id) {
            if (isUpdating)
                return;
            switch (repeatSettings.getRepeatType()) {
                case NONE:	// This shouldn't happen
                    Log.e(LOG_TAG, "Radio button selected in the reset group"
                            + " when no repeat interval is selected");
                    return;

                case DAILY:	// These shouldn't happen either
                case DAY_AFTER:
                case WEEKLY:
                case WEEK_AFTER:
                case MONTH_AFTER:
                case YEAR_AFTER:
                    Log.e(LOG_TAG, "Radio button selected in the reset group when "
                            + repeatSettings.getRepeatType()
                            + " repeat interval is selected");
                    return;

                case SEMI_MONTHLY_ON_DATES:
                    if (id == R.id.RepeatRadioButtonByDay)
                        repeatSettings.setRepeatType(
                                RepeatType.SEMI_MONTHLY_ON_DAYS);
                    else
                        return;
                    break;

                case SEMI_MONTHLY_ON_DAYS:
                    if (id == R.id.RepeatRadioButtonByDate)
                        repeatSettings.setRepeatType(
                                RepeatType.SEMI_MONTHLY_ON_DATES);
                    else
                        return;
                    break;

                case MONTHLY_ON_DATE:
                    if (id == R.id.RepeatRadioButtonByDay)
                        repeatSettings.setRepeatType(
                                RepeatType.MONTHLY_ON_DAY);
                    else
                        return;
                    break;

                case MONTHLY_ON_DAY:
                    if (id == R.id.RepeatRadioButtonByDate)
                        repeatSettings.setRepeatType(
                                RepeatType.MONTHLY_ON_DATE);
                    else
                        return;
                    break;

                case YEARLY_ON_DATE:
                    if (id == R.id.RepeatRadioButtonByDay)
                        repeatSettings.setRepeatType(
                                RepeatType.YEARLY_ON_DAY);
                    else
                        return;
                    break;

                case YEARLY_ON_DAY:
                    if (id == R.id.RepeatRadioButtonByDate)
                        repeatSettings.setRepeatType(
                                RepeatType.YEARLY_ON_DATE);
                    else
                        return;
                    break;
            }
            updateRepeatInterval();
            updateRepeatDescription();
        }
    }

    @Override
    public void onDueChanged(RepeatSettings settings, LocalDate newDue) {
        // This change doesn't affect anything else
    }

    // Callbacks for automatic changes to the repeat settings
    public void onTypeChanged(RepeatSettings settings, RepeatType newType) {
        updateRepeatInterval();
        updateRepeatDescription();
    }

    public void onIncrementChanged(RepeatSettings settings, int newIncrement) {
        updateRepeatDescription();
    }

    public void onWeekdaysChanged(
            RepeatSettings settings,
            Set<WeekDays> additions, Set<WeekDays> removals) {
        for (WeekDays day : additions) {
            int index = day.getValue() - WeekDays.SUNDAY.getValue();
            if (!weekdayToggle[index].isChecked())
                weekdayToggle[index].setChecked(true);
        }
        for (WeekDays day : removals) {
            int index = day.getValue() - WeekDays.SUNDAY.getValue();
            if (weekdayToggle[index].isChecked())
                weekdayToggle[index].setChecked(false);
        }
        updateRepeatDescription();
    }

    @Override
    public void onWeekdayDirectionChanged(
            RepeatSettings settings, WeekdayDirection newDirection) {
        // This change doesn't impact anything else
    }

    public void onDayOfWeekChanged(
            RepeatSettings settings, int index, @NonNull WeekDays newDay) {
        updateRepeatDescription();
    }

    public void onWeekChanged(
            RepeatSettings settings, int index, int newWeek) {
        updateRepeatDescription();
    }

    public void onDateChanged(
            RepeatSettings settings, int index, int newDate) {
        updateRepeatDescription();
    }

    public void onMonthChanged(
            RepeatSettings settings, @NonNull Months newMonth) {
        updateRepeatDescription();
    }

    @Override
    public void onEndDateChanged(
            RepeatSettings settings, @Nullable LocalDate newEndDate) {
        updateEndDateButton();
    }

}
