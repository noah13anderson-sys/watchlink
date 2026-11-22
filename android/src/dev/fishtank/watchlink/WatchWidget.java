package dev.fishtank.watchlink;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.format.DateFormat;
import android.widget.RemoteViews;

import java.util.Date;
import java.util.Locale;

/** Home screen widget: watch connection and battery, today's steps, heart rate and blood oxygen. */
public class WatchWidget extends AppWidgetProvider {
    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        WatchService.startIfAllowed(c);
        update(c);
    }

    /** Redraw every WatchLink widget; called whenever the watch sends new numbers. */
    static void update(Context c) {
        try {
            SharedPreferences p = c.getSharedPreferences(WatchService.PREFS, 0);
            String today = HealthDb.today();
            // keep the latest numbers (even with no widget yet) so a widget added later, or after the app
            // restarts, starts with them
            SharedPreferences.Editor e = p.edit();
            if (WatchService.steps >= 0) e.putLong("w.steps", WatchService.steps).putString("w.day", today);
            if (WatchService.hr.time >= 0) e.putInt("w.hr", WatchService.hr.value).putString("w.hrDay", today);
            if (WatchService.spo2.time >= 0) e.putInt("w.spo2", WatchService.spo2.value).putString("w.spo2Day", today);
            if (WatchService.battery >= 0) e.putInt("w.batt", WatchService.battery);
            if (WatchService.lastUpdate > 0) e.putLong("w.at", WatchService.lastUpdate);
            e.apply();

            AppWidgetManager m = AppWidgetManager.getInstance(c);
            int[] ids = m.getAppWidgetIds(new ComponentName(c, WatchWidget.class));
            if (ids != null && ids.length > 0) m.updateAppWidget(ids, views(c));
        } catch (Exception ex) {
            android.util.Log.w(WatchService.TAG, "widget", ex);
        }
    }

    /** The widget's content from the saved numbers. */
    static RemoteViews views(Context c) {
        SharedPreferences p = c.getSharedPreferences(WatchService.PREFS, 0);
        String today = HealthDb.today();
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
        boolean connected = "Connected".equals(WatchService.status);
        v.setTextViewText(R.id.w_status, connected ? "●  Momax watch" : "○  Watch not connected");
        int b = p.getInt("w.batt", -1);
        v.setTextViewText(R.id.w_battery, b < 0 ? "" : "🔋 " + b + "%");
        v.setTextColor(R.id.w_battery, b >= 0 && b <= 20 ? Ui.BAD : Ui.GOOD);
        v.setTextViewText(R.id.w_steps, today.equals(p.getString("w.day", ""))
                ? String.format(Locale.getDefault(), "%,d", p.getLong("w.steps", 0)) : "–");
        v.setTextViewText(R.id.w_hr, today.equals(p.getString("w.hrDay", "")) ? String.valueOf(p.getInt("w.hr", 0)) : "–");
        v.setTextViewText(R.id.w_spo2, today.equals(p.getString("w.spo2Day", "")) ? p.getInt("w.spo2", 0) + "%" : "–");
        long at = p.getLong("w.at", 0);
        v.setTextViewText(R.id.w_updated, at == 0 ? "Open WatchLink to connect"
                : "Updated " + DateFormat.getTimeFormat(c).format(new Date(at)));
        v.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(c, 0,
                new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE));
        return v;
    }
}
