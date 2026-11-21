package dev.fishtank.watchlink;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Watch settings found in the Momax library (see PROTOCOL_FULL.md). Every change is sent to the watch at once.
 * Settings the watch can report (do not disturb, raise to wake, walk reminder, calorie/distance goal) are read
 * back when the screen opens and after each change, so the screen shows what the watch really has.
 */
public class SettingsActivity extends Activity {
    private static SettingsActivity current;
    private SharedPreferences p;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final List<Runnable> binders = new ArrayList<>();
    private boolean binding;

    static void refresh() {
        SettingsActivity a = current;
        if (a != null) a.h.post(a::bindAll);
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        p = getSharedPreferences(WatchService.PREFS, 0);
        LinearLayout root = Ui.column(this);
        int pad = Ui.dp(this, 16);
        root.setPadding(pad, Ui.dp(this, 28), pad, pad);
        root.addView(Ui.label(this, "Watch settings", 28, true, Ui.text(this)));
        TextView sub = Ui.label(this, "Changes are sent to the watch straight away.", 14, false, Ui.subtext(this));
        sub.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 16));
        root.addView(sub);

        // ---- Display ----
        Ui.section(this, root, "Display");
        LinearLayout raise = Ui.card(this, root);
        toggle(raise, "Raise to wake", "Turn the screen on when you lift your wrist", "raise.on", false, this::sendRaise);
        toggle(raise, "Only during set hours", null, "raise.ranged", false, this::sendRaise);
        timeRange(raise, "Hours", "raise", 8, 0, 22, 0, this::sendRaise);

        // ---- Do not disturb ----
        Ui.section(this, root, "Do not disturb");
        LinearLayout dnd = Ui.card(this, root);
        toggle(dnd, "Do not disturb", "No buzzing for notifications during these hours", "dnd.on", false, this::sendDnd);
        timeRange(dnd, "Quiet hours", "dnd", 22, 0, 7, 0, this::sendDnd);

        // ---- Reminders ----
        Ui.section(this, root, "Reminders");
        LinearLayout walk = Ui.card(this, root);
        toggle(walk, "Walk reminder", "Nudge you when you haven't walked enough this hour", "walk.on", false, this::sendWalk);
        number(walk, "Steps per hour", "walk.steps", 100, 10, 2000, this::sendWalk);
        timeRange(walk, "Active hours", "walk", 9, 0, 21, 0, this::sendWalk);
        days(walk, "walk.days", 0x1f, this::sendWalk);

        LinearLayout sit = Ui.card(this, root);
        toggle(sit, "Sedentary reminder", "Buzz after sitting still for a while (not yet confirmed on the SW2S)",
                "sit.on", false, this::sendSit);
        number(sit, "Every (minutes)", "sit.interval", 60, 15, 240, this::sendSit);
        timeRange(sit, "Active hours", "sit", 9, 0, 18, 0, this::sendSit);
        days(sit, "sit.days", 0x1f, this::sendSit);

        LinearLayout water = Ui.card(this, root);
        toggle(water, "Drink water reminder", "Not yet confirmed on the SW2S", "water.on", false, this::sendWater);
        number(water, "Every (minutes)", "water.interval", 60, 15, 240, this::sendWater);
        timeRange(water, "Active hours", "water", 9, 0, 21, 0, this::sendWater);
        days(water, "water.days", 0x7f, this::sendWater);

        LinearLayout wash = Ui.card(this, root);
        toggle(wash, "Hand washing reminder", "Not yet confirmed on the SW2S", "wash.on", false, this::sendWash);
        number(wash, "Every (minutes)", "wash.interval", 120, 15, 240, this::sendWash);
        timeRange(wash, "Active hours", "wash", 9, 0, 21, 0, this::sendWash);
        days(wash, "wash.days", 0x7f, this::sendWash);

        // ---- Goals ----
        Ui.section(this, root, "Daily goals");
        LinearLayout goals = Ui.card(this, root);
        number(goals, "Steps", "goal.steps", 8000, 1000, 50000, this::sendSteps);
        number(goals, "Calories (kcal)", "goal.kcal", 500, 50, 5000, this::sendCalDist);
        number(goals, "Distance (m)", "goal.meters", 5000, 500, 100000, this::sendCalDist);

        // ---- Units & profile ----
        Ui.section(this, root, "Units & profile");
        LinearLayout units = Ui.card(this, root);
        toggle(units, "Miles", "Otherwise kilometres", "unit.miles", false, this::sendUnits);
        toggle(units, "Pounds", "Otherwise kilograms", "unit.lb", false, this::sendUnits);
        toggle(units, "Fahrenheit", "Otherwise Celsius", "unit.f", false, this::sendUnits);
        toggle(units, "12-hour clock", "Otherwise 24-hour", "unit.h12", false, this::sendUnits);
        LinearLayout prof = Ui.card(this, root);
        TextView pnote = Ui.label(this, "Used by the watch to work out calories and distance.", 13, false, Ui.subtext(this));
        pnote.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 6));
        prof.addView(pnote);
        number(prof, "Height (cm)", "user.height", 175, 100, 230, this::sendUser);
        number(prof, "Weight (kg)", "user.weight", 70, 30, 250, this::sendUser);
        number(prof, "Birth year", "user.year", 1995, 1920, 2020, this::sendUser);
        toggle(prof, "Female", "Otherwise male", "user.female", false, this::sendUser);

        // ---- Notifications ----
        Ui.section(this, root, "Notifications");
        LinearLayout icons = Ui.card(this, root);
        toggle(icons, "App icons on the watch", "Show WhatsApp, Telegram, Gmail… icons instead of the SMS icon",
                "appIcons", true, () -> {});
        toggle(icons, "Low watch battery alert", "A phone notification when the watch gets down to 20%",
                "lowBatteryAlert", true, () -> {});

        // ---- Watch ----
        Ui.section(this, root, "Watch");
        LinearLayout sys = Ui.card(this, root);
        Ui.row(this, sys, "Restart watch", null, v -> confirm("Restart the watch?", "It reconnects by itself after a few seconds.",
                () -> WatchService.send(java.util.Collections.singletonList(IdoProtocol.reboot()))));
        Ui.divider(this, sys);
        Ui.row(this, sys, "Factory reset", "Erases everything on the watch", v -> confirm("Factory reset the watch?",
                "This erases all data, faces and settings on the watch. It can't be undone.",
                () -> WatchService.send(java.util.Collections.singletonList(IdoProtocol.factoryReset()))));

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.bg(this));
        sv.addView(root);
        setContentView(sv);
        Ui.style(this, sv);
        bindAll();
    }

    @Override protected void onResume() {
        super.onResume();
        current = this;
        WatchService.readSettings();
    }

    @Override protected void onPause() {
        current = null;
        super.onPause();
    }

    private void bindAll() {
        binding = true;
        for (Runnable r : binders) r.run();
        binding = false;
    }

    // ---- senders ----
    private int i(String k, int d) { return p.getInt(k, d); }
    private boolean z(String k) { return p.getBoolean(k, false); }

    private void sendRaise() {
        send(IdoProtocol.raiseToWake(z("raise.on"), i("raise.sec", 9), z("raise.ranged"),
                i("raise.sh", 8), i("raise.sm", 0), i("raise.eh", 22), i("raise.em", 0)));
    }
    private void sendDnd() {
        send(IdoProtocol.doNotDisturb(z("dnd.on"), i("dnd.sh", 22), i("dnd.sm", 0), i("dnd.eh", 7), i("dnd.em", 0)));
    }
    private void sendWalk() {
        send(IdoProtocol.walkReminder(z("walk.on"), i("walk.steps", 100), i("walk.sh", 9), i("walk.sm", 0),
                i("walk.eh", 21), i("walk.em", 0), i("walk.days", 0x1f)));
    }
    private void sendSit() {
        send(IdoProtocol.sedentary(z("sit.on"), i("sit.sh", 9), i("sit.sm", 0), i("sit.eh", 18), i("sit.em", 0),
                i("sit.interval", 60), i("sit.days", 0x1f)));
    }
    private void sendWater() {
        send(IdoProtocol.drinkWater(z("water.on"), i("water.sh", 9), i("water.sm", 0), i("water.eh", 21), i("water.em", 0),
                i("water.days", 0x7f), i("water.interval", 60)));
    }
    private void sendWash() {
        send(IdoProtocol.handWashing(z("wash.on"), i("wash.sh", 9), i("wash.sm", 0), i("wash.eh", 21), i("wash.em", 0),
                i("wash.days", 0x7f), i("wash.interval", 120)));
    }
    private void sendSteps() { send(IdoProtocol.stepGoal(i("goal.steps", 8000), 8, 0)); }
    private void sendCalDist() {
        send(IdoProtocol.calorieDistanceGoal(i("goal.kcal", 500), i("goal.meters", 5000), WatchService.lastGoalReply));
    }
    private void sendUnits() {
        int stride = Math.max(50, Math.min(120, Math.round(i("user.height", 175) * 0.415f)));
        send(IdoProtocol.units(z("unit.miles"), z("unit.lb"), z("unit.f"), z("unit.h12"), stride));
    }
    private void sendUser() {
        send(IdoProtocol.userInfo(i("user.height", 175), i("user.weight", 70), z("user.female") ? 2 : 1,
                i("user.year", 1995), 1, 1));
    }

    /**
     * Sends shortly after the last change of the same setting, so quick changes to one setting send once while
     * changes to different settings (different command bytes) are all sent.
     */
    private final java.util.Map<Integer, Runnable> pending = new java.util.HashMap<>();
    private void send(byte[] pkt) {
        if (WatchService.instance == null) {
            Toast.makeText(this, "Not connected: saved, will need sending again", Toast.LENGTH_SHORT).show();
            return;
        }
        int kind = (pkt[0] & 0xff) << 8 | (pkt[1] & 0xff);
        Runnable old = pending.remove(kind);
        if (old != null) h.removeCallbacks(old);
        Runnable r = () -> { pending.remove(kind); WatchService.send(java.util.Collections.singletonList(pkt)); };
        pending.put(kind, r);
        h.postDelayed(r, 300);
    }

    @Override protected void onDestroy() {
        for (Runnable r : new ArrayList<>(pending.values())) { h.removeCallbacks(r); r.run(); }   // don't lose a change on the way out
        super.onDestroy();
    }

    // ---- widgets ----
    private ColorStateList tint() {
        return new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{Ui.ACCENT, Ui.subtext(this)});
    }

    private void toggle(LinearLayout card, String title, String sub, String key, boolean def, Runnable onChange) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 4), Ui.dp(this, 8), 0, Ui.dp(this, 8));
        LinearLayout text = Ui.column(this);
        text.addView(Ui.label(this, title, 17, false, Ui.text(this)));
        if (sub != null) text.addView(Ui.label(this, sub, 13, false, Ui.subtext(this)));
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        Switch sw = new Switch(this);
        sw.setThumbTintList(tint());
        sw.setTrackTintList(tint());
        row.addView(sw);
        card.addView(row);
        binders.add(() -> sw.setChecked(p.getBoolean(key, def)));
        sw.setOnCheckedChangeListener((v, c) -> {
            if (binding) return;
            p.edit().putBoolean(key, c).apply();
            onChange.run();
        });
    }

    private TextView valueRow(LinearLayout card, String title) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10));
        row.addView(Ui.label(this, title, 16, false, Ui.text(this)), new LinearLayout.LayoutParams(0, -2, 1));
        TextView val = Ui.label(this, "", 16, true, Ui.ACCENT);
        row.addView(val);
        card.addView(row);
        return val;
    }

    private void timeRange(LinearLayout card, String title, String k, int sh, int sm, int eh, int em, Runnable onChange) {
        TextView val = valueRow(card, title);
        binders.add(() -> val.setText(AlarmsActivity.show(this, i(k + ".sh", sh), i(k + ".sm", sm)) + " – "
                + AlarmsActivity.show(this, i(k + ".eh", eh), i(k + ".em", em))));
        ((LinearLayout) val.getParent()).setOnClickListener(v ->
                new TimePickerDialog(this, (tp, h1, m1) -> {
                    p.edit().putInt(k + ".sh", h1).putInt(k + ".sm", m1).apply();
                    new TimePickerDialog(this, (tp2, h2, m2) -> {
                        p.edit().putInt(k + ".eh", h2).putInt(k + ".em", m2).apply();
                        bindAll();
                        onChange.run();
                    }, i(k + ".eh", eh), i(k + ".em", em), android.text.format.DateFormat.is24HourFormat(this)).show();
                    Toast.makeText(this, "Now pick the end time", Toast.LENGTH_SHORT).show();
                }, i(k + ".sh", sh), i(k + ".sm", sm), android.text.format.DateFormat.is24HourFormat(this)).show());
    }

    private void number(LinearLayout card, String title, String key, int def, int min, int max, Runnable onChange) {
        TextView val = valueRow(card, title);
        binders.add(() -> val.setText(String.valueOf(p.getInt(key, def))));
        ((LinearLayout) val.getParent()).setOnClickListener(v -> {
            EditText e = new EditText(this);
            e.setInputType(InputType.TYPE_CLASS_NUMBER);
            e.setText(String.valueOf(p.getInt(key, def)));
            e.setSelectAllOnFocus(true);
            new AlertDialog.Builder(this).setTitle(title + " (" + min + "–" + max + ")").setView(e)
                    .setPositiveButton("OK", (d, w) -> {
                        try {
                            int n = Math.max(min, Math.min(max, Integer.parseInt(e.getText().toString().trim())));
                            p.edit().putInt(key, n).apply();
                            bindAll();
                            onChange.run();
                        } catch (NumberFormatException ignored) {}
                    }).setNegativeButton("Cancel", null).show();
        });
    }

    private void days(LinearLayout card, String key, int def, Runnable onChange) {
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 2));
        for (int d = 0; d < 7; d++) {
            final int bit = 1 << d;
            TextView chip = Ui.label(this, AlarmsActivity.DAYS[d], 14, true, Ui.text(this));
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(0, Ui.dp(this, 9), 0, Ui.dp(this, 9));
            Runnable paint = () -> {
                boolean sel = (p.getInt(key, def) & bit) != 0;
                chip.setTextColor(sel ? Ui.ON_ACCENT : Ui.subtext(this));
                chip.setBackground(Ui.rounded(sel ? Ui.ACCENT : 0xFF2A2F44, Ui.dp(this, 10)));
            };
            binders.add(paint);
            chip.setOnClickListener(v -> {
                p.edit().putInt(key, p.getInt(key, def) ^ bit).apply();
                paint.run();
                onChange.run();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            lp.setMargins(Ui.dp(this, 2), 0, Ui.dp(this, 2), 0);
            row.addView(chip, lp);
        }
        card.addView(row);
    }

    private void confirm(String title, String msg, Runnable yes) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(msg)
                .setPositiveButton("Yes", (d, w) -> yes.run()).setNegativeButton("Cancel", null).show();
    }
}
