package com.truex.googlereferenceapp.player;

import android.content.Context;
import android.util.Log;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.media3.ui.PlayerView;

import com.google.ads.interactivemedia.v3.api.Ad;
import com.google.ads.interactivemedia.v3.api.AdDisplayContainer;
import com.google.ads.interactivemedia.v3.api.AdErrorEvent;
import com.google.ads.interactivemedia.v3.api.AdEvent;
import com.google.ads.interactivemedia.v3.api.AdPodInfo;
import com.google.ads.interactivemedia.v3.api.AdProgressInfo;
import com.google.ads.interactivemedia.v3.api.AdsLoader;
import com.google.ads.interactivemedia.v3.api.AdsManager;
import com.google.ads.interactivemedia.v3.api.AdsManagerLoadedEvent;
import com.google.ads.interactivemedia.v3.api.AdsRenderingSettings;
import com.google.ads.interactivemedia.v3.api.CuePoint;
import com.google.ads.interactivemedia.v3.api.ImaSdkFactory;
import com.google.ads.interactivemedia.v3.api.ImaSdkSettings;
import com.google.ads.interactivemedia.v3.api.player.AdMediaInfo;
import com.google.ads.interactivemedia.v3.api.player.VideoAdPlayer;
import com.google.ads.interactivemedia.v3.api.player.VideoProgressUpdate;
import com.google.ads.interactivemedia.v3.api.player.VideoStreamPlayer;
import com.truex.adrenderer.TruexAdOptions;
import com.truex.adrenderer.TruexAdRenderer;
import com.truex.googlereferenceapp.VideoPlayerController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class VideoPlayerWithAds implements AdEvent.AdEventListener, AdErrorEvent.AdErrorListener, AdsLoader.AdsLoadedListener {
    private static final String CLASSTAG = VideoPlayerWithAds.class.getSimpleName();

    private String contentStreamUrl;
    private String adTagsUri;
    private String adTagsVastResponse;

    // These properties allow us to do the basic work of playing back ad-stitched video
    private Context context;
    private ViewGroup adUiContainer;
    private VideoPlayer videoPlayer;
    private PlayerView playerView;

    private AdDisplayContainer adDisplayContainer;
    private final AdsLoader adsLoader;
    private AdsManager adsManager;

    private long resumePositionAfterSnapbackMs; // Stream time to snap back to, in milliseconds.
    private boolean didSeekPastAdBreak;

    // The renderer that drives the TrueX Engagement experience
    private TruexAdRenderer truexAdRenderer;
    private boolean truexCredit; // user has interacted with the TrueX engagement sufficiently.

    private AdMediaInfo currentAd;
    private AdPodInfo currentAdPod;
    private boolean isDisplayingAd;

    final private List<VideoAdPlayer.VideoAdPlayerCallback> adCallbacks = new ArrayList<>();

    /**
     * Creates a new VideoPlaybackManager that implements IMA direct-ad-insertion.
     * @param context the app's context.
     * @param playerView the playerview videos will be displayed in
     * @param adUiContainer ViewGroup in which to display the ad's UI.
     */
    public VideoPlayerWithAds(Context context,
                       PlayerView playerView,
                       ViewGroup adUiContainer) {
        this.playerView = playerView;
        this.videoPlayer = new VideoPlayer(context, playerView);
        this.context = context;
        this.adUiContainer = adUiContainer;

        // Create an AdsLoader and optionally set the language.
        ImaSdkFactory sdkFactory = ImaSdkFactory.getInstance();
        ImaSdkSettings imaSdkSettings = sdkFactory.createImaSdkSettings();
        imaSdkSettings.setDebugMode(true);
        imaSdkSettings.setLanguage("en");

        VideoAdPlayer adPlayer = new VideoAdPlayer() {
            @Override
            public void loadAd(@NonNull AdMediaInfo adMediaInfo, @NonNull AdPodInfo adPodInfo) {
                currentAd = adMediaInfo;
                currentAdPod = adPodInfo;
                isDisplayingAd = false;
            }

            @Override
            public void playAd(@NonNull AdMediaInfo adMediaInfo) {
                isDisplayingAd = true;
                videoPlayer.play();
            }

            @Override
            public void pauseAd(@NonNull AdMediaInfo adMediaInfo) {
                isDisplayingAd = false;
                videoPlayer.pause();
            }


            @Override
            public void release() {
                // n/a
            }

            @Override
            public void stopAd(@NonNull AdMediaInfo adMediaInfo) {
                isDisplayingAd = false;
                videoPlayer.pause();
            }

            @NonNull
            @Override
            public VideoProgressUpdate getAdProgress() {
                if (!isDisplayingAd || videoPlayer.getDuration() <= 0) {
                    return VideoProgressUpdate.VIDEO_TIME_NOT_READY;
                }
                return new VideoProgressUpdate(
                  videoPlayer.getCurrentPositionMs(), videoPlayer.getDuration());
            }

            @Override
            public int getVolume() {
                return videoPlayer.getVolume();
            }

            @Override
            public void addCallback(@NonNull VideoAdPlayerCallback callback) {
                adCallbacks.add(callback);
            }

            @Override
            public void removeCallback(@NonNull VideoAdPlayerCallback callback) {
                adCallbacks.remove(callback);
            }
        }

        adDisplayContainer = ImaSdkFactory.createAdDisplayContainer(adUiContainer, adPlayer);
        adsLoader = sdkFactory.createAdsLoader(context, imaSdkSettings, adDisplayContainer);

        adsLoader.addAdErrorListener(
          new AdErrorEvent.AdErrorListener() {
              /** An event raised when there is an error loading or playing ads. */
              @Override
              public void onAdError(AdErrorEvent adErrorEvent) {
                  Log.e(CLASSTAG, "Ad Error: " + adErrorEvent.getError());
                  resumeStream();
                  resumeContent();
              }
          });

        adsLoader.addAdsLoadedListener(new VideoPlayerController.AdsLoadedListener());

        this.playerCallbacks = new ArrayList<>();
        this.sdkFactory = ImaSdkFactory.getInstance();
        ImaSdkSettings settings = sdkFactory.createImaSdkSettings();
        VideoStreamPlayer videoStreamPlayer = createVideoStreamPlayer();
        this.displayContainer = ImaSdkFactory.createStreamDisplayContainer(adUiContainer, videoStreamPlayer);
        videoPlayer.setCallback(
                new VideoPlayerCallback() {
                    @Override
                    public void onUserTextReceived(String userText) {
                        for (VideoStreamPlayer.VideoStreamPlayerCallback callback : playerCallbacks) {
                            callback.onUserTextReceived(userText);
                        }
                    }

                    @Override
                    public void onSeek(int windowIndex, long streamPositionMs) {
                        long allowedPositionMs = streamPositionMs;
                        if (streamManager != null) {
                            CuePoint cuePoint = streamManager.getPreviousCuePointForStreamTimeMs(streamPositionMs);
                            if (cuePoint != null && !cuePoint.isPlayed()) {
                                resumePositionAfterSnapbackMs = streamPositionMs; // Update snap back time.
                                // Missed cue point, so snap back to the beginning of cue point.
                                allowedPositionMs = cuePoint.getStartTimeMs();
                                Log.i(CLASSTAG, "Ad snapback to " + VideoPlayer.positionDisplay(allowedPositionMs)
                                        + " for " + VideoPlayer.positionDisplay(streamPositionMs));
                                videoPlayer.seekTo(windowIndex, allowedPositionMs);
                                videoPlayer.setCanSeek(false);
                                return;
                            }
                        }
                        videoPlayer.seekTo(windowIndex, allowedPositionMs);
                    }
                });
        adsLoader = sdkFactory.createAdsLoader(context, settings, displayContainer);
    }

    public void setContentStreamUrl(String streamUrl) {
        this.contentStreamUrl = streamUrl;
    }

    public void setAdTagsUri(String uri) {
        this.adTagsUri = uri;
    }

    public void setAdTagsVastResponse(String vastResponse) {
        this.adTagsVastResponse = vastResponse;
    }

    /**
     * Builds the stream request and begins playback of the requested stream
     */
    public void requestAndPlayStream() {
        boolean hasContent = contentStreamUrl != null && !contentStreamUrl.isEmpty();
        boolean hasAdUri = adTagsUri != null && !adTagsUri.isEmpty();
        boolean hasAdVastResponse = adTagsVastResponse != null && !adTagsVastResponse.isEmpty();
        boolean canPlay = hasContent && (hasAdUri || hasAdVastResponse);
        if (!canPlay) {
            Log.w(CLASSTAG, "cannot play stream, need content and ads to be specified");
            return;
        }

        // Enable controls for the video player
        videoPlayer.enableControls(true);

        // Request the stream
        adsLoader.addAdErrorListener(this);
        adsLoader.addAdsLoadedListener(this);
        adsLoader.requestStream(buildStreamRequest());
    }

    /**
     * Destroys and releases the video player and stream manager
     */
    public void release() {

        // Clean-up the TrueX ad manager
        if (truexAdRenderer != null) {
            truexAdRenderer.stop();
            truexAdRenderer = null;
        }

        // Clean-up the stream manager
        if (streamManager != null) {
            streamManager.destroy();
            streamManager = null;
        }

        // Clean-up the video player
        if (videoPlayer != null) {
            videoPlayer.release();
            videoPlayer = null;
        }

        if (adsLoader != null) {
            adsLoader.release();
            adsLoader = null;
        }
    }

    /**
     * Resumes playback of the video player
     */
    public void resume() {
        // Resume the current ad -- if active
        if (truexAdRenderer != null) {
            truexAdRenderer.resume();
            return;
        }

        // Resume video playback
        videoPlayer.requestFocus();
        if (videoPlayer != null && videoPlayer.isStreamRequested()) {
            videoPlayer.play();
        }
    }

    /**
     * Pauses playback of the video player
     */
    public void pause() {
        // Pause the current ad -- if active
        if (truexAdRenderer != null) {
            truexAdRenderer.pause();
            return;
        }

        // Pause video playback
        if (videoPlayer != null && videoPlayer.isStreamRequested()) {
            videoPlayer.pause();
        }
    }

    /**
     * Seeks past the first ad within the current ad pod
     * @param ad the first ad within the current ad pod
     * @param adPodInfo the ad pod info object for the current ad pod
     */
    private void seekPastInitialAd(Ad ad, AdPodInfo adPodInfo) {
        // Set-up the initial offset for seeking past the initial ad
        SeekPosition seekPosition = SeekPosition.fromSeconds(adPodInfo.getTimeOffset());

        // Add the duration of the initial ad
        seekPosition.addSeconds(ad.getDuration());

        // Subtract a hundred milliseconds to avoid displaying a black screen with a frozen UI
        seekPosition.subtractMilliseconds(100);

        // Seek past the ad
        videoPlayer.seekTo(seekPosition.getMilliseconds());
    }

    /**
     * Handles the ad started event
     * If the ad is a TrueX placeholder ad, we will display an interactive TrueX ad
     * Additionally, if the ad is a TrueX placeholder ad, we will seek past this initial ad
     * @param event the ad started event object
     */
    private void onAdStarted(AdEvent event) {
        Ad ad = event.getAd();

        // [1] - Look for TrueX ads
        if (!"trueX".equals(ad.getAdSystem())) return; // not a trueX ad

        // Retrieve the ad pod info
        AdPodInfo adPodInfo = ad.getAdPodInfo();
        if (adPodInfo == null) return;

        // [2] - Get ad parameters
        // The ad description contains the trueX vast config url
        String vastConfigUrl = ad.getDescription();
        if (vastConfigUrl == null || !vastConfigUrl.contains("get.truex.com")) return; // invalid vast config url

        // [3] - Prepare to enter the engagement
        // Pause the underlying stream, in order to present the TrueX experience, and seek over the current ad,
        // which is just a placeholder for the TrueX ad.
        videoPlayer.pause();
        videoPlayer.hide();
        seekPastInitialAd(ad, adPodInfo);

        // [4] - Start the TrueX engagement
        truexCredit = false;

        // Always allow remote debugging of ad web view for test and reference apps.
        TruexAdOptions options = new TruexAdOptions();
        options.enableWebViewDebugging = true;

        truexAdRenderer = new TruexAdRenderer(context);
        truexAdRenderer.init(vastConfigUrl, options);
        truexAdRenderer.start(adUiContainer);
    }

    /**
     * Creates a video stream player object wrapping the video player
     * The video stream player API is used by the IMA SDK to interact
     * with the video player and allows the video player to respond
     * to ad events - such as the beginning or end of an ad pod.
     * @return a video stream player wrapping the video player
     */
    private VideoStreamPlayer createVideoStreamPlayer() {
        return new VideoStreamPlayer() {
            @Override
            public void loadUrl(String url, List<HashMap<String, String>> subtitles) {
                videoPlayer.setStreamUrl(url);
                videoPlayer.play();
            }

            public void pause() {
                videoPlayer.pause();
            }

            public void resume() {
                videoPlayer.play();
            }

            @Override
            public int getVolume() {
                return videoPlayer.getVolume();
            }

            @Override
            public void addCallback(VideoStreamPlayerCallback videoStreamPlayerCallback) {
                playerCallbacks.add(videoStreamPlayerCallback);
            }

            @Override
            public void removeCallback(VideoStreamPlayerCallback videoStreamPlayerCallback) {
                playerCallbacks.remove(videoStreamPlayerCallback);
            }

            @Override
            public void onAdBreakStarted() {
                Log.i(CLASSTAG, "Ad Break Started");

                // Disable player controls
                videoPlayer.enableControls(false);
            }

            @Override
            public void onAdBreakEnded() {
                Log.i(CLASSTAG, "Ad Break Ended");

                if (resumePositionAfterSnapbackMs > 0) {
                    videoPlayer.seekTo(resumePositionAfterSnapbackMs);
                }
                resumePositionAfterSnapbackMs = 0;

                refreshAdMarkers();

                // Re-enable player controls
                videoPlayer.enableControls(true);
            }

            @Override
            public VideoProgressUpdate getContentProgress() {
                return new VideoProgressUpdate(videoPlayer.getCurrentPositionMs(),
                        videoPlayer.getDuration());
            }

            public void onAdPeriodStarted() {
                Log.i(CLASSTAG, "Ad Period Started");
            }

            public void onAdPeriodEnded() {
                Log.i(CLASSTAG, "Ad Period Ended");
            }

            public void seek(long milliseconds) {
                videoPlayer.seekTo(milliseconds);
            }
        };
    }

  private void refreshAdMarkers() {
        long[] extraAdGroupTimesMs = null;
        boolean[] extraPlayedAdGroups = null;
        if (adsManager != null) {
            // Set up the ad markers.
            List<Float> adBreaks = adsManager.getAdCuePoints();
            extraAdGroupTimesMs = new long[adBreaks.size()];
            extraPlayedAdGroups = new boolean[adBreaks.size()];
            for (int i = 0; i < adBreaks.size(); i++) {
                extraAdGroupTimesMs[i] = adBreaks.get(i).longValue();
                extraPlayedAdGroups[i] = false; // @TODO remember which ad breaks are complete
            }
        }
        playerView.setExtraAdGroupMarkers(extraAdGroupTimesMs, extraPlayedAdGroups);
    }

    public void resumeStream() {
        truexAdRenderer = null;

        // Display and resume the stream
        videoPlayer.show();
        videoPlayer.play();
    }

    public void skipCurrentAdBreak() {
        // Retrieve current ad
        Ad ad = streamManager.getCurrentAd();
        if (ad == null) {
            return;
        }

        // Retrieve ad pod info
        AdPodInfo adPodInfo = ad.getAdPodInfo();
        if (adPodInfo == null) {
            return;
        }

        // Retrieve ad progress info
        AdProgressInfo adProgressInfo = streamManager.getAdProgressInfo();
        if (adProgressInfo == null) {
            return;
        }

        // Set-up the initial offset for seeking past the ad break
        SeekPosition seekPosition = SeekPosition.fromSeconds(adPodInfo.getTimeOffset());

        // Add the duration of the ad break
        seekPosition.addSeconds(adProgressInfo.getAdBreakDuration());

        // Add two seconds to avoid displaying a frozen UI
        seekPosition.addSeconds(2);

        // Seek past the ad break
        videoPlayer.seekTo(seekPosition.getMilliseconds());

        // We will need to manually call onAdBreakEnded() when we resume the stream
        didSeekPastAdBreak = true;
    }

    /** AdErrorListener implementation **/

    @Override
    public void onAdError(AdErrorEvent event) {
        Log.i(CLASSTAG, String.format("Ad Error: %s", event.getError().getMessage()));
        this.release();
    }

    /** AdEventListener implementation **/

    @Override
    public void onAdEvent(AdEvent event) {
        if (event.getType() == AdEvent.AdEventType.AD_PROGRESS) {
            return;
        }

        Log.i(CLASSTAG, String.format("Event: %s", event.getType()));
        switch (event.getType()) {
            case CUEPOINTS_CHANGED:
                videoPlayer.setAdsTimeline(streamManager);
                break;
            case STARTED:
                onAdStarted(event);
                break;
        }
    }

    /** AdsLoadedListener implementation **/

    @Override
    public void onAdsManagerLoaded(AdsManagerLoadedEvent event) {
        streamManager = event.getStreamManager();

        // Create the ads rendering settings
        AdsRenderingSettings adsRenderingSettings = sdkFactory.createAdsRenderingSettings();
        adsRenderingSettings.setFocusSkipButtonWhenAvailable(true);

        // Initialize the stream manager
        streamManager.addAdErrorListener(this);
        streamManager.addAdEventListener(this);
        streamManager.init(adsRenderingSettings);
    }
}

