package com.baozivideo.api;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class HttpClient {
    public static class Response {
        public final int status;
        public final String text;
        public Response(int status, String text) {
            this.status = status;
            this.text = text;
        }
    }

    private HttpClient() {}

    public static Response request(String urlString, String method, Map<String, String> headers, byte[] body, int timeoutMs) throws ApiException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setUseCaches(false);
            if (headers != null) {
                for (Map.Entry<String, String> e : headers.entrySet()) {
                    conn.setRequestProperty(e.getKey(), e.getValue());
                }
            }
            if (body != null && ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method))) {
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body);
                }
            }
            int code = conn.getResponseCode();
            InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String text = "";
            if (is != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) {
                    baos.write(buf, 0, n);
                }
                text = new String(baos.toByteArray(), StandardCharsets.UTF_8);
                is.close();
            }
            return new Response(code, text);
        } catch (Exception e) {
            throw new ApiException("请求失败：" + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    public static Response get(String urlString, Map<String, String> headers, int timeoutMs) throws ApiException {
        return request(urlString, "GET", headers, null, timeoutMs);
    }

    public static Response post(String urlString, Map<String, String> headers, byte[] body, int timeoutMs) throws ApiException {
        return request(urlString, "POST", headers, body, timeoutMs);
    }
}
