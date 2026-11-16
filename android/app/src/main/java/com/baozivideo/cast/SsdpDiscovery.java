package com.baozivideo.cast;

import android.content.Context;
import android.net.wifi.WifiManager;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SsdpDiscovery {
    public static class SsdpHit {
        public String location;
        public String usn;
        public String st;
        public String server;
        public String address;
    }

    private static final String SSDP_GROUP = "239.255.255.250";
    private static final int SSDP_PORT = 1900;
    private static final String[] TARGETS = {
        "urn:schemas-upnp-org:device:MediaRenderer:1",
        "urn:schemas-upnp-org:service:AVTransport:1",
        "ssdp:all"
    };

    private SsdpDiscovery() {}

    public static List<SsdpHit> discover(Context context, int timeoutMs) {
        Map<String, SsdpHit> found = new LinkedHashMap<>();
        WifiManager.MulticastLock lock = null;
        try {
            WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                lock = wm.createMulticastLock("baozivideo_ssdp");
                lock.setReferenceCounted(false);
                lock.acquire();
            }
        } catch (Exception ignored) {}

        DatagramSocket unicast = null;
        MulticastSocket group = null;
        try {
            unicast = new DatagramSocket();
            unicast.setSoTimeout(350);

            group = new MulticastSocket(null);
            group.setReuseAddress(true);
            group.bind(new InetSocketAddress(SSDP_PORT));
            group.setSoTimeout(350);
            group.joinGroup(InetAddress.getByName(SSDP_GROUP));
        } catch (Exception ignored) {}

        try {
            long deadline = System.currentTimeMillis() + Math.max(800, timeoutMs);
            int round = 0;
            byte[] buf = new byte[4096];
            InetAddress groupAddr = InetAddress.getByName(SSDP_GROUP);

            while (System.currentTimeMillis() < deadline) {
                if (round < 4) {
                    sendSearch(unicast, groupAddr);
                    sendSearch(group, groupAddr);
                    round++;
                }
                drain(unicast, buf, found);
                drain(group, buf, found);
            }
        } catch (Exception ignored) {
        } finally {
            if (unicast != null) unicast.close();
            if (group != null) group.close();
            if (lock != null && lock.isHeld()) {
                try { lock.release(); } catch (Exception ignored) {}
            }
        }
        return new ArrayList<>(found.values());
    }

    private static void sendSearch(DatagramSocket sock, InetAddress dest) {
        if (sock == null) return;
        for (String st : TARGETS) {
            try {
                String msg = "M-SEARCH * HTTP/1.1\r\nHOST: " + SSDP_GROUP + ":" + SSDP_PORT +
                    "\r\nMAN: \"ssdp:discover\"\r\nMX: 3\r\nST: " + st + "\r\n\r\n";
                byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);
                sock.send(new DatagramPacket(bytes, bytes.length, dest, SSDP_PORT));
            } catch (Exception ignored) {}
        }
    }

    private static void drain(DatagramSocket sock, byte[] buf, Map<String, SsdpHit> found) {
        if (sock == null) return;
        while (true) {
            try {
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                sock.receive(pkt);
                String text = new String(pkt.getData(), 0, pkt.getLength(), StandardCharsets.UTF_8);
                String location = "", usn = "", st = "", server = "";
                for (String line : text.split("\r?\n")) {
                    int idx = line.indexOf(':');
                    if (idx <= 0) continue;
                    String k = line.substring(0, idx).trim().toLowerCase();
                    String v = line.substring(idx + 1).trim();
                    if ("location".equals(k)) location = v;
                    else if ("usn".equals(k)) usn = v;
                    else if ("st".equals(k)) st = v;
                    else if ("server".equals(k)) server = v;
                }
                if (!location.isEmpty() && !found.containsKey(location)) {
                    SsdpHit hit = new SsdpHit();
                    hit.location = location;
                    hit.usn = usn;
                    hit.st = st;
                    hit.server = server;
                    hit.address = pkt.getAddress() != null ? pkt.getAddress().getHostAddress() : "";
                    found.put(location, hit);
                }
            } catch (Exception e) {
                break;
            }
        }
    }
}
