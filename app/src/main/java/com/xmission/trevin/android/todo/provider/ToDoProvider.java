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

import com.xmission.trevin.android.todo.provider.ToDoSchema.ToDoCategoryColumns;
import com.xmission.trevin.android.todo.provider.ToDoSchema.ToDoItemColumns;

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
 *
 * @deprecated Replacing this with a {@link ToDoRepository}
 */
public class ToDoProvider extends ContentProvider {

    private static final String TAG = "ToDoProvider";

    /** @deprecated replace with {@link ToDoRepositoryImpl#DATABASE_VERSION} */
    public static final int DATABASE_VERSION = 3;
    /** @deprecated replace with {@link ToDoRepositoryImpl#CATEGORY_TABLE_NAME} */
    static final String CATEGORY_TABLE_NAME = "category";
    /** @deprecated replace with {@link ToDoRepositoryImpl#METADATA_TABLE_NAME} */
    static final String METADATA_TABLE_NAME = "misc";
    /** @deprecated replace with {@link ToDoRepositoryImpl#TODO_TABLE_NAME} */
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
            orderBy = ToDoSchema.ToDoCategoryColumns.DEFAULT_SORT_ORDER;
            break;

        case CATEGORY_ID:
            qb.setTables(CATEGORY_TABLE_NAME);
            qb.setProjectionMap(categoryProjectionMap);
            qb.appendWhere(ToDoCategoryColumns._ID + " = "
        	    + uri.getPathSegments().get(1));
            orderBy = null;
            break;

        case METADATA:
            qb.setTables(METADATA_TABLE_NAME);
            qb.setProjectionMap(metadataProjectionMap);
            orderBy = ToDoSchema.ToDoMetadataColumns.NAME;
            break;

        case METADATUM_ID:
            qb.setTables(METADATA_TABLE_NAME);
            qb.setProjectionMap(metadataProjectionMap);
            qb.appendWhere(ToDoSchema.ToDoMetadataColumns._ID + " = "
        	    + uri.getPathSegments().get(1));
            orderBy = null;
            break;

        case TODOS:
            qb.setTables(TODO_TABLE_NAME + " JOIN " + CATEGORY_TABLE_NAME
        	    + " ON (" + TODO_TABLE_NAME + "." + ToDoSchema.ToDoItemColumns.CATEGORY_ID
        	    + " = " + CATEGORY_TABLE_NAME + "." + ToDoCategoryColumns._ID + ")");
            qb.setProjectionMap(itemProjectionMap);
            orderBy = ToDoSchema.ToDoItemColumns.DEFAULT_SORT_ORDER;
            break;

        case TODO_ID:
            qb.setTables(TODO_TABLE_NAME + " JOIN " + CATEGORY_TABLE_NAME
        	    + " ON (" + TODO_TABLE_NAME + "." + ToDoItemColumns.CATEGORY_ID
        	    + " = " + CATEGORY_TABLE_NAME + "." + ToDoCategoryColumns._ID + ")");
            qb.setProjectionMap(itemProjectionMap);
            qb.appendWhere(TODO_TABLE_NAME + "." + ToDoSchema.ToDoItemColumns._ID
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
            return ToDoCategoryColumns.CONTENT_TYPE;

        case CATEGORY_ID:
            return ToDoSchema.ToDoCategoryColumns.CONTENT_ITEM_TYPE;

        case METADATA:
            return ToDoSchema.ToDoMetadataColumns.CONTENT_TYPE;

        case METADATUM_ID:
            return ToDoSchema.ToDoMetadataColumns.CONTENT_ITEM_TYPE;

        case TODOS:
            return ToDoSchema.ToDoItemColumns.CONTENT_TYPE;

        case TODO_ID:
            return ToDoItemColumns.CONTENT_ITEM_TYPE;

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
            if (!values.containsKey(ToDoCategoryColumns.NAME))
        	throw new NullPointerException(ToDoCategoryColumns.NAME);

            db = mOpenHelper.getWritableDatabase();
            rowId = db.insert(CATEGORY_TABLE_NAME, ToDoCategoryColumns.NAME, values);
            if (rowId > 0) {
        	Uri categoryUri = ContentUris.withAppendedId(
        		ToDoSchema.ToDoCategoryColumns.CONTENT_URI, rowId);
        	getContext().getContentResolver().notifyChange(categoryUri, null);
        	return categoryUri;
            }
            break;

        case METADATA:

            if (!values.containsKey(ToDoSchema.ToDoMetadataColumns.NAME))
        	throw new NullPointerException(ToDoSchema.ToDoMetadataColumns.NAME);

            db = mOpenHelper.getWritableDatabase();
            rowId = db.insert(METADATA_TABLE_NAME, ToDoSchema.ToDoMetadataColumns.NAME, values);
            if (rowId > 0) {
        	Uri datUri = ContentUris.withAppendedId(
        		ToDoSchema.ToDoMetadataColumns.CONTENT_URI, rowId);
        	getContext().getContentResolver().notifyChange(datUri, null);
        	return datUri;
            }
            break;

        case TODOS:

            long now = System.currentTimeMillis();

            // Make sure that the non-null fields are all set
            if (!values.containsKey(ToDoSchema.ToDoItemColumns.DESCRIPTION))
        	throw new NullPointerException(ToDoSchema.ToDoItemColumns.DESCRIPTION);

            if (!values.containsKey(ToDoSchema.ToDoItemColumns.CREATE_TIME))
        	values.put(ToDoSchema.ToDoItemColumns.CREATE_TIME, now);

            if (!values.containsKey(ToDoItemColumns.MOD_TIME))
        	values.put(ToDoItemColumns.MOD_TIME, now);

            if (!values.containsKey(ToDoSchema.ToDoItemColumns.CHECKED))
        	values.put(ToDoSchema.ToDoItemColumns.CHECKED, 0);

            if (!values.containsKey(ToDoSchema.ToDoItemColumns.PRIORITY))
        	values.put(ToDoItemColumns.PRIORITY, 1);

            if (!values.containsKey(ToDoItemColumns.PRIVATE))
        	values.put(ToDoSchema.ToDoItemColumns.PRIVATE, 0);

            if (!values.containsKey(ToDoSchema.ToDoItemColumns.CATEGORY_ID))
        	values.put(ToDoSchema.ToDoItemColumns.CATEGORY_ID, ToDoCategoryColumns.UNFILED);

            if (!values.containsKey(ToDoSchema.ToDoItemColumns.REPEAT_INTERVAL))
        	values.put(ToDoItemColumns.REPEAT_INTERVAL, ToDoItemColumns.REPEAT_NONE);

            db = mOpenHelper.getWritableDatabase();
            rowId = db.insert(TODO_TABLE_NAME, ToDoItemColumns.DESCRIPTION, values);
            if (rowId > 0) {
        	Uri todoUri = ContentUris.withAppendedId(ToDoItemColumns.CONTENT_URI, rowId);
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
            where = ToDoCategoryColumns._ID + " != " + ToDoSchema.ToDoCategoryColumns.UNFILED + (
        	    TextUtils.isEmpty(where) ? "" : (" AND (" + where + ")"));
            count = db.delete(CATEGORY_TABLE_NAME, where, whereArgs);
            if (count > 0) {
        	// Change the category of all To Do items to Unfiled
        	ContentValues categoryUpdate = new ContentValues();
        	categoryUpdate.put(ToDoSchema.ToDoItemColumns.CATEGORY_ID, ToDoCategoryColumns.UNFILED);
        	update(ToDoSchema.ToDoItemColumns.CONTENT_URI, categoryUpdate, null, null);
            }
            break;

        case CATEGORY_ID:
            long categoryId = Long.parseLong(uri.getPathSegments().get(1));
            if (categoryId == ToDoSchema.ToDoCategoryColumns.UNFILED)
        	// Don't delete the default category
        	return 0;
            db.beginTransaction();
            count = db.delete(CATEGORY_TABLE_NAME,
        	    ToDoCategoryColumns._ID + " = " + categoryId
        	    + (TextUtils.isEmpty(where) ? "" : (" AND (" + where + ")")),
        	    whereArgs);
            if (count > 0) {
        	// Change the category of all To Do items
        	// that were in this category to Unfiled
        	ContentValues categoryUpdate = new ContentValues();
        	categoryUpdate.put(ToDoSchema.ToDoItemColumns.CATEGORY_ID, ToDoCategoryColumns.UNFILED);
        	update(ToDoSchema.ToDoItemColumns.CONTENT_URI, categoryUpdate,
        		ToDoSchema.ToDoItemColumns.CATEGORY_ID + "=" + categoryId, null);
            }
            db.setTransactionSuccessful();
            db.endTransaction();
            break;

        case METADATA:
            count = db.delete(METADATA_TABLE_NAME, where, whereArgs);
            break;

        case METADATUM_ID:
            long datId = Long.parseLong(uri.getPathSegments().get(1));
            count = db.delete(METADATA_TABLE_NAME, ToDoSchema.ToDoMetadataColumns._ID + " = " + datId
        	    + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""),
        	    whereArgs);
            break;

        case TODOS:
            count = db.delete(TODO_TABLE_NAME, where, whereArgs);
            break;

        case TODO_ID:
            long todoId = Long.parseLong(uri.getPathSegments().get(1));
            count = db.delete(TODO_TABLE_NAME, ToDoSchema.ToDoItemColumns._ID + " = " + todoId
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
        	    ToDoCategoryColumns._ID + " = " + categoryId
        	    + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""),
        	    whereArgs);
            break;

        case METADATA:
            count = db.update(METADATA_TABLE_NAME, values, where, whereArgs);
            break;

        case METADATUM_ID:
            long datId = Long.parseLong(uri.getPathSegments().get(1));
            count = db.update(METADATA_TABLE_NAME, values,
        	    ToDoSchema.ToDoMetadataColumns._ID + " = " + datId
        	    + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""),
        	    whereArgs);
            break;

        case TODOS:
            count = db.update(TODO_TABLE_NAME, values, where, whereArgs);
            break;

        case TODO_ID:
            long todoId = Long.parseLong(uri.getPathSegments().get(1));
            count = db.update(TODO_TABLE_NAME, values,
        	    ToDoItemColumns._ID + " = " + todoId
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
        sUriMatcher.addURI(ToDoSchema.AUTHORITY, "categories", CATEGORIES);
        sUriMatcher.addURI(ToDoSchema.AUTHORITY, "categories/#", CATEGORY_ID);
        sUriMatcher.addURI(ToDoSchema.AUTHORITY, "misc", METADATA);
        sUriMatcher.addURI(ToDoSchema.AUTHORITY, "misc/#", METADATUM_ID);
        sUriMatcher.addURI(ToDoSchema.AUTHORITY, "todo", TODOS);
        sUriMatcher.addURI(ToDoSchema.AUTHORITY, "todo/#", TODO_ID);

        categoryProjectionMap = new HashMap<>();
        categoryProjectionMap.put(ToDoCategoryColumns._ID, ToDoSchema.ToDoCategoryColumns._ID);
        categoryProjectionMap.put(ToDoSchema.ToDoCategoryColumns.NAME, ToDoCategoryColumns.NAME);
        metadataProjectionMap = new HashMap<>();
        metadataProjectionMap.put(ToDoSchema.ToDoMetadataColumns._ID, ToDoSchema.ToDoMetadataColumns._ID);
        metadataProjectionMap.put(ToDoSchema.ToDoMetadataColumns.NAME, ToDoSchema.ToDoMetadataColumns.NAME);
        metadataProjectionMap.put(ToDoSchema.ToDoMetadataColumns.VALUE, ToDoSchema.ToDoMetadataColumns.VALUE);
        itemProjectionMap = new HashMap<>();
        itemProjectionMap.put(ToDoItemColumns._ID,
        	TODO_TABLE_NAME + "." + ToDoSchema.ToDoItemColumns._ID);
        itemProjectionMap.put(ToDoSchema.ToDoItemColumns.DESCRIPTION, ToDoSchema.ToDoItemColumns.DESCRIPTION);
        itemProjectionMap.put(ToDoSchema.ToDoItemColumns.CREATE_TIME, ToDoSchema.ToDoItemColumns.CREATE_TIME);
        itemProjectionMap.put(ToDoSchema.ToDoItemColumns.MOD_TIME, ToDoSchema.ToDoItemColumns.MOD_TIME);
        itemProjectionMap.put(ToDoSchema.ToDoItemColumns.DUE_TIME, ToDoSchema.ToDoItemColumns.DUE_TIME);
        itemProjectionMap.put(ToDoItemColumns.COMPLETED_TIME,
        	ToDoSchema.ToDoItemColumns.COMPLETED_TIME);
        itemProjectionMap.put(ToDoItemColumns.CHECKED, ToDoSchema.ToDoItemColumns.CHECKED);
        itemProjectionMap.put(ToDoSchema.ToDoItemColumns.PRIORITY, ToDoItemColumns.PRIORITY);
        itemProjectionMap.put(ToDoSchema.ToDoItemColumns.PRIVATE, ToDoItemColumns.PRIVATE);
        itemProjectionMap.put(ToDoItemColumns.CATEGORY_ID, ToDoSchema.ToDoItemColumns.CATEGORY_ID);
        itemProjectionMap.put(ToDoItemColumns.CATEGORY_NAME,
        	CATEGORY_TABLE_NAME + "." + ToDoSchema.ToDoCategoryColumns.NAME
        	+ " AS " + ToDoItemColumns.CATEGORY_NAME);
        itemProjectionMap.put(ToDoSchema.ToDoItemColumns.NOTE, ToDoItemColumns.NOTE);
	itemProjectionMap.put(ToDoItemColumns.ALARM_DAYS_EARLIER,
		ToDoSchema.ToDoItemColumns.ALARM_DAYS_EARLIER);
	itemProjectionMap.put(ToDoItemColumns.ALARM_TIME, ToDoSchema.ToDoItemColumns.ALARM_TIME);
	itemProjectionMap.put(ToDoItemColumns.REPEAT_INTERVAL,
		ToDoSchema.ToDoItemColumns.REPEAT_INTERVAL);
	itemProjectionMap.put(ToDoSchema.ToDoItemColumns.REPEAT_INCREMENT,
		ToDoItemColumns.REPEAT_INCREMENT);
	itemProjectionMap.put(ToDoSchema.ToDoItemColumns.REPEAT_WEEK_DAYS,
		ToDoSchema.ToDoItemColumns.REPEAT_WEEK_DAYS);
	itemProjectionMap.put(ToDoItemColumns.REPEAT_DAY,
		ToDoItemColumns.REPEAT_DAY);
	itemProjectionMap.put(ToDoSchema.ToDoItemColumns.REPEAT_DAY2,
		ToDoItemColumns.REPEAT_DAY2);
	itemProjectionMap.put(ToDoSchema.ToDoItemColumns.REPEAT_WEEK,
		ToDoSchema.ToDoItemColumns.REPEAT_WEEK);
	itemProjectionMap.put(ToDoItemColumns.REPEAT_WEEK2,
		ToDoItemColumns.REPEAT_WEEK2);
	itemProjectionMap.put(ToDoSchema.ToDoItemColumns.REPEAT_MONTH,
		ToDoItemColumns.REPEAT_MONTH);
	itemProjectionMap.put(ToDoSchema.ToDoItemColumns.REPEAT_END, ToDoSchema.ToDoItemColumns.REPEAT_END);
	itemProjectionMap.put(ToDoItemColumns.HIDE_DAYS_EARLIER,
		ToDoSchema.ToDoItemColumns.HIDE_DAYS_EARLIER);
	itemProjectionMap.put(ToDoSchema.ToDoItemColumns.NOTIFICATION_TIME,
		ToDoSchema.ToDoItemColumns.NOTIFICATION_TIME);
    }

}
