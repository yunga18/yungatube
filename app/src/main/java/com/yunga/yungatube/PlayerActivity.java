package com.yunga.yungatube;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PictureInPictureParams;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Rational;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.media3.ui.PlayerView;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Watch screen. Video is rendered here; the player itself lives in
 * PlaybackService so audio continues in the background and in PiP.
 */
public final class PlayerActivity extends Activity {
    private static final String DASH="dash", MUXED="muxed", PLAIN="plain", HLS="hls", AUDIO="audio";

    private static final class StreamOption {
        final String label, kind, videoUrl, videoManifest, audioUrl, audioManifest;
        final int height;
        StreamOption(String label,String kind,String videoUrl,String videoManifest,
                     String audioUrl,String audioManifest,int height){
            this.label=label;this.kind=kind;this.videoUrl=videoUrl;this.videoManifest=videoManifest;
            this.audioUrl=audioUrl;this.audioManifest=audioManifest;this.height=height;
        }
    }

    /** Extracted video info is reused for a while so reopening a video is instant. */
    private static final long INFO_TTL_MS=40*60_000L;
    private static final Map<String,Object[]> INFO_CACHE=new LinkedHashMap<>(16,0.75f,true);

    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ArrayList<StreamOption> options=new ArrayList<>();
    private final HashSet<Integer> tried=new HashSet<>();
    private ListenableFuture<MediaController> pendingController;
    private MediaController controller;
    private JSONObject video;
    private String id;
    private boolean offline=false, ready=false, live=false;
    private long pendingResumeMs=0;
    private Uri offlineUri;
    private int position=0;
    private final Handler sleepUiHandler=new Handler(Looper.getMainLooper());
    private final Runnable sleepUiTicker=new Runnable() {
        @Override public void run() {
            refreshSleepTimer();
            sleepUiHandler.postDelayed(this,1000);
        }
    };
    private LinearLayout root, details, suggestions, channelRow, descriptionBox;
    private ScrollView scroll;
    private FrameLayout viewport, avatarHolder;
    private PlayerView playerView;
    private TextView status, qualityButton, speedButton, savedButton, followButton, laterButton,
        title, meta, channelName, channelSubs, description, sleepButton, likeButton, downloadButton;

    private final Player.Listener listener=new Player.Listener(){
        @Override public void onPlayerError(PlaybackException error) {
            runOnUiThread(()->handleError(error));
        }
        @Override public void onPlaybackStateChanged(int state) {
            if(state==Player.STATE_READY || state==Player.STATE_ENDED)
                status.setVisibility(View.GONE);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try {video=new JSONObject(getIntent().getStringExtra("video"));}
        catch(Exception e){video=new JSONObject();}
        id=Ui.id(video);
        if(id.isEmpty()){finish();return;}
        String offlineValue=getIntent().getStringExtra("offlineUri");
        offline=offlineValue!=null && !offlineValue.isEmpty();
        if(offline)offlineUri=Uri.parse(offlineValue);
        live=Ui.live(video);
        Ui.lightBars(this);
        build();
        Store.visited(this,video);
        if(!offline)loadVideo();
        else {
            status.setText("Abriendo archivo descargado...");
            ready=true;
            startSourceWhenReady();
        }
    }

    // ------------------------------------------------------------------ layout

    private void build() {
        root=Ui.vertical(this);
        root.setBackgroundColor(Ui.BG);
        setContentView(root);
        Ui.insets(this,root);

        viewport=new FrameLayout(this);
        viewport.setBackgroundColor(Color.BLACK);
        root.addView(viewport,new LinearLayout.LayoutParams(-1,portraitVideoHeight()));
        playerView=new PlayerView(this);
        playerView.setUseController(true);
        playerView.setKeepScreenOn(true);
        playerView.setShowNextButton(false);
        playerView.setShowPreviousButton(false);
        playerView.setFullscreenButtonClickListener(full->setRequestedOrientation(full
            ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
        viewport.addView(playerView,new FrameLayout.LayoutParams(-1,-1));
        status=Ui.text(this,"Obteniendo video...",13,Color.WHITE,false);
        status.setGravity(Gravity.CENTER);
        status.setPadding(Ui.dp(this,20),0,Ui.dp(this,20),0);
        status.setBackgroundColor(0x99000000);
        viewport.addView(status,new FrameLayout.LayoutParams(-1,-1));
        TextView back=Ui.text(this,"‹",30,Color.WHITE,false);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Volver");
        back.setOnClickListener(v->finish());
        viewport.addView(back,new FrameLayout.LayoutParams(Ui.dp(this,44),Ui.dp(this,44)));

        scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        details=Ui.vertical(this);
        details.setPadding(0,Ui.dp(this,12),0,Ui.dp(this,30));
        scroll.addView(details);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        // Title + "views · date …más" (tap to expand the description).
        LinearLayout header=Ui.vertical(this);
        header.setPadding(Ui.dp(this,12),0,Ui.dp(this,12),0);
        title=Ui.text(this,video.optString("title","Video"),18,Ui.WHITE,true);
        title.setLineSpacing(Ui.dp(this,2),1f);
        header.addView(title);
        meta=Ui.text(this,"",12,Ui.MUTED,false);
        meta.setPadding(0,Ui.dp(this,6),0,0);
        header.addView(meta);
        header.setOnClickListener(v->toggleDescription());
        details.addView(header);

        descriptionBox=Ui.vertical(this);
        descriptionBox.setBackground(Ui.background(Ui.PANEL,12,this));
        descriptionBox.setPadding(Ui.dp(this,12),Ui.dp(this,10),Ui.dp(this,12),Ui.dp(this,10));
        description=Ui.text(this,"",13,Ui.WHITE,false);
        description.setLineSpacing(Ui.dp(this,3),1f);
        description.setTextIsSelectable(true);
        descriptionBox.addView(description);
        TextView less=Ui.text(this,"Mostrar menos",13,Ui.WHITE,true);
        less.setPadding(0,Ui.dp(this,10),0,0);
        less.setOnClickListener(v->toggleDescription());
        descriptionBox.addView(less);
        descriptionBox.setVisibility(View.GONE);
        LinearLayout.LayoutParams dp=Ui.lp(this,-1,-2);
        dp.setMargins(Ui.dp(this,12),Ui.dp(this,10),Ui.dp(this,12),0);
        details.addView(descriptionBox,dp);

        // Channel row with subscribe button.
        channelRow=Ui.horizontal(this);
        channelRow.setPadding(Ui.dp(this,12),Ui.dp(this,14),Ui.dp(this,12),Ui.dp(this,12));
        avatarHolder=new FrameLayout(this);
        channelRow.addView(avatarHolder,Ui.lp(this,38,38));
        LinearLayout names=Ui.vertical(this);
        names.setPadding(Ui.dp(this,10),0,Ui.dp(this,8),0);
        channelName=Ui.text(this,"",15,Ui.WHITE,true);
        channelName.setSingleLine(true);
        channelName.setEllipsize(TextUtils.TruncateAt.END);
        names.addView(channelName);
        channelSubs=Ui.text(this,"",12,Ui.MUTED,false);
        channelSubs.setPadding(0,Ui.dp(this,3),0,0);
        names.addView(channelSubs);
        channelRow.addView(names,new LinearLayout.LayoutParams(0,-2,1));
        followButton=Ui.action(this,"Suscribirse");
        followButton.setOnClickListener(v->{
            String channel=video.optString("authorId","");
            if(channel.isEmpty()) {toast("No encontramos un ID válido de canal");return;}
            boolean now=Store.toggleSubscription(this,channel,video.optString("author","Canal"));
            toast(now?"Suscrito a "+video.optString("author","este canal")
                :"Ya no sigues este canal");
            refreshSaved();
        });
        channelRow.addView(followButton,Ui.lp(this,-2,36));
        details.addView(channelRow);

        // Horizontally scrolling actions, like the row under a typical video.
        HorizontalScrollView actionScroll=new HorizontalScrollView(this);
        actionScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout actions=Ui.horizontal(this);
        actions.setPadding(Ui.dp(this,12),0,Ui.dp(this,8),0);
        actionScroll.addView(actions);
        likeButton=addAction(actions,"👍",v->toast("Los Me gusta se muestran tal como están en YouTube."));
        likeButton.setVisibility(View.GONE);
        savedButton=addAction(actions,"☆ Guardar",v->{
            boolean added=Store.toggleFavorite(this,video);
            toast(added?"Guardado en Favoritos":"Quitado de Favoritos");
            refreshSaved();
        });
        laterButton=addAction(actions,"◷ Ver después",v->{
            boolean added=Store.toggleWatchLater(this,video);
            toast(added?"Agregado a Ver después":"Quitado de Ver después");
            refreshSaved();
        });
        addAction(actions,"↗ Compartir",v->share());
        downloadButton=addAction(actions,"⤓ Descargar",v->saveOffline());
        sleepButton=addAction(actions,"☾ Temporizador",v->showSleepTimerDialog());
        sleepButton.setContentDescription("Abrir opciones del temporizador");
        qualityButton=addAction(actions,"⚙ Calidad",v->qualityPicker());
        speedButton=addAction(actions,speedLabel(savedSpeed()),v->speedPicker());
        addAction(actions,"♫ Solo audio",v->playAudioOnly());
        addAction(actions,"▣ Flotante",v->enterPip());
        TextView tools=addAction(actions,"☰ Funciones",v->showToolsDialog());
        tools.setContentDescription("Abrir todas las funciones y temporizador");
        details.addView(actionScroll,Ui.lp(this,-1,-2));

        LinearLayout.LayoutParams line=Ui.lp(this,-1,1);
        line.topMargin=Ui.dp(this,14);
        line.bottomMargin=Ui.dp(this,8);
        details.addView(Ui.divider(this),line);
        suggestions=Ui.vertical(this);
        details.addView(suggestions);
        TextView loading=Ui.text(this,"Cargando videos relacionados...",13,Ui.MUTED,false);
        loading.setPadding(Ui.dp(this,12),Ui.dp(this,10),0,0);
        suggestions.addView(loading);

        fillDetails();
        refreshSleepTimer();
        renderOrientation(getResources().getConfiguration().orientation);
    }

    private TextView addAction(LinearLayout row,String label,View.OnClickListener click){
        TextView view=Ui.action(this,label);
        view.setOnClickListener(click);
        LinearLayout.LayoutParams p=Ui.lp(this,-2,36);
        p.rightMargin=Ui.dp(this,8);
        row.addView(view,p);
        return view;
    }

    private int portraitVideoHeight(){
        android.util.DisplayMetrics m=getResources().getDisplayMetrics();
        return Math.min(m.widthPixels,m.heightPixels)*9/16;
    }

    /** Title, metadata, channel and description from the current {@link #video}. */
    private void fillDetails(){
        title.setText(video.optString("title","Video"));
        String info=Ui.meta(video,false);
        if(live) info="● EN VIVO"+(info.isEmpty()?"":" · "+info);
        boolean hasDescription=!video.optString("description","").trim().isEmpty();
        meta.setText(info+(hasDescription?"  …más":""));
        meta.setTextColor(live?Ui.LIVE:Ui.MUTED);
        description.setText(video.optString("description",""));
        String author=video.optString("author","");
        channelName.setText(author);
        long subs=video.optLong("subCount",-1);
        channelSubs.setText(subs>=0?Ui.count(subs)+" suscriptores":"");
        channelSubs.setVisibility(subs>=0?View.VISIBLE:View.GONE);
        channelRow.setVisibility(author.isEmpty()?View.GONE:View.VISIBLE);
        avatarHolder.removeAllViews();
        avatarHolder.addView(Cards.avatar(this,video),new FrameLayout.LayoutParams(-1,-1));
        long likes=video.optLong("likeCount",-1);
        likeButton.setText("👍 "+Ui.count(Math.max(0,likes)));
        likeButton.setVisibility(likes>0?View.VISIBLE:View.GONE);
        downloadButton.setVisibility(live?View.GONE:View.VISIBLE);
        speedButton.setVisibility(live?View.GONE:View.VISIBLE);
        refreshSaved();
    }

    private void toggleDescription(){
        if(video.optString("description","").trim().isEmpty())return;
        boolean show=descriptionBox.getVisibility()!=View.VISIBLE;
        descriptionBox.setVisibility(show?View.VISIBLE:View.GONE);
    }

    private void refreshSaved(){
        savedButton.setText(Store.favorite(this,id)?"★ Guardado":"☆ Guardar");
        laterButton.setText(Store.inWatchLater(this,id)?"✓ En Ver después":"◷ Ver después");
        boolean subscribed=Store.subscribed(this,video.optString("authorId",""));
        followButton.setText(subscribed?"Suscrito":"Suscribirse");
        followButton.setTextColor(subscribed?Ui.WHITE:Color.WHITE);
        followButton.setBackground(Ui.background(subscribed?Ui.PANEL:Ui.WHITE,18,this));
    }

    // -------------------------------------------------------- loading streams

    private void loadVideo(){
        Object[] cached;
        synchronized(INFO_CACHE){cached=INFO_CACHE.get(id);}
        if(cached!=null && System.currentTimeMillis()-(Long)cached[0]<INFO_TTL_MS){
            showInfo((JSONObject)cached[1],(String)cached[2]);
            return;
        }
        worker.execute(()->{
            try{
                Api.Result result=Api.get(this,"/api/v1/videos/"+id+"?region=EC&local=true");
                JSONObject info=result.object();
                if(info.has("error"))throw new Exception(info.optString("error"));
                synchronized(INFO_CACHE){
                    INFO_CACHE.put(id,new Object[]{System.currentTimeMillis(),info,result.host});
                    Iterator<String> oldest=INFO_CACHE.keySet().iterator();
                    while(INFO_CACHE.size()>12 && oldest.hasNext()){oldest.next();oldest.remove();}
                }
                runOnUiThread(()->{
                    if(!isFinishing() && !isDestroyed())showInfo(info,result.host);
                });
            }catch(Exception e){
                runOnUiThread(()->{
                    if(isFinishing() || isDestroyed())return;
                    status.setText("No pudimos abrir este video.\n"+e.getMessage());
                    status.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void showInfo(JSONObject info,String host){
        video=info;
        live=Ui.live(info);
        fillDetails();
        Store.visited(this,video);
        showRelated(info.optJSONArray("recommendedVideos"));
        options.clear();
        tried.clear();
        LinkedHashSet<String> seen=new LinkedHashSet<>();
        for(StreamOption s:buildOptions(info,host))
            if(seen.add(s.kind+"|"+s.videoUrl+"|"+s.audioUrl))options.add(s);
        if(options.isEmpty()){
            status.setText(live
                ? "Este directo no ofrece un formato compatible ahora mismo."
                : "Este video no tiene un formato compatible disponible.\n"+
                  "Prueba de nuevo o cambia la fuente en Configuración.");
            status.setVisibility(View.VISIBLE);
            return;
        }
        position=defaultOption();
        ready=true;
        startSourceWhenReady();
    }

    private ArrayList<StreamOption> buildOptions(JSONObject info,String host){
        ArrayList<StreamOption> list=new ArrayList<>();
        boolean direct="direct".equals(host);
        String hls=info.optString("hlsUrl","");
        if(live){
            if(hls.startsWith("https://"))
                list.add(new StreamOption("En vivo · automática",HLS,hls,null,null,null,0));
            return list;
        }
        // Best audio track, used for every HD option.
        String audioUrl=null, audioManifest=null;
        JSONArray audios=info.optJSONArray("audioStreams");
        if(audios!=null)for(int i=0;i<audios.length();i++){
            JSONObject a=audios.optJSONObject(i);
            if(a!=null && "audio/mp4".equals(a.optString("type"))
                    && a.optString("url","").startsWith("https://")){
                audioUrl=a.optString("url");
                audioManifest=a.optString("manifest","");
                break;
            }
        }
        // HD: H.264 video-only tracks merged with the audio track, low to high.
        JSONArray separate=info.optJSONArray("videoOnlyStreams");
        ArrayList<JSONObject> tracks=new ArrayList<>();
        if(audioUrl!=null && separate!=null)for(int i=0;i<separate.length();i++){
            JSONObject v=separate.optJSONObject(i);
            if(v!=null && v.optString("url","").startsWith("https://") && v.optInt("height",0)>0)
                tracks.add(v);
        }
        tracks.sort((a,b)->a.optInt("height")!=b.optInt("height")
            ? Integer.compare(a.optInt("height"),b.optInt("height"))
            : Integer.compare(a.optInt("fps"),b.optInt("fps")));
        HashSet<String> labels=new HashSet<>();
        for(JSONObject v:tracks){
            int h=v.optInt("height");
            String label=h+"p"+(v.optInt("fps",30)>30?Integer.toString(v.optInt("fps")):"");
            if(!labels.add(label))continue;
            list.add(new StreamOption(label,DASH,v.optString("url"),v.optString("manifest",""),
                audioUrl,audioManifest,h));
        }
        // Muxed MP4 (audio and video in one file).
        JSONArray progressive=info.optJSONArray("formatStreams");
        if(progressive!=null)for(int i=0;i<progressive.length();i++){
            JSONObject s=progressive.optJSONObject(i);
            if(s==null)continue;
            String url=s.optString("url","");
            if(url.startsWith("/") && !direct)url=host+url;
            if(!url.startsWith("https://") || !s.optString("type","").startsWith("video/mp4"))continue;
            String itag=s.optString("itag");
            String res=s.optString("resolution","");
            if(res.isEmpty())res="18".equals(itag)?"360p":("22".equals(itag)?"720p":"MP4 "+itag);
            String label=labels.contains(res)?res+" · MP4":res;
            labels.add(label);
            list.add(new StreamOption(label,direct?MUXED:PLAIN,url,null,null,null,
                parseHeight(res)));
        }
        if(hls.startsWith("https://"))
            list.add(new StreamOption("Automática (HLS)",HLS,hls,null,null,null,0));
        if(!direct)
            list.add(new StreamOption("Servidor alternativo",PLAIN,
                host+"/latest_version?id="+id+"&itag=18&local=true",null,null,null,360));
        if(audioUrl!=null)
            list.add(new StreamOption("Solo audio",AUDIO,null,null,audioUrl,audioManifest,0));
        return list;
    }

    private static int parseHeight(String label){
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("(\\d{3,4})p").matcher(label);
        return m.find()?Integer.parseInt(m.group(1)):0;
    }

    /** Last quality the user chose, else 720p, else the best HD ≤ 720p, else anything with video. */
    private int defaultOption(){
        String wanted=getSharedPreferences(Api.PREFS,0).getString("preferred_quality","720p");
        for(int i=0;i<options.size();i++)if(options.get(i).label.equals(wanted))return i;
        int best=-1;
        for(int i=0;i<options.size();i++){
            StreamOption o=options.get(i);
            if(DASH.equals(o.kind) && o.height<=720 && (best<0 || o.height>=options.get(best).height))
                best=i;
        }
        if(best>=0)return best;
        for(int i=0;i<options.size();i++)if(!AUDIO.equals(options.get(i).kind))return i;
        return 0;
    }

    /** When a source fails, try the most compatible remaining one; audio-only is last. */
    private void handleError(PlaybackException error){
        if(isFinishing() || isDestroyed())return;
        if(offline){
            status.setText("No se pudo abrir el archivo descargado");
            status.setVisibility(View.VISIBLE);
            return;
        }
        tried.add(position);
        long seek=controller==null?0:Math.max(0,controller.getCurrentPosition());
        String[] order={MUXED,PLAIN,HLS,DASH,AUDIO};
        for(String kind:order){
            int pick=-1;
            for(int i=0;i<options.size();i++){
                StreamOption o=options.get(i);
                if(tried.contains(i) || !kind.equals(o.kind))continue;
                // Among HD options prefer the highest one at or below 720p.
                if(pick<0 || (DASH.equals(kind) && o.height<=720 && o.height>options.get(pick).height))
                    pick=i;
            }
            if(pick>=0){
                position=pick;
                if(AUDIO.equals(kind))toast("No se pudo cargar la imagen; reproduciendo solo audio.");
                playOption(options.get(pick),seek);
                return;
            }
        }
        status.setText((live?"El directo no se pudo reproducir: ":"La reproducción falló: ")+
            error.getErrorCodeName()+"\nVuelve a intentarlo en unos segundos.");
        status.setVisibility(View.VISIBLE);
    }

    // ---------------------------------------------------------------- playback

    @Override protected void onStart(){
        super.onStart();
        sleepUiHandler.removeCallbacks(sleepUiTicker);
        sleepUiHandler.post(sleepUiTicker);
        SessionToken token=new SessionToken(this,new ComponentName(this,PlaybackService.class));
        pendingController=new MediaController.Builder(this,token).buildAsync();
        final ListenableFuture<MediaController> request=pendingController;
        request.addListener(()->{
            if(isFinishing() || isDestroyed())return;
            try {
                controller=request.get();
                controller.addListener(listener);
                playerView.setPlayer(controller);
                if(ready)startSourceWhenReady();
            }catch(Exception e){status.setText("Error del reproductor: "+e.getMessage());}
        },command->runOnUiThread(command));
    }

    @Override protected void onStop(){
        if(controller!=null && !live && controller.getPlaybackState()!=Player.STATE_ENDED) {
            MediaItem current=controller.getCurrentMediaItem();
            if(current!=null && id.equals(current.mediaId)) {
                long duration=controller.getDuration();
                Store.saveProgress(this,video,controller.getCurrentPosition(),
                    duration>0?duration:0);
            }
        }
        sleepUiHandler.removeCallbacks(sleepUiTicker);
        playerView.setPlayer(null);
        if(controller!=null){controller.removeListener(listener);controller=null;}
        if(pendingController!=null){
            MediaController.releaseFuture(pendingController);
            pendingController=null;
        }
        super.onStop();
    }

    private void startSourceWhenReady(){
        if(!ready || controller==null)return;
        // If the service is still playing this video (e.g. back from PiP), attach to it.
        MediaItem current=controller.getCurrentMediaItem();
        if(!offline && current!=null && id.equals(current.mediaId)
                && controller.getPlaybackState()!=Player.STATE_IDLE) {
            status.setVisibility(View.GONE);
            return;
        }
        long saved=live?0:Store.progressMs(this,id);
        pendingResumeMs=saved>5000?saved:0;
        if(offline) {
            qualityButton.setText("Sin conexión");
            playUri(offlineUri,"video/mp4",pendingResumeMs);
            applySavedSpeed();
            return;
        }
        if(!options.isEmpty())playOption(options.get(position),pendingResumeMs);
        if(pendingResumeMs>0)toast("Continuando desde "+Ui.duration((int)(pendingResumeMs/1000)));
    }

    private void playOption(StreamOption stream,long seek){
        qualityButton.setText("⚙ "+stream.label);
        status.setText(live?"Conectando con el directo...":"Cargando video...");
        status.setVisibility(View.VISIBLE);
        PlaybackService.Source source=new PlaybackService.Source();
        source.videoId=id;
        source.title=video.optString("title","Video");
        source.author=video.optString("author","");
        source.live=live;
        source.hls=HLS.equals(stream.kind);
        source.videoUrl=stream.videoUrl;
        source.videoManifest=stream.videoManifest;
        source.audioUrl=stream.audioUrl;
        source.audioManifest=stream.audioManifest;
        if(!PlaybackService.play(source,seek)){
            if(stream.videoUrl!=null && !DASH.equals(stream.kind))
                playUri(Uri.parse(stream.videoUrl),source.hls?"application/x-mpegURL":null,seek);
            else{
                status.setText("El reproductor todavía no está listo. Vuelve a intentarlo.");
                return;
            }
        }
        applySavedSpeed();
    }

    private void playUri(Uri uri,String mime,long seek){
        if(controller==null)return;
        status.setVisibility(View.VISIBLE);
        MediaMetadata metadata=new MediaMetadata.Builder()
            .setTitle(video.optString("title","Video"))
            .setArtist(video.optString("author",""))
            .build();
        MediaItem.Builder builder=new MediaItem.Builder().setUri(uri).setMediaId(id).setMediaMetadata(metadata);
        if(mime!=null && !mime.isEmpty())builder.setMimeType(mime);
        controller.setMediaItem(builder.build(),Math.max(0,seek));
        controller.prepare();
        controller.play();
    }

    private void playAudioOnly() {
        if(offline) {toast("La reproducción sin conexión usa el archivo descargado");return;}
        for(int i=0;i<options.size();i++) {
            if(AUDIO.equals(options.get(i).kind)) {
                position=i;
                playOption(options.get(i),controller==null?0:controller.getCurrentPosition());
                toast("Reproducción de solo audio activada");
                return;
            }
        }
        toast(live?"Los directos se reproducen con imagen y sonido"
            :"Este video no ofrece un formato de solo audio compatible");
    }

    private float savedSpeed() {
        return getSharedPreferences(Api.PREFS,0).getFloat("playback_speed",1f);
    }

    private void applySavedSpeed() {
        if(controller!=null) controller.setPlaybackSpeed(live?1f:savedSpeed());
    }

    private static String speedLabel(float rate) {
        String n=rate==(int)rate ? Integer.toString((int)rate) : Float.toString(rate);
        return "▶ "+n+"×";
    }

    // ----------------------------------------------------------------- dialogs

    private void showToolsDialog() {
        String[] choices={
            "☾ Temporizador de descanso",
            "⚙ Calidad",
            "▶ Velocidad",
            "▣ Ventana flotante",
            "♫ Solo audio",
            "⤓ Descargar MP4",
            "◷ Ver después",
            "★ Favoritos",
            "ℹ Reproducción en segundo plano"
        };
        new AlertDialog.Builder(this)
            .setTitle("Funciones · YungaTube "+Ui.version(this))
            .setItems(choices,(dialog,choice)->{
                switch(choice) {
                    case 0: showSleepTimerDialog();break;
                    case 1: qualityPicker();break;
                    case 2: speedPicker();break;
                    case 3: enterPip();break;
                    case 4: playAudioOnly();break;
                    case 5: saveOffline();break;
                    case 6: laterButton.performClick();break;
                    case 7: savedButton.performClick();break;
                    default:
                        new AlertDialog.Builder(this)
                            .setTitle("Segundo plano")
                            .setMessage("La reproducción continúa con la pantalla "+
                                "apagada cuando el servicio multimedia está activo. "+
                                "Android puede detenerla si restringe la batería.")
                            .setPositiveButton("Entendido",null).show();
                }
            }).setNegativeButton("Cerrar",null).show();
    }

    private void qualityPicker(){
        if(offline){toast("Este es un video guardado sin conexión");return;}
        if(options.isEmpty()){toast("Todavía no hay calidades disponibles");return;}
        String[] labels=new String[options.size()];
        for(int i=0;i<labels.length;i++)labels[i]=options.get(i).label;
        new AlertDialog.Builder(this).setTitle("Calidad del video")
            .setSingleChoiceItems(labels,position,(dialog,selected)->{
                long seek=controller==null?0:controller.getCurrentPosition();
                position=selected;
                tried.clear();
                StreamOption chosen=options.get(selected);
                if(DASH.equals(chosen.kind) || MUXED.equals(chosen.kind))
                    getSharedPreferences(Api.PREFS,0).edit()
                        .putString("preferred_quality",chosen.label).apply();
                playOption(chosen,seek);
                dialog.dismiss();
            }).setNegativeButton("Cancelar",null).show();
    }

    private void showSleepTimerDialog() {
        final String[] choices={
            "5 minutos", "10 minutos", "15 minutos", "30 minutos",
            "45 minutos", "60 minutos", "Elegir otro tiempo...",
            "Desactivar temporizador"
        };
        final int[] minutes={5,10,15,30,45,60};
        long remaining=PlaybackService.sleepTimerRemainingMillis();
        // AlertDialog shows either the message OR the items, so the countdown goes in the title.
        String heading=remaining>0 ?
            "☾ Temporizador · quedan "+((remaining+59_999)/60_000)+" min" :
            "☾ Temporizador de descanso";
        new AlertDialog.Builder(this)
            .setTitle(heading)
            .setItems(choices,(dialog,choice)->{
                if(choice<minutes.length)activateSleepTimer(minutes[choice]);
                else if(choice==6)customSleepTimer();
                else {
                    PlaybackService.cancelSleepTimer();
                    refreshSleepTimer();
                    toast("Temporizador desactivado");
                }
            })
            .setNegativeButton("Cerrar",null)
            .show();
    }

    private void customSleepTimer() {
        EditText input=new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine(true);
        input.setText("20");
        input.setSelectAllOnFocus(true);
        LinearLayout wrapper=Ui.vertical(this);
        wrapper.setPadding(Ui.dp(this,24),Ui.dp(this,6),Ui.dp(this,24),Ui.dp(this,6));
        wrapper.addView(input,Ui.lp(this,-1,-2));
        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle("Tiempo personalizado")
            .setMessage("Introduce entre 1 y 240 minutos.")
            .setView(wrapper)
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Activar",null)
            .create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(v->{
                int value;
                try { value=Integer.parseInt(input.getText().toString().trim()); }
                catch(NumberFormatException e) {
                    input.setError("Escribe un número entre 1 y 240");
                    return;
                }
                if(value<1 || value>240) {
                    input.setError("Elige entre 1 y 240 minutos");
                    return;
                }
                activateSleepTimer(value);
                dialog.dismiss();
            }));
        dialog.show();
    }

    private void activateSleepTimer(int minutes) {
        if(!PlaybackService.setSleepTimer(minutes)) {
            toast("Abre un video y espera a que se conecte el reproductor.");
            return;
        }
        refreshSleepTimer();
        toast("El video se pausará en "+minutes+" minutos.");
    }

    private void refreshSleepTimer() {
        if(sleepButton==null)return;
        long remaining=PlaybackService.sleepTimerRemainingMillis();
        if(remaining<=0) {
            sleepButton.setText("☾ Temporizador");
            sleepButton.setTextColor(Ui.WHITE);
            sleepButton.setBackground(Ui.background(Ui.PANEL,18,this));
            return;
        }
        long seconds=(remaining+999)/1000;
        String countdown=seconds>=3600 ?
            String.format(java.util.Locale.US,"%d:%02d:%02d",
                seconds/3600,(seconds%3600)/60,seconds%60) :
            String.format(java.util.Locale.US,"%02d:%02d",seconds/60,seconds%60);
        sleepButton.setText("☾ Pausa en "+countdown);
        sleepButton.setTextColor(Color.WHITE);
        sleepButton.setBackground(Ui.background(Ui.WHITE,18,this));
    }

    private void speedPicker(){
        if(controller==null){toast("Reproductor todavía no disponible");return;}
        if(live){toast("La velocidad no se puede cambiar en un directo");return;}
        String[] labels={"0.5×","0.75×","Normal 1×","1.25×","1.5×","1.75×","2×"};
        float[] rates={0.5f,0.75f,1f,1.25f,1.5f,1.75f,2f};
        new AlertDialog.Builder(this).setTitle("Velocidad de reproducción")
            .setItems(labels,(dialog,index)->{
                getSharedPreferences(Api.PREFS,0).edit()
                    .putFloat("playback_speed",rates[index]).apply();
                if(controller!=null) controller.setPlaybackSpeed(rates[index]);
                speedButton.setText(speedLabel(rates[index]));
            }).show();
    }

    private void saveOffline(){
        if(offline){toast("Este video ya está en Descargas");return;}
        if(live){toast("Los directos no se pueden descargar");return;}
        StreamOption chosen=null;
        for(StreamOption option:options)
            if((MUXED.equals(option.kind) || PLAIN.equals(option.kind))
                    && !option.label.equals("Servidor alternativo")){
                if(chosen==null || option.height==360)chosen=option;
            }
        if(chosen==null){
            new AlertDialog.Builder(this).setMessage(
                "No hay un archivo MP4 con audio y video disponible para este contenido. "+
                "Las transmisiones en vivo y algunos formatos protegidos no se pueden descargar."
            ).setPositiveButton("Entendido",null).show();
            return;
        }
        StreamOption selected=chosen;
        new AlertDialog.Builder(this).setTitle("Guardar sin conexión")
            .setMessage("Guardar "+selected.label+" en Descargas de YungaTube. "+
                "Requiere espacio libre. Solo descarga videos que tengas derecho a guardar.")
            .setPositiveButton("Descargar",(d,w)->{
                try{
                    Downloads.queue(this,video,selected.videoUrl);
                    toast("Descarga iniciada. Revisa la pestaña Descargas.");
                }catch(Exception e){toast("No se pudo iniciar: "+e.getMessage());}
            })
            .setNegativeButton("Cancelar",null).show();
    }

    // ----------------------------------------------------------- related list

    private void showRelated(JSONArray items){
        suggestions.removeAllViews();
        if(items==null || items.length()==0){
            TextView none=Ui.text(this,"No hay videos relacionados.",13,Ui.MUTED,false);
            none.setPadding(Ui.dp(this,12),Ui.dp(this,10),0,0);
            suggestions.addView(none);
            return;
        }
        for(int i=0;i<Math.min(items.length(),20);i++){
            JSONObject next=items.optJSONObject(i);
            if(next==null || Ui.id(next).isEmpty())continue;
            suggestions.addView(Cards.compact(this,next,()->openVideo(next),()->relatedMenu(next)));
        }
    }

    private void relatedMenu(JSONObject next){
        String vid=Ui.id(next);
        boolean later=Store.inWatchLater(this,vid);
        new AlertDialog.Builder(this).setTitle(next.optString("title","Video"))
            .setItems(new String[]{"▶ Reproducir",
                    later?"✓ Quitar de Ver después":"◷ Ver después","↗ Compartir"},
                (d,which)->{
                    if(which==0)openVideo(next);
                    else if(which==1){
                        boolean added=Store.toggleWatchLater(this,next);
                        toast(added?"Agregado a Ver después":"Quitado de Ver después");
                    }else shareId(vid);
                }).show();
    }

    /** Replaces this screen with the chosen video so screens don't pile up. */
    private void openVideo(JSONObject next){
        Intent detail=new Intent(this,PlayerActivity.class);
        detail.putExtra("video",next.toString());
        startActivity(detail);
        finish();
    }

    private void share(){ shareId(id); }

    private void shareId(String videoId){
        Intent intent=new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT,"https://www.youtube.com/watch?v="+videoId);
        startActivity(Intent.createChooser(intent,"Compartir video"));
    }

    // ------------------------------------------------------ PiP & orientation

    private void enterPip(){
        if(Build.VERSION.SDK_INT<26){toast("La ventana flotante requiere Android 8 o superior");return;}
        if(controller==null || !controller.isPlaying()){
            toast("Primero inicia la reproducción");
            return;
        }
        try {
            enterPictureInPictureMode(
                new PictureInPictureParams.Builder().setAspectRatio(new Rational(16,9)).build());
        }catch(Exception e){toast("Pantalla flotante no disponible en este dispositivo");}
    }

    @Override public void onUserLeaveHint(){
        super.onUserLeaveHint();
        if(getSharedPreferences(Api.PREFS,0).getBoolean("auto_pip",true)
            && Build.VERSION.SDK_INT>=26 && controller!=null && controller.isPlaying())
            enterPip();
    }

    @Override public void onPictureInPictureModeChanged(boolean inPip,Configuration config){
        super.onPictureInPictureModeChanged(inPip,config);
        scroll.setVisibility(inPip?View.GONE:View.VISIBLE);
        playerView.setUseController(!inPip);
        if(!inPip)renderOrientation(config.orientation);
        else viewport.setLayoutParams(new LinearLayout.LayoutParams(-1,-1));
    }

    @Override public void onConfigurationChanged(Configuration config){
        super.onConfigurationChanged(config);
        if(Build.VERSION.SDK_INT<26 || !isInPictureInPictureMode())
            renderOrientation(config.orientation);
    }

    private void renderOrientation(int orientation){
        boolean wide=orientation==Configuration.ORIENTATION_LANDSCAPE;
        if(Build.VERSION.SDK_INT>=26 && isInPictureInPictureMode())return;
        scroll.setVisibility(wide?View.GONE:View.VISIBLE);
        root.setBackgroundColor(wide?Color.BLACK:Ui.BG);
        viewport.setLayoutParams(new LinearLayout.LayoutParams(-1,
            wide?-1:portraitVideoHeight()));
        playerView.setFullscreenButtonState(wide);
        getWindow().getDecorView().setSystemUiVisibility(wide?
            View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY:
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }

    @Override public void onBackPressed(){
        if(getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE){
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }else super.onBackPressed();
    }

    private void toast(String message){
        Toast.makeText(this,message,Toast.LENGTH_SHORT).show();
    }

    @Override protected void onDestroy(){
        sleepUiHandler.removeCallbacks(sleepUiTicker);
        worker.shutdownNow();
        super.onDestroy();
    }
}
