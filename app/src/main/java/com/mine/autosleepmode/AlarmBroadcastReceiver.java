package com.mine.autosleepmode;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class AlarmBroadcastReceiver extends WakefulBroadcastReceiver
{
    private static final String TAG = "AlarmBroadcastReceiver";
    private AlarmManager alarmManager;
    private PendingIntent enableSleepModePendingIntent;
    private PendingIntent disableSleepModePendingIntent;
    private static final SimpleDateFormat SDF_1 = new SimpleDateFormat("dd/MM", Locale.getDefault());
    private static final SimpleDateFormat SDF_2 = new SimpleDateFormat("HH:mm", Locale.getDefault());

    @Override
    public void onReceive(Context context, Intent intent)
    {
        Log.d(TAG, "onReceive");
        if (intent == null) {
            Log.d(TAG, "intent shouldn't be null!");
            return;
        }

        Intent service = new Intent(context, AutoSleepModeService.class);
        int id = intent.getIntExtra(Constants.ID, 0);
        if (id == Constants.ID_ENABLE) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            service.putExtra(Constants.END, settings.getString(Constants.DISABLE_SLEEP_TIME, "08:00"));
        }
        service.putExtra(Constants.ID, id);
        startWakefulService(context, service);
    }

    public void setAlarmDisableSleepMode(Context context)
    {
        Log.d(TAG, "setAlarmDisableSleepMode");
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        String disableAutoSleepMode = settings.getString(Constants.DISABLE_SLEEP_TIME, "08:00");

        String[] disable = disableAutoSleepMode.split(":");

        Calendar now = Calendar.getInstance();
        Calendar calendarEnd = Calendar.getInstance();
        now.setTimeInMillis(System.currentTimeMillis());
        calendarEnd.setTimeInMillis(now.getTimeInMillis());

        calendarEnd.set(Calendar.HOUR_OF_DAY, Integer.valueOf(disable[0]));
        calendarEnd.set(Calendar.MINUTE, Integer.valueOf(disable[1]));
        calendarEnd.set(Calendar.SECOND, 0);
        calendarEnd.set(Calendar.MILLISECOND, 0);

        Intent intentDisable = new Intent(context, AlarmBroadcastReceiver.class);
        intentDisable.putExtra(Constants.ID, Constants.ID_DISABLE);

        disableSleepModePendingIntent = PendingIntent.getBroadcast(context, Constants.ID_DISABLE, intentDisable, 0);
        alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendarEnd.getTimeInMillis(), disableSleepModePendingIntent);
        setAlarmAfterReboot(context, true);
    }

    public void setAlarmEnableSleepMode(Context context)
    {
        Log.d(TAG, "setAlarmEnableSleepMode");
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        String enableAutoSleepMode = settings.getString(Constants.ENABLE_SLEEP_TIME, "23:00");

        String[] enable = enableAutoSleepMode.split(":");

        Calendar now = Calendar.getInstance();
        Calendar calendarStart = Calendar.getInstance();
        now.setTimeInMillis(System.currentTimeMillis());
        calendarStart.setTimeInMillis(now.getTimeInMillis());

        calendarStart.set(Calendar.HOUR_OF_DAY, Integer.valueOf(enable[0]));
        calendarStart.set(Calendar.MINUTE, Integer.valueOf(enable[1]));
        calendarStart.set(Calendar.SECOND, 0);
        calendarStart.set(Calendar.MILLISECOND, 0);

        Intent intentEnable = new Intent(context, AlarmBroadcastReceiver.class);
        intentEnable.putExtra(Constants.ID, Constants.ID_ENABLE);

        enableSleepModePendingIntent = PendingIntent.getBroadcast(context, Constants.ID_ENABLE, intentEnable, 0);
        alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendarStart.getTimeInMillis(), enableSleepModePendingIntent);
        setAlarmAfterReboot(context, true);
    }


    private boolean getNextScheduledDay(Context context, Calendar now, Calendar start, Calendar end) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        boolean checked = false;
        for (int i = 0; i < 7; i++) {
            checked = checked || settings.getBoolean("switch_" + String.valueOf(i), true);
        }
        if (!checked) {
            return false;
        }

        int WEEK = 7;
        int[] days = new int[WEEK];
        for (int i = 0; i < WEEK; i++) {
            int dow = now.get(Calendar.DAY_OF_WEEK);
            if (now.before(start) || (now.after(start) && now.before(end))) {
                days[i] = (dow + i) % 7;
            } else {
                days[i] = (dow + 1 + i) % 7;
            }
            if (days[i] == 0) {
                days[i] = WEEK;
            }
        }

        int dow = Calendar.MONDAY;
        for (int i = 0; i < WEEK; i++) {
            dow = days[i];
            if (settings.getBoolean("switch_" + String.valueOf(dow), true)) {
                break;
            }
        }

        int s = start.get(Calendar.DAY_OF_WEEK);
        int diff = Math.abs(dow - s);
        start.add(Calendar.DATE, diff);
        end.add(Calendar.DATE, diff);

        SimpleDateFormat sdf = new SimpleDateFormat("E, dd/MM HH:mm", Locale.getDefault());
        Log.d(TAG, sdf.format(start.getTime()));
        Log.d(TAG, sdf.format(end.getTime()));
        return true;
    }

    public void cancelAlarm(Context context, int alarmType)
    {
        Log.d(TAG, "cancelAlarms");
        if (alarmManager != null) {
            if (alarmType == Constants.ID_ENABLE && enableSleepModePendingIntent != null) {
                alarmManager.cancel(enableSleepModePendingIntent);
            } else if (alarmType == Constants.ID_DISABLE && disableSleepModePendingIntent != null) {
                alarmManager.cancel(disableSleepModePendingIntent);
            }
        }
        setAlarmAfterReboot(context, false);
    }

    private void setAlarmAfterReboot(Context context, boolean keep) {
        ComponentName receiver = new ComponentName(context, BootReceiver.class);
        PackageManager pm = context.getPackageManager();
        if (keep) {
            pm.setComponentEnabledSetting(receiver, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        } else {
            pm.setComponentEnabledSetting(receiver, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        }
    }
}
