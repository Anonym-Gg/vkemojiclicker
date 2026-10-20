package com.vk.emojiclicker;

import android.graphics.Bitmap;

/**
 * Быстрый шаблонный матчинг по пикселям (без OpenCV).
 * Подвыбирает пиксели (ускорение), ищет на нескольких масштабах.
 * Сначала грубый проход, потом уточнение вокруг лучшего места.
 */
public class TemplateMatcher {

    public static class Match {
        public int x, y;      // центр найденного шаблона
        public float score;   // 0..1, чем ближе к 1 тем лучше
        public int templateW, templateH; // размер найденного шаблона
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

        int[] s = new int[sw * sh];
        screen.getPixels(s, 0, sw, 0, 0, sw, sh);

        float bestScore = -1f;
        int bestX = 0, bestY = 0, bestNw = 0, bestNh = 0;

        // меньше масштабов, но крупнее шаг по позиции
        float[] scales = {1.0f, 0.8f, 1.25f, 0.6f};
        for (float sc : scales) {
            int nw = Math.max(4, (int)(tw * sc));
            int nh = Math.max(4, (int)(th * sc));
            if (nw >= sw || nh >= sh) continue;
            Bitmap r = Bitmap.createScaledBitmap(template, nw, nh, true);
            int[] t = new int[nw * nh];
            r.getPixels(t, 0, nw, 0, 0, nw, nh);
            r.recycle();

            // подвыборка пикселей (ускорение): смотрим каждый 2-й по x и y
            int step = 2;
            int nw2 = (nw + step - 1) / step;
            int nh2 = (nh + step - 1) / step;

            // грубый проход (шаг по экрану 4), затем уточнение вокруг лучшего
            int scanStep = 4;
            float bestAtScale = -1f;
            int bsx = 0, bsy = 0;
            for (int y = 0; y + nh <= sh; y += scanStep) {
                for (int x = 0; x + nw <= sw; x += scanStep) {
                    float sc2 = scoreAt(s, sw, t, nw, nh, nw2, nh2, step, x, y);
                    if (sc2 > bestAtScale) {
                        bestAtScale = sc2;
                        bsx = x; bsy = y;
                    }
                }
            }
            // уточнение: пробуем соседние смещения с шагом 1
            for (int dy = -4; dy <= 4; dy++) {
                for (int dx = -4; dx <= 4; dx++) {
                    int x = bsx + 2*dx;
                    int y = bsy + 2*dy;
                    if (x < 0 || y < 0 || x + nw > sw || y + nh > sh) continue;
                    float sc2 = scoreAt(s, sw, t, nw, nh, nw2, nh2, step, x, y);
                    if (sc2 > bestAtScale) {
                        bestAtScale = sc2;
                        bsx = x; bsy = y;
                    }
                }
            }
            if (bestAtScale > bestScore) {
                bestScore = bestAtScale;
                bestX = bsx + nw / 2;
                bestY = bsy + nh / 2;
                bestNw = nw;
                bestNh = nh;
            }
        }
        if (bestScore < 0f) return null;
        return new Match(bestX, bestY, bestScore, bestNw, bestNh);
    }

    // avg abs color diff on a pixel subsample -> score 0..1
    static float scoreAt(int[] s, int sw, int[] t, int nw, int nh, int nw2, int nh2, int step, int ox, int oy) {
        float diff = 0f;
        int cnt = 0;
        for (int j = 0; j < nh2; j++) {
            int sy = oy + j * step;
            int sRow = sy * sw + ox;
            int tRow = j * step * nw;
            for (int i = 0; i < nw2; i++) {
                int si = sRow + i * step;
                int ti = tRow + i * step;
                int sp = s[si];
                int tp = t[ti];
                diff += Math.abs(((sp >> 16) & 0xff) - ((tp >> 16) & 0xff))
                      + Math.abs(((sp >> 8) & 0xff) - ((tp >> 8) & 0xff))
                      + Math.abs((sp & 0xff) - (tp & 0xff));
                cnt++;
            }
        }
        if (cnt == 0) return 0f;
        return 1f - (diff / cnt) / 765f;
    }
}