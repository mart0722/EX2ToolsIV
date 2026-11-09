package com.ex2tools.iv.screenshot;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.R;

public final class ScreenshotTaker {

    public interface Callback {
        void onResult(boolean ok, String message);
    }

    private ScreenshotTaker() {
    }

    public static void take(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        new Thread(() -> {
            boolean ok = false;
            String message;
            try {
                byte[] png = LocalAdb.capture(app);
                Uri uri = ScreenshotStore.save(app, png);
                if (uri == null) throw new IllegalStateException("save failed");
                ok = true;
                message = uri.toString();
                LogStore.add(app, "screenshot saved: " + uri);
            } catch (Throwable t) {
                message = app.getString(R.string.screenshot_failed)
                        + (t.getMessage() == null ? "" : ": " + t.getMessage());
                LogStore.add(app, "screenshot failed: " + t);
            }
            final boolean resultOk = ok;
            final String resultMessage = message;
            new Handler(Looper.getMainLooper()).post(() ->
                    callback.onResult(resultOk, resultMessage));
        }, "ex2-screenshot").start();
    }
}
