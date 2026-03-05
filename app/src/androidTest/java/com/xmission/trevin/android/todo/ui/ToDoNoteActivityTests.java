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
package com.xmission.trevin.android.todo.ui;

import static com.xmission.trevin.android.todo.util.LaunchUtils.*;
import static com.xmission.trevin.android.todo.util.RandomToDoUtils.*;
import static com.xmission.trevin.android.todo.util.ViewActionUtils.*;
import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.Instrumentation.ActivityResult;
import android.content.Context;
import android.content.Intent;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.xmission.trevin.android.todo.R;
import com.xmission.trevin.android.todo.data.ToDoItem;
import com.xmission.trevin.android.todo.provider.MockToDoRepository;
import com.xmission.trevin.android.todo.provider.TestObserver;
import com.xmission.trevin.android.todo.provider.ToDoRepositoryImpl;
import com.xmission.trevin.android.todo.util.ActivityScenarioResultsWrapper;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.*;
import java.util.*;

/**
 * Tests for the {@link ToDoNoteActivity}
 *
 * @author Trevin Beattie
 */
/*
 * FIXME: The first six test cases require launching the activity for a result,
 * but ActivityScenario.launchActivityForResult is broken on API level 34
 * (Upside-down Cake).  These tests will block for 45 seconds then fail with
 * "Activity never becomes requested state "[STARTED, RESUMED, CREATED,
 * DESTROYED]" (last lifecycle transition = "PRE_ON_CREATE").
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class ToDoNoteActivityTests {

    static final Instrumentation instrument =
            InstrumentationRegistry.getInstrumentation();
    static Context testContext = null;

    static final Random RAND = new Random();

    private static MockToDoRepository mockRepo = null;

    @BeforeClass
    public static void getTestContext() {
        testContext = instrument.getTargetContext();
        mockRepo = MockToDoRepository.getInstance();
        ToDoRepositoryImpl.setInstance(mockRepo);
    }

    @Before
    public void initializeRepository() {
        mockRepo.open(testContext);
        mockRepo.clear();
    }

    @After
    public void releaseRepository() {
        mockRepo.release(testContext);
    }

    /**
     * Create an {@link Intent} for starting the note activity from
     * the details activity.
     *
     * @param todoId the ID of the To Do item whose note is being
     * edited, or {@code null} if working on a new item.
     * @param description the description of the To Do item.
     * May be an empty string, but not {@code null}.
     * @param oldNote the old content of the note, or an empty string
     * if the item does not have a note yet.
     *
     * @return the {@link Intent}
     */
    private static Intent noteIntent(
            @Nullable Long todoId,
            @NonNull String description,
            @NonNull String oldNote) {
        Intent intent = new Intent(testContext, ToDoNoteActivity.class);
        if (todoId != null)
            intent.putExtra(ToDoListActivity.EXTRA_ITEM_ID, todoId);
        intent.putExtra(ToDoNoteActivity.EXTRA_ITEM_DESCRIPTION, description);
        intent.putExtra(ToDoNoteActivity.EXTRA_ITEM_NOTE, oldNote);
        return intent;
    }

    /**
     * Create an {@link Intent} for starting the note activity from
     * a list item.
     *
     * @param todoId the ID of the To Do item whose note is being
     * edited.
     *
     * @return the {@link Intent}
     */
    private static Intent noteIntent(long todoId) {
        Intent intent = new Intent(testContext, ToDoNoteActivity.class);
        intent.putExtra(ToDoListActivity.EXTRA_ITEM_ID, todoId);
        return intent;
    }

    /**
     * Press the Back button, verify the cancel confirmation dialog
     * is displayed and press its confirmation button.
     * This is used in two of these test cases.
     *
     * @param scenario the scenario in which the test is running
     */
    private static void discardChanges(
            ActivityScenario<ToDoNoteActivity> scenario) {
        pressBackButton();
        assertAlertDialogShown(scenario, testContext
                .getString(R.string.AlertUnsavedChangesTitle));
        pressAlertDialogButton(scenario, android.R.id.button1,
                testContext.getString(R.string.ConfirmationButtonDiscard));
    }

    /**
     * Verify that when the activity is started from the note button on
     * the details activity, changes are returned in the activity result.
     * This version is for a new note so there is no note ID.
     */
    @Test
    public void testNewItemNewNoteResult() {
        final String expectedNote = randomParagraph();
        Intent intent = noteIntent(null, "New item, new note", "");
        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launchForResult(intent);
             TestObserver observer = new TestObserver(mockRepo)) {
            hideKeyboard(wrapper.getScenario());
            assertButtonShown(wrapper.getScenario(), "OK", R.id.NoteButtonOK);
            assertEquals("Initial note content", "",
                    getElementText(wrapper.getScenario(),
                            "Note", R.id.NoteEditText));
            setEditText(wrapper.getScenario(), "Note", R.id.NoteEditText,
                    expectedNote);
            pressButton(wrapper.getScenario(), R.id.NoteButtonOK);

            ActivityResult result = wrapper.getResult();
            assertEquals("Activity return status", Activity.RESULT_OK,
                    result.getResultCode());
            Intent returnedIntent = result.getResultData();
            assertNotNull("Activity did not return an Intent", returnedIntent);
            assertHasStringExtra(returnedIntent,
                    ToDoNoteActivity.EXTRA_ITEM_NOTE, expectedNote);
            observer.assertNotChanged();
        }
    }

    /**
     * Verify that when the activity is started from the note button on
     * the details activity, changes are returned in the activity result
     * and <i>not</i> written to the database.  This version is for an
     * existing note.
     */
    @Test
    public void testEditItemChangeNoteResult() {
        ToDoItem item = randomToDo();
        item.setNote(randomParagraph());
        item = mockRepo.insertItem(item);
        final String expectedNote = randomParagraph();
        Intent intent = noteIntent(item.getId(),
                "Edit item, change note", item.getNote());
        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launchForResult(intent);
             TestObserver observer = new TestObserver(mockRepo)) {
            hideKeyboard(wrapper.getScenario());
            assertButtonShown(wrapper.getScenario(), "OK", R.id.NoteButtonOK);
            assertEquals("Initial note content", item.getNote(),
                    getElementText(wrapper.getScenario(),
                            "Note", R.id.NoteEditText));
            setEditText(wrapper.getScenario(), "Note", R.id.NoteEditText,
                    expectedNote);
            pressButton(wrapper.getScenario(), R.id.NoteButtonOK);

            ActivityResult result = wrapper.getResult();
            assertEquals("Activity return status", Activity.RESULT_OK,
                    result.getResultCode());
            Intent returnedIntent = result.getResultData();
            assertNotNull("Activity did not return an Intent", returnedIntent);
            assertHasStringExtra(returnedIntent,
                    ToDoNoteActivity.EXTRA_ITEM_NOTE, expectedNote);
            observer.assertNotChanged();
        }
    }

    /**
     * Verify that when the activity started from the note button on
     * the details activity and the user presses the Back button,
     * the activity shows a confirmation alert dialog.  If the user
     * confirms discarding changes, a cancel result is returned instead
     * of any note changes.
     */
    @Test
    public void testEditItemCancelNoteResult() {
        ToDoItem item = randomToDo();
        final String expectedNote = randomParagraph();
        item.setNote(expectedNote);
        item = mockRepo.insertItem(item);
        Intent intent = noteIntent(item.getId(),
                "Edit item, cancel note", expectedNote);
        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launchForResult(intent);
             TestObserver observer = new TestObserver(mockRepo)) {
            hideKeyboard(wrapper.getScenario());
            assertEquals("Initial note content", expectedNote,
                    getElementText(wrapper.getScenario(),
                            "Note", R.id.NoteEditText));
            setEditText(wrapper.getScenario(), "Note", R.id.NoteEditText,
                    randomParagraph());
            discardChanges(wrapper.getScenario());

            // Verify that the activity returned a canceled status.
            ActivityResult result = wrapper.getResult();
            assertEquals("Activity return status", Activity.RESULT_CANCELED,
                    result.getResultCode());
            observer.assertNotChanged();
        }
    }

    /**
     * Verify that when the activity started from the note button on
     * the details activity, pressing the &ldquo;Delete&rdquo; button
     * brings up a confirmation alert dialog.  When the user presses
     * &ldquo;OK&rdquo;, the activity returns a {@code null} note in the
     * activity result <i>if</i> the note was not previously empty
     * and does not write to the database.
     */
    @Test
    public void testEditItemDeleteNoteResult() {
        ToDoItem item = randomToDo();
        item.setNote(randomParagraph());
        item = mockRepo.insertItem(item);
        Intent intent = noteIntent(item.getId(),
                "Edit item, delete note", item.getNote());
        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launchForResult(intent);
             TestObserver observer = new TestObserver(mockRepo)) {
            hideKeyboard(wrapper.getScenario());
            assertButtonShown(wrapper.getScenario(),
                    "Delete", R.id.NoteButtonDelete);
            assertEquals("Initial note content", item.getNote(),
                    getElementText(wrapper.getScenario(),
                            "Note", R.id.NoteEditText));
            pressButton(wrapper.getScenario(), R.id.NoteButtonDelete);

            assertAlertDialogShown(wrapper.getScenario(), testContext
                    .getString(R.string.ConfirmationTextDeleteNote));
            pressAlertDialogButton(wrapper.getScenario(),
                    android.R.id.button1,
                    testContext.getString(R.string.ConfirmationButtonOK));

            ActivityResult result = wrapper.getResult();
            assertEquals("Activity return status", Activity.RESULT_OK,
                    result.getResultCode());
            Intent returnedIntent = result.getResultData();
            assertNotNull("Activity did not return an Intent",
                    returnedIntent);
            assertHasStringExtra(returnedIntent,
                    ToDoNoteActivity.EXTRA_ITEM_NOTE, null);
            observer.assertNotChanged();
        }
    }

    /**
     * Verify that when the activity started from the note button on
     * the details activity, pressing the &ldquo;Delete&rdquo; button
     * brings up a confirmation alert dialog.  When the user presses
     * &ldquo;OK&rdquo; <i>and</i> the note was previously empty,
     * the activity returns the same result as cancelling the activity.
     */
    @Test
    public void testEditItemDeleteEmptyNoteResult() {
        ToDoItem item = randomToDo();
        item.setNote(null);
        item = mockRepo.insertItem(item);
        Intent intent = noteIntent(item.getId(),
                "Edit item, delete empty note", "");
        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launchForResult(intent);
             TestObserver observer = new TestObserver(mockRepo)) {
            hideKeyboard(wrapper.getScenario());
            assertButtonShown(wrapper.getScenario(),
                    "Delete", R.id.NoteButtonDelete);
            assertEquals("Initial note content", "",
                    getElementText(wrapper.getScenario(),
                            "Note", R.id.NoteEditText));
            setEditText(wrapper.getScenario(), "Note", R.id.NoteEditText,
                    randomParagraph());
            pressButton(wrapper.getScenario(), R.id.NoteButtonDelete);

            assertAlertDialogShown(wrapper.getScenario(), testContext
                    .getString(R.string.ConfirmationTextDeleteNote));
            pressAlertDialogButton(wrapper.getScenario(),
                    android.R.id.button1,
                    testContext.getString(R.string.ConfirmationButtonOK));

            ActivityResult result = wrapper.getResult();
            assertEquals("Activity return status", Activity.RESULT_CANCELED,
                    result.getResultCode());
            observer.assertNotChanged();
        }
    }

    /**
     * Verify that when the activity is started from the note icon for
     * an item in the To Do list, changes are written to the database
     * directly.  Also verify changes to the note are retained when
     * the activity is restarted due to a configuration change (e.g.
     * screen rotation, keyboard (a/de)tachment).
     */
    @Test
    public void testListItemRestoreEditNote() {
        ToDoItem item = randomToDo();
        item.setDescription("Edit note from list");
        item.setNote(randomParagraph());
        item = mockRepo.insertItem(item);
        final String expectedNote = randomParagraph();
        Intent intent = noteIntent(item.getId());
        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(intent);
             TestObserver observer = new TestObserver(mockRepo)) {
            hideKeyboard(wrapper.getScenario());
            assertButtonShown(wrapper.getScenario(), "OK", R.id.NoteButtonOK);
            assertEquals("Initial note content", item.getNote(),
                    getElementText(wrapper.getScenario(),
                            "Note", R.id.NoteEditText));
            setEditText(wrapper.getScenario(), "Note", R.id.NoteEditText,
                    expectedNote);

            wrapper.recreate();

            pressButton(wrapper.getScenario(), R.id.NoteButtonOK);
            observer.assertChanged("Note was not saved to the repository");
        }
        ToDoItem updatedItem = mockRepo.getItemById(item.getId());
        assertEquals("Note saved to the repository", expectedNote,
                updatedItem.getNote());
    }

    /**
     * Verify that when the activity is started from the note icon for
     * an item in the To Do list and the user presses the Back button,
     * the activity shows a confirmation alert dialog.  If the user
     * confirms discarding changes, no change is made to the To Do item.
     */
    @Test
    public void testListItemCancelNote() {
        ToDoItem item = randomToDo();
        item.setDescription("Cancel note from list");
        final String expectedNote = randomParagraph();
        item.setNote(expectedNote);
        item = mockRepo.insertItem(item);
        Intent intent = noteIntent(item.getId());
        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(intent);
             TestObserver observer = new TestObserver(mockRepo)) {
            hideKeyboard(wrapper.getScenario());
            assertEquals("Initial note content", expectedNote,
                    getElementText(wrapper.getScenario(),
                            "Note", R.id.NoteEditText));
            setEditText(wrapper.getScenario(), "Note", R.id.NoteEditText,
                    randomParagraph());
            discardChanges(wrapper.getScenario());
            observer.assertNotChanged();
        }
    }

    /**
     * Verify that when the activity is started from the note icon for
     * an item in the To Do list, pressing the &ldquo;Delete&rdquo;
     * button immediately deletes the note from the item in the database.
     */
    @Test
    public void testListItemDeleteNote() {
        ToDoItem item = randomToDo();
        item.setDescription("Delete note from list");
        item.setNote(randomParagraph());
        item = mockRepo.insertItem(item);
        Intent intent = noteIntent(item.getId());
        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launch(intent);
             TestObserver observer = new TestObserver(mockRepo)) {
            hideKeyboard(wrapper.getScenario());
            assertButtonShown(wrapper.getScenario(),
                    "Delete", R.id.NoteButtonDelete);
            assertEquals("Initial note content", item.getNote(),
                    getElementText(wrapper.getScenario(),
                            "Note", R.id.NoteEditText));
            pressButton(wrapper.getScenario(), R.id.NoteButtonDelete);

            assertAlertDialogShown(wrapper.getScenario(), testContext
                    .getString(R.string.ConfirmationTextDeleteNote));
            pressAlertDialogButton(wrapper.getScenario(), android.R.id.button1,
                    testContext.getString(R.string.ConfirmationButtonOK));
            observer.assertChanged("Note was not deleted from the repository");
        }
        ToDoItem updatedItem = mockRepo.getItemById(item.getId());
        assertNull("Note was not deleted from the repository",
                updatedItem.getNote());
    }

    /**
     * Verify that when a very long note is being edited and the
     * activity is restarted, the note edit box retains the cursor position.
     */
    @Test
    public void testRestoreCursorPosition() {
        List<String> paragraphs = new ArrayList<>();
        int targetPosition = 0;
        for (int i = 5 + RAND.nextInt(3); i >= 0; --i) {
            String p = randomParagraph();
            if (i > 1)
                targetPosition += p.length() + 2;
            paragraphs.add(p);
        }
        String document = String.join("\n\n", paragraphs);
        Intent intent = noteIntent(null, "Long document", "");
        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launchForResult(intent)) {
            hideKeyboard(wrapper.getScenario());
            assertButtonShown(wrapper.getScenario(), "OK", R.id.NoteButtonOK);
            setEditText(wrapper.getScenario(),
                    "Note", R.id.NoteEditText, document);
            final int initPosition = targetPosition;
            wrapper.onActivity(activity -> {
                EditText noteView = activity.findViewById(R.id.NoteEditText);
                noteView.requestFocus();
                noteView.setSelection(initPosition);
            });

            wrapper.recreate();

            final int[] actualPosition = new int[] { -1 };
            wrapper.onActivity(activity -> {
                EditText noteView = activity.findViewById(R.id.NoteEditText);
                actualPosition[0] = noteView.getSelectionStart();
            });
            assertEquals("Cursor position after restart",
                    targetPosition, actualPosition[0]);
        }
    }

}
