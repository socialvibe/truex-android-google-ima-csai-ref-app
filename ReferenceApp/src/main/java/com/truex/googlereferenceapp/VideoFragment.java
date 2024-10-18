package com.truex.googlereferenceapp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/** The main fragment for displaying video content. */
public class VideoFragment extends Fragment {
  private static final String CLASSTAG = VideoFragment.class.getSimpleName();

  private VideoPlayerController videoPlayerController;

  public boolean isTouchDevice() {
    Context context = getContext();
    return context != null && context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
      && !isTV();
  }

  public boolean isTV() {
    Context context = getContext();
    if (context == null) return false;
    UiModeManager uiModeManager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
    return uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    Log.i(CLASSTAG, "onCreateView");
    if (isTouchDevice()) {
      // Ensure we are in landscape for phones and tablets.
      Activity activity = getActivity();
      if (activity != null) activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }

    View rootView = inflater.inflate(R.layout.fragment_video, container, false);
    return rootView;
  }

  @SuppressLint("SourceLockedOrientationActivity")
  @Override
  public void onDetach() {
    Log.i(CLASSTAG, "onDetach");
    if (isTouchDevice()) {
      // Restore portrait orientation for normal usage on phones and tablets.
      Activity activity = getActivity();
      if (activity != null) activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
    }
    super.onDetach();
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

    // Use a sample vast xml for demonstration. Ensure we refer to CTV vs mobile ads.
    //videoPlayerController.setAdTagUrl("https://pubads.g.doubleclick.net/gampad/ads?sz=640x480&iu=/124319096/external/ad_rule_samples&ciu_szs=300x250&ad_rule=1&impl=s&gdfp_req=1&env=vp&output=vmap&unviewed_position_start=1&cust_params=deployment%3Ddevsite%26sample_ar%3Dpremidpost&cmsid=496&vid=short_onecue&correlator=");
    boolean isTV = getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    int vmapXmlResource = isTV ? R.raw.ctv_truex_vmap : R.raw.mobile_truex_vmap;
    videoPlayerController.setAdTagResponse(getRawFileContents(vmapXmlResource));

    final Handler handler = new Handler(Looper.getMainLooper());
    handler.postDelayed(new Runnable() {
      @Override
      public void run() {
        if (videoPlayerController != null) {
          videoPlayerController.requestAndPlayAds();
        }
      }
    }, 100);
  }

  private void initUi() throws IOException {
    View rootView = getView();
    VideoPlayerWithAdPlayback videoPlayerWithAdPlayback = rootView.findViewById(R.id.videoPlayerWithAdPlayback);
    ViewGroup videoContainer = rootView.findViewById(R.id.videoContainer);

    videoPlayerController =
      new VideoPlayerController(
        this.getActivity(),
        videoPlayerWithAdPlayback,
        videoContainer,
        getString(R.string.ad_ui_lang),
        (popupUrl) -> {
          Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(popupUrl));
          startActivity(browserIntent);
        });

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
    Log.i(CLASSTAG, "onDestroy");
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
