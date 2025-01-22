/*
 * $Id: AlarmInitReceiver.java,v 1.3 2011/07/18 00:47:37 trevin Exp trevin $
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
 * $Log: AlarmInitReceiver.java,v $
 * Revision 1.3  2011/07/18 00:47:37  trevin
 * Added the copyright notice
 *
 * Revision 1.2  2011/05/16 02:29:50  trevin
 * Restrict the action to simply passing the broadcast to the AlarmService.
 *
 * Revision 1.1  2011/03/08 05:40:24  trevin
 * Initial revision
 *
 */
package com.xmission.trevin.android.todo;

import android.content.*;
import android.util.Log;

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
	context.startService(alarmIntent);
    }
}
