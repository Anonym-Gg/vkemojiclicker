package com.vk.emojiclicker;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Клиент лицензии: собирает отпечаток устройства и проверяет ключ на твоём ВДС.
 */
public class LicenseClient {

    // Адрес твоего сервера лицензий
    private static final String SERVER = "http://91.107.125.66:8000";

    public static String getFingerprint(Context ctx) {
        StringBuilder raw = new StringBuilder();
        raw.append(android.os.Build.SERIAL != null ? android.os.Build.SERIAL : "");
        raw.append("|").append(Build.BOARD);
        raw.append("|").append(Build.DEVICE);
        raw.append("|").append(Build.PRODUCT);
        raw.append("|").append(Settings.Secure.getString(ctx.getContentResolver(),
                Settings.Secure.ANDROID_ID));
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(raw.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) {
            return raw.toString();
        }
    }

    /** POST-запрос, возвращает JSONObject или null. */
    private static JSONObject post(String path, JSONObject body) {
        try {
            URL url = new URL(SERVER + path);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.getOutputStream().write(body.toString().getBytes(StandardCharsets.UTF_8));
            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            if (is == null) return null;
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
            String s = new String(bos.toByteArray(), StandardCharsets.UTF_8);
            return new JSONObject(s);
        } catch (Exception e) {
            return null;
        }
    }

    public static JSONObject activate(Context ctx, String key) {
        try {
            JSONObject o = new JSONObject();
            o.put("key", key.trim());
            o.put("fingerprint", getFingerprint(ctx));
            return post("/api/activate", o);
        } catch (Exception e) {
            return null;
        }
    }

    public static JSONObject verify(Context ctx, String key) {
        try {
            JSONObject o = new JSONObject();
            o.put("key", key.trim());
            o.put("fingerprint", getFingerprint(ctx));
            return post("/api/verify", o);
        } catch (Exception e) {
            return null;
        }
    }
}