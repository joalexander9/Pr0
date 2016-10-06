package com.pr0gramm.app.ui.views.viewer;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.support.v4.content.ContextCompat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;

import com.akodiakson.sdk.simple.Sdk;
import com.google.common.base.Optional;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.hash.Hashing;
import com.jakewharton.rxbinding.view.RxView;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.Stats;
import com.pr0gramm.app.services.SingleShotService;
import com.pr0gramm.app.services.ThemeHelper;
import com.pr0gramm.app.services.Track;
import com.pr0gramm.app.ui.DialogBuilder;
import com.pr0gramm.app.ui.views.AspectLayout;
import com.pr0gramm.app.ui.views.viewer.video.AndroidVideoPlayer;
import com.pr0gramm.app.ui.views.viewer.video.ExoVideoPlayer;
import com.pr0gramm.app.ui.views.viewer.video.RxVideoPlayer;
import com.pr0gramm.app.ui.views.viewer.video.VideoPlayer;
import com.pr0gramm.app.util.AndroidUtility;
import com.trello.rxlifecycle.android.RxLifecycleAndroid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import butterknife.BindView;
import rx.android.schedulers.AndroidSchedulers;

/**
 */
public class VideoMediaView extends AbstractProgressMediaView implements VideoPlayer.Callbacks {
    static final Logger logger = LoggerFactory.getLogger("VideoMediaView");

    private static final String KEY_LAST_UNMUTED_VIDEO = "VideoMediaView.lastUnmutedVideo";

    // the video player that does all the magic
    private final RxVideoPlayer videoPlayer;
    private final ImageView muteButtonView;

    final AudioManager audioManager;

    @BindView(R.id.video_container)
    AspectLayout videoPlayerParent;


    @Inject
    SingleShotService singleShotService;

    @Inject
    Settings settings;

    @Inject
    SharedPreferences preferences;

    private boolean videoViewInitialized;

    private boolean errorShown;
    private boolean statsSent;
    private boolean droppedFramesShown;

    VideoMediaView(Config config) {
        super(config, R.layout.player_kind_video);

        audioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);

        if (Sdk.isAtLeastJellyBean() && settings.useExoPlayer()) {
            logger.info("Using exo player to play videos.");
            videoPlayer = new ExoVideoPlayer(getContext(), config.audio(), videoPlayerParent);
        } else {
            logger.info("Falling back on simple android video player.");
            videoPlayer = new AndroidVideoPlayer(getContext(), videoPlayerParent);
        }

        muteButtonView = (ImageView) LayoutInflater
                .from(getContext())
                .inflate(R.layout.player_mute_view, this, false);

        muteButtonView.setVisibility(hasAudio() ? VISIBLE : GONE);

        muteButtonView.setOnClickListener(v -> {
            setMuted(!videoPlayer.isMuted());
            Track.muted(!videoPlayer.isMuted());
        });

        RxView.detaches(this).subscribe(event -> videoPlayer.setVideoCallbacks(null));

        videoPlayer.buffering()
                .sample(500, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(RxLifecycleAndroid.bindView(this))
                .subscribe(this::showBusyIndicator);


        long seekCacheKey = config.mediaUri().getId();

        videoPlayer.detaches().subscribe(event -> {
            seekToCache.put(seekCacheKey, videoPlayer.currentPosition());
        });

        // restore seek position if known
        Integer seekTo = seekToCache.getIfPresent(config.mediaUri().getId());
        if (seekTo != null) {
            videoPlayer.seekTo(seekTo);
        }

        publishControllerView(muteButtonView);
    }

    @Override
    protected boolean userSeekable() {
        return true;
    }

    @Override
    protected void injectComponent(ActivityComponent component) {
        component.inject(this);
    }

    @Override
    public void playMedia() {
        super.playMedia();

        // apply state before starting playback.
        applyMuteState();

        if (!videoViewInitialized) {
            showBusyIndicator();

            videoViewInitialized = true;

            videoPlayer.setVideoCallbacks(this);
            videoPlayer.open(getEffectiveUri());
        }

        videoPlayer.start();
    }

    /**
     * Create the controller view.
     */
    private View createScrollView() {
        LayoutParams lp = new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);

        Button button = new Button(getContext());
        button.setText("this is a test");
        button.setLayoutParams(lp);
        return button;
    }

    /**
     * Mute if not "unmuted" within the last 10 minutes.
     */
    private void applyMuteState() {
        if (hasAudio()) {
            long now = System.currentTimeMillis();
            long lastUnmutedVideo = preferences.getLong(KEY_LAST_UNMUTED_VIDEO, 0);
            long diff = (now - lastUnmutedVideo) / 1000;
            setMuted(diff > 10 * 60);
        } else {
            videoPlayer.setMuted(true);
        }
    }

    private void storeUnmuteTime(long time) {
        preferences.edit()
                .putLong(KEY_LAST_UNMUTED_VIDEO, time)
                .apply();
    }

    void setMuted(boolean muted) {
        if (!muted) {
            int result = audioManager.requestAudioFocus(afChangeListener,
                    AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

            if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                logger.info("Did not get audio focus, muting now!");
                muted = true;
            }
        }

        logger.info("Setting mute state on video player: {}", muted);
        videoPlayer.setMuted(muted);

        Drawable icon;
        if (muted) {
            storeUnmuteTime(0);

            icon = ContextCompat.getDrawable(getContext(), R.drawable.ic_volume_off_white_24dp);
        } else {
            storeUnmuteTime(System.currentTimeMillis());

            icon = AndroidUtility.getTintentDrawable(getContext(),
                    R.drawable.ic_volume_up_white_24dp, ThemeHelper.primaryColor());
        }

        muteButtonView.setImageDrawable(icon);
    }

    @Override
    protected Optional<ProgressInfo> getVideoProgress() {
        if (videoPlayer != null && videoViewInitialized && isPlaying()) {
            return Optional.of(new ProgressInfo(videoPlayer.progress(), videoPlayer.buffered()));
        }

        return Optional.absent();
    }

    @Override
    public void onVideoBufferingStarts() {
    }

    @Override
    public void onVideoBufferingEnds() {
    }

    @Override
    public void onVideoRenderingStarts() {
        if (isPlaying()) {
            // mark media as viewed
            onMediaShown();
        }

        if (!statsSent) {
            Stats.get().incrementCounter("video.playback.succeeded");
            statsSent = true;
        }
    }

    @Override
    public void onVideoSizeChanged(int width, int height) {
        setViewAspect(width / (float) height);
    }

    @Override
    public void stopMedia() {
        super.stopMedia();

        videoPlayer.pause();
        audioManager.abandonAudioFocus(afChangeListener);
    }

    @Override
    public void rewind() {
        videoPlayer.rewind();
    }


    @Override
    public void onVideoError(String message, VideoPlayer.ErrorKind kind) {
        // we might be finished here :/
        hideBusyIndicator();

        if (!errorShown) {
            DialogBuilder.start(getContext())
                    .dontShowAgainKey("video." + Hashing.md5().hashUnencodedChars(message).toString())
                    .content(R.string.media_exo_error, message)
                    .positive()
                    .show();

            errorShown = true;
        }

        if (!statsSent && kind != VideoPlayer.ErrorKind.NETWORK) {
            Stats.get().incrementCounter("video.playback.failed");
            statsSent = true;
        }
    }

    @Override
    public void onDroppedFrames(int count) {
        if (!droppedFramesShown) {
            DialogBuilder.start(getContext())
                    .dontShowAgainKey("VideoMediaView.dropped-frames")
                    .content(R.string.media_dropped_frames_hint)
                    .positive()
                    .show();

            droppedFramesShown = true;
        }
    }

    @Override
    protected void userSeekTo(float fraction) {
        videoPlayer.seekTo((int) (fraction * videoPlayer.duration()));
    }

    final AudioManager.OnAudioFocusChangeListener afChangeListener = new AudioManager.OnAudioFocusChangeListener() {
        public void onAudioFocusChange(int focusChange) {
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                    || focusChange == AudioManager.AUDIOFOCUS_LOSS) {

                audioManager.abandonAudioFocus(afChangeListener);

                logger.info("Lost audio focus, muting now.");
                setMuted(true);
            }
        }
    };

    private static Cache<Long, Integer> seekToCache = CacheBuilder.newBuilder()
            .expireAfterWrite(5, TimeUnit.SECONDS)
            .build();
}
