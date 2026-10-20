package com.vk.emojiclicker;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;

/**
 * Служба доступности: эмулирует настоящие тапы по экрану в любом приложении (в т.ч. VK).
 * Диспетчер жестов (dispatchGesture) работает как реальное касание.
 */
public class VkClickerService extends AccessibilityService {

    private static VkClickerService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public static VkClickerService getInstance() {
        return instance;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // nothing to do; we only use the service for gestures
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    /** Эмулирует одиночный тап по экранным координатам. */
    public void tap(int x, int y) {
        if (x < 0 || y < 0) return;
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, 80); // 80ms press
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(stroke)
                .build();
        dispatchGesture(gesture, null, handler);
    }

    /** Эмулирует тап с точностью до Rect (центр прямоугольника). */
    public void tapRect(Rect rect) {
        if (rect == null) return;
        tap(rect.centerX(), rect.centerY());
    }
}