package com.ex2tools.iv.status;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.R;
import com.ex2tools.iv.voice.SystemControls;

public final class BrightnessActionReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            int applied = Prefs.statusBrightnessApplied(context);
            if (applied == 0) {
                int saved = SystemControls.brightnessPercent(context);
                Prefs.setStatusBrightnessSaved(context, saved);
                int pct = Prefs.statusBrightnessPct(context);
                SystemControls.setBrightnessPercent(context, pct);
                Prefs.setStatusBrightnessApplied(context, 1);
                Toast.makeText(context, context.getString(R.string.status_brightness_applied, pct),
                        Toast.LENGTH_SHORT).show();
            } else {
                int saved = Prefs.statusBrightnessSaved(context);
                SystemControls.setBrightnessPercent(context, saved < 0 ? 50 : saved);
                Prefs.setStatusBrightnessApplied(context, 0);
                Toast.makeText(context, R.string.status_brightness_restored, Toast.LENGTH_SHORT).show();
            }
        } catch (Throwable t) {
            LogStore.add(context, "brightness status action failed: " + t);
        }
    }
}
