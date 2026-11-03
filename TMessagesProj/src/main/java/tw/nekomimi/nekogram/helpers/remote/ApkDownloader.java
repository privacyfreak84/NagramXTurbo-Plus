package tw.nekomimi.nekogram.helpers.remote;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

// Downloads an APK from an arbitrary HTTPS URL (GitHub Release asset) into the
// app's private storage, reporting progress on the UI thread. Used by the in-app
// updater when the manifest points at a `url` instead of a Telegram document —
// APK no longer fits Bot API's 50MB upload limit, so it lives in GitHub Releases.
// A dropped connection is retried with an HTTP Range resume, so flaky networks
// converge on a complete file instead of restarting or hanging at "100%".
public class ApkDownloader {

    public interface Callback {
        default void onProgress(float progress) {}
        void onSuccess(File file);
        default void onError(String message) {}
    }

    public static final class Handle {
        final String url;
        final File dest;
        final File tmp;
        final Callback callback;
        volatile boolean canceled;
        volatile Call call;
        String etag;

        Handle(String url, File dest, Callback callback) {
            this.url = url;
            this.dest = dest;
            this.tmp = new File(dest.getPath() + ".tmp");
            this.callback = callback;
        }

        public void cancel() {
            canceled = true;
            Call active = call;
            if (active != null) {
                active.cancel();
            }
        }
    }

    private static final int BUFFER_SIZE = 16384;
    private static final long PROGRESS_THROTTLE_MS = 100;
    private static final int CONNECT_TIMEOUT_S = 30;
    private static final int READ_TIMEOUT_S = 60;
    private static final int WRITE_TIMEOUT_S = 60;
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 2000;
    private static final byte[] APK_ZIP_MAGIC = {'P', 'K', 3, 4};
    // Defence-in-depth: only ever install an APK fetched from this project's own
    // GitHub Release, even if the metadata-channel manifest were compromised.
    private static final String GITHUB_RELEASE_PREFIX = "https://github.com/privacyfreak84/NagramXTurbo-Plus/releases/";

    // Dedicated single-thread executor: a 50MB download must not block the shared
    // Utilities.globalQueue (used by polling, message events, etc.).
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static volatile Handle activeHandle;
    private static OkHttpClient client;

    private static class FatalDownloadException extends IOException {
        FatalDownloadException(String message) {
            super(message);
        }
    }

    private static OkHttpClient getClient() {
        if (client == null) {
            client = new OkHttpClient.Builder()
                    .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
                    .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
                    .writeTimeout(WRITE_TIMEOUT_S, TimeUnit.SECONDS)
                    .build();
        }
        return client;
    }

    public static File getDestFile(String url) {
        File dir = ApplicationLoader.getFilesDirFixed("update");
        return new File(dir, Utilities.MD5(url) + ".apk");
    }

    public static Handle download(String url, Callback callback) {
        Handle handle = new Handle(url, getDestFile(url), callback);
        if (url == null || !url.startsWith(GITHUB_RELEASE_PREFIX)) {
            reportError(handle, "Refusing download URL outside project releases");
            return handle;
        }
        File dest = handle.dest;
        if (dest.exists() && (dest.length() == 0 || !hasZipMagic(dest))) {
            if (!dest.delete()) {
                reportError(handle, "Failed to reset broken update file");
                return handle;
            }
        }
        if (dest.exists()) {
            File done = dest;
            AndroidUtilities.runOnUIThread(() -> callback.onSuccess(done));
            return handle;
        }
        Handle previous = activeHandle;
        if (previous != null) {
            previous.cancel();
        }
        activeHandle = handle;
        executor.submit(() -> {
            try {
                runWithRetries(handle);
            } finally {
                if (activeHandle == handle) {
                    activeHandle = null;
                }
            }
        });
        return handle;
    }

    public static TextView makeErrorText(Context context) {
        TextView textView = new TextView(context);
        textView.setVisibility(View.GONE);
        textView.setGravity(Gravity.CENTER_HORIZONTAL);
        textView.setMaxLines(2);
        textView.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        return textView;
    }

    private static void runWithRetries(Handle handle) {
        String lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS && !handle.canceled; attempt++) {
            if (attempt > 1) {
                sleepUnlessCanceled(RETRY_DELAY_MS, handle);
                if (handle.canceled) {
                    return;
                }
            }
            try {
                downloadAttempt(handle);
                boolean alive = !handle.canceled;
                // Finalize atomically: dest only appears once the full file is
                // written, so an interrupted download never leaves a partial
                // APK to install.
                if (!handle.tmp.renameTo(handle.dest)) {
                    throw new FatalDownloadException("Failed to finalize download");
                }
                if (alive) {
                    File done = handle.dest;
                    AndroidUtilities.runOnUIThread(() -> handle.callback.onSuccess(done));
                }
                return;
            } catch (CancellationException e) {
                return;
            } catch (FatalDownloadException e) {
                FileLog.e(e);
                handle.tmp.delete();
                reportError(handle, e.getMessage());
                return;
            } catch (IOException e) {
                FileLog.e(e);
                lastError = e.getMessage() == null ? "Download failed" : e.getMessage();
            }
        }
        if (!handle.canceled) {
            reportError(handle, lastError);
        }
    }

    private static void downloadAttempt(Handle handle) throws IOException {
        if (handle.canceled) {
            throw new CancellationException();
        }
        long offset = resumeOffset(handle.tmp);
        Request.Builder builder = new Request.Builder()
                .url(handle.url)
                .header("User-Agent", "NagramXTurbo");
        if (offset > 0) {
            builder.header("Range", "bytes=" + offset + "-");
            if (handle.etag != null) {
                builder.header("If-Range", handle.etag);
            }
        }
        Call call = getClient().newCall(builder.build());
        handle.call = call;
        try (Response response = call.execute()) {
            ResponseBody body = response.body();
            int code = response.code();
            if (code == 416) {
                // tmp outgrew the asset (stale cache, re-uploaded file): start over.
                resetPartial(handle.tmp);
                throw new IOException("HTTP 416");
            }
            if (body == null) {
                throw new IOException("Empty response body");
            }
            if (code != 200 && code != 206) {
                if (code >= 400 && code < 500) {
                    throw new FatalDownloadException("HTTP " + code);
                }
                throw new IOException("HTTP " + code);
            }
            String etag = response.header("ETag");
            if (etag != null) {
                handle.etag = etag;
            }
            long serverStart;
            long total = body.contentLength();
            if (code == 206) {
                long[] range = parseContentRange(response.header("Content-Range"));
                serverStart = range != null ? range[0] : -1;
                if (range != null && range[1] > 0) {
                    total = range[1];
                } else if (body.contentLength() > 0) {
                    total = offset + body.contentLength();
                }
            } else {
                serverStart = 0;
            }
            if (serverStart != offset) {
                // Server ignored our offset (or hid Content-Range): restart, never stitch blind.
                offset = 0;
                resetPartial(handle.tmp);
            }
            if (total <= 0) {
                total = -1;
            }
            boolean append = offset > 0;
            long read = copyStream(body, handle.tmp, append, offset, total, handle.callback);
            if (total > 0 && offset + read != total) {
                // A stream closed short of Content-Length is a truncation, not success.
                throw new IOException("Connection dropped at " + (offset + read) + "/" + total + " bytes");
            }
            if (offset + read == 0 || !hasZipMagic(handle.tmp)) {
                throw new FatalDownloadException("Downloaded file is not an APK");
            }
        }
    }

    private static long copyStream(ResponseBody body, File tmp, boolean append, long offset, long total, Callback callback) throws IOException {
        long read = 0;
        long lastReport = 0;
        FileOutputStream out;
        try {
            out = new FileOutputStream(tmp, append);
        } catch (IOException e) {
            throw new FatalDownloadException(e.getMessage() == null ? "Storage error" : e.getMessage());
        }
        try (InputStream in = body.byteStream();
             FileOutputStream fileOut = out) {
            byte[] buf = new byte[BUFFER_SIZE];
            int n;
            while ((n = in.read(buf)) != -1) {
                try {
                    fileOut.write(buf, 0, n);
                } catch (IOException e) {
                    throw new FatalDownloadException(e.getMessage() == null ? "Write failed" : e.getMessage());
                }
                read += n;
                long now = System.currentTimeMillis();
                if (total > 0 && now - lastReport > PROGRESS_THROTTLE_MS) {
                    lastReport = now;
                    float p = Math.min(1f, (offset + read) / (float) total);
                    AndroidUtilities.runOnUIThread(() -> callback.onProgress(p));
                }
            }
        }
        return read;
    }

    private static void resetPartial(File file) throws FatalDownloadException {
        if (!file.delete() && file.exists()) {
            throw new FatalDownloadException("Failed to reset partial download");
        }
    }

    private static long resumeOffset(File tmp) throws IOException {
        if (!tmp.exists() || tmp.length() == 0) {
            return 0;
        }
        if (!hasZipMagic(tmp)) {
            // Captive-portal garbage must never be appended to.
            resetPartial(tmp);
            return 0;
        }
        return tmp.length();
    }

    private static boolean hasZipMagic(File file) {
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] head = new byte[APK_ZIP_MAGIC.length];
            return in.read(head) == APK_ZIP_MAGIC.length && Arrays.equals(head, APK_ZIP_MAGIC);
        } catch (IOException e) {
            return false;
        }
    }

    private static long[] parseContentRange(String header) {
        // In "bytes 100-999/3000" start is before the dash, full size after the slash (Content-Length is just the chunk).
        if (header == null) {
            return null;
        }
        int slash = header.lastIndexOf('/');
        int space = header.indexOf(' ');
        int dash = header.indexOf('-', space);
        if (slash < 0 || slash + 1 >= header.length() || space < 0 || dash <= space) {
            return null;
        }
        long start = -1;
        long total = -1;
        try {
            start = Long.parseLong(header.substring(space + 1, dash));
        } catch (NumberFormatException ignored) {
        }
        String totalStr = header.substring(slash + 1);
        if (!"*".equals(totalStr)) {
            try {
                total = Long.parseLong(totalStr);
            } catch (NumberFormatException ignored) {
            }
        }
        return new long[]{start, total};
    }

    private static void sleepUnlessCanceled(long millis, Handle handle) {
        long deadline = System.currentTimeMillis() + millis;
        while (!handle.canceled && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static void reportError(Handle handle, String message) {
        AndroidUtilities.runOnUIThread(() -> handle.callback.onError(message));
    }
}
