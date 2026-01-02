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

import java.time.LocalDate;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Parent class for the types of repeat intervals which may be shifted
 * by a few days in either direction depending on what day of the week
 * the target date falls on.
 */
public abstract class AbstractAdjustableRepeat extends AbstractRepeat {

    /** Days of the week on which this event can occur */
    @NonNull
    private SortedSet<WeekDays> allowedWeekDays = new TreeSet<>();

    /**
     * The direction to look for the next available day of the week
     * when the target date falls on an unavailable day.
     */
    @NonNull
    private WeekdayDirection direction;

    /**
     * Initialize this repeat with all days of the week and a default
     * direction of {@link WeekdayDirection#NEXT NEXT}.
     * We require that there always be at least one day in the set.
     *
     * @param typeId the ID of this repeat interval type
     */
    protected AbstractAdjustableRepeat(int typeId) {
        this(typeId, WeekDays.DAYS_BIT_MASK
                | WeekdayDirection.NEXT.getValue(), LocalDate.now());
    }

    /**
     * Initialize this repeat with the days of the week and direction
     * given by a bit mask (i.e. from the database).
     *
     * @param typeId the ID of this repeat interval type
     * @param bitMask the bit field containing the allowed days
     *                on which this item can be repeated
     *
     * @throws IllegalArgumentException if the bit field
     * (masked by all possible days) is 0
     */
    protected AbstractAdjustableRepeat(int typeId, int bitMask) {
        this(typeId, bitMask, LocalDate.now());
    }

    /**
     * Initialize this repeat with all days of the week, a default
     * direction of {@link WeekdayDirection#NEXT NEXT}, and a day
     * of the month from the given date.
     *
     * @param typeId the ID of this repeat interval type
     * @param due the initial date on which to base this repeat interval
     */
    protected AbstractAdjustableRepeat(int typeId, @NonNull LocalDate due) {
        this(typeId, WeekDays.DAYS_BIT_MASK
                | WeekdayDirection.NEXT.getValue(), due);
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
    protected AbstractAdjustableRepeat(int typeId,
                                       int bitMask,
                                       @NonNull LocalDate due) {
        super(typeId, due);
        if ((bitMask & WeekDays.DAYS_BIT_MASK) == 0)
            throw new IllegalArgumentException("No days selected");
        allowedWeekDays.addAll(WeekDays.fromBitMap(bitMask));
        direction = WeekdayDirection.fromBitMap(bitMask);
    }

    /**
     * @return a copy of the days of the week on which this event can occur
     */
    @NonNull
    public SortedSet<WeekDays> getAllowedWeekDays() {
        return allowedWeekDays;
    }

    /**
     * Set the days of the week on which this event can occur.
     *
     * @param days the days of the week on which this event can occur
     */
    public void setAllowedWeekDays(@NonNull Set<WeekDays> days) {
        if ((days == null) || days.isEmpty())
            throw new IllegalArgumentException("At least one day must be set");
        allowedWeekDays.clear();
        allowedWeekDays.addAll(days);
    }

    /**
     * @return the direction to look for the next available day of the week
     */
    @NonNull
    public WeekdayDirection getDirection() {
        return direction;
    }

    /**
     * Set the direction to look for the next available day of the week
     *
     * @param direction the direction to use
     */
    public void setDirection(@NonNull WeekdayDirection direction) {
        if (direction == null)
            throw new IllegalArgumentException("Direction cannot be null");
        this.direction = direction;
    }

    /**
     * If the currently allowed days of the week doesn&rsquo;t include
     * the new due date, add it to the set.
     */
    @Override
    public boolean updateForDueDate(@NonNull LocalDate newDue) {
        WeekDays targetDay = WeekDays.fromJavaDay(newDue.getDayOfWeek());
        return allowedWeekDays.add(targetDay);
    }

    /**
     * Helper method for {@link #computeNextDueDate(LocalDate, LocalDate)}:
     * recompute a due date based on the available days of the week.
     *
     * @param candiDate the proposed next due date of the To Do item
     *
     * @return the next due date after ensuring it falls on one of the
     * allowed days of the week
     */
    @NonNull
    protected LocalDate adjustDueDate(@NonNull LocalDate candiDate) {
        WeekDays targetDay = WeekDays.fromJavaDay(candiDate.getDayOfWeek());
        if (allowedWeekDays.contains(targetDay))
            return candiDate;

        // Find the next and previous available days
        SortedSet<WeekDays> daysAfter = allowedWeekDays.tailSet(targetDay);
        SortedSet<WeekDays> daysBefore = allowedWeekDays.headSet(targetDay);
        // Count the number of days between the target day and the next
        // and previous available days; make sure to wrap around the week.
        int skipAhead = (daysAfter.isEmpty()
                ? (7 + allowedWeekDays.first().getValue()) // first available day next week
                : daysAfter.first().getValue())
                - targetDay.getValue();
        int skipBack = (daysBefore.isEmpty()
                ? (allowedWeekDays.last().getValue() - 7) // last available day last week
                : daysBefore.last().getValue())
                - targetDay.getValue();

        switch (direction) {
            case NEXT:
                return candiDate.plusDays(skipAhead);
            case PREVIOUS:
                return candiDate.plusDays(skipBack);
            case CLOSEST_OR_NEXT:
                if (Math.abs(skipAhead) <= Math.abs(skipBack))
                    return candiDate.plusDays(skipAhead);
                else
                    return candiDate.plusDays(skipBack);
            case CLOSEST_OR_PREVIOUS:
                if (Math.abs(skipBack) <= Math.abs(skipAhead))
                    return candiDate.plusDays(skipBack);
                else
                    return candiDate.plusDays(skipAhead);
        }
        // Should be unreachable code
        Log.e("AbstractAdjRepeat", "Invalid direction: " + direction);
        return candiDate;
    }

    /**
     * Helper method for adding the {@code allowedWeekDays} and
     * {@code direction} fields for a {@link #toString()} implementation.
     * These are formatted simply as &ldquo;, <i>fieldName</i> =
     * <i>value</i>&rdquo;, but only if {@code allowedWeekDays} is not
     * the full set of all days; if all days are allowed, this method
     * does nothing.
     */
    protected void formatDays(StringBuilder sb) {
        if (allowedWeekDays.size() == WeekDays.values().length)
            return;
        sb.append(", allowedWeekDays = ").append(allowedWeekDays);
        sb.append(", direction = ").append(direction);
    }

    @Override
    @NonNull
    public AbstractAdjustableRepeat clone() {
        AbstractAdjustableRepeat clone =
                (AbstractAdjustableRepeat) super.clone();
        clone.allowedWeekDays = new TreeSet<>(allowedWeekDays);
        return clone;
    }

    @Override
    public int hashCode() {
        int hash = super.hashCode() * 31 + allowedWeekDays.hashCode();
        hash = hash * 31 + direction.hashCode();
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (!super.equals(o))
            return false;
        if (!(o instanceof AbstractAdjustableRepeat))
            return false;
        AbstractAdjustableRepeat other = (AbstractAdjustableRepeat) o;
        if (!allowedWeekDays.equals(other.allowedWeekDays))
            return false;
        if (direction != other.direction)
            return false;
        return true;
    }

}
