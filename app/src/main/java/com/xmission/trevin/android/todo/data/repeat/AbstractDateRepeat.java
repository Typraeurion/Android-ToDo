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

import androidx.annotation.NonNull;

import java.time.LocalDate;

/**
 * Parent class for types of repeat intervals which occur on a specific
 * date of the month.
 */
public abstract class AbstractDateRepeat extends AbstractAdjustableRepeat {

    /** The date on which this item repeats */
    protected int date;

    /**
     * Initialize this repeat with all days of the week, a default
     * direction of {@link WeekdayDirection#NEXT NEXT}, and a
     * default date of the current day of the month.
     * We require that there always be at least one day in the set.
     *
     * @param typeId the ID of this repeat interval type
     */
    protected AbstractDateRepeat(int typeId) {
        super(typeId);
        date = LocalDate.now().getDayOfMonth();
    }

    /**
     * Initialize this repeat with the days of the week and direction
     * given by a bit mask (i.e. from the database) and a default date
     * of the current day of the month.
     *
     * @param typeId the ID of this repeat interval type
     * @param bitMask the bit field containing the allowed days
     *                on which this item can be repeated
     *
     * @throws IllegalArgumentException if the bit field
     * (masked by all possible days) is 0
     */
    protected AbstractDateRepeat(int typeId, int bitMask) {
        super(typeId, bitMask);
        date = LocalDate.now().getDayOfMonth();
    }

    /**
     * Initialize this repeat with all days of the week, a default
     * direction of {@link WeekdayDirection#NEXT NEXT}, and a day
     * of the month from the given date.
     *
     * @param typeId the ID of this repeat interval type
     * @param due the initial date on which to base this repeat interval
     */
    protected AbstractDateRepeat(int typeId, @NonNull LocalDate due) {
        super(typeId, due);
        date = due.getDayOfMonth();
    }

    /**
     * Initialize this repeat with the days of the week and direction
     * given by a bit mask (i.e. from the database) and a day
     * of the month from the given date.
     *
     * @param typeId the ID of this repeat interval type
     * @param bitMask the bit field containing the allowed days
     *                on which this item can be repeated
     * @param due the initial date on which to base this repeat interval
     *
     * @throws IllegalArgumentException if the bit field
     * (masked by all possible days) is 0
     */
    protected AbstractDateRepeat(int typeId,
                                 int bitMask,
                                 @NonNull LocalDate due) {
        super(typeId, bitMask, due);
        date = due.getDayOfMonth();
    }

    /**
    /** @return the date on which this item repeats */
    public int getDate() {
        return date;
    }

    /**
     * Set the date on which this item repeats.  The date must be
     * in the range 1–31; this class does not check whether the date
     * is valid for any particular month.
     *
     * @param date the date to set
     *
     * @throws IllegalArgumentException if {@code date} is less than 1
     * or greater than 31
     */
    public void setDate(int date) {
        if ((date < 1) || (date > 31))
            throw new IllegalArgumentException("Invalid date: " + date);
        this.date = date;
    }

    /**
     * If the currently configured date is different from the new due date,
     * update it.
     */
    @Override
    public boolean updateForDueDate(@NonNull LocalDate newDue) {
        boolean dateChanged = (date != newDue.getDayOfMonth());
        if (dateChanged)
            date = newDue.getDayOfMonth();
        boolean superChanged = super.updateForDueDate(newDue);
        return dateChanged || superChanged;
    }

    @Override
    @NonNull
    public AbstractDateRepeat clone() {
        return (AbstractDateRepeat) super.clone();
    }

    @Override
    public int hashCode() {
        return super.hashCode() * 31 + Integer.hashCode(date);
    }

    @Override
    public boolean equals(Object o) {
        if (!super.equals(o))
            return false;
        if (!(o instanceof AbstractDateRepeat))
            return false;
        AbstractDateRepeat other = (AbstractDateRepeat) o;
        if (date != other.date)
            return false;
        return true;
    }

}
