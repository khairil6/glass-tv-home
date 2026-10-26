package com.gathin.tvhome;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

/** Plays the azan over a foreground service so it continues while another app is in front. */
public class AzanService extends Service {
    private static final String TAG = "TvHome.Azan";
    private static final String CHANNEL = "azan";
    private static final int NOTIF_ID = 72;
    private static volatile AzanService running;
    private MediaPlayer player;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;

    static boolean isRunning() {
        return running != null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = this;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String prayer = intent != null ? intent.getStringExtra(AzanPlayer.EXTRA_PRAYER) : "Solat";
        if (prayer == null || prayer.isEmpty()) prayer = "Solat";
        AzanPlayer.markPlaying(prayer);
        startFg(prayer);
        startPlayback();
        return START_NOT_STICKY;
    }

    private void startFg(String prayer) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Azan", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Azan bila masuk waktu solat");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(AzanPlayer.EXTRA_CONFIRM, true);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        Notification n = b.setContentTitle("Azan " + prayer)
                .setContentText("Tekan Home untuk henti azan")
                .setSmallIcon(R.drawable.ic_mosque)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    private void startPlayback() {
        stopPlayback();
        AssetFileDescriptor afd = null;
        try {
            audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            requestFocus(attrs);
            afd = getResources().openRawResourceFd(AzanPlayer.audioRes(this));
            if (afd == null) {
                Log.w(TAG, "missing azan audio");
                stopSelf();
                return;
            }
            player = new MediaPlayer();
            player.setAudioAttributes(attrs);
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            player.setOnCompletionListener(mp -> stopSelf());
            player.setOnErrorListener((mp, what, extra) -> {
                Log.w(TAG, "playback error " + what + "/" + extra);
                stopSelf();
                return true;
            });
            player.prepare();
            player.start();
            Log.i(TAG, "playback started");
            MainActivity main = MainActivity.getInstance();
            if (main != null) {
                AzanPlayer.takePendingConfirm();
                main.runOnUiThread(main::showAzanStopConfirm);
            }
        } catch (Exception e) {
            Log.w(TAG, "playback failed: " + e.getMessage());
            stopSelf();
        } finally {
            if (afd != null) {
                try {
                    afd.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void requestFocus(AudioAttributes attrs) {
        if (audioManager == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setAudioAttributes(attrs)
                        .setOnAudioFocusChangeListener(i -> {})
                        .build();
                audioManager.requestAudioFocus(focusRequest);
            } else {
                audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            }
        } catch (Exception ignored) {
        }
    }

    private void stopPlayback() {
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
            }
            try {
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
        if (audioManager != null) {
            try {
                if (Build.VERSION.SDK_INT >= 26 && focusRequest != null) {
                    audioManager.abandonAudioFocusRequest(focusRequest);
                } else {
                    audioManager.abandonAudioFocus(null);
                }
            } catch (Exception ignored) {
            }
        }
        focusRequest = null;
    }

    @Override
    public void onDestroy() {
        if (running == this) running = null;
        stopPlayback();
        AzanPlayer.markStopped();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
