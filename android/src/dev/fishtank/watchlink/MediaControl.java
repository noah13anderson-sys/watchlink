package dev.fishtank.watchlink;

import android.content.Context;
import android.media.AudioManager;
import android.os.SystemClock;
import android.view.KeyEvent;

/** Turns the watch's music buttons (07 01 xx) into phone media keys. */
final class MediaControl {
    private MediaControl() {}

    static void handle(Context c, int evt) {
        AudioManager am = c.getSystemService(AudioManager.class);
        android.util.Log.i(WatchService.TAG, "watch music button " + evt);
        if (MusicSync.control(evt)) return;
        switch (evt) {
            case IdoProtocol.EVT_MUSIC_PLAY:  key(am, KeyEvent.KEYCODE_MEDIA_PLAY); break;
            case IdoProtocol.EVT_MUSIC_PAUSE: key(am, KeyEvent.KEYCODE_MEDIA_PAUSE); break;
            case IdoProtocol.EVT_MUSIC_STOP:  key(am, KeyEvent.KEYCODE_MEDIA_STOP); break;
            case IdoProtocol.EVT_MUSIC_PREV:  key(am, KeyEvent.KEYCODE_MEDIA_PREVIOUS); break;
            case IdoProtocol.EVT_MUSIC_NEXT:  key(am, KeyEvent.KEYCODE_MEDIA_NEXT); break;
            case IdoProtocol.EVT_VOL_UP:
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI); break;
            case IdoProtocol.EVT_VOL_DOWN:
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI); break;
            case IdoProtocol.EVT_CALL_ANSWER: CallReceiver.fromWatch(c, true); break;
            case IdoProtocol.EVT_CALL_REJECT: CallReceiver.fromWatch(c, false); break;
            default: break;
        }
    }

    private static void key(AudioManager am, int code) {
        long t = SystemClock.uptimeMillis();
        am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
        am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0));
    }
}
