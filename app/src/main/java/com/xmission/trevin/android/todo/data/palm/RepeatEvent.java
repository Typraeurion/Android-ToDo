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
package com.xmission.trevin.android.todo.data.palm;

import androidx.annotation.NonNull;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Structure of a repeat event in the Palm database file
 *
 * @author Trevin Beattie
 */
public class RepeatEvent {
    // The tag may be a short following a zero short.
    // The high (15th) bit is always set.  I do not know
    // what it means; my initial assessment was incorrect.
    public short tag;
    static final short TAG_UNKNOWN_01 = (short) 0x8001;
    static final short TAG_UNKNOWN_03 = (short) 0x8003;
    static final short TAG_UNKNOWN_0F = (short) 0x800f;
    static final short TAG_UNKNOWN_1B = (short) 0x801b;
    static final short TAG_UNKNOWN_24 = (short) 0x8024;
    static final short TAG_UNKNOWN_30 = (short) 0x8030;
    // If the tag is 0xffff, it is followed by a short of 1,
    // another short string length, and a string with the repeat type.
    public String typeName;
    public static final String NAME_REPEAT_BY_DAY = "CDayName";
    public static final String NAME_REPEAT_BY_WEEK = "CWeekly";
    public static final String NAME_REPEAT_BY_MONTH_DATE = "CDateOfMonth";
    public static final String NAME_REPEAT_BY_MONTH_DAY = "CDayOfMonth";
    public static final String NAME_REPEAT_BY_YEAR = "CDateOfYear";
    public int type; 	// integer
    // These values are in the first int past the optional type tag
    public static final int TYPE_REPEAT_BY_DAY = 1;
    public static final int TYPE_REPEAT_BY_WEEK = 2;
    public static final int TYPE_REPEAT_BY_MONTH_DAY = 3;
    public static final int TYPE_REPEAT_BY_MONTH_DATE = 4;
    public static final int TYPE_REPEAT_BY_YEAR = 5;
    public int interval; 	// "every N days/weeks/months/years"; integer
    // Indefinite repeats have the last date set to Dec. 31, 2031
    public long repeatUntil;	// date
    // I have no idea what's in the next field; it's always zero.
    // I think the rest of the fields depend on the repeat type.
    // The last field for daily repeats is the day of the week
    // (Sun=0, Mon=1, ..., Fri=5, Sat=6).
    public Integer dayOfWeek;
    // The next field for weekly repeats is always 1, but it is followed
    // by a single byte containing which days of the week the event
    // occurs on: Sun=1, Mon=2, Tue=4, Wed=8, Thu=16, Fri=32, Sat=64.
    public Byte dayOfWeekBitmap;
    // For monthly by date events, the last field is the date.
    public Integer dateOfMonth;
    // For monthly by day events, the fields after the zero appears to be
    // the day of the week (Sun=0, Sat=6),
    // followed by the week of the month (1st=0, last=4).
    public Integer weekOfMonth;
    // For annual events, the fields after the zero appears to be the
    // date of the month followed by the month of the year (Jan=0, Dec=11).
    public Integer monthOfYear;
    // There is no distinction made in the entire repeat field
    // between events on a fixed schedule and events after last completed!

    /**
     * RFC-1123-like date-only formatter
     * for the {@link #toString()} method
     */
    public static final DateTimeFormatter RFC_1123_DATE =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy");

    /** @return a String representation of this repeat event */
    @Override
    @NonNull
    public String toString() {
        StringBuilder sb = new StringBuilder("RepeatEvent[");
        sb.append(String.format("tag=%04x,", tag));
        if (typeName != null)
            sb.append("typeName=\"").append(typeName).append("\",");
        sb.append("type=");
        switch (type) {
            case TYPE_REPEAT_BY_DAY:
                sb.append("daily");
                break;
            case TYPE_REPEAT_BY_WEEK:
                sb.append("weekly");
                break;
            case TYPE_REPEAT_BY_MONTH_DATE:
                sb.append("monthly(date)");
                break;
            case TYPE_REPEAT_BY_MONTH_DAY:
                sb.append("monthly(day)");
                break;
            case TYPE_REPEAT_BY_YEAR: sb.append("yearly"); break;
            default: sb.append(type); break;
        }
        sb.append(",interval=").append(interval).append(',');
        LocalDate until = ZonedDateTime.ofInstant(
                Instant.ofEpochSecond(repeatUntil), ZoneOffset.UTC)
                .toLocalDate();
        sb.append("repeatUntil=\"").append(until.format(RFC_1123_DATE))
                .append('"');
        if (dayOfWeekBitmap != null) {
            sb.append(",days=");
            if ((dayOfWeekBitmap & 1) != 0)
                sb.append("Sun,");
            if ((dayOfWeekBitmap & 2) != 0)
                sb.append("Mon,");
            if ((dayOfWeekBitmap & 4) != 0)
                sb.append("Tue,");
            if ((dayOfWeekBitmap & 8) != 0)
                sb.append("Wed,");
            if ((dayOfWeekBitmap & 16) != 0)
                sb.append("Thu,");
            if ((dayOfWeekBitmap & 32) != 0)
                sb.append("Fri,");
            if ((dayOfWeekBitmap & 64) != 0)
                sb.append("Sat,");
            if (dayOfWeekBitmap != 0)
                sb.deleteCharAt(sb.length() - 1);
        }
        if (dateOfMonth != null)
            sb.append(",date=").append(dateOfMonth);
        if (dayOfWeek != null) {
            sb.append(",day=");
            switch (dayOfWeek) {
                case 0: sb.append("Sunday"); break;
                case 1: sb.append("Monday"); break;
                case 2: sb.append("Tuesday"); break;
                case 3: sb.append("Wednesday"); break;
                case 4: sb.append("Thursday"); break;
                case 5: sb.append("Friday"); break;
                case 6: sb.append("Saturday"); break;
                default: sb.append(dayOfWeek); break;
            }
        }
        if (weekOfMonth != null) {
            sb.append(",week=");
            if (weekOfMonth == 4)
                sb.append("last");
            else
                sb.append(weekOfMonth + 1);
        }
        if (monthOfYear != null) {
            sb.append(",month=");
            switch (monthOfYear) {
                case 0: sb.append("January"); break;
                case 1: sb.append("February"); break;
                case 2: sb.append("March"); break;
                case 3: sb.append("April"); break;
                case 4: sb.append("May"); break;
                case 5: sb.append("June"); break;
                case 6: sb.append("July"); break;
                case 7: sb.append("August"); break;
                case 8: sb.append("September"); break;
                case 9: sb.append("October"); break;
                case 10: sb.append("November"); break;
                case 11: sb.append("December"); break;
                default: sb.append(monthOfYear); break;
            }
        }
        sb.append(']');
        return sb.toString();
    }
}
