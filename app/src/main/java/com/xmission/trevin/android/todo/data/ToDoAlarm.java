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
package com.xmission.trevin.android.todo.data;

import static com.xmission.trevin.android.todo.provider.ToDoSchema.ToDoItemColumns.ALARM_TIME;
import static com.xmission.trevin.android.todo.provider.ToDoSchema.ToDoItemColumns.ALARM_DAYS_EARLIER;
import static com.xmission.trevin.android.todo.provider.ToDoSchema.ToDoItemColumns.NOTIFICATION_TIME;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalTime;

/**
 * Data object corresponding to the subset of fields in the
 * &ldquo;todo&rdquo; table relating to alarms for To Do items
 *
 * @author Trevin Beattie
 */
public class ToDoAlarm implements Cloneable, Serializable {

    private static final long serialVersionUID = 3;

    /** The time of day to trigger the alarm (milliseconds after midnight) */
    @NonNull
    private LocalTime time;
    /** The number of days in advance to trigger the alarm (optional) */
    private int daysEarlier;
    /**
     * The timestamp at which we last notified the user that this item
     * is due.  Alarms must not be repeated which precede this time.
     */
    private Instant notificationTime;

    /**
     * Create alarm settings with a default time of (local) noon
     * and no advance notice.
     */
    public ToDoAlarm() {
        this(LocalTime.NOON, 0);
    }

    /**
     * Create alarm settings for a given time of day
     * and no advance notice.
     */
    public ToDoAlarm(LocalTime time) {
        this(time, 0);
    }

    /**
     * Create alarm settings for a given time of day
     * and number of days in advance to show the alarm
     */
    public ToDoAlarm(LocalTime time, int days) {
        this.time = time;
        daysEarlier = days;
    }

    /**
     * Get the number of days in advance to trigger the alarm.
     *
     * @return the number of days in advance to trigger the alarm,
     * or {@code null} if no alarm is set
     */
    public int getAlarmDaysEarlier() {
        return daysEarlier;
    }

    /**
     * Set the number of days in advance to trigger the alarm.
     *
     * @param days the number of days in advance to trigger the alarm
     */
    public void setAlarmDaysEarlier(int days) {
        daysEarlier = days;
    }

    /**
     * Get the time of day to trigger the alarm.
     *
     * @return the time of day
     */
    @NonNull
    public LocalTime getTime() {
        return time;
    }

    /**
     * Set the time of day to trigger the alarm.
     *
     * @param time the time of day to trigger the alarm.
     */
    public void setTime(@NonNull LocalTime time) {
        if (time == null)
            throw new IllegalArgumentException("Alarm time cannot be null");
        this.time = time;
    }

    /**
     * Get the last time we notified the user that this item is due.
     *
     * @return the last notification time, or {@code null}
     * if we&rsquo;ve never yet notified the user.
     */
    @Nullable
    public Instant getNotificationTime() {
        return notificationTime;
    }

    /**
     * Set the last time we notified the user that this item is due.
     *
     * @param time the last notification time
     */
    public void setNotificationTime(Instant time) {
        notificationTime = time;
    }

    /**
     * Clear the notification time at which we last
     * reported this To Do item.
     */
    public void clearNotificationTime() {
        notificationTime = null;
    }

    /**
     * Set the last time we notified the user that this item is due
     * to the current time.
     */
    public void setNotificationTimeNow() {
        notificationTime = Instant.now();
    }

    @NonNull
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName())
                .append('(');
        sb.append(ALARM_TIME).append('=').append(time).append(", ");
        sb.append(ALARM_DAYS_EARLIER).append('=').append(daysEarlier);
        if (notificationTime != null)
            sb.append(", ").append(NOTIFICATION_TIME).append('=')
                    .append(notificationTime);
        sb.append(')');
        return sb.toString();
    }

    @Override
    public int hashCode() {
        int hash = 7 * 11 + time.hashCode();
        hash = hash * 31 + daysEarlier;
        hash *= 31;
        if (notificationTime != null)
            hash += notificationTime.hashCode();
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ToDoAlarm))
            return false;
        ToDoAlarm other = (ToDoAlarm) o;
        if (!time.equals(other.time))
            return false;
        if (daysEarlier != other.daysEarlier)
            return false;
        if ((notificationTime == null) != (other.notificationTime == null))
            return false;
        if ((notificationTime != null) &&
                !notificationTime.equals(other.notificationTime))
            return false;
        return true;
    }

    @Override
    @NonNull
    public ToDoAlarm clone() {
        try {
            return (ToDoAlarm) super.clone();
        } catch (CloneNotSupportedException e) {
            Log.e("ToDoAlarm", "Clone not supported", e);
            throw new RuntimeException(e);
        }
    }

}
