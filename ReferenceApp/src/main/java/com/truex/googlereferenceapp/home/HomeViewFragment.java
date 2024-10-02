package com.truex.googlereferenceapp.home;

import static com.truex.googlereferenceapp.home.StreamConfiguration.requestStreamConfigurations;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.media3.common.Player;
import androidx.media3.ui.PlayerView;

import com.bumptech.glide.Glide;
import com.truex.googlereferenceapp.R;
import com.truex.googlereferenceapp.player.PlayerViewFragment;
import com.truex.googlereferenceapp.player.VideoPlayer;
import com.truex.googlereferenceapp.util.FileUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

import javax.inject.Inject;

import dagger.android.support.DaggerFragment;
import okhttp3.OkHttpClient;

public class HomeViewFragment extends DaggerFragment {
    private static String CLASSTAG = HomeViewFragment.class.getSimpleName();

    @Inject
    OkHttpClient httpClient;

    private StreamConfiguration currentStreamConfiguration;

    private ViewGroup streamSelectionLayout;
    private TextView streamTitle;
    private TextView streamDescription;
    private ImageView streamCover;
    private PlayerView previewPlayerView;
    private VideoPlayer previewPlayer;
    private View playButton;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();

        streamSelectionLayout = getView().findViewById(R.id.stream_selection_layout);
        streamTitle = getView().findViewById(R.id.stream_title);
        streamDescription = getView().findViewById(R.id.stream_description);
        streamCover = getView().findViewById(R.id.stream_cover);
        previewPlayerView = getView().findViewById(R.id.player_view);
        playButton = getView().findViewById(R.id.play_button);

        previewPlayer = new VideoPlayer(getContext(), previewPlayerView);
        previewPlayer.enableControls(false);

        String streamsConfigURL = getResources().getString(R.string.streams_config_url);
        requestStreamConfigurations(httpClient, streamsConfigURL, (List<StreamConfiguration> streamConfigurations) -> {
            updateCurrentStream(streamConfigurations.get(0));
        }, (Exception e) -> {
            StreamConfiguration fallbackStreamConfiguration = getFallbackStreamConfiguration();
            if (fallbackStreamConfiguration != null) {
                updateCurrentStream(fallbackStreamConfiguration);
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    private void updateCurrentStream(StreamConfiguration streamConfiguration) {
        currentStreamConfiguration = streamConfiguration;

        getActivity().runOnUiThread(() -> {
            // Update the title
            streamTitle.setText(currentStreamConfiguration.getTitle());

            // Update the stream description
            streamDescription.setText(currentStreamConfiguration.getDescription());

            // Update the cover image
            Glide.with(this)
                    .load(streamConfiguration.getCoverURL())
                    .centerCrop()
                    .into(streamCover);

            // Update and play the preview video
            previewPlayer.setStreamUrl(currentStreamConfiguration.getPreviewURL());
            previewPlayer.enableRepeatOnce();
            previewPlayer.setVolume(0);
            previewPlayer.play();

            // Set-up the Play Button
            playButton.setOnClickListener((View v) -> onPlayButtonClicked());
            playButton.requestFocus();

            // Display the layout
            streamSelectionLayout.setVisibility(View.VISIBLE);
        });
    }

    private void onPlayButtonClicked() {
        previewPlayer.release();

        Bundle arguments = new Bundle();
        arguments.putParcelable(StreamConfiguration.class.getSimpleName(), currentStreamConfiguration);

        Fragment fragment = new PlayerViewFragment();
        fragment.setArguments(arguments);

        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }

    StreamConfiguration getFallbackStreamConfiguration() {
        try {
            String rawStreamConfiguration = FileUtils.getRawFileContents(getContext(), R.raw.stream_config_fallback);
            JSONObject streamConfigurationJSON = new JSONObject(rawStreamConfiguration);
            return StreamConfiguration.getStreamConfiguration(streamConfigurationJSON);
        } catch (JSONException e) {
            Log.d(CLASSTAG, "Failed to parse fallback stream configuration");
        }
        return null;
    }
}
