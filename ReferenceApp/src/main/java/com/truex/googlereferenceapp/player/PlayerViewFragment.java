package com.truex.googlereferenceapp.player;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.media3.ui.PlayerView;

import com.truex.googlereferenceapp.R;
import com.truex.googlereferenceapp.home.StreamConfiguration;
import com.truex.googlereferenceapp.util.FileUtils;

public class PlayerViewFragment extends Fragment {

    protected VideoPlayer videoPlayer;

    protected VideoPlayerWithAds videoPlayerWithAds;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_player, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();

        // Retrieve the stream configuration from the arguments bundle
        StreamConfiguration streamConfiguration = getArguments().getParcelable(StreamConfiguration.class.getSimpleName());

        ViewGroup adUiContainer = getView().findViewById(R.id.ad_ui_container);
        PlayerView playerView = getView().findViewById(R.id.player_view);

        videoPlayerWithAds = new VideoPlayerWithAds(getContext(), streamConfiguration, playerView, adUiContainer);

        String contentStream = "https://ctv.truex.com/assets/reference-app-stream-no-ads-720p.mp4";

        // Ensure we refer to CTV vs mobile ads.
        boolean isTV = getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);
        int vmapXmlResource = isTV ? R.raw.ctv_truex_vmap : R.raw.mobile_truex_vmap;
        String vastXml = FileUtils.getRawFileContents(getContext(), vmapXmlResource);

        videoPlayerWithAds.requestAndPlayStream(contentStream, vastXml);
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
        cleanUp();
        super.onDestroy();
    }

    private void cleanUp() {
        videoPlayerWithAds.release();
        reset();
    }

    private void reset() {
        videoPlayerWithAds = null;
        videoPlayer = null;
    }
}
