package com.yunga.yungatube;

import androidx.annotation.Nullable;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

/** The player belongs to the service so audio continues under screen lock. */
public final class PlaybackService extends MediaSessionService {
    private MediaSession session;

    @Override public void onCreate() {
        super.onCreate();
        ExoPlayer player=new ExoPlayer.Builder(this).build();
        player.setHandleAudioBecomingNoisy(true);
        session=new MediaSession.Builder(this,player).build();
    }

    @Nullable @Override public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return session;
    }

    @Override public void onDestroy() {
        if(session!=null) {
            session.getPlayer().release();
            session.release();
            session=null;
        }
        super.onDestroy();
    }
}
