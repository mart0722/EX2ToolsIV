package com.ex2tools.iv.voice;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import com.ex2tools.iv.car.CarProps;
import com.ex2tools.iv.widgets.WidgetsCommon;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class VoiceCommands {

    public static final String[] WAKE = {"слушай джили", "слушай джилли", "слушай джил"};

    private static final String[] NAV_PACKAGES = {
            "ru.yandex.yandexnavi", "ru.yandex.yandexmaps", "com.google.android.apps.maps"
    };
    private static final String[] MUSIC_PACKAGES = {
            "ru.yandex.music", "com.spotify.music", "com.google.android.apps.youtube.music"
    };
    private static final String[] RADIO_PACKAGES = {
            "com.flyme.auto.localmusic.radio", "com.geely.radio"
    };
    private static final String[] BROWSER_PACKAGES = {
            "com.yandex.browser.lite", "com.android.chrome", "org.mozilla.firefox"
    };
    private static final String[] YOUTUBE_PACKAGES = {
            "org.smarttube.stable", "com.google.android.youtube"
    };

    public static final class Result {
        public final boolean handled;
        public final String reply;

        Result(boolean handled, String reply) {
            this.handled = handled;
            this.reply = reply;
        }
    }

    private VoiceCommands() {
    }

    public static Result execute(Context c, String raw) {
        String text = VoiceText.normalize(raw);
        if (text.isEmpty()) return no("Не понял команду");
        List<String> clauses = VoiceText.splitClauses(text);
        if (clauses.size() <= 1) {
            String clause = clauses.isEmpty() ? text : clauses.get(0);
            return executeOne(c, clause);
        }
        boolean handled = false;
        StringBuilder reply = new StringBuilder();
        for (String clause : clauses) {
            Result result = executeOne(c, clause);
            if (!result.handled) continue;
            handled = true;
            if (reply.length() > 0) reply.append("; ");
            reply.append(result.reply);
        }
        return handled ? ok(reply.toString()) : no("Не понял команду");
    }

    private static Result executeOne(Context c, String clause) {
        String t = VoiceText.digitize(clause);

        CustomVoiceCommands.Command custom = CustomVoiceCommands.match(c, clause);
        if (custom != null) return openCustom(c, custom);

        if (VoiceText.has(t, "который час", "сколько времени", "скажи время",
                "текущее время", "время сейчас", "подскажи время")) {
            return ok(new SimpleDateFormat("HH:mm", Locale.US).format(new Date()));
        }
        if (VoiceText.has(t, "погода", "прогноз")
                || (VoiceText.has(t, "температур")
                && VoiceText.has(t, "на улице", "за окном", "снаружи"))) {
            return ok("Погода недоступна без интернета");
        }

        Result volume = volume(c, t);
        if (volume != null) return volume;
        Result brightness = brightness(c, t);
        if (brightness != null) return brightness;
        Result display = display(c, t);
        if (display != null) return display;

        Result boost = climateBoost(c, t);
        if (boost != null) return boost;
        Result close = closeApp(c, t);
        if (close != null) return close;
        Result glass = glass(c, t);
        if (glass != null) return glass;
        Result window = window(c, t);
        if (window != null) return window;
        Result trunk = trunk(t);
        if (trunk != null) return trunk;
        Result temp = temperature(c, t);
        if (temp != null) return temp;
        Result vent = vent(t);
        if (vent != null) return vent;
        Result fan = fan(t);
        if (fan != null) return fan;
        Result recirc = recirc(c, t);
        if (recirc != null) return recirc;
        Result wheel = wheel(c, t);
        if (wheel != null) return wheel;
        Result seat = seat(c, t);
        if (seat != null) return seat;
        Result ac = ac(c, t);
        if (ac != null) return ac;
        Result drive = drive(c, t);
        if (drive != null) return drive;
        Result regen = regen(c, t);
        if (regen != null) return regen;
        Result app = app(c, t);
        if (app != null) return app;

        Result fallback = openGeneric(c, t);
        if (fallback != null) return fallback;
        return no("Не понял команду");
    }

    private static Result volume(Context c, String t) {
        if (!VoiceText.has(t, "громкость", "звук", "громче", "тише", "погромче", "потише")) {
            return null;
        }
        if (VoiceText.has(t, "выключи", "выключить", "отруби", "убери", "заглуши", "стоп")
                && VoiceText.has(t, "звук", "громкость")) {
            SystemControls.mute(c, true);
            return ok("Звук выключен");
        }
        if (VoiceText.has(t, "включи", "включить", "верни")
                && VoiceText.has(t, "звук", "громкость")) {
            SystemControls.mute(c, false);
            return ok("Звук включён");
        }
        if (VoiceText.has(t, "максим")) {
            SystemControls.setVolumePercent(c, 100);
            return ok("Громкость 100%");
        }
        if (VoiceText.has(t, "минимум")) {
            SystemControls.setVolumePercent(c, 1);
            return ok("Громкость 1%");
        }
        if (VoiceText.has(t, "половин")) {
            SystemControls.setVolumePercent(c, 50);
            return ok("Громкость 50%");
        }
        boolean up = VoiceText.has(t, "громче", "погромче", "увеличь", "увеличить",
                "прибавь", "добавь", "выше", "больше");
        boolean down = VoiceText.has(t, "тише", "потише", "уменьши", "уменьшить",
                "убавь", "ниже", "меньше");
        Integer n = VoiceText.firstInt(t);
        if (n != null) {
            int value = n;
            if (!VoiceText.has(t, "процент") && !up && !down && value <= 10) value *= 10;
            if (up) {
                SystemControls.stepVolume(c, value);
                return ok("Громкость " + SystemControls.volumePercent(c) + "%");
            }
            if (down) {
                SystemControls.stepVolume(c, -value);
                return ok("Громкость " + SystemControls.volumePercent(c) + "%");
            }
            SystemControls.setVolumePercent(c, value);
            return ok("Громкость " + SystemControls.volumePercent(c) + "%");
        }
        if (up) {
            SystemControls.stepVolume(c, 10);
            return ok("Громкость " + SystemControls.volumePercent(c) + "%");
        }
        if (down) {
            SystemControls.stepVolume(c, -10);
            return ok("Громкость " + SystemControls.volumePercent(c) + "%");
        }
        return no("Не понял команду");
    }

    private static Result brightness(Context c, String t) {
        if (!VoiceText.has(t, "яркость", "ярче", "темнее", "светлее")) return null;
        if (VoiceText.has(t, "максим")) {
            SystemControls.setBrightnessPercent(c, 100);
            return ok("Яркость 100%");
        }
        if (VoiceText.has(t, "минимум")) {
            SystemControls.setBrightnessPercent(c, 1);
            return ok("Яркость 1%");
        }
        if (VoiceText.has(t, "половин")) {
            SystemControls.setBrightnessPercent(c, 50);
            return ok("Яркость 50%");
        }
        boolean up = VoiceText.has(t, "ярче", "светлее", "увеличь", "увеличить",
                "прибавь", "добавь", "выше", "больше");
        boolean down = VoiceText.has(t, "темнее", "уменьши", "уменьшить",
                "убавь", "ниже", "меньше");
        Integer n = VoiceText.firstInt(t);
        if (n != null) {
            int value = n;
            if (!VoiceText.has(t, "процент") && !up && !down && value <= 10) value *= 10;
            if (up) {
                SystemControls.stepBrightness(c, value);
                return ok("Яркость " + SystemControls.brightnessPercent(c) + "%");
            }
            if (down) {
                SystemControls.stepBrightness(c, -value);
                return ok("Яркость " + SystemControls.brightnessPercent(c) + "%");
            }
            SystemControls.setBrightnessPercent(c, value);
            return ok("Яркость " + SystemControls.brightnessPercent(c) + "%");
        }
        if (up) {
            SystemControls.stepBrightness(c, 10);
            return ok("Яркость " + SystemControls.brightnessPercent(c) + "%");
        }
        if (down) {
            SystemControls.stepBrightness(c, -10);
            return ok("Яркость " + SystemControls.brightnessPercent(c) + "%");
        }
        return no("Не понял команду");
    }

    private static Result display(Context c, String t) {
        if (!VoiceText.has(t, "ночной режим", "дневной режим", "режим ночи", "режим дня",
                "ночной экран", "дневной экран")) {
            return null;
        }
        boolean night = VoiceText.has(t, "ночн");
        if (VoiceText.has(t, "дневн", "день")) night = false;
        if (VoiceText.has(t, "выключи", "выключить", "отключи", "отключить", "отруби")) {
            night = !night;
        }
        if (!SystemControls.setDayNight(c, night)) return no("Не удалось переключить режим");
        return ok(night ? "Ночной режим" : "Дневной режим");
    }

    private static Result climateBoost(Context c, String t) {
        boolean fast = VoiceText.has(t, "быстр", "максимальн", "макс");
        boolean cool = VoiceText.has(t, "охлажд", "охлади", "выстуд", "остуди");
        boolean heat = VoiceText.has(t, "обогрев", "обогрей", "отопл", "нагрев", "нагрей", "прогре");
        if ((fast && cool && !heat) || (VoiceText.has(t, "охлади", "выстуди", "остуди")
                && VoiceText.has(t, "салон"))) {
            WidgetsCommon.setTemp(c, 16);
            WidgetsCommon.setFan(7);
            WidgetsCommon.setAc(c, true);
            return ok("Охлаждаю салон");
        }
        if ((fast && heat) || (VoiceText.has(t, "прогрей", "обогрей", "нагрей")
                && VoiceText.has(t, "салон"))) {
            WidgetsCommon.setTemp(c, 30);
            WidgetsCommon.setFan(7);
            WidgetsCommon.setAc(c, true);
            return ok("Прогреваю салон");
        }
        return null;
    }

    private static Result closeApp(Context c, String t) {
        if (!VoiceText.has(t, "закрой", "закрыть", "сверни", "свернуть", "выйди", "выйти",
                "на рабочий стол", "домой")) {
            return null;
        }
        if (VoiceText.has(t, "окн", "двер", "багажник", "стекл", "подогрев", "обогрев",
                "вентилятор", "кондиционер", "климат", "режим", "температур")) {
            return null;
        }
        if (VoiceText.has(t, "навигатор", "музык", "радио", "ютуб", "youtube", "браузер",
                "карты", "приложение", "на рабочий стол", "домой", "сверни", "свернуть")) {
            return home(c);
        }
        return null;
    }

    private static Result glass(Context c, String t) {
        boolean heat = VoiceText.has(t, "подогрев", "обогрев", "нагрев", "греть", "грей", "размороз");
        if (!heat) return null;
        boolean front = VoiceText.has(t, "лобов", "ветров");
        boolean rear = VoiceText.has(t, "задн", "зеркал");
        if (!front && !rear) return null;
        boolean on = !VoiceText.has(t, "выключи", "выключить", "отключи", "отключить",
                "отруби", "стоп", "убери", "погаси");
        StringBuilder reply = new StringBuilder();
        if (front) {
            WidgetsCommon.setWindshieldHeat(c, on);
            reply.append(on ? "Подогрев лобового включён" : "Подогрев лобового выключен");
        }
        if (rear) {
            WidgetsCommon.setRearDefrost(c, on);
            if (reply.length() > 0) reply.append("; ");
            reply.append(on ? "Подогрев заднего включён" : "Подогрев заднего выключен");
        }
        return ok(reply.toString());
    }

    private static Result window(Context c, String t) {
        boolean candidate = VoiceText.has(t, "окн", "стеклоподъем")
                || (VoiceText.has(t, "стекл") && VoiceText.has(t, "открой", "открыть",
                "закрой", "закрыть", "опусти", "опустить", "подними", "поднять"));
        if (!candidate) return null;
        int position;
        if (VoiceText.has(t, "половин", "наполовин")) {
            position = 44;
        } else {
            Integer n = VoiceText.firstInt(t);
            if (n != null) {
                position = Math.round(Math.max(0, Math.min(100, n)) * 88f / 100f);
            } else if (VoiceText.has(t, "закрой", "закрыть", "подними", "поднять")) {
                position = 0;
            } else {
                position = 88;
            }
        }
        int[] areas = windowAreas(t);
        if (!WidgetsCommon.setWindow(c, position, areas)) return no("Окна недоступны");
        if (position == 0) return ok("Окна закрыты");
        return ok("Окна открыты на " + Math.round(position * 100f / 88f) + "%");
    }

    private static int[] windowAreas(String t) {
        boolean all = VoiceText.has(t, "все", "всех", "оба", "обе");
        boolean left = VoiceText.has(t, "лев", "водител", "пилот");
        boolean right = VoiceText.has(t, "прав", "пассаж");
        boolean front = VoiceText.has(t, "перед");
        boolean rear = VoiceText.has(t, "зад");
        if (all || (VoiceText.has(t, "окна", "стекла")
                && !left && !right && !front && !rear)) {
            return new int[]{WidgetsCommon.WINDOW_FRONT_LEFT, WidgetsCommon.WINDOW_FRONT_RIGHT,
                    WidgetsCommon.WINDOW_REAR_LEFT, WidgetsCommon.WINDOW_REAR_RIGHT};
        }
        if (VoiceText.has(t, "водител", "пилот")) return new int[]{WidgetsCommon.WINDOW_FRONT_LEFT};
        if (VoiceText.has(t, "пассаж")) return new int[]{WidgetsCommon.WINDOW_FRONT_RIGHT};
        if (front && right) return new int[]{WidgetsCommon.WINDOW_FRONT_RIGHT};
        if (front && left) return new int[]{WidgetsCommon.WINDOW_FRONT_LEFT};
        if (rear && right) return new int[]{WidgetsCommon.WINDOW_REAR_RIGHT};
        if (rear && left) return new int[]{WidgetsCommon.WINDOW_REAR_LEFT};
        if (front) return new int[]{WidgetsCommon.WINDOW_FRONT_LEFT, WidgetsCommon.WINDOW_FRONT_RIGHT};
        if (rear) return new int[]{WidgetsCommon.WINDOW_REAR_LEFT, WidgetsCommon.WINDOW_REAR_RIGHT};
        if (right) return new int[]{WidgetsCommon.WINDOW_FRONT_RIGHT, WidgetsCommon.WINDOW_REAR_RIGHT};
        if (left) return new int[]{WidgetsCommon.WINDOW_FRONT_LEFT, WidgetsCommon.WINDOW_REAR_LEFT};
        return new int[]{WidgetsCommon.WINDOW_FRONT_LEFT};
    }

    private static Result trunk(String t) {
        if (!VoiceText.has(t, "багажник", "багаж", "капот")) return null;
        return ok("Багажник не поддерживается");
    }

    private static Result temperature(Context c, String t) {
        boolean climate = VoiceText.has(t, "кондиционер", "кондей", "климат")
                && VoiceText.firstInt(t) != null;
        if (!climate && !VoiceText.has(t, "температур", "градус", "теплее", "потеплее",
                "холоднее", "похолоднее", "жарче", "прохладнее")) {
            return null;
        }
        if (VoiceText.has(t, "максим")) {
            WidgetsCommon.setTemp(c, 30);
            return ok("Температура 30°");
        }
        if (VoiceText.has(t, "минимум")) {
            WidgetsCommon.setTemp(c, 16);
            return ok("Температура 16°");
        }
        if (VoiceText.has(t, "половин")) {
            WidgetsCommon.setTemp(c, 23);
            return ok("Температура 23°");
        }
        boolean up = VoiceText.has(t, "теплее", "потеплее", "жарче", "увеличь", "увеличить",
                "повысь", "прибавь", "добавь", "выше", "больше");
        boolean down = VoiceText.has(t, "холоднее", "похолоднее", "прохладнее", "уменьши",
                "уменьшить", "снизь", "понизь", "убавь", "ниже", "меньше");
        Integer n = VoiceText.firstInt(t);
        if (n != null) {
            if (up && !VoiceText.has(t, "до")) {
                WidgetsCommon.adjustTemp(c, n);
                return ok("Теплее на " + n + "°");
            }
            if (down && !VoiceText.has(t, "до")) {
                WidgetsCommon.adjustTemp(c, -n);
                return ok("Холоднее на " + n + "°");
            }
            int value = clamp(n, 16, 30);
            WidgetsCommon.setTemp(c, value);
            return ok("Температура " + value + "°");
        }
        if (up) {
            WidgetsCommon.stepTemp(c, 1);
            return ok("Теплее");
        }
        if (down) {
            WidgetsCommon.stepTemp(c, -1);
            return ok("Холоднее");
        }
        return no("Не понял команду");
    }

    private static Result vent(String t) {
        if (!VoiceText.has(t, "лицо", "ног", "стекл")) return null;
        if (!VoiceText.has(t, "обдув", "направлен", "воздух", "дуть")) return null;
        boolean face = VoiceText.has(t, "лицо", "лица");
        boolean feet = VoiceText.has(t, "ног", "ноги", "ногам");
        boolean glass = VoiceText.has(t, "стекл", "ветров", "лобов");
        int mode;
        if (face && feet) mode = 3;
        else if (feet && glass) mode = 6;
        else if (glass) mode = 4;
        else if (feet) mode = 2;
        else if (face) mode = 1;
        else return null;
        WidgetsCommon.setVent(mode);
        return ok("Обдув: " + ventName(mode));
    }

    private static Result fan(String t) {
        if (!VoiceText.has(t, "вентилятор", "обдув", "дуть", "дуй", "скорость обдува")) {
            return null;
        }
        if (VoiceText.has(t, "выключи", "выключить", "отключи", "отключить", "отруби",
                "стоп", "убери")) {
            WidgetsCommon.setFan(0);
            return ok("Обдув выключен");
        }
        if (VoiceText.has(t, "максим")) {
            WidgetsCommon.setFan(7);
            return ok("Обдув на максимум");
        }
        if (VoiceText.has(t, "минимум")) {
            WidgetsCommon.setFan(1);
            return ok("Обдув на минимум");
        }
        boolean up = VoiceText.has(t, "увеличь", "увеличить", "больше", "сильнее", "прибавь");
        boolean down = VoiceText.has(t, "уменьши", "уменьшить", "меньше", "слабее", "убавь");
        Integer n = VoiceText.firstInt(t);
        if (n != null) {
            int value = n;
            if (VoiceText.has(t, "процент") || value > 7) {
                value = Math.round(Math.max(0, Math.min(100, n)) * 7f / 100f);
            }
            if (up) {
                WidgetsCommon.adjustFan(value);
                return ok("Обдув " + fanLevel());
            }
            if (down) {
                WidgetsCommon.adjustFan(-value);
                return ok("Обдув " + fanLevel());
            }
            WidgetsCommon.setFan(value);
            return ok("Обдув " + fanLevel());
        }
        if (up) {
            WidgetsCommon.stepFan(1);
            return ok("Обдув " + fanLevel());
        }
        if (down) {
            WidgetsCommon.stepFan(-1);
            return ok("Обдув " + fanLevel());
        }
        WidgetsCommon.setFan(3);
        return ok("Обдув 3");
    }

    private static int fanLevel() {
        Integer level = WidgetsCommon.readFan();
        return level == null ? 0 : level;
    }

    private static Result recirc(Context c, String t) {
        if (!VoiceText.has(t, "рециркул", "забор", "свеж", "внутренний воздух",
                "воздух из салона", "наружн", "замкнут")) {
            return null;
        }
        boolean on = !VoiceText.has(t, "выключи", "выключить", "отключи", "отключить",
                "отруби", "стоп", "свеж", "наружн", "снаружи");
        WidgetsCommon.setRecirc(c, on);
        return ok(on ? "Рециркуляция включена" : "Свежий воздух");
    }

    private static Result wheel(Context c, String t) {
        if (!VoiceText.has(t, "рул")) return null;
        if (!VoiceText.has(t, "обогрев", "подогрев", "нагрев", "греть", "грей")) return null;
        Integer current = WidgetsCommon.readWheelHeatLevel();
        int level = level(t, current == null ? 0 : current);
        WidgetsCommon.setWheelHeatLevel(c, level);
        return ok(level == 0 ? "Обогрев руля выключен" : "Обогрев руля " + level);
    }

    private static Result seat(Context c, String t) {
        if (!VoiceText.has(t, "обогрев", "подогрев", "нагрев", "греть", "грей")) return null;
        boolean all = VoiceText.has(t, "сидений", "кресел", "сидушек", "все", "оба", "всех");
        boolean passenger = VoiceText.has(t, "пассаж", "прав");
        Integer current = WidgetsCommon.readSeatHeat(passenger ? 4 : 1);
        int level = level(t, current == null ? 0 : current);
        if (all) {
            WidgetsCommon.setSeatHeat(c, 1, level);
            WidgetsCommon.setSeatHeat(c, 4, level);
            return ok(level == 0 ? "Подогрев сидений выключен" : "Подогрев сидений " + level);
        }
        int area = passenger ? 4 : 1;
        WidgetsCommon.setSeatHeat(c, area, level);
        return ok(level == 0
                ? (passenger ? "Подогрев пассажирского выключен" : "Подогрев водительского выключен")
                : (passenger ? "Подогрев пассажирского " : "Подогрев водительского ") + level);
    }

    private static Result ac(Context c, String t) {
        if (!VoiceText.has(t, "кондиционер", "кондей", "климат")) return null;
        boolean on = !VoiceText.has(t, "выключи", "выключить", "отключи", "отключить",
                "отруби", "стоп", "убери");
        WidgetsCommon.setAc(c, on);
        if (on && VoiceText.has(t, "максим")) WidgetsCommon.setFan(7);
        return ok(on ? "Кондиционер включён" : "Кондиционер выключен");
    }

    private static Result drive(Context c, String t) {
        if (!VoiceText.has(t, "режим", "эко", "eco", "комфорт", "спорт")) return null;
        if (VoiceText.has(t, "обдув", "подогрев", "обогрев", "температур", "багажник")) return null;
        if (VoiceText.has(t, "следующ", "дальше", "вперед")) {
            WidgetsCommon.stepDrive(c, 1);
            return ok("Следующий режим");
        }
        if (VoiceText.has(t, "предыдущ", "назад")) {
            WidgetsCommon.stepDrive(c, -1);
            return ok("Предыдущий режим");
        }
        if (VoiceText.has(t, "эко", "eco", "экономичн")) {
            WidgetsCommon.applyDrive(c, CarProps.DRIVE_ECO);
            return ok("Режим Эко");
        }
        if (VoiceText.has(t, "комфорт")) {
            WidgetsCommon.applyDrive(c, CarProps.DRIVE_COMFORT);
            return ok("Режим Комфорт");
        }
        if (VoiceText.has(t, "спорт", "sport")) {
            WidgetsCommon.applyDrive(c, CarProps.DRIVE_SPORT);
            return ok("Режим Спорт");
        }
        return null;
    }

    private static Result regen(Context c, String t) {
        if (!VoiceText.has(t, "рекуп", "реген")) return null;
        if (VoiceText.has(t, "сильн", "максим", "высок", "strong")) {
            WidgetsCommon.applyRegen(c, CarProps.REGEN_STRONG);
            return ok("Сильная рекуперация");
        }
        if (VoiceText.has(t, "слаб", "мин", "низк", "weak")) {
            WidgetsCommon.applyRegen(c, CarProps.REGEN_WEAK);
            return ok("Слабая рекуперация");
        }
        if (VoiceText.has(t, "средн", "mid", "нормал")) {
            WidgetsCommon.applyRegen(c, CarProps.REGEN_MEDIUM);
            return ok("Средняя рекуперация");
        }
        if (VoiceText.has(t, "усиль", "увеличь", "больше")) {
            WidgetsCommon.stepRegen(c, 1);
            return ok("Рекуперация сильнее");
        }
        if (VoiceText.has(t, "ослаб", "уменьши", "меньше")) {
            WidgetsCommon.stepRegen(c, -1);
            return ok("Рекуперация слабее");
        }
        return null;
    }

    private static Result app(Context c, String t) {
        if (VoiceText.has(t, "навигатор", "навигаци", "нави", "карт", "маршрут", "navi", "maps")) {
            String name = launch(c, NAV_PACKAGES, "навигатор", "карты", "map", "navi", "yandex", "2гис", "2gis");
            return name == null ? no("Навигатор не найден") : ok("Открываю " + name);
        }
        if (VoiceText.has(t, "музык", "music", "плеер", "player", "плейлист", "spotify")) {
            String name = launch(c, MUSIC_PACKAGES, "музык", "music", "плеер", "player", "spotify");
            return name == null ? no("Музыка не найдена") : ok("Открываю " + name);
        }
        if (VoiceText.has(t, "радио", "radio")) {
            String name = launch(c, RADIO_PACKAGES, "радио", "radio");
            return name == null ? no("Радио не найдено") : ok("Открываю " + name);
        }
        if (VoiceText.has(t, "браузер", "browser")) {
            String name = launch(c, BROWSER_PACKAGES, "браузер", "browser", "chrome", "firefox", "yandex");
            return name == null ? no("Браузер не найден") : ok("Открываю " + name);
        }
        if (VoiceText.has(t, "ютуб", "youtube", "smarttube")) {
            String name = launch(c, YOUTUBE_PACKAGES, "youtube", "ютуб", "smarttube");
            return name == null ? no("YouTube не найден") : ok("Открываю " + name);
        }
        return null;
    }

    private static Result openGeneric(Context c, String t) {
        if (!VoiceText.has(t, "открой", "открыть", "запусти", "запустить", "включи", "включить")) {
            return null;
        }
        String query = t;
        for (String verb : new String[]{"открыть", "открой", "запустить", "запусти",
                "включить", "включи", "приложение", "программу", "пожалуйста"}) {
            query = query.replace(verb, " ");
        }
        query = query.replaceAll("\\s+", " ").trim();
        if (query.length() < 3) return null;
        String name = launchByLabel(c, query);
        return name == null ? null : ok("Открываю " + name);
    }

    private static Result openCustom(Context c, CustomVoiceCommands.Command command) {
        if (openPackage(c, command.packageName)) return ok("Открываю " + command.label);
        return no("Приложение не найдено");
    }

    private static Result home(Context c) {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(intent);
            return ok("Свернул");
        } catch (Throwable t) {
            return no("Не удалось свернуть");
        }
    }

    private static String launch(Context c, String[] packages, String... keys) {
        for (String pkg : packages) {
            if (openPackage(c, pkg)) {
                String label = labelOf(c, pkg);
                return label == null ? pkg : label;
            }
        }
        for (String key : keys) {
            String found = launchByLabel(c, key);
            if (found != null) return found;
        }
        return null;
    }

    private static boolean openPackage(Context c, String pkg) {
        try {
            Intent intent = c.getPackageManager().getLaunchIntentForPackage(pkg);
            if (intent == null) return false;
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(intent);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String labelOf(Context c, String pkg) {
        try {
            PackageManager pm = c.getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (Throwable t) {
            return null;
        }
    }

    private static String launchByLabel(Context c, String key) {
        try {
            PackageManager pm = c.getPackageManager();
            Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> list = new ArrayList<>(pm.queryIntentActivities(main, 0));
            list.sort(Comparator.comparing(info -> String.valueOf(info.loadLabel(pm))
                    .toLowerCase(Locale.ROOT)));
            String needle = VoiceText.normalize(key);
            for (ResolveInfo info : list) {
                String pkg = info.activityInfo.packageName;
                if (pkg.equals(c.getPackageName())) continue;
                String label = String.valueOf(info.loadLabel(pm));
                String normalizedLabel = VoiceText.normalize(label);
                String normalizedPkg = VoiceText.normalize(pkg);
                if (!normalizedLabel.contains(needle) && !normalizedPkg.contains(needle)) continue;
                if (!openPackage(c, pkg)) continue;
                return label;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static int level(String t, int current) {
        if (VoiceText.has(t, "выключи", "выключить", "отключи", "отключить", "отруби",
                "стоп", "убери", "погаси")) {
            return 0;
        }
        if (VoiceText.has(t, "максим")) return 3;
        if (VoiceText.has(t, "минимум")) return 1;
        if (VoiceText.has(t, "половин", "средн", "середин")) return 2;
        Integer n = VoiceText.firstInt(t);
        if (n != null) return n <= 0 ? 0 : clamp(n, 1, 3);
        if (VoiceText.has(t, "увеличь", "увеличить", "больше", "сильнее")) {
            return clamp(current + 1, 1, 3);
        }
        if (VoiceText.has(t, "уменьши", "уменьшить", "меньше", "слабее")) {
            return clamp(current - 1, 1, 3);
        }
        return 1;
    }

    private static String ventName(int mode) {
        switch (mode) {
            case 2:
                return "ноги";
            case 3:
                return "лицо и ноги";
            case 4:
                return "стекло";
            case 6:
                return "ноги и стекло";
            case 1:
            default:
                return "лицо";
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Result ok(String reply) {
        return new Result(true, reply);
    }

    private static Result no(String reply) {
        return new Result(false, reply);
    }
}
