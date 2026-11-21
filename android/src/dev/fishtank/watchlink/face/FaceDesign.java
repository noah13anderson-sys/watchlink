package dev.fishtank.watchlink.face;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * A custom watch face for the 240x240 SW2S, turned into an IDO .iwf file with the same layout format as the
 * cloud faces in the Momax app:
 *   "custom" widgets (time, date, week, step, calorie, heartrate, battery) draw numbers from small digit images,
 *   a "watch" widget rotates hour/minute/second hand images around a point on the screen.
 * Small labels ("steps", ♥, "kcal") are drawn into the background next to the numbers.
 */
public final class FaceDesign {
    public static final int SIZE = 240;

    public static final String[] STYLES = {"Digital", "Analog"};
    public static final String[] BACKGROUNDS = {"Teal", "Sunset", "Night", "Forest", "Berry", "Black", "Photo"};
    private static final int[][] GRADIENTS = {
            {0xFF00A896, 0xFF02344F}, {0xFFFF7E5F, 0xFF4B1248}, {0xFF1E2233, 0xFF05060B},
            {0xFF3A7D44, 0xFF0B2414}, {0xFFB0306A, 0xFF2A0A3A}, {0xFF000000, 0xFF000000}};

    public static final String[] FONTS = {"Bold", "Light", "Thin", "Black", "Serif", "Mono", "Condensed", "Rounded", "Script"};
    public static final int[] COLORS = {0xFFFFFFFF, 0xFF00D3B8, 0xFFFFD166, 0xFFFF6A00, 0xFFED254E, 0xFF71F79F, 0xFF8AB4F8};
    public static final String[] POSITIONS = {"Middle", "Upper", "Lower"};

    public int style, background, font, timeColor = 0xFFFFFFFF, accentColor = 0xFF00D3B8, position, timeSize = 64;
    public boolean date = true, weekday = true, steps = true, battery = true, heart = false, calories = false;
    /** Optional photo background, already cropped to 240x240. */
    public Bitmap photo;
    public int photoDim = 90;

    private static final String[] DAYS = {"mon", "tue", "wed", "thur", "fri", "sat", "sun"};
    private static final int ROW_H = 26, GAP = 6;

    private Typeface typeface() {
        switch (font) {
            case 1: return Typeface.create("sans-serif-light", Typeface.NORMAL);
            case 2: return Typeface.create("sans-serif-thin", Typeface.NORMAL);
            case 3: return Typeface.create("sans-serif-black", Typeface.NORMAL);
            case 4: return Typeface.create(Typeface.SERIF, Typeface.BOLD);
            case 5: return Typeface.create(Typeface.MONOSPACE, Typeface.BOLD);
            case 6: return Typeface.create("sans-serif-condensed", Typeface.BOLD);
            case 7: return Typeface.create("casual", Typeface.BOLD);
            case 8: return Typeface.create("cursive", Typeface.BOLD);
            default: return Typeface.create("sans-serif", Typeface.BOLD);
        }
    }

    private Bitmap baseBackground() {
        Bitmap b = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        if (background == BACKGROUNDS.length - 1 && photo != null) {
            c.drawBitmap(photo, null, new Rect(0, 0, SIZE, SIZE), new Paint(Paint.FILTER_BITMAP_FLAG));
            Paint dim = new Paint();
            dim.setShader(new RadialGradient(SIZE / 2f, SIZE / 2f, SIZE * 0.75f,
                    Color.argb(photoDim, 0, 0, 0), Color.argb(Math.min(255, photoDim + 60), 0, 0, 0), Shader.TileMode.CLAMP));
            c.drawRect(0, 0, SIZE, SIZE, dim);
        } else {
            int[] g = GRADIENTS[Math.min(background, GRADIENTS.length - 1)];
            Paint p = new Paint();
            p.setShader(new LinearGradient(0, 0, SIZE, SIZE, g[0], g[1], Shader.TileMode.CLAMP));
            c.drawRect(0, 0, SIZE, SIZE, p);
        }
        return b;
    }

    private static Bitmap glyph(String s, Paint p, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Paint.FontMetrics fm = p.getFontMetrics();
        new Canvas(b).drawText(s, w / 2f, h / 2f - (fm.ascent + fm.descent) / 2f, p);
        return b;
    }

    private static Paint paint(Typeface tf, float size, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(tf);
        p.setTextSize(size);
        p.setColor(color);
        p.setTextAlign(Paint.Align.CENTER);
        return p;
    }

    private static int digitWidth(Paint p) {
        float w = 0;
        for (char ch = '0'; ch <= '9'; ch++) w = Math.max(w, p.measureText(String.valueOf(ch)));
        return (int) Math.ceil(w) + 2;
    }

    /** A hand pointing up: image w x h, rotating around (w/2, pivot). */
    private static Bitmap hand(int w, int h, int pivot, int color, boolean thin) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        c.drawRoundRect(new RectF(thin ? w / 2f - 1 : 0, 0, thin ? w / 2f + 1 : w, h), w / 2f, w / 2f, p);
        p.setColor(0x55000000);
        c.drawCircle(w / 2f, pivot, Math.min(w / 2f, 3), p);
        return b;
    }

    private final class Parts {
        Bitmap bg;
        Bitmap[] time = new Bitmap[11], small = new Bitmap[11], batt = new Bitmap[11];
        Bitmap[] days = new Bitmap[7];
        Bitmap hourHand, minHand, secHand;
        int timeH, sw;
        JSONArray items = new JSONArray();
        /** Preview text per widget index. */
        List<String> sample = new ArrayList<>();
    }

    private static final int HOUR_W = 10, HOUR_H = 66, HOUR_PIV = 54;
    private static final int MIN_W = 8, MIN_H = 98, MIN_PIV = 84;
    private static final int SEC_W = 4, SEC_H = 110, SEC_PIV = 90;

    private Parts parts() throws Exception {
        Parts q = new Parts();
        q.bg = baseBackground();
        Canvas bgc = new Canvas(q.bg);
        Typeface tf = typeface();
        Paint tp = paint(tf, timeSize, timeColor);
        int dw = digitWidth(tp);
        q.timeH = (int) Math.ceil(timeSize * 1.1f);
        int colonW = (int) Math.ceil(tp.measureText(":")) + 4;
        for (int i = 0; i < 10; i++) q.time[i] = glyph(String.valueOf(i), tp, dw, q.timeH);
        q.time[10] = glyph(":", tp, colonW, q.timeH);
        int timeW = 4 * dw + colonW;

        Paint sp = paint(Typeface.create("sans-serif-medium", Typeface.NORMAL), 20, accentColor);
        q.sw = digitWidth(sp);
        for (int i = 0; i < 10; i++) { q.small[i] = glyph(String.valueOf(i), sp, q.sw, ROW_H); q.batt[i] = q.small[i]; }
        q.small[10] = glyph("/", sp, (int) Math.ceil(sp.measureText("/")) + 2, ROW_H);
        q.batt[10] = glyph("%", sp, (int) Math.ceil(sp.measureText("%")) + 2, ROW_H);
        Paint wp = paint(Typeface.create("sans-serif-medium", Typeface.BOLD), 18, timeColor);
        for (int i = 0; i < 7; i++) q.days[i] = glyph(DAYS[i].substring(0, 3).toUpperCase(), wp, 50, ROW_H);
        Paint lp = paint(Typeface.create("sans-serif", Typeface.NORMAL), 14, (timeColor & 0x00FFFFFF) | 0xB0000000);
        lp.setTextAlign(Paint.Align.LEFT);

        if (style == 1) {   // analog: ticks on the background, hands as images
            Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
            tick.setStrokeCap(Paint.Cap.ROUND);
            for (int i = 0; i < 60; i++) {
                boolean hour = i % 5 == 0;
                tick.setColor(hour ? timeColor : (timeColor & 0x00FFFFFF) | 0x66000000);
                tick.setStrokeWidth(hour ? 4 : 1.5f);
                double a = Math.PI * 2 * i / 60;
                float r1 = hour ? 102 : 108, r2 = 115;
                bgc.drawLine(120 + (float) (Math.sin(a) * r1), 120 - (float) (Math.cos(a) * r1),
                        120 + (float) (Math.sin(a) * r2), 120 - (float) (Math.cos(a) * r2), tick);
            }
            q.hourHand = hand(HOUR_W, HOUR_H, HOUR_PIV, timeColor, false);
            q.minHand = hand(MIN_W, MIN_H, MIN_PIV, timeColor, false);
            q.secHand = hand(SEC_W, SEC_H, SEC_PIV, accentColor, true);
        }

        // Rows from top to bottom; the stack is centred on the screen.
        List<String> rows = new ArrayList<>();
        boolean dateRow = date || weekday, hcRow = heart || calories;
        if (style == 1) {
            if (steps) rows.add("steps");
            rows.add("gap");
            if (dateRow) rows.add("date");
            if (hcRow) rows.add("hc");
            if (battery) rows.add("battery");
        } else if (position == 1) {
            rows.add("time"); if (dateRow) rows.add("date"); if (hcRow) rows.add("hc");
            if (steps) rows.add("steps"); if (battery) rows.add("battery");
        } else if (position == 2) {
            if (steps) rows.add("steps"); if (hcRow) rows.add("hc"); if (dateRow) rows.add("date");
            rows.add("time"); if (battery) rows.add("battery");
        } else {
            if (steps) rows.add("steps"); rows.add("time"); if (dateRow) rows.add("date");
            if (hcRow) rows.add("hc"); if (battery) rows.add("battery");
        }
        int total = 0;
        for (String r : rows) total += (r.equals("time") ? q.timeH : r.equals("gap") ? 64 : ROW_H) + GAP;
        total -= GAP;
        int y = Math.max(22, (SIZE - total) / 2);

        for (String r : rows) {
            switch (r) {
                case "time":
                    add(q, item("time", (SIZE - timeW) / 2, y, timeW, q.timeH, "g1", 11, null), "10:08");
                    y += q.timeH;
                    break;
                case "date": {
                    int dateW = 5 * q.sw + 4;
                    boolean both = date && weekday;
                    if (date) add(q, item("date", both ? SIZE / 2 - dateW - 4 : (SIZE - dateW) / 2, y, dateW, ROW_H, "g2", 11, 0), "11/21");
                    if (weekday) add(q, item("week", both ? SIZE / 2 + 8 : (SIZE - 50) / 2, y, 50, ROW_H, "week", 7, 0), "FRI");
                    y += ROW_H;
                    break;
                }
                case "hc": {
                    boolean both = heart && calories;
                    if (heart) labelled(q, bgc, lp, "♥", "heartrate", 3, both ? 60 : 120, y, "72");
                    if (calories) labelled(q, bgc, lp, "kcal", "calorie", 4, both ? 180 : 120, y, "320");
                    y += ROW_H;
                    break;
                }
                case "steps":
                    labelled(q, bgc, lp, "steps", "step", 5, 120, y, "5367");
                    y += ROW_H;
                    break;
                case "battery":
                    add(q, item("battery", (SIZE - 4 * q.sw) / 2, y, 4 * q.sw, ROW_H, "g3", 11, null), "80%");
                    y += ROW_H;
                    break;
                default:
                    y += 64;
            }
            y += GAP;
        }

        if (style == 1) {
            JSONObject w = new JSONObject();
            w.put("widget", "watch"); w.put("type", "time");
            w.put("x", 0); w.put("y", 0); w.put("w", SIZE); w.put("h", SIZE);
            w.put("fgcolor", "0xFFFFFFFF"); w.put("stepless_rotation", 0);
            w.put("second", "files3.png"); w.put("seccenterx", SEC_W / 2); w.put("seccentery", SEC_PIV);
            w.put("secanchorx", 120); w.put("secanchory", 120);
            w.put("minute", "files2.png"); w.put("mincenterx", MIN_W / 2); w.put("mincentery", MIN_PIV);
            w.put("minanchorx", 120); w.put("minanchory", 120);
            w.put("hour", "files1.png"); w.put("hourcenterx", HOUR_W / 2); w.put("hourcentery", HOUR_PIV);
            w.put("houranchorx", 120); w.put("houranchory", 120);
            add(q, w, "");
        }
        return q;
    }

    private static void add(Parts q, JSONObject o, String sample) { q.items.put(o); q.sample.add(sample); }

    /** A number with a small label drawn into the background on its left, centred around cx. */
    private void labelled(Parts q, Canvas bgc, Paint lp, String label, String type, int digits, int cx, int y,
                          String sample) throws Exception {
        float lw = lp.measureText(label) + 4;
        int nw = digits * q.sw;
        float x0 = cx - (lw + nw) / 2f;
        Paint.FontMetrics fm = lp.getFontMetrics();
        bgc.drawText(label, x0, y + ROW_H / 2f - (fm.ascent + fm.descent) / 2f, lp);
        add(q, item(type, Math.round(x0 + lw), y, nw, ROW_H, "g2", 10, null), sample);
    }

    private static JSONObject item(String type, int x, int y, int w, int h, String font, int num, Integer style)
            throws Exception {
        JSONObject o = new JSONObject();
        o.put("widget", "custom"); o.put("type", type);
        o.put("x", x); o.put("y", y); o.put("w", w); o.put("h", h);
        o.put("fgcolor", "0xFFFFFFFF"); o.put("fgrender", "0x0"); o.put("align", "center");
        if (style != null) o.put("style", style);
        o.put("font", font); o.put("fontnum", num);
        return o;
    }

    /** What the watch will show (10:08, Fri 11/21, 5367 steps, 72 bpm, 320 kcal, 80%). */
    public Bitmap preview() {
        try {
            Parts q = parts();
            Bitmap b = q.bg.copy(Bitmap.Config.ARGB_8888, true);
            Canvas c = new Canvas(b);
            for (int i = 0; i < q.items.length(); i++) {
                JSONObject o = q.items.getJSONObject(i);
                String t = o.getString("type");
                if (o.getString("widget").equals("watch")) {
                    drawHand(c, q.hourHand, HOUR_PIV, (10 + 8 / 60f) * 30);
                    drawHand(c, q.minHand, MIN_PIV, 8 * 6);
                    drawHand(c, q.secHand, SEC_PIV, 32 * 6);
                    continue;
                }
                if (t.equals("week")) { c.drawBitmap(q.days[4], o.getInt("x"), o.getInt("y"), null); continue; }
                Bitmap[] set = t.equals("time") ? q.time : t.equals("battery") ? q.batt : q.small;
                String text = q.sample.get(i);
                int w = 0;
                for (char ch : text.toCharArray()) w += set[idx(ch)].getWidth();
                int x = o.getInt("x") + (o.getInt("w") - w) / 2;
                for (char ch : text.toCharArray()) {
                    Bitmap g = set[idx(ch)];
                    c.drawBitmap(g, x, o.getInt("y"), null);
                    x += g.getWidth();
                }
            }
            return b;
        } catch (Exception e) {
            return baseBackground();
        }
    }

    private static void drawHand(Canvas c, Bitmap hand, int pivot, float degrees) {
        c.save();
        c.rotate(degrees, 120, 120);
        c.drawBitmap(hand, 120 - hand.getWidth() / 2f, 120 - pivot, new Paint(Paint.FILTER_BITMAP_FLAG));
        c.restore();
    }

    private static int idx(char ch) { return Character.isDigit(ch) ? ch - '0' : 10; }

    private static byte[] raw(Bitmap b) {
        int[] px = new int[b.getWidth() * b.getHeight()];
        b.getPixels(px, 0, b.getWidth(), 0, 0, b.getWidth(), b.getHeight());
        return IwfFile.raw565(px, b.getWidth(), b.getHeight());
    }

    /** The uncompressed .iwf file. */
    public byte[] build(String name) throws Exception {
        Parts q = parts();
        JSONObject iwf = new JSONObject();
        iwf.put("version", 1); iwf.put("clouddialversion", 3); iwf.put("preview", "preview.png");
        iwf.put("name", name); iwf.put("author", "WatchLink"); iwf.put("description", "SW2S");
        iwf.put("deviceId", "SW2S"); iwf.put("bluetooth", false); iwf.put("disturb", false);
        iwf.put("battery", false); iwf.put("compress", "LZ4");
        iwf.put("item", q.items); iwf.put("bkground", "files0.png");
        JSONArray fl = new JSONArray();
        for (String n : new String[]{"g1", "g2", "g3", "week"}) {
            JSONObject f = new JSONObject();
            f.put("name", n); f.put("bpp", 16); f.put("format", "png");
            fl.put(f);
        }
        JSONObject fonts = new JSONObject();
        fonts.put("item", fl);

        IwfFile f = new IwfFile();
        f.addText("iwf.json", iwf.toString());
        f.addText("font.json", fonts.toString());
        f.add("files0.png", raw(q.bg));
        if (style == 1) {
            f.add("files1.png", raw(q.hourHand));
            f.add("files2.png", raw(q.minHand));
            f.add("files3.png", raw(q.secHand));
        }
        f.add("preview.png", raw(Bitmap.createScaledBitmap(preview(), 120, 120, true)));
        for (int i = 0; i < 11; i++) f.add("g1_" + i, raw(q.time[i]));
        for (int i = 0; i < 11; i++) f.add("g2_" + i, raw(q.small[i]));
        for (int i = 0; i < 11; i++) f.add("g3_" + i, raw(q.batt[i]));
        for (int i = 0; i < 7; i++) f.add("week_en_" + DAYS[i], raw(q.days[i]));
        return f.build();
    }
}
