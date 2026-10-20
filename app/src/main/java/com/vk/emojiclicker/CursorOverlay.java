package com.vk.emojiclicker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/**
 * Оверлей-курсор: рисует синий прицел/точку в найденной позиции,
 * чтобы было видно, куда приложение собирается нажать.
 */
public class CursorOverlay extends View {
    private float cx = -1, cy = -1;
    private final Paint fill = new Paint();
    private final Paint ring = new Paint();

    public CursorOverlay(Context context) {
        super(context);
        fill.setColor(0xAA2196F3);
        fill.setStyle(Paint.Style.FILL);
        ring.setColor(0xFFFFFFFF);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(4);
    }

    public void showAt(int x, int y) {
        cx = x; cy = y;
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (cx < 0) return;
        canvas.drawCircle(cx, cy, 18, ring);
        canvas.drawCircle(cx, cy, 12, fill);
    }
}