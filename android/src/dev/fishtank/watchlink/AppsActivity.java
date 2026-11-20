package dev.fishtank.watchlink;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Choose which apps' notifications go to the watch. All apps are on by default. */
public class AppsActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        SharedPreferences p = getSharedPreferences(WatchService.PREFS, 0);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(40, 80, 40, 40);

        TextView t = new TextView(this);
        t.setText("Send notifications from:");
        t.setTextSize(22);
        l.addView(t);

        PackageManager pm = getPackageManager();
        List<ResolveInfo> apps = pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
        apps.sort((a, c) -> a.loadLabel(pm).toString().compareToIgnoreCase(c.loadLabel(pm).toString()));
        for (ResolveInfo ri : apps) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            CheckBox cb = new CheckBox(this);
            cb.setText(ri.loadLabel(pm));
            cb.setChecked(p.getBoolean("app:" + pkg, true));
            cb.setOnCheckedChangeListener((v, c) -> p.edit().putBoolean("app:" + pkg, c).apply());
            l.addView(cb);
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(l);
        sv.setBackgroundColor(Ui.bg(this));
        setContentView(sv);
        Ui.style(this, sv);
    }
}
