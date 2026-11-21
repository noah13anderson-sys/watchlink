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
                    FaceInstaller.install(name, iwf, (t, done) -> Log.i(WatchService.TAG, "debug design: " + t));
                } catch (Exception e) { Log.w(WatchService.TAG, "debug design", e); }
            }).start();
        }
        if (i.hasExtra("v3cmd")) {
            byte[] payload = IdoProtocol.unhex(i.getStringExtra("v3") == null ? "" : i.getStringExtra("v3"));
            WatchService.sendV3(i.getIntExtra("v3cmd", 0), payload);
            Log.i(WatchService.TAG, "debug v3 cmd=" + i.getIntExtra("v3cmd", 0) + " " + IdoProtocol.hex(payload));
        }
    }
}
