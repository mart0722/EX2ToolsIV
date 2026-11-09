package com.ex2tools.iv.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.ex2tools.iv.R;
import com.ex2tools.iv.voice.CustomVoiceCommands;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class CustomCommandsDialog {

    private interface AppCallback {
        void onPick(String packageName, String label);
    }

    private static final class AppEntry {
        final String packageName;
        final String label;

        AppEntry(String packageName, String label) {
            this.packageName = packageName;
            this.label = label;
        }
    }

    private CustomCommandsDialog() {
    }

    public static void show(Context c) {
        Dialog dialog = new Dialog(c, R.style.Theme_EX2ToolsIV_Dialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_custom_commands);
        dialog.setCanceledOnTouchOutside(true);
        LinearLayout list = dialog.findViewById(R.id.customCommandsList);
        renderList(c, list);
        FlymePress.bind(dialog.findViewById(R.id.customAdd));
        FlymePress.bind(dialog.findViewById(R.id.customClose));
        dialog.findViewById(R.id.customAdd).setOnClickListener(v ->
                showEditor(c, null, () -> renderList(c, list)));
        dialog.findViewById(R.id.customClose).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.55f);
            window.setGravity(Gravity.CENTER);
            window.setLayout((int) (c.getResources().getDisplayMetrics().widthPixels * 0.62f),
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private static void renderList(Context c, LinearLayout list) {
        list.removeAllViews();
        List<CustomVoiceCommands.Command> commands = CustomVoiceCommands.load(c);
        if (commands.isEmpty()) {
            TextView empty = new TextView(c);
            empty.setText(R.string.voice_custom_empty);
            empty.setTextColor(c.getColor(R.color.flyme_label_level2));
            empty.setTextSize(19f);
            empty.setPadding(dp(c, 4), dp(c, 12), dp(c, 4), dp(c, 12));
            list.addView(empty);
            return;
        }
        for (CustomVoiceCommands.Command command : commands) {
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setBackgroundResource(R.drawable.flyme_button_background);
            row.setPadding(dp(c, 18), dp(c, 14), dp(c, 18), dp(c, 14));
            row.setClickable(true);
            row.setFocusable(true);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(c, 8);
            list.addView(row, params);

            TextView title = new TextView(c);
            title.setText(command.phrase);
            title.setTextColor(c.getColor(R.color.flyme_label_level1));
            title.setTextSize(21f);
            title.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
                    android.graphics.Typeface.NORMAL));
            row.addView(title);

            TextView meta = new TextView(c);
            meta.setText(command.label + " · " + command.packageName);
            meta.setTextColor(c.getColor(R.color.flyme_label_level2));
            meta.setTextSize(17f);
            meta.setPadding(0, dp(c, 4), 0, 0);
            row.addView(meta);

            row.setOnClickListener(v -> showEditor(c, command, () -> renderList(c, list)));
            row.setOnLongClickListener(v -> {
                FlymeDialog.showConfirm(c,
                        c.getString(R.string.voice_custom_delete_title),
                        c.getString(R.string.voice_custom_delete_message,
                                command.phrase, command.label),
                        c.getString(R.string.confirm_yes),
                        c.getString(R.string.confirm_cancel),
                        () -> {
                            CustomVoiceCommands.delete(c, command.id);
                            renderList(c, list);
                        });
                return true;
            });
        }
    }

    private static void showEditor(Context c, CustomVoiceCommands.Command existing, Runnable onSaved) {
        Dialog dialog = new Dialog(c, R.style.Theme_EX2ToolsIV_Dialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_custom_command_edit);
        dialog.setCanceledOnTouchOutside(false);

        TextView title = dialog.findViewById(R.id.customEditTitle);
        EditText phrase = dialog.findViewById(R.id.customPhrase);
        TextView appLabel = dialog.findViewById(R.id.customAppLabel);
        if (existing != null) {
            title.setText(R.string.voice_custom_edit_title);
            phrase.setText(existing.phrase);
            phrase.setSelection(phrase.getText().length());
            appLabel.setText(existing.label);
            appLabel.setTextColor(c.getColor(R.color.flyme_label_level1));
        }

        final String[] selectedPackage = {existing == null ? null : existing.packageName};
        final String[] selectedLabel = {existing == null ? null : existing.label};

        dialog.findViewById(R.id.customPickApp).setOnClickListener(v ->
                pickApp(c, (packageName, label) -> {
                    selectedPackage[0] = packageName;
                    selectedLabel[0] = label;
                    appLabel.setText(label);
                    appLabel.setTextColor(c.getColor(R.color.flyme_label_level1));
                }));
        FlymePress.bind(dialog.findViewById(R.id.customEditCancel));
        FlymePress.bind(dialog.findViewById(R.id.customEditSave));
        dialog.findViewById(R.id.customEditCancel).setOnClickListener(v -> dialog.dismiss());
        dialog.findViewById(R.id.customEditSave).setOnClickListener(v -> {
            String text = phrase.getText().toString().trim();
            if (text.length() < 3) {
                Toast.makeText(c, R.string.voice_custom_phrase_short, Toast.LENGTH_SHORT).show();
                return;
            }
            if (selectedPackage[0] == null) {
                Toast.makeText(c, R.string.voice_custom_no_app, Toast.LENGTH_SHORT).show();
                return;
            }
            if (CustomVoiceCommands.exists(c, text, existing == null ? null : existing.id)) {
                Toast.makeText(c, R.string.voice_custom_duplicate, Toast.LENGTH_SHORT).show();
                return;
            }
            CustomVoiceCommands.upsert(c, existing == null ? null : existing.id,
                    text, selectedPackage[0], selectedLabel[0]);
            hideKeyboard(c, phrase);
            dialog.dismiss();
            Toast.makeText(c, R.string.voice_custom_saved, Toast.LENGTH_SHORT).show();
            if (onSaved != null) onSaved.run();
        });

        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.55f);
            window.setGravity(Gravity.CENTER);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            window.setLayout((int) (c.getResources().getDisplayMetrics().widthPixels * 0.54f),
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }
        phrase.requestFocus();
    }

    private static void pickApp(Context c, AppCallback callback) {
        List<AppEntry> apps = loadApps(c);
        if (apps.isEmpty()) {
            Toast.makeText(c, R.string.voice_custom_no_apps, Toast.LENGTH_SHORT).show();
            return;
        }
        Dialog dialog = new Dialog(c, R.style.Theme_EX2ToolsIV_Dialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_app_picker);
        dialog.setCanceledOnTouchOutside(true);
        LinearLayout list = dialog.findViewById(R.id.appPickerList);
        for (AppEntry entry : apps) {
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setBackgroundResource(R.drawable.flyme_button_background);
            row.setPadding(dp(c, 18), dp(c, 14), dp(c, 18), dp(c, 14));
            row.setClickable(true);
            row.setFocusable(true);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(c, 8);
            list.addView(row, params);

            TextView title = new TextView(c);
            title.setText(entry.label);
            title.setTextColor(c.getColor(R.color.flyme_label_level1));
            title.setTextSize(21f);
            title.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
                    android.graphics.Typeface.NORMAL));
            row.addView(title);

            TextView meta = new TextView(c);
            meta.setText(entry.packageName);
            meta.setTextColor(c.getColor(R.color.flyme_label_level2));
            meta.setTextSize(17f);
            meta.setPadding(0, dp(c, 3), 0, 0);
            row.addView(meta);

            row.setOnClickListener(v -> {
                callback.onPick(entry.packageName, entry.label);
                dialog.dismiss();
            });
        }
        FlymePress.bind(dialog.findViewById(R.id.appPickerCancel));
        dialog.findViewById(R.id.appPickerCancel).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.55f);
            window.setGravity(Gravity.CENTER);
            window.setLayout((int) (c.getResources().getDisplayMetrics().widthPixels * 0.62f),
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private static List<AppEntry> loadApps(Context c) {
        List<AppEntry> out = new ArrayList<>();
        try {
            PackageManager pm = c.getPackageManager();
            Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> list = pm.queryIntentActivities(main, 0);
            List<String> seen = new ArrayList<>();
            for (ResolveInfo info : list) {
                String pkg = info.activityInfo.packageName;
                if (pkg.equals(c.getPackageName()) || seen.contains(pkg)) continue;
                if (pm.getLaunchIntentForPackage(pkg) == null) continue;
                seen.add(pkg);
                out.add(new AppEntry(pkg, String.valueOf(info.loadLabel(pm))));
            }
        } catch (Throwable ignored) {
        }
        out.sort(Comparator.comparing(app -> app.label.toLowerCase(Locale.ROOT)));
        return out;
    }

    private static void hideKeyboard(Context c, View view) {
        try {
            InputMethodManager imm = (InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        } catch (Throwable ignored) {
        }
    }

    private static int dp(Context c, int value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }
}
