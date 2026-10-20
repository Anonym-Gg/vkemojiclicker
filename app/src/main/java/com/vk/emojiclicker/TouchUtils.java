package com.vk.emojiclicker;

import android.content.Context;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

/**
 * Эмуляция тапа по координатам внутри своего приложения (собственный WebView).
 * Не требует Accessibility — работает в пределах собственного окна.
 */
public class TouchUtils {

    public static void tap(Context ctx, View target, int x, int y) {
        long downTime = SystemClock.uptimeMillis();
        // 1. DOWN
        MotionEvent down = MotionEvent.obtain(downTime, downTime,
                MotionEvent.ACTION_DOWN, x, y, 0);
        target.dispatchTouchEvent(down);
        down.recycle();
        // 2. UP after small pause
        long upTime = downTime + 40;
        MotionEvent up = MotionEvent.obtain(downTime, upTime,
                MotionEvent.ACTION_UP, x, y, 0);
        target.dispatchTouchEvent(up);
        up.recycle();
    }
}