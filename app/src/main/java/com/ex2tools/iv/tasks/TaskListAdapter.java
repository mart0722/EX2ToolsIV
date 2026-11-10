package com.ex2tools.iv.tasks;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.ex2tools.iv.R;
import com.google.android.material.checkbox.MaterialCheckBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TaskListAdapter extends RecyclerView.Adapter<TaskListAdapter.VH> {

    public interface Listener {
        void onToggleSelected(TaskEntry entry);

        void onToggleLock(TaskEntry entry);

        void onSelectionChanged();
    }

    private final List<TaskEntry> items = new ArrayList<>();
    private final Listener listener;

    public TaskListAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<TaskEntry> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    public List<TaskEntry> items() {
        return new ArrayList<>(items);
    }

    public int selectedCount() {
        int count = 0;
        for (TaskEntry entry : items) {
            if (entry.selected) count++;
        }
        return count;
    }

    public void toggleSelected(TaskEntry entry) {
        int index = items.indexOf(entry);
        if (index < 0) return;
        entry.selected = !entry.selected;
        notifyItemChanged(index);
    }

    public void notifyEntry(TaskEntry entry) {
        int index = items.indexOf(entry);
        if (index >= 0) notifyItemChanged(index);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_task, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        TaskEntry entry = items.get(position);
        Context c = holder.itemView.getContext();
        holder.name.setText(entry.label);
        holder.pkg.setText(metadata(entry));
        if (entry.icon != null) holder.icon.setImageDrawable(entry.icon);
        else holder.icon.setImageResource(R.drawable.ic_service);

        holder.check.setOnCheckedChangeListener(null);
        holder.check.setChecked(entry.selected);

        holder.lock.setImageResource(R.drawable.ic_lock);
        holder.lock.setColorFilter(ContextCompat.getColor(c,
                entry.locked ? R.color.cyan : R.color.text_faint));
        holder.lock.setContentDescription(c.getString(
                entry.locked ? R.string.task_unlock : R.string.task_lock));

        holder.itemView.setOnClickListener(v -> listener.onToggleSelected(entry));
        holder.lock.setOnClickListener(v -> listener.onToggleLock(entry));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private static String metadata(TaskEntry entry) {
        if (entry.memKb <= 0) return entry.packageName;
        long mb = Math.max(1L, Math.round(entry.memKb / 1024.0));
        return entry.packageName + "  ·  " + String.format(Locale.US, "%d МБ", mb);
    }

    static final class VH extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView name;
        final TextView pkg;
        final ImageButton lock;
        final MaterialCheckBox check;

        VH(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.taskIcon);
            name = itemView.findViewById(R.id.taskName);
            pkg = itemView.findViewById(R.id.taskPackage);
            lock = itemView.findViewById(R.id.taskLock);
            check = itemView.findViewById(R.id.taskChecked);
        }
    }
}
