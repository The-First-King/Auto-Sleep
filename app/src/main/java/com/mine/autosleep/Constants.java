package com.mine.autosleep;

public class Constants {

    static final String APP_IS_ENABLED = "appEnabled";
    static final String ENABLE_SLEEP_TIME = "enableSleepTime";
    static final String DISABLE_SLEEP_TIME = "disableSleepTime";
    static final String START_ON_NEXT_DAY = "startOnNextDay";
	static final String PREF_SCHEDULED_EXIT_AT = "scheduled_exit_at_epoch";
    public static final String ID = "id";
    public static final int ID_ENABLE = 1;
    public static final int ID_DISABLE = 2;
    public static final String END = "end";
    public static final String ORIGIN = "origin";
    public static final String ORIGIN_ALARM = "alarm";
    public static final String ORIGIN_MANUAL = "manual";

    // Sleep mode state + snapshot (restore always)
    static final String PREF_SLEEP_ACTIVE = "sleepActive";
    static final String SNAP_AIRPLANE = "snap_airplane";
    static final String SNAP_WIFI = "snap_wifi";
    static final String SNAP_BT = "snap_bt";

    // Wi‑Fi hardening snapshot
    static final String SNAP_WIFI_SCAN_ALWAYS = "snap_wifi_scan_always";
    static final String SNAP_WIFI_WAKEUP_ENABLED = "snap_wifi_wakeup_enabled";
    static final String SNAP_WIFI_UP_IFACES = "snap_wifi_up_ifaces"; // comma-separated list
    static final String SNAP_VALID = "snap_valid";

    // Notification + actions
    static final int NOTIF_ID_SLEEP = 1;
    static final String NOTIF_CHANNEL_ID = "sleep_mode";
    static final String ACTION_EXIT_NOW = "com.mine.autosleep.action.EXIT_NOW";

    // JobIntentService job id (unique within app)
    static final int JOB_ID = 1001;
}
