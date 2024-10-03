package com.truex.googlereferenceapp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.media3.ui.PlayerView;

import com.truex.googlereferenceapp.player.VideoPlayerWithAds;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class PlaybackPageFragment extends Fragment {

    protected VideoPlayerWithAds videoPlayerWithAds;

    public boolean isTouchDevice() {
        return getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (isTouchDevice()) {
            // Ensure we are in landscape for phones and tablets.
            Activity activity = getActivity();
            if (activity != null) activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }

        return inflater.inflate(R.layout.fragment_playback, container, false);
    }

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    public void onDetach() {
        if (isTouchDevice()) {
            // Restore portrait orientation for normal usage on phones and tablets.
            Activity activity = getActivity();
            if (activity != null) activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        }
        super.onDetach();
    }

    @Override
    public void onStart() {
        super.onStart();

        ViewGroup adUiContainer = getView().findViewById(R.id.ad_ui_container);
        PlayerView playerView = getView().findViewById(R.id.player_view);

        videoPlayerWithAds = new VideoPlayerWithAds(getContext(), playerView, adUiContainer);

        videoPlayerWithAds.setContentStreamUrl("https://ctv.truex.com/assets/reference-app-stream-no-ads-720p.mp4");

        // Use a sample vast xml for demonstration. Ensure we refer to CTV vs mobile ads.
        //videoPlayerWithAds.setAdTagsUri("https://pubads.g.doubleclick.net/gampad/ads?sz=640x480&iu=/124319096/external/ad_rule_samples&ciu_szs=300x250&ad_rule=1&impl=s&gdfp_req=1&env=vp&output=vmap&unviewed_position_start=1&cust_params=deployment%3Ddevsite%26sample_ar%3Dpremidpost&cmsid=496&vid=short_onecue&correlator=");
        boolean isTV = getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);
        int vmapXmlResource = isTV ? R.raw.ctv_truex_vmap : R.raw.mobile_truex_vmap;
        videoPlayerWithAds.setAdTagsVastResponse(getRawFileContents(vmapXmlResource));

        videoPlayerWithAds.requestAndPlayStream();
    }

    @Override
    public void onPause() {
        super.onPause();

        // Pause the playback
        if (videoPlayerWithAds != null) {
            videoPlayerWithAds.pause();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Resume the playback
        if (videoPlayerWithAds != null) {
            videoPlayerWithAds.resume();
        }
    }

    @Override
    public void onDestroy() {
        if (videoPlayerWithAds != null) {
            videoPlayerWithAds.release();
            videoPlayerWithAds = null;
        }
        super.onDestroy();
    }

    private String getRawFileContents(int resourceId) {
        InputStream vastContentStream = getContext().getResources().openRawResource(resourceId);

        StringBuilder stringBuilder = new StringBuilder();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(vastContentStream));

            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
        } catch (IOException err) {
            throw new RuntimeException(err);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException err) {
                    throw new RuntimeException(err);
                }
            }
        }

        return stringBuilder.toString();
    }
}
