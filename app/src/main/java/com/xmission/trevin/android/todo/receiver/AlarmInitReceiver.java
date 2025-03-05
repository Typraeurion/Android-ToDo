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
package com.xmission.trevin.android.todo.receiver;

import android.content.*;
import android.os.Build;
import android.util.Log;

import com.xmission.trevin.android.todo.service.AlarmService;

/**
 * Pass system broadcast events to the AlarmService.
 *
 * @author Trevin Beattie
 */
public class AlarmInitReceiver extends BroadcastReceiver {

    private static final String TAG = "AlarmInitReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
	Log.d(TAG, "onReceive(action=" + intent.getAction()
		+ ", package=" + intent.getPackage()
		+ ", data=" + intent.getDataString() + ")");
	Intent alarmIntent = new Intent(context, AlarmService.class);
	alarmIntent.setAction(intent.getAction());
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            context.startService(alarmIntent);
        } else {
            /*
             * Since API 26, Android does not permit starting a service
             * in the background. (#*&@!!)  Instead we have to start
             * the service in the foreground, AND the service has to
             * provide a foreground notification!!
             *
             * On top of that, as of API 31 apps are not allowed to
             * start foreground services either. >:^(
             */
            context.startForegroundService(alarmIntent);
        }
    }
}
