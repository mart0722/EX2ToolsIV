package com.ex2tools.iv.sensor;

import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * Разбор рекламных пакетов MiBeacon v4/v5 (Xiaomi LYWSD03MMC и совместимые).
 *
 * Пакет приходит в service data сервиса 0xFE95. Для расшифровки нужен
 * bind key датчика (16 байт) и его MAC.
 */
public final class MiBeacon {

    public static final class Reading {
        public float tempC = Float.NaN;
        public int humidity = -1;
        public int battery = -1;
        public int counter;
        public long at;
        public int rssi;
        public String address = "";
        public boolean encrypted;
        public String error;
        public byte[] raw;

        public boolean hasData() {
            return !Float.isNaN(tempC) || humidity >= 0 || battery >= 0;
        }
    }

    private MiBeacon() {
    }

    /**
     * @param data service data без UUID (то, что лежит в ScanRecord.getServiceData)
     * @param mac  MAC датчика, 6 байт
     * @param key  bind key, 16 байт; может быть null для незашифрованных пакетов
     */
    public static Reading parse(byte[] data, byte[] mac, byte[] key) {
        if (data == null || data.length < 12) return null;

        int frctrl = (data[0] & 0xff) | ((data[1] & 0xff) << 8);
        int version = (frctrl >> 12) & 0x0f;
        boolean macInclude = ((frctrl >> 4) & 1) != 0;
        boolean capabilityInclude = ((frctrl >> 5) & 1) != 0;
        boolean objectInclude = ((frctrl >> 6) & 1) != 0;
        boolean encrypted = ((frctrl >> 3) & 1) != 0;

        Reading reading = new Reading();
        reading.raw = data.clone();
        reading.counter = data[4] & 0xff;
        reading.at = System.currentTimeMillis();
        reading.encrypted = encrypted;

        if (!objectInclude) {
            reading.error = "no object";
            return reading;
        }
        if (version <= 3) {
            reading.error = "unsupported version v" + version;
            return reading;
        }

        int index = 5;
        if (macInclude) index += 6;
        if (capabilityInclude) index += 1;

        if (!encrypted) {
            if (data.length <= index) {
                reading.error = "short frame";
                return reading;
            }
            parseObjects(Arrays.copyOfRange(data, index, data.length), reading);
            return reading;
        }

        if (key == null || key.length != 16 || mac == null || mac.length != 6) {
            reading.error = "bind key and mac required";
            return reading;
        }
        if (data.length < index + 9) {
            reading.error = "short encrypted frame";
            return reading;
        }

        byte[] nonce = new byte[12];
        for (int i = 0; i < 6; i++) {
            nonce[i] = mac[5 - i];
        }
        System.arraycopy(data, 2, nonce, 6, 3);
        System.arraycopy(data, data.length - 7, nonce, 9, 3);

        byte[] encoded = Arrays.copyOfRange(data, index, data.length - 7);
        byte[] mic = Arrays.copyOfRange(data, data.length - 4, data.length);
        byte[] cipher = new byte[encoded.length + mic.length];
        System.arraycopy(encoded, 0, cipher, 0, encoded.length);
        System.arraycopy(mic, 0, cipher, encoded.length, mic.length);

        try {
            byte[] plain = AesCcm.decrypt(key, nonce, new byte[]{0x11}, cipher, 4);
            parseObjects(plain, reading);
        } catch (GeneralSecurityException e) {
            reading.error = "decrypt failed";
        } catch (Throwable t) {
            reading.error = "decrypt error: " + t.getClass().getSimpleName();
        }
        return reading;
    }

    private static void parseObjects(byte[] payload, Reading reading) {
        if (payload == null) return;
        int pos = 0;
        while (pos + 3 <= payload.length) {
            int objectId = (payload[pos] & 0xff) | ((payload[pos + 1] & 0xff) << 8);
            int length = payload[pos + 2] & 0xff;
            int start = pos + 3;
            int end = start + length;
            if (end > payload.length) break;
            byte[] value = Arrays.copyOfRange(payload, start, end);
            pos = end;

            switch (objectId) {
                case 0x1004:
                    if (length == 2) {
                        short raw = (short) ((value[0] & 0xff) | ((value[1] & 0xff) << 8));
                        reading.tempC = raw / 10f;
                    }
                    break;
                case 0x1006:
                    if (length == 2) {
                        int raw = (value[0] & 0xff) | ((value[1] & 0xff) << 8);
                        reading.humidity = raw / 10;
                    } else if (length == 1) {
                        reading.humidity = value[0] & 0xff;
                    }
                    break;
                case 0x100A:
                    if (length >= 1) reading.battery = value[0] & 0xff;
                    break;
                case 0x100D:
                    if (length == 4) {
                        short temp = (short) ((value[0] & 0xff) | ((value[1] & 0xff) << 8));
                        int hum = (value[2] & 0xff) | ((value[3] & 0xff) << 8);
                        reading.tempC = temp / 10f;
                        reading.humidity = hum / 10;
                    }
                    break;
                default:
                    break;
            }
        }
    }

    public static byte[] hex(String value) {
        if (value == null) return new byte[0];
        String clean = value.replaceAll("[^0-9A-Fa-f]", "");
        if ((clean.length() & 1) != 0) clean = "0" + clean;
        byte[] out = new byte[clean.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    public static boolean isValidMac(String value) {
        return value != null
                && value.matches("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}");
    }

    public static boolean isValidKey(String value) {
        if (value == null) return false;
        String clean = value.replaceAll("[^0-9A-Fa-f]", "");
        return clean.length() == 32;
    }
}
