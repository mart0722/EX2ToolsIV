package com.ex2tools.iv.car;

import android.content.Context;

import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class DriveRegenController {

    private final Context context;

    public DriveRegenController(Context context) {
        this.context = context.getApplicationContext();
    }

    public Result applySaved() {
        return apply(Prefs.drive(context), Prefs.regen(context));
    }

    public Result apply(int drive, int regen) {
        CarConnection cc = CarConnection.get();
        if (!cc.ensure(context)) {
            return new Result(false, false, drive, regen, Integer.MIN_VALUE, Integer.MIN_VALUE,
                    context.getString(R.string.car_api_unavailable));
        }

        boolean driveOk = writeProp(cc, CarProps.DRIVE_MODE, drive);
        sleep(450);
        boolean regenOk = writeProp(cc, CarProps.REGEN_MODE, regen);
        sleep(250);

        int readDrive = readProp(cc, CarProps.DRIVE_MODE);
        int readRegen = readProp(cc, CarProps.REGEN_MODE);
        boolean exact = readDrive == drive && readRegen == regen;
        boolean ok = driveOk && regenOk;

        String applied = labelDrive(readDrive) + " / " + labelRegen(readRegen);
        if (readDrive == Integer.MIN_VALUE || readRegen == Integer.MIN_VALUE) {
            applied = labelDrive(drive) + " / " + labelRegen(regen);
        }
        String ts = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        Prefs.setLastApplied(context, applied + "  " + ts);
        Prefs.setLastResult(context, ok
                ? (exact ? context.getString(R.string.drive_applied) : context.getString(R.string.drive_applied) + " (readback " + applied + ")")
                : context.getString(R.string.drive_apply_failed));
        LogStore.add(context, "drive=" + drive + " regen=" + regen
                + " ok=" + ok + " exact=" + exact
                + " read=" + readDrive + "/" + readRegen);

        return new Result(ok, exact, drive, regen, readDrive, readRegen,
                exact ? context.getString(R.string.drive_applied) : context.getString(R.string.drive_apply_failed));
    }

    private boolean writeProp(CarConnection cc, int prop, int value) {
        boolean any = false;
        for (int area : CarProps.AREAS) {
            if (cc.writeInt(prop, area, value)) {
                any = true;
            }
        }
        return any;
    }

    private int readProp(CarConnection cc, int prop) {
        for (int area : CarProps.AREAS) {
            int v = cc.readInt(prop, area);
            if (v != Integer.MIN_VALUE) return v;
        }
        return Integer.MIN_VALUE;
    }

    public String labelDrive(int value) {
        return CarProps.driveLabel(context, value);
    }

    public String labelRegen(int value) {
        return CarProps.regenLabel(context, value);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    public static final class Result {
        public final boolean ok;
        public final boolean exact;
        public final int drive;
        public final int regen;
        public final int readDrive;
        public final int readRegen;
        public final String message;

        public Result(boolean ok, boolean exact, int drive, int regen,
                      int readDrive, int readRegen, String message) {
            this.ok = ok;
            this.exact = exact;
            this.drive = drive;
            this.regen = regen;
            this.readDrive = readDrive;
            this.readRegen = readRegen;
            this.message = message;
        }
    }
}
