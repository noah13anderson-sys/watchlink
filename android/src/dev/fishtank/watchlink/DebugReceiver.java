package dev.fishtank.watchlink;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.Collections;

/**
 * Developer hook, only usable from adb (protected by the DUMP permission):
 *   adb shell am broadcast -n dev.fishtank.watchlink/.DebugReceiver --es raw "02 01"
 *   adb shell am broadcast -n dev.fishtank.watchlink/.DebugReceiver --ei v3cmd 15 --es v3 "00"
 */
public class DebugReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        WatchService.startIfAllowed(c);
        String raw = i.getStringExtra("raw");
        if (raw != null) {
            byte[] b = IdoProtocol.unhex(raw);
            byte[] p = new byte[20];
            System.arraycopy(b, 0, p, 0, Math.min(20, b.length));
            WatchService.send(Collections.singletonList(p));
            Log.i(WatchService.TAG, "debug raw " + IdoProtocol.hex(p));
        }
        if (i.hasExtra("sendfile")) {           // --es sendfile /sdcard/x.iwf.lz --es name x.iwf.lz --ei comp 1 --ei orig N
            try {
                byte[] d = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(i.getStringExtra("sendfile")));
                FileTransfer.start(i.getIntExtra("type", FileTransfer.TYPE_IWF), i.getStringExtra("name"), d,
                        i.getIntExtra("comp", 0), i.getIntExtra("orig", d.length), new FileTransfer.Listener() {
                            public void onProgress(int pc) { Log.i(WatchService.TAG, "debug transfer " + pc + "%"); }
                            public void onDone(boolean ok, String m) { Log.i(WatchService.TAG, "debug transfer done ok=" + ok + " " + m); }
                        });
            } catch (Exception e) { Log.w(WatchService.TAG, "sendfile", e); }
        }
        if (i.hasExtra("importzip")) {          // --es importzip /data/local/tmp/x.zip  -> files/import.iwf
            try (java.io.InputStream in = new java.io.FileInputStream(i.getStringExtra("importzip"))) {
                dev.fishtank.watchlink.face.FaceImport f = dev.fishtank.watchlink.face.FaceImport.read(in);
                java.nio.file.Files.write(new java.io.File(c.getExternalFilesDir(null), "import.iwf").toPath(), f.iwf);
                Log.i(WatchService.TAG, "debug import ok " + f.name + " " + f.width + "x" + f.height + " " + f.iwf.length);
            } catch (Exception e) { Log.w(WatchService.TAG, "debug import", e); }
        }
        if (i.hasExtra("designface")) {         // --ei designface 1 (analog) --ez hr true --ez kcal true --ei bg 2
            dev.fishtank.watchlink.face.FaceDesign d = new dev.fishtank.watchlink.face.FaceDesign();
            d.style = i.getIntExtra("designface", 0);
            d.heart = i.getBooleanExtra("hr", false);
            d.calories = i.getBooleanExtra("kcal", false);
            d.weather = i.getBooleanExtra("weather", false);
            d.distance = i.getBooleanExtra("dist", false);
            d.steps = i.getBooleanExtra("steps", true);
            d.date = i.getBooleanExtra("date", true);
            d.weekday = i.getBooleanExtra("week", true);
            d.battery = i.getBooleanExtra("batt", true);
            d.timeColor = i.getIntExtra("tc", d.timeColor);
            d.miles = i.getBooleanExtra("miles", false);
            d.seconds = i.getBooleanExtra("sec", false);
            d.ampm = i.getBooleanExtra("apm", false);
            d.timeSize = i.getIntExtra("size", 64);
            d.accentColor = i.getIntExtra("accent", d.accentColor);
            d.effect = i.getIntExtra("fx", 0);
            d.position = i.getIntExtra("pos", 0);
            d.background = i.getIntExtra("bg", 0);
            d.font = i.getIntExtra("font", 0);
            new Thread(() -> {
                try {
                    String name = FaceInstaller.nextName();
                    byte[] iwf = d.build(name.replace(".iwf", ""));
                    java.nio.file.Files.write(new java.io.File(c.getExternalFilesDir(null), "design.iwf").toPath(), iwf);
                    try (java.io.FileOutputStream o = new java.io.FileOutputStream(new java.io.File(c.getExternalFilesDir(null), "design.png"))) {
                        d.preview().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, o);
                    }
                    if (i.getBooleanExtra("previewonly", false)) {
                        new java.io.File(c.getExternalFilesDir(null), "design.png")
                                .renameTo(new java.io.File(c.getExternalFilesDir(null), i.getStringExtra("out") != null
                                        ? i.getStringExtra("out") : "bg" + d.background + ".png"));
                        Log.i(WatchService.TAG, "debug design preview bg" + d.background);
                        return;
                    }
                    FaceInstaller.installMine(name, iwf, (t, done) -> Log.i(WatchService.TAG, "debug design: " + t));
                } catch (Exception e) { Log.w(WatchService.TAG, "debug design", e); }
            }).start();
        }
        if (i.hasExtra("importhealth")) {       // --es importhealth "<hex payload of a v3 0x04 reply>": parse and save
            byte[] p = IdoProtocol.unhex(i.getStringExtra("importhealth"));
            HealthDb.run(() -> {
                HealthDb db = HealthDb.get(c);
                int type = p.length > 1 ? p[1] & 0xff : 0;
                if (type == IdoProtocol.HEALTH_TYPE_SLEEP) {
                    IdoProtocol.SleepNight n = IdoProtocol.parseSleep(p);
                    boolean kept = n != null && db.saveNight(n, IdoProtocol.hex(p));
                    Log.i(WatchService.TAG, "debug import sleep " + (n == null ? "none"
                            : n.total + " min on " + HealthDb.nightDay(n) + (kept ? "" : " (not kept)")));
                    return;
                }
                IdoProtocol.HeartPage hp = IdoProtocol.parseSeries(p, type);
                if (hp == null || hp.year < 2000) { Log.i(WatchService.TAG, "debug import: no data"); return; }
                String day = HealthDb.day(hp.year, hp.month, hp.day);
                db.addSamples(type, day, hp.samples);
                Log.i(WatchService.TAG, "debug import type " + type + " " + day + ": " + hp.samples.size() + " samples");
            });
        }
        if (i.hasExtra("dbstats")) {
            HealthDb.run(() -> {
                HealthDb db = HealthDb.get(c);
                Log.i(WatchService.TAG, "debug db: hr=" + db.count(HealthDb.HR) + " stress=" + db.count(HealthDb.STRESS)
                        + " spo2=" + db.count(HealthDb.SPO2) + " steps=" + db.count(HealthDb.STEPS)
                        + " battery=" + db.count(HealthDb.BATTERY) + " first=" + db.firstDay()
                        + " today=" + java.util.Arrays.toString(db.dayTotals(HealthDb.today())));
            });
        }
        if (i.hasExtra("demohistory") && android.os.Build.PRODUCT.contains("sdk")) {   // emulator only: fake week
            HealthDb.run(() -> {
                HealthDb db = HealthDb.get(c);
                java.util.Random r = new java.util.Random(7);
                for (int d = 0; d < 7; d++) {
                    String day = HealthDb.shift(HealthDb.today(), -d);
                    long base = HealthDb.midnight(day);
                    java.util.List<int[]> hr = new java.util.ArrayList<>(), st = new java.util.ArrayList<>(), ox = new java.util.ArrayList<>();
                    int lim = d == 0 ? 15 * 3600 : 86400;
                    for (int t = 0; t < lim; t += 300) {
                        double hour = t / 3600.0;
                        boolean asleep = hour < 7 || hour > 23;
                        hr.add(new int[]{t, (int) ((asleep ? 56 : 72) + 10 * Math.sin(hour) + r.nextInt(12))});
                        if (t % 1800 == 0) st.add(new int[]{t, (int) ((asleep ? 15 : 40) + r.nextInt(30))});
                        if (asleep && t % 3600 == 0) ox.add(new int[]{t, 94 + r.nextInt(5)});
                    }
                    db.addSamples(HealthDb.HR, day, hr);
                    db.addSamples(HealthDb.STRESS, day, st);
                    db.addSamples(HealthDb.SPO2, day, ox);
                    int steps = 0;
                    for (int hh = 7; hh < (d == 0 ? 15 : 23); hh++) {
                        steps += r.nextInt(hh == 8 || hh == 17 ? 1500 : 500);
                        db.addSample(HealthDb.STEPS, base + hh * 3600 + 1800, steps);
                    }
                    for (int hh = 0; hh < (d == 0 ? 15 : 24); hh++) db.addSample(HealthDb.BATTERY, base + hh * 3600, 90 - hh * 2 - d);
                    android.content.ContentValues cv = new android.content.ContentValues();
                    cv.put("steps", steps); cv.put("kcal", steps / 25); cv.put("meters", steps * 7 / 10); cv.put("sleep", 380 + r.nextInt(120));
                    db.setDay(day, cv);
                }
                Log.i(WatchService.TAG, "debug demo history written");
            });
        }
        if (i.hasExtra("widgetpreview")) {      // render the home screen widget to files/widget.png
            try {
                android.widget.FrameLayout parent = new android.widget.FrameLayout(c);
                android.view.View v = WatchWidget.views(c).apply(c, parent);
                int w = Math.round(320 * c.getResources().getDisplayMetrics().density),
                        h = Math.round(130 * c.getResources().getDisplayMetrics().density);
                v.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY),
                        android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY));
                v.layout(0, 0, w, h);
                android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
                b.eraseColor(0xFF4A5A70);
                v.draw(new android.graphics.Canvas(b));
                try (java.io.FileOutputStream o = new java.io.FileOutputStream(new java.io.File(c.getExternalFilesDir(null), "widget.png"))) {
                    b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, o);
                }
                Log.i(WatchService.TAG, "debug widget preview written");
            } catch (Exception e) { Log.w(WatchService.TAG, "widget preview", e); }
        }
        if (i.hasExtra("iconpreview")) {        // render the launcher icon (as the launcher masks it) to files/icon.png
            try {
                android.graphics.drawable.Drawable d = c.getPackageManager().getApplicationIcon(c.getPackageName());
                android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(432, 432, android.graphics.Bitmap.Config.ARGB_8888);
                b.eraseColor(0xFF6A7F99);
                d.setBounds(48, 48, 384, 384);
                d.draw(new android.graphics.Canvas(b));
                try (java.io.FileOutputStream o = new java.io.FileOutputStream(new java.io.File(c.getExternalFilesDir(null), "icon.png"))) {
                    b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, o);
                }
                Log.i(WatchService.TAG, "debug icon preview written");
            } catch (Exception e) { Log.w(WatchService.TAG, "icon preview", e); }
        }
        if (i.hasExtra("v3cmd")) {
            byte[] payload = IdoProtocol.unhex(i.getStringExtra("v3") == null ? "" : i.getStringExtra("v3"));
            WatchService.sendV3(i.getIntExtra("v3cmd", 0), payload);
            Log.i(WatchService.TAG, "debug v3 cmd=" + i.getIntExtra("v3cmd", 0) + " " + IdoProtocol.hex(payload));
        }
    }
}
