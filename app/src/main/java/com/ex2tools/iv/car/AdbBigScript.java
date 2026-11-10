package com.ex2tools.iv.car;

import android.util.Base64;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Splits long shell scripts into chunks that fit through local adbd. */
final class AdbBigScript {

    interface Runner {
        String run(String command, int timeoutMs) throws IOException;
    }

    private static final int THRESHOLD = 3500;
    private static final int CHUNK_SIZE = 3000;

    private AdbBigScript() {
    }

    static String run(String script, int timeoutMs, Runner runner) throws IOException {
        byte[] bytes = script.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < THRESHOLD) {
            return runner.run(script, timeoutMs);
        }
        String encoded = Base64.encodeToString(bytes, Base64.NO_WRAP);
        long now = System.currentTimeMillis();
        String b64 = "'/data/local/tmp/_big_script_" + now + ".b64'";
        String sh = "'/data/local/tmp/_big_script_" + now + ".sh'";
        runner.run("rm -f " + b64 + " " + sh + " 2>/dev/null; true", 10000);
        for (int i = 0; i < encoded.length(); i += CHUNK_SIZE) {
            String chunk = encoded.substring(i,
                    Math.min(i + CHUNK_SIZE, encoded.length()));
            runner.run("echo '" + chunk + "' >> " + b64, 15000);
        }
        String result = runner.run("base64 -d " + b64 + " > " + sh
                + " 2>&1 && sh " + sh, timeoutMs);
        runner.run("rm -f " + b64 + " " + sh + " 2>/dev/null; true", 10000);
        return result;
    }
}
