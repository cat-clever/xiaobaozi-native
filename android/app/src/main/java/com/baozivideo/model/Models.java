package com.baozivideo.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Models {
    public enum SourceId {
        s1("全网源"),
        s2("精选源");

        public final String label;
        SourceId(String label) { this.label = label; }
    }

    public static class VodItem implements Serializable {
        public String id;
        public SourceId sourceId;
        public String title;
        public String coverUrl;
        public String rating;
        public String category;
        public String year;
        public String area;
        public String remarks;
        public String intro;
    }

    public static class Episode {
        public int episodeIndex;
        public String episodeId;
        public String title;
        public String vodToken;
        public String curTime;
        public String duration;
        public int durationSec;
        public String pid;
        public String scid;
    }

    public enum LineKind {
        signed, mirror, official, m3u8, backup
    }

    public static class PlayLine {
        public int type;
        public LineKind kind;
        public String name;
        public String url;
        public String parsePrefix;
        public String ck;
        public String wsKey;
        public boolean playable;
        public String note;
    }

    public static class QualityOption {
        public String label;
        public int value;
        public QualityOption(String label, int value) {
            this.label = label;
            this.value = value;
        }
    }

    public static class SeasonOption {
        public String sid;
        public String title;
        public SeasonOption(String sid, String title) {
            this.sid = sid;
            this.title = title;
        }
    }

    public static class VodDetail {
        public String vodId;
        public SourceId sourceId;
        public String name;
        public String pic;
        public String actor;
        public String director;
        public String year;
        public String area;
        public String blurb;
        public List<Episode> episodes = new ArrayList<>();
        public List<QualityOption> qualities = new ArrayList<>();
        public List<SeasonOption> seasons = new ArrayList<>();
    }

    public static class HomeBlock {
        public String title;
        public List<VodItem> items;
        public HomeBlock(String title, List<VodItem> items) {
            this.title = title;
            this.items = items;
        }
    }

    public static class FilterOption {
        public String label;
        public String value;
        public FilterOption(String label, String value) {
            this.label = label;
            this.value = value;
        }
    }

    public static class FilterRow {
        public String title;
        public List<FilterOption> options;
        public FilterRow(String title, List<FilterOption> options) {
            this.title = title;
            this.options = options;
        }
    }

    public static class CategoryQuery {
        public int page = 1;
        public int typeId = 0;
        public String typeValue = "";
        public String tagType = "";
        public String area = "";
        public Integer stype = null;
        public Integer cid = null;
        public String year = "";
        public String sort = "";
    }

    public static class ResolvedMediaStream {
        public String streamUrl;
        public String format; // "hls" or "mp4"
        public Map<String, String> headers;
        public String lineName;
        public Runnable dispose;
    }

    public static class CastSource {
        public String url;
        public String format;
        public boolean direct;
        public String lineName;
        public Runnable dispose;
    }

    public static class CastDevice {
        public String id;
        public String name;
        public String model;
        public String address;
        public String controlUrl;
        public List<String> controlUrls = new ArrayList<>();
        public String serviceType;
    }

    public static class CastTransportState {
        public String state = "unknown";
        public Double positionSec;
        public Double durationSec;
    }
}
