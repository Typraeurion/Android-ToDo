/*
 * Copyright © 2026 Trevin Beattie
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.xmission.trevin.android.todo.data.repeat.*;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.*;

/**
 * Container for the current state of UI elements in a {@link RepeatEditor}.
 * This is used to preserve elements that may disappear and reappear as
 * the user switches between interval types.
 *
 * @author Trevin Beattie
 */
public class RepeatSettings implements Serializable {

    private static final long serialVersionUID = 11;

    @NonNull
    private RepeatType repeatType = RepeatType.NONE;

    @Nullable
    private LocalDate dueDate = LocalDate.now();

    private int increment= 1;

    /**
     * For weekly events, the days on which the event occurs.
     * For events on a certain date, the days on which the event
     * may occur.  If the date falls outside of this set, the
     * {@link WeekdayDirection} indicates the next available date.
     */
    @NonNull
    private SortedSet<WeekDays> weekDays = new TreeSet<>(WeekDays.ALL);

    @NonNull
    private WeekdayDirection weekdayDirection = WeekdayDirection.NEXT;

    private final WeekDays[] dayOfWeek = new WeekDays[] {
            WeekDays.MONDAY, WeekDays.FRIDAY
    };

    private final int[] week = new int[] { 1, 3 };

    private final int[] date = new int[] { 15, 31 };

    @NonNull
    private Months month = Months.JANUARY;

    @Nullable
    private LocalDate endDate;

    /**
     * Interface for listeners interested in changes to repeat settings.
     * Listeners are only notified when changes are made by the
     * {@link RepeatSettings} class itself, not when changes are made
     * by calling one of the setXxx(xxx) methods.
     */
    public interface OnRepeatChangeListener {
        /**
         * Called when the due date changes.
         *
         * @param settings the repeat settings object
         * @param newDue the new due date
         */
        void onDueChanged(RepeatSettings settings, LocalDate newDue);
        /**
         * Called when the basic repeat interval type changes.
         *
         * @param settings the repeat settings object
         * @param newType the new repeat interval type
         */
        void onTypeChanged(RepeatSettings settings, RepeatType newType);
        /**
         * Called when the number of time units between events changes.
         *
         * @param settings the repeat settings object
         * @param newIncrement the new increment
         */
        void onIncrementChanged(RepeatSettings settings, int newIncrement);
        /**
         * Called when any of the days on which an event may occur change.
         *
         * @param settings the repeat settings object
         * @param additions the set of days that were added
         * @param removals the set of days that were removed
         */
        void onWeekdaysChanged(
                RepeatSettings settings,
                Set<WeekDays> additions, Set<WeekDays> removals);
        /**
         * Called when the direction for finding an allowed weekday changes.
         *
         * @param settings the repeat settings object
         * @param newDirection the new direction
         */
        void onWeekdayDirectionChanged(
                RepeatSettings settings, WeekdayDirection newDirection);
        /**
         * Called when the day of an event by day of the week changes.
         *
         * @param settings the repeat settings object
         * @param index for semi-monthly repeat interval, whether this
         * is the first (0) or second (1) day.
         * @param newDay the new day of the week
         */
        void onDayOfWeekChanged(RepeatSettings settings,
                                int index, @NonNull WeekDays newDay);
        /**
         * Called when the week of an event by day of the week changes.
         *
         * @param settings the repeat settings object
         * @param index for semi-monthly repeat interval, whether this
         * is the first (0) or second (1) week
         * @param newWeek the new week
         */
        void onWeekChanged(RepeatSettings settings, int index, int newWeek);
        /**
         * Called when the date of an event by day of month changes.
         *
         * @param settings the repeat settings object
         * @param index for semi-monthly repeat interval, whether this
         * is the first (0) or second (1) date
         * @param newDate the new date
         */
        void onDateChanged(RepeatSettings settings, int index, int newDate);
        /**
         * Called when the month of an annual event changes.
         *
         * @param settings the repeat settings object
         * @param newMonth the new month
         */
        void onMonthChanged(RepeatSettings settings, @NonNull Months newMonth);
        /**
         * Called when the end date of a repeating event changes.
         * The new end date may be null for an event that never ends.
         *
         * @param settings the repeat settings object
         * @param newEndDate the new end date
         */
        void onEndDateChanged(RepeatSettings settings,
                              @Nullable LocalDate newEndDate);
    }

    private transient final List<OnRepeatChangeListener> listeners =
            new LinkedList<>();

    /** Create a RepeatSettings object with a type of "None" */
    public RepeatSettings() {}

    /**
     * Create a RepeatSettings object for a given repeat interval
     * and due date.
     *
     * @param repeat the repeat interval to use
     * @param dueDate the initial due date which the
     * repeat interval is based on
     */
    public RepeatSettings(@Nullable RepeatInterval repeat,
                          @Nullable LocalDate dueDate) {
        this.dueDate = dueDate;
        setRepeat(repeat);
    }

    /**
     * Set our repeat settings according to the given interval.
     * The interval may be {@code null} which is equivalent to
     * a {@link RepeatNone}.
     *
     * @param repeat the repeat interval to use
     */
    public void setRepeat(@Nullable RepeatInterval repeat) {

        if (repeat == null)
            repeatType = RepeatType.NONE;
        else
            repeatType = repeat.getType();

        if (repeat instanceof AbstractRepeat) {
            increment = ((AbstractRepeat) repeat).getIncrement();
            endDate = ((AbstractRepeat) repeat).getEnd();
        }

        if (repeat instanceof AbstractAdjustableRepeat) {
            weekDays.clear();
            weekDays.addAll(((AbstractAdjustableRepeat) repeat)
                    .getAllowedWeekDays());
            weekdayDirection = ((AbstractAdjustableRepeat) repeat)
                    .getDirection();
        }

        if (repeat instanceof AbstractDateRepeat) {
            date[0] = ((AbstractDateRepeat) repeat).getDate();
        }

        if (repeat instanceof RepeatWeekly) {
            weekDays.clear();
            weekDays.addAll(((RepeatWeekly) repeat).getWeekDays());
        }

        if (repeat instanceof RepeatMonthlyOnDay) {
            dayOfWeek[0] = ((RepeatMonthlyOnDay) repeat).getDay();
            week[0] = ((RepeatMonthlyOnDay) repeat).getWeek();
        }

        if (repeat instanceof RepeatSemiMonthlyOnDates) {
            date[1] = ((RepeatSemiMonthlyOnDates) repeat).getDate2();
        }

        if (repeat instanceof RepeatSemiMonthlyOnDays) {
            dayOfWeek[1] = ((RepeatSemiMonthlyOnDays) repeat).getDay2();
            week[1] = ((RepeatSemiMonthlyOnDays) repeat).getWeek2();
        }

        if (repeat instanceof RepeatYearlyOnDate) {
            month = ((RepeatYearlyOnDate) repeat).getMonth();
        }

        if (repeat instanceof RepeatYearlyOnDay) {
            month = ((RepeatYearlyOnDay) repeat).getMonth();
        }

    }

    /**
     * Add a listener for change events.
     *
     * @param listener the listener to add
     */
    public void addOnRepeatChangeListener(
            @NonNull OnRepeatChangeListener listener) {
        listeners.add(listener);
    }

    /**
     * Remove a listener from change events.
     *
     * @param listener the listener to remove
     */
    public void removeOnRepeatChangeListener(
            @NonNull OnRepeatChangeListener listener) {
        listeners.remove(listener);
    }

    /**
     * Get the repeat interval type.  This is used by the editor
     * when populating and setting the visibility of UI elements
     * corresponding to these settings.
     *
     * @return the repeat type
     */
    @NonNull
    public RepeatType getRepeatType() { return repeatType; }

    /**
     * Change the repeat interval type.  This
     * does not affect other repeat settings.
     *
     * @param newType the new repeat interval type
     */
    public void setRepeatType(@NonNull RepeatType newType) {
        if (newType == repeatType)
            return;

        repeatType = newType;

        // Notify the listeners of all changes
        for (OnRepeatChangeListener listener : listeners) {
            listener.onTypeChanged(this, newType);
        }
    }

    /**
     * Change the due date for these repeat settings.
     * This may have side effects on other settings.
     *
     * @param newDue the new due date
     */
    public void setDueDate(@NonNull LocalDate newDue) {
        if (dueDate.equals(newDue))
            return;
        dueDate = newDue;

        boolean weekdaysChanged = false;
        Set<WeekDays> addedDay = new HashSet<>();
        Set<WeekDays> removedDay = new HashSet<>();
        // Make sure the set of week days includes the new day of the week.
        WeekDays targetDay = WeekDays.fromJavaDay(newDue.getDayOfWeek());
        if (!weekDays.contains(targetDay)) {
            if (repeatType == RepeatType.WEEKLY) {
                removedDay.addAll(weekDays);
                weekDays.clear();
            }
            addedDay.add(targetDay);
            weekdaysChanged = true;
        }

        boolean day1Changed = false;
        boolean day2Changed = false;
        if (targetDay != dayOfWeek[0]) {
            dayOfWeek[0] = targetDay;
            day1Changed = true;
            if (targetDay != dayOfWeek[1]) {
                dayOfWeek[1] = targetDay;
                day2Changed = true;
            }
        }

        boolean week1Changed = false;
        boolean week2Changed = false;
        int targetWeek = newDue.get(WeekFields.SUNDAY_START.weekOfMonth());
        if (targetWeek > 4)
            targetWeek = -1;
        if (targetWeek != week[0]) {
            week[0] = targetWeek;
            week1Changed = true;
            int targetWeek2 = (targetWeek < 0) ? 2 :
                    ((targetWeek > 2) ? targetWeek - 2 : targetWeek + 2);
            if (targetWeek2 != week[1]) {
                week[1] = targetWeek2;
                week2Changed = true;
            }
        }

        boolean date1Changed = false;
        boolean date2Changed = false;
        if (dueDate.getDayOfMonth() != date[0]) {
            date[0] = dueDate.getDayOfMonth();
            date1Changed = true;
            int targetDate2 = (date[0] == 15) ? 31
                    : ((date[0] > 30) ? 15
                    : ((date[0] > 15) ? date[0] - 15 : date[0] + 15));
            if (targetDate2 != date[1]) {
                date[1] = targetDate2;
                date2Changed = true;
            }
        }

        boolean monthChanged = false;
        Months targetMonth = Months.fromJavaMonth(newDue.getMonth());
        if (targetMonth != month) {
            month = targetMonth;
            monthChanged = true;
        }

        // Notify the listeners of all changes
        for (OnRepeatChangeListener listener : listeners) {
            listener.onDueChanged(this, dueDate);
            if (weekdaysChanged)
                listener.onWeekdaysChanged(this, addedDay, removedDay);
            if (day1Changed)
                listener.onDayOfWeekChanged(this, 0, dayOfWeek[0]);
            if (day2Changed)
                listener.onDayOfWeekChanged(this, 1, dayOfWeek[1]);
            if (week1Changed)
                listener.onWeekChanged(this, 0, week[0]);
            if (week2Changed)
                listener.onWeekChanged(this, 1, week[1]);
            if (date1Changed)
                listener.onDateChanged(this, 0, date[0]);
            if (date2Changed)
                listener.onDateChanged(this, 1, date[1]);
            if (monthChanged)
                listener.onMonthChanged(this, month);
        }
    }

    /** @return the increment between repeat intervals */
    public int getIncrement() {
        return increment;
    }

    /**
     * Set the increment between repeat intervals
     *
     * @param newIncrement the increment to set
     */
    public void setIncrement(int newIncrement) {
        if (newIncrement < 1)
            throw new IllegalArgumentException(Integer.toString(newIncrement));
        if (newIncrement == increment)
            return;
        increment = newIncrement;
        for (OnRepeatChangeListener listener : listeners) {
            listener.onIncrementChanged(this, increment);
        }
    }

    /** @return the set of weekdays on which the repeat may take place. */
    @NonNull
    public SortedSet<WeekDays> getWeekDays() {
        return Collections.unmodifiableSortedSet(weekDays);
    }

    /** @return whether this repeat may occur on a given weekday */
    public boolean isOnWeekday(@NonNull WeekDays day) {
        return weekDays.contains(day);
    }

    /**
     * Set or clear this repeat to occur on a given weekday.  The settings
     * does not allow the entire set to be cleared; if the last member
     * is removed, the day of the current {@code dueDate} is added back.
     *
     * @param day the day of the week to toggle
     * @param flag whether to set ({@code true}) or clear ({@code false})
     * the day.
     */
    public void setWeekdayMember(@NonNull WeekDays day, boolean flag) {
        if (flag) {
            if (weekDays.contains(day))
                return;
            weekDays.add(day);
            for (OnRepeatChangeListener listener : listeners) {
                listener.onWeekdaysChanged(this,
                        Collections.singleton(day),
                        Collections.emptySet());
            }
        }
        else {
            if (!weekDays.contains(day))
                return;
            weekDays.remove(day);
            if (weekDays.isEmpty()) {
                WeekDays targetDay = WeekDays.fromJavaDay(dueDate.getDayOfWeek());
                weekDays.add(targetDay);
                if (targetDay != day) {
                    for (OnRepeatChangeListener listener : listeners) {
                        listener.onWeekdaysChanged(this,
                                Collections.singleton(targetDay),
                                Collections.singleton(day));
                    }
                }
                return;
            }
            for (OnRepeatChangeListener listener : listeners) {
                listener.onWeekdaysChanged(this,
                        Collections.emptySet(),
                        Collections.singleton(day));
            }
        }
    }

    /** Set this repeat to allow all days of the week. */
    public void setAllWeekdays() {
        if (weekDays.equals(WeekDays.ALL))
            return;
        Set<WeekDays> missingDays = new HashSet<>(WeekDays.ALL);
        missingDays.removeAll(weekDays);
        weekDays.addAll(missingDays);
        for (OnRepeatChangeListener listener : listeners) {
            listener.onWeekdaysChanged(this,
                    missingDays, Collections.emptySet());
        }
    }

    /**
     * @return the direction for choosing the next available day of the week
     */
    public WeekdayDirection getDirection() {
        return weekdayDirection;
    }

    /**
     * Set the direction for choosing the next available day of the week.
     *
     * @param newDirection the new direction
     */
    public void setDirection(WeekdayDirection newDirection) {
        if (newDirection == weekdayDirection)
            return;
        weekdayDirection = newDirection;
        for (OnRepeatChangeListener listener : listeners) {
            listener.onWeekdayDirectionChanged(this, newDirection);
        }
    }

    /**
     * @param index {@code 0} for the first (or only) day of the week,
     * {@code 1} for the second day in a semi-monthly repeat.
     *
     * @return the day of the week for repeating by day
     */
    @NonNull
    public WeekDays getDayOfWeek(int index) {
        return dayOfWeek[index];
    }

    /**
     * Set the day of the week for repeating by day.
     *
     * @param index {@code 0} for the first (or only) day of the week,
     * {@code 1} for the second day in a semi-monthly repeat.
     * @param newDay the new day of the week
     */
    public void setDayOfWeek(int index, @NonNull WeekDays newDay) {
        if (newDay == dayOfWeek[index])
            return;
        dayOfWeek[index] = newDay;
        for (OnRepeatChangeListener listener : listeners) {
            listener.onDayOfWeekChanged(this, index, newDay);
        }
    }

    /**
     * @param index {@code 0} for the first (or only) week of the month,
     * {@code 1} for the second week in a semi-monthly repeat.
     *
     * @return the week of the month for repeating by day
     */
    public int getWeek(int index) {
        return week[index];
    }

    /**
     * Set the week of the month for repeating by day.
     *
     * @param index {@code 0} for the first (or only) week of the month,
     * {@code 1} for the second week in a semi-monthly repeat.
     * @param newWeek the new week of the month; must be {@code -1}
     * or {@code 1}&ndash;{@code 4}.
     */
    public void setWeek(int index, int newWeek) {
        if (newWeek == week[index])
            return;
        if ((newWeek < -1) || (newWeek == 0) || (newWeek > 4))
            throw new IllegalArgumentException(Integer.toString(newWeek));
        week[index] = newWeek;
        for (OnRepeatChangeListener listener : listeners) {
            listener.onWeekChanged(this, index, newWeek);
        }
    }

    /**
     * @param index {@code 0} for the first (or only) date of the month,
     * {@code 1} for the second date in a semi-monthly repeat.
     *
     * @return the date for repeating by date
     */
    public int getDate(int index) {
        return date[index];
    }

    /**
     * Set the date for repeating by date.
     *
     * @param index {@code 0} for the first (or only) date of the month,
     * {@code 1} for the second date in a semi-monthly repeat.
     * @param newDate the date to set
     */
    public void setDate(int index, int newDate) {
        if (newDate == date[index])
            return;
        if ((newDate < 1) || (newDate > 31))
            throw new IllegalArgumentException(Integer.toString(newDate));
        date[index] = newDate;
        for (OnRepeatChangeListener listener : listeners) {
            listener.onDateChanged(this, index, newDate);
        }
    }

    /** @return the month for repeating a yearly event */
    @NonNull
    public Months getMonth() {
        return month;
    }

    /**
     * Set the month for repeating a yearly event.
     *
     * @param newMonth the new month
     */
    public void setMonth(@NonNull Months newMonth) {
        if (newMonth == month)
            return;
        month = newMonth;
        for (OnRepeatChangeListener listener : listeners) {
            listener.onMonthChanged(this, newMonth);
        }
    }

    /**
     * @return the last date of this repeating event,
     * or {@code null} if the event repeats perpetually.
     */
    @Nullable
    public LocalDate getEndDate() {
        return endDate;
    }

    /**
     * Set the last date of this repeating event.
     * Set to {@code null} if the event repeats perpetually.
     *
     * @param newDate the new end date
     */
    public void setEndDate(LocalDate newDate) {
        if (Objects.equals(newDate, endDate))
            return;
        endDate = newDate;
        for (OnRepeatChangeListener listener : listeners) {
            listener.onEndDateChanged(this, newDate);
        }
    }

    /**
     * Construct a {@link RepeatInterval} based on the current settings.
     * May be {@code null} if the current repeat type is
     * {@link RepeatType#NONE}.
     *
     * @return the repeat interval
     */
    @Nullable
    public RepeatInterval getRepeat() {
        if (repeatType == RepeatType.NONE)
            return null;
        AbstractRepeat repeat = (AbstractRepeat) ((dueDate == null) ?
                repeatType.newInstance() : repeatType.newInstance(dueDate));
        repeat.setIncrement(increment);
        repeat.setEnd(endDate);

        if (repeat instanceof AbstractAdjustableRepeat) {
            ((AbstractAdjustableRepeat) repeat).setAllowedWeekDays(weekDays);
            ((AbstractAdjustableRepeat) repeat).setDirection(weekdayDirection);
        }

        if (repeat instanceof AbstractDateRepeat) {
            ((AbstractDateRepeat) repeat).setDate(date[0]);
        }

        if (repeat instanceof RepeatWeekly) {
            ((RepeatWeekly) repeat).setWeekDays(weekDays);
        }

        if (repeat instanceof RepeatMonthlyOnDay) {
            ((RepeatMonthlyOnDay) repeat).setDay(dayOfWeek[0]);
            ((RepeatMonthlyOnDay) repeat).setWeek(week[0]);
        }

        if (repeat instanceof RepeatSemiMonthlyOnDates) {
            ((RepeatSemiMonthlyOnDates) repeat).setDate2(date[1]);
        }

        if (repeat instanceof RepeatSemiMonthlyOnDays) {
            ((RepeatSemiMonthlyOnDays) repeat).setDay2(dayOfWeek[1]);
            ((RepeatSemiMonthlyOnDays) repeat).setWeek2(week[1]);
        }

        if (repeat instanceof RepeatYearlyOnDate) {
            ((RepeatYearlyOnDate) repeat).setMonth(month);
        }

        if (repeat instanceof RepeatYearlyOnDay) {
            ((RepeatYearlyOnDay) repeat).setMonth(month);
        }

        return repeat;
    }

    @Override
    @NonNull
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName());
        sb.append("[repeatType=").append(repeatType);
        sb.append(", dueDate=");
        if (dueDate == null)
            sb.append("null");
        else
            sb.append(dueDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
        sb.append(", increment=").append(increment);
        sb.append(", weekDays=").append(weekDays);
        sb.append(", weekdayDirection=").append(weekdayDirection);
        sb.append(", dayOfWeek=").append(Arrays.toString(dayOfWeek));
        sb.append(", week=").append(Arrays.toString(week));
        sb.append(", date=").append(Arrays.toString(date));
        sb.append(", month=").append(month);
        sb.append(", endDate=");
        if (endDate == null)
            sb.append("null");
        else
            sb.append(endDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
        sb.append(']');
        return sb.toString();
    }

}
