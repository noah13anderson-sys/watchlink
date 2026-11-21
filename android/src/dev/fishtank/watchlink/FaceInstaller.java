package dev.fishtank.watchlink;

import android.os.Handler;
import android.os.Looper;

import dev.fishtank.watchlink.face.IwfFile;

/** Sends a finished .iwf face to the watch (FastLZ-compressed, D1 file transfer) and shows it. */
final class FaceInstaller {
    interface Status { void show(String text, boolean done); }

    private FaceInstaller() {}

    /** First "wlN.iwf" name that isn't on the watch yet. */
    static String nextName() {
        IdoProtocol.FaceList fl = WatchService.faceList;
        for (int n = 1; n < 100; n++) {
            String name = "wl" + n + ".iwf";
            if (fl == null || !fl.names.contains(name)) return name;
        }
        return "wl99.iwf";
    }

    /**
     * Installs a face made in WatchLink. When the watch has no free slot or not enough room, an older WatchLink face
     * (the one on show if it is ours, else the first) is removed first, so a new design replaces the last one.
     */
    static void installMine(String name, byte[] iwf, Status st) {
        Handler h = new Handler(Looper.getMainLooper());
        IdoProtocol.FaceList fl = WatchService.faceList;
        if (WatchService.instance != null && fl != null && (fl.installed >= fl.slots || iwf.length > fl.free)) {
            String old = fl.current != null && fl.current.startsWith("wl") ? fl.current : null;
            for (String f : fl.names) if (old == null && f.startsWith("wl")) old = f;
            if (old != null) {
                String gone = old;
                h.post(() -> st.show("Replacing your face " + gone.replace(".iwf", "") + "…", false));
                if (gone.equals(fl.current))   // don't remove the face on show: show a built-in one meanwhile
                    for (String f : fl.names)
                        if (!f.startsWith("wl")) { WatchService.sendV3(IdoProtocol.V3_FACE_SET, IdoProtocol.faceSet(f)); break; }
                WatchService.sendV3(IdoProtocol.V3_FACE_SET, IdoProtocol.faceDelete(gone));
                h.postDelayed(() -> new Thread(() -> send(name, iwf, st)).start(), 2500);   // a slot was just freed
                return;
            }
        }
        install(name, iwf, st);
    }

    /** Checks room on the watch and sends the face. Call on any thread; status arrives on the main thread. */
    static void install(String name, byte[] iwf, Status st) {
        Handler h = new Handler(Looper.getMainLooper());
        if (WatchService.instance == null) { h.post(() -> st.show("Not connected to the watch.", true)); return; }
        IdoProtocol.FaceList fl = WatchService.faceList;
        if (fl != null && fl.installed >= fl.slots) {
            h.post(() -> st.show("The watch already has " + fl.installed + " of " + fl.slots
                    + " faces. Long-press one in Watch faces to remove it first.", true));
            return;
        }
        if (fl != null && iwf.length > fl.free) {
            h.post(() -> st.show("Not enough room on the watch (" + iwf.length / 1024 + " KB needed, "
                    + fl.free / 1024 + " KB free). Remove a face in Watch faces first.", true));
            return;
        }
        send(name, iwf, st);
    }

    private static void send(String name, byte[] iwf, Status st) {
        Handler h = new Handler(Looper.getMainLooper());
        byte[] lz = IwfFile.compress(iwf, 4096);
        h.post(() -> {
            st.show("Sending to the watch…", false);
            FileTransfer.start(FileTransfer.TYPE_IWF, name + ".lz", lz, 2, iwf.length, new FileTransfer.Listener() {
                @Override public void onProgress(int pc) { h.post(() -> st.show("Sending to the watch… " + pc + "%", false)); }
                @Override public void onDone(boolean ok, String msg) {
                    h.post(() -> {
                        if (ok) {
                            WatchService.sendV3(IdoProtocol.V3_FACE_SET, IdoProtocol.faceSet(name));
                            WatchService.sendV3(IdoProtocol.V3_FACE_LIST_NEW, new byte[0]);
                            st.show("Installed as " + name.replace(".iwf", "") + " and shown on the watch.", true);
                        } else st.show("Didn't work: " + msg, true);
                    });
                }
            });
        });
    }
}
