/*
 * $Id: RepeatEditor.java,v 1.2 2011/07/18 00:46:45 trevin Exp trevin $
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
 * $Log: RepeatEditor.java,v $
 * Revision 1.2  2011/07/18 00:46:45  trevin
 * Added the copyright notice
 *
 * Revision 1.1  2011/02/28 01:53:52  trevin
 * Initial revision
 *
 */
package com.xmission.trevin.android.todo;

import static com.xmission.trevin.android.todo.ToDo.ToDoItem.*;

import java.text.SimpleDateFormat;
import java.util.*;

import com.xmission.trevin.android.todo.RepeatSettings.IntervalType;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.*;
import android.widget.*;

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
    private ToggleButton[] weekdayToggle =
	new ToggleButton[WEEKDAY_TOGGLE_IDs.length];
    private ToggleButton nearestToggle = null;

    private String[] monthNames;
    private String[] weekdayNames;

    /**
     * Create a new repeat editor set to the current date
     */
    public RepeatEditor(Context context) {
	this(context, null);
    }

    /**
     * Create a new repeat editor set to the current date
     */
    public RepeatEditor(Context context, AttributeSet attrs) {
	this(context, attrs, 0);
    }

    /**
     * Create a new repeat editor set to the current date
     */
    public RepeatEditor(Context context, AttributeSet attrs, int defStyle) {
	super(context, attrs, defStyle);

	Log.d(LOG_TAG, "creating");

	monthNames = getResources().getStringArray(R.array.MonthList);
	weekdayNames = getResources().getStringArray(R.array.WeekdayList);

	LayoutInflater inflater = (LayoutInflater)
		context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	inflater.inflate(R.layout.repeat, this, true);

	noRepeatText = (TextView) findViewById(R.id.RepeatTextNone);
	yesRepeatLayout = (ViewGroup) findViewById(R.id.RepeatLayout);
	intervalGroup =
	    (RadioGroup) findViewById(R.id.RepeatRadioGroupInterval);
	resetGroup = (RadioGroup) findViewById(R.id.RepeatRadioGroupReset);
	alternateGroup =
	    (RadioGroup) findViewById(R.id.RepeatRadioGroupAlternateDirection);
	dayOrDateGroup = (RadioGroup) findViewById(R.id.RepeatRadioGroupDayte);
	incrementEditText = (EditText) findViewById(R.id.RepeatEditTextEvery);
	endDateButton = (Button) findViewById(R.id.RepeatButtonEndDate);
	for (int i = 0; i < DAYS_IN_WEEK; i++)
	    weekdayToggle[i] = (ToggleButton) findViewById(WEEKDAY_TOGGLE_IDs[i]);
	weekdayRow = (TableRow) findViewById(R.id.RepeatRowWeekdays);
	alternateRow = (TableRow) findViewById(R.id.RepeatRowAlternateDirection);
	nearestToggle = (ToggleButton) findViewById(R.id.RepeatToggleNearest);
	dayteRow = (TableRow) findViewById(R.id.RepeatRowDayDate);
	periodText = (TextView) findViewById(R.id.RepeatTextPeriod);
	weekdayLabelText = (TextView) findViewById(R.id.RepeatTextRepeatOn);
	descriptionText = (TextView) findViewById(R.id.RepeatTextDescription);

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
		    new WeekdayOnCheckedChangeListener(i + Calendar.SUNDAY));
	nearestToggle.setOnCheckedChangeListener(new AlternateChangeListener());
	alternateGroup.setOnCheckedChangeListener(new AlternateChangeListener());
	dayOrDateGroup.setOnCheckedChangeListener(new DayteRadioChangeListener());
    }

    /** @return the repeat settings object used to populate this widget */
    public RepeatSettings getRepeatSettings() { return repeatSettings; }

    /** Set the repeat settings object used to populate this widget */
    public void setRepeatSettings(RepeatSettings settings) {
	isUpdating = true;
	repeatSettings = settings.clone();
	// Update the widgets
	switch (settings.getIntervalType()) {
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
	switch (settings.getIntervalType()) {
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
	switch (settings.getIntervalType()) {
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
	switch (settings.getWeekdayDirection()) {
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
	incrementEditText.setText(Integer.toString(settings.getIncrement()));
	updateRepeatInterval();
	updateEndDateButton();
	updateRepeatDescription();

	// Add callbacks to the settings
	repeatSettings.addOnRepeatChangeListener(this);
	isUpdating = false;
    }

    /**
     * Update widgets in the dialog according to a change of the interval type.
     */
    private void updateRepeatInterval() {
	IntervalType type = repeatSettings.getIntervalType();
	boolean enabled = (type != IntervalType.NONE);
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

	default:
	    if (weekdayRow.getVisibility() != VISIBLE)
		weekdayRow.setVisibility(VISIBLE);
	    break;

	case SEMI_MONTHLY_ON_DAYS:
	case MONTHLY_ON_DAY:
	case YEARLY_ON_DAY:
	    if (weekdayRow.getVisibility() != GONE)
		weekdayRow.setVisibility(GONE);
	    break;
	}

	switch (type) {
	case NONE:
	    break;

	default:
	    if (alternateRow.getVisibility() != VISIBLE)
		alternateRow.setVisibility(VISIBLE);
	    break;

	case WEEKLY:
	case SEMI_MONTHLY_ON_DAYS:
	case MONTHLY_ON_DAY:
	case YEARLY_ON_DAY:
	    if (alternateRow.getVisibility() != GONE)
		alternateRow.setVisibility(GONE);
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
	    weekdayLabelText.setText(
		    getResources().getString(R.string.RepeatTextOnlyOn));
	    for (i = 0; i < DAYS_IN_WEEK; i++)
		weekdayToggle[i].setChecked(
			repeatSettings.getAllowedWeekDays().contains(
				i + Calendar.SUNDAY));
	    break;

	case WEEKLY:
	    weekdayLabelText.setText(
		    getResources().getString(R.string.RepeatTextRepeatOn));
	    for (i = 0; i < DAYS_IN_WEEK; i++)
		weekdayToggle[i].setChecked(
			repeatSettings.getFixedWeekDays().contains(
				i + Calendar.SUNDAY));
	    break;
	}
    }

    /** Update the text of the End Date button */
    private void updateEndDateButton() {
	Date d = repeatSettings.getEndDate();
	if (d == null) {
	    endDateButton.setText(R.string.RepeatButtonNoEndDate);
	} else {
	    Calendar c = Calendar.getInstance();
	    c.setTime(d);
	    SimpleDateFormat sdf = null;
	    long deltaT = d.getTime() - System.currentTimeMillis();
	    final long MS_PER_DAY = 24 * 60 * 60 * 1000;
	    if ((deltaT > -7 * MS_PER_DAY) && (deltaT < 14 * MS_PER_DAY))
		sdf = new SimpleDateFormat("EEEE, MMM d");
	    else if ((deltaT > -14 * MS_PER_DAY) && (deltaT < 60 * MS_PER_DAY))
		sdf = new SimpleDateFormat("EEE, MMMM d");
	    else
		sdf = new SimpleDateFormat("MMMM d, yyyy");
	    endDateButton.setText(sdf.format(d));
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
     * Update the text at the bottom area of the dialog
     * which shows a human-readable description of the
     * chosen repeat settings.
     */
    private void updateRepeatDescription() {
	int incr = repeatSettings.getIncrement();
	StringBuilder sb = new StringBuilder();

	// First step: the basic days/dates and increment */
	switch (repeatSettings.getIntervalType()) {
	case NONE:
	    sb.append(getResources().getString(R.string.RepeatDescriptionNone));
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
		    weekdayNames[repeatSettings.getDayOfWeek(0) - Calendar.SUNDAY],
		    getLastOrdinal(repeatSettings.getWeek(1)),
		    weekdayNames[repeatSettings.getDayOfWeek(1) - Calendar.SUNDAY],
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
		    getLastOrdinal(repeatSettings.getWeek()),
		    weekdayNames[repeatSettings.getDayOfWeek() - Calendar.SUNDAY],
		    getOtherOrdinal(incr)));
	    break;

	case MONTHLY_ON_DATE:
	    sb.append(getResources().getQuantityString(
		    R.plurals.RepeatDescriptionMonthlyOnDate, incr,
		    getOrdinal(repeatSettings.getDate()),
		    getOtherOrdinal(incr)));
	    break;

	case MONTH_AFTER:
	    sb.append(getResources().getQuantityString(
		    R.plurals.RepeatDescriptionMonthAfter, incr, incr));
	    break;

	case YEARLY_ON_DAY:
	    sb.append(getResources().getQuantityString(
		   R.plurals.RepeatDescriptionYearlyOnDay, incr,
		   getLastOrdinal(repeatSettings.getWeek()),
		   weekdayNames[repeatSettings.getDayOfWeek() - Calendar.SUNDAY],
		   monthNames[repeatSettings.getMonth() - Calendar.JANUARY],
		   getOtherOrdinal(incr)));
	    break;

	case YEARLY_ON_DATE:
	    sb.append(getResources().getQuantityString(
		    R.plurals.RepeatDescriptionYearlyOnDate, incr,
		    monthNames[repeatSettings.getMonth() - Calendar.JANUARY],
		    getOrdinal(repeatSettings.getDate()),
		    getOtherOrdinal(incr)));
	    break;

	case YEAR_AFTER:
	    sb.append(getResources().getQuantityString(
		    R.plurals.RepeatDescriptionYearAfter, incr, incr));
	    break;
	}

	// Second step: the fixed or allowed days
	switch (repeatSettings.getIntervalType()) {
	case NONE:
	case SEMI_MONTHLY_ON_DAYS:
	case MONTHLY_ON_DAY:
	case YEARLY_ON_DAY:
	    break;

	default:
	    SortedSet<Integer> days = repeatSettings.getAllowedWeekDays();
	    switch (days.size()) {
	    case 1:
		sb.append(getResources().getString(
			R.string.RepeatDescriptionOnDay,
			weekdayNames[days.first() - Calendar.SUNDAY]));
		break;

	    case 2:
		sb.append(getResources().getString(
			R.string.RepeatDescriptionOn2DaysAllowed,
			weekdayNames[days.first() - Calendar.SUNDAY],
			weekdayNames[days.last() - Calendar.SUNDAY]));
		break;

	    case 3:
	    case 4:
	    case 5:
	    case 6:
		StringBuffer sb2 = new StringBuffer();
		for (int d : days) {
		    if (d == days.last())
			break;
		    sb2.append(weekdayNames[d - Calendar.SUNDAY]);
		    sb2.append(", ");
		}
		sb2.deleteCharAt(sb2.length() - 1);
		sb.append(getResources().getString(
			R.string.RepeatDescriptionOn2DaysAllowed, sb2.toString(),
			weekdayNames[days.last() - Calendar.SUNDAY]));
		break;
	    }
	    break;

	case WEEKLY:
	    days = repeatSettings.getFixedWeekDays();
	    switch (days.size()) {
	    case 1:
		sb.append(getResources().getString(
			R.string.RepeatDescriptionOnDay,
			weekdayNames[days.first() - Calendar.SUNDAY]));
		break;

	    case 2:
		sb.append(getResources().getString(
			R.string.RepeatDescriptionOn2Days,
			weekdayNames[days.first() - Calendar.SUNDAY],
			weekdayNames[days.last() - Calendar.SUNDAY]));
		break;

	    case 3:
	    case 4:
	    case 5:
	    case 6:
	    case 7:
		StringBuffer sb2 = new StringBuffer();
		for (int d : days) {
		    if (d == days.last())
			break;
		    sb2.append(weekdayNames[d - Calendar.SUNDAY]);
		    sb2.append(", ");
		}
		sb2.deleteCharAt(sb2.length() - 1);
		sb.append(getResources().getString(
			R.string.RepeatDescriptionOn2Days, sb2.toString(),
			weekdayNames[days.last() - Calendar.SUNDAY]));
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
	    switch (id) {
	    case -1:	// This shouldn't happen
		Log.e(LOG_TAG, "No radio button selected in repeat group!");
		return;

	    default:	// This shouldn't happen either
		Log.e(LOG_TAG, "Unknown radio button selected in repeat group!");
		return;

	    case R.id.RepeatRadioButtonNone:
		repeatSettings.setIntervalType(IntervalType.NONE);
		break;

	    case R.id.RepeatRadioButtonDaily:
		repeatSettings.setIntervalType(
			(resetGroup.getCheckedRadioButtonId() ==
			    R.id.RepeatRadioButtonAfterCompleted)
			    ? IntervalType.DAY_AFTER : IntervalType.DAILY);
		break;

	    case R.id.RepeatRadioButtonWeekly:
		repeatSettings.setIntervalType(
			(resetGroup.getCheckedRadioButtonId() ==
			    R.id.RepeatRadioButtonAfterCompleted)
			    ? IntervalType.WEEK_AFTER : IntervalType.WEEKLY);
		break;

	    case R.id.RepeatRadioButtonSemiMonthly:
		repeatSettings.setIntervalType(
			(dayOrDateGroup.getCheckedRadioButtonId() ==
			    R.id.RepeatRadioButtonByDate)
			    ? IntervalType.SEMI_MONTHLY_ON_DATES
			    : IntervalType.SEMI_MONTHLY_ON_DAYS);
		break;

	    case R.id.RepeatRadioButtonMonthly:
		repeatSettings.setIntervalType(
			(resetGroup.getCheckedRadioButtonId() ==
			    R.id.RepeatRadioButtonAfterCompleted)
			    ? IntervalType.MONTH_AFTER
			    : (dayOrDateGroup.getCheckedRadioButtonId() ==
				R.id.RepeatRadioButtonByDate)
				? IntervalType.MONTHLY_ON_DATE
				: IntervalType.MONTHLY_ON_DAY);
		break;

	    case R.id.RepeatRadioButtonYearly:
		repeatSettings.setIntervalType(
			(resetGroup.getCheckedRadioButtonId() ==
			    R.id.RepeatRadioButtonAfterCompleted)
			    ? IntervalType.YEAR_AFTER
			    : (dayOrDateGroup.getCheckedRadioButtonId() ==
				R.id.RepeatRadioButtonByDate)
				? IntervalType.YEARLY_ON_DATE
				: IntervalType.YEARLY_ON_DAY);
		break;
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
	    switch (repeatSettings.getIntervalType().value) {
	    case REPEAT_NONE:	// This shouldn't happen
		Log.e(LOG_TAG, "Radio button selected in the reset group"
			+ " when no repeat interval is selected");
		return;

	    case REPEAT_DAILY:
		if (id == R.id.RepeatRadioButtonAfterCompleted)
		    repeatSettings.setIntervalType(IntervalType.DAY_AFTER);
		else
		    return;
		break;

	    case REPEAT_DAY_AFTER:
		if (id == R.id.RepeatRadioButtonFixedSchedule)
		    repeatSettings.setIntervalType(IntervalType.DAILY);
		else
		    return;
		break;

	    case REPEAT_WEEKLY:
		if (id == R.id.RepeatRadioButtonAfterCompleted)
		    repeatSettings.setIntervalType(IntervalType.WEEK_AFTER);
		else
		    return;
		break;

	    case REPEAT_WEEK_AFTER:
		if (id == R.id.RepeatRadioButtonFixedSchedule)
		    repeatSettings.setIntervalType(IntervalType.WEEKLY);
		else
		    return;
		break;

	    case REPEAT_SEMI_MONTHLY_ON_DATES:	// Neither of these should happen
	    case REPEAT_SEMI_MONTHLY_ON_DAYS:
		Log.e(LOG_TAG, "Radio button selected in reset group"
			+ " when semi-monthly repeat interval is selected");
		return;

	    case REPEAT_MONTHLY_ON_DATE:
	    case REPEAT_MONTHLY_ON_DAY:
		if (id == R.id.RepeatRadioButtonAfterCompleted)
		    repeatSettings.setIntervalType(IntervalType.MONTH_AFTER);
		else
		    return;
		break;

	    case REPEAT_MONTH_AFTER:
		if (id == R.id.RepeatRadioButtonFixedSchedule)
		    repeatSettings.setIntervalType(
			    dayOrDateGroup.getCheckedRadioButtonId() ==
				R.id.RepeatRadioButtonByDate
				? IntervalType.MONTHLY_ON_DATE
				: IntervalType.MONTHLY_ON_DAY);
		else
		    return;
		break;

	    case REPEAT_YEARLY_ON_DATE:
	    case REPEAT_YEARLY_ON_DAY:
		if (id == R.id.RepeatRadioButtonAfterCompleted)
		    repeatSettings.setIntervalType(IntervalType.YEAR_AFTER);
		else
		    return;
		break;

	    case REPEAT_YEAR_AFTER:
		if (id == R.id.RepeatRadioButtonFixedSchedule)
		    repeatSettings.setIntervalType(
			    dayOrDateGroup.getCheckedRadioButtonId() ==
				R.id.RepeatRadioButtonByDate
				? IntervalType.YEARLY_ON_DATE
				: IntervalType.YEARLY_ON_DAY);
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
	    Calendar c = Calendar.getInstance();
	    Date d = repeatSettings.getEndDate();
	    if (d != null)
		c.setTime(d);
	    if (endDateDialog == null) {
		endDateDialog = new CalendarDatePickerDialog(v.getContext(),
			v.getResources().getString(R.string.DatePickerTitleEndingOn),
			new EndDateOnDateSetListener(),
			c.get(Calendar.YEAR), c.get(Calendar.MONTH),
			c.get(Calendar.DATE));
	    }
	    endDateDialog.show();
	}
    }

    class EndDateOnDateSetListener
	implements CalendarDatePickerDialog.OnDateSetListener {
	@Override
	public void onDateSet(CalendarDatePicker picker,
		int year, int month, int date) {
	    Calendar c = Calendar.getInstance();
	    c.set(Calendar.YEAR, year);
	    c.set(Calendar.MONTH, month);
	    c.set(Calendar.DATE, date);
	    c.set(Calendar.HOUR_OF_DAY, 23);
	    c.set(Calendar.MINUTE, 59);
	    c.set(Calendar.SECOND, 59);
	    repeatSettings.setEndDate(c.getTime());
	//    updateEndDateButton();
	}
    }

    /** This callback handles the weekday toggles. */
    class WeekdayOnCheckedChangeListener
	implements CompoundButton.OnCheckedChangeListener {
	/** The day of the week which this particular toggle sets */
	final int weekday;

	public WeekdayOnCheckedChangeListener(int weekday) {
	    if ((weekday < Calendar.SUNDAY) || (weekday > Calendar.SATURDAY))
		throw new IllegalArgumentException();
	    this.weekday = weekday;
	}

	@Override
	public void onCheckedChanged(CompoundButton button, boolean state) {
	    if (isUpdating)
		return;
	    switch (repeatSettings.getIntervalType().value) {
	    case REPEAT_NONE:	// These should not happen
	    case REPEAT_SEMI_MONTHLY_ON_DAYS:
	    case REPEAT_MONTHLY_ON_DAY:
	    case REPEAT_YEARLY_ON_DAY:
		Log.e(LOG_TAG, "Weekday toggle selected when the repeat interval is "
			+ repeatSettings.getIntervalType());
		return;

	    case REPEAT_WEEKLY:
		repeatSettings.setOnFixedWeekday(weekday, state);
		break;

	    case REPEAT_DAILY:
	    case REPEAT_DAY_AFTER:
	    case REPEAT_WEEK_AFTER:
	    case REPEAT_SEMI_MONTHLY_ON_DATES:
	    case REPEAT_MONTHLY_ON_DATE:
	    case REPEAT_MONTH_AFTER:
	    case REPEAT_YEARLY_ON_DATE:
	    case REPEAT_YEAR_AFTER:
		repeatSettings.setOnAllowedWeekday(weekday, state);
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
	    switch (directionID) {
	    case R.id.RepeatRadioButtonNext:
		repeatSettings.setWeekdayDirection(nearest
			? RepeatSettings.WeekdayDirection.CLOSEST_OR_NEXT
			: RepeatSettings.WeekdayDirection.NEXT);
		break;

	    case R.id.RepeatRadioButtonPrevious:
		repeatSettings.setWeekdayDirection(nearest
			? RepeatSettings.WeekdayDirection.CLOSEST_OR_PREVIOUS
			: RepeatSettings.WeekdayDirection.PREVIOUS);
		break;
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
	    switch (repeatSettings.getIntervalType().value) {
	    case REPEAT_NONE:	// This shouldn't happen
		Log.e(LOG_TAG, "Radio button selected in the reset group"
			+ " when no repeat interval is selected");
		return;

	    case REPEAT_DAILY:	// These shouldn't happen either
	    case REPEAT_DAY_AFTER:
	    case REPEAT_WEEKLY:
	    case REPEAT_WEEK_AFTER:
	    case REPEAT_MONTH_AFTER:
	    case REPEAT_YEAR_AFTER:
		Log.e(LOG_TAG, "Radio button selected in the reset group when "
			+ repeatSettings.getIntervalType()
			+ " repeat interval is selected");
		return;

	    case REPEAT_SEMI_MONTHLY_ON_DATES:
		if (id == R.id.RepeatRadioButtonByDay)
		    repeatSettings.setIntervalType(
			    IntervalType.SEMI_MONTHLY_ON_DAYS);
		else
		    return;
		break;

	    case REPEAT_SEMI_MONTHLY_ON_DAYS:
		if (id == R.id.RepeatRadioButtonByDate)
		    repeatSettings.setIntervalType(
			    IntervalType.SEMI_MONTHLY_ON_DATES);
		else
		    return;
		break;

	    case REPEAT_MONTHLY_ON_DATE:
		if (id == R.id.RepeatRadioButtonByDay)
		    repeatSettings.setIntervalType(
			    IntervalType.MONTHLY_ON_DAY);
		else
		    return;
		break;

	    case REPEAT_MONTHLY_ON_DAY:
		if (id == R.id.RepeatRadioButtonByDate)
		    repeatSettings.setIntervalType(
			    IntervalType.MONTHLY_ON_DATE);
		else
		    return;
		break;

	    case REPEAT_YEARLY_ON_DATE:
		if (id == R.id.RepeatRadioButtonByDay)
		    repeatSettings.setIntervalType(
			    IntervalType.YEARLY_ON_DAY);
		else
		    return;
		break;

	    case REPEAT_YEARLY_ON_DAY:
		if (id == R.id.RepeatRadioButtonByDate)
		    repeatSettings.setIntervalType(
			    IntervalType.YEARLY_ON_DATE);
		else
		    return;
		break;
	    }
	    updateRepeatInterval();
	    updateRepeatDescription();
	}
    }

    // Callbacks for automatic changes to the repeat settings
    public void onTypeChanged(RepeatSettings settings, IntervalType newType) {
	updateRepeatInterval();
	updateRepeatDescription();
    }

    public void onIncrementChanged(RepeatSettings settings, int newIncrement) {
	updateRepeatDescription();
    }

    public void onFixedWeekdaysChanged(RepeatSettings settings,
	    Set<Integer> additions, Set<Integer> removals) {
	for (Integer day : additions) {
	    if (!weekdayToggle[day - Calendar.SUNDAY].isChecked())
		weekdayToggle[day - Calendar.SUNDAY].setChecked(true);
	}
	for (Integer day : removals) {
	    if (weekdayToggle[day - Calendar.SUNDAY].isChecked())
		weekdayToggle[day - Calendar.SUNDAY].setChecked(false);
	}
	updateRepeatDescription();
    }

    public void onAllowedWeekdaysChanged(RepeatSettings settings,
	    Set<Integer> additions, Set<Integer> removals) {
	for (Integer day : additions) {
	    if (!weekdayToggle[day - Calendar.SUNDAY].isChecked())
		weekdayToggle[day - Calendar.SUNDAY].setChecked(true);
	}
	for (Integer day : removals) {
	    if (weekdayToggle[day - Calendar.SUNDAY].isChecked())
		weekdayToggle[day - Calendar.SUNDAY].setChecked(false);
	}
	updateRepeatDescription();
    }

    public void onDayOfWeekChanged(RepeatSettings settings, int index, int newDay) {
	updateRepeatDescription();
    }

    public void onWeekChanged(RepeatSettings settings, int index, int newWeek) {
	updateRepeatDescription();
    }

    public void onDateChanged(RepeatSettings settings, int index, int newDate) {
	updateRepeatDescription();
    }

    public void onMonthChanged(RepeatSettings settings, int newMonth) {
	updateRepeatDescription();
    }

    public void onEndDateChanged(RepeatSettings settings, Date newEndDate) {
	updateEndDateButton();
    }
}
