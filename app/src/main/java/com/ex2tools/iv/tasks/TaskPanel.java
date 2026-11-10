package com.ex2tools.iv.tasks;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ex2tools.iv.R;
import com.ex2tools.iv.ui.FlymePress;

import java.util.List;

public final class TaskPanel {

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Dialog dialog;
    private TaskListAdapter adapter;
    private TextView empty;
    private TextView closeSelectedButton;
    private boolean loading;

    public TaskPanel(Context context) {
        this.context = context;
    }

    public boolean isShowing() {
        return dialog != null && dialog.isShowing();
    }

    public void show() {
        dialog = new Dialog(context, R.style.Theme_EX2ToolsIV_Dialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_tasks);
        dialog.setCanceledOnTouchOutside(true);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.45f);
            window.setGravity(Gravity.CENTER);
            window.setLayout(dp(520), dp(460));
        }

        RecyclerView list = dialog.findViewById(R.id.taskList);
        empty = dialog.findViewById(R.id.taskEmpty);
        closeSelectedButton = dialog.findViewById(R.id.taskCloseSelected);
        FlymePress.bind(closeSelectedButton);
        list.setLayoutManager(new LinearLayoutManager(context));
        adapter = new TaskListAdapter(new TaskListAdapter.Listener() {
            @Override
            public void onToggleSelected(TaskEntry entry) {
                adapter.toggleSelected(entry);
                updateCloseButton();
            }

            @Override
            public void onToggleLock(TaskEntry entry) {
                boolean locked = !entry.locked;
                Tasks.setLocked(context, entry, locked);
                entry.locked = locked;
                entry.selected = !locked;
                adapter.notifyEntry(entry);
                updateCloseButton();
            }

            @Override
            public void onSelectionChanged() {
                updateCloseButton();
            }
        });
        list.setAdapter(adapter);

        ImageButton refresh = dialog.findViewById(R.id.taskRefresh);
        refresh.setOnClickListener(v -> load());
        ImageButton close = dialog.findViewById(R.id.taskDialogClose);
        close.setOnClickListener(v -> dismiss());
        closeSelectedButton.setOnClickListener(v -> closeSelected());
        dialog.setOnDismissListener(d -> dialog = null);
        dialog.show();
        updateCloseButton();
        load();
    }

    public void dismiss() {
        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }
    }

    private void load() {
        if (loading) return;
        loading = true;
        new Thread(() -> {
            List<TaskEntry> tasks = Tasks.load(context);
            handler.post(() -> {
                loading = false;
                if (dialog == null || adapter == null) return;
                adapter.setItems(tasks);
                updateEmpty();
                updateCloseButton();
            });
        }, "ex2-tasks-load").start();
    }

    private void closeSelected() {
        if (adapter == null) return;
        List<TaskEntry> snapshot = adapter.items();
        if (adapter.selectedCount() == 0) return;
        FlymePress.setEnabled(closeSelectedButton, false);
        new Thread(() -> {
            int count = 0;
            for (TaskEntry entry : snapshot) {
                if (entry.selected && Tasks.close(context, entry)) count++;
            }
            final int closed = count;
            handler.post(() -> {
                if (dialog == null) return;
                Toast.makeText(context,
                        context.getString(R.string.task_closed_fmt, closed),
                        Toast.LENGTH_SHORT).show();
                load();
            });
        }, "ex2-tasks-close").start();
    }

    private void updateCloseButton() {
        if (closeSelectedButton == null) return;
        int count = adapter == null ? 0 : adapter.selectedCount();
        closeSelectedButton.setText(count > 0
                ? context.getString(R.string.task_close_selected_fmt, count)
                : context.getString(R.string.task_close_selected));
        FlymePress.setEnabled(closeSelectedButton, count > 0);
    }

    private void updateEmpty() {
        if (empty == null || adapter == null) return;
        empty.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
