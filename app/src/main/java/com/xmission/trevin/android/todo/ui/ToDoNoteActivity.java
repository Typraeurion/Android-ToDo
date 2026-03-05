/*
 * Copyright © 2011–2026 Trevin Beattie
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

import static com.xmission.trevin.android.todo.ui.ToDoListActivity.EXTRA_ITEM_ID;

import com.xmission.trevin.android.todo.R;
import com.xmission.trevin.android.todo.data.ToDoItem;
import com.xmission.trevin.android.todo.provider.ToDoRepository;
import com.xmission.trevin.android.todo.provider.ToDoRepositoryImpl;
import com.xmission.trevin.android.todo.provider.ToDoSchema.*;
import com.xmission.trevin.android.todo.util.EncryptionException;
import com.xmission.trevin.android.todo.util.StringEncryption;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.*;
import android.widget.*;

import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Displays the note of a To Do item.  Will display the item from the
 * {@link Uri} provided in the intent, which is required.
 *
 * @author Trevin Beattie
 */
public class ToDoNoteActivity extends Activity {

    private static final String TAG = "ToDoNoteActivity";

    /**
     * Name of the intent extra that holds the current item description
     * if we are being started from {@link ToDoDetailsActivity}, since the
     * description may be for a new item or may be in the middle of changes.
     */
    public static final String EXTRA_ITEM_DESCRIPTION = "ToDoDescription";

    /**
     * Name of the intent extra that holds the note content if we&rsquo;re
     * returning it to {@link ToDoDetailsActivity}.
     */
    public static final String EXTRA_ITEM_NOTE = "ToDoNote";

    /**
     * The ID of the To-Do item whose note we are editing;
     * {@code null} for a new item.
     */
    private Long todoId;

    /** The description of the To Do item that we are working on. */
    private String description;

    /** The original contents of the note (or an empty string for a new note) */
    private String oldNoteText;

    /** Whether we are handling a note from ToDoDetailsActivity */
    boolean isDetailHandoff;

    /** The To Do database */
    ToDoRepository repository = null;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    /** The note */
    EditText toDoNote = null;

    /** The &ldquoOK&rdquo; button for saving the note */
    Button okButton = null;

    /** The &ldquo;Delete&rdquo; button for existing notes */
    Button deleteButton = null;

    StringEncryption encryptor;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);

        Object savedData;
        if (savedInstanceState != null) {
            savedData = savedInstanceState.getSerializable("noteFormData");
        } else {
            savedData = getLastNonConfigurationInstance();
        }
        boolean hasSavedState = (savedData instanceof NoteFormData);

        if (hasSavedState) {
            todoId = ((NoteFormData) savedData).todoId;

            Log.d(TAG, String.format(Locale.US,
                    ".onCreate(%s); savedData=%s",
                    savedInstanceState, savedData));

        } else {
            Intent intent = getIntent();
            todoId = null;
            description = getString(R.string.UntitledItem);
            oldNoteText = "";
            if (intent.hasExtra(EXTRA_ITEM_ID)) {
                todoId = intent.getLongExtra(EXTRA_ITEM_ID, -1);
            }
            isDetailHandoff = intent.hasExtra(EXTRA_ITEM_DESCRIPTION);
            if (isDetailHandoff) {
                description = intent.getStringExtra(EXTRA_ITEM_DESCRIPTION);
                oldNoteText = intent.getStringExtra(EXTRA_ITEM_NOTE);
                if (oldNoteText == null)
                    oldNoteText = "";
                Log.d(TAG, String.format(Locale.US,
                        ".onCreate(%s); id=%s, description=\"%s\", note=\"%s%s\"",
                        savedInstanceState, todoId, description,
                        oldNoteText.substring(0, Math.min(oldNoteText.length(), 80)),
                        (oldNoteText.length() > 80) ? "\u2026" : ""));
            } else {
                Log.d(TAG, String.format(Locale.US,
                        ".onCreate(%s); id=%s", savedInstanceState, todoId));
            }
        }

        if (repository == null)
            repository = ToDoRepositoryImpl.getInstance();
        encryptor = StringEncryption.holdGlobalEncryption();

        // Inflate our view so we can find our field
        setContentView(R.layout.note);
        toDoNote = findViewById(R.id.NoteEditText);

        if (hasSavedState) {
            restoreState((NoteFormData) savedData);
        }

        else {
            // Initialize the default state for now; the OpenRepositoryRunner
            // will load any item and update the form when it's ready.
            setTitle(getResources().getString(R.string.app_name)
                    + " \u2015 " + description);

            toDoNote.setText(oldNoteText);
        }

        // Set callbacks
        okButton = findViewById(R.id.NoteButtonOK);
        okButton.setOnClickListener(new OKButtonOnClickListener());
        okButton.setEnabled(isDetailHandoff);

        deleteButton = findViewById(R.id.NoteButtonDelete);
        deleteButton.setOnClickListener(new DeleteButtonOnClickListener());
        deleteButton.setEnabled(isDetailHandoff);

        // Connect to the database (on a non-UI thread) and populate the UI
        Runnable openRepo = new OpenRepositoryRunner(
                !(isDetailHandoff || hasSavedState));
        executor.submit(openRepo);
    }

    /**
     * A runner to open the database on a non-UI thread
     * (if on Honeycomb or later) and then load the note if needed.
     */
    private class OpenRepositoryRunner implements Runnable {
        final boolean loadNote;
        OpenRepositoryRunner(boolean loadNote) {
            this.loadNote = loadNote;
        }
        @Override
        public void run() {
            repository.open(ToDoNoteActivity.this);
            ToDoItem todo = null;
            if (loadNote)
                todo = repository.getItemById(todoId);
            runOnUiThread(new FinalizeUIRunner(todo));
        }
    }

    /**
     * Called (on the UI thread) after we&rsquo;ve established a
     * connection to the database and read the To Do item (if any)
     * to populate the UI and enable buttons.
     */
    private class FinalizeUIRunner implements Runnable {
        final ToDoItem todo;
        /**
         * @param loadedItem the item to read the description and note
         * from, or {@code null} if we were called from the details
         * activity or restored from a saved state.
         */
        FinalizeUIRunner(@Nullable ToDoItem loadedItem) {
            todo = loadedItem;
        }
        @Override
        public void run() {
            if (todo != null) {
                description = todo.isEncrypted()
                        ? getResources().getString(R.string.PasswordProtected)
                        : todo.getDescription();
                oldNoteText = todo.isEncrypted()
                        ? description : todo.getNote();
                if (todo.isEncrypted()) {
                    if (encryptor.hasKey()) {
                        try {
                            description = encryptor.decrypt(
                                    todo.getEncryptedDescription());
                            if (todo.getEncryptedNote() != null)
                                oldNoteText = encryptor.decrypt(
                                        todo.getEncryptedNote());
                        } catch (EncryptionException e) {
                            Toast.makeText(ToDoNoteActivity.this,
                                    e.getMessage(), Toast.LENGTH_LONG)
                                    .show();
                            finish();
                            return;
                        }
                    } else {
                        Toast.makeText(ToDoNoteActivity.this,
                                R.string.PasswordProtected, Toast.LENGTH_LONG)
                                .show();
                        finish();
                        return;
                    }
                }
                if (oldNoteText == null)
                    oldNoteText = "";
                setTitle(getResources().getString(R.string.app_name)
                        + " \u2015 " + description);
                toDoNote.setText(oldNoteText);

            }
            okButton.setEnabled(true);
            deleteButton.setEnabled(true);
        }
    }

    /**
     * Restore the state of the activity from a saved configuration
     *
     * @param data the saved configuration data
     */
    private void restoreState(NoteFormData data) {
        todoId = data.todoId;
        description = data.description;
        oldNoteText = data.oldNoteText;
        isDetailHandoff = data.isDetailHandoff;

        setTitle(getResources().getString(R.string.app_name)
                + " \u2015 " + description);
        toDoNote.setText(data.currentNoteText);
    }

    /**
     * Called when the activity is about to be destroyed
     * and then immediately restarted (such as an orientation change).
     */
    @Override
    public NoteFormData onRetainNonConfigurationInstance() {
        Log.d(TAG, ".onRetainNonConfigurationInstance");
        NoteFormData data = new NoteFormData();
        data.todoId = todoId;
        data.isDetailHandoff = isDetailHandoff;
        data.description = description;
        data.oldNoteText = oldNoteText;
        data.currentNoteText = toDoNote.getText().toString();
        return data;
    }

    /**
     * Called when the activity is about to be destroyed and then
     * restarted at some indefinite point in the future,
     * for example when Android needs to reclaim resources.
     *
     * @param outState a container in which to save the state.
     */
    @Override
    public void onSaveInstanceState(Bundle outState) {
        Log.d(TAG, ".onSaveInstanceState");
        outState.putSerializable("noteFormData",
                onRetainNonConfigurationInstance());
        super.onSaveInstanceState(outState);
    }

    // This alert dialog is made available at the package level for testing
    AlertDialog discardConfirmationDialog = null;

    /** Called when the user presses the Back button */
    @Override
    public void onBackPressed() {
        Log.d(TAG, "Back button pressed");
        // Did the user make any changes to the note?
        String note = toDoNote.getText().toString();
        if (!TextUtils.equals(oldNoteText, note)) {
            Log.d(TAG, "Note has been changed; asking for confirmation");
            if (discardConfirmationDialog == null) {
                discardConfirmationDialog = new AlertDialog
                        .Builder(this)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.ConfirmUnsavedChanges)
                        .setTitle(R.string.AlertUnsavedChangesTitle)
                        .setNegativeButton(R.string.ConfirmationButtonCancel,
                                ToDoDetailsActivity.DISMISS_LISTENER)
                        .setPositiveButton(R.string.ConfirmationButtonDiscard,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog,
                                                        int which) {
                                        dialog.dismiss();
                                        Log.d(TAG, "Calling superclass onBackPressed");
                                        ToDoNoteActivity.super.onBackPressed();
                                    }
                                })
                        .create();
            }
            discardConfirmationDialog.show();
            return;
        }
        super.onBackPressed();
    }

    /** Called when the activity is about to be destroyed */
    @Override
    public void onDestroy() {
        repository.release(this);
        StringEncryption.releaseGlobalEncryption(this);
        super.onDestroy();
    }

    class OKButtonOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            Log.d(TAG, "NoteButtonOK.onClick");
            String note = toDoNote.getText().toString();
            // If the note content hasn't changed, skip saving it.
            if (TextUtils.equals(oldNoteText, note)) {
                if (isDetailHandoff)
                    setResult(RESULT_CANCELED);
                SAVE_FINISHED_RUNNER.run();
                return;
            }
            if (note.length() == 0)
                note = null;
            if (isDetailHandoff) {
                Intent returnIntent = new Intent();
                returnIntent.putExtra(EXTRA_ITEM_NOTE, note);
                setResult(RESULT_OK, returnIntent);
                SAVE_FINISHED_RUNNER.run();
            } else {
                // Disable the UI until the save is finished
                toDoNote.setEnabled(false);
                okButton.setEnabled(false);
                deleteButton.setEnabled(false);
                executor.submit(new SaveNoteRunner(todoId, note));
            }
        }
    }

    /**
     * Saves changes to the note on a non-UI thread.
     * Closes the activity when finished.  If an error occurs,
     * shows an alert (on the UI thread) instead.
     */
    private class SaveNoteRunner implements Runnable {
        private final long todoId;
        private final String note;
        SaveNoteRunner(long todoId, @Nullable String newNote) {
            this.todoId = todoId;
            note = newNote;
        }
        @Override
        public void run() {
            try {
                ToDoItem todo = repository.getItemById(todoId);
                if (todo.isEncrypted()) {
                    if (encryptor.hasKey()) {
                        todo.setEncryptedNote(encryptor.encrypt(note));
                    } else {
                        runOnUiThread(new SaveExceptionAlertRunner(
                                new IllegalStateException(
                                        "Cannot update a locked note")));
                        return;
                    }
                } else {
                    todo.setNote(note);
                }
                todo.setModTimeNow();
                repository.updateItem(todo);
                runOnUiThread(SAVE_FINISHED_RUNNER);
            } catch (Exception sx) {
                runOnUiThread(new SaveExceptionAlertRunner(sx));
            }
        }
    }

    // This alert dialog is made available at the package level for testing
    AlertDialog deleteConfirmationDialog = null;

    class DeleteButtonOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            Log.d(TAG, "NoteButtonDelete.onClick");
            if (deleteConfirmationDialog == null) {
                deleteConfirmationDialog = new AlertDialog
                        .Builder(ToDoNoteActivity.this)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.ConfirmationTextDeleteNote)
                        .setNegativeButton(R.string.ConfirmationButtonCancel,
                                ToDoDetailsActivity.DISMISS_LISTENER)
                        .setPositiveButton(R.string.ConfirmationButtonOK,
                                new DeleteConfirmedOnClickListener())
                        .create();
            }
            deleteConfirmationDialog.show();
        }
    }

    /** Second-level click listener for confirming note deletion */
    class DeleteConfirmedOnClickListener
            implements DialogInterface.OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            dialog.dismiss();
            // If the item didn't have a note to begin with, skip deleting it.
            if (TextUtils.equals(oldNoteText, "")) {
                if (isDetailHandoff)
                    setResult(RESULT_CANCELED);
                SAVE_FINISHED_RUNNER.run();
                return;
            }
            if (isDetailHandoff) {
                Intent returnIntent = new Intent();
                returnIntent.putExtra(EXTRA_ITEM_NOTE, (String) null);
                setResult(RESULT_OK, returnIntent);
                SAVE_FINISHED_RUNNER.run();
            } else {
                executor.submit(new SaveNoteRunner(todoId, null));
            }
        }
    }

    /** A runner to clean up the note activity and finish on the UI thread. */
    private final Runnable SAVE_FINISHED_RUNNER = new Runnable() {
        @Override
        public void run() {
            todoId = null;
            description = null;
            oldNoteText = null;
            ToDoNoteActivity.this.finish();
        }
    };

    /** A runner to display an exception message on the UI thread. */
    class SaveExceptionAlertRunner implements Runnable {
        private final Exception e;
        SaveExceptionAlertRunner(Exception exception) {
            e = exception;
        }
        @Override
        public void run() {
            new AlertDialog.Builder(ToDoNoteActivity.this)
                    .setMessage(e.getMessage())
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setNeutralButton(R.string.ConfirmationButtonCancel,
                            ToDoDetailsActivity.DISMISS_LISTENER)
                    .create().show();
            okButton.setEnabled(true);
            deleteButton.setEnabled(true);
        }
    }

}
