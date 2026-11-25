package com.ex2tools.iv.widgets;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.util.Log;

import com.ex2tools.iv.LogStore;

import java.util.List;

/**
 * Handles the standard APPWIDGET_BIND fallback for third-party launchers.
 *
 * Flyme images used by the EX2 do not ship an activity for
 * android.appwidget.action.APPWIDGET_BIND. A user-installed launcher without the
 * signature|privileged BIND_APPWIDGET permission therefore cannot bind any widget.
 * EX2ToolsIV runs as android.uid.system, so it can bind the widget on the
 * launcher's behalf and return the result the launcher expects.
 */
public final class AppWidgetBindActivity extends Activity {

    private static final String TAG = "EX2WidgetBind";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Intent intent = getIntent();
        int id = intent == null ? -1
                : intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        ComponentName provider = intent == null ? null
                : intent.getParcelableExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER);
        String caller = getCallingPackage();
        if (caller == null && getReferrer() != null) {
            caller = getReferrer().getHost();
        }

        if (id < 0 || provider == null || !isAllowedCaller(caller)) {
            Log.w(TAG, "refused id=" + id + " provider=" + provider + " caller=" + caller);
            LogStore.add(this, "widget bind refused: id=" + id + " provider=" + provider
                    + " caller=" + caller);
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        try {
            boolean bound = AppWidgetManager.getInstance(this)
                    .bindAppWidgetIdIfAllowed(id, provider);
            if (bound) {
                Log.i(TAG, "bound id=" + id + " provider=" + provider + " caller=" + caller);
                LogStore.add(this, "widget bound: id=" + id + " provider=" + provider
                        + " caller=" + caller);
                setResult(RESULT_OK, new Intent()
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id));
            } else {
                Log.w(TAG, "bind refused id=" + id + " provider=" + provider + " caller=" + caller);
                LogStore.add(this, "widget bind rejected: id=" + id + " provider=" + provider
                        + " caller=" + caller);
                setResult(RESULT_CANCELED);
            }
        } catch (Throwable t) {
            Log.w(TAG, "bind failed id=" + id + " provider=" + provider, t);
            LogStore.add(this, "widget bind failed: id=" + id + " provider=" + provider + ": " + t);
            setResult(RESULT_CANCELED);
        }
        finish();
    }

    /**
     * Only the current home applications (and our own/shell test paths) may use
     * this bridge. Without this check any app could bind a widget into another
     * host's id and put content on the user's home screen.
     */
    private boolean isAllowedCaller(String caller) {
        if (caller == null || caller.equals(getPackageName())
                || caller.equals("com.android.shell")) {
            return true;
        }
        try {
            Intent home = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME);
            List<ResolveInfo> launchers = getPackageManager()
                    .queryIntentActivities(home, 0);
            for (ResolveInfo info : launchers) {
                if (info.activityInfo != null
                        && caller.equals(info.activityInfo.packageName)) {
                    return true;
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "caller lookup failed", t);
        }
        return false;
    }
}
