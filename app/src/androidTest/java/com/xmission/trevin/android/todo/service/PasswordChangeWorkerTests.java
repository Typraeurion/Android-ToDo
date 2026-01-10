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
package com.xmission.trevin.android.todo.service;

import static com.xmission.trevin.android.todo.service.PasswordChangeWorker.*;
import static com.xmission.trevin.android.todo.util.RandomToDoUtils.randomParagraph;
import static com.xmission.trevin.android.todo.util.RandomToDoUtils.randomToDo;
import static com.xmission.trevin.android.todo.util.StringEncryption.METADATA_PASSWORD_HASH;
import static org.junit.Assert.*;

import android.content.Context;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.Data;
import androidx.work.ListenableWorker.Result;
import androidx.work.testing.TestListenableWorkerBuilder;

import com.xmission.trevin.android.todo.data.ToDoCategory;
import com.xmission.trevin.android.todo.data.ToDoItem;
import com.xmission.trevin.android.todo.data.ToDoMetadata;
import com.xmission.trevin.android.todo.provider.MockToDoRepository;
import com.xmission.trevin.android.todo.provider.ToDoRepositoryImpl;
import com.xmission.trevin.android.todo.util.StringEncryption;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.*;
import org.junit.runner.RunWith;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class PasswordChangeWorkerTests {

    private Context testContext = null;
    private MockToDoRepository mockRepo = null;
    private StringEncryption globalEncryption = null;

    @Before
    public void initializeRepository() {
        if (testContext == null)
            testContext = InstrumentationRegistry.getInstrumentation()
                    .getTargetContext();
        if (mockRepo == null) {
            mockRepo = MockToDoRepository.getInstance();
            ToDoRepositoryImpl.setInstance(mockRepo);
        }
        mockRepo.open(testContext);
        mockRepo.clear();
        globalEncryption = StringEncryption.holdGlobalEncryption();
    }

    @After
    public void releaseRepository() {
        StringEncryption.releaseGlobalEncryption(testContext);
        mockRepo.release(testContext);
    }

    /**
     * Set up the password change worker, call it,
     * then wait for it to finish.
     *
     * @param oldPassword the old password,
     * or {@code null} if setting a new one.
     * @param newPassword the new password,
     * or {@code null} if clearing the old one.
     * @param expectedResult the class of the expected result
     * ({@link Result.Success} or {@link Result.Failure}).
     *
     * @return the {@link Result} of the password change call.
     *
     * @throws AssertionError if the result is not the expected type.
     */
    private Result runPasswordChangeWorker(
            String oldPassword, String newPassword,
            Class<? extends Result> expectedResult) throws AssertionError {
        PasswordChangeWorker worker = TestListenableWorkerBuilder.from(
                        testContext, PasswordChangeWorker.class)
                .setInputData(new Data.Builder()
                        .putString(DATA_OLD_PASSWORD, oldPassword)
                        .putString(DATA_NEW_PASSWORD, newPassword)
                        .build())
                .setRunAttemptCount(1)
                .build();
        try {
            Result result = worker.startWork().get(10, TimeUnit.SECONDS);
            Map<String,Object> data = result.getOutputData().getKeyValueMap();
            if (!data.isEmpty())
                Log.i("Tests", "PasswordChangeWorker return data: " + data);
            assertEquals("Result of password change worker call",
                    expectedResult, result.getClass());
            return result;
        } catch (ExecutionException ee) {
            Log.e("Tests", "Worker threw an exception", ee);
            fail("Worker was aborted: " + ee.getMessage());
            // Unreachable
            return null;
        } catch (InterruptedException ie) {
            Log.e("Tests", "Worker was interrupted", ie);
            fail("Worker was interrupted");
            // Unreachable
            return null;
        } catch (TimeoutException te) {
            Log.e("Tests", "Worker timed out", te);
            fail("Worker timed out after 10 seconds");
            // Unreachable
            return null;
        }
    }

    /**
     * Test setting a new password when the database has private records.
     * All private records should become encrypted.
     */
    @Test
    public void testNewPassword() throws Exception {

        // Set up the test data
        ToDoItem publicItem = randomToDo();
        publicItem.setPrivate(0);
        publicItem.setCategoryName(mockRepo.getCategoryById(
                ToDoCategory.UNFILED).getName());
        mockRepo.insertItem(publicItem);
        ToDoItem privateItem = randomToDo();
        privateItem.setPrivate(1);
        privateItem.setCategoryName(mockRepo.getCategoryById(
                        ToDoCategory.UNFILED).getName());
        // Ensure we also have a note on the private item
        if (privateItem.getNote() == null)
            privateItem.setNote(randomParagraph());
        mockRepo.insertItem(privateItem);

        final String password = RandomStringUtils.randomAlphanumeric(8);

        // Call the password change worker
        runPasswordChangeWorker(null, password, Result.Success.class);

        // Verify the results
        ToDoMetadata passwordHash =
                mockRepo.getMetadataByName(METADATA_PASSWORD_HASH);
        assertNotNull("Password hash was not saved", passwordHash);

        ToDoItem savedItem = mockRepo.getItemById(publicItem.getId());
        assertEquals("Public To Do item", publicItem, savedItem);

        savedItem = mockRepo.getItemById(privateItem.getId());
        assertNotNull("Private To Do item not found", savedItem);
        assertTrue("Private To Do item was not encrypted",
                savedItem.isEncrypted());

        // Check the encryption on the encrypted item
        StringEncryption se = new StringEncryption();
        if (se.getPassword() == null) {
            se.setPassword(password.toCharArray());
            se.checkPassword(mockRepo);
        }
        String decrypted = (savedItem.getEncryptedDescription() == null)
                ? null : se.decrypt(savedItem.getEncryptedDescription());
        assertEquals("Decrypted description does not match original",
                privateItem.getDescription(), decrypted);

        decrypted = (savedItem.getEncryptedNote() == null) ? null
                : se.decrypt(savedItem.getEncryptedNote());
        assertEquals("Decrypted note does not match original",
                privateItem.getNote(), decrypted);
    }

    /**
     * Test clearing the password when the database has encrypted records.
     * All encrypted records should be unencrypted but private.
     */
    @Test
    public void testRemovePassword() throws Exception {

        // Set up the test data
        final String password = RandomStringUtils.randomAlphanumeric(8);
        globalEncryption.setPassword(password.toCharArray());
        globalEncryption.addSalt();
        globalEncryption.storePassword(mockRepo);

        ToDoItem publicItem = randomToDo();
        publicItem.setPrivate(0);
        publicItem.setCategoryName(mockRepo.getCategoryById(
                        ToDoCategory.UNFILED).getName());
        mockRepo.insertItem(publicItem);
        ToDoItem privateItem = randomToDo();
        String clearDescription = privateItem.getDescription();
        privateItem.setEncryptedDescription(
                globalEncryption.encrypt(clearDescription));
        String clearNote = privateItem.getNote();
        // Ensure we also have a note on the private item
        if (clearNote == null)
            clearNote = randomParagraph();
        privateItem.setEncryptedNote(globalEncryption.encrypt(clearNote));
        privateItem.setPrivate(2);
        privateItem.setCategoryName(mockRepo.getCategoryById(
                        ToDoCategory.UNFILED).getName());
        mockRepo.insertItem(privateItem);

        globalEncryption.forgetPassword();

        // Call the password change worker
        runPasswordChangeWorker(password, null, Result.Success.class);

        // Verify the results
        assertNull("Password was not removed",
                mockRepo.getMetadataByName(METADATA_PASSWORD_HASH));

        ToDoItem savedItem = mockRepo.getItemById(publicItem.getId());
        assertEquals("Public To Do item", publicItem, savedItem);

        savedItem = mockRepo.getItemById(privateItem.getId());
        assertNotNull("Private Do To item not found", savedItem);
        assertFalse("Private To Do item was not decrypted",
                savedItem.isEncrypted());
        assertTrue("Private To Do item was made public",
                savedItem.isPrivate());
        assertEquals("Private To Do item description",
                clearDescription, savedItem.getDescription());
        assertEquals("Private To Do item note",
                clearNote, savedItem.getNote());
    }

    /**
     * Test clearing the password when the proper old password has
     * not been given.  Should result in a bad password toast.
     */
    @Test
    public void testRemoveBadPassword() throws Exception {

        // Set up the test data
        String password = RandomStringUtils.randomAlphanumeric(12);
        globalEncryption.setPassword(password.toCharArray());
        globalEncryption.addSalt();
        globalEncryption.storePassword(mockRepo);

        globalEncryption.forgetPassword();

        password = RandomStringUtils.randomAlphabetic(8);

        // Call the password change worker
        Result result = runPasswordChangeWorker(
                password, null, Result.Failure.class);

        // Verify the results
        Data data = result.getOutputData();
        assertNotNull("Worker result did not return any dada", data);
        assertTrue("Worker result does not include \"message\"",
                data.hasKeyWithValueOfType("message", String.class));
        assertTrue("Worker result does not include \"Error\"",
                data.hasKeyWithValueOfType("Error", String.class));
        assertTrue("Worker result message did not report a bad password",
                data.getString("message").toLowerCase().contains("password"));
        assertEquals("Worker result Error",
                "Old password does not match hash in the database",
                data.getString("Error"));
    }

    /**
     * Test clearing the password when there is no password set.
     * Should result in a bad password toast.
     */
    @Test
    public void testRemoveNoPassword() {

        String password = RandomStringUtils.randomAlphabetic(8);

        // Call the password change worker
        Result result = runPasswordChangeWorker(
                password, null, Result.Failure.class);

        // Verify the results
        Data data = result.getOutputData();
        assertNotNull("Worker result did not return any dada", data);
        assertTrue("Worker result does not include \"message\"",
                data.hasKeyWithValueOfType("message", String.class));
        assertTrue("Worker result does not include \"Error\"",
                data.hasKeyWithValueOfType("Error", String.class));
        assertTrue("Worker result message did not report a bad password",
                data.getString("message").toLowerCase().contains("password"));
        assertEquals("Worker result Error",
                "Old password provided but no password has been set",
                data.getString("Error"));
    }

    /**
     * Test setting a &ldquo;new&rdquo; password when the database already
     * has a password.  Should result in a bad password toast.
     */
    @Test
    public void testNewPasswordConflict() throws Exception {

        // Set up the test data
        String password = RandomStringUtils.randomAlphanumeric(12);
        globalEncryption.setPassword(password.toCharArray());
        globalEncryption.addSalt();
        globalEncryption.storePassword(mockRepo);

        globalEncryption.forgetPassword();

        password = RandomStringUtils.randomAlphabetic(12);

        // Call the password change worker
        Result result = runPasswordChangeWorker(
                null, password, Result.Failure.class);

        // Verify the results
        Data data = result.getOutputData();
        assertNotNull("Worker result did not return any dada", data);
        assertTrue("Worker result does not include \"message\"",
                data.hasKeyWithValueOfType("message", String.class));
        assertTrue("Worker result does not include \"Error\"",
                data.hasKeyWithValueOfType("Error", String.class));
        assertTrue("Worker result message did not report a bad password",
                data.getString("message").toLowerCase().contains("password"));
        assertEquals("Worker result Error",
                "Current password was not provided",
                data.getString("Error"));
    }

    /**
     * Test changing the password when the database has encrypted records.
     * All encrypted records should be re-encrypted with the new password,
     * and any previously private but unencrypted records should be
     * encrypted as well.
     */
    @Test
    public void testChangePassword() throws Exception {

        // Set up the test data
        final String oldPassword = RandomStringUtils.randomAlphanumeric(8);
        globalEncryption.setPassword(oldPassword.toCharArray());
        globalEncryption.addSalt();
        globalEncryption.storePassword(mockRepo);

        ToDoItem publicItem = randomToDo();
        publicItem.setPrivate(0);
        publicItem.setCategoryName(mockRepo.getCategoryById(
                        ToDoCategory.UNFILED).getName());
        mockRepo.insertItem(publicItem);
        ToDoItem privateItem = randomToDo();
        String clearDescription1 = privateItem.getDescription();
        String clearNote1 = privateItem.getNote();
        if (clearNote1 == null) {
            clearNote1 = randomParagraph();
            privateItem.setNote(clearNote1);
        }
        privateItem.setPrivate(1);
        privateItem.setCategoryName(mockRepo.getCategoryById(
                        ToDoCategory.UNFILED).getName());
        mockRepo.insertItem(privateItem);
        ToDoItem encryptedItem = randomToDo();
        String clearDescription2 = encryptedItem.getDescription();
        String clearNote2 = encryptedItem.getNote();
        if (clearNote2 == null) {
            clearNote2 = randomParagraph();
        }
        encryptedItem.setPrivate(2);
        encryptedItem.setCategoryName(mockRepo.getCategoryById(
                        ToDoCategory.UNFILED).getName());
        encryptedItem.setEncryptedDescription(
                globalEncryption.encrypt(clearDescription2));
        encryptedItem.setEncryptedNote(
                globalEncryption.encrypt(clearNote2));
        mockRepo.insertItem(encryptedItem);

        final String newPassword = RandomStringUtils.randomAlphanumeric(12);
        globalEncryption.forgetPassword();

        // Call the password change worker
        runPasswordChangeWorker(oldPassword, newPassword,
                Result.Success.class);

        // Verify the results
        ToDoMetadata passwordHash =
                mockRepo.getMetadataByName(METADATA_PASSWORD_HASH);
        assertNotNull("Password hash was removed", passwordHash);

        ToDoItem savedItem = mockRepo.getItemById(publicItem.getId());
        assertEquals("Public To Do item", publicItem, savedItem);

        savedItem = mockRepo.getItemById(privateItem.getId());
        assertNotNull("Private To Do item (#1) not found", savedItem);
        assertTrue("Private To Do item (#1) was not encrypted",
                savedItem.isEncrypted());

        // Check the encryption on the first private item; don't rely on
        // which password was last set in the global encryption instance.
        globalEncryption.forgetPassword();
        globalEncryption.setPassword(newPassword.toCharArray());
        globalEncryption.checkPassword(mockRepo);
        String decrypted = (savedItem.getEncryptedDescription() == null) ? null
                : globalEncryption.decrypt(savedItem.getEncryptedDescription());
        assertEquals("Decrypted description (#1) does not match original",
                clearDescription1, decrypted);
        decrypted = (savedItem.getEncryptedNote() == null) ? null
                : globalEncryption.decrypt(savedItem.getEncryptedNote());
        assertEquals("Decrypted note (#1) does not match original",
                clearNote1, decrypted);

        savedItem = mockRepo.getItemById(encryptedItem.getId());
        assertNotNull("Encrypted To Do item (#2) not found", savedItem);
        assertTrue("Encrypted To Do item (#2) was left unencrypted",
                savedItem.isEncrypted());
        decrypted = (savedItem.getEncryptedDescription() == null) ? null
                : globalEncryption.decrypt(savedItem.getEncryptedDescription());
        assertEquals("Decrypted description (#2) does not match original",
                clearDescription2, decrypted);
        decrypted = (savedItem.getEncryptedNote() == null) ? null
                : globalEncryption.decrypt(savedItem.getEncryptedNote());
        assertEquals("Decrypted note (#2) does not match original",
                clearNote2, decrypted);
    }

}
