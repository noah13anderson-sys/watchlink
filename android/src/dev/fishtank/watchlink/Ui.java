package dev.fishtank.watchlink;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small helpers so screens look consistent without XML resources. */
final class Ui {
    private Ui() {}

    static boolean dark(Context c) {
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    static int bg(Context c)      { return dark(c) ? 0xFF121417 : 0xFFF3F4F7; }
    static int card(Context c)    { return dark(c) ? 0xFF1E2126 : 0xFFFFFFFF; }
    static int text(Context c)    { return dark(c) ? 0xFFECEDEF : 0xFF16181C; }
    static int subtext(Context c) { return dark(c) ? 0xFF9AA0A8 : 0xFF6B7078; }
    static final int ACCENT = 0xFF2E7D6B;   // teal-green
    static final int GOOD = 0xFF2EA043, WARN = 0xFFD29922, BAD = 0xFFCF4B3E;

    static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    static GradientDrawable rounded(int color, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout card(Context c, LinearLayout parent) {
        LinearLayout l = column(c);
        int p = dp(c, 18);
        l.setPadding(p, p, p, p);
        l.setBackground(rounded(card(c), dp(c, 20)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(c, 14);
        parent.addView(l, lp);
        return l;
    }

    static TextView label(Context c, String s, float sp, boolean bold, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    static TextView section(Context c, LinearLayout parent, String s) {
        TextView t = label(c, s.toUpperCase(), 13, true, subtext(c));
        t.setLetterSpacing(0.08f);
        t.setPadding(dp(c, 6), dp(c, 10), 0, dp(c, 8));
        parent.addView(t);
        return t;
    }

    /** A full-width row button inside a card: title + optional subtitle. */
    static TextView row(Context c, LinearLayout card, String title, String sub, View.OnClickListener cl) {
        TextView t = label(c, title, 17, false, text(c));
        if (sub != null) {
            t.setText(title + "\n");
            t.append(sub);
            // make the subtitle smaller and grey
            android.text.SpannableString ss = new android.text.SpannableString(t.getText());
            ss.setSpan(new android.text.style.RelativeSizeSpan(0.8f), title.length() + 1, ss.length(), 0);
            ss.setSpan(new android.text.style.ForegroundColorSpan(subtext(c)), title.length() + 1, ss.length(), 0);
            t.setText(ss);
        }
        int p = dp(c, 12);
        t.setPadding(dp(c, 4), p, dp(c, 4), p);
        t.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22888888), null, rounded(Color.WHITE, dp(c, 12))));
        t.setOnClickListener(cl);
        card.addView(t, new LinearLayout.LayoutParams(-1, -2));
        return t;
    }

    /** Set a row's title + grey subtitle (same style as row()). */
    static void setRow(Context c, TextView t, String title, String sub) {
        android.text.SpannableString ss = new android.text.SpannableString(title + "\n" + sub);
        ss.setSpan(new android.text.style.RelativeSizeSpan(0.8f), title.length() + 1, ss.length(), 0);
        ss.setSpan(new android.text.style.ForegroundColorSpan(subtext(c)), title.length() + 1, ss.length(), 0);
        t.setText(ss);
    }

    /** Prominent filled button. */
    static TextView primary(Context c, LinearLayout parent, String s, View.OnClickListener cl) {
        TextView b = label(c, s, 16, true, Color.WHITE);
        b.setGravity(Gravity.CENTER);
        int p = dp(c, 14);
        b.setPadding(p, p, p, p);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), rounded(ACCENT, dp(c, 14)), null));
        b.setOnClickListener(cl);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(dp(c, 4), 0, dp(c, 4), 0);
        parent.addView(b, lp);
        return b;
    }

    static View divider(Context c, LinearLayout parent) {
        View v = new View(c);
        v.setBackgroundColor(dark(c) ? 0x22FFFFFF : 0x14000000);
        parent.addView(v, new LinearLayout.LayoutParams(-1, 1));
        return v;
    }
}
