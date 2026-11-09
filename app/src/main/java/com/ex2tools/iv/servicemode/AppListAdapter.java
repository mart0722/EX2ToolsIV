package com.ex2tools.iv.servicemode;

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
import com.google.android.material.checkbox.MaterialCheckBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.VH> {

    public interface Listener {
        void onSelectionChanged(String pkg, boolean hide);
    }

    private final List<AppEntry> allItems = new ArrayList<>();
    private final List<AppEntry> items = new ArrayList<>();
    private final Listener listener;
    private String query = "";

    public AppListAdapter(List<AppEntry> initial, Listener listener) {
        this.listener = listener;
        if (initial != null) allItems.addAll(initial);
        applyFilter();
    }

    public void setItems(List<AppEntry> list) {
        allItems.clear();
        if (list != null) allItems.addAll(list);
        applyFilter();
    }

    public void filter(String text) {
        query = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        applyFilter();
    }

    private void applyFilter() {
        items.clear();
        for (AppEntry e : allItems) {
            if (query.isEmpty()
                    || e.label.toLowerCase(Locale.ROOT).contains(query)
                    || e.packageName.toLowerCase(Locale.ROOT).contains(query)) {
                items.add(e);
            }
        }
        notifyDataSetChanged();
    }

    public boolean allVisibleSelected() {
        if (items.isEmpty()) return false;
        for (AppEntry e : items) if (!e.selected) return false;
        return true;
    }

    public void setAllVisibleSelected(boolean selected) {
        for (AppEntry e : items) {
            if (e.selected == selected) continue;
            e.selected = selected;
            if (listener != null) listener.onSelectionChanged(e.packageName, selected);
        }
        notifyDataSetChanged();
    }

    public int visibleCount() {
        return items.size();
    }

    public int selectedCount() {
        int n = 0;
        for (AppEntry e : items) if (e.selected) n++;
        return n;
    }

    public List<String> selectedPackages() {
        List<String> out = new ArrayList<>();
        for (AppEntry e : items) if (e.selected) out.add(e.packageName);
        return out;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        AppEntry e = items.get(position);
        holder.name.setText(e.label);
        holder.pkg.setText(e.packageName);
        Context c = holder.itemView.getContext();
        if (e.icon != null) {
            holder.icon.setImageDrawable(e.icon);
        } else {
            holder.icon.setImageDrawable(ContextCompat.getDrawable(c, R.drawable.ic_service));
        }
        holder.check.setOnCheckedChangeListener(null);
        holder.check.setChecked(e.selected);
        holder.check.setOnCheckedChangeListener((buttonView, isChecked) -> {
            e.selected = isChecked;
            if (listener != null) listener.onSelectionChanged(e.packageName, isChecked);
        });
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
            icon = itemView.findViewById(R.id.appIcon);
            name = itemView.findViewById(R.id.appName);
            pkg = itemView.findViewById(R.id.appPackage);
            check = itemView.findViewById(R.id.appCheck);
        }
    }
}
