package org.telegram.ui;

/** Chromecast sync is disabled in this build; retained as a no-op compatibility shim. */
public final class CastSync {
    public static final int TYPE_PHOTOVIEWER = 0, TYPE_MUSIC = 1;
    private CastSync() {}
    public static void check(int type) {}
    public static void stop() {}
    public static boolean isActive() { return false; }
    public static long getPosition() { return -1L; }
    public static void seekTo(long position) {}
    public static void syncPosition(long position) {}
    public static void setVolume(float volume) {}
    public static float getVolume() { return 0.5f; }
    public static float getDeviceVolume() { return 0f; }
    public static boolean isPlaying() { return false; }
    public static void setPlaying(boolean play) {}
    public static void setSpeed(float speed) {}
    public static boolean isUpdatePending() { return false; }
    public static float getSpeed() { return 1f; }
    public static void doSyncVolume(boolean sync) {}
}
