package dev.fishtank.watchlink;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;

/** Main screen: watch status, today's stats, and controls. Built in code (no XML). */
public class MainActivity extends Activity {
    private static MainActivity current;
    private TextView statusText, batteryText, firmwareText, stepsVal, kcalVal, kcalSub, distVal, hrVal, hrSub, pairRow, weatherRow, stressVal, stressSub, spo2Val, spo2Sub, sleepVal, sleepSub, findBtn;
    private View statusDot;
    private boolean finding;
    private final Handler h = new Handler(Looper.getMainLooper());

    static void refresh() {
        MainActivity a = current;
        if (a != null) a.runOnUiThread(a::update);
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (getActionBar() != null) getActionBar().hide();
        getWindow().setStatusBarColor(Ui.bg(this));
        getWindow().setNavigationBarColor(Ui.bg(this));
        if (!Ui.dark(this)) getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        requestPermissions(new String[]{
                Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_CONTACTS,
                Manifest.permission.ANSWER_PHONE_CALLS, Manifest.permission.ACCESS_COARSE_LOCATION}, 1);

        LinearLayout root = Ui.column(this);
        int pad = Ui.dp(this, 16);
        root.setPadding(pad, Ui.dp(this, 28), pad, Ui.dp(this, 32));

        // ---- Header ----
        root.addView(Ui.label(this, "WatchLink", 30, true, Ui.text(this)));
        TextView sub = Ui.label(this, "Momax SW2S", 15, false, Ui.subtext(this));
        sub.setPadding(0, 0, 0, Ui.dp(this, 18));
        root.addView(sub);

        // ---- Status card ----
        LinearLayout status = Ui.card(this, root);
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        statusDot = new View(this);
        int d = Ui.dp(this, 12);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(d, d);
        dlp.rightMargin = Ui.dp(this, 10);
        line.addView(statusDot, dlp);
        statusText = Ui.label(this, "", 18, true, Ui.text(this));
        line.addView(statusText, new LinearLayout.LayoutParams(0, -2, 1));
        batteryText = Ui.label(this, "", 18, true, Ui.text(this));
        line.addView(batteryText);
        status.addView(line);
        firmwareText = Ui.label(this, "", 13, false, Ui.subtext(this));
        firmwareText.setPadding(Ui.dp(this, 22), Ui.dp(this, 4), 0, 0);
        status.addView(firmwareText);

        // ---- Today ----
        Ui.section(this, root, "Today");
        LinearLayout grid1 = new LinearLayout(this), grid2 = new LinearLayout(this);
        stepsVal = stat(grid1, "Steps");
        kcalVal = stat(grid1, "Calories");
        kcalSub = sub(kcalVal);
        distVal = stat(grid2, "Distance");
        hrVal = stat(grid2, "Heart rate");
        hrSub = Ui.label(this, "", 12, false, Ui.subtext(this));
        ((LinearLayout) hrVal.getParent()).addView(hrSub);
        root.addView(grid1);
        root.addView(grid2);

        // ---- Health (from the watch's health sync) ----
        Ui.section(this, root, "Health");
        LinearLayout grid3 = new LinearLayout(this), grid4 = new LinearLayout(this);
        stressVal = stat(grid3, "Stress");
        stressSub = sub(stressVal);
        spo2Val = stat(grid3, "Blood oxygen");
        spo2Sub = sub(spo2Val);
        sleepVal = stat(grid4, "Sleep last night");
        sleepSub = sub(sleepVal);
        root.addView(grid3);
        root.addView(grid4);
        for (TextView v : new TextView[]{stepsVal, kcalVal, distVal, hrVal, stressVal, spo2Val, sleepVal})
            ((View) v.getParent()).setOnClickListener(x -> startActivity(new Intent(this, HistoryActivity.class)));
        LinearLayout hist = Ui.card(this, root);
        Ui.row(this, hist, "History", "Day-by-day steps, heart rate, stress, blood oxygen and sleep", v ->
                startActivity(new Intent(this, HistoryActivity.class)));

        // ---- Quick actions ----
        Ui.section(this, root, "Watch");
        LinearLayout actions = new LinearLayout(this);
        findBtn = Ui.primary(this, actions, "Find watch", v -> toggleFind());
        Ui.primary(this, actions, "Sync now", v -> {
            WatchService.send(Arrays.asList(IdoProtocol.setTime(Calendar.getInstance()),
                    IdoProtocol.getDeviceInfo(), IdoProtocol.getLiveData()));
            WatchService.refreshHeartRate();
            Weather.update(this);
            Toast.makeText(this, "Syncing time and stats…", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, -2);
        alp.bottomMargin = Ui.dp(this, 14);
        root.addView(actions, alp);

        LinearLayout watchCard = Ui.card(this, root);
        pairRow = Ui.row(this, watchCard, "Calls & music", "Checking pairing…", v -> pairClassic());
        Ui.divider(this, watchCard);
        Ui.row(this, watchCard, "Music controls", "Show the music player on the watch", v -> {
            WatchService.send(Collections.singletonList(IdoProtocol.musicScreen(true)));
            Toast.makeText(this, "Music controls opened on watch", Toast.LENGTH_SHORT).show();
        });
        Ui.divider(this, watchCard);
        weatherRow = Ui.row(this, watchCard, "Weather", "Updating…", v -> {
            Weather.saveLocation(this);
            Weather.update(this);
            Toast.makeText(this, "Updating weather on the watch…", Toast.LENGTH_SHORT).show();
        });
        Ui.divider(this, watchCard);
        Ui.row(this, watchCard, "Watch faces", "Change the clock face on the watch", v ->
                startActivity(new Intent(this, FacesActivity.class)));
        Ui.divider(this, watchCard);
        Ui.row(this, watchCard, "Alarms", "Set wake-up alarms on the watch", v ->
                startActivity(new Intent(this, AlarmsActivity.class)));
        Ui.divider(this, watchCard);
        Ui.row(this, watchCard, "Watch settings", "Do not disturb, raise to wake, reminders, goals, units", v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        // ---- Notifications ----
        Ui.section(this, root, "Notifications");
        LinearLayout notif = Ui.card(this, root);
        Ui.row(this, notif, "Choose apps", "Pick which apps buzz your watch", v ->
                startActivity(new Intent(this, AppsActivity.class)));
        Ui.divider(this, notif);
        Ui.row(this, notif, "Send a test", "Check that notifications reach the watch", v -> {
            WatchService.sendV3(IdoProtocol.V3_NOTICE, IdoProtocol.noticeV3(0x01, "WatchLink", "", "Test notification from your phone"));
            Toast.makeText(this, "Test sent", Toast.LENGTH_SHORT).show();
        });
        Ui.divider(this, notif);
        Ui.row(this, notif, "Notification access", "Android setting WatchLink needs to read notifications", v ->
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));

        // ---- Advanced (collapsed) ----
        TextView advHeader = Ui.section(this, root, "Advanced  ▸");
        LinearLayout adv = Ui.card(this, root);
        adv.setVisibility(View.GONE);
        advHeader.setOnClickListener(v -> {
            boolean show = adv.getVisibility() != View.VISIBLE;
            adv.setVisibility(show ? View.VISIBLE : View.GONE);
            advHeader.setText(show ? "ADVANCED  ▾" : "ADVANCED  ▸");
        });
        adv.addView(Ui.label(this, "Watch Bluetooth address", 13, false, Ui.subtext(this)));
        EditText mac = new EditText(this);
        mac.setTextColor(Ui.text(this));
        mac.setText(getSharedPreferences(WatchService.PREFS, 0).getString("mac", WatchService.DEFAULT_MAC));
        mac.setHint("Leave empty to search for the watch");
        adv.addView(mac);
        Ui.row(this, adv, "Save & reconnect", null, v -> {
            getSharedPreferences(WatchService.PREFS, 0).edit()
                    .putString("mac", mac.getText().toString().trim().toUpperCase()).apply();
            stopService(new Intent(this, WatchService.class));
            startIfAllowed();
        });
        Ui.divider(this, adv);
        Ui.row(this, adv, "Battery optimisation", "Let WatchLink run in the background", v ->
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:" + getPackageName()))));
        TextView tip = Ui.label(this, "Tip: uninstall or disable Momax Fit so the two apps don't fight over the watch.",
                13, false, Ui.subtext(this));
        tip.setPadding(0, Ui.dp(this, 10), 0, 0);
        adv.addView(tip);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.bg(this));
        sv.addView(root);
        setContentView(sv);
        startIfAllowed();
    }

    /**
     * The watch's calling side ("Momax SW2S-BT") uses classic Bluetooth on the same address. Pairing it gives
     * call audio and standard music controls (AVRCP), which this watch uses for its music buttons.
     */
    @android.annotation.SuppressLint("MissingPermission")
    private boolean classicPaired() {
        try {
            String mac = getSharedPreferences(WatchService.PREFS, 0).getString("mac", WatchService.DEFAULT_MAC);
            android.bluetooth.BluetoothAdapter ad = getSystemService(android.bluetooth.BluetoothManager.class).getAdapter();
            return ad.getRemoteDevice(mac).getBondState() == android.bluetooth.BluetoothDevice.BOND_BONDED;
        } catch (Exception e) { return false; }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private void pairClassic() {
        if (classicPaired()) { Toast.makeText(this, "Already paired", Toast.LENGTH_SHORT).show(); return; }
        try {
            String mac = getSharedPreferences(WatchService.PREFS, 0).getString("mac", WatchService.DEFAULT_MAC);
            android.bluetooth.BluetoothDevice d = getSystemService(android.bluetooth.BluetoothManager.class)
                    .getAdapter().getRemoteDevice(mac);
            boolean ok = d.createBond();
            Toast.makeText(this, ok ? "Pairing… accept the request on your phone (and watch if asked)"
                    : "Couldn't start pairing. Try Settings → Bluetooth → Momax SW2S-BT", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Pairing failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        h.postDelayed(this::update, 15000);
    }

    private TextView sub(TextView valueView) {
        TextView t = Ui.label(this, "", 12, false, Ui.subtext(this));
        ((LinearLayout) valueView.getParent()).addView(t);
        return t;
    }

    /** Seconds after midnight as a clock time, in the phone's 12/24-hour style. */
    private String clock(int secs) { return AlarmsActivity.show(this, secs / 3600 % 24, (secs / 60) % 60); }

    private String clockAt(long unix) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(unix * 1000);
        return AlarmsActivity.show(this, c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE));
    }

    /** One stat tile in a 2-column grid. */
    private TextView stat(LinearLayout row, String name) {
        LinearLayout tile = Ui.column(this);
        int p = Ui.dp(this, 16);
        tile.setPadding(p, p, p, p);
        tile.setBackground(Ui.rounded(Ui.card(this), Ui.dp(this, 20)));
        tile.addView(Ui.label(this, name, 13, false, Ui.subtext(this)));
        TextView v = Ui.label(this, "–", 26, true, Ui.text(this));
        tile.addView(v);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);   // tiles in a row share its height
        lp.setMargins(row.getChildCount() == 0 ? 0 : Ui.dp(this, 7), 0,
                row.getChildCount() == 0 ? Ui.dp(this, 7) : 0, Ui.dp(this, 14));
        row.addView(tile, lp);
        return v;
    }

    /**
     * Find watch: the official "find device" command (06 04 00/01) is acknowledged by the SW2S but doesn't buzz it,
     * and the motor command (03 53) gets no answer at all. A notification always buzzes the watch and lights the
     * screen, so send one every 5 s until stopped (at most 4, ~20 s).
     */
    private void toggleFind() {
        finding = !finding;
        findBtn.setText(finding ? "Stop" : "Find watch");
        if (finding) {
            WatchService.send(Collections.singletonList(IdoProtocol.findWatch(true)));
            pulse(0);
        } else {
            h.removeCallbacks(pulser);
            WatchService.send(Collections.singletonList(IdoProtocol.findWatch(false)));
        }
    }

    private int pulses;
    private final Runnable pulser = () -> pulse(pulses + 1);

    private void pulse(int n) {
        pulses = n;
        if (!finding) return;
        if (n >= 4) { toggleFind(); return; }            // stop after ~20 s
        WatchService.sendV3(IdoProtocol.V3_NOTICE, IdoProtocol.noticeV3(0x01, "Find watch", "", "Your phone is looking for this watch"));
        h.postDelayed(pulser, 5000);
    }

    /** Android 14 refuses to start a connected-device service before BLUETOOTH_CONNECT is granted. */
    private void startIfAllowed() {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
            WatchService.start(this);
        else WatchService.status = "Waiting for Bluetooth permission";
        update();
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] results) { startIfAllowed(); }

    private void update() {
        String s = WatchService.status;
        boolean ok = "Connected".equals(s);
        boolean trying = s.startsWith("Connecting") || s.startsWith("Connected,") || s.startsWith("Looking");
        ((Runnable) () -> {
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(ok ? Ui.GOOD : trying ? Ui.WARN : Ui.BAD);
            statusDot.setBackground(dot);
        }).run();
        statusText.setText(ok ? "Connected" : s.startsWith("Disconnected") ? "Watch not in range" : s);
        int bat = WatchService.battery;
        batteryText.setText(bat < 0 ? "" : "🔋 " + bat + "%");
        long ago = WatchService.lastUpdate == 0 ? -1 : (System.currentTimeMillis() - WatchService.lastUpdate) / 60000;
        String upd = ago < 0 ? "" : ago == 0 ? "updated just now" : ago < 60 ? "updated " + ago + " min ago"
                : "updated " + (ago / 60) + " h ago";
        firmwareText.setText(ok ? "Firmware " + WatchService.firmware + (upd.isEmpty() ? "" : " · " + upd) : upd);

        if (WatchService.steps >= 0) {
            stepsVal.setText(String.format("%,d", WatchService.steps));
            // the watch shows activity calories (from the health sync); live data only has the total with resting burn
            int active = WatchService.activeKcal;
            kcalVal.setText((active >= 0 ? active : WatchService.kcal) + " kcal");
            kcalSub.setText(active >= 0 ? String.format("active · %,d in total", WatchService.kcal) : "in total, with resting");
            long m = WatchService.distance;
            distVal.setText(m >= 1000 ? String.format("%.2f km", m / 1000.0) : m + " m");
        }
        if (WatchService.hrTime >= 0) {
            int t = WatchService.hrTime;
            hrVal.setText(WatchService.heartRate + " bpm");
            hrSub.setText("at " + clock(t) + "\ntoday " + WatchService.hrMin + "–" + WatchService.hrMax);
        }
        WatchService.Series st = WatchService.stress, ox = WatchService.spo2;
        if (st.time >= 0) {
            stressVal.setText(String.valueOf(st.value));
            stressSub.setText((st.value < 40 ? "Relaxed" : st.value < 60 ? "Normal" : st.value < 80 ? "Medium" : "High")
                    + "\nat " + clock(st.time));
        } else stressSub.setText("No reading today");
        if (ox.time >= 0) {
            spo2Val.setText(ox.value + "%");
            spo2Sub.setText("at " + clock(ox.time) + "\ntoday " + ox.min + "–" + ox.max + "%");
        } else spo2Sub.setText("No reading today");
        boolean paired = classicPaired();
        Ui.setRow(this, pairRow, "Calls & music", paired ? "Paired: call audio and music buttons work"
                : "Not paired: tap to pair for call audio and music buttons");
        Ui.setRow(this, weatherRow, "Weather", Weather.summary.isEmpty()
                ? "Sends the forecast to the watch · tap to update" : Weather.summary + " · tap to update");
        IdoProtocol.SleepNight night = WatchService.lastNight;
        if (night != null) {
            sleepVal.setText(night.total / 60 + "h " + night.total % 60 + "m");
            String range = clockAt(night.start()) + " – " + clockAt(night.end());
            sleepSub.setText(night.score > 0 ? "Score " + night.score + " · " + range : range);
        } else { sleepVal.setText("–"); sleepSub.setText("Wear the watch to bed"); }
    }

    @Override protected void onResume() {
        super.onResume();
        current = this;
        update();
        WatchService.send(Collections.singletonList(IdoProtocol.getLiveData()));
        WatchService.refreshHeartRate();
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            Weather.saveLocation(this);
    }

    @Override protected void onPause() { current = null; super.onPause(); }
}
