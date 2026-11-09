package com.ex2tools.iv.tasks;

import android.graphics.drawable.Drawable;

public final class TaskEntry {

    public final int taskId;
    public final String packageName;
    public final String label;
    public final Drawable icon;
    public boolean locked;

    public TaskEntry(int taskId, String packageName, String label, Drawable icon, boolean locked) {
        this.taskId = taskId;
        this.packageName = packageName;
        this.label = label;
        this.icon = icon;
        this.locked = locked;
    }
}
