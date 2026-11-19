package io.github.ankushui18.fbaoffline;

import android.graphics.Bitmap;

/** No core plugins or loading from user-writable paths. */
final class Core {
    static { System.loadLibrary("offlinefb"); }
    private Core() {}

    static native String open(String rom, String system, String save, boolean patched, boolean linked, String profile);
    static native void close();
    static native int step(long input, short[] output);
    static native void keyboard(int[] keys);
    static native boolean reset();
    static native byte[] captureState();
    static native boolean restoreState(byte[] data);
    static native String[][] options(boolean bootOnly);
    static native boolean setOption(String key, int choice);
    static native long linkCheckpoint();
    static native int inputMask(int port);
    static native int frameSize();
    static native boolean videoUpdated();
    static native int rotation();
    static native float aspect();
    static native double fps();
    static native int sampleRate();
    // Copy the live core framebuffer. Emulation thread only, like the methods above.
    static native boolean copyFrame(Bitmap bitmap);

    // Pure bitmap transform on an owned snapshot; never reads the live core.
    static native boolean scaleFrame(Bitmap source, Bitmap destination, int factor);
}
