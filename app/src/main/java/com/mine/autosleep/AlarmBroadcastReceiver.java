package com.mine.autosleep;

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
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class AlarmBroadcastReceiver extends WakefulBroadcastReceiver
{
    private static final String TAG = "AlarmBroadcastReceiver";

    private AlarmManager alarmManager;
    private PendingIntent enableSleepPendingIntent;
    private PendingIntent disableSleepPendingIntent;

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

        Intent service = new Intent(context, AutoSleepService.class);
        int id = intent.getIntExtra(Constants.ID, 0);
        if (id == Constants.ID_ENABLE) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            service.putExtra(Constants.END, settings.getString(Constants.DISABLE_SLEEP_TIME, "08:00"));
        }
        service.putExtra(Constants.ID, id);
        startWakefulService(context, service);
    }

    public String setAlarms(Context context)
    {
        Log.d(TAG, "setAlarms");
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

        if (calendarStart.after(calendarEnd)) {
            calendarEnd.add(Calendar.DATE, 1);
        }

        if (!getNextScheduledDay(context, now, calendarStart, calendarEnd)) {
            Toast.makeText(context, "No day was checked in Settings, so Auto Sleep mode cannot be scheduled", Toast.LENGTH_LONG).show();
            return "";
        }

        Intent intentEnable = new Intent(context, AlarmBroadcastReceiver.class);
        intentEnable.setClass(context, AlarmBroadcastReceiver.class);
        Intent intentDisable = new Intent(context, AlarmBroadcastReceiver.class);
        intentDisable.setClass(context, AlarmBroadcastReceiver.class);

        intentEnable.putExtra(Constants.ID, Constants.ID_ENABLE);
        intentDisable.putExtra(Constants.ID, Constants.ID_DISABLE);

        // enableSleepPendingIntent = PendingIntent.getBroadcast(context, Constants.ID_ENABLE, intentEnable, 0);
        // disableSleepPendingIntent = PendingIntent.getBroadcast(context, Constants.ID_DISABLE, intentDisable, 0);
        enableSleepPendingIntent = PendingIntent.getBroadcast(context, Constants.ID_ENABLE, intentEnable, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        disableSleepPendingIntent = PendingIntent.getBroadcast(context, Constants.ID_DISABLE, intentDisable, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendarStart.getTimeInMillis(), enableSleepPendingIntent);
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendarEnd.getTimeInMillis(), disableSleepPendingIntent);
        setAlarmAfterReboot(context, true);

        String message;
        if (now.get(Calendar.DATE) == calendarStart.get(Calendar.DATE)) {
            message = context.getString(R.string.toast_next_sleep_today);
        } else {
            long diff = (calendarStart.getTimeInMillis() - now.getTimeInMillis()) / (24 * 60 * 60 * 1000);
            if (diff < 1) { // Same logical day but technically tomorrow morning
                message = context.getString(R.string.toast_next_sleep_tomorrow);
            } else {
                message = String.format(context.getString(R.string.toast_next_sleep_later),
                        SDF_1.format(calendarStart.getTime()),
                        SDF_2.format(calendarStart.getTime()));
            }
        }
        return message;
    }

    private boolean getNextScheduledDay(Context context, Calendar now, Calendar start, Calendar end) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        boolean checked = false;
        for (int i = 1; i <= 7; i++) {
            checked = checked || settings.getBoolean("switch_" + String.valueOf(i), true);
        }
        if (!checked) {
            return false;
        }

        // NEW: Check the Radio Button selection from MainActivity
        boolean startOnNextDay = settings.getBoolean(Constants.START_ON_NEXT_DAY, false);

        int WEEK = 7;
        int[] days = new int[WEEK];
        for (int i = 0; i < WEEK; i++) {
            int dow = now.get(Calendar.DAY_OF_WEEK);
            
            // Logic change: If "Next Day" is selected, we force the start day to be tomorrow (dow + 1)
            if (startOnNextDay) {
                days[i] = (dow + 1 + i) % 7;
            } else {
                // If "Today" is selected, check if time has passed
                if (now.before(start)) {
                    days[i] = (dow + i) % 7;
                } else {
                    days[i] = (dow + 1 + i) % 7;
                }
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
        // Calculate the difference in days correctly
        int diff = (dow - s + 7) % 7;
        
        // If "Tomorrow" was selected and diff is 0, it means the next available 
        // day in settings is today, but we must move it to next week.
        if (startOnNextDay && diff == 0) {
            diff = 7;
        }

        start.add(Calendar.DATE, diff);
        end.add(Calendar.DATE, diff);

        SimpleDateFormat sdf = new SimpleDateFormat("E, dd/MM HH:mm", Locale.getDefault());
        Log.d(TAG, "Scheduled Start: " + sdf.format(start.getTime()));
        Log.d(TAG, "Scheduled End: " + sdf.format(end.getTime()));
        return true;
    }

    public void cancelAlarms(Context context)
    {
        Log.d(TAG, "cancelAlarms");
        alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intentEnable = new Intent(context, AlarmBroadcastReceiver.class);
        Intent intentDisable = new Intent(context, AlarmBroadcastReceiver.class);
        
        PendingIntent piEnable = PendingIntent.getBroadcast(context, Constants.ID_ENABLE, intentEnable, 0);
        PendingIntent piDisable = PendingIntent.getBroadcast(context, Constants.ID_DISABLE, intentDisable, 0);
        
        if (alarmManager != null) {
            alarmManager.cancel(piEnable);
            alarmManager.cancel(piDisable);
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
