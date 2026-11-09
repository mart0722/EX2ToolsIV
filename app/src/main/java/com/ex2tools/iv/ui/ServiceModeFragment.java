package com.ex2tools.iv.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ex2tools.iv.MainActivity;
import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.R;
import com.ex2tools.iv.servicemode.AppEntry;
import com.ex2tools.iv.servicemode.AppListAdapter;
import com.ex2tools.iv.servicemode.ServiceModeManager;
import com.google.android.material.button.MaterialButton;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ServiceModeFragment extends Fragment {

    private RecyclerView list;
    private TextView empty;
    private TextView badge;
    private TextView info;
    private MaterialButton action;
    private MaterialButton selectAll;
    private FlymeSwitchRow hideSelf;
    private FlymeSwitchRow triggerLeft;
    private FlymeSwitchRow triggerRight;
    private AppListAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_service_mode, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Context c = requireContext();
        list = view.findViewById(R.id.appsList);
        empty = view.findViewById(R.id.appsEmpty);
        badge = view.findViewById(R.id.serviceBadge);
        info = view.findViewById(R.id.serviceInfo);
        action = view.findViewById(R.id.serviceAction);
        selectAll = view.findViewById(R.id.selectAll);
        hideSelf = view.findViewById(R.id.hideSelf);
        triggerLeft = view.findViewById(R.id.triggerLeft);
        triggerRight = view.findViewById(R.id.triggerRight);

        triggerLeft.setChecked(Prefs.triggerLeft(c));
        triggerLeft.setOnCheckedChangeListener((b, checked) -> {
            Prefs.setTrigger(c, "trigger_left", checked);
            ServiceModeManager.restartTriggerIfActive(c);
        });
        triggerRight.setChecked(Prefs.triggerRight(c));
        triggerRight.setOnCheckedChangeListener((b, checked) -> {
            Prefs.setTrigger(c, "trigger_right", checked);
            ServiceModeManager.restartTriggerIfActive(c);
        });
        view.findViewById(R.id.testTrigger).setOnClickListener(v -> {
            ServiceModeManager.testTrigger(c);
            Toast.makeText(c, R.string.trigger_note_short, Toast.LENGTH_LONG).show();
        });

        view.findViewById(R.id.showCorners).setOnClickListener(v -> {
            if (!ServiceModeManager.canUseOverlay(c)) {
                Toast.makeText(c, R.string.overlay_permission_needed, Toast.LENGTH_LONG).show();
                if (requireActivity() instanceof MainActivity) {
                    ((MainActivity) requireActivity()).openOverlaySettings();
                }
                return;
            }
            ServiceModeManager.showCorners(c);
            Toast.makeText(c, R.string.corners_shown, Toast.LENGTH_SHORT).show();
        });

        list.setLayoutManager(new LinearLayoutManager(c));
        view.findViewById(R.id.reloadApps).setOnClickListener(v -> loadApps());

        selectAll.setOnClickListener(v -> {
            if (adapter == null) return;
            adapter.setAllVisibleSelected(!adapter.allVisibleSelected());
            updateInfo();
            updateEmpty();
        });

        hideSelf.setChecked(Prefs.hideSelf(c));
        hideSelf.setOnCheckedChangeListener((b, checked) -> Prefs.setHideSelf(c, checked));

        action.setOnClickListener(v -> {
            if (ServiceModeManager.isActive(requireContext())) restoreAll();
            else hideSelected();
        });

        updateInfo();
        loadApps();
    }

    private void loadApps() {
        Context c = getContext();
        if (c == null) return;
        empty.setVisibility(View.VISIBLE);
        empty.setText(R.string.apps_loading);
        new Thread(() -> {
            List<AppEntry> apps = ServiceModeManager.listThirdParty(c);
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                adapter = new AppListAdapter(apps, (pkg, hide) -> {
                    Set<String> excluded = new LinkedHashSet<>(Prefs.excluded(requireContext()));
                    if (hide) excluded.remove(pkg);
                    else excluded.add(pkg);
                    Prefs.setExcluded(requireContext(), excluded);
                    updateInfo();
                });
                list.setAdapter(adapter);
                updateEmpty();
                updateInfo();
            });
        }).start();
    }

    private void hideSelected() {
        if (adapter == null) return;
        List<String> selected = adapter.selectedPackages();
        if (selected.isEmpty()) {
            Toast.makeText(requireContext(), R.string.no_selection, Toast.LENGTH_SHORT).show();
            return;
        }
        Context c = requireContext();
        boolean canRestore = Prefs.triggerLeft(c) || Prefs.triggerRight(c);
        if (!canRestore) {
            Toast.makeText(c, R.string.service_no_restore, Toast.LENGTH_LONG).show();
            return;
        }
        StringBuilder message = new StringBuilder();
        message.append(getString(R.string.service_hide_count, selected.size()))
                .append("\n\n")
                .append(getString(R.string.service_hide_methods))
                .append('\n');
        if (Prefs.triggerLeft(c)) {
            message.append(getString(R.string.service_hide_by_left)).append('\n');
        }
        if (Prefs.triggerRight(c)) {
            message.append(getString(R.string.service_hide_by_right)).append('\n');
        }
        message.append('\n').append(getString(R.string.service_hide_note));

        FlymeDialog.showConfirmLeft(requireContext(),
                getString(R.string.service_hide_title), message,
                getString(R.string.service_hide_confirm), getString(R.string.cancel), () -> {
                    new Thread(() -> {
                        int n = ServiceModeManager.hideSelected(c, selected);
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(c, getString(R.string.hide_success) + " (" + n + ")",
                                    Toast.LENGTH_SHORT).show();
                            loadApps();
                            updateInfo();
                        });
                    }).start();
                });
    }

    private void restoreAll() {
        FlymeDialog.showConfirm(requireContext(),
                getString(R.string.service_restore),
                getString(R.string.restore_confirm),
                getString(R.string.yes), getString(R.string.cancel), () -> {
                    Context c = requireContext();
                    new Thread(() -> {
                        ServiceModeManager.restoreAll(c);
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(c, R.string.restore_success, Toast.LENGTH_SHORT).show();
                            loadApps();
                            updateInfo();
                        });
                    }).start();
                });
    }

    private void updateInfo() {
        if (!isAdded()) return;
        Context c = requireContext();
        boolean active = ServiceModeManager.isActive(c);
        badge.setText(active ? R.string.service_active : R.string.service_inactive);
        badge.setTextColor(getResources().getColor(active ? R.color.emerald : R.color.text_dim));
        int selected = adapter == null ? 0 : adapter.selectedCount();
        info.setText(getString(R.string.service_info_fmt, ServiceModeManager.hiddenCount(c), selected));
        action.setText(active ? R.string.service_restore : R.string.service_hide);
        hideSelf.setChecked(Prefs.hideSelf(c));
        if (triggerLeft != null) triggerLeft.setChecked(Prefs.triggerLeft(c));
        if (triggerRight != null) triggerRight.setChecked(Prefs.triggerRight(c));
        if (selectAll != null) {
            selectAll.setText(adapter != null && adapter.allVisibleSelected()
                    ? R.string.clear_all : R.string.select_all);
        }
    }

    private void updateEmpty() {
        if (empty == null) return;
        boolean none = adapter == null || adapter.visibleCount() == 0;
        empty.setVisibility(none ? View.VISIBLE : View.GONE);
        empty.setText(R.string.no_apps);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateInfo();
    }
}
