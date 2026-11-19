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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

/** Runs the native core and audio. Hands raw snapshots to a separate presenter. */
final class GameSession implements Runnable {
    static final AudioAttributes AUDIO = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> new Thread(task, "arcade-core"));

    interface Callbacks {
        void ready(GameSession session);
        void failed(GameSession session, String message);
        void waiting(GameSession session, boolean waiting);
    }

    private static final int[] NO_KEYS = new int[0];
    private final FrameRenderer renderer;
    private final File rom, systemDir, saveDir, cover;
    private final Context app;
    final ConsoleSystem console;
    volatile int[] keyboard = NO_KEYS;
    private final GameCatalog catalog;
    final PatchStore.Entry patch;
    final GameLink link;
    final boolean continuing;
    private final int launchSlot;
    private final QuickResumeStore quickResume;
    private volatile int[] controls = new int[4];
    final StateStore states;
    private final ArrayBlockingQueue<Runnable> commands = new ArrayBlockingQueue<>(8);
    private final Callbacks callbacks;
    final AtomicInteger touch = new AtomicInteger();
    private final AtomicInteger inputReset = new AtomicInteger();
    private final AutoFire autoFire = new AutoFire();
    private int appliedInputReset;
    final AtomicIntegerArray keys = new AtomicIntegerArray(4);
    final AtomicIntegerArray stick = new AtomicIntegerArray(4);
    volatile int touchPort, turboMask, turboRate = 10, displayMode, frameSkip, measuredFps, drawnFps;
    volatile int filter;
    volatile boolean gpu, mirrorHardware;
    volatile boolean foreground, paused, soundAllowed;
    private volatile boolean stopping;

    GameSession(Context context, SurfaceHolder surface, File rom, File systemDir, File saveDir, GameCatalog catalog, File cover,
                PatchStore.Entry patch, int launchSlot, GameLink link, boolean continuing, Callbacks callbacks) {
        if (continuing && (link != null || launchSlot != 0)) throw new IllegalArgumentException("Quick Resume is a separate solo launch.");
        app = context.getApplicationContext();
        this.patch = patch;
        this.link = link;
        this.launchSlot = launchSlot;
        this.continuing = continuing;
        File privateData = app.getNoBackupFilesDir();
        states = new StateStore(new File(privateData, "states"), rom, patch == null ? null : patch.id);
        quickResume = new QuickResumeStore(privateData);
        this.catalog = catalog;
        this.cover = patch == null ? cover : patch.preview();
        renderer = new FrameRenderer(surface);
        this.rom = rom;
        console = ConsoleSystem.fromArchive(rom.getName());
        this.systemDir = systemDir;
        this.saveDir = patch == null ? saveDir : new File(saveDir, "ips/" + patch.id);
        this.callbacks = callbacks;
    }

    String romName() { return rom.getName(); }
    File cover() { return cover; }
    int inputMask(int port) { return controls[port]; }
    void start() { WORKER.execute(this); }

    /** Library I/O only: wait off the UI thread for any closing game to finish writing its saves. */
    static void awaitIdle() throws IOException {
        try { WORKER.submit(() -> {}).get(); }
        catch (InterruptedException cancelled) {
            Thread.currentThread().interrupt();
            LocalFiles.interrupted();
        } catch (ExecutionException unavailable) { throw new IOException("Couldn't finish closing the game.", unavailable); }
    }

    void stop() {
        stopping = true;
        renderer.close();
        commands.clear();
        if (link != null) link.stop();
    }
    void resetInputs() {
        touch.set(0);
        keyboard = NO_KEYS;
        for (int i = 0; i < 4; i++) { keys.set(i, 0); stick.set(i, 0); }
        inputReset.incrementAndGet();
    }
    boolean post(Runnable task) { return !stopping && commands.offer(task); }

    // Only explicit solo Exit enqueues this, after the screen has paused input and audio.
    // A failed capture leaves the game paused so the user can choose whether to exit.
    boolean exitWithQuickResume(Consumer<String> finished) {
        if (link != null) return false;
        return post(() -> {
            if (stopping) return;
            String error = null;
            try {
                quickResume.save(states, Core.captureState());
                stopping = true;
            } catch (IOException failed) {
                error = failed.getMessage() == null ? "Check available storage." : failed.getMessage();
            } catch (RuntimeException | OutOfMemoryError failed) {
                error = "The emulator couldn't capture this state.";
            }
            finished.accept(error);
        });
    }

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
        int size = Core.frameSize(), width = size >>> 16, height = size & 0xffff;
        if (width == 0 || height == 0) throw new IOException("No game frame is available yet.");
        // A screenshot/slot preview always uses the raw core image, not a presenter buffer.
        Bitmap raw = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888, false);
        try {
            if (!Core.copyFrame(raw)) throw new IOException("Couldn't capture this game frame.");
            int rotation = Core.rotation();
            boolean vertical = (rotation & 1) != 0;
            float aspect = FrameRenderer.aspect(width, height, rotation, Core.aspect());
            int w = aspect >= 1 ? 640 : Math.max(1, Math.round(480 * aspect));
            int h = aspect >= 1 ? Math.max(1, Math.round(640 / aspect)) : 480;
            Bitmap shot = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            try {
                Canvas c = new Canvas(shot);
                c.drawColor(Color.BLACK);
                c.translate(w / 2f, h / 2f);
                c.rotate(-90 * rotation);
                float dw = (vertical ? h : w) / 2f, dh = (vertical ? w : h) / 2f;
                c.drawBitmap(raw, null, new RectF(-dw, -dh, dw, dh), new Paint());
                ImageFiles.write(dest, shot);
            } finally { shot.recycle(); }
        } finally { raw.recycle(); }
    }

    private long input(double fps) {
        int reset = inputReset.get();
        if (reset != appliedInputReset) { autoFire.reset(); appliedInputReset = reset; }
        int touchMask = touch.get(), repeat = turboMask;
        int delay = Math.max(1, (int) Math.round(fps / 4));
        int wave = Math.max(2, (int) Math.round(fps / Math.max(1, turboRate)));
        long combined = 0;
        int mirror = 0;
        if (mirrorHardware) for (int port = 0; port < 4; port++) mirror |= keys.get(port) | stick.get(port);
        for (int port = 0; port < 4; port++) {
            int bits = mirror | keys.get(port) | stick.get(port) | (port == touchPort ? touchMask : 0);
            bits = autoFire.apply(port, bits, repeat, delay, wave);
            combined |= (long) (bits & 0xffff) << (port * 16);
        }
        return combined;
    }

    @Override public void run() {
        if (stopping) return;
        boolean gpuActive = gpu;
        boolean started = false;
        String error = null;
        AudioTrack audio = null;
        File definition = null, isolated = null, consoleRun = null;
        try {
            if (console != null && (link != null || patch != null)) throw new IOException("Console games currently support solo play without IPS variants.");
            File[] leftovers = app.getCacheDir().listFiles((dir, name) -> name.startsWith("ips-run-")
                    || name.startsWith("link-run-") || name.startsWith("nes-run-") || name.startsWith("console-run-"));
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
            // Check the snapshot before opening the driver; a mismatched ZIP must not boot or write solo battery data.
            byte[] resumeState = continuing ? quickResume.load(states) : null;
            if (stopping) return;
            File launchRom = rom;
            ConsoleStore.Launch staged = null;
            boolean patched = patch != null;
            if (console != null) {
                // The library ZIP name and fingerprint remain stable for saves and Quick Resume.
                consoleRun = Files.createTempDirectory(app.getCacheDir().toPath(), "console-run-").toFile();
                staged = catalog.consoleSets().stage(rom, consoleRun.toPath());
                launchRom = staged.rom;
                if (staged.patchedSystem != null) {
                    system = staged.patchedSystem;
                    saves = new File(saves, GameCatalog.setName(rom.getName()));
                    Files.createDirectories(saves.toPath());
                    patched = true;
                }
                if (stopping) return;
            }
            if (link == null) try {
                definition = GameCatalog.stageCheats(app, system, rom, staged == null ? null : staged.driver);
            } catch (IOException ignored) { /* A catalog/cache error must not block playing. */ }
            // FBNeo reloads the INI on variable changes and reset, not just during discovery.
            error = Core.open(launchRom.getAbsolutePath(), system.getAbsolutePath(), saves.getAbsolutePath(), patched, link != null);
            if (error != null) {
                if (staged != null) error += staged.firmwareHint;
                return;
            }
            started = true;
            if (stopping) return;
            short[] samples = new short[8192];
            // The pinned core publishes the real digital mappings on its first input poll.
            // Do a neutral frame before restoring a selected slot or beginning a synchronized link.
            if (Core.step(0, samples) < 0) throw new IOException("Couldn't prepare this FBNeo driver.");
            if (resumeState != null) {
                if (!Core.restoreState(resumeState)) throw new IOException("FBNeo couldn't restore this Quick Resume save.");
                resumeState = null;
            } else if (launchSlot > 0) loadSlot(launchSlot);
            int[] masks = new int[4];
            for (int i = 0; i < masks.length; i++) masks[i] = Core.inputMask(i);
            controls = masks;
            if (link != null) {
                if (masks[1] == 0) throw new IOException("This set doesn't expose a second digital player.");
                link.connect(rom);
                if (stopping) return;
            }
            renderer.start(gpuActive);
            callbacks.ready(this);
            boolean playing = false;
            int[] appliedKeys = NO_KEYS;
            int audioRate = 0;
            long nextFrame = 0, tick = 0, rateStart = System.nanoTime();
            int rateFrames = 0;
            long lastOffer = -5, lastPosted = 0, checkpoint = link == null ? 0 : Core.linkCheckpoint();
            boolean peerWaiting = false;
            while (!stopping) {
                if (renderer.failed()) { error = "Couldn't draw the game on this device."; break; }
                Runnable task = commands.poll();
                if (task != null) {
                    renderer.discard();
                    task.run();
                    nextFrame = 0;
                    continue;
                }
                boolean waiting = !foreground || paused || !renderer.isReady();
                if (nextFrame == 0) nextFrame = System.nanoTime();
                double fps = Core.fps();
                if (!(fps >= 10 && fps <= 240)) fps = 60;
                long pad = waiting ? 0 : input(fps);
                if (link != null) {
                    pad = link.exchange((int) (pad >>> (16 * link.player())), waiting, checkpoint);
                    boolean other = !waiting && pad < 0;
                    if (other != peerWaiting) { peerWaiting = other; callbacks.waiting(this, other); }
                    waiting = pad < 0;
                    if (stopping) break;
                }
                if (waiting) {
                    renderer.discard();
                    autoFire.reset();
                    if (playing) { silence(audio); playing = false; }
                    nextFrame = 0;
                    measuredFps = drawnFps = rateFrames = 0;
                    lastPosted = renderer.posted();
                    rateStart = System.nanoTime();
                    LockSupport.parkNanos(20_000_000);
                    continue;
                }
                long period = (long) (1_000_000_000 / fps);
                tick++;
                int[] pressed = keyboard;
                if (pressed != appliedKeys) { Core.keyboard(pressed); appliedKeys = pressed; }
                int count = Core.step(pad, samples);
                if (count < 0) { error = "The emulator stopped this game."; break; }
                if (link != null) checkpoint = tick == 1 || tick % 120 == 0 ? Core.linkCheckpoint() : 0;
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
                // Do not delay PCM for HQx or a busy surface. The presenter keeps only the newest
                // queued snapshot, and a skipped draw never skips a core step or Link exchange.
                int skip = frameSkip;
                boolean offer = skip < 0 ? tick - lastOffer >= 5 || System.nanoTime() < nextFrame + period : tick % (skip + 1) == 0;
                if (offer && renderer.offer(displayMode, filter)) lastOffer = tick;
                rateFrames++;
                long elapsed = System.nanoTime() - rateStart;
                if (elapsed >= 1_000_000_000L) {
                    long posted = renderer.posted();
                    measuredFps = (int) Math.round(rateFrames * 1_000_000_000.0 / elapsed);
                    drawnFps = (int) Math.round((posted - lastPosted) * 1_000_000_000.0 / elapsed);
                    rateFrames = 0;
                    lastPosted = posted;
                    rateStart += elapsed;
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
        } catch (OutOfMemoryError exhausted) {
            error = "Not enough memory to play this game.";
        } finally {
            renderer.close();
            if (link != null) link.stop();
            if (audio != null) { silence(audio); audio.release(); }
            if (started) Core.close();
            if (definition != null) definition.delete();
            LocalFiles.deleteTree(isolated);
            LocalFiles.deleteTree(consoleRun);
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
}
