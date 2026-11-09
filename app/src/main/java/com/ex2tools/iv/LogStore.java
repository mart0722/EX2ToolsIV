package com.ex2tools.iv;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class LogStore {

    private static final long MAX_BYTES = 64 * 1024;

    private LogStore() {
    }

    private static File file(Context c) {
        Context ctx = c;
        try {
            ctx = c.createDeviceProtectedStorageContext();
        } catch (Throwable ignored) {
        }
        return new File(ctx.getFilesDir(), "ex2tools.log");
    }

    public static synchronized void add(Context c, String line) {
        try {
            String ts = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
            byte[] data = (ts + "  " + line + "\n").getBytes(StandardCharsets.UTF_8);
            File f = file(c);
            if (f.length() + data.length > MAX_BYTES) {
                f.delete();
            }
            FileOutputStream out = new FileOutputStream(f, true);
            out.write(data);
            out.close();
        } catch (Throwable ignored) {
        }
    }

    public static synchronized String read(Context c) {
        String s = readAll(c);
        if (s.length() > 6000) s = s.substring(s.length() - 6000);
        return s;
    }

    public static synchronized String readAll(Context c) {
        try {
            File f = file(c);
            if (!f.exists()) return "";
            FileInputStream in = new FileInputStream(f);
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] tmp = new byte[4096];
            int n;
            while ((n = in.read(tmp)) > 0) buf.write(tmp, 0, n);
            in.close();
            return new String(buf.toByteArray(), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return "";
        }
    }

    public static synchronized void clear(Context c) {
        try {
            file(c).delete();
        } catch (Throwable ignored) {
        }
    }
}
