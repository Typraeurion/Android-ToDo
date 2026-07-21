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
package com.xmission.trevin.android.todo;

//import android.app.Application;
import android.app.Application;
import android.os.Build;
import android.util.Log;
import androidx.appcompat.app.AppCompatDelegate;

import com.xmission.trevin.android.todo.data.ToDoPreferences;
import com.xmission.trevin.android.todo.receiver.AlarmInitReceiver;

/**
 * Perform one-time initialization tasks for the To Do application.
 */
public class ToDoApplication extends Application {

    private static final String TAG = "ToDoApplication";

    @Override
    public void onCreate() {
        Log.d(TAG, ".onCreate");
        super.onCreate();

        // Skip initializing night mode if we're running instrumented tests
        // because using real preferences would interfere with mock preferences.
        try {
            Class.forName("androidx.test.InstrumentationRegistry");
            Log.d(TAG, "Instrumentation detected; skipping night mode initialization");
        } catch (ClassNotFoundException cx) {
            // Initialize night mode according to current preferences
            ToDoPreferences prefs = ToDoPreferences.getInstance(this);
            ToDoPreferences.UITheme userTheme = prefs.getUITheme();
            int nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            switch (userTheme) {
                case LIGHT:
                    nightMode = AppCompatDelegate.MODE_NIGHT_NO;
                    break;
                case DARK:
                    nightMode = AppCompatDelegate.MODE_NIGHT_YES;
                    break;
            }
            AppCompatDelegate.setDefaultNightMode(nightMode);

        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            /*
             * Oreo and up must use channels to send notifications;
             * we have to set this up in a separate class because
             * Dalvik will refuse to load the application when it
             * can&rsquo;t find the {@link NotificationChannel} class.
             */
            AlarmInitReceiver.onCreate(this);
        }
    }

    @Override
    public void onLowMemory() {
        Log.d(TAG, ".onLowMemory");
        super.onLowMemory();
    }

    @Override
    public void onTerminate() {
        Log.d(TAG, ".onTerminate");
        super.onTerminate();
    }

}
