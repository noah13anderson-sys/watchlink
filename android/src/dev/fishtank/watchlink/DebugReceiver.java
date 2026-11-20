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
        if (i.hasExtra("v3cmd")) {
            byte[] payload = IdoProtocol.unhex(i.getStringExtra("v3") == null ? "" : i.getStringExtra("v3"));
            WatchService.sendV3(i.getIntExtra("v3cmd", 0), payload);
            Log.i(WatchService.TAG, "debug v3 cmd=" + i.getIntExtra("v3cmd", 0) + " " + IdoProtocol.hex(payload));
        }
    }
}
