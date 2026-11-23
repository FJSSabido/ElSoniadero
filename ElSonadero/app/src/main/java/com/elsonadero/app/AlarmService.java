package com.elsonadero.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** Hace sonar la alarma (volumen creciente + vibración) y muestra la pantalla de despertar. */
public class AlarmService extends Service {

    public static final String ACTION_RING = "ring";
    public static final String ACTION_SNOOZE = "snooze";
    public static final String ACTION_STOP = "stop";
    private static final String CHANNEL = "alarma";
    private static final int NOTIF_ID = 7;
    private static final long AUTO_STOP_MS = 10 * 60_000L;

    public static volatile boolean ringing = false;

    private MediaPlayer player;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private float volume = 0.08f;

    private final Runnable ramp = new Runnable() {
        @Override
        public void run() {
            if (player == null) return;
            volume = Math.min(1f, volume + 0.04f);
            try { player.setVolume(volume, volume); } catch (Exception ignored) {}
            if (volume < 1f) handler.postDelayed(this, 1000);
        }
    };

    private final Runnable autoStop = this::stopSelf;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_RING : intent.getAction();
        if (ACTION_SNOOZE.equals(action)) {
            AlarmScheduler.snooze(this);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        String label = intent == null ? "" : intent.getStringExtra("label");
        Notification n = buildNotification(label);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIF_ID, n);
        }
        if (!ringing) startRinging();
        return START_NOT_STICKY;
    }

    private Notification buildNotification(String label) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Alarma", NotificationManager.IMPORTANCE_HIGH);
            ch.setSound(null, null);
            ch.enableVibration(false);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);
        }
        int f = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        Intent ring = new Intent(this, RingActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                .putExtra("label", label);
        PendingIntent ringPi = PendingIntent.getActivity(this, 10, ring, f);
        PendingIntent recordPi = PendingIntent.getActivity(this, 11,
                new Intent(this, RecordActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), f);
        PendingIntent snoozePi = PendingIntent.getService(this, 12,
                new Intent(this, AlarmService.class).setAction(ACTION_SNOOZE), f);

        String text = (label != null && !label.isEmpty()) ? label : "Buenos días. ¿Qué has soñado?";
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_moon)
                .setColor(getColor(R.color.neon))
                .setContentTitle("El Soñadero")
                .setContentText(text)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setFullScreenIntent(ringPi, true)
                .setContentIntent(ringPi)
                .addAction(new Notification.Action.Builder(null, "Posponer " + AlarmScheduler.SNOOZE_MINUTES + " min", snoozePi).build())
                .addAction(new Notification.Action.Builder(null, "Apagar y grabar", recordPi).build())
                .build();
    }

    private void startRinging() {
        ringing = true;
        PowerManager pm = getSystemService(PowerManager.class);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sonadero:alarm");
        wakeLock.acquire(AUTO_STOP_MS + 5000);

        Uri uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);
        if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            player.setDataSource(this, uri);
            player.setLooping(true);
            player.prepare();
            player.setVolume(volume, volume);
            player.start();
            handler.postDelayed(ramp, 1000);
        } catch (Exception e) {
            player = null;
        }

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 700, 900}, 0),
                    new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
        }
        handler.postDelayed(autoStop, AUTO_STOP_MS);
    }

    @Override
    public void onDestroy() {
        ringing = false;
        handler.removeCallbacksAndMessages(null);
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) {}
            player.release();
            player = null;
        }
        if (vibrator != null) vibrator.cancel();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        stopForeground(STOP_FOREGROUND_REMOVE);
        sendBroadcast(new Intent(RingActivity.ACTION_FINISH).setPackage(getPackageName()));
        super.onDestroy();
    }
}
