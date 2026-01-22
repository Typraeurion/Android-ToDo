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

import static android.content.Context.ALARM_SERVICE;
import static android.content.Context.NOTIFICATION_SERVICE;

import com.google.common.util.concurrent.ListenableFuture;
import com.xmission.trevin.android.todo.R;
import com.xmission.trevin.android.todo.data.AlarmInfo;
import com.xmission.trevin.android.todo.data.ToDoPreferences;
import com.xmission.trevin.android.todo.provider.ToDoRepository;
import com.xmission.trevin.android.todo.provider.ToDoRepositoryImpl;
import com.xmission.trevin.android.todo.provider.ToDoSchema.ToDoItemColumns;
import com.xmission.trevin.android.todo.receiver.AlarmInitReceiver;
import com.xmission.trevin.android.todo.ui.ToDoListActivity;
import com.xmission.trevin.android.todo.util.StringEncryption;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore.Audio.Media;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.ForegroundInfo;
import androidx.work.ListenableWorker.Result;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.impl.utils.futures.SettableFuture;

import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.SortedSet;

/**
 * Displays notifications when one or more To Do items&rsquo; alarms come
 * due.  Then reschedules itself to go off for the next configured alarm.
 *
 * @author Trevin Beattie
 */
public class AlarmWorker extends Worker {

    private static final String TAG = "AlarmWorker";

    /**
     * The name of the Intent action to use for waking up the receiver
     * by a scheduled alarm
     */
    public static final String ACTION_TRIGGER_ALARM =
            "com.xmission.trevin.android.todo.action.TRIGGER_ALARM";

    /** The name of the Intent action for acknowledging a notification */
    public static final String ACTION_NOTIFICATION_ACK =
            "com.xmission.trevin.android.todo.action.ALARM_ACKNOWLEDGE";
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

    private final Context context;
    private final AlarmManager alarmManager;
    private final NotificationManager notificationManager;

    /** Shared preferences */
    private final ToDoPreferences prefs;

    private final ToDoRepository repository;

    /** The name of the app; used for the title of notifications */
    private final String appName;

    /**
     * Initialize the AlarmWorker using the standard system services
     * and app class instances.
     *
     * @param context the application context
     * @param params Parameters to set up the internal state of this worker
     */
    public AlarmWorker(@NonNull Context context,
                       @NonNull WorkerParameters params) {
        super(context, params);
        Log.d(TAG, "Default initialization for context "
                + context.getClass().getName());
        this.context = context;
        appName = context.getString(R.string.app_name);
        notificationManager = (NotificationManager)
                context.getSystemService(NOTIFICATION_SERVICE);
        alarmManager = (AlarmManager) context.getSystemService(ALARM_SERVICE);
        prefs = ToDoPreferences.getInstance(context);
        repository = ToDoRepositoryImpl.getInstance();
    }

    /**
     * Initialize the AlarmWorker using designated services and
     * app class instances.  This is intended for testing purposes
     * to provide mock services.
     *
     * @param context the application context.
     * @param params Parameters to set up the internal state of this worker.
     * @param alarmManager the service used to schedule the next time
     *                this worker should run.
     * @param notificationManager the service used to show notifications
     *                of items whose alarms have triggered.
     * @param prefs the shared preferences object.
     * @param repository the To Do data repository.
     */
    public AlarmWorker(@NonNull Context context,
                       @NonNull WorkerParameters params,
                       @NonNull AlarmManager alarmManager,
                       @NonNull NotificationManager notificationManager,
                       @NonNull ToDoPreferences prefs,
                       @NonNull ToDoRepository repository) {
        super(context, params);
        Log.d(TAG, String.format(
                "Custom initialization for (%s, %s, %s, %s, %s)",
                context.getClass().getName(),
                alarmManager.getClass().getName(),
                notificationManager.getClass().getName(),
                prefs.getClass().getName(),
                repository.getClass().getName()));
        this.context = context;
        appName = context.getString(R.string.app_name);
        this.notificationManager = notificationManager;
        this.alarmManager = alarmManager;
        this.prefs = prefs;
        this.repository = repository;
    }

    /**
     * Main entry point of the worker: read all pending alarms, show
     * any pending notifications, and schedule the next time this
     * worker should run.
     */
    @Override
    @NonNull
    public Result doWork() {
        Log.d(TAG, ".doWork");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // If the user has disabled notifications, don't bother.
            if (!notificationManager.areNotificationsEnabled()) {
                Log.i(TAG, "User has disabled notifications;"
                        + " any pending alarms will be ignored.");
                return Result.success();
            }
        }
        StringEncryption encryptor = null;
        try {
            repository.open(context);
            SortedSet<AlarmInfo> pendingAlarms =
                    repository.getPendingAlarms(prefs.getTimeZone());
            if (pendingAlarms.isEmpty()) {
                Log.i(TAG, "No To Do alarms are pending");
                return Result.success();
            }

            // Gather current preferences and other constants
            // used for all notifications
            final boolean showPrivate = prefs.showPrivate();
            final boolean showEncrypted = prefs.showEncrypted();
            final boolean doVibrate = prefs.notificationVibrate();
            final long soundID = prefs.getNotificationSound();
            encryptor = showEncrypted
                    ? StringEncryption.holdGlobalEncryption() : null;
            final Instant now = Instant.now();
            final LocalDate today = LocalDate.now(prefs.getTimeZone());

            // Alarm items MUST be removed from the SortedSet as they
            // are processed, since updating their notification time
            // will affect their sort order.
            Iterator<AlarmInfo> iterator = pendingAlarms.iterator();
            List<AlarmInfo> alarmsProcessed = new ArrayList<>();
            while (iterator.hasNext()) {
                AlarmInfo alarm = iterator.next();
                if (alarm.getNextAlarmTime().toInstant().isBefore(now)) {
                    // This item's alarm is due.
                    iterator.remove();
                    postNotification(alarm, now, today, doVibrate, soundID,
                            showPrivate, showEncrypted, encryptor);
                    repository.updateAlarmNotificationTime(
                            alarm.getId(), now);
                    alarmsProcessed.add(alarm);
                }
            }

            if (alarmsProcessed.isEmpty())
                Log.d(TAG, "No pending alarms are due yet");
            else
                // Re-insert the processed notifications in the sorted set
                pendingAlarms.addAll(alarmsProcessed);

            /*
             * Schedule an alarm for the next notification time;
             * as this is the natural ordering of AlarmInfo,
             * we just need to look at the first alarm in the set.
             */
            // FIXME: Replace the receiver
            Intent intent = new Intent(context, AlarmInitReceiver.class);
            intent.setAction(ACTION_TRIGGER_ALARM);
            PendingIntent sender = PendingIntent.getBroadcast(context, 0,
                    intent, PendingIntent.FLAG_IMMUTABLE |
                            PendingIntent.FLAG_UPDATE_CURRENT);
            if (pendingAlarms.isEmpty()) {
                Log.d(TAG, "No To Do alarms are pending;"
                        + " cancelling any intended alarm");
                alarmManager.cancel(sender);
            } else {
                ZonedDateTime nextAlarm = pendingAlarms.first()
                        .getNextAlarmTime();
                // To avoid potentially spamming the user with successive
                // notifications, if the next alarm would be less than
                // a minute from now, delay it until the minute is up.
                if (nextAlarm.toInstant().isBefore(now.plusSeconds(60)))
                    nextAlarm = now.plusSeconds(60)
                            .atZone(nextAlarm.getZone());

                Log.d(TAG, String.format("%d To Do alarms are pending;"
                                + " scheduling an alarm for the next item at %s",
                        pendingAlarms.size(), nextAlarm.format(
                                DateTimeFormatter.ISO_ZONED_DATE_TIME)));
                alarmManager.set(AlarmManager.RTC_WAKEUP,
                        nextAlarm.toInstant().toEpochMilli(), sender);
            }
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Alarm work failed", e);
            return Result.failure();
        } finally {
            if (encryptor != null)
                StringEncryption.releaseGlobalEncryption(context);
            repository.release(context);
        }
    }

    /**
     * Create and post a notification for a given due item.
     * This handles differences between API levels.
     *
     * @param alarm the alarm item.  Its notification time will be updated,
     *        so this <i>must not</i> be present in any {@link Set} the
     *        caller is iterating over.
     * @param now the current time, as of when we started
     *        looping through the notifications
     * @param today the current date with respect to the preferred time zone
     * @param doVibrate whether to vibrate when posting the notification
     *        (Nougat and earlier)
     * @param soundID the ID of the notification sound to play
     *        (Nougat and earlier)
     * @param showPrivate whether private records are shown.  If not,
     *        the notification will show &ldquo;[Private]&rdquo; instead
     *        of the item&rsquo;s description.
     * @param showEncrypted whether encrypted records are shown.  If not,
     *        the notification will show &ldquo;[Locked]&rdquo; (if
     *        {@code showPrivate} is {@code true}) instead of the
     *        item&rsquo;s description.  The notification may still show
     *        &ldquo;[Locked]&rdquo; if we are unable to decrypt the item.
     * @param encryptor the {@link StringEncryption} service we use to
     *        decrypt encrypted records.
     */
    private void postNotification(
            AlarmInfo alarm, Instant now, LocalDate today,
            boolean doVibrate, long soundID,
            boolean showPrivate, boolean showEncrypted,
            StringEncryption encryptor) {

        Log.d(TAG, String.format(
                ".postNotification(item#%d)", alarm.getId()));
        String category = alarm.getCategory();
        String title;
        if (category == null)
            title = appName;
        else
            title = String.format("%s (%s)", appName, category);

        String descr;
        if (!alarm.isEncrypted()) {
            if (alarm.isPrivate() && !showPrivate)
                descr = context.getString(R.string.NotificationFormatPrivate);
            else
                descr = alarm.getDescription();
        } else { // (item.privacy > 1)
            if (showPrivate) {
                if (showEncrypted) {
                    try {
                        descr = encryptor.decrypt(
                                alarm.getEncryptedDescription());
                    } catch (GeneralSecurityException gsx) {
                        descr = context.getString(
                                R.string.NotificationFormatEncrypted);
                    }
                } else {
                    descr = context.getString(
                            R.string.NotificationFormatEncrypted);
                }
            } else {
                descr = context.getString(R.string.NotificationFormatPrivate);
            }
        }

        // Configure acknowledgements to be sent to the ToDoListActivity
        // along with the associated item's ID and category.
        Intent mainIntent = new Intent(context, ToDoListActivity.class);
       	mainIntent.setAction(ACTION_NOTIFICATION_ACK);
        mainIntent.setData(ToDoItemColumns.CONTENT_URI);
        mainIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
       	mainIntent.putExtra(EXTRA_NOTIFICATION_DATE, now.toEpochMilli());
        mainIntent.putExtra(EXTRA_ITEM_CATEGORY_ID, alarm.getCategoryId());
        mainIntent.putExtra(EXTRA_ITEM_ID, alarm.getId());

        int defaultFlags = Notification.DEFAULT_LIGHTS;
        if (doVibrate) defaultFlags |= Notification.DEFAULT_VIBRATE;
        boolean isOverdue = alarm.getDueDate().isBefore(today);

        /*
         * IMPORTANT!  Each PendingIntent must have a unique requestCode
         * corresponding to the main Intent that it holds; otherwise
         * the extra data for later notifications (i.e. the item it
         * belongs to) will overwrite the extra for earlier ones.
         * We still use FLAG_UPDATE_CURRENT in case we do raise an alarm
         * for an item where the user hasn't cleared the previous notice.
         */
        PendingIntent intent = PendingIntent.getActivity(context,
                (int) alarm.getId(), mainIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notice;

        Log.d(TAG, String.format(".postNotification(\"%s\" (%d), \"%s\" (%d), \"%s\", %s)",
                title, alarm.getCategoryId(), descr, alarm.getId(),
                alarm.getDueDate().format(DateTimeFormatter.ISO_DATE),
                isOverdue ? "overdue" : "normal"));

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            NotificationCompat.Builder builder =
                    new NotificationCompat.Builder(context)
                            .setSmallIcon(R.drawable.stat_todo)
                            .setContentTitle(title)
                            .setContentText(descr)
                            .setContentIntent(intent)
                            .setDefaults(defaultFlags)
                            .setOnlyAlertOnce(true)
                            .setTicker(descr)
                            .setWhen(alarm.getDueTime()
                                    .toInstant().toEpochMilli());
            if (soundID >= 0)
                builder = builder.setSound(Uri.withAppendedPath(
                        Media.INTERNAL_CONTENT_URI, Long.toString(soundID)));
            notice = builder.build();
        }
        else {
            Notification.Builder builder;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) { // KitKat through Nougat
                builder = new Notification.Builder(context)
                        .setDefaults(defaultFlags)
                        .setPriority(isOverdue ? Notification.PRIORITY_HIGH
                                : Notification.PRIORITY_DEFAULT);
                if (soundID >= 0)
                    builder = builder.setSound(Uri.withAppendedPath(
                            Media.INTERNAL_CONTENT_URI, Long.toString(soundID)));
            } else { // Oreo and up
                builder = new Notification.Builder(context,
                        isOverdue ? OVERDUE_CHANNEL_ID : ALMOST_DUE_CHANNEL_ID);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                builder = builder.setShowWhen(true);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                builder = builder.setGroup(String.format(Locale.US,
                        NOTIFICATION_GROUP_FORMAT, alarm.getCategoryId()));
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
                if (!alarm.isPrivate()) {
                    builder = builder.setVisibility(Notification.VISIBILITY_PUBLIC);
                } else if (!alarm.isEncrypted()) {
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
                    .setWhen(alarm.getDueTime()
                            .toInstant().toEpochMilli());
            notice = builder.build();
        }

        // We have to narrow the item ID to fit a notification ID;
        // hope we don't have any collisions.  (Shouldn't happen
        // on a reasonably-sized database of items.)
        notificationManager.notify((int) alarm.getId(), notice);
        alarm.setNotificationTime(now);

    }

    /**
     * Return a notification of this worker when it&rsquo;s run
     * in the foreground.
     */
    @Override
    @NonNull
    public ListenableFuture<ForegroundInfo> getForegroundInfoAsync() {
        Log.d(TAG, ".getForegroundInfoAsync");
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder = new Notification.Builder(context);
        } else {
            builder = new Notification.Builder(context, SILENT_CHANNEL_ID);
        }
        Notification busyNotification = builder
                .setSmallIcon(R.drawable.stat_todo)
                .setContentText(context.getString(R.string.app_name))
                .setContentText(context.getString(
                        R.string.AlarmServiceBackgroundMessage))
                .setOnlyAlertOnce(true)
                .build();
        ForegroundInfo info = new ForegroundInfo(
                FG_NOTIFICATION_ID, busyNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        SettableFuture<ForegroundInfo> future = SettableFuture.create();
        future.set(info);
        return future;
    }

}
