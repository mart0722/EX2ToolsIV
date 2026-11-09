package com.ex2tools.iv.apps;

import android.app.PendingIntent;
import android.content.Context;
import android.os.Build;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.drawable.Drawable;

import com.ex2tools.iv.Actions;
import com.ex2tools.iv.LogStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public final class AppsRepository {

    private static final AtomicInteger SEQ = new AtomicInteger(3000);

    private AppsRepository() {
    }

    public static List<InstalledApp> loadInstalled(Context context) {
        Context c = context.getApplicationContext();
        PackageManager pm = c.getPackageManager();
        List<InstalledApp> out = new ArrayList<>();
        List<PackageInfo> packages;
        try {
            packages = pm.getInstalledPackages(PackageManager.GET_SIGNATURES);
        } catch (Throwable t) {
            LogStore.add(c, "apps list failed: " + t);
            return out;
        }
        for (PackageInfo pi : packages) {
            if (pi == null || pi.applicationInfo == null || pi.packageName == null) continue;
            ApplicationInfo ai = pi.applicationInfo;
            String label;
            try {
                label = pm.getApplicationLabel(ai).toString();
            } catch (Throwable t) {
                label = pi.packageName;
            }
            Drawable icon = null;
            try {
                icon = pm.getApplicationIcon(ai);
            } catch (Throwable ignored) {
            }
            boolean system = (ai.flags & (ApplicationInfo.FLAG_SYSTEM
                    | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
            String versionName = pi.versionName == null ? "?" : pi.versionName;
            out.add(new InstalledApp(pi.packageName, label, versionName,
                    pi.getLongVersionCode(), system, signatureSha256(pi.signatures), icon));
        }
        Collections.sort(out, (a, b) -> {
            if (a.system != b.system) return a.system ? 1 : -1;
            return String.CASE_INSENSITIVE_ORDER.compare(a.label, b.label);
        });
        return out;
    }

    public static ApkInfo inspect(Context context, File apk) throws Exception {
        Context c = context.getApplicationContext();
        PackageManager pm = c.getPackageManager();
        int flags = PackageManager.GET_ACTIVITIES
                | PackageManager.GET_META_DATA
                | PackageManager.GET_SIGNATURES;
        PackageInfo archive = pm.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        if (archive == null || archive.applicationInfo == null || archive.packageName == null) {
            throw new IOException("not an apk");
        }
        archive.applicationInfo.sourceDir = apk.getAbsolutePath();
        archive.applicationInfo.publicSourceDir = apk.getAbsolutePath();
        String label;
        try {
            label = pm.getApplicationLabel(archive.applicationInfo).toString();
        } catch (Throwable t) {
            label = archive.packageName;
        }
        String versionName = archive.versionName == null ? "?" : archive.versionName;
        long versionCode = archive.getLongVersionCode();
        String signer = signatureSha256(archive.signatures);

        boolean installed = false;
        String installedName = "";
        long installedCode = 0;
        String installedSigner = "";
        try {
            PackageInfo existing = pm.getPackageInfo(archive.packageName,
                    PackageManager.GET_SIGNATURES);
            installed = true;
            installedName = existing.versionName == null ? "?" : existing.versionName;
            installedCode = existing.getLongVersionCode();
            installedSigner = signatureSha256(existing.signatures);
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        return new ApkInfo(archive.packageName, label, versionName, versionCode, signer,
                installed, installedName, installedCode, installedSigner);
    }

    public static void install(Context context, File apk, ApkInfo info) throws Exception {
        Context c = context.getApplicationContext();
        PackageManager pm = c.getPackageManager();
        PackageInstaller installer = pm.getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setSize(apk.length());
        try {
            params.setAppPackageName(info.packageName);
        } catch (Throwable ignored) {
        }

        int sessionId = installer.createSession(params);
        PackageInstaller.Session session = installer.openSession(sessionId);
        try {
            try (InputStream in = new FileInputStream(apk);
                 OutputStream out = session.openWrite("base.apk", 0, apk.length())) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    out.write(buffer, 0, read);
                }
                out.flush();
                session.fsync(out);
            }

            Intent result = new Intent(c, InstallResultReceiver.class)
                    .setAction(Actions.APP_INSTALL_RESULT)
                    .putExtra(Actions.EXTRA_PACKAGE, info.packageName)
                    .putExtra(Actions.EXTRA_MODE, "install");
            PendingIntent pending = PendingIntent.getBroadcast(c, nextRequestCode(), result,
                    pendingFlags());
            session.commit(pending.getIntentSender());
            LogStore.add(c, "install requested: " + info.packageName + " v" + info.versionCode);
            if (apk.exists()) apk.delete();
        } finally {
            session.close();
        }
    }

    public static void uninstall(Context context, String packageName) throws Exception {
        Context c = context.getApplicationContext();
        PackageInstaller installer = c.getPackageManager().getPackageInstaller();
        Intent result = new Intent(c, InstallResultReceiver.class)
                .setAction(Actions.APP_INSTALL_RESULT)
                .putExtra(Actions.EXTRA_PACKAGE, packageName)
                .putExtra(Actions.EXTRA_MODE, "uninstall");
        PendingIntent pending = PendingIntent.getBroadcast(c, nextRequestCode(), result,
                pendingFlags());
        installer.uninstall(packageName, pending.getIntentSender());
        LogStore.add(c, "uninstall requested: " + packageName);
    }

    public static void broadcastRefresh(Context c) {
        Intent i = new Intent(Actions.APPS_REFRESH,
                android.net.Uri.parse("package:" + c.getPackageName()));
        i.setPackage(c.getPackageName());
        c.sendBroadcast(i);
    }

    public static String formatSize(long bytes) {
        if (bytes <= 0) return "—";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024L) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024L * 1024L) {
            return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
        }
        return String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    private static String signatureSha256(Signature[] signatures) {
        if (signatures == null || signatures.length == 0) return "—";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(signatures[0].toByteArray());
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format(Locale.US, "%02x", b & 0xff));
            return sb.toString();
        } catch (Throwable t) {
            return "—";
        }
    }

    private static int pendingFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
        return flags;
    }

    private static int nextRequestCode() {
        int v = SEQ.incrementAndGet();
        if (v > 60000) {
            SEQ.set(3000);
            v = SEQ.incrementAndGet();
        }
        return v;
    }
}
