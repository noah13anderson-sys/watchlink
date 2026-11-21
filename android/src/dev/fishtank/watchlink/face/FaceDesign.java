package dev.fishtank.watchlink.face;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * A custom watch face for the 240x240 SW2S: background (gradient preset or photo), big digital time, and
 * optional date, weekday, steps and battery. Turns itself into an IDO .iwf file using the same layout
 * format as the cloud faces in the Momax app ("custom" widgets drawing digits from small images).
 */
public final class FaceDesign {
    public static final int SIZE = 240;

    public static final String[] BACKGROUNDS = {"Teal", "Sunset", "Night", "Forest", "Berry", "Black", "Photo"};
    private static final int[][] GRADIENTS = {
            {0xFF00A896, 0xFF02344F}, {0xFFFF7E5F, 0xFF4B1248}, {0xFF1E2233, 0xFF05060B},
            {0xFF3A7D44, 0xFF0B2414}, {0xFFB0306A, 0xFF2A0A3A}, {0xFF000000, 0xFF000000}};

    public static final String[] FONTS = {"Bold", "Light", "Serif", "Mono", "Condensed"};
    public static final int[] COLORS = {0xFFFFFFFF, 0xFF00D3B8, 0xFFFFD166, 0xFFFF6A00, 0xFFED254E, 0xFF71F79F, 0xFF8AB4F8};
    public static final String[] POSITIONS = {"Middle", "Upper", "Lower"};

    public int background, font, timeColor = 0xFFFFFFFF, accentColor = 0xFF00D3B8, position, timeSize = 64;
    public boolean date = true, weekday = true, steps = true, battery = true;
    /** Optional photo background, already cropped to 240x240. */
    public Bitmap photo;
    /** Darken the photo so the time stays readable (0..200). */
    public int photoDim = 90;

    private static final String[] DAYS = {"mon", "tue", "wed", "thur", "fri", "sat", "sun"};

    private Typeface typeface() {
        switch (font) {
            case 1: return Typeface.create("sans-serif-light", Typeface.NORMAL);
            case 2: return Typeface.create(Typeface.SERIF, Typeface.BOLD);
            case 3: return Typeface.create(Typeface.MONOSPACE, Typeface.BOLD);
            case 4: return Typeface.create("sans-serif-condensed", Typeface.BOLD);
            default: return Typeface.create("sans-serif", Typeface.BOLD);
        }
    }

    public Bitmap background() {
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

    /** One glyph image; digits all get the same width so the time doesn't jump around. */
    private static Bitmap glyph(String s, Paint p, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Paint.FontMetrics fm = p.getFontMetrics();
        float base = h / 2f - (fm.ascent + fm.descent) / 2f;
        new Canvas(b).drawText(s, w / 2f, base, p);
        return b;
    }

    private Paint paint(Typeface tf, float size, int color) {
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

    /** Everything the face needs, in one place, so the preview and the watch file match. */
    private final class Parts {
        Bitmap bg;
        Bitmap[] time = new Bitmap[11], small = new Bitmap[11], batt = new Bitmap[11];
        Bitmap[] days = new Bitmap[7];
        int timeY, timeW, timeH, smallH, dw, sw;
        int dateY, stepY, battY;
        JSONArray items = new JSONArray();
    }

    private Parts parts() throws Exception {
        Parts q = new Parts();
        q.bg = background();
        Typeface tf = typeface();
        Paint tp = paint(tf, timeSize, timeColor);
        q.dw = digitWidth(tp);
        q.timeH = (int) Math.ceil(timeSize * 1.1f);
        int colonW = (int) Math.ceil(tp.measureText(":")) + 4;
        for (int i = 0; i < 10; i++) q.time[i] = glyph(String.valueOf(i), tp, q.dw, q.timeH);
        q.time[10] = glyph(":", tp, colonW, q.timeH);
        q.timeW = 4 * q.dw + colonW;

        Paint sp = paint(Typeface.create("sans-serif-medium", Typeface.NORMAL), 20, accentColor);
        q.sw = digitWidth(sp);
        q.smallH = 26;
        for (int i = 0; i < 10; i++) {
            q.small[i] = glyph(String.valueOf(i), sp, q.sw, q.smallH);
            q.batt[i] = q.small[i];
        }
        q.small[10] = glyph("/", sp, (int) Math.ceil(sp.measureText("/")) + 2, q.smallH);
        q.batt[10] = glyph("%", sp, (int) Math.ceil(sp.measureText("%")) + 2, q.smallH);
        Paint wp = paint(Typeface.create("sans-serif-medium", Typeface.BOLD), 18, timeColor);
        for (int i = 0; i < 7; i++) q.days[i] = glyph(DAYS[i].substring(0, 3).toUpperCase(), wp, 50, q.smallH);

        // Vertical layout (the screen is 240 px; keep things away from the corners)
        if (position == 1) {            // Upper: time, date row, steps, battery
            q.timeY = 40; q.dateY = q.timeY + q.timeH + 6; q.stepY = q.dateY + 34; q.battY = 196;
        } else if (position == 2) {     // Lower: steps, date row, time, battery
            q.stepY = 32; q.dateY = 66; q.timeY = 100; q.battY = 196;
        } else {                        // Middle: steps, time, date row, battery
            q.stepY = 36; q.timeY = 120 - q.timeH / 2 - 8; q.dateY = q.timeY + q.timeH + 6; q.battY = 196;
        }

        q.items.put(item("time", (SIZE - q.timeW) / 2, q.timeY, q.timeW, q.timeH, "g1", 11, null));
        int dateW = 5 * q.sw + 4;
        boolean both = date && weekday;
        if (date) q.items.put(item("date", both ? SIZE / 2 - dateW - 4 : (SIZE - dateW) / 2, q.dateY, dateW, q.smallH, "g2", 11, 0));
        if (weekday) q.items.put(item("week", both ? SIZE / 2 + 8 : (SIZE - 50) / 2, q.dateY, 50, q.smallH, "week", 7, 0));
        if (steps) q.items.put(item("step", (SIZE - 6 * q.sw) / 2, q.stepY, 6 * q.sw, q.smallH, "g2", 10, null));
        if (battery) q.items.put(item("battery", (SIZE - 4 * q.sw) / 2, q.battY, 4 * q.sw, q.smallH, "g3", 11, null));
        return q;
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

    /** What the watch will show (10:08, 11/21, FRI, 5367 steps, 80%). */
    public Bitmap preview() {
        try {
            Parts q = parts();
            Bitmap b = q.bg.copy(Bitmap.Config.ARGB_8888, true);
            Canvas c = new Canvas(b);
            for (int i = 0; i < q.items.length(); i++) {
                JSONObject o = q.items.getJSONObject(i);
                String t = o.getString("type");
                Bitmap[] set; String text;
                switch (t) {
                    case "time": set = q.time; text = "10:08"; break;
                    case "date": set = q.small; text = "11/21"; break;
                    case "step": set = q.small; text = "5367"; break;
                    case "battery": set = q.batt; text = "80%"; break;
                    default: c.drawBitmap(q.days[4], o.getInt("x"), o.getInt("y"), null); continue;
                }
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
            return background();
        }
    }

    private static int idx(char ch) { return Character.isDigit(ch) ? ch - '0' : 10; }

    private static int[] pixels(Bitmap b) {
        int[] px = new int[b.getWidth() * b.getHeight()];
        b.getPixels(px, 0, b.getWidth(), 0, 0, b.getWidth(), b.getHeight());
        return px;
    }

    private static byte[] raw(Bitmap b) { return IwfFile.raw565(pixels(b), b.getWidth(), b.getHeight()); }

    /** The uncompressed .iwf file. */
    public byte[] build(String name) throws Exception {
        Parts q = parts();
        JSONObject iwf = new JSONObject();
        iwf.put("version", 1); iwf.put("clouddialversion", 3); iwf.put("preview", "preview.png");
        iwf.put("name", name); iwf.put("author", "WatchLink"); iwf.put("description", "SW2S");
        iwf.put("deviceId", "SW2S"); iwf.put("bluetooth", false); iwf.put("disturb", false);
        iwf.put("battery", false); iwf.put("compress", "LZ4");
        iwf.put("item", q.items); iwf.put("bkground", "files0.png");
        JSONObject fonts = new JSONObject();
        JSONArray fl = new JSONArray();
        for (String n : new String[]{"g1", "g2", "g3", "week"}) {
            JSONObject f = new JSONObject();
            f.put("name", n); f.put("bpp", 16); f.put("format", "png");
            fl.put(f);
        }
        fonts.put("item", fl);

        IwfFile f = new IwfFile();
        f.addText("iwf.json", iwf.toString());
        f.addText("font.json", fonts.toString());
        f.add("files0.png", raw(q.bg));
        f.add("preview.png", raw(Bitmap.createScaledBitmap(preview(), 120, 120, true)));
        for (int i = 0; i < 11; i++) f.add("g1_" + i, raw(q.time[i]));
        for (int i = 0; i < 11; i++) f.add("g2_" + i, raw(q.small[i]));
        for (int i = 0; i < 11; i++) f.add("g3_" + i, raw(q.batt[i]));
        for (int i = 0; i < 7; i++) f.add("week_en_" + DAYS[i], raw(q.days[i]));
        return f.build();
    }
}
