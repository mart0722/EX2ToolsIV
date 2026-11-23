package com.ex2tools.iv.service;

import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.NotificationHelper;
import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.car.CarProps;
import com.ex2tools.iv.car.Telemetry;
import com.ex2tools.iv.drive.DriveController;
import com.ex2tools.iv.state.VehicleState;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Принудительное удержание режима движения и рекуперации.
 *
 * Работает только если пользователь включил переключатель «Удерживать режим».
 * Пишет в машину строго в четырёх случаях:
 *   1) после загрузки ГУ (сервис стартует из BootReceiver);
 *   2) после пробуждения экрана (SCREEN_ON / USER_PRESENT) или включения зажигания;
 *   3) после смены передачи;
 *   4) по команде синхронизации из UI.
 *
 * Ручное переключение в штатном меню не «перебивается»: если значение
 * изменилось вне наших окон применения, оно принимается как новое желаемое.
 * Есть потолок: не больше MAX_APPLIES попыток за WINDOW_MS.
 */
public final class DriveRegenWatchService extends Service {

    private static final long WINDOW_MS = 10 * 60 * 1000L;
    private static final int MAX_APPLIES = 8;
    private static final long QUIET_BEFORE_ADOPT_MS = 30_000L;
    private static final long WAKE_GRACE_MS = 180_000L;

    private static volatile boolean running;

    private final AtomicBoolean forceApply = new AtomicBoolean();
    private volatile boolean stopRequested;
    private volatile long wakeGraceUntil;
    private boolean driveRestored;
    private int stableDrive = Integer.MIN_VALUE;
    private int stableRegen = Integer.MIN_VALUE;
    private int stableCount;
    private Thread worker;

    private final BroadcastReceiver wakeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            requestRestore(WAKE_GRACE_MS);
        }
    };

    private void requestRestore(long graceMs) {
        wakeGraceUntil = SystemClock.elapsedRealtime() + graceMs;
        forceApply.set(true);
    }

    public static void start(Context context) {
        try {
            ContextCompat.startForegroundService(context,
                    new Intent(context, DriveRegenWatchService.class));
        } catch (Throwable t) {
            LogStore.add(context, "start drive watch failed: " + t);
        }
    }

    public static void stop(Context context) {
        try {
            context.stopService(new Intent(context, DriveRegenWatchService.class));
        } catch (Throwable ignored) {
        }
    }

    /** Включён переключатель — сервис работает, выключен — останавливается. */
    public static void sync(Context context) {
        if (Prefs.autoApply(context)) start(context);
        else stop(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(NotificationHelper.ID_DRIVE_WATCH, NotificationHelper.driveWatch(this));
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        try {
            ContextCompat.registerReceiver(this, wakeReceiver, filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED);
        } catch (Throwable t) {
            LogStore.add(this, "drive watch receiver failed: " + t);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!Prefs.autoApply(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        requestRestore(120_000L);
        if (!running) {
            running = true;
            worker = new Thread(this::runWatch, "ex2-drive-watch");
            worker.start();
        }
        return START_STICKY;
    }

    private void runWatch() {
        try {
            VehicleState state = VehicleState.get();
            for (int attempt = 0; attempt < 36 && !stopRequested; attempt++) {
                if (state.ensure(this)) break;
                Thread.sleep(5_000L);
            }
            if (!state.isReady()) {
                LogStore.add(this, "drive watch: VHAL недоступен, выходим");
                return;
            }
            state.subscribe(CarProps.DRIVE_MODE, CarProps.DRIVE_AREAS);
            state.subscribe(CarProps.REGEN_MODE, CarProps.REGEN_AREAS);

            DriveController controller = DriveController.get();
            long[] delays = {0L, 1500L, 4000L, 8000L, 16000L};
            long lastWriteAt = 0L;
            int lastGear = Telemetry.gear(this);
            long windowStart = SystemClock.elapsedRealtime();
            long lastRangeSample = 0L;
            int applies = 0;
            boolean ignitionKnown = false;
            boolean waitingForIgnition = false;

            // Первичное применение после загрузки/синхронизации.
            for (long delay : delays) {
                if (stopRequested || Thread.currentThread().isInterrupted()) return;
                if (!Prefs.autoApply(this)) return;
                if (delay > 0) Thread.sleep(delay);
                Boolean ignitionOn = Telemetry.ignitionOn();
                ignitionKnown |= ignitionOn != null;
                if (ignitionKnown && !Boolean.TRUE.equals(ignitionOn)) {
                    waitingForIgnition = Boolean.FALSE.equals(ignitionOn);
                    driveRestored = false;
                    break;
                }
                DriveController.Outcome r = controller.applySaved(this);
                driveRestored = r.readDrive != null && r.readDrive == r.drive;
                applies++;
                lastWriteAt = SystemClock.elapsedRealtime();
                LogStore.add(this, "watch apply: " + r.describe());
                if (r.exact) break;
            }

            while (!Thread.currentThread().isInterrupted() && !stopRequested) {
                if (!Prefs.autoApply(this)) break;
                Thread.sleep(1000L);

                long now = SystemClock.elapsedRealtime();
                if (now - lastRangeSample > 10_000L) {
                    lastRangeSample = now;
                    com.ex2tools.iv.car.RangeModel.sample(this);
                }
                if (now - windowStart > WINDOW_MS) {
                    windowStart = now;
                    applies = 0;
                }

                Boolean ignitionOn = Telemetry.ignitionOn();
                ignitionKnown |= ignitionOn != null;
                if (ignitionKnown && !Boolean.TRUE.equals(ignitionOn)) {
                    if (Boolean.FALSE.equals(ignitionOn)) {
                        waitingForIgnition = true;
                        driveRestored = false;
                    }
                    stableCount = 0;
                    continue;
                }
                boolean ignitionResumed = waitingForIgnition;
                if (ignitionResumed) {
                    waitingForIgnition = false;
                    // На Flyme экран может остаться включённым между поездками.
                    requestRestore(WAKE_GRACE_MS);
                }

                int gear = Telemetry.gear(this);
                boolean gearChanged = gear != Integer.MIN_VALUE && gear != lastGear;
                if (gearChanged) {
                    lastGear = gear;
                    forceApply.set(true);
                }

                if (forceApply.getAndSet(false)) {
                    String trigger = ignitionResumed ? "ignition" : gearChanged ? "gear" : "wake";
                    stableCount = 0;
                    if (applies < MAX_APPLIES) {
                        DriveController.Outcome r = controller.applySaved(this);
                        driveRestored = r.readDrive != null && r.readDrive == r.drive;
                        applies++;
                        lastWriteAt = SystemClock.elapsedRealtime();
                        LogStore.add(this, "watch " + trigger + " apply: " + r.describe());
                    } else {
                        driveRestored = false;
                        LogStore.add(this, "watch " + trigger + ": лимит попыток исчерпан");
                    }
                }

                // Ручное переключение в штатном меню принимаем как новое желаемое,
                // но только вне окна применения, чтобы не спорить с ECU.
                if (now > wakeGraceUntil && now - lastWriteAt > QUIET_BEFORE_ADOPT_MS) {
                    adoptManualChange(controller);
                }
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            LogStore.add(this, "drive watch error: " + t);
        } finally {
            running = false;
            stopSelf();
        }
    }

    private void adoptManualChange(DriveController controller) {
        Integer drive = controller.currentDrive();
        Integer regen = controller.currentRegen();
        // Принимаем ручную правку только если значение стабильно несколько чтений:
        // сразу после wake ECU может отдавать дефолт comfort/medium.
        if (drive == null || regen == null) return;
        if (drive == stableDrive && regen == stableRegen) {
            stableCount++;
        } else {
            stableDrive = drive;
            stableRegen = regen;
            stableCount = 1;
        }
        if (stableCount < 5) return;
        boolean changed = false;
        int savedDrive = Prefs.drive(this);
        if (drive == savedDrive) driveRestored = true;
        // Пока восстановление не подтверждено, ECU может продолжать отдавать
        // режим по умолчанию. Не заменяем им сохранённый выбор.
        if (driveRestored && drive != savedDrive) {
            Prefs.setDrive(this, drive);
            changed = true;
        }
        if (regen != null && regen != Prefs.regen(this)) {
            Prefs.setRegen(this, regen);
            changed = true;
        }
        if (changed) {
            LogStore.add(this, "watch: принято ручное переключение drive=" + drive
                    + " regen=" + regen);
        }
    }

    @Override
    public void onDestroy() {
        stopRequested = true;
        running = false;
        if (worker != null) worker.interrupt();
        try {
            unregisterReceiver(wakeReceiver);
        } catch (Throwable ignored) {
        }
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(NotificationHelper.ID_DRIVE_WATCH);
        } catch (Throwable ignored) {
        }
        stopForeground(true);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
