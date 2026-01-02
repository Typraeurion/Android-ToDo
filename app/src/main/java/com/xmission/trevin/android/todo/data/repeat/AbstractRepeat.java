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

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDate;

/**
 * Abstract base class for repeat intervals.  This includes fields
 * that are common to all intervals (excluding {@link RepeatNone}).
 *
 * @author Trevin Beattie
 */
public abstract class AbstractRepeat implements RepeatInterval {

    /** The ID of this repeat interval, as stored in the database */
    protected final int id;

    /**
     * The number of days, weeks, months, or years
     * between repeat intervals
     */
    protected int increment = 1;

    /**
     * The date the repeat interval ends for this To Do item,
     * or {@code null} if the repeating never ends.
     */
    @Nullable
    protected LocalDate end;

    /**
     * Initialize this repeat interval for the given type ID
     *
     * @param typeId the ID of this repeat interval type
     */
    protected AbstractRepeat(int typeId) {
        this(typeId, LocalDate.now());
    }

    /**
     * Initialize this repeat interval for the given type ID
     * and due date.  Not all repeat intervals are based on the
     * next due date, but all repeat classes need to include this
     * to support the {@link RepeatType#newInstance(int, LocalDate)}
     * method.
     *
     * @param typeId the ID of this repeat interval type
     * @param due the first date on which this To Do item is due
     */
    protected AbstractRepeat(int typeId, @NonNull LocalDate due) {
        id = typeId;
    }

    /** @return the ID of this interval (as stored in the database) */
    public int getId() {
        return id;
    }

    /**
     * Get the number of days, weeks, months, or years
     * (according to the repeat interval type) between
     * repeats.
     *
     * @return the repeat increment
     */
    public int getIncrement() {
        return increment;
    }

    /**
     * Set the number of days, weeks, months, or years
     * (according to the repeat interval type) between
     * repeats.
     *
     * @param increment the repeat increment
     *
     * @throws IllegalArgumentException if the {@code increment}
     * is not a positive number
     */
    public void setIncrement(int increment) {
        if (increment <= 0)
            throw new IllegalArgumentException(
                    "Repeat increment must be a positive number");
        this.increment = increment;
    }

    /**
     * Get the date after which to stop repeating this To Do item,
     * or {@code null} if it repeats without end.
     *
     * @return the date after which to stop repeating
     * in milliseconds since the Epoch, or {@code null}
     */
    public LocalDate getEnd() {
        return end;
    }

    /**
     * Set (or clear) the date after which to stop repeating this To Do item.
     *
     * @param date the date after which to stop repeating this item,
     * or {@code null} to clear the end date.
     */
    public void setEnd(LocalDate date) {
        end = date;
    }

    /**
     * Helper method for {@code toString}s: format a number
     * as an ordinal with a following &ldquo;th&rdquo;, &ldquo;nd&rdquo;,
     * &ldquo;rd&rdquo;, or &ldquo;st&rdquo; according to the value.
     *
     * @param sb the {@link StringBuilder} to append the ordinal to
     * @param num the number to format
     */
    protected void formatOrdinal(StringBuilder sb, int num) {
        sb.append(num);
        switch (num % 100) {
            case 11:
            case 12:
            case 13:
                sb.append("th");
                break;
            default:
                switch (num % 10) {
                    case 1:
                        sb.append("st");
                        break;
                    case 2:
                        sb.append("nd");
                        break;
                    case 3:
                        sb.append("rd");
                        break;
                    default:
                        sb.append("th");
                        break;
                }
        }
    }

    /**
     * Check the proposed next due date against any end date for this
     * repeating interval.  If we&rsquo;re past the end date, return
     * {@code null}.
     *
     * @param priorDueDate the date on which this To Do item was last
     * completed (only for debug logging purposes)
     * @param nextDueDate the proposed next date to set for this item
     *
     * @return {@code nextDueDate} if it is not after {@code end} or
     * if {@code end} is {@code null}, otherwise {@code null}
     */
    @Nullable
    protected LocalDate checkEndDate(
            @NonNull LocalDate priorDueDate, @NonNull LocalDate nextDueDate) {
        Log.d(getClass().getSimpleName(), String.format(
                ".computeNextDueDate(%s) = %s", priorDueDate, nextDueDate));
        if ((end != null) && nextDueDate.isAfter(end)) {
            Log.d(getClass().getSimpleName(), String.format(
                    ".checkEndDate: Past the end date %s; will not repeat", end));
            return null;
        }
        return nextDueDate;
    }

    @Override
    @NonNull
    public AbstractRepeat clone() {
        try {
            return (AbstractRepeat) super.clone();
        } catch (CloneNotSupportedException e) {
            Log.e(getClass().getSimpleName(), "Clone not supported", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public int hashCode() {
        int hash = Integer.hashCode(increment) * 31;
        if (end != null)
            hash += end.hashCode();
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AbstractRepeat))
            return false;
        AbstractRepeat other = (AbstractRepeat) o;
        if (increment != other.increment)
            return false;
        if ((end == null) != (other.end == null))
            return false;
        if ((end != null) && !end.equals(other.end))
            return false;
        return true;
    }

}
