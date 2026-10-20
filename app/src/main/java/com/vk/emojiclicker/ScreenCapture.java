package com.vk.emojiclicker;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.nio.ByteBuffer;

/**
 * Захват экрана через MediaProjection. Позволяет получать растровый скриншот
 * ЛЮБОГО приложения (в т.ч. настоящего VK) для поиска эмодзи.
 */
public class ScreenCapture {

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private HandlerThread captureThread;
    private Handler captureHandler;
    private int width, height, density;

    /** Запускает захват; data приходит из onActivityResult по ACTION_MEDIA_PROJECTION. */
    public boolean start(Context ctx, int resultCode, Intent data) {
        if (data == null) return false;
        MediaProjectionManager mpm = (MediaProjectionManager) ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (mpm == null) return false;
        mediaProjection = mpm.getMediaProjection(resultCode, data);
        if (mediaProjection == null) return false;

        WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        width = dm.widthPixels;
        height = dm.heightPixels;
        density = dm.densityDpi;

        captureThread = new HandlerThread("capture");
        captureThread.start();
        captureHandler = new Handler(captureThread.getLooper());

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        virtualDisplay = mediaProjection.createVirtualDisplay("capture", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader.getSurface(), null, captureHandler);
        return true;
    }

    public boolean isRunning() {
        return mediaProjection != null && virtualDisplay != null;
    }

    /** Делает один скриншот текущего экрана (синхронно, ~50-150ms). */
    public Bitmap grab() {
        if (mediaProjection == null || imageReader == null) return null;
        try {
            final Image image = imageReader.acquireLatestImage();
            if (image == null) return null;
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();
            int rowPadding = rowStride - pixelStride * width;
            Bitmap bmp = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
            bmp.copyPixelsFromBuffer(buffer);
            image.close();
            if (rowPadding > 0) {
                // crop right padding
                Bitmap crop = Bitmap.createBitmap(bmp, 0, 0, width, height);
                bmp.recycle();
                return crop;
            }
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    /** Останавливает захват (баннер MediaProjection исчезает). */
    public void stop() {
        try {
            if (virtualDisplay != null) { virtualDisplay.release(); virtualDisplay = null; }
            if (imageReader != null) { imageReader.close(); imageReader = null; }
            if (mediaProjection != null) { mediaProjection.stop(); mediaProjection = null; }
            if (captureThread != null) { captureThread.quitSafely(); captureThread = null; }
        } catch (Exception ignored) {}
    }
}