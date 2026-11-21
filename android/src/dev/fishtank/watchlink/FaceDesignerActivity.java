package dev.fishtank.watchlink;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import dev.fishtank.watchlink.face.FaceDesign;

/** Design a watch face on the phone and install it on the watch. */
public class FaceDesignerActivity extends Activity {
    private static final int PICK_PHOTO = 1;
    private final FaceDesign d = new FaceDesign();
    private ImageView preview;
    private TextView status, install;
    private final List<Runnable> painters = new ArrayList<>();
    private SharedPreferences p;
    private boolean installing;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        p = getSharedPreferences(WatchService.PREFS, 0);
        load();
        LinearLayout root = Ui.column(this);
        int pad = Ui.dp(this, 16);
        root.setPadding(pad, Ui.dp(this, 28), pad, pad);
        root.addView(Ui.label(this, "New watch face", 28, true, Ui.text(this)));

        preview = new ImageView(this);
        int ps = Ui.dp(this, 220);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ps, ps);
        plp.gravity = Gravity.CENTER_HORIZONTAL;
        plp.setMargins(0, Ui.dp(this, 16), 0, Ui.dp(this, 16));
        preview.setClipToOutline(true);
        GradientDrawable frame = Ui.rounded(0xFF000000, Ui.dp(this, 36));
        preview.setBackground(frame);
        root.addView(preview, plp);

        Ui.section(this, root, "Style");
        chips(root, FaceDesign.STYLES, () -> d.style, i -> d.style = i);
        Ui.section(this, root, "Background");
        chips(root, FaceDesign.BACKGROUNDS, () -> d.background, i -> {
            if (i == FaceDesign.BACKGROUNDS.length - 1) {
                startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), PICK_PHOTO);
            } else d.background = i;
        });
        Ui.section(this, root, "Time");
        chips(root, FaceDesign.FONTS, () -> d.font, i -> d.font = i);
        chips(root, new String[]{"Small", "Medium", "Large"}, () -> d.timeSize == 52 ? 0 : d.timeSize == 76 ? 2 : 1,
                i -> d.timeSize = i == 0 ? 52 : i == 2 ? 76 : 64);
        colors(root, () -> d.timeColor, c -> d.timeColor = c);
        Ui.section(this, root, "Layout (digital)");
        chips(root, FaceDesign.POSITIONS, () -> d.position, i -> d.position = i);
        Ui.section(this, root, "Extras");
        colors(root, () -> d.accentColor, c -> d.accentColor = c);
        LinearLayout extras = Ui.card(this, root);
        toggle(extras, "Date", () -> d.date, v -> d.date = v);
        toggle(extras, "Weekday", () -> d.weekday, v -> d.weekday = v);
        toggle(extras, "Steps", () -> d.steps, v -> d.steps = v);
        toggle(extras, "Battery", () -> d.battery, v -> d.battery = v);
        toggle(extras, "Heart rate", () -> d.heart, v -> d.heart = v);
        toggle(extras, "Calories", () -> d.calories, v -> d.calories = v);

        status = Ui.label(this, "", 14, false, Ui.subtext(this));
        status.setPadding(Ui.dp(this, 4), Ui.dp(this, 8), 0, Ui.dp(this, 8));
        root.addView(status);
        LinearLayout row = new LinearLayout(this);
        install = Ui.primary(this, row, "Install on watch", v -> install());
        root.addView(row, new LinearLayout.LayoutParams(-1, -2));

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.bg(this));
        sv.addView(root);
        setContentView(sv);
        Ui.style(this, sv);
        WatchService.sendV3(IdoProtocol.V3_FACE_LIST_NEW, new byte[0]);
        redraw();
    }

    private void redraw() {
        for (Runnable r : painters) r.run();
        preview.setImageBitmap(d.preview());
        save();
    }

    // ---- remember the last design ----
    private void load() {
        d.background = p.getInt("fd.bg", 0); d.font = p.getInt("fd.font", 0);
        d.timeColor = p.getInt("fd.tc", 0xFFFFFFFF); d.accentColor = p.getInt("fd.ac", 0xFF00D3B8);
        d.position = p.getInt("fd.pos", 0); d.timeSize = p.getInt("fd.size", 64);
        d.date = p.getBoolean("fd.date", true); d.weekday = p.getBoolean("fd.week", true);
        d.steps = p.getBoolean("fd.steps", true); d.battery = p.getBoolean("fd.batt", true);
        d.heart = p.getBoolean("fd.hr", false); d.calories = p.getBoolean("fd.kcal", false);
        d.style = p.getInt("fd.style", 0);
        if (d.font >= FaceDesign.FONTS.length) d.font = 0;
        if (d.background == FaceDesign.BACKGROUNDS.length - 1) {
            d.photo = BitmapFactory.decodeFile(new java.io.File(getFilesDir(), "face_photo.png").getPath());
            if (d.photo == null) d.background = 0;
        }
    }

    private void save() {
        p.edit().putInt("fd.bg", d.background).putInt("fd.font", d.font).putInt("fd.tc", d.timeColor)
                .putInt("fd.ac", d.accentColor).putInt("fd.pos", d.position).putInt("fd.size", d.timeSize)
                .putBoolean("fd.date", d.date).putBoolean("fd.week", d.weekday).putBoolean("fd.steps", d.steps)
                .putBoolean("fd.batt", d.battery).putBoolean("fd.hr", d.heart).putBoolean("fd.kcal", d.calories)
                .putInt("fd.style", d.style).apply();
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        if (req != PICK_PHOTO || res != RESULT_OK || data == null || data.getData() == null) return;
        try (InputStream in = getContentResolver().openInputStream(data.getData())) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 1;
            byte[] bytes = readAll(in);
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
            int s = Math.min(o.outWidth, o.outHeight);
            o.inJustDecodeBounds = false;
            while (s / (o.inSampleSize * 2) >= FaceDesign.SIZE * 2) o.inSampleSize *= 2;
            Bitmap full = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
            int side = Math.min(full.getWidth(), full.getHeight());
            Bitmap sq = Bitmap.createBitmap(full, (full.getWidth() - side) / 2, (full.getHeight() - side) / 2, side, side);
            d.photo = Bitmap.createScaledBitmap(sq, FaceDesign.SIZE, FaceDesign.SIZE, true);
            d.background = FaceDesign.BACKGROUNDS.length - 1;
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(new java.io.File(getFilesDir(), "face_photo.png"))) {
                d.photo.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            redraw();
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't open that picture", Toast.LENGTH_SHORT).show();
        }
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
        return o.toByteArray();
    }

    // ---- install ----
    private void install() {
        if (installing) return;
        installing = true;
        status.setText("Building the face…");
        String name = FaceInstaller.nextName();
        new Thread(() -> {
            try {
                byte[] iwf = d.build(name.replace(".iwf", ""));
                FaceInstaller.install(name, iwf, (text, done) -> { status.setText(text); if (done) installing = false; });
            } catch (Exception e) {
                runOnUiThread(() -> { installing = false; status.setText("Couldn't build the face: " + e); });
            }
        }).start();
    }

    // ---- small widgets ----
    interface IntGet { int get(); }
    interface IntSet { void set(int v); }
    interface BoolGet { boolean get(); }
    interface BoolSet { void set(boolean v); }

    private void chips(LinearLayout root, String[] labels, IntGet sel, IntSet on) {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        for (int i = 0; i < labels.length; i++) {
            final int k = i;
            TextView c = Ui.label(this, labels[i], 15, true, Ui.text(this));
            c.setPadding(Ui.dp(this, 14), Ui.dp(this, 9), Ui.dp(this, 14), Ui.dp(this, 9));
            painters.add(() -> {
                boolean s = sel.get() == k;
                c.setTextColor(s ? Ui.ON_ACCENT : Ui.text(this));
                c.setBackground(Ui.rounded(s ? Ui.ACCENT : 0xFF2A2F44, Ui.dp(this, 18)));
            });
            c.setOnClickListener(v -> { on.set(k); redraw(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMargins(0, 0, Ui.dp(this, 8), Ui.dp(this, 8));
            row.addView(c, lp);
        }
        hs.addView(row);
        root.addView(hs);
    }

    private void colors(LinearLayout root, IntGet sel, IntSet on) {
        LinearLayout row = new LinearLayout(this);
        int s = Ui.dp(this, 34);
        for (int col : FaceDesign.COLORS) {
            View v = new View(this);
            painters.add(() -> {
                GradientDrawable g = new GradientDrawable();
                g.setShape(GradientDrawable.OVAL);
                g.setColor(col);
                g.setStroke(Ui.dp(this, sel.get() == col ? 3 : 1), sel.get() == col ? Ui.ACCENT : 0x44FFFFFF);
                v.setBackground(g);
            });
            v.setOnClickListener(x -> { on.set(col); redraw(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(s, s);
            lp.setMargins(0, Ui.dp(this, 2), Ui.dp(this, 10), Ui.dp(this, 10));
            row.addView(v, lp);
        }
        root.addView(row);
    }

    private void toggle(LinearLayout card, String title, BoolGet get, BoolSet set) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 4), Ui.dp(this, 6), 0, Ui.dp(this, 6));
        row.addView(Ui.label(this, title, 17, false, Ui.text(this)), new LinearLayout.LayoutParams(0, -2, 1));
        Switch sw = new Switch(this);
        android.content.res.ColorStateList t = new android.content.res.ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}}, new int[]{Ui.ACCENT, Ui.subtext(this)});
        sw.setThumbTintList(t); sw.setTrackTintList(t);
        sw.setChecked(get.get());
        sw.setOnCheckedChangeListener((v, c) -> { set.set(c); redraw(); });
        row.addView(sw);
        card.addView(row);
    }
}
