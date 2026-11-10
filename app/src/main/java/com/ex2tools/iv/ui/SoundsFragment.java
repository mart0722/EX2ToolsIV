package com.ex2tools.iv.ui;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.R;
import com.ex2tools.iv.apps.ApkPickerDialog;
import com.ex2tools.iv.car.CarProps;
import com.ex2tools.iv.car.Telemetry;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class SoundsFragment extends Fragment {

    private static final String PREFS = "lock_sounds";
    private static final String KEY_SELECTED = "selected_path";
    private static final String SETTING_PATH = "lock_car_audio_path";
    private static final String SETTING_ENABLE = "lock_car_audio_enable";
    private static final String SETTING_CHECK = "lock_car_audio_enable_check";
    private static final String FACTORY_DEFAULT = "/vendor/etc/carlock/yuedongyinghe.wav";
    private static final String ASSET_DIR = "lock_sounds";
    private static final int REQ_STORAGE = 4101;
    private static final long MAX_BYTES = 1024L * 1024L;
    private static final double MAX_SECONDS = 10.0;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<String> bundledNames = new HashSet<>();

    private FlymeSwitchRow activate;
    private TextView status;
    private LinearLayout builtInList;
    private LinearLayout factoryList;
    private LinearLayout customList;
    private TextView customEmpty;
    private MediaPlayer player;
    private AudioFocusRequest focusRequest;
    private boolean binding;
    private boolean loading;
    private String selectedPath;

    private static final class Sound {
        final String name;
        final String path;
        final String kind;
        final boolean factory;
        final boolean bundled;

        Sound(String name, String path, String kind, boolean factory, boolean bundled) {
            this.name = name;
            this.path = path;
            this.kind = kind;
            this.factory = factory;
            this.bundled = bundled;
        }
    }

    private static final class WavInfo {
        final long seconds;
        final long bytes;

        WavInfo(long seconds, long bytes) {
            this.seconds = seconds;
            this.bytes = bytes;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_sounds, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Context c = requireContext();
        activate = view.findViewById(R.id.soundActivate);
        status = view.findViewById(R.id.soundStatus);
        builtInList = view.findViewById(R.id.soundsBuiltInList);
        factoryList = view.findViewById(R.id.soundsFactoryList);
        customList = view.findViewById(R.id.soundsCustomList);
        customEmpty = view.findViewById(R.id.soundsCustomEmpty);

        selectedPath = prefs(c).getString(KEY_SELECTED, null);

        activate.setOnCheckedChangeListener((b, checked) -> {
            if (binding) return;
            onActivateChanged(checked);
        });

        view.findViewById(R.id.soundsAdd).setOnClickListener(v -> pickWav());
        view.findViewById(R.id.soundsRestore).setOnClickListener(v -> restoreFactory());

        loadSounds();
    }

    private void loadSounds() {
        if (loading || !isAdded()) return;
        loading = true;
        status.setText(R.string.sounds_loading);
        Context c = requireContext();
        new Thread(() -> {
            copyBundledSounds(c);
            if (!isAdded()) return;
            handler.post(() -> {
                loading = false;
                if (isAdded()) refreshLists();
            });
        }, "lock-sounds-load").start();
    }

    private void copyBundledSounds(Context c) {
        try {
            File dir = soundsDir(c);
            if (!dir.exists()) dir.mkdirs();
            String[] names = c.getAssets().list(ASSET_DIR);
            if (names == null) return;
            for (String name : names) {
                if (!name.toLowerCase(Locale.US).endsWith(".wav")) continue;
                File target = new File(dir, name);
                if (target.exists() && target.length() > 0) {
                    bundledNames.add(name);
                    continue;
                }
                try (InputStream in = c.getAssets().open(ASSET_DIR + "/" + name);
                     OutputStream out = new FileOutputStream(target)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) > 0) {
                        out.write(buffer, 0, read);
                    }
                    bundledNames.add(name);
                } catch (Throwable t) {
                    LogStore.add(c, "lock sound asset copy failed " + name + ": " + t);
                }
            }
        } catch (Throwable t) {
            LogStore.add(c, "lock sound asset load failed: " + t);
        }
    }

    private void refreshLists() {
        if (!isAdded()) return;
        Context c = requireContext();
        builtInList.removeAllViews();
        factoryList.removeAllViews();
        customList.removeAllViews();

        List<Sound> builtIns = bundledSounds(c);
        List<Sound> factory = factorySounds();
        List<Sound> custom = customSounds(c);

        for (Sound s : builtIns) addRow(builtInList, s, false);
        for (Sound s : factory) addRow(factoryList, s, false);
        for (Sound s : custom) addRow(customList, s, true);
        customEmpty.setVisibility(custom.isEmpty() ? View.VISIBLE : View.GONE);

        String path = currentPath(c);
        int enabled = currentEnabled(c);
        boolean active = enabled == 1 && path != null && !path.isEmpty()
                && !FACTORY_DEFAULT.equals(path) && new File(path).isFile();
        if (!active && path != null && !path.isEmpty() && !FACTORY_DEFAULT.equals(path)) {
            // The setting still points at our sound, but the file is missing.
            active = false;
        }
        binding = true;
        activate.setChecked(active);
        binding = false;

        if (active) {
            String name = nameForPath(c, path);
            status.setText(getString(R.string.sounds_status_active,
                    name == null ? getString(R.string.sounds_unknown) : name));
        } else {
            status.setText(R.string.sounds_status_factory);
        }
    }

    private List<Sound> bundledSounds(Context c) {
        List<Sound> out = new ArrayList<>();
        try {
            String[] names = c.getAssets().list(ASSET_DIR);
            if (names == null) return out;
            List<String> list = new ArrayList<>();
            for (String n : names) {
                if (n.toLowerCase(Locale.US).endsWith(".wav")) list.add(n);
            }
            list.sort(String.CASE_INSENSITIVE_ORDER);
            for (String n : list) {
                File f = new File(soundsDir(c), n);
                if (!f.isFile()) continue;
                out.add(new Sound(stripExt(n), f.getAbsolutePath(),
                        getString(R.string.sounds_builtin_label), false, true));
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private List<Sound> factorySounds() {
        List<Sound> out = new ArrayList<>();
        out.add(new Sound(getString(R.string.sounds_factory_name_default),
                FACTORY_DEFAULT, getString(R.string.sounds_factory_label), true, false));
        out.add(new Sound(getString(R.string.sounds_factory_name_stars),
                "/vendor/etc/carlock/xingguangshanyao.wav",
                getString(R.string.sounds_factory_label), true, false));
        out.add(new Sound(getString(R.string.sounds_factory_name_energy),
                "/vendor/etc/carlock/nengliangshouheng.wav",
                getString(R.string.sounds_factory_label), true, false));
        out.add(new Sound(getString(R.string.sounds_factory_name_luck),
                "/vendor/etc/carlock/haoyunbugu.wav",
                getString(R.string.sounds_factory_label), true, false));
        out.add(new Sound(getString(R.string.sounds_factory_name_big_luck),
                "/vendor/etc/carlock/dajidali.wav",
                getString(R.string.sounds_factory_label), true, false));
        List<Sound> existing = new ArrayList<>();
        for (Sound s : out) {
            if (new File(s.path).isFile()) existing.add(s);
        }
        return existing;
    }

    private List<Sound> customSounds(Context c) {
        List<Sound> out = new ArrayList<>();
        File[] files = soundsDir(c).listFiles();
        if (files == null) return out;
        for (File f : files) {
            if (!f.isFile() || !f.getName().toLowerCase(Locale.US).endsWith(".wav")) continue;
            if (bundledNames.contains(f.getName())) continue;
            out.add(new Sound(stripExt(f.getName()), f.getAbsolutePath(),
                    getString(R.string.sounds_custom_label), false, false));
        }
        out.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return out;
    }

    private void addRow(LinearLayout parent, Sound sound, boolean deletable) {
        Context c = requireContext();
        boolean selected = sound.path.equals(selectedPath);
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_sound_row);
        row.setSelected(selected);
        int pad = dp(16);
        row.setPadding(pad, dp(10), pad, dp(10));
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.bottomMargin = dp(8);
        row.setLayoutParams(rowLp);
        row.setOnClickListener(v -> select(sound));

        TextView play = new TextView(c);
        play.setText("▶");
        play.setTextSize(18f);
        play.setGravity(Gravity.CENTER);
        play.setTextColor(c.getColor(R.color.flyme_blue));
        play.setBackgroundResource(R.drawable.bg_sound_play);
        play.setOnClickListener(v -> preview(sound));
        LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams(dp(48), dp(48));
        playLp.setMarginEnd(dp(14));
        row.addView(play, playLp);

        LinearLayout texts = new LinearLayout(c);
        texts.setOrientation(LinearLayout.VERTICAL);
        row.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView name = new TextView(c);
        name.setText(sound.name);
        name.setTextSize(19f);
        name.setTextColor(c.getColor(R.color.flyme_label_level1));
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        texts.addView(name);

        TextView meta = new TextView(c);
        WavInfo info = readWav(sound.path);
        StringBuilder details = new StringBuilder(sound.kind);
        if (info != null) {
            details.append(" · ").append(formatSize(info.bytes))
                    .append(" · ").append(formatSeconds(info.seconds));
        }
        meta.setText(details.toString());
        meta.setTextSize(12f);
        meta.setTextColor(c.getColor(R.color.flyme_label_level2));
        texts.addView(meta);

        TextView check = new TextView(c);
        check.setText(selected ? "✓" : "");
        check.setTextSize(24f);
        check.setGravity(Gravity.CENTER);
        check.setTextColor(c.getColor(R.color.flyme_blue));
        row.addView(check, new LinearLayout.LayoutParams(dp(38), dp(48)));

        if (deletable) {
            TextView delete = new TextView(c);
            delete.setText("×");
            delete.setTextSize(24f);
            delete.setGravity(Gravity.CENTER);
            delete.setTextColor(c.getColor(R.color.flyme_label_level2));
            delete.setOnClickListener(v -> confirmDelete(sound));
            row.addView(delete, new LinearLayout.LayoutParams(dp(34), dp(48)));
        }

        parent.addView(row);
    }

    private void select(Sound sound) {
        Context c = requireContext();
        selectedPath = sound.path;
        prefs(c).edit().putString(KEY_SELECTED, selectedPath).apply();
        boolean active = !FACTORY_DEFAULT.equals(sound.path);
        binding = true;
        activate.setChecked(active);
        binding = false;
        applySound(sound.path, active);
        refreshLists();
    }

    private void onActivateChanged(boolean checked) {
        Context c = requireContext();
        if (checked) {
            String path = selectedPath;
            if (path == null || !new File(path).isFile() || FACTORY_DEFAULT.equals(path)) {
                List<Sound> built = bundledSounds(c);
                if (!built.isEmpty()) path = built.get(0).path;
            }
            if (path == null || !new File(path).isFile()) {
                binding = true;
                activate.setChecked(false);
                binding = false;
                Toast.makeText(c, R.string.sounds_apply_failed, Toast.LENGTH_LONG).show();
                return;
            }
            selectedPath = path;
            prefs(c).edit().putString(KEY_SELECTED, path).apply();
            applySound(path, true);
        } else {
            applySound(FACTORY_DEFAULT, false);
        }
        refreshLists();
    }

    private void restoreFactory() {
        Context c = requireContext();
        selectedPath = FACTORY_DEFAULT;
        prefs(c).edit().putString(KEY_SELECTED, FACTORY_DEFAULT).apply();
        binding = true;
        activate.setChecked(false);
        binding = false;
        applySound(FACTORY_DEFAULT, false);
        refreshLists();
    }

    private void applySound(String path, boolean active) {
        Context c = requireContext();
        String target = active ? path : FACTORY_DEFAULT;
        if (active && (target == null || !new File(target).isFile())) {
            Toast.makeText(c, R.string.sounds_apply_failed, Toast.LENGTH_LONG).show();
            return;
        }
        try {
            ContentResolver resolver = c.getContentResolver();
            Settings.System.putString(resolver, SETTING_PATH, target);
            Settings.System.putInt(resolver, SETTING_ENABLE, 1);
            Settings.System.putString(resolver, SETTING_CHECK,
                    String.valueOf(indexForPath(c, target)));
            LogStore.add(c, "lock sound applied: " + target);
            Toast.makeText(c, R.string.sounds_applied, Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            LogStore.add(c, "lock sound apply failed: " + t);
            Toast.makeText(c, R.string.sounds_apply_failed, Toast.LENGTH_LONG).show();
        }
    }

    private int indexForPath(Context c, String path) {
        if (path == null || FACTORY_DEFAULT.equals(path)) return 0;
        int index = 1;
        for (Sound s : bundledSounds(c)) {
            if (s.path.equals(path)) return index;
            index++;
        }
        for (Sound s : factorySounds()) {
            if (s.path.equals(path)) return index;
            index++;
        }
        for (Sound s : customSounds(c)) {
            if (s.path.equals(path)) return index;
            index++;
        }
        return 0;
    }

    private void preview(Sound sound) {
        Context c = requireContext();
        if (Telemetry.gear(c) != CarProps.GEAR_PARK) {
            Toast.makeText(c, R.string.sounds_preview_only_p, Toast.LENGTH_SHORT).show();
            return;
        }
        stopPreview();
        try {
            File file = new File(sound.path);
            if (!file.isFile()) {
                Toast.makeText(c, R.string.sounds_apply_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            player = new MediaPlayer();
            player.setAudioAttributes(attrs);
            player.setDataSource(file.getAbsolutePath());
            player.setVolume(0.55f, 0.55f);
            player.setOnCompletionListener(mp -> stopPreview());
            player.setOnErrorListener((mp, what, extra) -> {
                stopPreview();
                return true;
            });
            player.prepare();
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setAudioAttributes(attrs)
                        .build();
                am.requestAudioFocus(focusRequest);
            }
            player.start();
        } catch (Throwable t) {
            LogStore.add(c, "lock sound preview failed: " + t);
            stopPreview();
            Toast.makeText(c, R.string.sounds_apply_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void stopPreview() {
        try {
            if (player != null) {
                if (player.isPlaying()) player.stop();
                player.release();
            }
        } catch (Throwable ignored) {
        }
        player = null;
        try {
            if (focusRequest != null) {
                Context c = getContext();
                if (c != null) {
                    AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
                    if (am != null) am.abandonAudioFocusRequest(focusRequest);
                }
            }
        } catch (Throwable ignored) {
        }
        focusRequest = null;
    }

    private void pickWav() {
        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_STORAGE);
            return;
        }
        ApkPickerDialog.showWav(requireContext(), this::importPickedWav);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_STORAGE && isAdded()) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                ApkPickerDialog.showWav(requireContext(), this::importPickedWav);
            } else {
                Toast.makeText(requireContext(), R.string.apps_picker_no_access,
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void importPickedWav(File source) {
        Context c = requireContext();
        new Thread(() -> {
            File added = importWav(c, source);
            handler.post(() -> {
                if (!isAdded()) return;
                if (added != null) {
                    Toast.makeText(c, R.string.sounds_added, Toast.LENGTH_SHORT).show();
                    refreshLists();
                } else {
                    Toast.makeText(c, R.string.sounds_add_failed, Toast.LENGTH_LONG).show();
                }
            });
        }, "lock-sound-import").start();
    }

    private File importWav(Context c, File source) {
        if (source == null || !source.isFile()) return null;
        File dir = soundsDir(c);
        if (!dir.exists() && !dir.mkdirs()) return null;
        String name = source.getName();
        if (name == null || name.trim().isEmpty()) name = "sound.wav";
        name = sanitize(name);
        if (!name.toLowerCase(Locale.US).endsWith(".wav")) name = name + ".wav";
        File target = uniqueFile(dir, name);
        File temp = new File(dir, target.getName() + ".part");
        try (InputStream in = new FileInputStream(source);
             OutputStream out = new FileOutputStream(temp)) {
            if (in == null) return null;
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = in.read(buffer)) > 0) {
                total += read;
                if (total > MAX_BYTES) {
                    temp.delete();
                    handler.post(() -> Toast.makeText(c, R.string.sounds_too_large,
                            Toast.LENGTH_LONG).show());
                    return null;
                }
                out.write(buffer, 0, read);
            }
        } catch (Throwable t) {
            temp.delete();
            LogStore.add(c, "lock sound import failed: " + t);
            return null;
        }
        WavInfo info = readWav(temp.getAbsolutePath());
        if (info == null) {
            temp.delete();
            handler.post(() -> Toast.makeText(c, R.string.sounds_invalid_wav,
                    Toast.LENGTH_LONG).show());
            return null;
        }
        if (!temp.renameTo(target)) {
            temp.delete();
            return null;
        }
        if (info.seconds > MAX_SECONDS) {
            handler.post(() -> Toast.makeText(c, R.string.sounds_too_long,
                    Toast.LENGTH_LONG).show());
        }
        return target;
    }

    private void confirmDelete(Sound sound) {
        Context c = requireContext();
        FlymeDialog.showConfirm(c,
                getString(R.string.sounds_delete_confirm, sound.name),
                getString(R.string.sounds_info),
                getString(R.string.yes),
                getString(R.string.cancel),
                () -> new Thread(() -> {
                    boolean ok = new File(sound.path).delete();
                    handler.post(() -> {
                        if (!isAdded()) return;
                        Toast.makeText(c, ok ? R.string.sounds_deleted
                                : R.string.sounds_delete_failed, Toast.LENGTH_SHORT).show();
                        if (sound.path.equals(selectedPath)) {
                            restoreFactory();
                        } else {
                            refreshLists();
                        }
                    });
                }, "lock-sound-delete").start());
    }

    private String currentPath(Context c) {
        try {
            return Settings.System.getString(c.getContentResolver(), SETTING_PATH);
        } catch (Throwable t) {
            return null;
        }
    }

    private int currentEnabled(Context c) {
        try {
            return Settings.System.getInt(c.getContentResolver(), SETTING_ENABLE, 1);
        } catch (Throwable t) {
            return 1;
        }
    }

    private String nameForPath(Context c, String path) {
        if (path == null) return null;
        if (FACTORY_DEFAULT.equals(path)) return getString(R.string.sounds_factory_name_default);
        for (Sound s : bundledSounds(c)) if (s.path.equals(path)) return s.name;
        for (Sound s : factorySounds()) if (s.path.equals(path)) return s.name;
        for (Sound s : customSounds(c)) if (s.path.equals(path)) return s.name;
        return null;
    }

    private SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private File soundsDir(Context c) {
        return new File(Environment.getExternalStorageDirectory(), "Music/LockMusic");
    }

    private String stripExt(String name) {
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(0, i) : name;
    }

    private String sanitize(String name) {
        String s = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return s.isEmpty() ? "sound.wav" : s;
    }

    private File uniqueFile(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) return f;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; i < 1000; i++) {
            f = new File(dir, base + "-" + i + ext);
            if (!f.exists()) return f;
        }
        return new File(dir, System.currentTimeMillis() + ext);
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
        return null;
    }

    private WavInfo readWav(String path) {
        try (FileInputStream in = new FileInputStream(path)) {
            if (in.read() != 'R' || in.read() != 'I' || in.read() != 'F' || in.read() != 'F') {
                return null;
            }
            readLeInt(in);
            if (in.read() != 'W' || in.read() != 'A' || in.read() != 'V' || in.read() != 'E') {
                return null;
            }
            int audioFormat = 0;
            int channels = 0;
            int rate = 0;
            int bits = 0;
            long dataSize = 0;
            while (true) {
                int c1 = in.read();
                if (c1 < 0) break;
                int c2 = in.read();
                int c3 = in.read();
                int c4 = in.read();
                if (c2 < 0 || c3 < 0 || c4 < 0) break;
                String id = "" + (char) c1 + (char) c2 + (char) c3 + (char) c4;
                long size = readLeInt(in) & 0xffffffffL;
                if ("fmt ".equals(id)) {
                    audioFormat = readLeShort(in);
                    channels = readLeShort(in);
                    rate = readLeInt(in);
                    readLeInt(in);
                    readLeShort(in);
                    bits = readLeShort(in);
                    long skip = size - 16;
                    while (skip > 0) {
                        if (in.skip(skip) <= 0) break;
                        skip = 0;
                    }
                } else if ("data".equals(id)) {
                    dataSize = size;
                    break;
                } else {
                    long skip = size + (size & 1);
                    while (skip > 0) {
                        long n = in.skip(skip);
                        if (n <= 0) break;
                        skip -= n;
                    }
                }
            }
            if (audioFormat != 1 || channels < 1 || channels > 2 || bits != 16
                    || rate < 8000 || rate > 48000 || dataSize <= 0) return null;
            long bytes = new File(path).length();
            long seconds = Math.max(0, dataSize / (rate * channels * 2L));
            return new WavInfo(seconds, bytes);
        } catch (Throwable t) {
            return null;
        }
    }

    private static int readLeShort(InputStream in) throws Exception {
        int a = in.read();
        int b = in.read();
        if (a < 0 || b < 0) throw new IllegalStateException("eof");
        return (a & 0xff) | ((b & 0xff) << 8);
    }

    private static int readLeInt(InputStream in) throws Exception {
        int a = in.read();
        int b = in.read();
        int c = in.read();
        int d = in.read();
        if (a < 0 || b < 0 || c < 0 || d < 0) throw new IllegalStateException("eof");
        return (a & 0xff) | ((b & 0xff) << 8) | ((c & 0xff) << 16) | ((d & 0xff) << 24);
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " Б";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.0f КБ", bytes / 1024.0);
        return String.format(Locale.US, "%.1f МБ", bytes / (1024.0 * 1024.0));
    }

    private String formatSeconds(long seconds) {
        return seconds + " с";
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!loading) refreshLists();
    }

    @Override
    public void onPause() {
        stopPreview();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        stopPreview();
        super.onDestroyView();
    }
}
