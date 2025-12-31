package com.mine.autosleep;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.v4.app.JobIntentService;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

public class AutoSleepService extends JobIntentService {
    private static final String TAG = "AutoSleepService";

    static void enqueue(Context context, Intent work) {
        enqueueWork(context, AutoSleepService.class, Constants.JOB_ID, work);
    }

    @Override
    protected void onHandleWork(Intent intent) {
        if (intent == null) return;

        int id = intent.getIntExtra(Constants.ID, 0);
        Log.d(TAG, "onHandleWork id=" + id);

        if (id == Constants.ID_ENABLE) {
            SleepController.enterSleep(this);

            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            if (sp.getBoolean("notification_sleep_started", true)) {
                sendPersistentSleepNotification(sp.getString(Constants.DISABLE_SLEEP_TIME, "08:00"));
            }

        } else if (id == Constants.ID_DISABLE) {
            SleepController.exitSleep(this);

            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(Constants.NOTIF_ID_SLEEP);

            // Keep schedule enabled (Option A): reschedule next cycle if app is enabled [6](https://extremenetworks2com-my.sharepoint.com/personal/akoryakin_extremenetworks_com/Documents/Microsoft%20Copilot%20Chat%20Files/AndroidManifest.xml.java)
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            if (sp.getBoolean(Constants.APP_IS_ENABLED, false)) {
                AlarmBroadcastReceiver r = new AlarmBroadcastReceiver();
                r.setAlarms(getApplicationContext());
            }
        }
    }

    private void sendPersistentSleepNotification(String endOfSleep) {
        createNotificationChannelIfNeeded(); // required on API 26+ when targeting 26+ [5](https://github.com/tlredz/Scripts)[6](https://extremenetworks2com-my.sharepoint.com/personal/akoryakin_extremenetworks_com/Documents/Microsoft%20Copilot%20Chat%20Files/AndroidManifest.xml.java)

        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent exitNow = new Intent(this, ExitNowReceiver.class);
        exitNow.setAction(Constants.ACTION_EXIT_NOW);
        PendingIntent exitNowPi = PendingIntent.getBroadcast(
                this,
                1,
                exitNow,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, Constants.NOTIF_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_moon)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(String.format(getString(R.string.notification_content), endOfSleep))
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .addAction(0, getString(R.string.exit_now), exitNowPi);

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(Constants.NOTIF_ID_SLEEP, b.build());
    }

    private void createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            NotificationChannel ch = new NotificationChannel(
                    Constants.NOTIF_CHANNEL_ID,
                    getString(R.string.notification_title),
                    NotificationManager.IMPORTANCE_LOW
            );
            nm.createNotificationChannel(ch);
        }
    }
}