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

import static com.xmission.trevin.android.todo.provider.ToDoSchema.ToDoItemColumns.*;

import android.util.Log;
import androidx.annotation.NonNull;

import com.xmission.trevin.android.todo.data.repeat.RepeatInterval;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Date;

/**
 * Data object corresponding to the &ldquo;todo&rdquo; table in the database
 *
 * @author Trevin Beattie
 */
// tableName = ToDoRepositoryImpl.TODO_TABLE_NAME
public class ToDoItem implements Cloneable, Serializable {

    private static final long serialVersionUID = 26;

    // PrimaryKey
    private Long _id;
    /** The plain text of the description, if available */
    private String description;
    /** The encrypted contents of the description, if encrypted */
    // Ignore for storage
    private byte[] encryptedDescription;
    /** Creation timestamp of this item */
    private long created = System.currentTimeMillis();
    /** Modification timestamp of this item */
    private long modified = System.currentTimeMillis();
    /** The date this item is due (optional, milliseconds since the Epoch) */
    private Long due;
    /** The date this item was last checked off (optional timestamp) */
    private Long completed;
    private boolean checked;
    private int priority = 1;
    // The actual name of this column is "private", but that's a reserved word
    private int privacy;
    private long categoryId = ToDoCategory.UNFILED;
    // Ignore for storage
    private String categoryName;
    /** The plain text of the note, if available */
    private String note;
    /** The encrypted contents of the note, if encrypted */
    // Ignore for storage
    private byte[] encryptedNote;
    /** Alarm settings, including the last notification time */
    private ToDoAlarm alarm;
    /** The repeat interval for this To Do item */
    private RepeatInterval repeatInterval;
    /** Do not show the item until this many days before it is due */
    private Integer hideDaysEarlier;

    /**
     * Get the database ID of the To Do item.
     * This may be {@code null} for an item which has not yet
     * been stored in the database.
     *
     * @return the row ID
     */
    public Long getId() {
        return _id;
    }

    /**
     * Set the database ID of the To Do item.
     *
     * @param id the row ID
     */
    public void setId(long id) {
        _id = id;
    }

    /**
     * Clear the ID of a To Do item.  This is used during import
     * when an item needs a new ID.
     */
    public void clearId() {
        _id = null;
    }

    /**
     * Get the text of the description, if available.
     *
     * @return the description, or {@code null} if the description
     * hasn&rsquo;t been entered yet or is encrypted.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Set the plain text of the description.  Should only be used if the
     * description is not encrypted or when decrypting it.
     *
     * @param text the description text
     */
    public void setDescription(String text) {
        description = text;
    }

    /**
     * Get the encrypted text of the description,
     * possibly for decryption.
     *
     * @return the encrypted description content, or {@code null} if
     * the description is not encrypted.
     */
    // Ignore for storage
    public byte[] getEncryptedDescription() {
        return encryptedDescription;
    }

    /**
     * Set the encrypted description of the To Do item.
     *
     * @param crypticData the encrypted description
     */
    // Ignore for storage
    public void setEncryptedDescription(byte[] crypticData) {
        encryptedDescription = crypticData;
    }

    /**
     * Get the creation time of the To Do item
     * in milliseconds since the Epoch.
     *
     * @return the note creation time
     */
    public Long getCreateTime() {
        return created;
    }

    /**
     * Set the creation time of the To Do item
     * in milliseconds since the Epoch.
     *
     * @param timestamp the creation time to set
     */
    public void setCreateTime(long timestamp) {
        created = timestamp;
    }

    /**
     * Set the creation time of the To Do item
     * to the current time.
     */
    public void setCreateTimeNow() {
        created = System.currentTimeMillis();
    }

    /**
     * Get the last modification time of the To Do item
     * in milliseconds since the epoch.
     *
     * @return the note creation time
     */
    public Long getModTime() {
        return modified;
    }

    /**
     * Set the last modification time of the To Do item
     * in milliseconds since the epoch.
     *
     * @param timestamp the creation time to set
     */
    public void setModTime(long timestamp) {
        modified = timestamp;
    }

    /**
     * Set the last modification time of the To Do item
     * to the current time.
     */
    public void setModTimeNow() {
        modified = System.currentTimeMillis();
    }

    /**
     * Get the due date of the To Do item
     * in milliseconds since the Epoch.
     *
     * @return the due date, or {@code null} if no due date is set
     */
    public Long getDue() {
        return due;
    }

    /**
     * Set (or clear) the due date of the To Do item
     * in milliseconds since the epoch.
     *
     * @param timestamp the due date to set, or {@code null}
     * to clear the due date
     */
    public void setDue(Long timestamp) {
        due = timestamp;
    }

    /**
     * Get the time this item was last checked off
     * in milliseconds since the Epoch.
     *
     * @return the completion time, or {@code null} if the item
     * has not been completed
     */
    public Long getCompleted() {
        return completed;
    }

    /**
     * Set (or clear) the time this To Do item was last checked off
     * in milliseconds since the epoch.
     *
     * @param timestamp the completion time to set, or {@code null}
     * to clear the completion time
     */
    public void setCompleted(Long timestamp) {
        completed = timestamp;
    }

    /**
     * Set the time this To Do item was last checked off
     * to the current time.
     */
    public void setCompletedNow() {
        completed = System.currentTimeMillis();
    }

    /**
     * Get whether this To Do item has been completed.
     */
    public boolean isChecked() {
        return checked;
    }

    /**
     * Change whether this To Do item has been completed.
     *
     * @param isComplete whether this item has been completed
     */
    public void setChecked(boolean isComplete) {
        checked = isComplete;
    }

    /**
     * Get the priority of this To Do item.
     *
     * @return the item&rsquo;s priority
     */
    public int getPriority() {
        return priority;
    }

    /**
     * Set the priority of this To Do item.
     *
     * @param priority the item&rsquo;s priority
     */
    public void setPriority(int priority) {
        this.priority = priority;
    }

    /**
     * Get the privacy level of the To Do item.
     * Currently defined values are:
     * <dl>
     *     <dt>0</dt><dd>Public</dd>
     *     <dt>1</dt><dd>Private, but not encrypted</dd>
     *     <dt>2</dt><dd>Private, encrypted using a PBKDF2 key</dd>
     * </dl>
     *
     * @return the item&rsquo;s privacy level
     */
    public int getPrivate() {
        return privacy;
    }

    /** @return true if the To Do item is private (or encrypted) */
    // Ignore for storage
    public boolean isPrivate() {
        return privacy > 0;
    }

    /** @return true if the To Do item is encrypted */
    // Ignore for storage
    public boolean isEncrypted() {
        return privacy > 1;
    }

    /**
     * Set the privacy level of the To Do item.
     * Currently defined values are:
     * <dl>
     *     <dt>0</dt><dd>Public</dd>
     *     <dt>1</dt><dd>Private, but not encrypted</dd>
     *     <dt>2</dt><dd>Private, encrypted using a PBKDF2 key</dd>
     * </dl>
     *
     * @param level the privacy level
     */
    public void setPrivate(int level) {
        privacy = level;
    }

    /**
     * Get the category ID of the To Do item.
     *
     * @return the category ID
     */
    public Long getCategoryId() {
        return categoryId;
    }

    /**
     * Set the category ID of the To Do item.
     *
     * @param id the category ID
     */
    public void setCategoryId(long id) {
        categoryId = id;
    }

    /**
     * Get the name of the To Do item&rsquo;s category.
     *
     * @return the category name
     */
    public String getCategoryName() {
        return categoryName;
    }

    /**
     * Set the name of the To Do item&rqsuo;s category.
     *
     * @param name the category name
     */
    public void setCategoryName(String name) {
        categoryName = name;
    }

    /**
     * Get the text of the note, if available.
     *
     * @return the note content, or null if the note hasn&rsquo;t
     * been written yet or is encrypted.
     */
    public String getNote() {
        return note;
    }

    /**
     * Set the plain text of the note.  Should only be used if the
     * note is not encrypted or when decrypting it.
     *
     * @param text the note text
     */
    public void setNote(String text) {
        note = text;
    }

    /**
     * Get the encrypted text of the note,
     * possibly for decryption.
     *
     * @return the encrypted note content, or {@code null} if
     * the note is not encrypted.
     */
    // Ignore for storage
    public byte[] getEncryptedNote() {
        return encryptedNote;
    }

    /**
     * Set the encrypted text of the note.
     *
     * @param crypticData the encrypted note content
     */
    // Ignore for storage
    public void setEncryptedNote(byte[] crypticData) {
        encryptedNote = crypticData;
    }

    /**
     * Get the alarm settings
     *
     * @return the alarm settings, or {@code null} if no alarm is set
     */
    public ToDoAlarm getAlarm() {
        return alarm;
    }

    /**
     * Set (or clear) the alarm settings
     *
     * @param alarm the alarm settings
     */
    public void setAlarm(ToDoAlarm alarm) {
        this.alarm = alarm;
    }

    /**
     * Get the repeat interval
     *
     * @return the repeat interval, or {@code null} if no repeat is set
     */
    public RepeatInterval getRepeatInterval() {
        return repeatInterval;
    }

    /**
     * Set the repeat interval
     *
     * @param interval the repeat interval
     */
    public void setRepeatInterval(RepeatInterval interval) {
        repeatInterval = interval;
    }

    /**
     * Get the number of days prior to the due date before which this
     * To Do item should be hidden.
     *
     * @return the minimum number of days before due to hide the item,
     * or {@code null} if this item should never be hidden or has no due date.
     */
    public Integer getHideDaysEarlier() {
        return hideDaysEarlier;
    }

    /**
     * Set (or clear) the number of days prior to the due date
     * before which this To Do item should be hidden.
     *
     * @param days the minimum number of days before due to hide the item,
     * or {@code null} to clear the hide date.
     */
    public void setHideDaysEarlier(Integer days) {
        hideDaysEarlier = days;
    }

    @NonNull
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName())
                .append('(');
        if (_id != null)
            sb.append(_ID).append('=').append(_id).append(", ");
        if (description != null) {
            sb.append(DESCRIPTION).append('=');
            if (privacy >= 1)
                sb.append("[Private]");
            else if (description.length() <= 80)
                sb.append('"').append(description).append('"');
            else
                sb.append('"').append(description.substring(0, 77))
                        .append('\u2026').append('"');
        } else if (encryptedDescription != null) {
            sb.append(DESCRIPTION).append("=[Encrypted]");
        } else {
            sb.append(DESCRIPTION).append("=null");
        }
        sb.append(", ").append(CREATE_TIME).append('=')
                .append(new Date(created));
        sb.append(", ").append(MOD_TIME).append('=')
                .append(new Date(modified));
        if (due != null)
            sb.append(", ").append(DUE_TIME).append('=')
                    .append(new Date(due));
        if (completed != null)
            sb.append(", ").append(COMPLETED_TIME).append('=')
                    .append(new Date(completed));
        sb.append(", ").append(CHECKED).append('=')
                .append(checked);
        sb.append(", ").append(PRIORITY).append('=')
                .append(priority);
        sb.append(", ").append(PRIVATE).append('=')
                .append(privacy);
        sb.append(", ").append(CATEGORY_ID).append('=')
                .append(categoryId);
        if (categoryName != null)
            sb.append(", ").append(CATEGORY_NAME).append("=\"")
                    .append(categoryName).append('"');
        if (note != null) {
            sb.append(", ").append(NOTE).append('=');
            if (privacy >= 1)
                sb.append("[Private]");
            else if (note.length() <= 80)
                sb.append('"').append(note).append('"');
            else
                sb.append('"').append(note.substring(0, 77));
        } else if (encryptedNote != null) {
            sb.append(NOTE).append("=[Encrypted]");
        }
        if (alarm != null)
            sb.append(", alarm=").append(alarm);
        if (repeatInterval != null)
            sb.append(", repeatInterval=").append(repeatInterval);
        if (hideDaysEarlier != null)
            sb.append(", ").append(HIDE_DAYS_EARLIER).append('=')
                    .append(hideDaysEarlier);
        sb.append(')');
        return sb.toString();
    }

    @Override
    public int hashCode() {
        int hash = 7 * 31;
        if (_id != null)
            hash += _id.hashCode();
        hash *= 31;
        if (description != null)
            hash += description.hashCode();
        hash *= 31;
        if (encryptedDescription != null)
            hash += Arrays.hashCode(encryptedDescription);
        hash *= 31;
        hash += Long.hashCode(created);
        hash *= 31;
        hash += Long.hashCode(modified);
        hash *= 31;
        if (due != null)
            hash += due.hashCode();
        hash *= 31;
        if (completed != null)
            hash += completed.hashCode();
        hash *= 31;
        hash += Boolean.hashCode(checked);
        hash *= 31;
        // To Do: Add the rest of the fields after verifying
        // their nullability and data types.
        hash += Integer.hashCode(priority);
        hash *= 31;
        hash += Integer.hashCode(privacy);
        hash *= 31;
        hash += Long.hashCode(categoryId);
        hash *= 31;
        if (categoryName != null)
            hash += categoryName.hashCode();
        hash *= 31;
        if (note != null)
            hash += note.hashCode();
        hash *= 31;
        if (encryptedNote != null)
            hash += Arrays.hashCode(encryptedNote);
        hash *= 31;
        if (alarm != null)
            hash += alarm.hashCode();
        hash *= 31;
        if (repeatInterval != null)
            hash += repeatInterval.hashCode();
        hash *= 31;
        if (hideDaysEarlier != null)
            hash += hideDaysEarlier.hashCode();
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ToDoItem))
            return false;
        ToDoItem other = (ToDoItem) o;
        if ((_id == null) != (other._id == null))
            return false;
        if ((_id != null) && !_id.equals(other._id))
            return false;
        if ((description == null) != (other.description == null))
            return false;
        if ((description != null) && !description.equals(other.description))
            return false;
        if ((encryptedDescription == null) !=
                (other.encryptedDescription == null))
            return false;
        if ((encryptedDescription != null) && !Arrays.equals(
                encryptedDescription, other.encryptedDescription))
            return false;
        if (created != other.created)
            return false;
        if (modified != other.modified)
            return false;
        if ((due == null) != (other.due == null))
            return false;
        if ((due != null) && !due.equals(other.due))
            return false;
        if ((completed == null) != (other.completed == null))
            return false;
        if ((completed != null) && !completed.equals(other.completed))
            return false;
        if (checked != other.checked)
            return false;
        if (priority != other.priority)
            return false;
        if (privacy != other.privacy)
            return false;
        if (categoryId != other.categoryId)
            return false;
        if ((categoryName == null) != (other.categoryName == null))
            return false;
        if ((categoryName != null) && !categoryName.equals(other.categoryName))
            return false;
        if ((note == null) != (other.note == null))
            return false;
        if ((note != null) && !note.equals(other.note))
            return false;
        if ((encryptedNote == null) != (other.encryptedNote == null))
            return false;
        if ((encryptedNote != null) &&
                !Arrays.equals(encryptedNote, other.encryptedNote))
            return false;
        if ((alarm == null) != (other.alarm == null))
            return false;
        if ((alarm != null) && !alarm.equals(other.alarm))
            return false;
        if ((repeatInterval == null) != (other.repeatInterval == null))
            return false;
        if ((repeatInterval != null) &&
                !repeatInterval.equals(other.repeatInterval))
            return false;
        if ((hideDaysEarlier == null) != (other.hideDaysEarlier == null))
            return false;
        if ((hideDaysEarlier != null) &&
                !hideDaysEarlier.equals(other.hideDaysEarlier))
            return false;
        return true;
    }

    @Override
    @NonNull
    public ToDoItem clone() {
        try {
            ToDoItem clone = (ToDoItem) super.clone();
            if (encryptedDescription != null) {
                clone.encryptedDescription =
                        new byte[encryptedDescription.length];
                System.arraycopy(encryptedDescription, 0,
                        clone.encryptedDescription, 0,
                        encryptedDescription.length);
            }
            if (encryptedNote != null) {
                clone.encryptedNote = new byte[encryptedNote.length];
                System.arraycopy(encryptedNote, 0,
                        clone.encryptedNote, 0, encryptedNote.length);
            }
            if (alarm != null)
                clone.alarm = alarm.clone();
            if (repeatInterval != null)
                clone.repeatInterval = repeatInterval.clone();
            return clone;
        } catch (CloneNotSupportedException e) {
            Log.e("ToDoItem", "Clone not supported", e);
            throw new RuntimeException(e);
        }
    }

}
