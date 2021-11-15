package com.google.ads.interactivemedia.v3.samples.videoplayerapp;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import androidx.fragment.app.Fragment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/** Fragment for displaying a playlist of video thumbnails that the user can select from to play. */
public class VideoListFragment extends Fragment {

  private OnVideoSelectedListener selectedCallback;
  LayoutInflater inflater;
  ViewGroup container;

  /**
   * Listener called when the user selects a video from the list. Container activity must implement
   * this interface.
   */
  public interface OnVideoSelectedListener {
    void onVideoSelected(VideoItem videoItem);
  }

  private OnVideoListFragmentResumedListener resumeCallback;

  /** Listener called when the video list fragment resumes. */
  public interface OnVideoListFragmentResumedListener {
    public void onVideoListFragmentResumed();
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
    try {
      selectedCallback = (OnVideoSelectedListener) context;
    } catch (ClassCastException e) {
      throw new ClassCastException(
          context.toString() + " must implement " + OnVideoSelectedListener.class.getName());
    }

    try {
      resumeCallback = (OnVideoListFragmentResumedListener) context;
    } catch (ClassCastException e) {
      throw new ClassCastException(
          context.toString()
              + " must implement "
              + OnVideoListFragmentResumedListener.class.getName());
    }
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    this.inflater = inflater;
    this.container = container;
    View rootView = inflater.inflate(R.layout.fragment_video_list, container, false);

    final ListView listView = (ListView) rootView.findViewById(R.id.videoListView);
    VideoItemAdapter videoItemAdapter =
        new VideoItemAdapter(rootView.getContext(), R.layout.video_item, getVideoItems());
    listView.setAdapter(videoItemAdapter);

    listView.setOnItemClickListener(
        new AdapterView.OnItemClickListener() {
          @Override
          public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
            if (selectedCallback != null) {
              VideoItem selectedVideo = (VideoItem) listView.getItemAtPosition(position);

              selectedCallback.onVideoSelected(selectedVideo);
            }
          }
        });

    return rootView;
  }

  private List<VideoItem> getVideoItems() {
    final List<VideoItem> videoItems = new ArrayList<VideoItem>();

    videoItems.add(
            new VideoItem(
              "https://storage.googleapis.com/gvabox/media/samples/stock.mp4",
              "Truex vmap",
              "url",
              R.drawable.thumbnail1,
    true,
              getRawFileContents(R.raw.truex_vmap)
      )
    );

    videoItems.add(
            new VideoItem(
              "https://storage.googleapis.com/gvabox/media/samples/stock.mp4",
              "Plain Vmap",
              "https://pubads.g.doubleclick.net/gampad/ads?sz=640x480&iu=/124319096/external/ad_rule_samples&ciu_szs=300x250&ad_rule=1&impl=s&gdfp_req=1&env=vp&output=vmap&unviewed_position_start=1&cust_params=deployment%3Ddevsite%26sample_ar%3Dpremidpost&cmsid=496&vid=short_onecue&correlator=",
              R.drawable.thumbnail1,
              false
            )
    );


    return videoItems;
  }

  @Override
  public void onResume() {
    super.onResume();
    if (resumeCallback != null) {
      resumeCallback.onVideoListFragmentResumed();
    }
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
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if (reader != null) {
        try {
          reader.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }

    return stringBuilder.toString();
  }
}
