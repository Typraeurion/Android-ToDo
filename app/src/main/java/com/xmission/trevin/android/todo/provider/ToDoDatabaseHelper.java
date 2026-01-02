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

import static com.xmission.trevin.android.todo.provider.ToDoRepositoryImpl.*;

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

    static final String DATABASE_NAME = "to_do.db";

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
                + ToDoSchema.ToDoMetadataColumns._ID + " INTEGER PRIMARY KEY,"
                + ToDoSchema.ToDoMetadataColumns.NAME + " TEXT UNIQUE,"
                + ToDoSchema.ToDoMetadataColumns.VALUE + " BLOB);");

        db.execSQL("CREATE TABLE " + CATEGORY_TABLE_NAME + " ("
                + ToDoSchema.ToDoCategoryColumns._ID + " INTEGER PRIMARY KEY,"
                + ToDoSchema.ToDoCategoryColumns.NAME + " TEXT UNIQUE"
                + ");");
        ContentValues values = new ContentValues();
        values.put(ToDoSchema.ToDoCategoryColumns._ID, ToDoSchema.ToDoCategoryColumns.UNFILED);
        values.put(ToDoSchema.ToDoCategoryColumns.NAME,
                res.getString(R.string.Category_Unfiled));
        db.insert(CATEGORY_TABLE_NAME, null, values);

        db.execSQL("CREATE TABLE " + TODO_TABLE_NAME + " ("
                + ToDoSchema.ToDoItemColumns._ID + " INTEGER PRIMARY KEY,"
                + ToDoSchema.ToDoItemColumns.DESCRIPTION + " TEXT,"
                + ToDoSchema.ToDoItemColumns.CREATE_TIME + " INTEGER,"
                + ToDoSchema.ToDoItemColumns.MOD_TIME + " INTEGER,"
                + ToDoSchema.ToDoItemColumns.DUE_TIME + " INTEGER,"
                + ToDoSchema.ToDoItemColumns.COMPLETED_TIME + " INTEGER,"
                + ToDoSchema.ToDoItemColumns.CHECKED + " INTEGER,"
                + ToDoSchema.ToDoItemColumns.PRIORITY + " INTEGER,"
                + ToDoSchema.ToDoItemColumns.PRIVATE + " INTEGER,"
                + ToDoSchema.ToDoItemColumns.CATEGORY_ID + " INTEGER,"
                + ToDoSchema.ToDoItemColumns.NOTE + " TEXT,"
                + ToDoSchema.ToDoItemColumns.ALARM_DAYS_EARLIER + " INTEGER,"
                + ToDoSchema.ToDoItemColumns.ALARM_TIME + " INTEGER,"
                + ToDoSchema.ToDoItemColumns.REPEAT_INTERVAL + " INTEGER,"
                + ToDoSchema.ToDoItemColumns.REPEAT_INCREMENT + " INTEGER,"
                + ToDoSchema.ToDoItemColumns.REPEAT_WEEK_DAYS + " INTEGER,"
                + ToDoSchema.ToDoItemColumns.REPEAT_DAY + " INTEGER,"
                + ToDoSchema.ToDoItemColumns.REPEAT_DAY2 + " INTEGER,"
                + ToDoSchema.ToDoItemColumns.REPEAT_WEEK + " INTEGER,"
                + ToDoSchema.ToDoItemColumns.REPEAT_WEEK2 + " INTEGER,"
                + ToDoSchema.ToDoItemColumns.REPEAT_MONTH + " INTEGER,"
                + ToDoSchema.ToDoItemColumns.REPEAT_END + " INTEGER,"
                + ToDoSchema.ToDoItemColumns.HIDE_DAYS_EARLIER + " INTEGER,"
                + ToDoSchema.ToDoItemColumns.NOTIFICATION_TIME + " INTEGER"
                + ");");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d(TAG, getClass().getName() + ".onUpgrade("
                + db + "," + oldVersion + "," + newVersion + ")");
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE " + METADATA_TABLE_NAME + " ("
                    + ToDoSchema.ToDoMetadataColumns._ID + " INTEGER PRIMARY KEY,"
                    + ToDoSchema.ToDoMetadataColumns.NAME + " TEXT UNIQUE,"
                    + ToDoSchema.ToDoMetadataColumns.VALUE + " BLOB);");
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE " + TODO_TABLE_NAME + " ADD COLUMN "
                    + ToDoSchema.ToDoItemColumns.NOTIFICATION_TIME + " INTEGER;");
        }
    }

}
