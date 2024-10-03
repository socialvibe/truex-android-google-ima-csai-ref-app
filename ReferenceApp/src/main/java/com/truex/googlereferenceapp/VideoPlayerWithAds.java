// Copyright 2014 Google Inc. All Rights Reserved.

package com.truex.googlereferenceapp;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.ui.PlayerView;

import com.google.ads.interactivemedia.v3.api.AdPodInfo;
import com.google.ads.interactivemedia.v3.api.player.AdMediaInfo;
import com.google.ads.interactivemedia.v3.api.player.ContentProgressProvider;
import com.google.ads.interactivemedia.v3.api.player.VideoAdPlayer;
import com.google.ads.interactivemedia.v3.api.player.VideoProgressUpdate;
import com.truex.googlereferenceapp.player.VideoPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Video player that can play content video and ads.
 */
public class VideoPlayerWithAds extends RelativeLayout {

  // The wrapped video player.
  private PlayerView playerView;
  private VideoPlayer videoPlayer;

  // A Timer to help track media updates
  private Timer timer;

  // Track the currently playing media file. If doing preloading, this will need to be an
  // array or other data structure.
  private AdMediaInfo adMediaInfo;

  // The SDK will render ad playback UI elements into this ViewGroup.
  private ViewGroup adUiContainer;

  // Used to track if the current video is an ad (as opposed to a content video).
  private boolean isAdDisplayed;

  // Used to track the current content video URL to resume content playback.
  private String contentVideoUrl;

  // The saved position in the ad to resume if app is backgrounded during ad playback.
  private long savedAdPosition;

  // The saved position in the content to resume to after ad playback or if app is backgrounded
  // during content playback.
  private long savedContentPosition;

  // Used to track if the content has completed.
  private boolean contentHasCompleted;

  // VideoAdPlayer interface implementation for the SDK to send ad play/pause type events.
  private VideoAdPlayer videoAdPlayer;

  // ContentProgressProvider interface implementation for the SDK to check content progress.
  private ContentProgressProvider contentProgressProvider;

  private final List<VideoAdPlayer.VideoAdPlayerCallback> adCallbacks =
    new ArrayList<VideoAdPlayer.VideoAdPlayerCallback>(1);

  public VideoPlayerWithAds(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
  }

  public VideoPlayerWithAds(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public VideoPlayerWithAds(Context context) {
    super(context);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    init();
  }

  private void startTracking() {
    if (timer != null) {
      return;
    }
    timer = new Timer();
    TimerTask updateTimerTask =
      new TimerTask() {
        @Override
        public void run() {
          // Tell IMA the current video progress. A better implementation would be
          // reactive to events from the media player, instead of polling.
          for (VideoAdPlayer.VideoAdPlayerCallback callback : adCallbacks) {
            callback.onAdProgress(adMediaInfo, videoAdPlayer.getAdProgress());
          }
        }
      };
    int initialDelayMs = 250;
    int pollingTimeMs = 250;
    timer.schedule(updateTimerTask, pollingTimeMs, initialDelayMs);
  }

  private void stopTracking() {
    if (timer != null) {
      timer.cancel();
      timer = null;
    }
  }

  private void init() {
    isAdDisplayed = false;
    contentHasCompleted = false;
    savedAdPosition = 0;
    savedContentPosition = 0;
    playerView = this.getRootView().findViewById(R.id.player_view);
    videoPlayer = new VideoPlayer(this.getContext(), playerView);
    adUiContainer = (ViewGroup) this.getRootView().findViewById(R.id.adUiContainer);

    // Define VideoAdPlayer connector.
    videoAdPlayer =
      new VideoAdPlayer() {
        @Override
        public int getVolume() {
          return videoPlayer.getVolume();
        }

        @Override
        public void playAd(AdMediaInfo info) {
          startTracking();
          isAdDisplayed = true;
          videoPlayer.play();
        }

        @Override
        public void loadAd(AdMediaInfo info, AdPodInfo api) {
          adMediaInfo = info;
          isAdDisplayed = false;
          videoPlayer.setStreamUrl(info.getUrl());
        }

        @Override
        public void stopAd(AdMediaInfo info) {
          stopTracking();
          videoPlayer.stop();
        }

        @Override
        public void pauseAd(AdMediaInfo info) {
          stopTracking();
          videoPlayer.pause();
        }

        @Override
        public void release() {
          // any clean up that needs to be done
        }

        @Override
        public void addCallback(VideoAdPlayerCallback videoAdPlayerCallback) {
          adCallbacks.add(videoAdPlayerCallback);
        }

        @Override
        public void removeCallback(VideoAdPlayerCallback videoAdPlayerCallback) {
          adCallbacks.remove(videoAdPlayerCallback);
        }

        @Override
        public VideoProgressUpdate getAdProgress() {
          if (!isAdDisplayed || videoPlayer.getDuration() <= 0) {
            return VideoProgressUpdate.VIDEO_TIME_NOT_READY;
          }
          return new VideoProgressUpdate(
            videoPlayer.getCurrentPosition(), videoPlayer.getDuration());
        }
      };

    contentProgressProvider =
      new ContentProgressProvider() {
        @Override
        public VideoProgressUpdate getContentProgress() {
          if (isAdDisplayed || videoPlayer.getDuration() <= 0) {
            return VideoProgressUpdate.VIDEO_TIME_NOT_READY;
          }
          return new VideoProgressUpdate(
            videoPlayer.getCurrentPosition(), videoPlayer.getDuration());
        }
      };

    // Set player callbacks for delegating major video events.
    videoPlayer.addListener(
      new Player.Listener() {
        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
          if (isAdDisplayed) {
            if (isPlaying) {
              for (VideoAdPlayer.VideoAdPlayerCallback callback : adCallbacks) {
                callback.onPlay(adMediaInfo);
                // @TODO call onResume?
              }
            } else {
              for (VideoAdPlayer.VideoAdPlayerCallback callback : adCallbacks) {
                callback.onPause(adMediaInfo);
              }
            }
          }
        }

        public void onPlayerError(PlaybackException error) {
          if (isAdDisplayed) {
            for (VideoAdPlayer.VideoAdPlayerCallback callback : adCallbacks) {
              callback.onError(adMediaInfo);
            }
          }
        }

        public void onPlaybackStateChanged(@Player.State int playbackState) {
          if (playbackState == Player.STATE_ENDED) {
            if (isAdDisplayed) {
              for (VideoAdPlayer.VideoAdPlayerCallback callback : adCallbacks) {
                callback.onEnded(adMediaInfo);
              }
            } else {
              contentHasCompleted = true;
              for (VideoAdPlayer.VideoAdPlayerCallback callback : adCallbacks) {
                callback.onContentComplete();
              }
            }
          }
        }
      });
  }

  /**
   * Set the path of the video to be played as content.
   */
  public void setContentVideoPath(String contentVideoUrl) {
    this.contentVideoUrl = contentVideoUrl;
    videoPlayer.setStreamUrl(contentVideoUrl);
    contentHasCompleted = false;
  }

  /**
   * Save the playback progress state of the currently playing video. This is called when content is
   * paused to prepare for ad playback or when app is backgrounded.
   */
  public void savePosition() {
    if (isAdDisplayed) {
      savedAdPosition = videoPlayer.getCurrentPosition();
    } else {
      savedContentPosition = videoPlayer.getCurrentPosition();
    }
  }

  /**
   * Restore the currently loaded video to its previously saved playback progress state. This is
   * called when content is resumed after ad playback or when focus has returned to the app.
   */
  public void restorePosition() {
    if (isAdDisplayed) {
      videoPlayer.seekTo(savedAdPosition);
    } else {
      videoPlayer.seekTo(savedContentPosition);
    }
  }

  /**
   * Pauses the content video.
   */
  public void pause() {
    videoPlayer.pause();
  }

  /**
   * Plays the content video.
   */
  public void play() {
    videoPlayer.play();
  }

  /**
   * Seeks the content video.
   */
  public void seek(int time) {
    // Seek only if an ad is not playing. Save the content position either way.
    if (!isAdDisplayed) {
      videoPlayer.seekTo(time);
    }
    savedContentPosition = time;
  }

  /**
   * Returns current content video play time.
   */
  public long getCurrentContentTime() {
    if (isAdDisplayed) {
      return savedContentPosition;
    } else {
      return videoPlayer.getCurrentPosition();
    }
  }

  /**
   * Pause the currently playing content video in preparation for an ad to play, and disables the
   * media controller.
   */
  public void pauseContentForAdPlayback() {
    videoPlayer.enableControls(false);
    savePosition();
    videoPlayer.stop();
  }

  /**
   * Resume the content video from its previous playback progress position after an ad finishes
   * playing. Re-enables the media controller.
   */
  public void resumeContentAfterAdPlayback() {
    if (contentVideoUrl == null || contentVideoUrl.isEmpty()) {
      Log.w("ImaExample", "No content URL specified.");
      return;
    }
    isAdDisplayed = false;
    videoPlayer.setStreamUrl(contentVideoUrl);
    videoPlayer.enableControls(true);
    videoPlayer.seekTo(savedContentPosition);
    videoPlayer.play();

    if (contentHasCompleted) {
      videoPlayer.pause();
    }
  }

  /**
   * Returns the UI element for rendering video ad elements.
   */
  public ViewGroup getAdUiContainer() {
    return adUiContainer;
  }

  /**
   * Returns an implementation of the SDK's VideoAdPlayer interface.
   */
  public VideoAdPlayer getVideoAdPlayer() {
    return videoAdPlayer;
  }

  /**
   * Returns if an ad is displayed.
   */
  public boolean getIsAdDisplayed() {
    return isAdDisplayed;
  }

  public ContentProgressProvider getContentProgressProvider() {
    return contentProgressProvider;
  }

  public void enableControls() {
    // Calling enablePlaybackControls(0) with 0 milliseconds shows the controls until
    // disablePlaybackControls() is called.
    videoPlayer.enableControls(true);
  }

  public void disableControls() {
    videoPlayer.enableControls(false);
  }

  // On some older 4K devices we need to actually hide the actual playback view so that truex videos can show.
  public void hidePlayer() {
    videoPlayer.hide();
  }

  public void showPlayer() {
    videoPlayer.show();
  }
}
