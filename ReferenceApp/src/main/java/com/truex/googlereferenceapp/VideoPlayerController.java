// Copyright 2014 Google Inc. All Rights Reserved.

package com.truex.googlereferenceapp;

import android.content.Context;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.google.ads.interactivemedia.v3.api.Ad;
import com.google.ads.interactivemedia.v3.api.AdDisplayContainer;
import com.google.ads.interactivemedia.v3.api.AdErrorEvent;
import com.google.ads.interactivemedia.v3.api.AdEvent;
import com.google.ads.interactivemedia.v3.api.AdsLoader;
import com.google.ads.interactivemedia.v3.api.AdsManager;
import com.google.ads.interactivemedia.v3.api.AdsManagerLoadedEvent;
import com.google.ads.interactivemedia.v3.api.AdsRenderingSettings;
import com.google.ads.interactivemedia.v3.api.AdsRequest;
import com.google.ads.interactivemedia.v3.api.ImaSdkFactory;
import com.google.ads.interactivemedia.v3.api.ImaSdkSettings;
import com.truex.adrenderer.TruexAdEvent;
import com.truex.adrenderer.TruexAdOptions;
import com.truex.adrenderer.TruexAdRenderer;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;

/** Ads logic for handling the IMA SDK integration code and events. */
public class VideoPlayerController {
  private static final String CLASSTAG = VideoPlayerController.class.getSimpleName();

  // The AdsLoader instance exposes the requestAds method.
  private final AdsLoader adsLoader;

  // AdsManager exposes methods to control ad playback and listen to ad events.
  private AdsManager adsManager;

  // Ad-enabled video player.
  private final VideoPlayerWithAdPlayback videoPlayerWithAdPlayback;

  // VAST ad tag URL to use when requesting ads during video playback.
  private String currentAdTagUrl;

  private String currentAdTagResponse;

  // Tracks if the SDK is playing an ad, since the SDK might not necessarily use the video
  // player provided to play the video ad.
  private boolean isAdPlaying;

  // View that handles taps to toggle ad pause/resume during video playback.
  private final ViewGroup videoContainer;

  private final PopupCallback popupCallback;

  private TruexAdRenderer truexAdRenderer;
  private Boolean truexCredit;

  // Inner class implementation of AdsLoader.AdsLoaderListener.
  private class AdsLoadedListener implements AdsLoader.AdsLoadedListener {
    /** An event raised when ads are successfully loaded from the ad server via AdsLoader. */
    @Override
    public void onAdsManagerLoaded(AdsManagerLoadedEvent adsManagerLoadedEvent) {
      // Ads were successfully loaded, so get the AdsManager instance. AdsManager has
      // events for ad playback and errors.
      adsManager = adsManagerLoadedEvent.getAdsManager();

      // Attach event and error event listeners.
      adsManager.addAdErrorListener(
          new AdErrorEvent.AdErrorListener() {
            /** An event raised when there is an error loading or playing ads. */
            @Override
            public void onAdError(@NonNull AdErrorEvent adErrorEvent) {
              Log.e(CLASSTAG, "Ad Error: " + adErrorEvent.getError().getMessage());
              cleanupAds();
              resumeContent();
            }
          });
      adsManager.addAdEventListener(
          new AdEvent.AdEventListener() {
            /** Responds to AdEvents. */
            @Override
            public void onAdEvent(@NonNull AdEvent adEvent) {
              Ad ad = adEvent.getAd();
              String adId = ad == null ? null : ad.getAdId();
              if (adEvent.getType() != AdEvent.AdEventType.AD_PROGRESS) {
                Log.i(CLASSTAG, "Ad Event: " + adId + ": " + adEvent.getType()
                  + " " + VideoPlayerWithAdPlayback.positionDisplay(videoPlayerWithAdPlayback.getStreamPosition()));
              }

              // These are the suggested event types to handle. For full list of all ad
              // event types, see the documentation for AdEvent.AdEventType.
              switch (adEvent.getType()) {
                case LOADED:
                  // AdEventType.LOADED will be fired when ads are ready to be
                  // played. AdsManager.start() begins ad playback. This method is
                  // ignored for VMAP or ad rules playlists, as the SDK will
                  // automatically start executing the playlist.

                  adsManager.start();

                  break;
                case STARTED:
                  if (ad.getAdSystem().contains("trueX")) {
                    try {
                      String params = ad.getTraffickingParameters();
                      JSONObject json = new JSONObject(params);
                      String url = json.getString("vast_config_url");
                      playInteractiveAd(url);
                    } catch (JSONException e) {
                      throw new RuntimeException(e);
                    }
                  } else {
                    videoPlayerWithAdPlayback.showPlayer();
                  }
                  break;
                case CONTENT_PAUSE_REQUESTED:
                  // AdEventType.CONTENT_PAUSE_REQUESTED is fired immediately before
                  // a video ad is played.
                  pauseContent();
                  break;
                case CONTENT_RESUME_REQUESTED:
                  // AdEventType.CONTENT_RESUME_REQUESTED is fired when the ad is
                  // completed and you should start playing your content.
                  resumeContent();
                  break;
                case PAUSED:
                  isAdPlaying = false;
                  videoPlayerWithAdPlayback.enableControls();
                  break;
                case RESUMED:
                  isAdPlaying = true;
                  videoPlayerWithAdPlayback.disableControls();
                  break;
                case ALL_ADS_COMPLETED:
                  cleanupAds();
                  adsLoader.release();
                  break;
                default:
                  break;
              }
            }
          });
      AdsRenderingSettings adsRenderingSettings =
          ImaSdkFactory.getInstance().createAdsRenderingSettings();
      adsManager.init(adsRenderingSettings);
    }
  }

  public VideoPlayerController(
      Context context,
      VideoPlayerWithAdPlayback videoPlayerWithAdPlayback,
      ViewGroup videoContainer,
      String language,
      PopupCallback callback) {
    this.videoPlayerWithAdPlayback = videoPlayerWithAdPlayback;
    this.videoContainer = videoContainer;
    this.popupCallback = callback;
    isAdPlaying = false;

    // Create an AdsLoader and optionally set the language.
    ImaSdkFactory sdkFactory = ImaSdkFactory.getInstance();
    ImaSdkSettings imaSdkSettings = sdkFactory.createImaSdkSettings();
    imaSdkSettings.setDebugMode(true);
    imaSdkSettings.setLanguage(language);

    // Container with references to video player and ad UI ViewGroup.
    AdDisplayContainer adDisplayContainer = ImaSdkFactory.createAdDisplayContainer(
      videoPlayerWithAdPlayback.getAdUiContainer(),
      videoPlayerWithAdPlayback.getVideoAdPlayer());
    adsLoader = sdkFactory.createAdsLoader(context, imaSdkSettings, adDisplayContainer);

    adsLoader.addAdErrorListener(
        new AdErrorEvent.AdErrorListener() {
          /** An event raised when there is an error loading or playing ads. */
          @Override
          public void onAdError(@NonNull AdErrorEvent adErrorEvent) {
            Log.e(CLASSTAG, "Ad Error: " + adErrorEvent.getError());
            resumeContent();
          }
        });

    adsLoader.addAdsLoadedListener(new AdsLoadedListener());
  }

  private void pauseContent() {
    videoPlayerWithAdPlayback.pauseContentForAdPlayback();
    isAdPlaying = false;
    setPlayPauseOnAdTouch();
  }

  private void resumeContent() {
    videoPlayerWithAdPlayback.resumeContentAfterAdPlayback();
    videoPlayerWithAdPlayback.setVisibility(View.VISIBLE);
    isAdPlaying = false;
    removePlayPauseOnAdTouch();
  }

  /** Set the ad tag URL the player should use to request ads when playing a content video. */
  public void setAdTagUrl(String adTagUrl) {
    currentAdTagUrl = adTagUrl;
  }

  public void setAdTagResponse(String adTagResponse) {
    currentAdTagResponse = adTagResponse;
  }

  /** Request and subsequently play video ads from the ad server. */
  public void requestAndPlayAds() {
    if ((currentAdTagUrl == null || currentAdTagUrl.isEmpty()) &&
          (currentAdTagResponse) == null || currentAdTagResponse.isEmpty()) {
      Log.e(CLASSTAG, "No VAST ad tag URL specified");
      resumeContent();
      return;
    }

    // Since we're switching to a new video, tell the SDK the previous video is finished.
    cleanupAds();

    // Create the ads request.
    AdsRequest request = ImaSdkFactory.getInstance().createAdsRequest();

    if (currentAdTagResponse != null) {
      request.setAdsResponse(currentAdTagResponse);
    } else {
      request.setAdTagUrl(currentAdTagUrl);
    }

    request.setContentProgressProvider(videoPlayerWithAdPlayback.getContentProgressProvider());

    // Request the ad. After the ad is loaded, onAdsManagerLoaded() will be called.
    adsLoader.requestAds(request);
  }

  /** Touch to toggle play/pause during ad play instead of seeking. */
  private void setPlayPauseOnAdTouch() {
    // Use AdsManager pause/resume methods instead of the video player pause/resume methods
    // in case the SDK is using a different, SDK-created video player for ad playback.
    videoContainer.setOnTouchListener((View view, MotionEvent event) -> {
      // If an ad is playing, touching it will toggle playback.
      if (event.getAction() == MotionEvent.ACTION_DOWN) {
        if (isAdPlaying) {
          adsManager.pause();
        } else {
          adsManager.resume();
        }
        return true;
      } else {
        return false;
      }
    });
  }

  /** Remove the play/pause on touch behavior. */
  private void removePlayPauseOnAdTouch() {
    videoContainer.setOnTouchListener(null);
  }

  /**
   * Set metadata about the content video. In more complex implementations, this might more than
   * just a URL and could trigger additional decisions regarding ad tag selection.
   */
  public void setContentVideo(String videoPath) {
    videoPlayerWithAdPlayback.setContentVideoUrl(videoPath);
  }

  private void playInteractiveAd(String vastUrl) {
    adsManager.pause();

    // pre seek to the end in case we want to play the fallback ads.
    videoPlayerWithAdPlayback.seekToEnd();

//    if (adsLoader != null) {
//      this.playFallbackAds();
//      return;
//    }

    videoPlayerWithAdPlayback.disableControls();

    // On some older 4K devices we need to actually hide the actual playback view so that truex videos can show.
    videoPlayerWithAdPlayback.hidePlayer();

    truexCredit = false;
    truexAdRenderer = new TruexAdRenderer(videoPlayerWithAdPlayback.getContext());

    truexAdRenderer.addEventListener(null, this::onTruexAdEvent); // listen to all events.


    TruexAdOptions options = new TruexAdOptions();
    truexAdRenderer.init(vastUrl, options);
    truexAdRenderer.start(videoContainer);
  }

  private void onTruexAdEvent(TruexAdEvent event, Map<String, ?> data) {
    Log.i(CLASSTAG, "onTruexAdEvent: " + event);
    switch (event) {
      case AD_COMPLETED:
      case AD_ERROR:
      case NO_ADS_AVAILABLE:
        onTruexAdCompleted();
        break;
      case AD_FREE_POD:
        truexCredit = true;
        break;
      case POPUP_WEBSITE:
        String url = (String)data.get("url");
        popupCallback.onPopup(url);
        break;
      case AD_STARTED:
        videoPlayerWithAdPlayback.disableControls();
        break;

      case AD_FETCH_COMPLETED:
      case USER_CANCEL:
      case OPT_IN:
      case OPT_OUT:
      case SKIP_CARD_SHOWN:
      default:
        break;
    }
  }

  private void onTruexAdCompleted(){
    videoPlayerWithAdPlayback.showPlayer();
    if (truexAdRenderer != null) {
      truexAdRenderer.stop();
      truexAdRenderer = null;
    }
    if (truexCredit) {
      // The user received true[ATTENTION] credit
      // Resume the content stream (and skip any linear ads)
      resumeContentStream();
    } else {
      // The user did not receive credit
      // Continue the content stream and display linear ads
      playFallbackAds();
    }
  }

  public void resumeContentStream() {
    if (adsManager != null) {
      videoPlayerWithAdPlayback.logPosition("resumeContentStream");
      adsManager.discardAdBreak();
      adsManager.resume();
    }
  }

  public void playFallbackAds() {
    if (adsManager != null) {
      videoPlayerWithAdPlayback.logPosition("playFallbackAds");
      //adsManager.skip(); // "officially" not supported by Google IMA
      videoPlayerWithAdPlayback.seekToEnd(); // ensure the truex placeholder ad completes ASAP
      adsManager.resume();
    }
  }

  /**
   * Save position of the video, whether content or ad. Can be called when the app is paused, for
   * example.
   */
  public void pause() {
    videoPlayerWithAdPlayback.savePosition();
    if (adsManager != null && videoPlayerWithAdPlayback.getIsAdDisplayed()) {
      if (truexAdRenderer != null) truexAdRenderer.pause();
      adsManager.pause();
    } else {
      videoPlayerWithAdPlayback.pause();
    }
  }

  /**
   * Restore the previously saved progress location of the video. Can be called when the app is
   * resumed.
   */
  public void resume() {
    videoPlayerWithAdPlayback.restorePosition();
    if (adsManager != null && videoPlayerWithAdPlayback.getIsAdDisplayed()) {
      adsManager.resume();
      if (truexAdRenderer != null) truexAdRenderer.resume();
    } else {
      videoPlayerWithAdPlayback.play();
    }
  }

  public void destroy() {
    videoPlayerWithAdPlayback.release();
    cleanupAds();
    if (adsLoader != null) {
      adsLoader.release();
    }
  }

  private void cleanupAds() {
    if (truexAdRenderer != null) {
      truexAdRenderer.stop();
      truexAdRenderer = null;
    }
    if (adsManager != null) {
      adsManager.destroy();
      adsManager = null;
    }
  }
}
