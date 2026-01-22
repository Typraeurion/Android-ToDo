/*
 * Copyright © 2011–2026 Trevin Beattie
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
package com.xmission.trevin.android.todo.receiver;

import static android.content.Context.NOTIFICATION_SERVICE;
import static com.xmission.trevin.android.todo.service.AlarmWorker.ALMOST_DUE_CHANNEL_ID;
import static com.xmission.trevin.android.todo.service.AlarmWorker.OVERDUE_CHANNEL_ID;
import static com.xmission.trevin.android.todo.service.AlarmWorker.SILENT_CHANNEL_ID;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.*;
import android.os.Build;
import android.util.Log;

import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import com.xmission.trevin.android.todo.R;
import com.xmission.trevin.android.todo.ToDoApplication;
import com.xmission.trevin.android.todo.service.AlarmWorker;

/**
 * Pass system broadcast events to the AlarmService.
 *
 * @author Trevin Beattie
 */
public class AlarmInitReceiver extends BroadcastReceiver {

    private static final String TAG = "AlarmInitReceiver";

    private WorkManager workManager;

    /**
     * Initialize the notification channels if needed.
     * {@link BroadcastReceiver}s don&rsquo;t inherently have an
     * {@code onCreate} method, but this is called from
     * {@link ToDoApplication#onCreate()} because on older devices
     * Dalvik will refuse to load the application when it can&rsquo;t
     * find the {@link NotificationChannel} class.
     */
    public static void onCreate(Context context) {
        Log.d(TAG, ".onCreate");
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return;
        // Oreo and up must use channels to send notifications
        NotificationManager notificationManager = (NotificationManager)
                context.getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel almostDueChannel = new NotificationChannel(
                ALMOST_DUE_CHANNEL_ID,
                context.getString(R.string.NotificationChannelDueName),
                NotificationManager.IMPORTANCE_DEFAULT);
        almostDueChannel.setDescription(context.getString(
                R.string.NotificationChannelDueDescription));
        NotificationChannel overdueChannel = new NotificationChannel(
                OVERDUE_CHANNEL_ID,
                context.getString(R.string.NotificationChannelOverdueName),
                NotificationManager.IMPORTANCE_HIGH);
        overdueChannel.setDescription(context.getString(
                R.string.NotificationChannelOverdueDescription));
        NotificationChannel silentChannel = new NotificationChannel(
                SILENT_CHANNEL_ID,
                context.getString(R.string.NotificationChannelSilentName),
                NotificationManager.IMPORTANCE_NONE);
        silentChannel.setDescription(context.getString(
                R.string.NotificationChannelSilentDescription));
        notificationManager.createNotificationChannel(almostDueChannel);
        notificationManager.createNotificationChannel(overdueChannel);
        notificationManager.createNotificationChannel(silentChannel);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, ".onReceive(action=" + intent.getAction()
                + ", package=" + intent.getPackage()
                + ", data=" + intent.getDataString() + ")");
        if (workManager == null)
            workManager = WorkManager.getInstance(context);

        /**
         * Check whether we are running in a test context.
         * If so, skip the alarm initialization which
         * would set up the normal repository before the tests
         * have a chance to substitute the mock repository.
         */
        try {
            Class.forName("androidx.test.platform.app.InstrumentationRegistry");
            Log.i(TAG, "Skipping alarm initialization in test context");
            return;
        } catch (ClassNotFoundException e) {
            // Probably not in a test context
        }

        WorkRequest req = new OneTimeWorkRequest
                .Builder(AlarmWorker.class)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag(intent.getAction())
                .build();
        workManager.enqueue(req);
    }
}
