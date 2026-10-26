package org.telegram.messenger;

/**
 * Language detection is intentionally unavailable in the Google-free build.
 * Automatic translation can still be used with a user-selected source language.
 */
public class LanguageDetector {
    public interface StringCallback { void run(String str); }
    public interface ExceptionCallback { void run(Exception e); }

    public static boolean hasSupport() { return false; }

    public static void detectLanguage(String text, StringCallback onSuccess, ExceptionCallback onFail) {
        detectLanguage(text, onSuccess, onFail, false);
    }

    public static void detectLanguage(String text, StringCallback onSuccess, ExceptionCallback onFail, boolean initializeFirst) {
        if (onFail != null) {
            onFail.run(new UnsupportedOperationException("On-device language detection is disabled in this build."));
        }
    }
}
