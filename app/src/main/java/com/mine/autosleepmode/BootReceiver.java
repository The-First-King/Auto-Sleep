package com.mine.autosleepmode;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver
{
    private final AlarmBroadcastReceiver alarmBroadcastReceiver = new AlarmBroadcastReceiver();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && "android.intent.action.BOOT_COMPLETED".equals(intent.getAction())) {
            int id = intent.getIntExtra(Constants.ID, 0);
            if (id == Constants.ID_DISABLE) {
                alarmBroadcastReceiver.setAlarmDisableSleepMode(context);
            } else if (id == Constants.ID_ENABLE) {
                alarmBroadcastReceiver.setAlarmEnableSleepMode(context);
            }
        }
    }
}
