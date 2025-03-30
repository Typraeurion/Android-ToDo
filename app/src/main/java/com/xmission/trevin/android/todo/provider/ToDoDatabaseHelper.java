/*
 * Copyright © 2025 Trevin Beattie
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
package com.xmission.trevin.android.todo.provider;

import static com.xmission.trevin.android.todo.provider.ToDoProvider.*;

import android.content.ContentValues;
import android.content.Context;
import android.content.res.Resources;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.xmission.trevin.android.todo.R;

/**
 * This class helps open, create, and upgrade the database file.
 *
 * @author Trevin Beattie
 */
class ToDoDatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = "ToDoDatabaseHelper";

    /** Resources */
    private Resources res;

    ToDoDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        res = context.getResources();
        Log.d(TAG, getClass().getName() + " created");
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.d(TAG, getClass().getName() + ".onCreate(" + db + ")");
        db.execSQL("CREATE TABLE " + METADATA_TABLE_NAME + " ("
                + ToDo.ToDoMetadata._ID + " INTEGER PRIMARY KEY,"
                + ToDo.ToDoMetadata.NAME + " TEXT UNIQUE,"
                + ToDo.ToDoMetadata.VALUE + " BLOB);");

        db.execSQL("CREATE TABLE " + CATEGORY_TABLE_NAME + " ("
                + ToDo.ToDoCategory._ID + " INTEGER PRIMARY KEY,"
                + ToDo.ToDoCategory.NAME + " TEXT UNIQUE"
                + ");");
        ContentValues values = new ContentValues();
        values.put(ToDo.ToDoCategory._ID, ToDo.ToDoCategory.UNFILED);
        values.put(ToDo.ToDoCategory.NAME,
                res.getString(R.string.Category_Unfiled));
        db.insert(CATEGORY_TABLE_NAME, null, values);

        db.execSQL("CREATE TABLE " + TODO_TABLE_NAME + " ("
                + ToDo.ToDoItem._ID + " INTEGER PRIMARY KEY,"
                + ToDo.ToDoItem.DESCRIPTION + " TEXT,"
                + ToDo.ToDoItem.CREATE_TIME + " INTEGER,"
                + ToDo.ToDoItem.MOD_TIME + " INTEGER,"
                + ToDo.ToDoItem.DUE_TIME + " INTEGER,"
                + ToDo.ToDoItem.COMPLETED_TIME + " INTEGER,"
                + ToDo.ToDoItem.CHECKED + " INTEGER,"
                + ToDo.ToDoItem.PRIORITY + " INTEGER,"
                + ToDo.ToDoItem.PRIVATE + " INTEGER,"
                + ToDo.ToDoItem.CATEGORY_ID + " INTEGER,"
                + ToDo.ToDoItem.NOTE + " TEXT,"
                + ToDo.ToDoItem.ALARM_DAYS_EARLIER + " INTEGER,"
                + ToDo.ToDoItem.ALARM_TIME + " INTEGER,"
                + ToDo.ToDoItem.REPEAT_INTERVAL + " INTEGER,"
                + ToDo.ToDoItem.REPEAT_INCREMENT + " INTEGER,"
                + ToDo.ToDoItem.REPEAT_WEEK_DAYS + " INTEGER,"
                + ToDo.ToDoItem.REPEAT_DAY + " INTEGER,"
                + ToDo.ToDoItem.REPEAT_DAY2 + " INTEGER,"
                + ToDo.ToDoItem.REPEAT_WEEK + " INTEGER,"
                + ToDo.ToDoItem.REPEAT_WEEK2 + " INTEGER,"
                + ToDo.ToDoItem.REPEAT_MONTH + " INTEGER,"
                + ToDo.ToDoItem.REPEAT_END + " INTEGER,"
                + ToDo.ToDoItem.HIDE_DAYS_EARLIER + " INTEGER,"
                + ToDo.ToDoItem.NOTIFICATION_TIME + " INTEGER"
                + ");");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d(TAG, getClass().getName() + ".onUpgrade("
                + db + "," + oldVersion + "," + newVersion + ")");
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE " + METADATA_TABLE_NAME + " ("
                    + ToDo.ToDoMetadata._ID + " INTEGER PRIMARY KEY,"
                    + ToDo.ToDoMetadata.NAME + " TEXT UNIQUE,"
                    + ToDo.ToDoMetadata.VALUE + " BLOB);");
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE " + TODO_TABLE_NAME + " ADD COLUMN "
                    + ToDo.ToDoItem.NOTIFICATION_TIME + " INTEGER;");
        }
    }

}
