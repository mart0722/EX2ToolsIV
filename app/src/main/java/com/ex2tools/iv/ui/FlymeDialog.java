package com.ex2tools.iv.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import com.ex2tools.iv.R;

public final class FlymeDialog {

    private FlymeDialog() {
    }

    public static Dialog showConfirm(Context context, String title, CharSequence message,
                                     String okText, String cancelText, Runnable onConfirm) {
        return show(context, title, message, okText, cancelText, onConfirm, null, false);
    }

    public static Dialog showConfirmLeft(Context context, String title, CharSequence message,
                                         String okText, String cancelText, Runnable onConfirm) {
        return show(context, title, message, okText, cancelText, onConfirm, null, true);
    }

    public static Dialog showMessage(Context context, String title, CharSequence message,
                                     String okText, Runnable onDismiss) {
        return show(context, title, message, okText, null, null, onDismiss, false);
    }

    private static Dialog show(Context context, String title, CharSequence message,
                               String okText, String cancelText, Runnable onConfirm,
                               Runnable onDismiss, boolean alignLeft) {
        Dialog dialog = new Dialog(context, R.style.Theme_EX2ToolsIV_Dialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_flyme_confirm);
        dialog.setCanceledOnTouchOutside(false);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.55f);
            window.setGravity(Gravity.CENTER);
            int width = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.5f);
            window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
        }

        TextView titleView = dialog.findViewById(R.id.confirmTitle);
        TextView messageView = dialog.findViewById(R.id.confirmMessage);
        TextView okView = dialog.findViewById(R.id.confirmOkText);
        TextView cancelView = dialog.findViewById(R.id.confirmCancelText);
        View cancel = dialog.findViewById(R.id.confirmCancel);
        View space = dialog.findViewById(R.id.confirmSpace);
        View ok = dialog.findViewById(R.id.confirmOk);

        titleView.setText(title);
        messageView.setText(message);
        messageView.setGravity(alignLeft ? Gravity.START : Gravity.CENTER);
        okView.setText(okText == null ? context.getString(R.string.confirm_yes) : okText);
        if (cancelText == null) {
            cancel.setVisibility(View.GONE);
            space.setVisibility(View.GONE);
        } else {
            cancelView.setText(cancelText);
        }
        FlymePress.bind(ok);
        FlymePress.bind(cancel);
        cancel.setOnClickListener(v -> dialog.dismiss());
        ok.setOnClickListener(v -> {
            dialog.dismiss();
            if (onConfirm != null) onConfirm.run();
        });
        dialog.setOnDismissListener(d -> {
            if (onDismiss != null) onDismiss.run();
        });
        dialog.show();
        return dialog;
    }
}
