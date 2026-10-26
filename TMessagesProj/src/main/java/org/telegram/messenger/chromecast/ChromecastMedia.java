package org.telegram.messenger.chromecast;

import android.net.Uri;
import java.util.Objects;

/** Lightweight media descriptor retained temporarily for call-site compatibility; no Cast SDK. */
public final class ChromecastMedia {
    public static final String IMAGE_JPEG="image/jpeg", IMAGE_PNG="image/png", VIDEO_MP4="video/mp4", APPLICATION_X_MPEG_URL="application/x-mpegURL";
    public final String mimeType;
    public final Uri internalUri;
    public final String externalPath;
    public final int width, height;
    public final String title, subtitle;
    private ChromecastMedia(Builder b) { mimeType=b.mimeType; internalUri=b.internalUri; externalPath=b.externalPath; width=b.width; height=b.height; title=b.title; subtitle=b.subtitle; }
    public String getExternalUri(String host) { return internalUri != null ? internalUri.toString() : externalPath; }
    @Override public boolean equals(Object o) {
        if (!(o instanceof ChromecastMedia)) return false; ChromecastMedia x=(ChromecastMedia)o;
        return Objects.equals(mimeType,x.mimeType)&&Objects.equals(internalUri,x.internalUri)&&Objects.equals(externalPath,x.externalPath)&&Objects.equals(title,x.title)&&Objects.equals(subtitle,x.subtitle)&&width==x.width&&height==x.height;
    }
    @Override public int hashCode() { return Objects.hash(mimeType,internalUri,externalPath,title,subtitle,width,height); }
    public static final class Builder {
        private final String mimeType; private final Uri internalUri; private final String externalPath;
        private int width,height; private String title,subtitle;
        private Builder(Uri u,String p,String m) { internalUri=u; externalPath=p; mimeType=m; }
        public static Builder fromUri(Uri u,String p,String m) { return new Builder(u,p,m); }
        public Builder setTitle(String v) { title=v; return this; }
        public Builder setSubtitle(String v) { subtitle=v; return this; }
        public Builder setSize(int w,int h) { width=w; height=h; return this; }
        public Builder setMetadata(Object ignored) { return this; }
        public ChromecastMedia build() { return new ChromecastMedia(this); }
    }
}
