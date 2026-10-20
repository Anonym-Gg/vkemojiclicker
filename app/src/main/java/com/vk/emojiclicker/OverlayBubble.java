package com.vk.emojiclicker;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Плавающий пузырь ⚔️ поверх ВСЕХ приложений (в т.ч. настоящего VK).
 * Тап по пузырю — показывает/прячет панель управления оверлея.
 * Перетаскивание — двигает пузырь по экрану.
 */
public class OverlayBubble {

    private final Context ctx;
    private final WindowManager wm;
    private final WindowManager.LayoutParams bubbleParams;
    private final WindowManager.LayoutParams panelParams;
    private final TextView bubble;
    private final FrameLayout panel;
    private boolean panelVisible = false;
    private final Runnable onStart;     // запуск поиска (из Activity)
    private final Runnable onStop;      // стоп
    private final Runnable onSettings;  // открыть настройки в Activity

    private static int overlayType() {
        return android.os.Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    public OverlayBubble(Context context, Runnable onStart, Runnable onStop, Runnable onSettings) {
        this.ctx = context;
        this.onStart = onStart;
        this.onStop = onStop;
        this.onSettings = onSettings;
        this.wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        int dp = (int) context.getResources().getDisplayMetrics().density;

        bubble = new TextView(context);
        bubble.setText("⚔️");
        bubble.setTextSize(24);
        bubble.setGravity(Gravity.CENTER);
        bubble.setBackgroundColor(0xCC222222);
        bubble.setTextColor(Color.WHITE);
        bubbleParams = new WindowManager.LayoutParams(
                56 * dp, 56 * dp,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = 10 * dp;
        bubbleParams.y = 200 * dp;
        bubble.setOnClickListener(v -> togglePanel());
        setupDrag();

        panel = new FrameLayout(context);
        panel.setBackgroundColor(0xEE222222);
        Button startBtn = new Button(context);
        startBtn.setText("ВКЛ (авто-цикл)");
        startBtn.setTextSize(12);
        startBtn.setOnClickListener(v -> onStart.run());
        Button stopBtn = new Button(context);
        stopBtn.setText("Стоп");
        stopBtn.setTextSize(12);
        stopBtn.setOnClickListener(v -> onStop.run());
        Button settBtn = new Button(context);
        settBtn.setText("Настройки");
        settBtn.setTextSize(12);
        settBtn.setOnClickListener(v -> onSettings.run());
        Button hideBtn = new Button(context);
        hideBtn.setText("Скрыть");
        hideBtn.setTextSize(12);
        hideBtn.setOnClickListener(v -> togglePanel());

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(startBtn);
        row.addView(stopBtn);
        row.addView(settBtn);
        row.addView(hideBtn);
        panel.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.setVisibility(View.GONE);

        panelParams = new WindowManager.LayoutParams(
                340 * dp, ViewGroup.LayoutParams.WRAP_CONTENT,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        panelParams.gravity = Gravity.TOP | Gravity.START;
        panelParams.x = 10 * dp;
        panelParams.y = 270 * dp;
    }

    public void show() {
        try {
            wm.addView(bubble, bubbleParams);
            wm.addView(panel, panelParams);
        } catch (Exception ignored) {}
    }

    public void hide() {
        try {
            wm.removeView(bubble);
            wm.removeView(panel);
        } catch (Exception ignored) {}
    }

    private void togglePanel() {
        panelVisible = !panelVisible;
        panel.setVisibility(panelVisible ? View.VISIBLE : View.GONE);
    }

    // move the bubble anywhere on screen (WindowManager overlay)
    private void setupDrag() {
        bubble.setOnTouchListener(new View.OnTouchListener() {
            float startX, startY;
            int startPx, startPy;
            boolean moved;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getRawX();
                        startY = event.getRawY();
                        startPx = bubbleParams.x;
                        startPy = bubbleParams.y;
                        moved = false;
                        return false; // allow click
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - startX;
                        float dy = event.getRawY() - startY;
                        if (Math.abs(dx) > 6 || Math.abs(dy) > 6) moved = true;
                        if (moved) {
                            bubbleParams.x = startPx + (int) dx;
                            bubbleParams.y = startPy + (int) dy;
                            wm.updateViewLayout(bubble, bubbleParams);
                            return true; // drag consumes the gesture
                        }
                        return false;
                    case MotionEvent.ACTION_UP:
                        return moved; // if dragged, don't fire click; else let click happen
                }
                return false;
            }
        });
    }
}