package com.ex2tools.iv.apps;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AppsAdapter extends RecyclerView.Adapter<AppsAdapter.VH> {

    public interface Listener {
        void onSelect(InstalledApp app);
    }

    public static final int FILTER_ALL = 0;
    public static final int FILTER_USER = 1;
    public static final int FILTER_SYSTEM = 2;

    private final List<InstalledApp> all = new ArrayList<>();
    private final List<InstalledApp> items = new ArrayList<>();
    private final Listener listener;
    private String query = "";
    private int filter = FILTER_ALL;
    private String selectedPackage;

    public AppsAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<InstalledApp> list) {
        all.clear();
        if (list != null) all.addAll(list);
        apply();
    }

    public void filter(String text, int mode) {
        query = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        filter = mode;
        apply();
    }

    public void select(String packageName) {
        selectedPackage = packageName;
        notifyDataSetChanged();
    }

    public InstalledApp find(String packageName) {
        if (packageName == null) return null;
        for (InstalledApp e : all) {
            if (packageName.equals(e.packageName)) return e;
        }
        return null;
    }

    public int size() {
        return all.size();
    }

    public int visibleCount() {
        return items.size();
    }

    private void apply() {
        items.clear();
        for (InstalledApp e : all) {
            if (filter == FILTER_USER && e.system) continue;
            if (filter == FILTER_SYSTEM && !e.system) continue;
            if (!query.isEmpty()) {
                String label = e.label == null ? "" : e.label.toLowerCase(Locale.ROOT);
                String pkg = e.packageName == null ? "" : e.packageName.toLowerCase(Locale.ROOT);
                if (!label.contains(query) && !pkg.contains(query)) continue;
            }
            items.add(e);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_installed_app, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        InstalledApp e = items.get(position);
        Context c = holder.itemView.getContext();
        holder.name.setText(e.label);
        holder.pkg.setText(e.packageName);
        holder.version.setText(e.versionName);
        if (e.icon != null) {
            holder.icon.setImageDrawable(e.icon);
        } else {
            holder.icon.setImageDrawable(ContextCompat.getDrawable(c, R.drawable.ic_apps));
        }
        holder.system.setVisibility(e.system ? View.VISIBLE : View.GONE);
        boolean selected = e.packageName.equals(selectedPackage);
        holder.itemView.setBackgroundResource(selected ? R.drawable.flyme_list_selected : 0);
        holder.itemView.setOnClickListener(v -> {
            selectedPackage = e.packageName;
            notifyDataSetChanged();
            if (listener != null) listener.onSelect(e);
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
        final TextView version;
        final TextView system;

        VH(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.appIcon);
            name = itemView.findViewById(R.id.appName);
            pkg = itemView.findViewById(R.id.appPackage);
            version = itemView.findViewById(R.id.appVersion);
            system = itemView.findViewById(R.id.appSystem);
        }
    }
}
