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
package com.xmission.trevin.android.todo.data.repeat;

import static com.xmission.trevin.android.todo.provider.ToDoSchema.ToDoItemColumns.*;

import androidx.annotation.NonNull;

import java.time.DayOfWeek;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Days of the week for internal use.  This provides mappings
 * between Java&rsquo;s {@link DayOfWeek} enumeration and
 * bits or values we store in the database.
 */
public enum WeekDays {

    SUNDAY(DayOfWeek.SUNDAY, REPEAT_SUNDAYS, 0, "Sunday"),
    MONDAY(DayOfWeek.MONDAY, REPEAT_MONDAYS, 1, "Monday"),
    TUESDAY(DayOfWeek.TUESDAY, REPEAT_TUESDAYS, 2, "Tuesday"),
    WEDNESDAY(DayOfWeek.WEDNESDAY, REPEAT_WEDNESDAYS, 3, "Wednesday"),
    THURSDAY(DayOfWeek.THURSDAY, REPEAT_THURSDAYS, 4, "Thursday"),
    FRIDAY(DayOfWeek.FRIDAY, REPEAT_FRIDAYS, 5, "Friday"),
    SATURDAY(DayOfWeek.SATURDAY, REPEAT_SATURDAYS, 6, "Saturday");

    /** The official Java equivalent of this day */
    @NonNull
    private final DayOfWeek javaDay;
    /** The bit used to flag this day&rsquo;s entry */
    private final int bitValue;
    /** The value of this day when stored as a discrete number */
    private final int value;
    /** Display name of the day; exclusively for logging purposes */
    @NonNull
    private final String displayName;

    /** The full mask of bits for all days of the week */
    public static final int DAYS_BIT_MASK = (
            REPEAT_SUNDAYS | REPEAT_MONDAYS | REPEAT_TUESDAYS |
                    REPEAT_WEDNESDAYS | REPEAT_THURSDAYS |
                    REPEAT_FRIDAYS | REPEAT_SATURDAYS);

    WeekDays(DayOfWeek java, int bit, int v, String name) {
        javaDay = java;
        bitValue = bit;
        value = v;
        displayName = name;
    }

    /** @return the Java equivalent of this day enum */
    @NonNull
    public DayOfWeek getJavaDay() {
        return javaDay;
    }

    /** @return the bit mask for this day */
    public int getBitMask() {
        return bitValue;
    }

    /** @return the ordinal value for this day */
    public int getValue() {
        return value;
    }

    /** @return the display name of this day */
    @Override
    @NonNull
    public String toString() {
        return displayName;
    }

    /**
     * Given a Java {@link DayOfWeek}, return our
     * corresponding enum value
     *
     * @param day the Java day of the week
     *
     * @return our day of the week
     */
    @NonNull
    public static WeekDays fromJavaDay(DayOfWeek day) {
        for (WeekDays wd : values())
            if (wd.javaDay == day)
                return wd;
        throw new IllegalArgumentException("Unknown day of week: " + day);
    }

    /**
     * Given a bit map, return the days of the week which
     * are present.  The returned set is sorted and modifiable.
     */
    @NonNull
    public static SortedSet<WeekDays> fromBitMap(int bitMap) {
        SortedSet<WeekDays> days = new TreeSet<>();
        for (WeekDays wd : values())
            if ((bitMap & wd.bitValue) != 0)
                days.add(wd);
        return days;
    }

    /**
     * Given an ordinal value (as we store in the database),
     * return the corresponding day of the week.
     *
     * @param value the value of the day to match
     *
     * @return the day of the week
     */
    @NonNull
    public static WeekDays fromValue(int value) {
        for (WeekDays wd : values())
            if (wd.value == value)
                return wd;
        throw new IllegalArgumentException(
                "Unknown day of the week value: " + value);
    }

    /**
     * Given a set of days of the week, return a bit map
     * with those days&rsquo; bits set.
     *
     * @param days the days of the week to include in the bit map
     *
     * @return the bit map
     */
    public static int toBitMap(Set<WeekDays> days) {
        int bitMap = 0;
        for (WeekDays wd : days)
            bitMap |= wd.bitValue;
        return bitMap;
    }

}
