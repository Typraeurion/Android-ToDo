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

import static com.xmission.trevin.android.todo.provider.ToDoSchema.ToDoItemColumns.REPEAT_WEEKLY;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Repeating interval for a weekly To Do item.
 *
 * @author Trevin Beattie
 */
public class RepeatWeekly extends AbstractRepeat {

    private static final long serialVersionUID = 3;

    public static final int ID = REPEAT_WEEKLY;

    @NonNull
    private SortedSet<WeekDays> fixedWeekDays = new TreeSet<>();

    /**
     * Initialize this repeat with all days of the week.
     * We require that there always be at least one day in the set.
     */
    public RepeatWeekly() {
        this(WeekDays.DAYS_BIT_MASK |
                WeekdayDirection.NEXT.getValue(), LocalDate.now());
    }

    /**
     * Initialize this repeat with the days of the week given
     * by a bit mask (i.e. from the database).
     *
     * @throws IllegalArgumentException if the bit field
     * (masked by all possible days) is 0
     */
    public RepeatWeekly(int bitMask) {
        this(bitMask, LocalDate.now());
    }

    /**
     * Initialize this repeat with all days of the week for a given due date.
     * For a weekly task, the due date makes no difference.
     */
    public RepeatWeekly(@NonNull LocalDate due) {
        this(WeekDays.DAYS_BIT_MASK |
                WeekdayDirection.NEXT.getValue(), due);
    }

    /**
     * Initialize this repeat with the days of the week given
     * by a bit mask (i.e. from the database) for the given due
     * date.  For a weekly task, the due date makes no difference.
     *
     * @param bitMask the bit field containing the days
     *                on which this item can be repeated
     * @param due the first date on which this To Do item is due
     *
     * @throws IllegalArgumentException if the bit field
     * (masked by all possible days) is 0
     */
    public RepeatWeekly(int bitMask, @NonNull LocalDate due) {
        super(REPEAT_WEEKLY, due);
        if ((bitMask & WeekDays.DAYS_BIT_MASK) == 0)
            throw new IllegalArgumentException("No days selected");
        fixedWeekDays.addAll(WeekDays.fromBitMap(bitMask));
    }

    /**
     * @return a copy of the set of weekdays on which this item repeats
     */
    @NonNull
    public SortedSet<WeekDays> getWeekDays() {
        return new TreeSet<>(fixedWeekDays);
    }

    /**
     * Set the weekdays on which this item repeats.
     *
     * @param days the weekdays on which this item repeats
     *
     * @throws IllegalArgumentException if the set is empty
     */
    public void setWeekDays(Set<WeekDays> days) {
        if (days.isEmpty())
            throw new IllegalArgumentException("At least one day must be set");
        fixedWeekDays.clear();
        fixedWeekDays.addAll(days);
    }

    /**
     * If the current set of days of the week doesn&rsquo;t include
     * the new due date, add it to the set.
     */
    @Override
    public boolean updateForDueDate(LocalDate newDue) {
        WeekDays targetDay = WeekDays.fromJavaDay(newDue.getDayOfWeek());
        return fixedWeekDays.add(targetDay);
    }

    @Override
    @Nullable
    public LocalDate computeNextDueDate(
            @NonNull LocalDate priorDueDate, @NonNull LocalDate completed) {
        WeekDays day = WeekDays.fromJavaDay(priorDueDate.getDayOfWeek());
        // Get the next available day in the current week
        SortedSet<WeekDays> remainingDays =
                new TreeSet<>(fixedWeekDays.tailSet(day));
        remainingDays.removeFirst();
        if (remainingDays.isEmpty())
            // If no days left this week, skip `increment' weeks
            // and go to the first available day that week.
            return checkEndDate(priorDueDate, priorDueDate.plusDays(
                    7 * increment + fixedWeekDays.first().getValue()
                            - day.getValue()));
        // Otherwise go to the next available day
        LocalDate nextDueDate = priorDueDate.plusDays(
                remainingDays.first().getValue() - day.getValue());
        return checkEndDate(priorDueDate, nextDueDate);
    }

    @Override
    @NonNull
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName())
                .append("(Every ");
        if (increment == 1) {
            sb.append("week");
        } else {
            sb.append(increment).append(" weeks");
        }
        sb.append(" on ");
        int count = 0;
        for (WeekDays day : fixedWeekDays) {
            count++;
            sb.append(day.toString());
            if (count < fixedWeekDays.size()) {
                if (fixedWeekDays.size() > 2)
                    sb.append(',');
                sb.append(' ');
                if (count == fixedWeekDays.size() - 1)
                    sb.append("and ");
            }
        }
        if (end != null) {
            sb.append(", ending ").append(end);
        }
        sb.append(')');
        return sb.toString();
    }

    @Override
    @NonNull
    public RepeatWeekly clone() {
        RepeatWeekly clone = (RepeatWeekly) super.clone();
        clone.fixedWeekDays = new TreeSet<>(fixedWeekDays);
        return clone;
    }

    @Override
    public int hashCode() {
        int hash = super.hashCode() * 31 + fixedWeekDays.hashCode();
        return hash;
    }

}
