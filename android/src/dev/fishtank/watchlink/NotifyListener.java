package dev.fishtank.watchlink;

import android.app.Notification;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.HashMap;
import java.util.Map;

/** Forwards phone notifications to the watch. */
public class NotifyListener extends NotificationListenerService {
    private final Map<String, Long> recent = new HashMap<>();

    @Override public void onListenerConnected() { WatchService.startIfAllowed(this); }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn.isOngoing() || getPackageName().equals(sbn.getPackageName())) return;
        Notification n = sbn.getNotification();
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
        if (!getSharedPreferences(WatchService.PREFS, 0).getBoolean("app:" + sbn.getPackageName(), true)) return;

        Bundle e = n.extras;
        CharSequence title = e.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = e.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (text == null) text = e.getCharSequence(Notification.EXTRA_TEXT);
        if (title == null && text == null) return;

        // skip duplicates (apps often re-post the same notification)
        String key = sbn.getPackageName() + "|" + title + "|" + text;
        long now = System.currentTimeMillis();
        Long last = recent.get(key);
        if (last != null && now - last < 10000) return;
        recent.put(key, now);

        String app;
        try {
            PackageManager pm = getPackageManager();
            app = pm.getApplicationLabel(pm.getApplicationInfo(sbn.getPackageName(), 0)).toString();
        } catch (Exception ex) { app = sbn.getPackageName(); }

        String sender = app + (title != null ? ": " + title : "");
        WatchService.sendV3(IdoProtocol.V3_NOTICE,
                IdoProtocol.noticeV3(0x01, sender, "", text == null ? "" : text.toString()));
    }
}
