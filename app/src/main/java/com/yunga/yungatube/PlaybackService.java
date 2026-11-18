package com.yunga.yungatube;

import androidx.media3.common.MimeTypes;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.dash.DefaultDashChunkSource;
import androidx.media3.exoplayer.dash.manifest.DashManifest;
import androidx.media3.exoplayer.dash.manifest.DashManifestParser;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.MergingMediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.media3.common.Player;
import org.json.JSONObject;
import java.lang.ref.WeakReference;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

/** The player belongs to the service so audio continues under screen lock. */
public final class PlaybackService extends MediaSessionService {
    private MediaSession session;
    private ExoPlayer player;
    private final Handler sleepHandler = new Handler(Looper.getMainLooper());
    private final Handler progressHandler=new Handler(Looper.getMainLooper());
    private final Runnable progressSaver=new Runnable() {
        @Override public void run() {
            savePlaybackPosition();
            progressHandler.postDelayed(this,4000);
        }
    };
    private final Player.Listener progressListener=new Player.Listener() {
        @Override public void onIsPlayingChanged(boolean isPlaying) {
            if(!isPlaying) savePlaybackPosition();
        }
        @Override public void onPlaybackStateChanged(int state) {
            if(state==Player.STATE_ENDED && player!=null) {
                androidx.media3.common.MediaItem item=player.getCurrentMediaItem();
                if(item!=null) Store.clearProgress(PlaybackService.this,item.mediaId);
            }
        }
    };
    private Runnable sleepPauseAction;
    private long sleepDeadlineElapsed;
    private static WeakReference<PlaybackService> running = new WeakReference<>(null);

    @Override public void onCreate() {
        super.onCreate();
        // Start as soon as 1 s is buffered (default 2.5 s) for a faster first frame.
        player=new ExoPlayer.Builder(this)
            .setLoadControl(new DefaultLoadControl.Builder()
                .setBufferDurationsMs(15_000,50_000,1_000,2_500).build())
            .build();
        running=new WeakReference<>(this);
        player.setHandleAudioBecomingNoisy(true);
        player.addListener(progressListener);
        progressHandler.postDelayed(progressSaver,4000);
        session=new MediaSession.Builder(this,player).build();
    }

    @Override public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return session;
    }

    /** What to play. Exactly one of the video fields or the audio fields may be empty. */
    static final class Source {
        String videoId="", title="", author="";
        String videoUrl, videoManifest, audioUrl, audioManifest;
        boolean hls, live;
    }

    /**
     * Builds the right MediaSource for a YouTube stream: DASH (bounded ranges, fast
     * start and seeking) when a manifest is available, HLS for live streams, plain
     * progressive otherwise. Video-only and audio tracks are merged.
     */
    static boolean play(Source source,long seekMs) {
        PlaybackService service=running.get();
        if(service==null || service.player==null)return false;
        MediaMetadata metadata=new MediaMetadata.Builder()
            .setTitle(source.title).setArtist(source.author).build();
        try {
            MediaSource visual=null, sound=null;
            if(notEmpty(source.videoUrl)) {
                MediaItem item=new MediaItem.Builder().setUri(source.videoUrl)
                    .setMediaId(source.videoId).setMediaMetadata(metadata)
                    .setMimeType(source.hls?MimeTypes.APPLICATION_M3U8:null).build();
                visual=service.build(item,source.videoUrl,source.videoManifest,source.hls);
            }
            if(notEmpty(source.audioUrl)) {
                MediaItem.Builder item=new MediaItem.Builder().setUri(source.audioUrl);
                // Audio-only playback carries the metadata for the notification.
                if(visual==null) item.setMediaId(source.videoId).setMediaMetadata(metadata);
                sound=service.build(item.build(),source.audioUrl,source.audioManifest,false);
            }
            MediaSource result=visual!=null && sound!=null
                ? new MergingMediaSource(true,visual,sound)
                : (visual!=null?visual:sound);
            if(result==null)return false;
            if(source.live || seekMs<=0) service.player.setMediaSource(result); // live edge
            else service.player.setMediaSource(result,seekMs);
            service.player.prepare();
            service.player.play();
            return true;
        } catch(Exception e) {
            return false;
        }
    }

    private static boolean notEmpty(String value) {
        return value!=null && !value.isEmpty();
    }

    private MediaSource build(MediaItem item,String url,String manifest,boolean hls)
            throws IOException {
        if(hls) {
            return new HlsMediaSource.Factory(new DefaultHttpDataSource.Factory()
                .setUserAgent(YoutubeDataSource.USER_AGENT)
                .setAllowCrossProtocolRedirects(true)).createMediaSource(item);
        }
        if(notEmpty(manifest)) {
            DashManifest parsed=new DashManifestParser().parse(Uri.parse(url),
                new ByteArrayInputStream(manifest.getBytes(StandardCharsets.UTF_8)));
            return new DashMediaSource.Factory(
                new DefaultDashChunkSource.Factory(new YoutubeDataSource.Factory(true)),null)
                .createMediaSource(parsed,item);
        }
        String host=Uri.parse(url).getHost();
        DataSource.Factory data=host!=null && host.endsWith(".googlevideo.com")
            ? new YoutubeDataSource.Factory(false)
            : new DefaultDataSource.Factory(this,new DefaultHttpDataSource.Factory()
                .setUserAgent(YoutubeDataSource.USER_AGENT));
        return new ProgressiveMediaSource.Factory(data).createMediaSource(item);
    }

    /**
     * Pause playback after a real elapsed-time interval, including when the
     * Activity goes to PiP or the screen is locked. One active timer per player.
     * Elapsed realtime is monotonic and unaffected by wall-clock changes.
     */
    static boolean setSleepTimer(int minutes) {
        PlaybackService service=running.get();
        if(service==null || service.player==null || minutes<1 || minutes>240)
            return false;
        service.cancelSleepTimerInternal();
        service.sleepDeadlineElapsed=SystemClock.elapsedRealtime()+minutes*60_000L;
        service.sleepPauseAction=()->{
            service.sleepPauseAction=null;
            service.sleepDeadlineElapsed=0;
            if(service.player!=null) service.player.pause();
        };
        service.sleepHandler.postDelayed(service.sleepPauseAction,minutes*60_000L);
        return true;
    }

    static long sleepTimerRemainingMillis() {
        PlaybackService service=running.get();
        if(service==null)return 0;
        return Math.max(0,service.sleepDeadlineElapsed-SystemClock.elapsedRealtime());
    }

    static void cancelSleepTimer() {
        PlaybackService service=running.get();
        if(service!=null) service.cancelSleepTimerInternal();
    }

    private void cancelSleepTimerInternal() {
        if(sleepPauseAction!=null) sleepHandler.removeCallbacks(sleepPauseAction);
        sleepPauseAction=null;
        sleepDeadlineElapsed=0;
    }


    private void savePlaybackPosition() {
        if(player==null || player.getPlaybackState()==Player.STATE_ENDED
            || player.isCurrentMediaItemLive())return;
        androidx.media3.common.MediaItem item=player.getCurrentMediaItem();
        if(item==null || item.mediaId==null ||
            !item.mediaId.matches("[A-Za-z0-9_-]{11}"))return;
        long positionMs=Math.max(0,player.getCurrentPosition());
        if(positionMs<3000)return;
        long durationMs=player.getDuration();
        if(durationMs<=0) durationMs=0;
        JSONObject details=new JSONObject();
        try {
            details.put("videoId",item.mediaId);
            MediaMetadata meta=item.mediaMetadata;
            details.put("title",meta.title==null?"Video":meta.title.toString());
            details.put("author",meta.artist==null?"":meta.artist.toString());
        }catch(Exception ignored){}
        Store.saveProgress(this,details,positionMs,durationMs);
    }

    @Override public void onTaskRemoved(android.content.Intent rootIntent) {
        savePlaybackPosition();
        super.onTaskRemoved(rootIntent);
    }

    @Override public void onDestroy() {
        progressHandler.removeCallbacks(progressSaver);
        savePlaybackPosition();
        cancelSleepTimerInternal();
        running.clear();
        if(session!=null) {
            session.getPlayer().release();
            session.release();
            session=null;
        }
        super.onDestroy();
    }
}
