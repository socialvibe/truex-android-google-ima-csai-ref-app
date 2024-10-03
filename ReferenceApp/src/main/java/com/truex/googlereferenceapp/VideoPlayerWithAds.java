// Copyright 2014 Google Inc. All Rights Reserved.

package com.truex.googlereferenceapp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.ForwardingPlayer;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.dash.DefaultDashChunkSource;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.ui.PlayerView;

import com.google.ads.interactivemedia.v3.api.AdPodInfo;
import com.google.ads.interactivemedia.v3.api.player.AdMediaInfo;
import com.google.ads.interactivemedia.v3.api.player.ContentProgressProvider;
import com.google.ads.interactivemedia.v3.api.player.VideoAdPlayer;
import com.google.ads.interactivemedia.v3.api.player.VideoProgressUpdate;

import java.util.ArrayList;
import java.util.List;

/**
 * Video player that can play content video and ads.
 */
@OptIn(markerClass = UnstableApi.class)
public class VideoPlayerWithAds extends RelativeLayout {
  private static final String CLASSTAG = VideoPlayerWithAds.class.getSimpleName();

  // The wrapped video player.
  private PlayerView playerView;
  private ExoPlayer videoPlayer;

  private AdMediaInfo currentAd;
  private AdPodInfo currentAdPod;

  private ViewGroup adUiContainer;
  private boolean isAdPlaying;
  private String contentVideoUrl;

  private long savedAdPosition;
  private long savedContentPosition;

  private boolean contentHasCompleted;

  // VideoAdPlayer interface implementation for the SDK to send ad play/pause type events.
  private VideoAdPlayer videoAdPlayer;

  // ContentProgressProvider interface implementation for the SDK to check content progress.
  private ContentProgressProvider contentProgressProvider;

  private final List<VideoAdPlayer.VideoAdPlayerCallback> adCallbacks = new ArrayList<>();

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

  private void init() {
    isAdPlaying = false;
    contentHasCompleted = false;
    savedAdPosition = 0;
    savedContentPosition = 0;
    playerView = this.getRootView().findViewById(R.id.player_view);
    videoPlayer = new ExoPlayer.Builder(this.getContext()).build();

    ForwardingPlayer playerWrapper = new ForwardingPlayer(videoPlayer) {
      @Override
      public void seekToDefaultPosition() {
        seekToDefaultPosition(getCurrentMediaItemIndex());
      }

      @Override
      public void seekToDefaultPosition(int windowIndex) {
        seekTo(windowIndex, /* positionMs= */ C.TIME_UNSET);
      }

      @Override
      public void seekTo(long positionMs) {
        seekTo(getCurrentMediaItemIndex(), positionMs);
      }

      @Override
      public void seekTo(int windowIndex, long seekPos) {
        // @TODO seek snapback
        videoPlayer.seekTo(windowIndex, seekPos);
      }
    };
    playerView.setPlayer(playerWrapper);

    adUiContainer = this.getRootView().findViewById(R.id.adUiContainer);

    // Define VideoAdPlayer connector.
    videoAdPlayer =
      new VideoAdPlayer() {
        @Override
        public void loadAd(@NonNull AdMediaInfo adMediaInfo, @NonNull AdPodInfo adPodInfo) {
          currentAd = adMediaInfo;
          currentAdPod = adPodInfo;
          isAdPlaying = false;
          setStreamUrl(adMediaInfo.getUrl());
        }

        @Override
        public void playAd(@NonNull AdMediaInfo info) {
          // @TODO track progress events instead
          isAdPlaying = true;
          videoPlayer.play();
        }

        @Override
        public void stopAd(@NonNull AdMediaInfo info) {
          videoPlayer.stop();
        }

        @Override
        public void pauseAd(@NonNull AdMediaInfo info) {
          videoPlayer.pause();
        }

        @Override
        public void release() {
          // any clean up that needs to be done
        }

        @Override
        public void addCallback(@NonNull VideoAdPlayerCallback videoAdPlayerCallback) {
          adCallbacks.add(videoAdPlayerCallback);
        }

        @Override
        public void removeCallback(@NonNull VideoAdPlayerCallback videoAdPlayerCallback) {
          adCallbacks.remove(videoAdPlayerCallback);
        }

        @Override
        public int getVolume() {
          return Math.round(videoPlayer.getVolume() * 100);
        }

        @Override
        @NonNull
        public VideoProgressUpdate getAdProgress() {
          if (!isAdPlaying || videoPlayer.getDuration() <= 0) {
            return VideoProgressUpdate.VIDEO_TIME_NOT_READY;
          }
          return new VideoProgressUpdate(
            videoPlayer.getCurrentPosition(), videoPlayer.getDuration());
        }
      };

    contentProgressProvider = () -> {
      if (isAdPlaying || videoPlayer.getDuration() <= 0) {
        return VideoProgressUpdate.VIDEO_TIME_NOT_READY;
      }
      return new VideoProgressUpdate(
        videoPlayer.getCurrentPosition(), videoPlayer.getDuration());
    };

    // Set player callbacks for delegating major video events.
    videoPlayer.addListener(
      new Player.Listener() {
        public void onIsPlayingChanged(boolean isPlaying) {
          if (isAdPlaying) {
            if (isPlaying) {
              for (VideoAdPlayer.VideoAdPlayerCallback callback : adCallbacks) {
                callback.onPlay(currentAd);
                // @TODO call onResume?
              }

              // Ensure we are polling the ad progress.
              updateAdProgress();

            } else {
              for (VideoAdPlayer.VideoAdPlayerCallback callback : adCallbacks) {
                callback.onPause(currentAd);
              }
            }
          }
        }

        public void onPlayerError(@NonNull PlaybackException error) {
          if (isAdPlaying) {
            for (VideoAdPlayer.VideoAdPlayerCallback callback : adCallbacks) {
              callback.onError(currentAd);
            }
          }
        }

        public void onPlaybackStateChanged(@Player.State int playbackState) {
          if (playbackState == Player.STATE_ENDED) {
            if (isAdPlaying) {
              for (VideoAdPlayer.VideoAdPlayerCallback callback : adCallbacks) {
                callback.onEnded(currentAd);
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

  private void updateAdProgress() {
    if (!isAdPlaying || currentAd == null) return;

    long position = videoPlayer.getCurrentPosition();
    long duration = videoPlayer.getDuration();

    VideoProgressUpdate progress = (duration == C.TIME_UNSET) ? VideoProgressUpdate.VIDEO_TIME_NOT_READY
      : new VideoProgressUpdate(position, duration);

    for (VideoAdPlayer.VideoAdPlayerCallback callback : adCallbacks) {
      callback.onAdProgress(currentAd, progress);
    }

    if (videoPlayer.isPlaying()) {
      playerView.postDelayed(this::updateAdProgress, 1000);
    }
  }

  /**
   * Set the path of the video to be played as content.
   */
  public void setContentVideoPath(String contentVideoUrl) {
    this.contentVideoUrl = contentVideoUrl;
    contentHasCompleted = false;
  }

  public void setStreamUrl(String streamUrl) {
    Log.i(CLASSTAG, "*** setStreamUrl: " + streamUrl);

    if (streamUrl == null || streamUrl.isEmpty()) {
      videoPlayer.stop();
      return;
    }

    DataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(getContext());
    int type = Util.inferContentType(Uri.parse(streamUrl));
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(streamUrl));

    @SuppressLint("SwitchIntDef")
    MediaSource mediaSource = switch (type) {
      case C.CONTENT_TYPE_HLS ->
        new HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem);
      case C.CONTENT_TYPE_DASH -> new DashMediaSource.Factory(
        new DefaultDashChunkSource.Factory(dataSourceFactory), dataSourceFactory)
        .createMediaSource(mediaItem);
      case C.CONTENT_TYPE_OTHER ->
        new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem);
      default -> throw new UnsupportedOperationException("Unknown stream type: " + type);
    };

    videoPlayer.setMediaSource(mediaSource);
    videoPlayer.prepare();
  }

  /**
   * Save the playback progress state of the currently playing video. This is called when content is
   * paused to prepare for ad playback or when app is backgrounded.
   */
  public void savePosition() {
    if (isAdPlaying) {
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
    if (isAdPlaying) {
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
    if (!isAdPlaying) {
      videoPlayer.seekTo(time);
    }
    savedContentPosition = time;
  }

  /**
   * Returns current content video play time.
   */
  public long getCurrentContentTime() {
    if (isAdPlaying) {
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
    disableControls();
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
    isAdPlaying = false;
    setStreamUrl(contentVideoUrl);
    enableControls();
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
    return isAdPlaying;
  }

  public ContentProgressProvider getContentProgressProvider() {
    return contentProgressProvider;
  }

  public void enableControls() {
    enableControls(true);
  }

  public void disableControls() {
    enableControls(false);
  }

  private void enableControls(boolean enable) {
    if (enable) {
      playerView.showController();
    } else {
      playerView.hideController();
    }
    playerView.setControllerAutoShow(enable);
    playerView.setUseController(enable);
  }


  // On some older 4K devices we need to actually hide the actual playback view so that truex videos can show.
  public void hidePlayer() {
    playerView.setVisibility(View.GONE);
  }

  public void showPlayer() {
    playerView.setVisibility(View.VISIBLE);
  }
}
