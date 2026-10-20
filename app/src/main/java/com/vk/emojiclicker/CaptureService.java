package com.vk.emojiclicker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.os.IBinder;

/**
 * Foreground service that owns the MediaProjection.
 * Required on Android 10+ so the screen capture actually works:
 * MediaProjection must be created while the app is running a foreground
 * service. Shows a persistent notification "Захват экрана идёт".
 */
public class CaptureService extends Service {

    private static final String CHANNEL_ID = "capture_channel";
    private static final int NOTIF_ID = 1;
    public static ScreenCapture capture = null;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int resultCode = intent != null ? intent.getIntExtra("resultCode", 0) : 0;
        Intent data = intent != null ? (android.content.Intent) intent.getParcelableExtra("data") : null;
        startForegroundCompat();
        if (capture == null) capture = new ScreenCapture();
        boolean ok = data != null && capture.start(this, resultCode, data);
        if (!ok) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    private void startForegroundCompat() {
        Intent si = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, si, PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("VK Emoji Pro")
                .setContentText("Захват экрана идёт")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
        try {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } catch (Exception e) {
            startForeground(NOTIF_ID, n);
        }
    }

    private void createChannel() {
        try {
            NotificationManager nm = getSystemService(NotificationManager.class);
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Захват экрана",
                    NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        } catch (Exception ignored) {}
    }

    /** Текущий скриншот (если захват активен). */
    public static Bitmap grabScreen() {
        try {
            if (capture != null && capture.isRunning()) return capture.grab();
        } catch (Exception ignored) {}
        return null;
    }

    public static boolean isCapturing() {
        return capture != null && capture.isRunning();
    }

    @Override
    public void onDestroy() {
        if (capture != null) {
            try { capture.stop(); } catch (Exception ignored) {}
            capture = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}