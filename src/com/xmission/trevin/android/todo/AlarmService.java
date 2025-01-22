/*
 * $Id: AlarmService.java,v 1.4 2017/07/25 16:59:20 trevin Exp trevin $
 * Copyright © 2011 Trevin Beattie
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
 *
 * $Log: AlarmService.java,v $
 * Revision 1.4  2017/07/25 16:59:20  trevin
 * Fixed an annoyance where notifications would continually be
 *   re-displayed after every update to To Do items.  The alarmed
 *   item's `last notification time' is now set when the notification
 *   occurs rather than when (or if) it was acknowledged.
 *
 * Revision 1.3  2014/03/22 19:03:51  trevin
 * When an alarm goes off, set the notification time on the item
 *   so that we don’t repeat the alarm for that item until the next day.
 *   (Currently broken.)
 * Make sure we’re holding a StringEncryption before releasing it.
 *
 * Revision 1.2  2011/05/19 05:12:53  trevin
 * Removed the binder; we don't use binding.
 * Implemented gathering alarm data and sending an alarm notification.
 * Don't cancel any notification when destroyed;
 *   destruction happens right after we finish initializing the alarms!
 *
 * Revision 1.1  2010/12/30 01:13:28  trevin
 * Initial revision
 *
 */
package com.xmission.trevin.android.todo;

import static com.xmission.trevin.android.todo.ToDoListActivity.*;

import java.security.GeneralSecurityException;
import java.util.*;

import com.xmission.trevin.android.todo.ToDo.ToDoItem;

import android.app.*;
import android.content.*;
import android.database.*;
import android.net.Uri;
import android.provider.MediaStore.Audio.Media;
import android.util.Log;

/**
 * Displays a notification when a To Do item's alarm comes due.
 *
 * @author Trevin Beattie
 */
public class AlarmService extends IntentService {

    private static final String TAG = "AlarmService";

    /** The name of the Intent action for acknowledging a notification */
    public static final String ACTION_NOTIFICATION_ACK =
	"com.xmission.trevin.android.todo.AlarmSnooze";
    /** The name of the Intent extra data that holds the notification date */
    public static final String EXTRA_NOTIFICATION_DATE =
	"com.xmission.trevin.android.todo.AlarmTime";

    private AlarmManager alarmManager;
    private NotificationManager notificationManager;

    /** Shared preferences */
    private SharedPreferences prefs;

    /**
     * The columns we are interested in from the item table
     */
    private static final String[] ITEM_PROJECTION = new String[] {
            ToDoItem._ID,
            ToDoItem.DESCRIPTION,
            ToDoItem.MOD_TIME,
            ToDoItem.CHECKED,
            ToDoItem.DUE_TIME,
            ToDoItem.ALARM_DAYS_EARLIER,
            ToDoItem.ALARM_TIME,
            ToDoItem.PRIVATE,
            ToDoItem.NOTIFICATION_TIME,
    };

    /**
     * Keep track of notifications we have already sent since the service
     * was activated.  For each item with an alarm we need to know its
     * ID, the time it was last modified when we looked at it, the
     * time its next alarm should go off, and when it will be overdue.
     * If an item is later modified, any previous alarm notification
     * is forgotten.
     */
    static class ItemInfo implements Comparable<ItemInfo> {
	final long id;
	final long lastModified;
	final int privacy;
	final String description;
	final byte[] encryptedDescription;
	final long dueDate;
	final long alarmTime;
	final int daysEarlier;
	final long notificationTime;
	Date alarmDate;

	public ItemInfo(Cursor c) {
	    id = c.getLong(c.getColumnIndex(ToDoItem._ID));
	    lastModified = c.getLong(c.getColumnIndex(ToDoItem.MOD_TIME));
	    privacy = c.getInt(c.getColumnIndex(ToDoItem.PRIVATE));
	    if (privacy <= 1) {
		description =
		    c.getString(c.getColumnIndex(ToDoItem.DESCRIPTION));
		encryptedDescription = null;
	    } else {
		description = null;
		encryptedDescription =
		    c.getBlob(c.getColumnIndex(ToDoItem.DESCRIPTION));
	    }
	    dueDate = c.getLong(c.getColumnIndex(ToDoItem.DUE_TIME));
	    alarmTime = c.getLong(c.getColumnIndex(ToDoItem.ALARM_TIME));
	    daysEarlier =
		c.getInt(c.getColumnIndex(ToDoItem.ALARM_DAYS_EARLIER));
	    notificationTime =
		c.getLong(c.getColumnIndex(ToDoItem.NOTIFICATION_TIME));

	    // Set the date of the next alarm.
	    Calendar cal = Calendar.getInstance();
	    cal.setTimeInMillis(dueDate);
	    cal.add(Calendar.DATE, -daysEarlier);
	    cal.set(Calendar.HOUR_OF_DAY, (int) (alarmTime / 3600000L));
	    cal.set(Calendar.MINUTE, (int) (alarmTime / 60000L) % 60);
	    cal.set(Calendar.SECOND, (int) (alarmTime / 1000L) % 60);
	    cal.set(Calendar.MILLISECOND, (int) (alarmTime % 1000));
	    while (cal.getTimeInMillis() < notificationTime)
		cal.add(Calendar.DATE, 1);
	    alarmDate = cal.getTime();
	}

	/**
	 * Compare this item's info with that of another item.
	 * Sorts the items by the alarm time or, if both items
	 * have the same alarm time, their descriptions.
	 */
	public int compareTo(ItemInfo i2) {
	    if (alarmDate.before(i2.alarmDate))
		return -1;
	    else if (alarmDate.after(i2.alarmDate))
		return 1;
	    if (description == null)
		return (i2.description == null) ? 0 : -1;
	    else
		return (i2.description == null) ? 1
			: description.compareTo(i2.description);
	}

	/** Advance the alarm to the next day past the given day */
	public void advanceToNextDay(long afterTime) {
	    Calendar cal = Calendar.getInstance();
	    cal.setTime(alarmDate);
	    if (afterTime > cal.getTimeInMillis())
		cal.add(Calendar.DATE, (int)
			((afterTime + 86399000L - cal.getTimeInMillis())
				/ 86400000L));
	    cal.add(Calendar.DATE, 1);
	    alarmDate = cal.getTime();
	}

	/** Item hashes are based on ID and modification time. */
	@Override
	public int hashCode() {
	    int hash = Long.valueOf(id).hashCode();
	    hash *= 31;
	    hash += Long.valueOf(lastModified).hashCode();
	    return hash;
	}

	/** Items are equal if they have the same ID and modification time. */
	@Override
	public boolean equals(Object o) {
	    if (!(o instanceof ItemInfo))
		return false;
	    ItemInfo i2 = (ItemInfo) o;
	    return (i2.id == id) && (i2.lastModified == lastModified);
	}
    }

    /** Our pending alarms in sorted order */
    private SortedSet<ItemInfo> pendingAlarms = new TreeSet<ItemInfo>();

    /** Create the importer service with a named worker thread */
    public AlarmService() {
	super(AlarmService.class.getSimpleName());
	Log.d(TAG,"created");
	// If we die, restart nothing.
	setIntentRedelivery(false);
    }

    /**
     * Called when the service is created.
     */
    @Override
    public void onCreate() {
        Log.d(TAG, ".onCreate");
        super.onCreate();
	notificationManager = (NotificationManager)
		getSystemService(NOTIFICATION_SERVICE);
	alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
	prefs = getSharedPreferences(TODO_PREFERENCES, MODE_PRIVATE);
    }

    /**
     * Called when service is requested.
     * For this service, it handles notification that a system event
     * has occurred such as boot-up, clock change, or time zone change.
     */
    @Override
    protected void onHandleIntent(Intent intent) {
	Log.d(TAG, ".onHandleIntent(" + intent.getAction() + ")");
	refreshAlarms();
	if (ACTION_NOTIFICATION_ACK.equals(intent.getAction())) {
	    snooze(intent.getLongExtra(EXTRA_NOTIFICATION_DATE,
		    System.currentTimeMillis()));
	    notificationManager.cancel(0);
	}
	else if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) ||
		Intent.ACTION_MAIN.equals(intent.getAction())) {
	}
	else if (Intent.ACTION_TIMEZONE_CHANGED.equals(intent.getAction())) {
	    // We have to change the alarm time on all items to
	    // the current time zone.
	}
	else if (Intent.ACTION_EDIT.equals(intent.getAction())) {
	    // Called by the To Do list activity when the data changes
	}
	if (!showNotification())
	    resetAlarm();
	pendingAlarms.clear();
    }

    /** Called when the service is about to be destroyed. */
    @Override
    public void onDestroy() {
        Log.d(TAG, ".onDestroy");
        super.onDestroy();
    }

    /**
     * Read all alarms in the To Do list and generate our pending alarm list.
     * We need to query the To-Do database for any entries
     * which have an alarm at some point in the future,
     * find the next one which is about to come due,
     * and set an alarm to go off at that time.
     * <p>
     * In the event an alarm is scheduled one or more days
     * in advance, set the alarm to go off at the scheduled
     * time if we are within that many days &mdash; e.g.,
     * if an alarm is set 3 days in advance we will notify
     * the user 3 days prior, 2 days prior, the day before,
     * and on the due date, all at the given alarm time.
     * <p>
     * If a To-Do item is overdue and has an alarm set,
     * notify the user immediately regardless of the alarm time.
     */
    private void refreshAlarms() {
	StringBuilder where = new StringBuilder();
	where.append(ToDoItem.CHECKED).append(" = 0 AND ");
	where.append(ToDoItem.DUE_TIME).append(" IS NOT NULL AND ");
	where.append(ToDoItem.ALARM_DAYS_EARLIER).append(" IS NOT NULL");
	Cursor c = getContentResolver().query(ToDoItem.CONTENT_URI,
		ITEM_PROJECTION, where.toString(), null, null);
	try {
	    while (c.moveToNext()) {
		ItemInfo item = new ItemInfo(c);
		Log.d(TAG, ".refreshAlarms(): Adding alarm for item " + item.id
			+ " at " + item.alarmDate.toString());
		pendingAlarms.add(item);
	    }
	} finally {
	    c.close();
	}
    }

    /**
     * Called when the user acknowledges a notification.
     * We need to push forward all past-due alarms by one day,
     * then start up the To Do List activity.
     */
    private void snooze(long alarmTime) {
	for (ItemInfo item : pendingAlarms) {
	    if (item.alarmDate.getTime() <= alarmTime)
		item.advanceToNextDay(alarmTime);
	}

	Intent intent = new Intent(this, ToDoListActivity.class);
	intent.setAction(Intent.ACTION_MAIN);
	intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	startActivity(intent);
    }

    /**
     * Called when an alarm goes off.
     * 
     * @return whether a notification has been displayed.
     */
    private boolean showNotification() {
	if (pendingAlarms.isEmpty())
	    return false;

	StringBuilder tickerText = new StringBuilder();
	tickerText.append(getString(R.string.NotificationHeader));
	boolean showPrivate = prefs.getBoolean(TPREF_SHOW_PRIVATE, false);
	boolean showEncrypted = prefs.getBoolean(TPREF_SHOW_ENCRYPTED, false);
	StringEncryption encryptor = showEncrypted
	    ? StringEncryption.holdGlobalEncryption() : null;
	int dueItems = 0;
	Date now = new Date();
	long firstDue = pendingAlarms.last().dueDate;
	ContentValues notificationTimeValues = new ContentValues();
	notificationTimeValues.put(ToDoItem.NOTIFICATION_TIME, now.getTime());
	for (ItemInfo item : pendingAlarms) {
	    if (item.alarmDate.before(now)) {
		// This item's alarm is due.  Add it to the ticker.
		dueItems++;

		String fmt = getString(R.string.NotificationFormatItem);
		String desc = item.description;
		if (item.privacy > 1) {
		    if (showPrivate) {
			if (showEncrypted) {
			    try {
				desc = encryptor.decrypt(item.encryptedDescription);
			    } catch (GeneralSecurityException gsx) {
				desc = "";
				fmt = getString(R.string.NotificationFormatEncrypted);
			    }
			} else {
			    fmt = getString(R.string.NotificationFormatEncrypted);
			}
		    } else {
			fmt = getString(R.string.NotificationFormatPrivate);
		    }
		} else if (item.privacy == 1) {
		    if (!showPrivate)
			fmt = getString(R.string.NotificationFormatPrivate);
		}
		tickerText.append(String.format(fmt, desc));

		Uri todoUri = Uri.withAppendedPath(ToDoItem.CONTENT_URI,
			Long.toString(item.id));
		getContentResolver().update(todoUri,
			notificationTimeValues, null, null);

		// Keep track of the first due item
		if (firstDue > item.dueDate)
		    firstDue = item.dueDate;
	    }
	}
	if (encryptor != null)
	    StringEncryption.releaseGlobalEncryption(this);

	if (dueItems == 0)
	    // No alarms had actually gone off yet.
	    return false;

	Intent mainIntent = new Intent(this, AlarmService.class);
	mainIntent.setAction(ACTION_NOTIFICATION_ACK);
	mainIntent.putExtra(EXTRA_NOTIFICATION_DATE, now.getTime());
	PendingIntent intent = PendingIntent.getService(this, 0, mainIntent,
		PendingIntent.FLAG_UPDATE_CURRENT);
	Notification notice = new Notification(R.drawable.stat_todo,
		tickerText.toString(), firstDue);
	notice.setLatestEventInfo(this,
		getResources().getString(R.string.app_name), tickerText, intent);
	notice.defaults = Notification.DEFAULT_ALL & ~Notification.DEFAULT_SOUND;
	long soundID = prefs.getLong(TPREF_NOTIFICATION_SOUND, -1);
	if (soundID >= 0)
	    notice.sound = Uri.withAppendedPath(
		    Media.INTERNAL_CONTENT_URI, Long.toString(soundID));
	notificationManager.notify(0, notice);
	return true;
    }

    /** Schedule an alarm for the next item due to come up. */
    private void resetAlarm() {
	Intent intent = new Intent(this, AlarmInitReceiver.class);
	PendingIntent sender = PendingIntent.getBroadcast(
		this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
	if (pendingAlarms.isEmpty())
	    alarmManager.cancel(sender);
	else
	    alarmManager.set(0,
		    pendingAlarms.first().alarmDate.getTime(), sender);
    }
}
