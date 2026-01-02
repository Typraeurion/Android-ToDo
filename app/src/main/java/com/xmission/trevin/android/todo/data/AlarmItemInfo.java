/*
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
 */
package com.xmission.trevin.android.todo.data;

import android.database.Cursor;
import androidx.annotation.NonNull;

import com.xmission.trevin.android.todo.provider.ToDoSchema;

import java.util.Calendar;
import java.util.Date;

/**
 * Keep track of notifications we have already sent since the service
 * was activated.  For each item with an alarm we need to know its
 * ID, the time it was last modified when we looked at it, the
 * time its next alarm should go off, and when it will be overdue.
 * If an item is later modified, any previous alarm notification
 * is forgotten.
 *
 * @author Trevin Beattie
 */
public class AlarmItemInfo implements Comparable<AlarmItemInfo> {

    /** Internal ID of the item */
    final long id;

    /**
     * The timestamp that the item was most recently modified,
     * in milliseconds since the Epoch
     */
    final long lastModified;

    /**
     * Privacy level of the item:
     * <dl>
     * <dt>0</dt><dd>Non-private item</dd>
     * <dt>1</dt><dd>Private item; not encrypted</dd>
     * <dt>2</dt><dd>Private item encrypted with Password-Based
     * Key Derivation Function 2 (forked BouncyCastle crypto code)</dd>
     * </dl>
     */
    final int privacy;

    /**
     * Internal ID of the item category; &ldquo;Unfiled&rdquo; should be 0.
     */
    final long categoryId;

    /**
     * The item&rsquo;s category, or @code{null} if the category is
     * &ldquo;Unfiled&rdquo;.
     */
    final String category;

     /**
      * The plain-text description of the item, <b>if</b> it is not encrypted
      */
    final String description;

    /**
     * The encrypted description of the item, if it is encrypted
     */
    final byte[] encryptedDescription;

    /**
     * The time that the item is due, in milliseconds since the Epoch
     */
    final long dueDate;

    /**
     * The time of day at which the alarm should go off,
     * in milliseconds after local midnight
     */
    final long alarmTime;

    /**
     * The number of days prior to the item&rsquo;s due date
     * that the alarm should go off
     */
    final int daysEarlier;

    /**
     * The time that we posted a notification about this item,
     * in milliseconds since the Epoch
     */
    final long notificationTime;

    /**
     * The time at which the alarm should go off (computed)
     */
    Date alarmDate;

    /**
     * Create a new alarm item from the current row under a database {@link Cursor}.
     *
     * @param c the database cursor pointing to the item with the alarm
     */
    public AlarmItemInfo(Cursor c) {
        id = c.getLong(c.getColumnIndex(ToDoSchema.ToDoItemColumns._ID));
        lastModified = c.getLong(c.getColumnIndex(ToDoSchema.ToDoItemColumns.MOD_TIME));
        categoryId = c.getLong(c.getColumnIndex(ToDoSchema.ToDoItemColumns.CATEGORY_ID));
        category = (categoryId <= 0) ? null
                : c.getString(c.getColumnIndex(ToDoSchema.ToDoItemColumns.CATEGORY_NAME));
        privacy = c.getInt(c.getColumnIndex(ToDoSchema.ToDoItemColumns.PRIVATE));
        if (privacy <= 1) {
            description =
                    c.getString(c.getColumnIndex(ToDoSchema.ToDoItemColumns.DESCRIPTION));
            encryptedDescription = null;
        } else {
            description = null;
            encryptedDescription =
                    c.getBlob(c.getColumnIndex(ToDoSchema.ToDoItemColumns.DESCRIPTION));
        }
        dueDate = c.getLong(c.getColumnIndex(ToDoSchema.ToDoItemColumns.DUE_TIME));
        alarmTime = c.getLong(c.getColumnIndex(ToDoSchema.ToDoItemColumns.ALARM_TIME));
        daysEarlier =
                c.getInt(c.getColumnIndex(ToDoSchema.ToDoItemColumns.ALARM_DAYS_EARLIER));
        notificationTime =
                c.getLong(c.getColumnIndex(ToDoSchema.ToDoItemColumns.NOTIFICATION_TIME));

        // Set the date of the next alarm.
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(dueDate);
        cal.add(Calendar.DATE, -daysEarlier);
        cal.set(Calendar.HOUR_OF_DAY, (int) (alarmTime / 3600000L));
        cal.set(Calendar.MINUTE, (int) (alarmTime / 60000L) % 60);
        cal.set(Calendar.SECOND, (int) (alarmTime / 1000L) % 60);
        cal.set(Calendar.MILLISECOND, (int) (alarmTime % 1000));
        while (cal.getTimeInMillis() < notificationTime)
            cal.add(Calendar.DATE, 1);
        alarmDate = cal.getTime();
    }

    /** @return the item ID */
    public long getId() {
        return id;
    }

    /**
     * @return the last modification time of the item,
     * in milliseconds since the Epoch
     */
    public long getLastModified() {
        return lastModified;
    }

    /**
     * @return the privacy level of the item:
     * <dl>
     * <dt>0</dt><dd>Non-private item</dd>
     * <dt>1</dt><dd>Private item; not encrypted</dd>
     * <dt>2</dt><dd>Private item encrypted with Password-Based
     * Key Derivation Function 2 (forked BouncyCastle crypto code)</dd>
     * </dl>
     */
    public int getPrivacy() {
        return privacy;
    }

    /** @return the category ID */
    public long getCategoryId() {
        return categoryId;
    }

    /**
     * @return the item&rsquo;s category, or @code{null} if the category is
     * &ldquo;Unfiled&rdquo;.
     */
    public String getCategory() {
        return category;
    }

     /**
      * @return the plain-text description of the item,
      * <b>if</b> it is not encrypted; otherwise returns @code{null}.
      */
    public String getDescription() {
        return description;
    }

    /**
     * @return the encrypted description of the item, if it is encrypted;
     * otherwise returns @code{null}.
     */
    public byte[] getEncryptedDescription() {
        return encryptedDescription;
    }

    /**
     * @return the time that the item is due, in milliseconds since the Epoch
     */
    public long getDueDate() {
        return dueDate;
    }

    /**
     * @return the time of day at which the alarm should go off,
     * in milliseconds after local midnight
     */
    public long getAlarmTime() {
        return alarmTime;
    }

    /**
     * @return the number of days prior to the item&rsquo;s due date
     * that the alarm should go off
     */
    public int getDaysEarlier() {
        return daysEarlier;
    }

    /**
     * @return the time that we posted a notification about this item,
     * in milliseconds since the Epoch
     */
    public long getNotificationTime() {
        return notificationTime;
    }

    /**
     * @return the time at which the alarm should go off
     */
    public Date getAlarmDate() {
        return alarmDate;
    }

    /**
     * Compare this item's info with that of another item.
     * Sorts the items by the alarm time or, if both items
     * have the same alarm time, their descriptions.
     */
    @Override
    public int compareTo(@NonNull AlarmItemInfo i2) {
        if (alarmDate.before(i2.alarmDate))
            return -1;
        else if (alarmDate.after(i2.alarmDate))
            return 1;
        if (description == null)
            return (i2.description == null) ? 0 : -1;
        else
            return (i2.description == null) ? 1
                    : description.compareTo(i2.description);
    }

    /** Advance the alarm to the next day past the given day */
    public void advanceToNextDay(long afterTime) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(alarmDate);
        if (afterTime > cal.getTimeInMillis())
            cal.add(Calendar.DATE, (int)
                    ((afterTime + 86399000L - cal.getTimeInMillis())
                            / 86400000L));
        cal.add(Calendar.DATE, 1);
        alarmDate = cal.getTime();
    }

    /** Item hashes are based on ID and modification time. */
    @Override
    public int hashCode() {
        int hash = Long.valueOf(id).hashCode();
        hash *= 31;
        hash += Long.valueOf(lastModified).hashCode();
        return hash;
    }

    /** Items are equal if they have the same ID and modification time. */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AlarmItemInfo))
            return false;
        AlarmItemInfo i2 = (AlarmItemInfo) o;
        return (i2.id == id) && (i2.lastModified == lastModified);
    }

}
