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
package com.xmission.trevin.android.todo.ui;

import static com.xmission.trevin.android.todo.provider.ToDoSchema.ToDoCategoryColumns.*;

import com.xmission.trevin.android.todo.R;
import com.xmission.trevin.android.todo.data.ToDoCategory;
import com.xmission.trevin.android.todo.provider.ToDoRepository;
import com.xmission.trevin.android.todo.provider.ToDoRepositoryImpl;
import com.xmission.trevin.android.todo.provider.ToDoSchema.*;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Displays a list of To Do categories, allows the user to add to or edit
 * them, and saves the list back to the repository.
 *
 * @author Trevin Beattie
 */
public class CategoryListActivity extends AppCompatActivity {

    private static final String TAG = "CategoryListActivity";

    /** @deprecated */
    public static final String ORIG_NAME = "original " + NAME;

    private ToDoRepository repository = null;

    private boolean isOpen = false;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    /**
     * A copy of the actual categories from the repository.
     * These are the categories which are displayed and may be
     * edited.
     */
    private List<ToDoCategory> categoryList = null;

    /**
     * A copy of the original categories that were read in.
     * We use this to check which ones were modified and thus
     * need to be updated or deleted.  Keyed by database ID.
     */
    private final Map<Long,String> originalNames = new HashMap<>();

    /**
     * The list adapter which provides the categories to display in this
     * activity.  This will be set once we've read the categories,
     * which will be done on a separate (non-UI) thread.
     */
    private CategoryEditorAdapter categoryAdapter = null;

    private ListView listView;

    /**
     * A runner for opening the repository on a non-UI thread,
     * reading the current list of categories, and initializing
     * the list adapter.  (This is run on a separate non-UI thread
     * if on Honeycomb or later.)
     */
    private class OpenRepositoryRunner implements Runnable {
        @Override
        public void run() {
            synchronized (CategoryListActivity.this) {
                repository.open(CategoryListActivity.this);
                isOpen = true;

                // Populate the category list
                categoryList = repository.getCategories();
                Iterator<ToDoCategory> caiter = categoryList.iterator();
                while (caiter.hasNext()) {
                    ToDoCategory category = caiter.next();
                    if (category.getId() == UNFILED) {
                        // Exclude the "Unfiled" category from edits
                        caiter.remove();
                        continue;
                    }
                    originalNames.put(category.getId(), category.getName());
                }

                categoryAdapter = new CategoryEditorAdapter(
                        CategoryListActivity.this, categoryList);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        listView.setAdapter(categoryAdapter);
                    }
                });

                CategoryListActivity.this.notify();
            }
        }
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, ".onCreate");

        setContentView(R.layout.category_list);
        setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);

        listView = findViewById(R.id.CategoryList);

        if (repository == null)
            repository = ToDoRepositoryImpl.getInstance();

        /*
         * The repository should be opened, but not on the UI thread.
         * After opening the repository, we'll read in the current list
         * of categories.  Changes will not be written back out until
         * the activity is finished (clicking "OK").
         */
        Runnable openRepo = new OpenRepositoryRunner();
        if (Looper.getMainLooper().getThread() != Thread.currentThread()) {
            // This could happen if we're running in a test context.
            // We can open the repository directly.
            openRepo.run();
        } else {
            executor.submit(openRepo);
        }

        // Add callbacks
        Button newButton = (Button) findViewById(R.id.CategoryListButtonNew);
        newButton.setOnClickListener(new NewCategoryListener());

        Button okButton = (Button) findViewById(R.id.CategoryListButtonOK);
        okButton.setOnClickListener(new SaveChangesListener());

        Button cancelButton = (Button)
                findViewById(R.id.CategoryListButtonCancel);
        cancelButton.setOnClickListener(new CancelListener());
    }

    private static final DialogInterface.OnClickListener DISMISS_DIALOG_LISTENER =
            new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int id) {
                    dialog.cancel();
                }
            };

    /**
     * Display an error message
     *
     * @param message the message to show
     */
    private void showAlert(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setTitle(R.string.ErrorTitle);
        builder.setMessage(message);
        builder.setNeutralButton(R.string.CategoryListButtonOKText,
                DISMISS_DIALOG_LISTENER);
        builder.show();
    }

    /**
     * Check that the repository has been opened and the list adapter
     * set up.  If not, wait for up to 1 second for it to open.
     * This should be called before any callback tries to use the
     * category list.
     *
     * @return true if the list has been initialized, false if it
     * was not initialized before the timeout.
     */
    private synchronized boolean checkListReady() {
        if (isOpen)
            return true;
        try {
            wait(5000);
            return isOpen;
        } catch (InterruptedException e) {
            Log.e(TAG, "repository failed to open within 1 second", e);
            showAlert(getString(R.string.ErrorDatabaseNotOpen));
            return false;
        }
    }

    /**
     * Callback for adding a new category to the list
     */
    private class NewCategoryListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            if (!checkListReady())
                return;
            Log.d(TAG, "NewCategoryListener: adding a new category to the list");
            // Add a new item to the list
            ToDoCategory newCategory = new ToDoCategory();
            newCategory.setName("");
            categoryList.add(newCategory);
            // Tell the adapter to refresh the display
            categoryAdapter.notifyDataSetChanged();
            // Scroll to the bottom of the list.
            listView.setSelection(categoryList.size() - 1);
            // Once the display has been updated, change the focus to the new row.
            listView.post(new Runnable() {
                @Override
                public void run() {
                    View newRow = listView.getChildAt(listView.getChildCount() - 1);
                    if (newRow instanceof EditText)
                        newRow.requestFocus();
                }
            });
        }
    }

    /**
     * Callback for saving all changes to the category list.
     */
    private class SaveChangesListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            Log.d(TAG, "SaveChangesListener");
            if (!checkListReady()) {
                // Since we haven't had a chance to read the categories
                // in yet, there are no changes to save.  Just exit.
                finish();
                return;
            }
            // Ensure focus has been removed from any text field
            // so that any recent edits have been saved.
            View focus = CategoryListActivity.this.getCurrentFocus();
            if (focus instanceof EditText)
                focus.clearFocus();

            // Before running the SaveChangesRunner, we need to
            // ensure the clearFocus call above has been processed.
            // (This addresses GitHub issue #2 from the NotePad app.)
            v.post(new Runnable() {
                @Override
                public void run() {
                    executor.submit(new SaveChangesRunner());
                }
            });
        }
    }

    /**
     * Callback for canceling all changes.  We simply finish the activity,
     * dropping all work in progress.
     */
    private class CancelListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            Log.d(TAG, "CancelListener");
            finish();
        }
    }

    /**
     * A runner for saving all changes to the category list.
     * We do this in a separate thread because database operations
     * cannot be run on the main UI thread.
     */
    private class SaveChangesRunner implements Runnable {
        @Override
        public void run() {
            try {
                repository.runInTransaction(
                        new SaveChangesTransactionRunner());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        finish();
                    }
                });
            } catch (final Exception e) {
                Log.e(TAG, "SaveChangesRunner", e);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showAlert(String.format("%s: %s",
                                e.getClass().getSimpleName(), e.getMessage()));
                    }
                });
            }
        }
    }

    /**
     * A runner for the repository calls that
     * need to be done within a transaction.
     * This must be called by the repository.
     */
    private class SaveChangesTransactionRunner implements Runnable {
        @Override
        public void run() {
            for (ToDoCategory category : categoryList) {
                if (category.getId() != null) {
                    // Has this entry been modified?
                    if (TextUtils.equals(category.getName(),
                            originalNames.get(category.getId())))
                        continue;
                    if (category.getName().length() == 0)
                        repository.deleteCategory(category.getId());
                    else
                        repository.updateCategory(category.getId(),
                                category.getName());
                } else {
                    if (category.getName().length() > 0)
                        repository.insertCategory(category.getName());
                }
            }
        }
    }

    /*
     * Called when the activity is being destroyed
     */
    @Override
    public void onDestroy() {
        if (isOpen)
            repository.release(this);
        super.onDestroy();
    }

}
