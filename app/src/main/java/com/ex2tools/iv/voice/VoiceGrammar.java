package com.ex2tools.iv.voice;

import android.content.Context;

import com.ex2tools.iv.R;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

public final class VoiceGrammar {

    private VoiceGrammar() {
    }

    public static String build(Context c) {
        return build(c, true);
    }

    public static String build(Context c, boolean includeCustom) {
        Set<String> phrases = new LinkedHashSet<>();
        loadBase(c, phrases);
        if (includeCustom) {
            for (String phrase : CustomVoiceCommands.phrases(c)) {
                if (isCyrillic(phrase)) add(phrases, phrase);
            }
        }
        phrases.add("[unk]");
        JSONArray array = new JSONArray();
        for (String phrase : phrases) array.put(phrase);
        return array.toString();
    }

    private static void loadBase(Context c, Set<String> phrases) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                c.getResources().openRawResource(R.raw.voice_phrases), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                add(phrases, line);
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean isCyrillic(String phrase) {
        boolean hasLetter = false;
        for (int i = 0; i < phrase.length(); i++) {
            char c = phrase.charAt(i);
            if (c >= 'а' && c <= 'я') {
                hasLetter = true;
            } else if (!(c >= '0' && c <= '9') && c != ' ') {
                return false;
            }
        }
        return hasLetter;
    }

    private static void add(Set<String> set, String phrase) {
        String normalized = VoiceText.normalize(phrase);
        if (!normalized.isEmpty()) set.add(normalized);
    }
}
