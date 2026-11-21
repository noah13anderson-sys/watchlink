package dev.fishtank.watchlink;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

/**
 * Packet builders for the IDO protocol used by the Momax SW2S.
 * Every packet is 20 bytes: [cmd][key][18 bytes payload].
 *
 * Verified on the real watch (2026-11-20):
 *   02 01  get device info  -> reply 02 01 <id lo> <id hi> <fw major> <fw minor> ? <battery%> ...
 *   03 01  set time         -> reply 03 01
 *   05 03  message notice   -> reply 05 03 <total> <index> 00 per chunk
 * From libprotocol_c.so, not yet tested:
 *   05 01  incoming-call notice
 */
public final class IdoProtocol {
    private IdoProtocol() {}

    static UUID u16(int v) { return UUID.fromString(String.format("%08x-0000-1000-8000-00805f9b34fb", v)); }
    public static final UUID SERVICE       = u16(0x0AF0);
    public static final UUID WRITE         = u16(0x0AF6);
    public static final UUID NOTIFY        = u16(0x0AF7);
    public static final UUID HEALTH_NOTIFY = u16(0x0AF2);
    public static final UUID CCCD          = u16(0x2902);
    public static final UUID BATTERY_SVC   = u16(0x180F);
    public static final UUID BATTERY_LEVEL = u16(0x2A19);

    static byte[] packet(int cmd, int key, byte[] payload) {
        byte[] p = new byte[20];
        p[0] = (byte) cmd; p[1] = (byte) key;
        if (payload != null) System.arraycopy(payload, 0, p, 2, Math.min(18, payload.length));
        return p;
    }

    public static byte[] getDeviceInfo() { return packet(0x02, 0x01, null); }

    public static byte[] setTime(Calendar c) {
        int y = c.get(Calendar.YEAR);
        int dow = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7;   // Monday = 0
        return packet(0x03, 0x01, new byte[]{
                (byte) (y & 0xff), (byte) (y >> 8),
                (byte) (c.get(Calendar.MONTH) + 1), (byte) c.get(Calendar.DAY_OF_MONTH),
                (byte) c.get(Calendar.HOUR_OF_DAY), (byte) c.get(Calendar.MINUTE),
                (byte) c.get(Calendar.SECOND), (byte) dow});
    }

    /** UTF-8 bytes truncated to max bytes without cutting a character in half. */
    static byte[] utf8(String s, int max) {
        if (s == null) s = "";
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        if (b.length <= max) return b;
        int end = max;
        while (end > 0 && (b[end] & 0xC0) == 0x80) end--;   // back off continuation bytes
        byte[] out = new byte[end];
        System.arraycopy(b, 0, out, 0, end);
        return out;
    }

    static List<byte[]> chunk(int cmd, int key, byte[] body) {
        int n = (body.length + 15) / 16;
        List<byte[]> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            byte[] pl = new byte[18];
            pl[0] = (byte) n; pl[1] = (byte) (i + 1);
            System.arraycopy(body, i * 16, pl, 2, Math.min(16, body.length - i * 16));
            out.add(packet(cmd, key, pl));
        }
        return out;
    }

    /** Message notification. appType 0x01 is verified to display; other values untested. */
    public static List<byte[]> message(int appType, String sender, String text) {
        byte[] contact = utf8(sender, 64);
        byte[] phone = new byte[0];
        byte[] body = utf8(text, 200);
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(appType); b.write(body.length); b.write(phone.length); b.write(contact.length);
        b.write(phone, 0, phone.length); b.write(contact, 0, contact.length); b.write(body, 0, body.length);
        return chunk(0x05, 0x03, b.toByteArray());
    }

    /** Incoming call: body = [phone len][contact len] phone contact. Untested on the watch. */
    public static List<byte[]> incomingCall(String number, String name) {
        byte[] phone = utf8(number, 32);
        byte[] contact = utf8(name, 64);
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(phone.length); b.write(contact.length);
        b.write(phone, 0, phone.length); b.write(contact, 0, contact.length);
        return chunk(0x05, 0x01, b.toByteArray());
    }

    // ---- Live activity ------------------------------------------------------
    /** 02 A0: reply = u32 steps, u32 kcal, u32 distance(m), u32 active time, u8 heart rate (from offset 2). */
    public static byte[] getLiveData() { return packet(0x02, 0xA0, null); }

    static long u32(byte[] v, int o) {
        return (v[o] & 0xffL) | (v[o + 1] & 0xffL) << 8 | (v[o + 2] & 0xffL) << 16 | (v[o + 3] & 0xffL) << 24;
    }

    // ---- App -> watch controls (06 xx: payload 00 = start, 01 = stop) ---------
    public static byte[] findWatch(boolean start) { return packet(0x06, 0x04, new byte[]{(byte) (start ? 0 : 1)}); }
    public static byte[] musicScreen(boolean open) { return packet(0x06, 0x01, new byte[]{(byte) (open ? 0 : 1)}); }

    // ---- Alarms (03 02, one packet per alarm, id 0..19) ------------------------
    /**
     * Experimental: the status and repeat encodings follow the usual IDO convention
     * (status AA = on / 55 = off; repeat bit0 = enabled, bit1..7 = Mon..Sun) and are not yet confirmed on the SW2S.
     */
    public static byte[] alarm(int id, boolean on, int hour, int minute, int daysMonToSunMask, int snoozeMin) {
        int repeat = (on ? 1 : 0) | ((daysMonToSunMask & 0x7f) << 1);
        return packet(0x03, 0x02, new byte[]{
                (byte) id, (byte) (on ? 0xAA : 0x55), 0x00 /* type: wake up */,
                (byte) hour, (byte) minute, (byte) repeat, (byte) snoozeMin});
    }

    // ---- Notifications, v3 (cmd 0x0B) ---------------------------------------------
    // Payload: ver(1), flags(0), has_contact, has_phone, has_msg, u16 (type | 0x3000), u32 id, u32 0, u16 0,
    //          then each present string as 01 + u32 len + bytes (contact, phone, message).
    // Verified 2026-11-20: shows correctly on the SW2S. The old 05 03 format shows "IDO test" on this firmware.
    public static final int V3_NOTICE = 0x0B;

    public static byte[] noticeV3(int appType, String contact, String phone, String msg) {
        byte[] c = utf8(contact, 63), ph = utf8(phone, 31), m = utf8(msg, 250);
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(1); b.write(0);
        b.write(c.length > 0 ? 1 : 0); b.write(ph.length > 0 ? 1 : 0); b.write(m.length > 0 ? 1 : 0);
        int t = appType | 0x3000;
        b.write(t & 0xff); b.write(t >> 8);
        for (int i = 0; i < 10; i++) b.write(0);          // u32 id, u32 0, u16 0
        for (byte[] s : new byte[][]{c, ph, m}) {
            if (s.length == 0) continue;
            b.write(1);
            b.write(s.length); b.write(0); b.write(0); b.write(0);
            b.write(s, 0, s.length);
        }
        return b.toByteArray();
    }

    // ---- Alarms, v3 (cmd 0x0E) ---------------------------------------------------
    // Payload: [version 0][count 10] + 10 x 34-byte slots:
    //   id, status(AA on / 55 off), type, hour, minute, repeat, snooze, delay_min, shock_on_off, repeat_times, name[24]
    // Verified 2026-11-20: a v3 alarm rang on the SW2S (it does not appear in the watch's own alarm list).
    public static final int V3_SET_ALARMS = 0x0E;

    public static final class Alarm {
        public boolean on; public int hour, minute, daysMonToSun; public String name;
        public Alarm(boolean on, int h, int m, int days, String name) {
            this.on = on; hour = h; minute = m; daysMonToSun = days; this.name = name;
        }
    }

    /**
     * Alarm list semantics, learned from Momax Fit's own alarms (read back with 0x0F) and tested 2026-11-20:
     *   entry status 0x55 + id  = add/update alarm <id>
     *   entry status 0xAA + id  = delete alarm <id>; later alarms shift down (ids renumber)
     *   all-zero entries are ignored
     *   repeat bit0 = on/off, bits1..7 = Mon..Sun; Momax uses snooze 0, delay 5, shock 0, times 3.
     */
    public static byte[] alarmsClearAllV3() {
        byte[] p = new byte[2 + 10 * 34];
        p[1] = 10;
        for (int i = 0; i < 10; i++) { p[2 + i * 34] = 1; p[3 + i * 34] = (byte) 0xAA; }  // delete #1, ten times
        return p;
    }

    /** Adds the given (non-null) alarms as ids 1..n. Send alarmsClearAllV3() first to replace the list. */
    public static byte[] alarmsV3(List<Alarm> alarms) {
        byte[] p = new byte[2 + 10 * 34];
        p[0] = 0; p[1] = 10;
        int n = 0;
        for (Alarm a : alarms) {
            if (a == null || n >= 10) continue;
            int o = 2 + n * 34;
            p[o] = (byte) (++n);
            p[o + 1] = 0x55;
            p[o + 2] = 0;                          // type
            p[o + 3] = (byte) a.hour;
            p[o + 4] = (byte) a.minute;
            p[o + 5] = (byte) ((a.on ? 1 : 0) | ((a.daysMonToSun & 0x7f) << 1));
            p[o + 6] = 0;                          // snooze
            p[o + 7] = 5;                          // delay_min
            p[o + 8] = 0;                          // shock_on_off
            p[o + 9] = 3;                          // repeat_times
            byte[] nm = utf8(a.name, 23);
            System.arraycopy(nm, 0, p, o + 10, nm.length);
        }
        return p;
    }

    // ---- Watch -> app events (07 01 xx) ----------------------------------------
    public static final int EVT_MUSIC_PLAY = 0x01, EVT_MUSIC_PAUSE = 0x02, EVT_MUSIC_STOP = 0x03,
            EVT_MUSIC_PREV = 0x04, EVT_MUSIC_NEXT = 0x05, EVT_VOL_UP = 0x08, EVT_VOL_DOWN = 0x09;

    // ---- v3 framing -------------------------------------------------------------
    // Frame: DA AD DA AD | 01 | u16 len(=payload+11) | u16 cmd | u16 seq | payload | crc16 (LE)
    // Sent in BLE packets that each start with 0x33. CRC = Nordic crc16_compute (CCITT, init FFFF).
    // Verified against frames sent by the watch (crc 0x7d5f matched).
    private static int v3seq = 1;

    static int crc16(byte[] d, int len) {
        int crc = 0xFFFF;
        for (int i = 0; i < len; i++) {
            crc = ((crc >>> 8) | (crc << 8)) & 0xFFFF;
            crc ^= d[i] & 0xFF;
            crc ^= (crc & 0xFF) >> 4;
            crc ^= (crc << 12) & 0xFFFF;
            crc ^= ((crc & 0xFF) << 5) & 0xFFFF;
        }
        return crc;
    }

    public static synchronized List<byte[]> v3(int cmd, byte[] payload, int maxPacket) {
        int len = payload.length + 11;
        byte[] f = new byte[len + 2];
        f[0] = (byte) 0xDA; f[1] = (byte) 0xAD; f[2] = (byte) 0xDA; f[3] = (byte) 0xAD; f[4] = 1;
        f[5] = (byte) len; f[6] = (byte) (len >> 8);
        f[7] = (byte) cmd; f[8] = (byte) (cmd >> 8);
        f[9] = (byte) v3seq; f[10] = (byte) (v3seq >> 8);
        v3seq = (v3seq % 0xFFFF) + 1;
        System.arraycopy(payload, 0, f, 11, payload.length);
        int crc = crc16(f, len);
        f[len] = (byte) crc; f[len + 1] = (byte) (crc >> 8);
        List<byte[]> out = new ArrayList<>();
        int chunk = maxPacket - 1;
        for (int o = 0; o < f.length; o += chunk) {
            int n = Math.min(chunk, f.length - o);
            byte[] p = new byte[n + 1];
            p[0] = 0x33;
            System.arraycopy(f, o, p, 1, n);
            out.add(p);
        }
        return out;
    }

    public static byte[] unhex(String s) {
        s = s.replaceAll("[^0-9a-fA-F]", "");
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        return b;
    }

    // ---- Heart rate, v3 health sync (cmd 0x04) ----------------------------------
    // Request: [op 0=start][type 3=heart rate][flag 1][u16 offset (items to skip)].
    // Reply payload (sent WITHOUT a CRC, one packet): echo(5), u16 item count, u16 head len (31),
    //   u16 data bytes, ..., head{u16 year, u8 month, u8 day, u32 start_time (seconds after midnight), ...},
    //   then items of (u8 seconds since previous item, u8 bpm). bpm 0 = filler (e.g. ff 00 = 255 s gap).
    // Worked out and checked against a real reading on 2026-11-20.
    public static final int V3_HEALTH = 0x04, HEALTH_TYPE_HR = 3;

    public static final int HEALTH_TYPE_SPO2 = 1, HEALTH_TYPE_STRESS = 2, HEALTH_TYPE_SLEEP = 7;

    public static byte[] heartRateRequest(int offset) { return healthRequest(HEALTH_TYPE_HR, offset); }

    public static byte[] healthRequest(int type, int offset) {
        return new byte[]{0, (byte) type, 1, (byte) offset, (byte) (offset >> 8)};
    }

    /**
     * Generic page for types 1 (SpO2), 2 (stress) and 3 (heart rate): head starts with the date and a u32 start
     * time (seconds after midnight for heart rate, minutes for SpO2/stress), then (u8 delta, u8 value) items.
     * Stress verified 2026-11-20 (30..51, start 18:42); SpO2 assumed to match (no data yet).
     * Returned sample times are always in seconds after midnight.
     */
    public static HeartPage parseSeries(byte[] p, int type) {
        if (p.length < 11 || (p[1] & 0xff) != type) return null;
        HeartPage hp = new HeartPage();
        hp.items = (p[5] & 0xff) | (p[6] & 0xff) << 8;
        int headLen = (p[7] & 0xff) | (p[8] & 0xff) << 8;
        int dataLen = (p[9] & 0xff) | (p[10] & 0xff) << 8;
        hp.pageItems = dataLen / 2;
        int dataStart = p.length - dataLen, head = dataStart - headLen;
        if (dataLen == 0 || head < 11) return hp;
        long t = (p[head + 4] & 0xffL) | (p[head + 5] & 0xffL) << 8 | (p[head + 6] & 0xffL) << 16 | (p[head + 7] & 0xffL) << 24;
        int unit = type == HEALTH_TYPE_HR ? 1 : 60;
        t *= unit;
        for (int i = dataStart; i + 1 < p.length; i += 2) {
            t += (long) (p[i] & 0xff) * unit;
            int v = p[i + 1] & 0xff;
            if (v > 0) hp.samples.add(new int[]{(int) t, v});
        }
        return hp;
    }

    /**
     * Sleep (type 7): head (40 bytes) starts with the date; u16 total minutes at head+6 and u16 stage count at
     * head+22 (from protocol_v3_health_sleep_data_cb_handle); items are (u8 stage, u8 minutes).
     * Not yet checked against a real night: only the total is shown. Returns total minutes, or -1.
     */
    public static int parseSleepTotal(byte[] p) {
        if (p.length < 11 || (p[1] & 0xff) != HEALTH_TYPE_SLEEP) return -1;
        int headLen = (p[7] & 0xff) | (p[8] & 0xff) << 8;
        int dataLen = (p[9] & 0xff) | (p[10] & 0xff) << 8;
        if (dataLen == 0) return -1;
        int head = p.length - dataLen - headLen;
        if (head < 11) return -1;
        int total = (p[head + 6] & 0xff) | (p[head + 7] & 0xff) << 8;
        if (total == 0) {   // fall back to adding up the stages
            for (int i = p.length - dataLen; i + 1 < p.length; i += 2) total += p[i + 1] & 0xff;
        }
        return total;
    }

    /** Parsed heart-rate page: samples as {secondsAfterMidnight, bpm}. */
    public static final class HeartPage {
        public int items, pageItems; public List<int[]> samples = new ArrayList<>();
    }

    public static HeartPage parseHeartRate(byte[] p) {
        if (p.length < 11 || p[1] != HEALTH_TYPE_HR) return null;
        HeartPage hp = new HeartPage();
        hp.items = (p[5] & 0xff) | (p[6] & 0xff) << 8;
        int headLen = (p[7] & 0xff) | (p[8] & 0xff) << 8;
        int dataLen = (p[9] & 0xff) | (p[10] & 0xff) << 8;
        hp.pageItems = dataLen / 2;
        int dataStart = p.length - dataLen, head = dataStart - headLen;
        if (head < 11 || dataLen < 0) return hp;
        long t = (p[head + 4] & 0xffL) | (p[head + 5] & 0xffL) << 8 | (p[head + 6] & 0xffL) << 16 | (p[head + 7] & 0xffL) << 24;
        for (int i = dataStart; i + 1 < p.length; i += 2) {
            t += p[i] & 0xff;
            int bpm = p[i + 1] & 0xff;
            if (bpm > 0) hp.samples.add(new int[]{(int) t, bpm});
        }
        return hp;
    }

    // ---- Music info, v3 (cmd 0x0A) -----------------------------------------------
    // 71-byte payload (from json_protocol_v3_cmd_handle): u8 status, u16 cur_time_second, u16 total_time_second,
    // u16 music_name length (max 64), music_name[64].
    public static final int V3_MUSIC_INFO = 0x0A;

    public static byte[] musicInfo(int status, int curSec, int totalSec, String name) {
        byte[] p = new byte[71];
        byte[] n = utf8(name, 64);
        p[0] = (byte) status;
        p[1] = (byte) curSec; p[2] = (byte) (curSec >> 8);
        p[3] = (byte) totalSec; p[4] = (byte) (totalSec >> 8);
        p[5] = (byte) n.length; p[6] = 0;
        System.arraycopy(n, 0, p, 7, n.length);
        return p;
    }

    /**
     * 03 44: all-day SpO2 monitoring (from protocol_set_2_json, key 0x44): on/off (AA/55), start h:m, end h:m,
     * low-SpO2 alert on/off (AA/55), alert threshold. Acknowledged by the SW2S 2026-11-20.
     */
    public static byte[] spo2AllDay(boolean on) {
        return packet(0x03, 0x44, new byte[]{(byte) (on ? 0xAA : 0x55), 0, 0, 23, 59, 0x55, 90});
    }

    /** 03 53: run the vibration motor (key 0x53 in protocol_write_vbus_control): on/off + u16 milliseconds. */
    public static byte[] vibrate(int ms) {
        return packet(0x03, 0x53, new byte[]{(byte) (ms > 0 ? 1 : 0), (byte) ms, (byte) (ms >> 8)});
    }

    /** 03 2D AA: weather feature on (old SDK). */
    public static byte[] weatherSwitch(boolean on) { return packet(0x03, 0x2D, new byte[]{(byte) (on ? 0xAA : 0x55)}); }

    /** 05 02 01: call answered/ended, stop ringing on the watch. */
    public static byte[] callEnded() { return packet(0x05, 0x02, new byte[]{1}); }

    public static final int EVT_CALL_ANSWER = 0x0C, EVT_CALL_REJECT = 0x0D;

    public static String hex(byte[] d) {
        StringBuilder sb = new StringBuilder();
        for (byte x : d) sb.append(String.format("%02x ", x));
        return sb.toString().trim();
    }

    // ---- Watch settings (layouts from the DWARF structs in libprotocol_c.so, see PROTOCOL_FULL.md) ----
    // Days are a Mon..Sun mask (bit0 = Monday) and are sent in bits 1..7, like the alarms.
    static int daysField(int monToSun) { return (monToSun & 0x7f) << 1; }
    static int daysFromField(int v) { return (v >> 1) & 0x7f; }

    /** 03 28 raise to wake (protocol_set_up_hand_gesture). AA/55; read back with 02 B1. Verified 2026-11-21. */
    public static byte[] raiseToWake(boolean on, int showSec, boolean ranged, int sh, int sm, int eh, int em) {
        return packet(0x03, 0x28, new byte[]{(byte) (on ? 0xAA : 0x55), (byte) showSec, (byte) (ranged ? 1 : 0),
                (byte) sh, (byte) sm, (byte) eh, (byte) em});
    }
    public static byte[] getRaiseToWake() { return packet(0x02, 0xB1, null); }

    /** 03 29 do not disturb (protocol_do_not_disturb). AA/55; read back with 02 30. Verified 2026-11-21. */
    public static byte[] doNotDisturb(boolean on, int sh, int sm, int eh, int em) {
        return packet(0x03, 0x29, new byte[]{(byte) (on ? 0xAA : 0x55), (byte) sh, (byte) sm, (byte) eh, (byte) em,
                0, 0, 0x55, 0, 0, 0, 0, 0x55, 0x55});
    }
    public static byte[] getDoNotDisturb() { return packet(0x02, 0x30, null); }

    /** 03 47 walk reminder (protocol_set_walk_reminder). on = 1/0; read back with 02 47. Verified 2026-11-21. */
    public static byte[] walkReminder(boolean on, int steps, int sh, int sm, int eh, int em, int days) {
        return packet(0x03, 0x47, new byte[]{(byte) (on ? 1 : 0), (byte) steps, (byte) (steps >> 8),
                (byte) sh, (byte) sm, (byte) eh, (byte) em, (byte) daysField(days)});
    }
    public static byte[] getWalkReminder() { return packet(0x02, 0x47, null); }

    /** 03 20 sedentary reminder (protocol_long_sit): times, u16 interval (min), u16 repeat (bit0 on + days). Unverified. */
    public static byte[] sedentary(boolean on, int sh, int sm, int eh, int em, int intervalMin, int days) {
        int rep = (on ? 1 : 0) | daysField(days);
        return packet(0x03, 0x20, new byte[]{(byte) sh, (byte) sm, (byte) eh, (byte) em,
                (byte) intervalMin, (byte) (intervalMin >> 8), (byte) rep, 0});
    }

    /** 03 60 drink-water reminder (protocol_drink_water_reminder). Unverified: on = 1/0 like the walk reminder. */
    public static byte[] drinkWater(boolean on, int sh, int sm, int eh, int em, int days, int intervalMin) {
        return packet(0x03, 0x60, new byte[]{(byte) (on ? 1 : 0), (byte) sh, (byte) sm, (byte) eh, (byte) em,
                (byte) daysField(days), (byte) intervalMin, (byte) (intervalMin >> 8)});
    }

    /** 03 55 hand-washing reminder (protocol_set_hand_washing_reminder). Unverified: on = 1/0. */
    public static byte[] handWashing(boolean on, int sh, int sm, int eh, int em, int days, int intervalMin) {
        return packet(0x03, 0x55, new byte[]{(byte) (on ? 1 : 0), (byte) sh, (byte) sm, (byte) eh, (byte) em,
                (byte) daysField(days), (byte) intervalMin, (byte) (intervalMin >> 8)});
    }

    /** 03 03 daily step goal (protocol_set_sport_goal, type 0 = steps) + sleep goal. Unverified (no read command). */
    public static byte[] stepGoal(int steps, int sleepH, int sleepM) {
        return packet(0x03, 0x03, new byte[]{0, (byte) steps, (byte) (steps >> 8), (byte) (steps >> 16), (byte) (steps >> 24),
                (byte) sleepH, (byte) sleepM});
    }

    /** 03 43 calorie (kcal) + distance (m) goals; the other fields are sent back as the watch reported them. */
    public static byte[] calorieDistanceGoal(int kcal, int meters, byte[] lastReply) {
        byte[] p = new byte[18];
        if (lastReply != null && lastReply.length > 2) System.arraycopy(lastReply, 2, p, 0, Math.min(18, lastReply.length - 2));
        p[0] = (byte) kcal; p[1] = (byte) (kcal >> 8); p[2] = (byte) (kcal >> 16); p[3] = (byte) (kcal >> 24);
        p[4] = (byte) meters; p[5] = (byte) (meters >> 8); p[6] = (byte) (meters >> 16); p[7] = (byte) (meters >> 24);
        return packet(0x03, 0x43, p);
    }
    public static byte[] getCalorieDistanceGoal() { return packet(0x02, 0x43, null); }

    /** 03 11 units (protocol_set_unit): 1 = metric/°C/24 h, 2 = imperial/°F/12 h; language 2 = English; stride cm. */
    public static byte[] units(boolean miles, boolean pounds, boolean fahrenheit, boolean h12, int strideCm) {
        return packet(0x03, 0x11, new byte[]{(byte) (miles ? 2 : 1), (byte) (pounds ? 2 : 1), (byte) (fahrenheit ? 2 : 1),
                (byte) strideCm, 2, (byte) (h12 ? 2 : 1)});
    }

    /** 03 10 profile (protocol_set_user_info): height cm, u16 weight kg x100, gender (1 male / 2 female), birthday. */
    public static byte[] userInfo(int heightCm, int weightKg, int gender, int year, int month, int day) {
        int w = weightKg * 100;
        return packet(0x03, 0x10, new byte[]{(byte) heightCm, (byte) w, (byte) (w >> 8), (byte) gender,
                (byte) year, (byte) (year >> 8), (byte) month, (byte) day});
    }

    /** F0 01 reboot, F0 04 factory reset (erases the watch). */
    public static byte[] reboot() { return packet(0xF0, 0x01, null); }
    public static byte[] factoryReset() { return packet(0xF0, 0x04, null); }

    /**
     * Notification icon types. Order = the bits of the watch's function table (02 02 / 02 07), which is also IDO's
     * numbering: 1 SMS, 2 email, 3 QQ, 4 WeChat, 5 Weibo, 6 Facebook, 7 Twitter, 8 WhatsApp, 9 Messenger,
     * 10 Instagram, 11 LinkedIn, 12 calendar, 13 Skype, 15 other, 16 VK, 17 Line, 18 Viber, 19 KakaoTalk,
     * 20 Gmail, 21 Outlook, 22 Snapchat, 23 Telegram, 24 Chatwork, 25 Slack, 26 Yahoo Mail, 27 Tumblr,
     * 28 YouTube, 29 Pinterest, 32 TikTok. WhatsApp (8) verified on the SW2S; the rest follow the same numbering.
     */
    public static int appType(String pkg) {
        if (pkg == null) return 1;
        switch (pkg) {
            case "com.google.android.apps.messaging": case "com.android.mms": case "com.samsung.android.messaging":
            case "com.coloros.mms": case "com.oplus.mms": return 1;
            case "com.google.android.gm": return 20;
            case "com.microsoft.office.outlook": return 21;
            case "com.tencent.mobileqq": return 3;
            case "com.tencent.mm": return 4;
            case "com.sina.weibo": return 5;
            case "com.facebook.katana": case "com.facebook.lite": return 6;
            case "com.twitter.android": return 7;
            case "com.whatsapp": case "com.whatsapp.w4b": return 8;
            case "com.facebook.orca": case "com.facebook.mlite": return 9;
            case "com.instagram.android": return 10;
            case "com.linkedin.android": return 11;
            case "com.google.android.calendar": case "com.samsung.android.calendar": case "com.coloros.calendar": return 12;
            case "com.skype.raider": case "com.skype.insiders": return 13;
            case "com.vkontakte.android": return 16;
            case "jp.naver.line.android": return 17;
            case "com.viber.voip": return 18;
            case "com.kakao.talk": return 19;
            case "com.snapchat.android": return 22;
            case "org.telegram.messenger": case "org.thunderdog.challegram": case "org.telegram.plus": return 23;
            case "jp.ecstudio.chatworkandroid": return 24;
            case "com.Slack": return 25;
            case "com.yahoo.mobile.client.android.mail": return 26;
            case "com.tumblr": return 27;
            case "com.google.android.youtube": return 28;
            case "com.pinterest": return 29;
            case "com.zhiliaoapp.musically": case "com.ss.android.ugc.trill": return 32;
            default: return 1;
        }
    }

    // ---- Watch faces (v3) ----
    public static final int V3_FACE_LIST = 0x06, V3_FACE_SET = 0x08;

    /** Make an installed face current: name (e.g. "w331.iwf") zero-padded to 31 bytes. Reply 00 = ok. */
    public static byte[] faceSet(String name) {
        byte[] out = new byte[31];
        byte[] n = name.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(n, 0, out, 0, Math.min(n.length, 30));
        return out;
    }

    /** Face list reply: a short header, then entries of 01, u32 length, ASCII name. */
    public static java.util.List<String> parseFaceList(byte[] p) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (int i = 0; i + 5 < p.length; i++) {
            if (p[i] != 1 || p[i + 2] != 0 || p[i + 3] != 0 || p[i + 4] != 0) continue;
            int n = p[i + 1] & 0xff;
            if (n < 3 || n > 30 || i + 5 + n > p.length) continue;
            String s = new String(p, i + 5, n, java.nio.charset.StandardCharsets.US_ASCII);
            if (!s.matches("[\\w.-]+")) continue;
            out.add(s);
            i += 4 + n;
        }
        return out;
    }
}
