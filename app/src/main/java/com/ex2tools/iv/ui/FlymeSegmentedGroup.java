package com.ex2tools.iv.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;

import com.google.android.material.button.MaterialButton;

public final class FlymeSegmentedGroup extends LinearLayout {

    public interface OnButtonCheckedListener {
        void onButtonChecked(FlymeSegmentedGroup group, int checkedId, boolean isChecked);
    }

    private OnButtonCheckedListener listener;
    private int checkedId = View.NO_ID;

    public FlymeSegmentedGroup(Context context) {
        this(context, null);
    }

    public FlymeSegmentedGroup(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        int gap = dp(8);
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            if (i > 0) lp.setMarginStart(gap);
            child.setLayoutParams(lp);
            if (child instanceof MaterialButton) {
                MaterialButton button = (MaterialButton) child;
                button.setCheckable(true);
                button.setCornerRadius(dp(6));
                button.setInsetTop(0);
                button.setInsetBottom(0);
                button.setMinWidth(0);
                button.setOnClickListener(v -> check(v.getId()));
            }
        }
    }

    public void check(int id) {
        if (checkedId == id) {
            updateChecks();
            return;
        }
        int previous = checkedId;
        checkedId = id;
        updateChecks();
        if (listener != null) {
            if (previous != View.NO_ID) listener.onButtonChecked(this, previous, false);
            if (id != View.NO_ID) listener.onButtonChecked(this, id, true);
        }
    }

    public int getCheckedButtonId() {
        return checkedId;
    }

    public void addOnButtonCheckedListener(OnButtonCheckedListener listener) {
        this.listener = listener;
    }

    private void updateChecks() {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof MaterialButton) {
                ((MaterialButton) child).setChecked(child.getId() == checkedId);
            }
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
