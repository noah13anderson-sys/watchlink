package dev.fishtank.watchlink;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayDeque;
import java.util.Calendar;
import java.util.List;

/** Keeps a BLE connection to the watch and sends queued packets one at a time. */
@SuppressLint("MissingPermission")
public class WatchService extends Service {
    static final String TAG = "WatchLink";
    public static final String PREFS = "watchlink";
    /** No address is built in: the watch is found by scanning for its 0AF0 service, then remembered. */
    public static final String DEFAULT_MAC = "";

    static WatchService instance;
    static volatile String status = "Not started";
    static volatile int battery = -1;
    static volatile String firmware = "?";

    private final Handler h = new Handler(Looper.getMainLooper());
    private final ArrayDeque<byte[]> queue = new ArrayDeque<>();
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic writeChar;
    private boolean busy, ready;
    private BluetoothGattCharacteristic healthNotify;
    private int retryDelay = 2000;

    public static void start(Context c) {
        c.startForegroundService(new Intent(c, WatchService.class));
    }

    /** Start only when Bluetooth permission is granted (Android 14 rejects it otherwise). */
    public static void startIfAllowed(Context c) {
        if (instance == null && c.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            try { start(c); } catch (Exception e) { Log.w(TAG, "could not start service: " + e); }
        }
    }

    /** Called by the notification listener / call receiver. */
    public static void send(List<byte[]> packets) {
        WatchService s = instance;
        if (s == null) return;
        s.h.post(() -> { s.queue.addAll(packets); s.pump(); });
    }

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("conn", "Watch connection", NotificationManager.IMPORTANCE_MIN));
        Notification n = new Notification.Builder(this, "conn")
                .setContentTitle("WatchLink").setContentText("Connected to watch service")
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).build();
        if (android.os.Build.VERSION.SDK_INT >= 29)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        else startForeground(1, n);
        connect();
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onDestroy() {
        instance = null;
        h.removeCallbacksAndMessages(null);
        if (gatt != null) { gatt.disconnect(); gatt.close(); }
        super.onDestroy();
    }

    private void setStatus(String s) { status = s; Log.i(TAG, s); MainActivity.refresh(); }

    private void connect() {
        BluetoothAdapter ad = getSystemService(BluetoothManager.class).getAdapter();
        if (ad == null || !ad.isEnabled()) { setStatus("Bluetooth is off"); retry(); return; }
        String mac = getSharedPreferences(PREFS, 0).getString("mac", DEFAULT_MAC);
        if (mac == null || !BluetoothAdapter.checkBluetoothAddress(mac)) { scanForWatch(ad); return; }
        BluetoothDevice dev = ad.getRemoteDevice(mac);
        setStatus("Connecting…");
        if (gatt != null) gatt.close();
        gatt = dev.connectGatt(this, true, cb, BluetoothDevice.TRANSPORT_LE);  // autoConnect: reconnects by itself
    }

    // ---- First run: find the watch by its IDO service UUID (0AF0) ----
    private boolean scanning;
    private final android.bluetooth.le.ScanCallback scanCb = new android.bluetooth.le.ScanCallback() {
        @Override public void onScanResult(int type, android.bluetooth.le.ScanResult r) {
            if (!scanning) return;
            stopScan();
            String mac = r.getDevice().getAddress();
            Log.i(TAG, "found watch " + r.getDevice().getName());
            getSharedPreferences(PREFS, 0).edit().putString("mac", mac).apply();
            h.post(WatchService.this::connect);
        }
    };

    private void scanForWatch(BluetoothAdapter ad) {
        if (scanning) return;
        android.bluetooth.le.BluetoothLeScanner sc = ad.getBluetoothLeScanner();
        if (sc == null) { retry(); return; }
        setStatus("Looking for your watch…");
        scanning = true;
        android.bluetooth.le.ScanFilter f = new android.bluetooth.le.ScanFilter.Builder()
                .setServiceUuid(new android.os.ParcelUuid(IdoProtocol.SERVICE)).build();
        android.bluetooth.le.ScanSettings s = new android.bluetooth.le.ScanSettings.Builder()
                .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        sc.startScan(java.util.Collections.singletonList(f), s, scanCb);
        h.postDelayed(() -> { if (scanning) { stopScan(); setStatus("Watch not found, retrying…"); retry(); } }, 20000);
    }

    private void stopScan() {
        scanning = false;
        try {
            getSystemService(BluetoothManager.class).getAdapter().getBluetoothLeScanner().stopScan(scanCb);
        } catch (Exception ignored) {}
    }

    private void retry() {
        h.postDelayed(this::connect, retryDelay);
        retryDelay = Math.min(retryDelay * 2, 60000);
    }

    private long busySince;
    private int failedWrites;

    private void pump() {
        // Watchdog: if Android never confirmed the last write, don't wait forever (it used to stall the queue)
        if (busy && System.currentTimeMillis() - busySince > 3000) {
            Log.w(TAG, "write not confirmed after 3 s, continuing");
            busy = false;
        }
        if (!ready || busy || queue.isEmpty() || gatt == null) return;
        byte[] p = queue.poll();
        writeChar.setValue(p);
        writeChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        busy = gatt.writeCharacteristic(writeChar);
        Log.d(TAG, "-> " + IdoProtocol.hex(p) + (busy ? "" : " (failed)"));
        if (busy) {
            busySince = System.currentTimeMillis();
            failedWrites = 0;
            h.postDelayed(this::pump, 3100);          // wakes the watchdog if no confirmation comes
        } else {
            queue.addFirst(p);                        // try the same packet again shortly
            if (++failedWrites > 20) {                // link is probably dead: reconnect
                Log.w(TAG, "writes keep failing, reconnecting");
                failedWrites = 0;
                ready = false;
                gatt.disconnect();
                gatt.close();
                gatt = null;
                h.postDelayed(this::connect, 2000);
                return;
            }
            h.postDelayed(this::pump, 250);
        }
    }

    private void enableNotify(BluetoothGattCharacteristic c) {
        if (c == null) return;
        gatt.setCharacteristicNotification(c, true);
        BluetoothGattDescriptor d = c.getDescriptor(IdoProtocol.CCCD);
        if (d != null) {
            d.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            gatt.writeDescriptor(d);
        }
    }

    private final BluetoothGattCallback cb = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt g, int st, int newState) {
            h.post(() -> {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    retryDelay = 2000;
                    setStatus("Connected, discovering…");
                    g.requestMtu(517);
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    ready = false; busy = false;
                    setStatus("Disconnected (waiting for watch)");
                }
            });
        }

        @Override public void onMtuChanged(BluetoothGatt g, int m, int st) {
            if (st == BluetoothGatt.GATT_SUCCESS) mtu = m;
            Log.i(TAG, "MTU " + m + " status " + st);
            h.post(g::discoverServices);
        }

        @Override public void onServicesDiscovered(BluetoothGatt g, int st) {
            h.post(() -> {
                for (BluetoothGattService sv : g.getServices()) {
                    StringBuilder sb = new StringBuilder("gatt service " + sv.getUuid());
                    for (BluetoothGattCharacteristic c : sv.getCharacteristics())
                        sb.append("\n   ").append(c.getUuid()).append(" props=").append(c.getProperties());
                    Log.i(TAG, sb.toString());
                }
                BluetoothGattService s = g.getService(IdoProtocol.SERVICE);
                if (s == null) { setStatus("Watch service 0AF0 not found"); return; }
                writeChar = s.getCharacteristic(IdoProtocol.WRITE);
                healthNotify = s.getCharacteristic(IdoProtocol.HEALTH_NOTIFY);
                enableNotify(s.getCharacteristic(IdoProtocol.NOTIFY));
                // the 0AF2 descriptor write must wait for the first one; done in onDescriptorWrite
            });
        }

        @Override public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor d, int st) {
            h.post(() -> {
                if (ready) return;
                if (healthNotify != null) {            // second step: health channel 0AF2
                    BluetoothGattCharacteristic hn = healthNotify;
                    healthNotify = null;
                    enableNotify(hn);
                    return;
                }
                ready = true;
                setStatus("Connected");
                queue.addFirst(IdoProtocol.getDeviceInfo());
                queue.addFirst(IdoProtocol.setTime(Calendar.getInstance()));
                queue.add(IdoProtocol.spo2AllDay(true));       // so the watch saves SpO2 readings
                queue.add(IdoProtocol.weatherSwitch(true));
                pump();
                h.postDelayed(WatchService.this::requestHealth, 3000);   // fresh health data after connecting
                h.postDelayed(() -> { MusicSync.start(WatchService.this); MusicSync.push(); }, 1500);
                h.postDelayed(() -> Weather.update(WatchService.this), 6000);
            });
        }

        @Override public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic c, int st) {
            h.postDelayed(() -> { busy = false; pump(); }, 30);
        }

        @Override public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c) {
            byte[] v = c.getValue();
            boolean health = IdoProtocol.HEALTH_NOTIFY.equals(c.getUuid());
            Log.d(TAG, (health ? "<-H " : "<- ") + IdoProtocol.hex(v));
            if (v.length < 2) return;
            int cmd = v[0] & 0xff, key = v[1] & 0xff;
            if (cmd == 0x33) { h.post(() -> onV3Packet(v)); return; }
            if (cmd == 0xD1) { FileTransfer.onReply(v); return; }
            if (cmd == 0x02 && key == 0x01 && v.length >= 8) {
                firmware = (v[4] & 0xff) + "." + String.format("%02d", v[5] & 0xff);
                battery = v[7] & 0xff;
                h.post(MainActivity::refresh);
            } else if (cmd == 0x02 && key == 0xA0 && v.length >= 19) {
                steps = IdoProtocol.u32(v, 2);
                kcal = IdoProtocol.u32(v, 6);
                distance = IdoProtocol.u32(v, 10);
                activeTime = IdoProtocol.u32(v, 14);
                h.post(MainActivity::refresh);
            } else if (cmd == 0x07 && key == 0x01 && v.length >= 3) {
                h.post(() -> MediaControl.handle(WatchService.this, v[2] & 0xff));
            } else if (cmd == 0x07 && key == 0x02) {
                h.post(() -> FindPhone.start(WatchService.this));
            } else if (cmd == 0x07 && key == 0x40) {
                // the watch says a setting changed (also after our own writes): read the settings again
                h.removeCallbacks(readSettingsLater);
                h.postDelayed(readSettingsLater, 1500);
            } else if (cmd == 0x02 && (key == 0x30 || key == 0x47 || key == 0xB1 || key == 0x43)) {
                h.post(() -> onSettingReply(key, v));
            }
        }
    };

    // ---- Watch settings read back from the watch (02 30 / 02 47 / 02 B1 / 02 43) ----
    static volatile byte[] lastGoalReply;
    private final Runnable readSettingsLater = WatchService::readSettings;

    /** Ask the watch for the settings it can report; replies land in the prefs (see onSettingReply). */
    public static void readSettings() {
        send(java.util.Arrays.asList(IdoProtocol.getDoNotDisturb(), IdoProtocol.getWalkReminder(),
                IdoProtocol.getRaiseToWake(), IdoProtocol.getCalorieDistanceGoal()));
    }

    private static int b(byte[] v, int i) { return i < v.length ? v[i] & 0xff : 0; }

    private void onSettingReply(int key, byte[] v) {
        android.content.SharedPreferences.Editor e = getSharedPreferences(PREFS, 0).edit();
        if (key == 0x30) {
            e.putBoolean("dnd.on", b(v, 2) == 0xAA).putInt("dnd.sh", b(v, 3)).putInt("dnd.sm", b(v, 4))
                    .putInt("dnd.eh", b(v, 5)).putInt("dnd.em", b(v, 6));
        } else if (key == 0x47) {
            e.putBoolean("walk.on", b(v, 2) != 0).putInt("walk.steps", b(v, 3) | b(v, 4) << 8)
                    .putInt("walk.sh", b(v, 5)).putInt("walk.sm", b(v, 6)).putInt("walk.eh", b(v, 7))
                    .putInt("walk.em", b(v, 8)).putInt("walk.days", IdoProtocol.daysFromField(b(v, 9)));
        } else if (key == 0xB1) {
            e.putBoolean("raise.on", b(v, 2) == 0xAA).putInt("raise.sec", b(v, 3)).putBoolean("raise.ranged", b(v, 4) != 0)
                    .putInt("raise.sh", b(v, 5)).putInt("raise.sm", b(v, 6)).putInt("raise.eh", b(v, 7)).putInt("raise.em", b(v, 8));
        } else if (key == 0x43 && v.length >= 10) {
            lastGoalReply = v;
            e.putInt("goal.kcal", (int) IdoProtocol.u32(v, 2)).putInt("goal.meters", (int) IdoProtocol.u32(v, 6));
        }
        e.apply();
        SettingsActivity.refresh();
    }

    static volatile int mtu = 23;

    /** Send a v3 command (framed + split to the current MTU). */
    public static void sendV3(int cmd, byte[] payload) {
        send(IdoProtocol.v3(cmd, payload, Math.max(20, mtu - 3)));
    }

    // ---- v3 receive: reassemble 0x33 packets into frames ----
    private final java.io.ByteArrayOutputStream v3buf = new java.io.ByteArrayOutputStream();
    private int v3want = -1;

    private void onV3Packet(byte[] v) {
        if (v.length >= 8 && (v[1] & 0xff) == 0xDA && (v[2] & 0xff) == 0xAD) {   // first packet of a frame
            v3buf.reset();
            int cmd = (v[8] & 0xff) | (v[9] & 0xff) << 8;
            // health data (cmd 4) frames come without the 2-byte CRC
            v3want = ((v[6] & 0xff) | (v[7] & 0xff) << 8) + (cmd == IdoProtocol.V3_HEALTH ? 0 : 2);
        }
        if (v3want < 0) return;
        v3buf.write(v, 1, v.length - 1);
        if (v3buf.size() >= v3want) {
            byte[] f = v3buf.toByteArray();
            v3want = -1;
            int cmd = (f[7] & 0xff) | (f[8] & 0xff) << 8;
            int len = (f[5] & 0xff) | (f[6] & 0xff) << 8;
            boolean crcOk = cmd == IdoProtocol.V3_HEALTH || f.length < len + 2
                    || IdoProtocol.crc16(f, len) == ((f[len] & 0xff) | (f[len + 1] & 0xff) << 8);
            byte[] payload = java.util.Arrays.copyOfRange(f, 11, Math.min(len, f.length));
            Log.i(TAG, "v3 frame cmd=0x" + Integer.toHexString(cmd) + " crc=" + (crcOk ? "ok" : "BAD")
                    + " payload=" + IdoProtocol.hex(payload));
            if (cmd == IdoProtocol.V3_HEALTH) onHeartPage(payload);
            else if (cmd == IdoProtocol.V3_FACE_LIST) {
                faces = IdoProtocol.parseFaceList(payload);
                FacesActivity.refresh();
            } else if (cmd == IdoProtocol.V3_FACE_LIST_NEW) {
                IdoProtocol.FaceList fl = IdoProtocol.parseFaceListNew(payload);
                if (fl != null) { faceList = fl; faces = fl.names; FacesActivity.refresh(); }
            } else if (cmd == IdoProtocol.V3_FACE_SET) FacesActivity.onSetResult(payload.length > 0 && payload[0] == 0);
        }
    }

    /** Faces installed on the watch (null until the watch has answered). */
    static volatile List<String> faces;
    /** Faces with sizes and free space (v3 0x31), null until the watch has answered. */
    static volatile IdoProtocol.FaceList faceList;

    // ---- Health sync: heart rate, stress, SpO2, sleep (one type at a time) ----
    /** Latest value, its time (seconds after midnight, -1 = none), and today's min/max. */
    static final class Series { volatile int value, time = -1, min, max; }
    static final Series hr = new Series(), stress = new Series(), spo2 = new Series();
    static volatile int sleepMins = -1;
    // kept for older callers
    static volatile int heartRate, hrTime = -1, hrMin, hrMax;

    private static final int[] HEALTH_TYPES = {IdoProtocol.HEALTH_TYPE_HR, IdoProtocol.HEALTH_TYPE_STRESS,
            IdoProtocol.HEALTH_TYPE_SPO2, IdoProtocol.HEALTH_TYPE_SLEEP};
    private int healthIdx = -1, healthOffset = -1, healthRun;
    private final List<int[]> healthSamples = new java.util.ArrayList<>();

    /** Download today's health data, one type after another. */
    void requestHealth() {
        healthIdx = 0;
        healthRun++;
        startHealthType();
    }

    void requestHeartRate() { requestHealth(); }

    private void startHealthType() {
        if (healthIdx < 0 || healthIdx >= HEALTH_TYPES.length) { healthIdx = -1; MainActivity.refresh(); return; }
        healthOffset = 0;
        healthSamples.clear();
        sendV3(IdoProtocol.V3_HEALTH, IdoProtocol.healthRequest(HEALTH_TYPES[healthIdx], 0));
        // move on if the watch never answers
        final int idx = healthIdx, run = healthRun;
        h.postDelayed(() -> { if (healthRun == run && healthIdx == idx) { healthIdx++; startHealthType(); } }, 8000);
    }

    private String lastRaw = "";

    /** Keep a copy of new, non-empty health replies (Android/data/dev.fishtank.watchlink/files/health-raw.txt)
     *  so formats such as sleep stages can be decoded later. */
    private void saveRaw(String what, byte[] payload) {
        boolean empty = true;
        for (int i = 5; i < payload.length; i++) if (payload[i] != 0 && i != 7) { empty = false; break; }
        String hex = IdoProtocol.hex(payload);
        if (empty || hex.equals(lastRaw)) return;
        lastRaw = hex;
        try (java.io.FileWriter w = new java.io.FileWriter(new java.io.File(getExternalFilesDir(null), "health-raw.txt"), true)) {
            w.write(new java.util.Date() + " " + what + " " + hex + "\n");
        } catch (Exception e) { Log.w(TAG, "saveRaw", e); }
    }

    private void onHeartPage(byte[] payload) {
        if (healthIdx < 0 || payload.length < 5) return;
        int type = HEALTH_TYPES[healthIdx];
        if ((payload[1] & 0xff) != type) return;
        int echoedOffset = (payload[3] & 0xff) | (payload[4] & 0xff) << 8;
        if (echoedOffset != healthOffset) return;      // stale/duplicate reply

        if (type == IdoProtocol.HEALTH_TYPE_SLEEP) {
            saveRaw("sleep", payload);
            sleepMins = IdoProtocol.parseSleepTotal(payload);
            Log.i(TAG, "sleep " + sleepMins + " min");
            healthIdx++; startHealthType();
            return;
        }
        IdoProtocol.HeartPage p = IdoProtocol.parseSeries(payload, type);
        if (p == null) return;
        healthSamples.addAll(p.samples);
        // "items" = items remaining from this offset; a page holds dataLen/2 of them.
        // Past the end the watch starts over from 0, so only continue while items remain.
        if (p.items > p.pageItems && p.pageItems > 0 && healthOffset < 5000) {
            healthOffset += p.pageItems;
            sendV3(IdoProtocol.V3_HEALTH, IdoProtocol.healthRequest(type, healthOffset));
            return;
        }
        Series s = type == IdoProtocol.HEALTH_TYPE_HR ? hr : type == IdoProtocol.HEALTH_TYPE_STRESS ? stress : spo2;
        if (!healthSamples.isEmpty()) {
            int[] last = healthSamples.get(healthSamples.size() - 1);
            int mn = 999, mx = 0;
            for (int[] x : healthSamples) { mn = Math.min(mn, x[1]); mx = Math.max(mx, x[1]); }
            s.value = last[1]; s.time = last[0]; s.min = mn; s.max = mx;
            if (s == hr) { heartRate = s.value; hrTime = s.time; hrMin = mn; hrMax = mx; }
            Log.i(TAG, "health type " + type + ": " + s.value + " at " + s.time / 3600 + ":" + (s.time / 60) % 60
                    + " (" + healthSamples.size() + " samples, " + mn + "-" + mx + ")");
        }
        healthIdx++;
        startHealthType();
    }

    static volatile long steps = -1, kcal, distance, activeTime;

    /** Poll live steps every minute and heart rate every 5 minutes while connected. */
    private int pollCount;
    private final Runnable livePoll = new Runnable() {
        @Override public void run() {
            if (ready) {
                queue.add(IdoProtocol.getLiveData()); pump();
                if (pollCount % 30 == 29) Weather.update(WatchService.this);
                if (pollCount++ % 5 == 0) requestHealth();
            }
            pump();                                   // also lets the watchdog run once a minute
            h.postDelayed(this, 60000);
        }
    };

    public static void refreshHeartRate() {
        WatchService s = instance;
        if (s != null) s.h.post(s::requestHealth);
    }
    { h.postDelayed(livePoll, 5000); }

    @Override public int onStartCommand(Intent i, int f, int id) {
        if (i != null && FindPhone.ACTION_STOP.equals(i.getAction())) FindPhone.stop(this);
        return START_STICKY;
    }
}
