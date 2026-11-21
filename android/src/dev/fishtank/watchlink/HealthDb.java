package dev.fishtank.watchlink;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Everything the watch reports, kept on the phone so it can be looked at later (History screen, CSV export).
 * Stored in the app's private health.db; writes run on one background thread.
 *
 *   sample(kind, t, v)  one reading: kind = SPO2/STRESS/HR (the watch's health type numbers), STEPS (running
 *                       total for the day) or BATTERY; t = unix seconds; one row per kind and second.
 *   day(day, ...)       daily totals, day = "yyyy-MM-dd" in local time.
 *   raw(day, kind, hex) the last non-empty reply per day and type, for formats that aren't decoded yet (sleep stages).
 */
final class HealthDb extends SQLiteOpenHelper {
    static final int SPO2 = 1, STRESS = 2, HR = 3, SLEEP = 7, STEPS = 101, BATTERY = 102;

    private static HealthDb instance;
    private static final ExecutorService io = Executors.newSingleThreadExecutor();

    static synchronized HealthDb get(Context c) {
        if (instance == null) instance = new HealthDb(c.getApplicationContext());
        return instance;
    }

    private HealthDb(Context c) { super(c, "health.db", null, 1); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE sample (kind INTEGER NOT NULL, t INTEGER NOT NULL, v INTEGER NOT NULL,"
                + " PRIMARY KEY (kind, t)) WITHOUT ROWID");
        db.execSQL("CREATE TABLE day (day TEXT PRIMARY KEY, steps INTEGER, kcal INTEGER, meters INTEGER,"
                + " active INTEGER, sleep INTEGER, updated INTEGER)");
        db.execSQL("CREATE TABLE raw (day TEXT NOT NULL, kind INTEGER NOT NULL, hex TEXT NOT NULL,"
                + " saved INTEGER NOT NULL, PRIMARY KEY (day, kind)) WITHOUT ROWID");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int from, int to) {}

    /** Run work on the database thread. */
    static void run(Runnable r) { io.execute(r); }

    // ---- dates ----

    static String day(int y, int m, int d) { return String.format(Locale.ROOT, "%04d-%02d-%02d", y, m, d); }

    static String day(Calendar c) {
        return day(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    static String today() { return day(Calendar.getInstance()); }

    /** Local midnight of a "yyyy-MM-dd" day, in unix seconds. */
    static long midnight(String day) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(Integer.parseInt(day.substring(0, 4)), Integer.parseInt(day.substring(5, 7)) - 1,
                Integer.parseInt(day.substring(8, 10)));
        return c.getTimeInMillis() / 1000;
    }

    static String shift(String day, int days) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(midnight(day) * 1000);
        c.add(Calendar.DAY_OF_MONTH, days);
        return day(c);
    }

    // ---- writes (call from the database thread, see run()) ----

    /** Readings for one day: samples are {seconds after local midnight, value}. */
    void addSamples(int kind, String day, List<int[]> samples) {
        if (samples.isEmpty()) return;
        long base = midnight(day);
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (int[] s : samples)
                db.execSQL("INSERT OR REPLACE INTO sample (kind, t, v) VALUES (?, ?, ?)",
                        new Object[]{kind, base + s[0], s[1]});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** One reading at unix time t. */
    void addSample(int kind, long t, int v) {
        getWritableDatabase().execSQL("INSERT OR REPLACE INTO sample (kind, t, v) VALUES (?, ?, ?)",
                new Object[]{kind, t, v});
    }

    /** Set columns of a day's totals (steps, kcal, meters, active, sleep). */
    void setDay(String day, ContentValues cv) {
        SQLiteDatabase db = getWritableDatabase();
        cv.put("updated", System.currentTimeMillis() / 1000);
        db.execSQL("INSERT OR IGNORE INTO day (day) VALUES (?)", new Object[]{day});
        db.update("day", cv, "day = ?", new String[]{day});
    }

    void saveRaw(String day, int kind, String hex) {
        getWritableDatabase().execSQL("INSERT OR REPLACE INTO raw (day, kind, hex, saved) VALUES (?, ?, ?, ?)",
                new Object[]{day, kind, hex, System.currentTimeMillis() / 1000});
    }

    // ---- reads ----

    /** Readings of one kind between two unix times: {t, v} sorted by time. */
    List<long[]> samples(int kind, long from, long to) {
        List<long[]> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT t, v FROM sample WHERE kind = ? AND t >= ? AND t < ? ORDER BY t",
                new String[]{String.valueOf(kind), String.valueOf(from), String.valueOf(to)})) {
            while (c.moveToNext()) out.add(new long[]{c.getLong(0), c.getLong(1)});
        }
        return out;
    }

    /** A day's totals, or null: {steps, kcal, meters, active, sleep}; -1 where unknown. */
    long[] dayTotals(String day) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT steps, kcal, meters, active, sleep FROM day WHERE day = ?", new String[]{day})) {
            if (!c.moveToFirst()) return null;
            long[] r = new long[5];
            for (int i = 0; i < 5; i++) r[i] = c.isNull(i) ? -1 : c.getLong(i);
            return r;
        }
    }

    /** Oldest day with any data, or null. */
    String firstDay() {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT MIN(d) FROM (SELECT MIN(day) AS d FROM day UNION ALL SELECT MIN(day) FROM raw)", null)) {
            String d = c.moveToFirst() && !c.isNull(0) ? c.getString(0) : null;
            try (Cursor s = getReadableDatabase().rawQuery("SELECT MIN(t) FROM sample", null)) {
                if (s.moveToFirst() && !s.isNull(0)) {
                    Calendar cal = Calendar.getInstance();
                    cal.setTimeInMillis(s.getLong(0) * 1000);
                    String sd = day(cal);
                    if (d == null || sd.compareTo(d) < 0) d = sd;
                }
            }
            return d;
        }
    }

    int count(int kind) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM sample WHERE kind = ?",
                new String[]{String.valueOf(kind)})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    /** Everything as CSV: one section of readings, one of daily totals. */
    String csv() {
        StringBuilder sb = new StringBuilder("type,time,value\n");
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
        try (Cursor c = getReadableDatabase().rawQuery("SELECT kind, t, v FROM sample ORDER BY kind, t", null)) {
            while (c.moveToNext())
                sb.append(kindName(c.getInt(0))).append(',').append(f.format(new java.util.Date(c.getLong(1) * 1000)))
                        .append(',').append(c.getLong(2)).append('\n');
        }
        sb.append("\nday,steps,kcal,meters,active_time,sleep_minutes\n");
        try (Cursor c = getReadableDatabase().rawQuery("SELECT day, steps, kcal, meters, active, sleep FROM day ORDER BY day", null)) {
            while (c.moveToNext()) {
                sb.append(c.getString(0));
                for (int i = 1; i < 6; i++) sb.append(',').append(c.isNull(i) ? "" : String.valueOf(c.getLong(i)));
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    static String kindName(int kind) {
        switch (kind) {
            case SPO2: return "spo2_percent";
            case STRESS: return "stress";
            case HR: return "heart_rate_bpm";
            case STEPS: return "steps_today";
            case BATTERY: return "watch_battery_percent";
            default: return "type" + kind;
        }
    }

    static void log(String s) { Log.i(WatchService.TAG, "db " + s); }
}
