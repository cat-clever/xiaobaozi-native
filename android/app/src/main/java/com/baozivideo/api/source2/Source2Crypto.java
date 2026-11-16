package com.baozivideo.api.source2;

import android.util.Base64;
import com.baozivideo.utils.CryptoUtils;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class Source2Crypto {
    private Source2Crypto() {}

    public static byte[][] deriveKeyIv(String deviceUid, Long ts, String material) {
        String src = ts == null ? deviceUid : deviceUid + ts;
        String did = CryptoUtils.md5Hex(src);
        String h = CryptoUtils.md5Hex(did + material);
        byte[] key = h.substring(0, 16).getBytes(StandardCharsets.UTF_8);
        byte[] iv = h.substring(16, 32).getBytes(StandardCharsets.UTF_8);
        return new byte[][]{key, iv};
    }

    public static String decrypt(String dataB64, String deviceUid, Long ts) throws Exception {
        byte[][] kv = deriveKeyIv(deviceUid, ts, Source2Constants.MATERIAL_DEC);
        byte[] raw = Base64.decode(dataB64, Base64.DEFAULT);
        byte[] plain = CryptoUtils.aesCbcDecrypt(kv[0], kv[1], raw);
        return new String(plain, StandardCharsets.UTF_8);
    }

    public static String makeSign(Map<String, String> kv) {
        List<String> keys = new ArrayList<>(kv.keySet());
        Collections.sort(keys, Collections.reverseOrder());
        StringBuilder sb = new StringBuilder();
        for (String k : keys) {
            sb.append("&").append(k).append("=").append(kv.get(k));
        }
        return CryptoUtils.md5Hex(sb.toString() + "&" + Source2Constants.MATERIAL_SIGN);
    }

    public static String makeUk(String deviceUid) {
        try {
            byte[] enc = CryptoUtils.aesCbcEncrypt(
                Source2Constants.UK_KEY.getBytes(StandardCharsets.UTF_8),
                Source2Constants.UK_IV.getBytes(StandardCharsets.UTF_8),
                deviceUid.getBytes(StandardCharsets.UTF_8)
            );
            return Base64.encodeToString(enc, Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }

    public static String aesD(String text, String deviceUid) {
        try {
            String h = CryptoUtils.md5Hex(deviceUid);
            byte[] enc = CryptoUtils.aesCbcEncrypt(
                h.substring(0, 16).getBytes(StandardCharsets.UTF_8),
                h.substring(16, 32).getBytes(StandardCharsets.UTF_8),
                text.getBytes(StandardCharsets.UTF_8)
            );
            return Base64.encodeToString(enc, Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }

    public static String makeSignHeader(String deviceUid) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("uid", deviceUid);
            obj.put("ts", System.currentTimeMillis());
            return aesD(obj.toString(), deviceUid);
        } catch (Exception e) {
            return "";
        }
    }

    public static String randUid(String chars, int length) {
        Random r = new Random();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(r.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
