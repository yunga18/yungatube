package com.yunga.yungatube;

import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.MergingMediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import android.net.Uri;
import java.lang.ref.WeakReference;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

/** The player belongs to the service so audio continues under screen lock. */
public final class PlaybackService extends MediaSessionService {
    private MediaSession session;
    private ExoPlayer player;
    private static WeakReference<PlaybackService> running = new WeakReference<>(null);

    @Override public void onCreate() {
        super.onCreate();
        player=new ExoPlayer.Builder(this).build();
        running=new WeakReference<>(this);
        player.setHandleAudioBecomingNoisy(true);
        session=new MediaSession.Builder(this,player).build();
    }

    @Override public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return session;
    }

    /** Merge HD video and audio at playback time. */
    static boolean playMerged(String videoId,Uri visualUri,Uri audioUri,
                              String title,String channel,long seek) {
        PlaybackService service=running.get();
        if(service==null || service.player==null)return false;
        MediaMetadata metadata=new MediaMetadata.Builder()
            .setTitle(title).setArtist(channel).build();
        MediaItem visual=new MediaItem.Builder()
            .setUri(visualUri).setMediaId(videoId).setMediaMetadata(metadata).build();
        MediaItem sound=MediaItem.fromUri(audioUri);
        ProgressiveMediaSource.Factory factory=new ProgressiveMediaSource.Factory(
            new DefaultDataSource.Factory(service));
        MediaSource visualSource=factory.createMediaSource(visual);
        MediaSource soundSource=factory.createMediaSource(sound);
        service.player.setMediaSource(
            new MergingMediaSource(visualSource,soundSource),seek);
        service.player.prepare();
        service.player.play();
        return true;
    }

    @Override public void onDestroy() {
        running.clear();
        if(session!=null) {
            session.getPlayer().release();
            session.release();
            session=null;
        }
        super.onDestroy();
    }
}
