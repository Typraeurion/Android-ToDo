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
package com.xmission.trevin.android.todo.provider;

import android.annotation.TargetApi;
import android.app.LoaderManager;
import android.content.Context;
import android.content.Loader;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.xmission.trevin.android.todo.data.ToDoItem;
import com.xmission.trevin.android.todo.data.ToDoPreferences;
import com.xmission.trevin.android.todo.ui.ToDoCursorAdapter;

/**
 * Callbacks to have the {@link LoaderManager} call to provide a
 * {@link ToDoCursor} to the {@link ToDoCursorAdapter}.
 *
 * @author Trevin Beattie
 */
@TargetApi(11)
public class ItemLoaderCallbacks
        implements LoaderManager.LoaderCallbacks<ToDoCursor> {

    private static final String TAG = "ItemLoaderCallbacks";

    private final Context context;
    private final ToDoPreferences sharedPrefs;

    /** Used to map {@link ToDoItem}s from the database to {@link View}s */
    private final ToDoCursorAdapter itemAdapter;

    /** The repository that provides our To Do items */
    private final ToDoRepository repository;

    /**
     * Initialize the callbacks
     *
     * @param context the context in which the loader is being used
     * @param preferences The To Do preferences
     * @param adapter The Adapter which will map {@link ToDoItem}s
     *                from the database to {@link View}s
     * @param repository The repository that provides our To Do items
     */
    public ItemLoaderCallbacks(@NonNull Context context,
                               @NonNull ToDoPreferences preferences,
                               @NonNull ToDoCursorAdapter adapter,
                               @NonNull ToDoRepository repository) {
        this.context = context;
        sharedPrefs = preferences;
        itemAdapter = adapter;
        this.repository = repository;
    }

    @NonNull
    @Override
    public Loader<ToDoCursor> onCreateLoader(int id, @Nullable Bundle args) {
        Log.d(TAG, ".onCreateLoader");
        return new ToDoCursorLoader(context, repository, sharedPrefs);
    }

    @Override
    public void onLoadFinished(@NonNull Loader<ToDoCursor> loader,
                               ToDoCursor cursor) {
        Log.d(TAG, ".onLoadFinished");
        itemAdapter.swapCursor(cursor);
    }

    @Override
    public void onLoaderReset(@NonNull Loader<ToDoCursor> loader) {
        Log.d(TAG, ".onLoaderReset");
        itemAdapter.swapCursor(null);
    }

}
