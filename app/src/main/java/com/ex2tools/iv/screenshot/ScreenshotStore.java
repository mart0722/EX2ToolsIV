package com.ex2tools.iv.screenshot;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class ScreenshotStore {

    private ScreenshotStore() {
    }

    public static Uri save(Context c, byte[] png) {
        String name = "EX2ToolsIV_" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
                .format(new Date()) + ".png";
        ContentResolver resolver = c.getContentResolver();
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/EX2ToolsIV");
                values.put(MediaStore.Images.Media.IS_PENDING, 1);
                Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) return null;
                OutputStream out = resolver.openOutputStream(uri);
                if (out == null) return null;
                out.write(png);
                out.close();
                values.clear();
                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                resolver.update(uri, values, null, null);
                return uri;
            }

            Bitmap bmp = BitmapFactory.decodeByteArray(png, 0, png.length);
            if (bmp != null) {
                String path = MediaStore.Images.Media.insertImage(
                        resolver, bmp, name, "EX2ToolsIV screenshot");
                if (path != null) return Uri.parse(path);
            }
        } catch (Throwable ignored) {
        }

        // Fallback: app-specific external storage, always writable without
        // the legacy storage permission.
        try {
            File dir = new File(c.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Screenshots");
            if (!dir.exists() && !dir.mkdirs()) return null;
            File file = new File(dir, name);
            FileOutputStream out = new FileOutputStream(file);
            out.write(png);
            out.close();
            MediaScannerConnection.scanFile(c, new String[]{file.getAbsolutePath()},
                    new String[]{"image/png"}, null);
            return Uri.fromFile(file);
        } catch (Throwable t) {
            return null;
        }
    }
}
