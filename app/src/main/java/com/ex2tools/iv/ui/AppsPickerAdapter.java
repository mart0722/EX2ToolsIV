package com.ex2tools.iv.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.ex2tools.iv.R;
import com.ex2tools.iv.apps.InstalledApp;
import com.google.android.material.checkbox.MaterialCheckBox;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Multi-select list used by the app-icon overlay picker. */
public final class AppsPickerAdapter extends RecyclerView.Adapter<AppsPickerAdapter.VH> {

    public interface Listener {
        void onSelectionChanged(int count);

        void onLimitReached();
    }

    static final int MAX_SELECTED = 9;

    private final List<InstalledApp> all = new ArrayList<>();
    private final List<InstalledApp> items = new ArrayList<>();
    private final LinkedHashSet<String> selected;
    private final Listener listener;
    private String query = "";

    public AppsPickerAdapter(LinkedHashSet<String> selected, Listener listener) {
        this.selected = selected;
        this.listener = listener;
    }

    public void setItems(List<InstalledApp> list) {
        all.clear();
        if (list != null) all.addAll(list);
        applyFilter();
    }

    public void filter(String text) {
        query = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        applyFilter();
    }

    public int totalCount() {
        return all.size();
    }

    private void applyFilter() {
        items.clear();
        for (InstalledApp app : all) {
            if (query.isEmpty()) {
                items.add(app);
                continue;
            }
            String label = app.label == null ? "" : app.label.toLowerCase(Locale.ROOT);
            String pkg = app.packageName == null ? "" : app.packageName.toLowerCase(Locale.ROOT);
            if (label.contains(query) || pkg.contains(query)) items.add(app);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_apps_picker, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        InstalledApp app = items.get(position);
        Context c = holder.itemView.getContext();
        holder.name.setText(app.label);
        holder.pkg.setText(app.packageName);
        if (app.icon != null) {
            holder.icon.setImageDrawable(app.icon);
        } else {
            holder.icon.setImageDrawable(ContextCompat.getDrawable(c, R.drawable.ic_apps));
        }
        holder.check.setChecked(selected.contains(app.packageName));
        holder.itemView.setOnClickListener(v -> toggle(holder.getAdapterPosition(), app));
    }

    private void toggle(int position, InstalledApp app) {
        if (position == RecyclerView.NO_POSITION) return;
        String pkg = app.packageName;
        if (selected.contains(pkg)) {
            selected.remove(pkg);
        } else {
            if (selected.size() >= MAX_SELECTED) {
                notifyItemChanged(position);
                if (listener != null) listener.onLimitReached();
                return;
            }
            selected.add(pkg);
        }
        notifyItemChanged(position);
        if (listener != null) listener.onSelectionChanged(selected.size());
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class VH extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView name;
        final TextView pkg;
        final MaterialCheckBox check;

        VH(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.pickerAppIcon);
            name = itemView.findViewById(R.id.pickerAppName);
            pkg = itemView.findViewById(R.id.pickerAppPackage);
            check = itemView.findViewById(R.id.pickerAppCheck);
        }
    }
}
