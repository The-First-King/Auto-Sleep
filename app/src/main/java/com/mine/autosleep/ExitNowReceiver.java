package com.mine.autosleep;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ExitNowReceiver extends BroadcastReceiver {
    @Override
       public void onReceive(Context context, Intent intent) {
        Intent work = new Intent(context, AutoSleepService.class);
        work.putExtra(Constants.ID, Constants.ID_DISABLE);
        AutoSleepService.enqueue(context, work);
    }
}