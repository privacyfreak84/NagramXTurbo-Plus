package tw.nekomimi.nekogram.helpers;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bookkeeping for "forward messages I linger on to Saved Messages".
 * <p>
 * The chat screen reports which messages are currently on screen every tick; this class
 * remembers for how long each one has been continuously visible, hands back the ones that
 * crossed the configured threshold, and persists which messages were already forwarded so a
 * message is never saved twice (even after the chat is reopened or the app restarts).
 */
public class DwellForwardTracker {

    private static final String PREFS = "dwell_forward";
    private static final int MAX_STORED_PER_DIALOG = 500;

    private final int account;
    private final long dialogId;
    private final String prefKey;

    /** message id -> time (ms) it became continuously visible. */
    private final Map<Integer, Long> visibleSince = new HashMap<>();
    /** ids already forwarded (persisted, insertion ordered so the oldest can be trimmed). */
    private final LinkedHashSet<Integer> forwarded = new LinkedHashSet<>();

    public DwellForwardTracker(int account, long dialogId) {
        this.account = account;
        this.dialogId = dialogId;
        this.prefKey = "a" + account + "_" + dialogId;
        load();
    }

    /** Forget all running timers (chat paused, hidden or setting turned off). */
    public void reset() {
        visibleSince.clear();
    }

    public boolean isForwarded(int messageId) {
        return forwarded.contains(messageId);
    }

    /**
     * @param visibleIds ids of eligible messages that are on screen right now
     * @return ids that have now been visible without interruption for at least {@code thresholdMs}
     */
    public List<Integer> update(Set<Integer> visibleIds, long now, long thresholdMs) {
        for (Iterator<Integer> it = visibleSince.keySet().iterator(); it.hasNext(); ) {
            if (!visibleIds.contains(it.next())) {
                it.remove(); // scrolled away: the timer starts over if it comes back
            }
        }
        List<Integer> due = new ArrayList<>();
        for (Integer id : visibleIds) {
            if (forwarded.contains(id)) {
                continue;
            }
            Long since = visibleSince.get(id);
            if (since == null) {
                visibleSince.put(id, now);
            } else if (now - since >= thresholdMs) {
                due.add(id);
            }
        }
        return due;
    }

    /** Record messages as forwarded so they are not sent again. */
    public void markForwarded(Iterable<Integer> ids) {
        for (Integer id : ids) {
            forwarded.add(id);
            visibleSince.remove(id);
        }
        while (forwarded.size() > MAX_STORED_PER_DIALOG) {
            Iterator<Integer> it = forwarded.iterator();
            it.next();
            it.remove();
        }
        save();
    }

    private SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private void load() {
        String raw = prefs().getString(prefKey, "");
        if (raw == null || raw.isEmpty()) {
            return;
        }
        for (String part : raw.split(",")) {
            try {
                forwarded.add(Integer.parseInt(part));
            } catch (NumberFormatException ignore) {
            }
        }
    }

    private void save() {
        StringBuilder sb = new StringBuilder();
        for (Integer id : forwarded) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(id);
        }
        prefs().edit().putString(prefKey, sb.toString()).apply();
    }
}
