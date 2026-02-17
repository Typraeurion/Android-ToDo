/*
 * Copyright © 2011–2026 Trevin Beattie
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
package com.xmission.trevin.android.todo.data.palm;

import androidx.annotation.NonNull;

/**
 * Category entry as stored in the Palm database file
 *
 * @author Trevin Beattie
 */
public class CategoryEntry {

    /** The category key used by To Do entries in the Palm database */
    public int index;
    /** The original category ID in the Palm database */
    public int ID;
    public int dirty;
    public String longName;
    /** Short version of the category name; limited to 8 characters */
    public String shortName;
    /** If merging or adding, the new category ID in the Android database */
    public long newID;

    @Override
    @NonNull
    public String toString() {
        StringBuilder sb = new StringBuilder("CategoryEntry[#");
        sb.append(index).append(",ID=");
        sb.append(ID).append(',');
        if (dirty != 0)
            sb.append("dirty,");
        sb.append("name=\"").append(longName).append("\",abbr=\"");
        sb.append(shortName).append("\"]");
        return sb.toString();
    }

}
