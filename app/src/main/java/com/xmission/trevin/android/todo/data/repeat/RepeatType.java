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
package com.xmission.trevin.android.todo.data.repeat;

import static com.xmission.trevin.android.todo.provider.ToDoSchema.ToDoItemColumns.*;

import android.util.Log;

import androidx.annotation.NonNull;

import java.time.LocalDate;

/**
 * Enumeration of the different types of repeating intervals
 * supported by the To Do app.  This includes references
 * to the object classes which implement them.
 */
public enum RepeatType {

    NONE(REPEAT_NONE, RepeatNone.class),
    DAILY(REPEAT_DAILY, RepeatDaily.class),
    DAY_AFTER(REPEAT_DAY_AFTER, RepeatDayAfter.class),
    WEEKLY(REPEAT_WEEKLY, RepeatWeekly.class),
    WEEK_AFTER(REPEAT_WEEK_AFTER, RepeatWeekAfter.class),
    SEMI_MONTHLY_ON_DAYS(REPEAT_SEMI_MONTHLY_ON_DAYS,
            RepeatSemiMonthlyOnDays.class),
    SEMI_MONTHLY_ON_DATES(REPEAT_SEMI_MONTHLY_ON_DATES,
            RepeatSemiMonthlyOnDates.class),
    MONTHLY_ON_DAY(REPEAT_MONTHLY_ON_DAY, RepeatMonthlyOnDay.class),
    MONTHLY_ON_DATE(REPEAT_MONTHLY_ON_DATE, RepeatMonthlyOnDate.class),
    MONTH_AFTER(REPEAT_MONTH_AFTER, RepeatMonthAfter.class),
    YEARLY_ON_DAY(REPEAT_YEARLY_ON_DAY, RepeatYearlyOnDay.class),
    YEARLY_ON_DATE(REPEAT_YEARLY_ON_DATE, RepeatYearlyOnDate.class),
    YEAR_AFTER(REPEAT_YEAR_AFTER, RepeatYearAfter.class);

    /** Internal ID of the interval type, as stored in the database */
    private final int id;
    /**
     * The class implementing this interval type.
     * Must support both a no-argument constructor
     * and a constructor that takes a {@link LocalDate} (due date).
     */
    @NonNull
    private final Class<? extends RepeatInterval> implementingClass;

    RepeatType(int typeId, Class<? extends RepeatInterval> clazz) {
        id = typeId;
        implementingClass = clazz;
    }

    /**
     * Create an instance of the {@link RepeatInterval} object
     * for the given repeat type.  Its fields will be initialized
     * with default values assuming the next due date is today.
     *
     * @param typeId the ID of the repeat interval to create
     *
     * @return an instance of the {@link RepeatInterval} object
     * for the given repeat type.
     *
     * @throws IllegalArgumentException if {@code typeId}
     * does not correspond to any implemented type.
     */
    @NonNull
    public static RepeatInterval newInstance(int typeId) {
        for (RepeatType type : RepeatType.values()) {
            if (type.id == typeId)
                try {
                    return type.implementingClass
                            .getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    Log.e("RepeatType", String.format(
                            "Failed to create a %s (type %d)",
                            type.getClass().getSimpleName(), typeId), e);
                }
        }
        throw new IllegalArgumentException("Unknown repeat type: " + typeId);
    }

    /**
     * Create an instance of the {@link RepeatInterval} object for the
     * given repeat type, initializing its fields on the basis of
     * a given due date.
     *
     * @param typeId the ID of the repeat interval to create
     * @param due the first date on which this To Do item is due
     *
     * @return an instance of the {@link RepeatInterval} object
     * for the given repeat type.
     *
     * @throws IllegalArgumentException if {@code typeId}
     * does not correspond to any implemented type.
     */
    @NonNull
    public static RepeatInterval newInstance(int typeId, LocalDate due) {
        for (RepeatType type : RepeatType.values()) {
            if (type.id == typeId)
                try {
                    return type.implementingClass
                            .getDeclaredConstructor(LocalDate.class)
                            .newInstance(due);
                } catch (Exception e) {
                    Log.e("RepeatType", String.format(
                            "Failed to create a %s (type %d)",
                            type.getClass().getSimpleName(), typeId), e);
                }
        }
        throw new IllegalArgumentException("Unknown repeat type: " + typeId);
    }

    /** @return the ID of this repeat interval (as stored in the database) */
    public int getId() {
        return id;
    }

}
