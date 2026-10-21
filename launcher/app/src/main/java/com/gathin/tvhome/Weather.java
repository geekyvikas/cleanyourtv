package com.gathin.tvhome;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Current weather above the date (show_weather in config.xml, for the latitude and longitude there). One small
 * request to Open-Meteo (free, no key, open data under CC BY 4.0) at most every {@link #REFRESH_MS}, and only while
 * Home is on screen; MainActivity schedules it. The request carries the coordinates and nothing else. No Android
 * types, so the parsing is tested on the JVM.
 */
final class Weather {

    static final long REFRESH_MS = 30 * 60_000L;
    /** After a failed request (no network yet after boot), try again sooner than a full refresh. */
    static final long RETRY_MS = 5 * 60_000L;
    /** A reading older than this is not shown at all. */
    static final long STALE_MS = 3 * 60 * 60_000L;

    /** Indexes into the weather_kinds string array. */
    static final int CLEAR = 0, MOSTLY_CLEAR = 1, PARTLY_CLOUDY = 2, CLOUDY = 3, FOG = 4, DRIZZLE = 5, RAIN = 6,
            SNOW = 7, SHOWERS = 8, THUNDERSTORM = 9;

    static final class Now {
        final int tempC;
        final int kind;

        Now(int tempC, int kind) {
            this.tempC = tempC;
            this.kind = kind;
        }
    }

    private Weather() {
    }

    /** The request for these coordinates, or null when they are missing or not numbers in range. */
    static String url(String latitude, String longitude) {
        double lat, lon;
        try {
            lat = Double.parseDouble(latitude.trim());
            lon = Double.parseDouble(longitude.trim());
        } catch (NumberFormatException | NullPointerException e) {
            return null;
        }
        if (Math.abs(lat) > 90 || Math.abs(lon) > 180) return null;
        return String.format(Locale.ROOT, "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f"
                + "&current=temperature_2m,weather_code", lat, lon);
    }

    /** WMO weather interpretation code (what Open-Meteo reports) to one of the kinds above, or -1. */
    static int kind(int wmo) {
        if (wmo == 0) return CLEAR;
        if (wmo == 1) return MOSTLY_CLEAR;
        if (wmo == 2) return PARTLY_CLOUDY;
        if (wmo == 3) return CLOUDY;
        if (wmo == 45 || wmo == 48) return FOG;
        if (wmo >= 51 && wmo <= 57) return DRIZZLE;
        if (wmo >= 61 && wmo <= 67) return RAIN;
        if ((wmo >= 71 && wmo <= 77) || wmo == 85 || wmo == 86) return SNOW;
        if (wmo >= 80 && wmo <= 82) return SHOWERS;
        if (wmo >= 95 && wmo <= 99) return THUNDERSTORM;
        return -1;
    }

    private static final Pattern TEMP = Pattern.compile("\"temperature_2m\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern CODE = Pattern.compile("\"weather_code\"\\s*:\\s*(\\d+)");

    /**
     * The "current" block of an Open-Meteo answer, or null when it is not there or incomplete. Only that block is
     * read: "current_units" carries the same keys with unit strings.
     */
    static Now parse(String json) {
        if (json == null) return null;
        int start = json.indexOf("\"current\"");
        if (start < 0) return null;
        int open = json.indexOf('{', start), close = open < 0 ? -1 : json.indexOf('}', open);
        if (close < 0) return null;
        String block = json.substring(open, close);
        Matcher t = TEMP.matcher(block), c = CODE.matcher(block);
        if (!t.find() || !c.find()) return null;
        int k;
        try {
            k = kind(Integer.parseInt(c.group(1)));
        } catch (NumberFormatException e) {
            return null;
        }
        if (k < 0) return null;
        return new Now((int) Math.round(Double.parseDouble(t.group(1))), k);
    }

    /** Blocking: call off the main thread. */
    static Now fetch(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setUseCaches(false);
        try {
            if (c.getResponseCode() != 200) return null;
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                for (int n; (n = in.read(buf)) > 0 && out.size() < 64 * 1024; ) out.write(buf, 0, n);
                return parse(new String(out.toByteArray(), StandardCharsets.UTF_8));
            }
        } finally {
            c.disconnect();
        }
    }
}
