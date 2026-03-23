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
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.app.Instrumentation.ActivityResult;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.text.Layout;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.xmission.trevin.android.todo.R;
import com.xmission.trevin.android.todo.data.MockSharedPreferences;
import com.xmission.trevin.android.todo.data.ToDoItem;
import com.xmission.trevin.android.todo.data.ToDoPreferences;
import com.xmission.trevin.android.todo.provider.MockToDoRepository;
import com.xmission.trevin.android.todo.provider.TestObserver;
import com.xmission.trevin.android.todo.provider.ToDoRepositoryImpl;
import com.xmission.trevin.android.todo.util.ActivityScenarioResultsWrapper;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

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

    private static MockSharedPreferences mockPrefs = null;

    @BeforeClass
    public static void getTestContext() {
        testContext = instrument.getTargetContext();
        mockPrefs = MockSharedPreferences.getInstance();
        ToDoPreferences.setSharedPreferences(mockPrefs);
        mockRepo = MockToDoRepository.getInstance();
        ToDoRepositoryImpl.setInstance(mockRepo);
    }

    @Before
    public void initializeRepository() {
        mockRepo.open(testContext);
        mockRepo.clear();
        mockPrefs.resetMock();
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
        assertDialogShown(scenario, testContext
                .getString(R.string.AlertUnsavedChangesTitle));
        AlertDialog[] dialogRef = new AlertDialog[1];
        scenario.onActivity(activity -> {
            dialogRef[0] = activity.discardConfirmationDialog;
        });
        assertNotNull("Note activity did not set its discardConfirmationDialog",
                dialogRef[0]);
        int okButtonId = dialogRef[0].getButton(
                DialogInterface.BUTTON_POSITIVE).getId();
        pressDialogButton(scenario, dialogRef[0], okButtonId);
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

            assertDialogShown(wrapper.getScenario(), testContext
                    .getString(R.string.ConfirmationTextDeleteNote));
            AlertDialog[] dialogRef = new AlertDialog[1];
            wrapper.onActivity(activity -> {
                dialogRef[0] = activity.deleteConfirmationDialog;
            });
            assertNotNull("Note activity did not set its deleteConfirmationDialog",
                    dialogRef[0]);
            int okButtonId = dialogRef[0].getButton(
                    DialogInterface.BUTTON_POSITIVE).getId();
            pressDialogButton(wrapper.getScenario(), dialogRef[0], okButtonId);

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

            assertDialogShown(wrapper.getScenario(), testContext
                    .getString(R.string.ConfirmationTextDeleteNote));
            AlertDialog[] dialogRef = new AlertDialog[1];
            wrapper.onActivity(activity -> {
                dialogRef[0] = activity.deleteConfirmationDialog;
            });
            assertNotNull("Note activity did not set its deleteConfirmationDialog",
                    dialogRef[0]);
            int okButtonId = dialogRef[0].getButton(
                    DialogInterface.BUTTON_POSITIVE).getId();
            pressDialogButton(wrapper.getScenario(), dialogRef[0], okButtonId);

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

            assertDialogShown(wrapper.getScenario(), testContext
                    .getString(R.string.ConfirmationTextDeleteNote));
            AlertDialog[] dialogRef = new AlertDialog[1];
            wrapper.onActivity(activity -> {
                dialogRef[0] = activity.deleteConfirmationDialog;
            });
            assertNotNull("Note activity did not set its deleteConfirmationDialog",
                    dialogRef[0]);
            int okButtonId = dialogRef[0].getButton(
                    DialogInterface.BUTTON_POSITIVE).getId();
            pressDialogButton(wrapper.getScenario(), dialogRef[0], okButtonId);
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

    // ==================== Scroll bar tests ====================

    /**
     * Build a note consisting of the given number of sentences,
     * one per line, suitable for filling vertical space predictably.
     */
    private static String buildNoteOfLines(int numLines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < numLines; i++)
            sb.append("Line ").append(i + 1).append(": ")
              .append(randomSentence()).append('\n');
        return sb.toString();
    }

    /**
     * Verify that when the scroll bar threshold is 0 (&ldquo;never
     * show&rdquo;) the scroll bar is never made visible, even when the
     * note is very long.
     */
    @Test
    public void testScrollBarNeverShown() {
        mockPrefs.initializePreference(
                ToDoPreferences.TPREF_SCROLL_THRESHOLD, 0.0f);
        Intent intent = noteIntent(null, "Never show scroll bar",
                buildNoteOfLines(200));
        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launchForResult(intent)) {
            hideKeyboard(wrapper.getScenario());
            assertScrollBarGone(wrapper.getScenario(),
                    "Scroll Bar", R.id.NoteScrollBar);
        }
    }

    /**
     * Verify that when the scroll bar threshold is &infin; (&ldquo;always
     * show&rdquo;) the scroll bar is immediately visible, even when the
     * note is empty.
     */
    @Test
    public void testScrollBarAlwaysShown() {
        mockPrefs.initializePreference(
                ToDoPreferences.TPREF_SCROLL_THRESHOLD,
                Float.POSITIVE_INFINITY);
        Intent intent = noteIntent(null, "Always show scroll bar", "");
        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launchForResult(intent)) {
            hideKeyboard(wrapper.getScenario());
            assertScrollBarVisible(wrapper.getScenario(),
                    "Scroll Bar", R.id.NoteScrollBar);
        }
    }

    /**
     * Verify that with a threshold of 2 the scroll bar is hidden when the
     * note fills less than half the view, then becomes visible once the
     * note is extended past the half-page mark.
     */
    @Test
    public void testScrollBarAppearsWithContent() {
        mockPrefs.initializePreference(
                ToDoPreferences.TPREF_SCROLL_THRESHOLD, 2.0f);
        // Start with a single short line — definitely less than half a page.
        Intent intent = noteIntent(null, "Scroll bar appears", "Short note.\n");
        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launchForResult(intent)) {
            ActivityScenario<ToDoNoteActivity> scenario = wrapper.getScenario();
            hideKeyboard(scenario);
            assertScrollBarGone(scenario, "Scroll Bar", R.id.NoteScrollBar);

            // Measure the note view so we can compute the half-page threshold.
            final int[] dims = new int[2]; // [viewHeight, lineHeight]
            scenario.onActivity(activity -> {
                dims[0] = activity.toDoNote.getHeight();
                dims[1] = activity.toDoNote.getLineHeight();
            });
            instrument.waitForIdleSync();
            assertTrue("Note view has not been laid out yet", dims[0] > 0);

            // We need contentHeight > viewHeight / threshold (= viewHeight/2).
            // Add a couple of extra lines as margin.
            int linesNeeded = dims[0] / (dims[1] * 2) + 3;
            setEditText(scenario, "Note", R.id.NoteEditText,
                    buildNoteOfLines(linesNeeded));
            assertScrollBarVisible(scenario, "Scroll Bar", R.id.NoteScrollBar);
        }
    }

    /**
     * Verify that with a threshold of 1 the scroll bar is visible when
     * the note exceeds one page, then disappears once the note is reduced
     * to less than one page.
     */
    @Test
    public void testScrollBarHidesWithShortContent() {
        mockPrefs.initializePreference(
                ToDoPreferences.TPREF_SCROLL_THRESHOLD, 1.0f);
        // 200 lines is guaranteed to be more than one page on any device.
        Intent intent = noteIntent(null, "Scroll bar hides",
                buildNoteOfLines(200));
        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launchForResult(intent)) {
            ActivityScenario<ToDoNoteActivity> scenario = wrapper.getScenario();
            hideKeyboard(scenario);
            assertScrollBarVisible(scenario, "Scroll Bar", R.id.NoteScrollBar);

            // Reduce to a single line — clearly less than one page.
            setEditText(scenario, "Note", R.id.NoteEditText, "Short note.\n");
            assertScrollBarGone(scenario, "Scroll Bar", R.id.NoteScrollBar);
        }
    }

    /**
     * Verify that moving the scroll bar causes the note edit box to
     * scroll by the same relative amount.  Tests mid-position, bottom,
     * and back to top.
     */
    @Test
    public void testScrollBarMovesNoteScroll() {
        mockPrefs.initializePreference(
                ToDoPreferences.TPREF_SCROLL_THRESHOLD, 0.5f);
        // 200 lines gives ~4–5 pages on any device.
        Intent intent = noteIntent(null, "Bar moves note",
                buildNoteOfLines(200));
        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launchForResult(intent)) {
            ActivityScenario<ToDoNoteActivity> scenario = wrapper.getScenario();
            hideKeyboard(scenario);
            assertScrollBarVisible(scenario, "Scroll Bar", R.id.NoteScrollBar);

            // Capture maxPos (contentSize − viewSize) and lineHeight.
            final int[] lineHeight = new int[1];
            final int[] maxPos = new int[1];
            scenario.onActivity(activity -> {
                lineHeight[0] = activity.toDoNote.getLineHeight();
                maxPos[0] = (int)(activity.scrollBar.getContentSize()
                        - activity.scrollBar.getViewSize());
            });
            instrument.waitForIdleSync();
            assertTrue("Content must be longer than the view", maxPos[0] > 0);

            // -- Mid position (50%) --
            moveScrollBar(scenario, "Note scroll bar",
                    R.id.NoteScrollBar, 0.5f);
            final int[] scrollY = new int[1];
            final double[] barPos = new double[1];
            scenario.onActivity(activity -> {
                scrollY[0] = activity.toDoNote.getScrollY();
                barPos[0] = activity.scrollBar.getPosition();
            });
            instrument.waitForIdleSync();
            // Note must have scrolled away from both top and bottom.
            double expectedMid = maxPos[0] / 2.0;
            double allowedFuzz = expectedMid - 2.0 * lineHeight[0];
            assertEquals("Note did not scroll away from top or bottom (mid).",
                    expectedMid, scrollY[0], allowedFuzz);
            // Bar position and scrollY must agree within two lines.
            assertEquals(String.format(Locale.US,
                    "Bar position matches scrollY (mid) within \u00b1%d",
                            lineHeight[0] * 2),
                    barPos[0], scrollY[0], lineHeight[0] * 2);

            // Allow the rate limiter (> 41.6 ms) to clear between moves.
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}

            // -- Bottom (fraction 1.0) --
            moveScrollBar(scenario, "Note scroll bar",
                    R.id.NoteScrollBar, 1.0f);
            scenario.onActivity(activity -> {
                scrollY[0] = activity.toDoNote.getScrollY();
                barPos[0] = activity.scrollBar.getPosition();
            });
            instrument.waitForIdleSync();
            // Re-read maxPos in case of any reflow.
            final int[] maxPosRef = new int[1];
            scenario.onActivity(activity -> {
                maxPosRef[0] = (int)(activity.scrollBar.getContentSize()
                        - activity.scrollBar.getViewSize());
            });
            instrument.waitForIdleSync();
            assertEquals("Note did not scroll near bottom.",
                    maxPosRef[0], scrollY[0], lineHeight[0] * 2);
            assertEquals(String.format(Locale.US,
                    "Bar position matches scrollY (bottom) within \u00b1%d",
                            lineHeight[0]),
                    barPos[0],  scrollY[0], lineHeight[0]);

            try { Thread.sleep(100); } catch (InterruptedException ignored) {}

            // -- Top (fraction 0.0) --
            moveScrollBar(scenario, "Note scroll bar",
                    R.id.NoteScrollBar, 0.0f);
            scenario.onActivity(activity -> {
                scrollY[0] = activity.toDoNote.getScrollY();
                barPos[0] = activity.scrollBar.getPosition();
            });
            instrument.waitForIdleSync();
            assertEquals("Note did not scroll back to top.",
                    0.0f, scrollY[0], lineHeight[0] * 2);
            assertEquals(String.format(Locale.US,
                    "Bar position matches scrollY (top) within \u00b1%d",
                            lineHeight[0]),
                    barPos[0], scrollY[0], lineHeight[0]);
        }
    }

    /**
     * Verify that scrolling the note edit box directly causes the
     * scroll bar thumb to track the same position.  Tests mid-position,
     * bottom, and back to top.
     */
    @Test
    public void testNoteScrollMovesScrollBar() {
        mockPrefs.initializePreference(
                ToDoPreferences.TPREF_SCROLL_THRESHOLD, 0.5f);
        Intent intent = noteIntent(null, "Note moves bar",
                buildNoteOfLines(200));
        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launchForResult(intent)) {
            ActivityScenario<ToDoNoteActivity> scenario = wrapper.getScenario();
            hideKeyboard(scenario);
            assertScrollBarVisible(scenario, "Scroll Bar", R.id.NoteScrollBar);

            final int[] lineHeight = new int[1];
            final int[] maxPos = new int[1];
            scenario.onActivity(activity -> {
                lineHeight[0] = activity.toDoNote.getLineHeight();
                maxPos[0] = (int)(activity.scrollBar.getContentSize()
                        - activity.scrollBar.getViewSize());
            });
            instrument.waitForIdleSync();
            assertTrue("Content must be longer than the view", maxPos[0] > 0);

            // Helper: scroll note and read both scrollY and bar position.
            final int[] actualScrollY = new int[1];
            final double[] barPos = new double[1];

            // -- Mid position (50% of maxPos) --
            final int midScrollY = maxPos[0] / 2;
            scenario.onActivity(activity ->
                    activity.toDoNote.scrollTo(0, midScrollY));
            instrument.waitForIdleSync();
            scenario.onActivity(activity -> {
                actualScrollY[0] = activity.toDoNote.getScrollY();
                barPos[0] = activity.scrollBar.getPosition();
            });
            instrument.waitForIdleSync();
            assertTrue(String.format(Locale.US,
                            "Scroll bar did not track mid scroll: too low."
                            + "  Expected: >= %d, but was:%f",
                            actualScrollY[0] - lineHeight[0] * 2, barPos[0]),
                    barPos[0] >= actualScrollY[0] - lineHeight[0] * 2);
            assertTrue(String.format(Locale.US,
                            "Scroll bar did not track mid scroll: too high."
                            + "  Expected: <= %d, but was:%f",
                            actualScrollY[0] + lineHeight[0] * 2, barPos[0]),
                    barPos[0] <= actualScrollY[0] + lineHeight[0] * 2);

            // -- Bottom: overshoot so the EditText clamps to its actual max --
            scenario.onActivity(activity ->
                    activity.toDoNote.scrollTo(0, 2 * maxPos[0]));
            instrument.waitForIdleSync();
            // Re-read maxPos and capture state together.
            final int[] maxPosRef = new int[1];
            scenario.onActivity(activity -> {
                actualScrollY[0] = activity.toDoNote.getScrollY();
                barPos[0] = activity.scrollBar.getPosition();
                maxPosRef[0] = (int)(activity.scrollBar.getContentSize()
                        - activity.scrollBar.getViewSize());
            });
            instrument.waitForIdleSync();
            assertTrue(String.format(Locale.US,
                            "Scroll bar did not reach bottom."
                            + "  Expected: >= %d, but was:%f",
                            maxPosRef[0] - lineHeight[0] * 2, barPos[0]),
                    barPos[0] >= maxPosRef[0] - lineHeight[0] * 2);

            // -- Top --
            scenario.onActivity(activity ->
                    activity.toDoNote.scrollTo(0, 0));
            instrument.waitForIdleSync();
            scenario.onActivity(activity -> {
                actualScrollY[0] = activity.toDoNote.getScrollY();
                barPos[0] = activity.scrollBar.getPosition();
            });
            instrument.waitForIdleSync();
            assertTrue(String.format(Locale.US,
                            "Scroll bar did not return to top."
                            + "  Expected: <= %d, but was:%f",
                            lineHeight[0] * 2, barPos[0]),
                    barPos[0] <= lineHeight[0] * 2);
            assertEquals("Bar position matches scrollY (top)",
                    actualScrollY[0], barPos[0], lineHeight[0]);
        }
    }

    /**
     * Verify that when adding text to the note the scroll bar thumb size
     * is updated according to the new content size and its position is
     * adjusted according to the edit box&rsquo; new scroll position.
     */
    @Test
    public void testNoteGrowthAdjustsScrollBar() {
        mockPrefs.initializePreference(
                ToDoPreferences.TPREF_SCROLL_THRESHOLD, 1.0f);
        String oldNote = buildNoteOfLines(200);
        Intent intent = noteIntent(null, "Note moves bar", oldNote);
        try (ActivityScenarioResultsWrapper<ToDoNoteActivity> wrapper =
                     ActivityScenarioResultsWrapper.launchForResult(intent)) {
            ActivityScenario<ToDoNoteActivity> scenario = wrapper.getScenario();
            hideKeyboard(scenario);
            assertScrollBarVisible(scenario, "Scroll Bar", R.id.NoteScrollBar);

            String newNote = oldNote + "\n" + buildNoteOfLines(100);
            setEditText(wrapper.getScenario(), "Note",
                    R.id.NoteEditText, newNote);
            final int[] lineHeight = new int[1];
            final int[] contentHeight = new int[] { -1 };
            final int[] scrollY = new int[1];
            final double[] barSize = new double[1];
            final double[] barPos = new double[1];
            scenario.onActivity(activity -> {
                lineHeight[0] = activity.toDoNote.getLineHeight();
                Layout textLayout = activity.toDoNote.getLayout();
                if (textLayout != null)
                    contentHeight[0] = textLayout.getHeight();
                scrollY[0] = activity.toDoNote.getScrollY();
                barSize[0] = activity.scrollBar.getContentSize();
                barPos[0] = activity.scrollBar.getPosition();
            });
            instrument.waitForIdleSync();
            assertNotEquals("Failed to obtain the edit text layout",
                    -1, contentHeight[0]);
            assertEquals("Scrollbar content size was not adjusted",
                    contentHeight[0], barSize[0], lineHeight[0]);
            assertEquals("Scrollbar position was not adjusted",
                    scrollY[0], barPos[0], lineHeight[0]);
        }
    }

}
