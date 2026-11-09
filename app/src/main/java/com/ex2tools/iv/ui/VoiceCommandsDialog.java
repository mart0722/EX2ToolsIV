package com.ex2tools.iv.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ex2tools.iv.R;
import com.ex2tools.iv.voice.CustomVoiceCommands;

import java.util.List;

public final class VoiceCommandsDialog {

    private static final String[][] GROUPS = {
            {"Время и погода",
                    "«Который час»",
                    "«Какая погода»"},
            {"Громкость",
                    "«Громче» / «Тише»",
                    "«Громкость 50»",
                    "«Выключи звук» / «Включи звук»"},
            {"Экран",
                    "«Ярче» / «Темнее»",
                    "«Ночной режим» / «Дневной режим»"},
            {"Климат",
                    "«Температура 23» / «Теплее» / «Холоднее»",
                    "«Обдув на лицо», «на ноги», «на стекло»",
                    "«Вентилятор 3» / «Выключи обдув»",
                    "«Кондиционер включи» / «выключи»",
                    "«Рециркуляция» / «Свежий воздух»",
                    "«Обогрев лобового» / «заднего»",
                    "«Обогрев руля 2»",
                    "«Подогрев сидений 2»",
                    "«Охлади салон» / «Прогрей салон»"},
            {"Движение",
                    "«Режим Эко» / «Комфорт» / «Спорт»",
                    "«Следующий режим»",
                    "«Рекуперация сильная» / «средняя» / «слабая»"},
            {"Окна",
                    "«Открой окна» / «Закрой окна»",
                    "«Открой передние» / «задние»",
                    "«Окна наполовину»"},
            {"Приложения",
                    "«Открой навигатор» / «музыку» / «радио»",
                    "«Открой браузер» / «YouTube»",
                    "«Открой <название приложения>»",
                    "«Сверни» / «На рабочий стол»"}
    };

    private VoiceCommandsDialog() {
    }

    public static void show(Context c) {
        Dialog dialog = new Dialog(c, R.style.Theme_EX2ToolsIV_Dialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_voice_commands);
        dialog.setCanceledOnTouchOutside(true);

        LinearLayout list = dialog.findViewById(R.id.voiceCommandsList);
        render(c, list);

        TextView close = dialog.findViewById(R.id.voiceCommandsClose);
        FlymePress.bind(close);
        close.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.55f);
            window.setGravity(Gravity.CENTER);
            window.setLayout((int) (c.getResources().getDisplayMetrics().widthPixels * 0.64f),
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private static void render(Context c, LinearLayout list) {
        list.removeAllViews();
        for (String[] group : GROUPS) {
            addGroupTitle(c, list, group[0]);
            for (int i = 1; i < group.length; i++) {
                addCommand(c, list, group[i]);
            }
        }

        List<CustomVoiceCommands.Command> custom = CustomVoiceCommands.load(c);
        addGroupTitle(c, list, c.getString(R.string.voice_available_commands_custom));
        if (custom.isEmpty()) {
            addNote(c, list, c.getString(R.string.voice_available_commands_custom_note));
        } else {
            for (CustomVoiceCommands.Command command : custom) {
                addCommand(c, list, "«" + command.phrase + "»");
            }
        }
    }

    private static void addGroupTitle(Context c, LinearLayout list, String text) {
        TextView title = new TextView(c);
        title.setText(text);
        title.setTextColor(c.getColor(R.color.flyme_label_level1));
        title.setTextSize(22f);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setPadding(0, dp(c, 18), 0, dp(c, 4));
        list.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private static void addCommand(Context c, LinearLayout list, String text) {
        TextView row = new TextView(c);
        row.setText("•  " + text);
        row.setTextColor(c.getColor(R.color.flyme_label_level2));
        row.setTextSize(19f);
        row.setLineSpacing(0f, 1.12f);
        row.setPadding(dp(c, 4), dp(c, 5), dp(c, 4), dp(c, 5));
        list.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private static void addNote(Context c, LinearLayout list, String text) {
        TextView note = new TextView(c);
        note.setText(text);
        note.setTextColor(c.getColor(R.color.flyme_label_level3));
        note.setTextSize(17f);
        note.setLineSpacing(0f, 1.1f);
        note.setPadding(dp(c, 4), dp(c, 4), dp(c, 4), dp(c, 4));
        list.addView(note, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private static int dp(Context c, int value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }
}
