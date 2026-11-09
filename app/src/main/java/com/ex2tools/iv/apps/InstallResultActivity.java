package com.ex2tools.iv.apps;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;

import com.ex2tools.iv.Actions;
import com.ex2tools.iv.R;
import com.ex2tools.iv.ui.FlymeDialog;

public final class InstallResultActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent source = getIntent();
        boolean ok = source != null && source.getBooleanExtra(Actions.EXTRA_OK, false);
        String pkg = source == null ? null : source.getStringExtra(Actions.EXTRA_PACKAGE);
        String error = source == null ? null : source.getStringExtra(Actions.EXTRA_ERROR);
        String mode = source == null ? null : source.getStringExtra(Actions.EXTRA_MODE);
        boolean uninstall = "uninstall".equals(mode);
        String title = getString(ok ? (uninstall ? R.string.apps_uninstall_success_title
                : R.string.apps_install_success_title) : R.string.apps_install_error_title);
        String message;
        if (ok) {
            message = getString(R.string.apps_install_success_message,
                    pkg == null ? "" : pkg, getString(R.string.apps_install_result_done));
        } else {
            message = error == null || error.isEmpty()
                    ? getString(R.string.apps_install_failed, "") : error;
        }

        Intent launch = null;
        if (ok && !uninstall && pkg != null) {
            try {
                launch = getPackageManager().getLaunchIntentForPackage(pkg);
            } catch (Throwable ignored) {
            }
        }
        if (launch != null) {
            final Intent open = launch;
            Dialog dialog = FlymeDialog.showConfirm(this, title, message,
                    getString(R.string.apps_install_result_open),
                    getString(R.string.apps_install_result_done), () -> {
                        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        try {
                            startActivity(open);
                        } catch (Throwable ignored) {
                        }
                        finish();
                    });
            dialog.setOnDismissListener(d -> finish());
        } else {
            FlymeDialog.showMessage(this, title, message,
                    getString(R.string.apps_install_result_done), this::finish);
        }
    }
}
