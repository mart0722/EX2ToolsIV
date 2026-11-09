package com.ex2tools.iv.voice;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VoiceText {

    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern DIGITS = Pattern.compile("\\d{1,4}");
    private static final Pattern AND_VERB = Pattern.compile(
            "\\s+и\\s+(?=(?:включи|включить|выключи|выключить|открой|открыть|закрой|закрыть|"
                    + "поставь|поставить|установи|установить|сделай|сделать|увеличь|увеличить|"
                    + "уменьши|уменьшить|опусти|опустить|подними|поднять|отруби|стоп|запусти|"
                    + "запустить|переключи|прогрей|прогреть|охлади|охладить|сверни|свернуть|"
                    + "найди|найти|отключи|отключить|выруби|вырубить|погаси|погасить|"
                    + "нагрей|нагреть)(?:\\s|$))");

    private static final Map<String, Integer> NUMBERS = new HashMap<>();

    private static final String[] U = {
            "ноль", "один", "два", "три", "четыре", "пять", "шесть", "семь", "восемь", "девять"
    };
    private static final String[] T = {
            "десять", "одиннадцать", "двенадцать", "тринадцать", "четырнадцать",
            "пятнадцать", "шестнадцать", "семнадцать", "восемнадцать", "девятнадцать"
    };
    private static final String[] D = {
            "", "", "двадцать", "тридцать", "сорок", "пятьдесят",
            "шестьдесят", "семьдесят", "восемьдесят", "девяносто"
    };

    static {
        put(0, "ноль", "нуль", "ноля", "нуля");
        put(1, "один", "одна", "одно", "одну", "одного", "одной", "единица", "единицы", "единицу", "первый", "первая", "первое", "первую");
        put(2, "два", "две", "двух", "двум", "двумя", "двойка", "двойки", "двойку", "второй", "вторая", "второе", "вторую");
        put(3, "три", "трех", "трем", "тремя", "тройка", "третий", "третья", "третье", "третью");
        put(4, "четыре", "четырех", "четырем", "четырьмя", "четвертый", "четвертая", "четвертое", "четвертую", "четвёртый", "четвёртая", "четвёртое", "четвёртую");
        put(5, "пять", "пяти", "пятью", "пятый", "пятая", "пятое", "пятую");
        put(6, "шесть", "шести", "шестью", "шестой", "шестая", "шестое", "шестую");
        put(7, "семь", "семи", "семью", "седьмой", "седьмая", "седьмое", "седьмую");
        put(8, "восемь", "восьми", "восемью", "восьмой", "восьмая", "восьмое", "восьмую");
        put(9, "девять", "девяти", "девятью", "девятый", "девятая", "девятое", "девятую");
        put(10, "десять", "десяти", "десятью", "десятый", "десятая", "десятое", "десятую");
        put(11, "одиннадцать", "одиннадцати", "одиннадцатью");
        put(12, "двенадцать", "двенадцати", "двенадцатью");
        put(13, "тринадцать", "тринадцати", "тринадцатью");
        put(14, "четырнадцать", "четырнадцати", "четырнадцатью");
        put(15, "пятнадцать", "пятнадцати", "пятнадцатью");
        put(16, "шестнадцать", "шестнадцати", "шестнадцатью");
        put(17, "семнадцать", "семнадцати", "семнадцатью", "семьнадцать", "семьнадцати");
        put(18, "восемнадцать", "восемнадцати", "восемнадцатью");
        put(19, "девятнадцать", "девятнадцати", "девятнадцатью");
        put(20, "двадцать", "двадцати", "двадцатью");
        put(30, "тридцать", "тридцати", "тридцатью");
        put(40, "сорок", "сорока");
        put(50, "пятьдесят", "пятидесяти", "пятьюдесятью");
        put(60, "шестьдесят", "шестидесяти", "шестьюдесятью");
        put(70, "семьдесят", "семидесяти", "семьюдесятью");
        put(80, "восемьдесят", "восьмидесяти", "восемьюдесятью");
        put(90, "девяносто", "девяноста");
        put(100, "сто", "ста");
        put(200, "двести", "двухсот");
        put(300, "триста", "трехсот");
        put(400, "четыреста", "четырехсот");
        put(500, "пятьсот", "пятисот");
        put(600, "шестьсот", "шестисот");
        put(700, "семьсот", "семисот");
        put(800, "восемьсот", "восьмисот");
        put(900, "девятьсот", "девятисот");
        put(1000, "тысяча", "тысячи", "тысяч");
    }

    private VoiceText() {
    }

    private static void put(int value, String... words) {
        for (String word : words) NUMBERS.put(word, value);
    }

    public static String normalize(String s) {
        if (s == null) return "";
        String lower = s.toLowerCase(Locale.ROOT).replace('ё', 'е');
        StringBuilder sb = new StringBuilder(lower.length());
        boolean space = false;
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
                space = false;
            } else if (!space) {
                sb.append(' ');
                space = true;
            }
        }
        return SPACES.matcher(sb.toString()).replaceAll(" ").trim();
    }

    public static String digitize(String s) {
        String text = normalize(s);
        if (text.isEmpty()) return "";
        String[] parts = text.split(" ");
        StringBuilder out = new StringBuilder();
        int total = 0;
        int current = 0;
        boolean number = false;
        for (String part : parts) {
            Integer value = NUMBERS.get(part);
            if (value != null) {
                int n = value;
                if (n >= 1000) {
                    total += (current == 0 ? 1 : current) * n;
                    current = 0;
                } else {
                    current += n;
                }
                number = true;
                continue;
            }
            if (number) {
                if (out.length() > 0) out.append(' ');
                out.append(total + current);
                total = 0;
                current = 0;
                number = false;
            }
            if (out.length() > 0) out.append(' ');
            out.append(part);
        }
        if (number) {
            if (out.length() > 0) out.append(' ');
            out.append(total + current);
        }
        return out.toString().trim();
    }

    public static List<String> splitClauses(String s) {
        String text = normalize(s);
        if (text.isEmpty()) return new ArrayList<>();
        text = text.replaceAll("\\s*,\\s*", " | ");
        text = text.replaceAll("\\s*;\\s*", " | ");
        text = text.replaceAll("\\s+(?:затем|потом|далее|после этого|а также)\\s+", " | ");
        text = AND_VERB.matcher(text).replaceAll(" | ");
        List<String> out = new ArrayList<>();
        for (String part : text.split("\\s*\\|\\s*")) {
            String clause = part.trim();
            if (!clause.isEmpty()) out.add(clause);
        }
        if (out.isEmpty()) out.add(text);
        return out;
    }

    public static boolean has(String text, String... keys) {
        if (text == null || text.isEmpty()) return false;
        for (String key : keys) {
            if (key != null && !key.isEmpty() && text.contains(key)) return true;
        }
        return false;
    }

    public static Integer firstInt(String text) {
        if (text == null) return null;
        Matcher m = DIGITS.matcher(text);
        if (!m.find()) return null;
        try {
            return Integer.parseInt(m.group());
        } catch (Throwable t) {
            return null;
        }
    }

    public static String number(int value) {
        if (value < 0) return String.valueOf(value);
        if (value < 10) return U[value];
        if (value < 20) return T[value - 10];
        if (value < 100) {
            int tens = value / 10;
            int unit = value % 10;
            return unit == 0 ? D[tens] : D[tens] + " " + U[unit];
        }
        if (value == 100) return "сто";
        if (value < 1000) {
            int hundreds = value / 100;
            int rest = value % 100;
            String head = hundreds == 1 ? "сто" : hundreds == 2 ? "двести" : hundreds == 3 ? "триста"
                    : hundreds == 4 ? "четыреста" : hundreds == 5 ? "пятьсот"
                    : hundreds == 6 ? "шестьсот" : hundreds == 7 ? "семьсот"
                    : hundreds == 8 ? "восемьсот" : "девятьсот";
            return rest == 0 ? head : head + " " + number(rest);
        }
        return String.valueOf(value);
    }

    public static List<String> words(String s) {
        String text = normalize(s);
        if (text.isEmpty()) return new ArrayList<>();
        return Arrays.asList(text.split(" "));
    }
}
