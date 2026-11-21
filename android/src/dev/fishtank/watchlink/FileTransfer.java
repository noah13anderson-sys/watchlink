package dev.fishtank.watchlink;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

/**
 * IDO file transfer ("data_tran", v2 command group 0xD1), as libprotocol_c.so's data_tran_* code does it:
 *   D1 08  original (uncompressed) size: type, u32 size, compression, name   (only for *.iwf.lz files)
 *   D1 01  request: type, u32 size, compression, name                        reply: err, wait seconds
 *   D1 05  packets per acknowledgement (PRN)                                 reply: err, accepted prn
 *   D1 02  data: 00 + up to (MTU - 3) bytes; after every PRN packets the watch replies
 *          D1 02 err, u32 check code so far, u32 offset it has
 *   D1 03  end: u32 check code = sum of all bytes                           reply: err (0 = ok)
 * Error 0x18/0x19 on a request means "the watch is making room, try again in N seconds".
 * Names up to 12 bytes fit in a 20-byte packet; longer ones are sent in one packet of 8 + name length.
 */
final class FileTransfer {
    interface Listener { void onProgress(int percent); void onDone(boolean ok, String message); }

    static final int TYPE_IWF = 6;

    private static final String TAG = WatchService.TAG;
    private static final int PRN = 10;
    private static final Handler h = new Handler(Looper.getMainLooper());

    private static FileTransfer current;

    private final byte[] data;
    private final String name;
    private final int type, compression, originalSize;
    private final Listener listener;
    private int step, offset, sentSinceAck, lastPercent = -1, retries;

    private FileTransfer(int type, String name, byte[] data, int compression, int originalSize, Listener l) {
        this.type = type; this.name = name; this.data = data;
        this.compression = compression; this.originalSize = originalSize; this.listener = l;
    }

    static boolean busy() { return current != null; }

    /** Send a file; compression 0 = none. For "*.iwf.lz" the original size is announced first. */
    static void start(int type, String name, byte[] data, int compression, int originalSize, Listener l) {
        if (current != null) { l.onDone(false, "Another transfer is running"); return; }
        current = new FileTransfer(type, name, data, compression, originalSize, l);
        current.begin();
    }

    static void cancel() {
        if (current != null) current.finish(false, "Cancelled");
    }

    private void begin() {
        if (name.endsWith("iwf.lz")) { step = 8; send(header(0x08, originalSize)); }
        else { step = 1; send(header(0x01, data.length)); }
        armTimeout();
    }

    private byte[] header(int key, int size) {
        byte[] nm = name.getBytes(StandardCharsets.US_ASCII);
        byte[] p = new byte[nm.length <= 12 ? 20 : 8 + nm.length];
        p[0] = (byte) 0xD1; p[1] = (byte) key; p[2] = (byte) type;
        p[3] = (byte) size; p[4] = (byte) (size >> 8); p[5] = (byte) (size >> 16); p[6] = (byte) (size >> 24);
        p[7] = (byte) compression;
        System.arraycopy(nm, 0, p, 8, nm.length);
        return p;
    }

    private static void send(byte[] p) { WatchService.send(Collections.singletonList(p)); }

    private final Runnable timeout = () -> {
        if (current == this) finish(false, "The watch stopped answering (step " + step + ")");
    };

    private void armTimeout() {
        h.removeCallbacks(timeout);
        h.postDelayed(timeout, 15000);
    }

    /** Called by WatchService for every D1 xx notification. */
    static void onReply(byte[] v) {
        FileTransfer t = current;
        if (t != null) h.post(() -> t.handle(v));
    }

    private void handle(byte[] v) {
        if (current != this || v.length < 3) return;
        int key = v[1] & 0xff, err = v[2] & 0xff;
        Log.i(TAG, "transfer reply key " + key + " err " + err + " (step " + step + ")");
        armTimeout();
        if ((key == 0x08 || key == 0x01) && (err == 0x18 || err == 0x19)) {
            int wait = v.length > 3 ? Math.max(1, v[3] & 0xff) : 3;
            if (retries++ > 20) { finish(false, "The watch is still busy making room"); return; }
            h.postDelayed(() -> { if (current == this) send(header(key, key == 0x08 ? originalSize : data.length)); },
                    wait * 1000L);
            listener.onProgress(0);
            return;
        }
        switch (key) {
            case 0x08:
                if (err != 0) { finish(false, "Watch refused the file size (error " + err + ")"); return; }
                step = 1; send(header(0x01, data.length));
                break;
            case 0x01:
                if (err != 0) { finish(false, "Watch refused the file (error " + err + ")"); return; }
                step = 5; send(new byte[]{(byte) 0xD1, 0x05, (byte) PRN});
                break;
            case 0x05:
                if (err != 0) { finish(false, "Watch refused the packet setting (error " + err + ")"); return; }
                step = 2; offset = 0; sentSinceAck = 0;
                sendPackets();
                break;
            case 0x02: {
                if (err != 0) { finish(false, "Transfer error " + err + " at " + offset); return; }
                if (v.length >= 11) {
                    int got = (v[7] & 0xff) | (v[8] & 0xff) << 8 | (v[9] & 0xff) << 16 | (v[10] & 0xff) << 24;
                    if (got != offset) { Log.w(TAG, "watch has " + got + ", we sent " + offset + "; resending from there"); offset = got; }
                }
                sentSinceAck = 0;
                if (offset >= data.length) sendEnd(); else sendPackets();
                break;
            }
            case 0x03:
                finish(err == 0, err == 0 ? "Installed" : "The watch rejected the file (error " + err + ")");
                break;
            default:
                break;
        }
    }

    /** Queue up to PRN packets, then wait for the watch's D1 02 acknowledgement. */
    private void sendPackets() {
        int chunk = Math.min(512, Math.max(23, WatchService.mtu - 3)) - 3;
        java.util.List<byte[]> batch = new java.util.ArrayList<>();
        while (sentSinceAck < PRN && offset < data.length) {
            int n = Math.min(chunk, data.length - offset);
            byte[] p = new byte[3 + n];
            p[0] = (byte) 0xD1; p[1] = 0x02; p[2] = 0;
            System.arraycopy(data, offset, p, 3, n);
            batch.add(p);
            offset += n;
            sentSinceAck++;
        }
        WatchService.send(batch);
        int pct = (int) (offset * 100L / data.length);
        if (pct != lastPercent) { lastPercent = pct; listener.onProgress(Math.min(99, pct)); }
        // The library only waits for an acknowledgement after PRN packets; the last, short batch is
        // acknowledged too on watches with "data_tran_over_wait_ble_d1_02", so wait for it either way, but
        // don't hang if it never comes.
        if (offset >= data.length && sentSinceAck < PRN) {
            h.postDelayed(() -> { if (current == this && step == 2 && offset >= data.length) sendEnd(); }, 3000);
        }
    }

    private void sendEnd() {
        if (step == 3) return;
        step = 3;
        int c = dev.fishtank.watchlink.face.IwfFile.checkCode(data);
        send(new byte[]{(byte) 0xD1, 0x03, (byte) c, (byte) (c >> 8), (byte) (c >> 16), (byte) (c >> 24)});
    }

    private void finish(boolean ok, String msg) {
        h.removeCallbacks(timeout);
        if (current == this) current = null;
        Log.i(TAG, "transfer " + name + ": " + msg);
        listener.onDone(ok, msg);
    }
}
