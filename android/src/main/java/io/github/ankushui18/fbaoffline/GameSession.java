package io.github.ankushui18.fbaoffline;

import android.content.Context;
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
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicIntegerArray;
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
        void waiting(GameSession session, boolean waiting);
    }

    private final SurfaceHolder surface;
    private final File rom, systemDir, saveDir, cover;
    private final Context app;
    final PatchStore.Entry patch;
    final GameLink link;
    private final int launchSlot;
    private volatile int[] controls = new int[4];
    final StateStore states;
    private final ArrayBlockingQueue<Runnable> commands = new ArrayBlockingQueue<>(8);
    private final Callbacks callbacks;
    final AtomicInteger touch = new AtomicInteger();
    final AtomicIntegerArray keys = new AtomicIntegerArray(4);
    final AtomicIntegerArray stick = new AtomicIntegerArray(4);
    volatile int touchPort, turboMask, turboRate = 10, displayMode, frameSkip, measuredFps;
    volatile int filter;
    volatile boolean gpu, mirrorHardware;
    private boolean gpuActive, gpuUnavailable, hqxUnavailable;
    volatile boolean foreground, paused, soundAllowed;
    private volatile boolean stopping;
    private Bitmap frame;
    private final Paint paint = new Paint();
    private final RectF destination = new RectF();

    GameSession(Context context, SurfaceHolder surface, File rom, File systemDir, File saveDir, File cover,
                PatchStore.Entry patch, int launchSlot, GameLink link, Callbacks callbacks) {
        app = context.getApplicationContext();
        this.patch = patch;
        this.link = link;
        this.launchSlot = launchSlot;
        states = new StateStore(new File(app.getNoBackupFilesDir(), "states"), rom, patch == null ? null : patch.id);
        this.cover = patch == null ? cover : patch.preview();
        this.surface = surface;
        this.rom = rom;
        this.systemDir = systemDir;
        this.saveDir = patch == null ? saveDir : new File(saveDir, "ips/" + patch.id);
        this.callbacks = callbacks;
        paint.setFilterBitmap(false);
    }

    String romName() { return rom.getName(); }
    File cover() { return cover; }
    int inputMask(int port) { return controls[port]; }
    void start() { WORKER.execute(this); }
    void stop() {
        stopping = true;
        commands.clear();
        if (link != null) link.stop();
    }
    void resetInputs() {
        touch.set(0);
        for (int i = 0; i < 4; i++) { keys.set(i, 0); stick.set(i, 0); }
    }
    boolean post(Runnable task) { return !stopping && commands.offer(task); }

    void saveSlot(int slot) throws IOException {
        states.save(slot, Core.captureState());
        File preview = states.preview(slot);
        try { writeScreenshot(preview); }
        catch (IOException ignored) { preview.delete(); /* The state is already saved. */ }
    }

    void loadSlot(int slot) throws IOException {
        if (!Core.restoreState(states.load(slot))) throw new IOException("FBNeo couldn't restore this state.");
    }

    void screenshot() throws IOException { writeScreenshot(cover); }

    private void writeScreenshot(File dest) throws IOException {
        if (!refreshFrame(0)) throw new IOException("No game frame is available yet.");
        int rotation = Core.rotation();
        boolean vertical = (rotation & 1) != 0;
        float aspect = gameAspect(rotation);
        int w = aspect >= 1 ? 640 : Math.max(1, Math.round(480 * aspect));
        int h = aspect >= 1 ? Math.max(1, Math.round(640 / aspect)) : 480;
        Bitmap shot = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        try {
            Canvas c = new Canvas(shot);
            c.drawColor(Color.BLACK);
            c.translate(w / 2f, h / 2f);
            c.rotate(-90 * rotation);
            float dw = (vertical ? h : w) / 2f, dh = (vertical ? w : h) / 2f;
            c.drawBitmap(frame, null, new RectF(-dw, -dh, dw, dh), new Paint());
            ImageFiles.write(dest, shot);
        } finally { shot.recycle(); }
    }

    private long input(long tick, double fps) {
        int touchMask = touch.get();
        // Turbofire only pulses the selected buttons while held; directions never repeat.
        int wave = Math.max(2, (int) Math.round(fps / Math.max(1, turboRate)));
        long combined = 0;
        int mirror = 0;
        if (mirrorHardware) for (int port = 0; port < 4; port++) mirror |= keys.get(port) | stick.get(port);
        for (int port = 0; port < 4; port++) {
            int bits = mirror | keys.get(port) | stick.get(port) | (port == touchPort ? touchMask : 0);
            if (tick % wave >= (wave + 1) / 2) bits &= ~turboMask;
            combined |= (long) (bits & 0xffff) << (port * 16);
        }
        return combined;
    }

    @Override public void run() {
        if (stopping) return;
        gpuActive = gpu;
        boolean started = false;
        String error = null;
        AudioTrack audio = null;
        File definition = null, isolated = null;
        try {
            File[] leftovers = app.getCacheDir().listFiles((dir, name) -> name.startsWith("ips-run-") || name.startsWith("link-run-"));
            if (leftovers != null) for (File temp : leftovers) LocalFiles.deleteTree(temp);
            File saves = saveDir;
            if (link != null) {
                // A fresh battery/EEPROM and system directory on each device; do not read or alter solo progress.
                isolated = Files.createTempDirectory(app.getCacheDir().toPath(), "link-run-").toFile();
                saves = new File(isolated, "save");
            } else if (patch != null) {
                // Only this session can see the generated override. FBNeo still opens the original archive.
                isolated = Files.createTempDirectory(app.getCacheDir().toPath(), "ips-run-").toFile();
                PatchStore.stage(rom, patch, new File(isolated, "fbneo/patched/" + rom.getName()));
            }
            File system = isolated == null ? systemDir : isolated;
            Files.createDirectories(saves.toPath());
            if (stopping) return;
            if (link == null) try {
                definition = GameCatalog.stageCheats(app, system, rom);
            } catch (IOException ignored) { /* A catalog/cache error must not block playing. */ }
            // FBNeo reloads the INI on variable changes and reset, not just during discovery.
            error = Core.open(rom.getAbsolutePath(), system.getAbsolutePath(), saves.getAbsolutePath(), patch != null, link != null);
            if (error != null) return;
            started = true;
            if (stopping) return;
            short[] samples = new short[8192];
            // The pinned core publishes the real digital mappings on its first input poll.
            // Do a neutral frame before restoring a selected slot or beginning a synchronized link.
            if (Core.step(0, samples) < 0) throw new IOException("Couldn't prepare this FBNeo driver.");
            if (launchSlot > 0) loadSlot(launchSlot);
            int[] masks = new int[4];
            for (int i = 0; i < masks.length; i++) masks[i] = Core.inputMask(i);
            controls = masks;
            if (link != null) {
                if (masks[1] == 0) throw new IOException("This set doesn't expose a second digital player.");
                link.connect(rom);
                if (stopping) return;
            }
            callbacks.ready(this);
            boolean playing = false;
            int audioRate = 0;
            long nextFrame = 0, tick = 0, rateStart = System.nanoTime();
            int rateFrames = 0;
            long lastDraw = -5, checkpoint = link == null ? 0 : Core.linkCheckpoint();
            boolean peerWaiting = false;
            while (!stopping) {
                Runnable task = commands.poll();
                if (task != null) { task.run(); nextFrame = 0; continue; }
                boolean waiting = !foreground || paused || !surface.getSurface().isValid();
                if (nextFrame == 0) nextFrame = System.nanoTime();
                double fps = Core.fps();
                if (!(fps >= 10 && fps <= 240)) fps = 60;
                long pad = waiting ? 0 : input(tick, fps);
                if (link != null) {
                    pad = link.exchange((int) (pad >>> (16 * link.player())), waiting, checkpoint);
                    boolean other = !waiting && pad < 0;
                    if (other != peerWaiting) { peerWaiting = other; callbacks.waiting(this, other); }
                    waiting = pad < 0;
                    if (stopping) break;
                }
                if (waiting) {
                    if (playing) { silence(audio); playing = false; }
                    nextFrame = 0;
                    measuredFps = rateFrames = 0;
                    rateStart = System.nanoTime();
                    LockSupport.parkNanos(20_000_000);
                    continue;
                }
                long period = (long) (1_000_000_000 / fps);
                tick++;
                int count = Core.step(pad, samples);
                if (count < 0) { error = "The emulator stopped this game."; break; }
                if (link != null) checkpoint = tick == 1 || tick % 120 == 0 ? Core.linkCheckpoint() : 0;
                int skip = frameSkip;
                // Auto mode can skip a late draw, but never skips core steps or audio.
                if (skip < 0 ? tick - lastDraw >= 5 || System.nanoTime() < nextFrame + period : tick % (skip + 1) == 0) {
                    render();
                    lastDraw = tick;
                }
                rateFrames++;
                long elapsed = System.nanoTime() - rateStart;
                if (elapsed >= 1_000_000_000L) {
                    measuredFps = (int) Math.round(rateFrames * 1_000_000_000.0 / elapsed);
                    rateFrames = 0;
                    rateStart += elapsed;
                }
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
                nextFrame += period;
                long remaining = nextFrame - System.nanoTime();
                if (remaining > 0) LockSupport.parkNanos(remaining);
                else if (remaining < -50_000_000) nextFrame = 0; // Don't run a burst after a stall.
            }
        } catch (IOException unavailable) {
            error = unavailable.getMessage() == null ? "Couldn't prepare the game files." : unavailable.getMessage();
        } catch (LinkageError incompatible) {
            error = "Couldn't load the FBNeo engine on this device.";
        } catch (RuntimeException unavailable) {
            error = "Couldn't render or play this set on the device.";
        } finally {
            if (link != null) link.stop();
            if (audio != null) { silence(audio); audio.release(); }
            if (started) Core.close();
            if (definition != null) definition.delete();
            LocalFiles.deleteTree(isolated);
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

    private boolean refreshFrame(int mode) {
        int size = Core.frameSize(), w = size >>> 16, h = size & 0xffff;
        if (w <= 0 || h <= 0) return false;
        int scale = !hqxUnavailable && mode >= 2 && mode <= 4 && w <= 2048 / mode && h <= 2048 / mode && w * h * mode * mode <= 3 * 1024 * 1024 ? mode : 1;
        w *= scale; h *= scale;
        if (frame == null || w != frame.getWidth() || h != frame.getHeight()) {
            if (frame != null) frame.recycle();
            frame = null;
            try { frame = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); }
            catch (OutOfMemoryError exhausted) {
                if (scale == 1) throw exhausted;
                hqxUnavailable = true;
                return refreshFrame(0);
            }
        }
        if (Core.copyFrame(frame, scale)) return true;
        if (scale == 1) return false;
        hqxUnavailable = true;
        return refreshFrame(0);
    }

    private void render() {
        if (!refreshFrame(filter)) return;
        int rawSize = Core.frameSize(), w = rawSize >>> 16, h = rawSize & 0xffff;
        int rotation = Core.rotation();
        boolean sideways = (rotation & 1) != 0;
        float aspect = gameAspect(rotation);
        paint.setFilterBitmap(filter == 1);
        Canvas canvas = null;
        if (gpuActive && !gpuUnavailable) try { canvas = surface.lockHardwareCanvas(); }
        catch (IllegalStateException | UnsupportedOperationException unavailable) { gpuUnavailable = true; }
        if (canvas == null) canvas = surface.lockCanvas();
        if (canvas == null) return;
        try {
            canvas.drawColor(Color.BLACK);
            int mode = displayMode;
            if (mode == 2) aspect = 4f / 3f;
            else if (mode == 3) aspect = 3f / 4f;
            float dw = Math.min(canvas.getWidth(), canvas.getHeight() * aspect), dh = dw / aspect;
            if (mode == 1) { dw = canvas.getWidth(); dh = canvas.getHeight(); }
            else if (mode == 4) {
                int rawW = sideways ? h : w, rawH = sideways ? w : h;
                float factor = Math.min((float) canvas.getWidth() / rawW, (float) canvas.getHeight() / rawH);
                if (factor > 1) factor = (int) factor;
                dw = rawW * factor; dh = rawH * factor;
            }
            float halfW = (sideways ? dh : dw) / 2, halfH = (sideways ? dw : dh) / 2;
            canvas.translate(canvas.getWidth() / 2f, canvas.getHeight() / 2f);
            canvas.rotate(-90 * rotation);
            destination.set(-halfW, -halfH, halfW, halfH);
            canvas.drawBitmap(frame, null, destination, paint);
        } finally {
            surface.unlockCanvasAndPost(canvas);
        }
    }
    private float gameAspect(int rotation) {
        float aspect = Core.aspect();
        if (aspect >= .2f && aspect <= 8f) return aspect;
        return (rotation & 1) != 0 ? (float) frame.getHeight() / frame.getWidth() : (float) frame.getWidth() / frame.getHeight();
    }
}
