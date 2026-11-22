package dev.fishtank.watchlink;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import java.util.Locale;

/**
 * Small charts for the History screen: readings across a day (line or dots, x = hours 0..24) or one bar per slot
 * (hours of a day, or days). Drawn in code to match the rest of the app.
 */
final class ChartView extends View {
    static final int LINE = 0, DOTS = 1, BARS = 2;

    private int mode = LINE, color = Ui.ACCENT;
    private float[] xs = new float[0], ys = new float[0];
    private float xMin, xMax = 24, yMin, yMax = 1, goal = -1, maxGap = Float.MAX_VALUE;
    private String[] xLabels = new String[0];
    private float[] xLabelAt = new float[0];
    /** For bars: the slot to highlight (e.g. today), -1 for none. */
    private int highlight = -1;
    private String empty = "No data";

    private final Paint grid = new Paint(), text = new Paint(Paint.ANTI_ALIAS_FLAG), ink = new Paint(Paint.ANTI_ALIAS_FLAG),
            fill = new Paint(Paint.ANTI_ALIAS_FLAG), dash = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float dp;

    ChartView(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        grid.setColor(0x22FFFFFF);
        grid.setStrokeWidth(Math.max(1, dp * 0.75f));
        text.setColor(Ui.subtext(c));
        text.setTextSize(11 * c.getResources().getDisplayMetrics().scaledDensity);
        ink.setStrokeWidth(2 * dp);
        ink.setStyle(Paint.Style.STROKE);
        ink.setStrokeCap(Paint.Cap.ROUND);
        ink.setStrokeJoin(Paint.Join.ROUND);
        dash.setStyle(Paint.Style.STROKE);
        dash.setStrokeWidth(1.5f * dp);
        dash.setPathEffect(new DashPathEffect(new float[]{5 * dp, 4 * dp}, 0));
    }

    /** Readings across a day: x in hours (0..24). Lines break across gaps longer than maxGapHours. */
    void day(int mode, float[] hours, float[] values, int color, float floor, float ceil, float maxGapHours) {
        this.mode = mode; this.xs = hours; this.ys = values; this.color = color;
        xMin = 0; xMax = 24; maxGap = maxGapHours; goal = -1; highlight = -1;
        xLabels = android.text.format.DateFormat.is24HourFormat(getContext())
                ? new String[]{"0:00", "6:00", "12:00", "18:00", "24:00"}
                : new String[]{"12 am", "6 am", "12 pm", "6 pm", "12 am"};
        xLabelAt = new float[]{0, 6, 12, 18, 24};
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        for (float v : values) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
        if (values.length == 0 || fixedRange) { lo = floor; hi = ceil; }
        float pad = fixedRange ? 0 : Math.max(2, (hi - lo) * 0.12f);
        range(Math.max(floor, lo - pad), Math.min(ceil, hi + pad), floor, ceil);
        invalidate();
    }

    private boolean fixedRange;

    /** Use exactly floor..ceil for the next day() call (e.g. 0..100% for the battery). */
    ChartView fixed() { fixedRange = true; return this; }

    /** y range on multiples of 10, with a round number in the middle for the centre grid line. */
    private void range(float lo, float hi, float floor, float ceil) {
        yMin = (float) Math.floor(lo / 10) * 10;
        yMax = (float) Math.ceil(hi / 10) * 10;
        if (yMax <= yMin) yMax = yMin + 20;
        if ((yMax - yMin) % 20 != 0) {
            if (yMax + 10 <= ceil) yMax += 10; else yMin = Math.max(floor - 10, yMin - 10);
        }
    }

    /** Like bars(), but the axis starts near the smallest value (for things like heart rate) and shows values. */
    void barsFrom(String[] labels, float[] values, int color, int highlight) {
        bars(labels, values, color, -1, highlight);
        float lo = Float.MAX_VALUE, hi = 0;
        for (float v : values) if (v > 0) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
        if (hi > 0) range(lo - 10, hi + 5, 0, 1000);
        showValues = true;
        invalidate();
    }

    private boolean showValues, hours;

    /** Values are hours (sleep): the axis shows "8h", bars "7h32". */
    ChartView hours() { hours = true; return this; }

    /** One bar per slot with a label under each (labels may be blank). */
    void bars(String[] labels, float[] values, int color, float goal, int highlight) {
        showValues = labels.length <= 7;
        this.mode = BARS; this.color = color; this.goal = goal; this.highlight = highlight;
        int n = values.length;
        xs = new float[n]; ys = values;
        for (int i = 0; i < n; i++) xs[i] = i;
        xMin = -0.5f; xMax = n - 0.5f;
        xLabels = labels;
        xLabelAt = new float[n];
        for (int i = 0; i < n; i++) xLabelAt[i] = i;
        float hi = Math.max(goal, 0);
        for (float v : values) hi = Math.max(hi, v);
        yMin = 0;
        yMax = hi <= 0 ? 1 : niceCeil(hi * 1.1f);
        invalidate();
    }

    void setEmptyText(String s) { empty = s; invalidate(); }

    private static float niceCeil(float v) {
        double p = Math.pow(10, Math.floor(Math.log10(v)));
        for (double m : new double[]{1, 2, 2.5, 5, 10}) if (m * p >= v) return (float) (m * p);
        return (float) (10 * p);
    }

    @Override protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), Math.round(150 * dp));
    }

    private String fmt(float v) {
        if (hours) return v == Math.floor(v) ? (int) v + "h" : String.format(Locale.ROOT, "%.1fh", v);
        if (Math.abs(v) >= 10000) return Math.round(v / 1000) + "k";
        if (Math.abs(v) >= 1000) return String.format("%.1fk", v / 1000).replace(".0k", "k");
        return String.valueOf(Math.round(v));
    }

    private String barLabel(float v) {
        if (!hours) return fmt(v);
        int m = Math.round(v * 60);
        return m % 60 == 0 ? m / 60 + "h" : String.format(Locale.ROOT, "%dh%02d", m / 60, m % 60);
    }

    @Override protected void onDraw(Canvas c) {
        float left = 4 * dp, right = getWidth() - 4 * dp, top = 8 * dp, bottom = getHeight() - 20 * dp;
        float labelW = text.measureText("0000") + 6 * dp;
        float plotL = left + labelW, plotR = right;
        // grid with values
        text.setTextAlign(Paint.Align.LEFT);
        for (int i = 0; i <= 2; i++) {
            float v = yMin + (yMax - yMin) * i / 2f, y = yOf(v, top, bottom);
            c.drawLine(plotL, y, plotR, y, grid);
            c.drawText(fmt(v), left, y + text.getTextSize() * 0.35f, text);
        }
        // x labels
        text.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < xLabels.length; i++) {
            if (xLabels[i] == null || xLabels[i].isEmpty()) continue;
            float x = xOf(xLabelAt[i], plotL, plotR);
            if (mode != BARS) {
                if (i == 0) text.setTextAlign(Paint.Align.LEFT);
                else if (i == xLabels.length - 1) text.setTextAlign(Paint.Align.RIGHT);
                else text.setTextAlign(Paint.Align.CENTER);
            }
            c.drawText(xLabels[i], x, getHeight() - 5 * dp, text);
        }
        boolean any = false;
        for (float v : ys) if (v > 0 || mode != BARS) { any = true; break; }
        if (ys.length == 0 || !any) {
            text.setTextAlign(Paint.Align.CENTER);
            c.drawText(empty, (plotL + plotR) / 2, (top + bottom) / 2, text);
            return;
        }
        ink.setColor(color);
        fill.setColor(color);
        if (mode == BARS) {
            float slot = (plotR - plotL) / (xMax - xMin), bw = Math.min(slot * 0.62f, 28 * dp);
            for (int i = 0; i < ys.length; i++) {
                if (ys[i] <= 0) continue;
                float x = xOf(xs[i], plotL, plotR), y = yOf(ys[i], top, bottom);
                fill.setColor(highlight < 0 || i == highlight ? color : (color & 0x00FFFFFF) | 0x8C000000);
                float r = Math.min(bw / 2, 4 * dp);
                Path p = new Path();
                p.addRoundRect(new RectF(x - bw / 2, y, x + bw / 2, bottom), new float[]{r, r, r, r, 0, 0, 0, 0}, Path.Direction.CW);
                c.drawPath(p, fill);
                if (showValues) {
                    text.setTextAlign(Paint.Align.CENTER);
                    c.drawText(barLabel(ys[i]), x, Math.max(top + text.getTextSize(), y - 4 * dp), text);
                }
            }
            if (goal > 0) {
                dash.setColor(0x99FFFFFF);
                float y = yOf(goal, top, bottom);
                c.drawLine(plotL, y, plotR, y, dash);
            }
            return;
        }
        if (mode == DOTS) {
            for (int i = 0; i < xs.length; i++)
                c.drawCircle(xOf(xs[i], plotL, plotR), yOf(ys[i], top, bottom), 3 * dp, fill);
            return;
        }
        // line, broken across gaps, with a soft fill under each piece
        Path line = new Path(), area = new Path();
        float startX = 0, prevX = 0;
        boolean open = false;
        for (int i = 0; i < xs.length; i++) {
            float x = xOf(xs[i], plotL, plotR), y = yOf(ys[i], top, bottom);
            if (open && xs[i] - xs[i - 1] > maxGap) {
                area.lineTo(prevX, bottom); area.lineTo(startX, bottom); area.close();
                open = false;
            }
            if (!open) {
                line.moveTo(x, y);
                area.moveTo(x, y);
                startX = x;
                open = true;
                if (i + 1 >= xs.length || xs[i + 1] - xs[i] > maxGap) c.drawCircle(x, y, 2.5f * dp, fill);
            } else {
                line.lineTo(x, y);
                area.lineTo(x, y);
            }
            prevX = x;
        }
        if (open) { area.lineTo(prevX, bottom); area.lineTo(startX, bottom); area.close(); }
        Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
        shade.setShader(new LinearGradient(0, top, 0, bottom, (color & 0x00FFFFFF) | 0x55000000,
                color & 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawPath(area, shade);
        c.drawPath(line, ink);
    }

    private float xOf(float x, float l, float r) { return l + (x - xMin) / (xMax - xMin) * (r - l); }

    private float yOf(float v, float top, float bottom) {
        return bottom - (Math.max(yMin, Math.min(yMax, v)) - yMin) / (yMax - yMin) * (bottom - top);
    }
}
