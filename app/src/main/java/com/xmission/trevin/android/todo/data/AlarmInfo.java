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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.xmission.trevin.android.todo.util.StringEncryption;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;

/**
 * Keeps track of notifications that need to be posted.
 * For each item with an alarm we need to know its ID,
 * the time it was last modified when we looked at it, the
 * time its next alarm should go off, and when it will be overdue.
 * If an item is later modified, any previous alarm notification
 * is forgotten.
 *
 * @author Trevin Beattie
 */
public class AlarmInfo implements Cloneable,
        Comparable<AlarmInfo>, Serializable {

    private static final long serialVersionUID = 13;

    // Subset of the fields from ToDoItem that we use
    long id;
    Instant lastModified;
    int privacy;
    long categoryId = ToDoCategory.UNFILED;
    String category;
    String description;
    byte[] encryptedDescription;
    LocalDate dueDate;
    LocalTime alarmTime;
    @NonNull
    ZoneId timeZone = ZoneOffset.UTC;
    int daysEarlier;
    Instant notificationTime;
    /**
     * The next time at which the alarm should go off.
     * This is computed as needed and cached.
     */
    ZonedDateTime nextAlarmTime;

    /** Create a new alarm info to be filled in (e.g. from the database) */
    public AlarmInfo() {}

    /**
     * Create an alarm info using the relevant fields from a To Do item.
     *
     * @param todo the To Do item to mirror
     *
     * @throws IllegalArgumentException if the To Do item does not have
     * an ID, description, due date, or alarm
     */
    public AlarmInfo(@NonNull ToDoItem todo) {
        if (todo.getId() == null)
            throw new IllegalArgumentException("Item has no ID");
        if (todo.isEncrypted() ?
                ((todo.getEncryptedDescription() == null) ||
                        (todo.getEncryptedDescription().length == 0))
                : ((todo.getDescription() == null) ||
                (todo.getDescription().length() == 0)))
            throw new IllegalArgumentException("Item has no description");
        if (todo.getDue() == null)
            throw new IllegalArgumentException("Item has no due date");
        if (todo.getAlarm() == null)
            throw new IllegalArgumentException("Item has no alarm");

        id = todo.getId();
        lastModified = todo.getModTime();
        privacy = todo.getPrivate();
        categoryId = todo.getCategoryId();
        category = todo.getCategoryName();
        description = todo.getDescription();
        encryptedDescription = todo.getEncryptedDescription();
        dueDate = todo.getDue();
        alarmTime = todo.getAlarm().getTime();
        daysEarlier = todo.getAlarm().getAlarmDaysEarlier();
        notificationTime = todo.getAlarm().getNotificationTime();
    }

    /** @return the item ID */
    public long getId() {
        return id;
    }

    /** Set the item ID */
    public void setId(long id) {
        this.id = id;
    }

    /** @return the last modification time of the item */
    @NonNull
    public Instant getLastModified() {
        return (lastModified == null) ? Instant.now() : lastModified;
    }

    /** Set the last modification time of the item */
    public void setLastModified(@NonNull Instant lastModified) {
        if (lastModified == null)
            throw new IllegalArgumentException("lastModified cannot be null");
        this.lastModified = lastModified;
    }

    /** @return the privacy level of the item */
    public int getPrivate() {
        return privacy;
    }

    /** @return true if this is a private item */
    public boolean isPrivate() {
        return privacy > 0;
    }

    /** @return true if this item is encrypted */
    public boolean isEncrypted() {
        return privacy > StringEncryption.NO_ENCRYPTION;
    }

    /** Set the privacy level of this item */
    public void setPrivate(int privacyLevel) {
        if (privacyLevel < 0)
            throw new IllegalArgumentException(
                    "Privacy level cannot be negative");
        if (privacyLevel > StringEncryption.MAX_SUPPORTED_ENCRYPTION)
            throw new IllegalArgumentException(
                    "Unsupported encryption type " + privacyLevel);
        this.privacy = privacyLevel;
    }

    /** @return the category ID of the item */
    public long getCategoryId() {
        return categoryId;
    }

    /** Set the category ID of the item */
    public void setCategoryId(long categoryId) {
        this.categoryId = categoryId;
    }

    /**
     * @return the category name of the item, or
     * {@code null} if the category is &ldquo;Unfiled&rdquo;.
     */
    public String getCategory() {
        return category;
    }

    /**
     * Set the category name of the item.
     * Should be {@code null} if the category is &ldquo;Unfiled&rdquo;.
     */
    public void setCategory(String categoryName) {
        category = categoryName;
    }

    /**
     * @return the plain-text description of the item,
     * <b>if</b> it is not encrypted; otherwise returns {@code null}.
     */
    public String getDescription() {
        return description;
    }

    /** Set the plain-text description of the item. */
    public void setDescription(String desc) {
        description = desc;
    }

    /**
     * @return the encrypted description of the item, if it is encrypted;
     * otherwise returns {@code null}.
     */
    public byte[] getEncryptedDescription() {
        return encryptedDescription;
    }

    /** Set the encrypted description of the item. */
    public void setEncryptedDescription(byte[] desc) {
        encryptedDescription = desc;
    }

    /** @return the date that the item is due */
    @NonNull
    public LocalDate getDueDate() {
        return (dueDate == null) ? LocalDate.of(1970, 1, 1) : dueDate;
    }

    /** Set the date that the item is due */
    public void setDueDate(@NonNull LocalDate due) {
        if (due == null)
            throw new IllegalArgumentException("Due date cannot be null");
        dueDate = due;
        nextAlarmTime = null;
    }

    /** @return the time of day at which the alarm should go off */
    @NonNull
    public LocalTime getAlarmTime() {
        return (alarmTime == null) ? LocalTime.of(12, 0) : alarmTime;
    }

    /** Set the time of day at which the alarm should go off */
    public void setAlarmTime(LocalTime time) {
        if (time == null)
            throw new IllegalArgumentException("Alarm time cannot be null");
        alarmTime = time;
        nextAlarmTime = null;
    }

    /** @return the time zone for which {@code alarmTime} is determined */
    @NonNull
    public ZoneId getTimeZone() {
        return timeZone;
    }

    /** Set the time zone for which {@code alarmTime} is determined */
    public void setTimeZone(@NonNull ZoneId zone) {
        if (zone == null)
            throw new IllegalArgumentException("Time zone cannot be null");
        timeZone = zone;
        nextAlarmTime = null;
    }

    /**
     * @return the number of days before the due date that the first
     * alarm should go off
     */
    public int getDaysEarlier() {
        return daysEarlier;
    }

    /**
     * Set the number of days before the due date
     * that the first alarm should go off
     */
    public void setDaysEarlier(int days) {
        daysEarlier = days;
        nextAlarmTime = null;
    }

    /**
     * @return the time at which this item is due.  This combines
     * the {@code dueDate} and {@code alarmTime} for the given
     * {@code timeZone}.
     */
    public ZonedDateTime getDueTime() {
        return ZonedDateTime.of((dueDate == null) ?
                LocalDate.of(1970, 1, 1) : dueDate,
                (alarmTime == null) ? LocalTime.of(12, 0) : alarmTime,
                timeZone);
    }

    /** @return the time that we last posted a notification about this item */
    @Nullable
    public Instant getNotificationTime() {
        return notificationTime;
    }

    /** Set the time that we last posted a notification about this item */
    public void setNotificationTime(Instant time) {
        if (time == null)
            throw new IllegalArgumentException("Notification time cannot be null");
        notificationTime = time;
        nextAlarmTime = null;
    }

    /** Set the last notification time to the current time */
    public void setNotificationTimeNow() {
        notificationTime = Instant.now();
        nextAlarmTime = null;
    }

    /**
     * Get the time at which the alarm should go off.  This is
     * computed from max({@code dueDate} - {@code daysEarlier},
     * today, {@code notificationTime} + 1 day) at {@code alarmTime}.
     * It&rsquo;s returned as a zoned date and time; the alarm
     * service needs to provide whichever time zone the
     * user has designated for alarm notifications (e.g.
     * a fixed zone or determined by the system) to the
     * {@link #setTimeZone(ZoneId)} field.
     *
     * @return the next time at which the alarm should go off.
     */
    public ZonedDateTime getNextAlarmTime() {
        if (nextAlarmTime == null) {
            LocalDate firstDate = dueDate.minusDays(daysEarlier);
            LocalDate nextNotificationDate = LocalDate.now();
            if (notificationTime != null) {
                LocalDate lastNotificationDate =
                        notificationTime.atZone(timeZone).toLocalDate();
                if (!lastNotificationDate.isBefore(nextNotificationDate))
                    nextNotificationDate = lastNotificationDate.plusDays(1);
            }
            if (firstDate.isAfter(nextNotificationDate))
                nextNotificationDate = firstDate;
            nextAlarmTime = ZonedDateTime.of(
                    nextNotificationDate, alarmTime, timeZone);
        }
        return nextAlarmTime;
    }

    /**
     * Alarm items are equal if they have the same To Do item ID
     * and last modification time.
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AlarmInfo))
            return false;
        AlarmInfo ai2 = (AlarmInfo) o;
        return (id == ai2.id) &&
                (getLastModified().equals(ai2.getLastModified()));
     }

     /** Alarm info hashes are based on ID and modification time. */
    @Override
    public int hashCode() {
        int hash = Long.valueOf(id).hashCode();
        hash *= 31;
        if (lastModified != null)
            hash += lastModified.hashCode();
        return hash;
    }

    /**
     * Compare this alarm info with that of another item.
     * Sorts the alarms by the alarm time or, if both items
     * have the same alarm time, their descriptions.
     */
    @Override
    public int compareTo(@NonNull AlarmInfo alarm2) {
        if (getNextAlarmTime().isBefore(alarm2.getNextAlarmTime()))
            return -1;
        if (getNextAlarmTime().isAfter(alarm2.getNextAlarmTime()))
            return 1;
        if (description == null)
            return (alarm2.description == null) ? 0 : -1;
        else
            return (alarm2.description == null) ? 1
                    : description.compareTo(alarm2.description);
    }

    @Override
    public AlarmInfo clone() {
        try {
             AlarmInfo copy = (AlarmInfo) super.clone();
             if (encryptedDescription != null)
                 copy.encryptedDescription = Arrays.copyOf(
                         encryptedDescription, encryptedDescription.length);
             return copy;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

}
