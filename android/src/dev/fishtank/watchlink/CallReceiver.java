package dev.fishtank.watchlink;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.util.Collections;

/**
 * Incoming-call alerts on the watch. Uses the old 05 01 format, which the Momax library also uses
 * for calls (ProtocolV2SetCallEvt); verified on the SW2S 2026-11-20. 05 02 01 stops the alert.
 */
public class CallReceiver extends BroadcastReceiver {
    private static boolean ringing;

    @Override public void onReceive(Context c, Intent i) {
        String state = i.getStringExtra(TelephonyManager.EXTRA_STATE);
        if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
            String number = i.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
            if (number == null) return;   // Android sends the broadcast twice; only one has the number
            ringing = true;
            String name = lookup(c, number);
            WatchService.startIfAllowed(c);
            WatchService.send(IdoProtocol.incomingCall(number, name == null ? number : name));
        } else if (ringing) {   // answered (OFFHOOK) or ended (IDLE): stop the alert on the watch
            ringing = false;
            WatchService.send(Collections.singletonList(IdoProtocol.callEnded()));
        }
    }

    /** Called when the watch's answer / reject buttons are pressed (07 01 0C / 0D). */
    @SuppressLint("MissingPermission")
    static void fromWatch(Context c, boolean answer) {
        try {
            TelecomManager tm = c.getSystemService(TelecomManager.class);
            if (answer) tm.acceptRingingCall(); else tm.endCall();
        } catch (SecurityException e) {
            Log.w(WatchService.TAG, "need ANSWER_PHONE_CALLS permission: " + e);
        }
    }

    static String lookup(Context c, String number) {
        try (Cursor cur = c.getContentResolver().query(
                Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
                new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null)) {
            if (cur != null && cur.moveToFirst()) return cur.getString(0);
        } catch (Exception ignored) {}
        return null;
    }
}
