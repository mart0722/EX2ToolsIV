package com.ex2tools.iv.voice;

import android.content.Context;
import android.media.AudioManager;
import android.provider.Settings;

import com.ex2tools.iv.car.CarConnection;
import com.ex2tools.iv.car.CarProps;

public final class SystemControls {

    private SystemControls() {
    }

    public static int volumePercent(Context c) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return 0;
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int current = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        return max <= 0 ? 0 : Math.round(current * 100f / max);
    }

    public static void setVolumePercent(Context c, int percent) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int value = Math.round(Math.max(0, Math.min(100, percent)) * max / 100f);
        try {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0);
        } catch (Throwable ignored) {
        }
    }

    public static void stepVolume(Context c, int deltaPercent) {
        setVolumePercent(c, volumePercent(c) + deltaPercent);
    }

    public static void mute(Context c, boolean value) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        try {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    value ? AudioManager.ADJUST_MUTE : AudioManager.ADJUST_UNMUTE, 0);
        } catch (Throwable ignored) {
        }
    }

    public static int brightnessPercent(Context c) {
        try {
            int value = Settings.System.getInt(c.getContentResolver(), "screen_brightness", 128);
            return Math.round(Math.max(0, Math.min(255, value)) * 100f / 255f);
        } catch (Throwable t) {
            return 0;
        }
    }

    public static boolean setBrightnessPercent(Context c, int percent) {
        try {
            int value = Math.round(Math.max(0, Math.min(100, percent)) * 255f / 100f);
            return Settings.System.putInt(c.getContentResolver(), "screen_brightness", value);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void stepBrightness(Context c, int deltaPercent) {
        setBrightnessPercent(c, brightnessPercent(c) + deltaPercent);
    }

    public static boolean setDayNight(Context c, boolean night) {
        CarConnection cc = CarConnection.get();
        if (cc.ensure(c)) {
            int value = night ? CarProps.DAY_NIGHT_NIGHT : CarProps.DAY_NIGHT_DAY;
            if (cc.writeInt(CarProps.DAY_NIGHT_SETTING, 0, value)) return true;
        }
        try {
            return Settings.System.putString(c.getContentResolver(), "day_night",
                    night ? "night" : "day");
        } catch (Throwable t) {
            return false;
        }
    }
}
