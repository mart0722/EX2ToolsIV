package com.ex2tools.iv.apps;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.util.AtomicFile;

import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

public final class SteeringPatch {
    public static final String PACKAGE = "com.flyme.auto.keyserver";
    static final String INSTALL = "steering_patch_install";
    static final String RESTORE = "steering_patch_restore";

    private static final long VERSION = 26052410L;
    private static final String ORIGINAL_NAME = "GeelyAutoKeyServer-original.apk";
    private static final String PATCH_NAME = "GeelyAutoKeyServer-climate.apk";
    private static final String ORIGINAL_HASH = "62b94ffa72e5e4bb81fce4882750530089cab94fcb286c1fcea288f71781fa73";
    private static final String PATCH_HASH = "ce2261a1eb5efd2829fff337332da148240c1daf73868d78cf18982052b7afa4";

    private SteeringPatch() {}

    public record State(int message, boolean canInstall, boolean canRestore, boolean patched) {
        public boolean busy() {
            return message == R.string.steering_patch_busy;
        }
    }

    public static State readState(Context context) {
        try {
            if (hasPendingInstall(context)) {
                return new State(R.string.steering_patch_busy, false, false, false);
            }
            File installedApk = findInstalledApk(context);
            String hash = installedApk == null ? "" : sha256(installedApk);
            if (ORIGINAL_HASH.equals(hash)) {
                boolean hasPatch = hasAsset(context, PATCH_NAME);
                return new State(hasPatch ? R.string.steering_patch_stock
                        : R.string.steering_patch_missing_asset, hasPatch, false, false);
            }
            if (PATCH_HASH.equals(hash)) {
                boolean canRestore = hasBackup(context) || hasAsset(context, ORIGINAL_NAME);
                return new State(canRestore ? R.string.steering_patch_installed
                        : R.string.steering_patch_missing_backup, false, canRestore, true);
            }
            return new State(R.string.steering_patch_unsupported, false, false, false);
        } catch (PackageManager.NameNotFoundException e) {
            return new State(R.string.steering_patch_service_missing, false, false, false);
        } catch (IOException | RuntimeException e) {
            LogStore.add(context, "steering patch status: " + e);
            return new State(R.string.steering_patch_status_failed, false, false, false);
        }
    }

    public static synchronized void change(Context context, boolean install) throws Exception {
        if (hasPendingInstall(context)) throw new IOException("Keyserver install already pending");
        File installedApk = findInstalledApk(context);
        String expectedInstalled = install ? ORIGINAL_HASH : PATCH_HASH;
        if (installedApk == null || !expectedInstalled.equals(sha256(installedApk))) {
            throw new IOException("Unexpected installed keyserver APK");
        }

        AtomicFile backup = backupFile(context);
        if (!hasBackup(context)) {
            try (InputStream original = install ? new FileInputStream(installedApk)
                    : openAsset(context, ORIGINAL_NAME)) {
                saveBackup(backup, original);
            }
        }

        File replacement = install ? File.createTempFile("keyserver-patch-", ".apk", context.getCacheDir())
                : backup.getBaseFile();
        try {
            if (install) {
                try (InputStream in = openAsset(context, PATCH_NAME);
                     OutputStream out = new FileOutputStream(replacement)) {
                    copy(in, out, null);
                }
            }
            if (!(install ? PATCH_HASH : ORIGINAL_HASH).equals(sha256(replacement))) {
                throw new IOException("Unexpected replacement APK checksum");
            }
            ApkInfo info = AppsRepository.inspect(context, replacement);
            if (!PACKAGE.equals(info.packageName) || info.versionCode != VERSION
                    || !info.installed || info.installedVersionCode != VERSION
                    || "—".equals(info.signer) || !info.signer.equals(info.installedSigner)) {
                throw new IOException("Unexpected keyserver package, version or certificate");
            }
            File current = findInstalledApk(context);
            if (!installedApk.equals(current) || !expectedInstalled.equals(sha256(current))) {
                throw new IOException("The keyserver APK changed while preparing installation");
            }
            AppsRepository.install(context, replacement, info, install ? INSTALL : RESTORE);
        } finally {
            if (install) replacement.delete();
        }
    }

    private static boolean hasPendingInstall(Context context) {
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        for (PackageInstaller.SessionInfo session : installer.getMySessions()) {
            if (PACKAGE.equals(session.getAppPackageName()) && (session.isActive() || session.isSealed())) {
                return true;
            }
        }
        return false;
    }

    private static File findInstalledApk(Context context) throws PackageManager.NameNotFoundException {
        PackageInfo info = context.getPackageManager().getPackageInfo(PACKAGE, 0);
        ApplicationInfo app = info.applicationInfo;
        if (info.getLongVersionCode() != VERSION || app == null || app.sourceDir == null
                || (app.splitSourceDirs != null && app.splitSourceDirs.length > 0)) {
            return null;
        }
        return new File(app.sourceDir);
    }

    private static AtomicFile backupFile(Context context) {
        return new AtomicFile(new File(context.getFilesDir(), ORIGINAL_NAME));
    }

    private static boolean hasBackup(Context context) throws IOException {
        File file = backupFile(context).getBaseFile();
        return file.isFile() && ORIGINAL_HASH.equals(sha256(file));
    }

    private static void saveBackup(AtomicFile backup, InputStream original) throws IOException {
        FileOutputStream out = backup.startWrite();
        try {
            MessageDigest digest = newDigest();
            copy(original, out, digest);
            if (!ORIGINAL_HASH.equals(hex(digest.digest()))) {
                throw new IOException("Original APK checksum does not match");
            }
            out.getFD().sync();
        } catch (IOException | RuntimeException e) {
            backup.failWrite(out);
            throw e;
        }
        backup.finishWrite(out);
        if (!ORIGINAL_HASH.equals(sha256(backup.getBaseFile()))) {
            throw new IOException("Original APK backup could not be verified");
        }
    }

    private static boolean hasAsset(Context context, String name) {
        try (InputStream ignored = openAsset(context, name)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static InputStream openAsset(Context context, String name) throws IOException {
        return context.getAssets().open("steering/" + name);
    }

    private static String sha256(File file) throws IOException {
        MessageDigest digest = newDigest();
        try (InputStream in = new FileInputStream(file)) {
            copy(in, null, digest);
        }
        return hex(digest.digest());
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) hex.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return hex.toString();
    }

    private static void copy(InputStream in, OutputStream out, MessageDigest digest) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        int count;
        while ((count = in.read(buffer)) != -1) {
            if (out != null) out.write(buffer, 0, count);
            if (digest != null) digest.update(buffer, 0, count);
        }
    }
}
