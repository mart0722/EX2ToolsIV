package com.ex2tools.iv.sensor;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;

import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.Prefs;

import java.util.ArrayList;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Фоновое сканирование BLE и разбор MiBeacon от внешнего датчика (Xiaomi).
 *
 * Работает без BouncyCastle: AES-CCM разбирает {@link AesCcm}.
 * Сканирование идёт постоянно, пока сервис климата запущен: датчик рекламируется
 * примерно раз в минуту, поэтому окна сканирования низкого энергопотребления
 * могут пропускать пакеты. При долгом молчании сканер перезапускается.
 */
public final class CabinSensor {

    public static final ParcelUuid MIBEACON =
            ParcelUuid.fromString("0000fe95-0000-1000-8000-00805f9b34fb");

    private static final long WATCHDOG_MS = 60_000L;
    private static final long RESTART_AFTER_MS = 5 * 60_000L;

    public interface Listener {
        void onReading(MiBeacon.Reading reading);
    }

    /** Устройство, замеченное при сканировании: для выбора датчика в настройках. */
    public static final class Seen {
        public final String address;
        public String name;
        public int rssi;
        public long at;
        public boolean decoded;
        public float tempC = Float.NaN;

        Seen(String address) {
            this.address = address;
        }
    }

    private static volatile CabinSensor instance;

    private final Context app;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ex2-cabin-sensor");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, Seen> seen = new ConcurrentHashMap<>();

    private BluetoothLeScanner scanner;
    private volatile boolean scanning;
    private boolean receiverRegistered;
    private volatile long lastPacketAt;
    private volatile String status = "выключен";
    private volatile MiBeacon.Reading last;

    private final BroadcastReceiver btReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
                if (state == BluetoothAdapter.STATE_ON) {
                    restart();
                } else if (state == BluetoothAdapter.STATE_OFF) {
                    status = "Bluetooth выключен";
                }
            }
        }
    };

    private final Runnable watchdog = new Runnable() {
        @Override
        public void run() {
            if (!scanning) return;
            long now = System.currentTimeMillis();
            String mac = Prefs.sensorMac(app);
            if (!mac.isEmpty() && lastPacketAt > 0 && now - lastPacketAt > RESTART_AFTER_MS) {
                LogStore.add(app, "cabin sensor: no packets for "
                        + ((now - lastPacketAt) / 1000) + "s, restart scan");
                restartScanOnly();
            }
            handler.postDelayed(this, WATCHDOG_MS);
        }
    };

    public static CabinSensor get(Context context) {
        CabinSensor local = instance;
        if (local == null) {
            synchronized (CabinSensor.class) {
                local = instance;
                if (local == null) {
                    local = new CabinSensor(context.getApplicationContext());
                    instance = local;
                }
            }
        }
        return local;
    }

    private CabinSensor(Context app) {
        this.app = app;
    }

    public static boolean hasLocationPermission(Context c) {
        try {
            return c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean start() {
        if (scanning) return true;
        BluetoothManager manager =
                (BluetoothManager) app.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null) {
            status = "Bluetooth недоступен";
            return false;
        }
        if (!adapter.isEnabled()) {
            status = "Bluetooth выключен";
            return false;
        }
        if (!hasLocationPermission(app)) {
            status = "нет разрешения на геолокацию";
            return false;
        }
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            status = "BLE-сканер недоступен";
            return false;
        }
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .build();
        try {
            scanner.startScan(null, settings, callback);
        } catch (Throwable t) {
            status = "не удалось запустить скан";
            LogStore.add(app, "cabin sensor start failed: " + t);
            return false;
        }
        scanning = true;
        status = "сканирование";
        if (!receiverRegistered) {
            try {
                app.registerReceiver(btReceiver,
                        new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
                receiverRegistered = true;
            } catch (Throwable ignored) {
            }
        }
        handler.removeCallbacks(watchdog);
        handler.postDelayed(watchdog, WATCHDOG_MS);
        LogStore.add(app, "cabin sensor: scan started");
        return true;
    }

    public void stop() {
        handler.removeCallbacks(watchdog);
        if (receiverRegistered) {
            try {
                app.unregisterReceiver(btReceiver);
            } catch (Throwable ignored) {
            }
            receiverRegistered = false;
        }
        stopScanOnly();
        status = "выключен";
        LogStore.add(app, "cabin sensor: scan stopped");
    }

    public void restart() {
        restartScanOnly();
        handler.removeCallbacks(watchdog);
        handler.postDelayed(watchdog, WATCHDOG_MS);
    }

    private void stopScanOnly() {
        if (scanner != null && scanning) {
            try {
                scanner.stopScan(callback);
            } catch (Throwable ignored) {
            }
        }
        scanning = false;
    }

    private void restartScanOnly() {
        stopScanOnly();
        start();
    }

    public boolean isScanning() {
        return scanning;
    }

    public String status() {
        return status;
    }

    public MiBeacon.Reading last() {
        return last;
    }

    public long ageMs() {
        MiBeacon.Reading r = last;
        if (r == null) {
            r = fromPrefs();
        }
        return r == null ? Long.MAX_VALUE : System.currentTimeMillis() - r.at;
    }

    public void addListener(Listener listener) {
        if (listener != null && !listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public void clearSeen() {
        seen.clear();
    }

    public List<Seen> seenSnapshot() {
        return new ArrayList<>(seen.values());
    }

    private MiBeacon.Reading fromPrefs() {
        long at = Prefs.sensorAt(app);
        if (at <= 0) return null;
        MiBeacon.Reading r = new MiBeacon.Reading();
        r.tempC = Prefs.sensorTemp(app);
        r.humidity = Prefs.sensorHumidity(app);
        r.battery = Prefs.sensorBattery(app);
        r.at = at;
        return r;
    }

    private final ScanCallback callback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            try {
                handleResult(result);
            } catch (Throwable t) {
                LogStore.add(app, "cabin sensor parse failed: " + t);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            scanning = false;
            status = "ошибка сканирования " + errorCode;
            LogStore.add(app, "cabin sensor scan failed: " + errorCode);
        }
    };

    private void handleResult(ScanResult result) {
        if (result == null || result.getScanRecord() == null) return;
        Map<ParcelUuid, byte[]> serviceData = result.getScanRecord().getServiceData();
        if (serviceData == null) return;
        byte[] payload = serviceData.get(MIBEACON);
        if (payload == null) return;

        String address = result.getDevice() == null ? "" : result.getDevice().getAddress();
        if (address == null) address = "";
        String name = result.getDevice() == null ? "" : result.getDevice().getName();

        Seen entry = seen.get(address);
        if (entry == null) {
            entry = new Seen(address);
            seen.put(address, entry);
        }
        entry.name = name;
        entry.rssi = result.getRssi();
        entry.at = System.currentTimeMillis();

        String mac = Prefs.sensorMac(app);
        if (mac.isEmpty() || !mac.equalsIgnoreCase(address)) {
            return;
        }
        String keyHex = Prefs.sensorKey(app);
        if (keyHex.replaceAll("[^0-9A-Fa-f]", "").length() != 32) {
            status = "нужен ключ датчика";
            return;
        }
        byte[] key = MiBeacon.hex(keyHex);
        MiBeacon.Reading reading = MiBeacon.parse(
                payload, MiBeacon.hex(mac.replace(":", "")), key);

        entry.decoded = reading != null && reading.hasData();
        if (entry.decoded) entry.tempC = reading.tempC;
        if (reading == null || !reading.hasData()) {
            if (reading != null && reading.error != null) {
                LogStore.add(app, "cabin sensor: frame error " + reading.error);
            }
            return;
        }
        reading.address = address;
        reading.rssi = result.getRssi();
        last = reading;
        lastPacketAt = System.currentTimeMillis();
        Prefs.setSensorReading(app, reading.tempC, reading.humidity,
                reading.battery, reading.at);
        dispatch(reading);
    }

    private void dispatch(MiBeacon.Reading reading) {
        worker.execute(() -> {
            for (Listener listener : listeners) {
                try {
                    listener.onReading(reading);
                } catch (Throwable t) {
                    LogStore.add(app, "cabin sensor listener failed: " + t);
                }
            }
        });
    }
}
