package com.baozivideo.api.source1;

import java.util.HashMap;
import java.util.Map;

public final class Source1Constants {
    public static final int MAHAO_DIEYING = 0;
    public static final int MAHAO_TIANJI = 1;
    public static final int MAHAO_CHSP = 2;
    public static final int MAHAO_CHSP_OLD = 3;

    public static class MahaoEntry {
        public final String label;
        public final String appId;
        public final String packageName;
        public final String version;
        public final String channelCode;
        public final String wsKey;
        public final String secDomain;
        public final boolean isOldApi;

        public MahaoEntry(String label, String appId, String packageName, String version, String channelCode, String wsKey, String secDomain, boolean isOldApi) {
            this.label = label;
            this.appId = appId;
            this.packageName = packageName;
            this.version = version;
            this.channelCode = channelCode;
            this.wsKey = wsKey;
            this.secDomain = secDomain;
            this.isOldApi = isOldApi;
        }
    }

    public static final Map<Integer, MahaoEntry> MAHAO_TABLE = new HashMap<>();
    static {
        MAHAO_TABLE.put(MAHAO_DIEYING, new MahaoEntry("叠影视频", "dieyingshipin", "com.pathrecall.gj", "50000", "dysp_sp01", "6Jh7hrLCXBrutmJEYpMpvbU3LDEHwUZY", "", false));
        MAHAO_TABLE.put(MAHAO_TIANJI, new MahaoEntry("天际视频", "xingkongshipin", "com.lineone.connecter", "50100", "xk_sp01", "vT1RQRz8YzlzTgN26pIXNJ7Mi65juwSP", "", false));
        MAHAO_TABLE.put(MAHAO_CHSP, new MahaoEntry("彩虹视频（新接口）", "caihongshipin", "com.hjmore.hdcre", "43000", "sp01", "af2tu1aCoXwDIUpAFBv7i8sDhpi3cSrr", "", false));
        MAHAO_TABLE.put(MAHAO_CHSP_OLD, new MahaoEntry("彩虹视频（老接口）", "caihongshipin", "com.hjmore.hdcre", "43000", "sp01", "af2tu1aCoXwDIUpAFBv7i8sDhpi3cSrr", "", true));
    }

    public static final int[] AUTO_CHAIN = {MAHAO_CHSP, MAHAO_CHSP_OLD, MAHAO_TIANJI, MAHAO_DIEYING};
    public static final int[] PLAY_CHAIN = {MAHAO_CHSP, MAHAO_CHSP_OLD, MAHAO_TIANJI, MAHAO_DIEYING};

    public static final String TIANJI_DEVICE_ID = "96ab557f6826febf";
    public static final String TIANJI_TOKEN = "gAAAAABqyeanWUJ6JutPAslzwyrJdqtIZthjnfZrAfgFmbZhOdDlJYtI4XCW3TfbAFIRrDoecPmA32TWPizIq5tewpE0QHRYpGZCUPGL0HV14wFuwQo-4qf8ON1pMBiFF3Odq7p2oyw7K5szpSE-nwiGUYU5_5zA5bb6p8Q_23gYcanLA1XXLJp1RX8Iz645_e_aaRibg25KSTyJKOErmhgiiAJ-nlp6TA==";

    public static final String DEFAULT_BASE = "https://u.gslock.com";
    public static final String[] FALLBACK_BASES = {
        "https://u.yyxdmn.com",
        "https://u.jsyysm.com",
        "https://u.skzsz.com",
        "https://u.uccao.cn"
    };

    public static final String SIGN_SALT = "zD9[bM4~sF4~uY2)";
    public static final String AES_KEY = "aZ9$kU5%qI7=yC2=zH2#gM0@pX7^wF3a";
    public static final String AES_IV = "hY2&tN3]kF7,dL7=";
    public static final String SYS_PLATFORM = "2";
    public static final int SUCCESS_CODE = 10000;
    public static final int API_TIMEOUT_MS = 12000;

    public static final String MEDIA_UA = "Mozi";
    public static final String IMAGE_BASE = "http://wim.xzjykj.com/";
    public static final String IMAGE_UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    public static final String API_UA = "okhttp/4.9.0";
    public static final String BADCI = "723baba511fd6e9378e4f9eec2a97ca3";
    public static final String SEC_DOMAIN_FALLBACK = "http://chw.ycblhs.com";
    public static final String PLACEHOLDER_HOST = "ydsp.skzsz.com";

    public static final int RETRIES = 2;
    public static final int LINE_OFFICIAL = 1;
    public static final int LINE_M3U8 = 2;
    public static final int LINE_BACKUP = 3;
    public static final int LINE_MIRROR = 4;
    public static final int LINE_SIGNED = 5;

    public static final Map<String, String> API_NEW = new HashMap<>();
    public static final Map<String, String> API_OLD = new HashMap<>();
    static {
        API_NEW.put("init", "/api/new_public/init_v2");
        API_NEW.put("types", "/api/new_type/list_v2");
        API_NEW.put("screen", "/api/new_search/screen_v2");
        API_NEW.put("search", "/api/new_search/result_v2");
        API_NEW.put("recommend", "/api/new_search/recommend_v2");
        API_NEW.put("detail", "/api/new_video/result_v2");
        API_NEW.put("collection", "/api/new_video/collection_v2");
        API_NEW.put("collection_list", "/api/new_video/collection_list_v2");
        API_NEW.put("hot_search", "/api/new_search/hot_search_v2");

        API_OLD.put("init", "/api/public/init");
        API_OLD.put("types", "/api/type/get_list");
        API_OLD.put("screen", "/api/search/screen");
        API_OLD.put("search", "/api/search/result");
        API_OLD.put("recommend", "/api/search/recommend");
        API_OLD.put("detail", "/api/video/result");
        API_OLD.put("collection", "/api/video/collection");
        API_OLD.put("collection_list", "/api/video/collection_list");
        API_OLD.put("hot_search", "/api/search/hot_search");
    }

    private Source1Constants() {}
}
