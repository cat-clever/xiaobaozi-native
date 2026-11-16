package com.baozivideo.cast;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DlnaParse {
    public static final String AVTRANSPORT = "avtransport";
    private static final String[] GUESSED_PATHS = {
        "/AVTransport/control", "/upnp/control/AVTransport1", "/upnp/control/AVTransport", "/AVTransport/ctrl"
    };
    private static final String[] FALLBACK_PATHS = {
        "/control", "/AVTransport/control", "/upnp/control/AVTransport1", "/upnp/control/AVTransport", "/AVTransport/ctrl"
    };

    private DlnaParse() {}

    public static String tagText(String xml, String tag) {
        Pattern p = Pattern.compile("<(?:[\\w.-]+:)?" + tag + "\\b[^>]*>([\\s\\S]*?)</(?:[\\w.-]+:)?" + tag + "\\s*>", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(xml);
        if (!m.find()) return "";
        return m.group(1).replaceAll("<[^>]*>", "").trim();
    }

    public static List<String> allBlocks(String xml, String tag) {
        List<String> out = new ArrayList<>();
        Pattern p = Pattern.compile("<(?:[\\w.-]+:)?" + tag + "\\b[^>]*>[\\s\\S]*?</(?:[\\w.-]+:)?" + tag + "\\s*>", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(xml);
        while (m.find()) out.add(m.group());
        return out;
    }

    public static class ParsedDescription {
        public String udn;
        public String friendlyName;
        public String modelName;
        public List<String> controlUrls;
        public String serviceType;
    }

    public static ParsedDescription parseDescription(String xml, String descriptionUrl) {
        String rawBase = tagText(xml, "URLBase");
        String base = normalizeBase(rawBase, descriptionUrl);

        List<String> controlUrls = new ArrayList<>();
        String serviceType = "";
        for (String block : allBlocks(xml, "service")) {
            String type = tagText(block, "serviceType");
            String control = tagText(block, "controlURL");
            if (control.isEmpty()) continue;
            if (type.toLowerCase().contains(AVTRANSPORT)) {
                controlUrls = controlCandidates(base, rawBase, descriptionUrl, control);
                serviceType = type;
                break;
            }
            if (controlUrls.isEmpty() && control.toLowerCase().contains(AVTRANSPORT)) {
                controlUrls = controlCandidates(base, rawBase, descriptionUrl, control);
                serviceType = type.isEmpty() ? "urn:schemas-upnp-org:service:AVTransport:1" : type;
            }
        }

        if (controlUrls.isEmpty() && Pattern.compile("<\\s*(?:[\\w.-]+:)?(?:root|device)\\b", Pattern.CASE_INSENSITIVE).matcher(xml).find()) {
            String root = rootOf(base);
            if (!root.isEmpty()) controlUrls.add(root + GUESSED_PATHS[0]);
        }
        if (controlUrls.isEmpty()) return null;

        ParsedDescription pd = new ParsedDescription();
        pd.udn = tagText(xml, "UDN");
        pd.friendlyName = tagText(xml, "friendlyName");
        String model = tagText(xml, "modelName");
        pd.modelName = model.isEmpty() ? tagText(xml, "manufacturer") : model;
        pd.controlUrls = controlUrls;
        pd.serviceType = serviceType.isEmpty() ? "urn:schemas-upnp-org:service:AVTransport:1" : serviceType;
        return pd;
    }

    public static List<String> controlCandidates(String base, String rawBase, String descriptionUrl, String control) {
        List<String> out = new ArrayList<>();
        addUnique(out, resolveUrl(base, control));
        if (!rawBase.trim().isEmpty()) addUnique(out, resolveUrl(rawBase.trim(), control));
        addUnique(out, resolveUrl(descriptionUrl, control));
        String root = rootOf(base);
        if (root.isEmpty()) root = rootOf(descriptionUrl);
        if (!root.isEmpty()) {
            for (String path : FALLBACK_PATHS) addUnique(out, root + path);
        }
        return out;
    }

    private static void addUnique(List<String> list, String url) {
        if (url != null && !url.isEmpty() && !list.contains(url)) list.add(url);
    }

    public static String normalizeBase(String raw, String fallback) {
        String base = raw == null || raw.trim().isEmpty() ? fallback : raw.trim();
        try {
            URL url = new URL(base);
            String path = url.getPath();
            String trimmed = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
            if (trimmed.isEmpty()) return base;
            String last = trimmed.substring(trimmed.lastIndexOf('/') + 1);
            if (!last.contains(".")) return base;
            String portStr = url.getPort() != -1 ? ":" + url.getPort() : "";
            return url.getProtocol() + "://" + url.getHost() + portStr + trimmed.substring(0, trimmed.lastIndexOf('/') + 1);
        } catch (Exception e) {
            return base;
        }
    }

    public static String resolveUrl(String base, String relative) {
        String rel = relative == null ? "" : relative.trim();
        if (rel.isEmpty()) return "";
        if (rel.toLowerCase().startsWith("http://") || rel.toLowerCase().startsWith("https://")) return rel;
        try {
            return new URL(new URL(base), rel).toString();
        } catch (Exception e) {
            return rel;
        }
    }

    public static String rootOf(String urlString) {
        try {
            URL url = new URL(urlString);
            String portStr = url.getPort() != -1 ? ":" + url.getPort() : "";
            return url.getProtocol() + "://" + url.getHost() + portStr;
        } catch (Exception e) {
            return "";
        }
    }

    public static String normalizeState(String raw) {
        if (raw == null) return "unknown";
        switch (raw.trim().toUpperCase()) {
            case "PLAYING": return "playing";
            case "PAUSED_PLAYBACK":
            case "PAUSED_RECORDING":
            case "PAUSED": return "paused";
            case "STOPPED":
            case "NO_MEDIA_PRESENT": return "stopped";
            case "TRANSITIONING": return "transitioning";
            default: return "unknown";
        }
    }

    public static String faultOf(String xml) {
        if (xml == null || !xml.toLowerCase().contains("fault")) return "";
        String reason = tagText(xml, "faultstring");
        if (reason.isEmpty()) reason = tagText(xml, "errorDescription");
        if (reason.isEmpty()) reason = tagText(xml, "errorCode");
        return reason.isEmpty() ? "设备返回了 SOAP Fault" : reason;
    }

    public static String escapeXml(String input) {
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }

    public static String buildEnvelope(String serviceType, String action, Map<String, String> args) {
        StringBuilder inner = new StringBuilder();
        for (Map.Entry<String, String> e : args.entrySet()) {
            inner.append("<").append(e.getKey()).append(">").append(escapeXml(e.getValue())).append("</").append(e.getKey()).append(">");
        }
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
            "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
            "<s:Body><u:" + action + " xmlns:u=\"" + serviceType + "\">" + inner + "</u:" + action + "></s:Body></s:Envelope>";
    }

    public static String buildMetadata(String url, String title) {
        String safeTitle = escapeXml(title == null || title.isEmpty() ? "Video" : title);
        return "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" " +
            "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" " +
            "xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">" +
            "<item id=\"0\" parentID=\"-1\" restricted=\"1\">" +
            "<dc:title>" + safeTitle + "</dc:title>" +
            "<upnp:class>object.item.videoItem</upnp:class>" +
            "<res protocolInfo=\"http-get:*:video/*:*\">" + escapeXml(url) + "</res>" +
            "</item></DIDL-Lite>";
    }
}
