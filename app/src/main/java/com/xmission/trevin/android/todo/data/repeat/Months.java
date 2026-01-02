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

import androidx.annotation.NonNull;

import java.time.Month;

public enum Months {

    JANUARY(Month.JANUARY, 0, "January"),
    FEBRUARY(Month.FEBRUARY, 1, "February"),
    MARCH(Month.MARCH, 2, "March"),
    APRIL(Month.APRIL, 3, "April"),
    MAY(Month.MAY, 4, "May"),
    JUNE(Month.JUNE, 5, "June"),
    JULY(Month.JULY, 6, "July"),
    AUGUST(Month.AUGUST, 7, "August"),
    SEPTEMBER(Month.SEPTEMBER, 8, "September"),
    OCTOBER(Month.OCTOBER, 9, "October"),
    NOVEMBER(Month.NOVEMBER, 10, "November"),
    DECEMBER(Month.DECEMBER, 11, "December");

    /** The official Java equivalent of this month */
    @NonNull
    private final Month javaMonth;

    /** Interval value for this month */
    private final int value;
    /** Display name of the month; exclusively for logging purposes */
    @NonNull
    private final String displayName;


    Months(Month java, int v, String name) {
        javaMonth = java;
        value = v;
        displayName = name;
    }

    /** @return the Java equivalent of this month enum */
    @NonNull
    public Month getJavaMonth() {
        return javaMonth;
    }

    /** @return the ordinal value for this month */
    public int getValue() {
        return value;
    }

    /** @return the display name of this month */
    @Override
    @NonNull
    public String toString() {
        return displayName;
    }

    /**
     * Given a Java month, return our
     * corresponding enum value
     *
     * @param month the Java month
     *
     * @return our month
     */
    @NonNull
    public static Months fromJavaMonth(Month month) {
        for (Months m : values())
            if (m.javaMonth == month)
                return m;
        throw new IllegalArgumentException("Unknown month: " + month);
    }

    /**
     * Given an ordinal value (as we store in the database),
     * return the corresponding month.
     *
     * @param value the value of the month to match
     *
     * @return the month
     */
    @NonNull
    public static Months fromValue(int value) {
        for (Months m : values())
            if (m.value == value)
                return m;
        throw new IllegalArgumentException(
                "Unknown month value: " + value);
    }

}
