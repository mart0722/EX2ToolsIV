package com.ex2tools.iv.car;

import android.content.Context;

import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.Prefs;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Sapo-compatible TTL 65 setting.
 *
 * The rules are applied in the mangle POSTROUTING chain for IPv4 and IPv6.
 * Enabling requires both rules to exist; disabling requires both to be gone.
 * The preference is updated only after that verification succeeds.
 */
public final class Ttl65 {

    private static final int[] LOCAL_ADB_PORTS = {65010, 5555};
    private static volatile Boolean localAdbUsable;

    private Ttl65() {
    }

    public static boolean apply(Context c, boolean enable) {
        String out = runBest(script(enable), "TTL_OK");
        LogStore.add(c, "ttl65 " + (enable ? "on" : "off") + ": " + shortOutput(out));
        return out != null && out.contains("TTL_OK");
    }

    public static void applyOnBoot(Context c) {
        try {
            Thread.sleep(30000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        if (!Prefs.ttl65(c)) return;
        boolean ok = apply(c, true);
        LogStore.add(c, "ttl65 boot reapply: " + (ok ? "ok" : "failed"));
    }

    private static String script(boolean enable) {
        StringBuilder sh = new StringBuilder();
        sh.append("V4=0\n");
        sh.append("V6=0\n");
        sh.append("for i in 1 2 3 4 5; do ");
        sh.append("iptables -t mangle -D POSTROUTING -j TTL --ttl-set 65 ");
        sh.append("2>/dev/null || break; done\n");
        sh.append("for i in 1 2 3 4 5; do ");
        sh.append("ip6tables -t mangle -D POSTROUTING -j HL --hl-set 65 ");
        sh.append("2>/dev/null || break; done\n");

        if (enable) {
            sh.append("iptables -t mangle -A POSTROUTING -j TTL --ttl-set 65 2>&1\n");
            sh.append("ip6tables -t mangle -A POSTROUTING -j HL --hl-set 65 2>&1\n");
        }

        sh.append("V4=$(iptables -t mangle -S POSTROUTING 2>/dev/null ");
        sh.append("| grep -c 'ttl-set 65')\n");
        sh.append("V6=$(ip6tables -t mangle -S POSTROUTING 2>/dev/null ");
        sh.append("| grep -c 'hl-set 65')\n");
        sh.append("echo \"TTL_V4=$V4\"\n");
        sh.append("echo \"TTL_V6=$V6\"\n");
        if (enable) {
            sh.append("if [ \"${V4:-0}\" -gt 0 ] && [ \"${V6:-0}\" -gt 0 ]; ");
            sh.append("then echo TTL_OK; else echo TTL_FAIL; fi\n");
        } else {
            sh.append("if [ \"${V4:-0}\" -eq 0 ] && [ \"${V6:-0}\" -eq 0 ]; ");
            sh.append("then echo TTL_OK; else echo TTL_FAIL; fi\n");
        }
        return sh.toString();
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

    private static String shortOutput(String out) {
        if (out == null) return "";
        String value = out.replace('\n', ' ').trim();
        return value.length() > 600 ? value.substring(0, 600) : value;
    }
}
