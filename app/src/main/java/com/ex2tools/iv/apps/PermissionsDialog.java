package com.ex2tools.iv.apps;

import android.app.AppOpsManager;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.R;
import com.ex2tools.iv.ui.FlymePress;
import com.google.android.material.button.MaterialButton;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class PermissionsDialog {

    private PermissionsDialog() {
    }

    public static void show(Context context, String packageName, String label) {
        if (context == null || packageName == null) return;
        Context c = context.getApplicationContext();
        Dialog dialog = new Dialog(context, R.style.Theme_EX2ToolsIV_Dialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_permissions);
        Window window = dialog.getWindow();
        if (window != null) window.setBackgroundDrawableResource(android.R.color.transparent);

        TextView subtitle = dialog.findViewById(R.id.permSubtitle);
        if (subtitle != null) {
            subtitle.setText(label == null || label.isEmpty()
                    ? packageName : label + " · " + packageName);
        }
        LinearLayout list = dialog.findViewById(R.id.permList);
        View scroll = dialog.findViewById(R.id.permScroll);
        TextView empty = dialog.findViewById(R.id.permEmpty);
        TextView grantAll = dialog.findViewById(R.id.permGrantAll);
        TextView close = dialog.findViewById(R.id.permClose);
        FlymePress.bind(grantAll);
        FlymePress.bind(close);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler main = new Handler(Looper.getMainLooper());
        List<Row> rows = new ArrayList<>();

        if (close != null) close.setOnClickListener(v -> dialog.dismiss());
        if (grantAll != null) {
            grantAll.setOnClickListener(v -> {
                FlymePress.setEnabled(grantAll, false);
                executor.execute(() -> {
                    int changed = 0;
                    for (Row row : rows) {
                        if (!row.perm.manageable || row.perm.granted) continue;
                        if (setAccess(c, packageName, row.perm, true)) changed++;
                    }
                    for (Row row : rows) row.perm.granted = isGranted(c, packageName, row.perm);
                    int result = changed;
                    main.post(() -> {
                        for (Row row : rows) bind(row);
                        toast(c, result > 0
                                ? c.getString(R.string.perm_granted_all)
                                : c.getString(R.string.perm_nothing_to_grant));
                        FlymePress.setEnabled(grantAll, true);
                    });
                });
            });
        }

        executor.execute(() -> {
            List<Perm> perms = load(c, packageName);
            main.post(() -> {
                if (!dialog.isShowing()) return;
                if (perms.isEmpty()) {
                    if (scroll != null) scroll.setVisibility(View.GONE);
                    if (empty != null) empty.setVisibility(View.VISIBLE);
                    FlymePress.setEnabled(grantAll, false);
                    return;
                }
                if (scroll != null) scroll.setVisibility(View.VISIBLE);
                if (empty != null) empty.setVisibility(View.GONE);
                LayoutInflater inflater = LayoutInflater.from(context);
                for (Perm perm : perms) {
                    View rowView = inflater.inflate(R.layout.item_permission, list, false);
                    Row row = new Row(rowView, perm);
                    row.action = rowView.findViewById(R.id.permAction);
                    row.state = rowView.findViewById(R.id.permState);
                    row.name = rowView.findViewById(R.id.permName);
                    if (row.action != null) {
                        row.action.setOnClickListener(v -> {
                            if (!perm.manageable) return;
                            row.action.setEnabled(false);
                            boolean target = !perm.granted;
                            executor.execute(() -> {
                                setAccess(c, packageName, perm, target);
                                perm.granted = isGranted(c, packageName, perm);
                                main.post(() -> {
                                    bind(row);
                                    if (dialog.isShowing()) row.action.setEnabled(true);
                                });
                            });
                        });
                    }
                    rows.add(row);
                    list.addView(rowView);
                    bind(row);
                }
            });
        });

        dialog.setOnDismissListener(d -> executor.shutdownNow());
        dialog.show();
    }

    private static void bind(Row row) {
        if (row.name != null) row.name.setText(row.perm.shortName);
        if (row.state != null) {
            String state = !row.perm.manageable
                    ? row.perm.context.getString(R.string.perm_system)
                    : row.perm.granted
                    ? row.perm.context.getString(R.string.perm_granted)
                    : row.perm.context.getString(R.string.perm_denied);
            row.state.setText(row.perm.name + " · " + state);
            row.state.setTextColor(ContextCompat.getColor(row.perm.context,
                    row.perm.manageable && row.perm.granted
                            ? R.color.flyme_blue : R.color.flyme_label_level3));
        }
        if (row.action != null) {
            if (!row.perm.manageable) {
                row.action.setEnabled(false);
                row.action.setText(R.string.perm_system);
            } else {
                row.action.setEnabled(true);
                row.action.setText(row.perm.granted ? R.string.perm_revoke : R.string.perm_grant);
            }
        }
    }

    private static List<Perm> load(Context c, String packageName) {
        List<Perm> out = new ArrayList<>();
        try {
            PackageManager pm = c.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);
            String[] requested = pi.requestedPermissions;
            if (requested == null) return out;
            int[] flags = pi.requestedPermissionsFlags;
            for (int i = 0; i < requested.length; i++) {
                String name = requested[i];
                if (name == null || name.isEmpty()) continue;
                Perm p = new Perm();
                p.context = c;
                p.name = name;
                p.shortName = name.substring(name.lastIndexOf('.') + 1);
                p.installGranted = flags != null
                        && i < flags.length
                        && (flags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0;
                try {
                    PermissionInfo info = pm.getPermissionInfo(name, 0);
                    int base = info.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE;
                    p.runtime = base == PermissionInfo.PROTECTION_DANGEROUS;
                } catch (Throwable ignored) {
                }
                try {
                    p.op = AppOpsManager.permissionToOp(name);
                } catch (Throwable ignored) {
                }
                p.appOp = p.op != null && !p.op.isEmpty();
                p.manageable = p.runtime || p.appOp;
                p.granted = isGranted(c, packageName, p);
                out.add(p);
            }
        } catch (Throwable t) {
            LogStore.add(c, "permissions load failed: " + t);
        }
        Collections.sort(out, new Comparator<Perm>() {
            @Override
            public int compare(Perm a, Perm b) {
                if (a.manageable != b.manageable) return a.manageable ? -1 : 1;
                return String.CASE_INSENSITIVE_ORDER.compare(a.shortName, b.shortName);
            }
        });
        return out;
    }

    private static boolean isGranted(Context c, String packageName, Perm p) {
        try {
            PackageManager pm = c.getPackageManager();
            if (p.runtime) {
                return pm.checkPermission(p.name, packageName) == PackageManager.PERMISSION_GRANTED;
            }
            if (p.appOp) {
                AppOpsManager aom = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
                if (aom == null) return false;
                int uid = pm.getApplicationInfo(packageName, 0).uid;
                int mode = aom.checkOpNoThrow(p.op, uid, packageName);
                return mode == AppOpsManager.MODE_ALLOWED || mode == AppOpsManager.MODE_FOREGROUND;
            }
        } catch (Throwable ignored) {
        }
        return p.installGranted;
    }

    private static boolean setAccess(Context c, String packageName, Perm p, boolean grant) {
        boolean ok = true;
        if (p.runtime) {
            ok = setRuntime(c, packageName, p.name, grant);
        }
        if (p.appOp && !p.runtime) {
            ok = setAppOp(c, packageName, p.op, grant) && ok;
        }
        boolean now = isGranted(c, packageName, p);
        return ok && now == grant;
    }

    private static boolean setRuntime(Context c, String packageName, String permission, boolean grant) {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{
                    "pm", grant ? "grant" : "revoke", packageName, permission});
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Throwable t) {
            LogStore.add(c, "pm grant failed: " + t);
            return false;
        }
    }

    private static boolean setAppOp(Context c, String packageName, String op, boolean allow) {
        try {
            PackageManager pm = c.getPackageManager();
            int uid = pm.getApplicationInfo(packageName, 0).uid;
            AppOpsManager aom = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
            if (aom == null) return false;
            Method strOpToOp = AppOpsManager.class.getDeclaredMethod("strOpToOp", String.class);
            strOpToOp.setAccessible(true);
            int opInt = (int) strOpToOp.invoke(null, op);
            Method setMode = AppOpsManager.class.getDeclaredMethod(
                    "setMode", int.class, int.class, String.class, int.class);
            setMode.setAccessible(true);
            setMode.invoke(aom, opInt, uid, packageName,
                    allow ? AppOpsManager.MODE_ALLOWED : AppOpsManager.MODE_IGNORED);
            int mode = aom.checkOpNoThrow(op, uid, packageName);
            boolean now = mode == AppOpsManager.MODE_ALLOWED || mode == AppOpsManager.MODE_FOREGROUND;
            if (now == allow) return true;
        } catch (Throwable t) {
            LogStore.add(c, "appop set failed: " + t);
        }
        try {
            Process process = Runtime.getRuntime().exec(new String[]{
                    "appops", "set", packageName, op, allow ? "allow" : "deny"});
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Throwable t) {
            LogStore.add(c, "appops shell failed: " + t);
            return false;
        }
    }

    private static void toast(Context c, String text) {
        Toast.makeText(c, text, Toast.LENGTH_SHORT).show();
    }

    private static final class Perm {
        Context context;
        String name;
        String shortName;
        String op;
        boolean runtime;
        boolean appOp;
        boolean installGranted;
        boolean manageable;
        boolean granted;
    }

    private static final class Row {
        final View view;
        final Perm perm;
        TextView name;
        TextView state;
        MaterialButton action;

        Row(View view, Perm perm) {
            this.view = view;
            this.perm = perm;
        }
    }
}
