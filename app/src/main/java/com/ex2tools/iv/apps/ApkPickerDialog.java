package com.ex2tools.iv.apps;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Environment;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.ex2tools.iv.R;
import com.ex2tools.iv.ui.FlymePress;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Small built-in APK picker for head units without a system file manager.
 * It browses readable storage roots with java.io.File and only shows
 * directories and .apk files.
 */
public final class ApkPickerDialog {

    public interface Listener {
        void onPick(File apk);
    }

    private static final String APK_EXT = ".apk";
    private static final String WAV_EXT = ".wav";

    private final Context context;
    private final Listener listener;
    private final Dialog dialog;
    private final TextView pathView;
    private final TextView emptyView;
    private final TextView internalView;
    private final TextView usbView;
    private final TextView upView;
    private final File storageRoot;
    private final File internalDir;
    private final File usbDir;
    private final FileAdapter adapter;
    private final String extension;
    private final int fileIconRes;
    private final int fileMetaRes;
    private File current;

    private ApkPickerDialog(Context context, Listener listener, String extension,
                            int titleRes, int emptyRes, int fileIconRes, int fileMetaRes) {
        this.context = context;
        this.listener = listener;
        this.extension = extension;
        this.fileIconRes = fileIconRes;
        this.fileMetaRes = fileMetaRes;
        this.storageRoot = new File("/storage");
        this.internalDir = Environment.getExternalStorageDirectory();
        this.usbDir = findUsbDir(context);
        this.current = readableDir(storageRoot) ? storageRoot : internalDir;

        dialog = new Dialog(context, R.style.Theme_EX2ToolsIV_Dialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_apk_picker);
        dialog.setCanceledOnTouchOutside(false);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.55f);
            window.setGravity(Gravity.CENTER);
            int width = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.62f);
            int height = (int) (context.getResources().getDisplayMetrics().heightPixels * 0.82f);
            window.setLayout(width, height);
        }

        TextView titleView = dialog.findViewById(R.id.pickerTitle);
        titleView.setText(titleRes);
        pathView = dialog.findViewById(R.id.pickerPath);
        emptyView = dialog.findViewById(R.id.pickerEmpty);
        emptyView.setText(emptyRes);
        internalView = dialog.findViewById(R.id.pickerInternal);
        usbView = dialog.findViewById(R.id.pickerUsb);
        upView = dialog.findViewById(R.id.pickerUp);
        TextView cancelView = dialog.findViewById(R.id.pickerCancel);
        ListView listView = dialog.findViewById(R.id.pickerList);

        adapter = new FileAdapter();
        listView.setAdapter(adapter);
        listView.setEmptyView(emptyView);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            File item = adapter.getItem(position);
            if (item.isDirectory()) {
                open(item);
            } else {
                pick(item);
            }
        });

        FlymePress.bind(internalView);
        FlymePress.bind(usbView);
        FlymePress.bind(upView);
        FlymePress.bind(cancelView);

        internalView.setOnClickListener(v -> open(internalDir));
        upView.setOnClickListener(v -> {
            File parent = current.getParentFile();
            if (parent != null) open(parent);
        });
        cancelView.setOnClickListener(v -> dialog.dismiss());

        if (usbDir == null) {
            usbView.setVisibility(View.GONE);
        } else {
            usbView.setOnClickListener(v -> open(usbDir));
        }

        open(current);
    }

    public static void show(Context context, Listener listener) {
        new ApkPickerDialog(context, listener, APK_EXT,
                R.string.apps_picker_title,
                R.string.apps_picker_empty,
                R.drawable.ic_apps,
                R.string.apps_picker_apk_meta).show();
    }

    public static void showWav(Context context, Listener listener) {
        new ApkPickerDialog(context, listener, WAV_EXT,
                R.string.sounds_picker_title,
                R.string.sounds_picker_empty,
                R.drawable.ic_widget_volume,
                R.string.sounds_picker_wav_meta).show();
    }

    private void show() {
        dialog.show();
    }

    private void open(File dir) {
        if (dir == null || !dir.isDirectory()) return;
        if (!dir.canRead()) {
            Toast.makeText(context, R.string.apps_picker_no_access, Toast.LENGTH_SHORT).show();
            return;
        }

        File[] files;
        try {
            files = dir.listFiles();
        } catch (Throwable t) {
            Toast.makeText(context, R.string.apps_picker_no_access, Toast.LENGTH_SHORT).show();
            return;
        }

        current = dir;
        pathView.setText(dir.getAbsolutePath());

        List<File> items = new ArrayList<>();
        if (files != null) {
            for (File file : files) {
                if (file == null) continue;
                if (file.isDirectory()) {
                    if (!"self".equals(file.getName())) items.add(file);
                } else if (file.isFile() && isAccepted(file)) {
                    items.add(file);
                }
            }
        }
        items.sort((a, b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });
        adapter.setItems(items);

        boolean canUp = current.getParentFile() != null && !current.equals(storageRoot);
        FlymePress.setEnabled(upView, canUp);
    }

    private void pick(File file) {
        dialog.dismiss();
        if (listener != null) listener.onPick(file);
    }

    private boolean isAccepted(File file) {
        return file.getName().toLowerCase(Locale.ROOT).endsWith(extension);
    }

    private static boolean readableDir(File dir) {
        return dir != null && dir.isDirectory() && dir.canRead();
    }

    private static File findUsbDir(Context context) {
        try {
            File[] dirs = context.getExternalFilesDirs(null);
            if (dirs == null) return null;
            for (File dir : dirs) {
                File root = volumeRoot(dir);
                if (root != null && root.isDirectory()
                        && Environment.isExternalStorageRemovable(root)) {
                    return root;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static File volumeRoot(File appDir) {
        if (appDir == null) return null;
        File p = appDir;
        while (p != null && !"Android".equals(p.getName())) {
            p = p.getParentFile();
        }
        return p == null ? null : p.getParentFile();
    }

    private final class FileAdapter extends BaseAdapter {

        private final List<File> items = new ArrayList<>();

        void setItems(List<File> files) {
            items.clear();
            if (files != null) items.addAll(files);
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public File getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = LayoutInflater.from(context).inflate(R.layout.item_apk_picker, parent, false);
            }

            File file = getItem(position);
            boolean dir = file.isDirectory();

            ImageView icon = view.findViewById(R.id.pickerIcon);
            TextView name = view.findViewById(R.id.pickerName);
            TextView meta = view.findViewById(R.id.pickerMeta);
            ImageView arrow = view.findViewById(R.id.pickerArrow);

            icon.setImageResource(dir ? R.drawable.ic_folder : fileIconRes);
            name.setText(file.getName());
            meta.setText(dir
                    ? context.getString(R.string.apps_picker_folder)
                    : context.getString(fileMetaRes,
                    AppsRepository.formatSize(file.length())));
            arrow.setVisibility(dir ? View.VISIBLE : View.INVISIBLE);
            return view;
        }
    }
}
