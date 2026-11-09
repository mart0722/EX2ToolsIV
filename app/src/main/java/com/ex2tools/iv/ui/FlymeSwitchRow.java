package com.ex2tools.iv.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.ex2tools.iv.R;

public final class FlymeSwitchRow extends LinearLayout {

    private TextView title;
    private TextView description;
    private FlymeSwitch toggle;

    public FlymeSwitchRow(Context context) {
        this(context, null);
    }

    public FlymeSwitchRow(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setMinimumHeight(Math.round(104f * getResources().getDisplayMetrics().density));
        setPadding(0, dp(8), 0, dp(8));

        CharSequence text = null;
        CharSequence descText = null;
        int textColor = ContextCompat.getColor(context, R.color.flyme_label_level1);
        float textSize = 22f * getResources().getDisplayMetrics().scaledDensity;
        if (attrs != null) {
            TypedArray ta = context.obtainStyledAttributes(attrs,
                    new int[]{android.R.attr.text, android.R.attr.textColor, android.R.attr.textSize});
            text = ta.getText(0);
            textColor = ta.getColor(1, textColor);
            textSize = ta.getDimension(2, textSize);
            ta.recycle();

            TypedArray row = context.obtainStyledAttributes(attrs, R.styleable.FlymeSwitchRow);
            descText = row.getText(R.styleable.FlymeSwitchRow_rowDescription);
            row.recycle();
        }

        toggle = new FlymeSwitch(context);
        toggle.setClickable(false);
        toggle.setFocusable(false);
        LayoutParams toggleParams = new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        toggleParams.setMarginEnd(dp(20));
        addView(toggle, toggleParams);

        LinearLayout box = new LinearLayout(context);
        box.setOrientation(VERTICAL);
        box.setGravity(Gravity.CENTER_VERTICAL);

        title = new TextView(context);
        title.setText(text);
        title.setTextColor(textColor);
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
                android.graphics.Typeface.NORMAL));
        title.setMaxLines(2);
        if (text == null || text.length() == 0) title.setVisibility(GONE);
        box.addView(title, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));

        description = new TextView(context);
        description.setText(descText);
        description.setTextColor(ContextCompat.getColor(context, R.color.flyme_label_level2));
        description.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                19f * getResources().getDisplayMetrics().scaledDensity);
        description.setMaxLines(3);
        if (descText == null || descText.length() == 0) description.setVisibility(GONE);
        LayoutParams descParams = new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT);
        descParams.topMargin = dp(4);
        box.addView(description, descParams);

        addView(box, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        setClickable(true);
        setFocusable(true);
        setOnClickListener(v -> toggle.toggle());
    }

    public void setText(CharSequence text) {
        title.setText(text);
        title.setVisibility(text == null || text.length() == 0 ? GONE : VISIBLE);
    }

    public void setDescription(CharSequence text) {
        description.setText(text);
        description.setVisibility(text == null || text.length() == 0 ? GONE : VISIBLE);
    }

    public void setChecked(boolean checked) {
        toggle.setChecked(checked);
    }

    public boolean isChecked() {
        return toggle.isChecked();
    }

    public void setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener listener) {
        toggle.setOnCheckedChangeListener(listener);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        title.setAlpha(enabled ? 1f : 0.4f);
        description.setAlpha(enabled ? 1f : 0.4f);
        toggle.setEnabled(enabled);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
