package org.telegram.messenger;

import org.telegram.tgnet.ConnectionsManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;

public class CaptchaController {

    private static class Request {
        private final int currentAccount;
        private final int requestToken;
        private final int generation;
        private boolean completed;

        private Request(int currentAccount, int requestToken, int generation) {
            this.currentAccount = currentAccount;
            this.requestToken = requestToken;
            this.generation = generation;
        }

        private void done(String token) {
            if (completed) {
                return;
            }
            completed = true;
            ConnectionsManager.native_receivedCaptchaResult(currentAccount, requestToken, generation, token);
        }
    }

    public static void request(int currentAccount, int requestToken, int generation, String action, String key_id) {
        final Request finalRequest = new Request(currentAccount, requestToken, generation);

        // reCAPTCHA is not bundled in this Google-free build. Fail closed so a
        // server challenge cannot be silently bypassed.
        FileLog.e("CaptchaController: reCAPTCHA provider unavailable; failing challenge closed");
        finalRequest.done("RECAPTCHA_FAILED_NO_ACTIVITY");
    }


}
