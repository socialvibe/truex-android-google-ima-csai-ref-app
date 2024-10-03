/*
 * Copyright 2015 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.truex.googlereferenceapp.player;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.view.View;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.ForwardingPlayer;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
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

import com.google.ads.interactivemedia.v3.api.StreamManager;

import java.util.Formatter;
import java.util.Locale;

/**
 * A video player that plays HLS or DASH streams using ExoPlayer.
 */
@OptIn(markerClass = UnstableApi.class)
public class VideoPlayer {

    private static final String CLASSTAG = VideoPlayer.class.getSimpleName();

    private final Context context;

    final private ExoPlayer exoPlayer;
    private final PlayerView playerView;
    private VideoPlayerCallback playerCallback;

    private String streamUrl;
    private Boolean streamRequested;
    private boolean canSeek;

    private StreamManager streamManager;
    private Timeline timelineWithAds;

    public VideoPlayer(Context context, PlayerView playerView) {
        this.context = context;
        this.playerView = playerView;
        streamRequested = false;
        canSeek = true;

        exoPlayer = new ExoPlayer.Builder(context).build();

        ForwardingPlayer playerWrapper = new ForwardingPlayer(exoPlayer) {
            @Override
            public void seekToDefaultPosition() {
                seekToDefaultPosition(getCurrentMediaItemIndex());
            }

            @Override
            public void seekToDefaultPosition(int windowIndex) {
                seekTo(windowIndex, /* positionMs= */ C.TIME_UNSET);
            }

            @Override
            public void seekTo(long positionMs) {
                seekTo(getCurrentMediaItemIndex(), positionMs);
            }

            @Override
            public void seekTo(int windowIndex, long seekPos) {
                if (playerCallback != null) {
                    playerCallback.onSeek(windowIndex, seekPos);
                } else {
                    exoPlayer.seekTo(windowIndex, seekPos);
                }
            }
        };
        playerView.setPlayer(playerWrapper);
    }

    static public String positionDisplay(long position) {
        StringBuilder formatBuilder = new StringBuilder();
        Formatter formatter = new Formatter(formatBuilder, Locale.getDefault());
        return Util.getStringForTime(formatBuilder, formatter, position);
    }

    public void logPosition(String context) {
        long streamPos = exoPlayer.getCurrentPosition();
        int state = exoPlayer.getPlaybackState();
        boolean loading = exoPlayer.isLoading();
        boolean playing = exoPlayer.isPlaying();
        boolean inAd = exoPlayer.isPlayingAd();
        logPosition(context + ": state: " + state + " playing: " + playing + " loading: " + loading + " inAd: " + inAd, streamPos);
    }

    static public void logPosition(String context, long position) {
        StringBuilder msg = new StringBuilder();
        msg.append("*** ");
        msg.append(context);
        msg.append(": ");
        msg.append(positionDisplay(position));
        Log.i(CLASSTAG, msg.toString());
    }

    public void play() {
        if (streamRequested) {
            // Stream already requested, just resume.
            exoPlayer.play();
            return;
        }

        Log.i(CLASSTAG, "*** play: " + streamUrl);
        DataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(context);
        int type = Util.inferContentType(Uri.parse(streamUrl));
        MediaItem mediaItem = MediaItem.fromUri(Uri.parse(streamUrl));
        MediaSource mediaSource;
        switch (type) {
            case C.CONTENT_TYPE_HLS:
                mediaSource = new HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem);
                break;
            case C.CONTENT_TYPE_DASH:
                mediaSource =
                        new DashMediaSource.Factory(
                                new DefaultDashChunkSource.Factory(dataSourceFactory), dataSourceFactory)
                                .createMediaSource(mediaItem);
                break;
            case C.CONTENT_TYPE_OTHER:
                mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem);
                break;
            default:
                throw new UnsupportedOperationException("Unknown stream type.");
        }

        exoPlayer.setMediaSource(mediaSource);
        exoPlayer.prepare();

        exoPlayer.play();
        streamRequested = true;
    }

    public void pause() {
        if (exoPlayer != null) exoPlayer.pause();
    }

    public void stop() {
        if (exoPlayer != null) exoPlayer.stop();
        streamRequested = false;
    }

    public void hide() {
        playerView.setVisibility(View.GONE);
    }

    public void show() {
        playerView.setVisibility(View.VISIBLE);
    }

    public void seekTo(long positionMs) {
        logPosition("raw seekTo", positionMs);
        exoPlayer.seekTo(positionMs);
    }

    public void seekTo(int windowIndex, long positionMs) {
        logPosition("raw seekTo", positionMs);
        exoPlayer.seekTo(windowIndex, positionMs);
    }

    public void release() {
        exoPlayer.release();
        streamRequested = false;
    }

    public void setStreamUrl(String streamUrl) {
        if (streamRequested) {
            exoPlayer.stop();
        }
        this.streamUrl = streamUrl;
        streamRequested = false; // request new stream on play
    }


    public boolean isPlayingAd() {
        return streamManager != null && streamManager.getAdProgressInfo() != null;
    }

    public void enableControls(boolean doEnable) {
        if (doEnable) {
            playerView.showController();
        } else {
            playerView.hideController();
        }
        playerView.setControllerAutoShow(doEnable);
        playerView.setUseController(doEnable);
        canSeek = doEnable;
    }

    public void requestFocus() {
        playerView.requestFocus();
    }

    public boolean isStreamRequested() {
        return streamRequested;
    }

    public boolean isPlaying() {
        return exoPlayer != null && exoPlayer.isPlaying();
    }

    // Methods for exposing player information.
    void setCallback(VideoPlayerCallback callback) {
        playerCallback = callback;
    }

    public void setCanSeek(boolean canSeek) {
        this.canSeek = canSeek;
    }

    /**
     * Returns current position of the playhead in milliseconds for DASH and HLS stream.
     */
    public long getCurrentPosition() {
        return exoPlayer.getCurrentPosition();
    }

    public void enableRepeatOnce() {
        exoPlayer.setRepeatMode(Player.REPEAT_MODE_ONE);
    }

    public void setVolume(float volume) {
        exoPlayer.setVolume(volume);
    }

    public int getVolume() {
        return Math.round(exoPlayer.getVolume() * 100);
    }

    public long getDuration() {
        return exoPlayer.getDuration();
    }

    public void addListener(Player.Listener listener) {
        exoPlayer.addListener(listener);
    }

    public void removeListener(Player.Listener listener) {
        exoPlayer.removeListener(listener);
    }
}
