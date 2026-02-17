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

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.xmission.trevin.android.todo.R;
import com.xmission.trevin.android.todo.data.*;
import com.xmission.trevin.android.todo.data.repeat.*;
import com.xmission.trevin.android.todo.provider.ToDoRepository;
import com.xmission.trevin.android.todo.provider.ToDoRepositoryImpl;
import com.xmission.trevin.android.todo.service.PalmImporter.ImportType;
import com.xmission.trevin.android.todo.util.StringEncryption;

import java.io.*;
import java.time.*;
import java.util.*;

/**
 * This class imports To Do data from a Palm database (todo.dat).
 *
 * @author Trevin Beattie
 */
public class PalmImportWorker extends Worker implements ProgressBarUpdater {

    public static final String TAG = "PalmImportWorker";

    /**
     * The key of the input data that holds the location
     * of the &ldquo;todo.dat&rdquo; file
     */
    public static final String PALM_DATA_FILENAME = "PalmDataFilename";
    /** The key of the input data that holds the import typem (enum name) */
    public static final String PALM_IMPORT_TYPE = "PalmImportType";

    /**
     * The key of the input data that indicates
     * whether to import private records (boolean)
     */
    public static final String IMPORT_PRIVATE = "PalmImportPrivate";

    /** The name of the todo.dat file */
    private final String datFileName;

    /**
     * Input stream from which we will be reading the Palm database.
     * <p>
     * <b>Caution:</b> in order to properly use the Storage Access
     * Framework and check for access errors, the file is opened
     * in this class&rsquo; constructor; the actual read operation
     * does not occur until {@link #doWork()} is called, so the
     * file may remain open for an indeterminate amount of time.
     * </p>
     */
    private final InputStream inStream;

    /** The type of import this worker will be doing */
    private final ImportType importType;

    /** Whether to import private records */
    private final boolean importPrivate;

    /**
     * The password used to encrypt private records in the database.
     * If {@code null}, private records will not be encrypted.
     */
    private String currentPassword = null;

    /** Internal time when we last updated the async progress */
    private long lastProgressTimeNano;

    @NonNull
    private final Context context;

    @NonNull
    private final ToDoRepository repository;

    /** Handler for making calls involving the UI */
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    /**
     * Initialize the PalmImportWorker using the standard system services
     * and app class instances.
     *
     * @param context the application context
     * @param params Parameters to set up the internal state of this worker
     *
     * @throws IllegalArgumentException if the input data is invalid.
     */
    public PalmImportWorker(@NonNull Context context,
                            @NonNull WorkerParameters params)
        throws IllegalArgumentException, IOException {
        super(context, params);
        Log.d(TAG, String.format("Default initialization for %s",
                context.getClass().getName()));
        this.context = context;
        repository = ToDoRepositoryImpl.getInstance();

        if (!params.getInputData().hasKeyWithValueOfType(
                PALM_DATA_FILENAME, String.class))
            throw new IllegalArgumentException(
                    "No Palm data file provided");
        if (!params.getInputData().hasKeyWithValueOfType(
                PALM_IMPORT_TYPE, Integer.class))
            throw new IllegalArgumentException("Import type not provided");
        try {
            importType = ImportType.valueOf(params.getInputData()
                    .getString(PALM_IMPORT_TYPE));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid import type", e);
        }
        importPrivate = params.getInputData().getBoolean(
                IMPORT_PRIVATE, false);

        /*
         * Note: We don't verify the current password when initializing
         * the worker; there could be a race condition where it may change
         * between here and importing data.  This check will be done
         * by the import method inside a database transaction.
         */
        if (importPrivate) {
            StringEncryption se = StringEncryption.holdGlobalEncryption();
            if (se.hasKey())
                currentPassword = new String(se.getPassword());
        }

        datFileName = params.getInputData().getString(PALM_DATA_FILENAME);
        if (datFileName.startsWith("content://")) {
            // This is a URI from the Storage Access Framework
            try {
                Uri contentUri = Uri.parse(datFileName);
                inStream = context.getContentResolver()
                        .openInputStream(contentUri);
            } catch (FileNotFoundException fe) {
                Log.e(TAG, String.format(Locale.US,
                        "Content URI %s does not exist", datFileName), fe);
                showToast(context.getString(
                        R.string.ErrorImportNotFound, datFileName));
                throw fe;
            } catch (IOException e) {
                Log.e(TAG, String.format(Locale.US,
                        "Failed to open %s for reading", datFileName), e);
                showToast(context.getString(
                        R.string.ErrorImportCantRead, datFileName));
                throw e;
            }
        }

        else {
            File dataFile = new File(datFileName);
            if (!dataFile.exists()) {
                Log.e(TAG, String.format(Locale.US,
                        "File %s does not exist", datFileName));
                showToast(context.getString(
                        R.string.ErrorImportNotFound, datFileName));
                throw new FileNotFoundException(datFileName);
            }
            if (!dataFile.canRead()) {
                Log.e(TAG, String.format(Locale.US,
                        "Cannot read %s", datFileName));
                showToast(context.getString(
                        R.string.ErrorImportPermissionDenied, datFileName));
                throw new IOException(String.format(Locale.US,
                        "Cannot read %s", dataFile.getAbsolutePath()));
            }
            try {
                inStream = new FileInputStream(dataFile);
            } catch (IOException e) {
                Log.e(TAG, String.format(Locale.US,
                        "Failed to open %s for reading", datFileName), e);
                showToast(context.getString(
                        R.string.ErrorImportCantRead, datFileName));
                throw e;
            }
        }

        // Initialize string resources on the importer for the progress bar
        PalmImporter.setModeText(PalmImporter.OpMode.START,
                context.getString(R.string.ProgressMessageStart));
        PalmImporter.setModeText(PalmImporter.OpMode.CATEGORIES,
                context.getString(R.string.ProgressMessageImportCategories));
        PalmImporter.setModeText(PalmImporter.OpMode.ITEMS,
                context.getString(R.string.ProgressMessageImportItems));
        PalmImporter.setModeText(PalmImporter.OpMode.FINISH,
                context.getString(R.string.ProgressMessageFinish));
    }

    /**
     * Main entry point of the worker.
     */
    @Override
    @NonNull
    public Result doWork() {
        Log.d(TAG, ".doWork");
        long startTimeNano = System.nanoTime();
        lastProgressTimeNano = startTimeNano;
        updateProgress(context.getString(
                R.string.ProgressMessageImportReading), 0, 0, false);
        repository.open(context);
        try {
            PalmImporter.importData(repository, datFileName,
                    inStream, importType, importPrivate,
                    currentPassword, this);
            return Result.success();
        }

        catch (Exception e) {
            Log.e(TAG, "Error importing Palm data!", e);
            showToast(e.getMessage());
            return Result.failure(new Data.Builder()
                    .putString("Exception", e.getClass().getCanonicalName())
                    .putString("message", e.getMessage())
                    .build());
        }

        finally {
            long now = System.nanoTime();
            repository.release(context);
            Log.d(TAG, String.format("Finished work in %.4f seconds",
                    (now - startTimeNano) / 1.0e+9));
        }
    }

    /**
     * Update the async progress indicator.
     *
     * @param modeString the current mode of operation (reading,
     *                   adding categories, adding items)
     * @param importCount the number of items imported so far
     * @param totalCount the total number of items to be imported
     * @param throttle if {@code true}, skip updating the progress
     *                 if it&rsquo;s been less than 250 ms since
     *                 we last posted our progress.
     */
    @Override
    public void updateProgress(String modeString,
                                int importCount, int totalCount,
                                boolean throttle) {
        if (throttle) {
            long now = System.nanoTime();
            if ((now - lastProgressTimeNano) < 250000000L)
                return;
            lastProgressTimeNano = now;
        }
        Data progressData = new Data.Builder()
                .putString(PROGRESS_CURRENT_MODE, modeString)
                .putInt(PROGRESS_MAX_COUNT, totalCount)
                .putInt(PROGRESS_CURRENT_COUNT, importCount)
                .build();
        setProgressAsync(progressData);
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
