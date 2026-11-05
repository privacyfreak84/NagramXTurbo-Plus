package tw.nekomimi.nekogram.helpers.remote;

import android.app.Activity;
import android.content.Context;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import kotlin.Unit;
import tw.nekomimi.nekogram.ui.BottomBuilder;
import xyz.nextalone.nagram.NaConfig;

public class UpdateHelper extends BaseRemoteHelper {

    public static final int UPDATE_OFF = 0;
    public static final int UPDATE_CHANNEL_RELEASE = 1;
    public static final int UPDATE_CHANNEL_BETA = 2;
    private static final String UPDATE_IS_BETA_KEY = "updateIsBeta";
    private boolean updateAlways = false;

    public static UpdateHelper getInstance() {
        return InstanceHolder.instance;
    }

    public static boolean isCurrentUpdateBeta() {
        return preferences.getBoolean(UPDATE_IS_BETA_KEY, false);
    }

    public static String getAutoUpdateChannelName() {
        return getUpdateChannelName(NaConfig.INSTANCE.getAutoUpdateChannel().Int());
    }

    private static String getUpdateChannelName(int channel) {
        int resId;
        switch (channel) {
            case UPDATE_OFF:
                resId = R.string.AutoCheckUpdateOFF;
                break;
            case UPDATE_CHANNEL_BETA:
                resId = R.string.AutoCheckUpdateBeta;
                break;
            default:
                resId = R.string.AutoCheckUpdateRelease;
                break;
        }
        return LocaleController.getString(resId);
    }

    public static void switchUpdateChannel(Context context, int channel) {
        NaConfig.INSTANCE.getAutoUpdateChannel().setConfigInt(channel);
        cleanAppUpdate();
        if (channel != UPDATE_OFF) {
            Browser.openUrl(context, "tg://update");
        }
    }

    public static org.telegram.ui.ActionBar.BottomSheet buildUpdateChannelSwitcher(Activity activity) {
        BottomBuilder switchBuilder = new BottomBuilder(activity);
        int channel = NaConfig.INSTANCE.getAutoUpdateChannel().Int();
        if (channel == UPDATE_CHANNEL_BETA) {
            switchBuilder.addTitle(LocaleController.getString(R.string.AutoCheckUpdateSwitch), LocaleController.getString(R.string.TurboUpdateChannelBetaHint));
        } else {
            switchBuilder.addTitle(LocaleController.getString(R.string.AutoCheckUpdateSwitch));
        }
        appendChannelRadio(switchBuilder, activity, UPDATE_OFF);
        appendChannelRadio(switchBuilder, activity, UPDATE_CHANNEL_RELEASE);
        appendChannelRadio(switchBuilder, activity, UPDATE_CHANNEL_BETA);
        return switchBuilder.create();
    }

    private static void appendChannelRadio(BottomBuilder switchBuilder, Activity activity, int channel) {
        switchBuilder.addRadioItem(getUpdateChannelName(channel), NaConfig.INSTANCE.getAutoUpdateChannel().Int() == channel, null, radioButtonCell -> {
            switchBuilder.doRadioCheck(radioButtonCell);
            AndroidUtilities.runOnUIThread(() -> {
                switchBuilder.dismiss();
                switchUpdateChannel(activity, channel);
            }, 500);
            return Unit.INSTANCE;
        });
    }

    public static void cleanAppUpdate() {
        if (SharedConfig.pendingAppUpdate != null && SharedConfig.pendingAppUpdate.document != null) {
            File path = FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(SharedConfig.pendingAppUpdate.document, true);
            if (path != null && path.exists()) {
                Utilities.globalQueue.postRunnable(() -> {
                    try {
                        if (!path.delete()) path.deleteOnExit();
                    } catch (Exception ignored) {
                    }
                });
            }
        }
        SharedConfig.pendingAppUpdate = null;
        SharedConfig.saveConfig();
        preferences.edit().putBoolean(UPDATE_IS_BETA_KEY, false).apply();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
    }

    @Override
    protected void onError(String text, Delegate delegate) {
        delegate.onTLResponse(null, text);
    }

    @Override
    protected String getTag() {
        if (BuildConfig.DEBUG) return "updateDebug";
        return NaConfig.INSTANCE.getAutoUpdateChannel().Int() == UPDATE_CHANNEL_BETA ? "updateBeta" : "updateRelease";
    }

    @SuppressWarnings("ConstantConditions")
    private int getPreferredAbiFile(Map<String, Integer> files) {
        for (String abi : Build.SUPPORTED_ABIS) {
            if (files.containsKey(abi)) {
                return files.get(abi);
            }
        }
        return files.getOrDefault("universal", files.get("arm64-v8a"));
    }

    private String getPreferredUpdateUrl(String urlArm64, String urlArmeabiV7a) {
        if (!urlArmeabiV7a.isEmpty()) {
            for (String abi : Build.SUPPORTED_ABIS) {
                if ("armeabi-v7a".equals(abi)) {
                    return urlArmeabiV7a;
                }
                if ("arm64-v8a".equals(abi)) {
                    break;
                }
            }
        }
        return urlArm64;
    }

    private Map<String, Integer> jsonToMap(JSONObject obj) {
        Map<String, Integer> map = new HashMap<>();
        List<String> abis = new ArrayList<>();
        abis.add("arm64-v8a");
        abis.add("universal");
        try {
            for (var abi : abis) {
                map.put(abi, obj.getInt(abi));
            }
        } catch (JSONException ignored) {
        }
        return map;
    }

    private Update getShouldUpdateVersion(List<JSONObject> responses) {
        int currentVersion = BuildConfig.VERSION_CODE;
        long buildTimestamp = BuildConfig.BUILD_TIMESTAMP;
        Update ref = null;
        for (var string : responses) {
            try {
                int remoteVersion = string.getInt("version_code");
                long remoteBuildTimestamp = string.optLong("build_timestamp", 0L);
                boolean shouldUpdate = false;
                if (remoteVersion > currentVersion) {
                    shouldUpdate = true;
                } else if (remoteVersion == currentVersion && remoteBuildTimestamp > buildTimestamp) {
                    shouldUpdate = true;
                }
                if (shouldUpdate || updateAlways) {
                    String updateUrlField = BuildVars.TURBO_BASE ? "url_base" : "url";
                    String urlArm64 = string.optString(updateUrlField, "");
                    String urlArmeabiV7a = string.optString(updateUrlField + "_armeabi_v7a", "");
                    if (BuildVars.TURBO_BASE && urlArm64.isEmpty() && urlArmeabiV7a.isEmpty()) {
                        continue;
                    }
                    if (updateAlways) {
                        updateAlways = false;
                    }
                    ref = new Update(
                            string.getBoolean("can_not_skip"),
                            string.getString("version"),
                            remoteVersion,
                            string.getInt("sticker"),
                            string.getInt("message"),
                            jsonToMap(string.getJSONObject("document")),
                            getPreferredUpdateUrl(urlArm64, urlArmeabiV7a)
                    );
                    break;
                }
            } catch (JSONException ignored) {
            }
        }
        return ref;
    }

    private void getNewVersionMessagesCallback(Delegate delegate, Update json, HashMap<String, Integer> ids, TLObject response) {
        var update = new TLRPC.TL_help_appUpdate();
        update.version = json.version;
        update.can_not_skip = json.canNotSkip;
        if (json.url != null) {
            update.url = json.url;
            update.flags |= 4;
        }
        if (NaConfig.INSTANCE.getAutoUpdateChannel().Int() == UPDATE_OFF && !update.can_not_skip) {
            delegate.onTLResponse(null, null);
            return;
        }
        if (response != null) {
            var res = (TLRPC.messages_Messages) response;
            getMessagesController().removeDeletedMessagesFromArray(CHANNEL_METADATA_ID, res.messages);
            var messages = new HashMap<Integer, TLRPC.Message>();
            for (var message : res.messages) {
                messages.put(message.id, message);
            }
            if (ids.containsKey("sticker")) {
                var sticker = messages.get(ids.get("sticker"));
                if (sticker != null && sticker.media != null) {
                    update.sticker = sticker.media.document;
                    update.flags |= 8;
                }
            }
            if (ids.containsKey("message")) {
                var message = messages.get(ids.get("message"));
                if (message != null) {
                    update.text = message.message;
                    update.entities = message.entities;
                }
            }
            if (ids.containsKey("document")) {
                var file = messages.get(ids.get("document"));
                if (file != null && file.media != null) {
                    update.document = file.media.document;
                    update.flags |= 2;
                }
            }
        }
        preferences.edit().putBoolean(UPDATE_IS_BETA_KEY, "updateBeta".equals(getTag())).apply();
        delegate.onTLResponse(update, null);
    }

    @Override
    protected void onLoadSuccess(ArrayList<JSONObject> responses, Delegate delegate) {
        var update = getShouldUpdateVersion(responses);
        if (update == null) {
            delegate.onTLResponse(null, null);
            return;
        }
        var ids = new HashMap<String, Integer>();
        if (update.sticker != null) {
            ids.put("sticker", update.sticker);
        }
        if (update.message != null) {
            ids.put("message", update.message);
        }
        // APK is served via `url` (GitHub Release) when present — skip the channel
        // document lookup so we never request the placeholder id=0 from getMessages.
        if (update.document != null && (update.url == null || update.url.isEmpty())) {
            ids.put("document", getPreferredAbiFile(update.document));
        }
        if (ids.isEmpty()) {
            getNewVersionMessagesCallback(delegate, update, null, null);
        } else {
            var req = new TLRPC.TL_channels_getMessages();
            req.channel = getMessagesController().getInputChannel(CHANNEL_METADATA_ID);
            req.id = new ArrayList<>(ids.values());
            getConnectionsManager().sendRequest(req, (response1, error1) -> {
                if (error1 == null) {
                    getNewVersionMessagesCallback(delegate, update, ids, response1);
                } else {
                    delegate.onTLResponse(null, error1.text);
                }
            });
        }
    }

    private static final String FORK_LATEST_RELEASE_API = "https://api.github.com/repos/privacyfreak84/NagramXTurbo-Plus/releases/latest";
    private static OkHttpClient releaseClient;

    private static synchronized OkHttpClient getReleaseClient() {
        if (releaseClient == null) {
            releaseClient = new OkHttpClient.Builder()
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build();
        }
        return releaseClient;
    }

    public void checkNewVersionAvailable(Delegate delegate) {
        checkNewVersionAvailable(delegate, false);
    }

    /**
     * Updates come only from this fork's own GitHub releases (not upstream Turbo's metadata channel).
     * A release counts as new when it was built from a different commit than the installed app and was
     * published after the installed app was built, so installing a release never offers that same release.
     */
    public void checkNewVersionAvailable(Delegate delegate, boolean updateAlways) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                Request request = new Request.Builder()
                        .url(FORK_LATEST_RELEASE_API)
                        .header("Accept", "application/vnd.github+json")
                        .build();
                try (Response response = getReleaseClient().newCall(request).execute()) {
                    if (response.code() == 404) {
                        delegate.onTLResponse(null, null); // no release published yet
                        return;
                    }
                    if (!response.isSuccessful() || response.body() == null) {
                        delegate.onTLResponse(null, "HTTP " + response.code());
                        return;
                    }
                    JSONObject release = new JSONObject(response.body().string());
                    TLRPC.TL_help_appUpdate update = buildForkUpdate(release, updateAlways);
                    if (update != null) {
                        preferences.edit().putBoolean(UPDATE_IS_BETA_KEY, false).apply();
                    }
                    delegate.onTLResponse(update, null);
                }
            } catch (Exception e) {
                delegate.onTLResponse(null, e.getMessage() != null ? e.getMessage() : "Update check failed");
            }
        });
    }

    private static TLRPC.TL_help_appUpdate buildForkUpdate(JSONObject release, boolean always) {
        if (release.optBoolean("draft", false)) {
            return null;
        }
        String url = pickApkUrl(release.optJSONArray("assets"));
        if (url == null) {
            return null;
        }
        boolean newer = false;
        String installed = BuildConfig.GIT_COMMIT;
        String target = release.optString("target_commitish", "");
        if (installed.length() >= 7 && target.matches("[0-9a-fA-F]{40}")) {
            boolean differentCommit = !target.regionMatches(true, 0, installed, 0, 7);
            newer = differentCommit && parseIsoSeconds(release.optString("published_at", "")) > BuildConfig.BUILD_TIMESTAMP;
        }
        if (!newer && !always) {
            return null;
        }
        String tag = release.optString("tag_name", "");
        String name = release.optString("name", "");
        TLRPC.TL_help_appUpdate update = new TLRPC.TL_help_appUpdate();
        update.version = tag.isEmpty() ? name : tag;
        update.can_not_skip = false;
        update.url = url;
        update.flags |= 4;
        update.text = release.optString("body", "");
        update.entities = new ArrayList<>();
        return update;
    }

    private static String pickApkUrl(JSONArray assets) {
        if (assets == null) {
            return null;
        }
        String arm64 = null, armv7 = null, any = null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null) {
                continue;
            }
            String name = asset.optString("name", "");
            String link = asset.optString("browser_download_url", "");
            if (!name.endsWith(".apk") || link.isEmpty()) {
                continue;
            }
            if (name.contains("arm64-v8a")) {
                arm64 = link;
            } else if (name.contains("armeabi-v7a")) {
                armv7 = link;
            } else if (any == null) {
                any = link;
            }
        }
        for (String abi : Build.SUPPORTED_ABIS) {
            if ("arm64-v8a".equals(abi) && arm64 != null) {
                return arm64;
            }
            if ("armeabi-v7a".equals(abi) && armv7 != null) {
                return armv7;
            }
        }
        return arm64 != null ? arm64 : (armv7 != null ? armv7 : any);
    }

    private static long parseIsoSeconds(String iso) {
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            return format.parse(iso).getTime() / 1000L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private static final class InstanceHolder {
        private static final UpdateHelper instance = new UpdateHelper();
    }

    public static class Update {
        public Boolean canNotSkip;
        public String version;
        public Integer versionCode;
        public Integer sticker;
        public Integer message;
        public Map<String, Integer> document;
        public String url;

        public Update(Boolean canNotSkip, String version, int versionCode, int sticker, int message, Map<String, Integer> document, String url) {
            this.canNotSkip = canNotSkip;
            this.version = version;
            this.versionCode = versionCode;
            this.sticker = sticker;
            this.message = message;
            this.document = document;
            this.url = url;
        }
    }
}
