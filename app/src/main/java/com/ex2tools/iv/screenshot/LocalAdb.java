package com.ex2tools.iv.screenshot;

import android.content.Context;
import android.util.Base64;

import com.ex2tools.iv.LogStore;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Minimal ADB client for the head unit's local adbd.
 * Uses: screencap -p | base64.
 */
public final class LocalAdb {

    private static final int A_CNXN = 0x4e584e43;
    private static final int A_AUTH = 0x48545541;
    private static final int A_OPEN = 0x4e45504f;
    private static final int A_OKAY = 0x59414b4f;
    private static final int A_CLSE = 0x45534c43;
    private static final int A_WRTE = 0x45545257;

    private static final int[] PORTS = {5555, 65010};

    private LocalAdb() {
    }

    public static byte[] capture(Context context) throws Exception {
        Throwable last = null;
        for (int port : PORTS) {
            try {
                byte[] png = captureOnPort(port);
                LogStore.add(context, "screencap ok via adb:" + port + " (" + png.length + " bytes)");
                return png;
            } catch (Throwable t) {
                last = t;
                LogStore.add(context, "screencap adb:" + port + " failed: " + t);
            }
        }
        throw new IOException(last == null ? "adbd unavailable" : String.valueOf(last.getMessage()));
    }

    private static byte[] captureOnPort(int port) throws Exception {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 3000);
            socket.setSoTimeout(60000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            OutputStream out = socket.getOutputStream();

            send(out, A_CNXN, 0x01000000, 4096, ascii("host::ex2tools"));
            readUntilConnect(in);

            send(out, A_OPEN, 1, 0,
                    ascii("shell:screencap -p 2>/dev/null | base64 2>/dev/null"));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            int remoteId = 0;
            while (true) {
                Message msg = read(in);
                switch (msg.command) {
                    case A_OKAY:
                        remoteId = msg.arg0;
                        break;
                    case A_WRTE:
                        output.write(msg.payload);
                        send(out, A_OKAY, 1, msg.arg0, new byte[0]);
                        break;
                    case A_CLSE:
                        send(out, A_CLSE, 1, remoteId, new byte[0]);
                        return decode(output.toByteArray());
                    case A_AUTH:
                        throw new IOException("adbd pediu autenticacao por chave");
                    default:
                        throw new IOException("mensagem ADB inesperada: 0x"
                                + Integer.toHexString(msg.command));
                }
            }
        } finally {
            try {
                socket.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static byte[] decode(byte[] raw) throws IOException {
        String text = new String(raw, StandardCharsets.UTF_8).replaceAll("\\s+", "");
        if (text.isEmpty()) throw new IOException("screencap retornou vazio");
        byte[] png;
        try {
            png = Base64.decode(text, Base64.DEFAULT);
        } catch (Throwable t) {
            throw new IOException("base64 invalido: " + t.getMessage());
        }
        if (png.length < 8 || (png[0] & 0xff) != 0x89 || png[1] != 'P') {
            throw new IOException("resultado nao e PNG");
        }
        return png;
    }

    private static void readUntilConnect(DataInputStream in) throws IOException {
        while (true) {
            Message msg = read(in);
            if (msg.command == A_CNXN) return;
            if (msg.command == A_AUTH) {
                throw new IOException("adbd pediu autenticacao por chave");
            }
        }
    }

    private static byte[] ascii(String text) {
        byte[] raw = text.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[raw.length + 1];
        System.arraycopy(raw, 0, out, 0, raw.length);
        out[raw.length] = 0;
        return out;
    }

    private static void send(OutputStream out, int command, int arg0, int arg1, byte[] payload)
            throws IOException {
        ByteBuffer header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(command);
        header.putInt(arg0);
        header.putInt(arg1);
        header.putInt(payload.length);
        header.putInt(checksum(payload));
        header.putInt(~command);
        out.write(header.array());
        out.write(payload);
        out.flush();
    }

    private static Message read(DataInputStream in) throws IOException {
        byte[] headerBytes = new byte[24];
        in.readFully(headerBytes);
        ByteBuffer header = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN);
        int command = header.getInt();
        int arg0 = header.getInt();
        int arg1 = header.getInt();
        int length = header.getInt();
        int sum = header.getInt();
        int magic = header.getInt();
        if ((~command) != magic) throw new IOException("cabecalho ADB invalido");
        byte[] payload = new byte[Math.max(0, length)];
        if (length > 0) in.readFully(payload);
        if (checksum(payload) != sum) throw new IOException("checksum ADB invalido");
        return new Message(command, arg0, arg1, payload);
    }

    private static int checksum(byte[] payload) {
        int sum = 0;
        for (byte b : payload) sum += b & 0xff;
        return sum;
    }

    private static final class Message {
        final int command;
        final int arg0;
        final int arg1;
        final byte[] payload;

        Message(int command, int arg0, int arg1, byte[] payload) {
            this.command = command;
            this.arg0 = arg0;
            this.arg1 = arg1;
            this.payload = payload;
        }
    }
}
