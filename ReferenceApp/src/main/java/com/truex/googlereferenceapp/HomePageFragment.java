package com.truex.googlereferenceapp;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

public class HomePageFragment extends Fragment implements View.OnClickListener {
  private static final String CLASSTAG = HomePageFragment.class.getSimpleName();

  private Button playButton;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    Log.i(CLASSTAG, "onCreateView");
    View view = inflater.inflate(R.layout.fragment_home, container, false);
    playButton = view.findViewById(R.id.playbackButton);
    playButton.setOnClickListener(this);
    return view;
  }

  @Override
  public void onResume() {
    Log.i(CLASSTAG, "onResume");
    if (playButton != null) playButton.requestFocus();
    super.onResume();
  }

  @Override
  public void onDetach() {
    Log.i(CLASSTAG, "onDetach");
    super.onDetach();
  }

  @Override
  public void onClick(View view) {
    switch (view.getId()) {
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
