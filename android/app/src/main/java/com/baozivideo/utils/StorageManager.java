package com.baozivideo.utils;

import android.content.Context;
import android.content.SharedPreferences;
import com.baozivideo.api.source2.Source2Constants;
import com.baozivideo.api.source2.Source2Crypto;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class StorageManager {
    private static final String PREF_NAME = "baozivideo_prefs";
    private static final String KEY_DEVICE_UID = "deviceUid";
    private static final String KEY_UID_SLOW_HITS = "deviceUidSlowHits";
    private static final String KEY_S1_DEVICE_ID = "source1DeviceId";
    private static final String KEY_SEARCH_HISTORY = "searchHistory";
    private static final String KEY_WATCH_HISTORY = "watchHistory";
    private static final String KEY_THEME_MODE = "themeMode";
    private static final String KEY_GLASS = "themeGlass";
    private static final int SLOW_HITS_BEFORE_ROTATE = 2;
    private static final Pattern UID_RE = Pattern.compile("^[0-9A-Za-z]{20}$");
    private static final Pattern S1_DEV_RE = Pattern.compile("^[0-9a-f]{16}$");

    private static StorageManager instance;
    private final SharedPreferences prefs;

    private StorageManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static void init(Context context) {
        if (instance == null) {
            instance = new StorageManager(context);
        }
    }

    public static StorageManager getInstance() {
        return instance;
    }

    public String getSource1DeviceId(int mahao, String fallbackGenerator) {
        String key = KEY_S1_DEVICE_ID + "_" + mahao;
        String saved = prefs.getString(key, null);
        if (saved != null && S1_DEV_RE.matcher(saved).matches()) {
            return saved;
        }
        prefs.edit().putString(key, fallbackGenerator).apply();
        return fallbackGenerator;
    }

    public String getDeviceUid() {
        String saved = prefs.getString(KEY_DEVICE_UID, null);
        int slowHits = prefs.getInt(KEY_UID_SLOW_HITS, 0);
        if (saved != null && UID_RE.matcher(saved).matches() && slowHits < SLOW_HITS_BEFORE_ROTATE) {
            return saved;
        }
        String fresh = Source2Crypto.randUid(Source2Constants.RAND_CHARS, 20);
        prefs.edit().putString(KEY_DEVICE_UID, fresh).putInt(KEY_UID_SLOW_HITS, 0).apply();
        return fresh;
    }

    public void notePlayNode(boolean hasHttps) {
        if (hasHttps) {
            prefs.edit().putInt(KEY_UID_SLOW_HITS, 0).apply();
        } else {
            int hits = prefs.getInt(KEY_UID_SLOW_HITS, 0) + 1;
            prefs.edit().putInt(KEY_UID_SLOW_HITS, hits).apply();
        }
    }

    public String getThemeMode() {
        return prefs.getString(KEY_THEME_MODE, "system");
    }

    public void saveThemeMode(String mode) {
        prefs.edit().putString(KEY_THEME_MODE, mode).apply();
    }

    public boolean getGlassEnabled() {
        return prefs.getBoolean(KEY_GLASS, true);
    }

    public void saveGlassEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_GLASS, enabled).apply();
    }

    public List<String> getSearchHistory() {
        List<String> out = new ArrayList<>();
        try {
            String raw = prefs.getString(KEY_SEARCH_HISTORY, "[]");
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                out.add(arr.getString(i));
            }
        } catch (Exception ignored) {}
        return out;
    }

    public List<String> pushSearchHistory(String keyword) {
        List<String> list = getSearchHistory();
        List<String> next = new ArrayList<>();
        next.add(keyword);
        for (String item : list) {
            if (!item.equals(keyword) && next.size() < 20) {
                next.add(item);
            }
        }
        JSONArray arr = new JSONArray(next);
        prefs.edit().putString(KEY_SEARCH_HISTORY, arr.toString()).apply();
        return next;
    }

    public void clearSearchHistory() {
        prefs.edit().remove(KEY_SEARCH_HISTORY).apply();
    }

    public void saveWatchRecord(String id, String sourceId, String title, String cover, int epIdx, double posSec) {
        try {
            String raw = prefs.getString(KEY_WATCH_HISTORY, "[]");
            JSONArray arr = new JSONArray(raw);
            JSONArray next = new JSONArray();
            JSONObject rec = new JSONObject();
            rec.put("id", id);
            rec.put("sourceId", sourceId);
            rec.put("title", title);
            rec.put("cover", cover);
            rec.put("episodeIndex", epIdx);
            rec.put("positionSec", posSec);
            rec.put("updatedAt", System.currentTimeMillis());
            next.put(rec);
            for (int i = 0; i < arr.length() && next.length() < 100; i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null && !id.equals(o.optString("id"))) {
                    next.put(o);
                }
            }
            prefs.edit().putString(KEY_WATCH_HISTORY, next.toString()).apply();
        } catch (Exception ignored) {}
    }
}
