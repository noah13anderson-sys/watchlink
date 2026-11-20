package dev.fishtank.watchlink;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.Handler;
import android.os.Looper;

/** "Find my phone" from the watch (07 02): ring loudly for up to 30 s, with a Stop button. */
final class FindPhone {
    static final String ACTION_STOP = "dev.fishtank.watchlink.STOP_RING";
    private static Ringtone ringtone;
    private static int savedVolume = -1;
    private static final Handler h = new Handler(Looper.getMainLooper());

    private FindPhone() {}

    static void start(Context c) {
        if (ringtone != null && ringtone.isPlaying()) return;
        ringtone = RingtoneManager.getRingtone(c, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));
        if (ringtone == null) return;
        ringtone.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
        android.media.AudioManager am = c.getSystemService(android.media.AudioManager.class);
        savedVolume = am.getStreamVolume(android.media.AudioManager.STREAM_ALARM);
        am.setStreamVolume(android.media.AudioManager.STREAM_ALARM, am.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM), 0);
        ringtone.setLooping(true);
        ringtone.play();

        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("find", "Find phone", NotificationManager.IMPORTANCE_HIGH));
        PendingIntent stop = PendingIntent.getForegroundService(c, 0,
                new Intent(c, WatchService.class).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE);
        nm.notify(2, new Notification.Builder(c, "find")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Your watch is looking for this phone")
                .setContentText("Tap to stop ringing")
                .setContentIntent(stop)
                .addAction(new Notification.Action.Builder(null, "Stop", stop).build())
                .setAutoCancel(true).build());
        h.postDelayed(() -> stop(c), 30000);
    }

    static void stop(Context c) {
        h.removeCallbacksAndMessages(null);
        if (ringtone != null) ringtone.stop();
        if (savedVolume >= 0) {   // put the alarm volume back
            c.getSystemService(android.media.AudioManager.class)
                    .setStreamVolume(android.media.AudioManager.STREAM_ALARM, savedVolume, 0);
            savedVolume = -1;
        }
        c.getSystemService(NotificationManager.class).cancel(2);
    }
}
