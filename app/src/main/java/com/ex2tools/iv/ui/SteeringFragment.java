package com.ex2tools.iv.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.ex2tools.iv.Actions;
import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.R;
import com.ex2tools.iv.apps.SteeringPatch;
import com.ex2tools.iv.voice.SteeringButton;
import com.ex2tools.iv.voice.SteeringKeysService;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SteeringFragment extends Fragment {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable retry = this::refreshState;
    private boolean reading;
    private boolean refreshRequested;
    private boolean changing;
    private boolean started;

    private final BroadcastReceiver updates = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Actions.APPS_REFRESH.equals(intent.getAction())
                    || (intent.getData() != null
                    && SteeringPatch.PACKAGE.equals(intent.getData().getSchemeSpecificPart()))) {
                refreshState();
            }
        }
    };

    public SteeringFragment() {
        super(R.layout.fragment_steering);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        view.findViewById(R.id.steeringClimate).setEnabled(false);
        view.findViewById(R.id.steeringInstallPatch).setOnClickListener(v -> confirmChange(true));
        view.findViewById(R.id.steeringRestoreOriginal).setOnClickListener(v -> confirmChange(false));
        bindAssignments(view.findViewById(R.id.steeringAssignments));
    }

    private void bindAssignments(LinearLayout list) {
        Context context = list.getContext();
        LayoutInflater inflater = LayoutInflater.from(context);
        SteeringButton.Action[] actions = SteeringButton.Action.values();
        CharSequence[] labels = new CharSequence[actions.length];
        for (int i = 0; i < actions.length; i++) labels[i] = context.getText(actions[i].label);

        for (SteeringButton button : SteeringButton.values()) {
            View row = inflater.inflate(R.layout.row_steering_assignment, list, false);
            TextView title = row.findViewById(R.id.steeringButtonLabel);
            TextView assignment = row.findViewById(R.id.steeringActionLabel);
            title.setText(button.label);
            assignment.setText(button.action(context).label);
            FlymePress.bind(row);
            row.setOnClickListener(v -> new MaterialAlertDialogBuilder(context)
                    .setTitle(button.label)
                    .setSingleChoiceItems(labels, button.action(context).ordinal(), (dialog, index) -> {
                        SteeringButton.Action action = actions[index];
                        button.setAction(context, action);
                        assignment.setText(action.label);
                        dialog.dismiss();
                    })
                    .setNegativeButton(R.string.confirm_cancel, null)
                    .show());
            list.addView(row);
        }
    }

    @Override public void onStart() {
        super.onStart();
        started = true;
        IntentFilter filter = new IntentFilter(Actions.APPS_REFRESH);
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        ContextCompat.registerReceiver(requireContext(), updates, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        refreshState();
    }

    @Override public void onStop() {
        started = false;
        main.removeCallbacks(retry);
        requireContext().unregisterReceiver(updates);
        super.onStop();
    }

    private void refreshState() {
        View view = getView();
        if (!started || view == null || changing) return;
        if (reading) {
            refreshRequested = true;
            return;
        }
        main.removeCallbacks(retry);
        refreshRequested = false;
        reading = true;
        Context app = requireContext().getApplicationContext();
        WORKER.execute(() -> {
            SteeringPatch.State state = SteeringPatch.readState(app);
            main.post(() -> {
                reading = false;
                if (!started || changing) return;
                if (refreshRequested || getView() != view) {
                    refreshState();
                    return;
                }
                render(view, state);
                if (state.busy()) main.postDelayed(retry, 1000L);
            });
        });
    }

    private void render(View view, SteeringPatch.State state) {
        TextView status = view.findViewById(R.id.steeringPatchStatus);
        status.setText(state.message());
        FlymePress.setEnabled(view.findViewById(R.id.steeringInstallPatch), state.canInstall());
        FlymePress.setEnabled(view.findViewById(R.id.steeringRestoreOriginal), state.canRestore());

        Context c = view.getContext();
        FlymeSwitchRow toggle = view.findViewById(R.id.steeringClimate);
        boolean enabled = Prefs.steeringClimate(c);
        toggle.setOnCheckedChangeListener(null);
        toggle.setChecked(enabled);
        toggle.setEnabled(!state.busy() && (state.patched() || enabled));
        toggle.setOnCheckedChangeListener((button, checked) -> {
            Prefs.setSteeringClimate(c, checked);
            SteeringKeysService.sync(c);
            toggle.setEnabled(state.patched() || checked);
        });
    }

    private void confirmChange(boolean install) {
        Context c = requireContext();
        String action = getString(install ? R.string.steering_patch_install : R.string.steering_patch_restore);
        FlymeDialog.showConfirm(c, action,
                getString(install ? R.string.steering_patch_confirm_install : R.string.steering_patch_confirm_restore),
                action, getString(R.string.confirm_cancel), () -> changePatch(install));
    }

    private void changePatch(boolean install) {
        View view = getView();
        if (view == null || changing) return;
        changing = true;
        render(view, new SteeringPatch.State(R.string.steering_patch_busy, false, false, false));
        Context app = view.getContext().getApplicationContext();
        WORKER.execute(() -> {
            try {
                SteeringPatch.change(app, install);
            } catch (Exception e) {
                LogStore.add(app, "steering patch " + (install ? "install" : "restore") + ": " + e);
                main.post(() -> Toast.makeText(app, R.string.steering_patch_failed, Toast.LENGTH_LONG).show());
            } finally {
                main.post(() -> {
                    changing = false;
                    refreshState();
                });
            }
        });
    }
}
