package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.Locale;

/**
 * Quran verse of the day from AlQuran Cloud (https://alquran.cloud/api).
 * Arabic Uthmani + Malay Basmeih, cached for the calendar day. Offline fallback stays local.
 */
final class DailyAyat {
    private static final String TAG = "TvHome.Ayat";
    private static final String PREFS = "ayat_cloud";
    private static final String API = "https://api.alquran.cloud/v1/ayah/";
    private static final String EDITIONS = "/editions/quran-uthmani,ms.basmeih";
    private static final int AYAH_COUNT = 6236;
    private static final int MAX_ARABIC = 220;
    private static final int MAX_TRIES = 14;

    static class Ayat {
        final String reference;
        final String arabic;
        final String translation;

        Ayat(String reference, String arabic, String translation) {
            this.reference = reference;
            this.arabic = arabic;
            this.translation = translation;
        }
    }

    private static final Ayat[] FALLBACK = new Ayat[]{
            new Ayat("AL-BAQARAH 2:152",
                    "فَاذْكُرُونِي أَذْكُرْكُمْ وَاشْكُرُوا لِي وَلَا تَكْفُرُونِ",
                    "Maka ingatlah kamu kepada-Ku, nescaya Aku ingat kepada kamu."),
            new Ayat("ASH-SHARH 94:5",
                    "فَإِنَّ مَعَ الْعُسْرِ يُسْرًا",
                    "Maka sesungguhnya bersama kesukaran itu ada kemudahan."),
            new Ayat("AR-RA'D 13:28",
                    "أَلَا بِذِكْرِ اللَّهِ تَطْمَئِنُّ الْقُلُوبُ",
                    "Ketahuilah, dengan zikir kepada Allah hati menjadi tenang."),
            new Ayat("AT-TALAQ 65:3",
                    "وَمَن يَتَوَكَّلْ عَلَى اللَّهِ فَهُوَ حَسْبُهُ",
                    "Dan sesiapa yang bertawakal kepada Allah, maka cukuplah Allah baginya."),
            new Ayat("AL-BAQARAH 2:286",
                    "لَا يُكَلِّفُ اللَّهُ نَفْسًا إِلَّا وُسْعَهَا",
                    "Allah tidak membebani seseorang melainkan sesuai dengan kesanggupannya."),
            new Ayat("IBRAHIM 14:7",
                    "لَئِن شَكَرْتُمْ لَأَزِيدَنَّكُمْ",
                    "Sesungguhnya jika kamu bersyukur, pasti Aku akan tambah nikmat-Ku kepadamu."),
            new Ayat("AZ-ZUMAR 39:53",
                    "لَا تَقْنَطُوا مِن رَّحْمَةِ اللَّهِ",
                    "Janganlah kamu berputus asa daripada rahmat Allah."),
            new Ayat("TA-HA 20:114",
                    "رَّبِّ زِدْنِي عِلْمًا",
                    "Wahai Tuhanku, tambahkanlah ilmuku.")
    };

    private DailyAyat() {
    }

    static Ayat getToday(Context ctx) {
        loadCache(ctx);
        return cachedToday != null ? cachedToday : fallbackToday();
    }

    static Ayat getRandom(Context ctx) {
        loadCache(ctx);
        if (cachedRand != null) return cachedRand;
        int i = (int) (System.nanoTime() % FALLBACK.length);
        if (i < 0) i = -i;
        return FALLBACK[i];
    }

    static void syncIfDue(Context context, Runnable onUpdated) {
        if (context == null) return;
        final Context app = context.getApplicationContext();
        loadCache(app);
        final String dayKey = todayKey();
        SharedPreferences sp = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (dayKey.equals(sp.getString("day", "")) && cachedToday != null) {
            return;
        }
        new Thread(() -> {
            try {
                int start = ayahForToday();
                Ayat today = fetchSuitable(start);
                if (today == null) return;
                Ayat rand = fetchOne("random");
                if (rand == null || tooLong(rand)) rand = today;
                save(app, "today", today);
                save(app, "rand", rand);
                cachedToday = today;
                cachedRand = rand;
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putString("day", dayKey)
                        .apply();
                if (onUpdated != null) {
                    new Handler(Looper.getMainLooper()).post(onUpdated);
                }
                Log.i(TAG, "synced " + today.reference);
            } catch (Exception e) {
                Log.w(TAG, "ayat sync: " + e.getMessage());
            }
        }, "ayat-sync").start();
    }

    private static Ayat fallbackToday() {
        int day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
        return FALLBACK[day % FALLBACK.length];
    }

    private static int ayahForToday() {
        Calendar c = Calendar.getInstance();
        int seed = c.get(Calendar.YEAR) * 400 + c.get(Calendar.DAY_OF_YEAR);
        return 1 + Math.floorMod(seed * 17, AYAH_COUNT);
    }

    private static String todayKey() {
        Calendar c = Calendar.getInstance();
        return String.format(Locale.US, "%04d-%03d",
                c.get(Calendar.YEAR), c.get(Calendar.DAY_OF_YEAR));
    }

    private static Ayat fetchSuitable(int start) throws Exception {
        for (int i = 0; i < MAX_TRIES; i++) {
            int n = 1 + Math.floorMod(start - 1 + i, AYAH_COUNT);
            Ayat a = fetchOne(String.valueOf(n));
            if (a != null && !tooLong(a)) return a;
        }
        return fetchOne(String.valueOf(start));
    }

    private static boolean tooLong(Ayat a) {
        return a.arabic == null || a.arabic.length() > MAX_ARABIC
                || a.translation == null || a.translation.length() > 360;
    }

    private static Ayat fetchOne(String ref) throws Exception {
        URL url = new URL(API + ref + EDITIONS);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        if (conn.getResponseCode() != 200) {
            conn.disconnect();
            return null;
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();
        conn.disconnect();
        return parse(sb.toString());
    }

    private static Ayat parse(String body) throws Exception {
        JSONObject root = new JSONObject(body);
        if (root.optInt("code", 0) != 200) return null;
        JSONArray data = root.optJSONArray("data");
        if (data == null || data.length() == 0) {
            JSONObject one = root.optJSONObject("data");
            if (one == null) return null;
            data = new JSONArray().put(one);
        }
        String arabic = "";
        String malay = "";
        String reference = "";
        for (int i = 0; i < data.length(); i++) {
            JSONObject a = data.getJSONObject(i);
            String id = a.getJSONObject("edition").optString("identifier", "");
            String text = a.optString("text", "").trim();
            JSONObject surah = a.optJSONObject("surah");
            if (surah != null && reference.isEmpty()) {
                String name = surah.optString("englishName", "Quran").toUpperCase(Locale.US);
                reference = name + " " + surah.optInt("number") + ":" + a.optInt("numberInSurah");
            }
            if ("quran-uthmani".equals(id) || "ar".equals(a.getJSONObject("edition").optString("language"))) {
                if (arabic.isEmpty()) arabic = text;
            } else {
                malay = text;
            }
        }
        if (arabic.isEmpty()) return null;
        if (malay.isEmpty()) malay = arabic;
        return new Ayat(reference, arabic, malay);
    }

    private static void save(Context ctx, String slot, Ayat a) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(slot + "_ref", a.reference)
                .putString(slot + "_ar", a.arabic)
                .putString(slot + "_ms", a.translation)
                .apply();
    }

    private static volatile Ayat cachedToday;
    private static volatile Ayat cachedRand;

    private static void loadCache(Context context) {
        if (context == null || cachedToday != null) return;
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!todayKey().equals(sp.getString("day", ""))) return;
        cachedToday = fromPrefs(sp, "today");
        cachedRand = fromPrefs(sp, "rand");
    }

    private static Ayat fromPrefs(SharedPreferences sp, String slot) {
        String ar = sp.getString(slot + "_ar", "");
        if (ar == null || ar.isEmpty()) return null;
        return new Ayat(
                sp.getString(slot + "_ref", ""),
                ar,
                sp.getString(slot + "_ms", ""));
    }
}
