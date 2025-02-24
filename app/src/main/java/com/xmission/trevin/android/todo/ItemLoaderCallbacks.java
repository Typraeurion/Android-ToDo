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
package com.xmission.trevin.android.todo;

import static com.xmission.trevin.android.todo.ToDoListActivity.ITEM_PROJECTION;
import static com.xmission.trevin.android.todo.ToDoListActivity.TPREF_SORT_ORDER;

import android.annotation.TargetApi;
import android.app.LoaderManager;
import android.content.CursorLoader;
import android.content.Loader;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

/**
 * As of Honeycomb (API 11), the application needs to re-initialize
 * the item cursor whenever the activity is restarted.
 * This class provides the callbacks for the item loader manager.
 * Used when ToDoListActivity is created.
 *
 * @deprecated as of Pie (API 28)
 */
@TargetApi(11)
class ItemLoaderCallbacks
        implements LoaderManager.LoaderCallbacks<Cursor> {

    private static final String TAG = "ItemLoaderCallbacks";

    private final ToDoListActivity listActivity;
    private final SharedPreferences sharedPrefs;

    // Used to map To Do entries from the database to views
    private final ToDoCursorAdapter itemAdapter;

    /** The URI by which we were started for the To-Do items */
    private final Uri todoUri;

    ItemLoaderCallbacks(ToDoListActivity activity,
                        SharedPreferences prefs,
                        ToDoCursorAdapter adapter,
                        Uri uri) {
        listActivity = activity;
        sharedPrefs = prefs;
        itemAdapter = adapter;
        todoUri = uri;
    }

    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        Log.d(TAG, ".onCreateLoader");
        CursorLoader loader = new CursorLoader(listActivity,
                // The selection and sort order parameters
                // are overridden in loadInBackground
                todoUri, ITEM_PROJECTION, null, null,
                ToDo.ToDoItem.DEFAULT_SORT_ORDER) {

            //private Cursor myCursor = null;

            @Override
            public Cursor loadInBackground() {
                Log.d(TAG, ".CursorLoader.loadInBackground");
                /*
                if (myCursor != null)
                    myCursor.close();
                */
                int selectedSortOrder = sharedPrefs.getInt(TPREF_SORT_ORDER, 0);
                if ((selectedSortOrder < 0) ||
                        (selectedSortOrder >= ToDo.ToDoItem.USER_SORT_ORDERS.length)) {
                    sharedPrefs.edit().putInt(TPREF_SORT_ORDER, 0).apply();
                    selectedSortOrder = 0;
                }
                setSelection(listActivity.generateWhereClause());
                setSortOrder(ToDo.ToDoItem.USER_SORT_ORDERS[selectedSortOrder]);
                /*
                myCursor = listActivity.getContentResolver().query(todoUri,
                        ITEM_PROJECTION, listActivity.generateWhereClause(), null,
                        ToDo.ToDoItem.USER_SORT_ORDERS[selectedSortOrder]);
                return myCursor;
                */
                return super.loadInBackground();
            }

            @Override
            public void cancelLoadInBackground() {
                Log.d(TAG, ".CursorLoader.cancelLoadInBackground");
                super.cancelLoadInBackground();
            }

            @Override
            public void deliverResult(Cursor cursor) {
                Log.d(TAG, String.format(".CursorLoader.deliverResult(%s)",
                        (cursor == null) ? "null"
                                : cursor.isClosed() ? "closed cursor"
                                : "open cursor"));
                super.deliverResult(cursor);
            }

            @Override
            protected void onStartLoading() {
                Log.d(TAG, ".CursorLoader.onStartLoading");
                super.onStartLoading();
            }

            @Override
            public void onCanceled(Cursor cursor) {
                Log.d(TAG, String.format(".CursorLoader.onCanceled(%s)",
                        (cursor == null) ? "null"
                                : cursor.isClosed() ? "closed cursor"
                                : "open cursor"));
                super.onCanceled(cursor);
            }

            @Override
            protected void onStopLoading() {
                Log.d(TAG, ".CursorLoader.onStopLoading");
                super.onStopLoading();
                /*
                if (myCursor != null)
                    myCursor.close();
                myCursor = null;
                */
            }
        };
        return loader;
    }

    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        Log.d(TAG, ".onLoadFinished");
        itemAdapter.swapCursor(data);
    }

    public void onLoaderReset(Loader<Cursor> loader) {
        Log.d(TAG, ".onLoaderReset");
        itemAdapter.swapCursor(null);
    }

}
