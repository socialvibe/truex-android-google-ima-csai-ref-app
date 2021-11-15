package com.google.ads.interactivemedia.v3.samples.videoplayerapp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.fragment.app.Fragment;

/** The main fragment for displaying video content. */
public class VideoFragment extends Fragment {

  private VideoPlayerController videoPlayerController;
  private VideoItem videoItem;
  private TextView videoTitle;
  private ScrollView videoExampleLayout;
  private OnVideoFragmentViewCreatedListener viewCreatedCallback;
  private final Boolean debugEnabled = true;

  /** Listener called when the fragment's onCreateView is fired. */
  public interface OnVideoFragmentViewCreatedListener {
    public void onVideoFragmentViewCreated();
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View rootView = inflater.inflate(R.layout.fragment_video, container, false);
    if (viewCreatedCallback != null) {
      viewCreatedCallback.onVideoFragmentViewCreated();
    }
    return rootView;
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    initUi();
  }

  public void loadVideo(VideoItem videoItem) {
    this.videoItem = videoItem;
    if (videoPlayerController == null) {
      return;
    }

    videoPlayerController.setContentVideo(videoItem.getVideoUrl());
    videoPlayerController.setAdTagUrl(videoItem.getAdTagUrl());
    videoPlayerController.setAdTagResponse(videoItem.getAdTagResponse());
    videoTitle.setText(videoItem.getTitle());
  }

  private void initUi() {
    View rootView = getView();
    VideoPlayerWithAdPlayback videoPlayerWithAdPlayback =
        rootView.findViewById(R.id.videoPlayerWithAdPlayback);
    View playButton = rootView.findViewById(R.id.playButton);
    View videoContainer = rootView.findViewById(R.id.videoContainer);
    ViewGroup companionAdSlot = rootView.findViewById(R.id.companionAdSlot);

    videoTitle = rootView.findViewById(R.id.video_title);
    videoExampleLayout = rootView.findViewById(R.id.videoExampleLayout);
    videoExampleLayout.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
    videoExampleLayout.setSmoothScrollingEnabled(true);

    // Make the dummyScrollContent height the size of the screen height.
    DisplayMetrics displayMetrics = new DisplayMetrics();
    getActivity().getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
    ConstraintLayout constraintLayout = rootView.findViewById(R.id.constraintLayout);
    ConstraintSet forceHeight = new ConstraintSet();
    forceHeight.clone(constraintLayout);
    forceHeight.constrainHeight(R.id.dummyScrollContent, displayMetrics.heightPixels);
    forceHeight.applyTo(constraintLayout);

    final TextView logText = rootView.findViewById(R.id.logText);

    if (debugEnabled) {
      logText.setVisibility(View.VISIBLE);
    } else {
      logText.setVisibility(View.GONE);
    }

    // Provide an implementation of a logger so we can output SDK events to the UI.
    VideoPlayerController.Logger logger =
        new VideoPlayerController.Logger() {
          @Override
          public void log(String message) {
            Log.i("ImaExample", message);
            if (logText != null) {
              logText.append(message);
            }
          }
        };

    videoPlayerController =
        new VideoPlayerController(
            this.getActivity(),
            videoPlayerWithAdPlayback,
            playButton,
            videoContainer,
            getString(R.string.ad_ui_lang),
            companionAdSlot,
            logger,
            (popupUrl) -> {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(popupUrl));
                startActivity(browserIntent);
            });

    // If we've already selected a video, load it now.
    if (videoItem != null) {
      loadVideo(videoItem);
    }
  }

  /** Shows or hides all non-video UI elements to make the video as large as possible. */
  public void makeFullscreen(boolean isFullscreen) {
    for (int i = 0; i < videoExampleLayout.getChildCount(); i++) {
      View view = videoExampleLayout.getChildAt(i);
      // If it's not the video element, hide or show it, depending on fullscreen status.
      if (view.getId() != R.id.videoContainer) {
        if (isFullscreen) {
          view.setVisibility(View.GONE);
        } else {
          view.setVisibility(View.VISIBLE);
        }
      }
    }
  }

  public VideoPlayerController getVideoPlayerController() {
    return videoPlayerController;
  }

  @Override
  public void onPause() {
    if (videoPlayerController != null) {
      videoPlayerController.pause();
    }
    super.onPause();
  }

  @Override
  public void onResume() {
    if (videoPlayerController != null) {
      videoPlayerController.resume();
    }
    super.onResume();
  }

  @Override
  public void onDestroy() {
    if (videoPlayerController != null) {
      videoPlayerController.destroy();
      videoPlayerController = null;
    }
    super.onDestroy();
  }

  public boolean isVmap() {
    return videoItem.getIsVmap();
  }
}
