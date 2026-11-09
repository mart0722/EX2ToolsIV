package com.ex2tools.iv;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Lifecycle;

import com.ex2tools.iv.apps.AppsFragment;
import com.ex2tools.iv.car.Telemetry;
import com.ex2tools.iv.ui.InfoFragment;
import com.ex2tools.iv.ui.OverviewFragment;
import com.ex2tools.iv.ui.ServiceModeFragment;
import com.ex2tools.iv.ui.SettingsFragment;
import com.ex2tools.iv.ui.SoundsFragment;
import com.ex2tools.iv.ui.StatusBarFragment;
import com.ex2tools.iv.ui.VoiceFragment;
import com.ex2tools.iv.ui.WidgetsFragment;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends AppCompatActivity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private TextView carStatus;
    private TextView lastUpdated;
    private View statusDot;
    private View statusPill;
    private LinearLayout navContainer;
    private int current = -1;
    private Runnable carTick;
    private final List<View> navViews = new ArrayList<>();

    private final Fragment[] fragments = new Fragment[9];
    private final Drawable[] heroDrawables = new Drawable[9];
    private static final String[] TAGS = {"overview", "settings", "status", "voice", "widgets",
            "sounds", "apps", "service", "info"};

    private static final int[] HERO_BG = {
            R.drawable.hero_overview, R.drawable.hero_settings, R.drawable.hero_status,
            R.drawable.hero_voice, R.drawable.hero_status, R.drawable.hero_sounds,
            0, 0, 0
    };

    private static final int[] ICONS = {
            R.drawable.ic_dashboard, R.drawable.ic_settings, R.drawable.ic_status,
            R.drawable.ic_voice, R.drawable.ic_widgets, R.drawable.ic_widget_volume,
            R.drawable.ic_apps, R.drawable.ic_service, R.drawable.ic_info
    };

    private static final int[] TITLES = {
            R.string.overview_title, R.string.settings_title, R.string.status_bar_title,
            R.string.voice_title, R.string.widgets_title, R.string.sounds_title,
            R.string.apps_page_title, R.string.service_title, R.string.info_title
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, bars.top, 0, bars.bottom);
            return insets;
        });
        applySystemBarAppearance();

        carStatus = findViewById(R.id.carStatus);
        lastUpdated = findViewById(R.id.lastUpdated);
        statusDot = findViewById(R.id.statusDot);
        statusPill = findViewById(R.id.statusPill);
        navContainer = findViewById(R.id.navContainer);

        buildNavigation();

        ImageButton refresh = findViewById(R.id.btnRefresh);
        refresh.setOnClickListener(v -> refreshNow());

        int restored = -1;
        if (savedInstanceState != null) {
            restored = savedInstanceState.getInt("current", -1);
            if (restored < 0 || restored >= fragments.length) restored = -1;
            for (int i = 0; i < TAGS.length; i++) {
                fragments[i] = getSupportFragmentManager().findFragmentByTag(TAGS[i]);
            }
        }

        final boolean firstLaunch = savedInstanceState == null;
        show(restored >= 0 ? restored : 0);
        getWindow().getDecorView().post(() -> {
            if (isFinishing() || isDestroyed()) return;
            if (firstLaunch) {
                handler.postDelayed(() -> preload(1), 2200L);
                handler.postDelayed(() -> preload(2), 4000L);
            }
            handler.postDelayed(this::syncServices, 700L);
        });
        startCarTicker();
        } catch (Throwable t) {
            LogStore.add(this, "UI CRASH: " + android.util.Log.getStackTraceString(t));
            showCrash(t);
        }
    }

    private void showCrash(Throwable t) {
        String trace = android.util.Log.getStackTraceString(t);
        try {
            java.io.File dir = getExternalFilesDir(null);
            if (dir != null) {
                if (!dir.exists()) dir.mkdirs();
                java.io.FileWriter fw = new java.io.FileWriter(
                        new java.io.File(dir, "last_crash.txt"), false);
                fw.write(trace);
                fw.close();
            }
        } catch (Throwable ignored) {
        }
        try {
            android.widget.ScrollView scroll = new android.widget.ScrollView(this);
            TextView text = new TextView(this);
            text.setText("EX2ToolsIV UI CRASH\n\n" + trace);
            text.setTextSize(14f);
            text.setTextIsSelectable(true);
            text.setPadding(32, 32, 32, 32);
            scroll.addView(text);
            setContentView(scroll);
        } catch (Throwable ignored) {
        }
    }

    private void applySystemBarAppearance() {
        boolean night = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(!night);
        controller.setAppearanceLightNavigationBars(!night);
    }

    private void buildNavigation() {
        navContainer.removeAllViews();
        navViews.clear();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < TITLES.length; i++) {
            View item = inflater.inflate(R.layout.item_nav, navContainer, false);
            ImageView icon = item.findViewById(R.id.navIcon);
            TextView label = item.findViewById(R.id.navLabel);
            icon.setImageResource(ICONS[i]);
            label.setText(TITLES[i]);
            final int index = i;
            item.setOnClickListener(v -> show(index));
            applyPressAnimation(item);
            navContainer.addView(item);
            navViews.add(item);
        }
    }

    private void applyWindowBackground(int index) {
        try {
            if (index >= 0 && index < heroDrawables.length && HERO_BG[index] != 0) {
                Drawable drawable = heroDrawables[index];
                if (drawable == null) {
                    drawable = ContextCompat.getDrawable(this, HERO_BG[index]);
                    heroDrawables[index] = drawable;
                }
                if (drawable != null) {
                    getWindow().setBackgroundDrawable(drawable);
                    return;
                }
            }
            getWindow().setBackgroundDrawable(new ColorDrawable(
                    ContextCompat.getColor(this, R.color.flyme_window_background)));
        } catch (Throwable t) {
            LogStore.add(this, "window background failed: " + t);
        }
    }

    private void syncServices() {
        if (isFinishing() || isDestroyed()) return;
        try {
            com.ex2tools.iv.status.StatusIconService.sync(this);
            com.ex2tools.iv.screenshot.ScreenshotButtonService.sync(this);
            com.ex2tools.iv.tasks.TaskManagerButtonService.sync(this);
            com.ex2tools.iv.widgets.WidgetUpdateService.sync(this);
            com.ex2tools.iv.service.DriveRegenWatchService.sync(this);
        } catch (Throwable t) {
            LogStore.add(this, "service sync failed: " + t);
        }
    }

    public void dropHiddenFragments() {
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        boolean any = false;
        for (int i = 0; i < fragments.length; i++) {
            Fragment f = fragments[i];
            if (f != null && i != current && f.isAdded()) {
                ft.remove(f);
                fragments[i] = null;
                any = true;
            }
        }
        if (any) ft.commitNowAllowingStateLoss();
    }

    private void applyPressAnimation(View view) {
        view.setOnTouchListener((v, event) -> {
            int action = event.getAction();
            if (action == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.97f).scaleY(0.97f).alpha(0.7f)
                        .setDuration(160L).setInterpolator(new DecelerateInterpolator(1.2f))
                        .start();
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).alpha(1f)
                        .setDuration(260L).setInterpolator(new DecelerateInterpolator(1.4f))
                        .start();
            }
            return false;
        });
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Throwable t) {
            return "1.0.0";
        }
    }

    private void show(int index) {
        if (index == current) return;
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.setReorderingAllowed(true);
        for (int i = 0; i < fragments.length; i++) {
            Fragment f = fragments[i];
            if (f != null && i != index && f.isAdded()) {
                ft.hide(f);
                ft.setMaxLifecycle(f, Lifecycle.State.STARTED);
            }
        }
        Fragment target = fragments[index];
        if (target == null) {
            target = createFragment(index);
            fragments[index] = target;
            ft.add(R.id.fragmentContainer, target, TAGS[index]);
        } else if (!target.isAdded()) {
            ft.add(R.id.fragmentContainer, target, TAGS[index]);
        } else {
            ft.show(target);
        }
        ft.setMaxLifecycle(target, Lifecycle.State.RESUMED);
        ft.commitNowAllowingStateLoss();
        current = index;
        applyWindowBackground(index);
        if (statusPill != null) {
            statusPill.setVisibility(index <= 2 ? View.VISIBLE : View.GONE);
        }
        updateNav(index);
    }

    private void preload(int index) {
        if (isFinishing() || isDestroyed()) return;
        if (index == current || fragments[index] != null) return;
        Fragment fragment = createFragment(index);
        fragments[index] = fragment;
        getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .add(R.id.fragmentContainer, fragment, TAGS[index])
                .hide(fragment)
                .setMaxLifecycle(fragment, Lifecycle.State.STARTED)
                .commitNowAllowingStateLoss();
    }

    private Fragment createFragment(int index) {
        switch (index) {
            case 1:
                return new SettingsFragment();
            case 2:
                return new StatusBarFragment();
            case 3:
                return new VoiceFragment();
            case 4:
                return new WidgetsFragment();
            case 5:
                return new SoundsFragment();
            case 6:
                return new AppsFragment();
            case 7:
                return new ServiceModeFragment();
            case 8:
                return new InfoFragment();
            case 0:
            default:
                return new OverviewFragment();
        }
    }

    private void updateNav(int active) {
        int blue = ContextCompat.getColor(this, R.color.flyme_blue);
        int inactive = ContextCompat.getColor(this, R.color.flyme_label_level1);
        for (int i = 0; i < navViews.size(); i++) {
            View item = navViews.get(i);
            boolean selected = i == active;
            item.setSelected(selected);
            View indicator = item.findViewById(R.id.navIndicator);
            if (indicator != null) {
                indicator.setBackgroundColor(selected ? blue : 0x00000000);
            }
            ImageView icon = item.findViewById(R.id.navIcon);
            if (icon != null) icon.setColorFilter(selected ? blue : inactive);
            TextView label = item.findViewById(R.id.navLabel);
            if (label != null) {
                label.setTextColor(selected ? blue : inactive);
            }
        }
    }

    private void startCarTicker() {
        carTick = new Runnable() {
            @Override
            public void run() {
                if (!executor.isShutdown()) {
                    executor.execute(() -> {
                        boolean ok = Telemetry.carReady(MainActivity.this);
                        String time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            carStatus.setText(ok ? R.string.car_connected : R.string.car_disconnected);
                            carStatus.setTextColor(ContextCompat.getColor(MainActivity.this,
                                    ok ? R.color.flyme_green : R.color.flyme_label_level2));
                            statusDot.setBackgroundTintList(ColorStateList.valueOf(
                                    ContextCompat.getColor(MainActivity.this,
                                            ok ? R.color.flyme_green : R.color.flyme_label_level3)));
                            lastUpdated.setText(time);
                        });
                    });
                }
                handler.postDelayed(this, 5000L);
            }
        };
        handler.post(carTick);
    }

    public void refreshNow() {
        handler.removeCallbacks(carTick);
        handler.post(carTick);
        Intent i = new Intent(Actions.REFRESH);
        i.setPackage(getPackageName());
        sendBroadcast(i);
    }

    public void openOverlaySettings() {
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Throwable t) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
            } catch (Throwable ignored) {
            }
        }
    }

    public void restartDriveApply(boolean once) {
        com.ex2tools.iv.service.DriveRegenService.start(this, once);
    }

    @Override
    public void onBackPressed() {
        if (current != 0) {
            show(0);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(@androidx.annotation.NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("current", current);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        super.onDestroy();
    }
}
