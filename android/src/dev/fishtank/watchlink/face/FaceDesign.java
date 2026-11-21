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
    /** Gradients, then patterns drawn in the accent colour, then the photo (always last). */
    public static final String[] BACKGROUNDS = {
            "Teal", "Sunset", "Night", "Forest", "Berry", "Black", "Ocean", "Fire", "Lavender", "Mint", "Gold", "Steel",
            "Rose", "Aurora", "Rings", "Stripes", "Dots", "Carbon", "Rays", "Grid", "Photo"};
    public static final int PHOTO = BACKGROUNDS.length - 1;
    private static final int FIRST_PATTERN = 14;
    private static final int[][] GRADIENTS = {
            {0xFF00A896, 0xFF02344F}, {0xFFFF7E5F, 0xFF4B1248}, {0xFF1E2233, 0xFF05060B},
            {0xFF3A7D44, 0xFF0B2414}, {0xFFB0306A, 0xFF2A0A3A}, {0xFF000000, 0xFF000000},
            {0xFF2E86DE, 0xFF0A1A3F}, {0xFFFFB000, 0xFF8E1B10}, {0xFFB79CED, 0xFF3B2A6B}, {0xFF7BE0AD, 0xFF114B3A},
            {0xFFE6C068, 0xFF5A3D0C}, {0xFF8E9AAF, 0xFF1F2430}, {0xFFFFAFCC, 0xFF6B2D5C}, {0xFF00E0A0, 0xFF3A0CA3}};

    public static final String[] FONTS = {"Bold", "Light", "Thin", "Black", "Serif", "Mono", "Condensed", "Rounded",
            "Script", "Medium", "Small caps", "Mono serif", "Digital", "Dots"};
    private static final int FONT_DIGITAL = 12, FONT_DOTS = 13;
    public static final String[] EFFECTS = {"Plain", "Outline", "Shadow", "Glow"};
    public static final int[] COLORS = {0xFFFFFFFF, 0xFF00D3B8, 0xFFFFD166, 0xFFFF6A00, 0xFFED254E, 0xFF71F79F,
            0xFF8AB4F8, 0xFFC792EA, 0xFFFF8FAB, 0xFF5EEAD4, 0xFFFACC15, 0xFFB0B8C8, 0xFF60A5FA, 0xFF111111};
    public static final String[] POSITIONS = {"Middle", "Upper", "Lower", "Big", "Stacked"};
    private static final int POS_BIG = 3, POS_STACKED = 4;

    public int effect;
    public int style, background, font, timeColor = 0xFFFFFFFF, accentColor = 0xFF00D3B8, position, timeSize = 64;
    public boolean date = true, weekday = true, steps = true, battery = true, heart = false, calories = false,
            weather = false, distance = false, seconds = false, ampm = false;
    /** Distance label: "km" or "mi" (follows the watch's unit setting). */
    public boolean miles;
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
            case 9: return Typeface.create("sans-serif-medium", Typeface.NORMAL);
            case 10: return Typeface.create("sans-serif-smallcaps", Typeface.BOLD);
            case 11: return Typeface.create("serif-monospace", Typeface.BOLD);
            default: return Typeface.create("sans-serif", Typeface.BOLD);
        }
    }

    private Bitmap baseBackground() {
        Bitmap b = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        if (background == PHOTO && photo != null) {
            c.drawBitmap(photo, null, new Rect(0, 0, SIZE, SIZE), new Paint(Paint.FILTER_BITMAP_FLAG));
            Paint dim = new Paint();
            dim.setShader(new RadialGradient(SIZE / 2f, SIZE / 2f, SIZE * 0.75f,
                    Color.argb(photoDim, 0, 0, 0), Color.argb(Math.min(255, photoDim + 60), 0, 0, 0), Shader.TileMode.CLAMP));
            c.drawRect(0, 0, SIZE, SIZE, dim);
            return b;
        }
        if (background >= FIRST_PATTERN && background < PHOTO) { pattern(c, background - FIRST_PATTERN); return b; }
        int[] g = GRADIENTS[Math.min(background, GRADIENTS.length - 1)];
        Paint p = new Paint();
        p.setShader(new LinearGradient(0, 0, SIZE, SIZE, g[0], g[1], Shader.TileMode.CLAMP));
        c.drawRect(0, 0, SIZE, SIZE, p);
        return b;
    }

    /** Patterns: dark base, shapes in the accent colour. */
    private void pattern(Canvas c, int kind) {
        Paint base = new Paint();
        base.setShader(new RadialGradient(SIZE / 2f, SIZE / 2f, SIZE * 0.72f, 0xFF1A1D2B, 0xFF07080D, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, SIZE, SIZE, base);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int rgb = accentColor & 0x00FFFFFF;
        switch (kind) {
            case 0:   // rings
                p.setStyle(Paint.Style.STROKE);
                for (int r = 14, i = 0; r < 170; r += 14, i++) {
                    p.setStrokeWidth(i % 3 == 0 ? 2.5f : 1.2f);
                    p.setColor(rgb | (i % 3 == 0 ? 0x70000000 : 0x38000000));
                    c.drawCircle(SIZE / 2f, SIZE / 2f, r, p);
                }
                break;
            case 1:   // diagonal stripes
                p.setStrokeWidth(9);
                p.setColor(rgb | 0x30000000);
                for (int x = -SIZE; x < SIZE * 2; x += 26) c.drawLine(x, 0, x + SIZE, SIZE, p);
                break;
            case 2:   // dots, bigger towards the edge
                for (int y = 6; y < SIZE; y += 16)
                    for (int x = ((y / 16) % 2) * 8 + 6; x < SIZE; x += 16) {
                        double d = Math.hypot(x - 120, y - 120) / 120;
                        p.setColor(rgb | ((int) (40 + 90 * Math.min(1, d)) << 24));
                        c.drawCircle(x, y, (float) (1.2 + 2.6 * Math.min(1, d)), p);
                    }
                break;
            case 3:   // carbon weave
                for (int y = 0; y < SIZE; y += 8)
                    for (int x = 0; x < SIZE; x += 8) {
                        boolean a = ((x + y) / 8) % 2 == 0;
                        p.setColor(a ? 0xFF22252F : 0xFF14161C);
                        c.drawRect(x, y, x + 8, y + 4, p);
                        p.setColor(a ? 0xFF14161C : 0xFF22252F);
                        c.drawRect(x, y + 4, x + 8, y + 8, p);
                    }
                Paint glow = new Paint();
                glow.setShader(new RadialGradient(120, 120, 130, rgb | 0x40000000, rgb, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, SIZE, SIZE, glow);
                break;
            case 4:   // sun rays
                for (int i = 0; i < 24; i++) {
                    p.setColor(rgb | (i % 2 == 0 ? 0x34000000 : 0x14000000));
                    c.drawArc(new RectF(-120, -120, 360, 360), i * 15f, 15f, true, p);
                }
                break;
            default:  // grid
                p.setStrokeWidth(1);
                p.setColor(rgb | 0x40000000);
                for (int x = 0; x <= SIZE; x += 20) { c.drawLine(x, 0, x, SIZE, p); c.drawLine(0, x, SIZE, x, p); }
                break;
        }
    }

    private boolean drawnFont() { return font == FONT_DIGITAL || font == FONT_DOTS; }

    /** Width of one big digit for the time font (drawn fonts have fixed proportions). */
    private int timeDigitWidth(Paint tp) {
        int pad = effect == 0 ? 0 : 4;
        if (drawnFont()) { int w = Math.round(tp.getTextSize() * (font == FONT_DOTS ? 0.62f : 0.56f)) + pad; return w + (w & 1); }
        int w = digitWidth(tp) + pad;
        return w + (w & 1);
    }

    private int colonWidth(Paint tp) {
        int pad = effect == 0 ? 0 : 4;
        if (drawnFont()) { int w = Math.round(tp.getTextSize() * 0.26f) + pad; return w + (w & 1); }
        int w = (int) Math.ceil(tp.measureText(":")) + 4 + pad;
        return w + (w & 1);
    }

    /** One character of the time in the chosen font and effect. */
    private Bitmap timeGlyph(char ch, Paint tp, int w, int h) {
        Bitmap shape = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(shape);
        int pad = effect == 0 ? 0 : 2;
        if (font == FONT_DIGITAL) sevenSegment(c, ch, pad, pad, w - 2 * pad, h - 2 * pad, tp.getColor());
        else if (font == FONT_DOTS) dotMatrix(c, ch, pad, pad, w - 2 * pad, h - 2 * pad, tp.getColor());
        else {
            Paint.FontMetrics fm = tp.getFontMetrics();
            c.drawText(String.valueOf(ch), w / 2f, h / 2f - (fm.ascent + fm.descent) / 2f, tp);
        }
        if (effect == 0) return shape;
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas o = new Canvas(out);
        Bitmap mask = shape.extractAlpha();
        Paint mp = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (effect == 1) {          // outline in the extras colour
            mp.setColor(accentColor);
            for (int dx = -2; dx <= 2; dx++) for (int dy = -2; dy <= 2; dy++)
                if (dx * dx + dy * dy <= 5) o.drawBitmap(mask, dx, dy, mp);
        } else if (effect == 2) {   // drop shadow
            mp.setColor(0xB0000000);
            o.drawBitmap(mask, 2, 2, mp);
        } else {                    // glow in the extras colour
            mp.setColor(accentColor);
            mp.setMaskFilter(new android.graphics.BlurMaskFilter(3, android.graphics.BlurMaskFilter.Blur.NORMAL));
            o.drawBitmap(mask, 0, 0, mp);
            o.drawBitmap(mask, 0, 0, mp);
        }
        o.drawBitmap(shape, 0, 0, null);
        return out;
    }

    /** 7-segment LCD digit, unlit segments faintly visible. */
    private static void sevenSegment(Canvas c, char ch, float x, float y, float w, float h, int color) {
        Paint on = new Paint(Paint.ANTI_ALIAS_FLAG), off = new Paint(Paint.ANTI_ALIAS_FLAG);
        on.setColor(color);
        off.setColor((color & 0x00FFFFFF) | 0x1C000000);
        if (ch == ':') {
            float r = w * 0.18f;
            c.drawCircle(x + w / 2, y + h * 0.33f, r, on);
            c.drawCircle(x + w / 2, y + h * 0.67f, r, on);
            return;
        }
        String segs = new String[]{"abcdef", "bc", "abged", "abgcd", "fgbc", "afgcd", "afgedc", "abc", "abcdefg", "abcdfg"}[ch - '0'];
        float t = Math.max(3, w * 0.17f), m = w * 0.06f;
        float l = x + m, r = x + w - m, top = y + h * 0.08f, mid = y + h / 2, bot = y + h * 0.92f;
        float[][] rects = {
                {l + t / 2, top, r - t / 2, top + t},              // a
                {r - t, top + t / 2, r, mid - t / 4},              // b
                {r - t, mid + t / 4, r, bot - t / 2},              // c
                {l + t / 2, bot - t, r - t / 2, bot},              // d
                {l, mid + t / 4, l + t, bot - t / 2},              // e
                {l, top + t / 2, l + t, mid - t / 4},              // f
                {l + t / 2, mid - t / 2, r - t / 2, mid + t / 2}}; // g
        for (int i = 0; i < 7; i++) {
            float[] q = rects[i];
            c.drawRoundRect(new RectF(q[0], q[1], q[2], q[3]), t / 2, t / 2, segs.indexOf('a' + i) >= 0 ? on : off);
        }
    }

    /** 5x7 dot-matrix digit. */
    private static void dotMatrix(Canvas c, char ch, float x, float y, float w, float h, int color) {
        String[] rows;
        switch (ch) {
            case '0': rows = new String[]{"01110", "10001", "10011", "10101", "11001", "10001", "01110"}; break;
            case '1': rows = new String[]{"00100", "01100", "00100", "00100", "00100", "00100", "01110"}; break;
            case '2': rows = new String[]{"01110", "10001", "00001", "00010", "00100", "01000", "11111"}; break;
            case '3': rows = new String[]{"11111", "00010", "00100", "00010", "00001", "10001", "01110"}; break;
            case '4': rows = new String[]{"00010", "00110", "01010", "10010", "11111", "00010", "00010"}; break;
            case '5': rows = new String[]{"11111", "10000", "11110", "00001", "00001", "10001", "01110"}; break;
            case '6': rows = new String[]{"00110", "01000", "10000", "11110", "10001", "10001", "01110"}; break;
            case '7': rows = new String[]{"11111", "00001", "00010", "00100", "01000", "01000", "01000"}; break;
            case '8': rows = new String[]{"01110", "10001", "10001", "01110", "10001", "10001", "01110"}; break;
            case '9': rows = new String[]{"01110", "10001", "10001", "01111", "00001", "00010", "01100"}; break;
            default:  rows = new String[]{"0", "0", "1", "0", "1", "0", "0"};
        }
        int cols = rows[0].length();
        float cell = Math.min(w / cols, h / 7f);
        float ox = x + (w - cell * cols) / 2, oy = y + (h - cell * 7) / 2;
        Paint on = new Paint(Paint.ANTI_ALIAS_FLAG), off = new Paint(Paint.ANTI_ALIAS_FLAG);
        on.setColor(color);
        off.setColor((color & 0x00FFFFFF) | 0x18000000);
        for (int r = 0; r < 7; r++)
            for (int k = 0; k < cols; k++)
                c.drawCircle(ox + cell * (k + 0.5f), oy + cell * (r + 0.5f), cell * 0.4f, rows[r].charAt(k) == '1' ? on : off);
    }

    private static int evenCeil(float v) { int i = (int) Math.ceil(v); return i + (i & 1); }

    private static Bitmap glyph(String s, Paint p, int w, int h) {
        w += w & 1;   // the SW2S needs even image widths (odd ones come out skewed), see the note in IwfFile.raw565
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
        int d = (int) Math.ceil(w) + 2;
        return d + (d & 1);
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
        /** g4: 0-9, '-', °C, °F (weather 13, heart rate 11); g5: 0-9, '.' (distance). */
        Bitmap[] temp = new Bitmap[13], dist = new Bitmap[11];
        Bitmap[] days = new Bitmap[7];
        /** g6: seconds digits; apm: AM, PM. */
        Bitmap[] sec = new Bitmap[10], apm = new Bitmap[2];
        int timeX, sideX, secY, apmY, secH, apmH, secW, dw;
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
        boolean stacked = style == 0 && position == POS_STACKED, big = style == 0 && position == POS_BIG;
        boolean side = style == 0 && (seconds || ampm) && !stacked;
        int size = stacked ? Math.round(timeSize * 1.25f) : big ? 110 : timeSize, timeW, sideW = 0;
        // Height left for the time once the other rows are placed (the screen is round: keep ~14 px margins)
        int smallCount = (steps ? 1 : 0) + (heart ? 1 : 0) + (calories ? 1 : 0) + (weather ? 1 : 0) + (distance ? 1 : 0);
        int pairRows = big ? 0 : smallCount == 0 ? 0 : (position == 1 ? (smallCount + 1) / 2 : 1 + smallCount / 2);
        int otherRows = pairRows + ((date || weekday) ? 1 : 0) + (battery ? 1 : 0);
        int room = SIZE - 28 - otherRows * (ROW_H + 4);
        if (style == 0) {
            float k = (drawnFont() ? 1.0f : 1.1f);
            int maxSize = Math.round((stacked ? room / 2f + (drawnFont() ? 0 : room / 16f) : room) / k) - (effect == 0 ? 0 : 4);
            size = Math.max(32, Math.min(size, maxSize));
        }
        Paint tp, secP = null, apmP = null;
        while (true) {   // shrink the time until its row fits the screen
            tp = paint(tf, size, timeColor);
            int dw = timeDigitWidth(tp);
            timeW = stacked ? 2 * dw : 4 * dw + colonWidth(tp);
            if (side) {
                secP = paint(tf, Math.round(size * 0.42f), timeColor);
                apmP = paint(Typeface.create("sans-serif-medium", Typeface.BOLD), Math.max(14, Math.round(size * 0.24f)), accentColor);
                q.secW = timeDigitWidth(secP);
                sideW = Math.max(seconds ? 2 * q.secW : 0, ampm ? evenCeil(apmP.measureText("PM")) + 4 : 0);
            }
            int limit = big ? SIZE - 40 : SIZE - 24;
            if (timeW + (side ? 6 + sideW : 0) <= limit || size <= 36) break;
            size -= 2;
        }
        int dw = timeDigitWidth(tp);
        q.timeH = (int) Math.ceil(size * (drawnFont() ? 1.0f : 1.1f)) + (effect == 0 ? 0 : 4);
        q.timeH += q.timeH & 1;
        int colonW = colonWidth(tp);
        for (int i = 0; i < 10; i++) q.time[i] = timeGlyph((char) ('0' + i), tp, dw, q.timeH);
        q.time[10] = timeGlyph(':', tp, colonW, q.timeH);
        q.dw = dw;
        timeW = stacked ? 2 * dw : 4 * dw + colonW;
        q.timeX = (SIZE - timeW - (side ? 6 + sideW : 0)) / 2;
        q.sideX = q.timeX + timeW + 6;
        if (side) {
            Paint.FontMetrics fm = tp.getFontMetrics();
            float base = q.timeH / 2f - (fm.ascent + fm.descent) / 2f;      // baseline of the big digits
            if (seconds) {
                q.secH = (int) Math.ceil(secP.getTextSize() * 1.1f);
                q.secW = timeDigitWidth(secP);
                for (int i = 0; i < 10; i++) q.sec[i] = timeGlyph((char) ('0' + i), secP, q.secW, q.secH);
                Paint.FontMetrics sf = secP.getFontMetrics();
                q.secY = Math.round(base - (q.secH / 2f - (sf.ascent + sf.descent) / 2f));   // same baseline
            }
            if (ampm) {
                q.apmH = (int) Math.ceil(apmP.getTextSize() * 1.2f);
                int aw = evenCeil(apmP.measureText("PM")) + 4;
                apmP.setTextAlign(Paint.Align.CENTER);
                q.apm[0] = glyph("AM", apmP, aw, q.apmH);
                q.apm[1] = glyph("PM", apmP, aw, q.apmH);
                q.apmY = Math.max(0, Math.round(base + fm.ascent) - 2);   // top of the digits
            }
        }

        Paint sp = paint(Typeface.create("sans-serif-medium", Typeface.NORMAL), 20, accentColor);
        q.sw = digitWidth(sp);
        for (int i = 0; i < 10; i++) {
            q.small[i] = glyph(String.valueOf(i), sp, q.sw, ROW_H);
            q.batt[i] = q.temp[i] = q.dist[i] = q.small[i];
        }
        q.small[10] = glyph("/", sp, (int) Math.ceil(sp.measureText("/")) + 2, ROW_H);
        q.batt[10] = glyph("%", sp, (int) Math.ceil(sp.measureText("%")) + 2, ROW_H);
        q.temp[10] = glyph("-", sp, (int) Math.ceil(sp.measureText("-")) + 2, ROW_H);
        q.temp[11] = glyph("\u00b0C", sp, (int) Math.ceil(sp.measureText("\u00b0C")) + 2, ROW_H);
        q.temp[12] = glyph("\u00b0F", sp, (int) Math.ceil(sp.measureText("\u00b0F")) + 2, ROW_H);
        q.dist[10] = glyph(".", sp, (int) Math.ceil(sp.measureText(".")) + 2, ROW_H);
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

        // Small readouts, packed two per row: {label, type, font, fontnum, digits, sample}
        List<Object[]> small = new ArrayList<>();
        if (steps) small.add(new Object[]{"steps", "step", "g2", 10, 5, "5367"});
        if (heart) small.add(new Object[]{"\u2665", "heartrate", "g4", 11, 3, "72"});
        if (calories) small.add(new Object[]{"kcal", "calorie", "g2", 10, 4, "320"});
        if (weather) small.add(new Object[]{"\u26c5", "weather", "g4", 13, 4, "21C"});
        if (distance) small.add(new Object[]{miles ? "mi" : "km", "distance", "g5", 11, 4, "4.19"});
        // The SW2S screen is round: the row nearest the top edge holds one readout, the rest are paired.
        List<List<Object[]>> pairs = new ArrayList<>();
        boolean firstSingle = style == 1 || position != 1;
        for (int i = 0; i < small.size(); ) {
            int n = (i == 0 && firstSingle) ? 1 : 2;
            pairs.add(small.subList(i, Math.min(i + n, small.size())));
            i += n;
        }

        // Rows from top to bottom; the stack is centred on the screen.
        List<Object> rows = new ArrayList<>();
        boolean dateRow = date || weekday;
        if (style == 1) {
            if (!pairs.isEmpty()) rows.add(pairs.get(0));
            rows.add("gap");
            if (dateRow) rows.add("date");
            for (int i = 1; i < pairs.size(); i++) rows.add(pairs.get(i));
        } else if (position == POS_BIG) {      // big time: only the date row and battery
            rows.add("time"); if (dateRow) rows.add("date");
        } else if (position == POS_STACKED) {  // hours above minutes
            if (!pairs.isEmpty()) rows.add(pairs.get(0));
            rows.add("hour"); rows.add("minute"); if (dateRow) rows.add("date");
            for (int i = 1; i < pairs.size(); i++) rows.add(pairs.get(i));
        } else if (position == 1) {
            rows.add("time"); if (dateRow) rows.add("date"); rows.addAll(pairs);
        } else if (position == 2) {
            rows.addAll(pairs); if (dateRow) rows.add("date"); rows.add("time");
        } else {
            if (!pairs.isEmpty()) rows.add(pairs.get(0));
            rows.add("time"); if (dateRow) rows.add("date");
            for (int i = 1; i < pairs.size(); i++) rows.add(pairs.get(i));
        }
        if (battery) rows.add("battery");
        int gap = GAP, total;
        while (true) {
            total = 0;
            for (Object r : rows) total += ("time".equals(r) || "hour".equals(r) || "minute".equals(r) ? q.timeH
                    : "gap".equals(r) ? 64 : ROW_H) + gap;
            total -= gap;
            if (total <= SIZE - 36 || gap == 0) break;
            gap--;
        }
        int y = Math.max(16, (SIZE - total) / 2);

        for (Object r : rows) {
            if (r instanceof List) {
                @SuppressWarnings("unchecked") List<Object[]> pr = (List<Object[]>) r;
                // place the readouts side by side, centred, 14 px apart
                float[] wdt = new float[pr.size()];
                float sum = 14 * (pr.size() - 1);
                for (int i = 0; i < pr.size(); i++) { wdt[i] = widthOf(q, lp, pr.get(i)); sum += wdt[i]; }
                float x = (SIZE - sum) / 2f;
                for (int i = 0; i < pr.size(); i++) {
                    Object[] o = pr.get(i);
                    labelled(q, bgc, lp, (String) o[0], (String) o[1], (String) o[2], (Integer) o[3], (Integer) o[4],
                            Math.round(x + wdt[i] / 2), y, (String) o[5]);
                    x += wdt[i] + 14;
                }
                y += ROW_H;
            } else if ("time".equals(r)) {
                add(q, item("time", q.timeX, y, timeW, q.timeH, "g1", 11, null), "10:08");
                if (style == 0 && seconds)
                    add(q, item("second", q.sideX, y + q.secY, 2 * q.secW, q.secH, "g6", 10, null), "32");
                if (style == 0 && ampm)
                    add(q, item("apm", q.sideX, y + q.apmY, q.apm[0].getWidth(), q.apmH, "apm", 2, null), "PM");
                y += q.timeH;
            } else if ("hour".equals(r) || "minute".equals(r)) {
                add(q, item((String) r, (SIZE - 2 * q.dw) / 2, y, 2 * q.dw, q.timeH, "g1", 10, null),
                        "hour".equals(r) ? "10" : "08");
                y += q.timeH - (drawnFont() ? 0 : q.timeH / 8);   // tuck the two lines together a little
            } else if ("date".equals(r)) {
                int dateW = 5 * q.sw + 4;
                boolean both = date && weekday;
                if (date) add(q, item("date", both ? SIZE / 2 - dateW - 4 : (SIZE - dateW) / 2, y, dateW, ROW_H, "g2", 11, 0), "11/21");
                if (weekday) add(q, item("week", both ? SIZE / 2 + 8 : (SIZE - 50) / 2, y, 50, ROW_H, "week", 7, 0), "FRI");
                y += ROW_H;
            } else if ("battery".equals(r)) {
                add(q, item("battery", (SIZE - 4 * q.sw) / 2, y, 4 * q.sw, ROW_H, "g3", 11, null), "80%");
                y += ROW_H;
            } else {
                y += 64;
            }
            y += gap;
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

    private static float widthOf(Parts q, Paint lp, Object[] o) {
        int digits = (Integer) o[4];
        return lp.measureText((String) o[0]) + 4 + digits * q.sw + ("weather".equals(o[1]) ? 2 * q.sw : 0);
    }

    /** A number with a small label drawn into the background on its left, centred around cx. */
    private void labelled(Parts q, Canvas bgc, Paint lp, String label, String type, String font, int fontnum,
                          int digits, int cx, int y, String sample) throws Exception {
        float lw = lp.measureText(label) + 4;
        int nw = digits * q.sw + (type.equals("weather") ? 2 * q.sw : 0);
        float x0 = cx - (lw + nw) / 2f;
        Paint.FontMetrics fm = lp.getFontMetrics();
        bgc.drawText(label, x0, y + ROW_H / 2f - (fm.ascent + fm.descent) / 2f, lp);
        add(q, item(type, Math.round(x0 + lw), y, nw, ROW_H, font, fontnum, type.equals("weather") ? 2 : null), sample);
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

    /** What the watch will show (10:08, Fri 11/21, 5367 steps, 72 bpm, 320 kcal, 21°C, 4.19 km, 80%). */
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
                if (t.equals("apm")) { c.drawBitmap(q.apm[1], o.getInt("x"), o.getInt("y"), null); continue; }
                if (t.equals("second")) {
                    c.drawBitmap(q.sec[3], o.getInt("x"), o.getInt("y"), null);
                    c.drawBitmap(q.sec[2], o.getInt("x") + q.secW, o.getInt("y"), null);
                    continue;
                }
                String fnt = o.getString("font");
                Bitmap[] set = t.equals("time") || t.equals("hour") || t.equals("minute") ? q.time : fnt.equals("g3") ? q.batt : fnt.equals("g4") ? q.temp
                        : fnt.equals("g5") ? q.dist : q.small;
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

    private static int idx(char ch) { return Character.isDigit(ch) ? ch - '0' : ch == 'C' ? 11 : ch == 'F' ? 12 : 10; }

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
        List<String> fontNames = new ArrayList<>(java.util.Arrays.asList("g1", "g2", "g3", "g4", "g5", "week"));
        if (style == 0 && seconds) fontNames.add("g6");
        if (style == 0 && ampm) fontNames.add("apm");
        for (String n : fontNames) {
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
        for (int i = 0; i < 13; i++) f.add("g4_" + i, raw(q.temp[i]));
        for (int i = 0; i < 11; i++) f.add("g5_" + i, raw(q.dist[i]));
        if (style == 0 && seconds) for (int i = 0; i < 10; i++) f.add("g6_" + i, raw(q.sec[i]));
        if (style == 0 && ampm) { f.add("apm_en_am", raw(q.apm[0])); f.add("apm_en_pm", raw(q.apm[1])); }
        for (int i = 0; i < 7; i++) f.add("week_en_" + DAYS[i], raw(q.days[i]));
        return f.build();
    }
}
