package dev.fishtank.watchlink.face;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A custom watch face for the 240x240 round SW2S, turned into an IDO .iwf file with the same layout format as the
 * cloud faces in the Momax app:
 *   "custom" widgets (time, date, week, step, calorie, heartrate, battery, ...) draw numbers from small digit images,
 *   a "watch" widget rotates hour/minute/second hand images around a point on the screen.
 * The icons next to the numbers are drawn into the background.
 *
 * Digital faces are a centred stack of rows: the date, the time, a row of up to three readouts (icon above the
 * number) and, when more readouts are on, single lines (icon left of the number). Every row is checked against the
 * round screen and the time shrinks until everything fits. Analog faces put readouts at 9, 3 and 6 o'clock and the
 * date at 12.
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
    private static final int POS_UPPER = 1, POS_LOWER = 2, POS_BIG = 3, POS_STACKED = 4;

    public int effect;
    public int style, background, font, timeColor = 0xFFFFFFFF, accentColor = 0xFF00D3B8, position, timeSize = 64;
    public boolean date = true, weekday = true, steps = true, battery = true, heart = false, calories = false,
            weather = false, distance = false, seconds = false, ampm = false;
    /** Distance unit shown with the distance: "MI" or "KM" (follows the watch's unit setting). */
    public boolean miles;
    /** Optional photo background, already cropped to 240x240. */
    public Bitmap photo;
    public int photoDim = 90;

    private static final String[] DAYS = {"mon", "tue", "wed", "thur", "fri", "sat", "sun"};

    /** Readouts: icon size, number text size and number image height. */
    private static final int ICON = 18, VAL = 20, VAL_H = 24;
    /** A readout in a row of columns (icon above the number), and on a line (icon left of the number). */
    private static final int COL_H = ICON + 2 + VAL_H, LINE_H = VAL_H;
    /** Keep this far inside the round edge. */
    private static final int EDGE = 10;
    private static final int IC_STEPS = 0, IC_HEART = 1, IC_FLAME = 2, IC_WEATHER = 3, IC_DIST = 4, IC_BATTERY = 5;

    // ------------------------------------------------------------------------------------------------ background

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

    /** Patterns: dark base, shapes in the accent colour, kept faint so the numbers stay easy to read. */
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
                    p.setColor(rgb | (i % 3 == 0 ? 0x5C000000 : 0x2E000000));
                    c.drawCircle(SIZE / 2f, SIZE / 2f, r, p);
                }
                break;
            case 1:   // diagonal stripes
                p.setStrokeWidth(9);
                p.setColor(rgb | 0x26000000);
                for (int x = -SIZE; x < SIZE * 2; x += 26) c.drawLine(x, 0, x + SIZE, SIZE, p);
                break;
            case 2:   // dots, bigger towards the edge
                for (int y = 6; y < SIZE; y += 16)
                    for (int x = ((y / 16) % 2) * 8 + 6; x < SIZE; x += 16) {
                        double d = Math.hypot(x - 120, y - 120) / 120;
                        p.setColor(rgb | ((int) (30 + 80 * Math.min(1, d)) << 24));
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
                    p.setColor(rgb | (i % 2 == 0 ? 0x2A000000 : 0x10000000));
                    c.drawArc(new RectF(-120, -120, 360, 360), i * 15f, 15f, true, p);
                }
                break;
            default:  // grid
                p.setStrokeWidth(1);
                p.setColor(rgb | 0x30000000);
                for (int x = 0; x <= SIZE; x += 20) { c.drawLine(x, 0, x, SIZE, p); c.drawLine(0, x, SIZE, x, p); }
                break;
        }
    }

    /**
     * Ordered (Bayer 4x4) dither down to the watch's RGB565, so gradients and photos come out smooth instead of in
     * bands. Each channel ends up on an exact 565 step, so IwfFile.raw565 keeps it as is.
     */
    private static Bitmap dither565(Bitmap src) {
        final int[] bayer = {0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5};
        int w = src.getWidth(), h = src.getHeight();
        int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int i = y * w + x, c = px[i], t = bayer[(y & 3) * 4 + (x & 3)];
                px[i] = (c & 0xFF000000) | step(c >> 16 & 0xFF, t, 3) << 16 | step(c >> 8 & 0xFF, t, 2) << 8 | step(c & 0xFF, t, 3);
            }
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        out.setPixels(px, 0, w, 0, 0, w, h);
        return out;
    }

    /** v rounded down to a multiple of 2^bits after adding (t + 0.5) / 16 of a step; averages back to v. */
    private static int step(int v, int t, int bits) {
        int s = 1 << bits, n = (32 * v + (2 * t + 1) * s) / (32 * s);
        return Math.min(255 >> bits, n) << bits;
    }

    // ------------------------------------------------------------------------------------------------ time glyphs

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

    /** The time font at one size, measured. Glyph images hug the digits so the gaps between rows look even. */
    private static final class TimeFit {
        Paint tp, secP, apmP;
        /** Digit width, colon width, image height, baseline, top and bottom of the digits, row width, width beside. */
        int dw, colonW, h, base, top, bottom, w, sideW;
    }

    private TimeFit timeFit(int size, boolean stacked, boolean side) {
        TimeFit t = new TimeFit();
        t.tp = paint(typeface(), size, timeColor);
        t.dw = timeDigitWidth(t.tp);
        t.colonW = colonWidth(t.tp);
        int[] box = digitBox(t.tp);
        t.h = box[0]; t.base = box[1]; t.top = box[2]; t.bottom = box[3];
        t.w = stacked ? 2 * t.dw : 4 * t.dw + t.colonW;
        if (side) {
            t.secP = paint(typeface(), Math.round(size * 0.42f), timeColor);
            t.apmP = paint(Typeface.create(Typeface.SANS_SERIF, 700, false), Math.max(14, Math.round(size * 0.24f)), accentColor);
            t.sideW = Math.max(seconds ? 2 * timeDigitWidth(t.secP) : 0, ampm ? evenCeil(t.apmP.measureText("PM")) + 4 : 0);
        }
        return t;
    }

    /** {image height, baseline, top of the digits, bottom of the digits} for digits in this paint. */
    private int[] digitBox(Paint p) {
        if (drawnFont()) {   // drawn digits fill 8%..92% of the box
            int h = Math.round(p.getTextSize()) + (effect == 0 ? 0 : 4);
            h += h & 1;
            return new int[]{h, Math.round(h * 0.92f), Math.round(h * 0.08f), Math.round(h * 0.92f)};
        }
        int pad = effect == 0 ? 3 : 5;
        Rect b = new Rect();
        p.getTextBounds("0123456789:", 0, 11, b);
        int h = b.height() + 2 * pad;
        h += h & 1;
        int top = (h - b.height()) / 2;
        return new int[]{h, top - b.top, top, top + b.height()};
    }

    /** One character of the time in the chosen font and effect. */
    private Bitmap timeGlyph(char ch, Paint tp, int w, int h, int base) {
        Bitmap shape = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(shape);
        int pad = effect == 0 ? 0 : 2;
        if (font == FONT_DIGITAL) sevenSegment(c, ch, pad, pad, w - 2 * pad, h - 2 * pad, tp.getColor());
        else if (font == FONT_DOTS) dotMatrix(c, ch, pad, pad, w - 2 * pad, h - 2 * pad, tp.getColor());
        else c.drawText(String.valueOf(ch), w / 2f, base, tp);
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
            mp.setMaskFilter(new BlurMaskFilter(3, BlurMaskFilter.Blur.NORMAL));
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

    // ------------------------------------------------------------------------------------------------ text helpers

    private static int evenCeil(float v) { int i = (int) Math.ceil(v); return i + (i & 1); }

    /** Text in a w x h image with its baseline at base; align -1 left, 0 centre, 1 right. */
    private static Bitmap text(String s, Paint p, int w, int h, float base, int align) {
        w += w & 1;   // the SW2S needs even image widths (odd ones come out skewed), see the note in IwfFile.raw565
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Paint pp = new Paint(p);
        pp.setTextAlign(align < 0 ? Paint.Align.LEFT : align > 0 ? Paint.Align.RIGHT : Paint.Align.CENTER);
        new Canvas(b).drawText(s, align < 0 ? 1 : align > 0 ? w - 1 : w / 2f, base, pp);
        return b;
    }

    /** Width for a glyph image that just fits s. */
    private static int fit(Paint p, String s) { return evenCeil(p.measureText(s)) + 2; }

    private static Paint paint(Typeface tf, float size, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(tf);
        p.setTextSize(size);
        p.setColor(color);
        p.setTextAlign(Paint.Align.CENTER);
        return p;
    }

    private static float maxDigitAdvance(Paint p) {
        float w = 0;
        for (char ch = '0'; ch <= '9'; ch++) w = Math.max(w, p.measureText(String.valueOf(ch)));
        return w;
    }

    private static int digitWidth(Paint p) {
        int d = (int) Math.ceil(maxDigitAdvance(p)) + 2;
        return d + (d & 1);
    }

    // ------------------------------------------------------------------------------------------------ icons

    /** An ICON-sized symbol in the accent colour centred on (cx, cy). Shapes are drawn in a -0.5..0.5 box. */
    private void icon(Canvas c, int kind, float cx, float cy) {
        int s = ICON + 6;
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas k = new Canvas(b);
        Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
        f.setColor(accentColor);
        if (kind == IC_DIST) {   // the unit itself
            String u = miles ? "MI" : "KM";
            f.setTypeface(Typeface.create(Typeface.SANS_SERIF, 700, false));
            f.setTextSize(11.5f);
            f.setTextAlign(Paint.Align.CENTER);
            f.setLetterSpacing(0.04f);
            Rect r = new Rect();
            f.getTextBounds(u, 0, u.length(), r);
            k.drawText(u, s / 2f, s / 2f - r.exactCenterY(), f);
        } else {
            k.translate(s / 2f, s / 2f);
            k.scale(ICON, ICON);
            switch (kind) {
                case IC_STEPS: k.drawPath(footprints(), f); break;
                case IC_HEART: k.drawPath(heart(), f); break;
                case IC_FLAME: k.drawPath(flame(), f); break;
                case IC_BATTERY: k.drawPath(batteryIcon(), f); break;
                default: {   // weather: sun behind a cloud
                    k.translate(0.03f, 0.06f);
                    float sx = -0.13f, sy = -0.13f;
                    k.drawCircle(sx, sy, 0.15f, f);
                    Paint ray = new Paint(f);
                    ray.setStyle(Paint.Style.STROKE);
                    ray.setStrokeWidth(0.075f);
                    ray.setStrokeCap(Paint.Cap.ROUND);
                    for (int i = 0; i < 8; i++) {
                        double a = Math.PI * i / 4;
                        float dx = (float) Math.cos(a), dy = (float) Math.sin(a);
                        k.drawLine(sx + dx * 0.23f, sy + dy * 0.23f, sx + dx * 0.32f, sy + dy * 0.32f, ray);
                    }
                    Path cloud = cloud();
                    Paint gap = new Paint(Paint.ANTI_ALIAS_FLAG);
                    gap.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                    gap.setStyle(Paint.Style.STROKE);
                    gap.setStrokeWidth(0.16f);
                    k.drawPath(cloud, gap);
                    k.drawPath(cloud, f);
                }
            }
        }
        c.drawBitmap(b, Math.round(cx - s / 2f), Math.round(cy - s / 2f), null);
    }

    private static Path poly(float... xy) {
        Path p = new Path();
        p.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) p.lineTo(xy[i], xy[i + 1]);
        p.close();
        return p;
    }

    private static Path circle(float x, float y, float r) {
        Path p = new Path();
        p.addCircle(x, y, r, Path.Direction.CW);
        return p;
    }

    private static Path heart() {
        Path p = circle(-0.23f, -0.15f, 0.26f);
        p.op(circle(0.23f, -0.15f, 0.26f), Path.Op.UNION);
        // sides run from the point at the bottom to where they touch the two circles
        p.op(poly(0f, 0.43f, -0.41f, 0.038f, -0.23f, -0.15f, 0.23f, -0.15f, 0.41f, 0.038f), Path.Op.UNION);
        return p;
    }

    /** Two shoe prints, the right one a step ahead. */
    private static Path footprints() {
        Path p = new Path();
        for (int side = -1; side <= 1; side += 2) {
            float x = side * 0.2f, y = -side * 0.07f;
            Path foot = new Path();
            foot.addOval(new RectF(x - 0.14f, y - 0.37f, x + 0.14f, y + 0.08f), Path.Direction.CW);
            foot.addOval(new RectF(x - 0.11f, y + 0.13f, x + 0.11f, y + 0.34f), Path.Direction.CW);
            Matrix m = new Matrix();
            m.setRotate(side * 10, x, y);
            foot.transform(m);
            p.addPath(foot);
        }
        return p;
    }

    private static Path flame() {
        Path p = new Path();
        p.moveTo(0.04f, -0.46f);
        p.cubicTo(0.30f, -0.26f, 0.38f, -0.04f, 0.36f, 0.14f);
        p.cubicTo(0.34f, 0.34f, 0.18f, 0.46f, 0f, 0.46f);
        p.cubicTo(-0.18f, 0.46f, -0.36f, 0.34f, -0.36f, 0.14f);
        p.cubicTo(-0.36f, -0.02f, -0.26f, -0.12f, -0.18f, -0.22f);
        p.cubicTo(-0.16f, -0.10f, -0.10f, -0.04f, -0.04f, -0.02f);
        p.cubicTo(-0.06f, -0.20f, -0.04f, -0.34f, 0.04f, -0.46f);
        p.close();
        Path inner = new Path();
        inner.moveTo(0f, 0.04f);
        inner.cubicTo(0.08f, 0.12f, 0.15f, 0.18f, 0.15f, 0.25f);
        inner.cubicTo(0.15f, 0.31f, 0.08f, 0.35f, 0f, 0.35f);
        inner.cubicTo(-0.08f, 0.35f, -0.15f, 0.31f, -0.15f, 0.25f);
        inner.cubicTo(-0.15f, 0.18f, -0.08f, 0.12f, 0f, 0.04f);
        inner.close();
        p.op(inner, Path.Op.DIFFERENCE);
        return p;
    }

    private static Path cloud() {
        Path c = circle(0.04f, 0.10f, 0.19f);
        c.op(circle(0.25f, 0.18f, 0.14f), Path.Op.UNION);
        c.op(circle(-0.17f, 0.21f, 0.12f), Path.Op.UNION);
        Path base = new Path();
        base.addRoundRect(new RectF(-0.29f, 0.17f, 0.39f, 0.33f), 0.08f, 0.08f, Path.Direction.CW);
        c.op(base, Path.Op.UNION);
        return c;
    }

    private static Path batteryIcon() {
        Path p = new Path();
        p.addRoundRect(new RectF(-0.46f, -0.25f, 0.36f, 0.25f), 0.09f, 0.09f, Path.Direction.CW);
        Path inside = new Path();
        inside.addRoundRect(new RectF(-0.37f, -0.16f, 0.27f, 0.16f), 0.04f, 0.04f, Path.Direction.CW);
        p.op(inside, Path.Op.DIFFERENCE);
        p.addRoundRect(new RectF(0.39f, -0.1f, 0.47f, 0.1f), 0.03f, 0.03f, Path.Direction.CW);   // terminal
        p.addRoundRect(new RectF(-0.31f, -0.1f, 0.12f, 0.1f), 0.02f, 0.02f, Path.Direction.CW);  // charge
        return p;
    }

    // ------------------------------------------------------------------------------------------------ analog hands

    private static final int HOUR_W = 12, HOUR_H = 68, HOUR_PIV = 56;
    private static final int MIN_W = 10, MIN_H = 104, MIN_PIV = 90;
    private static final int SEC_W = 12, SEC_H = 124, SEC_PIV = 100;

    /** A hand pointing up in a w x h image, turning around (w/2, piv): a rounded bar with a round hub. */
    private static Bitmap hand(int w, int h, int piv, float width, float hub, int color) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        float cx = w / 2f;
        c.drawRoundRect(new RectF(cx - width / 2, 1, cx + width / 2, h - 1), width / 2, width / 2, p);
        c.drawCircle(cx, piv, hub, p);
        return b;
    }

    /** Thin needle with a counterweight; its hub (drawn last by the watch) caps the centre. */
    private Bitmap secondHand() {
        Bitmap b = Bitmap.createBitmap(SEC_W, SEC_H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(accentColor);
        float cx = SEC_W / 2f;
        c.drawRoundRect(new RectF(cx - 1, 0, cx + 1, SEC_H - 2), 1, 1, p);
        c.drawRoundRect(new RectF(cx - 2.5f, SEC_PIV + 10, cx + 2.5f, SEC_H - 1), 2.5f, 2.5f, p);
        c.drawCircle(cx, SEC_PIV, 4.5f, p);
        p.setColor(0xFF15171F);
        c.drawCircle(cx, SEC_PIV, 1.6f, p);
        return b;
    }

    /** Minute and hour ticks; the quarters a little bolder and 12 o'clock in the accent colour. */
    private void dial(Canvas c) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 60; i++) {
            boolean hour = i % 5 == 0, quarter = i % 15 == 0;
            p.setColor(i == 0 ? accentColor : hour ? timeColor : (timeColor & 0x00FFFFFF) | 0x55000000);
            p.setStrokeWidth(quarter ? 5 : hour ? 3.5f : 1.5f);
            float r1 = quarter ? 99 : hour ? 103 : 109, r2 = 114;
            double a = Math.PI * 2 * i / 60;
            float sx = (float) Math.sin(a), cy = (float) Math.cos(a);
            c.drawLine(120 + sx * r1, 120 - cy * r1, 120 + sx * r2, 120 - cy * r2, p);
        }
    }

    // ------------------------------------------------------------------------------------------------ layout

    /** A number the watch fills in, with an icon drawn into the background. */
    private static final class Readout {
        final String type, font, sample, longest;
        final int fontnum, icon;
        final Integer style;

        Readout(String type, String font, int fontnum, String sample, String longest, int icon, Integer style) {
            this.type = type; this.font = font; this.fontnum = fontnum;
            this.sample = sample; this.longest = longest; this.icon = icon; this.style = style;
        }
    }

    /** The readouts that are switched on, in a fixed order. */
    private List<Readout> readouts() {
        List<Readout> r = new ArrayList<>();
        if (steps) r.add(new Readout("step", "g2", 10, "5367", "88888", IC_STEPS, null));
        if (heart) r.add(new Readout("heartrate", "g4", 11, "72", "188", IC_HEART, null));
        if (calories) r.add(new Readout("calorie", "g2", 10, "320", "8888", IC_FLAME, null));
        if (distance) r.add(new Readout("distance", "g5", 11, "4.19", "88.88", IC_DIST, null));
        if (weather) r.add(new Readout("weather", "g4", 13, "21C", "-88C", IC_WEATHER, 2));
        if (battery) r.add(new Readout("battery", "g3", 11, "80%", "100%", IC_BATTERY, null));
        return r;
    }

    /** A row of readouts with the icons above the numbers. */
    private static final class Cols { final List<Readout> r; Cols(List<Readout> r) { this.r = r; } }
    /** Readouts on one line, each icon left of its number. */
    private static final class Line { final List<Readout> r; Line(List<Readout> r) { this.r = r; } }

    private static final class Parts {
        Bitmap bg;
        final Bitmap[] time = new Bitmap[11], g2 = new Bitmap[11], g3 = new Bitmap[11], g4 = new Bitmap[13],
                g5 = new Bitmap[11], days = new Bitmap[7], sec = new Bitmap[10], apm = new Bitmap[2];
        Bitmap hourHand, minHand, secHand;
        /** Empty space above/below the digits in a number image; seconds and AM/PM placement beside the time. */
        int valPad, secW, secH, secY, apmH, apmY;
        final JSONArray items = new JSONArray();
        /** Preview text per item. */
        final List<String> sample = new ArrayList<>();

        Bitmap[] font(String name) {
            switch (name) {
                case "g1": return time;
                case "g3": return g3;
                case "g4": return g4;
                case "g5": return g5;
                case "g6": return sec;
                default: return g2;
            }
        }

        int width(String font, String text) {
            Bitmap[] set = font(font);
            int w = 0;
            for (char ch : text.toCharArray()) w += set[idx(ch)].getWidth();
            return w;
        }
    }

    private Parts parts() throws Exception {
        Parts q = new Parts();
        q.bg = baseBackground();
        Canvas bgc = new Canvas(q.bg);
        valueGlyphs(q);
        List<Readout> all = readouts();
        if (style == 1) analog(q, bgc, all);
        else digital(q, bgc, all);
        return q;
    }

    /** Number glyphs for the readouts and the date (all the same size and colour), and the weekday names. */
    private void valueGlyphs(Parts q) {
        Typeface medium = Typeface.create(Typeface.SANS_SERIF, 500, false);
        Paint vp = paint(medium, VAL, timeColor), up = paint(medium, Math.round(VAL * 0.7f), timeColor);
        Rect b = new Rect();
        vp.getTextBounds("0123456789", 0, 10, b);
        float base = (VAL_H - b.height()) / 2f - b.top;
        q.valPad = (VAL_H - b.height()) / 2;
        int sw = evenCeil(maxDigitAdvance(vp));   // same width for every digit, so numbers don't shift as they change
        for (int i = 0; i < 10; i++) q.g2[i] = q.g3[i] = q.g4[i] = q.g5[i] = text(String.valueOf(i), vp, sw, VAL_H, base, 0);
        q.g2[10] = text("/", vp, fit(vp, "/"), VAL_H, base, 0);
        q.g3[10] = text("%", up, fit(up, "%"), VAL_H, base, 0);
        q.g4[10] = text("-", vp, fit(vp, "-"), VAL_H, base, 0);
        q.g4[11] = text("°C", up, fit(up, "°C"), VAL_H, base, 0);
        q.g4[12] = text("°F", up, fit(up, "°F"), VAL_H, base, 0);
        q.g5[10] = text(".", vp, fit(vp, "."), VAL_H, base, 0);
        // Weekdays in accent capitals on the same baseline; right-aligned when the date follows them
        Paint wp = paint(Typeface.create(Typeface.SANS_SERIF, 700, false), 17, accentColor);
        wp.setLetterSpacing(0.06f);
        int ww = 0;
        for (String d : DAYS) ww = Math.max(ww, fit(wp, d.substring(0, 3).toUpperCase()));
        int align = date && weekday ? 1 : 0;
        for (int i = 0; i < 7; i++) q.days[i] = text(DAYS[i].substring(0, 3).toUpperCase(), wp, ww, VAL_H, base, align);
    }

    private void timeGlyphs(Parts q, TimeFit t, boolean side) {
        for (int i = 0; i < 10; i++) q.time[i] = timeGlyph((char) ('0' + i), t.tp, t.dw, t.h, t.base);
        q.time[10] = timeGlyph(':', t.tp, t.colonW, t.h, t.base);
        if (!side) return;
        if (seconds) {   // smaller digits sharing the big digits' baseline
            int[] sb = digitBox(t.secP);
            q.secH = sb[0];
            q.secW = timeDigitWidth(t.secP);
            for (int i = 0; i < 10; i++) q.sec[i] = timeGlyph((char) ('0' + i), t.secP, q.secW, q.secH, sb[1]);
            q.secY = Math.max(0, t.base - sb[1]);
        }
        if (ampm) {      // AM/PM level with the tops of the big digits
            Rect b = new Rect();
            t.apmP.getTextBounds("AMP", 0, 3, b);
            q.apmH = b.height() + 6;
            int top = 3, base = top - b.top, aw = evenCeil(t.apmP.measureText("PM")) + 4;
            q.apm[0] = text("AM", t.apmP, aw, q.apmH, base, 0);
            q.apm[1] = text("PM", t.apmP, aw, q.apmH, base, 0);
            q.apmY = Math.max(0, t.top - top);
        }
    }

    private void digital(Parts q, Canvas bgc, List<Readout> all) throws Exception {
        boolean stacked = position == POS_STACKED, big = position == POS_BIG;
        boolean side = (seconds || ampm) && !stacked;
        List<Readout> cols = new ArrayList<>(), top = new ArrayList<>(), bottom = new ArrayList<>();
        if (big) { for (Readout r : all) if (r.type.equals("battery")) bottom.add(r); }   // big time: battery only
        else spread(all, cols, top, bottom);
        Object dateRow = date || weekday ? "date" : null;
        Object topRow = top.isEmpty() ? null : new Line(top), bottomRow = bottom.isEmpty() ? null : new Line(bottom);
        Object colRow = cols.isEmpty() ? null : new Cols(cols);
        List<Object> rows = new ArrayList<>();
        switch (position) {
            case POS_UPPER: addRows(rows, "time", dateRow, colRow, topRow, bottomRow); break;
            case POS_LOWER: addRows(rows, topRow, colRow, dateRow, "time", bottomRow); break;
            case POS_BIG: addRows(rows, dateRow, "time", bottomRow); break;
            case POS_STACKED: addRows(rows, topRow, dateRow, "hour", "minute", colRow, bottomRow); break;
            default: addRows(rows, topRow, dateRow, "time", colRow, bottomRow);
        }
        // The biggest time (up to the chosen size) with every row inside the round screen; spacing gives way first
        int size = stacked ? Math.round(timeSize * 1.25f) : big ? 110 : timeSize;
        int floor = Math.max(30, Math.round(timeSize * 0.7f));   // past this, crowding beats a tiny time
        TimeFit t;
        int[] ys;
        while (true) {
            t = timeFit(size, stacked, side);
            ys = null;
            for (int gap = 14; gap >= 6 && ys == null; gap -= 2) ys = stack(q, rows, t, side, gap, true);
            if (ys != null) break;
            if (size <= floor) { ys = stack(q, rows, t, side, 4, false); break; }
            size -= 2;
        }
        timeGlyphs(q, t, side);
        for (int i = 0; i < rows.size(); i++) {
            Object r = rows.get(i);
            int y = ys[i];
            if ("time".equals(r)) {
                int x = (SIZE - t.w - (side ? 6 + t.sideW : 0)) / 2;
                add(q, item("time", x, y, t.w, t.h, "g1", 11, null, "center"), "10:08");
                if (side && seconds)
                    add(q, item("second", x + t.w + 6, y + q.secY, 2 * q.secW, q.secH, "g6", 10, null, "center"), "32");
                if (side && ampm)
                    add(q, item("apm", x + t.w + 6, y + q.apmY, q.apm[0].getWidth(), q.apmH, "apm", 2, null, "center"), "PM");
            } else if ("hour".equals(r) || "minute".equals(r)) {
                add(q, item((String) r, (SIZE - t.w) / 2, y, t.w, t.h, "g1", 10, null, "center"), "hour".equals(r) ? "10" : "08");
            } else if ("date".equals(r)) {
                placeDate(q, y);
            } else if (r instanceof Line) {
                placeLine(q, bgc, ((Line) r).r, y);
            } else {
                placeCols(q, bgc, ((Cols) r).r, y);
            }
        }
    }

    private static void addRows(List<Object> rows, Object... r) {
        for (Object o : r) if (o != null) rows.add(o);
    }

    /** Up to three readouts go in the row under the time; the others go on single lines above and below it. */
    private static void spread(List<Readout> all, List<Readout> cols, List<Readout> top, List<Readout> bottom) {
        List<Readout> rest = new ArrayList<>(all);
        for (String type : new String[]{"battery", "weather", "distance", "calorie", "heartrate", "step"}) {
            if (rest.size() <= 3) break;
            for (Readout r : rest)
                if (r.type.equals(type)) { rest.remove(r); (top.size() <= bottom.size() ? top : bottom).add(r); break; }
        }
        if (rest.size() == 3)   // the longest number (steps) in the middle, where the screen is widest
            for (Readout r : rest) if (r.type.equals("step")) { rest.remove(r); rest.add(1, r); break; }
        cols.addAll(rest);
    }

    /**
     * Top y of each row, stacked with about `gap` px of visible space between them and centred on the screen.
     * When strict, returns null if the stack is too tall or a row would run past the round edge.
     */
    private int[] stack(Parts q, List<Object> rows, TimeFit t, boolean side, int gap, boolean strict) {
        int n = rows.size(), total = 0;
        int[] h = new int[n], g = new int[n];
        for (int i = 0; i < n; i++) {
            h[i] = rowH(rows.get(i), t);
            if (i > 0) g[i] = Math.max(0, gap - padBelow(q, rows.get(i - 1), t) - padAbove(q, rows.get(i), t));
            total += h[i] + g[i];
        }
        if (strict && total > SIZE - 24) return null;
        int[] ys = new int[n];
        // centre what shows, not the empty edges of the first and last images
        int y = (SIZE - total) / 2 + (padBelow(q, rows.get(n - 1), t) - padAbove(q, rows.get(0), t)) / 2;
        for (int i = 0; i < n; i++) {
            y += g[i];
            ys[i] = y;
            Object r = rows.get(i);
            if (strict && rowW(q, r, t, side) > 2 * room(y + padAbove(q, r, t), y + h[i] - padBelow(q, r, t))) return null;
            y += h[i];
        }
        return ys;
    }

    private static int rowH(Object r, TimeFit t) {
        if (r instanceof Cols) return COL_H;
        if (r instanceof Line || "date".equals(r)) return LINE_H;
        return t.h;
    }

    private static int padAbove(Parts q, Object r, TimeFit t) {
        if (r instanceof Cols) return 1;
        if (r instanceof Line || "date".equals(r)) return q.valPad;
        return t.top;
    }

    private static int padBelow(Parts q, Object r, TimeFit t) {
        if (r instanceof Cols || r instanceof Line || "date".equals(r)) return q.valPad;
        return t.h - t.bottom;
    }

    private int rowW(Parts q, Object r, TimeFit t, boolean side) {
        if ("time".equals(r)) return t.w + (side ? 6 + t.sideW : 0);
        if ("hour".equals(r) || "minute".equals(r)) return t.w;
        if ("date".equals(r)) return dateWidth(q);
        if (r instanceof Line) return lineWidth(q, ((Line) r).r);
        List<Readout> rs = ((Cols) r).r;
        int w = 10 * (rs.size() - 1);
        for (Readout x : rs) w += Math.max(ICON, q.width(x.font, x.longest));
        return w;
    }

    /** Half the width of the round screen at height y. */
    private static float halfChord(float y) {
        float r = SIZE / 2f, dy = Math.abs(y - r);
        return dy >= r ? 0 : (float) Math.sqrt(r * r - dy * dy);
    }

    /** Half the usable width for something between heights y1 and y2. */
    private static float room(float y1, float y2) {
        return Math.min(halfChord(y1), halfChord(y2)) - EDGE;
    }

    private int dateWidth(Parts q) {
        int ww = q.days[0].getWidth(), dw = q.width("g2", "11/21");
        return date && weekday ? ww + 6 + dw : weekday ? ww : dw;
    }

    /** Weekday (accent) then date, as one line of text centred on the screen. */
    private void placeDate(Parts q, int y) throws Exception {
        int ww = q.days[0].getWidth(), dw = evenCeil(q.width("g2", "88/88"));
        if (date && weekday) {
            int x = Math.round((SIZE - dateWidth(q)) / 2f);
            add(q, item("week", x, y, ww, VAL_H, "week", 7, 0, "center"), "FRI");
            add(q, item("date", x + ww + 6, y, dw, VAL_H, "g2", 11, 0, "left"), "11/21");
        } else if (weekday) {
            add(q, item("week", (SIZE - ww) / 2, y, ww, VAL_H, "week", 7, 0, "center"), "FRI");
        } else {
            add(q, item("date", (SIZE - dw) / 2, y, dw, VAL_H, "g2", 11, 0, "center"), "11/21");
        }
    }

    private static int lineWidth(Parts q, List<Readout> rs) {
        int w = 16 * (rs.size() - 1);
        for (Readout r : rs) w += ICON + 4 + q.width(r.font, r.sample);
        return w;
    }

    /** Icon then number, left-aligned so the number always sits right next to its icon. */
    private void placeLine(Parts q, Canvas bgc, List<Readout> rs, int y) throws Exception {
        float x = (SIZE - lineWidth(q, rs)) / 2f;
        for (Readout r : rs) {
            icon(bgc, r.icon, x + ICON / 2f, y + VAL_H / 2f);
            add(q, item(r.type, Math.round(x + ICON + 4), y, evenCeil(q.width(r.font, r.longest)), VAL_H, r.font,
                    r.fontnum, r.style, "left"), r.sample);
            x += ICON + 4 + q.width(r.font, r.sample) + 16;
        }
    }

    /** Readouts side by side, icons above the numbers, with hairlines between them. */
    private void placeCols(Parts q, Canvas bgc, List<Readout> rs, int y) throws Exception {
        int n = rs.size();
        int[] w = new int[n];
        for (int i = 0; i < n; i++) w[i] = Math.max(ICON, evenCeil(q.width(rs.get(i).font, rs.get(i).longest)));
        float step = n == 2 ? 84 : 66;
        if (n > 1) {
            float half = room(y + 1, y + COL_H - q.valPad);
            step = Math.min(step, (half - Math.max(w[0], w[n - 1]) / 2f) * 2 / (n - 1));
            for (int i = 0; i + 1 < n; i++) step = Math.max(step, (w[i] + w[i + 1]) / 2f + 10);
        }
        Paint line = new Paint();
        line.setColor((timeColor & 0x00FFFFFF) | 0x33000000);
        for (int i = 0; i < n; i++) {
            float cx = SIZE / 2f + (i - (n - 1) / 2f) * step;
            placeCol(q, bgc, rs.get(i), cx, y);
            if (i + 1 < n) {
                int dx = Math.round(cx + step / 2f);
                bgc.drawRect(dx, y + 4, dx + 1, y + COL_H - q.valPad, line);
            }
        }
    }

    private void placeCol(Parts q, Canvas bgc, Readout r, float cx, int y) throws Exception {
        int w = evenCeil(q.width(r.font, r.longest));
        icon(bgc, r.icon, cx, y + ICON / 2f);
        add(q, item(r.type, Math.round(cx - w / 2f), y + ICON + 2, w, VAL_H, r.font, r.fontnum, r.style, "center"), r.sample);
    }

    /** Ticks and hands; readouts at 9, 3 and 6 o'clock, the date at 12 and any extras on lines. */
    private void analog(Parts q, Canvas bgc, List<Readout> all) throws Exception {
        dial(bgc);
        int n = Math.min(3, all.size());
        int[][] spots = n == 1 ? new int[][]{{120, 150}} : n == 2 ? new int[][]{{62, 98}, {178, 98}}
                : new int[][]{{62, 98}, {178, 98}, {120, 150}};
        for (int i = 0; i < n; i++) placeCol(q, bgc, all.get(i), spots[i][0], spots[i][1]);
        List<Readout> rest = new ArrayList<>(all.subList(n, all.size()));
        boolean dateRow = date || weekday;
        if (dateRow) placeDate(q, 50);
        else if (!rest.isEmpty()) placeCol(q, bgc, rest.remove(0), 120, 44);   // 12 o'clock
        int[] lines = dateRow ? new int[]{198, 76, 24} : new int[]{198, 20};
        for (int i = 0; i < rest.size() && i < lines.length; i++) placeLine(q, bgc, rest.subList(i, i + 1), lines[i]);

        q.hourHand = hand(HOUR_W, HOUR_H, HOUR_PIV, 7, 5.5f, timeColor);
        q.minHand = hand(MIN_W, MIN_H, MIN_PIV, 5, 4.5f, timeColor);
        q.secHand = secondHand();
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

    private static void add(Parts q, JSONObject o, String sample) { q.items.put(o); q.sample.add(sample); }

    private static JSONObject item(String type, int x, int y, int w, int h, String font, int num, Integer style,
                                   String align) throws Exception {
        JSONObject o = new JSONObject();
        o.put("widget", "custom"); o.put("type", type);
        o.put("x", x); o.put("y", y); o.put("w", w); o.put("h", h);
        o.put("fgcolor", "0xFFFFFFFF"); o.put("fgrender", "0x0"); o.put("align", align);
        if (style != null) o.put("style", style);
        o.put("font", font); o.put("fontnum", num);
        return o;
    }

    // ------------------------------------------------------------------------------------------------ output

    /** What the watch will show (10:08, Fri 11/21, 5367 steps, 72 bpm, 320 kcal, 21°C, 4.19 km, 80%). */
    public Bitmap preview() {
        try {
            Parts q = parts();
            Bitmap b = q.bg.copy(Bitmap.Config.ARGB_8888, true);
            Canvas c = new Canvas(b);
            for (int i = 0; i < q.items.length(); i++) {
                JSONObject o = q.items.getJSONObject(i);
                String t = o.getString("type");
                int x = o.getInt("x"), y = o.getInt("y");
                if (o.getString("widget").equals("watch")) {
                    drawHand(c, q.hourHand, HOUR_PIV, (10 + 8 / 60f) * 30);
                    drawHand(c, q.minHand, MIN_PIV, 8 * 6);
                    drawHand(c, q.secHand, SEC_PIV, 32 * 6);
                    continue;
                }
                if (t.equals("week")) { c.drawBitmap(q.days[4], x, y, null); continue; }
                if (t.equals("apm")) { c.drawBitmap(q.apm[1], x, y, null); continue; }
                Bitmap[] set = q.font(o.getString("font"));
                String text = q.sample.get(i);
                if (!"left".equals(o.optString("align"))) x += (o.getInt("w") - q.width(o.getString("font"), text)) / 2;
                for (char ch : text.toCharArray()) {
                    Bitmap g = set[idx(ch)];
                    c.drawBitmap(g, x, y, null);
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
        List<String> fontNames = new ArrayList<>(Arrays.asList("g2", "g3", "g4", "g5", "week"));
        if (q.time[0] != null) fontNames.add(0, "g1");   // analog faces have no digital time
        if (style == 0 && seconds && position != POS_STACKED) fontNames.add("g6");
        if (style == 0 && ampm && position != POS_STACKED) fontNames.add("apm");
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
        f.add("files0.png", raw(dither565(q.bg)));
        if (style == 1) {
            f.add("files1.png", raw(q.hourHand));
            f.add("files2.png", raw(q.minHand));
            f.add("files3.png", raw(q.secHand));
        }
        f.add("preview.png", raw(Bitmap.createScaledBitmap(preview(), 120, 120, true)));
        if (q.time[0] != null) for (int i = 0; i < 11; i++) f.add("g1_" + i, raw(q.time[i]));
        for (int i = 0; i < 11; i++) f.add("g2_" + i, raw(q.g2[i]));
        for (int i = 0; i < 11; i++) f.add("g3_" + i, raw(q.g3[i]));
        for (int i = 0; i < 13; i++) f.add("g4_" + i, raw(q.g4[i]));
        for (int i = 0; i < 11; i++) f.add("g5_" + i, raw(q.g5[i]));
        if (fontNames.contains("g6")) for (int i = 0; i < 10; i++) f.add("g6_" + i, raw(q.sec[i]));
        if (fontNames.contains("apm")) { f.add("apm_en_am", raw(q.apm[0])); f.add("apm_en_pm", raw(q.apm[1])); }
        for (int i = 0; i < 7; i++) f.add("week_en_" + DAYS[i], raw(q.days[i]));
        return f.build();
    }
}
