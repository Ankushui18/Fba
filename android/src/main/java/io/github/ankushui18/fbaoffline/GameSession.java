package io.github.ankushui18.fbaoffline;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.view.SurfaceHolder;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/** Runs the native core, audio and surface rendering away from the Android UI thread. */
final class GameSession implements Runnable {
    static final AudioAttributes AUDIO = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> new Thread(task, "arcade-core"));

    interface Callbacks {
        void ready(GameSession session);
        void failed(GameSession session, String message);
    }

    private final SurfaceHolder surface;
    private final File rom, systemDir, saveDir;
    private final Callbacks callbacks;
    final AtomicInteger touch = new AtomicInteger();
    final AtomicInteger keys = new AtomicInteger();
    final AtomicInteger stick = new AtomicInteger();
    volatile boolean foreground, paused, soundAllowed;
    private volatile boolean stopping;
    private Bitmap frame;
    private final Paint paint = new Paint();
    private final RectF destination = new RectF();

    GameSession(SurfaceHolder surface, File rom, File systemDir, File saveDir, Callbacks callbacks) {
        this.surface = surface;
        this.rom = rom;
        this.systemDir = systemDir;
        this.saveDir = saveDir;
        this.callbacks = callbacks;
        paint.setFilterBitmap(false);
    }

    String romName() { return rom.getName(); }
    void start() { WORKER.execute(this); }
    void stop() { stopping = true; }
    void resetInputs() { touch.set(0); keys.set(0); stick.set(0); }

    @Override public void run() {
        if (stopping) return;
        boolean started = false;
        String error = null;
        AudioTrack audio = null;
        try {
            error = Core.open(rom.getAbsolutePath(), systemDir.getAbsolutePath(), saveDir.getAbsolutePath());
            if (error != null) return;
            started = true;
            if (stopping) return;
            callbacks.ready(this);
            short[] samples = new short[8192];
            boolean playing = false;
            int audioRate = 0;
            long nextFrame = 0;
            while (!stopping) {
                if (!foreground || paused || !surface.getSurface().isValid()) {
                    if (playing) { silence(audio); playing = false; }
                    nextFrame = 0;
                    LockSupport.parkNanos(20_000_000);
                    continue;
                }
                if (nextFrame == 0) nextFrame = System.nanoTime();
                int count = Core.step(touch.get() | keys.get() | stick.get(), samples);
                if (count < 0) { error = "The emulator stopped this game."; break; }
                render();
                if (soundAllowed) {
                    int rate = Core.sampleRate();
                    if (rate != audioRate) {
                        if (audio != null) audio.release();
                        audio = openAudio(rate);
                        playing = false;
                        audioRate = rate;
                    }
                    if (audio != null && count > 0) {
                        try {
                            if (!playing) { audio.play(); playing = true; }
                            if (audio.write(samples, 0, count, AudioTrack.WRITE_NON_BLOCKING) < 0) {
                                audio.release(); audio = null; playing = false;
                            }
                        } catch (IllegalStateException unavailable) {
                            audio.release(); audio = null; playing = false;
                        }
                    }
                } else if (playing) {
                    silence(audio);
                    playing = false;
                }
                double fps = Core.fps();
                if (!(fps >= 10 && fps <= 240)) fps = 60;
                nextFrame += (long) (1_000_000_000 / fps);
                long remaining = nextFrame - System.nanoTime();
                if (remaining > 0) LockSupport.parkNanos(remaining);
                else if (remaining < -50_000_000) nextFrame = 0; // Don't run a burst after a stall.
            }
        } catch (LinkageError incompatible) {
            error = "Couldn't load the FBNeo engine on this device.";
        } catch (RuntimeException unavailable) {
            error = "Couldn't render or play this set on the device.";
        } finally {
            if (audio != null) { silence(audio); audio.release(); }
            if (started) Core.close();
            if (frame != null) frame.recycle();
            if (error != null && !stopping) callbacks.failed(this, error);
        }
    }

    private static AudioTrack openAudio(int rate) {
        if (rate == 0) return null;
        int channels = AudioFormat.CHANNEL_OUT_STEREO;
        int format = AudioFormat.ENCODING_PCM_16BIT;
        int min = AudioTrack.getMinBufferSize(rate, channels, format);
        if (min <= 0) return null;
        try {
            AudioTrack track = new AudioTrack.Builder().setAudioAttributes(AUDIO)
                    .setAudioFormat(new AudioFormat.Builder().setSampleRate(rate).setChannelMask(channels)
                            .setEncoding(format).build())
                    .setBufferSizeInBytes(Math.max(min * 2, rate / 10 * 4))
                    .setTransferMode(AudioTrack.MODE_STREAM).build();
            if (track.getState() == AudioTrack.STATE_INITIALIZED) return track;
            track.release();
        } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException unavailable) {
            // Video is still usable without audio on a device that rejects this track.
        }
        return null;
    }

    private static void silence(AudioTrack track) {
        try { track.pause(); track.flush(); }
        catch (IllegalStateException ignored) { /* The route may have disconnected. */ }
    }

    private void render() {
        int size = Core.frameSize();
        int w = size >>> 16, h = size & 0xffff;
        if (w == 0 || h == 0) return;
        if (frame == null || frame.getWidth() != w || frame.getHeight() != h) {
            if (frame != null) frame.recycle();
            frame = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            frame.setHasAlpha(false);
        }
        if (!Core.copyFrame(frame)) return;
        int rotation = Core.rotation();
        boolean sideways = (rotation & 1) != 0;
        float aspect = Core.aspect();
        if (!(aspect >= 0.2f && aspect <= 8f)) aspect = sideways ? (float) h / w : (float) w / h;
        Canvas canvas = surface.lockCanvas();
        if (canvas == null) return;
        try {
            canvas.drawColor(Color.BLACK);
            float dw = Math.min(canvas.getWidth(), canvas.getHeight() * aspect);
            float dh = dw / aspect;
            float halfW = (sideways ? dh : dw) / 2, halfH = (sideways ? dw : dh) / 2;
            canvas.translate(canvas.getWidth() / 2f, canvas.getHeight() / 2f);
            canvas.rotate(-90 * rotation);
            destination.set(-halfW, -halfH, halfW, halfH);
            canvas.drawBitmap(frame, null, destination, paint);
        } finally {
            surface.unlockCanvasAndPost(canvas);
        }
    }
}
