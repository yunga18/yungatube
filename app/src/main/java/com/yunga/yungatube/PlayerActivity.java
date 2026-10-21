package com.yunga.yungatube;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.VideoView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PlayerActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ArrayList<String> sources=new ArrayList<>();
    private JSONObject item;
    private String id;
    private int sourceIndex=0;
    private boolean opened=false, shouldResume=false;
    private LinearLayout root, header, details, recommendations;
    private ScrollView scroll;
    private FrameLayout viewport;
    private VideoView video;
    private TextView status, favorite, follow, fullscreen, title, meta;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        try { item=new JSONObject(getIntent().getStringExtra("video")); }
        catch(Exception ignore) { item=new JSONObject(); }
        id=Ui.id(item);
        if(id.isEmpty()) {finish();return;}
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        build();
        Store.visited(this,item);
        fetchVideo();
    }

    private void build() {
        root=Ui.vertical(this);
        root.setBackgroundColor(Ui.BG);
        setContentView(root);
        Ui.insets(this,root);

        header=Ui.horizontal(this);
        header.setPadding(Ui.dp(this,14),Ui.dp(this,9),Ui.dp(this,14),Ui.dp(this,9));
        TextView back=Ui.pill(this,"‹ Volver",false);
        back.setOnClickListener(v->finish());
        header.addView(back,Ui.lp(this,-2,40));
        TextView brand=Ui.text(this,"  YungaTube",20,Ui.WHITE,true);
        header.addView(brand);
        root.addView(header);

        viewport=new FrameLayout(this);
        viewport.setBackgroundColor(Color.BLACK);
        root.addView(viewport,Ui.lp(this,-1,225));
        video=new VideoView(this);
        viewport.addView(video,new FrameLayout.LayoutParams(-1,-1));
        MediaController controller=new MediaController(this);
        controller.setAnchorView(video);
        video.setMediaController(controller);
        video.setOnPreparedListener(mp->{
            opened=true;
            status.setVisibility(View.GONE);
            video.start();
        });
        video.setOnErrorListener((mp,what,extra)->{
            handler.post(this::nextSource);
            return true;
        });
        video.setOnCompletionListener(mp->{
            status.setText("Video finalizado");
            status.setVisibility(View.VISIBLE);
        });

        status=Ui.text(this,"Conectando con el servidor...",13,Ui.WHITE,false);
        status.setGravity(Gravity.CENTER);
        status.setPadding(Ui.dp(this,15),0,Ui.dp(this,15),0);
        FrameLayout.LayoutParams stateLp=new FrameLayout.LayoutParams(-1,-1);
        viewport.addView(status,stateLp);

        fullscreen=Ui.pill(this,"⛶",false);
        fullscreen.setContentDescription("Pantalla completa");
        FrameLayout.LayoutParams fsLp=new FrameLayout.LayoutParams(-2,Ui.dp(this,40),
            Gravity.TOP|Gravity.RIGHT);
        fsLp.setMargins(0,Ui.dp(this,8),Ui.dp(this,8),0);
        viewport.addView(fullscreen,fsLp);
        fullscreen.setOnClickListener(v->rotate());

        scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        details=Ui.vertical(this);
        details.setPadding(Ui.dp(this,18),Ui.dp(this,17),Ui.dp(this,18),Ui.dp(this,28));
        scroll.addView(details);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        title=Ui.text(this,item.optString("title","Cargando video..."),20,Ui.WHITE,true);
        title.setMaxLines(4);
        details.addView(title);
        meta=Ui.text(this,item.optString("author",""),13,Ui.MUTED,false);
        meta.setPadding(0,Ui.dp(this,8),0,Ui.dp(this,14));
        details.addView(meta);

        LinearLayout actions=Ui.horizontal(this);
        favorite=Ui.pill(this,"☆ Guardar",false);
        favorite.setOnClickListener(v->{
            Store.toggleFavorite(this,item);
            updateButtons();
        });
        actions.addView(favorite);
        follow=Ui.pill(this,"+ Seguir",false);
        LinearLayout.LayoutParams followLp=Ui.lp(this,-2,-2);
        followLp.leftMargin=Ui.dp(this,8);
        actions.addView(follow,followLp);
        follow.setOnClickListener(v->{
            String channel=item.optString("authorId","");
            if(channel.isEmpty()) {
                android.widget.Toast.makeText(this,
                    "Este video no tiene un canal disponible",android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            Store.toggleSubscription(this,channel,item.optString("author","Canal"));
            updateButtons();
        });
        details.addView(actions);
        LinearLayout extra=Ui.horizontal(this);
        extra.setPadding(0,Ui.dp(this,12),0,Ui.dp(this,12));
        TextView share=Ui.pill(this,"↗ Compartir",false);
        share.setOnClickListener(v->share());
        extra.addView(share);
        TextView external=Ui.pill(this,"ⓘ Información",false);
        LinearLayout.LayoutParams extLp=Ui.lp(this,-2,-2);
        extLp.leftMargin=Ui.dp(this,8);
        extra.addView(external,extLp);
        external.setOnClickListener(v->
            new android.app.AlertDialog.Builder(this)
                .setTitle("YungaTube es independiente")
                .setMessage("La reproducción utiliza instancias públicas de Invidious. " +
                    "Si un video no funciona, prueba otra instancia en la configuración " +
                    "de la pantalla inicial. No se accede a tu cuenta de Google.")
                .setPositiveButton("Entendido",null).show());
        details.addView(extra);
        TextView similar=Ui.text(this,"Más para ver",19,Ui.WHITE,true);
        similar.setPadding(0,Ui.dp(this,12),0,Ui.dp(this,10));
        details.addView(similar);
        recommendations=Ui.vertical(this);
        details.addView(recommendations);
        updateButtons();
        renderOrientation(getResources().getConfiguration().orientation);
    }

    private void updateButtons() {
        favorite.setText(Store.favorite(this,id)?"★ Guardado":"☆ Guardar");
        favorite.setBackground(Ui.background(Store.favorite(this,id)?Ui.RED:Ui.SOFT,50,this));
        String authorId=item.optString("authorId","");
        follow.setText(Store.subscribed(this,authorId)?"✓ Siguiendo":"+ Seguir");
        follow.setBackground(Ui.background(Store.subscribed(this,authorId)?Ui.RED:Ui.SOFT,50,this));
    }

    private void fetchVideo() {
        status.setText("Buscando una fuente de reproducción...");
        worker.execute(()->{
            try {
                Api.Result proxied=Api.get(this,"/api/v1/videos/"+id+"?region=EC&local=true");
                JSONObject info=proxied.object();
                if(info.has("error")) throw new Exception(info.optString("error"));
                ArrayList<String> found=new ArrayList<>();
                collectStreams(info,proxied.host,found);
                // A last-resort 360p HTTP redirect via the same instance.
                found.add(proxied.host+"/latest_version?id="+id+"&itag=18&local=true");
                // Fetch direct playback links as fallback if this instance blocks proxying.
                try {
                    Api.Result direct=Api.get(this,"/api/v1/videos/"+id+"?region=EC&local=false");
                    collectStreams(direct.object(),direct.host,found);
                } catch(Exception ignored) {}
                found.add(proxied.host+"/latest_version?id="+id+"&itag=18");
                handler.post(()->{
                    if(isFinishing() || isDestroyed()) return;
                    item=info;
                    title.setText(info.optString("title",title.getText().toString()));
                    meta.setText(Ui.compact(info));
                    Store.visited(this,item);
                    updateButtons();
                    recommendations.removeAllViews();
                    JSONArray related=info.optJSONArray("recommendedVideos");
                    if(related!=null) for(int i=0;i<related.length() && i<12;i++) {
                        JSONObject o=related.optJSONObject(i);
                        if(o!=null && !Ui.id(o).isEmpty()) addRelated(o);
                    }
                    LinkedHashSet<String> unique=new LinkedHashSet<>(found);
                    sources.clear();sources.addAll(unique);
                    sourceIndex=0;
                    nextSource();
                });
            } catch(Exception e) {
                handler.post(()->{
                    if(isFinishing() || isDestroyed()) return;
                    status.setText("No se pudo obtener el video.\n" + e.getMessage() +
                        "\n\nPrueba otro servidor en la configuración.");
                });
            }
        });
    }

    private void collectStreams(JSONObject info, String host, ArrayList<String> urls) {
        JSONArray streams=info.optJSONArray("formatStreams");
        if(streams==null) return;
        // Progressive MP4 streams contain video and audio; VideoView cannot mix DASH tracks.
        for(String preferred:new String[]{"22","18","37","36"}) {
            for(int i=0;i<streams.length();i++) {
                JSONObject format=streams.optJSONObject(i);
                if(format==null || !preferred.equals(format.optString("itag"))) continue;
                String type=format.optString("type","").toLowerCase(java.util.Locale.ROOT);
                if(!type.contains("video/mp4")) continue;
                String url=format.optString("url","");
                if(url.startsWith("/")) url=host+url;
                if(url.startsWith("https://")) urls.add(url);
            }
        }
    }

    private void nextSource() {
        if(isFinishing() || isDestroyed()) return;
        if(sourceIndex>=sources.size()) {
            status.setText("No se puede reproducir este video desde los servidores disponibles.\n" +
                "Regresa a Inicio → ⚙ y elige otro servidor.");
            status.setVisibility(View.VISIBLE);
            return;
        }
        String url=sources.get(sourceIndex++);
        status.setText("Iniciando video... ("+sourceIndex+"/"+sources.size()+")");
        status.setVisibility(View.VISIBLE);
        try {
            video.stopPlayback();
            video.setVideoURI(Uri.parse(url));
            video.requestFocus();
            video.start();
        } catch(Exception e) { handler.post(this::nextSource); }
    }

    private void addRelated(JSONObject other) {
        LinearLayout row=Ui.horizontal(this);
        row.setPadding(Ui.dp(this,8),Ui.dp(this,8),Ui.dp(this,8),Ui.dp(this,8));
        row.setBackground(Ui.background(Ui.PANEL,14,this));
        LinearLayout.LayoutParams rowLp=Ui.lp(this,-1,-2);
        rowLp.bottomMargin=Ui.dp(this,9);
        ImageView picture=new ImageView(this);
        picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
        picture.setBackground(Ui.background(Ui.SOFT,10,this));
        row.addView(picture,Ui.lp(this,128,76));
        Images.load(picture,Ui.id(other));
        LinearLayout labels=Ui.vertical(this);
        labels.setPadding(Ui.dp(this,10),0,0,0);
        TextView name=Ui.text(this,other.optString("title","Video"),13,Ui.WHITE,true);
        name.setMaxLines(3);
        labels.addView(name);
        TextView author=Ui.text(this,other.optString("author",""),11,Ui.MUTED,false);
        author.setPadding(0,Ui.dp(this,5),0,0);
        labels.addView(author);
        row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        row.setOnClickListener(v->{
            Intent i=new Intent(this,PlayerActivity.class);
            i.putExtra("video",other.toString());
            startActivity(i);
        });
        recommendations.addView(row,rowLp);
    }

    private void share() {
        Intent intent=new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT,"https://www.youtube.com/watch?v="+id);
        startActivity(Intent.createChooser(intent,"Compartir video"));
    }

    private void rotate() {
        if(getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE)
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        else setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }

    private void renderOrientation(int orientation) {
        boolean wide=orientation==Configuration.ORIENTATION_LANDSCAPE;
        header.setVisibility(wide?View.GONE:View.VISIBLE);
        scroll.setVisibility(wide?View.GONE:View.VISIBLE);
        viewport.setLayoutParams(new LinearLayout.LayoutParams(-1,
            wide ? -1 : Ui.dp(this,225)));
        if(wide) getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        else getWindow().getDecorView().setSystemUiVisibility(0);
    }

    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        renderOrientation(configuration.orientation);
    }

    @Override public void onBackPressed() {
        if(getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE)
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        else super.onBackPressed();
    }

    @Override protected void onPause() {
        if(video!=null) {
            shouldResume=video.isPlaying();
            video.pause();
        }
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if(shouldResume && video!=null) video.start();
        shouldResume=false;
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        if(video!=null) video.stopPlayback();
        super.onDestroy();
    }
}
