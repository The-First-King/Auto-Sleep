package com.mine.autosleep;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;

final class SleepController {
    private static final String TAG = "SleepController";

    // Wi-Fi / Bluetooth via svc (https://developer.android.com/develop/ui/views/notifications/channels)
    private static final String CMD_WIFI_OFF = "svc wifi disable";
    private static final String CMD_WIFI_ON  = "svc wifi enable";
    private static final String CMD_BT_OFF   = "svc bluetooth disable";
    private static final String CMD_BT_ON    = "svc bluetooth enable";

    // Doze force/unforce (mandatory force; always unforce on exit)
    private static final String CMD_FORCE_IDLE = "dumpsys deviceidle force-idle";
    private static final String CMD_UNFORCE_IDLE = "dumpsys deviceidle unforce";

    // Airplane mode commands
    private static final String CMD_AP_ON_1  = "settings put global airplane_mode_on 1";
    private static final String CMD_AP_ON_2  = "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true";
    private static final String CMD_AP_OFF_1 = "settings put global airplane_mode_on 0";
    private static final String CMD_AP_OFF_2 = "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false";

    private SleepController() {}

    static boolean isSleepActive(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return sp.getBoolean(Constants.PREF_SLEEP_ACTIVE, false);
    }

    static void enterSleep(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);

        if (sp.getBoolean(Constants.PREF_SLEEP_ACTIVE, false)) {
            Log.d(TAG, "enterSleep: already active");
            return;
        }

        // Snapshot current states (read via su to avoid OEM API restrictions)
        Snapshot snap = readSnapshotViaSu();
        sp.edit()
                .putInt(Constants.SNAP_AIRPLANE, snap.airplane)
                .putInt(Constants.SNAP_WIFI, snap.wifi)
                .putInt(Constants.SNAP_BT, snap.bt)
                .putBoolean(Constants.SNAP_VALID, true)
                .apply();

        // Apply sleep bundle:
        // airplane ON, wifi OFF, bt OFF, force idle
        runSu(
                CMD_AP_ON_1,
                CMD_AP_ON_2,
                CMD_WIFI_OFF,
                CMD_BT_OFF,
                CMD_FORCE_IDLE
        );

        sp.edit().putBoolean(Constants.PREF_SLEEP_ACTIVE, true).apply();
        Log.d(TAG, "enterSleep: done");
    }

    static void exitSleep(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);

        // Always unforce idle, even if state says "not active" [2](https://iut-fbleau.fr/docs/android/reference/android/service/quicksettings/TileService.html)[3](https://www.geeksforgeeks.org/android/quick-settings-tile-api-in-android-13/)
        runSu(CMD_UNFORCE_IDLE);

        if (!sp.getBoolean(Constants.PREF_SLEEP_ACTIVE, false)) {
            Log.d(TAG, "exitSleep: not active (unforced only)");
            return;
        }

        boolean hasSnap = sp.getBoolean(Constants.SNAP_VALID, false);
        int airplane = hasSnap ? sp.getInt(Constants.SNAP_AIRPLANE, 0) : 0;
        int wifi = hasSnap ? sp.getInt(Constants.SNAP_WIFI, 0) : 0;
        int bt = hasSnap ? sp.getInt(Constants.SNAP_BT, 0) : 0;

        // Restore exactly the snapshot (always), regardless of manual changes during sleep
        if (wifi == 1) runSu(CMD_WIFI_ON); else runSu(CMD_WIFI_OFF);
        if (bt == 1)   runSu(CMD_BT_ON);   else runSu(CMD_BT_OFF);

        if (airplane == 1) {
            runSu(CMD_AP_ON_1, CMD_AP_ON_2);
        } else {
            runSu(CMD_AP_OFF_1, CMD_AP_OFF_2);
        }

        sp.edit()
                .putBoolean(Constants.PREF_SLEEP_ACTIVE, false)
                .putBoolean(Constants.SNAP_VALID, false)
                .apply();

        Log.d(TAG, "exitSleep: restored snapshot and cleared state");
    }

    private static void runSu(String... commands) {
        Process p = null;
        try {
            p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            for (String cmd : commands) {
                os.writeBytes(cmd + "\n");
            }
            os.writeBytes("exit\n");
            os.flush();
            p.waitFor();
        } catch (Exception e) {
            Log.e(TAG, "runSu failed: " + e.getMessage(), e);
        } finally {
            if (p != null) p.destroy();
        }
    }

    private static Snapshot readSnapshotViaSu() {
        Snapshot s = new Snapshot();
        s.airplane = 0;
        s.wifi = 0;
        s.bt = 0;

        Process p = null;
        try {
            p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());

            os.writeBytes("echo AP=$(settings get global airplane_mode_on)\n");
            os.writeBytes("echo WIFI=$(settings get global wifi_on)\n");
            os.writeBytes("echo BT=$(settings get global bluetooth_on)\n");
            os.writeBytes("exit\n");
            os.flush();

            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("AP=")) s.airplane = parse01(line.substring(3));
                else if (line.startsWith("WIFI=")) s.wifi = parse01(line.substring(5));
                else if (line.startsWith("BT=")) s.bt = parse01(line.substring(3));
            }

            p.waitFor();
        } catch (Exception e) {
            Log.e(TAG, "readSnapshotViaSu failed: " + e.getMessage(), e);
        } finally {
            if (p != null) p.destroy();
        }
        return s;
    }

    private static int parse01(String v) {
        try {
            v = v.trim();
            return "1".equals(v) ? 1 : 0;
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static final class Snapshot {
        int airplane;
        int wifi;
        int bt;
    }

}
