package com.google.ads.interactivemedia.v3.samples.videoplayerapp;

import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

public class DetailFragment extends Fragment implements View.OnClickListener {
    private static final String CLASSTAG = DetailFragment.class.getSimpleName();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_detail, container, false);
        Button button = view.findViewById(R.id.playbackButton);
        button.setOnClickListener(this);

        return view;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()){
            case R.id.playbackButton:
                loadPlayerFragment();
                break;
        }
    }

    private void loadFragment(Fragment fragment) {
        FragmentActivity activity = getActivity();
        activity.getSupportFragmentManager().beginTransaction()
                .replace(R.id.activity_main, fragment)
                .addToBackStack(null)
                .commit();
    }

    private void loadPlayerFragment() {
        loadFragment(new VideoFragment());
    }
}
