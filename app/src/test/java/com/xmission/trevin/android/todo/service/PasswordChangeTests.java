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

import static com.xmission.trevin.android.todo.util.RandomToDoUtils.randomParagraph;
import static com.xmission.trevin.android.todo.util.RandomToDoUtils.randomToDo;
import static com.xmission.trevin.android.todo.util.StringEncryption.METADATA_PASSWORD_HASH;
import static org.junit.Assert.*;

import com.xmission.trevin.android.todo.data.ToDoCategory;
import com.xmission.trevin.android.todo.data.ToDoItem;
import com.xmission.trevin.android.todo.data.ToDoMetadata;
import com.xmission.trevin.android.todo.provider.MockToDoRepository;
import com.xmission.trevin.android.todo.provider.ToDoRepositoryImpl;
import com.xmission.trevin.android.todo.util.AuthenticationException;
import com.xmission.trevin.android.todo.util.PasswordMismatchException;
import com.xmission.trevin.android.todo.util.PasswordRequiredException;
import com.xmission.trevin.android.todo.util.StringEncryption;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.*;

import java.util.*;

/**
 * Unit tests for setting, changing, and removing a password.
 */
public class PasswordChangeTests {

    private static final RandomStringUtils SRAND = RandomStringUtils.insecure();

    private MockToDoRepository mockRepo = null;
    private StringEncryption globalEncryption = null;

    @Before
    public void initializeRepository() {
        if (mockRepo == null) {
            mockRepo = MockToDoRepository.getInstance();
            ToDoRepositoryImpl.setInstance(mockRepo);
        }
        mockRepo.clear();
        globalEncryption = StringEncryption.holdGlobalEncryption();
    }

    @After
    public void releaseRepository() {
        StringEncryption.releaseGlobalEncryption();
    }

    /**
     * Set up the password change worker, call it,
     * then wait for it to finish.
     *
     * @param oldPassword the old password,
     * or {@code null} if setting a new one.
     * @param newPassword the new password,
     * or {@code null} if clearing the old one.
     * @param expectedException the class of the expected exception,
     * if any; {@code null} otherwise.
     *
     * @return the progress of the password change call.
     *
     * @throws AssertionError if the result is not the expected type.
     * @throws AuthenticationException if there is an unexpected
     * error with the password change
     */
    private MockProgressBar runPasswordChangeWorker(
            String oldPassword, String newPassword,
            Class<? extends AuthenticationException> expectedException)
            throws AssertionError, AuthenticationException {
        MockProgressBar progress = new MockProgressBar();
        try {
            PasswordChanger.changePassword(mockRepo,
                    (oldPassword == null) ? null : oldPassword.toCharArray(),
                    (newPassword == null) ? null : newPassword.toCharArray(),
                    progress);
            assertEquals("Exception thrown ", expectedException, null);
        } catch (AuthenticationException ae) {
            if (ae.getClass() != expectedException)
                throw ae;
        } finally {
            progress.setEndTime();
        }
        return progress;
    }

    /**
     * Test setting a new password when the database has private records.
     * All private records should become encrypted.
     */
    @Test
    public void testNewPassword() {

        // Set up the test data
        ToDoItem publicItem = randomToDo();
        publicItem.setPrivate(0);
        publicItem.setCategoryName(mockRepo.getCategoryById(
                ToDoCategory.UNFILED).getName());
        mockRepo.insertItem(publicItem);
        ToDoItem privateItem = randomToDo();
        privateItem.setPrivate(StringEncryption.NO_ENCRYPTION);
        privateItem.setCategoryName(mockRepo.getCategoryById(
                        ToDoCategory.UNFILED).getName());
        // Ensure we also have a note on the private item
        if (privateItem.getNote() == null)
            privateItem.setNote(randomParagraph());
        mockRepo.insertItem(privateItem);

        final String password = SRAND.nextAlphanumeric(8);

        // Call the password change worker
        MockProgressBar progressBar = runPasswordChangeWorker(
                null, password, null);

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
            assertTrue("New password hash was not stored in the repository",
                    se.checkPassword(mockRepo));
        }
        String decrypted = (savedItem.getEncryptedDescription() == null)
                ? null : se.decrypt(savedItem.getEncryptedDescription());
        assertEquals("Decrypted description does not match original",
                privateItem.getDescription(), decrypted);

        decrypted = (savedItem.getEncryptedNote() == null) ? null
                : se.decrypt(savedItem.getEncryptedNote());
        assertEquals("Decrypted note does not match original",
                privateItem.getNote(), decrypted);

        MockProgressBar.Progress lastProgress = progressBar.getEndProgress();
        assertNotNull("Progress meter was not updated", lastProgress);
        assertEquals("Size of the progress meter", 1, lastProgress.total);
        assertEquals("Number of records encrypted", 1, lastProgress.current);
    }

    /**
     * Test clearing the password when the database has encrypted records.
     * All encrypted records should be unencrypted but private.
     */
    @Test
    public void testRemovePassword() {

        // Set up the test data
        final String password = SRAND.nextAlphanumeric(8);
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
        privateItem.setPrivate(StringEncryption.encryptionType());
        privateItem.setCategoryName(mockRepo.getCategoryById(
                        ToDoCategory.UNFILED).getName());
        mockRepo.insertItem(privateItem);

        globalEncryption.forgetPassword();

        // Call the password change worker
        MockProgressBar progressBar = runPasswordChangeWorker(
                password, null, null);

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

        MockProgressBar.Progress lastProgress = progressBar.getEndProgress();
        assertNotNull("Progress meter was not updated", lastProgress);
        assertEquals("Size of the progress meter", 1, lastProgress.total);
        assertEquals("Number of records decrypted", 1, lastProgress.current);
    }

    /**
     * Test clearing the password when the proper old password has
     * not been given.  Should result in a bad password exception.
     */
    @Test
    public void testRemoveBadPassword() {

        // Set up the test data
        String password = SRAND.nextAlphanumeric(12);
        globalEncryption.setPassword(password.toCharArray());
        globalEncryption.addSalt();
        globalEncryption.storePassword(mockRepo);

        globalEncryption.forgetPassword();

        password = SRAND.nextAlphabetic(8);

        // Call the password change worker
        runPasswordChangeWorker( password, null,
                PasswordMismatchException.class);
    }

    /**
     * Test clearing the password when there is no password set.
     * Should result in a bad password exception.
     */
    @Test
    public void testRemoveNoPassword() {

        String password = SRAND.nextAlphabetic(8);

        // Call the password change worker
        runPasswordChangeWorker(password, null,
                PasswordMismatchException.class);
    }

    /**
     * Test setting a &ldquo;new&rdquo; password when the database already
     * has a password.  Should result in a bad password exception.
     */
    @Test
    public void testNewPasswordConflict() {

        // Set up the test data
        String password = SRAND.nextAlphanumeric(12);
        globalEncryption.setPassword(password.toCharArray());
        globalEncryption.addSalt();
        globalEncryption.storePassword(mockRepo);

        globalEncryption.forgetPassword();

        password = SRAND.nextAlphabetic(12);

        // Call the password change worker
        runPasswordChangeWorker(null, password,
                PasswordRequiredException.class);
    }

    /**
     * Test changing the password when the database has encrypted records.
     * All encrypted records should be re-encrypted with the new password,
     * and any previously private but unencrypted records should be
     * encrypted as well.
     */
    @Test
    public void testChangePassword() {

        // Set up the test data
        final String oldPassword = SRAND.nextAlphanumeric(8);
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
        privateItem.setPrivate(StringEncryption.NO_ENCRYPTION);
        privateItem.setCategoryName(mockRepo.getCategoryById(
                        ToDoCategory.UNFILED).getName());
        mockRepo.insertItem(privateItem);
        ToDoItem encryptedItem = randomToDo();
        String clearDescription2 = encryptedItem.getDescription();
        String clearNote2 = encryptedItem.getNote();
        if (clearNote2 == null) {
            clearNote2 = randomParagraph();
        }
        encryptedItem.setPrivate(StringEncryption.encryptionType());
        encryptedItem.setCategoryName(mockRepo.getCategoryById(
                        ToDoCategory.UNFILED).getName());
        encryptedItem.setEncryptedDescription(
                globalEncryption.encrypt(clearDescription2));
        encryptedItem.setEncryptedNote(
                globalEncryption.encrypt(clearNote2));
        mockRepo.insertItem(encryptedItem);

        final String newPassword = SRAND.nextAlphanumeric(12);
        globalEncryption.forgetPassword();

        // Call the password change worker
        MockProgressBar progressBar = runPasswordChangeWorker(
                oldPassword, newPassword, null);

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
        assertTrue("New password hash was not stored in the repository",
                globalEncryption.checkPassword(mockRepo));
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

        MockProgressBar.Progress lastProgress = progressBar.getEndProgress();
        assertNotNull("Progress meter was not updated", lastProgress);
        assertEquals("Size of the progress meter", 2, lastProgress.total);
        assertEquals("Number of records re-encrypted", 2, lastProgress.current);
    }

}
