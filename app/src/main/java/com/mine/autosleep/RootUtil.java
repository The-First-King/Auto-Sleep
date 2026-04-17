package com.mine.autosleep;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

final class RootUtil {
    
    static boolean isDeviceRooted() {
        return checkRootMethod3() || checkRootMethod1() || checkRootMethod2();
    }

    private static boolean checkRootMethod1() {
        String buildTags = android.os.Build.TAGS;
        return buildTags != null && buildTags.contains("test-keys");
    }

    private static boolean checkRootMethod2() {
        String[] paths = { "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su", "/system/xbin/su", "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su",
                "/system/bin/failsafe/su", "/data/local/su", "/su/bin/su"};
        for (String path : paths) {
            try {
                if (new File(path).exists()) return true;
            } catch (Exception e) {
            }
        }
        return false;
    }

    private static boolean checkRootMethod3() {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
            BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String output = in.readLine();
            
            if (output != null && output.toLowerCase().contains("uid=0")) {
                return true;
            }
            return false;
        } catch (Throwable t) {
            return false;
        } finally {
            if (process != null) process.destroy();
        }
    }
}
