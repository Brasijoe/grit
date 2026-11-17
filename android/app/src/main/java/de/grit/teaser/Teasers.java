package de.grit.teaser;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Holt die freigegebenen Teaser von der Website und meldet neue als Benachrichtigung. */
final class Teasers {
    static final String CHANNEL = "teaser";
    private static final int JOB_ID = 1;
    private static final int B64 = Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP;

    static String file() { return BuildConfig.TEST ? "teaser-test.json" : "teaser.json"; }

    /** Alle 15 Minuten im Hintergrund nachsehen (Android legt den genauen Zeitpunkt fest). */
    static void schedule(Context c) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (js == null || js.getPendingJob(JOB_ID) != null) return;
        js.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(c, TeaserJob.class))
                .setPeriodic(15 * 60 * 1000L)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .build());
    }

    /**
     * Lädt die Teaser. Neue (noch nicht gesehene) werden gemeldet, wenn notify true ist.
     * Beim allerersten Mal wird nichts gemeldet, nur gemerkt.
     */
    static synchronized void check(Context c, boolean notify) throws Exception {
        if (BuildConfig.APP_KEY.isEmpty()) return;
        URL url = new URL(BuildConfig.SITE + file() + "?t=" + System.currentTimeMillis());
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setUseCaches(false);
        con.setConnectTimeout(15000);
        con.setReadTimeout(15000);
        int code = con.getResponseCode();
        if (code == 404) return;
        if (code != 200) throw new IOException("HTTP " + code);
        String body;
        try (InputStream in = con.getInputStream()) { body = new String(readAll(in), StandardCharsets.UTF_8); }

        JSONObject all = new JSONObject(body);
        SharedPreferences p = c.getSharedPreferences("teaser", Context.MODE_PRIVATE);
        boolean first = !p.getBoolean("init", false);
        Set<String> seen = new HashSet<>(p.getStringSet("seen", new HashSet<>()));
        Set<String> now = new HashSet<>();
        Iterator<String> it = all.keys();
        while (it.hasNext()) {
            String id = it.next();
            JSONArray arr = all.optJSONArray(id);
            if (arr == null) continue;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject t;
                try { t = decrypt(arr.getString(i)); } catch (Exception e) { continue; }
                String uid = t.optString("uid", arr.getString(i));
                now.add(uid);
                if (notify && !first && !seen.contains(uid)) show(c, Integer.parseInt(id), uid, t.optString("text"));
            }
        }
        p.edit().putStringSet("seen", now).putBoolean("init", true).apply();
    }

    static JSONObject decrypt(String s) throws Exception {
        byte[] raw = Base64.decode(s, B64);
        byte[] key = Base64.decode(BuildConfig.APP_KEY, B64);
        Cipher ci = Cipher.getInstance("AES/GCM/NoPadding");
        ci.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, raw, 0, 12));
        byte[] plain = ci.doFinal(raw, 12, raw.length - 12);
        return new JSONObject(new String(plain, StandardCharsets.UTF_8));
    }

    static void show(Context c, int month, String uid, String text) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Neue Teaser", NotificationManager.IMPORTANCE_HIGH));
        Intent open = new Intent(c, MainActivity.class)
                .putExtra("monat", month)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, uid.hashCode(), open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle((BuildConfig.TEST ? "Test · " : "") + "Neuer Teaser für Monat " + month + " 🎁")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        try { nm.notify(uid.hashCode(), n); } catch (SecurityException ignored) { /* Benachrichtigungen nicht erlaubt */ }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private Teasers() {}
}
