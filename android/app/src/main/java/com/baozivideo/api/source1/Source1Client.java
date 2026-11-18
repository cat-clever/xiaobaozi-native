package com.baozivideo.api.source1;

import com.baozivideo.api.ApiException;
import com.baozivideo.api.HttpClient;
import com.baozivideo.utils.StorageManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class Source1Client {
    public final int mahao;
    public final Source1Constants.MahaoEntry entry;
    public final String wsKey;
    public String deviceId;
    public String token;
    public String baseUrl;
    public String secDomain = Source1Constants.SEC_DOMAIN_FALLBACK;

    private static volatile String memoBaseUrl = "";

    public static String randomDeviceId() {
        String hex = "0123456789abcdef";
        Random r = new Random();
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) sb.append(hex.charAt(r.nextInt(16)));
        return sb.toString();
    }

    public Source1Client(int mahao) {
        this.mahao = mahao;
        this.entry = Source1Constants.MAHAO_TABLE.get(mahao);
        this.wsKey = entry.wsKey;
        String rawBase = memoBaseUrl.isEmpty() ? Source1Constants.DEFAULT_BASE : memoBaseUrl;
        this.baseUrl = rawBase.replaceAll("/+$", "");

        if (mahao == Source1Constants.MAHAO_TIANJI) {
            this.deviceId = Source1Constants.TIANJI_DEVICE_ID;
            this.token = Source1Constants.TIANJI_TOKEN;
        } else {
            StorageManager sm = StorageManager.getInstance();
            this.deviceId = sm != null ? sm.getSource1DeviceId(mahao, randomDeviceId()) : randomDeviceId();
            this.token = "";
        }
    }

    public String api(String key) {
        return entry.isOldApi ? Source1Constants.API_OLD.get(key) : Source1Constants.API_NEW.get(key);
    }

    public List<String> candidates() {
        List<String> out = new ArrayList<>();
        out.add(baseUrl);
        String def = Source1Constants.DEFAULT_BASE.replaceAll("/+$", "");
        if (!out.contains(def)) out.add(def);
        for (String b : Source1Constants.FALLBACK_BASES) {
            String t = b.replaceAll("/+$", "");
            if (!out.contains(t)) out.add(t);
        }
        return out;
    }

    public JSONObject post(String path, Map<String, Object> params, String overrideBase) throws ApiException {
        String root = (overrideBase != null ? overrideBase : baseUrl).replaceAll("/+$", "");
        String url = root + path;

        StringBuilder bodySb = new StringBuilder();
        if (params != null) {
            for (Map.Entry<String, Object> e : params.entrySet()) {
                if (e.getValue() == null) continue;
                if (bodySb.length() > 0) bodySb.append("&");
                try {
                    bodySb.append(URLEncoder.encode(e.getKey(), "UTF-8"))
                          .append("=")
                          .append(URLEncoder.encode(String.valueOf(e.getValue()), "UTF-8"));
                } catch (Exception ignored) {}
            }
        }
        byte[] bodyBytes = bodySb.toString().getBytes(StandardCharsets.UTF_8);

        String last = "";
        int busy = 0;

        for (int attempt = 0; attempt < Source1Constants.RETRIES; attempt++) {
            Map<String, String> headers = Source1Crypto.buildHeaders(entry, deviceId, token, System.currentTimeMillis());
            headers.put("Content-Type", "application/x-www-form-urlencoded");

            String text;
            try {
                HttpClient.Response resp = HttpClient.post(url, headers, bodyBytes, Source1Constants.API_TIMEOUT_MS);
                text = resp.text;
            } catch (Exception e) {
                last = e.getMessage();
                if (attempt < Source1Constants.RETRIES - 1) sleepRetry();
                continue;
            }

            JSONObject data;
            try {
                data = Source1Crypto.decodeBody(text);
            } catch (ApiException e) {
                if (e.isServerBusy) {
                    busy++;
                    last = "服务端返回错误提示：" + e.getMessage();
                } else {
                    last = e.getMessage();
                }
                if (attempt < Source1Constants.RETRIES - 1) sleepRetry();
                continue;
            }

            int code = data.optInt("code", 0);
            if (code != Source1Constants.SUCCESS_CODE) {
                throw new ApiException("接口返回 code=" + code + " message=" + data.optString("message", ""));
            }
            return data;
        }

        if (busy == Source1Constants.RETRIES) throw new ApiException(last, true);
        throw new ApiException("请求失败（已重试 " + Source1Constants.RETRIES + " 次）: " + last);
    }

    private void sleepRetry() {
        try {
            Thread.sleep(3000 + (long) (Math.random() * 3000));
        } catch (InterruptedException ignored) {}
    }

    public void init() throws ApiException {
        List<String> errors = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        params.put("invited_by", "");
        params.put("is_install", "0");
        params.put("ua", "0");

        for (String base : candidates()) {
            try {
                JSONObject payload = post(api("init"), params, base);
                applyInit(payload, base);
                return;
            } catch (Exception e) {
                errors.add(base + ": " + e.getMessage());
            }
        }
        throw new ApiException("init 在所有候选域名上都失败：" + String.join(" | ", errors));
    }

    private void applyInit(JSONObject payload, String usedBase) {
        this.baseUrl = usedBase;
        memoBaseUrl = usedBase;

        JSONObject result = payload.optJSONObject("result");
        if (result == null) result = payload.optJSONObject("data");
        if (result == null) result = new JSONObject();

        JSONObject apiConf = result.optJSONObject("api_conf");
        if (apiConf != null) {
            String nextUrl = apiConf.optString("api_url2", "").trim();
            if (nextUrl.isEmpty()) nextUrl = apiConf.optString("api_url", "").trim();
            if (nextUrl.startsWith("http")) {
                this.baseUrl = nextUrl.replaceAll("/+$", "");
                memoBaseUrl = this.baseUrl;
            }
        }

        JSONObject userInfo = result.optJSONObject("user_info");
        if (userInfo != null) {
            String issued = userInfo.optString("token", "").trim();
            if (!issued.isEmpty()) this.token = issued;
        }

        JSONObject sysConf = result.optJSONObject("sys_conf");
        if (sysConf != null) {
            JSONObject p2p = sysConf.optJSONObject("p2p_config");
            if (p2p != null) {
                String sec = p2p.optString("sec_domain", "").trim();
                if (!sec.isEmpty()) this.secDomain = sec;
            }
        }
        if (!entry.secDomain.isEmpty()) this.secDomain = entry.secDomain;
    }

    public JSONObject postWithFailover(String path, Map<String, Object> params) throws ApiException {
        List<String> errors = new ArrayList<>();
        for (String base : candidates()) {
            try {
                JSONObject res = post(path, params, base);
                if (!base.equals(this.baseUrl)) {
                    this.baseUrl = base;
                    memoBaseUrl = base;
                }
                return res;
            } catch (ApiException e) {
                if (e.isServerBusy) throw e;
                errors.add(base + ": " + e.getMessage());
            }
        }
        throw new ApiException("所有候选域名都失败：" + String.join(" | ", errors));
    }

    private Object call(String path, Map<String, Object> params) throws ApiException {
        return Source1Crypto.unwrap(postWithFailover(path, params));
    }

    public JSONArray types() throws ApiException {
        Object res = call(api("types"), new HashMap<>());
        return res instanceof JSONArray ? (JSONArray) res : new JSONArray();
    }

    public JSONArray screen(int pn, int typeId, String typeValue, String tagType, String area, String year, String sort) throws ApiException {
        Map<String, Object> p = new HashMap<>();
        p.put("pn", pn);
        p.put("type_id", typeId);
        p.put("type_value", typeValue == null ? "" : typeValue);
        p.put("tag_type", tagType == null ? "" : tagType);
        p.put("area", area == null ? "" : area);
        p.put("year", year == null ? "" : year);
        p.put("sort", sort == null ? "" : sort);
        Object res = call(api("screen"), p);
        return res instanceof JSONArray ? (JSONArray) res : new JSONArray();
    }

    public JSONArray search(String keyword, int pn) throws ApiException {
        Map<String, Object> p = new HashMap<>();
        p.put("kw", keyword);
        p.put("pn", pn);
        Object res = call(api("search"), p);
        return res instanceof JSONArray ? (JSONArray) res : new JSONArray();
    }

    public JSONObject detail(int vodId) throws ApiException {
        Map<String, Object> p = new HashMap<>();
        p.put("vod_id", vodId);
        Object res = call(api("detail"), p);
        return res instanceof JSONObject ? (JSONObject) res : new JSONObject();
    }

    public JSONObject collection(int vodId, int collectionId, String vodToken, String curTime) throws ApiException {
        Map<String, Object> p = new HashMap<>();
        p.put("vod_id", vodId);
        p.put("collection_id", collectionId);
        p.put("vod_token", vodToken);
        p.put("cur_time", curTime);
        Object res = call(api("collection"), p);
        return res instanceof JSONObject ? (JSONObject) res : new JSONObject();
    }

    public JSONObject collectionList(int vodId) throws ApiException {
        Map<String, Object> p = new HashMap<>();
        p.put("vod_id", vodId);
        Object res = call(api("collection_list"), p);
        return res instanceof JSONObject ? (JSONObject) res : new JSONObject();
    }

    public JSONArray hotSearch() throws ApiException {
        Object res = call(api("hot_search"), new HashMap<>());
        return res instanceof JSONArray ? (JSONArray) res : new JSONArray();
    }

    public String resolve(String prefix, String target) throws ApiException {
        if (prefix == null || prefix.isEmpty() || target == null || target.isEmpty()) {
            throw new ApiException("缺少解析前缀或线路地址");
        }
        Map<String, String> headers = Source1Crypto.buildHeaders(entry, deviceId, token, System.currentTimeMillis());
        HttpClient.Response resp = HttpClient.get(prefix + target, headers, Source1Constants.API_TIMEOUT_MS);
        try {
            JSONObject data = new JSONObject(resp.text);
            if (data.optInt("code", 0) != 200) {
                throw new ApiException("解析失败 code=" + data.optInt("code") + " message=" + data.optString("message", ""));
            }
            String real = data.optString("url", "").trim();
            if (real.isEmpty()) throw new ApiException("解析成功但没返回地址");
            return real;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("解析返回的不是 JSON");
        }
    }
}
