package com.ex2tools.iv.ui;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.ex2tools.iv.MainActivity;
import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.R;
import com.ex2tools.iv.voice.VoiceService;

public final class VoiceFragment extends Fragment {

    private static final int REQ_MIC = 7100;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private FlymeSwitchRow enable;
    private FlymeSwitchRow wake;
    private FlymeSwitchRow micKey;
    private FlymeSwitchRow overlay;
    private TextView status;
    private TextView last;
    private boolean pendingStart;
    private boolean pendingTest;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateState();
            handler.postDelayed(this, 500L);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_voice, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Context c = requireContext();
        enable = view.findViewById(R.id.voiceEnable);
        wake = view.findViewById(R.id.voiceWake);
        micKey = view.findViewById(R.id.voiceMicKey);
        overlay = view.findViewById(R.id.voiceOverlay);
        status = view.findViewById(R.id.voiceStatus);
        last = view.findViewById(R.id.voiceLast);

        View info = view.findViewById(R.id.voiceCommandsInfo);
        FlymePress.bind(info);
        info.setOnClickListener(v -> VoiceCommandsDialog.show(c));

        enable.setChecked(Prefs.voiceEnabled(c));
        enable.setOnCheckedChangeListener((b, checked) -> {
            Prefs.setVoiceEnabled(c, checked);
            if (!checked) {
                VoiceService.stop(c);
                updateState();
                return;
            }
            if (!hasMic(c)) {
                pendingStart = true;
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            } else {
                VoiceService.start(c);
            }
            updateState();
        });

        wake.setChecked(Prefs.voiceWake(c));
        wake.setOnCheckedChangeListener((b, checked) -> {
            Prefs.setVoiceWake(c, checked);
            VoiceService.sync(c);
        });

        micKey.setChecked(Prefs.voiceMicKey(c));
        micKey.setOnCheckedChangeListener((b, checked) -> {
            Prefs.setVoiceMicKey(c, checked);
            VoiceService.sync(c);
        });

        overlay.setChecked(Prefs.voiceOverlay(c));
        overlay.setOnCheckedChangeListener((b, checked) -> Prefs.setVoiceOverlay(c, checked));

        view.findViewById(R.id.voiceTest).setOnClickListener(v -> {
            if (!Prefs.voiceEnabled(c)) {
                enable.setChecked(true);
                return;
            }
            if (!hasMic(c)) {
                pendingTest = true;
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
                return;
            }
            VoiceService.listen(c);
            updateState();
        });

        view.findViewById(R.id.voicePermissions).setOnClickListener(v -> {
            if (!hasMic(c)) {
                pendingStart = true;
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            } else if (!Settings.canDrawOverlays(c)) {
                Toast.makeText(c, R.string.voice_permission_overlay, Toast.LENGTH_LONG).show();
                if (requireActivity() instanceof MainActivity) {
                    ((MainActivity) requireActivity()).openOverlaySettings();
                }
            } else {
                Toast.makeText(c, R.string.voice_permissions_ok, Toast.LENGTH_SHORT).show();
            }
        });

        view.findViewById(R.id.voiceCustomCommands).setOnClickListener(v ->
                CustomCommandsDialog.show(c));
        updateState();
    }

    private void updateState() {
        if (!isAdded() || status == null) return;
        Context c = requireContext();
        boolean enabled = Prefs.voiceEnabled(c);
        if (!enabled) {
            status.setText(R.string.voice_status_disabled);
        } else {
            String state = VoiceService.state();
            switch (state) {
                case "unpacking":
                    status.setText(R.string.voice_model_unpack);
                    break;
                case "loading":
                    status.setText(R.string.voice_model_loading);
                    break;
                case "ready":
                    status.setText(Prefs.voiceWake(c)
                            ? R.string.voice_wake_listening : R.string.voice_model_ready);
                    break;
                case "wake":
                    status.setText(R.string.voice_wake_listening);
                    break;
                case "command":
                    status.setText(R.string.voice_command_listening);
                    break;
                case "mic_denied":
                    status.setText(R.string.voice_permission_mic);
                    break;
                case "error":
                    status.setText(R.string.voice_model_error);
                    break;
                default:
                    status.setText(VoiceService.isRunning()
                            ? R.string.voice_model_loading : R.string.voice_status_disabled);
                    break;
            }
        }
        String text = VoiceService.lastText();
        last.setText(text == null || text.isEmpty() ? "" : getString(R.string.voice_last_fmt, text));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_MIC || !isAdded()) return;
        boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (!granted) {
            pendingStart = false;
            pendingTest = false;
            Toast.makeText(requireContext(), R.string.voice_permission_mic, Toast.LENGTH_LONG).show();
            enable.setChecked(false);
            return;
        }
        if (pendingStart) {
            pendingStart = false;
            VoiceService.start(requireContext());
        }
        if (pendingTest) {
            pendingTest = false;
            VoiceService.listen(requireContext());
        }
        updateState();
    }

    private boolean hasMic(Context c) {
        return ContextCompat.checkSelfPermission(c, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onResume() {
        super.onResume();
        handler.post(tick);
    }

    @Override
    public void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
    }
}
