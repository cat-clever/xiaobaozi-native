package com.baozivideo.api.source2;

import com.baozivideo.api.ApiException;
import com.baozivideo.api.HttpClient;
import com.baozivideo.model.Models;
import com.baozivideo.utils.CryptoUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Source2Client {
    public final String deviceUid;
    public final boolean randomUid;

    public Source2Client(String deviceUid, boolean randomUid) {
        this.deviceUid = (deviceUid == null || deviceUid.isEmpty()) ? Source2Constants.DEFAULT_DEVICE_UID : deviceUid;
        this.randomUid = randomUid;
    }

    public String pickUid() {
        return randomUid ? Source2Crypto.randUid(Source2Constants.RAND_CHARS, 20) : deviceUid;
    }

    public String udidOf(String uid) {
        return CryptoUtils.md5Hex(uid);
    }

    public Map<String, String> headers(String uid) {
        Map<String, String> h = new HashMap<>();
        h.put("vc", Source2Constants.VC);
        h.put("vn", Source2Constants.VN);
        h.put("ch", Source2Constants.CH);
        h.put("app", Source2Constants.APP);
        h.put("User-Agent", Source2Constants.UA);
        h.put("said", Source2Constants.SAID);
        h.put("auth-token", "");
        h.put("auth-uid", "");
        h.put("sign", Source2Crypto.makeSignHeader(uid));
        h.put("uk", Source2Crypto.makeUk(uid));
        return h;
    }

    public static String stripHtml(String s) {
        if (s == null || "null".equals(s)) return "";
        return s.replaceAll("<[^>]*>", "").trim();
    }

    public static Models.VodItem seriesBrief(JSONObject s) {
        JSONObject image = s.optJSONObject("image");
        JSONObject cate = s.optJSONObject("relatedCateInfo");
        long published = s.optLong("publishTime", 0);
        String yearStr = "";
        if (published > 0) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(published);
            yearStr = String.valueOf(cal.get(Calendar.YEAR));
        } else {
            yearStr = s.optString("year", "");
        }

        Models.VodItem item = new Models.VodItem();
        item.id = "s2_" + s.optString("sid", "");
        item.sourceId = Models.SourceId.s2;
        item.title = s.optString("name", s.optString("title", ""));
        item.coverUrl = image != null ? image.optString("thumb", image.optString("poster", "")) : "";
        item.rating = s.optString("score", "");
        item.category = cate != null ? cate.optString("name", "") : "";
        item.year = yearStr;
        item.area = s.optString("area", "");
        item.remarks = s.optString("conerMemo", s.optString("detailMemo", s.optString("memo", "")));
        item.intro = stripHtml(s.optString("shorthand", s.optString("intro", "")));
        return item;
    }

    public static class RawResp {
        public final JSONObject json;
        public final String uid;
        public RawResp(JSONObject json, String uid) {
            this.json = json;
            this.uid = uid;
        }
    }

    public RawResp get(String path, Map<String, Object> params, String overrideUid) throws ApiException {
        String u = overrideUid != null ? overrideUid : pickUid();
        StringBuilder urlSb = new StringBuilder(Source2Constants.BASE).append("/").append(path.replaceAll("^/+", ""));
        if (params != null && !params.isEmpty()) {
            boolean first = true;
            for (Map.Entry<String, Object> e : params.entrySet()) {
                if (e.getValue() == null) continue;
                String val = String.valueOf(e.getValue());
                if (val.isEmpty()) continue;
                urlSb.append(first ? "?" : "&");
                first = false;
                try {
                    urlSb.append(URLEncoder.encode(e.getKey(), "UTF-8")).append("=").append(URLEncoder.encode(val, "UTF-8"));
                } catch (Exception ignored) {}
            }
        }
        HttpClient.Response resp = HttpClient.get(urlSb.toString(), headers(u), 15000);
        if (resp.status < 200 || resp.status >= 300) throw new ApiException("上游 HTTP " + resp.status);
        if (resp.text.isEmpty()) throw new ApiException("上游返回空");
        try {
            return new RawResp(new JSONObject(resp.text), u);
        } catch (Exception e) {
            throw new ApiException("上游返回的不是 JSON");
        }
    }

    public RawResp post(String path, JSONObject payload, String overrideUid) throws ApiException {
        String u = overrideUid != null ? overrideUid : pickUid();
        String url = Source2Constants.BASE + "/" + path.replaceAll("^/+", "");
        Map<String, String> h = headers(u);
        h.put("Content-Type", "application/json");
        HttpClient.Response resp = HttpClient.post(url, h, payload.toString().getBytes(StandardCharsets.UTF_8), 15000);
        if (resp.status < 200 || resp.status >= 300) throw new ApiException("上游 HTTP " + resp.status);
        try {
            return new RawResp(new JSONObject(resp.text), u);
        } catch (Exception e) {
            throw new ApiException("上游返回的不是 JSON");
        }
    }

    public JSONObject getDecrypted(String path, Map<String, Object> params) throws ApiException {
        RawResp r = get(path, params, null);
        String dataStr = r.json.optString("data", "");
        if (!dataStr.isEmpty()) {
            Long ts = r.json.has("ts") && !r.json.isNull("ts") ? r.json.optLong("ts") : null;
            try {
                String plain = Source2Crypto.decrypt(dataStr, r.uid, ts);
                return new JSONObject(plain);
            } catch (Exception ignored) {}
        }
        return r.json;
    }

    public List<Models.HomeBlock> recommendBlocks(int page) throws ApiException {
        Map<String, Object> p = new HashMap<>();
        p.put("page", page);
        JSONObject d = getDecrypted("api/index/recommend_v5", p);
        JSONArray blocks = d.optJSONArray("mediaBlocks");
        List<Models.HomeBlock> out = new ArrayList<>();
        if (blocks == null) return out;
        for (int i = 0; i < blocks.length(); i++) {
            JSONObject blk = blocks.optJSONObject(i);
            if (blk == null) continue;
            JSONArray series = blk.optJSONArray("seriesList");
            List<Models.VodItem> items = new ArrayList<>();
            if (series != null) {
                for (int j = 0; j < series.length(); j++) {
                    JSONObject s = series.optJSONObject(j);
                    if (s != null && s.has("sid") && !s.isNull("sid")) items.add(seriesBrief(s));
                }
            }
            if (!items.isEmpty()) out.add(new Models.HomeBlock(blk.optString("title", ""), items));
        }
        return out;
    }

    public static class CateResult {
        public static class Group {
            public String label;
            public int value;
            public List<Models.FilterOption> cates = new ArrayList<>();
        }
        public List<Group> groups = new ArrayList<>();
        public List<Models.FilterOption> sorts = new ArrayList<>();
        public List<Models.FilterOption> years = new ArrayList<>();
        public List<Models.VodItem> items = new ArrayList<>();
    }

    private List<Models.FilterOption> toOpts(JSONArray arr) {
        List<Models.FilterOption> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String name = o.optString("name", "");
            if (!name.isEmpty()) out.add(new Models.FilterOption(name, o.optString("value", "")));
        }
        return out;
    }

    public CateResult catePage(Integer stype, Integer cid, String year, String sort, int page) throws ApiException {
        Map<String, Object> p = new HashMap<>();
        p.put("page", page);
        if (stype != null) p.put("stype", stype);
        if (cid != null) p.put("cid", cid);
        if (year != null && !year.isEmpty()) p.put("year", year);
        if (sort != null && !sort.isEmpty()) p.put("sort", sort);

        JSONObject d = getDecrypted("api/series2/arrange/cate", p);
        CateResult res = new CateResult();
        JSONArray groups = d.optJSONArray("groups");
        if (groups != null) {
            for (int i = 0; i < groups.length(); i++) {
                JSONObject g = groups.optJSONObject(i);
                if (g == null) continue;
                CateResult.Group grp = new CateResult.Group();
                grp.label = g.optString("name", "");
                grp.value = g.optInt("stype", 0);
                grp.cates = toOpts(g.optJSONArray("cates"));
                res.groups.add(grp);
            }
        }
        res.sorts = toOpts(d.optJSONArray("sorts"));
        res.years = toOpts(d.optJSONArray("years"));
        JSONArray list = d.optJSONArray("seriesList");
        if (list != null) {
            for (int i = 0; i < list.length(); i++) {
                JSONObject x = list.optJSONObject(i);
                if (x != null && x.has("sid") && !x.isNull("sid")) res.items.add(seriesBrief(x));
            }
        }
        return res;
    }

    public List<Models.VodItem> searchPage(String keyword) throws ApiException {
        Map<String, Object> p = new HashMap<>();
        p.put("k", keyword);
        p.put("srefer", "search");
        JSONObject d = getDecrypted("api/search/s4", p);
        JSONArray list = d.optJSONArray("seriesList");
        List<Models.VodItem> out = new ArrayList<>();
        if (list != null) {
            for (int i = 0; i < list.length(); i++) {
                JSONObject x = list.optJSONObject(i);
                if (x != null && x.has("sid") && !x.isNull("sid")) out.add(seriesBrief(x));
            }
        }
        return out;
    }

    public Models.VodDetail detailPage(String sid) throws ApiException {
        Map<String, Object> p = new HashMap<>();
        p.put("sid", sid);
        JSONObject d = getDecrypted("api/series2/detail/normal", p);
        JSONObject s = d.optJSONObject("series");
        if (s == null) s = new JSONObject();
        JSONObject img = s.optJSONObject("image");

        Models.VodDetail detail = new Models.VodDetail();
        detail.vodId = s.optString("sid", sid);
        detail.sourceId = Models.SourceId.s2;
        detail.name = s.optString("name", "");
        detail.pic = img != null ? img.optString("thumb", img.optString("poster", "")) : "";
        detail.actor = s.optString("crew", "");
        detail.director = "";
        detail.year = "";
        detail.area = "";
        detail.blurb = stripHtml(s.optString("intro", ""));

        JSONArray playItems = d.optJSONArray("playItems");
        if (playItems != null) {
            int idx = 0;
            for (int i = 0; i < playItems.length(); i++) {
                JSONObject it = playItems.optJSONObject(i);
                if (it == null || !it.has("pid") || it.isNull("pid")) continue;
                Models.Episode ep = new Models.Episode();
                ep.episodeIndex = idx;
                ep.title = CryptoUtils.episodeTitle(it.opt("title"), idx);
                ep.pid = it.optString("pid", "");
                ep.duration = it.optString("duration", "");
                detail.episodes.add(ep);
                idx++;
            }
        }

        JSONArray rawQ = s.optJSONArray("scopeQualities");
        if (rawQ != null) {
            List<Models.QualityOption> free = new ArrayList<>();
            List<Models.QualityOption> all = new ArrayList<>();
            for (int i = 0; i < rawQ.length(); i++) {
                JSONObject q = rawQ.optJSONObject(i);
                if (q == null) continue;
                String label = (q.optString("name", "") + " " + q.optString("resolution", "")).trim();
                if (label.isEmpty()) continue;
                Models.QualityOption opt = new Models.QualityOption(label, q.optInt("value", 0));
                all.add(opt);
                if (q.optInt("vtype", 0) != 1) free.add(opt);
            }
            detail.qualities = free.isEmpty() ? all : free;
        }

        JSONArray buckets = d.optJSONArray("buckets");
        if (buckets != null) {
            for (int i = 0; i < buckets.length(); i++) {
                JSONObject b = buckets.optJSONObject(i);
                if (b != null && b.has("sid") && !b.isNull("sid")) {
                    detail.seasons.add(new Models.SeasonOption(b.optString("sid", ""), b.optString("title", "")));
                }
            }
        }

        return detail;
    }

    public String pickSource(String pid) throws ApiException {
        Map<String, Object> p = new HashMap<>();
        p.put("pid", pid);
        p.put("refer", "");
        JSONObject d = getDecrypted("api/series2/episode/detail", p);
        JSONObject pi = d.optJSONObject("playItem");
        if (pi != null) {
            JSONArray sources = pi.optJSONArray("sources");
            if (sources != null) {
                for (int i = 0; i < sources.length(); i++) {
                    JSONObject s = sources.optJSONObject(i);
                    if (s != null && s.has("scid") && !s.isNull("scid")) {
                        String scid = s.optString("scid", "");
                        if (!scid.isEmpty()) return scid;
                    }
                }
            }
        }
        return null;
    }

    public RawResp resolveV4(String pid, String scid, String overrideUid, Integer sq, String ttk) throws ApiException {
        String t = String.valueOf(System.currentTimeMillis() / 1000);
        String u = overrideUid != null ? overrideUid : pickUid();

        Map<String, String> kv = new HashMap<>();
        kv.put("t", t);
        kv.put("dt", "android");
        kv.put("version", Source2Constants.VN);
        kv.put("uuid", Source2Crypto.randUid("0123456789abcdef", 32));
        kv.put("udid", udidOf(u));
        kv.put("pid", pid);
        kv.put("scid", scid == null ? "" : scid);
        kv.put("re", "0");
        if (sq != null) kv.put("sq", String.valueOf(sq));
        if (ttk != null && !ttk.isEmpty()) kv.put("ttk", ttk);

        Map<String, Object> params = new HashMap<>();
        for (Map.Entry<String, String> e : kv.entrySet()) {
            if (!"udid".equals(e.getKey())) params.put(e.getKey(), e.getValue());
        }
        params.put("sign", Source2Crypto.makeSign(kv));
        return get("api/series/rslvV4", params, u);
    }

    public String rewardToken(String pid, String uid) {
        try {
            Map<String, Object> p = new HashMap<>();
            p.put("scene", Source2Constants.SCENE_REWARD);
            RawResp r1 = get("api/carp/reward/v2", p, uid);
            String d1 = r1.json.optString("data", "");
            if (d1.isEmpty()) return null;
            Long ts1 = r1.json.has("ts") && !r1.json.isNull("ts") ? r1.json.optLong("ts") : null;
            JSONObject obj1 = new JSONObject(Source2Crypto.decrypt(d1, uid, ts1));
            String traceId = obj1.optString("traceId", "");
            if (traceId.isEmpty()) return null;

            JSONObject bodyObj = new JSONObject();
            bodyObj.put("pid", pid);
            bodyObj.put("scene", Source2Constants.SCENE_REWARD);
            bodyObj.put("traceId", traceId);
            JSONObject postPayload = new JSONObject();
            postPayload.put("data", Source2Crypto.aesD(bodyObj.toString(), uid));

            RawResp r2 = post("api/carp/reward/rp/v2", postPayload, uid);
            String d2 = r2.json.optString("data", "");
            if (d2.isEmpty()) return null;
            Long ts2 = r2.json.has("ts") && !r2.json.isNull("ts") ? r2.json.optLong("ts") : null;
            JSONObject obj2 = new JSONObject(Source2Crypto.decrypt(d2, uid, ts2));
            JSONObject info = obj2.optJSONObject("rewardTokenInfo");
            return info != null ? info.optString("token", null) : null;
        } catch (Exception e) {
            return null;
        }
    }

    public List<JSONObject> playUrl(String pid, String scid, Integer sq) throws ApiException {
        String source = (scid != null && !scid.isEmpty()) ? scid : pickSource(pid);
        if (source == null || source.isEmpty()) return Collections.emptyList();

        RawResp resp = resolveV4(pid, source, null, sq, null);
        if (resp.json.optInt("rescode", 0) == 10401) {
            String uid = pickUid();
            String ttk = rewardToken(pid, uid);
            if (ttk != null && !ttk.isEmpty()) {
                resp = resolveV4(pid, source, uid, sq, ttk);
            }
        }

        List<JSONObject> out = new ArrayList<>();
        JSONArray datas = resp.json.optJSONArray("datas");
        if (datas != null) {
            for (int i = 0; i < datas.length(); i++) {
                JSONObject item = datas.optJSONObject(i);
                if (item == null) continue;
                String dataB64 = item.optString("data", "");
                if (dataB64.isEmpty()) continue;
                try {
                    JSONObject info = new JSONObject(Source2Crypto.decrypt(dataB64, resp.uid, null));
                    info.put("qualityName", resp.json.optString("qualityName", ""));
                    out.add(info);
                } catch (Exception ignored) {}
            }
        }
        return out;
    }

    public static boolean hasHttpsPlay(List<JSONObject> items) {
        for (JSONObject it : items) {
            if (it.optString("playUrl", "").toLowerCase().startsWith("https://")) return true;
        }
        return false;
    }

    public static List<String> sampleUids(int count, String exclude) {
        List<String> pool = new ArrayList<>();
        for (String u : Source2Constants.GOOD_UIDS) {
            if (!u.equals(exclude)) pool.add(u);
        }
        Collections.shuffle(pool);
        return pool.subList(0, Math.min(count, pool.size()));
    }
}
