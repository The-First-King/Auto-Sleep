package com.mine.autosleep;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

final class SleepController {
    private static final String TAG = "SleepController";

    // Wi‑Fi / Bluetooth via svc
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

    // Settings keys we snapshot/restore when supported
    private static final String CMD_GET_SCAN_ALWAYS =
            "/system/bin/settings get global wifi_scan_always_enabled 2>/dev/null";
    private static final String CMD_GET_WAKEUP_ENABLED =
            "/system/bin/settings get global wifi_wakeup_enabled 2>/dev/null";

    private static final String CMD_PUT_SCAN_ALWAYS_0 =
            "/system/bin/settings put global wifi_scan_always_enabled 0";
    private static final String CMD_PUT_WAKEUP_ENABLED_0 =
            "/system/bin/settings put global wifi_wakeup_enabled 0";

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

        // Snapshot current states (read via su)
        Snapshot snap = readSnapshotViaSu();

        sp.edit()
                .putInt(Constants.SNAP_AIRPLANE, snap.airplane)
                .putInt(Constants.SNAP_WIFI, snap.wifi)
                .putInt(Constants.SNAP_BT, snap.bt)
                .putInt(Constants.SNAP_WIFI_SCAN_ALWAYS, snap.wifiScanAlways)         // -1 if unsupported
                .putInt(Constants.SNAP_WIFI_WAKEUP_ENABLED, snap.wifiWakeupEnabled)   // -1 if unsupported
                .putString(Constants.SNAP_WIFI_UP_IFACES, snap.wifiStaUpIfacesCsv)    // STA-only (may be empty)
                .putBoolean(Constants.SNAP_VALID, true)
                .apply();

        // Apply sleep bundle in requested order:
        // Airplane ON, wifi OFF, ip-link DOWN (STA that were UP), scan/wakeup OFF (if were 1), BT OFF, force idle
        List<String> cmds = new ArrayList<>();

        cmds.add(CMD_AP_ON_1);
        cmds.add(CMD_AP_ON_2);

        // Framework Wi‑Fi OFF (AOSP svc->cmd mapping keeps this consistent)
        cmds.add(CMD_WIFI_OFF);

        // Bring DOWN only STA interfaces that were UP at entry
        addIfaceCommands(cmds, snap.wifiStaUpIfacesCsv, false /*down*/);

        // Disable scan-always / wakeup only if supported and currently enabled in snapshot
        if (snap.wifiScanAlways == 1) cmds.add(CMD_PUT_SCAN_ALWAYS_0);
        if (snap.wifiWakeupEnabled == 1) cmds.add(CMD_PUT_WAKEUP_ENABLED_0);

        // Bluetooth OFF
        cmds.add(CMD_BT_OFF);

        // Force Doze idle
        cmds.add(CMD_FORCE_IDLE);

        runSu(cmds.toArray(new String[0]));

        sp.edit().putBoolean(Constants.PREF_SLEEP_ACTIVE, true).apply();
        Log.d(TAG, "enterSleep: done");
    }

    static void exitSleep(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);

        // Always unforce idle
        runSu(CMD_UNFORCE_IDLE);

        if (!sp.getBoolean(Constants.PREF_SLEEP_ACTIVE, false)) {
            Log.d(TAG, "exitSleep: not active (unforced only)");
            return;
        }

        boolean hasSnap = sp.getBoolean(Constants.SNAP_VALID, false);

        int airplane = hasSnap ? sp.getInt(Constants.SNAP_AIRPLANE, 0) : 0;
        int wifi     = hasSnap ? sp.getInt(Constants.SNAP_WIFI, 0) : 0;
        int bt       = hasSnap ? sp.getInt(Constants.SNAP_BT, 0) : 0;

        int scanAlways     = hasSnap ? sp.getInt(Constants.SNAP_WIFI_SCAN_ALWAYS, -1) : -1;
        int wakeupEnabled  = hasSnap ? sp.getInt(Constants.SNAP_WIFI_WAKEUP_ENABLED, -1) : -1;
        String upStaIfaces = hasSnap ? sp.getString(Constants.SNAP_WIFI_UP_IFACES, "") : "";

        // Restore Wi‑Fi settings BEFORE enabling Wi‑Fi and before bringing interfaces up
        List<String> cmds = new ArrayList<>();

        if (scanAlways == 0 || scanAlways == 1) {
            cmds.add("/system/bin/settings put global wifi_scan_always_enabled " + scanAlways);
        }
        if (wakeupEnabled == 0 || wakeupEnabled == 1) {
            cmds.add("/system/bin/settings put global wifi_wakeup_enabled " + wakeupEnabled);
        }

        // Bring UP only the STA interfaces that were UP at entry
        addIfaceCommands(cmds, upStaIfaces, true /*up*/);

        // Restore framework Wi‑Fi after restoring settings & interfaces
        if (wifi == 1) cmds.add(CMD_WIFI_ON);
        else cmds.add(CMD_WIFI_OFF);

        // Restore Bluetooth
        if (bt == 1) cmds.add(CMD_BT_ON);
        else cmds.add(CMD_BT_OFF);

        // Restore Airplane
        if (airplane == 1) {
            cmds.add(CMD_AP_ON_1);
            cmds.add(CMD_AP_ON_2);
        } else {
            cmds.add(CMD_AP_OFF_1);
            cmds.add(CMD_AP_OFF_2);
        }

        runSu(cmds.toArray(new String[0]));

        sp.edit()
                .putBoolean(Constants.PREF_SLEEP_ACTIVE, false)
                .putBoolean(Constants.SNAP_VALID, false)
                .apply();

        Log.d(TAG, "exitSleep: restored snapshot and cleared state");
    }

    private static void addIfaceCommands(List<String> cmds, String ifacesCsv, boolean up) {
        if (ifacesCsv == null) return;
        String trimmed = ifacesCsv.trim();
        if (trimmed.isEmpty()) return;

        String[] parts = trimmed.split(",");
        for (String p : parts) {
            if (p == null) continue;
            String iface = p.trim();
            if (iface.isEmpty()) continue;
            cmds.add("ip link set " + iface + (up ? " up" : " down"));
        }
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

        s.wifiScanAlways = -1;
        s.wifiWakeupEnabled = -1;
        s.wifiStaUpIfacesCsv = "";

        Process p = null;
        try {
            p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());

            os.writeBytes("echo AP=$(/system/bin/settings get global airplane_mode_on 2>/dev/null)\n");
            os.writeBytes("echo WIFI=$(/system/bin/settings get global wifi_on 2>/dev/null)\n");
            os.writeBytes("echo BT=$(/system/bin/settings get global bluetooth_on 2>/dev/null)\n");

            os.writeBytes("echo SCAN=$(" + CMD_GET_SCAN_ALWAYS + ")\n");
            os.writeBytes("echo WAKEUP=$(" + CMD_GET_WAKEUP_ENABLED + ")\n");

            // Build CSV of STA interfaces that are UP
            // 1) iw dev -> interfaces with "type managed" (STA/client)
            // 2) fallback getprop wifi.interface
            // 3) fallback heuristic list
            os.writeBytes(
                    "STA_LIST=$(iw dev 2>/dev/null | " +
                            "awk '$1==\"Interface\"{i=$2} $1==\"type\" && $2==\"managed\"{print i}');\n" +
                    "if [ -z \"$STA_LIST\" ]; then " +
                            "IF=$(getprop wifi.interface 2>/dev/null); " +
                            "[ -n \"$IF\" ] && STA                            "[ -n \"$IF\" ] && STA_LIST=\"$IF\"; " +
                    "fi;\n" +
                    "if [ -z \"$STA_LIST\" ]; then " +
                            "STA_LIST=$(for IF in /sys/class/net/*; do " +
                                "I=$(basename $IF); " +
                                "case \"$I\" in *p2p*|*ap*|swlan*|nan* ) continue;; esac; " +
                                "[ -d /sys/class/net/$I/wireless ] || continue; " +
                                "echo $I; " +
                            "done); " +
                    "fi;\n" +
                    "CSV=\"\";\n" +
                    "for I in $STA_LIST; do " +
                        "S=$(cat /sys/class/net/$I/operstate 2>/dev/null); " +
                        "[ \"$S\" = \"up\" ] && CSV=\"$CSV$I,\"; " +
                    "done;\n" +
                    "echo IFACES=$CSV\n"
            );

            os.writeBytes("exit\n");
            os.flush();

            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("AP=")) s.airplane = parse01(line.substring(3));
                else if (line.startsWith("WIFI=")) s.wifi = parse01(line.substring(5));
                else if (line.startsWith("BT=")) s.bt = parse01(line.substring(3));

                else if (line.startsWith("SCAN=")) s.wifiScanAlways = parseNullable01(line.substring(5));
                else if (line.startsWith("WAKEUP=")) s.wifiWakeupEnabled = parseNullable01(line.substring(7));

                else if (line.startsWith("IFACES=")) {
                    String csv = line.substring(7).trim();
                    if (csv.endsWith(",")) csv = csv.substring(0, csv.length() - 1);
                    s.wifiStaUpIfacesCsv = csv;
                }
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

    private static int parseNullable01(String v) {
        if (v == null) return -1;
        v = v.trim();
        if (v.isEmpty()) return -1;
        if ("null".equalsIgnoreCase(v)) return -1;
        return "1".equals(v) ? 1 : 0;
    }

    private static final class Snapshot {
        int airplane;
        int wifi;
        int bt;

        int wifiScanAlways;      // -1 unsupported, else 0/1
        int wifiWakeupEnabled;   // -1 unsupported, else 0/1

        // STA-only interfaces that were UP at entry, e.g. "wlan0,wlan1"
        String wifiStaUpIfacesCsv;
    }
}
