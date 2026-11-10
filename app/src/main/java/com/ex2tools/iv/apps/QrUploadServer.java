package com.ex2tools.iv.apps;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.ex2tools.iv.R;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class QrUploadServer {

    public interface Listener {
        void onReady(String url);

        void onProgress(long received, long total);

        void onFile(File apk);

        void onError(String message);
    }

    private final Context app;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService clients = Executors.newCachedThreadPool();
    private final AtomicBoolean uploading = new AtomicBoolean(false);
    private final String token;

    private volatile boolean running;
    private volatile ServerSocket server;
    private volatile Socket activeSocket;
    private volatile File activeFile;
    private volatile boolean completed;
    private long lastProgress;

    public QrUploadServer(Context context, Listener listener) {
        this.app = context.getApplicationContext();
        this.listener = listener;
        this.token = randomToken();
    }

    public void start() {
        if (running) return;
        running = true;
        Thread thread = new Thread(this::acceptLoop, "qr-upload-server");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running = false;
        try {
            ServerSocket s = server;
            if (s != null) s.close();
        } catch (Throwable ignored) {
        }
        server = null;
        try {
            Socket s = activeSocket;
            if (s != null) s.close();
        } catch (Throwable ignored) {
        }
        activeSocket = null;
        clients.shutdownNow();
        File f = activeFile;
        if (f != null && !completed) {
            f.delete();
            new File(f.getAbsolutePath() + ".part").delete();
        }
        activeFile = null;
    }

    private void acceptLoop() {
        try {
            String ip = localIpv4();
            if (ip == null) throw new IOException("нет сети");
            ServerSocket ss = new ServerSocket(0);
            server = ss;
            String url = "http://" + ip + ":" + ss.getLocalPort() + "/" + token + "/";
            post(() -> listener.onReady(url));
            while (running) {
                Socket socket = ss.accept();
                if (!running) {
                    close(socket);
                    break;
                }
                clients.execute(() -> handle(socket));
            }
        } catch (Throwable t) {
            if (running) post(() -> listener.onError(message(t)));
        } finally {
            running = false;
        }
    }

    private void handle(Socket socket) {
        activeSocket = socket;
        try (Socket s = socket;
             BufferedInputStream in = new BufferedInputStream(s.getInputStream());
             OutputStream out = s.getOutputStream()) {
            String requestLine = readLine(in);
            if (requestLine == null) return;
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                write(out, 400, "Bad Request", "text/plain; charset=utf-8", "Bad request");
                return;
            }
            String method = parts[0];
            String path = parts[1];
            Map<String, String> headers = readHeaders(in);
            String base = "/" + token;

            if (!path.startsWith(base)) {
                write(out, 404, "Not Found", "text/plain; charset=utf-8", "Not found");
                return;
            }
            String rest = path.substring(base.length());
            int queryAt = rest.indexOf('?');
            String query = queryAt >= 0 ? rest.substring(queryAt + 1) : "";
            if (queryAt >= 0) rest = rest.substring(0, queryAt);

            if ("GET".equals(method) && (rest.isEmpty() || "/".equals(rest)
                    || "/index.html".equals(rest))) {
                write(out, 200, "OK", "text/html; charset=utf-8", page());
                return;
            }
            if ("GET".equals(method) && "/favicon.png".equals(rest)) {
                byte[] icon = iconBytes();
                if (icon != null) {
                    writeBytes(out, 200, "OK", "image/png", icon);
                } else {
                    write(out, 404, "Not Found", "text/plain; charset=utf-8", "Not found");
                }
                return;
            }
            if ("GET".equals(method) && "/status".equals(rest)) {
                write(out, 200, "OK", "application/json",
                        "{\"ok\":true,\"busy\":" + uploading.get() + "}");
                return;
            }
            if ("POST".equals(method) && "/upload".equals(rest)) {
                handleUpload(in, out, headers, query);
                return;
            }
            write(out, 404, "Not Found", "text/plain; charset=utf-8", "Not found");
        } catch (Throwable t) {
            if (running) post(() -> listener.onError(message(t)));
        } finally {
            if (activeSocket == socket) activeSocket = null;
        }
    }

    private void handleUpload(InputStream in, OutputStream out, Map<String, String> headers,
                              String query) throws IOException {
        if (!uploading.compareAndSet(false, true)) {
            write(out, 409, "Conflict", "application/json", "{\"ok\":false,\"error\":\"busy\"}");
            return;
        }
        File file = null;
        try {
            String name = queryParam(query, "name");
            if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".apk")) {
                write(out, 400, "Bad Request", "application/json",
                        "{\"ok\":false,\"error\":\"apk only\"}");
                return;
            }
            String expect = headers.get("expect");
            if (expect != null && expect.toLowerCase(Locale.ROOT).contains("100-continue")) {
                out.write("HTTP/1.1 100 Continue\r\n\r\n"
                        .getBytes(StandardCharsets.ISO_8859_1));
                out.flush();
            }
            long length = parseLong(headers.get("content-length"));
            String transfer = headers.get("transfer-encoding");
            boolean chunked = transfer != null
                    && transfer.toLowerCase(Locale.ROOT).contains("chunked");
            if (!chunked && length <= 0) throw new IOException("empty body");
            File dir = new File(app.getCacheDir(), "qr_uploads");
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("cache unavailable");
            long usable = dir.getUsableSpace();
            if (length > 0 && usable > 0 && length > usable - 16L * 1024L * 1024L) {
                write(out, 507, "Insufficient Storage", "application/json",
                        "{\"ok\":false,\"error\":\"no space\"}");
                return;
            }

            file = new File(dir, "upload_" + System.currentTimeMillis() + ".apk");
            activeFile = file;
            completed = false;
            copyBody(in, headers, file, length);
            completed = true;
            activeFile = null;
            write(out, 200, "OK", "application/json", "{\"ok\":true}");
            final File done = file;
            post(() -> listener.onFile(done));
        } catch (Throwable t) {
            if (file != null) {
                file.delete();
                new File(file.getAbsolutePath() + ".part").delete();
            }
            activeFile = null;
            final String msg = message(t);
            write(out, 500, "Internal Error", "application/json",
                    "{\"ok\":false,\"error\":\"upload failed\"}");
            post(() -> listener.onError(msg));
        } finally {
            uploading.set(false);
        }
    }

    private void copyBody(InputStream in, Map<String, String> headers, File file,
                          long contentLength) throws IOException {
        File part = new File(file.getAbsolutePath() + ".part");
        FileOutputStream fos = new FileOutputStream(part);
        try {
            String transfer = headers.get("transfer-encoding");
            if (transfer != null && transfer.toLowerCase(Locale.ROOT).contains("chunked")) {
                copyChunked(in, fos);
            } else {
                copyFixed(in, fos, contentLength);
            }
            fos.flush();
            fos.getFD().sync();
        } finally {
            fos.close();
        }
        if (!part.renameTo(file)) {
            throw new IOException("rename failed");
        }
    }

    private void copyFixed(InputStream in, OutputStream out, long length) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long readTotal = 0;
        if (length <= 0) {
            int read;
            while ((read = in.read(buffer)) >= 0) {
                if (read == 0) continue;
                out.write(buffer, 0, read);
                readTotal += read;
                progress(readTotal, -1);
                if (!running) throw new IOException("cancelled");
            }
        } else {
            long remaining = length;
            while (remaining > 0) {
                int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read < 0) throw new IOException("connection closed");
                out.write(buffer, 0, read);
                remaining -= read;
                readTotal += read;
                progress(readTotal, length);
                if (!running) throw new IOException("cancelled");
            }
        }
        progress(readTotal, length > 0 ? length : readTotal);
    }

    private void copyChunked(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long total = 0;
        while (true) {
            String line = readLine(in);
            if (line == null) throw new IOException("chunk missing");
            int semi = line.indexOf(';');
            if (semi >= 0) line = line.substring(0, semi);
            int size;
            try {
                size = Integer.parseInt(line.trim(), 16);
            } catch (Throwable t) {
                throw new IOException("bad chunk");
            }
            if (size == 0) {
                String trailer;
                do {
                    trailer = readLine(in);
                } while (trailer != null && !trailer.isEmpty());
                break;
            }
            int remaining = size;
            while (remaining > 0) {
                int read = in.read(buffer, 0, Math.min(buffer.length, remaining));
                if (read < 0) throw new IOException("chunk closed");
                out.write(buffer, 0, read);
                remaining -= read;
                total += read;
                progress(total, -1);
                if (!running) throw new IOException("cancelled");
            }
            readLine(in);
        }
        progress(total, total);
    }

    private void progress(long received, long total) {
        long now = System.currentTimeMillis();
        if (total > 0 && received < total && now - lastProgress < 120) return;
        if (total <= 0 && now - lastProgress < 200) return;
        lastProgress = now;
        post(() -> listener.onProgress(received, total));
    }

    private String page() {
        return """
                <!doctype html>
                <html lang="ru">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
                <link rel="icon" type="image/png" href="favicon.png">
                <title>EX2ToolsIV - установка</title>
                <style>
                :root{--bg:#D3DBE5;--card:#FFFFFF;--text:#12161C;--muted:#525A66;--soft:#6B7684;--accent:#1368FB;--line:#E7ECF3}
                *{box-sizing:border-box}
                body{margin:0;min-height:100vh;background:var(--bg);color:var(--text);font:17px/1.45 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;display:flex;align-items:flex-start;justify-content:center;padding:24px 16px}
                .wrap{width:100%;max-width:560px}
                .card{background:var(--card);border-radius:22px;padding:24px;box-shadow:0 14px 42px rgba(18,22,28,.12)}
                .brand{display:flex;align-items:center;gap:14px;margin-bottom:8px}
                .logo{width:50px;height:50px;border-radius:15px;background:var(--accent);display:block;object-fit:cover}
                h1{font-size:23px;line-height:1.15;margin:0;letter-spacing:-.3px}
                .sub{color:var(--muted);font-size:15px;margin:2px 0 0}
                .steps{list-style:none;padding:0;margin:22px 0 24px;display:grid;gap:10px}
                .steps li{display:flex;gap:12px;align-items:flex-start;background:#F7F9FC;border:1px solid var(--line);border-radius:15px;padding:12px 14px}
                .num{flex:0 0 28px;height:28px;border-radius:50%;background:#E5EFFE;color:var(--accent);display:grid;place-items:center;font-weight:700;font-size:14px}
                .steps b{display:block;font-size:16px;line-height:1.25}
                .steps small{display:block;color:var(--soft);font-size:14px;margin-top:2px}
                .label{display:flex;align-items:center;justify-content:space-between;gap:12px;font-size:15px;font-weight:600;margin:0 0 10px}
                .badge{font-size:12px;font-weight:600;color:var(--accent);background:#E5EFFE;border-radius:999px;padding:4px 9px;text-transform:uppercase;letter-spacing:.4px}
                input[type=file]{width:100%;padding:16px;border:1.5px dashed #C9D2DC;border-radius:14px;background:#F7F9FC;color:var(--text);font:inherit}
                input[type=file]:focus{outline:2px solid var(--accent);outline-offset:2px}
                button{width:100%;margin-top:14px;padding:16px;border:0;border-radius:14px;background:var(--accent);color:#fff;font:inherit;font-size:18px;font-weight:700;cursor:pointer}
                button:active{transform:scale(.99)}
                button:disabled{opacity:.45;cursor:default}
                progress{-webkit-appearance:none;appearance:none;width:100%;height:10px;margin-top:18px;border:0;border-radius:999px;background:#E6EBF2;overflow:hidden}
                progress::-webkit-progress-bar{background:#E6EBF2;border-radius:999px}
                progress::-webkit-progress-value{background:var(--accent);border-radius:999px}
                progress::-moz-progress-bar{background:var(--accent);border-radius:999px}
                .status{margin-top:12px;color:var(--muted);font-size:15px;min-height:22px}
                .note{margin-top:20px;padding-top:16px;border-top:1px solid var(--line);color:var(--soft);font-size:14px}
                .note b{color:var(--muted)}
                </style>
                </head>
                <body>
                <div class="wrap">
                  <div class="card">
                    <div class="brand">
                      <img class="logo" src="favicon.png" alt="EX2ToolsIV">
                      <div>
                        <h1>EX2ToolsIV</h1>
                        <p class="sub">Установка приложений на головное устройство</p>
                      </div>
                    </div>
                    <ol class="steps">
                      <li><span class="num">1</span><div><b>Выберите APK</b><small>Файл приложения для установки</small></div></li>
                      <li><span class="num">2</span><div><b>Загрузите файл</b><small>Телефон и ГУ должны быть в одной сети</small></div></li>
                      <li><span class="num">3</span><div><b>Подтвердите на ГУ</b><small>Диалог установки появится на экране автомобиля</small></div></li>
                    </ol>
                    <label class="label" for="f">Файл приложения <span class="badge">.apk</span></label>
                    <input id="f" type="file" accept=".apk,application/vnd.android.package-archive">
                    <button id="b">Загрузить и установить</button>
                    <progress id="p" max="100" value="0"></progress>
                    <div id="s" class="status" aria-live="polite">Ожидание файла…</div>
                    <div class="note"><b>Не закрывайте страницу</b> до завершения загрузки. Перед установкой приложение проверит подпись и версию.</div>
                  </div>
                </div>
                <script>
                const f=document.getElementById('f'),b=document.getElementById('b'),p=document.getElementById('p'),s=document.getElementById('s');
                const setStatus=(t)=>{s.textContent=t;};
                f.addEventListener('change',()=>{const file=f.files[0];setStatus(file?'Выбран: '+file.name:'Ожидание файла…');});
                b.onclick=()=>{
                  const file=f.files[0];
                  if(!file){setStatus('Выберите APK');return;}
                  if(!/\\.apk$/i.test(file.name)){setStatus('Нужен файл .apk');return;}
                  b.disabled=true;setStatus('Загрузка…');p.value=0;
                  const x=new XMLHttpRequest();
                  x.open('POST','upload?name='+encodeURIComponent(file.name));
                  x.setRequestHeader('Content-Type','application/octet-stream');
                  x.upload.onprogress=e=>{if(e.lengthComputable){p.value=Math.round(e.loaded*100/e.total);setStatus('Загрузка: '+p.value+'%');}};
                  x.onload=()=>{if(x.status===200){p.value=100;setStatus('Файл получен. Проверьте экран ГУ и подтвердите установку.');}else{setStatus('Ошибка загрузки: '+x.status);b.disabled=false;}};
                  x.onerror=()=>{setStatus('Сеть недоступна');b.disabled=false;};
                  x.send(file);
                };
                </script>
                </body>
                </html>
                """;
    }

    private static Map<String, String> readHeaders(InputStream in) throws IOException {
        Map<String, String> out = new HashMap<>();
        while (true) {
            String line = readLine(in);
            if (line == null || line.isEmpty()) break;
            int at = line.indexOf(':');
            if (at <= 0) continue;
            out.put(line.substring(0, at).trim().toLowerCase(Locale.ROOT),
                    line.substring(at + 1).trim());
        }
        return out;
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int b;
        boolean any = false;
        while ((b = in.read()) >= 0) {
            any = true;
            if (b == '\n') break;
            if (b != '\r') out.write(b);
            if (out.size() > 8192) throw new IOException("header too long");
        }
        if (!any && out.size() == 0) return null;
        return out.toString(StandardCharsets.ISO_8859_1.name());
    }

    private static void write(OutputStream out, int code, String reason, String type, String body)
            throws IOException {
        writeBytes(out, code, reason, type, body.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeBytes(OutputStream out, int code, String reason, String type,
                                   byte[] bytes) throws IOException {
        String head = "HTTP/1.1 " + code + " " + reason + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Content-Length: " + bytes.length + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.ISO_8859_1));
        out.write(bytes);
        out.flush();
    }

    private byte[] iconBytes() {
        try (InputStream in = app.getResources().openRawResource(R.drawable.qr_icon);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            return out.toByteArray();
        } catch (Throwable t) {
            return null;
        }
    }

    private static String queryParam(String query, String key) {
        if (query == null || query.isEmpty()) return null;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String k = eq >= 0 ? pair.substring(0, eq) : pair;
            if (!key.equals(k)) continue;
            try {
                return URLDecoder.decode(eq >= 0 ? pair.substring(eq + 1) : "", "UTF-8");
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    private static long parseLong(String value) {
        if (value == null) return 0;
        try {
            return Long.parseLong(value);
        } catch (Throwable t) {
            return 0;
        }
    }

    private static String message(Throwable t) {
        String m = t.getMessage();
        return m == null || m.isEmpty() ? t.getClass().getSimpleName() : m;
    }

    private static String randomToken() {
        byte[] data = new byte[16];
        new SecureRandom().nextBytes(data);
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) sb.append(String.format(Locale.US, "%02x", b & 0xff));
        return sb.toString();
    }

    private static String localIpv4() {
        String best = null;
        int bestScore = -1;
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces != null && ifaces.hasMoreElements()) {
                NetworkInterface ni = ifaces.nextElement();
                if (ni == null || !ni.isUp() || ni.isLoopback()) continue;
                String name = ni.getName() == null ? "" : ni.getName();
                int score = name.startsWith("ap") ? 4
                        : name.startsWith("wlan") ? 3
                        : name.startsWith("eth") ? 2 : 1;
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress address = ia.getAddress();
                    if (!(address instanceof Inet4Address)) continue;
                    if (address.isLoopbackAddress() || address.isLinkLocalAddress()) continue;
                    String host = address.getHostAddress();
                    if (host == null || host.isEmpty()) continue;
                    boolean siteLocal = address.isSiteLocalAddress();
                    int s = score + (siteLocal ? 10 : 0);
                    if (s > bestScore) {
                        bestScore = s;
                        best = host;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return best;
    }

    private void post(Runnable runnable) {
        main.post(runnable);
    }

    private static void close(Socket socket) {
        try {
            if (socket != null) socket.close();
        } catch (Throwable ignored) {
        }
    }
}
