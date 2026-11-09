package com.ex2tools.iv.voice;

import android.content.Context;

import com.ex2tools.iv.Prefs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CustomVoiceCommands {

    public static final class Command {
        public final String id;
        public final String phrase;
        public final String packageName;
        public final String label;

        Command(String id, String phrase, String packageName, String label) {
            this.id = id;
            this.phrase = phrase;
            this.packageName = packageName;
            this.label = label;
        }
    }

    private CustomVoiceCommands() {
    }

    public static List<Command> load(Context c) {
        List<Command> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(Prefs.voiceCustomCommands(c));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String phrase = o.optString("phrase", "").trim();
                String pkg = o.optString("package", "").trim();
                if (phrase.isEmpty() || pkg.isEmpty()) continue;
                String id = o.optString("id", "");
                if (id.isEmpty()) id = UUID.randomUUID().toString();
                String label = o.optString("label", pkg);
                out.add(new Command(id, phrase, pkg, label));
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    public static void save(Context c, List<Command> commands) {
        JSONArray arr = new JSONArray();
        for (Command command : commands) {
            try {
                JSONObject o = new JSONObject();
                o.put("id", command.id);
                o.put("phrase", command.phrase);
                o.put("package", command.packageName);
                o.put("label", command.label);
                arr.put(o);
            } catch (Throwable ignored) {
            }
        }
        Prefs.setVoiceCustomCommands(c, arr.toString());
    }

    public static void upsert(Context c, String id, String phrase, String packageName, String label) {
        List<Command> commands = load(c);
        String key = id == null || id.isEmpty() ? UUID.randomUUID().toString() : id;
        boolean replaced = false;
        for (int i = 0; i < commands.size(); i++) {
            if (commands.get(i).id.equals(key)) {
                commands.set(i, new Command(key, phrase, packageName, label));
                replaced = true;
                break;
            }
        }
        if (!replaced) commands.add(new Command(key, phrase, packageName, label));
        save(c, commands);
    }

    public static void delete(Context c, String id) {
        List<Command> commands = load(c);
        for (int i = commands.size() - 1; i >= 0; i--) {
            if (commands.get(i).id.equals(id)) commands.remove(i);
        }
        save(c, commands);
    }

    public static boolean exists(Context c, String phrase, String exceptId) {
        String normalized = VoiceText.normalize(phrase);
        if (normalized.isEmpty()) return false;
        for (Command command : load(c)) {
            if (exceptId != null && exceptId.equals(command.id)) continue;
            if (VoiceText.normalize(command.phrase).equals(normalized)) return true;
        }
        return false;
    }

    public static Command match(Context c, String text) {
        String normalized = VoiceText.normalize(text);
        if (normalized.isEmpty()) return null;
        for (Command command : load(c)) {
            String phrase = VoiceText.normalize(command.phrase);
            if (phrase.isEmpty()) continue;
            if (normalized.equals(phrase) || normalized.contains(phrase)) return command;
            String[] tokens = phrase.split(" ");
            int useful = 0;
            boolean all = true;
            for (String token : tokens) {
                if (token.length() < 3) continue;
                useful++;
                if (!normalized.contains(token)) {
                    all = false;
                    break;
                }
            }
            if (useful > 0 && all) return command;
        }
        return null;
    }

    public static List<String> phrases(Context c) {
        List<String> out = new ArrayList<>();
        for (Command command : load(c)) {
            String phrase = VoiceText.normalize(command.phrase);
            if (!phrase.isEmpty() && !out.contains(phrase)) out.add(phrase);
        }
        return out;
    }
}
