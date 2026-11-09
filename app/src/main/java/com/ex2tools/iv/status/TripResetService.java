package com.ex2tools.iv.status;

import android.app.Dialog;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.IBinder;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.NotificationHelper;
import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.R;
import com.ex2tools.iv.car.Telemetry;
import com.ex2tools.iv.ui.FlymePress;

public final class TripResetService extends Service {

    private Dialog dialog;

    public static void start(Context c) {
        try {
            ContextCompat.startForegroundService(c,
                    new Intent(c, TripResetService.class));
        } catch (Throwable t) {
            LogStore.add(c, "start trip reset failed: " + t);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(NotificationHelper.ID_TRIP_RESET, NotificationHelper.tripReset(this));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (dialog == null) showDialog();
        return START_NOT_STICKY;
    }

    private void showDialog() {
        try {
            dialog = new Dialog(this, R.style.Theme_EX2ToolsIV_Dialog);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.setContentView(R.layout.dialog_trip_reset);
            dialog.setCanceledOnTouchOutside(true);

            Window window = dialog.getWindow();
            if (window != null) {
                window.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                window.setDimAmount(0.5f);
                window.setGravity(Gravity.CENTER);
                window.setLayout(dp(440), WindowManager.LayoutParams.WRAP_CONTENT);
            }

            TextView message = dialog.findViewById(R.id.tripResetMessage);
            TextView current = dialog.findViewById(R.id.tripResetCurrent);
            TextView odometer = dialog.findViewById(R.id.tripResetOdometer);
            View confirm = dialog.findViewById(R.id.tripResetConfirm);
            View cancel = dialog.findViewById(R.id.tripResetCancel);
            FlymePress.bind(confirm);
            FlymePress.bind(cancel);

            float odo = Telemetry.odometerKm(this);
            if (Float.isNaN(odo)) {
                message.setText(R.string.status_trip_reset_unavailable);
                current.setText(R.string.status_unavailable);
                odometer.setText("");
                FlymePress.setEnabled(confirm, false);
            } else {
                float start = Prefs.tripStartKm(this);
                int trip = start <= 0f ? 0 : Math.round(Math.max(0f, odo - start));
                int total = Math.round(odo);
                current.setText(getString(R.string.trip_reset_current_fmt, trip));
                odometer.setText(getString(R.string.trip_reset_odometer_fmt, total));
                confirm.setOnClickListener(v -> {
                    Prefs.setTripStartKm(this, odo);
                    StatusIconService.refresh(this);
                    Toast.makeText(this, R.string.status_trip_reset_done, Toast.LENGTH_SHORT).show();
                    dismissAndStop();
                });
            }

            cancel.setOnClickListener(v -> dismissAndStop());
            dialog.setOnDismissListener(d -> {
                dialog = null;
                stopSelf();
            });
            dialog.show();
        } catch (Throwable t) {
            LogStore.add(this, "trip reset dialog failed: " + t);
            Toast.makeText(this, R.string.status_trip_reset_unavailable, Toast.LENGTH_SHORT).show();
            stopSelf();
        }
    }

    private void dismissAndStop() {
        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }
        stopSelf();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroy() {
        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }
        stopForeground(true);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
