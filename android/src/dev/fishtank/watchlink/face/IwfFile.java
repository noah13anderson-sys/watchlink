package dev.fishtank.watchlink.face;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds IDO ".iwf" watch-face files, as mkWatchFace::makeIwfFile in the Momax library does
 * (worked out from its disassembly + DWARF types; see PROTOCOL.md "Custom watch faces").
 *
 * Container: "iwf\0", u16 version 1, u16 file count; then per file a 40-byte entry
 * {char name[30], u16 0, u32 offset, u32 length} where offset counts from the start of the file;
 * then the file data in the same order.
 * Files: "iwf.json" (the layout), "font.json", then every image converted to a RAW bitmap
 * (root images keep their file name, e.g. "files0.png"; font images are named "<font>_<name>", e.g. "g16_0").
 *
 * Pure Java (no Android classes) so it can be tested on a PC.
 */
public final class IwfFile {
    private final List<String> names = new ArrayList<>();
    private final List<byte[]> datas = new ArrayList<>();

    public void add(String name, byte[] data) {
        names.add(name);
        datas.add(data);
    }

    public void addText(String name, String text) { add(name, text.getBytes(StandardCharsets.UTF_8)); }

    public byte[] build() {
        int n = names.size();
        int head = 8 + 40 * n;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write('i'); out.write('w'); out.write('f'); out.write(0);
        le16(out, 1);
        le16(out, n);
        int offset = head;
        for (int i = 0; i < n; i++) {
            byte[] nm = names.get(i).getBytes(StandardCharsets.US_ASCII);
            byte[] field = new byte[32];                 // name[30] + 2 padding bytes
            System.arraycopy(nm, 0, field, 0, Math.min(29, nm.length));
            out.write(field, 0, 32);
            le32(out, offset);
            le32(out, datas.get(i).length);
            offset += datas.get(i).length;
        }
        for (byte[] d : datas) out.write(d, 0, d.length);
        return out.toByteArray();
    }

    /**
     * RAW bitmap, format 0x85 (what the SW2S reports in v3 0x07): 16-byte head "RAW\0", u16 w, u16 h,
     * u8 format, u8 alpha format (0x66 = 4-bit alpha plane, 0 = opaque), u16 0, u32 alpha offset;
     * pixels RGB565 big-endian; then (unless fully opaque) 4 bits of alpha per pixel, even pixel in the
     * high nibble. Byte-identical to the library's bitmap_tool_get_font_buff on the sample faces.
     */
    public static byte[] raw565(int[] argb, int w, int h) {
        // Note: the SW2S reads the alpha plane as if every row started on a new byte, so images with an odd
        // width come out skewed (seen 2026-11-21). The library packs the plane continuously like we do; callers
        // keep widths even instead (FaceDesign rounds up, FaceImport pads a column).
        int n = w * h;
        byte[] px = new byte[2 * n];
        byte[] alpha = new byte[(n + 1) / 2];
        boolean opaque = true;
        for (int i = 0; i < n; i++) {
            int c = argb[i];
            int a = c >>> 24, r = (c >> 16) & 0xff, g = (c >> 8) & 0xff, b = c & 0xff;
            int v = ((r >> 3) << 11) | ((g >> 2) << 5) | (b >> 3);
            px[2 * i] = (byte) (v >> 8);
            px[2 * i + 1] = (byte) v;
            if (a != 255) opaque = false;
            alpha[i / 2] |= (byte) ((a >> 4) << ((i & 1) == 0 ? 4 : 0));
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write('R'); out.write('A'); out.write('W'); out.write(0);
        le16(out, w);
        le16(out, h);
        out.write(0x85);
        out.write(opaque ? 0 : 0x66);
        le16(out, 0);
        le32(out, opaque ? 0 : px.length);
        out.write(px, 0, px.length);
        if (!opaque) out.write(alpha, 0, alpha.length);
        return out.toByteArray();
    }

    /**
     * RAW bitmap format 0x66, used for fonts with "bpp": 4: head as above with format 0x66 and alpha format 0,
     * then 4 bits of grey per pixel ((30R + 59G + 11B) / 100, top 4 bits), even pixel in the high nibble.
     * Byte-identical to the library on the sample face.
     */
    public static byte[] raw4(int[] argb, int w, int h) {
        int n = w * h;
        byte[] g = new byte[(n + 1) / 2];
        for (int i = 0; i < n; i++) {
            int c = argb[i];
            int v = (((c >> 16) & 0xff) * 30 + ((c >> 8) & 0xff) * 59 + (c & 0xff) * 11) / 100;
            g[i / 2] |= (byte) ((v >> 4) << ((i & 1) == 0 ? 4 : 0));
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write('R'); out.write('A'); out.write('W'); out.write(0);
        le16(out, w);
        le16(out, h);
        out.write(0x66);
        out.write(0);
        le16(out, 0);
        le32(out, 0);
        out.write(g, 0, g.length);
        return out.toByteArray();
    }

    /**
     * ".iwf.lz" file as compress_file_to_lz / fastlz_util_compress_file make it: the input cut into
     * blockSize blocks (4096 on the SW2S, from v3 0x07), each written as u32 big-endian length + FastLZ level-2
     * data. A block that doesn't shrink below blockSize is written as two halves instead.
     */
    public static byte[] compress(byte[] data, int blockSize) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int pos = 0; pos < data.length; pos += blockSize) {
            int len = Math.min(blockSize, data.length - pos);
            byte[] c = FastLz.compress(data, pos, len);
            if (c.length <= blockSize) {
                be32(out, c.length); out.write(c, 0, c.length);
            } else {
                int half = len / 2;
                byte[] a = FastLz.compress(data, pos, half);
                byte[] b = FastLz.compress(data, pos + half, len - half);   // library drops the odd byte; we keep it
                be32(out, a.length); out.write(a, 0, a.length);
                be32(out, b.length); out.write(b, 0, b.length);
            }
        }
        return out.toByteArray();
    }

    /** Sum of all bytes, the check code the watch compares at the end of a transfer (cal_check_code_optimized). */
    public static int checkCode(byte[] d) {
        int s = 0;
        for (byte b : d) s += b & 0xff;
        return s;
    }

    static void le16(ByteArrayOutputStream o, int v) { o.write(v); o.write(v >> 8); }
    static void le32(ByteArrayOutputStream o, int v) { o.write(v); o.write(v >> 8); o.write(v >> 16); o.write(v >> 24); }
    static void be32(ByteArrayOutputStream o, int v) { o.write(v >> 24); o.write(v >> 16); o.write(v >> 8); o.write(v); }
}
