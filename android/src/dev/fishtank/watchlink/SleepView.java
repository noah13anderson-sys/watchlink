package dev.fishtank.watchlink;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.util.Calendar;

/** One night as a hypnogram: a row per sleep stage (awake at the top, deep at the bottom) across the night. */
final class SleepView extends View {
    /** Rows from the top, and their colours (also used for the legend on the History screen). */
    static final int[] ROW_STAGE = {IdoProtocol.SleepNight.AWAKE, IdoProtocol.SleepNight.REM,
            IdoProtocol.SleepNight.LIGHT, IdoProtocol.SleepNight.DEEP};
    static final String[] ROW_NAME = {"Awake", "REM", "Light", "Deep"};
    static final int[] ROW_COLOR = {0xFFFF8A7A, 0xFF7FDBFF, 0xFF3D8BFF, 0xFF6E5BF0};

    private IdoProtocol.SleepNight night;
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG),
            grid = new Paint(), link = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float dp;
    private final RectF r = new RectF();

    SleepView(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        text.setColor(Ui.subtext(c));
        text.setTextSize(11 * c.getResources().getDisplayMetrics().scaledDensity);
        grid.setColor(0x22FFFFFF);
        grid.setStrokeWidth(Math.max(1, dp * 0.75f));
        link.setColor(0x47FFFFFF);
        link.setStrokeWidth(Math.max(1, dp));
    }

    void set(IdoProtocol.SleepNight n) { night = n; invalidate(); }

    static int colorOf(int stage) {
        for (int i = 0; i < ROW_STAGE.length; i++) if (ROW_STAGE[i] == stage) return ROW_COLOR[i];
        return 0xFF888888;
    }

    private static int rowOf(int stage) {
        for (int i = 0; i < ROW_STAGE.length; i++) if (ROW_STAGE[i] == stage) return i;
        return 0;
    }

    @Override protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), Math.round(150 * dp));
    }

    @Override protected void onDraw(Canvas c) {
        float left = 4 * dp, right = getWidth() - 4 * dp, top = 6 * dp, bottom = getHeight() - 22 * dp;
        float labelW = 0;
        for (String s : ROW_NAME) labelW = Math.max(labelW, text.measureText(s));
        float plotL = left + labelW + 10 * dp, plotR = right, rowH = (bottom - top) / ROW_NAME.length;

        text.setTextAlign(Paint.Align.LEFT);
        for (int i = 0; i < ROW_NAME.length; i++) {
            float y = top + rowH * (i + 0.5f);
            c.drawText(ROW_NAME[i], left, y + text.getTextSize() * 0.35f, text);
            c.drawLine(plotL, y, plotR, y, grid);
        }
        IdoProtocol.SleepNight n = night;
        if (n == null || n.stages.length == 0) return;
        int total = 0;
        for (int m : n.minutes) total += m;
        if (total <= 0) return;

        // stage blocks, joined by thin lines where the stage changes
        float half = rowH * 0.34f, rad = Math.min(3 * dp, half);
        int at = 0;
        float prevX = -1, prevY = 0;
        for (int i = 0; i < n.stages.length; i++) {
            int row = rowOf(n.stages[i]);
            float x0 = plotL + (plotR - plotL) * at / total;
            at += n.minutes[i];
            float x1 = plotL + (plotR - plotL) * at / total, y = top + rowH * (row + 0.5f);
            if (prevX >= 0 && prevY != y) c.drawLine(x0, Math.min(prevY, y), x0, Math.max(prevY, y), link);
            fill.setColor(ROW_COLOR[row]);
            r.set(x0, y - half, Math.max(x1, x0 + dp), y + half);
            c.drawRoundRect(r, rad, rad, fill);
            prevX = x1;
            prevY = y;
        }

        // times: falling asleep, the whole hours in between (where they fit), waking up
        long start = n.start(), end = start + total * 60L;
        float baseline = getHeight() - 5 * dp, gap = 10 * dp;
        String a = time(start, false), b = time(end, false);
        float aEnd = plotL + text.measureText(a), bStart = plotR - text.measureText(b);
        text.setTextAlign(Paint.Align.LEFT);
        c.drawText(a, plotL, baseline, text);
        text.setTextAlign(Paint.Align.RIGHT);
        c.drawText(b, plotR, baseline, text);
        text.setTextAlign(Paint.Align.CENTER);
        float lastEnd = aEnd;
        int step = total > 10 * 60 ? 3 : total > 5 * 60 ? 2 : 1;
        for (long t = (start / 3600 + 1) * 3600; t < end; t += 3600) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(t * 1000);
            if (cal.get(Calendar.MINUTE) != 0 || cal.get(Calendar.HOUR_OF_DAY) % step != 0) continue;
            String s = time(t, true);
            float x = plotL + (plotR - plotL) * (t - start) / (total * 60f), w = text.measureText(s);
            if (x - w / 2 < lastEnd + gap || x + w / 2 > bStart - gap) continue;
            c.drawLine(x, top, x, bottom, grid);
            c.drawText(s, x, baseline, text);
            lastEnd = x + w / 2;
        }
    }

    /** A clock time in the phone's 12/24-hour style; whole hours as "2 am" in 12-hour style. */
    private String time(long t, boolean hour) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(t * 1000);
        int h = cal.get(Calendar.HOUR_OF_DAY), m = cal.get(Calendar.MINUTE);
        if (hour && !android.text.format.DateFormat.is24HourFormat(getContext()))
            return ((h + 11) % 12 + 1) + (h < 12 ? " am" : " pm");
        return AlarmsActivity.show(getContext(), h, m);
    }
}
