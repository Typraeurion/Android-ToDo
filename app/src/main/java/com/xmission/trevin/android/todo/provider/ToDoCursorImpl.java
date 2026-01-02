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

import com.xmission.trevin.android.todo.data.ToDoAlarm;
import com.xmission.trevin.android.todo.data.ToDoItem;
import com.xmission.trevin.android.todo.data.repeat.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;

/**
 * Run-time implementation of the {@link ToDoCursor}.
 * This should only be used by the {@link ToDoRepositoryImpl}
 * to provide a wrapper around the {@link Cursor} provided by
 * a {@link SQLiteDatabase}.
 *
 * @author Trevin Beattie
 */
public class ToDoCursorImpl implements ToDoCursor {

    private static final String TAG = "ToDoCursorImpl";

    /**
     * The underlying {@link Cursor} from which
     * we obtain {@link ToDoItem} data.
     */
    private final Cursor dbCursor;

    // Indices for our columns are set when we get the underlying cursor.
    // We expect not all queries will use all columns, so these may have
    // values of -1 for columns that are missing.
    private final int idColumn;
    private final int descriptionColumn;
    private final int createTimeColumn;
    private final int modTimeColumn;
    private final int dueColumn;
    private final int completedColumn;
    private final int checkedColumn;
    private final int priorityColumn;
    private final int privateColumn;
    private final int categoryIdColumn;
    private final int categoryNameColumn;
    private final int noteColumn;
    private final int alarmDaysEarlierColumn;
    private final int alarmTimeColumn;
    private final int repeatIntervalColumn;
    private final int repeatIncrementColumn;
    private final int repeatWeekDaysColumn;
    private final int repeatDayColumn;
    private final int repeatDay2Column;
    private final int repeatWeekColumn;
    private final int repeatWeek2Column;
    private final int repeatMonthColumn;
    private final int repeatEndColumn;
    private final int hideDaysEarlierColumn;
    private final int notificationTimeColumn;

    ToDoCursorImpl(Cursor underlyingCursor) {
        dbCursor = underlyingCursor;
        idColumn = dbCursor.getColumnIndex(_ID);
        descriptionColumn = dbCursor.getColumnIndex(DESCRIPTION);
        createTimeColumn = dbCursor.getColumnIndex(CREATE_TIME);
        modTimeColumn = dbCursor.getColumnIndex(MOD_TIME);
        dueColumn = dbCursor.getColumnIndex(DUE_TIME);
        completedColumn = dbCursor.getColumnIndex(COMPLETED_TIME);
        checkedColumn = dbCursor.getColumnIndex(CHECKED);
        priorityColumn = dbCursor.getColumnIndex(PRIORITY);
        privateColumn = dbCursor.getColumnIndex(PRIVATE);
        categoryIdColumn = dbCursor.getColumnIndex(CATEGORY_ID);
        categoryNameColumn = dbCursor.getColumnIndex(CATEGORY_NAME);
        noteColumn = dbCursor.getColumnIndex(NOTE);
        alarmDaysEarlierColumn = dbCursor.getColumnIndex(ALARM_DAYS_EARLIER);
        alarmTimeColumn = dbCursor.getColumnIndex(ALARM_TIME);
        repeatIntervalColumn = dbCursor.getColumnIndex(REPEAT_INTERVAL);
        repeatIncrementColumn = dbCursor.getColumnIndex(REPEAT_INCREMENT);
        repeatWeekDaysColumn = dbCursor.getColumnIndex(REPEAT_WEEK_DAYS);
        repeatDayColumn = dbCursor.getColumnIndex(REPEAT_DAY);
        repeatDay2Column = dbCursor.getColumnIndex(REPEAT_DAY2);
        repeatWeekColumn = dbCursor.getColumnIndex(REPEAT_WEEK);
        repeatWeek2Column = dbCursor.getColumnIndex(REPEAT_WEEK2);
        repeatMonthColumn = dbCursor.getColumnIndex(REPEAT_MONTH);
        repeatEndColumn = dbCursor.getColumnIndex(REPEAT_END);
        hideDaysEarlierColumn = dbCursor.getColumnIndex(HIDE_DAYS_EARLIER);
        notificationTimeColumn = dbCursor.getColumnIndex(NOTIFICATION_TIME);

        /*
         * Consistency check: in order to read the description and note
         * columns, we also need to know whether the item is encrypted.
         */
        if (privateColumn < 0) {
            if (descriptionColumn >= 0)
                Log.e(TAG, String.format("Internal error: data cursor"
                                + " includes the %s column but not %s;"
                                + " unable to determine how to read %s.",
                        DESCRIPTION, PRIVATE, DESCRIPTION));
            if (noteColumn >= 0)
                Log.e(TAG, String.format("Internal error: data cursor"
                                + " includes the %s column but not %s;"
                                + " unable to determine how to read %s.",
                        NOTE, PRIVATE, NOTE));
            if ((descriptionColumn >= 0) || (noteColumn >= 0))
                throw new SQLException(
                        "Query returns items without privacy level");
        }
    }

    @Override
    public void close() {
        dbCursor.close();
    }

    @Override
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

    @Override
    public ToDoItem getItem() {
        ToDoItem item = new ToDoItem();
        if (idColumn >= 0)
            item.setId(dbCursor.getLong(idColumn));
        if (createTimeColumn >= 0)
            item.setCreateTime(dbCursor.getLong(createTimeColumn));
        if (modTimeColumn >= 0)
            item.setModTime(dbCursor.getLong(modTimeColumn));
        if (dueColumn >= 0)
            item.setDue(dbCursor.getLong(dueColumn));
        if (completedColumn >= 0)
            item.setCompleted(dbCursor.getLong(completedColumn));
        if (checkedColumn >= 0)
            item.setChecked(dbCursor.getInt(checkedColumn) != 0);
        if (priorityColumn >= 0)
            item.setPriority(dbCursor.getInt(priorityColumn));
        if (privateColumn >= 0)
            item.setPrivate(dbCursor.getInt(privateColumn));
        if (categoryIdColumn >= 0)
            item.setCategoryId(dbCursor.getLong(categoryIdColumn));
        if (categoryNameColumn >= 0)
            item.setCategoryName(dbCursor.getString(categoryNameColumn));

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

        if (noteColumn >= 0) {
            if (item.getPrivate() <= 1) {
                item.setNote(dbCursor.getString(noteColumn));
            } else {
                item.setEncryptedNote(dbCursor.getBlob(noteColumn));
            }
        }

        if ((alarmTimeColumn >= 0)&& !dbCursor.isNull(alarmTimeColumn)) {
            ToDoAlarm alarm = new ToDoAlarm(millisToTime(
                    dbCursor.getLong(alarmTimeColumn)),
                    dbCursor.isNull(alarmDaysEarlierColumn) ? 0
                    : dbCursor.getInt(alarmDaysEarlierColumn));
            if ((notificationTimeColumn >= 0) &&
                    !dbCursor.isNull(notificationTimeColumn))
                alarm.setNotificationTime(Instant.ofEpochMilli(
                        dbCursor.getLong(notificationTimeColumn)));
            item.setAlarm(alarm);
        }

        if ((repeatIntervalColumn >= 0) &&
                !dbCursor.isNull(repeatIntervalColumn)) {
            RepeatInterval repeat = RepeatType.newInstance(
                    dbCursor.getInt(repeatIntervalColumn));
            if (repeat instanceof AbstractRepeat) {
                AbstractRepeat ar = (AbstractRepeat) repeat;
                if ((repeatIncrementColumn >= 0) &&
                        !dbCursor.isNull(repeatIncrementColumn))
                    ar.setIncrement(dbCursor.getInt(repeatIncrementColumn));
                if ((repeatEndColumn >= 0) &&
                        !dbCursor.isNull(repeatEndColumn))
                    ar.setEnd(millisToDate(dbCursor.getLong(repeatEndColumn)));
            }
            if (repeat instanceof AbstractAdjustableRepeat) {
                AbstractAdjustableRepeat aar = (AbstractAdjustableRepeat) repeat;
                if ((repeatWeekDaysColumn >= 0) &&
                        !dbCursor.isNull(repeatWeekDaysColumn)) {
                    int bitMap = dbCursor.getInt(repeatWeekDaysColumn);
                    aar.setAllowedWeekDays(WeekDays.fromBitMap(bitMap));
                    aar.setDirection(WeekdayDirection.fromBitMap(bitMap));
                }
            }
            if (repeat instanceof AbstractDateRepeat) {
                AbstractDateRepeat adr = (AbstractDateRepeat) repeat;
                if ((repeatDayColumn >= 0) &&
                        !dbCursor.isNull(repeatDayColumn))
                    adr.setDate(dbCursor.getInt(repeatDayColumn));
            }
            if (repeat instanceof RepeatMonthlyOnDay) {
                RepeatMonthlyOnDay rmd = (RepeatMonthlyOnDay) repeat;
                if ((repeatDayColumn >= 0) &&
                        !dbCursor.isNull(repeatDayColumn))
                    rmd.setDay(WeekDays.fromValue(
                            dbCursor.getInt(repeatDayColumn)));
                if ((repeatWeekColumn >= 0) &&
                        !dbCursor.isNull(repeatWeekColumn))
                    rmd.setWeek(dbCursor.getInt(repeatWeekColumn));
            }
            if (repeat instanceof RepeatSemiMonthlyOnDates) {
                RepeatSemiMonthlyOnDates rsmd = (RepeatSemiMonthlyOnDates) repeat;
                if ((repeatDay2Column >= 0) &&
                        !dbCursor.isNull(repeatDay2Column))
                    rsmd.setDate2(dbCursor.getInt(repeatDay2Column));
            }
            if (repeat instanceof RepeatSemiMonthlyOnDays) {
                RepeatSemiMonthlyOnDays rsmd = (RepeatSemiMonthlyOnDays) repeat;
                if ((repeatDay2Column >= 0) &&
                        !dbCursor.isNull(repeatDay2Column))
                    rsmd.setDay2(WeekDays.fromValue(
                            dbCursor.getInt(repeatDay2Column)));
                if ((repeatWeek2Column >= 0) &&
                        !dbCursor.isNull(repeatWeek2Column))
                    rsmd.setWeek2(dbCursor.getInt(repeatWeek2Column));
            }
            if (repeat instanceof RepeatWeekly) {
                RepeatWeekly rw = (RepeatWeekly) repeat;
                if ((repeatWeekDaysColumn >= 0) &&
                        !dbCursor.isNull(repeatWeekDaysColumn))
                    rw.setWeekDays(WeekDays.fromBitMap(
                            dbCursor.getInt(repeatWeekDaysColumn)));
            }
            if (repeat instanceof RepeatYearlyOnDate) {
                RepeatYearlyOnDate ryd = (RepeatYearlyOnDate) repeat;
                if ((repeatMonthColumn >= 0) &&
                        !dbCursor.isNull(repeatMonthColumn))
                    ryd.setMonth(Months.fromValue(
                            dbCursor.getInt(repeatMonthColumn)));
            }
            if (repeat instanceof RepeatYearlyOnDay) {
                RepeatYearlyOnDay ryd = (RepeatYearlyOnDay) repeat;
                if ((repeatMonthColumn >= 0) &&
                        !dbCursor.isNull(repeatMonthColumn))
                    ryd.setMonth(Months.fromValue(
                            dbCursor.getInt(repeatMonthColumn)));
            }
            item.setRepeatInterval(repeat);
        }

        if ((hideDaysEarlierColumn >= 0) &&
                !dbCursor.isNull(hideDaysEarlierColumn))
            item.setHideDaysEarlier(dbCursor.getInt(hideDaysEarlierColumn));

        return item;
    }

    @Override
    public int getPosition() {
        return dbCursor.getPosition();
    }

    @Override
    public boolean isAfterLast() {
        return dbCursor.isAfterLast();
    }

    @Override
    public boolean isBeforeFirst() {
        return dbCursor.isBeforeFirst();
    }

    @Override
    public boolean isClosed() {
        return dbCursor.isClosed();
    }

    @Override
    public boolean isFirst() {
        return dbCursor.isFirst();
    }

    @Override
    public boolean isLast() {
        return dbCursor.isLast();
    }

    @Override
    public boolean move(int offset) {
        return dbCursor.move(offset);
    }

    @Override
    public boolean moveToFirst() {
        return dbCursor.moveToFirst();
    }

    @Override
    public boolean moveToLast() {
        return dbCursor.moveToLast();
    }

    @Override
    public boolean moveToNext() {
        return dbCursor.moveToNext();
    }

    @Override
    public boolean moveToPosition(int position) {
        return dbCursor.moveToPosition(position);
    }

    @Override
    public boolean moveToPrevious() {
        return dbCursor.moveToPrevious();
    }

}
