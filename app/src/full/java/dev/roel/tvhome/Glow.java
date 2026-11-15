package dev.roel.tvhome;

/** Finds an app's main colour from a small copy of its banner, for the background glow. */
final class Glow {
    static final int FALLBACK = 0xFF4A3AA8; // indigo, for tiles with no app (inputs, hidden apps)

    private Glow() {}

    /** Average of the pixels, weighted so white, grey, black and see-through parts count for (almost) nothing. */
    static int pick(int[] px) {
        double r = 0, g = 0, b = 0, w = 0;
        for (int c : px) {
            if ((c >>> 24) < 128) continue;
            int R = (c >> 16) & 255, G = (c >> 8) & 255, B = c & 255;
            int max = Math.max(R, Math.max(G, B)), min = Math.min(R, Math.min(G, B));
            if (max == 0) continue;
            double s = (max - min) / (double) max, wt = s * s * max / 255.0;
            r += R * wt;
            g += G * wt;
            b += B * wt;
            w += wt;
        }
        if (w < px.length * 0.005) return FALLBACK; // almost no colour: a black-and-white banner
        return 0xFF000000 | ((int) (r / w) << 16) | ((int) (g / w) << 8) | (int) (b / w);
    }
}
