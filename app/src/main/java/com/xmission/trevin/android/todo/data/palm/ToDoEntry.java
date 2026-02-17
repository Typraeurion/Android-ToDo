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
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * To Do entry as stored in the Palm database file
 *
 * @author Trevin Beattie
 */
public class ToDoEntry {

    /** Integer field type identifier */
    public static final int TYPE_INTEGER = 1;
    /** Float field type identifier */
    public static final int TYPE_FLOAT = 2;
    /** Date field type identifier (seconds since the epoch) */
    public static final int TYPE_DATE = 3;
    /** Alpha field type identifier (?) */
    public static final int TYPE_ALPHA = 4;
    /** C string field type identifier (length prefix) */
    public static final int TYPE_CSTRING = 5;
    /** Boolean field type identifier (integer ?= 0) */
    public static final int TYPE_BOOLEAN = 6;
    /** Bit flag field type identifier */
    public static final int TYPE_BITFLAG = 7;
    /** Repeat event field type identifier */
    public static final int TYPE_REPEAT = 8;
    // Unknown data type; appears to be 4 bytes
    public static final int TYPE_UNKNOWN40 = 64;
    // Also unknown; appears to be 4 bytes
    public static final int TYPE_UNKNOWN41 = 65;
    // Also also unknown; appears to be 4 bytes
    public static final int TYPE_UNKNOWN42 = 66;
    // Differs from the data type in the records!  Appears to be 4 bytes
    public static final int TYPE_UNKNOWN43 = 67;

    /** Expected field types for version 1 */
    public static final int[] expectedFieldTypesV1 = {
            TYPE_INTEGER, TYPE_INTEGER, TYPE_INTEGER, TYPE_CSTRING,
            TYPE_DATE, TYPE_BOOLEAN, TYPE_INTEGER, TYPE_BOOLEAN,
            TYPE_INTEGER, TYPE_CSTRING
    };
    /** Expected fields for version 2 */
    public static final int[] expectedFieldTypesV2 = {
            TYPE_INTEGER, TYPE_INTEGER, TYPE_INTEGER, TYPE_INTEGER,
            TYPE_INTEGER, TYPE_INTEGER, TYPE_BOOLEAN, TYPE_UNKNOWN40,
            TYPE_UNKNOWN41, TYPE_UNKNOWN42, TYPE_UNKNOWN43,
            TYPE_CSTRING, TYPE_CSTRING, TYPE_CSTRING, TYPE_DATE,
            TYPE_BOOLEAN, TYPE_INTEGER, TYPE_CSTRING, TYPE_BOOLEAN,
            TYPE_DATE, TYPE_BOOLEAN, TYPE_DATE, TYPE_INTEGER,
            TYPE_DATE, TYPE_REPEAT };
    // Internal field identifiers; these were originally based on
    // 0-based field positions in a V2 data file, but there are
    // many unknown fields.
    public static final int FIELD_ID = 0;
    /** Record ID */
    public Integer ID;
    // In version 2 files, this field appears to be 0x1000
    // plus the index of the record.
    public static final int FIELD_PLUSK_INDEX = 1;
    public static final int FIELD_STATUS = 2;
    /** Status flags (bitmask) */
    public Integer status;
    /** Bit for the "add" flag */
    public static final int STATUS_ADD = 1;
    /** Bit for the "update" flag */
    public static final int STATUS_UPDATE = 2;
    /**
     * @deprecated This was supposed to be the bit
     * for the "delete" flag, but it isn't
     */
    public static final int STATUS_DELETE = 4;
    /** Bit for the "pending" flag */
    public static final int STATUS_PENDING = 8;
    /** Bit for the "archive" flag */
    public static final int STATUS_ARCHIVE = 0x80;
    /*
     * The "offset" has nothing to do with the To Do item itself;
     * it is the number of fields we are into the database file
     * as of the start of the record, or 25 * the record index.
     */
    public static final int FIELD_OFFSET = 3;
    public Integer offset;
    // The "position" field appears to be a constant:
    // INT_MAX (2^32-1) for v1 files, 1 for v2 files.
    public static final int FIELD_POSITION = 4;
    public Integer position;
    // Field 5 is never anything but 1 in my To Do list
    public static final int FIELD_CATEGORY = 5;
    public Integer categoryIndex;
    public static final int FIELD_PRIVATE = 6;
    public Boolean isPrivate;	// field 7
    // Field 8 is never non-zero in my To Do list
    // Field 9 is never non-zero in my To Do list
    // Field 10 is never non-zero in my To Do list
    // Field 11 is never non-zero in my To Do list
    public static final int FIELD_DESCRIPTION = 13;
    // Field 12 is never set in my To Do list.
    // Field 13 is never set in my To Do list.
    public String description;
    public static final int FIELD_DUE_DATE = 14;
    /*
     * Note: the due date is set to Dec. 31, 2031 22(?):59:59
     * for tasks which have have no due date.
     * Times appear to be set to midnight *local* time at the start
     * of the date due, not the end.  Very strange -- is there
     * something in the data file that provides the base time zone?
     */
    public Long dueDate;
    /**
     * Maximum date allowed: 2032-01-01 00:00:00
     * (in seconds since the epoch)
     */
    public static final long MAX_DATE = 1956528000L;
    public static final int FIELD_COMPLETED = 15;
    public Boolean completed;
    public static final int FIELD_PRIORITY = 16;
    public Integer priority;
    public static final int FIELD_NOTE = 17;
    public String note;
    public static final int FIELD_REPEAT_AFTER_COMPLETE = 18;
    /*
     * FIXME: I can't find any documentation on whether this flag
     * simply means the event repeats (the same as a non-null
     * `repeat' field) or if it affects _how_ the next repeat
     * is calculated (e.g. must follow the completion date).
     * For now we're going to assume this is just redundant
     * with a non-null `repeat` field.
     */
    public Boolean repeatAfterCompleted;
    public static final int FIELD_COMPLETION_DATE = 19;
    // "Unset" completion dates are also set to Dec. 31, 2031
    public Long completionDate;
    public static final int FIELD_HAS_ALARM = 20;
    public Boolean hasAlarm;
    // The alarm time is given in UTC on January 1/2, 1971*
    // if the alarm is set.  Otherwise the time is -1 (1s before 00:00 UTC).
    public static final int FIELD_ALARM_TIME = 21;
    // Unset alarm times have the value -1
    public Long alarmTime;
    public static final int FIELD_ALARM_DAYS_IN_ADVANCE = 22;
    public Integer alarmDaysInAdvance;
    // Field 24 is never anything but Dec 31, 2031 in my To Do list
    // The repeat interval is a variable-length field!
    public static final int FIELD_REPEAT = 24;
    // Placeholder for unknown fields in version 2
    public static final int FIELD_UNKNOWN = -1;
    public RepeatEvent repeat;
    /** Store any unknown fields we encounter */
    public Object[] unknownFields;

    /** Field order for version 1 */
    public static final int[] fieldOrderV1 = {
            FIELD_ID, FIELD_STATUS, FIELD_POSITION,
            FIELD_DESCRIPTION, FIELD_DUE_DATE, FIELD_COMPLETED,
            FIELD_PRIORITY, FIELD_PRIVATE, FIELD_CATEGORY,
            FIELD_NOTE
    };
    /** Field order for version 2 */
    public static final int[] fieldOrderV2 = {
            FIELD_ID, FIELD_PLUSK_INDEX, FIELD_STATUS, FIELD_OFFSET,
            FIELD_POSITION, FIELD_CATEGORY, FIELD_PRIVATE, FIELD_UNKNOWN,
            FIELD_UNKNOWN, FIELD_UNKNOWN, FIELD_UNKNOWN, FIELD_UNKNOWN,
            FIELD_UNKNOWN, FIELD_DESCRIPTION, FIELD_DUE_DATE,
            FIELD_COMPLETED, FIELD_PRIORITY, FIELD_NOTE,
            FIELD_REPEAT_AFTER_COMPLETE, FIELD_COMPLETION_DATE,
            FIELD_HAS_ALARM, FIELD_ALARM_TIME,
            FIELD_ALARM_DAYS_IN_ADVANCE, FIELD_UNKNOWN, FIELD_REPEAT
    };

    @Override
    @NonNull
    public String toString() {
        StringBuilder sb = new StringBuilder("ToDoEntry[");
        if (ID != null) {
            sb.append(String.format(Locale.US, "ID=%8d,", ID));
        }
        if (status != null) {
            sb.append("status=[");
            sb.append(((status & STATUS_ADD) != 0) ? '+' : ' ');
            sb.append(((status & STATUS_UPDATE) != 0) ? '^' : ' ');
            sb.append(((status & STATUS_DELETE) != 0) ? '-' : ' ');
            sb.append(((status & STATUS_PENDING) != 0) ? '.' : ' ');
            sb.append(((status & 0x10) != 0) ? '?' : ' ');
            sb.append(((status & 0x20) != 0) ? '?' : ' ');
            sb.append(((status & 0x40) != 0) ? '?' : ' ');
            sb.append(((status & STATUS_ARCHIVE) != 0) ? 'A' : ' ');
            sb.append(((status & 0x100) != 0) ? '?' : ' ');
            sb.append(((status & 0x1000) != 0) ? '?' : ' ');
            // I haven't seen any other bits set in this field.
            sb.append("],");
        }
        if (position != null)
            sb.append(String.format(Locale.US,
                    "position=%5d,", position));
        if (categoryIndex != null)
            sb.append(String.format(Locale.US,
                    "categoryIndex=%2d,", categoryIndex));
        if (isPrivate != null)
            sb.append(isPrivate ? "priv" : "publ").append(',');
        if (description != null)
            sb.append("description=\"").append(description
                            .replace("\\", "\\\\")
                            .replace("\r", "\\r")
                            .replace("\n", "\\n")
            ).append("\",");
        if (dueDate != null) {
            ZonedDateTime due = ZonedDateTime.ofInstant(
                    Instant.ofEpochSecond(dueDate), ZoneOffset.UTC);
            sb.append("dueDate=\"").append(due.format(
                    DateTimeFormatter.RFC_1123_DATE_TIME)).append("\",");
        }
        if (completed != null)
            sb.append("completed=").append(completed
                    ? "yes" : " no").append(',');
        if (priority != null)
            sb.append("priority=").append(priority).append(',');
        if (note != null)
            sb.append("note=\"").append(note).append("\",");
        if (completionDate != null) {
            ZonedDateTime completed = ZonedDateTime.ofInstant(
                    Instant.ofEpochSecond(completionDate),
                    ZoneOffset.UTC);
            sb.append("completionDate=\"").append(completed.format(
                    DateTimeFormatter.RFC_1123_DATE_TIME)).append("\",");
        }
        if (hasAlarm != null) {
            sb.append("hasAlarm=").append(hasAlarm).append(',');
            ZonedDateTime alarm = ZonedDateTime.ofInstant(
                    Instant.ofEpochSecond(alarmTime), ZoneOffset.UTC);
            sb.append("alarmTime=\"").append(alarm.format(
                    DateTimeFormatter.RFC_1123_DATE_TIME)).append("\",");
            sb.append("alarmDaysInAdvance=").append(alarmDaysInAdvance).append(',');
        }
        if (repeat != null) {
            sb.append(repeat);
//                if (repeatAfterCompleted)
//                    sb.append("(after last completed)");
            sb.append(',');
        }
        sb.append("\n\t");
        int[] fields = (unknownFields.length < expectedFieldTypesV2.length)
                ? fieldOrderV1 : fieldOrderV2;
        for (int j = 0; j < fields.length; j++) {
            switch (fields[j]) {
                case FIELD_ID:
                case FIELD_STATUS:
                case FIELD_OFFSET:
                case FIELD_POSITION:
                case FIELD_CATEGORY:
                case FIELD_PRIVATE:
                case FIELD_DESCRIPTION:
                case FIELD_DUE_DATE:
                case FIELD_COMPLETED:
                case FIELD_PRIORITY:
                case FIELD_NOTE:
                case FIELD_REPEAT_AFTER_COMPLETE:
                case FIELD_COMPLETION_DATE:
                case FIELD_HAS_ALARM:
                case FIELD_ALARM_TIME:
                case FIELD_ALARM_DAYS_IN_ADVANCE:
                case FIELD_REPEAT:
                    break;
                default:
                    if (unknownFields[j] != null) {
                        sb.append(String.format(Locale.US,
                                "unknown%02d=", j + 1));
                        if ((unknownFields[j] instanceof Number) ||
                                (unknownFields[j] instanceof Boolean))
                            sb.append(unknownFields[j]).append(',');
                        else
                            sb.append('"').append(unknownFields[j])
                                    .append("\",");
                    }
                    break;
            }
        }
        sb.append(']');
        return sb.toString();
    }
}
