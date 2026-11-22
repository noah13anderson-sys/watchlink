package dev.fishtank.watchlink;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Which apps' notifications go to the watch (all on by default), and when to keep quiet. */
public class AppsActivity extends Activity {
    private SharedPreferences p;
    private LinearLayout known, others;
    private TextView knownTitle, othersTitle, noMatch;
    private final List<View> rows = new ArrayList<>();
    private final List<String> labels = new ArrayList<>();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        p = getSharedPreferences(WatchService.PREFS, 0);
        LinearLayout root = Ui.column(this);
        int pad = Ui.dp(this, 16);
        root.setPadding(pad, Ui.dp(this, 28), pad, pad);
        root.addView(Ui.label(this, "Notifications", 28, true, Ui.text(this)));
        TextView sub = Ui.label(this, "Choose which apps buzz your watch.", 14, false, Ui.subtext(this));
        sub.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 16));
        root.addView(sub);

        Ui.section(this, root, "When to stay quiet");
        LinearLayout rules = Ui.card(this, root);
        toggle(rules, "Skip silent notifications", "Ones your phone shows without a sound or buzz", "skipSilent", true);
        toggle(rules, "Follow the phone's Do not disturb", "Hold back what Do not disturb holds back on the phone", "followDnd", true);
        toggle(rules, "App icons on the watch", "WhatsApp, Gmail, Telegram… show their own icon and just the sender", "appIcons", true);

        EditText search = new EditText(this);
        search.setHint("Search apps");
        search.setSingleLine(true);
        search.setTextColor(Ui.text(this));
        search.setHintTextColor(Ui.subtext(this));
        search.setBackground(Ui.rounded(Ui.card(this), Ui.dp(this, 14)));
        int sp = Ui.dp(this, 14);
        search.setPadding(sp, sp, sp, sp);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.setMargins(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        root.addView(search, slp);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) {}
            @Override public void afterTextChanged(Editable s) { filter(s.toString()); }
        });

        knownTitle = Ui.section(this, root, "Apps with their own icon");
        known = Ui.card(this, root);
        knownTitle.setVisibility(View.GONE);   // shown once the apps have loaded
        known.setVisibility(View.GONE);
        othersTitle = Ui.section(this, root, "Other apps");
        others = Ui.card(this, root);
        TextView loading = Ui.label(this, "Loading apps…", 15, false, Ui.subtext(this));
        others.addView(loading);
        noMatch = Ui.label(this, "No apps match", 15, false, Ui.subtext(this));
        noMatch.setPadding(Ui.dp(this, 4), Ui.dp(this, 12), 0, 0);
        noMatch.setVisibility(View.GONE);
        root.addView(noMatch);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.bg(this));
        sv.addView(root);
        setContentView(sv);
        Ui.style(this, sv);
        new Thread(this::loadApps).start();
    }

    private static final class App { String pkg, label; Drawable icon; boolean known; }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        List<ResolveInfo> ris = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
        List<App> apps = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : ris) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
            App a = new App();
            a.pkg = pkg;
            a.label = ri.loadLabel(pm).toString();
            a.icon = ri.loadIcon(pm);
            a.known = IdoProtocol.knownAppType(pkg) != 0;
            apps.add(a);
        }
        apps.sort((x, y) -> x.label.compareToIgnoreCase(y.label));
        runOnUiThread(() -> show(apps));
    }

    private void show(List<App> apps) {
        others.removeAllViews();
        for (App a : apps) {
            LinearLayout card = a.known ? known : others;
            if (card.getChildCount() > 0) Ui.divider(this, card);
            View row = appRow(card, a);
            rows.add(row);
            labels.add(a.label.toLowerCase(Locale.ROOT));
        }
        knownTitle.setVisibility(known.getChildCount() == 0 ? View.GONE : View.VISIBLE);
        known.setVisibility(known.getChildCount() == 0 ? View.GONE : View.VISIBLE);
    }

    private View appRow(LinearLayout card, App a) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 2), Ui.dp(this, 8), 0, Ui.dp(this, 8));
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(a.icon);
        int s = Ui.dp(this, 36);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(s, s);
        ilp.rightMargin = Ui.dp(this, 14);
        row.addView(iv, ilp);
        row.addView(Ui.label(this, a.label, 16, false, Ui.text(this)), new LinearLayout.LayoutParams(0, -2, 1));
        Switch sw = new Switch(this);
        tint(sw);
        sw.setChecked(p.getBoolean("app:" + a.pkg, true));
        sw.setOnCheckedChangeListener((v, c) -> p.edit().putBoolean("app:" + a.pkg, c).apply());
        row.addView(sw);
        row.setOnClickListener(v -> sw.toggle());
        card.addView(row);
        return row;
    }

    /** Show only apps whose name contains the search text (dividers follow their rows); sections with no match hide. */
    private void filter(String q) {
        String s = q.trim().toLowerCase(Locale.ROOT);
        int knownShown = 0, othersShown = 0;
        for (int i = 0; i < rows.size(); i++) {
            View row = rows.get(i);
            boolean show = s.isEmpty() || labels.get(i).contains(s);
            row.setVisibility(show ? View.VISIBLE : View.GONE);
            LinearLayout card = (LinearLayout) row.getParent();
            if (show) { if (card == known) knownShown++; else othersShown++; }
            int at = card.indexOfChild(row);
            if (at > 0) card.getChildAt(at - 1).setVisibility(s.isEmpty() ? View.VISIBLE : View.GONE);   // no dividers while searching
        }
        int k = knownShown > 0 ? View.VISIBLE : View.GONE, o = othersShown > 0 ? View.VISIBLE : View.GONE;
        knownTitle.setVisibility(k);
        known.setVisibility(k);
        othersTitle.setVisibility(o);
        others.setVisibility(o);
        noMatch.setVisibility(knownShown + othersShown == 0 && !rows.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void tint(Switch sw) {
        ColorStateList t = new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{Ui.ACCENT, Ui.subtext(this)});
        sw.setThumbTintList(t);
        sw.setTrackTintList(t);
    }

    private void toggle(LinearLayout card, String title, String sub, String key, boolean def) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 4), Ui.dp(this, 8), 0, Ui.dp(this, 8));
        LinearLayout text = Ui.column(this);
        text.addView(Ui.label(this, title, 17, false, Ui.text(this)));
        text.addView(Ui.label(this, sub, 13, false, Ui.subtext(this)));
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        Switch sw = new Switch(this);
        tint(sw);
        sw.setChecked(p.getBoolean(key, def));
        sw.setOnCheckedChangeListener((v, c) -> p.edit().putBoolean(key, c).apply());
        row.addView(sw);
        card.addView(row);
    }
}
