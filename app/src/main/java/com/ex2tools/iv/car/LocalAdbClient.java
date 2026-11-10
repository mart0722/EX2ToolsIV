package com.ex2tools.iv.car;

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
 * Minimal local ADB shell client. Sapo executes its privileged RVC script
 * through local adbd (127.0.0.1), not through the app UID shell.
 */
final class LocalAdbClient {

    private static final int A_AUTH = 0x48545541;  // AUTH
    private static final int A_CLSE = 0x45534c43;  // CLSE
    private static final int A_CNXN = 0x4e584e43;  // CNXN
    private static final int A_OKAY = 0x59414b4f;  // OKAY
    private static final int A_OPEN = 0x4e45504f;  // OPEN
    private static final int A_WRTE = 0x45545257;  // WRTE
    private static final int A_VERSION = 0x01000000;
    private static final int A_MAX_DATA = 4096;

    private final String host;
    private final int port;

    LocalAdbClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    String runShell(String command, int timeoutMs) throws IOException {
        long deadline = System.currentTimeMillis() + Math.max(1, timeoutMs);
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port),
                    Math.max(1, (int) Math.min(Integer.MAX_VALUE, timeoutMs)));
            socket.setSoTimeout(timeoutMs);
            DataInputStream input = new DataInputStream(socket.getInputStream());
            OutputStream output = socket.getOutputStream();

            send(output, A_CNXN, A_VERSION, A_MAX_DATA, ascii("host::central-geely"));
            if (readUntilConnect(input, socket, deadline).command != A_CNXN) {
                throw new IOException("unexpected ADB handshake");
            }
            send(output, A_OPEN, 1, 0, ascii("shell:" + command));

            ByteArrayOutputStream result = new ByteArrayOutputStream();
            boolean opened = false;
            int remoteId = 0;
            while (true) {
                socket.setSoTimeout(remaining(deadline));
                Message message = read(input);
                if (message.command == A_AUTH) {
                    throw new IOException("adbd requested authentication");
                }
                if (message.command == A_OKAY) {
                    if (message.arg1 == 1) {
                        remoteId = message.arg0;
                        opened = true;
                    }
                } else if (message.command == A_WRTE) {
                    if (!opened) {
                        remoteId = message.arg0;
                        opened = true;
                    }
                    result.write(message.payload);
                    send(output, A_OKAY, 1, message.arg0, new byte[0]);
                } else if (message.command == A_CLSE) {
                    send(output, A_CLSE, 1, remoteId, new byte[0]);
                    return result.toString("UTF-8").trim();
                } else {
                    throw new IOException("unexpected ADB message: 0x"
                            + Integer.toHexString(message.command));
                }
            }
        } finally {
            try {
                socket.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static int remaining(long deadline) throws IOException {
        long left = deadline - System.currentTimeMillis();
        if (left <= 0) throw new IOException("ADB command timed out");
        return (int) Math.min(left, Integer.MAX_VALUE);
    }

    private static Message readUntilConnect(DataInputStream input, Socket socket,
                                            long deadline) throws IOException {
        Message message;
        do {
            socket.setSoTimeout(remaining(deadline));
            message = read(input);
            if (message.command == A_AUTH) {
                throw new IOException("adbd requested authentication");
            }
        } while (message.command != A_CNXN);
        return message;
    }

    private static byte[] ascii(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[bytes.length + 1];
        System.arraycopy(bytes, 0, out, 0, bytes.length);
        return out;
    }

    private static void send(OutputStream output, int command, int arg0, int arg1,
                             byte[] payload) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(command);
        header.putInt(arg0);
        header.putInt(arg1);
        header.putInt(payload.length);
        header.putInt(checksum(payload));
        header.putInt(~command);
        output.write(header.array());
        output.write(payload);
        output.flush();
    }

    private static Message read(DataInputStream input) throws IOException {
        byte[] header = new byte[24];
        input.readFully(header);
        ByteBuffer buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        int command = buffer.getInt();
        int arg0 = buffer.getInt();
        int arg1 = buffer.getInt();
        int length = buffer.getInt();
        int expectedChecksum = buffer.getInt();
        if ((~command) != buffer.getInt()) {
            throw new IOException("invalid ADB header");
        }
        if (length < 0 || length > A_MAX_DATA) {
            throw new IOException("invalid ADB payload length");
        }
        byte[] payload = new byte[length];
        if (length > 0) input.readFully(payload);
        if (checksum(payload) != expectedChecksum) {
            throw new IOException("invalid ADB checksum");
        }
        return new Message(command, arg0, arg1, payload);
    }

    private static int checksum(byte[] payload) {
        int sum = 0;
        for (byte b : payload) sum += b & 0xFF;
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
