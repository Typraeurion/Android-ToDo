/*
 * Copyright © 2011–2025 Trevin Beattie
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

import com.xmission.trevin.android.todo.provider.ToDo.ToDoCategory;
import com.xmission.trevin.android.todo.provider.ToDo.ToDoItem;
import com.xmission.trevin.android.todo.provider.ToDo.ToDoMetadata;

import java.util.Arrays;
import java.util.HashMap;

import android.content.*;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.*;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.NonNull;

/**
 * Provides access to a database of To Do items and categories.
 *
 * @author Trevin Beattie
 */
public class ToDoProvider extends ContentProvider {

    private static final String TAG = "ToDoProvider";

    static final String DATABASE_NAME = "to_do.db";
    public static final int DATABASE_VERSION = 3;
    static final String CATEGORY_TABLE_NAME = "category";
    static final String METADATA_TABLE_NAME = "misc";
    public static final String TODO_TABLE_NAME = "todo";

    /** Projection fields which are available in a category query */
    private static HashMap<String, String> categoryProjectionMap;

    /** Projection fields which are available in a metadata query */
    private static HashMap<String, String> metadataProjectionMap;

    /** Projection fields which are available in a to-do item query */
    private static HashMap<String, String> itemProjectionMap;

    private static final int CATEGORIES = 3;
    private static final int CATEGORY_ID = 4;
    private static final int METADATA = 5;
    private static final int METADATUM_ID = 6;
    private static final int TODOS = 1;
    private static final int TODO_ID = 2;

    private static final UriMatcher sUriMatcher;

    private ToDoDatabaseHelper mOpenHelper;

    @Override
    public boolean onCreate() {
	Log.d(TAG, getClass().getSimpleName() + ".onCreate");
        mOpenHelper = new ToDoDatabaseHelper(getContext());
	return true;
    }

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
	Log.d(TAG, String.format("%s.query(%s, %s, \"%s\"),",
                getClass().getSimpleName(), uri,
		Arrays.toString(projection), selection));
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

        // In case no sort order is specified set the default
        String orderBy;
        switch (sUriMatcher.match(uri)) {
        case CATEGORIES:
            qb.setTables(CATEGORY_TABLE_NAME);
            qb.setProjectionMap(categoryProjectionMap);
            orderBy = ToDoCategory.DEFAULT_SORT_ORDER;
            break;

        case CATEGORY_ID:
            qb.setTables(CATEGORY_TABLE_NAME);
            qb.setProjectionMap(categoryProjectionMap);
            qb.appendWhere(ToDoCategory._ID + " = "
        	    + uri.getPathSegments().get(1));
            orderBy = null;
            break;

        case METADATA:
            qb.setTables(METADATA_TABLE_NAME);
            qb.setProjectionMap(metadataProjectionMap);
            orderBy = ToDoMetadata.NAME;
            break;

        case METADATUM_ID:
            qb.setTables(METADATA_TABLE_NAME);
            qb.setProjectionMap(metadataProjectionMap);
            qb.appendWhere(ToDoMetadata._ID + " = "
        	    + uri.getPathSegments().get(1));
            orderBy = null;
            break;

        case TODOS:
            qb.setTables(TODO_TABLE_NAME + " JOIN " + CATEGORY_TABLE_NAME
        	    + " ON (" + TODO_TABLE_NAME + "." + ToDoItem.CATEGORY_ID
        	    + " = " + CATEGORY_TABLE_NAME + "." + ToDoCategory._ID + ")");
            qb.setProjectionMap(itemProjectionMap);
            orderBy = ToDoItem.DEFAULT_SORT_ORDER;
            break;

        case TODO_ID:
            qb.setTables(TODO_TABLE_NAME + " JOIN " + CATEGORY_TABLE_NAME
        	    + " ON (" + TODO_TABLE_NAME + "." + ToDoItem.CATEGORY_ID
        	    + " = " + CATEGORY_TABLE_NAME + "." + ToDoCategory._ID + ")");
            qb.setProjectionMap(itemProjectionMap);
            qb.appendWhere(TODO_TABLE_NAME + "." + ToDoItem._ID
        	    + " = " + uri.getPathSegments().get(1));
            orderBy = null;
            break;

        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        if (!TextUtils.isEmpty(sortOrder))
            orderBy = sortOrder;

        // Get the database and run the query
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, orderBy);

        // Tell the cursor what uri to watch, so it knows when its source data changes
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    @Override
    public String getType(@NonNull Uri uri) {
	Log.d(TAG, String.format("%s.getType(%s)",
                getClass().getSimpleName(), uri));
        switch (sUriMatcher.match(uri)) {
        case CATEGORIES:
            return ToDoCategory.CONTENT_TYPE;

        case CATEGORY_ID:
            return ToDoCategory.CONTENT_ITEM_TYPE;

        case METADATA:
            return ToDoMetadata.CONTENT_TYPE;

        case METADATUM_ID:
            return ToDoMetadata.CONTENT_ITEM_TYPE;

        case TODOS:
            return ToDoItem.CONTENT_TYPE;

        case TODO_ID:
            return ToDoItem.CONTENT_ITEM_TYPE;

        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public Uri insert(@NonNull Uri uri, ContentValues initialValues) {
	Log.d(TAG, String.format("%s.insert(%s, %s)",
                getClass().getSimpleName(), uri, initialValues));
	ContentValues values;
        if (initialValues != null) {
            values = new ContentValues(initialValues);
        } else {
            values = new ContentValues();
        }

        SQLiteDatabase db;
        long rowId;

        // Validate the requested uri
        switch (sUriMatcher.match(uri)) {
        default:
            throw new IllegalArgumentException("Unknown URI " + uri);

        case CATEGORIES:

            // Make sure that the fields are all set
            if (!values.containsKey(ToDoCategory.NAME))
        	throw new NullPointerException(ToDoCategory.NAME);

            db = mOpenHelper.getWritableDatabase();
            rowId = db.insert(CATEGORY_TABLE_NAME, ToDoCategory.NAME, values);
            if (rowId > 0) {
        	Uri categoryUri = ContentUris.withAppendedId(
        		ToDoCategory.CONTENT_URI, rowId);
        	getContext().getContentResolver().notifyChange(categoryUri, null);
        	return categoryUri;
            }
            break;

        case METADATA:

            if (!values.containsKey(ToDoMetadata.NAME))
        	throw new NullPointerException(ToDoMetadata.NAME);

            db = mOpenHelper.getWritableDatabase();
            rowId = db.insert(METADATA_TABLE_NAME, ToDoMetadata.NAME, values);
            if (rowId > 0) {
        	Uri datUri = ContentUris.withAppendedId(
        		ToDoMetadata.CONTENT_URI, rowId);
        	getContext().getContentResolver().notifyChange(datUri, null);
        	return datUri;
            }
            break;

        case TODOS:

            long now = System.currentTimeMillis();

            // Make sure that the non-null fields are all set
            if (!values.containsKey(ToDoItem.DESCRIPTION))
        	throw new NullPointerException(ToDoItem.DESCRIPTION);

            if (!values.containsKey(ToDoItem.CREATE_TIME))
        	values.put(ToDoItem.CREATE_TIME, now);

            if (!values.containsKey(ToDoItem.MOD_TIME))
        	values.put(ToDoItem.MOD_TIME, now);

            if (!values.containsKey(ToDoItem.CHECKED))
        	values.put(ToDoItem.CHECKED, 0);

            if (!values.containsKey(ToDoItem.PRIORITY))
        	values.put(ToDoItem.PRIORITY, 1);

            if (!values.containsKey(ToDoItem.PRIVATE))
        	values.put(ToDoItem.PRIVATE, 0);

            if (!values.containsKey(ToDoItem.CATEGORY_ID))
        	values.put(ToDoItem.CATEGORY_ID, ToDoCategory.UNFILED);

            if (!values.containsKey(ToDoItem.REPEAT_INTERVAL))
        	values.put(ToDoItem.REPEAT_INTERVAL, ToDoItem.REPEAT_NONE);

            db = mOpenHelper.getWritableDatabase();
            rowId = db.insert(TODO_TABLE_NAME, ToDoItem.DESCRIPTION, values);
            if (rowId > 0) {
        	Uri todoUri = ContentUris.withAppendedId(ToDoItem.CONTENT_URI, rowId);
        	getContext().getContentResolver().notifyChange(todoUri, null);
        	return todoUri;
            }
            break;
        }

        throw new SQLException("Failed to insert row into " + uri);
    }

    @Override
    public int delete(@NonNull Uri uri, String where, String[] whereArgs) {
	Log.d(TAG, String.format("%s.delete(%s)",
                getClass().getSimpleName(), uri));
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count;
        switch (sUriMatcher.match(uri)) {
        case CATEGORIES:
            // Make sure we don't delete the default category
            where = ToDoCategory._ID + " != " + ToDoCategory.UNFILED + (
        	    TextUtils.isEmpty(where) ? "" : (" AND (" + where + ")"));
            count = db.delete(CATEGORY_TABLE_NAME, where, whereArgs);
            if (count > 0) {
        	// Change the category of all To Do items to Unfiled
        	ContentValues categoryUpdate = new ContentValues();
        	categoryUpdate.put(ToDoItem.CATEGORY_ID, ToDoCategory.UNFILED);
        	update(ToDoItem.CONTENT_URI, categoryUpdate, null, null);
            }
            break;

        case CATEGORY_ID:
            long categoryId = Long.parseLong(uri.getPathSegments().get(1));
            if (categoryId == ToDoCategory.UNFILED)
        	// Don't delete the default category
        	return 0;
            db.beginTransaction();
            count = db.delete(CATEGORY_TABLE_NAME,
        	    ToDoCategory._ID + " = " + categoryId
        	    + (TextUtils.isEmpty(where) ? "" : (" AND (" + where + ")")),
        	    whereArgs);
            if (count > 0) {
        	// Change the category of all To Do items
        	// that were in this category to Unfiled
        	ContentValues categoryUpdate = new ContentValues();
        	categoryUpdate.put(ToDoItem.CATEGORY_ID, ToDoCategory.UNFILED);
        	update(ToDoItem.CONTENT_URI, categoryUpdate,
        		ToDoItem.CATEGORY_ID + "=" + categoryId, null);
            }
            db.setTransactionSuccessful();
            db.endTransaction();
            break;

        case METADATA:
            count = db.delete(METADATA_TABLE_NAME, where, whereArgs);
            break;

        case METADATUM_ID:
            long datId = Long.parseLong(uri.getPathSegments().get(1));
            count = db.delete(METADATA_TABLE_NAME, ToDoMetadata._ID + " = " + datId
        	    + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""),
        	    whereArgs);
            break;

        case TODOS:
            count = db.delete(TODO_TABLE_NAME, where, whereArgs);
            break;

        case TODO_ID:
            long todoId = Long.parseLong(uri.getPathSegments().get(1));
            count = db.delete(TODO_TABLE_NAME, ToDoItem._ID + " = " + todoId
                    + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""),
                    whereArgs);
            break;

        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String where,
	    String[] whereArgs) {
	Log.d(TAG, String.format("%s.update(%s, %s)",
                getClass().getSimpleName(), uri, values));
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count;
        switch (sUriMatcher.match(uri)) {
        case CATEGORIES:
            throw new UnsupportedOperationException(
        	    "Cannot modify multiple categories");

        case CATEGORY_ID:
            long categoryId = Long.parseLong(uri.getPathSegments().get(1));
            // To do: prevent duplicate names
            count = db.update(CATEGORY_TABLE_NAME, values,
        	    ToDoCategory._ID + " = " + categoryId
        	    + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""),
        	    whereArgs);
            break;

        case METADATA:
            count = db.update(METADATA_TABLE_NAME, values, where, whereArgs);
            break;

        case METADATUM_ID:
            long datId = Long.parseLong(uri.getPathSegments().get(1));
            count = db.update(METADATA_TABLE_NAME, values,
        	    ToDoMetadata._ID + " = " + datId
        	    + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""),
        	    whereArgs);
            break;

        case TODOS:
            count = db.update(TODO_TABLE_NAME, values, where, whereArgs);
            break;

        case TODO_ID:
            long todoId = Long.parseLong(uri.getPathSegments().get(1));
            count = db.update(TODO_TABLE_NAME, values,
        	    ToDoItem._ID + " = " + todoId
                    + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""),
                    whereArgs);
            break;

        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(ToDo.AUTHORITY, "categories", CATEGORIES);
        sUriMatcher.addURI(ToDo.AUTHORITY, "categories/#", CATEGORY_ID);
        sUriMatcher.addURI(ToDo.AUTHORITY, "misc", METADATA);
        sUriMatcher.addURI(ToDo.AUTHORITY, "misc/#", METADATUM_ID);
        sUriMatcher.addURI(ToDo.AUTHORITY, "todo", TODOS);
        sUriMatcher.addURI(ToDo.AUTHORITY, "todo/#", TODO_ID);

        categoryProjectionMap = new HashMap<>();
        categoryProjectionMap.put(ToDoCategory._ID, ToDoCategory._ID);
        categoryProjectionMap.put(ToDoCategory.NAME, ToDoCategory.NAME);
        metadataProjectionMap = new HashMap<>();
        metadataProjectionMap.put(ToDoMetadata._ID, ToDoMetadata._ID);
        metadataProjectionMap.put(ToDoMetadata.NAME, ToDoMetadata.NAME);
        metadataProjectionMap.put(ToDoMetadata.VALUE, ToDoMetadata.VALUE);
        itemProjectionMap = new HashMap<>();
        itemProjectionMap.put(ToDoItem._ID,
        	TODO_TABLE_NAME + "." + ToDoItem._ID);
        itemProjectionMap.put(ToDoItem.DESCRIPTION, ToDoItem.DESCRIPTION);
        itemProjectionMap.put(ToDoItem.CREATE_TIME, ToDoItem.CREATE_TIME);
        itemProjectionMap.put(ToDoItem.MOD_TIME, ToDoItem.MOD_TIME);
        itemProjectionMap.put(ToDoItem.DUE_TIME, ToDoItem.DUE_TIME);
        itemProjectionMap.put(ToDoItem.COMPLETED_TIME,
        	ToDoItem.COMPLETED_TIME);
        itemProjectionMap.put(ToDoItem.CHECKED, ToDoItem.CHECKED);
        itemProjectionMap.put(ToDoItem.PRIORITY, ToDoItem.PRIORITY);
        itemProjectionMap.put(ToDoItem.PRIVATE, ToDoItem.PRIVATE);
        itemProjectionMap.put(ToDoItem.CATEGORY_ID, ToDoItem.CATEGORY_ID);
        itemProjectionMap.put(ToDoItem.CATEGORY_NAME,
        	CATEGORY_TABLE_NAME + "." + ToDoCategory.NAME
        	+ " AS " + ToDoItem.CATEGORY_NAME);
        itemProjectionMap.put(ToDoItem.NOTE, ToDoItem.NOTE);
	itemProjectionMap.put(ToDoItem.ALARM_DAYS_EARLIER,
		ToDoItem.ALARM_DAYS_EARLIER);
	itemProjectionMap.put(ToDoItem.ALARM_TIME, ToDoItem.ALARM_TIME);
	itemProjectionMap.put(ToDoItem.REPEAT_INTERVAL,
		ToDoItem.REPEAT_INTERVAL);
	itemProjectionMap.put(ToDoItem.REPEAT_INCREMENT,
		ToDoItem.REPEAT_INCREMENT);
	itemProjectionMap.put(ToDoItem.REPEAT_WEEK_DAYS,
		ToDoItem.REPEAT_WEEK_DAYS);
	itemProjectionMap.put(ToDoItem.REPEAT_DAY,
		ToDoItem.REPEAT_DAY);
	itemProjectionMap.put(ToDoItem.REPEAT_DAY2,
		ToDoItem.REPEAT_DAY2);
	itemProjectionMap.put(ToDoItem.REPEAT_WEEK,
		ToDoItem.REPEAT_WEEK);
	itemProjectionMap.put(ToDoItem.REPEAT_WEEK2,
		ToDoItem.REPEAT_WEEK2);
	itemProjectionMap.put(ToDoItem.REPEAT_MONTH,
		ToDoItem.REPEAT_MONTH);
	itemProjectionMap.put(ToDoItem.REPEAT_END, ToDoItem.REPEAT_END);
	itemProjectionMap.put(ToDoItem.HIDE_DAYS_EARLIER,
		ToDoItem.HIDE_DAYS_EARLIER);
	itemProjectionMap.put(ToDoItem.NOTIFICATION_TIME,
		ToDoItem.NOTIFICATION_TIME);
    }

}
