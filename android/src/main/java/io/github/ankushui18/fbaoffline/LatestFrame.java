package io.github.ankushui18.fbaoffline;

/** Two reusable slots, for one producer and one consumer. No pixel work under the monitor. */
final class LatestFrame<T> {
    private T first, second, pending, reading;
    private boolean closed;
    private volatile long generation;

    LatestFrame(T first, T second) {
        this.first = first;
        this.second = second;
    }

    long generation() { return generation; }

    synchronized T beginWrite(long stamp) {
        if (closed || stamp != generation) return null;
        // Unpublish the older snapshot before overwriting its storage. Never touch the reader's slot.
        pending = null;
        return reading == first ? second : first;
    }

    synchronized boolean publish(T frame, long stamp) {
        if (closed || stamp != generation) return false;
        pending = frame;
        notifyAll();
        return true;
    }

    synchronized T take() throws InterruptedException {
        while (!closed && pending == null) wait();
        reading = pending;
        pending = null;
        return reading;
    }

    synchronized void endRead() { reading = null; }

    synchronized void discard() {
        generation++;
        pending = null;
    }

    synchronized void close() {
        if (closed) return;
        closed = true;
        discard();
        first = second = reading = null;
        notifyAll();
    }
}
