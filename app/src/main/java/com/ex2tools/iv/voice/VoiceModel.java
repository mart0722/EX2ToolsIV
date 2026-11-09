package com.ex2tools.iv.voice;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class VoiceModel {

    private static final String TAG = "VoiceModel";
    private static final String ASSET_ROOT = "model-ru";

    private VoiceModel() {
    }

    public static File dir(Context c) {
        return new File(c.getFilesDir(), "voice-model/model-ru");
    }

    public static synchronized File ensure(Context c) throws IOException {
        File dir = dir(c);
        File marker = new File(dir, "am/final.mdl");
        if (marker.isFile()) return dir;

        File parent = dir.getParentFile();
        if (parent != null && parent.exists()) delete(parent);
        if (parent != null) parent.mkdirs();
        Log.i(TAG, "unpacking model to " + dir);
        copy(c.getAssets(), ASSET_ROOT, parent);
        if (!marker.isFile()) {
            throw new IOException("model-ru not found in assets");
        }
        return dir;
    }

    private static void copy(AssetManager assets, String path, File target) throws IOException {
        String[] children = assets.list(path);
        if (children == null) children = new String[0];
        File out = new File(target, path);
        if (children.length > 0) {
            if (!out.mkdirs() && !out.isDirectory()) throw new IOException("mkdirs " + out);
            for (String child : children) copy(assets, path + "/" + child, target);
            return;
        }
        File parent = out.getParentFile();
        if (parent != null) parent.mkdirs();
        try (InputStream in = assets.open(path);
             FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) fos.write(buffer, 0, read);
            fos.getFD().sync();
        }
    }

    private static void delete(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) delete(child);
        }
        file.delete();
    }
}
