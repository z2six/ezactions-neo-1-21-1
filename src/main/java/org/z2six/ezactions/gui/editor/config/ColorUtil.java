// MainFile: src/main/java/org/z2six/ezactions/gui/editor/config/ColorUtil.java
package org.z2six.ezactions.gui.editor.config;

import java.util.Locale;

/**
 * // MainFile: src/main/java/org/z2six/ezactions/gui/editor/config/ColorUtil.java
 * Tiny ARGB & HSV helpers used by the config UI/color picker.
 */
public final class ColorUtil {

    private ColorUtil() {}

    public static int argb(int a, int r, int g, int b) {
        a = clamp8(a); r = clamp8(r); g = clamp8(g); b = clamp8(b);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int a(int argb) { return (argb >>> 24) & 0xFF; }
    public static int r(int argb) { return (argb >>> 16) & 0xFF; }
    public static int g(int argb) { return (argb >>> 8)  & 0xFF; }
    public static int b(int argb) { return  argb         & 0xFF; }

    public static String toHexARGB(int argb) {
        return String.format(Locale.ROOT, "0x%08X", argb);
    }

    public static int parseHexARGB(String s, int fallback) {
        if (s == null) return fallback;
        String t = s.trim();
        if (t.isEmpty()) return fallback;
        if (t.startsWith("#")) t = t.substring(1);
        if (t.startsWith("0x") || t.startsWith("0X")) t = t.substring(2);
        try {
            long v = Long.parseUnsignedLong(t, 16);
            if (t.length() == 6) {
                // RGB only → assume opaque
                v = (0xFFL << 24) | v;
            }
            return (int)v;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    // --- HSV <-> RGB (A kept separate) --------------------------------------

    /** @return int[]{r,g,b} */
    public static int[] hsvToRgb(float h, float s, float v) {
        h = wrap01(h);
        s = clamp01(s);
        v = clamp01(v);

        float c = v * s;
        float x = c * (1 - Math.abs((h * 6f) % 2f - 1f));
        float m = v - c;

        float rf, gf, bf;
        int sext = (int)Math.floor(h * 6f);
        switch (sext) {
            case 0 -> { rf = c; gf = x; bf = 0; }
            case 1 -> { rf = x; gf = c; bf = 0; }
            case 2 -> { rf = 0; gf = c; bf = x; }
            case 3 -> { rf = 0; gf = x; bf = c; }
            case 4 -> { rf = x; gf = 0; bf = c; }
            case 5 -> { rf = c; gf = 0; bf = x; }
            default -> { rf = c; gf = x; bf = 0; } // <-- was "default:"
        }
        int r = clamp8(Math.round((rf + m) * 255f));
        int g = clamp8(Math.round((gf + m) * 255f));
        int b = clamp8(Math.round((bf + m) * 255f));
        return new int[]{r,g,b};
    }

    /** @return float[]{h,s,v} */
    public static float[] rgbToHsv(int r, int g, int b) {
        float rf = r / 255f, gf = g / 255f, bf = b / 255f;
        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float d = max - min;

        float h;
        if (d == 0) h = 0;
        else if (max == rf) h = ((gf - bf) / d + 6f) % 6f;
        else if (max == gf) h = ((bf - rf) / d + 2f);
        else                h = ((rf - gf) / d + 4f);
        h /= 6f;

        float s = max == 0 ? 0 : d / max;
        float v = max;
        return new float[]{wrap01(h), clamp01(s), clamp01(v)};
    }

    // --- misc ----------------------------------------------------------------

    public static int withAlpha(int argb, int a) {
        return (clamp8(a) << 24) | (argb & 0x00FFFFFF);
    }

    private static int clamp8(int x) { return Math.max(0, Math.min(255, x)); }
    public static float clamp01(float x) { return Math.max(0f, Math.min(1f, x)); }
    private static float wrap01(float x) {
        x = x % 1f;
        return x < 0f ? x + 1f : x;
    }
}
