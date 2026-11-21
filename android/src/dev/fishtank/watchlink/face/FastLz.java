package dev.fishtank.watchlink.face;

import java.io.ByteArrayOutputStream;

/**
 * FastLZ level-2 compressor (the format of fastlz_compress_level(2, ...), which the watch decodes).
 * Stream: instructions, the first one a literal run whose top 3 bits carry the level marker (001).
 *   ctrl < 32         : copy ctrl+1 literal bytes that follow
 *   ctrl >= 32        : match; t = ctrl>>5. Length = t+2 when t < 7; when t == 7 extra length bytes follow
 *                       (255 = keep reading) and length = 9 + their sum. Then the low distance byte.
 *                       Distance D = ((ctrl & 31) << 8 | low) means "copy from D+1 bytes back".
 *                       D = 8191 (31, 255) marks a far match: a big-endian u16 F follows and the copy
 *                       starts F + 8192 bytes back.
 * Our own greedy implementation; any valid stream decodes the same.
 */
public final class FastLz {
    private FastLz() {}

    private static final int MAX_COPY = 32, MAX_NEAR = 8191, MAX_FAR = 65535 + 8191;
    private static final int HASH_BITS = 13;

    public static byte[] compress(byte[] in, int off, int len) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(len + len / 16 + 16);
        int end = off + len;
        int[] table = new int[1 << HASH_BITS];
        java.util.Arrays.fill(table, -1);
        int litStart = off;
        int ip = off;
        boolean first = true;
        while (ip + 3 <= end) {
            int h = hash(in, ip);
            int ref = table[h];
            table[h] = ip;
            int dist = ref >= 0 ? ip - ref - 1 : -1;
            if (ref >= 0 && dist < MAX_FAR && in[ref] == in[ip] && in[ref + 1] == in[ip + 1] && in[ref + 2] == in[ip + 2]
                    && !(first && ip == off)) {
                int m = 3;
                while (ip + m < end && in[ref + m] == in[ip + m]) m++;
                first = flushLiterals(out, in, litStart, ip, first);
                if (first) {                         // a stream must start with a literal run
                    first = flushLiterals(out, in, ip, ip + 1, true);
                    ip++; litStart = ip;
                    continue;
                }
                writeMatch(out, m, dist);
                // index a couple of positions inside the match for better ratios
                for (int k = 1; k < m && ip + k + 3 <= end; k += (m > 16 ? 4 : 1)) table[hash(in, ip + k)] = ip + k;
                ip += m;
                litStart = ip;
            } else {
                ip++;
            }
        }
        flushLiterals(out, in, litStart, end, first);
        return out.toByteArray();
    }

    private static int hash(byte[] b, int i) {
        int v = (b[i] & 0xff) | (b[i + 1] & 0xff) << 8 | (b[i + 2] & 0xff) << 16;
        return (int) (((v * 2654435761L) & 0xffffffffL) >>> (32 - HASH_BITS));
    }

    /** Writes in[from, to) as literal runs; returns whether nothing has been written to the stream yet. */
    private static boolean flushLiterals(ByteArrayOutputStream out, byte[] in, int from, int to, boolean first) {
        while (from < to) {
            int n = Math.min(MAX_COPY, to - from);
            out.write((n - 1) | (first ? 0x20 : 0));
            first = false;
            out.write(in, from, n);
            from += n;
        }
        return first;
    }

    private static void writeMatch(ByteArrayOutputStream out, int length, int dist) {
        boolean far = dist >= MAX_NEAR;
        int hi = far ? 31 : dist >> 8, lo = far ? 255 : dist & 0xff;
        if (length < 9) {
            out.write(((length - 2) << 5) | hi);
        } else {
            out.write((7 << 5) | hi);
            int extra = length - 9;
            while (extra >= 255) { out.write(255); extra -= 255; }
            out.write(extra);
        }
        out.write(lo);
        if (far) {
            int f = dist - MAX_NEAR;
            out.write(f >> 8);
            out.write(f);
        }
    }

    /** Reference decoder (for tests). */
    public static byte[] decompress(byte[] in) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1 << 20];
        int op = 0, ip = 0;
        int ctrl = in[ip++] & 31;
        while (true) {
            if (ctrl >= 32) {
                int len = (ctrl >> 5) - 1;
                int ofs = (ctrl & 31) << 8;
                int ref = op - ofs - 1;
                if (len == 6) { int c; do { c = in[ip++] & 0xff; len += c; } while (c == 255); }
                int code = in[ip++] & 0xff;
                ref -= code;
                if (code == 255 && ofs == (31 << 8)) {
                    int f = (in[ip++] & 0xff) << 8; f |= in[ip++] & 0xff;
                    ref = op - f - MAX_NEAR - 1;
                }
                for (int k = 0; k < len + 3; k++) buf[op++] = buf[ref++];
            } else {
                for (int k = 0; k <= ctrl; k++) buf[op++] = in[ip++];
            }
            if (ip >= in.length) break;
            ctrl = in[ip++] & 0xff;
        }
        out.write(buf, 0, op);
        return out.toByteArray();
    }
}
