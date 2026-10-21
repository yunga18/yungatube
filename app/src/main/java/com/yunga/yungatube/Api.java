package com.yunga.yungatube;

import android.content.Context;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;

final class Api {
    // Public instances listed by https://docs.invidious.io/instances/ ; availability varies.
    static final String[] INSTANCES = {
        "https://inv.nadeko.net",
        "https://invidious.nerdvpn.de",
        "https://yt.chocolatemoo53.com",
        "https://invidious.tiekoetter.com"
    };
    static final String PREFS = "yungatube";
    private static volatile String lastError = "";

    static final class Result {
        final String host;
        final String json;
        Result(String host, String json) { this.host = host; this.json = json; }
        JSONArray array() throws Exception { return new JSONArray(json); }
        JSONObject object() throws Exception { return new JSONObject(json); }
    }

    static String configured(Context c) {
        return c.getSharedPreferences(PREFS,0).getString("instance", INSTANCES[0]);
    }

    static String validate(String input) {
        String s = input == null ? "" : input.trim().replaceAll("/+$", "");
        try {
            Uri uri = Uri.parse(s);
            if (!"https".equalsIgnoreCase(uri.getScheme()) ||
                uri.getHost() == null || uri.getHost().isEmpty() ||
                uri.getUserInfo() != null ||
                (uri.getPath() != null && !uri.getPath().isEmpty()) ||
                uri.getQuery() != null || uri.getFragment() != null) {
                return null;
            }
            return s;
        } catch (Exception e) { return null; }
    }

    static void setInstance(Context c, String instance) {
        String good = validate(instance);
        if (good == null) throw new IllegalArgumentException("Introduce una URL HTTPS válida");
        c.getSharedPreferences(PREFS,0).edit().putString("instance",good).putString("source","invidious").apply();
    }

    static boolean useDirect(Context c) {
        return "direct".equals(c.getSharedPreferences(PREFS,0).getString("source","direct"));
    }

    static void selectDirect(Context c) {
        c.getSharedPreferences(PREFS,0).edit().putString("source","direct").apply();
    }

    static Result get(Context c, String path) throws Exception {
        if (useDirect(c)) return DirectExtractor.get(path);
        if (!path.startsWith("/api/v1/")) throw new IllegalArgumentException("Ruta de API no válida");
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        candidates.add(configured(c));
        for (String url: INSTANCES) candidates.add(url);
        Exception last = null;
        for (String host : candidates) {
            try {
                URL url = new URL(host + path);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                try {
                    connection.setConnectTimeout(9000);
                    connection.setReadTimeout(12000);
                    connection.setRequestProperty("Accept","application/json");
                    connection.setRequestProperty("User-Agent","YungaTube/0.1 (+https://github.com/yunga18/yungatube)");
                    int code = connection.getResponseCode();
                    if (code != 200) throw new Exception(host + ": HTTP " + code);
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        connection.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) sb.append(line);
                    }
                    String body = sb.toString().trim();
                    if (!(body.startsWith("[") || body.startsWith("{")))
                        throw new Exception("Respuesta inválida de " + host);
                    lastError = "";
                    return new Result(host,body);
                } finally { connection.disconnect(); }
            } catch (Exception e) {
                last = e;
                lastError = e.getMessage();
            }
        }
        throw new Exception("No hay servidores de Invidious disponibles. " +
            (last == null ? "" : last.getMessage()));
    }

    static String encode(String raw) {
        return java.net.URLEncoder.encode(raw, StandardCharsets.UTF_8);
    }

    static String lastError() { return lastError; }
}
