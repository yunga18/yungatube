package com.yunga.yungatube;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.PictureInPictureParams;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Rational;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.media3.ui.PlayerView;
import com.google.common.util.concurrent.ListenableFuture;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Session-based playback: video in the Activity, audio and media controls in
 * PlaybackService. Independent from Google accounts and proprietary YouTube APK.
 */
public final class PlayerActivity extends Activity {
    private static final class StreamOption {
        final String label,url,type;
        StreamOption(String label,String url,String type){
            this.label=label;this.url=url;this.type=type;
        }
    }
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ArrayList<StreamOption> options=new ArrayList<>();
    private ListenableFuture<MediaController> pendingController;
    private MediaController controller;
    private JSONObject video;
    private String id;
    private boolean offline=false, ready=false, selecting=false;
    private Uri offlineUri;
    private int position=0;
    private LinearLayout root, header, details, suggestions;
    private ScrollView scroll;
    private FrameLayout viewport;
    private PlayerView playerView;
    private TextView status, qualityButton, speedButton, savedButton, followButton, title, author;

    private final Player.Listener listener=new Player.Listener(){
        @Override public void onPlayerError(PlaybackException error) {
            runOnUiThread(()->{
                if(isFinishing() || isDestroyed() || offline) {
                    status.setText("No se pudo abrir el archivo descargado");
                    status.setVisibility(View.VISIBLE);
                    return;
                }
                // Try another progressive source; never loop forever.
                if(position+1<options.size()) {
                    position++;
                    playOption(options.get(position),0);
                } else {
                    status.setText("La reproducción falló: "+error.getErrorCodeName()+
                        "\nPrueba otra calidad o vuelve a buscar el video.");
                    status.setVisibility(View.VISIBLE);
                }
            });
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
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        build();
        Store.visited(this,video);
        if(!offline)loadVideo();
        else {
            status.setText("Abriendo archivo descargado...");
            ready=true;
            startSourceWhenReady();
        }
    }

    private void build() {
        root=Ui.vertical(this);
        root.setBackgroundColor(Ui.BG);
        setContentView(root);
        Ui.insets(this,root);
        header=Ui.horizontal(this);
        header.setPadding(Ui.dp(this,14),Ui.dp(this,8),Ui.dp(this,12),Ui.dp(this,8));
        TextView back=Ui.pill(this,"‹  Volver",false);
        back.setOnClickListener(v->finish());
        header.addView(back);
        TextView logo=Ui.text(this,"   YungaTube",20,Ui.WHITE,true);
        header.addView(logo);
        root.addView(header);

        viewport=new FrameLayout(this);
        viewport.setBackgroundColor(Color.BLACK);
        root.addView(viewport,Ui.lp(this,-1,220));
        playerView=new PlayerView(this);
        playerView.setUseController(true);
        playerView.setKeepScreenOn(true);
        viewport.addView(playerView,new FrameLayout.LayoutParams(-1,-1));
        status=Ui.text(this,"Obteniendo video...",13,Ui.WHITE,false);
        status.setGravity(Gravity.CENTER);
        status.setPadding(Ui.dp(this,20),0,Ui.dp(this,20),0);
        status.setBackgroundColor(0xBB000000);
        viewport.addView(status,new FrameLayout.LayoutParams(-1,-1));

        scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        details=Ui.vertical(this);
        details.setPadding(Ui.dp(this,15),Ui.dp(this,16),Ui.dp(this,15),Ui.dp(this,35));
        scroll.addView(details);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        title=Ui.text(this,video.optString("title","Video"),20,Ui.WHITE,true);
        details.addView(title);
        author=Ui.text(this,video.optString("author",""),13,Ui.MUTED,false);
        author.setPadding(0,Ui.dp(this,8),0,Ui.dp(this,16));
        details.addView(author);

        LinearLayout line1=Ui.horizontal(this);
        savedButton=Ui.pill(this,"☆  Guardar",false);
        savedButton.setOnClickListener(v->{Store.toggleFavorite(this,video);refreshSaved();});
        line1.addView(savedButton);
        followButton=Ui.pill(this,"+  Suscribirme",false);
        addWithMargin(line1,followButton);
        followButton.setOnClickListener(v->{
            String channel=video.optString("authorId","");
            if(channel.isEmpty()) {toast("No encontramos un ID válido de canal");return;}
            Store.toggleSubscription(this,channel,video.optString("author","Canal"));
            refreshSaved();
        });
        TextView share=Ui.pill(this,"↗",false);
        addWithMargin(line1,share);
        share.setOnClickListener(v->share());
        details.addView(line1);

        LinearLayout line2=Ui.horizontal(this);
        line2.setPadding(0,Ui.dp(this,13),0,Ui.dp(this,12));
        qualityButton=Ui.pill(this,"⚙ Calidad",false);
        qualityButton.setOnClickListener(v->qualityPicker());
        line2.addView(qualityButton);
        speedButton=Ui.pill(this,"1×",false);
        speedButton.setOnClickListener(v->speedPicker());
        addWithMargin(line2,speedButton);
        TextView pip=Ui.pill(this,"▣ Flotante",false);
        pip.setOnClickListener(v->enterPip());
        addWithMargin(line2,pip);
        details.addView(line2);

        LinearLayout line3=Ui.horizontal(this);
        TextView download=Ui.pill(this,"↓  Descargar",false);
        download.setOnClickListener(v->saveOffline());
        line3.addView(download);
        TextView later=Ui.pill(this,"◷  Ver después",false);
        later.setOnClickListener(v->{
            Store.toggleWatchLater(this,video);
            later.setText(Store.inWatchLater(this,id)?"✓  En Ver después":"◷  Ver después");
            toast(Store.inWatchLater(this,id)?"Agregado a Ver después":"Quitado de Ver después");
        });
        addWithMargin(line3,later);
        details.addView(line3);

        TextView heading=Ui.text(this,"Sigue viendo",19,Ui.WHITE,true);
        heading.setPadding(0,Ui.dp(this,24),0,Ui.dp(this,12));
        details.addView(heading);
        suggestions=Ui.vertical(this);
        details.addView(suggestions);
        refreshSaved();
        renderOrientation(getResources().getConfiguration().orientation);
    }

    private void addWithMargin(LinearLayout row,TextView view) {
        LinearLayout.LayoutParams p=Ui.lp(this,-2,-2);
        p.leftMargin=Ui.dp(this,8);
        row.addView(view,p);
    }

    private void refreshSaved(){
        savedButton.setText(Store.favorite(this,id)?"★  Guardado":"☆  Guardar");
        boolean subscribed=Store.subscribed(this,video.optString("authorId",""));
        followButton.setText(subscribed?"✓  Suscrito":"+  Suscribirme");
        followButton.setBackground(Ui.background(subscribed?Ui.RED:Ui.SOFT,50,this));
    }

    @Override protected void onStart(){
        super.onStart();
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
        playerView.setPlayer(null);
        if(controller!=null){controller.removeListener(listener);controller=null;}
        if(pendingController!=null){
            MediaController.releaseFuture(pendingController);
            pendingController=null;
        }
        super.onStop();
    }

    private void loadVideo(){
        worker.execute(()->{
            try{
                Api.Result result=Api.get(this,"/api/v1/videos/"+id+"?region=EC&local=true");
                JSONObject info=result.object();
                if(info.has("error"))throw new Exception(info.optString("error"));
                ArrayList<StreamOption> fresh=new ArrayList<>();
                JSONArray progressive=info.optJSONArray("formatStreams");
                if(progressive!=null)for(int i=0;i<progressive.length();i++){
                    JSONObject s=progressive.optJSONObject(i);
                    if(s==null)continue;
                    String url=s.optString("url","");
                    String type=s.optString("type","");
                    if(url.startsWith("/") && !"direct".equals(result.host)) url=result.host+url;
                    if(!url.startsWith("https://") || !type.startsWith("video/mp4")) continue;
                    String itag=s.optString("itag");
                    String label="18".equals(itag)?"360p":("22".equals(itag)?"720p":
                        ("37".equals(itag)?"1080p":"Video "+itag));
                    fresh.add(new StreamOption(label,url,"video/mp4"));
                }
                JSONArray audios=info.optJSONArray("audioStreams");
                if(audios!=null)for(int i=0;i<audios.length();i++){
                    JSONObject s=audios.optJSONObject(i);
                    if(s==null)continue;
                    String url=s.optString("url","");
                    if(url.startsWith("https://")){
                        String type=s.optString("type","audio/mp4");
                        fresh.add(new StreamOption("Solo audio",url,
                            type.startsWith("audio/mp4")?"audio/mp4":type));
                        break;
                    }
                }
                String hls=info.optString("hlsUrl","");
                if(hls.startsWith("https://"))
                    fresh.add(new StreamOption("Directo",hls,MimeTypes.APPLICATION_M3U8));
                if(!"direct".equals(result.host)){
                    fresh.add(new StreamOption("Servidor alternativo",
                        result.host+"/latest_version?id="+id+"&itag=18&local=true","video/mp4"));
                }
                runOnUiThread(()->{
                    if(isFinishing() || isDestroyed())return;
                    video=info;
                    title.setText(info.optString("title",title.getText().toString()));
                    author.setText(Ui.compact(info));
                    Store.visited(this,video);
                    refreshSaved();
                    options.clear();
                    // Preserve ascending preferences for a stable default.
                    LinkedHashSet<String> seen=new LinkedHashSet<>();
                    for(StreamOption s:fresh)if(seen.add(s.url))options.add(s);
                    if(options.isEmpty()) {
                        status.setText("Este video no tiene un formato compatible disponible. "+
                            "Prueba otro video o la fuente Invidious en Configuración.");
                        return;
                    }
                    ready=true;
                    int preferred=0;
                    for(int i=0;i<options.size();i++)
                        if("360p".equals(options.get(i).label)){preferred=i;break;}
                    position=preferred;
                    showRelated(info.optJSONArray("recommendedVideos"));
                    startSourceWhenReady();
                });
            }catch(Exception e){
                runOnUiThread(()->{
                    if(!isFinishing() && !isDestroyed())
                        status.setText("No pudimos abrir este video.\n"+e.getMessage());
                });
            }
        });
    }

    private void startSourceWhenReady(){
        if(!ready || controller==null)return;
        if(offline) {
            qualityButton.setText("Sin conexión");
            playUri(offlineUri,"video/mp4",0);
            return;
        }
        if(!options.isEmpty())playOption(options.get(position),0);
    }

    private void playOption(StreamOption stream,long seek){
        qualityButton.setText("⚙ "+stream.label);
        playUri(Uri.parse(stream.url),stream.type,seek);
    }

    private void playUri(Uri uri,String mime,long seek){
        if(controller==null)return;
        status.setVisibility(View.VISIBLE);
        status.setText("Preparando reproducción...");
        MediaMetadata metadata=new MediaMetadata.Builder()
            .setTitle(video.optString("title","Video"))
            .setArtist(video.optString("author",""))
            .build();
        MediaItem.Builder builder=new MediaItem.Builder().setUri(uri).setMediaMetadata(metadata);
        if(mime!=null && !mime.isEmpty())builder.setMimeType(mime);
        controller.setMediaItem(builder.build(),Math.max(0,seek));
        controller.prepare();
        controller.play();
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
                playOption(options.get(selected),seek);
                dialog.dismiss();
            }).setNegativeButton("Cancelar",null).show();
    }

    private void speedPicker(){
        if(controller==null){toast("Reproductor todavía no disponible");return;}
        String[] labels={"0.5×","0.75×","Normal 1×","1.25×","1.5×","1.75×","2×"};
        float[] rates={0.5f,0.75f,1f,1.25f,1.5f,1.75f,2f};
        new AlertDialog.Builder(this).setTitle("Velocidad de reproducción")
            .setItems(labels,(dialog,index)->{
                controller.setPlaybackSpeed(rates[index]);
                speedButton.setText(labels[index].replace("Normal ",""));
            }).show();
    }

    private void saveOffline(){
        if(offline){toast("Este video ya está en Descargas");return;}
        StreamOption chosen=null;
        for(StreamOption option:options)
            if("video/mp4".equals(option.type) && !option.label.equals("Servidor alternativo")){
                if(chosen==null || "360p".equals(option.label))chosen=option;
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
                    Downloads.queue(this,video,selected.url);
                    toast("Descarga iniciada. Revisa Biblioteca → Descargas.");
                }catch(Exception e){toast("No se pudo iniciar: "+e.getMessage());}
            })
            .setNegativeButton("Cancelar",null).show();
    }

    private void showRelated(JSONArray items){
        suggestions.removeAllViews();
        if(items==null)return;
        for(int i=0;i<Math.min(items.length(),16);i++){
            JSONObject next=items.optJSONObject(i);
            if(next==null || Ui.id(next).isEmpty())continue;
            LinearLayout row=Ui.horizontal(this);
            row.setBackground(Ui.background(Ui.PANEL,12,this));
            row.setPadding(Ui.dp(this,7),Ui.dp(this,7),Ui.dp(this,7),Ui.dp(this,7));
            LinearLayout.LayoutParams p=Ui.lp(this,-1,-2);
            p.bottomMargin=Ui.dp(this,10);
            ImageView img=new ImageView(this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setBackground(Ui.background(Ui.SOFT,12,this));
            row.addView(img,Ui.lp(this,138,80));
            Images.load(img,Ui.id(next));
            LinearLayout texts=Ui.vertical(this);
            texts.setPadding(Ui.dp(this,12),0,0,0);
            TextView t=Ui.text(this,next.optString("title","Video"),14,Ui.WHITE,true);
            t.setMaxLines(3);
            texts.addView(t);
            TextView a=Ui.text(this,next.optString("author",""),12,Ui.MUTED,false);
            a.setMaxLines(1);
            texts.addView(a);
            row.addView(texts,new LinearLayout.LayoutParams(0,-2,1));
            row.setOnClickListener(v->{
                Intent detailIntent=new Intent(this,PlayerActivity.class);
                detailIntent.putExtra("video",next.toString());
                startActivity(detailIntent);
            });
            suggestions.addView(row,p);
        }
    }

    private void share(){
        Intent intent=new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT,"https://www.youtube.com/watch?v="+id);
        startActivity(Intent.createChooser(intent,"Compartir video"));
    }

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
        header.setVisibility(inPip?View.GONE:View.VISIBLE);
        scroll.setVisibility(inPip?View.GONE:View.VISIBLE);
        if(!inPip)renderOrientation(config.orientation);
    }

    @Override public void onConfigurationChanged(Configuration config){
        super.onConfigurationChanged(config);
        if(Build.VERSION.SDK_INT<26 || !isInPictureInPictureMode())
            renderOrientation(config.orientation);
    }

    private void renderOrientation(int orientation){
        boolean wide=orientation==Configuration.ORIENTATION_LANDSCAPE;
        if(Build.VERSION.SDK_INT>=26 && isInPictureInPictureMode())return;
        header.setVisibility(wide?View.GONE:View.VISIBLE);
        scroll.setVisibility(wide?View.GONE:View.VISIBLE);
        viewport.setLayoutParams(new LinearLayout.LayoutParams(-1,
            wide?-1:Ui.dp(this,220)));
        getWindow().getDecorView().setSystemUiVisibility(wide?
            View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY:0);
    }

    @Override public void onBackPressed(){
        if(getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE){
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }else super.onBackPressed();
    }

    private void toast(String message){
        Toast.makeText(this,message,Toast.LENGTH_LONG).show();
    }

    @Override protected void onDestroy(){
        worker.shutdownNow();
        super.onDestroy();
    }
}
