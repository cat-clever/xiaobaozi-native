package com.baozivideo.api.source1;

import com.baozivideo.api.ApiException;
import com.baozivideo.utils.CryptoUtils;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public final class Source1Crypto {
    private static final Pattern RE_BASE64 = Pattern.compile("^[A-Za-z0-9+/=\\s]+$");

    private Source1Crypto() {}

    public static Map<String, String> buildHeaders(Source1Constants.MahaoEntry entry, String deviceId, String token, long curTimeMs) {
        String raw = Source1Constants.SIGN_SALT + deviceId + curTimeMs;
        Map<String, String> headers = new HashMap<>();
        headers.put("app_id", entry.appId);
        headers.put("package_name", entry.packageName);
        headers.put("version", entry.version);
        headers.put("sys_platform", Source1Constants.SYS_PLATFORM);
        headers.put("mob_mfr", "xiaomi");
        headers.put("mobmodel", "MI 9");
        headers.put("sysrelease", "10");
        headers.put("device_id", deviceId);
        headers.put("channel_code", entry.channelCode);
        headers.put("cur_time", String.valueOf(curTimeMs));
        headers.put("token", token == null ? "" : token);
        headers.put("sign", CryptoUtils.md5HexUpper(raw));
        headers.put("User-Agent", Source1Constants.API_UA);
        return headers;
    }

    public static JSONObject decodeBody(String text) throws ApiException {
        String body = text == null ? "" : text.trim();
        if (body.isEmpty()) throw new ApiException("空响应", true);

        if (body.startsWith("{")) {
            try {
                return new JSONObject(body);
            } catch (Exception ignored) {}
        }

        if (!RE_BASE64.matcher(body).matches()) {
            throw new ApiException(body.substring(0, Math.min(120, body.length())), true);
        }

        try {
            byte[] raw = CryptoUtils.base64DecodeFlexible(body);
            byte[] plainBytes = CryptoUtils.aesCbcDecrypt(
                Source1Constants.AES_KEY.getBytes(StandardCharsets.UTF_8),
                Source1Constants.AES_IV.getBytes(StandardCharsets.UTF_8),
                raw
            );
            String plain = new String(plainBytes, StandardCharsets.UTF_8);
            return new JSONObject(plain);
        } catch (Exception e) {
            throw new ApiException("解密失败: " + e.getMessage());
        }
    }

    public static Object unwrap(JSONObject data) throws ApiException {
        int code = data.optInt("code", 0);
        if (code != Source1Constants.SUCCESS_CODE) {
            throw new ApiException("接口返回 code=" + code + " message=" + data.optString("message", ""));
        }
        Object res = data.opt("result");
        if (res == null || res == JSONObject.NULL) res = data.opt("data");
        return res == null ? new JSONObject() : res;
    }

    public static String parseCk(JSONObject bean) {
        if (bean == null) return "";
        String ck = bean.optString("ck", "");
        if (ck.isEmpty()) return "";
        try {
            return new String(CryptoUtils.base64DecodeFlexible(ck), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
