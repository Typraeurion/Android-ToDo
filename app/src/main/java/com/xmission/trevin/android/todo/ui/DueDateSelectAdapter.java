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
package com.xmission.trevin.android.todo.ui;

import android.content.Context;
import android.database.DataSetObserver;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import androidx.annotation.NonNull;

import com.xmission.trevin.android.todo.R;
import com.xmission.trevin.android.todo.data.ToDoPreferences;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.WeakHashMap;

/**
 * An adapter for selecting a due date for a To Do item.
 * The list consists of eight entries for specific days
 * (the current date through a week from today), an entry
 * for &ldquo;No Date&rdquo;, and an entry for
 * &ldquo;Choose Date&hellip;&rdquo; which opens a
 * calendar date picker.
 *
 * @author Trevin Beattie
 */
public class DueDateSelectAdapter extends BaseAdapter {

    public static final String TAG = "DueDateSelectAdapter";

    private final LayoutInflater inflater;

    /** Shared preferences */
    private final ToDoPreferences prefs;

    private final List<DataSetObserver> observers = new ArrayList<>();

    /** View types (internal use) */
    public enum ViewType {
        /** Imminent date items */
        WEEK,
        /** The No Date item */
        NO_DATE,
        /** The Choose Date item */
        CHOOSE_DATE
    }

    /**
     * Container for adapter data; this holds the static dates
     * for the current display or placeholder entries for
     * &ldquo;No Date&rdquo; or &ldquo;Choose Date&hellip;&rdquo;.
     */
    public static class SelectedDate {

        private final LocalDate fixedDate;
        private final ViewType viewType;
        private final String displayText;

        /**
         * Construct a date entry for a given date.
         *
         * @param date the date to display
         * @param text the display text for this entry
         */
        SelectedDate(LocalDate date, String text) {
            fixedDate = date;
            viewType = ViewType.WEEK;
            displayText = text;
        }

        /**
         * Construct a date entry for &ldquo;No Date&rdquo;
         * or &ldquo;Choose Date&hellip;&rdquo;.
         *
         * @param type the type of entry
         * @param text the display text for this entry
         */
        private SelectedDate(ViewType type, String text) {
            fixedDate = null;
            viewType = type;
            displayText = text;
        }

        /**
         * @return the view type associated with this entry
         */
        public ViewType getViewType() {
            return viewType;
        }

        /**
         * @return the date associated with this entry if it&rsquo;s for
         * a fixed date, {@code null} otherwise
         */
        public LocalDate getDate() {
            return fixedDate;
        }

        /** @return the text to show for this entry */
        public String getDisplayText() {
            return displayText;
        }

    }

    /** The fixed entry for &ldquo;No Date&rdquo; */
    private final SelectedDate NO_DATE;

    /** The fixed entry for &ldquo;Choose Date&hellip;&rdquo; */
    private final SelectedDate CHOOSE_DATE;

    /** Formatters used to format the near date entries */
    private final DateTimeFormatter[] dateFormatters;

    /** The list of dates shown by this adapter */
    private final List<SelectedDate> dates;

    /**
     * Weak map of views that have been used and the view type
     * they should be associated with.  We use this to determine
     * whether these views may be re-used or must be replaced.
     */
    private final WeakHashMap<View,ViewType> spinnerViews =
            new WeakHashMap<>();

    /**
     * Create the date select adapter with the given context and preferences.
     * The date list will be initialized starting from today,
     * and an alarm will be set for midnight in the user&rsquo;s
     * preferred time zone.
     *
     * @param context the context in which the adapter is being used
     * @param preferences the To Do preferences to use
     */
    public DueDateSelectAdapter(@NonNull Context context,
                                @NonNull ToDoPreferences preferences) {
        Log.d(TAG, "created");
        prefs = preferences;
        inflater = (LayoutInflater) context.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);

        NO_DATE = new SelectedDate(ViewType.NO_DATE,
                context.getString(R.string.DueDateNoDate));
        CHOOSE_DATE = new SelectedDate(ViewType.CHOOSE_DATE,
                context.getString(R.string.DueDateOther));

        String[] dueDateOptionFormats = context.getResources()
                .getStringArray(R.array.DueDateFormatList);
        dateFormatters = new DateTimeFormatter[dueDateOptionFormats.length];
        for (int i = 0; i < dueDateOptionFormats.length; i++) {
            dateFormatters[i] = DateTimeFormatter.ofPattern(
                    dueDateOptionFormats[i], Locale.getDefault());
        }
        dates = new ArrayList<>(dueDateOptionFormats.length + 2);
        LocalDate today = LocalDate.now(prefs.getTimeZone());
        generateDates(today);
    }

    /**
     * Refresh the list of dates if needed.
     * This should be called from ToDoListActivity whenever the
     * date list dialog pops up.
     */
    public void refreshDates() {
        LocalDate today = LocalDate.now(prefs.getTimeZone());
        if (!today.equals(dates.get(0).getDate())) {
            generateDates(today);
            notifyDataSetChanged();
        }
    }

    /**
     * Generate the list of date entries to select from.
     *
     * @param start the date to start with
     */
    private synchronized void generateDates(LocalDate start) {
        dates.clear();
        for (int i = 0; i < dateFormatters.length; i++) {
            LocalDate date = start.plusDays(i);
            dates.add(new SelectedDate(date, date.format(dateFormatters[i])));
        }
        dates.add(NO_DATE);
        dates.add(CHOOSE_DATE);
    }

    /** Indicate that all items in this adapter are enabled */
    @Override
    public boolean areAllItemsEnabled() {
        return true;
    }

    /**
     * Get the number of items in the data set represented by this adapter
     *
     * @return the size of the date formatter list plus 2
     */
    @Override
    public int getCount() {
        return dateFormatters.length + 2;
    }

    /**
     * Get the date selector associated with the specified position
     * in the data.  The first 8 positions have a fixed date.
     * The second to last position is &ldquo;No Date&rdquo;.
     * The last position is &ldquo;Choose Date&hellip;&rdquo;.
     *
     * @param position the position in the list
     *
     * @return the date selector for that position
     */
    @Override
    public SelectedDate getItem(int position) {
        if ((position < 0) || (position > dates.size())) {
            Log.w(TAG, String.format(".getItem(%d) - Invalid position",
                    position));
            return null;
        }
        return dates.get(position);
    }

    /**
     * Get the &lqduo;row ID&rdquo; associated with the specified position
     * in the list.  We define this to be the number of days since the
     * Epoch for fixed dates, 0 for &ldquo;No Date&rdquo; or -1 for
     * &ldquo;Choose Date&hellip;&rdquo;.
     *
     * @param position the position in the list
     *
     * @return the ID corresponding to the category
     */
    @Override
    public long getItemId(int position) {
        SelectedDate entry = getItem(position);
        switch (entry.getViewType()) {
            case WEEK:
                return entry.getDate().toEpochDay();
            case NO_DATE:
                return 0;
            default:
                return -1;
        }
    }

    /**
     * Get a View that displays the date at the specified position
     * in the data set.
     *
     * @param position the position of the date in the list
     * @param convertView the old view to use, if possible
     * @param parent the parent that this view will be attached to
     *
     * @return a View of the date at this position
     */
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // For debug logging
        String cvDesc = (convertView == null) ? "null"
                : convertView.getClass().getSimpleName();
        if (convertView instanceof TextView)
            cvDesc = String.format("%s@%s(\"%s\")", cvDesc,
                    Integer.toHexString(System.identityHashCode(convertView)),
                    ((TextView) convertView).getText().toString());
        Log.d(TAG, String.format(Locale.US, ".getView(%d,%s,%s)",
                position, cvDesc, parent));
        SelectedDate date = getItem(position);
        TextView tv;
        ViewType viewType = dates.get(position).getViewType();
        if (convertView != null) {
            if (!spinnerViews.containsKey(convertView) ||
                    (viewType != spinnerViews.get(convertView))) {
                Log.d(TAG, "Old view does not match target view type; ignoring it");
                convertView = null;
            }
        }
        if (convertView instanceof TextView) {
            tv = (TextView) convertView;
        } else {
            Log.d(TAG, "Creating a new spinner item view");
            tv = (TextView) inflater.inflate(android.R.layout.simple_spinner_item,
                    parent, false);
        }
        if (date != null) {
            tv.setText(date.getDisplayText());
            spinnerViews.put(tv, viewType);
        }
        return tv;
    }

    /**
     * Item ID&rsquo;s are <i>not</i> stable across changes to the
     * underlying data since the &ldquo;Today&rdquo;, &ldquo;Tomorrow&rdquo;,
     * and &ldquo;In 1 week&rdquo; entries may use different dates.
     *
     * @return {@code false}
     */
    @Override
    public boolean hasStableIds() {
        return false;
    }

    /**
     * The date selection is never empty.
     *
     * @return {@code false}
     */
    @Override
    public boolean isEmpty() {
        return false;
    }

    /**
     * All items for this selection are always enabled.
     *
     * @param position (ignored)
     *
     * @return {@code true}
     */
    @Override
    public boolean isEnabled(int position) {
        return true;
    }

    /**
     * Notify any attached observers that the underlying categories
     * have been changed and any View reflecting the data set
     * should refresh itself.
     */
    @Override
    public void notifyDataSetChanged() {
        Log.d(TAG, ".notifyDataSetChanged");
        for (DataSetObserver observer : observers) try {
            observer.onChanged();
        } catch (RuntimeException e) {
            Log.w(TAG, "Failed to notify observer "
                    + observer.getClass().getCanonicalName(), e);
        }
    }

    /**
     * Register an observer that is called when changes happen
     * to the category list.
     *
     * @param observer the observer to notify when changes happen
     */
    @Override
    public void registerDataSetObserver(DataSetObserver observer) {
        observers.add(observer);
    }

    /**
     * Unregister an observer that has previously been registered with
     * {@link #registerDataSetObserver}
     *
     * @param observer the observer to unregister
     */
    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
        observers.remove(observer);
    }

}
