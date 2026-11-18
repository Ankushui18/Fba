package io.github.ankushui18.fbaoffline;

import android.graphics.Bitmap;

/** No core plugins or loading from user-writable paths. */
final class Core {
    static { System.loadLibrary("offlinefb"); }
    private Core() {}

    static native String open(String rom, String system, String save, boolean patched, boolean linked);
    static native void close();
    static native int step(long input, short[] output);
    static native boolean reset();
    static native byte[] captureState();
    static native boolean restoreState(byte[] data);
    static native String[][] options();
    static native boolean setOption(String key, int choice);
    static native long linkCheckpoint();
    static native int inputMask(int port);
    static native int frameSize();
    static native int rotation();
    static native float aspect();
    static native double fps();
    static native int sampleRate();
    static native boolean copyFrame(Bitmap bitmap, int scale);
}
