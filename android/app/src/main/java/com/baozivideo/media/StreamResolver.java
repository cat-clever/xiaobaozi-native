package com.baozivideo.media;

import com.baozivideo.api.ApiException;
import com.baozivideo.api.source1.Source1Constants;
import com.baozivideo.api.source1.Source1Service;
import com.baozivideo.api.source2.Source2Constants;
import com.baozivideo.model.Models;
import org.json.JSONObject;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class StreamResolver {
    private static final Map<String, String> S1_HEADERS = new HashMap<>();
    private static final Map<String, String> S1_SIGNED_HEADERS = new HashMap<>();
    private static final Map<String, String> S2_HEADERS = new HashMap<>();

    static {
        S1_HEADERS.put("User-Agent", Source1Constants.MEDIA_UA);
        S1_HEADERS.put("Accept", "*/*");

        S1_SIGNED_HEADERS.putAll(S1_HEADERS);
        S1_SIGNED_HEADERS.put("Badci", Source1Constants.BADCI);

        S2_HEADERS.put("User-Agent", Source2Constants.PLAY_UA);
    }

    private StreamResolver() {}

    public static String formatOf(String url) {
        return url.toLowerCase().matches(".*\\.mp4(\\?.*|$)") ? "mp4" : "hls";
    }

    public static Models.PlayLine pickPlayableLine(List<Models.PlayLine> lines) {
        Models.PlayLine mirror = null, m3u8 = null, official = null, signed = null;
        for (Models.PlayLine l : lines) {
            if (!l.playable || (l.note != null && !l.note.isEmpty())) continue;
            if (l.kind == Models.LineKind.mirror && mirror == null) mirror = l;
            else if (l.kind == Models.LineKind.m3u8 && m3u8 == null) m3u8 = l;
            else if (l.kind == Models.LineKind.official && official == null) official = l;
            else if (l.kind == Models.LineKind.signed && signed == null) signed = l;
        }
        if (mirror != null) return mirror;
        if (m3u8 != null) return m3u8;
        if (official != null) return official;
        return signed;
    }

    private static Models.ResolvedMediaStream openLocal(String target, Map<String, String> headers, MediaServer.SignRule sign, boolean inheritQuery, String format, String lineName) {
        Models.ResolvedMediaStream res = new Models.ResolvedMediaStream();
        res.format = format;
        res.lineName = lineName;
        try {
            String sid = MediaServer.getInstance().openSession(headers, sign, inheritQuery, false);
            res.streamUrl = MediaServer.getInstance().streamUrl(sid, target, format, false);
            res.headers = Collections.emptyMap();
            res.dispose = () -> MediaServer.getInstance().closeSession(sid);
        } catch (Exception e) {
            res.streamUrl = target;
            res.headers = headers;
            res.dispose = () -> {};
        }
        return res;
    }

    public static Models.ResolvedMediaStream resolveSource1(List<Models.PlayLine> lines, Models.PlayLine preferred) throws ApiException {
        Models.PlayLine line = preferred != null ? preferred : pickPlayableLine(lines);
        if (line == null) throw new ApiException("这些线路当前都播不了");
        String target = line.kind == Models.LineKind.official ? Source1Service.resolveOfficialLine(line) : line.url;
        String format = formatOf(target);
        Map<String, String> hdrs = line.kind == Models.LineKind.signed ? S1_SIGNED_HEADERS : S1_HEADERS;
        MediaServer.SignRule sign = (line.kind == Models.LineKind.signed && line.wsKey != null && !line.wsKey.isEmpty())
            ? new MediaServer.SignRule(line.wsKey, line.ck) : null;
        return openLocal(target, hdrs, sign, false, format, line.name);
    }

    public static Models.ResolvedMediaStream resolveSource2Candidate(JSONObject item) throws ApiException {
        String url = item.optString("playUrl", "");
        if (url.isEmpty()) throw new ApiException("这条清晰度没有播放地址");
        String format = formatOf(url);
        String lineName = item.optString("qualityName", item.optString("src", "默认线路"));
        return openLocal(url, S2_HEADERS, null, "hls".equals(format), format, lineName);
    }

    public static Models.CastSource castSourceForSource1(List<Models.PlayLine> lines, Models.PlayLine preferred) throws ApiException {
        Models.PlayLine line = preferred != null ? preferred : pickPlayableLine(lines);
        if (line == null) throw new ApiException("这些线路当前都播不了，换一条再投。");
        String target = line.kind == Models.LineKind.official ? Source1Service.resolveOfficialLine(line) : line.url;
        String format = formatOf(target);
        if (line.kind == Models.LineKind.m3u8 || line.kind == Models.LineKind.official) {
            Models.CastSource cs = new Models.CastSource();
            cs.url = target;
            cs.format = format;
            cs.direct = true;
            cs.lineName = line.name;
            cs.dispose = () -> {};
            return cs;
        }
        Map<String, String> hdrs = line.kind == Models.LineKind.signed ? S1_SIGNED_HEADERS : S1_HEADERS;
        MediaServer.SignRule sign = (line.kind == Models.LineKind.signed && line.wsKey != null && !line.wsKey.isEmpty())
            ? new MediaServer.SignRule(line.wsKey, line.ck) : null;
        return openCastProxy(target, hdrs, sign, false, format, line.name);
    }

    public static Models.CastSource castSourceForSource2Candidate(JSONObject item) throws ApiException {
        String url = item.optString("playUrl", "");
        if (url.isEmpty()) throw new ApiException("这条清晰度没有播放地址");
        String format = formatOf(url);
        String lineName = item.optString("qualityName", item.optString("src", "默认线路"));
        return openCastProxy(url, S2_HEADERS, null, "hls".equals(format), format, lineName);
    }

    private static Models.CastSource openCastProxy(String target, Map<String, String> headers, MediaServer.SignRule sign, boolean inheritQuery, String format, String lineName) throws ApiException {
        String lan = MediaServer.lanAddress();
        if (lan.isEmpty()) {
            throw new ApiException("这台设备现在没有局域网地址（请连接 Wi-Fi 后重试）");
        }
        try {
            String sid = MediaServer.getInstance().openSession(headers, sign, inheritQuery, true);
            Models.CastSource cs = new Models.CastSource();
            cs.url = MediaServer.getInstance().streamUrl(sid, target, format, true);
            cs.format = format;
            cs.direct = false;
            cs.lineName = lineName;
            cs.dispose = () -> MediaServer.getInstance().closeSession(sid);
            return cs;
        } catch (Exception e) {
            throw new ApiException("启动投屏转发失败：" + e.getMessage());
        }
    }
}
