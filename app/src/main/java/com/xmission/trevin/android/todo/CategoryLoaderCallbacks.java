package com.xmission.trevin.android.todo;

import static com.xmission.trevin.android.todo.ToDoListActivity.CATEGORY_PROJECTION;
import static com.xmission.trevin.android.todo.ToDoListActivity.TPREF_SELECTED_CATEGORY;

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
 * the category cursor whenever the activity is restarted.
 * This class provides the callbacks for the category loader manager.
 * Used when ToDoListActivity is created.
 *
 * @deprecated as of Pie (API 28)
 */
@TargetApi(11)
class CategoryLoaderCallbacks
        implements LoaderManager.LoaderCallbacks<Cursor> {

    private static final String TAG = "CategoryLoaderCallbacks";

    private final ToDoListActivity listActivity;
    private final SharedPreferences sharedPrefs;

    // Used to map categories from the database to views
    private final CategoryFilterCursorAdapter categoryAdapter;

    /** The URI by which we were started for the categories */
    private final Uri categoryUri;

    CategoryLoaderCallbacks(ToDoListActivity activity,
                            SharedPreferences prefs,
                            CategoryFilterCursorAdapter adapter,
                            Uri uri) {
        listActivity = activity;
        sharedPrefs = prefs;
        categoryAdapter = adapter;
        categoryUri = uri;
    }

    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        Log.d(TAG, ".onCreateLoader");
        CursorLoader loader = new CursorLoader(listActivity,
                categoryUri, CATEGORY_PROJECTION, null, null,
                ToDo.ToDoCategory.DEFAULT_SORT_ORDER) {

            //private Cursor myCursor = null;

            @Override
            public Cursor loadInBackground() {
                Log.d(TAG, ".CursorLoader.loadInBackground");
                /*
                if (myCursor != null)
                    myCursor.close();
                myCursor = listActivity.getContentResolver().query(categoryUri,
                        CATEGORY_PROJECTION, null, null,
                        ToDo.ToDoCategory.DEFAULT_SORT_ORDER);
                // Ensure the cursor window is filled (from super class)
                myCursor.getCount();
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
        categoryAdapter.swapCursor(data);
        listActivity.setCategorySpinnerByID(
                sharedPrefs.getLong(TPREF_SELECTED_CATEGORY, -1));
    }

    public void onLoaderReset(Loader<Cursor> loader) {
        Log.d(TAG, ".onLoaderReset");
        categoryAdapter.swapCursor(null);
    }

}
