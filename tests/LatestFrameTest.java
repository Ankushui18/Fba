package io.github.ankushui18.fbaoffline;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

public final class LatestFrameTest {
    private static final class Frame { int id; }

    public static void main(String[] args) throws Exception {
        replacesBacklogWithoutTouchingReader();
        invalidatesPendingAndInFlightFrames();
        System.out.println("Latest-frame presenter keeps two owned buffers, skips backlog and invalidates old snapshots.");
    }

    private static void replacesBacklogWithoutTouchingReader() throws Exception {
        Frame first = new Frame(), second = new Frame();
        LatestFrame<Frame> frames = new LatestFrame<>(first, second);
        long stamp = frames.generation();
        require(frames.beginWrite(stamp) == first, "initial slot");
        first.id = 1;
        require(frames.publish(first, stamp), "publish initial frame");
        CountDownLatch entered = new CountDownLatch(1), resume = new CountDownLatch(1);
        FutureTask<Integer> consumer = new FutureTask<>(() -> {
            Frame held = frames.take();
            require(held == first && held.id == 1, "first snapshot");
            entered.countDown();
            require(resume.await(5, TimeUnit.SECONDS), "renderer release");
            require(held.id == 1, "producer must not overwrite renderer-owned pixels");
            frames.endRead();
            Frame newest = frames.take();
            require(newest == second, "only the queued slot remains");
            frames.endRead();
            return newest.id;
        });
        start(consumer);
        try {
            require(entered.await(5, TimeUnit.SECONDS), "renderer started");
            for (int id = 2; id <= 4096; id++) {
                Frame next = frames.beginWrite(stamp);
                require(next == second, "backlog replaces queued snapshot, not the one being drawn");
                next.id = id;
                require(frames.publish(next, stamp), "producer can publish while draw is stalled");
            }
            resume.countDown();
            require(consumer.get(5, TimeUnit.SECONDS) == 4096, "draw the newest snapshot after the stall");
        } finally { frames.close(); resume.countDown(); }
    }

    private static void invalidatesPendingAndInFlightFrames() throws Exception {
        LatestFrame<Frame> frames = new LatestFrame<>(new Frame(), new Frame());
        long stale = frames.generation();
        Frame copying = frames.beginWrite(stale);
        frames.discard(); // Pause or surface loss midway through a copy.
        require(stale != frames.generation() && frames.beginWrite(stale) == null, "old ticket revoked");
        require(!frames.publish(copying, stale), "late copy cannot requeue stale pixels");

        long active = frames.generation();
        require(frames.publish(frames.beginWrite(active), active), "new surface can accept frames");
        frames.discard();
        FutureTask<Frame> waiter = new FutureTask<>(frames::take);
        start(waiter);
        frames.close();
        require(waiter.get(5, TimeUnit.SECONDS) == null, "closing ends consumer wait and drops pending work");
        require(frames.beginWrite(frames.generation()) == null && !frames.publish(copying, frames.generation()), "closed stays closed");
    }

    private static void start(Runnable task) {
        Thread worker = new Thread(task, "test-renderer");
        worker.setDaemon(true);
        worker.start();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
