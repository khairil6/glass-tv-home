package com.gathin.tvhome;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Azan when a prayer time begins. Home asks before stopping it.
 * Styles in res/raw: Malaysia CC0 Beautiful_adhan (Wikimedia),
 * Madinah Sheikh Mansour Al-Zahrani (AlAdhan / Islamic Network),
 * Makkah CC BY Masjid al-Haram field recording.
 */
final class AzanPlayer {
    private static final String TAG = "TvHome.Azan";
    private static final String PREFS = "azan_prefs";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_STYLE = "style";
    private static final String KEY_LAST = "last_played";

    static final String STYLE_MALAYSIA = "malaysia";
    static final String STYLE_MADINAH = "madinah";
    static final String STYLE_MAKKAH = "makkah";
    static final String STYLE_OFF = "off";
    static final String EXTRA_PRAYER = "prayer";
    static final String EXTRA_CONFIRM = "azan_confirm";
    static final String ACTION_AZAN = "com.gathin.tvhome.AZAN";
    private static final long WINDOW_MS = 3 * 60 * 1000L;
    private static final int ALARM_BASE = 7201;

    private static volatile boolean playing;
    private static volatile boolean confirmVisible;
    private static volatile boolean pendingConfirm;
    private static volatile String currentPrayer = "";

    private AzanPlayer() {
    }

    static boolean isEnabled(Context ctx) {
        return !STYLE_OFF.equals(getStyle(ctx));
    }

    static String getStyle(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (sp.contains(KEY_STYLE)) return sp.getString(KEY_STYLE, STYLE_MALAYSIA);
        return sp.getBoolean(KEY_ENABLED, true) ? STYLE_MALAYSIA : STYLE_OFF;
    }

    static String getStyleDisplayName(Context ctx) {
        switch (getStyle(ctx)) {
            case STYLE_MADINAH:
                return "Madinah";
            case STYLE_MAKKAH:
                return "Makkah";
            case STYLE_OFF:
                return "Mati";
            default:
                return "Malaysia";
        }
    }

    static void setStyle(Context ctx, String style) {
        if (style == null || style.isEmpty()) style = STYLE_MALAYSIA;
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_STYLE, style)
                .putBoolean(KEY_ENABLED, !STYLE_OFF.equals(style))
                .apply();
        if (STYLE_OFF.equals(style)) {
            cancelAlarms(ctx);
            stop(ctx);
        } else {
            scheduleAlarms(ctx);
        }
    }

    static int audioRes(Context ctx) {
        switch (getStyle(ctx)) {
            case STYLE_MADINAH:
                return R.raw.azan_madinah;
            case STYLE_MAKKAH:
                return R.raw.azan_makkah;
            default:
                return R.raw.azan_malaysia;
        }
    }

    static void setEnabled(Context ctx, boolean on) {
        setStyle(ctx, on ? STYLE_MALAYSIA : STYLE_OFF);
    }

    static boolean isPlaying() {
        return playing || AzanService.isRunning();
    }

    static String currentPrayer() {
        return currentPrayer;
    }

    static boolean isConfirmVisible() {
        return confirmVisible;
    }

    static void setConfirmVisible(boolean visible) {
        confirmVisible = visible;
    }

    static void markPlaying(String prayer) {
        playing = true;
        pendingConfirm = true;
        currentPrayer = prayer != null ? prayer : "";
    }

    static void markStopped() {
        playing = false;
        confirmVisible = false;
        pendingConfirm = false;
        currentPrayer = "";
    }

    static boolean takePendingConfirm() {
        boolean p = pendingConfirm;
        pendingConfirm = false;
        return p;
    }

    static void play(Context ctx, String prayer) {
        if (ctx == null || !isEnabled(ctx) || playing) return;
        if (prayer == null || prayer.isEmpty()) return;
        String stamp = todayKey() + ":" + prayer;
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (stamp.equals(sp.getString(KEY_LAST, ""))) return;
        sp.edit().putString(KEY_LAST, stamp).apply();
        startService(ctx, prayer);
    }

    /** Play the selected azan now (settings preview). Does not consume today's prayer stamp. */
    static void preview(Context ctx) {
        if (ctx == null || !isEnabled(ctx)) return;
        String prayer = "solat";
        PrayerTimes.Slot due = PrayerTimes.dueAzan(ctx, System.currentTimeMillis(), WINDOW_MS);
        if (due != null) prayer = due.name;
        else {
            List<PrayerTimes.Slot> upcoming = PrayerTimes.upcomingAzan(ctx, System.currentTimeMillis());
            if (!upcoming.isEmpty()) prayer = upcoming.get(0).name;
        }
        startService(ctx, prayer);
    }

    private static void startService(Context ctx, String prayer) {
        Log.i(TAG, "starting azan for " + prayer);
        markPlaying(prayer);
        Intent i = new Intent(ctx, AzanService.class).putExtra(EXTRA_PRAYER, prayer);
        try {
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
            else ctx.startService(i);
        } catch (Exception e) {
            Log.w(TAG, "could not start azan: " + e.getMessage());
            markStopped();
        }
    }

    static void stop(Context ctx) {
        confirmVisible = false;
        try {
            ctx.stopService(new Intent(ctx, AzanService.class));
        } catch (Exception ignored) {
        }
        markStopped();
    }

    /** If a prayer started in the last few minutes and azan has not played yet. */
    static void checkAndPlay(Context ctx) {
        if (!isEnabled(ctx) || playing) return;
        PrayerTimes.Slot due = PrayerTimes.dueAzan(ctx, System.currentTimeMillis(), WINDOW_MS);
        if (due != null) play(ctx, due.name);
    }

    static void scheduleAlarms(Context ctx) {
        if (!isEnabled(ctx)) {
            cancelAlarms(ctx);
            return;
        }
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        cancelAlarms(ctx);
        List<PrayerTimes.Slot> upcoming = PrayerTimes.upcomingAzan(ctx, System.currentTimeMillis());
        int n = Math.min(upcoming.size(), 6);
        for (int i = 0; i < n; i++) {
            PrayerTimes.Slot slot = upcoming.get(i);
            Intent intent = new Intent(ACTION_AZAN)
                    .setClass(ctx, AzanReceiver.class)
                    .putExtra(EXTRA_PRAYER, slot.name);
            PendingIntent pi = PendingIntent.getBroadcast(ctx, ALARM_BASE + i, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            try {
                if (Build.VERSION.SDK_INT >= 23) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, slot.atMs, pi);
                } else {
                    am.setExact(AlarmManager.RTC_WAKEUP, slot.atMs, pi);
                }
            } catch (Exception e) {
                Log.w(TAG, "alarm " + slot.name + ": " + e.getMessage());
            }
        }
        Log.i(TAG, "scheduled " + n + " azan alarm(s)");
    }

    private static void cancelAlarms(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        for (int i = 0; i < 6; i++) {
            Intent intent = new Intent(ACTION_AZAN).setClass(ctx, AzanReceiver.class);
            PendingIntent pi = PendingIntent.getBroadcast(ctx, ALARM_BASE + i, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.cancel(pi);
        }
    }

    private static String todayKey() {
        Calendar c = Calendar.getInstance();
        return String.format(Locale.US, "%04d-%02d-%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }
}
