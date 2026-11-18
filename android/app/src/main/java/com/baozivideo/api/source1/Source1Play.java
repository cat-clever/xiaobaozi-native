package com.baozivideo.api.source1;

import com.baozivideo.model.Models;
import com.baozivideo.utils.CryptoUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Source1Play {
    private Source1Play() {}

    public static String mirrorUrl(String vodUrl, String secDomain) {
        if (vodUrl == null || vodUrl.isEmpty() || secDomain == null || secDomain.isEmpty()) return "";
        try {
            URL parts = new URL(vodUrl);
            URL root = new URL(secDomain);
            if (root.getHost().equalsIgnoreCase(parts.getHost())) {
                root = new URL(Source1Constants.SEC_DOMAIN_FALLBACK);
                if (root.getHost().equalsIgnoreCase(parts.getHost())) return "";
            }
            String portStr = root.getPort() != -1 ? ":" + root.getPort() : "";
            String queryStr = parts.getQuery() != null ? "?" + parts.getQuery() : "";
            return root.getProtocol() + "://" + root.getHost() + portStr + parts.getPath() + queryStr;
        } catch (Exception e) {
            return "";
        }
    }

    public static boolean isPlaceholderUrl(String vodUrl) {
        try {
            return Source1Constants.PLACEHOLDER_HOST.equalsIgnoreCase(new URL(vodUrl).getHost());
        } catch (Exception e) {
            return false;
        }
    }

    public static JSONArray asLineList(Object value) {
        if (value instanceof JSONArray) return (JSONArray) value;
        if (value instanceof String && !((String) value).isEmpty()) {
            try {
                return new JSONArray((String) value);
            } catch (Exception ignored) {}
        }
        return new JSONArray();
    }

    public static int parseDuration(String text) {
        if (text == null || text.isEmpty()) return 0;
        double sec = 0;
        for (String part : text.split(":")) {
            String t = part.trim();
            if (t.isEmpty()) return 0;
            try {
                sec = sec * 60 + Double.parseDouble(t);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return (int) sec;
    }

    public static List<Models.PlayLine> playLines(JSONObject bean, String secDomain, String wsKey) {
        List<Models.PlayLine> lines = new ArrayList<>();
        if (bean == null) return lines;
        String vodUrl = bean.optString("vod_url", "");
        boolean placeholder = isPlaceholderUrl(vodUrl);
        String placeholderNote = "接口这次下发的是占位路径，直接播只会拿到一段 11 秒的警告片。";

        if (vodUrl.startsWith("http")) {
            Models.PlayLine l = new Models.PlayLine();
            l.type = Source1Constants.LINE_SIGNED;
            l.kind = Models.LineKind.signed;
            l.name = "主线路";
            l.url = vodUrl;
            l.ck = Source1Crypto.parseCk(bean);
            l.wsKey = wsKey;
            l.playable = !placeholder;
            l.note = placeholder ? placeholderNote : "";
            lines.add(l);
        }

        if (!placeholder) {
            String mirrored = mirrorUrl(vodUrl, secDomain);
            if (!mirrored.isEmpty()) {
                Models.PlayLine l = new Models.PlayLine();
                l.type = Source1Constants.LINE_MIRROR;
                l.kind = Models.LineKind.mirror;
                l.name = "备用线路";
                l.url = mirrored;
                l.playable = true;
                l.note = "";
                lines.add(l);
            }
        }

        JSONArray jx = asLineList(bean.opt("jx_json"));
        String prefix = jx.length() > 0 && jx.optJSONObject(0) != null ? jx.optJSONObject(0).optString("url", "") : "";

        JSONArray official = asLineList(bean.opt("official_json"));
        for (int i = 0; i < official.length(); i++) {
            JSONObject item = official.optJSONObject(i);
            if (item == null) continue;
            String target = item.optString("url", "");
            if (target.isEmpty()) continue;
            Models.PlayLine l = new Models.PlayLine();
            l.type = Source1Constants.LINE_OFFICIAL;
            l.kind = Models.LineKind.official;
            l.name = item.optString("zname", item.optString("name", "官方线路"));
            l.url = target;
            l.parsePrefix = prefix;
            l.playable = !prefix.isEmpty();
            l.note = prefix.isEmpty() ? "这条官方线路没带解析服务，播不了" : "";
            lines.add(l);
        }

        if (vodUrl.startsWith("http")) {
            Models.PlayLine l = new Models.PlayLine();
            l.type = Source1Constants.LINE_BACKUP;
            l.kind = Models.LineKind.backup;
            l.name = "备用高清";
            l.url = vodUrl;
            l.playable = !placeholder;
            l.note = placeholder ? placeholderNote : "";
            lines.add(l);
        }

        JSONArray m3u8 = asLineList(bean.opt("m3u8_json"));
        for (int i = 0; i < m3u8.length(); i++) {
            JSONObject item = m3u8.optJSONObject(i);
            if (item == null) continue;
            String url = item.optString("url", "");
            if (!url.startsWith("http")) continue;
            boolean m3u8Placeholder = isPlaceholderUrl(url);
            Models.PlayLine l = new Models.PlayLine();
            l.type = Source1Constants.LINE_M3U8;
            l.kind = Models.LineKind.m3u8;
            l.name = item.optString("zname", item.optString("name", "m3u8 线路"));
            l.url = url;
            l.playable = !m3u8Placeholder;
            l.note = m3u8Placeholder ? placeholderNote : "";
            lines.add(l);
        }

        return lines;
    }

    public static boolean hasPlayableLine(List<Models.PlayLine> lines) {
        if (lines == null) return false;
        for (Models.PlayLine l : lines) {
            if (l.playable && (l.note == null || l.note.isEmpty()) && l.kind != Models.LineKind.backup) {
                return true;
            }
        }
        return false;
    }

    public static List<Models.Episode> mergeDurations(JSONArray eps, JSONArray lengths) {
        Map<String, String> byId = new HashMap<>();
        if (lengths != null) {
            for (int i = 0; i < lengths.length(); i++) {
                JSONObject row = lengths.optJSONObject(i);
                if (row != null) byId.put(row.optString("id", ""), row.optString("duration", ""));
            }
        }
        List<Models.Episode> out = new ArrayList<>();
        if (eps == null) return out;
        for (int i = 0; i < eps.length(); i++) {
            JSONObject bean = eps.optJSONObject(i);
            if (bean == null) continue;
            String id = bean.optString("id", "");
            String dur = byId.containsKey(id) && !byId.get(id).isEmpty() ? byId.get(id) : bean.optString("duration", "");
            Models.Episode ep = new Models.Episode();
            ep.episodeIndex = i;
            ep.episodeId = id;
            Object titleRaw = bean.has("title") ? bean.opt("title") : (bean.has("collection_new_title") ? bean.opt("collection_new_title") : bean.opt("collection"));
            ep.title = CryptoUtils.episodeTitle(titleRaw, i);
            ep.vodToken = bean.optString("vod_token", "");
            ep.curTime = bean.optString("cur_time", "");
            ep.duration = dur;
            ep.durationSec = parseDuration(dur);
            out.add(ep);
        }
        return out;
    }
}
