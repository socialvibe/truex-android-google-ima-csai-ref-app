package com.truex.googlereferenceapp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.C;
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
import java.util.Formatter;
import java.util.List;
import java.util.Locale;

/**
 * Video player that can play content video and ads.
 */
@OptIn(markerClass = UnstableApi.class)
public class VideoPlayerWithAdPlayback extends RelativeLayout {
  private static final String CLASSTAG = VideoPlayerWithAdPlayback.class.getSimpleName();

  // The wrapped video player.
  private PlayerView playerView;
  private ExoPlayer videoPlayer;

  private AdMediaInfo currentAd;

  private String contentVideoUrl;

  private String currentStreamUrl;

  private long savedAdPosition;
  private long savedContentPosition;

  private boolean contentHasCompleted;

  // ContentProgressProvider interface implementation for the SDK to check content progress.
  private ContentProgressProvider contentProgressProvider;

  private final List<VideoAdPlayer.VideoAdPlayerCallback> adCallbacks = new ArrayList<>();

  public VideoPlayerWithAdPlayback(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
  }

  public VideoPlayerWithAdPlayback(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public VideoPlayerWithAdPlayback(Context context) {
    super(context);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    init();
  }

  private void init() {
    contentHasCompleted = false;
    savedAdPosition = 0;
    savedContentPosition = 0;
    videoPlayer = new ExoPlayer.Builder(this.getContext()).build();

    playerView = this.getRootView().findViewById(R.id.player_view);
    playerView.setPlayer(videoPlayer);

    contentProgressProvider = () -> {
      if (currentAd != null || videoPlayer.getDuration() <= 0) {
        return VideoProgressUpdate.VIDEO_TIME_NOT_READY;
      }
      return new VideoProgressUpdate(
        videoPlayer.getCurrentPosition(), videoPlayer.getDuration());
    };

    // Set player callbacks for delegating major video events.
    videoPlayer.addListener(
      new Player.Listener() {
        public void onIsPlayingChanged(boolean isPlaying) {
          if (currentAd != null) {
            if (isPlaying) {
              boolean hasStarted = videoPlayer.getCurrentPosition() > 0;
              for (VideoAdPlayer.VideoAdPlayerCallback callback : adCallbacks) {
                if (hasStarted) {
                  callback.onResume(currentAd);
                } else {
                  callback.onPlay(currentAd);
                }
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
          if (currentAd == null) return;
          for (VideoAdPlayer.VideoAdPlayerCallback callback : adCallbacks) {
            callback.onError(currentAd);
          }
        }

        public void onPlaybackStateChanged(@Player.State int playbackState) {
          if (playbackState == Player.STATE_ENDED) {
            if (currentAd != null) {
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

  static public String positionDisplay(long position) {
    StringBuilder formatBuilder = new StringBuilder();
    Formatter formatter = new Formatter(formatBuilder, Locale.getDefault());
    String timeDisplay = Util.getStringForTime(formatBuilder, formatter, position);
    return timeDisplay;
  }

  public void logPosition(String context) {
    long streamPos = videoPlayer.getCurrentPosition();
    int state = videoPlayer.getPlaybackState();
    String stateLabel = switch (state) {
      case Player.STATE_IDLE -> "idle";
      case Player.STATE_BUFFERING -> "buffering";
      case Player.STATE_READY -> "ready";
      case Player.STATE_ENDED -> "ended";
      default -> "unknown: " + state;
    };
    boolean loading = videoPlayer.isLoading();
    boolean playing = videoPlayer.isPlaying();
    // For now we don't need full player state details
    //logPosition(context + ": state: " + stateLabel + " playing: " + playing, streamPos);
    logPosition(context, streamPos);
  }

  static public void logPosition(String context, long position) {
    StringBuilder msg = new StringBuilder();
    msg.append("*** ");
    msg.append(context);
    msg.append(" at ");
    msg.append(positionDisplay(position));
    Log.i(CLASSTAG, msg.toString());
  }

  private void updateAdProgress() {
    if (currentAd == null) return;

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
  public void setContentVideoUrl(String contentVideoUrl) {
    this.contentVideoUrl = contentVideoUrl;
    contentHasCompleted = false;
  }

  public void setStreamUrl(String streamUrl) {
    Log.i(CLASSTAG, "*** setStreamUrl: " + streamUrl);

    if (streamUrl == null || streamUrl.isEmpty()) {
      videoPlayer.stop();
      return;
    }

    currentStreamUrl = streamUrl;

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
    if (currentAd != null) {
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
    if (currentAd != null) {
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
  public void seekTo(long positionMs) {
    logPosition("seekTo", positionMs);
    // Seek only if an ad is not playing. Save the content position either way.
    if (currentAd == null) {
      videoPlayer.seekTo(positionMs);
    }
    savedContentPosition = positionMs;
  }

  /**
   * Useful for skipping an ad video.
   */
  public void seekToEnd() {
    long duration = videoPlayer.getDuration();
    if (duration > 0) {
      long beforeEndPos = duration - 500; // allow a bit more playback to get the ad completion.
      logPosition("seekToEnd", beforeEndPos);
      videoPlayer.seekTo(beforeEndPos);
    }
  }

  public void stop() {
    Log.i(CLASSTAG, "stop");
    videoPlayer.stop();
  }

  /**
   * For final player cleanup
   */
  public void release() {
    Log.i(CLASSTAG, "release");
    videoPlayer.release();
  }

  /**
   * Returns current content video play time.
   */
  public long getContentPosition() {
    if (currentAd != null) {
      return savedContentPosition;
    } else {
      return videoPlayer.getCurrentPosition();
    }
  }

  // i.e. ad or content
  public long getStreamPosition() {
    return videoPlayer.getCurrentPosition();
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
      Log.w(CLASSTAG, "resumeContentAfterAdPlayback: No content URL specified.");
      return;
    }
    Log.i(CLASSTAG, "resumeContentAfterAdPlayback");

    // Ensure there are no remnants of any ad playbacks (matters on some older TV devices)
    videoPlayer.stop();

    setStreamUrl(contentVideoUrl);
    enableControls();

    if (savedContentPosition > 0) seekTo(savedContentPosition);

    videoPlayer.play();
    showPlayer();

    if (contentHasCompleted) {
      videoPlayer.pause();
    }
  }

  /**
   * Returns an implementation of the SDK's VideoAdPlayer interface.
   */
  public VideoAdPlayer getVideoAdPlayer() {
    return new VideoAdPlayer() {
      @Override
      public void loadAd(@NonNull AdMediaInfo adMediaInfo, @NonNull AdPodInfo adPodInfo) {
        logPosition("loadAd");
        currentAd = adMediaInfo;
        setStreamUrl(adMediaInfo.getUrl());
      }

      @Override
      public void playAd(@NonNull AdMediaInfo info) {
        logPosition("playAd");
        videoPlayer.play();
      }

      @Override
      public void stopAd(@NonNull AdMediaInfo info) {
        currentAd = null;
      }

      @Override
      public void pauseAd(@NonNull AdMediaInfo info) {
        logPosition("pauseAd");
        videoPlayer.pause();
      }

      @Override
      public void release() {
        // nothing more to do
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
        if (currentAd == null || videoPlayer.getDuration() <= 0) {
          return VideoProgressUpdate.VIDEO_TIME_NOT_READY;
        }
        return new VideoProgressUpdate(
          videoPlayer.getCurrentPosition(), videoPlayer.getDuration());
      }
    };
  }

  public void setAdMarkers(List<AdBreak> adBreaks) {
    long[] extraAdGroupTimesMs = null;
    boolean[] extraPlayedAdGroups = null;
    if (adBreaks != null && adBreaks.size() > 0) {
      // Set up the ad markers.
      extraAdGroupTimesMs = new long[adBreaks.size()];
      extraPlayedAdGroups = new boolean[adBreaks.size()];
      for (int i = 0; i < adBreaks.size(); i++) {
        AdBreak adBreak = adBreaks.get(i);
        extraAdGroupTimesMs[i] = adBreak.contentPosition;
        extraPlayedAdGroups[i] = adBreak.wasStarted;
      }
    }
    playerView.setExtraAdGroupMarkers(extraAdGroupTimesMs, extraPlayedAdGroups);
  }

  public boolean isPlayingAd() {
    return currentAd != null;
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
    this.setVisibility(View.GONE);
  }

  public void showPlayer() {
    this.setVisibility(View.VISIBLE);
  }
}
