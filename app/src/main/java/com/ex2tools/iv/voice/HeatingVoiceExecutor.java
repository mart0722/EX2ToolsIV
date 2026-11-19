package com.ex2tools.iv.voice;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.climate.ClimateController;
import com.ex2tools.iv.vehicle.CommandDispatcher;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Multi-heater scenes may wait for read-back; never perform that work on the UI thread. */
final class HeatingVoiceExecutor {
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private HeatingVoiceExecutor() {}

    static void submit(Context context, CustomVoiceCommands.Command command) {
        Context app = context.getApplicationContext();
        executor.execute(() -> {
            String reply = apply(command);
            LogStore.add(app, "voice heating: " + reply);
            new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(app, reply, Toast.LENGTH_LONG).show());
        });
    }

    static String apply(CustomVoiceCommands.Command command) {
        ClimateController climate = ClimateController.get();
        StringBuilder failed = new StringBuilder();
        for (CustomVoiceCommands.HeatingAction action : command.heating) {
            boolean sent = false;
            try {
                CommandDispatcher.Result result;
                switch (action.target) {
                    case "wheel": result = climate.setWheelHeat(action.level != 0); break;
                    case "driver": result = climate.setSeatHeat(1, action.level); break;
                    case "passenger": result = climate.setSeatHeat(4, action.level); break;
                    case "windshield": result = climate.setWindshieldHeat(action.level != 0); break;
                    case "rear": result = climate.setRearDefrost(action.level != 0); break;
                    default: result = null;
                }
                sent = result != null && result.ok();
            } catch (RuntimeException ignored) { }
            if (!sent) {
                if (failed.length() > 0) failed.append("; ");
                failed.append(action.description());
            }
        }
        return failed.length() == 0 ? "Команды отправлены: " + command.description()
                : "Не удалось выполнить: " + failed;
    }
}
