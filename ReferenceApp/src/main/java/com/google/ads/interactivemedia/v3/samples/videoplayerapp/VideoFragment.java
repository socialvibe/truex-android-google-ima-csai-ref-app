package com.google.ads.interactivemedia.v3.samples.videoplayerapp;

import android.content.Intent;
import android.content.pm.PackageManager;
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/** The main fragment for displaying video content. */
public class VideoFragment extends Fragment {

  private VideoPlayerController videoPlayerController;
  private TextView videoTitle;
  private ScrollView videoExampleLayout;
  private final Boolean debugEnabled = false; // for now

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View rootView = inflater.inflate(R.layout.fragment_video, container, false);
    return rootView;
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    try {
      initUi();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void loadVideo() throws IOException {
    if (videoPlayerController == null) {
      return;
    }

    videoPlayerController.setContentVideo("https://ctv.truex.com/assets/reference-app-stream-no-ads-720p.mp4");
//    videoPlayerController.setAdTagUrl("https://pubads.g.doubleclick.net/gampad/ads?sz=640x480&iu=/124319096/external/ad_rule_samples&ciu_szs=300x250&ad_rule=1&impl=s&gdfp_req=1&env=vp&output=vmap&unviewed_position_start=1&cust_params=deployment%3Ddevsite%26sample_ar%3Dpremidpost&cmsid=496&vid=short_onecue&correlator=");

    // Ensure we refer to CTV vs mobile ads.
    boolean isTV = getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    int vmapXmlResource = isTV ? R.raw.ctv_truex_vmap : R.raw.mobile_truex_vmap;
    videoPlayerController.setAdTagResponse(getRawFileContents(vmapXmlResource));

    videoTitle.setText("Test Video");
  }

  private void initUi() throws IOException {
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
            },
            debugEnabled);

    playButton.requestFocus();

    loadVideo();
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

  private String getRawFileContents(int resourceId) throws IOException {
    InputStream vastContentStream = getContext().getResources().openRawResource(resourceId);

    StringBuilder stringBuilder = new StringBuilder();
    BufferedReader reader = null;
    try {
      reader = new BufferedReader(new InputStreamReader(vastContentStream));

      String line;
      while ((line = reader.readLine()) != null) {
        stringBuilder.append(line);
      }
    } finally {
      if (reader != null) {
        reader.close();
      }
    }

    return stringBuilder.toString();
  }
}
