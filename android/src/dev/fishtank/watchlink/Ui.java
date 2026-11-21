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

    /** WatchLink always uses the dark Sweet-Mars palette (matches the KDE desktop theme). */
    static boolean dark(Context c) { return true; }

    // Sweet-Mars (KDE) colours
    static int bg(Context c)      { return 0xFF181B28; }   // Window background 24,27,40
    static int card(Context c)    { return 0xFF1E2233; }   // Button/View alternate 30,34,51
    static int text(Context c)    { return 0xFFD3DAE3; }   // Foreground 211,218,227
    static int subtext(Context c) { return 0xFF8A909E; }   // a little brighter than KDE's inactive grey, for contrast
    static final int ACCENT = 0xFF00D3B8;                   // teal 0,211,184
    static final int ON_ACCENT = 0xFF161925;                // dark text on teal buttons
    static final int GOOD = 0xFF71F79F, WARN = 0xFFFF6A00, BAD = 0xFFED254E;

    /** Colour an activity's window and system bars, and give check boxes the accent colour. */
    static void style(android.app.Activity a, View root) {
        a.getWindow().setStatusBarColor(bg(a));
        a.getWindow().setNavigationBarColor(bg(a));
        if (a.getActionBar() != null) a.getActionBar().hide();
        tint(root);
    }

    private static void tint(View v) {
        if (v instanceof android.widget.CompoundButton) {
            ((android.widget.CompoundButton) v).setButtonTintList(ColorStateList.valueOf(ACCENT));
            ((TextView) v).setTextColor(0xFFD3DAE3);
        } else if (v instanceof android.widget.Button) {
            ((TextView) v).setTextColor(ON_ACCENT);
            v.setBackground(rounded(ACCENT, v.getResources().getDisplayMetrics().density * 12));
        } else if (v instanceof android.widget.EditText) ((TextView) v).setTextColor(0xFFD3DAE3);
        // plain labels keep the colour they were made with (titles light, subtitles grey)
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) tint(g.getChildAt(i));
        }
    }

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
        t.setBackground(new RippleDrawable(ColorStateList.valueOf(0x3300D3B8), null, rounded(Color.WHITE, dp(c, 12))));
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
        TextView b = label(c, s, 16, true, ON_ACCENT);
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
