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
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(40, 80, 40, 40);

        TextView t = new TextView(this);
        t.setText("Alarms");
        t.setTextSize(22);
        l.addView(t);

        for (int i = 0; i < COUNT; i++) l.addView(row(i));

        Button send = new Button(this);
        send.setText("Send alarms to watch");
        send.setOnClickListener(v -> { sendAll(this, p); Toast.makeText(this, "Sent", Toast.LENGTH_SHORT).show(); });
        l.addView(send);

        Button test = new Button(this);
        test.setText("Test alarm in 2 minutes");
        test.setOnClickListener(v -> {
            java.util.Calendar c = java.util.Calendar.getInstance();
            c.add(java.util.Calendar.MINUTE, 2);
            testAlarm = new IdoProtocol.Alarm(true, c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE), 0x7f, "Test");
            sendAll(this, p);
            Toast.makeText(this, "Test alarm set for " + fmt(testAlarm.hour, testAlarm.minute), Toast.LENGTH_LONG).show();
        });
        l.addView(test);
        Button clear = new Button(this);
        clear.setText("Remove test alarm");
        clear.setOnClickListener(v -> { testAlarm = null; sendAll(this, p); });
        l.addView(clear);

        TextView note = new TextView(this);
        note.setPadding(0, 32, 0, 0);
        note.setText("Ticked alarms are sent to the watch; unticked ones are removed from it.");
        l.addView(note);

        ScrollView sv = new ScrollView(this);
        sv.addView(l);
        sv.setBackgroundColor(Ui.bg(this));
        setContentView(sv);
        Ui.style(this, sv);
    }

    private LinearLayout row(int i) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(0, 24, 0, 24);

        LinearLayout top = new LinearLayout(this);
        CheckBox on = new CheckBox(this);
        on.setText("Alarm " + (i + 1));
        on.setChecked(p.getBoolean("alarm" + i + "on", false));
        on.setOnCheckedChangeListener((v, c) -> p.edit().putBoolean("alarm" + i + "on", c).apply());
        top.addView(on);

        Button time = new Button(this);
        time.setText(fmt(p.getInt("alarm" + i + "h", 7), p.getInt("alarm" + i + "m", 0)));
        time.setOnClickListener(v -> new TimePickerDialog(this, (tp, hh, mm) -> {
            p.edit().putInt("alarm" + i + "h", hh).putInt("alarm" + i + "m", mm).apply();
            time.setText(fmt(hh, mm));
        }, p.getInt("alarm" + i + "h", 7), p.getInt("alarm" + i + "m", 0), true).show());
        top.addView(time);
        r.addView(top);

        LinearLayout days = new LinearLayout(this);
        int mask = p.getInt("alarm" + i + "days", 0x1f);   // default Mon-Fri
        for (int d = 0; d < 7; d++) {
            CheckBox cb = new CheckBox(this);
            cb.setText(DAYS[d]);
            cb.setChecked((mask & (1 << d)) != 0);
            final int bit = 1 << d;
            cb.setOnCheckedChangeListener((v, c) -> {
                int m = p.getInt("alarm" + i + "days", 0x1f);
                p.edit().putInt("alarm" + i + "days", c ? (m | bit) : (m & ~bit)).apply();
            });
            days.addView(cb);
        }
        r.addView(days);
        return r;
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
