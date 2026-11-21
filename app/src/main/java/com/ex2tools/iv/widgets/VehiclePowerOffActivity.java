package com.ex2tools.iv.widgets;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.R;
import com.ex2tools.iv.body.BodyController;

public final class VehiclePowerOffActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            if (savedInstanceState != null) return;
            if (Prefs.widgetPowerOffWithoutConfirmation(this)) {
                int message = switch (BodyController.get().requestParkedPowerOff(this)) {
                    case SENT -> 0;
                    case NOT_PARKED -> R.string.widget_power_off_park_only;
                    case UNAVAILABLE -> R.string.widget_power_off_direct_unavailable;
                };
                if (message != 0) Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            } else {
                startActivity(new Intent("com.flyme.auto.settings.action.CAR_POWER_OFF")
                        .setPackage("com.flyme.auto.settings"));
            }
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.widget_power_off_unavailable, Toast.LENGTH_SHORT).show();
        } finally {
            finish();
        }
    }
}
