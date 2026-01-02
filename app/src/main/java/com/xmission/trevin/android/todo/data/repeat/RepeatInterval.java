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
import androidx.annotation.Nullable;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * Type of data objects which hold settings for various types of
 * repeating intervals for To Do items.
 *
 * @author Trevin Beattie
 */
public interface RepeatInterval extends Cloneable, Serializable {

    /** @return the ID of this interval (as stored in the database) */
    int getId();

    /**
     * Update the repeat settings to accommodate a change in the due date.
     * This is intended for use when the user is changing the repeat
     * settings for a To Do item in the UI; if the due date changes
     * internally (e.g. advancing to the next due date), this should
     * <i>not</i> be called.  If any settings were modified as a result
     * of this call, the UI <i>must</i> re-read all of the settings and
     * update the UI elements accordingly.
     *
     * @param newDue the new due date
     *
     * @return {@code true} if any of the repeat interval settings
     * have changed, {@code false} if there was no change.
     */
    boolean updateForDueDate(@NonNull LocalDate newDue);

    /**
     * Using these repeat settings, return the next due date from
     * the prior due date and completion date.
     *
     * @return the next due date, or {@code null} if the item
     * will not repeat
     */
    @Nullable
    LocalDate computeNextDueDate(
            @NonNull LocalDate priorDueDate, @NonNull LocalDate completed);

    @NonNull
    RepeatInterval clone();

}
