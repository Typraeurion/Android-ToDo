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
package com.xmission.trevin.android.todo.ui;

import java.util.WeakHashMap;

import android.content.Context;
import android.database.Cursor;
import android.view.*;
import android.widget.*;

/**
 * An extension of the @{link SimpleCursorAdapter} which adds a
 * static entry to the top of the list, representing none of the below.
 *
 * @author Trevin Beattie
 */
public class NoSelectionCursorAdapter extends SimpleCursorAdapter {

    public static final String TAG = "NoSelectionCursorAdapter";

    private final String noText;

    /** Keeps track of the views we create ourself. */
    private final WeakHashMap<View,Integer> ourViews =
            new WeakHashMap<>();

    private final LayoutInflater inflater;

    /** View type for the No selection item */
    private static final int NO_VIEW_TYPE = 1;

    public NoSelectionCursorAdapter(Context context, Cursor c,
                                    String displayColumn, String noText) {
        super(context, android.R.layout.simple_spinner_item,
                c, new String[] { displayColumn },
                new int[] { android.R.id.text1 });
        this.noText = noText;
        inflater = (LayoutInflater)
                context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    }

    /**
     * @return how many items are in the data set represented by this Adapter.
     * We always have 1 item in addition to the underlying data.
     *
     * @see android.widget.Adapter#getCount()
     */
    @Override
    public int getCount() {
        return super.getCount() + 1;
    }

    /**
     * @return the data item associated with the specified position
     * in the data set.
     *
     * @see android.widget.Adapter#getItem(int)
     */
    @Override
    public Object getItem(int position) {
        if (position == 0)
            return null;
        return super.getItem(position - 1);
    }

    /**
     * @return the row id associated with the specified position in the list.
     * <b>Caution:</b> the row ID is meaningless for the first
     * item in this adapter, as it does not come from the data set.
     *
     * @see android.widget.Adapter#getItemId(int)
     */
    @Override
    public long getItemId(int position) {
        if (position == 0)
            return -1;
        return super.getItemId(position - 1);
    }

    /**
     * Get a {@link View} that displays the data in the spinner
     * when the specified position in the data set is selected.
     *
     * @param position the position in the data set whose view to return
     * @param convertView the old view to reuse, if possible
     * @param parent the parent that this view will eventually be attached to
     *
     * @return a View corresponding to the data at the specified position.
     *
     * @see android.widget.Adapter#getView(int, View, ViewGroup)
     */
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (position > 0) {
            if (ourViews.containsKey(convertView))
                // Not convertible!
                convertView = null;
            return super.getView(position - 1, convertView, parent);
        }
        if (!ourViews.containsKey(convertView))
            // Not convertible!
            convertView = null;

        View v;
        if (convertView == null) {
            v = inflater.inflate(android.R.layout.simple_spinner_item,
                    parent, false);
            ourViews.put(v, NO_VIEW_TYPE);
        } else {
            v = convertView;
        }

        // Bind the View
        TextView textView = (TextView) v;
        textView.setText(noText);

        return v;
    }

    /**
     * Get a {@link View} that displays the data
     * for the specified position in the data set.
     *
     * @param position the position in the data set whose view to return
     * @param convertView the old view to reuse, if possible
     * @param parent the parent that this view will eventually be attached to
     *
     * @return a View corresponding to the data at the specified position.
     *
     * @see android.widget.Adapter#getView(int, View, ViewGroup)
     */
    @Override
    public View getDropDownView(int position,
                                View convertView, ViewGroup parent) {
        if (position > 0) {
            if (ourViews.containsKey(convertView))
                // Not convertible!
                convertView = null;
            return super.getDropDownView(position - 1, convertView, parent);
        }
        if (!ourViews.containsKey(convertView))
            // Not convertible!
            convertView = null;

        View v;
        if (convertView == null) {
            v = inflater.inflate(
                    android.R.layout.simple_spinner_dropdown_item, parent, false);
            ourViews.put(v, NO_VIEW_TYPE);
        } else {
            v = convertView;
        }

        // Bind the View
        TextView textView = (TextView) v;
        textView.setText(noText);

        return v;
    }
}
