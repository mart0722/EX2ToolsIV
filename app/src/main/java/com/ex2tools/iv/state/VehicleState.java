package com.ex2tools.iv.state;

import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.car.CarProps;
import com.ex2tools.iv.vehicle.CapabilityRegistry;
import com.ex2tools.iv.vehicle.VhalGateway;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Единственный источник чтения состояния машины.
 *
 * Значения приходят подписками CarPropertyManager, кэшируются по паре
 * id/зона и раздаются слушателям с объединением частых событий.
 *
 * Для свойств, которые молчат (не присылают события), работает медленный
 * опрос-подстраховка. Записей в VHAL здесь нет по построению.
 */
public final class VehicleState {

    /** Слушатель получает одно событие на пачку изменений. */
    public interface Listener {
        void onVehicleStateChanged();
    }

    /** Значение считаем свежим, пока оно не старше этого времени. */
    private static final long FRESH_MS = 10_000L;
    /** Период подстраховочного опроса молчащих свойств. */
    private static final long POLL_MS = 2_500L;
    /** Если событий нет дольше этого времени, свойство опрашивается. */
    private static final long EVENT_SILENCE_MS = 6_000L;
    /** Объединение частых событий перед рассылкой слушателям. */
    private static final long NOTIFY_DELAY_MS = 200L;

    private static volatile VehicleState instance;

    private final Object lock = new Object();
    private final Map<Long, Entry> entries = new HashMap<>();
    private final Set<Long> wanted = new HashSet<>();
    private final Set<Integer> registered = new HashSet<>();
    private final Set<Integer> failed = new HashSet<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    private final Handler main = new Handler(Looper.getMainLooper());

    private CarPropertyManager cpm;
    private Context appContext;
    private Thread poller;
    private volatile boolean polling;
    private boolean notifyScheduled;
    private long eventCount;
    private long notifyCount;

    private final CarPropertyManager.CarPropertyEventCallback callback =
            new CarPropertyManager.CarPropertyEventCallback() {
                @Override
                public void onChangeEvent(CarPropertyValue value) {
                    if (value == null) return;
                    int id = value.getPropertyId();
                    Object raw = id == CarProps.HVAC_TEMP_AOSP
                            ? climateSetpoint(value) : value.getValue();
                    if (raw == null) return;
                    long ts = value.getTimestamp() / 1_000_000L;
                    if (ts <= 0L) ts = SystemClock.elapsedRealtime();
                    put(id, value.getAreaId(), raw, ts, true);
                }

                @Override
                public void onErrorEvent(int propertyId, int zone) {
                    // Ничего не делаем: опрос-подстраховка всё равно прочитает значение.
                }
            };

    public static VehicleState get() {
        VehicleState local = instance;
        if (local == null) {
            synchronized (VehicleState.class) {
                local = instance;
                if (local == null) {
                    local = new VehicleState();
                    instance = local;
                }
            }
        }
        return local;
    }

    private VehicleState() {
    }

    public boolean ensure(Context context) {
        VhalGateway gateway = VhalGateway.get();
        if (!gateway.ensure(context)) return false;
        CarPropertyManager current = gateway.cpm();
        if (current == null) return false;

        boolean reconnected = false;
        synchronized (lock) {
            appContext = context.getApplicationContext();
            if (cpm != current) {
                cpm = current;
                registered.clear();
                failed.clear();
                reconnected = true;
            }
        }
        if (reconnected) {
            log("connected: " + (capabilities() == null ? 0 : capabilities().count())
                    + " properties");
            startPoller();
        }
        return true;
    }

    public boolean isReady() {
        return cpm != null;
    }

    // ------------------------------------------------------------ reading

    public Object value(int id, int area) {
        return value(id, area, FRESH_MS);
    }

    public Object value(int id, int area, long maxAgeMs) {
        long key = key(id, area);
        Entry entry;
        synchronized (lock) {
            entry = entries.get(key);
        }
        long now = SystemClock.elapsedRealtime();
        if (entry != null && entry.value != null && now - entry.readMs <= maxAgeMs) {
            return entry.value;
        }
        Object fresh = readDirect(id, area);
        if (fresh != null) {
            put(id, area, fresh, now, false);
            return fresh;
        }
        return entry == null ? null : entry.value;
    }

    public Integer intValue(int id, int area) {
        Object value = value(id, area);
        return value instanceof Number ? ((Number) value).intValue() : null;
    }

    public Float floatValue(int id, int area) {
        Object value = value(id, area);
        return value instanceof Number ? ((Number) value).floatValue() : null;
    }

    public Boolean boolValue(int id, int area) {
        Object value = value(id, area);
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).intValue() != 0;
        return null;
    }

    public Integer intAny(int id, int[] areas) {
        for (int area : areas) {
            Integer value = intValue(id, area);
            if (value != null) return value;
        }
        return null;
    }

    public Float floatAny(int id, int[] areas) {
        for (int area : areas) {
            Float value = floatValue(id, area);
            if (value != null) return value;
        }
        return null;
    }

    public Boolean boolAny(int id, int[] areas) {
        for (int area : areas) {
            Boolean value = boolValue(id, area);
            if (value != null) return value;
        }
        return null;
    }

    public long ageMs(int id, int area) {
        long key = key(id, area);
        synchronized (lock) {
            Entry entry = entries.get(key);
            if (entry == null || entry.value == null) return Long.MAX_VALUE;
            return SystemClock.elapsedRealtime() - entry.readMs;
        }
    }

    public boolean fresh(int id, int area) {
        return ageMs(id, area) <= FRESH_MS;
    }

    // ------------------------------------------------------------- areas

    /**
     * Зоны свойства: сначала реальные из CapabilityRegistry, иначе запасные
     * из CarProps. Убирает расхождения вроде recirc area=117.
     */
    public int[] areas(int id, int[] fallback) {
        CapabilityRegistry caps = capabilities();
        if (caps != null && caps.has(id)) {
            int[] areas = caps.areas(id);
            if (areas != null && areas.length > 0) return areas;
        }
        return fallback == null ? new int[]{0} : fallback;
    }

    public int preferredArea(int id, int fallback) {
        int[] areas = areas(id, new int[]{fallback});
        return areas.length == 0 ? fallback : areas[0];
    }

    /** Все зоны из реестра; если свойство однозонное, вернёт {0}. */
    public int[] areasOf(int id) {
        return areas(id, new int[]{0});
    }

    // --------------------------------------------------------- subscribing

    public void subscribe(int id, int[] areas) {
        if (areas == null) return;
        boolean needRegister;
        synchronized (lock) {
            for (int area : areas) wanted.add(key(id, area));
            needRegister = !registered.contains(id) && !failed.contains(id);
            if (needRegister) registered.add(id);
        }
        if (needRegister) register(id);
        startPoller();
    }

    public void unsubscribe(int id, int[] areas) {
        if (areas == null) return;
        synchronized (lock) {
            for (int area : areas) wanted.remove(key(id, area));
        }
    }

    private void register(int id) {
        CarPropertyManager manager = cpm;
        if (manager == null) return;
        try {
            if (!manager.registerCallback(callback, id, 0f)) {
                synchronized (lock) {
                    registered.remove(id);
                    failed.add(id);
                }
                log(String.format(java.util.Locale.US,
                        "subscribe rejected 0x%08X", id));
            }
        } catch (Throwable t) {
            synchronized (lock) {
                registered.remove(id);
                failed.add(id);
            }
            log(String.format(java.util.Locale.US,
                    "subscribe failed 0x%08X: %s", id, t.getClass().getSimpleName()));
        }
    }

    // ------------------------------------------------------------ poller

    private void startPoller() {
        synchronized (lock) {
            if (polling) return;
            polling = true;
        }
        poller = new Thread(this::pollLoop, "vehicle-state");
        poller.setDaemon(true);
        poller.start();
    }

    private void pollLoop() {
        long lastLog = SystemClock.elapsedRealtime();
        while (polling) {
            try {
                Thread.sleep(POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            long now = SystemClock.elapsedRealtime();
            List<Long> keys;
            synchronized (lock) {
                keys = new ArrayList<>(wanted);
            }
            for (long key : keys) {
                try {
                    int id = (int) (key >> 32);
                    int area = (int) key;
                    Entry entry;
                    synchronized (lock) {
                        entry = entries.get(key);
                    }
                    if (entry != null && entry.value != null
                            && now - entry.eventMs < EVENT_SILENCE_MS) {
                        continue;
                    }
                    if (entry != null && now - entry.readMs < POLL_MS) continue;
                    Object value = readDirect(id, area);
                    if (value != null) put(id, area, value, now, false);
                } catch (Throwable ignored) {
                }
            }
            if (now - lastLog >= 60_000L) {
                lastLog = now;
                log("state: " + describe());
            }
        }
    }

    // ------------------------------------------------------------ notify

    private void put(int id, int area, Object value, long valueTs, boolean event) {
        long key = key(id, area);
        boolean changed;
        synchronized (lock) {
            Entry entry = entries.get(key);
            if (entry == null) {
                entry = new Entry();
                entries.put(key, entry);
            }
            changed = entry.value == null || !entry.value.equals(value);
            entry.value = value;
            entry.valueTs = valueTs;
            entry.readMs = SystemClock.elapsedRealtime();
            if (event) {
                entry.eventMs = entry.readMs;
                entry.evented = true;
                eventCount++;
            }
            if (changed) entry.changedMs = entry.readMs;
        }
        if (changed) scheduleNotify();
    }

    /** Redraw controls that display a locally accepted command before hardware feedback. */
    public void requestDisplayRefresh() {
        scheduleNotify();
    }

    private void scheduleNotify() {
        synchronized (lock) {
            if (notifyScheduled) return;
            notifyScheduled = true;
        }
        main.postDelayed(() -> {
            synchronized (lock) {
                notifyScheduled = false;
                notifyCount++;
            }
            for (Listener listener : listeners) {
                try {
                    listener.onVehicleStateChanged();
                } catch (Throwable t) {
                    log("listener failed: " + t);
                }
            }
        }, NOTIFY_DELAY_MS);
    }

    public void addListener(Listener listener) {
        if (listener != null && !listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    // ------------------------------------------------------------- helpers

    private Object readDirect(int id, int area) {
        CarPropertyManager manager = cpm;
        if (manager == null) return null;
        try {
            if (id == CarProps.HVAC_TEMP_AOSP) {
                // Типизированное чтение просит адаптер Flyme преобразовать код MCU в градусы.
                return climateSetpoint(manager.getProperty(Float.class, id, area));
            }
            CarPropertyValue<?> value = manager.getProperty(id, area);
            return value == null ? null : value.getValue();
        } catch (Throwable t) {
            return null;
        }
    }

    private static Float climateSetpoint(CarPropertyValue<?> value) {
        if (value == null || value.getStatus() != CarPropertyValue.STATUS_AVAILABLE) return null;
        Object degrees = value.getValue();
        return degrees instanceof Float ? (Float) degrees : null;
    }

    private CapabilityRegistry capabilities() {
        CapabilityRegistry caps = VhalGateway.get().capabilities();
        return caps;
    }

    private static long key(int id, int area) {
        return ((long) id << 32) | (area & 0xFFFFFFFFL);
    }

    public String describe() {
        int values;
        int evented;
        synchronized (lock) {
            values = entries.size();
            evented = 0;
            for (Entry entry : entries.values()) {
                if (entry.evented) evented++;
            }
        }
        return "subscribed=" + registered.size()
                + " values=" + values
                + " evented=" + evented
                + " events=" + eventCount
                + " batches=" + notifyCount
                + (failed.isEmpty() ? "" : " failed=" + failed.size());
    }

    private void log(String message) {
        Context context = appContext;
        if (context != null) LogStore.add(context, "VehicleState: " + message);
    }

    private static final class Entry {
        Object value;
        long valueTs;
        long readMs;
        long changedMs;
        long eventMs;
        boolean evented;
    }
}
