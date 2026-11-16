package com.baozivideo.api.source1;

import com.baozivideo.api.ApiException;
import com.baozivideo.model.Models;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Source1Service {
    private static volatile Source1Client sharedClient;

    public static class CategoryTree {
        public static class Dim {
            public List<String> type = new ArrayList<>();
            public List<List<String>> tagType = new ArrayList<>();
            public List<String> area = new ArrayList<>();
            public List<String> year = new ArrayList<>();
            public List<String> sort = new ArrayList<>();
        }
        public List<Models.FilterOption> categories = new ArrayList<>();
        public Map<Integer, Dim> dims = new HashMap<>();
    }

    private Source1Service() {}

    public static String absolutePic(String raw) {
        if (raw == null) return "";
        String url = raw.trim();
        if (url.isEmpty() || "null".equals(url)) return "";
        if (url.toLowerCase().startsWith("http://") || url.toLowerCase().startsWith("https://")) return url;
        return Source1Constants.IMAGE_BASE + url.replaceAll("^/+", "");
    }

    public static Models.VodItem toVodItem(JSONObject raw) {
        Models.VodItem item = new Models.VodItem();
        item.id = "s1_" + raw.optString("id", "");
        item.sourceId = Models.SourceId.s1;
        item.title = raw.optString("vod_name", "");
        item.coverUrl = absolutePic(raw.optString("vod_pic", ""));
        item.rating = raw.optString("vod_douban_score", "");
        item.category = raw.optString("vod_tag", "");
        item.year = raw.optString("vod_year", "");
        item.area = raw.optString("vod_area", "");
        item.remarks = raw.optString("remarks", "");
        item.intro = raw.optString("vod_blurb", "");
        return item;
    }

    private static synchronized Source1Client getClient() throws ApiException {
        if (sharedClient != null) return sharedClient;
        List<String> errors = new ArrayList<>();
        for (int mahao : Source1Constants.AUTO_CHAIN) {
            Source1Client client = new Source1Client(mahao);
            try {
                client.init();
                sharedClient = client;
                return client;
            } catch (Exception e) {
                errors.add(client.entry.label + ": " + e.getMessage());
            }
        }
        throw new ApiException("所有接口都不可用（" + String.join("；", errors) + "）");
    }

    public static List<Models.HomeBlock> homeFeed(int page) throws ApiException {
        Source1Client client = getClient();
        JSONArray list = client.screen(page, 0, "", "", "", "", "日榜");
        List<Models.VodItem> items = new ArrayList<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject obj = list.optJSONObject(i);
            if (obj == null) continue;
            Models.VodItem item = toVodItem(obj);
            if (!item.title.trim().isEmpty()) items.add(item);
        }
        return items.isEmpty() ? Collections.emptyList() : Collections.singletonList(new Models.HomeBlock("热播推荐", items));
    }

    public static List<Models.VodItem> listByCategory(Models.CategoryQuery q) throws ApiException {
        Source1Client client = getClient();
        JSONArray list = client.screen(q.page, q.typeId, q.typeValue, q.tagType, q.area, q.year, q.sort);
        List<Models.VodItem> items = new ArrayList<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject obj = list.optJSONObject(i);
            if (obj == null) continue;
            Models.VodItem item = toVodItem(obj);
            if (!item.title.trim().isEmpty()) items.add(item);
        }
        return items;
    }

    public static CategoryTree categoryTree() throws ApiException {
        Source1Client client = getClient();
        JSONArray list = client.types();
        CategoryTree tree = new CategoryTree();

        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.optJSONObject(i);
            if (item == null) continue;
            int id = item.optInt("id", item.optInt("type_id", 0));
            if (id == 0) continue;
            String label = item.optString("name", item.optString("type_name", "分类" + id));
            tree.categories.add(new Models.FilterOption(label, String.valueOf(id)));

            CategoryTree.Dim dim = new CategoryTree.Dim();
            JSONArray msgs = item.optJSONArray("msg");
            if (msgs != null) {
                for (int j = 0; j < msgs.length(); j++) {
                    JSONObject msg = msgs.optJSONObject(j);
                    if (msg == null) continue;
                    String name = msg.optString("name", "");
                    JSONArray data = msg.optJSONArray("data");
                    if (data == null) continue;
                    if ("tag_type".equals(name)) {
                        for (int k = 0; k < data.length(); k++) {
                            JSONArray sub = data.optJSONArray(k);
                            List<String> subList = new ArrayList<>();
                            if (sub != null) {
                                for (int m = 0; m < sub.length(); m++) subList.add(sub.optString(m));
                            }
                            dim.tagType.add(subList);
                        }
                    } else {
                        List<String> target = new ArrayList<>();
                        for (int k = 0; k < data.length(); k++) target.add(data.optString(k));
                        switch (name) {
                            case "type": dim.type = target; break;
                            case "area": dim.area = target; break;
                            case "year": dim.year = target; break;
                            case "sort": dim.sort = target; break;
                        }
                    }
                }
            }
            tree.dims.put(id, dim);
        }
        return tree;
    }

    public static List<Models.FilterRow> filterRowsFor(CategoryTree tree, int typeId, String genre) {
        CategoryTree.Dim dim = tree.dims.get(typeId);
        if (dim == null) return Collections.emptyList();
        Set<String> none = new HashSet<>(Arrays.asList("全部", "排序"));

        List<Models.FilterRow> rows = new ArrayList<>();
        addFilterRow(rows, "题材", dim.type, none);
        addFilterRow(rows, "地区", dim.area, none);
        addFilterRow(rows, "年份", dim.year, none);
        addFilterRow(rows, "排序", dim.sort, none);

        int idx = dim.type.indexOf(genre);
        if (idx >= 0 && idx < dim.tagType.size()) {
            addFilterRow(rows, "标签", dim.tagType.get(idx), none);
        }
        return rows;
    }

    private static void addFilterRow(List<Models.FilterRow> rows, String title, List<String> values, Set<String> none) {
        List<Models.FilterOption> opts = new ArrayList<>();
        for (String v : values) {
            if (v != null && !v.isEmpty()) {
                opts.add(new Models.FilterOption(v, none.contains(v) ? "" : v));
            }
        }
        if (!opts.isEmpty()) rows.add(new Models.FilterRow(title, opts));
    }

    public static List<Models.VodItem> search(String keyword) throws ApiException {
        Source1Client client = getClient();
        JSONArray list = client.search(keyword, 1);
        List<Models.VodItem> items = new ArrayList<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject obj = list.optJSONObject(i);
            if (obj == null) continue;
            Models.VodItem item = toVodItem(obj);
            if (!item.title.trim().isEmpty()) items.add(item);
        }
        return items;
    }

    public static List<String> hotSearch() {
        try {
            Source1Client client = getClient();
            JSONArray list = client.hotSearch();
            List<String> out = new ArrayList<>();
            for (int i = 0; i < list.length(); i++) {
                JSONObject obj = list.optJSONObject(i);
                if (obj == null) continue;
                String s = obj.optString("name", obj.optString("word", "")).trim();
                if (!s.isEmpty()) out.add(s);
            }
            return out;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public static Models.VodDetail detail(String vodId) throws ApiException {
        Source1Client client = getClient();
        int idNum = Integer.parseInt(vodId.replace("s1_", ""));
        JSONObject raw = client.detail(idNum);
        JSONObject collection = new JSONObject();
        try {
            collection = client.collectionList(idNum);
        } catch (Exception ignored) {}

        if (raw.length() == 0 && collection.length() > 0) raw = collection;

        Models.VodDetail d = new Models.VodDetail();
        d.vodId = String.valueOf(idNum);
        d.sourceId = Models.SourceId.s1;
        d.name = raw.optString("vod_name", "");
        d.pic = absolutePic(raw.optString("vod_pic", raw.optString("coverUrl", "")));
        d.actor = raw.optString("vod_actor", "");
        d.director = raw.optString("vod_director", "");
        d.year = raw.optString("vod_year", "");
        d.area = raw.optString("vod_area", "");
        d.blurb = raw.optString("vod_blurb", "");
        d.episodes = Source1Play.mergeDurations(raw.optJSONArray("vod_collection"), collection.optJSONArray("vod_collection"));
        return d;
    }

    private static JSONObject pickBean(JSONArray list, Models.Episode ep) {
        if (list == null) return null;
        if (ep.episodeId != null && !ep.episodeId.isEmpty()) {
            for (int i = 0; i < list.length(); i++) {
                JSONObject o = list.optJSONObject(i);
                if (o != null && ep.episodeId.equals(o.optString("id", ""))) return o;
            }
        }
        return ep.episodeIndex < list.length() ? list.optJSONObject(ep.episodeIndex) : null;
    }

    public static List<Models.PlayLine> playLinesFor(String vodId, Models.Episode ep) throws ApiException {
        int idNum = Integer.parseInt(vodId.replace("s1_", ""));
        int collectionId;
        try {
            collectionId = Integer.parseInt(ep.episodeId);
        } catch (Exception e) {
            throw new ApiException("这一集没有拿到上游选集 id，无法取线路");
        }
        List<String> errors = new ArrayList<>();
        for (int mahao : Source1Constants.PLAY_CHAIN) {
            Source1Client client = new Source1Client(mahao);
            try {
                client.init();
                JSONObject bean = null;
                try {
                    bean = pickBean(client.detail(idNum).optJSONArray("vod_collection"), ep);
                } catch (Exception ignored) {}
                if (bean == null || bean.optString("vod_token", "").isEmpty()) {
                    try {
                        JSONObject alt = pickBean(client.collectionList(idNum).optJSONArray("vod_collection"), ep);
                        if (alt != null) bean = alt;
                    } catch (Exception ignored) {}
                }
                if (bean == null || bean.optString("vod_token", "").isEmpty()) {
                    errors.add(client.entry.label + "：签不出 vod_token");
                    continue;
                }
                JSONObject col = client.collection(idNum, collectionId, bean.optString("vod_token", ""), bean.optString("cur_time", ""));
                List<Models.PlayLine> lines = Source1Play.playLines(col, client.secDomain, client.wsKey);
                if (!lines.isEmpty()) return lines;
                errors.add(client.entry.label + "：上游没回线路");
            } catch (Exception e) {
                errors.add(client.entry.label + "：" + e.getMessage());
            }
        }
        throw new ApiException("取不到线路：" + String.join("；", errors));
    }

    public static String resolveOfficialLine(Models.PlayLine line) throws ApiException {
        return getClient().resolve(line.parsePrefix, line.url);
    }
}
