package com.ex2tools.iv.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.R;
import com.ex2tools.iv.voice.SteeringKeysService;

public final class SteeringFragment extends Fragment {
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle state) {
        android.content.Context c = requireContext();
        int pad = Math.round(24 * c.getResources().getDisplayMetrics().density);
        ScrollView scroll = new ScrollView(c);
        LinearLayout content = new LinearLayout(c);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, pad, pad, pad);
        scroll.addView(content);
        TextView title = new TextView(c);
        title.setText(R.string.steering_title);
        title.setTextSize(30);
        title.setTextColor(c.getColor(R.color.flyme_label_level1));
        content.addView(title);
        FlymeSwitchRow toggle = new FlymeSwitchRow(c);
        toggle.setText(c.getString(R.string.steering_climate));
        toggle.setChecked(Prefs.steeringClimate(c));
        content.addView(toggle, new LinearLayout.LayoutParams(-1, -2));
        toggle.setOnCheckedChangeListener((button, enabled) -> {
            Prefs.setSteeringClimate(c, enabled);
            SteeringKeysService.sync(c);
        });
        TextView help = new TextView(c);
        help.setText(c.getString(R.string.steering_climate_hint) + "\n\n" + c.getString(R.string.steering_help));
        help.setTextSize(20);
        help.setTextColor(c.getColor(R.color.flyme_label_level2));
        help.setPadding(0, pad, 0, 0);
        content.addView(help);
        return scroll;
    }
}
