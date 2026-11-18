package io.github.ankushui18.fbaoffline;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Process;
import android.view.SurfaceHolder;

/** Per-surface presenter. Only offer() runs on the core thread and reads the live framebuffer. */
final class FrameRenderer implements SurfaceHolder.Callback {
    private static final int ORIGINAL_SIZE = 5;
    private static final int HQX_PIXELS = 3 * 1024 * 1024;

    private static final class Frame {
        Bitmap pixels;
        int mode, filter, rotation;
        float aspect;
        long stamp;
    }

    private final SurfaceHolder surface;
    private final LatestFrame<Frame> frames = new LatestFrame<>(new Frame(), new Frame());
    // Only Canvas access is serialized with surface destruction, never scaling or the core.
    private final Object canvasLock = new Object();
    private volatile boolean surfaceReady, failed;
    private volatile long posted;
    // Presenter-thread only.
    private final Paint paint = new Paint();
    private final RectF destination = new RectF();
    private Bitmap filtered;
    private boolean gpuUnavailable, hqxUnavailable;

    FrameRenderer(SurfaceHolder surface) {
        this.surface = surface;
        surface.addCallback(this); // The holder belongs to this game screen only.
    }

    void start(boolean gpu) { new Thread(() -> run(gpu), "arcade-video").start(); }
    void discard() { frames.discard(); }
    void close() { frames.close(); }
    boolean isReady() { return surfaceReady; }
    boolean failed() { return failed; }
    long posted() { return posted; }

    @Override public void surfaceCreated(SurfaceHolder holder) { surfaceReady = true; }
    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
        discard();
        // If a canvas is already locked, let it post before Android releases the surface.
        synchronized (canvasLock) { /* Canvas lock/draw/post is now drained. */ }
    }

    boolean offer(int mode, int filter) {
        long stamp = frames.generation();
        if (!surfaceReady) return false;
        Frame frame = frames.beginWrite(stamp);
        if (frame == null) return false;
        int size = Core.frameSize(), w = size >>> 16, h = size & 0xffff;
        if (w == 0 || h == 0) return false;
        frame.pixels = reuse(frame.pixels, w, h);
        if (!Core.copyFrame(frame.pixels)) return false;
        frame.rotation = Core.rotation();
        frame.aspect = aspect(w, h, frame.rotation, Core.aspect());
        frame.mode = mode;
        frame.filter = mode == ORIGINAL_SIZE ? 0 : filter;
        frame.stamp = stamp;
        return frames.publish(frame, stamp);
    }

    private boolean current(Frame frame) { return surfaceReady && frame.stamp == frames.generation(); }

    private void run(boolean gpu) {
        try {
            // Under CPU contention, favor the core and audio over optional video work.
            try { Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT + 2 * Process.THREAD_PRIORITY_LESS_FAVORABLE); }
            catch (SecurityException ignored) { /* Still render on a separate thread. */ }
            Frame frame;
            while ((frame = frames.take()) != null) {
                try {
                    if (!current(frame)) continue;
                    Bitmap pixels = scale(frame);
                    synchronized (canvasLock) {
                        if (current(frame) && draw(frame, pixels, gpu)) posted++;
                    }
                } finally { frames.endRead(); }
            }
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            failed = true;
        } catch (RuntimeException | LinkageError | OutOfMemoryError unavailable) {
            failed = true;
        } finally {
            frames.close();
            // Let Android release bitmaps once Canvas/GPU no longer holds a reference.
            filtered = null;
        }
    }

    private Bitmap scale(Frame frame) {
        Bitmap raw = frame.pixels;
        int w = raw.getWidth(), h = raw.getHeight(), factor = frame.filter;
        if (!hqxUnavailable && factor >= 2 && factor <= 4 && w <= 2048 / factor && h <= 2048 / factor
                && w * h * factor * factor <= HQX_PIXELS) {
            try {
                filtered = reuse(filtered, w * factor, h * factor);
                if (Core.scaleFrame(raw, filtered, factor)) return filtered;
            } catch (OutOfMemoryError exhausted) { /* Fall back to sharp for this session. */ }
            hqxUnavailable = true;
        }
        filtered = null;
        return raw;
    }

    private boolean draw(Frame frame, Bitmap pixels, boolean gpu) {
        Canvas canvas = null;
        if (gpu && !gpuUnavailable) try { canvas = surface.lockHardwareCanvas(); }
        catch (IllegalStateException | UnsupportedOperationException unavailable) { gpuUnavailable = true; }
        if (canvas == null) {
            canvas = surface.lockCanvas();
            if (canvas == null) return false;
            gpuUnavailable = true; // Once drawing in software, keep using that connection.
        }
        try {
            canvas.drawColor(Color.BLACK);
            int mode = frame.mode, rotation = frame.rotation;
            boolean sideways = (rotation & 1) != 0;
            float aspect = mode == 2 ? 4f / 3f : mode == 3 ? 3f / 4f : frame.aspect;
            float dw = Math.min(canvas.getWidth(), canvas.getHeight() * aspect), dh = dw / aspect;
            if (mode == 1) { dw = canvas.getWidth(); dh = canvas.getHeight(); }
            else if (mode == 4 || mode == ORIGINAL_SIZE) {
                int w = frame.pixels.getWidth(), h = frame.pixels.getHeight();
                int rawW = sideways ? h : w, rawH = sideways ? w : h;
                float factor = Math.min((float) canvas.getWidth() / rawW, (float) canvas.getHeight() / rawH);
                if (factor > 1) factor = mode == ORIGINAL_SIZE ? 1 : (int) factor;
                dw = rawW * factor; dh = rawH * factor;
            }
            float halfW = (sideways ? dh : dw) / 2, halfH = (sideways ? dw : dh) / 2;
            float left = (canvas.getWidth() - dw) / 2, top = (canvas.getHeight() - dh) / 2;
            if (mode == ORIGINAL_SIZE) { left = (int) left; top = (int) top; }
            canvas.translate(left + dw / 2, top + dh / 2);
            canvas.rotate(-90 * rotation);
            destination.set(-halfW, -halfH, halfW, halfH);
            paint.setFilterBitmap(frame.filter == 1);
            canvas.drawBitmap(pixels, null, destination, paint);
        } finally { surface.unlockCanvasAndPost(canvas); }
        return true;
    }

    private static Bitmap reuse(Bitmap bitmap, int w, int h) {
        if (bitmap != null && bitmap.getWidth() == w && bitmap.getHeight() == h) return bitmap;
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888, false); // Core colors are opaque.
    }

    static float aspect(int width, int height, int rotation, float aspect) {
        if (aspect >= .2f && aspect <= 8f) return aspect;
        return (rotation & 1) != 0 ? (float) height / width : (float) width / height;
    }
}
