package io.github.ankushui18.fbaoffline;

import java.io.File;
import java.io.IOException;

/** Optional transport contract. The offline APK doesn't contain an implementation. */
interface GameLink {
    int player();
    String status();
    void connect(File rom) throws IOException;
    long exchange(int input, boolean pause, long checkpoint) throws IOException;
    void stop();
}
