package com.baozivideo.api;

import com.baozivideo.api.source1.Source1Service;
import com.baozivideo.api.source2.Source2Service;
import com.baozivideo.model.Models;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

public final class Aggregator {
    private static final Map<String, List<Models.HomeBlock>> homeCache = new ConcurrentHashMap<>();

    private Aggregator() {}

    public static List<Models.HomeBlock> peekHomeBlocks(Models.SourceId sourceId, int page) {
        return homeCache.get(sourceId.name() + "|" + page);
    }

    public static void clearHomeCache() {
        homeCache.clear();
    }

    public static List<Models.HomeBlock> homeBlocksOf(Models.SourceId sourceId, int page, boolean force) throws ApiException {
        String key = sourceId.name() + "|" + page;
        if (!force) {
            List<Models.HomeBlock> hit = homeCache.get(key);
            if (hit != null) return hit;
        }
        List<Models.HomeBlock> raw = sourceId == Models.SourceId.s1 ? Source1Service.homeFeed(page) : Source2Service.homeFeed(page);
        List<Models.HomeBlock> normalized = new ArrayList<>();
        for (Models.HomeBlock b : raw) {
            normalized.add(new Models.HomeBlock(sourceId.label + " · " + b.title, b.items));
        }
        if (!normalized.isEmpty()) homeCache.put(key, normalized);
        return normalized;
    }

    public static class SearchResult {
        public final List<Models.VodItem> items = new ArrayList<>();
        public final List<String> errors = new ArrayList<>();
    }

    public static SearchResult searchAll(String keyword) {
        SearchResult res = new SearchResult();
        final List<Models.VodItem>[] s1Items = new List[1];
        final List<Models.VodItem>[] s2Items = new List[1];
        final String[] err1 = new String[1];
        final String[] err2 = new String[1];

        CountDownLatch latch = new CountDownLatch(2);
        new Thread(() -> {
            try { s1Items[0] = Source1Service.search(keyword); }
            catch (Exception e) { err1[0] = "全网源：" + e.getMessage(); }
            finally { latch.countDown(); }
        }).start();

        new Thread(() -> {
            try { s2Items[0] = Source2Service.search(keyword); }
            catch (Exception e) { err2[0] = "精选源：" + e.getMessage(); }
            finally { latch.countDown(); }
        }).start();

        try { latch.await(); } catch (InterruptedException ignored) {}

        if (s1Items[0] != null) res.items.addAll(s1Items[0]);
        if (s2Items[0] != null) res.items.addAll(s2Items[0]);
        if (err1[0] != null) res.errors.add(err1[0]);
        if (err2[0] != null) res.errors.add(err2[0]);
        return res;
    }

    public static List<Models.VodItem> listByCategory(Models.SourceId sourceId, Models.CategoryQuery q) throws ApiException {
        return sourceId == Models.SourceId.s1 ? Source1Service.listByCategory(q) : Source2Service.listByCategory(q);
    }

    public static Models.VodDetail detail(Models.SourceId sourceId, String id) throws ApiException {
        return sourceId == Models.SourceId.s1 ? Source1Service.detail(id) : Source2Service.detail(id);
    }
}
