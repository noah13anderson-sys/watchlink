package dev.fishtank.watchlink;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Pick which of the faces installed on the watch is shown. */
public class FacesActivity extends Activity {
    private static FacesActivity current;
    private LinearLayout list;
    private SharedPreferences p;
    private String pending;

    private volatile boolean answered;

    static void refresh() {
        FacesActivity a = current;
        if (a != null) { a.answered = true; a.runOnUiThread(a::fill); }
    }

    static void onSetResult(boolean ok) {
        FacesActivity a = current;
        if (a == null) return;
        a.runOnUiThread(() -> {
            if (ok && a.pending != null) a.p.edit().putString("face", a.pending).apply();
            Toast.makeText(a, ok ? "Watch face changed" : "The watch refused that face", Toast.LENGTH_SHORT).show();
            a.pending = null;
            a.fill();
        });
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (getActionBar() != null) getActionBar().hide();
        getWindow().setStatusBarColor(Ui.bg(this));
        p = getSharedPreferences(WatchService.PREFS, 0);

        LinearLayout root = Ui.column(this);
        int pad = Ui.dp(this, 16);
        root.setPadding(pad, Ui.dp(this, 28), pad, pad);
        root.setBackgroundColor(Ui.bg(this));
        root.addView(Ui.label(this, "Watch faces", 28, true, Ui.text(this)));
        TextView sub = Ui.label(this, "Faces installed on your watch. Tap one to show it.", 14, false, Ui.subtext(this));
        sub.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 16));
        root.addView(sub);
        list = Ui.card(this, root);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.bg(this));
        sv.addView(root);
        setContentView(sv);
    }

    @Override protected void onResume() {
        super.onResume();
        current = this;
        fill();
        ask(0);
    }

    /** The watch ignores requests while it is streaming health data, so ask again until it answers. */
    private void ask(int tries) {
        if (current != this || tries > 6) return;
        if (tries > 0 && answered) return;
        WatchService.sendV3(IdoProtocol.V3_FACE_LIST, new byte[0]);
        list.postDelayed(() -> ask(tries + 1), 4000);
    }

    @Override protected void onPause() {
        super.onPause();
        if (current == this) current = null;
    }

    private void fill() {
        list.removeAllViews();
        List<String> faces = WatchService.faces;
        if (faces == null || faces.isEmpty()) {
            list.addView(Ui.label(this, faces == null ? "Asking the watch…" : "No faces found.", 16, false, Ui.subtext(this)));
            return;
        }
        String cur = p.getString("face", null);
        for (int i = 0; i < faces.size(); i++) {
            String f = faces.get(i);
            if (i > 0) Ui.divider(this, list);
            String title = "Face " + (i + 1) + (f.equals(cur) ? "   ✓" : "");
            String sub = f.equals(pending) ? "Sending…" : f.replace(".iwf", "");
            Ui.row(this, list, title, sub, v -> {
                pending = f;
                WatchService.sendV3(IdoProtocol.V3_FACE_SET, IdoProtocol.faceSet(f));
                fill();
            });
        }
    }
}
