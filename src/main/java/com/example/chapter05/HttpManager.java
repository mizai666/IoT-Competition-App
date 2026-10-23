package com.example.chapter05;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * HTTP 请求工具（原生 HttpURLConnection，无需额外依赖）。
 * 需在后台线程调用。
 */
public class HttpManager {

    public static class HttpResult {
        public int status;
        public String body;

        public HttpResult(int status, String body) {
            this.status = status;
            this.body = body;
        }

        public boolean isOk() {
            return status >= 200 && status < 300;
        }
    }

    public static HttpResult request(String urlStr, String method, String body) throws Exception {
        if (urlStr == null || urlStr.trim().isEmpty()) {
            throw new IllegalArgumentException("URL 不能为空");
        }
        urlStr = urlStr.trim();
        if (!urlStr.startsWith("http://") && !urlStr.startsWith("https://")) {
            urlStr = "http://" + urlStr;
        }

        String upperMethod = (method == null || method.isEmpty()) ? "GET" : method.toUpperCase();
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod(upperMethod);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");

        if ("POST".equals(upperMethod) || "PUT".equals(upperMethod)) {
            conn.setDoOutput(true);
            byte[] bytes = (body == null ? "" : body).getBytes(StandardCharsets.UTF_8);
            conn.setRequestProperty("Content-Length", String.valueOf(bytes.length));
            OutputStream os = conn.getOutputStream();
            os.write(bytes);
            os.close();
        }

        int status = conn.getResponseCode();
        InputStream is = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();
        String resp = readStream(is);
        conn.disconnect();
        return new HttpResult(status, resp);
    }

    private static String readStream(InputStream is) throws Exception {
        if (is == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append('\n');
        }
        reader.close();
        return sb.toString().trim();
    }
}
