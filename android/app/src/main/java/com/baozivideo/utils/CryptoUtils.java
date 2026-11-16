package com.baozivideo.utils;

import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class CryptoUtils {
    private CryptoUtils() {}

    public static String md5Hex(String input) {
        return md5Hex(input.getBytes(StandardCharsets.UTF_8));
    }

    public static String md5Hex(byte[] input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String md5HexUpper(String input) {
        return md5Hex(input).toUpperCase();
    }

    public static byte[] aesCbcDecrypt(byte[] key, byte[] iv, byte[] data) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        return cipher.doFinal(data);
    }

    public static byte[] aesCbcEncrypt(byte[] key, byte[] iv, byte[] data) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        return cipher.doFinal(data);
    }

    public static byte[] base64DecodeFlexible(String input) {
        String s = input.trim().replace('-', '+').replace('_', '/');
        int rem = s.length() % 4;
        if (rem != 0) {
            s += "====".substring(rem);
        }
        return Base64.decode(s, Base64.DEFAULT);
    }

    public static String base64UrlEncode(byte[] data) {
        return Base64.encodeToString(data, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    public static String episodeTitle(Object raw, int index) {
        if (raw == null) return String.valueOf(index + 1);
        String text = String.valueOf(raw).trim();
        if (text.isEmpty() || "null".equals(text)) return String.valueOf(index + 1);
        if (text.matches("^\\d+$")) {
            try {
                return String.valueOf(Integer.parseInt(text));
            } catch (NumberFormatException ignored) {}
        }
        return text;
    }
}
