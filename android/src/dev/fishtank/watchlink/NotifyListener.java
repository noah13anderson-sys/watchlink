package dev.fishtank.watchlink;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.SharedPreferences;
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
        SharedPreferences p = getSharedPreferences(WatchService.PREFS, 0);
        if (!p.getBoolean("app:" + sbn.getPackageName(), true)) return;
        if (quiet(sbn, p)) return;

        Bundle e = n.extras;
        CharSequence title = e.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = e.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (text == null) text = e.getCharSequence(Notification.EXTRA_TEXT);
        if (title == null && text == null) return;

        // skip duplicates (apps often re-post the same notification); forget entries after a minute
        String key = sbn.getPackageName() + "|" + title + "|" + text;
        long now = System.currentTimeMillis();
        recent.values().removeIf(t -> now - t > 60000);
        Long last = recent.get(key);
        if (last != null && now - last < 10000) return;
        recent.put(key, now);

        String app;
        try {
            PackageManager pm = getPackageManager();
            app = pm.getApplicationLabel(pm.getApplicationInfo(sbn.getPackageName(), 0)).toString();
        } catch (Exception ex) { app = sbn.getPackageName(); }

        // When the watch shows the app's own icon (WhatsApp, Gmail, …) the sender alone is enough;
        // other apps get the SMS icon, so their name goes in front of the sender.
        int icon = p.getBoolean("appIcons", true) ? IdoProtocol.knownAppType(sbn.getPackageName()) : 0;
        String sender = icon != 0 ? (title != null ? title.toString() : app) : app + (title != null ? ": " + title : "");
        WatchService.sendV3(IdoProtocol.V3_NOTICE,
                IdoProtocol.noticeV3(icon != 0 ? icon : 0x01, sender, "", text == null ? "" : text.toString()));
    }

    /**
     * Notifications the phone itself keeps quiet stay off the watch too: silent ones (low importance), and, when
     * "Quiet with phone's Do not disturb" is on, ones that Do not disturb holds back.
     */
    private boolean quiet(StatusBarNotification sbn, SharedPreferences p) {
        try {
            Ranking r = new Ranking();
            if (!getCurrentRanking().getRanking(sbn.getKey(), r)) return false;
            if (p.getBoolean("skipSilent", true) && r.getImportance() < NotificationManager.IMPORTANCE_DEFAULT) return true;
            return p.getBoolean("followDnd", true) && !r.matchesInterruptionFilter();
        } catch (Exception ignored) {
            return false;
        }
    }
}
