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

    static void onSetResult(boolean ok, int operate) {
        FacesActivity a = current;
        if (a == null) return;
        a.runOnUiThread(() -> {
            if (ok) WatchService.sendV3(IdoProtocol.V3_FACE_LIST_NEW, new byte[0]);
            Toast.makeText(a, !ok ? "The watch refused that" : operate == 2 ? "Face removed" : "Watch face changed",
                    Toast.LENGTH_SHORT).show();
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
        TextView sub = Ui.label(this, "Faces installed on your watch. Tap one to show it, long-press to remove it.", 14, false, Ui.subtext(this));
        sub.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 16));
        root.addView(sub);
        list = Ui.card(this, root);
        LinearLayout newRow = new LinearLayout(this);
        Ui.primary(this, newRow, "Design a new face", v -> startActivity(new android.content.Intent(this, FaceDesignerActivity.class)));
        root.addView(newRow, new LinearLayout.LayoutParams(-1, -2));

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.bg(this));
        sv.addView(root);
        setContentView(sv);
        getWindow().setNavigationBarColor(Ui.bg(this));
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
        WatchService.sendV3(IdoProtocol.V3_FACE_LIST_NEW, new byte[0]);
        list.postDelayed(() -> ask(tries + 1), 4000);
    }

    @Override protected void onPause() {
        super.onPause();
        if (current == this) current = null;
    }

    private void fill() {
        list.removeAllViews();
        IdoProtocol.FaceList fl = WatchService.faceList;
        if (fl == null || fl.names.isEmpty()) {
            list.addView(Ui.label(this, fl == null ? "Asking the watch…" : "No faces found.", 16, false, Ui.subtext(this)));
            return;
        }
        TextView info = Ui.label(this, fl.installed + " of " + fl.slots + " slots used · " + fl.free / 1024 + " KB free",
                13, false, Ui.subtext(this));
        info.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 6));
        list.addView(info);
        for (int i = 0; i < fl.names.size(); i++) {
            String f = fl.names.get(i);
            Ui.divider(this, list);
            boolean mine = f.startsWith("wl");
            String title = (mine ? "Your face " + f.replace(".iwf", "").substring(2) : "Face " + f.replace(".iwf", ""))
                    + (f.equals(fl.current) ? "   ✓" : "");
            String sub = f.equals(pending) ? "Sending…" : (fl.sizes.get(i) / 1024) + " KB" + (mine ? " · made in WatchLink" : "");
            TextView r = Ui.row(this, list, title, sub, v -> {
                pending = f;
                WatchService.sendV3(IdoProtocol.V3_FACE_SET, IdoProtocol.faceSet(f));
                fill();
            });
            r.setOnLongClickListener(v -> {
                new android.app.AlertDialog.Builder(this).setTitle("Remove " + title.replace("   ✓", "") + "?")
                        .setMessage(mine ? "You can make it again in the designer."
                                : "This face came with the Momax app. WatchLink can't download it again.")
                        .setPositiveButton("Remove", (d, w) -> {
                            WatchService.sendV3(IdoProtocol.V3_FACE_SET, IdoProtocol.faceDelete(f));
                            list.postDelayed(() -> WatchService.sendV3(IdoProtocol.V3_FACE_LIST_NEW, new byte[0]), 1500);
                            Toast.makeText(this, "Removing…", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", null).show();
                return true;
            });
        }
    }
}
