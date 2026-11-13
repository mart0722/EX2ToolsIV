package com.ex2tools.iv.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.R;
import com.ex2tools.iv.apps.AppsRepository;
import com.ex2tools.iv.apps.InstalledApp;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Flyme-style multi-select picker for the "Приложения" overlay widget.
 * Selection order is preserved and becomes the order of icons.
 */
public final class AppsPickerDialog {

    public interface Listener {
        void onSaved();
    }

    private final Context context;
    private final Listener listener;
    private final Dialog dialog;
    private final LinkedHashSet<String> selected = new LinkedHashSet<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private AppsPickerAdapter adapter;
    private TextView counter;
    private TextView empty;
    private TextView loading;

    private AppsPickerDialog(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
        this.selected.addAll(Prefs.overlayApps(context));

        dialog = new Dialog(context, R.style.Theme_EX2ToolsIV_Dialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_apps_picker);
        dialog.setCanceledOnTouchOutside(true);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.55f);
            window.setGravity(Gravity.CENTER);
            int width = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.66f);
            int height = (int) (context.getResources().getDisplayMetrics().heightPixels * 0.84f);
            window.setLayout(width, height);
        }

        counter = dialog.findViewById(R.id.appsPickerCounter);
        empty = dialog.findViewById(R.id.appsPickerEmpty);
        loading = dialog.findViewById(R.id.appsPickerLoading);

        RecyclerView list = dialog.findViewById(R.id.appsPickerList);
        list.setLayoutManager(new LinearLayoutManager(context));
        adapter = new AppsPickerAdapter(selected, new AppsPickerAdapter.Listener() {
            @Override
            public void onSelectionChanged(int count) {
                updateCounter();
            }

            @Override
            public void onLimitReached() {
                Toast.makeText(context, R.string.widgets_apps_limit, Toast.LENGTH_SHORT).show();
            }
        });
        list.setAdapter(adapter);

        EditText search = dialog.findViewById(R.id.appsPickerSearch);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s == null ? "" : s.toString());
                updateEmpty();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        TextView cancel = dialog.findViewById(R.id.appsPickerCancel);
        TextView save = dialog.findViewById(R.id.appsPickerSave);
        FlymePress.bind(cancel);
        FlymePress.bind(save);
        cancel.setOnClickListener(v -> dialog.dismiss());
        save.setOnClickListener(v -> {
            Prefs.setOverlayApps(context, currentLaunchableSelection());
            dialog.dismiss();
            if (listener != null) listener.onSaved();
        });

        updateCounter();
        dialog.setOnDismissListener(d -> io.shutdownNow());
    }

    public static void show(Context context, Listener listener) {
        new AppsPickerDialog(context, listener).open();
    }

    private void open() {
        dialog.show();
        load();
    }

    private void load() {
        loading.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);
        io.execute(() -> {
            List<InstalledApp> apps = AppsRepository.loadLaunchable(context.getApplicationContext());
            main.post(() -> {
                if (!dialog.isShowing()) return;
                pruneMissing(apps);
                adapter.setItems(apps);
                loading.setVisibility(View.GONE);
                updateEmpty();
            });
        });
    }

    private void updateCounter() {
        if (counter != null) {
            counter.setText(context.getString(R.string.widgets_apps_counter, selected.size()));
        }
    }

    private void pruneMissing(List<InstalledApp> apps) {
        Set<String> available = new HashSet<>();
        for (InstalledApp app : apps) available.add(app.packageName);
        if (selected.retainAll(available)) updateCounter();
    }

    private List<String> currentLaunchableSelection() {
        List<String> out = new ArrayList<>();
        PackageManager pm = context.getPackageManager();
        for (String pkg : selected) {
            try {
                if (pm.getLaunchIntentForPackage(pkg) != null) out.add(pkg);
            } catch (Throwable ignored) {
            }
        }
        return out;
    }

    private void updateEmpty() {
        if (empty == null) return;
        boolean show = loading.getVisibility() != View.VISIBLE
                && adapter != null && adapter.getItemCount() == 0;
        if (show && adapter != null) {
            empty.setText(adapter.totalCount() == 0
                    ? R.string.widgets_apps_empty : R.string.widgets_apps_not_found);
        }
        empty.setVisibility(show ? View.VISIBLE : View.GONE);
    }
}
