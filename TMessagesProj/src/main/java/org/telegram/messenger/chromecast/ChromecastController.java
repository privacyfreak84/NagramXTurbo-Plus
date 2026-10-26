package org.telegram.messenger.chromecast;

import java.io.File;

/** No-op compatibility shim. Chromecast playback is disabled in this build. */
public final class ChromecastController {
    private static final ChromecastController INSTANCE = new ChromecastController();
    private ChromecastController() {}
    public static ChromecastController getInstance() { return INSTANCE; }
    public boolean isCasting() { return false; }
    public boolean isPlaying(ChromecastMediaVariations media) { return false; }
    public void setCurrentMediaAndCastIfNeeded(ChromecastMediaVariations media) {}
    public String setCover(File file) { return ""; }
    public static boolean eq(ChromecastMedia a, ChromecastMedia b) { return a == b || (a != null && a.equals(b)); }
    public static boolean eq(ChromecastMediaVariations a, ChromecastMediaVariations b) {
        if (a == b) return true;
        if (a == null || b == null || a.getVariationsCount() != b.getVariationsCount()) return false;
        for (int i = 0; i < a.getVariationsCount(); i++) if (!eq(a.getVariation(i), b.getVariation(i))) return false;
        return true;
    }
}
