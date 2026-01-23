package com.mine.autosleep;

import android.app.AlarmManager;
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
        String origin = intent.getStringExtra(Constants.ORIGIN);
        Log.d(TAG, "onHandleWork id=" + id + " origin=" + origin);

        if (id == Constants.ID_ENABLE) {
            SleepController.enterSleep(this);

            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            boolean serviceEnabled = sp.getBoolean(Constants.APP_IS_ENABLED, false);

            if (serviceEnabled) {
                long exitAt = computeNextEndEpoch(this);
                ensureExitAlarm(this, exitAt);
                sp.edit().putLong(Constants.PREF_SCHEDULED_EXIT_AT, exitAt).apply();

                if (sp.getBoolean("notification_sleep_started", true)) {
                    sendPersistentSleepNotification(exitAt);
                }
            } else {
                // Service status OFF → manual/indefinite sleep
                cancelExitAlarm(this);
                sp.edit().remove(Constants.PREF_SCHEDULED_EXIT_AT).apply();

                if (sp.getBoolean("notification_sleep_started", true)) {
                    sendPersistentSleepNotificationIndefinite();
                }
            }

        } else if (id == Constants.ID_DISABLE) {
            // Always exit sleep (restores radios/airplane/Doze)
            SleepController.exitSleep(this);

            // Clear the ongoing notification
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(Constants.NOTIF_ID_SLEEP);

            // Clear persisted exit epoch
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            sp.edit().remove(Constants.PREF_SCHEDULED_EXIT_AT).apply();

            // Only reschedule the next cycle when this disable came from the alarm
            if (Constants.ORIGIN_ALARM.equals(origin) && sp.getBoolean(Constants.APP_IS_ENABLED, false)) {
                AlarmBroadcastReceiver r = new AlarmBroadcastReceiver();
                r.setAlarms(getApplicationContext());
            }
        }
    }

    private long computeNextEndEpoch(Context ctx) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
        String endStr = sp.getString(Constants.DISABLE_SLEEP_TIME, "08:00");

        int hh = 8, mm = 0;
        try {
            String[] parts = endStr.split(":");
            hh = Integer.parseInt(parts[0]);
            mm = Integer.parseInt(parts[1]);
        } catch (Throwable ignored) {}

        java.util.Calendar now = java.util.Calendar.getInstance();
        java.util.Calendar end = (java.util.Calendar) now.clone();
        end.set(java.util.Calendar.HOUR_OF_DAY, hh);
        end.set(java.util.Calendar.MINUTE, mm);
        end.set(java.util.Calendar.SECOND, 0);
        end.set(java.util.Calendar.MILLISECOND, 0);

        if (end.getTimeInMillis() <= now.getTimeInMillis()) {
            end.add(java.util.Calendar.DATE, 1);
        }
        return end.getTimeInMillis();
    }

    private void ensureExitAlarm(Context ctx, long triggerAtMillis) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);

        Intent intentDisable = new Intent(ctx, AlarmBroadcastReceiver.class);
        intentDisable.putExtra(Constants.ID, Constants.ID_DISABLE);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent piDisable = PendingIntent.getBroadcast(ctx, Constants.ID_DISABLE, intentDisable, flags);

        if (am != null) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, piDisable);
        }
    }

    private void cancelExitAlarm(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intentDisable = new Intent(ctx, AlarmBroadcastReceiver.class);
        intentDisable.putExtra(Constants.ID, Constants.ID_DISABLE);
        PendingIntent piDisable = PendingIntent.getBroadcast(ctx, Constants.ID_DISABLE, intentDisable, PendingIntent.FLAG_IMMUTABLE);
        am.cancel(piDisable);
    }

    private void sendPersistentSleepNotification(long exitEpoch) {
        createNotificationChannelIfNeeded();

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
        String endOfSleep = sdf.format(new java.util.Date(exitEpoch));

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
                .setSmallIcon(R.drawable.ic_stat_autosleep)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(String.format(getString(R.string.notification_content), endOfSleep))
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .addAction(0, getString(R.string.exit_now), exitNowPi);

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(Constants.NOTIF_ID_SLEEP, b.build());
    }

    private void sendPersistentSleepNotificationIndefinite() {
        createNotificationChannelIfNeeded();

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

        String content = "Until you turn it off";

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, Constants.NOTIF_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_autosleep)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(content)
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
