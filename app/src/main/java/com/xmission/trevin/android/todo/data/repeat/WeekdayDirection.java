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

import androidx.annotation.NonNull;

/**
 * Which direction to look for the next available date,
 * if the target date falls on an unavailable day of the week.
 */
public enum WeekdayDirection {

    NEXT(0, "Next allowed day"),
    PREVIOUS(REPEAT_PREVIOUS_WEEKDAY, "Previous allowed day"),
    CLOSEST_OR_NEXT(REPEAT_CLOSEST_WEEKDAY,
            "Closest allowed day (next if tied)"),
    CLOSEST_OR_PREVIOUS(REPEAT_PREVIOUS_WEEKDAY | REPEAT_CLOSEST_WEEKDAY,
            "Closest allowed day (previous if tied)");

    /** Value of this direction as stored in the database */
    private final int value;
    /** Display name of the direction; exclusively for logging purposes */
    @NonNull
    private final String displayName;

    /** The full mask of bits for all directions */
    public static final int DIRECTION_BIT_MASK = (
            REPEAT_PREVIOUS_WEEKDAY | REPEAT_CLOSEST_WEEKDAY);

    WeekdayDirection(int v, String name) {
        value = v;
        displayName = name;
    }

    /**
     * @return the value of this direction as stored in the database
     */
    public int getValue() {
        return value;
    }

    /**
     * Given a bit map, return the direction from the
     * corresponding field.
     */
    @NonNull
    public static WeekdayDirection fromBitMap(int bitMap) {
        return fromValue(bitMap & DIRECTION_BIT_MASK);
    }

    /**
     * Given the value of this direction, return the corresponding
     * direction enum.
     *
     * @param value the value of the direction to match
     *
     * @return the direction
     */
    @NonNull
    public static WeekdayDirection fromValue(int value) {
        for (WeekdayDirection wd : values())
            if (wd.value == value)
                return wd;
        throw new IllegalArgumentException(
                "Unknown weekday direction value: " + value);
    }

    /** @return the display name of this direction */
    @Override
    @NonNull
    public String toString() {
        return displayName;
    }

}
