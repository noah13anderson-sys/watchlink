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
    private static final int IMPORT = 7;

    @Override protected void onActivityResult(int req, int res, android.content.Intent data) {
        if (req != IMPORT || res != RESULT_OK || data == null || data.getData() == null) return;
        Toast.makeText(this, "Reading the face…", Toast.LENGTH_SHORT).show();
        android.net.Uri uri = data.getData();
        new Thread(() -> {
            try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
                dev.fishtank.watchlink.face.FaceImport f = dev.fishtank.watchlink.face.FaceImport.read(in);
                runOnUiThread(() -> confirmImport(f));
            } catch (Exception e) {
                runOnUiThread(() -> new android.app.AlertDialog.Builder(this).setTitle("Can't use this file")
                        .setMessage(String.valueOf(e.getMessage())).setPositiveButton("OK", null).show());
            }
        }).start();
    }

    private void confirmImport(dev.fishtank.watchlink.face.FaceImport f) {
        LinearLayout box = Ui.column(this);
        int pad = Ui.dp(this, 20);
        box.setPadding(pad, pad / 2, pad, 0);
        if (f.preview != null) {
            android.widget.ImageView iv = new android.widget.ImageView(this);
            iv.setImageBitmap(f.preview);
            iv.setAdjustViewBounds(true);
            box.addView(iv, new LinearLayout.LayoutParams(-1, Ui.dp(this, 180)));
        }
        String msg = (f.description.isEmpty() ? "" : f.description + " · ") + f.iwf.length / 1024 + " KB";
        if (f.width > 0 && (f.width != 240 || f.height != 240))
            msg += "\n\nThis face was made for a " + f.width + "×" + f.height
                    + " screen. The SW2S is 240×240, so parts of it may be cut off or out of place.";
        TextView t = Ui.label(this, msg, 14, false, 0xFF444444);
        t.setPadding(0, Ui.dp(this, 10), 0, 0);
        box.addView(t);
        new android.app.AlertDialog.Builder(this).setTitle("Install " + (f.name.isEmpty() ? "this face" : f.name) + "?")
                .setView(box)
                .setPositiveButton("Install", (d, w) -> {
                    String name = FaceInstaller.nextName();
                    Toast.makeText(this, "Sending to the watch…", Toast.LENGTH_SHORT).show();
                    FaceInstaller.install(name, f.iwf, (text, done) -> {
                        if (done) {
                            new android.app.AlertDialog.Builder(this).setMessage(text).setPositiveButton("OK", null).show();
                            WatchService.sendV3(IdoProtocol.V3_FACE_LIST_NEW, new byte[0]);
                        } else if (text.endsWith("0%")) {
                            Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Cancel", null).show();
    }

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
        Ui.primary(this, newRow, "Import (.zip)", v -> startActivityForResult(
                new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).setType("application/zip")
                        .putExtra(android.content.Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/x-zip-compressed", "application/octet-stream"}),
                IMPORT));
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
