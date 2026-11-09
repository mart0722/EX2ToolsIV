package com.ex2tools.iv.car;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Parcel;
import android.os.SystemClock;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Read-only probe for the factory Energy app's exported ChargeStatusService.
 * Never writes anything; if the service is not running, bind returns false and
 * we do not create it.
 */
public final class ChargeStatusProbe {

    private static final String PKG = "com.flyme.auto.energy";
    private static final String ACTION = "com.flyme.auto.energy.action.CHARGE_STATUS_SERVICE";
    private static final String DESCRIPTOR = "com.flyme.auto.energy.IChargeStatusService";

    private static volatile long lastCheck;
    private static volatile boolean lastCharging;

    private ChargeStatusProbe() {
    }

    /**
     * Cached charging flag from the factory Energy service. Blocking; call from a
     * worker thread only. Returns false when the service is not running.
     */
    public static boolean isCharging(Context c) {
        long now = SystemClock.elapsedRealtime();
        if (lastCheck != 0 && now - lastCheck < 15000L) return lastCharging;
        synchronized (ChargeStatusProbe.class) {
            now = SystemClock.elapsedRealtime();
            if (lastCheck != 0 && now - lastCheck < 15000L) return lastCharging;
            boolean charging = false;
            final CountDownLatch latch = new CountDownLatch(1);
            final IBinder[] binder = new IBinder[1];
            ServiceConnection connection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    binder[0] = service;
                    latch.countDown();
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                }
            };
            Intent intent = new Intent(ACTION);
            intent.setPackage(PKG);
            boolean bound = false;
            try {
                bound = c.bindService(intent, connection, 0);
            } catch (Throwable ignored) {
            }
            if (bound) {
                try {
                    if (latch.await(1200, TimeUnit.MILLISECONDS) && binder[0] != null) {
                        charging = transact(binder[0], 4) == 5;
                    }
                } catch (Throwable ignored) {
                } finally {
                    try {
                        c.unbindService(connection);
                    } catch (Throwable ignored) {
                    }
                }
            }
            lastCharging = charging;
            lastCheck = SystemClock.elapsedRealtime();
            return charging;
        }
    }

    /** Blocking; call from a worker thread only. */
    public static String read(Context c) {
        final CountDownLatch latch = new CountDownLatch(1);
        final IBinder[] binder = new IBinder[1];
        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                binder[0] = service;
                latch.countDown();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
            }
        };
        Intent intent = new Intent(ACTION);
        intent.setPackage(PKG);
        boolean bound;
        try {
            bound = c.bindService(intent, connection, 0);
        } catch (Throwable t) {
            return "bind failed: " + t;
        }
        if (!bound) return "not running (bind=false)";
        try {
            if (!latch.await(1500, TimeUnit.MILLISECONDS) || binder[0] == null) {
                return "bind timeout";
            }
            int state = transact(binder[0], 4);
            int soc = transact(binder[0], 3);
            String stateText = state == 5 ? "charging" : (state == 0 ? "idle" : String.valueOf(state));
            return "state=" + stateText + " (" + state + "), soc=" + soc;
        } catch (Throwable t) {
            return "read failed: " + t;
        } finally {
            try {
                c.unbindService(connection);
            } catch (Throwable ignored) {
            }
        }
    }

    private static int transact(IBinder binder, int code) throws android.os.RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            binder.transact(code, data, reply, 0);
            reply.readException();
            return reply.readInt();
        } finally {
            reply.recycle();
            data.recycle();
        }
    }
}
