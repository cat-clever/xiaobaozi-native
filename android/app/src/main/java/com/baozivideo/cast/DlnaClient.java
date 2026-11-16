package com.baozivideo.cast;

import android.content.Context;
import com.baozivideo.api.ApiException;
import com.baozivideo.api.HttpClient;
import com.baozivideo.model.Models;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DlnaClient {
    private static final String DEFAULT_SERVICE = "urn:schemas-upnp-org:service:AVTransport:1";

    private DlnaClient() {}

    public static List<Models.CastDevice> discoverDevices(Context context) {
        List<SsdpDiscovery.SsdpHit> hits = SsdpDiscovery.discover(context, 4000);
        if (hits.isEmpty()) return Collections.emptyList();

        List<SsdpDiscovery.SsdpHit> renderers = new ArrayList<>();
        List<SsdpDiscovery.SsdpHit> others = new ArrayList<>();
        for (SsdpDiscovery.SsdpHit hit : hits) {
            String combo = (hit.st + " " + hit.usn + " " + hit.server).toLowerCase();
            if (combo.contains("mediarenderer")) renderers.add(hit);
            else others.add(hit);
        }

        List<Models.CastDevice> found = describeAll(renderers);
        if (found.isEmpty()) found = describeAll(others);
        found.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return found;
    }

    private static List<Models.CastDevice> describeAll(List<SsdpDiscovery.SsdpHit> hits) {
        List<Models.CastDevice> out = new ArrayList<>();
        for (SsdpDiscovery.SsdpHit hit : hits) {
            try {
                Map<String, String> hdr = new HashMap<>();
                hdr.put("Accept", "text/xml, application/xml, */*");
                HttpClient.Response resp = HttpClient.get(hit.location, hdr, 2500);
                DlnaParse.ParsedDescription pd = DlnaParse.parseDescription(resp.text, hit.location);
                if (pd == null) continue;
                String host = hostOf(hit.location);
                Models.CastDevice d = new Models.CastDevice();
                d.id = !pd.udn.isEmpty() ? pd.udn : (!hit.usn.isEmpty() ? hit.usn : host);
                d.name = !pd.friendlyName.isEmpty() ? pd.friendlyName : (!pd.modelName.isEmpty() ? pd.modelName : host);
                d.model = pd.modelName;
                d.address = hit.address.isEmpty() ? host : hit.address;
                d.controlUrl = pd.controlUrls.get(0);
                d.controlUrls = pd.controlUrls;
                d.serviceType = pd.serviceType;
                out.add(d);
            } catch (Exception ignored) {}
        }
        return out;
    }

    private static String hostOf(String url) {
        try { return new URL(url).getHost(); }
        catch (Exception e) { return url; }
    }

    public static void cast(Models.CastDevice device, String url, String title) throws ApiException {
        Map<String, String> args = new LinkedHashMap<>();
        args.put("InstanceID", "0");
        args.put("CurrentURI", url);
        args.put("CurrentURIMetaData", DlnaParse.buildMetadata(url, title));
        try {
            soap(device, "SetAVTransportURI", args);
        } catch (Exception err) {
            args.put("CurrentURIMetaData", "");
            soap(device, "SetAVTransportURI", args);
        }
        play(device);
    }

    public static void play(Models.CastDevice device) throws ApiException {
        Map<String, String> args = new LinkedHashMap<>();
        args.put("InstanceID", "0");
        args.put("Speed", "1");
        soap(device, "Play", args);
    }

    public static void pause(Models.CastDevice device) throws ApiException {
        Map<String, String> args = new LinkedHashMap<>();
        args.put("InstanceID", "0");
        soap(device, "Pause", args);
    }

    public static void stop(Models.CastDevice device) throws ApiException {
        Map<String, String> args = new LinkedHashMap<>();
        args.put("InstanceID", "0");
        soap(device, "Stop", args);
    }

    public static Models.CastTransportState status(Models.CastDevice device) {
        Models.CastTransportState st = new Models.CastTransportState();
        Map<String, String> args = Collections.singletonMap("InstanceID", "0");
        try {
            String info = soap(device, "GetTransportInfo", args);
            st.state = DlnaParse.normalizeState(DlnaParse.tagText(info, "CurrentTransportState"));
        } catch (Exception ignored) {}
        return st;
    }

    private static String soap(Models.CastDevice device, String action, Map<String, String> args) throws ApiException {
        List<String> urls = device.controlUrls.isEmpty() ? Collections.singletonList(device.controlUrl) : device.controlUrls;
        String serviceType = (device.serviceType == null || device.serviceType.isEmpty()) ? DEFAULT_SERVICE : device.serviceType;
        String body = DlnaParse.buildEnvelope(serviceType, action, args);

        ApiException lastErr = null;
        for (String url : urls) {
            if (url == null || url.isEmpty()) continue;
            String[] headers = {"\"" + serviceType + "#" + action + "\"", serviceType + "#" + action};
            for (String h : headers) {
                try {
                    Map<String, String> reqHdr = new HashMap<>();
                    reqHdr.put("Content-Type", "text/xml; charset=\"utf-8\"");
                    reqHdr.put("SOAPACTION", h);
                    reqHdr.put("Accept", "text/xml, application/xml, */*");
                    HttpClient.Response resp = HttpClient.post(url, reqHdr, body.getBytes(StandardCharsets.UTF_8), 8000);
                    if (resp.status == 404) {
                        lastErr = new ApiException("控制地址不存在（404）");
                        break;
                    }
                    String fault = DlnaParse.faultOf(resp.text);
                    if (!fault.isEmpty()) throw new ApiException(fault);
                    return resp.text;
                } catch (ApiException e) {
                    lastErr = e;
                }
            }
        }
        throw lastErr != null ? lastErr : new ApiException(action + " 调用失败");
    }
}
