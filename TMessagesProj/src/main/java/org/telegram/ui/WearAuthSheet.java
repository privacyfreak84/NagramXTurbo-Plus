package org.telegram.ui;

import java.util.List;

/** Wear-based login handoff is disabled in this Google-free build. */
public final class WearAuthSheet {
    private WearAuthSheet() {}
    public static void onOfferReceived(byte[] data, String sourceNodeId) {}
    public static void onCancelReceived() {}
    public static void show() {}
    public static void showEmojis(int account, List<String> emojis) {}
    public static void cancel() {}
}
