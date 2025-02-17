/*
 * $Id: ToDo.java,v 1.2 2011/07/18 00:44:49 trevin Exp trevin $
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
 *
 * $Log: ToDo.java,v $
 * Revision 1.2  2011/07/18 00:44:49  trevin
 * Added the copyright notice
 *
 * Revision 1.1  2011/01/12 05:52:22  trevin
 * Initial revision
 *
 */
package com.xmission.trevin.android.todo;

import android.net.Uri;
import android.provider.BaseColumns;

/**
 * Field definitions for the To Do list provider
 */
public final class ToDo {
    /** The content provider part of the URI for locating To Do records */
    public static final String AUTHORITY = "com.xmission.trevin.android.todo.ToDo";

    // This class cannot be instantiated
    private ToDo() {}

    /**
     * Additional data that doesn't fit into a relational schema
     * but needs to be stored with the database
     */
    public static final class ToDoMetadata implements BaseColumns {
        // This class cannot be instantiated
        private ToDoMetadata() {}

        /**
         * The content:// style URL for this table
         */
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/misc");

        /**
         * The MIME type of {@link #CONTENT_URI} providing a directory of metadata.
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.xmission.trevin.todo.misc";

        /**
         * The MIME type of a {@link #CONTENT_URI} sub-directory of a single datum.
         */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.xmission.trevin.todo.misc";

        /**
         * The name of the datum
         * <P>Type: TEXT</P>
         */
        public static final String NAME = "name";

        /**
         * The value of the datum
         * <P>Type: BLOB</P>
         */
        public static final String VALUE = "value";

    }

    /**
     * Categories table
     */
    public static final class ToDoCategory implements BaseColumns {
        // This class cannot be instantiated
        private ToDoCategory() {}

        /**
         * The content:// style URL for this table
         */
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/categories");

        /**
         * The MIME type of {@link #CONTENT_URI} providing a directory of To Do categories.
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.xmission.trevin.todo.category";

        /**
         * The MIME type of a {@link #CONTENT_URI} sub-directory of a single category.
         */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.xmission.trevin.todo.category";

        /**
         * The name of the category
         * <P>Type: TEXT</P>
         */
        public static final String NAME = "name";

        /**
         * The default sort order for this table
         */
        public static final String DEFAULT_SORT_ORDER = NAME;

        /** The ID to use for unfiled items */
        public static final long UNFILED = 0;
    }

    /**
     * To Do table
     */
    public static final class ToDoItem implements BaseColumns {
        // This class cannot be instantiated
        private ToDoItem() {}

        /**
         * The content:// style URL for this table
         */
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/todo");

        /**
         * The MIME type of {@link #CONTENT_URI} providing a directory of To Do items.
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.xmission.trevin.todo";

        /**
         * The MIME type of a {@link #CONTENT_URI} sub-directory of a single To Do item.
         */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.xmission.trevin.todo";

        /**
         * The description of the To Do item (displayed text)
         * <P>Type: TEXT</P>
         */
        public static final String DESCRIPTION = "description";

        /**
         * The date this To Do item was created
         * <P>Type: INTEGER (long from System.curentTimeMillis())</P>
         */
        public static final String CREATE_TIME = "created";

        /**
         * The date this To Do item was last modified
         * <P>Type: INTEGER (long from System.curentTimeMillis())</P>
         */
        public static final String MOD_TIME = "modified";

        /**
         * The date this To Do item is due
         * <P>Type: INTEGER (long from Date.getTime())</P>
         */
        public static final String DUE_TIME = "due";

        /**
         * The date this To Do item was last checked off
         * <P>Type: INTEGER (long from System.curentTimeMillis())</P>
         */
        public static final String COMPLETED_TIME = "completed";

        /**
         * Whether this item has been completed
         * <P>Type: INTEGER</P>
         */
        public static final String CHECKED = "checked";

        /**
         * The priority for addressing this To Do item
         * <P>Type: INTEGER</P>
         */
        public static final String PRIORITY = "priority";

        /**
         * Whether this item is considered private.
         * 0 = public, 1 = private but unencrypted,
         * 2 = encrypted with password key.
         * <P>Type: INTEGER</P>
         */
        public static final String PRIVATE = "private";

        /**
         * The ID of the item's category
         * <P>Type: INTEGER</P>
         */
        public static final String CATEGORY_ID = "category_id";

        /**
         * The name of the item's category
         * <P>Type: TEXT</P>
         */
        public static final String CATEGORY_NAME = "category_name";

        /**
         * A longer note attached to the To Do item
         * <P>Type: TEXT</P>
         */
        public static final String NOTE = "note";

        /**
         * The number of days in advance to trigger the alarm (null disables)
         * <P>Type: INTEGER</P>
         */
        public static final String ALARM_DAYS_EARLIER = "alarm_days_earlier";

        /**
         * The time of day to trigger the alarm
         * <P>Type: INTEGER (long milliseconds after midnight)</P>
         */
        public static final String ALARM_TIME = "alarm_time";

        /**
         * The repeat interval for this To Do item
         * <P>Type: INTEGER (None, Daily, Weekly, Monthly_by_Day,
         * Monthly_by_Date, or Annually)</P>
         */
        public static final String REPEAT_INTERVAL = "repeat_interval";

        /** Value for no repeat */
        public static final int REPEAT_NONE = 0;
        /** Value for repeating daily on a fixed schedule */
        public static final int REPEAT_DAILY = 1;
        /** Value for repeating a day after the last completed date */
        public static final int REPEAT_DAY_AFTER = 2;
        /** Value for repeating weekly on a fixed schedule */
        public static final int REPEAT_WEEKLY = 3;
        /** Value for repeating a week after the last completed date */
        public static final int REPEAT_WEEK_AFTER = 4;
        /**
         * Value for repeating semi-monthly on a fixed schedule
         * by two days and weeks
         */
        public static final int REPEAT_SEMI_MONTHLY_ON_DAYS = 5;
        /** Value for repeating semi-monthly on a fixed schedule on two dates */
        public static final int REPEAT_SEMI_MONTHLY_ON_DATES = 6;
        /** Value for repeating monthly on a fixed schedule by week and day */
        public static final int REPEAT_MONTHLY_ON_DAY = 7;
        /** Value for repeating monthly on a fixed schedule on a certain date */
        public static final int REPEAT_MONTHLY_ON_DATE = 8;
        /** Value for repeating a month after the last completed date */
        public static final int REPEAT_MONTH_AFTER = 9;
        /** Value for repeating yearly on a fixed schedule by month, week, and day */
        public static final int REPEAT_YEARLY_ON_DAY = 10;
        /** Value for repeating yearly on a fixed schedule on a certain date */
        public static final int REPEAT_YEARLY_ON_DATE = 11;
        /** Value for repeating yearly after the last completed date */
        public static final int REPEAT_YEAR_AFTER = 12;

        /**
         * The number of days, weeks, months, or years between
         * repeat intervals
         * <P>Type: INTEGER</P>
         */
        public static final String REPEAT_INCREMENT = "repeat_increment";

        /**
         * For repeating weekly, the days of the week to do the item
         * (1 = Sunday, 2 = Monday, 4 = Tuesday, 8 = Wednesday,
         * 16 = Thursday, 32 = Friday, 64 = Saturday.)
         * <P>
         * For repeating monthly or annually by date, the days of the week
         * on which the event may occur.  In the event a day is blocked,
         * the following bits determine the alternate date to choose:
         * <ul>
         * <li>bit 7: 0 = next available day, 1 = previous available day</li>
         * <li>bit 8: 0 = always the given direction, 1 = closest available
         * day, ties go in given direction</li>
         * </ul>
         * <P>Type: INTEGER</P>
         */
        public static final String REPEAT_WEEK_DAYS = "repeat_week_days";

        /** Sunday bit */
        public static final int REPEAT_SUNDAYS = 1;
        /** Monday bit */
        public static final int REPEAT_MONDAYS = 2;
        /** Tuesday bit */
        public static final int REPEAT_TUESDAYS = 4;
        /** Wednesday bit */
        public static final int REPEAT_WEDNESDAYS = 8;
        /** Thursday bit */
        public static final int REPEAT_THURSDAYS = 0x10;
        /** Friday bit */
        public static final int REPEAT_FRIDAYS = 0x20;
        /** Saturday bit */
        public static final int REPEAT_SATURDAYS = 0x40;
        /** All days of the week */
        public static final int REPEAT_ALL_WEEK = 0x7f;
        /** First previously available day of the week */
        public static final int REPEAT_PREVIOUS_WEEKDAY = 0x80;
        /** Closest available day of the week */
        public static final int REPEAT_CLOSEST_WEEKDAY = 0x100;

        /*
         * For repeating monthly or annually by day of the week,
         * the week day number (0 = Sunday, 6 = Saturday).
         * For repeating monthly or annually by day of the month,
         * the date (1-31).  Negative numbers count from the end
         * of the month — e.g. -1 is the last day, -7 is a week
         * from the 1st of next month, etc.
         * <P>Type: INTEGER</P>
         */
        public static final String REPEAT_DAY = "repeat_day";

        /*
         * For repeating semi-monthly, the second day (0-6) or date (1-31).
         * <P>Type: INTEGER</P>
         */
        public static final String REPEAT_DAY2 = "repeat_day2";

        /*
         * For repeating monthly or annually by day of the week,
         * the week number (0 = 1st Xxxday, 4 = last Xxxday).
         * <P>Type: INTEGER</P>
         */
        public static final String REPEAT_WEEK = "repeat_week";

        /*
         * For repeating semi-monthly by day of the week,
         * the second week number (0-4).
         * <P>Type: INTEGER</P>
         */
        public static final String REPEAT_WEEK2 = "repeat_week2";

        /*
         * For repeating annually, the month number
         * (0 = January, 11 = December).
         * <P>Type: INTEGER</P>
         */
        public static final String REPEAT_MONTH = "repeat_month";

        /**
         * The day the repeat interval ends for this To Do item
         * <P>Type: (long from System.curentTimeMillis())</P>
         */
        public static final String REPEAT_END = "repeat_end";

        /**
         * Do not show the item until this many days before it is due
         * <P>Type: INTEGER</P>
         */
        public static final String HIDE_DAYS_EARLIER = "hide_days_earlier";

        /**
         * The time at which we last notified the user that this item
         * is due.  Alarms must not be repeated which precede this time.
         * <P>Type: (long from System.currentTimeMillis())</P>
         */
        public static final String NOTIFICATION_TIME = "notification_time";

        /**
         * The default sort order for this table
         */
        public static final String DEFAULT_SORT_ORDER = MOD_TIME;

        /**
         * Other pre-defined sort orders for this table.
         * The order must match the PrefSortByList string array resource.
         */
        public static final String[] USER_SORT_ORDERS = {
            PRIORITY + ", ifnull(" + DUE_TIME + ", 9.22e+18), lower(" + DESCRIPTION + "), " + MOD_TIME,
            "ifnull(" + DUE_TIME + ", 9.22e+18), " + PRIORITY + ", lower(" + DESCRIPTION + "), " + MOD_TIME,
            "lower(" + CATEGORY_NAME + "), " + PRIORITY + ", lower(" + DESCRIPTION + "), " + MOD_TIME,
            "lower(" + CATEGORY_NAME + "), ifnull(" + DUE_TIME + ", 9.22e+18), lower(" + DESCRIPTION + "), " + MOD_TIME,
            "lower(" + DESCRIPTION + "), " + MOD_TIME,
            "ifnull(" + DUE_TIME + ", 9.22e+18), lower(" + DESCRIPTION + "), " + MOD_TIME,
        };
    }
}
