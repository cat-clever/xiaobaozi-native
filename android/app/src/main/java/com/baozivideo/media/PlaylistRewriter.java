package com.baozivideo.media;

import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlaylistRewriter {
    private static final Pattern URI_PATTERN = Pattern.compile("URI=\"([^\"]+)\"");

    private PlaylistRewriter() {}

    public interface LocalMapper {
        String toLocal(String absoluteUrl);
    }

    public static String basenameOf(String urlString) {
        try {
            String path = new URL(urlString).getPath();
            int idx = path.lastIndexOf('/');
            String last = idx >= 0 ? path.substring(idx + 1) : path;
            if (!last.isEmpty() && last.contains(".")) return last;
            return (last.isEmpty() ? "index" : last) + ".ts";
        } catch (Exception e) {
            return "seg.ts";
        }
    }

    public static String localSegmentName(String upstreamUrl, boolean forCast) {
        String name = basenameOf(upstreamUrl);
        if (forCast) {
            String stripped = name.replaceAll("(?i)\\.ts$", "");
            return stripped.isEmpty() ? name : stripped;
        }
        return name;
    }

    public static String absolutize(String target, String playlistUrl) {
        if (target.toLowerCase().startsWith("http://") || target.toLowerCase().startsWith("https://")) {
            return target;
        }
        try {
            URL base = new URL(playlistUrl);
            String portStr = base.getPort() != -1 ? ":" + base.getPort() : "";
            String origin = base.getProtocol() + "://" + base.getHost() + portStr;
            if (target.startsWith("/")) return origin + target;
            String path = base.getPath();
            int idx = path.lastIndexOf('/');
            String dir = idx >= 0 ? path.substring(0, idx + 1) : "/";
            return origin + dir + target;
        } catch (Exception e) {
            return target;
        }
    }

    public static String decorateUrl(String target, String playlistUrl, boolean inheritQuery) {
        String abs = absolutize(target, playlistUrl);
        if (!inheritQuery) return abs;
        try {
            String query = new URL(playlistUrl).getQuery();
            if (query == null || query.isEmpty() || abs.contains("sign=")) return abs;
            return abs + (abs.contains("?") ? "&" : "?") + query;
        } catch (Exception e) {
            return abs;
        }
    }

    public static String rewrite(String text, String playlistUrl, boolean inheritQuery, LocalMapper mapper) {
        String[] lines = text.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                sb.append(line);
            } else if (trimmed.startsWith("#")) {
                Matcher m = URI_PATTERN.matcher(line);
                if (m.find()) {
                    String uri = m.group(1);
                    String local = mapper.toLocal(decorateUrl(uri, playlistUrl, inheritQuery));
                    sb.append(m.replaceFirst(Matcher.quoteReplacement("URI=\"" + local + "\"")));
                } else {
                    sb.append(line);
                }
            } else {
                sb.append(mapper.toLocal(decorateUrl(trimmed, playlistUrl, inheritQuery)));
            }
            if (i < lines.length - 1) sb.append("\n");
        }
        String out = sb.toString();
        if (!out.toUpperCase().contains("#EXT-X-ENDLIST")) {
            out += out.endsWith("\n") ? "#EXT-X-ENDLIST\n" : "\n#EXT-X-ENDLIST\n";
        }
        return out;
    }
}
