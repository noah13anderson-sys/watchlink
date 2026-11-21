package dev.fishtank.watchlink;

import android.app.Activity;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Day-by-day history of everything WatchLink has saved: steps, heart rate, stress, blood oxygen, sleep, battery. */
public class HistoryActivity extends Activity {
    private static HistoryActivity current;
    private String day = HealthDb.today();
    private String firstDay;
    private TextView dayLabel, prev, next, stepsVal, stepsSub, hrVal, hrSub, stressVal, stressSub, spo2Val, spo2Sub,
            sleepVal, sleepSub, weekSub, battSub, footer;
    private ChartView stepsChart, hrChart, stressChart, spo2Chart, weekChart, hrWeekChart, battChart;

    /** Called when new health data has been saved. */
    static void refresh() {
        HistoryActivity a = current;
        if (a != null) a.runOnUiThread(a::load);
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = Ui.column(this);
        int pad = Ui.dp(this, 16);
        root.setPadding(pad, Ui.dp(this, 28), pad, Ui.dp(this, 32));
        root.addView(Ui.label(this, "History", 28, true, Ui.text(this)));
        TextView sub = Ui.label(this, "Saved on this phone from your watch", 14, false, Ui.subtext(this));
        sub.setPadding(0, 0, 0, Ui.dp(this, 14));
        root.addView(sub);

        // day switcher
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        prev = chip("‹");
        prev.setOnClickListener(v -> go(-1));
        dayLabel = Ui.label(this, "", 18, true, Ui.text(this));
        dayLabel.setGravity(Gravity.CENTER);
        next = chip("›");
        next.setOnClickListener(v -> go(1));
        bar.addView(prev);
        bar.addView(dayLabel, new LinearLayout.LayoutParams(0, -2, 1));
        bar.addView(next);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.bottomMargin = Ui.dp(this, 14);
        root.addView(bar, blp);

        LinearLayout c = card(root, "Steps");
        stepsVal = big(c); stepsSub = small(c);
        stepsChart = chart(c);
        c = card(root, "Heart rate");
        hrVal = big(c); hrSub = small(c);
        hrChart = chart(c);
        c = card(root, "Stress");
        stressVal = big(c); stressSub = small(c);
        stressChart = chart(c);
        c = card(root, "Blood oxygen");
        spo2Val = big(c); spo2Sub = small(c);
        spo2Chart = chart(c);
        c = card(root, "Sleep");
        sleepVal = big(c); sleepSub = small(c);
        c = card(root, "Last 7 days");
        weekSub = small(c);
        weekChart = chart(c);
        TextView hrw = Ui.label(this, "Average heart rate", 13, false, Ui.subtext(this));
        hrw.setPadding(0, Ui.dp(this, 12), 0, 0);
        c.addView(hrw);
        hrWeekChart = chart(c);
        c = card(root, "Watch battery");
        battSub = small(c);
        battChart = chart(c);

        LinearLayout exp = Ui.card(this, root);
        Ui.row(this, exp, "Export all data", "Save everything as a CSV file in Downloads/WatchLink", v -> export());
        footer = Ui.label(this, "", 13, false, Ui.subtext(this));
        footer.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), 0);
        root.addView(footer);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.bg(this));
        sv.addView(root);
        setContentView(sv);
        Ui.style(this, sv);
    }

    @Override protected void onResume() {
        super.onResume();
        current = this;
        load();
    }

    @Override protected void onPause() { current = null; super.onPause(); }

    private void go(int days) {
        String d = HealthDb.shift(day, days);
        if (d.compareTo(HealthDb.today()) > 0) return;
        if (days < 0 && firstDay != null && d.compareTo(firstDay) < 0) return;
        day = d;
        load();
    }

    // ---- small views ----

    private TextView chip(String s) {
        TextView t = Ui.label(this, s, 22, true, Ui.text(this));
        t.setGravity(Gravity.CENTER);
        int sz = Ui.dp(this, 44);
        t.setLayoutParams(new LinearLayout.LayoutParams(sz, sz));
        t.setBackground(Ui.rounded(Ui.card(this), sz / 2f));
        return t;
    }

    private LinearLayout card(LinearLayout root, String title) {
        LinearLayout c = Ui.card(this, root);
        TextView t = Ui.label(this, title.toUpperCase(), 12, true, Ui.subtext(this));
        t.setLetterSpacing(0.08f);
        c.addView(t);
        return c;
    }

    private TextView big(LinearLayout c) {
        TextView t = Ui.label(this, "–", 26, true, Ui.text(this));
        t.setPadding(0, Ui.dp(this, 4), 0, 0);
        c.addView(t);
        return t;
    }

    private TextView small(LinearLayout c) {
        TextView t = Ui.label(this, "", 13, false, Ui.subtext(this));
        t.setPadding(0, 0, 0, Ui.dp(this, 8));
        c.addView(t);
        return t;
    }

    private ChartView chart(LinearLayout c) {
        ChartView v = new ChartView(this);
        c.addView(v, new LinearLayout.LayoutParams(-1, -2));
        return v;
    }

    // ---- data ----

    /** Everything one screen needs, read on the database thread. */
    private static final class Data {
        String day, firstDay;
        List<long[]> hr, stress, spo2, steps, batt;
        long[] totals;
        long[] weekSteps = new long[7];
        float[] weekHr = new float[7];
        String[] weekNames = new String[7];
        int hrCount, stressCount, spo2Count;
    }

    private void load() {
        final String d = day;
        HealthDb.run(() -> {
            HealthDb db = HealthDb.get(this);
            Data x = new Data();
            x.day = d;
            long start = HealthDb.midnight(d), end = HealthDb.midnight(HealthDb.shift(d, 1));
            x.hr = db.samples(HealthDb.HR, start, end);
            x.stress = db.samples(HealthDb.STRESS, start, end);
            x.spo2 = db.samples(HealthDb.SPO2, start, end);
            x.steps = db.samples(HealthDb.STEPS, start, end);
            x.batt = db.samples(HealthDb.BATTERY, start, end);
            x.totals = db.dayTotals(d);
            x.firstDay = db.firstDay();
            SimpleDateFormat wd = new SimpleDateFormat("EEE", Locale.getDefault());
            for (int i = 0; i < 7; i++) {
                String dd = HealthDb.shift(d, i - 6);
                long[] t = db.dayTotals(dd);
                x.weekSteps[i] = t == null ? 0 : Math.max(0, t[0]);
                List<long[]> h = db.samples(HealthDb.HR, HealthDb.midnight(dd), HealthDb.midnight(HealthDb.shift(dd, 1)));
                long sum = 0;
                for (long[] s : h) sum += s[1];
                x.weekHr[i] = h.isEmpty() ? 0 : (float) sum / h.size();
                x.weekNames[i] = wd.format(new java.util.Date(HealthDb.midnight(dd) * 1000));
            }
            x.hrCount = db.count(HealthDb.HR);
            x.stressCount = db.count(HealthDb.STRESS);
            x.spo2Count = db.count(HealthDb.SPO2);
            runOnUiThread(() -> show(x));
        });
    }

    private static float[] hours(List<long[]> s, long start) {
        float[] r = new float[s.size()];
        for (int i = 0; i < r.length; i++) r[i] = (s.get(i)[0] - start) / 3600f;
        return r;
    }

    private static float[] values(List<long[]> s) {
        float[] r = new float[s.size()];
        for (int i = 0; i < r.length; i++) r[i] = s.get(i)[1];
        return r;
    }

    private static String clock(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t * 1000);
        return String.format(Locale.ROOT, "%d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }

    private void show(Data x) {
        if (!x.day.equals(day)) return;
        firstDay = x.firstDay;
        String today = HealthDb.today();
        long start = HealthDb.midnight(x.day);
        String label = x.day.equals(today) ? "Today" : x.day.equals(HealthDb.shift(today, -1)) ? "Yesterday"
                : new SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(new java.util.Date(start * 1000));
        dayLabel.setText(label);
        boolean canNext = !x.day.equals(today), canPrev = firstDay != null && x.day.compareTo(firstDay) > 0;
        next.setAlpha(canNext ? 1f : 0.3f);
        prev.setAlpha(canPrev ? 1f : 0.3f);

        // steps: day total and steps per hour from the running count
        long[] t = x.totals;
        if (t != null && t[0] >= 0) {
            stepsVal.setText(String.format(Locale.getDefault(), "%,d steps", t[0]));
            List<String> parts = new ArrayList<>();
            if (t[1] >= 0) parts.add(t[1] + " kcal");
            if (t[2] >= 0) parts.add(t[2] >= 1000 ? String.format(Locale.getDefault(), "%.2f km", t[2] / 1000.0) : t[2] + " m");
            int goal = getSharedPreferences(WatchService.PREFS, 0).getInt("goal.steps", 8000);
            if (goal > 0) parts.add(Math.round(100f * t[0] / goal) + "% of " + String.format(Locale.getDefault(), "%,d", goal));
            stepsSub.setText(android.text.TextUtils.join(" · ", parts));
        } else {
            stepsVal.setText("–");
            stepsSub.setText("No steps saved for this day");
        }
        float[] perHour = new float[24];
        long prevTotal = 0;
        for (long[] s : x.steps) {
            int hr = (int) Math.min(23, Math.max(0, (s[0] - start) / 3600));
            long diff = s[1] >= prevTotal ? s[1] - prevTotal : s[1];   // the count restarts at midnight
            perHour[hr] += diff;
            prevTotal = s[1];
        }
        String[] hl = new String[24];
        for (int i = 0; i < 24; i++) hl[i] = i % 6 == 0 ? i + ":00" : "";
        stepsChart.bars(hl, perHour, Ui.ACCENT, -1, -1);
        stepsChart.setEmptyText("Steps per hour show up while the phone is connected");

        // heart rate
        if (!x.hr.isEmpty()) {
            long sum = 0, lo = 999, hi = 0;
            for (long[] s : x.hr) { sum += s[1]; lo = Math.min(lo, s[1]); hi = Math.max(hi, s[1]); }
            hrVal.setText(Math.round((float) sum / x.hr.size()) + " bpm average");
            hrSub.setText("Lowest " + lo + " · highest " + hi + " · " + x.hr.size() + " readings, last at "
                    + clock(x.hr.get(x.hr.size() - 1)[0]));
        } else { hrVal.setText("–"); hrSub.setText("No heart rate saved for this day"); }
        hrChart.day(ChartView.LINE, hours(x.hr, start), values(x.hr), 0xFFFF6B81, 40, 200, 0.5f);

        // stress
        if (!x.stress.isEmpty()) {
            long sum = 0;
            for (long[] s : x.stress) sum += s[1];
            int avg = Math.round((float) sum / x.stress.size());
            stressVal.setText(avg + " average");
            stressSub.setText(level(avg) + " · " + x.stress.size() + " readings");
        } else { stressVal.setText("–"); stressSub.setText("No stress readings for this day"); }
        stressChart.day(ChartView.LINE, hours(x.stress, start), values(x.stress), 0xFFFFB86C, 0, 100, 1.5f);

        // blood oxygen
        if (!x.spo2.isEmpty()) {
            long sum = 0, lo = 100;
            for (long[] s : x.spo2) { sum += s[1]; lo = Math.min(lo, s[1]); }
            spo2Val.setText(Math.round((float) sum / x.spo2.size()) + "% average");
            spo2Sub.setText("Lowest " + lo + "% · " + x.spo2.size() + " readings");
        } else { spo2Val.setText("–"); spo2Sub.setText("No blood oxygen readings for this day"); }
        spo2Chart.day(ChartView.DOTS, hours(x.spo2, start), values(x.spo2), 0xFF8AB4F8, 80, 100, 1);

        // sleep (night ending on this day)
        if (t != null && t[4] > 0) {
            sleepVal.setText(t[4] / 60 + "h " + t[4] % 60 + "m");
            sleepSub.setText("Night ending on this day (the watch's total; stages aren't decoded yet)");
        } else { sleepVal.setText("–"); sleepSub.setText("No sleep saved for this day. Wear the watch to bed."); }

        // last 7 days
        float[] ws = new float[7];
        long weekSum = 0;
        int days = 0;
        for (int i = 0; i < 7; i++) { ws[i] = x.weekSteps[i]; if (x.weekSteps[i] > 0) { weekSum += x.weekSteps[i]; days++; } }
        int goal = getSharedPreferences(WatchService.PREFS, 0).getInt("goal.steps", 8000);
        weekSub.setText(days == 0 ? "Steps per day" : String.format(Locale.getDefault(), "Steps per day · average %,d", weekSum / days));
        weekChart.bars(x.weekNames, ws, Ui.ACCENT, goal, 6);
        hrWeekChart.barsFrom(x.weekNames, x.weekHr, 0xFFFF6B81, 6);

        // battery
        if (!x.batt.isEmpty()) {
            long[] last = x.batt.get(x.batt.size() - 1);
            battSub.setText("Last " + last[1] + "% at " + clock(last[0]));
        } else battSub.setText("Saved every half hour while connected");
        battChart.fixed().day(ChartView.LINE, hours(x.batt, start), values(x.batt), Ui.GOOD, 0, 100, 2);

        footer.setText(String.format(Locale.getDefault(), "Saved so far: %,d heart rate, %,d stress and %,d blood oxygen readings%s.",
                x.hrCount, x.stressCount, x.spo2Count, firstDay == null ? "" : ", from " + firstDay));
    }

    private static String level(int v) { return v < 40 ? "Relaxed" : v < 60 ? "Normal" : v < 80 ? "Medium" : "High"; }

    // ---- export ----

    private void export() {
        Toast.makeText(this, "Exporting…", Toast.LENGTH_SHORT).show();
        HealthDb.run(() -> {
            String csv = HealthDb.get(this).csv();
            String name = "watchlink-health-" + HealthDb.today() + ".csv";
            String msg;
            try {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
                cv.put(MediaStore.Downloads.MIME_TYPE, "text/csv");
                cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/WatchLink");
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (uri == null) throw new java.io.IOException("no file");
                try (OutputStream o = getContentResolver().openOutputStream(uri)) {
                    o.write(csv.getBytes(StandardCharsets.UTF_8));
                }
                msg = "Saved Downloads/WatchLink/" + name;
            } catch (Exception e) {
                msg = "Couldn't export: " + e.getMessage();
            }
            String m = msg;
            runOnUiThread(() -> Toast.makeText(this, m, Toast.LENGTH_LONG).show());
        });
    }
}
