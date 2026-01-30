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

import com.xmission.trevin.android.todo.R;
import com.xmission.trevin.android.todo.data.ToDoItem;
import com.xmission.trevin.android.todo.provider.ToDoRepository;
import com.xmission.trevin.android.todo.provider.ToDoRepositoryImpl;
import com.xmission.trevin.android.todo.util.AuthenticationException;
import com.xmission.trevin.android.todo.util.EncryptionException;
import com.xmission.trevin.android.todo.util.StringEncryption;

import android.content.Context;
import android.database.SQLException;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * Encrypts and decrypts private entries in the database
 * when the user sets or changes the password.
 *
 * @author Trevin Beattie
 */
public class PasswordChangeWorker extends Worker {

    private static final String TAG = "PasswordChangeWorker";

    /** The key of the input data that holds the old password. */
    public static final String DATA_OLD_PASSWORD = "OldPassword";
    /** The key of the input data that holds the new password. */
    public static final String DATA_NEW_PASSWORD = "NewPassword";

    /**
     * The key of the progress data that holds the current mode
     * of operation
     */
    public static final String PROGRESS_CURRENT_MODE = "ProgressCurrentMode";
    /**
     * The key of the progress data that holds the
     * total number of To Do items that require updating
     */
    public static final String PROGRESS_MAX_COUNT = "ProgressMaxCount";
    /**
     * The key of the progress data that holds the number of
     * To Do items that have been updated so far
     */
    public static final String PROGRESS_CHANGED_COUNT = "ProgressChangedCount";

    @NonNull
    private final Context context;

    @NonNull
    private final ToDoRepository repository;

    /** Handler for making calls involving the UI */
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    /** The old password used to decrypt encrypted records */
    private char[] oldPassword;

    /** The new password (if any) used to (re-)encrypt private records */
    private char[] newPassword;

    /** The current number of entries changed */
    private int numChanged;

    /** The total number of entries to be changed */
    private int changeTarget = 1;

    /**
     * Initialize the PasswordChangeWorker using the standard system services
     * and app class instances.
     *
     * @param context the application context
     * @param params Parameters to set up the internal state of this worker
     *
     * @throws IllegalArgumentException if the input data is invalid.
     */
    public PasswordChangeWorker(@NonNull Context context,
                                @NonNull WorkerParameters params)
            throws IllegalArgumentException  {
        super(context, params);
        Log.d(TAG, String.format("Default initialization for %s",
                context.getClass().getName()));
        this.context = context;
        repository = ToDoRepositoryImpl.getInstance();
        getInputData(params);
    }

    /**
     * Initialize the PasswordChangeWorker using designated services and
     * app class instances.  This is intended for testing purposes
     * to provide mock services.
     *
     * @param context the application context.
     * @param params Parameters to set up the internal state of this worker.
     * @param repository the To Do data repository.
     *
     * @throws IllegalArgumentException if the input data is invalid.
     */
    public PasswordChangeWorker(@NonNull Context context,
                                @NonNull WorkerParameters params,
                       @NonNull ToDoRepository repository)
            throws IllegalArgumentException {
        super(context, params);
        Log.d(TAG, String.format(
                "Custom initialization for (%s, %s)",
                context.getClass().getName(),
                repository.getClass().getName()));
        this.context = context;
        this.repository = repository;
        getInputData(params);
    }

    /**
     * Extract our input data from the {@link WorkerParameters}.
     *
     * @throws IllegalArgumentException if the input data is invalid.
     */
    private void getInputData(@NonNull WorkerParameters params) {
        if (params.getInputData().hasKeyWithValueOfType(
                DATA_OLD_PASSWORD, String.class))
            oldPassword = params.getInputData().getString(
                    DATA_OLD_PASSWORD).toCharArray();
        else
            oldPassword = null;

        if (params.getInputData().hasKeyWithValueOfType(
                DATA_NEW_PASSWORD, String.class))
            newPassword = params.getInputData().getString(
                    DATA_NEW_PASSWORD).toCharArray();
        else
            newPassword = null;

        if ((oldPassword == null) && (newPassword == null))
            throw new IllegalArgumentException(
                    "Both the old and new passwords have not been provided"
                            + " or are the wrong type");
    }

    /**
     * Main entry point of the worker.  This checks the old password,
     * runs the main work loop in a database transaction, then updates
     * or clears the password in the global encryption object as needed.
     */
    @Override
    @NonNull
    public Result doWork() {
        Log.d(TAG, ".doWork");
        numChanged = 0;
        // Don't set this to 0, or it will mess up the progress bar.
        changeTarget = 1;
        // Initialize the progress indicator
        Data progressData = new Data.Builder()
                .putString(PROGRESS_CURRENT_MODE,
                        context.getString(R.string.ProgressMessageStart))
                .putInt(PROGRESS_MAX_COUNT, changeTarget)
                .putInt(PROGRESS_CHANGED_COUNT, numChanged)
                .build();
        setProgressAsync(progressData);

        StringEncryption oldCrypt = null;
        StringEncryption newCrypt = null;
        StringEncryption globalEncryption =
                StringEncryption.holdGlobalEncryption();
        final String badPasswordMessage =
                context.getString(R.string.ToastBadPassword);

        repository.open(context);
        try {
            if (oldPassword != null) {
                if (!globalEncryption.hasPassword(repository)) {
                    showToast(badPasswordMessage);
                    return Result.failure(new Data.Builder()
                            .putString("Error", "Old password provided but"
                                    + " no password has been set")
                            .putString("message", badPasswordMessage)
                            .build());
                }
                oldCrypt = new StringEncryption();
                oldCrypt.setPassword(oldPassword);
                if (!oldCrypt.checkPassword(repository)) {
                    showToast(badPasswordMessage);
                    return Result.failure(new Data.Builder()
                            .putString("Error", "Old password does not match"
                                    + " hash in the database")
                            .putString("message", badPasswordMessage)
                            .build());
                }
            } else {
                if (globalEncryption.hasPassword(repository)) {
                    showToast(badPasswordMessage);
                    return Result.failure(new Data.Builder()
                            .putString("Error",
                                    "Current password was not provided")
                            .putString("message", badPasswordMessage)
                            .build());
                }
            }
            if (newPassword != null) {
                newCrypt = new StringEncryption();
                newCrypt.setPassword(newPassword);
                newCrypt.addSalt();
            }

            repository.runInTransaction(new PasswordChangeTransactionRunner(
                    oldCrypt, newCrypt));

            progressData = new Data.Builder()
                    .putString(PROGRESS_CURRENT_MODE,
                            context.getString(R.string.ProgressMessageFinish))
                    .putInt(PROGRESS_MAX_COUNT, changeTarget)
                    .putInt(PROGRESS_CHANGED_COUNT, numChanged)
                    .build();
            setProgressAsync(progressData);

            if ((newCrypt != null) && globalEncryption.hasKey()) {
                globalEncryption.setPassword(newPassword);
                globalEncryption.checkPassword(repository);
            } else {
                globalEncryption.forgetPassword();
            }
            return Result.success();
        }

        catch (Exception e) {
            Log.e(TAG, "Error changing the password!", e);
            showToast(e.getMessage());
            return Result.failure(new Data.Builder()
                    .putString("Exception", e.getClass().getCanonicalName())
                    .putString("message", e.getMessage())
                    .build());
        }

        finally {
            repository.release(context);
            StringEncryption.releaseGlobalEncryption(context);
        }
    }

    /**
     * Main work loop which is done in a database transaction &mdash;
     * either all data changes succeed, or none of them do.  This
     * runs through each private record in the repository, decrypting
     * it with the old password (if any) and encrypting it with the
     * new password (if any).  After the private records have been
     * updated, it stores the hash of the new password.
     */
    private class PasswordChangeTransactionRunner implements Runnable {
        final StringEncryption oldEncryption;
        final StringEncryption newEncryption;
        final String progressMode;
        /**
         * @param oldCrypt encryption object which has been configured
         * with the old password, or {@code null} if there was no
         * password previously set.  The caller should have already
         * verified whether the password is correct; otherwise decrypting
         * encrypted records will fail.
         * @param newCrypt encryption object which has been configured
         * with the new password, or {@code null} if we are removing
         * the password.
         */
        PasswordChangeTransactionRunner(
                @Nullable StringEncryption oldCrypt,
                @Nullable StringEncryption newCrypt) {
            oldEncryption = oldCrypt;
            newEncryption = newCrypt;

            if ((oldEncryption == null) && (newEncryption != null))
                progressMode = context.getString(
                        R.string.ProgressMessageEncrypting);
            else if ((oldEncryption != null) && (newEncryption == null))
                progressMode = context.getString(
                        R.string.ProgressMessageDecrypting);
            else
                progressMode = context.getString(
                        R.string.ProgressMessageReencrypting);
        }
        @Override
        public void run() throws EncryptionException, SQLException {
            long[] privateItemIds = repository.getPrivateItemIds();
            changeTarget = privateItemIds.length;
            int countDecrypted = 0;
            int countEncrypted = 0;
            long now = System.nanoTime();
            long startTime = now;
            long lastProgressUpdate = now;
            for (long id : privateItemIds) {
                ToDoItem item = repository.getItemById(id);
                if (item == null) {
                    Log.w(TAG, String.format(
                            "To Do item #%d disappeared while"
                                    + " changing the password!", id));
                    continue;
                }
                if (item.isEncrypted()) {
                    if (oldEncryption == null)
                        throw new IllegalStateException("Encrypted record"
                                + " found but no old password was provided");
                    item.setDescription(oldEncryption.decrypt(
                            item.getEncryptedDescription()));
                    item.setEncryptedDescription(null);
                    if (item.getEncryptedNote() != null) {
                        item.setNote(oldEncryption.decrypt(
                                item.getEncryptedNote()));
                        item.setEncryptedNote(null);
                    }
                    item.setPrivate(1);
                    countDecrypted++;
                }
                if (newEncryption != null) {
                    item.setEncryptedDescription(newEncryption.encrypt(
                            item.getDescription()));
                    item.setDescription(null);
                    if (item.getNote() != null) {
                        item.setEncryptedNote(newEncryption.encrypt(
                                item.getNote()));
                        item.setNote(null);
                    }
                    item.setPrivate(2);
                    countEncrypted++;
                }
                repository.updateItem(item);
                numChanged++;

                // Periodically update our progress
                now = System.nanoTime();
                if (now >= lastProgressUpdate + 250000000L) {
                    Data progressData = new Data.Builder()
                            .putString(PROGRESS_CURRENT_MODE, progressMode)
                            .putInt(PROGRESS_MAX_COUNT, changeTarget)
                            .putInt(PROGRESS_CHANGED_COUNT, numChanged)
                            .build();
                    setProgressAsync(progressData);
                    lastProgressUpdate = now;
                }
            }
            Log.d(TAG, String.format(
                    "%d items decrypted, %d encrypted in %.3fs",
                    countDecrypted, countEncrypted,
                    (now - startTime) / 1.0e+9));
            if (newEncryption == null) {
                if (oldEncryption != null)
                    oldEncryption.removePassword(repository);
            } else {
                newEncryption.storePassword(repository);
            }
        }
    }

    /**
     * @return the current mode of operation
     */
    private String getCurrentMode() {
        if (numChanged == 0)
            return context.getString(R.string.ProgressMessageStart);
        if (numChanged >= changeTarget)
            return context.getString(R.string.ProgressMessageFinish);
        if ((oldPassword == null) && (newPassword != null))
            return context.getString(R.string.ProgressMessageEncrypting);
        if ((oldPassword != null) && (newPassword == null))
            return context.getString(R.string.ProgressMessageDecrypting);
        return context.getString(R.string.ProgressMessageReencrypting);
    }

    /**
     * Show a toast message.  This must be done on the UI thread.
     *
     * @param message the message to toast
     */
    private void showToast(String message) {
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, message,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

}
