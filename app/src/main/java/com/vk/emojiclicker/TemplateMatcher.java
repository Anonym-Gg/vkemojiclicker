package com.vk.emojiclicker;

import android.graphics.Bitmap;

/**
 * Простой шаблонный матчинг по пикселям (без OpenCV).
 * Ищет шаблон (иконку эмодзи) на скриншоте и возвращает центр лучшего совпадения.
 * Использует среднюю абсолютную разницу цвета (маленькая = лучше) на нескольких масштабах.
 */
public class TemplateMatcher {

    public static class Match {
        public int x, y;      // центр найденного шаблона
        public float score;   // 0..1, чем ближе к 1 тем лучше
        public int templateW, templateH; // размер найденного шаблона
        Match(int x, int y, float s) { this.x = x; this.y = y; this.score = s; }
        Match(int x, int y, float s, int tw, int th) { this.x = x; this.y = y; this.score = s; this.templateW = tw; this.templateH = th; }
    }

    public static Bitmap toBitmap(int[] argb, int width, int height) {
        return Bitmap.createBitmap(argb, width, height, Bitmap.Config.ARGB_8888);
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

        // несколько масштабов шаблона
        float[] scales = {1.0f, 0.9f, 0.8f, 0.7f, 1.1f, 1.2f, 1.3f, 0.6f, 1.4f};
        for (float sc : scales) {
            int nw = Math.max(2, (int)(tw * sc));
            int nh = Math.max(2, (int)(th * sc));
            if (nw >= sw || nh >= sh) continue;
            Bitmap r = Bitmap.createScaledBitmap(template, nw, nh, true);
            int[] t = new int[nw * nh];
            r.getPixels(t, 0, nw, 0, 0, nw, nh);
            r.recycle();

            for (int y = 0; y + nh <= sh; y += 2) {
                for (int x = 0; x + nw <= sw; x += 2) {
                    float diff = 0f;
                    int cnt = 0;
                    for (int j = 0; j < nh; j++) {
                        int sRow = (y + j) * sw + x;
                        int tRow = j * nw;
                        for (int i = 0; i < nw; i++) {
                            int sp = s[sRow + i];
                            int tp = t[tRow + i];
                            long dr = Math.abs(((sp >> 16) & 0xff) - ((tp >> 16) & 0xff));
                            long dg = Math.abs(((sp >> 8) & 0xff) - ((tp >> 8) & 0xff));
                            long db = Math.abs((sp & 0xff) - (tp & 0xff));
                            diff += dr + dg + db;
                            cnt++;
                        }
                    }
                    if (cnt == 0) continue;
                    float avg = diff / cnt;              // 0..765
                    float score = 1f - avg / 765f;       // 0..1
                    if (score > bestScore) {
                        bestScore = score;
                        bestX = x + nw / 2;
                        bestY = y + nh / 2;
                        bestNw = nw;
                        bestNh = nh;
                    }
                }
            }
        }
        if (bestScore < 0f) return null;
        return new Match(bestX, bestY, bestScore, bestNw, bestNh);
    }
}