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
package com.xmission.trevin.android.todo.provider;

import static com.xmission.trevin.android.todo.provider.ToDoSchema.ToDoItemColumns.*;

import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.xmission.trevin.android.todo.data.AlarmInfo;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Wrapper around a {@link SQLiteDatabase} {@link Cursor}
 * used to read {@link AlarmInfo} records.
 */
public class AlarmInfoCursor {

    private static final String TAG = "AlarmInfoCursor";

    /**
     * The underlying {@link Cursor} from which
     * we obtain {@link AlarmInfo} data.
     */
    private final Cursor dbCursor;

    // Indices for our columns are set when we get the underlying cursor.
    // We expect not all queries will use all columns, so these may have
    // values of -1 for columns that are missing.
    private final int idColumn;
    private final int descriptionColumn;
    private final int modTimeColumn;
    private final int dueColumn;
    private final int privateColumn;
    private final int categoryIdColumn;
    private final int categoryNameColumn;
    private final int alarmDaysEarlierColumn;
    private final int alarmTimeColumn;
    private final int notificationTimeColumn;
    private final ZoneId timeZone;

    AlarmInfoCursor(Cursor underlyingCursor, ZoneId timeZone) {
        dbCursor = underlyingCursor;
        idColumn = dbCursor.getColumnIndex(_ID);
        descriptionColumn = dbCursor.getColumnIndex(DESCRIPTION);
        modTimeColumn = dbCursor.getColumnIndex(MOD_TIME);
        dueColumn = dbCursor.getColumnIndex(DUE_TIME);
        privateColumn = dbCursor.getColumnIndex(PRIVATE);
        categoryIdColumn = dbCursor.getColumnIndex(CATEGORY_ID);
        categoryNameColumn = dbCursor.getColumnIndex(CATEGORY_NAME);
        alarmDaysEarlierColumn = dbCursor.getColumnIndex(ALARM_DAYS_EARLIER);
        alarmTimeColumn = dbCursor.getColumnIndex(ALARM_TIME);
        notificationTimeColumn = dbCursor.getColumnIndex(NOTIFICATION_TIME);
        this.timeZone = timeZone;

        /*
         * Consistency check: in order to read the description column,
         * we also need to know whether the item is encrypted.
         */
        if (privateColumn < 0) {
            if (descriptionColumn >= 0) {
                Log.e(TAG, String.format("Internal error: data cursor"
                                + " includes the %s column but not %s;"
                                + " unable to determine how to read %s.",
                        DESCRIPTION, PRIVATE, DESCRIPTION));
                throw new SQLException(
                        "Query returns items without privacy level");
            }
        }
    }

    public void close() {
        dbCursor.close();
    }

    public int getCount() {
        return dbCursor.getCount();
    }

    /**
     * Convert an integer field to a {@link LocalDate} by interpreting it
     * as a value in milliseconds since the epoch (legacy `new Date(value)').
     *
     * @param millis the number of milliseconds from the Epoch to
     *               convert to a date
     *
     * @return the converted date (or {@code null}
     * if {@code millis} is {@code null}).
     */
    private static LocalDate millisToDate(Long millis) {
        if (millis == null)
            return null;
        return LocalDate.ofInstant(Instant.ofEpochMilli(millis),
                ZoneOffset.UTC);
    }

    /**
     * Convert an integer field to a {@link LocalTime} by interpreting it
     * as a value in milliseconds since midnight.
     *
     * @param millis the number of milliseconds since midnight
     *              to convert to a time
     *
     * @return the converted time (or {@code null}
     * if {@code millis} is {@code null}).
     */
    private static LocalTime millisToTime(Long millis) {
        if (millis == null)
            return null;
        return LocalTime.ofNanoOfDay(millis * 1000000L);
    }

    public AlarmInfo getItem() {
        AlarmInfo item = new AlarmInfo();
        if (idColumn >= 0)
            item.setId(dbCursor.getLong(idColumn));
        if (modTimeColumn >= 0)
            item.setLastModified(Instant.ofEpochMilli(
                    dbCursor.getLong(modTimeColumn)));
        if ((dueColumn >= 0) && !dbCursor.isNull(dueColumn))
            item.setDueDate(millisToDate(dbCursor.getLong(dueColumn)));
        if (privateColumn >= 0)
            item.setPrivate(dbCursor.getInt(privateColumn));
        if (categoryIdColumn >= 0)
            item.setCategoryId(dbCursor.getLong(categoryIdColumn));
        if (categoryNameColumn >= 0)
            item.setCategory(dbCursor.getString(categoryNameColumn));

        if (descriptionColumn >= 0) {
            // How we read this column depends on whether the item is encrypted
            if (item.getPrivate() <= 1) {
                // Plain text
                item.setDescription(dbCursor.getString(descriptionColumn));
            } else {
                // Encrypted; stored as a byte array
                item.setEncryptedDescription(
                        dbCursor.getBlob(descriptionColumn));
            }
        }

        if ((alarmTimeColumn >= 0) && !dbCursor.isNull(alarmTimeColumn))
            item.setAlarmTime(millisToTime(dbCursor.getLong(alarmTimeColumn)));
        if ((alarmDaysEarlierColumn >= 0) && !dbCursor.isNull(alarmDaysEarlierColumn))
            item.setDaysEarlier(dbCursor.getInt(alarmDaysEarlierColumn));
        if ((notificationTimeColumn >= 0) && !dbCursor.isNull(notificationTimeColumn))
            item.setNotificationTime(Instant.ofEpochMilli(
                    dbCursor.getLong(notificationTimeColumn)));

        item.setTimeZone(timeZone);

        return item;
    }

    public int getPosition() {
        return dbCursor.getPosition();
    }

    public boolean isClosed() {
        return dbCursor.isClosed();
    }

    public boolean moveToNext() {
        return dbCursor.moveToNext();
    }

}
