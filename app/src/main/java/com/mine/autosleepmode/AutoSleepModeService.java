package com.mine.autosleepmode;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.IOException;

public class AutoSleepModeService extends IntentService
{
    private static final String TAG = "AutoSleepModeService";
    private static final String COMMAND_FLIGHT_MODE_1 = "settings put global sleep_mode_on ";
    private static final String COMMAND_FLIGHT_MODE_2 = "am broadcast -a android.intent.action.SLEEP_MODE --ez state ";

    public AutoSleepModeService() {
        super("SchedulingService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(TAG, "onHandleIntent");

        int id = intent.getIntExtra(Constants.ID, 0);
        if (toggleSleepMode(id)) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            if (settings.getBoolean("notification_sleep_mode_started", true)) {
                sendNotificationWhenSleepIsEnabled(intent.getStringExtra(Constants.END));
            }
        } else {
            NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            notificationManager.cancel(0);
            Log.d(TAG, "sleep mode is going off -> scheduling new alarm!");
            AlarmBroadcastReceiver r = new AlarmBroadcastReceiver();
            r.setAlarms(getApplicationContext());
        }
        AlarmBroadcastReceiver.completeWakefulIntent(intent);
    }

    private void sendNotificationWhenSleepIsEnabled(String endOfSleepMode) {
        Log.d(TAG, "sendNotificationWhenSleepIsEnabled");
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0);
        Notification.Builder builder = new Notification.Builder(this);
        builder.setContentTitle(getString(R.string.notification_title));
        builder.setContentText(String.format(getString(R.string.notification_content),
                endOfSleepMode));
        builder.setSmallIcon(R.drawable.ic_moon);
        builder.setContentIntent(contentIntent);
        builder.setAutoCancel(true);

        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(0, builder.build());
    }

    private boolean toggleSleepMode(int id) {
        Log.d(TAG, "toggleSleepMode");
        boolean enable = id == Constants.ID_ENABLE;
        String v = enable ? "1" : "0";
        String command = COMMAND_FLIGHT_MODE_1 + v;
        executeCommandWithoutWait(command);
        String command2 = COMMAND_FLIGHT_MODE_2 + enable;
        executeCommandWithoutWait(command2);
        Settings.Global.putInt(getApplicationContext().getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, enable ? 1 : 0);
        return enable;
    }

    private void executeCommandWithoutWait(String command) {
        Log.d(TAG, "executeCommandWithoutWait");
        try {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes(command + "\n");
            os.writeBytes("exit\n");
            os.flush();
            Log.d(TAG, command);
        } catch (IOException e) {
            Log.e(TAG, "su command has failed due to: " + e.fillInStackTrace());
        }
    }
}
