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
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.RadioGroup;
import android.widget.RadioButton;
import android.widget.ScrollView;

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
            meta.setText(command.description());
            meta.setTextColor(c.getColor(R.color.flyme_label_level2));
            meta.setTextSize(17f);
            meta.setPadding(0, dp(c, 4), 0, 0);
            row.addView(meta);
            FlymeSwitchRow enabled = new FlymeSwitchRow(c);
            enabled.setText("Включена");
            enabled.setChecked(command.enabled);
            enabled.setOnCheckedChangeListener((button, checked) -> {
                CustomVoiceCommands.setEnabled(c, command, checked);
                renderList(c, list);
            });
            row.addView(enabled);


            row.setOnClickListener(v -> showEditor(c, command, () -> renderList(c, list)));
            row.setOnLongClickListener(v -> {
                FlymeDialog.showConfirm(c,
                        c.getString(R.string.voice_custom_delete_title),
                        c.getString(R.string.voice_custom_delete_message,
                                command.phrase, command.description()),
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
        LinearLayout content = new LinearLayout(c);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(c,24),dp(c,24),dp(c,24),dp(c,24));
        content.setBackgroundResource(R.drawable.flyme_modal_background);
        ScrollView scroll = new ScrollView(c);
        scroll.addView(content);
        dialog.setContentView(scroll);
        dialog.setCanceledOnTouchOutside(false);
        addText(c, content, existing == null ? "Новая команда" : "Изменить команду", 26);
        addText(c, content, "Фраза", 19);
        EditText phrase = new EditText(c);
        phrase.setSingleLine(true);
        phrase.setHint("Например: согрей меня");
        phrase.setTextSize(21);
        phrase.setTextColor(c.getColor(R.color.flyme_label_level1));
        phrase.setText(existing == null ? "" : existing.phrase);
        content.addView(phrase, new LinearLayout.LayoutParams(-1,dp(c,64)));
        addText(c, content, "Другие фразы (каждая с новой строки, необязательно)", 17);
        EditText aliases = new EditText(c);
        aliases.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        aliases.setMinLines(1); aliases.setMaxLines(3);
        aliases.setTextSize(18);
        aliases.setTextColor(c.getColor(R.color.flyme_label_level1));
        aliases.setText(existing == null ? "" : android.text.TextUtils.join("\n", existing.aliases));
        content.addView(aliases, new LinearLayout.LayoutParams(-1,dp(c,90)));

        RadioGroup type = new RadioGroup(c);
        type.setOrientation(LinearLayout.HORIZONTAL);
        RadioButton app = new RadioButton(c);
        app.setId(View.generateViewId()); app.setText("Приложение");
        RadioButton heat = new RadioButton(c);
        heat.setId(View.generateViewId()); heat.setText("Подогревы");
        app.setTextColor(c.getColor(R.color.flyme_label_level1));
        heat.setTextColor(c.getColor(R.color.flyme_label_level1));
        type.addView(app);type.addView(heat);content.addView(type);

        TextView appLabel = addText(c, content, existing != null && !existing.isHeating()
                ? existing.label : "Выбрать приложение ›", 21);
        appLabel.setBackgroundResource(R.drawable.flyme_button_background);
        final String[] selectedPackage = {existing == null ? "" : existing.packageName};
        final String[] selectedLabel = {existing == null ? "" : existing.label};
        appLabel.setOnClickListener(v -> pickApp(c, (pkg,label) -> {
            selectedPackage[0]=pkg;selectedLabel[0]=label;appLabel.setText(label);
        }));

        LinearLayout heating = new LinearLayout(c);
        heating.setOrientation(LinearLayout.VERTICAL);
        content.addView(heating);
        addText(c, heating, "Можно выбрать несколько подогревов для одной фразы", 17);
        String[] targets={"wheel","driver","passenger","windshield","rear"};
        String[] labels={"Руль","Сиденье водителя","Сиденье пассажира","Лобовое стекло","Заднее стекло"};
        Spinner[] levels=new Spinner[targets.length];
        for(int i=0;i<targets.length;i++) {
            LinearLayout row=new LinearLayout(c);row.setOrientation(LinearLayout.HORIZONTAL);
            TextView name=addText(c,row,labels[i],19);
            name.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
            Spinner level=new Spinner(c);
            String[] options=i==1||i==2 ? new String[]{"Не менять","Выключить","Уровень 1","Уровень 2","Уровень 3"}
                    : new String[]{"Не менять","Выключить","Включить"};
            ArrayAdapter<String> adapter=new ArrayAdapter<>(c,android.R.layout.simple_spinner_item,options);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            level.setAdapter(adapter);
            if(existing!=null)for(CustomVoiceCommands.HeatingAction action:existing.heating)
                if(action.target.equals(targets[i]))level.setSelection(action.level+1);
            levels[i]=level;
            row.addView(level,new LinearLayout.LayoutParams(dp(c,190),dp(c,60)));
            row.setGravity(Gravity.CENTER_VERTICAL);
            heating.addView(row);
        }
        type.setOnCheckedChangeListener((group,id) -> {
            boolean isHeat=id==heat.getId();
            heating.setVisibility(isHeat?View.VISIBLE:View.GONE);
            appLabel.setVisibility(isHeat?View.GONE:View.VISIBLE);
        });
        type.check(existing!=null&&existing.isHeating()?heat.getId():app.getId());
        LinearLayout buttons=new LinearLayout(c);buttons.setOrientation(LinearLayout.HORIZONTAL);
        content.addView(buttons);
        TextView cancel=addText(c,buttons,"Отмена",21);
        TextView save=addText(c,buttons,"Сохранить",21);
        cancel.setLayoutParams(new LinearLayout.LayoutParams(0,dp(c,64),1));
        save.setLayoutParams(new LinearLayout.LayoutParams(0,dp(c,64),1));
        cancel.setGravity(Gravity.CENTER);save.setGravity(Gravity.CENTER);
        cancel.setBackgroundResource(R.drawable.flyme_button_background);
        save.setBackgroundResource(R.drawable.flyme_button_background);
        FlymePress.bind(cancel);FlymePress.bind(save);
        cancel.setOnClickListener(v -> dialog.dismiss());
        save.setOnClickListener(v -> {
            String text=phrase.getText().toString().trim();
            if(text.length()<3){Toast.makeText(c,R.string.voice_custom_phrase_short,Toast.LENGTH_SHORT).show();return;}
            List<String> other=new ArrayList<>();
            for(String line:aliases.getText().toString().split("\n"))if(!line.trim().isEmpty())other.add(line.trim());
            List<String> all=new ArrayList<>();all.add(text);all.addAll(other);
            java.util.Set<String> unique=new java.util.HashSet<>();
            for(String candidate:all) {
                String normalized=com.ex2tools.iv.voice.VoiceText.digitize(com.ex2tools.iv.voice.VoiceText.normalize(candidate));
                if(normalized.length()<3 || !unique.add(normalized)
                        || CustomVoiceCommands.exists(c,candidate,existing==null?null:existing.id)) {
                    Toast.makeText(c,"Фразы должны быть разными и не повторять другие команды",Toast.LENGTH_LONG).show();return;
                }
            }
            List<CustomVoiceCommands.HeatingAction> actions=new ArrayList<>();
            if(heat.isChecked()) {
                for(int i=0;i<targets.length;i++)if(levels[i].getSelectedItemPosition()>0)
                    actions.add(new CustomVoiceCommands.HeatingAction(targets[i],levels[i].getSelectedItemPosition()-1));
                if(actions.isEmpty()){Toast.makeText(c,"Выберите хотя бы один подогрев",Toast.LENGTH_SHORT).show();return;}
            } else if(selectedPackage[0].isEmpty()) {
                Toast.makeText(c,R.string.voice_custom_no_app,Toast.LENGTH_SHORT).show();return;
            }
            CustomVoiceCommands.upsert(c,existing==null?null:existing.id,text,selectedPackage[0],selectedLabel[0],
                    existing==null||existing.enabled,other,actions);
            hideKeyboard(c,phrase);dialog.dismiss();
            if(onSaved!=null)onSaved.run();
        });
        dialog.show();
        Window window=dialog.getWindow();
        if(window!=null){
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(0.55f);
            window.setGravity(Gravity.CENTER);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            window.setLayout((int)(c.getResources().getDisplayMetrics().widthPixels*0.66f),
                    (int)(c.getResources().getDisplayMetrics().heightPixels*0.88f));
        }
    }

    private static TextView addText(Context c, LinearLayout parent, String text, int size) {
        TextView view=new TextView(c);view.setText(text);view.setTextSize(size);
        view.setTextColor(c.getColor(R.color.flyme_label_level1));
        view.setPadding(dp(c,8),dp(c,12),dp(c,8),dp(c,12));
        parent.addView(view);return view;
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
