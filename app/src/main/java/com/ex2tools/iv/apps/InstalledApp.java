package com.ex2tools.iv.apps;

import android.graphics.drawable.Drawable;

public final class InstalledApp {

    public final String packageName;
    public final String label;
    public final String versionName;
    public final long versionCode;
    public final boolean system;
    public final String signer;
    public final Drawable icon;

    public InstalledApp(String packageName, String label, String versionName, long versionCode,
                        boolean system, String signer, Drawable icon) {
        this.packageName = packageName;
        this.label = label;
        this.versionName = versionName;
        this.versionCode = versionCode;
        this.system = system;
        this.signer = signer;
        this.icon = icon;
    }
}
