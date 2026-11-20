package dev.fishtank.watchlink;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.List;

/**
 * Follows whatever is playing on the phone (via media sessions; needs notification access) and keeps the
 * watch's music screen up to date with v3 command 0x0A. Watch buttons are routed to the same player.
 */
final class MusicSync {
    private static MediaController current;
    private static final Handler h = new Handler(Looper.getMainLooper());
    private static String lastSent = "";

    private MusicSync() {}

    private static boolean started;

    static void start(Context c) {
        if (started) return;
        try {
            MediaSessionManager msm = c.getSystemService(MediaSessionManager.class);
            ComponentName cn = new ComponentName(c, NotifyListener.class);
            msm.addOnActiveSessionsChangedListener(list -> pick(list), cn, h);
            pick(msm.getActiveSessions(cn));
            started = true;
        } catch (SecurityException e) {
            Log.w(WatchService.TAG, "music sync needs notification access: " + e);
        }
    }

    private static void pick(List<MediaController> list) {
        MediaController best = null;
        if (list != null) for (MediaController mc : list) {
            PlaybackState st = mc.getPlaybackState();
            if (best == null) best = mc;
            if (st != null && st.getState() == PlaybackState.STATE_PLAYING) { best = mc; break; }
        }
        if (current != null) current.unregisterCallback(cb);
        current = best;
        if (current != null) current.registerCallback(cb, h);
        push();
    }

    private static final MediaController.Callback cb = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState s) { push(); }
        @Override public void onMetadataChanged(MediaMetadata m) { push(); }
        @Override public void onSessionDestroyed() { current = null; push(); }
    };

    /** Send the current track and state to the watch (skipped if nothing changed). */
    static void push() {
        String title = "", artist = "";
        int status = 2, pos = 0, dur = 0;   // 1 = playing, 2 = paused (matches the 07 01 01/02 event codes)
        if (current != null) {
            MediaMetadata m = current.getMetadata();
            if (m != null) {
                CharSequence t = m.getText(MediaMetadata.METADATA_KEY_TITLE);
                CharSequence a = m.getText(MediaMetadata.METADATA_KEY_ARTIST);
                title = t == null ? "" : t.toString();
                artist = a == null ? "" : a.toString();
                dur = (int) Math.max(0, m.getLong(MediaMetadata.METADATA_KEY_DURATION) / 1000);
            }
            PlaybackState s = current.getPlaybackState();
            if (s != null) {
                if (s.getState() == PlaybackState.STATE_PLAYING) status = 1;
                pos = (int) Math.max(0, s.getPosition() / 1000);
            }
        }
        String name = artist.isEmpty() ? title : title + " - " + artist;
        String key = status + "|" + name + "|" + dur;
        if (key.equals(lastSent)) return;
        lastSent = key;
        WatchService.sendV3(IdoProtocol.V3_MUSIC_INFO, IdoProtocol.musicInfo(status, pos, dur, name));
    }

    /** Watch button pressed: control the active player directly, falling back to media keys. */
    static boolean control(int evt) {
        if (current == null) return false;
        MediaController.TransportControls tc = current.getTransportControls();
        if (evt == IdoProtocol.EVT_MUSIC_PLAY || evt == IdoProtocol.EVT_MUSIC_PAUSE) {
            // the SW2S sends "play" (01) for every press of its play/pause button, so toggle
            PlaybackState st = current.getPlaybackState();
            boolean playing = st != null && st.getState() == PlaybackState.STATE_PLAYING;
            if (playing) tc.pause(); else tc.play();
            return true;
        }
        switch (evt) {
            case IdoProtocol.EVT_MUSIC_PLAY: tc.play(); return true;
            case IdoProtocol.EVT_MUSIC_PAUSE: tc.pause(); return true;
            case IdoProtocol.EVT_MUSIC_STOP: tc.stop(); return true;
            case IdoProtocol.EVT_MUSIC_PREV: tc.skipToPrevious(); return true;
            case IdoProtocol.EVT_MUSIC_NEXT: tc.skipToNext(); return true;
            default: return false;
        }
    }
}
