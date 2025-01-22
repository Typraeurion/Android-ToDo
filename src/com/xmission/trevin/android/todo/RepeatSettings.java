/*
 * $Id: RepeatSettings.java,v 1.2 2011/07/18 00:46:20 trevin Exp trevin $
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
 * $Log: RepeatSettings.java,v $
 * Revision 1.2  2011/07/18 00:46:20  trevin
 * Added the copyright notice
 *
 * Revision 1.1  2011/02/28 03:30:05  trevin
 * Initial revision
 *
 */
package com.xmission.trevin.android.todo;

import static com.xmission.trevin.android.todo.ToDo.ToDoItem.*;

import java.util.*;

import com.xmission.trevin.android.todo.ToDo.ToDoItem;

import android.content.ContentValues;
import android.database.Cursor;
/**
 * A class for all of the repeat settings.
 * <p>
 * It also provides methods to populate a set of {@link ContentValues}
 * from these settings, or conversely to return a settings object
 * based on data read from a {@link Cursor}.
 * <p>
 * It also contains logic to select the next due date after a task
 * has been completed.
 *
 * @author Trevin Beattie
 */
public class RepeatSettings implements Cloneable {

    public static enum IntervalType {
	/** No repetition */
	NONE(REPEAT_NONE),
	/** Daily */
	DAILY(REPEAT_DAILY),
	/** Every day after last completed */
	DAY_AFTER(REPEAT_DAY_AFTER),
	/** Weekly on a given day(s) of the week */
	WEEKLY(REPEAT_WEEKLY),
	/** A week after last completed */
	WEEK_AFTER(REPEAT_WEEK_AFTER),
	/** Semi-monthly on two days of two weeks */
	SEMI_MONTHLY_ON_DAYS(REPEAT_SEMI_MONTHLY_ON_DAYS),
	/** Semi-monthly on two dates */
	SEMI_MONTHLY_ON_DATES(REPEAT_SEMI_MONTHLY_ON_DATES),
	/** Monthly on a days of the week */
	MONTHLY_ON_DAY(REPEAT_MONTHLY_ON_DAY),
	/** Monthly on a date */
	MONTHLY_ON_DATE(REPEAT_MONTHLY_ON_DATE),
	/** A month after last completed */
	MONTH_AFTER(REPEAT_MONTH_AFTER),
	/** Yearly on a day of a week of a month */
	YEARLY_ON_DAY(REPEAT_YEARLY_ON_DAY),
	/** Yearly on a specific date */
	YEARLY_ON_DATE(REPEAT_YEARLY_ON_DATE),
	/** A year after last completed */
	YEAR_AFTER(REPEAT_YEAR_AFTER);

	private static Map<Integer,IntervalType> idMap =
	    new HashMap<Integer,IntervalType>();
	public final int value;
	private IntervalType(int v) { value = v; }
	static {
	    for (IntervalType type : IntervalType.values())
		idMap.put(type.value, type);
	}
	public static IntervalType lookup(int type) {
	    return idMap.get(type);
	}
    }
    private IntervalType intervalType = IntervalType.NONE;

    /** The due date on which many of the repeat settings are based */
    private Calendar dueDate = Calendar.getInstance();

    private Integer increment;

    /**
     * For weekly events, this {@link Set} indicates the days
     * on which the event occurs.
     */
    private SortedSet<Integer> fixedWeekDays;

    /**
     * For (semi-)monthly or annual events on a certain date,
     * this {@link Set} indicates the days on which the event
     * may occur.  If the date falls outside of this set, the
     * {@link WeekdayDirection} indicates the next available date.
     */
    private SortedSet<Integer> allowedWeekDays;

    /** The set of all week days */
    private static final SortedSet<Integer> ALL_WEEK_DAYS;
    static {
	TreeSet<Integer> weekDays = new TreeSet<Integer>();
	for (int day = Calendar.SUNDAY; day <= Calendar.SATURDAY; day++)
	    weekDays.add(day);
	ALL_WEEK_DAYS = Collections.unmodifiableSortedSet(weekDays);
    }

    /** WeekDays is a multiple-selection bitmap, returning a {@link Set}. */
    public static enum WeekDays {
	SUNDAY(REPEAT_SUNDAYS),
	MONDAY(REPEAT_MONDAYS),
	TUESDAY(REPEAT_TUESDAYS),
	WEDNESDAY(REPEAT_WEDNESDAYS),
	THURSDAY(REPEAT_THURSDAYS),
	FRIDAY(REPEAT_FRIDAYS),
	SATURDAY(REPEAT_SATURDAYS);

	public final int value;
	private WeekDays(int v) { value = v; }

	public static WeekDays lookupDay(int day) {
	    switch (day) {
	    default: throw new IllegalArgumentException(Integer.toString(day));
	    case Calendar.SUNDAY: return SUNDAY;
	    case Calendar.MONDAY: return MONDAY;
	    case Calendar.TUESDAY: return TUESDAY;
	    case Calendar.WEDNESDAY: return WEDNESDAY;
	    case Calendar.THURSDAY: return THURSDAY;
	    case Calendar.FRIDAY: return FRIDAY;
	    case Calendar.SATURDAY: return SATURDAY;
	    }
	}

	public static Set<WeekDays> lookupBitmap(int bitmap) {
	    Set<WeekDays> days = new HashSet<WeekDays>();
	    for (WeekDays day : WeekDays.values()) {
		if ((bitmap & day.value) != 0)
		    days.add(day);
	    }
	    return days;
	}

	public static Set<WeekDays> lookupDaySet(int day) {
	    Set<WeekDays> days = new HashSet<WeekDays>();
	    switch (day) {
	    case Calendar.SUNDAY: days.add(SUNDAY); break;
	    case Calendar.MONDAY: days.add(MONDAY); break;
	    case Calendar.TUESDAY: days.add(TUESDAY); break;
	    case Calendar.WEDNESDAY: days.add(WEDNESDAY); break;
	    case Calendar.THURSDAY: days.add(THURSDAY); break;
	    case Calendar.FRIDAY: days.add(FRIDAY); break;
	    case Calendar.SATURDAY: days.add(SATURDAY); break;
	    }
	    return days;
	}
    }

    /**
     * Which direction to look for the next available date,
     * if the target date falls on an unavailable day of the week.
     */
    public static enum WeekdayDirection {
	NEXT(0),
	PREVIOUS(REPEAT_PREVIOUS_WEEKDAY),
	CLOSEST_OR_NEXT(REPEAT_CLOSEST_WEEKDAY),
	CLOSEST_OR_PREVIOUS(REPEAT_PREVIOUS_WEEKDAY | REPEAT_CLOSEST_WEEKDAY);

	public final int value;
	private WeekdayDirection(int v) { value = v; }

	public static WeekdayDirection lookup(int bitmap) {
	    switch (bitmap &
		    (REPEAT_PREVIOUS_WEEKDAY | REPEAT_CLOSEST_WEEKDAY)) {
	    default: return NEXT;
	    case REPEAT_PREVIOUS_WEEKDAY: return PREVIOUS;
	    case REPEAT_CLOSEST_WEEKDAY: return CLOSEST_OR_NEXT;
	    case REPEAT_PREVIOUS_WEEKDAY | REPEAT_CLOSEST_WEEKDAY:
		return CLOSEST_OR_PREVIOUS;
	    }
	}
    }
    private WeekdayDirection weekdayDirection = WeekdayDirection.NEXT;

    private Integer[] dayOfWeek = new Integer[2];
    private Integer[] week = new Integer[2];
    private Integer[] date = new Integer[2];
    private Integer month;
    private Date end;

    /**
     * Interface for listeners interested in changes to repeat settings.
     * Listeners are only notified when changes are made by the
     * {@link RepeatSettings} class itself, not when changes are made
     * by calling one of the setXxx(xxx) methods.
     */
    public interface OnRepeatChangeListener {
	/** Called when the basic repeat interval type changes */
	void onTypeChanged(RepeatSettings settings, IntervalType newType);
	/** Called when the number of time units between events changes */
	void onIncrementChanged(RepeatSettings settings, int newIncrement);
	/** Called when any of the days on which a weekly event occurs changes */
	void onFixedWeekdaysChanged(RepeatSettings settings,
		Set<Integer> additions, Set<Integer> removals);
	/**
	 * Called when any of the days on which the next occurance of an
	 * event after the last completed date or by day of month changes.
	 */
	void onAllowedWeekdaysChanged(RepeatSettings settings,
		Set<Integer> additions, Set<Integer> removals);
	/** Called when the day of an event by day of the week changes */
	void onDayOfWeekChanged(RepeatSettings settings, int index, int newDay);
	/** Called when the week of an event by day of the week changes */
	void onWeekChanged(RepeatSettings settings, int index, int newWeek);
	/** Called when the date of an event by day of month changes */
	void onDateChanged(RepeatSettings settings, int index, int newDate);
	/** Called when the month of an annual event changes */
	void onMonthChanged(RepeatSettings settings, int newMonth);
	/**
	 * Called when the end date of a repeating event changes.
	 * The new end date may be null for an event that never ends.
	 */
	void onEndDateChanged(RepeatSettings settings, Date newEndDate);
    }
    List<OnRepeatChangeListener> listeners =
	new LinkedList<OnRepeatChangeListener>();

    /** Create a RepeatSettings object with a type of "None" */
    public RepeatSettings() {}

    /**
     * Create a default RepeatSettings object for a given interval,
     * assuming the due date is today.
     */
    public RepeatSettings(IntervalType type) {
	this(type, null);
    }

    /**
     * Create a default RepeatSettings object for a given interval
     * and due date, or the current date if the given {@link Date} is null.
     */
    public RepeatSettings(IntervalType type, Date due) {
	intervalType = type;
	if (due != null)
	    dueDate.setTime(due);

	if (type != IntervalType.NONE)
	    increment = 1;

	/* Set the week day(s) */
	switch (type) {
	case DAILY:
	case DAY_AFTER:
	case WEEK_AFTER:
	case SEMI_MONTHLY_ON_DATES:
	case MONTHLY_ON_DATE:
	case MONTH_AFTER:
	case YEARLY_ON_DATE:
	case YEAR_AFTER:
	    allowedWeekDays = new TreeSet<Integer>(ALL_WEEK_DAYS);
	    break;

	case WEEKLY:
	    fixedWeekDays = new TreeSet<Integer>();
	    fixedWeekDays.add(dueDate.get(Calendar.DAY_OF_WEEK));
	    break;
	}

	/* Set the day and optionally the week for certain types */
	switch (type) {
	case SEMI_MONTHLY_ON_DATES:
	case MONTHLY_ON_DATE:
	case YEARLY_ON_DATE:
	    date[0] = dueDate.get(Calendar.DATE);
	    break;

	case SEMI_MONTHLY_ON_DAYS:
	case MONTHLY_ON_DAY:
	case YEARLY_ON_DAY:
	    dayOfWeek[0] = dueDate.get(Calendar.DAY_OF_WEEK);
	    week[0] = dueDate.get(Calendar.DAY_OF_WEEK_IN_MONTH);
	    if (week[0] == 5)
		week[0] = -1;
	    break;
	}

	/* Type-specific settings */
	switch (type) {
	case SEMI_MONTHLY_ON_DAYS:
	    dayOfWeek[1] = dayOfWeek[0];
	    if (week[0] > 2) {
		week[1] = week[0];
		week[0] -= 2;
	    } else if (week[0] == -1) {
		week[1] = 2;
	    } else {
		week[1] = week[0] + 2;
	    }
	    break;

	case SEMI_MONTHLY_ON_DATES:
	    if (date[0] > 15) {
		date[1] = date[0];
		if (date[0] > 30)
		    date[0] = 15;
		else
		    date[0] -= 15;
	    } else {
		if (date[0] == 15)
		    date[1] = 31;
		else
		    date[1] = date[0] + 15;
	    }
	    break;

	case YEARLY_ON_DAY:
	case YEARLY_ON_DATE:
	    month = dueDate.get(Calendar.MONTH);
	    break;
	}
    }

    /**
     * Create a default RepeatSettings object from the repeat fields
     * of a {@link Cursor}.
     */
    public RepeatSettings(Cursor c) {
	int i = c.getColumnIndex(ToDoItem.DUE_TIME);
	if (!c.isNull(i))
	    dueDate.setTime(new Date(c.getLong(i)));

	i = c.getColumnIndex(ToDoItem.REPEAT_INTERVAL);
	if (c.isNull(i))
	    return;	// "None" is the default

	intervalType = IntervalType.lookup(c.getInt(i));
	if (intervalType == null)
	    intervalType = IntervalType.NONE;
	else if (intervalType != IntervalType.NONE) {
	    i = c.getColumnIndex(ToDoItem.REPEAT_INCREMENT);
	    increment = c.isNull(i) ? 1 : c.getInt(i);
	    if (increment < 1)
		increment = 1;
	}

	/*
	 * This first switch chooses whether to read the day(s).
	 */
	switch (intervalType) {
	case SEMI_MONTHLY_ON_DAYS:
	    i = c.getColumnIndex(ToDoItem.REPEAT_DAY2);
	    if (c.isNull(i))
		intervalType = IntervalType.MONTHLY_ON_DATE;
	    else
		dayOfWeek[1] = c.getInt(i);
	    // Fall through
	case MONTHLY_ON_DAY:
	case YEARLY_ON_DAY:
	    i = c.getColumnIndex(ToDoItem.REPEAT_DAY);
	    if (c.isNull(i)) {
		dayOfWeek[0] = dueDate.get(Calendar.DATE);
	    } else {
		dayOfWeek[0] = c.getInt(i);
	    }
	    break;

	case SEMI_MONTHLY_ON_DATES:
	    i = c.getColumnIndex(ToDoItem.REPEAT_DAY2);
	    if (c.isNull(i))
		intervalType = IntervalType.MONTHLY_ON_DATE;
	    else
		date[1] = c.getInt(i);
	    // Fall through
	case MONTHLY_ON_DATE:
	case YEARLY_ON_DATE:
	    i = c.getColumnIndex(ToDoItem.REPEAT_DAY);
	    if (c.isNull(i)) {
		date[0] = dueDate.get(Calendar.DATE);
	    } else {
		date[0] = c.getInt(i);
	    }
	    break;
	}

	/* This chooses whether to read the set of week days or the week. */
	switch (intervalType) {
	case DAILY:
	case DAY_AFTER:
	case WEEK_AFTER:
	case SEMI_MONTHLY_ON_DATES:
	case MONTHLY_ON_DATE:
	case MONTH_AFTER:
	case YEARLY_ON_DATE:
	case YEAR_AFTER:
	    i = c.getColumnIndex(ToDoItem.REPEAT_WEEK_DAYS);
	    int bitmap = c.isNull(i) ? REPEAT_ALL_WEEK : c.getInt(i);
	    allowedWeekDays = new TreeSet<Integer>();
	    for (int j = 0; j < 7; j++) {
		if ((bitmap & (1 << j)) != 0)
		    allowedWeekDays.add(Calendar.SUNDAY + j);
	    }
	    weekdayDirection = WeekdayDirection.lookup(bitmap);
	    break;

	case WEEKLY:
	    i = c.getColumnIndex(ToDoItem.REPEAT_WEEK_DAYS);
	    fixedWeekDays = new TreeSet<Integer>();
	    if (c.isNull(i)) {
		fixedWeekDays.add(dueDate.get(Calendar.DAY_OF_WEEK));
	    } else {
		bitmap = c.getInt(i);
		for (int j = 0; j < 7; j++) {
		    if ((bitmap & (1 << j)) != 0)
			fixedWeekDays.add(Calendar.SUNDAY + j);
		}
	    }
	    break;

	case SEMI_MONTHLY_ON_DAYS:
	    i = c.getColumnIndex(ToDoItem.REPEAT_WEEK2);
	    if (c.isNull(i)) {
		intervalType = IntervalType.MONTHLY_ON_DAY;
		dayOfWeek[1] = null;
	    } else {
		week[1] = c.getInt(i);
	    }
	    // Fall through
	case MONTHLY_ON_DAY:
	case YEARLY_ON_DAY:
	    i = c.getColumnIndex(ToDoItem.REPEAT_WEEK);
	    if (c.isNull(i)) {
		week[0] = dueDate.get(Calendar.DAY_OF_WEEK_IN_MONTH);
	    } else {
		week[0] = c.getInt(i);
	    }
	    break;
	}

	/* Lastly, choose whether to read the month */
	switch (intervalType) {
	case YEARLY_ON_DAY:
	case YEARLY_ON_DATE:
	    i = c.getColumnIndex(ToDoItem.REPEAT_MONTH);
	    if (c.isNull(i)) {
		month = dueDate.get(Calendar.MONTH);
	    } else {
		month = c.getInt(i);
	    }
	    break;
	}

	i = c.getColumnIndex(ToDoItem.REPEAT_END);
	if (!c.isNull(i))
	    end = new Date(c.getLong(i));
    }

    /** Add a listener for change events */
    public void addOnRepeatChangeListener(OnRepeatChangeListener listener) {
	if (listener == null)
	    throw new NullPointerException();
	listeners.add(listener);
    }

    /** Remove a listener from change events */
    public void removeOnRepeatChangeListener(OnRepeatChangeListener listener) {
	listeners.remove(listener);
    }

    /** @return the repeat interval type */
    public IntervalType getIntervalType() { return intervalType; }

    /**
     * Change the repeat interval type.
     * This may have side effects of initializing other repeat settings.
     */
    @SuppressWarnings("unchecked")
    public void setIntervalType(IntervalType type) {
	intervalType = type;
	if (type == IntervalType.NONE)
	    return;	// No side effects (remember prior settings)

	if (increment == null) {
	    increment = 1;
	    for (OnRepeatChangeListener listener : listeners)
		listener.onIncrementChanged(this, increment);
	}

	/* Set the week day(s) */
	switch (type) {
	case DAILY:
	case DAY_AFTER:
	case WEEK_AFTER:
	case SEMI_MONTHLY_ON_DATES:
	case MONTHLY_ON_DATE:
	case MONTH_AFTER:
	case YEARLY_ON_DATE:
	case YEAR_AFTER:
	    if (allowedWeekDays == null) {
		allowedWeekDays = new TreeSet<Integer>(ALL_WEEK_DAYS);
		for (OnRepeatChangeListener listener : listeners)
		    listener.onAllowedWeekdaysChanged(this, ALL_WEEK_DAYS,
			    (Set<Integer>) Collections.EMPTY_SET);
	    }
	    break;

	case WEEKLY:
	    if (fixedWeekDays == null) {
		fixedWeekDays = new TreeSet<Integer>();
		fixedWeekDays.add(dueDate.get(Calendar.DAY_OF_WEEK));
		for (OnRepeatChangeListener listener : listeners)
		    listener.onFixedWeekdaysChanged(this,
			    Collections.unmodifiableSet(fixedWeekDays),
			    (Set<Integer>) Collections.EMPTY_SET);
	    }
	    break;
	}

	/* Set the day and optionally the week for certain types */
	switch (type) {
	case SEMI_MONTHLY_ON_DATES:
	case MONTHLY_ON_DATE:
	case YEARLY_ON_DATE:
	    if (date[0] == null)
		date[0] = dueDate.get(Calendar.DATE);
	    break;

	case SEMI_MONTHLY_ON_DAYS:
	case MONTHLY_ON_DAY:
	case YEARLY_ON_DAY:
	    if (dayOfWeek[0] ==null)
		dayOfWeek[0] = dueDate.get(Calendar.DAY_OF_WEEK);
	    week[0] = dueDate.get(Calendar.DAY_OF_WEEK_IN_MONTH);
	    if (week[0] == 5)
		week[0] = -1;
	    break;
	}

	/* Type-specific settings */
	switch (type) {
	case SEMI_MONTHLY_ON_DAYS:
	    if (dayOfWeek[1] == null)
		dayOfWeek[1] = dayOfWeek[0];
	    if (week[1] == null) {
		if (week[0] > 2) {
		    week[1] = week[0];
		    week[0] -= 2;
		} else if (week[0] == -1) {
		    week[1] = 2;
		} else {
		    week[1] = week[0] + 2;
		}
	    }
	    break;

	case SEMI_MONTHLY_ON_DATES:
	    if (date[1] == null) {
		if (date[0] > 15) {
		    date[1] = date[0];
		    if (date[0] > 30)
			date[0] = 15;
		    else
			date[0] -= 15;
		} else {
		    if (date[0] == 15)
			date[1] = 31;
		    else
			date[1] = date[0] + 15;
		}
	    }
	    break;

	case YEARLY_ON_DAY:
	case YEARLY_ON_DATE:
	    if (month == null)
		month = dueDate.get(Calendar.MONTH);
	    break;
	}
    }

    /**
     * Change the due date.
     * This may have side effects on other settings.
     */
    @SuppressWarnings("unchecked")
    public void setDueDate(Date due) {
	dueDate.setTime(due);

	if (fixedWeekDays != null) {
	    // Make sure the set of week days includes the new day of the week.
	    int newDay = dueDate.get(Calendar.DAY_OF_WEEK);
	    if (!fixedWeekDays.contains(newDay)) {
		// If the previous set only included one day, remove the old day.
		Set<Integer> oldDays = null;
		if (fixedWeekDays.size() == 1) {
		    if (!listeners.isEmpty())
			oldDays = new TreeSet<Integer>(fixedWeekDays);
		    fixedWeekDays.clear();
		}
		fixedWeekDays.add(newDay);
		if (!listeners.isEmpty()) {
		    Set<Integer> newDays = new TreeSet<Integer>();
		    newDays.add(newDay);
		    for (OnRepeatChangeListener listener : listeners)
			listener.onFixedWeekdaysChanged(this, newDays, oldDays);
		}
	    }
	}

	if (allowedWeekDays != null) {
	    int newDay = dueDate.get(Calendar.DAY_OF_WEEK);
	    if (!allowedWeekDays.contains(newDay)) {
		allowedWeekDays.add(newDay);
		if (!listeners.isEmpty()) {
		    Set<Integer> newDays = new TreeSet<Integer>();
		    newDays.add(newDay);
		    for (OnRepeatChangeListener listener : listeners)
			listener.onAllowedWeekdaysChanged(this, newDays,
				(Set<Integer>) Collections.EMPTY_SET);
		}
	    }
	}

	if (dayOfWeek[0] != null) {
	    dayOfWeek[0] = dueDate.get(Calendar.DAY_OF_WEEK);
	    for (OnRepeatChangeListener listener : listeners)
		listener.onDayOfWeekChanged(this, 0, dayOfWeek[0]);
	}

	if (dayOfWeek[1] != null) {
	    dayOfWeek[1] = dueDate.get(Calendar.DAY_OF_WEEK);
	    for (OnRepeatChangeListener listener : listeners)
		listener.onDayOfWeekChanged(this, 1, dayOfWeek[1]);
	}

	if (week[0] != null) {
	    week[0] = dueDate.get(Calendar.DAY_OF_WEEK_IN_MONTH);
	    if (week[0] == 5)
		week[0] = -1;

	    if (week[1] != null) {
		if (week[0] > 2) {
		    week[1] = week[0];
		    week[0] -= 2;
		} else if (week[0] == -1) {
		    week[1] = 2;
		} else {
		    week[1] = week[0] + 2;
		}
		for (OnRepeatChangeListener listener : listeners)
		    listener.onWeekChanged(this, 1, week[1]);
	    }

	    for (OnRepeatChangeListener listener : listeners)
		listener.onWeekChanged(this, 0, week[0]);
	}

    	if (date[0] != null) {
    	    date[0] = dueDate.get(Calendar.DATE);

    	    if (date[1] != null) {
    		if (date[0] > 15) {
		    date[1] = date[0];
		    if (date[0] > 30)
			date[0] = 15;
		    else
			date[0] -= 15;
		} else {
		    if (date[0] == 15)
			date[1] = 31;
		    else
			date[1] = date[0] + 15;
		}
    		for (OnRepeatChangeListener listener : listeners)
    		    listener.onDateChanged(this, 1, date[1]);
    	    }

	    for (OnRepeatChangeListener listener : listeners)
		listener.onDateChanged(this, 0, date[0]);
    	}

    	if (month != null) {
    	    month = dueDate.get(Calendar.MONTH);
	    for (OnRepeatChangeListener listener : listeners)
    		listener.onMonthChanged(this, month);
    	}
    }

    /** @return the increment between repeat intervals */
    public int getIncrement() {
	return (increment == null) ? 1 : increment;
    }

    /** Set the increment between repeat intervals */
    public void setIncrement(int delta) {
	if (delta < 1)
	    throw new IllegalArgumentException(Integer.toString(delta));
	increment = delta;
    }

    /** @return the set of weekdays on which the repeat takes place */
    public SortedSet<Integer> getFixedWeekDays() {
	final SortedSet<Integer> SORTED_EMPTY_SET =
	    Collections.unmodifiableSortedSet(new TreeSet<Integer>());
	return (fixedWeekDays == null) ? SORTED_EMPTY_SET
	: Collections.unmodifiableSortedSet(fixedWeekDays);
    }

    /** @return the set of weekdays on which the repeat may take place */
    public SortedSet<Integer> getAllowedWeekDays() {
	final SortedSet<Integer> SORTED_EMPTY_SET =
	    Collections.unmodifiableSortedSet(new TreeSet<Integer>());
	return (allowedWeekDays == null) ? SORTED_EMPTY_SET
	: Collections.unmodifiableSortedSet(allowedWeekDays);
    }

    /** @return whether this repeat will occur on a given weekday */
    public boolean isOnFixedWeekday(int day) {
	return ((fixedWeekDays != null) && fixedWeekDays.contains(day));
    }

    /** @return whether this repeat may occur on a given weekday */
    public boolean isOnAllowedWeekday(int day) {
	return ((allowedWeekDays != null) && allowedWeekDays.contains(day));
    }

    /** Set or clear this repeat to occur on a given weekday */
    public void setOnFixedWeekday(int day, boolean flag) {
	if (flag) {
	    if (fixedWeekDays == null)
		fixedWeekDays = new TreeSet<Integer>();
	    fixedWeekDays.add(day);
	} else {
	    if (fixedWeekDays != null) {
		fixedWeekDays.remove(day);
		// If the last date was removed,
		// re-add the current day of the week
		if (fixedWeekDays.size() == 0) {
		    fixedWeekDays.add(dueDate.get(Calendar.DAY_OF_WEEK));
		    if (!listeners.isEmpty() &&
			    (day != dueDate.get(Calendar.DAY_OF_WEEK))) {
			Set<Integer> oldDay = new TreeSet<Integer>();
			oldDay.add(day);
			for (OnRepeatChangeListener listener : listeners)
			    listener.onFixedWeekdaysChanged(this,
				    Collections.unmodifiableSet(fixedWeekDays),
				    oldDay);
		    }
		}
	    }
	}
    }

    /** Set this repeat to occur on a given list of weekdays */
    public void setOnFixedWeekdays(int... days) {
	if (fixedWeekDays == null)
	    fixedWeekDays = new TreeSet<Integer>();
	fixedWeekDays.clear();
	if (days.length == 0) {
	    // Add the current day of the week
	    fixedWeekDays.add(dueDate.get(Calendar.DAY_OF_WEEK));
	} else {
	    for (int d : days)
		fixedWeekDays.add(d);
	}
    }

    /** Allow or forbid this repeat to occur on a given weekday */
    public void setOnAllowedWeekday(int day, boolean flag) {
	if (flag) {
	    if (allowedWeekDays == null)
		allowedWeekDays = new TreeSet<Integer>();
	    allowedWeekDays.add(day);
	} else {
	    if (allowedWeekDays != null) {
		allowedWeekDays.remove(day);
		// If the last date was removed,
		// re-add the current day of the week
		if (allowedWeekDays.size() == 0) {
		    allowedWeekDays.add(dueDate.get(Calendar.DAY_OF_WEEK));
		    if (!listeners.isEmpty() &&
			    (day != dueDate.get(Calendar.DAY_OF_WEEK))) {
			Set<Integer> oldDay = new TreeSet<Integer>();
			oldDay.add(day);
			for (OnRepeatChangeListener listener : listeners)
			    listener.onFixedWeekdaysChanged(this,
				    Collections.unmodifiableSet(allowedWeekDays),
				    oldDay);
		    }
		}
	    }
	}
    }

    /** Allow or forbid this repeat to occur on a given list of weekdays */
    public void setOnAllowedWeekdays(int... days) {
	if (allowedWeekDays == null)
	    allowedWeekDays = new TreeSet<Integer>();
	allowedWeekDays.clear();
	if (days.length == 0) {
	    // Add the current day of the week
	    allowedWeekDays.add(dueDate.get(Calendar.DAY_OF_WEEK));
	} else {
	    for (int d : days)
		allowedWeekDays.add(d);
	}
    }

    /** @return the direction for choosing the next available day of the week */
    public WeekdayDirection getWeekdayDirection() { return weekdayDirection; }

    /** Set the direction for choosing the next available day of the week */
    public void setWeekdayDirection(WeekdayDirection direction) {
	if (direction == null)
	    throw new NullPointerException();
	weekdayDirection = direction;
    }

    /** @return the day of the week for repeating by day */
    public int getDayOfWeek() {
	return getDayOfWeek(0);
    }

    /** @return the day of the week for repeating a semi-monthly event by day */
    public int getDayOfWeek(int index) {
	if (dayOfWeek[index] == null)
	    return dueDate.get(Calendar.DAY_OF_WEEK);
	return dayOfWeek[index];
    }

    /** Set the day of the week for repeating by day */
    public void setDayOfWeek(int day) {
	setDayOfWeek(0, day);
    }

    /** Set the day of the week for repeating a semi-monthly event by day */
    public void setDayOfWeek(int index, int day) {
	if ((day < Calendar.SUNDAY) || (day > Calendar.SATURDAY))
	    throw new IllegalArgumentException(Integer.toString(day));
	dayOfWeek[index] = day;
    }

    /** @return the week of the month for repeating by day */
    public int getWeek() {
	return getWeek(0);
    }

    /** @return the week of the month for repeating a semi-monthly event by day */
    public int getWeek(int index) {
	if (week[index] == null)
	    return dueDate.get(Calendar.DAY_OF_WEEK_IN_MONTH);
	return week[index];
    }

    /** Set the week of the month for repeating by day */
    public void setWeek(int week) {
	setWeek(0, week);
    }

    /** Set the week of the month for repeating a semi-monthly event by day */
    public void setWeek(int index, int week) {
	if ((week < -4) || (week == 0) || (week > 5))
	    throw new IllegalArgumentException(Integer.toString(week));
	if (week == 5)
	    week = -1;
	this.week[index] = week;
    }

    /** @return the date for repeating by date */
    public int getDate() {
	return getDate(0);
    }

    /** @return the date for repeating a semi-monthly event by date */
    public int getDate(int index) {
	if (date[index] == null)
	    return dueDate.get(Calendar.DATE);
	return date[index];
    }

    /** Set the date for repeating by date */
    public void setDate(int date) {
	setDate(0, date);
    }

    /** Set the date for repeating a semi-monthly event by date */
    public void setDate(int index, int date) {
	if ((date < 1) || (date > 31))
	    throw new IllegalArgumentException(Integer.toString(date));
	this.date[index] = date;
    }

    /** @return the month for repeating a yearly event */
    public int getMonth() {
	if (month == null)
	    return dueDate.get(Calendar.MONTH);
	return month;
    }

    /** Set the month for repeating a yearly event */
    public void setMonth(int month) {
	if ((month < Calendar.JANUARY) || (month > Calendar.DECEMBER))
	    throw new IllegalArgumentException(Integer.toString(month));
	this.month = month;
    }

    /**
     * @return the last date of this repeating event,
     * or null if the event repeats perpetually.
     */
    public Date getEndDate() { return end; }

    /**
     * Set the last date of this repeating event.
     * Set to null if the event repeats perpetually.
     */
    public void setEndDate(Date date) {
	end = date;
    }

    /**
     * @return a bitmap consisting of the set of week days selected
     * for these repeat settings plus the direction for choosing
     * the next available day of the week.
     */
    public int getWeekdayBitmap() {
	int bitmap = 0;
	switch (intervalType) {
	case NONE:
	case SEMI_MONTHLY_ON_DAYS:
	case MONTHLY_ON_DAY:
	case YEARLY_ON_DAY:
	    break;

	case WEEKLY:
	    if (fixedWeekDays != null) {
		for (int day : fixedWeekDays)
		    bitmap |= WeekDays.lookupDay(day).value;
	    }
	    break;

	case DAILY:
	case DAY_AFTER:
	case WEEK_AFTER:
	case SEMI_MONTHLY_ON_DATES:
	case MONTHLY_ON_DATE:
	case MONTH_AFTER:
	case YEARLY_ON_DATE:
	case YEAR_AFTER:
	    if (allowedWeekDays != null) {
		for (int day : allowedWeekDays)
		    bitmap |= WeekDays.lookupDay(day).value;
		bitmap |= weekdayDirection.value;
	    }
	    break;
	}
	return bitmap;
    }

    /** Close these repeat settings */
    @Override
    public RepeatSettings clone() {
	try {
	    RepeatSettings copy = (RepeatSettings) super.clone();
	    if (allowedWeekDays != null)
		copy.allowedWeekDays = new TreeSet<Integer>(allowedWeekDays);
	    copy.date = new Integer[date.length];
	    System.arraycopy(date, 0, copy.date, 0, date.length);
	    copy.dayOfWeek = new Integer[dayOfWeek.length];
	    System.arraycopy(dayOfWeek, 0, copy.dayOfWeek, 0, dayOfWeek.length);
	    copy.dueDate = Calendar.getInstance();
	    copy.dueDate.setTime(dueDate.getTime());
	    if (end != null)
		copy.end = new Date(end.getTime());
	    if (fixedWeekDays != null)
		copy.fixedWeekDays = new TreeSet<Integer>(fixedWeekDays);
	    copy.week = new Integer[week.length];
	    System.arraycopy(week, 0, copy.week, 0, week.length);
	    return copy;
	} catch (CloneNotSupportedException cnsx) {
	    throw new RuntimeException(cnsx);
	}
    }

    /**
     * @return a set of {@link ContentValues} that can be sent to a database
     * to store the current repeat settings.
     */
    public ContentValues getContentValues() {
	ContentValues values = new ContentValues();
	store(values);
	return values;
    }

    /**
     * Add these repeat settings to an existing set of {@link ContentValues}.
     */
    public void store(ContentValues values) {
	values.put(ToDoItem.REPEAT_INTERVAL, intervalType.value);

	if (intervalType == IntervalType.NONE) {
	    values.putNull(ToDoItem.REPEAT_INCREMENT);
	    values.putNull(ToDoItem.REPEAT_END);
	} else {
	    values.put(ToDoItem.REPEAT_INCREMENT, increment);
	    if (end == null)
		values.putNull(ToDoItem.REPEAT_END);
	    else
		values.put(ToDoItem.REPEAT_END, end.getTime());
	}

	switch (intervalType) {
	default:
	    values.putNull(ToDoItem.REPEAT_WEEK_DAYS);
	    break;

	case DAILY:
	case DAY_AFTER:
	case WEEKLY:
	case WEEK_AFTER:
	case SEMI_MONTHLY_ON_DATES:
	case MONTHLY_ON_DATE:
	case MONTH_AFTER:
	case YEARLY_ON_DATE:
	case YEAR_AFTER:
	    values.put(ToDoItem.REPEAT_WEEK_DAYS, getWeekdayBitmap());
	    break;
	}

	switch (intervalType) {
	default:
	    values.putNull(ToDoItem.REPEAT_DAY);
	    break;

	case SEMI_MONTHLY_ON_DAYS:
	case MONTHLY_ON_DAY:
	case YEARLY_ON_DAY:
	    values.put(ToDoItem.REPEAT_DAY, dayOfWeek[0]);
	    break;

	case SEMI_MONTHLY_ON_DATES:
	case MONTHLY_ON_DATE:
	case YEARLY_ON_DATE:
	    values.put(ToDoItem.REPEAT_DAY, date[0]);
	    break;
	}

	switch (intervalType) {
	default:
	    values.putNull(ToDoItem.REPEAT_DAY2);
	    values.putNull(ToDoItem.REPEAT_WEEK2);
	    break;

	case SEMI_MONTHLY_ON_DAYS:
	    values.put(ToDoItem.REPEAT_DAY2, dayOfWeek[1]);
	    values.put(ToDoItem.REPEAT_WEEK2, week[1]);
	    break;

	case SEMI_MONTHLY_ON_DATES:
	    values.put(ToDoItem.REPEAT_DAY2, date[1]);
	    values.putNull(ToDoItem.REPEAT_WEEK2);
	    break;
	}

	switch (intervalType) {
	default:
	    values.putNull(ToDoItem.REPEAT_WEEK);
	    break;

	case SEMI_MONTHLY_ON_DAYS:
	case MONTHLY_ON_DAY:
	case YEARLY_ON_DAY:
	    values.put(ToDoItem.REPEAT_WEEK, week[0]);
	    break;
	}

	switch (intervalType) {
	default:
	    values.putNull(ToDoItem.REPEAT_MONTH);
	    break;

	case YEARLY_ON_DAY:
	case YEARLY_ON_DATE:
	    values.put(ToDoItem.REPEAT_MONTH, month);
	    break;
	}
    }

    /**
     * Recompute a due date based on the available days of the week.
     * This must only be called when the interval type is by date
     * or after last completed.
     */
    private void adjustDueDate(Calendar dueDate) {
	switch (getIntervalType()) {
	default: return;

	case DAILY:
	case DAY_AFTER:
	case WEEK_AFTER:
	case SEMI_MONTHLY_ON_DATES:
	case MONTHLY_ON_DATE:
	case MONTH_AFTER:
	case YEARLY_ON_DATE:
	case YEAR_AFTER:
	    break;
	}
	// If the new due date is on an allowed day, no adjustment is needed.
	int targetDate = dueDate.get(Calendar.DAY_OF_WEEK);
	if (allowedWeekDays.contains(targetDate))
	    return;

	// Find the next and previous available days.
	SortedSet<Integer> daysAfter = allowedWeekDays.tailSet(targetDate);
	SortedSet<Integer> daysBefore = allowedWeekDays.headSet(targetDate);
	int skipAhead = (daysAfter.isEmpty() ? (7 + allowedWeekDays.first())
		: daysAfter.first()) - targetDate;
	int skipBack = (daysBefore.isEmpty() ? (allowedWeekDays.last() - 7)
		: daysBefore.last()) - targetDate;

	switch (weekdayDirection) {
	case NEXT:
	    dueDate.add(Calendar.DATE, skipAhead);
	    break;

	case PREVIOUS:
	    dueDate.add(Calendar.DATE, skipBack);
	    break;

	case CLOSEST_OR_NEXT:
	    if (Math.abs(skipAhead) <= Math.abs(skipBack))
		dueDate.add(Calendar.DATE, skipAhead);
	    else
		dueDate.add(Calendar.DATE, skipBack);
	    break;

	case CLOSEST_OR_PREVIOUS:
	    if (Math.abs(skipBack) <= Math.abs(skipAhead))
		dueDate.add(Calendar.DATE, skipBack);
	    else
		dueDate.add(Calendar.DATE, skipAhead);
	    break;
	}
    }

    /**
     * Using these repeat settings, return the next due date from
     * the prior due date (previously set in {@link #setDueDate}
     * and completion date.  Returns null if the item will not repeat.
     */
    public Date computeNextDueDate(Date completed) {
	Date origDate = dueDate.getTime();
	switch (getIntervalType()) {
	default: return null;

	case DAILY:
	    dueDate.add(Calendar.DATE, getIncrement());
	    adjustDueDate(dueDate);
	    for (int i = getIncrement() * 2; !dueDate.getTime().after(origDate);
		i += getIncrement()) {
		dueDate.setTime(origDate);
		dueDate.add(Calendar.DATE, i);
		adjustDueDate(dueDate);
	    }
	    break;

	case DAY_AFTER:
	    dueDate.setTimeInMillis(completed.getTime());
	    dueDate.add(Calendar.DATE, getIncrement());
	    adjustDueDate(dueDate);
	    for (int i = getIncrement() * 2; !dueDate.getTime().after(completed);
		i += getIncrement()) {
		dueDate.setTime(origDate);
		dueDate.add(Calendar.DATE, i);
		adjustDueDate(dueDate);
	    }
	    break;

	case WEEKLY:
	    int day = dueDate.get(Calendar.DAY_OF_WEEK);
	    SortedSet<Integer> remainingDays = fixedWeekDays.tailSet(day + 1);
	    if (remainingDays.isEmpty())
		dueDate.add(Calendar.DATE,
			7 * getIncrement() + fixedWeekDays.first() - day);
	    else
		dueDate.add(Calendar.DATE, remainingDays.first() - day);
	    break;

	case WEEK_AFTER:
	    dueDate.setTimeInMillis(completed.getTime());
	    dueDate.add(Calendar.DATE, 7 * getIncrement());
	    adjustDueDate(dueDate);
	    break;

	case SEMI_MONTHLY_ON_DATES:
	case SEMI_MONTHLY_ON_DAYS:
	    Calendar date1 = Calendar.getInstance();
	    Calendar date2 = Calendar.getInstance();
	    date1.setTime(dueDate.getTime());
	    date2.setTime(dueDate.getTime());
	    if (getIntervalType() == IntervalType.SEMI_MONTHLY_ON_DATES) {
		if (getDate(0) > date1.getActualMaximum(Calendar.DATE))
		    date1.set(Calendar.DATE, date1.getActualMaximum(Calendar.DATE));
		else
		    date1.set(Calendar.DATE, getDate(0));
		if (getDate(1) > date2.getActualMaximum(Calendar.DATE))
		    date2.set(Calendar.DATE, date2.getActualMaximum(Calendar.DATE));
		else
		    date2.set(Calendar.DATE, getDate(1));
	    } else {
		date1.set(Calendar.DAY_OF_WEEK, getDayOfWeek(0));
		if (getWeek(0) > date1.getActualMaximum(Calendar.DAY_OF_WEEK_IN_MONTH))
		    date1.set(Calendar.DAY_OF_WEEK_IN_MONTH,
			    date1.getActualMaximum(Calendar.DAY_OF_WEEK_IN_MONTH));
		else
		    date1.set(Calendar.DAY_OF_WEEK_IN_MONTH, getWeek(0));
		date2.set(Calendar.DAY_OF_WEEK, getDayOfWeek(1));
		if (getWeek(1) > date2.getActualMaximum(Calendar.DAY_OF_WEEK_IN_MONTH))
		    date2.set(Calendar.DAY_OF_WEEK_IN_MONTH,
			    date2.getActualMaximum(Calendar.DAY_OF_WEEK_IN_MONTH));
		else
		    date2.set(Calendar.DAY_OF_WEEK_IN_MONTH, getWeek(1));
	    }
	    Calendar adjDate1 = Calendar.getInstance();
	    Calendar adjDate2 = Calendar.getInstance();
	    adjDate1.setTime(date1.getTime());
	    adjDate2.setTime(date2.getTime());
	    adjustDueDate(adjDate1);
	    adjustDueDate(adjDate2);
	    if (!adjDate1.getTime().after(origDate)) {
		date1.add(Calendar.MONTH, getIncrement());
		adjDate1.setTime(date1.getTime());
		adjustDueDate(adjDate1);
	    }
	    if (!adjDate2.getTime().after(origDate)) {
		date2.add(Calendar.MONTH, getIncrement());
		adjDate2.setTime(date2.getTime());
		adjustDueDate(adjDate2);
	    }
	    if (adjDate1.before(adjDate2))
		dueDate.setTime(adjDate1.getTime());
	    else
		dueDate.setTime(adjDate2.getTime());
	    break;

	case MONTHLY_ON_DATE:
	    dueDate.add(Calendar.MONTH, getIncrement());
	    // If the specified date is past the end of the month,
	    // use the end of the month instead.
	    if (getDate() > dueDate.getActualMaximum(Calendar.DATE))
		dueDate.set(Calendar.DATE, dueDate.getActualMaximum(Calendar.DATE));
	    else
		dueDate.set(Calendar.DATE, getDate());
	    adjustDueDate(dueDate);
	    break;

	case MONTHLY_ON_DAY:
	    dueDate.add(Calendar.MONTH, getIncrement());
	    dueDate.set(Calendar.DAY_OF_WEEK, getDayOfWeek());
	    if (getWeek() > dueDate.getActualMaximum(Calendar.DAY_OF_WEEK_IN_MONTH))
		dueDate.set(Calendar.DAY_OF_WEEK_IN_MONTH,
			dueDate.getActualMaximum(Calendar.DAY_OF_WEEK_IN_MONTH));
	    dueDate.set(Calendar.DAY_OF_WEEK_IN_MONTH, getWeek());
	    break;

	case MONTH_AFTER:
	    dueDate.setTimeInMillis(completed.getTime());
	    dueDate.add(Calendar.MONTH, getIncrement());
	    adjustDueDate(dueDate);
	    break;

	case YEARLY_ON_DATE:
	    dueDate.add(Calendar.YEAR, getIncrement());
	    // The only reason the date may change is if it was specified
	    // as February 29 in a leap year.  So handle it.
	    dueDate.set(Calendar.MONTH, getMonth());
	    if (getDate() > dueDate.getActualMaximum(Calendar.DATE))
		dueDate.set(Calendar.DATE, dueDate.getActualMaximum(Calendar.DATE));
	    else
		dueDate.set(Calendar.DATE, getDate());
	    adjustDueDate(dueDate);
	    break;

	case YEARLY_ON_DAY:
	    dueDate.add(Calendar.YEAR, getIncrement());
	    dueDate.set(Calendar.MONTH, getMonth());
	    dueDate.set(Calendar.DAY_OF_WEEK, getDayOfWeek());
	    if (getWeek() > dueDate.getActualMaximum(Calendar.DAY_OF_WEEK_IN_MONTH))
		dueDate.set(Calendar.DAY_OF_WEEK_IN_MONTH,
			dueDate.getActualMaximum(Calendar.DAY_OF_WEEK_IN_MONTH));
	    else
		dueDate.set(Calendar.DAY_OF_WEEK_IN_MONTH, getWeek());
	    break;

	case YEAR_AFTER:
	    dueDate.setTimeInMillis(completed.getTime());
	    dueDate.add(Calendar.YEAR, getIncrement());
	    adjustDueDate(dueDate);
	    break;
	}

	// Check whether we've exceeded the end date
	if ((end != null) && (dueDate.getTimeInMillis() > end.getTime())) {
	    dueDate.setTime(origDate);
	    return null;
	} else {
	    return dueDate.getTime();
	}
    }
}
