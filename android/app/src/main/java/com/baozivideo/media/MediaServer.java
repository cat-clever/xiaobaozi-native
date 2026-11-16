package com.baozivideo.media;

import com.baozivideo.utils.CryptoUtils;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MediaServer {
    public static class SignRule {
        public final String key;
        public final String extra;
        public SignRule(String key, String extra) {
            this.key = key;
            this.extra = extra;
        }
    }

    public static class Session {
        public final Map<String, String> headers;
        public final SignRule sign;
        public final boolean inheritQuery;
        public final boolean forCast;
        public Session(Map<String, String> headers, SignRule sign, boolean inheritQuery, boolean forCast) {
            this.headers = headers;
            this.sign = sign;
            this.inheritQuery = inheritQuery;
            this.forCast = forCast;
        }
    }

    private static final MediaServer instance = new MediaServer();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final ExecutorService pool = Executors.newCachedThreadPool();

    private ServerSocket serverSocket;
    private int port = 0;
    private String token = "";

    private MediaServer() {}

    public static MediaServer getInstance() { return instance; }

    public static String lanAddress() {
        String preferred = "";
        String fallback = "";
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface nif : Collections.list(ifaces)) {
                if (!nif.isUp() || nif.isLoopback()) continue;
                String name = nif.getName();
                for (InetAddress addr : Collections.list(nif.getInetAddresses())) {
                    if (!(addr instanceof Inet4Address)) continue;
                    String ip = addr.getHostAddress();
                    if (ip == null || ip.startsWith("127.") || ip.startsWith("169.254.")) continue;
                    if (name.startsWith("wlan") || name.startsWith("eth")) {
                        if (preferred.isEmpty()) preferred = ip;
                    } else if (!name.startsWith("tun") && !name.startsWith("rmnet") && fallback.isEmpty()) {
                        fallback = ip;
                    }
                }
            }
        } catch (Exception ignored) {}
        return preferred.isEmpty() ? fallback : preferred;
    }

    public synchronized void ensureStarted() throws Exception {
        if (serverSocket != null && !serverSocket.isClosed() && port > 0) return;
        serverSocket = new ServerSocket(0);
        port = serverSocket.getLocalPort();
        token = UUID.randomUUID().toString().replace("-", "");
        ServerSocket ss = serverSocket;
        pool.execute(() -> {
            while (!ss.isClosed()) {
                try {
                    Socket client = ss.accept();
                    pool.execute(() -> handleClient(client));
                } catch (Exception ignored) {
                    if (ss.isClosed()) break;
                }
            }
        });
    }

    public synchronized void stop() {
        sessions.clear();
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (Exception ignored) {}
        serverSocket = null;
        port = 0;
    }

    public String openSession(Map<String, String> headers, SignRule sign, boolean inheritQuery, boolean forCast) throws Exception {
        ensureStarted();
        String sid = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        sessions.put(sid, new Session(headers, sign, inheritQuery, forCast));
        return sid;
    }

    public void closeSession(String sid) {
        if (sid == null) return;
        sessions.remove(sid);
        if (sessions.isEmpty()) stop();
    }

    public String streamUrl(String sid, String upstreamUrl, String format, boolean forCast) {
        String host = forCast ? lanAddress() : "127.0.0.1";
        if (host.isEmpty()) host = "127.0.0.1";
        String encoded = CryptoUtils.base64UrlEncode(upstreamUrl.getBytes(StandardCharsets.UTF_8));
        String name = "mp4".equals(format) || upstreamUrl.split("\\?")[0].toLowerCase().endsWith(".m3u8")
            ? PlaylistRewriter.basenameOf(upstreamUrl)
            : PlaylistRewriter.localSegmentName(upstreamUrl, forCast);
        return "http://" + host + ":" + port + "/m/" + token + "/" + sid + "/" + encoded + "/" + name;
    }

    private void handleClient(Socket socket) {
        try (Socket s = socket) {
            s.setSoTimeout(30000);
            InputStream in = new BufferedInputStream(s.getInputStream());
            OutputStream out = s.getOutputStream();

            ByteArrayOutputStream headBuf = new ByteArrayOutputStream();
            int prev = 0, curr;
            int headerState = 0;
            while ((curr = in.read()) != -1) {
                headBuf.write(curr);
                if (headerState == 0 && curr == '\r') headerState = 1;
                else if (headerState == 1 && curr == '\n') headerState = 2;
                else if (headerState == 2 && curr == '\r') headerState = 3;
                else if (headerState == 3 && curr == '\n') break;
                else headerState = 0;
                if (headBuf.size() > 8192) break;
            }
            String head = new String(headBuf.toByteArray(), StandardCharsets.UTF_8);
            String[] lines = head.split("\r\n");
            if (lines.length == 0) return;
            String[] reqParts = lines[0].split(" ");
            if (reqParts.length < 2) return;
            String method = reqParts[0].toUpperCase();
            String rawPath = reqParts[1].split("\\?")[0];
            String[] segs = rawPath.replaceAll("^/+", "").split("/");
            if (segs.length < 4 || !"m".equals(segs[0]) || !token.equals(segs[1])) {
                sendStatus(out, 403, "Forbidden");
                return;
            }
            String sid = segs[2];
            Session session = sessions.get(sid);
            if (session == null) {
                sendStatus(out, 404, "Session Not Found");
                return;
            }
            String targetUrl = new String(CryptoUtils.base64DecodeFlexible(segs[3]), StandardCharsets.UTF_8);
            if (!targetUrl.startsWith("http")) {
                sendStatus(out, 400, "Bad Target");
                return;
            }

            String range = null;
            String reqHost = "127.0.0.1:" + port;
            for (int i = 1; i < lines.length; i++) {
                int idx = lines[i].indexOf(':');
                if (idx <= 0) continue;
                String k = lines[i].substring(0, idx).trim().toLowerCase();
                String v = lines[i].substring(idx + 1).trim();
                if ("range".equals(k)) range = v;
                else if ("host".equals(k)) reqHost = v;
            }

            boolean isPlaylist = targetUrl.split("\\?")[0].toLowerCase().endsWith(".m3u8");
            String finalUrl = signUrlIfNeeded(targetUrl, session.sign);
            HttpURLConnection conn = (HttpURLConnection) new URL(finalUrl).openConnection();
            conn.setRequestMethod("HEAD".equals(method) && !isPlaylist ? "HEAD" : "GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            for (Map.Entry<String, String> e : session.headers.entrySet()) {
                conn.setRequestProperty(e.getKey(), e.getValue());
            }
            if (range != null && !isPlaylist) conn.setRequestProperty("Range", range);

            int status = conn.getResponseCode();
            if (isPlaylist) {
                if (status < 200 || status >= 300) {
                    sendStatus(out, 502, "Upstream Error");
                    return;
                }
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try (InputStream uis = conn.getInputStream()) {
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = uis.read(buf)) != -1) baos.write(buf, 0, n);
                }
                String text = new String(baos.toByteArray(), StandardCharsets.UTF_8);
                String prefix = "http://" + reqHost + "/m/" + token + "/" + sid;
                String rewritten = PlaylistRewriter.rewrite(text, targetUrl, session.inheritQuery, absUrl -> {
                    String b64 = CryptoUtils.base64UrlEncode(absUrl.getBytes(StandardCharsets.UTF_8));
                    return prefix + "/" + b64 + "/" + PlaylistRewriter.localSegmentName(absUrl, session.forCast);
                });
                byte[] outBytes = rewritten.getBytes(StandardCharsets.UTF_8);
                String hdr = "HTTP/1.1 200 OK\r\nContent-Type: application/vnd.apple.mpegurl\r\nContent-Length: " + outBytes.length + "\r\nConnection: close\r\n\r\n";
                out.write(hdr.getBytes(StandardCharsets.UTF_8));
                if (!"HEAD".equals(method)) out.write(outBytes);
                out.flush();
            } else {
                StringBuilder hdr = new StringBuilder();
                hdr.append("HTTP/1.1 ").append(status).append(" OK\r\n");
                String ct = conn.getContentType();
                if (ct != null) hdr.append("Content-Type: ").append(ct).append("\r\n");
                String cr = conn.getHeaderField("Content-Range");
                if (cr != null) hdr.append("Content-Range: ").append(cr).append("\r\n");
                String ar = conn.getHeaderField("Accept-Ranges");
                if (ar != null) hdr.append("Accept-Ranges: ").append(ar).append("\r\n");
                long len = conn.getContentLengthLong();
                if (len >= 0) hdr.append("Content-Length: ").append(len).append("\r\n");
                hdr.append("Connection: close\r\n\r\n");
                out.write(hdr.toString().getBytes(StandardCharsets.UTF_8));
                if (!"HEAD".equals(method)) {
                    InputStream uis = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
                    if (uis != null) {
                        byte[] buf = new byte[65536];
                        int n;
                        while ((n = uis.read(buf)) != -1) {
                            out.write(buf, 0, n);
                        }
                        uis.close();
                    }
                }
                out.flush();
            }
            conn.disconnect();
        } catch (Exception ignored) {}
    }

    private String signUrlIfNeeded(String urlString, SignRule rule) {
        if (rule == null || rule.key == null || rule.key.isEmpty()) return urlString;
        try {
            URL url = new URL(urlString);
            long ts = System.currentTimeMillis() / 1000L;
            String tsHex = Long.toHexString(ts);
            String path = url.getPath();
            String secret = CryptoUtils.md5Hex(rule.key + path + tsHex);

            StringBuilder q = new StringBuilder();
            String rawQuery = url.getQuery();
            if (rawQuery != null && !rawQuery.isEmpty()) {
                for (String kv : rawQuery.split("&")) {
                    if (kv.isEmpty()) continue;
                    String k = kv.split("=", 2)[0];
                    if (!"wsSecret".equals(k) && !"wsTime".equals(k)) {
                        if (q.length() > 0) q.append("&");
                        q.append(kv);
                    }
                }
            }
            if (q.length() > 0) q.append("&");
            q.append("wsSecret=").append(secret).append("&wsTime=").append(tsHex);
            if (rule.extra != null && !rule.extra.isEmpty()) {
                q.append("&").append(rule.extra);
            }
            String portStr = url.getPort() != -1 ? ":" + url.getPort() : "";
            return url.getProtocol() + "://" + url.getHost() + portStr + path + "?" + q;
        } catch (Exception e) {
            return urlString;
        }
    }

    private void sendStatus(OutputStream out, int code, String text) throws Exception {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        String hdr = "HTTP/1.1 " + code + " " + text + "\r\nContent-Type: text/plain\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n";
        out.write(hdr.getBytes(StandardCharsets.UTF_8));
        out.write(body);
        out.flush();
    }
}
