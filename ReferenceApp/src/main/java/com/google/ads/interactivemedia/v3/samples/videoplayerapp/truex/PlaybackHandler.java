package com.google.ads.interactivemedia.v3.samples.videoplayerapp.truex;

public interface PlaybackHandler {
    void resumeStream();
    void closeStream();
    void displayRegularAds();
    void handlePopup(String url);
}
