package com.ex2tools.iv.apps;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

import com.ex2tools.iv.Actions;
import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.R;
import com.ex2tools.iv.voice.SteeringKeysService;

public final class InstallResultReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Actions.APP_INSTALL_RESULT.equals(intent.getAction())) return;
        Context c = context.getApplicationContext();
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1);
        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        String pkg = intent.getStringExtra(Actions.EXTRA_PACKAGE);
        String mode = intent.getStringExtra(Actions.EXTRA_MODE);
        boolean restoreSteering = SteeringPatch.RESTORE.equals(mode);
        boolean steering = SteeringPatch.PACKAGE.equals(pkg)
                && (restoreSteering || SteeringPatch.INSTALL.equals(mode));
        LogStore.add(c, "installer " + mode + " " + pkg + " status=" + status
                + (message == null ? "" : " " + message));

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                try {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    c.startActivity(confirm);
                    return;
                } catch (Throwable t) {
                    LogStore.add(c, "installer confirm start failed: " + t);
                }
            }
            if (steering) {
                int sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1);
                if (sessionId >= 0) {
                    try {
                        c.getPackageManager().getPackageInstaller().abandonSession(sessionId);
                    } catch (RuntimeException e) {
                        LogStore.add(c, "steering installer abandon: " + e);
                    }
                }
            }
        }

        boolean ok = status == PackageInstaller.STATUS_SUCCESS;
        if (steering) {
            int feedback = R.string.steering_patch_failed;
            if (ok) {
                if (restoreSteering) {
                    Prefs.setSteeringClimate(c, false);
                    SteeringKeysService.sync(c);
                }
                feedback = SteeringKeysService.startKeyServer(c)
                        ? (restoreSteering ? R.string.steering_patch_restored : R.string.steering_patch_installed)
                        : R.string.steering_patch_restart_failed;
            }
            Toast.makeText(c, feedback, Toast.LENGTH_LONG).show();
            AppsRepository.broadcastRefresh(c);
            return;
        }
        String text = ok ? null
                : message == null || message.isEmpty() ? "status " + status : message;
        try {
            Intent ui = new Intent(c, InstallResultActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(Actions.EXTRA_OK, ok)
                    .putExtra(Actions.EXTRA_PACKAGE, pkg)
                    .putExtra(Actions.EXTRA_MODE, mode)
                    .putExtra(Actions.EXTRA_ERROR, text);
            c.startActivity(ui);
        } catch (Throwable t) {
            LogStore.add(c, "install result ui failed: " + t);
            Toast.makeText(c, ok ? c.getString(R.string.apps_result_ok, pkg)
                    : c.getString(R.string.apps_result_fail, text), Toast.LENGTH_LONG).show();
        }
        AppsRepository.broadcastRefresh(c);
    }
}
