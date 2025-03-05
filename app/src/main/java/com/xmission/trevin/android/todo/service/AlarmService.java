/*
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
 */
package com.xmission.trevin.android.todo.service;

import static com.xmission.trevin.android.todo.ui.ToDoListActivity.*;

import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.*;

import com.xmission.trevin.android.todo.R;
import com.xmission.trevin.android.todo.data.AlarmItemInfo;
import com.xmission.trevin.android.todo.data.ToDo.ToDoItem;
import com.xmission.trevin.android.todo.receiver.AlarmInitReceiver;
import com.xmission.trevin.android.todo.ui.ToDoListActivity;
import com.xmission.trevin.android.todo.util.StringEncryption;

import android.app.*;
import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore.Audio.Media;
import android.support.v4.app.NotificationCompat;
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
    /** The name of the Intent extra data that holds the item category ID */
    public static final String EXTRA_ITEM_CATEGORY_ID =
            "com.xmission.trevin.android.todo.CategoryId";
    /** The name of the Intent extra data that holds the item ID */
    public static final String EXTRA_ITEM_ID =
            "com.xmission.trevin.android.todo.ItemId";

    public static final String ALMOST_DUE_CHANNEL_ID =
            "due_notification_channel";
    public static final String OVERDUE_CHANNEL_ID =
            "overdue_notification_channel";

    public static final String SILENT_CHANNEL_ID =
            "silent_notification_channel";

    /**
     * Template for notification groups.  This needs to be a unique string
     * (the documentation isn&rsquo;t clear whether that means unique within
     * or across applications) so we can&rsquo;t rely on user-defined
     * category names; we&rsquo;ll use the category ID instead.
     */
    private static final String NOTIFICATION_GROUP_FORMAT =
            "com.xmission.trevin.android.todo.category.%04d";

    /**
     * Notification ID to use when running this service in the foreground
     * (Oreo or later).  This <b>must not</b> conflict with the ID of
     * any alarm notification, which are based on To Do item ID&rsquo;s.
     */
    private static final int FG_NOTIFICATION_ID = -379110754;

    private AlarmManager alarmManager;
    private NotificationManager notificationManager;

    /** Notification channel to use for upcoming items (API 26+) */
    private NotificationChannel almostDueChannel;

    /** Notification channel to use for past-due items (API 26+) */
    private NotificationChannel overdueChannel;

    /**
     * &ldquo;Notification&rdquo; channel to use when the service is
     * raising or updating alarms (Oreo and up)
     */
    private NotificationChannel silentChannel;

    /** Shared preferences */
    private SharedPreferences prefs;

    /** The name of the app; used for the title of notifications */
    private String appName;

    /**
     * The columns we are interested in from the item table
     */
    private static final String[] ITEM_PROJECTION = new String[] {
            ToDoItem._ID,
            ToDoItem.CATEGORY_ID,
            ToDoItem.CATEGORY_NAME,
            ToDoItem.DESCRIPTION,
            ToDoItem.MOD_TIME,
            ToDoItem.CHECKED,
            ToDoItem.DUE_TIME,
            ToDoItem.ALARM_DAYS_EARLIER,
            ToDoItem.ALARM_TIME,
            ToDoItem.PRIVATE,
            ToDoItem.NOTIFICATION_TIME,
    };

    /** Our pending alarms in sorted order */
    private final SortedSet<AlarmItemInfo> pendingAlarms = new TreeSet<>();

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
        appName = getString(R.string.app_name);
	notificationManager = (NotificationManager)
		getSystemService(NOTIFICATION_SERVICE);
	alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
	prefs = getSharedPreferences(TODO_PREFERENCES, MODE_PRIVATE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Oreo and up must use channels to send notifications
            almostDueChannel = new NotificationChannel(ALMOST_DUE_CHANNEL_ID,
                    getString(R.string.NotificationChannelDueName),
                    NotificationManager.IMPORTANCE_DEFAULT);
            almostDueChannel.setDescription(getString(
                    R.string.NotificationChannelDueDescription));
            overdueChannel = new NotificationChannel(OVERDUE_CHANNEL_ID,
                    getString(R.string.NotificationChannelOverdueName),
                    NotificationManager.IMPORTANCE_HIGH);
            overdueChannel.setDescription(getString(
                    R.string.NotificationChannelOverdueDescription));
            silentChannel = new NotificationChannel(SILENT_CHANNEL_ID,
                    getString(R.string.NotificationChannelSilentName),
                    NotificationManager.IMPORTANCE_NONE);
            silentChannel.setDescription(getString(
                    R.string.NotificationChannelSilentDescription));
            notificationManager.createNotificationChannel(almostDueChannel);
            notificationManager.createNotificationChannel(overdueChannel);
            notificationManager.createNotificationChannel(silentChannel);

            Notification busyNotification =
                    new Notification.Builder(this, SILENT_CHANNEL_ID)
                            .setSmallIcon(R.drawable.stat_todo)
                            .setContentTitle(getString(R.string.app_name))
                            .setContentText(getString(
                                    R.string.AlarmServiceBackgroundMessage))
                            .build();
            // To Do: In API 29+, call the version of this method
            // which includes a third parameter for
            // ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
            // **unless** we're acknowledging a notification click;
            // "short" services are not allowed to start a foreground service.
            //if (!ACTION_NOTIFICATION_ACK.equals(intent.getAction())) {
                startForeground(FG_NOTIFICATION_ID, busyNotification);
            //}
        }
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
            long categoryId = intent.getLongExtra(EXTRA_ITEM_CATEGORY_ID, -1);
            long itemId = intent.getLongExtra(EXTRA_ITEM_ID, -1);
            long notificationDate = intent.getLongExtra(EXTRA_NOTIFICATION_DATE,
                    System.currentTimeMillis());
	    snooze(notificationDate, categoryId, itemId);
	    // Fix Me: instead of calling notificationManager.cancel((int) itemId);
            // change
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
	showPendingNotifications();
        resetAlarm();
	pendingAlarms.clear();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        }
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
                AlarmItemInfo item = new AlarmItemInfo(c);
		Log.d(TAG, ".refreshAlarms(): Adding alarm for item " + item.getId()
			+ " at " + item.getAlarmDate().toString());
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
     *
     * @param alarmTime the time that the notification was posted
     *        (or when it was acknowledged if the notification time
     *         is not available)
     * @param categoryId the ID of the category that the item
     *        belongs to
     * @param itemId the ID of the item whose notification
     *        the user acknowledged
     */
    private void snooze(long alarmTime, long categoryId, long itemId) {
	for (AlarmItemInfo item : pendingAlarms) {
	    if (item.getAlarmDate().getTime() <= alarmTime)
		item.advanceToNextDay(alarmTime);
	}

	Intent intent = new Intent(this, ToDoListActivity.class);
	intent.setAction(Intent.ACTION_MAIN);
        intent.setData(ToDoItem.CONTENT_URI);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(EXTRA_ITEM_CATEGORY_ID, categoryId);
        intent.putExtra(EXTRA_ITEM_ID, itemId);
	startActivity(intent);
    }

    /**
     * Called when an alarm goes off.
     * 
     * @return whether a notification has been displayed.
     */
    private boolean showPendingNotifications() {
        if (pendingAlarms.isEmpty())
            return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (!notificationManager.areNotificationsEnabled())
                // The user has disabled notifications; don't show any alarm
                return false;
        }

	boolean showPrivate = prefs.getBoolean(TPREF_SHOW_PRIVATE, false);
	boolean showEncrypted = prefs.getBoolean(TPREF_SHOW_ENCRYPTED, false);
	boolean doVibrate = prefs.getBoolean(TPREF_NOTIFICATION_VIBRATE, false);
        long soundID = prefs.getLong(TPREF_NOTIFICATION_SOUND, -1);
	StringEncryption encryptor = showEncrypted
	    ? StringEncryption.holdGlobalEncryption() : null;
	int dueItems = 0;
	Date now = new Date();
	ContentValues notificationTimeValues = new ContentValues();
	notificationTimeValues.put(ToDoItem.NOTIFICATION_TIME, now.getTime());
	for (AlarmItemInfo item : pendingAlarms) {
	    if (item.getAlarmDate().before(now)) {
		// This item's alarm is due.
		dueItems++;
                postNotification(item, now, doVibrate, soundID,
                        showPrivate, showEncrypted, encryptor);

		Uri todoUri = Uri.withAppendedPath(ToDoItem.CONTENT_URI,
			Long.toString(item.getId()));
		getContentResolver().update(todoUri,
			notificationTimeValues, null, null);
	    }
	}
	if (encryptor != null)
	    StringEncryption.releaseGlobalEncryption(this);

	return (dueItems > 0);

    }

    /** Date format to use for debug log messages */
    static final SimpleDateFormat LOG_DATE_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd");

    /**
     * Create and post a notification for a given due item.
     * This handles differences between API levels.
     *
     * @param item the alarm item
     * @param now the current time, as of when we started
     *        looping through the notifications
     * @param doVibrate whether to vibrate when posting the notification
     *        (Nougat and earlier)
     * @param soundID the ID of the notification sound to play
     *        (Nougat and earlier)
     * @param showPrivate whether private records are shown.  If not,
     *        the notification will show &ldquo;[Private]&rdquo; instead
     *        of the item&rsquo;s description.
     * @param showEncrypted whether encrypted records are shown.  If not,
     *        the notification will show &ldquo;[Locked]&rdquo; (if
     *        @code{showPrivate} is @code{true}) instead of the
     *        item&rsquo;s description.  The notification may still show
     *        &ldquo;[Locked]&rdquo; if we are unable to decrypt the item.
     * @param encryptor the {@link StringEncryption} service we use to
     *        decrypt encrypted records.
     */
    private void postNotification(
            AlarmItemInfo item, Date now,
            boolean doVibrate, long soundID,
            boolean showPrivate, boolean showEncrypted,
            StringEncryption encryptor) {

        String category = item.getCategory();
        String title;
        if (category == null)
            title = appName;
        else
            title = String.format("%s (%s)", appName, category);

        String descr;
        if (item.getPrivacy() <= 1) {
            if ((item.getPrivacy() == 1) && !showPrivate)
                descr = getString(R.string.NotificationFormatPrivate);
            else
                descr = item.getDescription();
        } else { // (item.privacy > 1)
            if (showPrivate) {
                if (showEncrypted) {
                    try {
                        descr = encryptor.decrypt(item.getEncryptedDescription());
                    } catch (GeneralSecurityException gsx) {
                        descr = getString(R.string.NotificationFormatEncrypted);
                    }
                } else {
                    descr = getString(R.string.NotificationFormatEncrypted);
                }
            } else {
                descr = getString(R.string.NotificationFormatPrivate);
            }
        }

        Intent mainIntent = new Intent(this, AlarmService.class);
       	mainIntent.setAction(ACTION_NOTIFICATION_ACK);
       	mainIntent.putExtra(EXTRA_NOTIFICATION_DATE, now.getTime());
        mainIntent.putExtra(EXTRA_ITEM_CATEGORY_ID, item.getCategoryId());
        mainIntent.putExtra(EXTRA_ITEM_ID, item.getId());

        int defaultFlags = Notification.DEFAULT_LIGHTS;
        if (doVibrate) defaultFlags |= Notification.DEFAULT_VIBRATE;
        boolean isOverdue = item.getDueDate() < now.getTime() ;

        /*
         * IMPORTANT!  Each PendingIntent must have a unique requestCode
         * corresponding to the main Intent that it holds; otherwise
         * the extra data for later notifications (i.e. the item it
         * belongs to) will overwrite the extra for earlier ones.
         * We still use FLAG_UPDATE_CURRENT in case we do raise an alarm
         * for an item where the user hasn't cleared the previous notice.
         */
        PendingIntent intent = PendingIntent.getService(this,
                (int) item.getId(), mainIntent,
       		PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notice;

        Log.d(TAG, String.format(".postNotification(\"%s\" (%d), \"%s\" (%d), \"%s\", %s)",
                title, item.getCategoryId(), descr, item.getId(),
                LOG_DATE_FORMAT.format(item.getDueDate()),
                isOverdue ? "overdue" : "normal"));

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                    .setSmallIcon(R.drawable.stat_todo)
                    .setContentTitle(title)
                    .setContentText(descr)
                    .setContentIntent(intent)
                    .setDefaults(defaultFlags)
                    .setOnlyAlertOnce(true)
                    .setTicker(descr)
                    .setWhen(item.getDueDate());
            if (soundID >= 0)
                builder = builder.setSound(Uri.withAppendedPath(
                        Media.INTERNAL_CONTENT_URI, Long.toString(soundID)));
            notice = builder.build();
        }
        else {
            Notification.Builder builder;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) { // KitKat through Nougat
                builder = new Notification.Builder(this)
                        .setDefaults(defaultFlags)
                        .setPriority(isOverdue ? Notification.PRIORITY_HIGH
                                : Notification.PRIORITY_DEFAULT);
                if (soundID >= 0)
                    builder = builder.setSound(Uri.withAppendedPath(
                            Media.INTERNAL_CONTENT_URI, Long.toString(soundID)));
            } else { // Oreo and up
                builder = new Notification.Builder(this,
                        isOverdue ? OVERDUE_CHANNEL_ID : ALMOST_DUE_CHANNEL_ID);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                builder = builder.setShowWhen(true);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                builder = builder.setGroup(String.format(
                        NOTIFICATION_GROUP_FORMAT, item.getCategoryId()));
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if (isOverdue) {
                    builder = builder.setCategory(Notification.CATEGORY_ALARM);
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    builder = builder.setCategory(Notification.CATEGORY_REMINDER);
                } else {
                    // Substitute category for upcoming due items on Lollipop
                    builder = builder.setCategory(Notification.CATEGORY_EVENT);
                }
                if (item.getPrivacy() <= 0) {
                    builder = builder.setVisibility(Notification.VISIBILITY_PUBLIC);
                } else if (item.getPrivacy() == 1) {
                    builder = builder.setVisibility(Notification.VISIBILITY_PRIVATE);
                } else {
                    builder = builder.setVisibility(Notification.VISIBILITY_SECRET);
                }
            }
            builder = builder.setSmallIcon(R.drawable.stat_todo)
                    .setContentTitle(title)
                    .setContentText(descr)
                    .setContentIntent(intent)
                    .setOnlyAlertOnce(true)
                    .setTicker(descr)
                    .setWhen(item.getDueDate());
            notice = builder.build();
        }

        // We have to narrow the item ID to fit a notification ID;
        // hope we don't have any collisions.  (Shouldn't happen
        // on a reasonably-sized database of items.)
        notificationManager.notify((int) item.getId(), notice);

    }

    /** Date+time format to use for debug log messages */
    static final SimpleDateFormat LOG_DATIME_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss z");

    /** Schedule an alarm for the next item due to come up. */
    private void resetAlarm() {
	Intent intent = new Intent(this, AlarmInitReceiver.class);
	PendingIntent sender = PendingIntent.getBroadcast(
		this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
	if (pendingAlarms.isEmpty()) {
            Log.d(TAG, "No To Do alarms are pending;"
                    + " cancelling any intended alarm");
	    alarmManager.cancel(sender);
        } else {
            Log.d(TAG, String.format("%d To Do alarms are pending;"
                    + " scheduling an alarm for the next item at %s",
                    pendingAlarms.size(), LOG_DATIME_FORMAT.format(
                            pendingAlarms.first().getAlarmDate())));
	    alarmManager.set(AlarmManager.RTC_WAKEUP,
		    pendingAlarms.first().getAlarmDate().getTime(), sender);
        }
    }

}
