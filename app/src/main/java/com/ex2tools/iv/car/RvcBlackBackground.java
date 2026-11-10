package com.ex2tools.iv.car;

import android.content.Context;

import com.ex2tools.iv.LogStore;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Temporarily replaces the reverse-camera background and obstacle graphics
 * with the same black/yellow assets used by Sapo. The changes are made with
 * bind mounts; /system is not modified and the effect disappears on reboot.
 */
public final class RvcBlackBackground {

    public static final String TARGET_BG =
            "/system/etc/automotive/evsapp/rvc/rvc_left_bg.png";
    public static final String TARGET_DIR =
            "/system/etc/automotive/evsapp/rvc";
    private static final String TMP_BG = "/data/local/tmp/rvc_black_bg.png";
    private static final String TMP_DIR = "/data/local/tmp/rvc_overlay";
    private static final String SERVICE = "evsapp";
    private static final int[] LOCAL_ADB_PORTS = {65010, 5555};
    private static volatile Boolean localAdbUsable;

    private static final String[] YELLOW = {
            "avm_obstacle_distance_cm.png",
            "avm_obstacle_distance_num0.png",
            "avm_obstacle_distance_num1.png",
            "avm_obstacle_distance_num2.png",
            "avm_obstacle_distance_num3.png",
            "avm_obstacle_distance_num4.png",
            "avm_obstacle_distance_num5.png",
            "avm_obstacle_distance_num6.png",
            "avm_obstacle_distance_num7.png",
            "avm_obstacle_distance_num8.png",
            "avm_obstacle_distance_num9.png"
    };

    private RvcBlackBackground() {
    }

    public static synchronized boolean apply(Context c) {
        String target = runDirect("[ -f " + q(TARGET_BG) + " ] && echo TARGET_OK"
                + " || echo TARGET_MISSING");
        if (!target.contains("TARGET_OK")) {
            LogStore.add(c, "rvc black target missing: " + TARGET_BG);
            return false;
        }
        if (!copyAssets(c)) return false;
        String out = runBest(buildApplyScript(), "BIND_OK");
        LogStore.add(c, "rvc black apply: " + shortOutput(out));
        return out.contains("BIND_OK");
    }

    /**
     * Boot-safe entry point: a running bind mount survives an app update, so
     * re-use it instead of restarting the camera service a second time.
     */
    public static synchronized boolean applyIfNeeded(Context c) {
        if (isApplied(c)) {
            LogStore.add(c, "rvc black boot: already mounted");
            return true;
        }
        return apply(c);
    }

    public static synchronized boolean remove(Context c) {
        String out = runBest(buildRemoveScript(), "LIVRE");
        LogStore.add(c, "rvc black remove: " + shortOutput(out));
        return out.contains("LIVRE");
    }

    public static boolean isApplied(Context c) {
        String out = runBest("grep -F \" " + TARGET_BG + " \" /proc/mounts >/dev/null 2>&1"
                + " && echo Y || echo N", "Y");
        return out.trim().endsWith("Y");
    }

    private static boolean copyAssets(Context c) {
        File cache = c.getCacheDir();
        if (!copyAsset(c, "rvc_black_bg.png", new File(cache, "rvcstage_black.png"))) {
            return false;
        }
        for (String name : YELLOW) {
            // Like Sapo, a missing auxiliary yellow asset must not abort the
            // black background replacement.
            copyAsset(c, "rvc_yellow/" + name,
                    new File(cache, "rvcstage_" + name));
        }

        StringBuilder sh = new StringBuilder();
        sh.append("mkdir -p ").append(q(TMP_DIR)).append(" 2>/dev/null || true\n");
        sh.append("cp -f ").append(q(new File(cache, "rvcstage_black.png").getAbsolutePath()))
                .append(" ").append(q(TMP_BG)).append(" 2>&1 || true\n");
        for (String name : YELLOW) {
            sh.append("cp -f ")
                    .append(q(new File(cache, "rvcstage_" + name).getAbsolutePath()))
                    .append(" ").append(q(TMP_DIR + "/" + name)).append(" 2>&1 || true\n");
        }
        sh.append("chown root:root ").append(q(TMP_BG)).append(" 2>/dev/null || true\n");
        sh.append("chmod 644 ").append(q(TMP_BG)).append(" 2>/dev/null || true\n");
        sh.append("restorecon -v ").append(q(TMP_BG)).append(" 2>/dev/null || true\n");
        sh.append("chcon u:object_r:system_file:s0 ").append(q(TMP_BG))
                .append(" 2>/dev/null || true\n");
        for (String name : YELLOW) {
            String p = TMP_DIR + "/" + name;
            sh.append("chown root:root ").append(q(p)).append(" 2>/dev/null || true\n");
            sh.append("chmod 644 ").append(q(p)).append(" 2>/dev/null || true\n");
            sh.append("restorecon -v ").append(q(p)).append(" 2>/dev/null || true\n");
            sh.append("chcon u:object_r:system_file:s0 ").append(q(p))
                    .append(" 2>/dev/null || true\n");
        }
        sh.append("sync 2>/dev/null || true\n");
        sh.append("[ -f ").append(q(TMP_BG)).append(" ] && echo SRC_DONE || echo SRC_FAIL\n");
        String out = runBest(sh.toString(), "SRC_DONE");
        LogStore.add(c, "rvc black assets: " + shortOutput(out));
        return out.contains("SRC_DONE");
    }

    private static boolean copyAsset(Context c, String asset, File target) {
        try (InputStream in = c.getAssets().open(asset);
             OutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            return true;
        } catch (Throwable t) {
            LogStore.add(c, "rvc asset copy failed " + asset + ": " + t);
            return false;
        }
    }

    private static String buildApplyScript() {
        StringBuilder sh = new StringBuilder();
        sh.append("setenforce 0 2>/dev/null || true\n");
        sh.append("echo \"== rvc overlays bind ==\"\n");
        appendBind(sh, TARGET_BG, TMP_BG);
        for (String name : YELLOW) {
            appendBind(sh, TARGET_DIR + "/" + name, TMP_DIR + "/" + name);
        }
        sh.append("setprop ctl.restart ").append(SERVICE).append(" 2>/dev/null || true\n");
        sh.append("if grep -F \" ").append(TARGET_BG)
                .append(" \" /proc/mounts >/dev/null 2>&1; then echo BIND_OK;"
                        + " else echo BIND_FAIL; fi\n");
        return sh.toString();
    }

    private static void appendBind(StringBuilder sh, String target, String source) {
        sh.append("if [ -f ").append(q(source)).append(" ] && [ -f ")
                .append(q(target)).append(" ]; then\n");
        sh.append("  _n=0; while grep -F \" ").append(target)
                .append(" \" /proc/mounts >/dev/null 2>&1 && [ $_n -lt 12 ]; do umount ")
                .append(q(target)).append(" 2>/dev/null || umount -l ")
                .append(q(target)).append(" 2>/dev/null || break; _n=$((_n+1)); done\n");
        sh.append("  chown root:root ").append(q(source)).append(" 2>&1 || true\n");
        sh.append("  chmod 644 ").append(q(source)).append(" 2>&1 || true\n");
        sh.append("  restorecon -v ").append(q(source)).append(" 2>&1 || true\n");
        sh.append("  chcon u:object_r:system_file:s0 ").append(q(source)).append(" 2>&1 || true\n");
        sh.append("  restorecon -v ").append(q(target)).append(" 2>&1 || true\n");
        sh.append("  mount --bind ").append(q(source)).append(" ").append(q(target))
                .append(" 2>&1 && echo \"BOUND:").append(target)
                .append("\" || echo \"FAILBIND:").append(target).append("\"\n");
        sh.append("  chmod 644 ").append(q(target)).append(" 2>&1 || true\n");
        sh.append("  restorecon -v ").append(q(target)).append(" 2>&1 || true\n");
        sh.append("fi\n");
    }

    private static String buildRemoveScript() {
        StringBuilder sh = new StringBuilder();
        sh.append("echo \"== rvc overlays unbind ==\"\n");
        appendUnbind(sh, TARGET_BG);
        for (String name : YELLOW) {
            appendUnbind(sh, TARGET_DIR + "/" + name);
        }
        sh.append("setprop ctl.restart ").append(SERVICE).append(" 2>/dev/null || true\n");
        sh.append("if grep -F \" ").append(TARGET_BG)
                .append(" \" /proc/mounts >/dev/null 2>&1; then echo AINDA_MONTADO;"
                        + " else echo LIVRE; fi\n");
        return sh.toString();
    }

    private static void appendUnbind(StringBuilder sh, String target) {
        sh.append("_n=0; while grep -F \" ").append(target)
                .append(" \" /proc/mounts >/dev/null 2>&1 && [ $_n -lt 12 ]; do umount ")
                .append(q(target)).append(" 2>/dev/null || umount -l ")
                .append(q(target)).append(" 2>/dev/null || break; _n=$((_n+1)); done\n");
        sh.append("restorecon -v ").append(q(target)).append(" 2>/dev/null || true\n");
    }

    private static String runBest(String script, String successMarker) {
        String local = runLocalAdb(script);
        if (local != null && local.contains(successMarker)) return local;
        String direct = runDirect(script);
        if (direct.contains(successMarker)) return direct;
        if (local == null) return direct;
        return local + "\nDIRECT:\n" + direct;
    }

    private static String runLocalAdb(String script) {
        if (Boolean.FALSE.equals(localAdbUsable)) return null;
        for (int port : LOCAL_ADB_PORTS) {
            try {
                String out = AdbBigScript.run(script, 45000, (command, timeoutMs) ->
                        new LocalAdbClient("127.0.0.1", port).runShell(command, timeoutMs));
                localAdbUsable = true;
                return out;
            } catch (Throwable ignored) {
            }
        }
        localAdbUsable = false;
        return null;
    }

    private static String runDirect(String script) {
        Process process = null;
        try {
            process = new ProcessBuilder("sh", "-c", script)
                    .redirectErrorStream(true)
                    .start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    out.append(line).append('\n');
                }
            }
            if (!process.waitFor(45, TimeUnit.SECONDS)) {
                process.destroy();
                out.append("SCRIPT_TIMEOUT\n");
            }
            return out.toString();
        } catch (Throwable t) {
            return "SCRIPT_ERROR: " + t;
        } finally {
            if (process != null) process.destroy();
        }
    }

    private static String q(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static String shortOutput(String out) {
        if (out == null) return "";
        String value = out.replace('\n', ' ').trim();
        return value.length() > 600 ? value.substring(0, 600) : value;
    }
}
