package com.ex2tools.iv.tasks;

import android.graphics.drawable.Drawable;

public final class TaskEntry {

    public final int taskId;
    public final int pid;
    public final long memKb;
    public final String packageName;
    public final String label;
    public final Drawable icon;
    public boolean locked;
    public boolean selected;

    public TaskEntry(int taskId, String packageName, String label, Drawable icon, boolean locked) {
        this(taskId, packageName, label, icon, locked, -1, 0L);
    }

    public TaskEntry(int taskId, String packageName, String label, Drawable icon,
                     boolean locked, int pid, long memKb) {
        this.taskId = taskId;
        this.pid = pid;
        this.memKb = memKb;
        this.packageName = packageName;
        this.label = label;
        this.icon = icon;
        this.locked = locked;
        this.selected = !locked;
    }
}
