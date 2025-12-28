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
package com.xmission.trevin.android.todo.service;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

import com.xmission.trevin.android.todo.R;
import com.xmission.trevin.android.todo.util.StringEncryption;
import com.xmission.trevin.android.todo.provider.ToDo.ToDoItem;

import android.app.IntentService;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.util.Log;
import android.widget.Toast;

/**
 * Encrypts and decrypts private entries in the database
 * when the user sets or changes the password.
 *
 * @author Trevin Beattie
 */
public class PasswordChangeService extends IntentService
	implements ProgressReportingService {

    private static final String TAG = "PasswordChangeService";

    /** The name of the Intent action for changing the password */
    public static final String ACTION_CHANGE_PASSWORD =
	"com.xmission.trevin.android.todo.ChangePassword";
    /**
     * The name of the Intent extra data that holds the old password.
     * This must be a char array!
     */
    public static final String EXTRA_OLD_PASSWORD =
	"com.xmission.trevin.android.todo.OldPassword";
    /**
     * The name of the Intent extra data that holds the new password.
     * This must be a char array!
     */
    public static final String EXTRA_NEW_PASSWORD =
	"com.xmission.trevin.android.todo.NewPassword";

    /**
     * The columns we are interested in from the item table
     */
    private static final String[] ITEM_PROJECTION = new String[] {
            ToDoItem._ID,
            ToDoItem.DESCRIPTION,
            ToDoItem.NOTE,
            ToDoItem.PRIVATE,
    };

    /** The current mode of operation */
    public enum OpMode {
	DECRYPTING, ENCRYPTING
    }
    private OpMode currentMode = OpMode.DECRYPTING;

    /** The current number of entries changed */
    private int numChanged = 0;

    /** The total number of entries to be changed */
    private int changeTarget = 1;

    public class PasswordBinder extends Binder {
	public PasswordChangeService getService() {
	    Log.d(TAG, "PasswordBinder.getService()");
	    return PasswordChangeService.this;
	}
    }

    private PasswordBinder binder = new PasswordBinder();

    /** Handler for making calls involving the UI */
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    /**
     * Observers to call (on the UI thread) when
     * {@link #onHandleIntent(Intent)} is finished
     */
    private final List<HandleIntentObserver> observers = new ArrayList<>();

    /** Create the importer service with a named worker thread */
    public PasswordChangeService() {
	super(PasswordChangeService.class.getSimpleName());
	Log.d(TAG,"created");
	// If we die in the middle of a password change, restart the request.
	setIntentRedelivery(true);
    }

    /** @return the current mode of operation */
    public String getCurrentMode() {
	switch (currentMode) {
	case DECRYPTING:
	    return getString(R.string.ProgressMessageDecrypting);
	case ENCRYPTING:
	    return getString(R.string.ProgressMessageEncrypting);
	default:
	    return "";
	}
    }

    /** @return the total number of entries to be changed */
    public int getMaxCount() { return changeTarget; }

    /** @return the number of entries changed so far */
    public int getChangedCount() { return numChanged; }

    /** Called when an activity requests a password change */
    @Override
    protected void onHandleIntent(Intent intent) {
	Log.d(TAG, ".onHandleIntent({action=" + intent.getAction()
		+ ", data=" + intent.getDataString() + ")}");
	// Get the old password, if there is one.
	char[] oldPassword = intent.getCharArrayExtra(EXTRA_OLD_PASSWORD);
	// Get the new password, if there is one.
	char[] newPassword = intent.getCharArrayExtra(EXTRA_NEW_PASSWORD);
	Cursor c = null;
	ContentResolver resolver = getContentResolver();
	int decrypTotal = 0;
	StringEncryption globalEncryption =
	    StringEncryption.holdGlobalEncryption();
	try {
	    StringEncryption encryptor = new StringEncryption();
	    if (oldPassword != null) {
		if (!encryptor.hasPassword(resolver)) {
                    showToast(getString(R.string.ToastBadPassword));
                    notifyObservers(false);
                    return;
		}
		encryptor.setPassword(oldPassword);
		if (!encryptor.checkPassword(resolver)) {
                    showToast(getString(R.string.ToastBadPassword));
                    notifyObservers(false);
                    return;
		}
		// Decrypt all entries
		c = resolver.query(
			ToDoItem.CONTENT_URI, ITEM_PROJECTION,
			ToDoItem.PRIVATE + " > 1", null, null);
		decrypTotal = c.getCount();
		Log.d(TAG, ".onHandleIntent: Decrypting "
			+ decrypTotal + " items");
		// For now, just estimate the amount of work to be done.
		changeTarget = (newPassword == null) ?
			decrypTotal : (decrypTotal * 2);
		while (c.moveToNext()) {
		    ContentValues values = new ContentValues();
		    Uri itemUri = Uri.withAppendedPath(
			    ToDoItem.CONTENT_URI,
			    Integer.toString(c.getInt(
				    c.getColumnIndex(ToDoItem._ID))));
		    values.put(ToDoItem.DESCRIPTION,
			    encryptor.decrypt(c.getBlob(
				    c.getColumnIndex(
					    ToDoItem.DESCRIPTION))));
		    if (!c.isNull(c.getColumnIndex(ToDoItem.NOTE)))
			values.put(ToDoItem.NOTE,
				encryptor.decrypt(c.getBlob(
					c.getColumnIndex(ToDoItem.NOTE))));
		    values.put(ToDoItem.PRIVATE, 1);
		    resolver.update(itemUri, values, null, null);
		    numChanged++;
		    Log.d(TAG, ".onHandleIntent: decrypted row " + numChanged);
		}
		c.close();
		c = null;
		Log.d(TAG, ".onHandleIntent: Removing the old password hash");
		encryptor.removePassword(resolver);

		// Forget the old password
		encryptor.forgetPassword();
	    } else {
		if (encryptor.hasPassword(resolver)) {
                    showToast(getString(R.string.ToastBadPassword));
                    notifyObservers(false);
                    return;
		}
	    }

	    if (newPassword != null) {
		currentMode = OpMode.ENCRYPTING;
		Log.d(TAG, ".onHandleIntent: Storing the new password hash");
		encryptor.setPassword(newPassword);
		// Set the new password
		encryptor.storePassword(resolver);

		// Encrypt all entries
		c = resolver.query(ToDoItem.CONTENT_URI, ITEM_PROJECTION,
			ToDoItem.PRIVATE + " = 1", null, null);
		changeTarget = decrypTotal + c.getCount();
		Log.d(TAG, ".onHandleIntent: Encrypting "
			+ c.getCount() + " items");
		while (c.moveToNext()) {
		    ContentValues values = new ContentValues();
		    Uri itemUri = Uri.withAppendedPath(
				ToDoItem.CONTENT_URI,
				Integer.toString(c.getInt(
					c.getColumnIndex(ToDoItem._ID))));
		    values.put(ToDoItem.DESCRIPTION,
			    encryptor.encrypt(c.getString(
				    c.getColumnIndex(
					    ToDoItem.DESCRIPTION))));
		    if (!c.isNull(c.getColumnIndex(ToDoItem.NOTE)))
			values.put(ToDoItem.NOTE,
				encryptor.encrypt(c.getString(
					c.getColumnIndex(ToDoItem.NOTE))));
		    values.put(ToDoItem.PRIVATE, 2);
		    // Verify the data types — there have been problems with this
		    if (!(values.get(ToDoItem.DESCRIPTION) instanceof byte[]) ||
			    (values.containsKey(ToDoItem.NOTE) &&
				    !(values.get(ToDoItem.NOTE) instanceof byte[]))) {
			Log.e(TAG, "Error storing encrypted description: expected byte[], got "
				+ values.get(ToDoItem.DESCRIPTION).getClass().getSimpleName());
		    } else {
			resolver.update(itemUri, values, null, null);
		    }
		    numChanged++;
		    Log.d(TAG, ".onHandleIntent: encrypted row "
			    + (numChanged - decrypTotal));
		}
		if (globalEncryption.hasKey()) {
		    globalEncryption.setPassword(newPassword);
		    globalEncryption.checkPassword(resolver);
		}
	    } else {
		if (globalEncryption.hasKey())
		    globalEncryption.forgetPassword();
	    }

            notifyObservers(true);

	} catch (GeneralSecurityException gsx) {
	    if (c != null)
		c.close();
            Log.e(TAG, "Error changing the password!", gsx);
            showToast(gsx.getMessage());
            notifyObservers(gsx);
	} finally {
	    StringEncryption.releaseGlobalEncryption();
	}
    }

    /**
     * Called when the service is created.
     */
    @Override
    public void onCreate() {
        Log.d(TAG, ".onCreate");
        super.onCreate();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, ".onBind");
	return binder;
    }

    public void registerObserver(HandleIntentObserver observer) {
        Log.d(TAG, String.format(".registerObserver(%s)",
                observer.getClass().getName()));
        observers.add(observer);
    }

    public void unregisterObserver(HandleIntentObserver observer) {
        Log.d(TAG, String.format(".unregisterObserver(%s)",
                observer.getClass().getName()));
        observers.remove(observer);
    }

    /**
     * Notify all observers that the intent handler is finished.
     * The notifications will all be done on the UI thread.
     *
     * @param success whether the intent handler completed successfully
     */
    private void notifyObservers(final boolean success) {
        if (observers.isEmpty())
            // Shortcut out
            return;
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                for (HandleIntentObserver observer : observers) try {
                    if (success)
                        observer.onComplete();
                    else
                        observer.onRejected();
                } catch (Exception e) {
                    Log.w(TAG, "Failed to notify "
                            + observer.getClass().getName(), e);
                }
            }
        });
    }

    /**
     * Show a toast message.  This must be done on the UI thread.
     * In addition, we&rsquo;ll notify any observers of the message.
     *
     * @param message the message to toast
     */
    private void showToast(String message) {
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(PasswordChangeService.this, message,
                        Toast.LENGTH_LONG).show();
                for (HandleIntentObserver observer : observers) try {
                    observer.onToast(message);
                } catch (Exception e) {
                    Log.w(TAG, String.format(
                            "Failed to notify %s of toast \"%s\"",
                            observer.getClass().getName(), message), e);
                }
            }
        });
    }

    /**
     * Notify all observers that an exception has occurred.
     * The notifications will all be done on the UI thread.
     *
     * @param e the exception that occurred
     */
    private void notifyObservers(final Exception e) {
        if (observers.isEmpty())
            // Shortcut out
            return;
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                for (HandleIntentObserver observer : observers) try {
                    observer.onError(e);
                } catch (Exception e2) {
                    Log.w(TAG, String.format(
                            "Failed to notify %s of %s",
                            observer.getClass().getName(),
                            e.getClass().getName()), e2);
                }
            }
        });
    }

}
