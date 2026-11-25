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

/**
 * Installs/restores the patched GeelyAutoKeyServer.
 *
 * Patches are full keyserver APKs, so each supported keyserver build needs its
 * own pair. Selection is by installed versionCode + SHA-256 of the installed
 * APK, never by firmware name alone. The original APK is backed up before the
 * first install; an optional bundled original can be used for offline restore.
 */
public final class SteeringPatch {
    public static final String PACKAGE = "com.flyme.auto.keyserver";
    static final String INSTALL = "steering_patch_install";
    static final String RESTORE = "steering_patch_restore";

    private static final String ASSET_DIR = "steering";
    private static final String BACKUP_PREFIX = "GeelyAutoKeyServer-original-";

    private record Patch(long version, String originalAsset, String originalHash,
                         String patchAsset, String patchHash) {
        boolean hasPatch() {
            return patchHash != null && !patchHash.isEmpty();
        }
    }

    private record Installed(long version, File apk) {
    }

    private static final Patch[] PATCHES = {
            // IHU629G 1025, and 1111 if its keyserver is the same older build.
            new Patch(25102118L,
                    "GeelyAutoKeyServer-25102118-8e16d841-original.apk",
                    "8E16D841931244250FA4C0B97A9CD30AB61DE658311A5FD063C0856B5FC619E3",
                    "GeelyAutoKeyServer-25102118-8e16d841-climate.apk",
                    "70645B6B933546DF3C1059A4F5486C7DDE62EF29174CBA6F1BDC9E0B2B189D3C"),
            // IHU629G 1114.
            new Patch(26030420L,
                    "GeelyAutoKeyServer-26030420-586cd35d-original.apk",
                    "586CD35D9C7DE0BBADDED94B4F9378FDB720CC78DBB544626A041F6D70FACFBA",
                    "GeelyAutoKeyServer-26030420-586cd35d-climate.apk",
                    "58493A9F5A76A94FC29CA02593B59F66FCDFE6E4C6CCE989A689F6244DC72E3C"),
            // IHU629G 1121 (patch supplied by nicola1223).
            new Patch(26052410L,
                    "GeelyAutoKeyServer-26052410-62b94ffa-original.apk",
                    "62B94FFA72E5E4BB81FCE4882750530089CAB94FCB286C1FCEA288F71781FA73",
                    "GeelyAutoKeyServer-26052410-62b94ffa-climate.apk",
                    "CE2261A1EB5EFD2829FFF337332DA148240C1DAF73868D78CF18982052B7AFA4")
    };

    private SteeringPatch() {
    }

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
            Installed installed = findInstalled(context);
            if (installed == null) {
                return new State(R.string.steering_patch_service_missing, false, false, false);
            }
            String hash = sha256(installed.apk());

            Patch original = findOriginal(installed.version(), hash);
            if (original != null) {
                boolean hasPatch = original.hasPatch() && hasAsset(context, original.patchAsset());
                return new State(hasPatch ? R.string.steering_patch_stock
                        : R.string.steering_patch_missing_asset, hasPatch, false, false);
            }

            Patch patched = findPatched(installed.version(), hash);
            if (patched != null) {
                boolean canRestore = hasBackup(context, patched)
                        || hasAsset(context, patched.originalAsset());
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

        Installed installed = findInstalled(context);
        if (installed == null) throw new IOException("Keyserver APK unavailable");
        String currentHash = sha256(installed.apk());

        Patch patch = install ? findOriginal(installed.version(), currentHash)
                : findPatched(installed.version(), currentHash);
        if (patch == null) throw new IOException("Unexpected installed keyserver APK");

        AtomicFile backup = backupFile(context, patch);
        File replacement;
        boolean temporary = false;
        if (install) {
            if (!patch.hasPatch() || !hasAsset(context, patch.patchAsset())) {
                throw new IOException("Patch APK is not bundled");
            }
            if (!hasBackup(context, patch)) {
                saveBackup(context, patch, installed.apk());
            }
            replacement = File.createTempFile("keyserver-patch-", ".apk", context.getCacheDir());
            temporary = true;
            copyAsset(context, patch.patchAsset(), replacement);
            if (!patch.patchHash().equalsIgnoreCase(sha256(replacement))) {
                throw new IOException("Unexpected replacement APK checksum");
            }
        } else {
            if (hasBackup(context, patch)) {
                replacement = backup.getBaseFile();
            } else if (hasAsset(context, patch.originalAsset())) {
                replacement = File.createTempFile("keyserver-original-", ".apk", context.getCacheDir());
                temporary = true;
                copyAsset(context, patch.originalAsset(), replacement);
            } else {
                throw new IOException("Original keyserver backup is missing");
            }
            if (!patch.originalHash().equalsIgnoreCase(sha256(replacement))) {
                throw new IOException("Unexpected original APK checksum");
            }
        }

        try {
            ApkInfo info = AppsRepository.inspect(context, replacement);
            if (!PACKAGE.equals(info.packageName) || info.versionCode != patch.version()
                    || !info.installed || info.installedVersionCode != patch.version()
                    || "?".equals(info.signer) || !info.signer.equals(info.installedSigner)) {
                throw new IOException("Unexpected keyserver package, version or certificate");
            }

            Installed current = findInstalled(context);
            if (current == null || !installed.apk().equals(current.apk())
                    || !currentHash.equalsIgnoreCase(sha256(current.apk()))) {
                throw new IOException("The keyserver APK changed while preparing installation");
            }

            AppsRepository.install(context, replacement, info, install ? INSTALL : RESTORE);
        } finally {
            if (temporary) replacement.delete();
        }
    }

    private static Patch findOriginal(long version, String hash) {
        for (Patch patch : PATCHES) {
            if (patch.version() == version && patch.originalHash().equalsIgnoreCase(hash)) {
                return patch;
            }
        }
        return null;
    }

    private static Patch findPatched(long version, String hash) {
        for (Patch patch : PATCHES) {
            if (patch.version() == version && patch.hasPatch()
                    && patch.patchHash().equalsIgnoreCase(hash)) {
                return patch;
            }
        }
        return null;
    }

    private static boolean hasPendingInstall(Context context) {
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        for (PackageInstaller.SessionInfo session : installer.getMySessions()) {
            if (PACKAGE.equals(session.getAppPackageName())
                    && (session.isActive() || session.isSealed())) {
                return true;
            }
        }
        return false;
    }

    private static Installed findInstalled(Context context) throws PackageManager.NameNotFoundException {
        PackageInfo info = context.getPackageManager().getPackageInfo(PACKAGE, 0);
        ApplicationInfo app = info.applicationInfo;
        if (app == null || app.sourceDir == null
                || (app.splitSourceDirs != null && app.splitSourceDirs.length > 0)) {
            return null;
        }
        return new Installed(info.getLongVersionCode(), new File(app.sourceDir));
    }

    private static AtomicFile backupFile(Context context, Patch patch) {
        String name = BACKUP_PREFIX + patch.version() + "-"
                + patch.originalHash().substring(0, 8).toLowerCase(Locale.ROOT) + ".apk";
        return new AtomicFile(new File(context.getFilesDir(), name));
    }

    private static boolean hasBackup(Context context, Patch patch) throws IOException {
        File file = backupFile(context, patch).getBaseFile();
        return file.isFile() && patch.originalHash().equalsIgnoreCase(sha256(file));
    }

    private static void saveBackup(Context context, Patch patch, File installed) throws IOException {
        AtomicFile backup = backupFile(context, patch);
        FileOutputStream out = backup.startWrite();
        try {
            MessageDigest digest = newDigest();
            try (InputStream original = new FileInputStream(installed)) {
                copy(original, out, digest);
            }
            if (!patch.originalHash().equalsIgnoreCase(hex(digest.digest()))) {
                throw new IOException("Original APK checksum does not match");
            }
            out.getFD().sync();
        } catch (IOException | RuntimeException e) {
            backup.failWrite(out);
            throw e;
        }
        backup.finishWrite(out);
        if (!hasBackup(context, patch)) {
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
        return context.getAssets().open(ASSET_DIR + "/" + name);
    }

    private static void copyAsset(Context context, String name, File target) throws IOException {
        try (InputStream in = openAsset(context, name);
             OutputStream out = new FileOutputStream(target)) {
            copy(in, out, null);
        }
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
