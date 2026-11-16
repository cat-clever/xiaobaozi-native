package com.baozivideo.api.source2;

import com.baozivideo.api.ApiException;
import com.baozivideo.model.Models;
import com.baozivideo.utils.StorageManager;
import org.json.JSONObject;
import java.util.List;

public final class Source2Service {
    private static String cachedUid;

    private Source2Service() {}

    private static synchronized String deviceUid() {
        if (cachedUid == null) {
            cachedUid = StorageManager.getInstance().getDeviceUid();
        }
        return cachedUid;
    }

    private static Source2Client makeClient() {
        return new Source2Client(deviceUid(), false);
    }

    public static List<Models.HomeBlock> homeFeed(int page) throws ApiException {
        return makeClient().recommendBlocks(page);
    }

    public static List<Models.VodItem> listByCategory(Models.CategoryQuery q) throws ApiException {
        return makeClient().catePage(q.stype, q.cid, q.year, q.sort, q.page).items;
    }

    public static Source2Client.CateResult categoryTree() throws ApiException {
        return makeClient().catePage(null, null, null, null, 1);
    }

    public static List<Models.VodItem> search(String keyword) throws ApiException {
        return makeClient().searchPage(keyword);
    }

    public static Models.VodDetail detail(String sid) throws ApiException {
        return makeClient().detailPage(sid.replace("s2_", ""));
    }

    public static List<JSONObject> playFor(String sid, Models.Episode ep, Integer quality) throws ApiException {
        Source2Client client = makeClient();
        String pid = ep.pid == null ? "" : ep.pid;
        List<JSONObject> items = client.playUrl(pid, null, quality);
        if (!items.isEmpty()) {
            StorageManager.getInstance().notePlayNode(Source2Client.hasHttpsPlay(items));
        }
        if (Source2Client.hasHttpsPlay(items)) return items;

        List<JSONObject> best = items.isEmpty() ? null : items;
        for (String uid : Source2Client.sampleUids(Source2Constants.GOOD_UID_TRIES, client.deviceUid)) {
            try {
                Source2Client alt = new Source2Client(uid, false);
                List<JSONObject> t = alt.playUrl(pid, null, quality);
                if (Source2Client.hasHttpsPlay(t)) return t;
                if (best == null && !t.isEmpty()) best = t;
            } catch (Exception ignored) {}
        }
        return best != null ? best : items;
    }
}
