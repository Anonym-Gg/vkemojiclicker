package com.vk.emojiclicker;

import android.graphics.Bitmap;

/**
 * Шаблонный матчинг как в ПК-версии: нормированная кросс-корреляция
 * (аналог cv2.TM_CCOEFF_NORMED) по яркости с учётом маски прозрачности шаблона.
 * Прозрачные пиксели шаблона исключаются из расчёта — шаблон с прозрачным фоном
 * находит эмодзи на любом фоне (светлом/тёмном/цветном).
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

        // screen luminance
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

        // scales cover smaller & bigger variants of the emoji in the game grid
        float[] scales = {0.45f, 0.6f, 0.8f, 1.0f, 1.25f, 1.5f};
        for (float sc : scales) {
            int nw = Math.max(4, (int)(tw * sc));
            int nh = Math.max(4, (int)(th * sc));
            if (nw >= sw || nh >= sh) continue;
            Bitmap r = Bitmap.createScaledBitmap(template, nw, nh, true);
            int[] tp = new int[nw * nh];
            r.getPixels(tp, 0, nw, 0, 0, nw, nh);
            r.recycle();

            // luminance + alpha mask of the scaled template
            short[] t = new short[nw * nh];
            boolean[] mask = new boolean[nw * nh];
            int maskCount = 0;
            for (int i = 0; i < tp.length; i++) {
                int c = tp[i];
                int a = (c >>> 24) & 0xff;
                boolean usable = a > 40; // ignore nearly transparent pixels
                mask[i] = usable;
                if (usable) {
                    t[i] = (short)(((c >> 16) & 0xff) * 299 / 1000
                                 + ((c >> 8) & 0xff) * 587 / 1000
                                 + (c & 0xff) * 114 / 1000);
                    maskCount++;
                }
            }
            if (maskCount < 8) continue; // degenerate template

            int scanStep = 2;
            float bestAt = -2f;
            int bsx = 0, bsy = 0;
            for (int y = 0; y + nh <= sh; y += scanStep) {
                for (int x = 0; x + nw <= sw; x += scanStep) {
                    float sc2 = corrMasked(lum, sw, t, mask, nw, nh, maskCount, x, y);
                    if (sc2 > bestAt) { bestAt = sc2; bsx = x; bsy = y; }
                }
            }
            for (int dy = -2; dy <= 2; dy++) {
                for (int dx = -2; dx <= 2; dx++) {
                    int x = bsx + dx, y = bsy + dy;
                    if (x < 0 || y < 0 || x + nw > sw || y + nh > sh) continue;
                    float sc2 = corrMasked(lum, sw, t, mask, nw, nh, maskCount, x, y);
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

    // masked normalized cross-correlation (only non-transparent template pixels)
    static float corrMasked(short[] lum, int sw, short[] t, boolean[] mask, int nw, int nh, int maskCount, int ox, int oy) {
        long sumT = 0, sumS = 0, sumTS = 0, sumTT = 0, sumSS = 0;
        for (int j = 0; j < nh; j++) {
            int sRow = (oy + j) * sw + ox;
            int tRow = j * nw;
            for (int i = 0; i < nw; i++) {
                if (!mask[tRow + i]) continue;
                int sv = lum[sRow + i];
                int tv = t[tRow + i];
                sumT += tv;
                sumS += sv;
                sumTS += (long) tv * sv;
                sumTT += (long) tv * tv;
                sumSS += (long) sv * sv;
            }
        }
        int n = maskCount;
        float v1 = sumTS - (float)sumT * sumS / n;
        float d1 = (float)Math.sqrt(Math.max(0L, (long)sumTT - (float)sumT * sumT / n));
        float d2 = (float)Math.sqrt(Math.max(0L, (long)sumSS - (float)sumS * sumS / n));
        if (d2 < 1e-3f) return -2f; // screen window is a flat region -> no signal
        if (d1 < 1e-3f) {
            // template is nearly uniform (e.g. plain monochrome icon): use non-centered correlation
            return (float)sumTS / ((float)Math.sqrt(sumTT) * d2 + 1e-6f);
        }
        return v1 / (d1 * d2);
    }
}