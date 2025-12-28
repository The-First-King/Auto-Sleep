package com.mine.autosleep;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver
{
    private final AlarmBroadcastReceiver alarmBroadcastReceiver = new AlarmBroadcastReceiver();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && "android.intent.action.BOOT_COMPLETED".equals(intent.getAction())) {
            alarmBroadcastReceiver.setAlarms(context);
        }
    }
}
