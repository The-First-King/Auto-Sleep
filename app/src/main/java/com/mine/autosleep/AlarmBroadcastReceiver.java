package com.mine.autosleep;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class AlarmBroadcastReceiver extends android.content.BroadcastReceiver {

    private AlarmManager alarmManager;
    private static final SimpleDateFormat SDF_1 = new SimpleDateFormat("dd/MM", Locale.getDefault());
    private static final SimpleDateFormat SDF_2 = new SimpleDateFormat("HH:mm", Locale.getDefault());

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        int id = intent.getIntExtra(Constants.ID, 0);

        Intent work = new Intent(context, AutoSleepService.class);
        work.putExtra(Constants.ID, id);

        if (id == Constants.ID_ENABLE) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            work.putExtra(Constants.END, settings.getString(Constants.DISABLE_SLEEP_TIME, "08:00"));
        }

        AutoSleepService.enqueue(context, work);
    }

    public String setAlarms(Context context) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        String enableAutoSleep = settings.getString(Constants.ENABLE_SLEEP_TIME, "23:00");
        String disableAutoSleep = settings.getString(Constants.DISABLE_SLEEP_TIME, "08:00");

        String[] enable = enableAutoSleep.split(":");
        String[] disable = disableAutoSleep.split(":");

        Calendar now = Calendar.getInstance();
        Calendar calendarStart = Calendar.getInstance();
        Calendar calendarEnd = Calendar.getInstance();

        calendarStart.set(Calendar.HOUR_OF_DAY, Integer.valueOf(enable[0]));
        calendarStart.set(Calendar.MINUTE, Integer.valueOf(enable[1]));
        calendarStart.set(Calendar.SECOND, 0);
        calendarStart.set(Calendar.MILLISECOND, 0);

        calendarEnd.set(Calendar.HOUR_OF_DAY, Integer.valueOf(disable[0]));
        calendarEnd.set(Calendar.MINUTE, Integer.valueOf(disable[1]));
        calendarEnd.set(Calendar.SECOND, 0);
        calendarEnd.set(Calendar.MILLISECOND, 0);

        if (!findNextStartDay(context, now, calendarStart)) {
            return "No day was checked in Settings";
        }

        boolean endOnNextDay = settings.getBoolean(Constants.START_ON_NEXT_DAY, false);

        calendarEnd.set(Calendar.YEAR, calendarStart.get(Calendar.YEAR));
        calendarEnd.set(Calendar.DAY_OF_YEAR, calendarStart.get(Calendar.DAY_OF_YEAR));

        if (endOnNextDay) {
            calendarEnd.add(Calendar.DATE, 1);
        } else if (calendarEnd.before(calendarStart)) {
            calendarEnd.add(Calendar.DATE, 1);
        }

        Intent intentEnable = new Intent(context, AlarmBroadcastReceiver.class);
        intentEnable.putExtra(Constants.ID, Constants.ID_ENABLE);

        Intent intentDisable = new Intent(context, AlarmBroadcastReceiver.class);
        intentDisable.putExtra(Constants.ID, Constants.ID_DISABLE);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

        PendingIntent piEnable = PendingIntent.getBroadcast(context, Constants.ID_ENABLE, intentEnable, flags);
        PendingIntent piDisable = PendingIntent.getBroadcast(context, Constants.ID_DISABLE, intentDisable, flags);

        alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendarStart.getTimeInMillis(), piEnable);
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendarEnd.getTimeInMillis(), piDisable);

        setAlarmAfterReboot(context, true);

        if (now.get(Calendar.DAY_OF_YEAR) == calendarStart.get(Calendar.DAY_OF_YEAR)) {
            return context.getString(R.string.toast_next_sleep_today) + " " + SDF_2.format(calendarStart.getTime());
        } else {
            return "Next sleep on " + SDF_1.format(calendarStart.getTime()) + " at " + SDF_2.format(calendarStart.getTime());
        }
    }

    private boolean findNextStartDay(Context context, Calendar now, Calendar start) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        for (int i = 0; i < 7; i++) {
            Calendar checkCycle = (Calendar) start.clone();
            checkCycle.add(Calendar.DATE, i);
            if (i == 0 && now.after(start)) continue;

            int dayOfWeek = checkCycle.get(Calendar.DAY_OF_WEEK);
            if (settings.getBoolean("switch_" + dayOfWeek, true)) {
                start.add(Calendar.DATE, i);
                return true;
            }
        }
        return false;
    }

    public void cancelAlarms(Context context) {
        alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        Intent intentEnable = new Intent(context, AlarmBroadcastReceiver.class);
        Intent intentDisable = new Intent(context, AlarmBroadcastReceiver.class);

        PendingIntent piEnable = PendingIntent.getBroadcast(context, Constants.ID_ENABLE, intentEnable, PendingIntent.FLAG_IMMUTABLE);
        PendingIntent piDisable = PendingIntent.getBroadcast(context, Constants.ID_DISABLE, intentDisable, PendingIntent.FLAG_IMMUTABLE);

        if (alarmManager != null) {
            alarmManager.cancel(piEnable);
            alarmManager.cancel(piDisable);
        }

        setAlarmAfterReboot(context, false);
    }

    private void setAlarmAfterReboot(Context context, boolean keep) {
        ComponentName receiver = new ComponentName(context, BootReceiver.class);
        PackageManager pm = context.getPackageManager();

        pm.setComponentEnabledSetting(
                receiver,
                keep ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
        );
       }
}
