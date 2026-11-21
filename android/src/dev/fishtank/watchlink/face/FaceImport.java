package dev.fishtank.watchlink.face;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Turns an IDO cloud-face package (.zip with iwf.json, font.json, images and one folder per font) into an
 * .iwf file, in the same order and with the same names as mkWatchFace::makeIwfFile.
 */
public final class FaceImport {
    public String name = "", description = "";
    public int width, height;
    public Bitmap preview;
    public byte[] iwf;

    private static final long MAX_TOTAL = 8 << 20;

    public static FaceImport read(InputStream in) throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        long total = 0;
        try (ZipInputStream z = new ZipInputStream(in)) {
            ZipEntry e;
            byte[] buf = new byte[65536];
            while ((e = z.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String n = e.getName().replace('\\', '/');
                if (n.contains("..") || n.startsWith("__MACOSX/")) continue;
                ByteArrayOutputStream o = new ByteArrayOutputStream();
                int r;
                while ((r = z.read(buf)) > 0) {
                    o.write(buf, 0, r);
                    total += r;
                    if (total > MAX_TOTAL) throw new Exception("The package is too big for a watch face");
                }
                files.put(n, o.toByteArray());
            }
        }
        String root = null;
        for (String n : files.keySet()) {
            if (n.equals("iwf.json") || n.endsWith("/iwf.json")) {
                String r = n.substring(0, n.length() - "iwf.json".length());
                if (root == null || r.length() < root.length()) root = r;
            }
        }
        if (root == null) throw new Exception("No iwf.json in this file, so it isn't an IDO watch face");

        FaceImport f = new FaceImport();
        byte[] mainJson = files.get(root + "iwf.json");
        JSONObject main = new JSONObject(new String(mainJson, StandardCharsets.UTF_8));
        f.name = main.optString("name");
        f.description = main.optString("description");

        IwfFile out = new IwfFile();
        out.add("iwf.json", mainJson);
        if (files.containsKey(root + "iwf1.json")) out.add("iwf1.json", files.get(root + "iwf1.json"));
        byte[] fontJson = files.get(root + "font.json");
        if (fontJson != null) out.add("font.json", fontJson);

        // root images, then each font's folder
        List<String> rootImages = new ArrayList<>();
        for (String n : files.keySet()) {
            String rel = n.substring(Math.min(root.length(), n.length()));
            if (n.startsWith(root) && !rel.contains("/") && isImage(rel)) rootImages.add(rel);
        }
        Collections.sort(rootImages);
        String bk = main.optString("bkground");
        for (String rel : rootImages) {
            Bitmap b = decode(files.get(root + rel));
            if (rel.equals(bk)) { f.width = b.getWidth(); f.height = b.getHeight(); }
            if (rel.equals(main.optString("preview"))) f.preview = b;
            out.add(rel, rel.contains("_4bit") ? IwfFile.raw4(px(b), b.getWidth(), b.getHeight())
                    : IwfFile.raw565(px(b), b.getWidth(), b.getHeight()));
        }
        if (fontJson != null) {
            JSONArray fonts = new JSONObject(new String(fontJson, StandardCharsets.UTF_8)).optJSONArray("item");
            for (int i = 0; fonts != null && i < fonts.length(); i++) {
                JSONObject font = fonts.getJSONObject(i);
                String fn = font.getString("name");
                boolean four = font.optInt("bpp", 16) == 4;
                List<String> imgs = new ArrayList<>();
                for (String n : files.keySet()) {
                    if (n.startsWith(root + fn + "/") && isImage(n) && n.indexOf('/', root.length() + fn.length() + 1) < 0)
                        imgs.add(n);
                }
                Collections.sort(imgs);
                for (String n : imgs) {
                    String base = n.substring(n.lastIndexOf('/') + 1);
                    base = base.substring(0, base.lastIndexOf('.'));
                    Bitmap b = decode(files.get(n));
                    out.add(fn + "_" + base, four ? IwfFile.raw4(px(b), b.getWidth(), b.getHeight())
                            : IwfFile.raw565(px(b), b.getWidth(), b.getHeight()));
                }
            }
        }
        f.iwf = out.build();
        return f;
    }

    private static boolean isImage(String n) {
        String l = n.toLowerCase();
        return l.endsWith(".png") || l.endsWith(".bmp") || l.endsWith(".jpg") || l.endsWith(".jpeg");
    }

    private static Bitmap decode(byte[] d) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        o.inPremultiplied = false;          // keep the colours of semi-transparent pixels as stored
        o.inScaled = false;
        Bitmap b = BitmapFactory.decodeByteArray(d, 0, d.length, o);
        if (b == null) throw new Exception("An image in the package couldn't be read");
        return b;
    }

    private static int[] px(Bitmap b) {
        int[] p = new int[b.getWidth() * b.getHeight()];
        b.getPixels(p, 0, b.getWidth(), 0, 0, b.getWidth(), b.getHeight());
        return p;
    }
}
