package com.ex2tools.iv.apps;

import android.Manifest;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ex2tools.iv.Actions;
import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.R;
import com.ex2tools.iv.ui.FlymeDialog;
import com.ex2tools.iv.ui.FlymePress;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AppsFragment extends Fragment {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable reloadRunnable = this::loadApps;

    private RecyclerView list;
    private AppsAdapter adapter;
    private EditText search;
    private ChipGroup filters;
    private TextView summary;
    private TextView empty;
    private ProgressBar loading;
    private View usb;
    private View qr;
    private ActivityResultLauncher<String[]> picker;
    private View detailEmpty;
    private View detailPanel;
    private ImageView detailIcon;
    private TextView detailName;
    private TextView detailType;
    private TextView detailPackage;
    private TextView detailVersion;
    private TextView detailSignature;
    private MaterialButton detailOpen;
    private MaterialButton detailUninstall;
    private MaterialButton detailPermissions;
    private InstalledApp selected;

    private QrUploadServer qrServer;
    private Dialog qrDialog;
    private ImageView qrImage;
    private TextView qrUrl;
    private TextView qrStatus;
    private ProgressBar qrProgress;

    private volatile boolean loadingApps;
    private static final int REQ_STORAGE = 71;

    private final BroadcastReceiver refreshReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            scheduleReload();
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_apps, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Context c = requireContext();
        list = view.findViewById(R.id.appsList);
        search = view.findViewById(R.id.appSearch);
        filters = view.findViewById(R.id.appFilterGroup);
        summary = view.findViewById(R.id.appsSummary);
        empty = view.findViewById(R.id.appsEmpty);
        loading = view.findViewById(R.id.appsLoading);
        usb = view.findViewById(R.id.appsUsb);
        qr = view.findViewById(R.id.appsQr);
        detailEmpty = view.findViewById(R.id.detailEmpty);
        detailPanel = view.findViewById(R.id.detailPanel);
        detailIcon = view.findViewById(R.id.detailIcon);
        detailName = view.findViewById(R.id.detailName);
        detailType = view.findViewById(R.id.detailType);
        detailOpen = view.findViewById(R.id.detailOpen);
        detailUninstall = view.findViewById(R.id.detailUninstall);
        detailPermissions = view.findViewById(R.id.detailPermissions);

        View rowPackage = view.findViewById(R.id.rowPackage);
        View rowVersion = view.findViewById(R.id.rowVersion);
        View rowSignature = view.findViewById(R.id.rowSignature);
        ((TextView) rowPackage.findViewById(R.id.rowLabel)).setText(R.string.apps_detail_package);
        ((TextView) rowVersion.findViewById(R.id.rowLabel)).setText(R.string.apps_detail_version);
        ((TextView) rowSignature.findViewById(R.id.rowLabel)).setText(R.string.apps_detail_signature);
        detailPackage = rowPackage.findViewById(R.id.rowValue);
        detailVersion = rowVersion.findViewById(R.id.rowValue);
        detailSignature = rowSignature.findViewById(R.id.rowValue);

        list.setLayoutManager(new LinearLayoutManager(c));
        adapter = new AppsAdapter(this::selectApp);
        list.setAdapter(adapter);
        detailOpen.setOnClickListener(v -> openSelected());
        detailUninstall.setOnClickListener(v -> {
            if (selected != null) confirmUninstall(selected);
        });
        detailPermissions.setOnClickListener(v -> {
            if (selected == null) return;
            PermissionsDialog.show(requireContext(), selected.packageName, selected.label);
        });
        showDetail(null);

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                applyFilter();
            }
        });
        filters.setOnCheckedChangeListener((group, checkedId) -> applyFilter());

        picker = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) importUri(uri);
        });
        usb.setOnClickListener(v -> openApkPicker());
        qr.setOnClickListener(v -> startQr());

        updateSummary();
        updateEmpty();
        loadApps();
    }

    @Override
    public void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(Actions.APPS_REFRESH);
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        requireContext().registerReceiver(refreshReceiver, filter);
    }

    @Override
    public void onStop() {
        try {
            requireContext().unregisterReceiver(refreshReceiver);
        } catch (Throwable ignored) {
        }
        super.onStop();
    }

    @Override
    public void onPause() {
        if (qrDialog != null) dismissQr();
        super.onPause();
    }

    private void scheduleReload() {
        handler.removeCallbacks(reloadRunnable);
        handler.postDelayed(reloadRunnable, 400L);
    }

    private void loadApps() {
        Context c = getContext();
        if (c == null || loadingApps) return;
        loadingApps = true;
        if (loading != null) loading.setVisibility(View.VISIBLE);
        if (empty != null) empty.setVisibility(View.GONE);
        executor.execute(() -> {
            List<InstalledApp> apps = AppsRepository.loadInstalled(c);
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                loadingApps = false;
                if (loading != null) loading.setVisibility(View.GONE);
                if (adapter != null) {
                    adapter.setItems(apps);
                    applyFilter();
                    InstalledApp keep = selected == null ? null : adapter.find(selected.packageName);
                    if (keep != null) selectApp(keep);
                    else if (!apps.isEmpty()) selectApp(apps.get(0));
                    else {
                        selected = null;
                        showDetail(null);
                    }
                }
            });
        });
    }

    private void applyFilter() {
        if (adapter == null || search == null || filters == null) return;
        int mode = AppsAdapter.FILTER_ALL;
        if (filters.getCheckedChipId() == R.id.chipUser) mode = AppsAdapter.FILTER_USER;
        else if (filters.getCheckedChipId() == R.id.chipSystem) mode = AppsAdapter.FILTER_SYSTEM;
        adapter.filter(search.getText().toString(), mode);
        updateSummary();
        updateEmpty();
    }

    private void updateSummary() {
        if (summary == null) return;
        int count = adapter == null ? 0 : adapter.visibleCount();
        summary.setText(getString(R.string.apps_count_fmt, count));
    }

    private void updateEmpty() {
        if (empty == null) return;
        boolean none = adapter == null || adapter.visibleCount() == 0;
        empty.setVisibility(none ? View.VISIBLE : View.GONE);
        empty.setText(R.string.apps_empty);
    }

    private void openApkPicker() {
        if (!isAdded()) return;
        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_STORAGE);
            return;
        }
        launchApkPicker();
    }

    private void launchApkPicker() {
        if (!isAdded()) return;
        ApkPickerDialog.show(requireContext(), this::importFile);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_STORAGE) {
            launchApkPicker();
        }
    }

    private void importFile(File source) {
        Context c = getContext();
        if (c == null || source == null) return;
        if (loading != null) loading.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            File out = new File(c.getCacheDir(), "import_" + System.currentTimeMillis() + ".apk");
            try (InputStream in = new FileInputStream(source);
                 FileOutputStream fos = new FileOutputStream(out)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    fos.write(buffer, 0, read);
                }
                fos.flush();
                LogStore.add(c, "apk picked: " + source.getName() + " -> " + out.length() + " bytes");
                runOnUi(() -> inspectAndConfirm(out, "USB"));
            } catch (Throwable t) {
                out.delete();
                LogStore.add(c, "apk import failed: " + t);
                runOnUi(() -> {
                    if (loading != null) loading.setVisibility(View.GONE);
                    toast(R.string.apps_pick_failed);
                });
            }
        });
    }

    private void importUri(Uri uri) {
        Context c = getContext();
        if (c == null) return;
        if (loading != null) loading.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            File out = new File(c.getCacheDir(), "import_" + System.currentTimeMillis() + ".apk");
            try (InputStream in = c.getContentResolver().openInputStream(uri);
                 FileOutputStream fos = new FileOutputStream(out)) {
                if (in == null) throw new IllegalStateException("open failed");
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    fos.write(buffer, 0, read);
                }
                fos.flush();
                String name = displayName(c, uri);
                LogStore.add(c, "apk picked: " + name + " -> " + out.length() + " bytes");
                runOnUi(() -> inspectAndConfirm(out, "USB"));
            } catch (Throwable t) {
                out.delete();
                LogStore.add(c, "apk import failed: " + t);
                runOnUi(() -> {
                    if (loading != null) loading.setVisibility(View.GONE);
                    toast(R.string.apps_pick_failed);
                });
            }
        });
    }

    private void inspectAndConfirm(File file, String source) {
        Context c = getContext();
        if (c == null) {
            file.delete();
            return;
        }
        executor.execute(() -> {
            ApkInfo info;
            try {
                info = AppsRepository.inspect(c, file);
            } catch (Throwable t) {
                file.delete();
                LogStore.add(c, "apk inspect failed (" + source + "): " + t);
                runOnUi(() -> {
                    if (loading != null) loading.setVisibility(View.GONE);
                    toast(R.string.apps_pick_failed);
                });
                return;
            }
            runOnUi(() -> {
                if (loading != null) loading.setVisibility(View.GONE);
                showConfirm(file, info);
            });
        });
    }

    private void showConfirm(File file, ApkInfo info) {
        if (!isAdded()) {
            file.delete();
            return;
        }
        boolean update = info.isUpdate();
        if (info.installed && !update) {
            file.delete();
            FlymeDialog.showMessage(requireContext(),
                    getString(R.string.apps_install_blocked_title),
                    getString(R.string.apps_install_blocked_same,
                            info.installedVersionName, info.installedVersionCode),
                    getString(R.string.ok), null);
            return;
        }
        if (info.installed && !info.signatureMatches()) {
            file.delete();
            FlymeDialog.showMessage(requireContext(),
                    getString(R.string.apps_install_blocked_title),
                    getString(R.string.apps_install_blocked_signature),
                    getString(R.string.ok), null);
            return;
        }
        StringBuilder message = new StringBuilder();
        message.append(getString(R.string.apps_install_name, info.label)).append('\n');
        message.append(getString(R.string.apps_install_package, info.packageName)).append('\n');
        message.append(getString(R.string.apps_install_version, info.versionName, info.versionCode))
                .append('\n');
        message.append(getString(R.string.apps_install_signature, info.signer));
        if (info.installed) {
            message.append('\n').append(getString(R.string.apps_install_installed,
                    info.installedVersionName, info.installedVersionCode));
        }

        boolean[] installing = {false};
        String positive = update ? getString(R.string.apps_update) : getString(R.string.apps_install);
        Dialog dialog = FlymeDialog.showConfirm(requireContext(),
                getString(update ? R.string.apps_update_title : R.string.apps_install_title),
                message.toString(), positive, getString(R.string.cancel), () -> {
                    installing[0] = true;
                    startInstall(file, info);
                });
        dialog.setOnDismissListener(d -> {
            if (!installing[0]) file.delete();
        });
    }

    private void startInstall(File file, ApkInfo info) {
        Context c = getContext();
        if (c == null) {
            file.delete();
            return;
        }
        executor.execute(() -> {
            try {
                AppsRepository.install(c, file, info);
                runOnUi(() -> toast(R.string.apps_install_started));
            } catch (Throwable t) {
                file.delete();
                LogStore.add(c, "install failed: " + t);
                runOnUi(() -> toastText(getString(R.string.apps_install_failed, String.valueOf(t))));
            }
        });
    }

    private void selectApp(InstalledApp app) {
        selected = app;
        if (adapter != null) adapter.select(app == null ? null : app.packageName);
        showDetail(app);
    }

    private void showDetail(InstalledApp app) {
        if (detailEmpty == null || detailPanel == null) return;
        if (app == null) {
            detailEmpty.setVisibility(View.VISIBLE);
            detailPanel.setVisibility(View.GONE);
            return;
        }
        detailEmpty.setVisibility(View.GONE);
        detailPanel.setVisibility(View.VISIBLE);
        detailIcon.setImageDrawable(app.icon != null ? app.icon
                : ContextCompat.getDrawable(requireContext(), R.drawable.ic_apps));
        detailName.setText(app.label);
        detailType.setText(app.system ? R.string.apps_type_system : R.string.apps_type_user);
        detailType.setTextColor(ContextCompat.getColor(requireContext(),
                app.system ? R.color.flyme_label_level3 : R.color.flyme_blue));
        detailPackage.setText(app.packageName);
        detailVersion.setText(app.versionName + " (" + app.versionCode + ")");
        detailSignature.setText(app.signer == null || app.signer.isEmpty() ? "—" : app.signer);
        boolean self = app.packageName.equals(requireContext().getPackageName());
        detailUninstall.setVisibility(app.system || self ? View.GONE : View.VISIBLE);
        boolean launchable = requireContext().getPackageManager()
                .getLaunchIntentForPackage(app.packageName) != null;
        detailOpen.setEnabled(launchable);
        detailOpen.setAlpha(launchable ? 1f : 0.4f);
    }

    private void openSelected() {
        if (selected == null || !isAdded()) return;
        try {
            Intent launch = requireContext().getPackageManager()
                    .getLaunchIntentForPackage(selected.packageName);
            if (launch == null) {
                toast(R.string.apps_no_launch);
                return;
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launch);
        } catch (Throwable t) {
            LogStore.add(requireContext(), "open app failed: " + t);
        }
    }

    private void confirmUninstall(InstalledApp app) {
        if (app.system || !isAdded()) return;
        if (app.packageName.equals(requireContext().getPackageName())) return;
        FlymeDialog.showConfirm(requireContext(),
                getString(R.string.apps_uninstall_title),
                getString(R.string.apps_uninstall_message, app.label, app.packageName),
                getString(R.string.apps_uninstall), getString(R.string.cancel), () -> {
                    Context c = getContext();
                    if (c == null) return;
                    executor.execute(() -> {
                        try {
                            AppsRepository.uninstall(c, app.packageName);
                            runOnUi(() -> toast(R.string.apps_uninstall_done));
                        } catch (Throwable t) {
                            LogStore.add(c, "uninstall failed: " + t);
                            runOnUi(() -> toastText(getString(R.string.apps_uninstall_failed,
                                    String.valueOf(t))));
                        }
                    });
                });
    }

    private void startQr() {
        if (qrDialog != null) return;
        Context c = requireContext();
        qrServer = new QrUploadServer(c, new QrUploadServer.Listener() {
            @Override
            public void onReady(String url) {
                runOnUi(() -> {
                    if (qrDialog == null) return;
                    if (qrUrl != null) qrUrl.setText(url);
                    if (qrStatus != null) qrStatus.setText(R.string.apps_qr_waiting);
                    if (qrProgress != null) qrProgress.setVisibility(View.GONE);
                    if (qrImage != null) {
                        try {
                            qrImage.setImageBitmap(QrCode.encode(url, 720));
                        } catch (Throwable t) {
                            if (qrStatus != null) {
                                qrStatus.setText(getString(R.string.apps_qr_error, String.valueOf(t)));
                            }
                        }
                    }
                });
            }

            @Override
            public void onProgress(long received, long total) {
                runOnUi(() -> {
                    if (qrDialog == null || qrProgress == null) return;
                    qrProgress.setVisibility(View.VISIBLE);
                    if (total > 0) {
                        int pct = (int) Math.min(100L, received * 100L / total);
                        qrProgress.setIndeterminate(false);
                        qrProgress.setMax(100);
                        qrProgress.setProgress(pct);
                        if (qrStatus != null) {
                            qrStatus.setText(getString(R.string.apps_qr_uploading, pct));
                        }
                    } else {
                        qrProgress.setIndeterminate(true);
                        if (qrStatus != null) qrStatus.setText(R.string.apps_qr_waiting);
                    }
                });
            }

            @Override
            public void onFile(File apk) {
                runOnUi(() -> {
                    if (qrDialog == null) {
                        apk.delete();
                        return;
                    }
                    if (qrStatus != null) qrStatus.setText(R.string.apps_qr_received);
                    stopQrServer();
                    qrDialog.dismiss();
                    inspectAndConfirm(apk, "QR");
                });
            }

            @Override
            public void onError(String message) {
                runOnUi(() -> {
                    if (qrDialog == null || qrStatus == null) return;
                    qrStatus.setText(getString(R.string.apps_qr_error, message));
                    if (qrProgress != null) qrProgress.setVisibility(View.GONE);
                });
            }
        });
        showQrDialog();
        qrServer.start();
    }

    private void showQrDialog() {
        Context c = requireContext();
        qrDialog = new Dialog(c, R.style.Theme_EX2ToolsIV_Dialog);
        qrDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        qrDialog.setContentView(R.layout.dialog_qr_install);
        qrDialog.setCanceledOnTouchOutside(false);
        Window w = qrDialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            w.setDimAmount(0.6f);
            w.setGravity(Gravity.CENTER);
            w.setLayout(dp(560), WindowManager.LayoutParams.WRAP_CONTENT);
        }
        qrImage = qrDialog.findViewById(R.id.qrImage);
        qrUrl = qrDialog.findViewById(R.id.qrUrl);
        qrStatus = qrDialog.findViewById(R.id.qrStatus);
        qrProgress = qrDialog.findViewById(R.id.qrProgress);
        View cancel = qrDialog.findViewById(R.id.qrCancel);
        FlymePress.bind(cancel);
        if (qrStatus != null) qrStatus.setText(R.string.apps_qr_starting);
        if (qrProgress != null) {
            qrProgress.setIndeterminate(true);
            qrProgress.setVisibility(View.GONE);
        }
        if (cancel != null) cancel.setOnClickListener(v -> dismissQr());
        qrDialog.setOnDismissListener(d -> {
            qrDialog = null;
            stopQrServer();
        });
        qrDialog.show();
    }

    private void dismissQr() {
        stopQrServer();
        Dialog d = qrDialog;
        qrDialog = null;
        if (d != null && d.isShowing()) d.dismiss();
    }

    private void stopQrServer() {
        QrUploadServer s = qrServer;
        qrServer = null;
        if (s != null) s.stop();
    }

    private String displayName(Context c, Uri uri) {
        try (Cursor cursor = c.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return cursor.getString(idx);
            }
        } catch (Throwable ignored) {
        }
        return uri.getLastPathSegment() == null ? "apk" : uri.getLastPathSegment();
    }

    private void runOnUi(Runnable runnable) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            if (isAdded()) runnable.run();
        });
    }

    private void toast(int res) {
        toastText(getString(res));
    }

    private void toastText(String text) {
        if (!isAdded()) return;
        Toast.makeText(requireContext(), text, Toast.LENGTH_LONG).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroy() {
        stopQrServer();
        if (qrDialog != null) {
            qrDialog.setOnDismissListener(null);
            qrDialog.dismiss();
            qrDialog = null;
        }
        handler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        super.onDestroy();
    }
}
