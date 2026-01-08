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

import android.content.*;
import android.util.Log;

import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import com.xmission.trevin.android.todo.service.AlarmWorker;

/**
 * Pass system broadcast events to the AlarmService.
 *
 * @author Trevin Beattie
 */
public class AlarmInitReceiver extends BroadcastReceiver {

    private static final String TAG = "AlarmInitReceiver";

    private WorkManager workManager;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive(action=" + intent.getAction()
                + ", package=" + intent.getPackage()
                + ", data=" + intent.getDataString() + ")");
        if (workManager == null)
            workManager = WorkManager.getInstance(context);
        WorkRequest req = new OneTimeWorkRequest
                .Builder(AlarmWorker.class)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag(intent.getAction())
                .build();
        workManager.enqueue(req);
    }
}
