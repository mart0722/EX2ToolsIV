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
            if (!Prefs.statusBrightness(context)) return;
            boolean restoring = Prefs.statusBrightnessApplied(context) != 0;
            int current = SystemControls.brightnessPercent(context);
            int target = Prefs.statusBrightnessNextPct(context);
            if (!SystemControls.setBrightnessPercent(context, target)) {
                LogStore.add(context, "brightness status action could not set " + target + "%");
                return;
            }
            if (!restoring) Prefs.setStatusBrightnessSaved(context, current);
            Prefs.setStatusBrightnessApplied(context, restoring ? 0 : 1);
            String message = restoring ? context.getString(R.string.status_brightness_restored)
                    : context.getString(R.string.status_brightness_applied, target);
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            LogStore.add(context, "brightness status action failed: " + t);
        }
    }
}
