package com.vk.emojiclicker;

import android.graphics.Bitmap;

/**
 * Шаблонный матчинг как в ПК-версии: нормированная кросс-корреляция
 * (аналог cv2.TM_CCOEFF_NORMED) по яркости, на нескольких масштабах.
 * Инвариантен к яркости/контрасту — находит ТОТ ЖЕ эмодзи, который прикреплён,
 * даже если на экране он чуть другого оттенка или в тени.
 */
public class TemplateMatcher {

    public static class Match {
        public int x, y;      // центр найденного шаблона
        public float score;   // -1..1 (как CCOEFF_NORMED), 1 = идеально
        public int templateW, templateH;
        Match(int x, int y, float s) { this.x = x; this.y = y; this.score = s; }
        Match(int x, int y, float s, int tw, int th) { this.x = x; this.y = y; this.score = s; this.templateW = tw; this.templateH = th; }
    }

    /** Находит лучший центр шаблона на кадре. Returns null если совпадение ниже threshold. */
    public static Match find(Bitmap screen, Bitmap template, double threshold) {
        Match m = findBest(screen, template);
        if (m == null || m.score < threshold) return null;
        return m;
    }

    /** Всегда возвращает лучшее совпадение (даже ниже порога) или null, если шаблон больше экрана. */
    public static Match findBest(Bitmap screen, Bitmap template) {
        int sw = screen.getWidth(), sh = screen.getHeight();
        int tw = template.getWidth(), th = template.getHeight();
        if (tw >= sw || th >= sh) return null;

        // grayscale luminance of screen
        int[] s = new int[sw * sh];
        screen.getPixels(s, 0, sw, 0, 0, sw, sh);
        short[] lum = new short[sw * sh];
        for (int i = 0; i < s.length; i++) {
            int c = s[i];
            lum[i] = (short)(((c >> 16) & 0xff) * 299 / 1000
                           + ((c >> 8) & 0xff) * 587 / 1000
                           + (c & 0xff) * 114 / 1000);
        }

        float bestScore = -2f;
        int bestX = 0, bestY = 0, bestNw = 0, bestNh = 0;

        // PC parity: scales 0.35..1.5 step 0.07
        int steps = (int)((1.5f - 0.35f) / 0.07f) + 1;
        for (int k = 0; k < steps; k++) {
            float sc = 0.35f + 0.07f * k;
            int nw = Math.max(4, (int)(tw * sc));
            int nh = Math.max(4, (int)(th * sc));
            if (nw >= sw || nh >= sh) continue;
            Bitmap r = Bitmap.createScaledBitmap(template, nw, nh, true);
            int[] tp = new int[nw * nh];
            r.getPixels(tp, 0, nw, 0, 0, nw, nh);
            r.recycle();
            short[] t = new short[nw * nh];
            for (int i = 0; i < tp.length; i++) {
                int c = tp[i];
                t[i] = (short)(((c >> 16) & 0xff) * 299 / 1000
                             + ((c >> 8) & 0xff) * 587 / 1000
                             + (c & 0xff) * 114 / 1000);
            }

            // template mean
            float tMean = 0f;
            for (short v : t) tMean += v;
            tMean /= t.length;

            int scanStep = 3;
            float bestAt = -2f;
            int bsx = 0, bsy = 0;
            for (int y = 0; y + nh <= sh; y += scanStep) {
                for (int x = 0; x + nw <= sw; x += scanStep) {
                    float sc2 = corrAt(lum, sw, t, nw, nh, tMean, x, y);
                    if (sc2 > bestAt) { bestAt = sc2; bsx = x; bsy = y; }
                }
            }
            // refine around best with step 1
            for (int dy = -3; dy <= 3; dy++) {
                for (int dx = -3; dx <= 3; dx++) {
                    int x = bsx + dx, y = bsy + dy;
                    if (x < 0 || y < 0 || x + nw > sw || y + nh > sh) continue;
                    float sc2 = corrAt(lum, sw, t, nw, nh, tMean, x, y);
                    if (sc2 > bestAt) { bestAt = sc2; bsx = x; bsy = y; }
                }
            }

            if (bestAt > bestScore) {
                bestScore = bestAt;
                bestX = bsx + nw / 2;
                bestY = bsy + nh / 2;
                bestNw = nw;
                bestNh = nh;
            }
        }
        if (bestScore < -1f) return null;
        return new Match(bestX, bestY, bestScore, bestNw, bestNh);
    }

    // normalized cross-correlation of template (offset ox,oy in screen) vs screen luminance
    static float corrAt(short[] lum, int sw, short[] t, int nw, int nh, float tMean, int ox, int oy) {
        long sumT = 0, sumS = 0, sumTS = 0, sumTT = 0, sumSS = 0;
        for (int j = 0; j < nh; j++) {
            int sRow = (oy + j) * sw + ox;
            int tRow = j * nw;
            for (int i = 0; i < nw; i++) {
                int sv = lum[sRow + i];
                int tv = t[tRow + i];
                sumT += tv;
                sumS += sv;
                sumTS += (long) tv * sv;
                sumTT += (long) tv * tv;
                sumSS += (long) sv * sv;
            }
        }
        int n = nw * nh;
        float v1 = sumTS - (float)sumT * sumS / n;
        float d1 = (float)Math.sqrt(Math.max(0L, (long)sumTT - (float)sumT * sumT / n));
        float d2 = (float)Math.sqrt(Math.max(0L, (long)sumSS - (float)sumS * sumS / n));
        if (d1 < 1e-6f || d2 < 1e-6f) return -2f;
        return v1 / (d1 * d2);
    }
}