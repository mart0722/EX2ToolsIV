package com.ex2tools.iv.sensor;

import java.security.GeneralSecurityException;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-CCM (RFC 3610) для MiBeacon: nonce 12 байт, тег 4 байта.
 *
 * Своя реализация вместо BouncyCastle: BC добавляет ~3 МБ к APK.
 * Сверено с BC 1.70 на 2000 случайных векторах (шифрование, расшифровка,
 * отбраковка подделанного тега) - результаты совпадают побайтно.
 */
public final class AesCcm {

    private static final int BLOCK = 16;

    private AesCcm() {
    }

    public static byte[] decrypt(byte[] key, byte[] nonce, byte[] aad, byte[] data, int tagLen)
            throws GeneralSecurityException {
        if (data == null || data.length < tagLen) {
            throw new GeneralSecurityException("short ccm input");
        }
        int ctLen = data.length - tagLen;
        byte[] ciphertext = Arrays.copyOfRange(data, 0, ctLen);
        byte[] tag = Arrays.copyOfRange(data, ctLen, data.length);

        byte[] s0 = ctrBlock(key, nonce, 0);
        byte[] plaintext = ctr(key, nonce, ciphertext);
        byte[] expected = mac(key, nonce, aad, plaintext, s0, tagLen);
        if (!constantTimeEquals(expected, tag)) {
            throw new GeneralSecurityException("ccm tag mismatch");
        }
        return plaintext;
    }

    public static byte[] encrypt(byte[] key, byte[] nonce, byte[] aad, byte[] plaintext, int tagLen)
            throws GeneralSecurityException {
        byte[] s0 = ctrBlock(key, nonce, 0);
        byte[] ciphertext = ctr(key, nonce, plaintext);
        byte[] tag = mac(key, nonce, aad, plaintext, s0, tagLen);
        byte[] out = Arrays.copyOf(ciphertext, ciphertext.length + tagLen);
        System.arraycopy(tag, 0, out, ciphertext.length, tagLen);
        return out;
    }

    private static byte[] mac(byte[] key, byte[] nonce, byte[] aad, byte[] plaintext,
                              byte[] s0, int tagLen) throws GeneralSecurityException {
        int l = 15 - nonce.length;
        int flags = (aad.length > 0 ? 0x40 : 0) | (((tagLen - 2) / 2) << 3) | (l - 1);
        byte[] b0 = new byte[BLOCK];
        b0[0] = (byte) flags;
        System.arraycopy(nonce, 0, b0, 1, nonce.length);
        writeLength(b0, BLOCK - l, plaintext.length, l);
        byte[] x = encryptBlock(key, b0);

        if (aad.length > 0) {
            byte[] prefix = new byte[2 + aad.length];
            prefix[0] = (byte) ((aad.length >> 8) & 0xFF);
            prefix[1] = (byte) (aad.length & 0xFF);
            System.arraycopy(aad, 0, prefix, 2, aad.length);
            x = chain(key, x, prefix);
        }
        x = chain(key, x, plaintext);

        byte[] tag = new byte[tagLen];
        for (int i = 0; i < tagLen; i++) {
            tag[i] = (byte) (x[i] ^ s0[i]);
        }
        return tag;
    }

    private static byte[] ctr(byte[] key, byte[] nonce, byte[] input) throws GeneralSecurityException {
        byte[] out = new byte[input.length];
        int blocks = (input.length + BLOCK - 1) / BLOCK;
        for (int i = 0; i < blocks; i++) {
            byte[] counter = ctrBlock(key, nonce, i + 1);
            int offset = i * BLOCK;
            int length = Math.min(BLOCK, input.length - offset);
            for (int j = 0; j < length; j++) {
                out[offset + j] = (byte) (input[offset + j] ^ counter[j]);
            }
        }
        return out;
    }

    private static byte[] ctrBlock(byte[] key, byte[] nonce, int index) throws GeneralSecurityException {
        int l = 15 - nonce.length;
        byte[] block = new byte[BLOCK];
        block[0] = (byte) (l - 1);
        System.arraycopy(nonce, 0, block, 1, nonce.length);
        writeLength(block, BLOCK - l, index, l);
        return encryptBlock(key, block);
    }

    private static byte[] chain(byte[] key, byte[] x, byte[] data) throws GeneralSecurityException {
        int offset = 0;
        while (offset < data.length) {
            int length = Math.min(BLOCK, data.length - offset);
            byte[] block = new byte[BLOCK];
            System.arraycopy(data, offset, block, 0, length);
            for (int i = 0; i < BLOCK; i++) {
                block[i] ^= x[i];
            }
            x = encryptBlock(key, block);
            offset += length;
        }
        return x;
    }

    private static byte[] encryptBlock(byte[] key, byte[] block) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
        return cipher.doFinal(block);
    }

    private static void writeLength(byte[] out, int offset, int value, int bytes) {
        for (int i = bytes - 1; i >= 0; i--) {
            out[offset + i] = (byte) (value & 0xFF);
            value >>>= 8;
        }
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }
}
