package com.baozivideo.update;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import com.baozivideo.api.HttpClient;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class UpdaterService {
    private static final String REPO = "cat-clever/xiaobaozi-native";
    public static final String RELEASE_PAGE_URL = "https://github.com/" + REPO + "/releases/latest";
    public static final String MANIFEST_URL = "https://github.com/" + REPO + "/releases/latest/download/latest.json";

    public static class Manifest {
        public String version;
        public int versionCode;
        public String androidUrl;
        public String pageUrl;
        public List<String> notes = new ArrayList<>();
    }

    private UpdaterService() {}

    public static String getCurrentVersionName(Context context) {
        try {
            PackageInfo pi = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return pi.versionName == null ? "" : pi.versionName;
        } catch (Exception e) {
            return "";
        }
    }

    public static long getCurrentVersionCode(Context context) {
        try {
            PackageInfo pi = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? pi.getLongVersionCode() : pi.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    public static Manifest fetchManifest() throws Exception {
        String url = MANIFEST_URL + "?t=" + System.currentTimeMillis();
        HttpClient.Response resp = HttpClient.get(url, Collections.emptyMap(), 10000);
        if (resp.status < 200 || resp.status >= 300) throw new Exception("HTTP " + resp.status);
        JSONObject o = new JSONObject(resp.text);
        String ver = o.optString("version", "").trim();
        if (!ver.matches("^\\d+(?:\\.\\d+)*$")) throw new Exception("无效版本号");
        Manifest m = new Manifest();
        m.version = ver;
        m.versionCode = o.optInt("versionCode", 0);
        JSONObject android = o.optJSONObject("android");
        m.androidUrl = android != null ? android.optString("url", "") : "";
        m.pageUrl = o.optString("page", RELEASE_PAGE_URL);
        JSONArray notes = o.optJSONArray("notes");
        if (notes != null) {
            for (int i = 0; i < notes.length(); i++) {
                String n = notes.optString(i, "").trim();
                if (!n.isEmpty()) m.notes.add(n);
            }
        }
        return m;
    }

    public static boolean hasUpdate(Context context, Manifest m) {
        long curCode = getCurrentVersionCode(context);
        if (m.versionCode > 0 && curCode > 0) return m.versionCode > curCode;
        return compareVersions(m.version, getCurrentVersionName(context)) > 0;
    }

    public static int compareVersions(String a, String b) {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        int len = Math.max(pa.length, pb.length);
        for (int i = 0; i < len; i++) {
            int na = i < pa.length ? parseIntSafe(pa[i]) : 0;
            int nb = i < pb.length ? parseIntSafe(pb[i]) : 0;
            if (na != nb) return na > nb ? 1 : -1;
        }
        return 0;
    }

    private static int parseIntSafe(String s) {
        try { return Integer.parseInt(s); }
        catch (Exception e) { return 0; }
    }

    public static void downloadAndInstall(Context context, String urlString) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.getPackageManager().canRequestPackageInstalls()) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            throw new Exception("请先允许「安装未知应用」后重试");
        }

        File updaterDir = new File(context.getCacheDir(), "updater");
        if (!updaterDir.exists()) updaterDir.mkdirs();
        File outFile = new File(updaterDir, "update.apk");
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        try (InputStream is = conn.getInputStream(); FileOutputStream fos = new FileOutputStream(outFile)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = is.read(buf)) != -1) fos.write(buf, 0, n);
        } finally {
            conn.disconnect();
        }

        Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".updater", outFile);
        Intent install = new Intent(Intent.ACTION_VIEW);
        install.setDataAndType(uri, "application/vnd.android.package-archive");
        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(install);
    }
}
