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

/** Up to 5 alarms, stored locally and sent to the watch with 03 02 (experimental encoding). */
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
        TextView sub = Ui.label(this, "Switched-on alarms are sent to the watch; switched-off ones are removed from it.",
                14, false, Ui.subtext(this));
        sub.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 16));
        root.addView(sub);

        for (int i = 0; i < COUNT; i++) card(root, i);

        LinearLayout send = new LinearLayout(this);
        Ui.primary(this, send, "Send alarms to watch", v -> {
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
        TextView time = Ui.label(this, fmt(p.getInt(k + "h", 7), p.getInt(k + "m", 0)), 34, true, Ui.text(this));
        time.setOnClickListener(v -> new TimePickerDialog(this, (tp, hh, mm) -> {
            p.edit().putInt(k + "h", hh).putInt(k + "m", mm).apply();
            time.setText(fmt(hh, mm));
        }, p.getInt(k + "h", 7), p.getInt(k + "m", 0), true).show());
        top.addView(time, new LinearLayout.LayoutParams(0, -2, 1));
        android.widget.Switch on = new android.widget.Switch(this);
        on.setChecked(p.getBoolean(k + "on", false));
        android.content.res.ColorStateList st = new android.content.res.ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}}, new int[]{Ui.ACCENT, Ui.subtext(this)});
        on.setThumbTintList(st);
        on.setTrackTintList(st);
        on.setOnCheckedChangeListener((v, ch) -> p.edit().putBoolean(k + "on", ch).apply());
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
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            lp.setMargins(Ui.dp(this, 2), 0, Ui.dp(this, 2), 0);
            days.addView(chip, lp);
        }
        c.addView(days);
    }

    static String fmt(int h, int m) { return String.format("%02d:%02d", h, m); }

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
