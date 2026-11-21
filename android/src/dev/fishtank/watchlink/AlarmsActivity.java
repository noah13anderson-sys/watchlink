package dev.fishtank.watchlink;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Up to 5 alarms, stored on the phone and sent to the watch (v3 0x0E) a moment after each change. */
public class AlarmsActivity extends Activity {
    static final int COUNT = 5;
    static final String[] DAYS = {"Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"};
    private SharedPreferences p;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        p = getSharedPreferences(WatchService.PREFS, 0);
        LinearLayout root = Ui.column(this);
        int pad = Ui.dp(this, 16);
        root.setPadding(pad, Ui.dp(this, 28), pad, pad);
        root.addView(Ui.label(this, "Alarms", 28, true, Ui.text(this)));
        TextView sub = Ui.label(this, "Changes go to the watch straight away. Switched-off alarms are removed from it.",
                14, false, Ui.subtext(this));
        sub.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 16));
        root.addView(sub);

        for (int i = 0; i < COUNT; i++) card(root, i);

        LinearLayout send = new LinearLayout(this);
        Ui.primary(this, send, "Send to watch again", v -> {
            sendAll(this, p);
            Toast.makeText(this, "Sent", Toast.LENGTH_SHORT).show();
        });
        root.addView(send, new LinearLayout.LayoutParams(-1, -2));

        Ui.section(this, root, "Testing");
        LinearLayout test = Ui.card(this, root);
        Ui.row(this, test, "Test alarm in 2 minutes", "Uses a spare slot, so your alarms stay as they are", v -> {
            java.util.Calendar c = java.util.Calendar.getInstance();
            c.add(java.util.Calendar.MINUTE, 2);
            testAlarm = new IdoProtocol.Alarm(true, c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE), 0x7f, "Test");
            sendAll(this, p);
            Toast.makeText(this, "Test alarm set for " + fmt(testAlarm.hour, testAlarm.minute), Toast.LENGTH_LONG).show();
        });
        Ui.divider(this, test);
        Ui.row(this, test, "Remove test alarm", null, v -> { testAlarm = null; sendAll(this, p); });

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.bg(this));
        sv.addView(root);
        setContentView(sv);
        getWindow().setStatusBarColor(Ui.bg(this));
        getWindow().setNavigationBarColor(Ui.bg(this));
        if (getActionBar() != null) getActionBar().hide();
    }

    private void card(LinearLayout root, int i) {
        LinearLayout c = Ui.card(this, root);
        String k = "alarm" + i;

        LinearLayout top = new LinearLayout(this);
        top.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView time = Ui.label(this, show(p.getInt(k + "h", 7), p.getInt(k + "m", 0)), 34, true, Ui.text(this));
        time.setOnClickListener(v -> new TimePickerDialog(this, (tp, hh, mm) -> {
            p.edit().putInt(k + "h", hh).putInt(k + "m", mm).apply();
            time.setText(show(hh, mm));
            changed();
        }, p.getInt(k + "h", 7), p.getInt(k + "m", 0), android.text.format.DateFormat.is24HourFormat(this)).show());
        top.addView(time, new LinearLayout.LayoutParams(0, -2, 1));
        android.widget.Switch on = new android.widget.Switch(this);
        on.setChecked(p.getBoolean(k + "on", false));
        android.content.res.ColorStateList st = new android.content.res.ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}}, new int[]{Ui.ACCENT, Ui.subtext(this)});
        on.setThumbTintList(st);
        on.setTrackTintList(st);
        on.setOnCheckedChangeListener((v, ch) -> { p.edit().putBoolean(k + "on", ch).apply(); changed(); });
        top.addView(on);
        c.addView(top);
        TextView name = Ui.label(this, "Alarm " + (i + 1) + "  ·  tap the time to change it", 13, false, Ui.subtext(this));
        name.setPadding(0, 0, 0, Ui.dp(this, 12));
        c.addView(name);

        LinearLayout days = new LinearLayout(this);
        for (int d = 0; d < 7; d++) {
            final int bit = 1 << d;
            TextView chip = Ui.label(this, DAYS[d], 14, true, Ui.text(this));
            chip.setGravity(android.view.Gravity.CENTER);
            chip.setPadding(0, Ui.dp(this, 9), 0, Ui.dp(this, 9));
            Runnable paint = () -> {
                boolean sel = (p.getInt(k + "days", 0x1f) & bit) != 0;
                chip.setTextColor(sel ? Ui.ON_ACCENT : Ui.subtext(this));
                chip.setBackground(Ui.rounded(sel ? Ui.ACCENT : 0xFF2A2F44, Ui.dp(this, 10)));
            };
            paint.run();
            chip.setOnClickListener(v -> {
                p.edit().putInt(k + "days", p.getInt(k + "days", 0x1f) ^ bit).apply();
                paint.run();
                changed();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            lp.setMargins(Ui.dp(this, 2), 0, Ui.dp(this, 2), 0);
            days.addView(chip, lp);
        }
        c.addView(days);
    }

    static String fmt(int h, int m) { return String.format("%02d:%02d", h, m); }

    private String show(int h, int m) { return show(this, h, m); }

    /** The time as the phone shows it (24-hour or 12-hour). */
    static String show(android.content.Context c, int h, int m) {
        if (android.text.format.DateFormat.is24HourFormat(c)) return fmt(h, m);
        return String.format(java.util.Locale.getDefault(), "%d:%02d %s", (h + 11) % 12 + 1, m, h < 12 ? "am" : "pm");
    }

    private final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable sender = () -> {
        sendAll(this, p);
        Toast.makeText(this, WatchService.instance == null ? "Saved. Not connected, so open this again later to send"
                : "Alarms sent to the watch", Toast.LENGTH_SHORT).show();
    };

    /** Send a moment after the last change, so several quick taps send once. */
    private void changed() {
        h.removeCallbacks(sender);
        h.postDelayed(sender, 1500);
    }

    @Override protected void onDestroy() {
        if (h.hasCallbacks(sender)) { h.removeCallbacks(sender); sendAll(this, p); }
        super.onDestroy();
    }

    static IdoProtocol.Alarm testAlarm;

    static void sendAll(android.content.Context c, SharedPreferences p) {
        List<IdoProtocol.Alarm> list = new ArrayList<>();
        for (int i = 0; i < COUNT; i++) {
            boolean on = p.getBoolean("alarm" + i + "on", false);
            list.add(!on ? null : new IdoProtocol.Alarm(true,
                    p.getInt("alarm" + i + "h", 7), p.getInt("alarm" + i + "m", 0),
                    p.getInt("alarm" + i + "days", 0x1f), "Alarm " + (i + 1)));
        }
        while (list.size() < 9) list.add(null);
        list.add(testAlarm);   // slot 10 is reserved for the test alarm
        WatchService.sendV3(IdoProtocol.V3_SET_ALARMS, IdoProtocol.alarmsClearAllV3());
        WatchService.sendV3(IdoProtocol.V3_SET_ALARMS, IdoProtocol.alarmsV3(list));
    }
}
