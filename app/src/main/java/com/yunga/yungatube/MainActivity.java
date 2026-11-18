package com.yunga.yungatube;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Mobile-first home with large thumbnails and a local library (no Google login). */
public final class MainActivity extends Activity {
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private LinearLayout root, feed, bottom, filterRow, libraryTabs;
    private HorizontalScrollView libraryScroll;
    private ScrollView scroll;
    private ProgressBar progress;
    private EditText search;
    private TextView heading;
    private String tab="Inicio", selectedTopic="Todo", librarySection="Continuar viendo";
    private String lastQuery="";
    private int token=0;
    private boolean resumedOnce=false;
    /** In-memory feed cache so returning from a video does not re-download the feed. */
    private static final long FEED_TTL_MS=10*60_000L;
    private static final HashMap<String,Object[]> FEED_CACHE=new HashMap<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        drawLayout();
        changeTab("Inicio");
        openIncoming(getIntent());
    }
    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);
        setIntent(intent);
        openIncoming(intent);
    }
    private void openIncoming(Intent intent){
        if(intent==null || intent.getData()==null)return;
        Uri uri=intent.getData();
        String id=uri.getQueryParameter("v");
        if(id==null && "youtu.be".equals(uri.getHost()))id=uri.getLastPathSegment();
        if(id==null || !id.matches("[A-Za-z0-9_-]{11}"))return;
        JSONObject item=new JSONObject();
        try{item.put("videoId",id);item.put("title","Abriendo video...");}
        catch(Exception ignored){}
        play(item,null);
    }

    private void drawLayout(){
        root=Ui.vertical(this);
        root.setBackgroundColor(Ui.BG);
        setContentView(root);
        Ui.insets(this,root);

        LinearLayout brand=Ui.horizontal(this);
        brand.setPadding(Ui.dp(this,14),Ui.dp(this,10),Ui.dp(this,12),Ui.dp(this,8));
        ImageView play=new ImageView(this);
        play.setImageResource(R.drawable.logo_yungatube);
        play.setScaleType(ImageView.ScaleType.FIT_CENTER);
        play.setContentDescription("Logo YungaTube");
        brand.addView(play,Ui.lp(this,46,46));
        TextView word=Ui.text(this," YungaTube "+Ui.version(this),19,Ui.WHITE,true);
        brand.addView(word,new LinearLayout.LayoutParams(0,Ui.dp(this,44),1));
        TextView refresh=Ui.text(this,"⟳",27,Ui.WHITE,false);
        refresh.setGravity(Gravity.CENTER);
        refresh.setContentDescription("Actualizar videos");
        refresh.setOnClickListener(v->refresh(true));
        brand.addView(refresh,Ui.lp(this,45,42));
        TextView settings=Ui.text(this,"⚙",24,Ui.WHITE,false);
        settings.setGravity(Gravity.CENTER);
        settings.setContentDescription("Ajustes de YungaTube");
        settings.setOnClickListener(v->settings());
        brand.addView(settings,Ui.lp(this,45,42));
        root.addView(brand);

        LinearLayout searchBox=Ui.horizontal(this);
        searchBox.setPadding(Ui.dp(this,14),Ui.dp(this,0),Ui.dp(this,14),Ui.dp(this,9));
        search= new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(Ui.WHITE);
        search.setHintTextColor(Ui.MUTED);
        search.setTextSize(15);
        search.setHint("⌕  Buscar en YouTube");
        search.setPadding(Ui.dp(this,13),0,Ui.dp(this,12),0);
        search.setBackground(Ui.background(Ui.PANEL,25,this));
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setOnEditorActionListener((v,action,event)->{
            if(action==EditorInfo.IME_ACTION_SEARCH){searchNow();return true;}
            return false;
        });
        searchBox.addView(search,new LinearLayout.LayoutParams(0,Ui.dp(this,46),1));
        TextView go=Ui.pill(this,"Buscar",true);
        LinearLayout.LayoutParams goLp=Ui.lp(this,-2,45);
        goLp.leftMargin=Ui.dp(this,8);
        searchBox.addView(go,goLp);
        go.setOnClickListener(v->searchNow());
        root.addView(searchBox);

        filterRow=Ui.horizontal(this);
        HorizontalScrollView filters=new HorizontalScrollView(this);
        filters.setHorizontalScrollBarEnabled(false);
        filters.setFillViewport(false);
        filters.addView(filterRow);
        root.addView(filters,Ui.lp(this,-1,49));

        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setIndeterminateTintList(ColorStateList.valueOf(Ui.RED));
        root.addView(progress,Ui.lp(this,-1,2));

        scroll=new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        LinearLayout container=Ui.vertical(this);
        heading=Ui.text(this,"",22,Ui.WHITE,true);
        heading.setPadding(Ui.dp(this,15),Ui.dp(this,13),Ui.dp(this,15),Ui.dp(this,8));
        container.addView(heading);
        libraryTabs=Ui.horizontal(this);
        libraryTabs.setPadding(Ui.dp(this,12),0,Ui.dp(this,12),Ui.dp(this,12));
        libraryScroll=new HorizontalScrollView(this);
        libraryScroll.setHorizontalScrollBarEnabled(false);
        libraryScroll.setFillViewport(false);
        libraryScroll.addView(libraryTabs);
        libraryScroll.setVisibility(View.GONE);
        container.addView(libraryScroll);
        feed=Ui.vertical(this);
        container.addView(feed);
        scroll.addView(container);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        bottom=Ui.horizontal(this);
        bottom.setBackgroundColor(Ui.PANEL);
        bottom.setPadding(Ui.dp(this,4),Ui.dp(this,6),Ui.dp(this,4),Ui.dp(this,6));
        root.addView(bottom);
        tabs();
    }

    private void tabs(){
        bottom.removeAllViews();
        String[][] tabs={
            {"Inicio","⌂"},{"Shorts","▣"},{"Seguidos","♡"},
            {"Biblioteca","▤"},{"Descargas","↓"}
        };
        for(String[] pair:tabs){
            boolean active=pair[0].equals(tab);
            LinearLayout item=Ui.vertical(this);
            item.setGravity(Gravity.CENTER);
            TextView icon=Ui.text(this,pair[1],23,active?Ui.WHITE:Ui.MUTED,true);
            icon.setGravity(Gravity.CENTER);
            item.addView(icon);
            TextView label=Ui.text(this,pair[0],10,active?Ui.WHITE:Ui.MUTED,active);
            label.setGravity(Gravity.CENTER);
            item.addView(label);
            if(active)item.setBackground(Ui.background(Ui.SOFT,16,this));
            item.setOnClickListener(v->changeTab(pair[0]));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,Ui.dp(this,56),1);
            p.leftMargin=Ui.dp(this,2);
            p.rightMargin=Ui.dp(this,2);
            bottom.addView(item,p);
        }
    }

    private void changeTab(String next){
        tab=next;
        tabs();
        refresh(false);
        scroll.scrollTo(0,0);
    }

    private void topics(){
        filterRow.removeAllViews();
        if(!"Inicio".equals(tab)){
            filterRow.setVisibility(View.GONE);
            return;
        }
        filterRow.setVisibility(View.VISIBLE);
        String[] names={"Todo","Música","Videojuegos","Tecnología","Ciencia",
            "Matemáticas","Noticias","En vivo"};
        for(String name:names){
            TextView chip=Ui.pill(this,name,name.equals(selectedTopic));
            if(!name.equals(selectedTopic))chip.setTextColor(Ui.WHITE);
            LinearLayout.LayoutParams p=Ui.lp(this,-2,37);
            p.leftMargin=Ui.dp(this,7);
            p.topMargin=Ui.dp(this,3);
            filterRow.addView(chip,p);
            chip.setOnClickListener(v->{selectedTopic=name;refresh(false);scroll.scrollTo(0,0);});
        }
    }

    private interface Loader{JSONArray run() throws Exception;}
    private void load(String key,boolean force,Loader loader){
        final int current=++token;
        // Source can change in settings, so it is part of the cache key.
        final String cacheKey=(Api.useDirect(this)?"direct|":Api.configured(this)+"|")+key;
        Object[] hit;
        synchronized(FEED_CACHE){hit=FEED_CACHE.get(cacheKey);}
        if(!force && hit!=null && System.currentTimeMillis()-(Long)hit[0]<FEED_TTL_MS){
            progress.setVisibility(View.GONE);
            cards((JSONArray)hit[1]);
            return;
        }
        feed.removeAllViews();
        progress.setVisibility(View.VISIBLE);
        TextView loading=Ui.text(this,"Buscando videos...",14,Ui.MUTED,false);
        loading.setPadding(Ui.dp(this,16),Ui.dp(this,25),0,0);
        feed.addView(loading);
        executor.execute(()->{
            try{
                JSONArray videos=loader.run();
                if(videos!=null && videos.length()>0)synchronized(FEED_CACHE){
                    FEED_CACHE.put(cacheKey,new Object[]{System.currentTimeMillis(),videos});
                }
                runOnUiThread(()->{
                    if(current!=token || isFinishing() || isDestroyed())return;
                    progress.setVisibility(View.GONE);
                    cards(videos);
                });
            }catch(Exception e){
                runOnUiThread(()->{
                    if(current!=token || isFinishing() || isDestroyed())return;
                    progress.setVisibility(View.GONE);
                    empty("No se pudieron cargar los videos.\n"+e.getMessage()+
                        "\n\nPrueba actualizar, otra búsqueda o cambiar la fuente en ⚙.");
                });
            }
        });
    }

    private void refresh(boolean force){
        topics();
        libraryTabs.removeAllViews();
        libraryScroll.setVisibility("Biblioteca".equals(tab)?View.VISIBLE:View.GONE);
        switch(tab){
            case "Inicio":
                heading.setText(selectedTopic.equals("Todo")?"Recomendados para descubrir":selectedTopic);
                String topic=selectedTopic;
                if("Todo".equals(topic)){
                    load("trending",force,()->Api.get(this,"/api/v1/trending?region=EC").array());
                }else if("En vivo".equals(topic)){
                    load("topic:"+topic,force,()->Api.get(this,"/api/v1/search?q="+Api.encode("en vivo Ecuador")+"&type=video").array());
                }else{
                    load("topic:"+topic,force,()->Api.get(this,"/api/v1/search?q="+Api.encode(topic)+"&type=video").array());
                }
                break;
            case "Shorts":
                heading.setText("Shorts");
                load("shorts",force,()->Api.get(this,"/api/v1/search?q="+Api.encode("shorts español")+"&type=video").array());
                break;
            case "Seguidos":
                heading.setText("Tus suscripciones");
                if(Store.subscriptions(this).length()==0){
                    ++token;
                    progress.setVisibility(View.GONE);
                    empty("Cuando sigas canales aparecerán sus videos aquí.\nNo necesitas iniciar sesión.");
                } else load("followed:"+Store.subscriptions(this).toString(),force,this::followed);
                break;
            case "Biblioteca":
                heading.setText("Tu biblioteca");
                libraryPicker();
                library();
                break;
            case "Descargas":
                heading.setText("Descargas sin conexión");
                downloads();
                break;
            case "Buscar":
                heading.setText("Resultados: "+lastQuery);
                String query=lastQuery;
                load("search:"+query,force,()->Api.get(this,"/api/v1/search?q="+Api.encode(query)+"&type=video").array());
                break;
        }
    }

    private void searchNow(){
        String query=search.getText().toString().trim();
        if(query.isEmpty()){search.setError("Escribe qué deseas buscar");return;}
        lastQuery=query;
        ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE))
            .hideSoftInputFromWindow(search.getWindowToken(),0);
        changeTab("Buscar");
    }

    /** Fetches up to 20 channels in parallel and interleaves their latest videos. */
    private JSONArray followed() throws Exception{
        JSONArray subs=Store.subscriptions(this),out=new JSONArray();
        ExecutorService pool=Executors.newFixedThreadPool(4);
        List<Future<JSONArray>> jobs=new ArrayList<>();
        try{
            for(int i=0;i<subs.length() && jobs.size()<20;i++){
                JSONObject sub=subs.optJSONObject(i);
                if(sub==null)continue;
                String channel=sub.optString("id","");
                if(!channel.matches("UC[A-Za-z0-9_-]{22}"))continue;
                jobs.add(pool.submit(()->{
                    JSONArray items=Api.get(this,"/api/v1/channels/"+channel)
                        .object().optJSONArray("latestVideos");
                    return items==null?new JSONArray():items;
                }));
            }
            List<JSONArray> lists=new ArrayList<>();
            for(Future<JSONArray> job:jobs){
                try{lists.add(job.get());}
                catch(InterruptedException e){Thread.currentThread().interrupt();throw e;}
                catch(Exception ignored){/* one unavailable channel must not block all */}
            }
            for(int j=0;j<10;j++)for(JSONArray items:lists){
                JSONObject v=items.optJSONObject(j);
                if(v!=null)out.put(v);
            }
        }finally{pool.shutdownNow();}
        return out;
    }

    private void libraryPicker(){
        String[] names={"Continuar viendo","Favoritos","Ver después","Historial"};
        for(String name:names){
            TextView chip=Ui.pill(this,name,name.equals(librarySection));
            LinearLayout.LayoutParams p=Ui.lp(this,-2,38);
            p.rightMargin=Ui.dp(this,8);
            libraryTabs.addView(chip,p);
            chip.setOnClickListener(v->{
                librarySection=name;libraryTabs.removeAllViews();libraryPicker();library();
            });
        }
    }

    private void library(){
        ++token;
        progress.setVisibility(View.GONE);
        if("Continuar viendo".equals(librarySection)) {
            cards(Store.continueWatching(this));
            return;
        }
        String key="Favoritos".equals(librarySection)?"favorites":
            ("Ver después".equals(librarySection)?"watch_later":"history");
        cards(Store.list(this,key));
        if("Historial".equals(librarySection) && Store.list(this,"history").length()>0){
            TextView clear=Ui.pill(this,"Borrar historial",false);
            LinearLayout.LayoutParams p=Ui.lp(this,-1,44);
            p.setMargins(Ui.dp(this,15),Ui.dp(this,12),Ui.dp(this,15),Ui.dp(this,12));
            feed.addView(clear,p);
            clear.setOnClickListener(v->new AlertDialog.Builder(this)
                .setMessage("¿Eliminar el historial de reproducción local?")
                .setPositiveButton("Eliminar",(d,w)->{Store.clearHistory(this);library();})
                .setNegativeButton("Cancelar",null).show());
        }
    }

    private void downloads(){
        ++token;
        progress.setVisibility(View.GONE);
        feed.removeAllViews();
        JSONArray items=Downloads.entries(this);
        if(items.length()==0){
            empty("Aquí aparecerán tus videos descargados.\nAbre un video y pulsa ↓ Descargar.");
            return;
        }
        for(int i=items.length()-1;i>=0;i--){
            JSONObject item=items.optJSONObject(i);
            if(item==null)continue;
            long downloadId=item.optLong("downloadId",-1);
            int state=Downloads.status(this,downloadId);
            String label=Downloads.description(state);
            addCard(item,label,()->{
                if(state==DownloadManager.STATUS_SUCCESSFUL){
                    Uri uri=Downloads.uri(this,downloadId);
                    if(uri!=null)play(item,uri.toString());
                    else Toast.makeText(this,"El archivo no está disponible",Toast.LENGTH_LONG).show();
                } else Toast.makeText(this,label,Toast.LENGTH_LONG).show();
            },()->new AlertDialog.Builder(this)
                .setTitle("Eliminar descarga")
                .setMessage("¿Borrar el archivo descargado del teléfono?")
                .setPositiveButton("Eliminar",(d,w)->{Downloads.remove(this,downloadId);downloads();})
                .setNegativeButton("Cancelar",null).show());
        }
    }

    private void cards(JSONArray items){
        feed.removeAllViews();
        if("Inicio".equals(tab))addContinueSection();
        if(items==null || items.length()==0){
            if(feed.getChildCount()==0)
                empty("No hay videos aquí por ahora.\nPrueba otra búsqueda.");
            return;
        }
        for(int i=0;i<Math.min(90,items.length());i++){
            JSONObject item=items.optJSONObject(i);
            if(item==null || Ui.id(item).isEmpty())continue;
            addCard(item,"",()->play(item,null),null);
        }
        if(feed.getChildCount()==0)empty("No encontramos videos para mostrar.");
    }


    private void addContinueSection() {
        JSONArray items=Store.continueWatching(this);
        if(items.length()==0) return;
        LinearLayout headingRow=Ui.horizontal(this);
        headingRow.setPadding(Ui.dp(this,15),Ui.dp(this,12),
            Ui.dp(this,15),Ui.dp(this,10));
        TextView heading=Ui.text(this,"▶ Continuar viendo",19,Ui.WHITE,true);
        headingRow.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        TextView all=Ui.text(this,"Ver todos ›",13,Ui.RED,true);
        all.setOnClickListener(v->{
            librarySection="Continuar viendo";
            changeTab("Biblioteca");
        });
        headingRow.addView(all);
        feed.addView(headingRow);

        HorizontalScrollView scroll=new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout list=Ui.horizontal(this);
        list.setPadding(Ui.dp(this,12),0,Ui.dp(this,12),0);
        scroll.addView(list);
        for(int i=0;i<Math.min(items.length(),12);i++) {
            JSONObject item=items.optJSONObject(i);
            if(item==null || Ui.id(item).isEmpty())continue;
            LinearLayout card=Ui.vertical(this);
            card.setBackground(Ui.background(Ui.PANEL,14,this));
            LinearLayout.LayoutParams cardParams=Ui.lp(this,182,-2);
            cardParams.leftMargin=Ui.dp(this,4);
            cardParams.rightMargin=Ui.dp(this,5);
            list.addView(card,cardParams);

            FrameLayout thumbnail=new FrameLayout(this);
            ImageView picture=new ImageView(this);
            picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumbnail.addView(picture,new FrameLayout.LayoutParams(-1,-1));
            Images.load(picture,Ui.id(item));
            int pct=0;
            long dur=item.optLong("durationMs",0);
            if(dur>0)pct=(int)Math.min(100,Math.max(0,100*item.optLong("resumeMs",0)/dur));
            View track=new View(this);
            track.setBackgroundColor(0x99000000);
            FrameLayout.LayoutParams trackLp=new FrameLayout.LayoutParams(-1,Ui.dp(this,4),Gravity.BOTTOM);
            thumbnail.addView(track,trackLp);
            if(pct>0){
                View meter=new View(this);
                meter.setBackgroundColor(Ui.RED);
                FrameLayout.LayoutParams meterLp=new FrameLayout.LayoutParams(
                    Ui.dp(this,182)*pct/100,Ui.dp(this,4),Gravity.BOTTOM|Gravity.LEFT);
                thumbnail.addView(meter,meterLp);
            }
            card.addView(thumbnail,Ui.lp(this,-1,102));
            TextView name=Ui.text(this,item.optString("title","Video"),13,Ui.WHITE,true);
            name.setMaxLines(2);
            name.setPadding(Ui.dp(this,9),Ui.dp(this,8),
                Ui.dp(this,9),Ui.dp(this,3));
            card.addView(name);
            String time=Ui.duration((int)(item.optLong("resumeMs",0)/1000));
            TextView resume=Ui.text(this,"Desde "+time,12,Ui.MUTED,false);
            resume.setPadding(Ui.dp(this,9),0,Ui.dp(this,9),Ui.dp(this,10));
            card.addView(resume);
            card.setOnClickListener(v->play(item,null));
        }
        feed.addView(scroll);
        View spacing=new View(this);
        feed.addView(spacing,Ui.lp(this,-1,10));
        TextView forYou=Ui.text(this,"Descubre más videos",18,Ui.WHITE,true);
        forYou.setPadding(Ui.dp(this,15),Ui.dp(this,12),
            Ui.dp(this,15),Ui.dp(this,12));
        feed.addView(forYou);
    }

    private void addCard(JSONObject video,String suffix,Runnable action,Runnable longPress){
        LinearLayout card=Ui.vertical(this);
        LinearLayout.LayoutParams cardLp=Ui.lp(this,-1,-2);
        cardLp.bottomMargin=Ui.dp(this,12);
        feed.addView(card,cardLp);
        FrameLayout thumb=new FrameLayout(this);
        int widthPx=getResources().getDisplayMetrics().widthPixels;
        int height=Math.max(150,(int)((widthPx/getResources().getDisplayMetrics().density)*9.0/16.0));
        ImageView image=new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Ui.SOFT);
        thumb.addView(image,new FrameLayout.LayoutParams(-1,-1));
        Images.load(image,Ui.id(video));
        int length=video.optInt("lengthSeconds",0);
        if(length>0){
            TextView duration=Ui.text(this,Ui.duration(length),12,Color.WHITE,true);
            duration.setPadding(Ui.dp(this,5),0,Ui.dp(this,5),0);
            duration.setBackground(Ui.background(0xE0000000,4,this));
            FrameLayout.LayoutParams d=new FrameLayout.LayoutParams(-2,Ui.dp(this,22),
                Gravity.RIGHT|Gravity.BOTTOM);
            d.setMargins(0,0,Ui.dp(this,9),Ui.dp(this,8));
            thumb.addView(duration,d);
        }
        card.addView(thumb,Ui.lp(this,-1,height));
        LinearLayout summary=Ui.horizontal(this);
        summary.setPadding(Ui.dp(this,15),Ui.dp(this,11),Ui.dp(this,15),Ui.dp(this,12));
        TextView avatar=Ui.text(this,firstLetter(video.optString("author","Y")),19,Ui.WHITE,true);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(Ui.background(Ui.RED,40,this));
        summary.addView(avatar,Ui.lp(this,40,40));
        LinearLayout meta=Ui.vertical(this);
        meta.setPadding(Ui.dp(this,11),0,0,0);
        TextView name=Ui.text(this,video.optString("title","Video"),16,Ui.WHITE,true);
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        meta.addView(name);
        String sub=video.optString("author","Canal");
        if(video.optLong("resumeMs",0)>0) {
            sub=sub+"  ·  Continuar desde "+
                Ui.duration((int)(video.optLong("resumeMs",0)/1000));
        } else if(suffix!=null && !suffix.isEmpty())sub=sub+"  ·  "+suffix;
        else if(!video.optString("publishedText","").isEmpty())
            sub=sub+"  ·  "+video.optString("publishedText");
        TextView channel=Ui.text(this,sub,12,Ui.MUTED,false);
        channel.setMaxLines(1);
        channel.setEllipsize(android.text.TextUtils.TruncateAt.END);
        channel.setPadding(0,Ui.dp(this,5),0,0);
        meta.addView(channel);
        summary.addView(meta,new LinearLayout.LayoutParams(0,-2,1));
        TextView more=Ui.text(this,"⋮",22,Ui.MUTED,true);
        more.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        more.setContentDescription("Más opciones");
        more.setOnClickListener(v->videoMenu(video,action));
        summary.addView(more,Ui.lp(this,28,45));
        card.addView(summary);
        card.setOnClickListener(v->action.run());
        if(longPress!=null)card.setOnLongClickListener(v->{longPress.run();return true;});
    }

    private void videoMenu(JSONObject video,Runnable open){
        String vid=Ui.id(video);
        boolean fav=Store.favorite(this,vid), later=Store.inWatchLater(this,vid);
        boolean resumable=Store.progressMs(this,vid)>0;
        List<String> labels=new ArrayList<>();
        List<Runnable> actions=new ArrayList<>();
        labels.add("▶ Abrir video");actions.add(open);
        labels.add(fav?"★ Quitar de Favoritos":"☆ Guardar en Favoritos");
        actions.add(()->{
            boolean added=Store.toggleFavorite(this,video);
            toast(added?"Agregado a Favoritos":"Quitado de Favoritos");
            if("Biblioteca".equals(tab))library();
        });
        labels.add(later?"✓ Quitar de Ver después":"◷ Ver después");
        actions.add(()->{
            boolean added=Store.toggleWatchLater(this,video);
            toast(added?"Agregado a Ver después":"Quitado de Ver después");
            if("Biblioteca".equals(tab))library();
        });
        if(resumable){
            labels.add("✕ Quitar de Continuar viendo");
            actions.add(()->{
                Store.clearProgress(this,vid);
                toast("Quitado de Continuar viendo");
                if("Biblioteca".equals(tab))library();
                else if("Inicio".equals(tab))refresh(false);
            });
        }
        labels.add("↗ Compartir");
        actions.add(()->{
            Intent send=new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT,"https://www.youtube.com/watch?v="+vid);
            startActivity(Intent.createChooser(send,"Compartir video"));
        });
        new AlertDialog.Builder(this)
            .setTitle(video.optString("title","Video"))
            .setItems(labels.toArray(new String[0]),(d,choice)->actions.get(choice).run())
            .show();
    }

    private void toast(String message){
        Toast.makeText(this,message,Toast.LENGTH_SHORT).show();
    }

    private String firstLetter(String source){
        if(source==null || source.isEmpty())return "Y";
        return source.substring(0,1).toUpperCase(java.util.Locale.ROOT);
    }

    private void play(JSONObject video,String offline){
        Intent intent=new Intent(this,PlayerActivity.class);
        intent.putExtra("video",video.toString());
        if(offline!=null)intent.putExtra("offlineUri",offline);
        startActivity(intent);
    }

    private void empty(String message){
        feed.removeAllViews();
        // Unavailable search/trending servers must not hide locally saved progress.
        if("Inicio".equals(tab))addContinueSection();
        TextView view=Ui.text(this,message,15,Ui.MUTED,false);
        view.setGravity(Gravity.CENTER);
        view.setPadding(Ui.dp(this,22),Ui.dp(this,60),Ui.dp(this,22),Ui.dp(this,18));
        feed.addView(view,Ui.lp(this,-1,-2));
    }

    private void settings(){
        String[] actions={
            "Fuente de videos: "+(Api.useDirect(this)?"Directa (NewPipe)":"Invidious"),
            "Ventana flotante automática: "+(
                getSharedPreferences(Api.PREFS,0).getBoolean("auto_pip",true)?"Activada":"Desactivada"),
            "Información de versión y funciones",
            "Privacidad y limitaciones"
        };
        new AlertDialog.Builder(this).setTitle("Configuración")
            .setItems(actions,(d,which)->{
                if(which==0)serverPicker();
                else if(which==1){
                    boolean prior=getSharedPreferences(Api.PREFS,0).getBoolean("auto_pip",true);
                    getSharedPreferences(Api.PREFS,0).edit().putBoolean("auto_pip",!prior).apply();
                    settings();
                } else if(which==2)showVersionInfo();
                else privacy();
            }).setNegativeButton("Cerrar",null).show();
    }

    private void showVersionInfo() {
        new AlertDialog.Builder(this)
            .setTitle("YungaTube · versión "+Ui.version(this))
            .setMessage("Edición instalada: "+getPackageName()+
                "\n\nPara encontrar los controles de reproducción, abre un video "+
                "y toca «☾ Funciones» en la barra superior. Allí están "+
                "Temporizador, calidad, velocidad, ventana flotante, solo "+
                "audio, descargas, Ver después y favoritos.\n\n"+
                "Si la app muestra otro número de versión, estás abriendo "+
                "una instalación anterior.")
            .setPositiveButton("Entendido",null).show();
    }

    private void serverPicker(){
        String[] options={
            "Directa · NewPipe (recomendada)",
            "Nadeko","NerdVPN","ChocolateMoo","Tiekoetter",
            "Servidor HTTPS propio..."
        };
        new AlertDialog.Builder(this).setTitle("Fuente para obtener videos")
            .setMessage("La fuente directa consulta YouTube sin iniciar sesión. "+
                "Las instancias de Invidious son servicios de terceros y pueden fallar.")
            .setItems(options,(d,index)->{
                if(index==0){Api.selectDirect(this);refresh(true);}
                else if(index<=Api.INSTANCES.length){
                    Api.setInstance(this,Api.INSTANCES[index-1]);
                    refresh(true);
                }else customServer();
            }).setNegativeButton("Cancelar",null).show();
    }

    private void customServer(){
        EditText input=new EditText(this);
        input.setSingleLine();
        input.setText(Api.configured(this));
        new AlertDialog.Builder(this).setTitle("Servidor Invidious HTTPS")
            .setView(input)
            .setPositiveButton("Aplicar",(d,w)->{
                String entered=Api.validate(input.getText().toString());
                if(entered==null)
                    Toast.makeText(this,"Introduce un dominio HTTPS válido",Toast.LENGTH_LONG).show();
                else{Api.setInstance(this,entered);refresh(true);}
            }).setNegativeButton("Cancelar",null).show();
    }

    private void privacy(){
        new AlertDialog.Builder(this).setTitle("Sobre YungaTube")
            .setMessage("YungaTube es una aplicación independiente y no está afiliada "+
                "a YouTube. No solicita una cuenta de Google. Los favoritos, "+
                "el historial y las listas se guardan localmente. "+
                "El modo directo consulta servidores de YouTube; las instancias "+
                "Invidious son servicios de terceros. "+
                "La reproducción y las descargas dependen de los formatos "+
                "disponibles y pueden dejar de funcionar. No incluye YouTube "+
                "Music Premium, beneficios de membresía ni contenido protegido.")
            .setPositiveButton("Entendido",null).show();
    }

    @Override protected void onResume(){
        super.onResume();
        // Re-render with fresh local progress; the network feed comes from cache
        // so the list (and scroll position) is kept after closing a video.
        // The first resume follows onCreate, which already started loading.
        if(!resumedOnce){resumedOnce=true;return;}
        if(root!=null && ("Biblioteca".equals(tab)||"Descargas".equals(tab)||"Inicio".equals(tab))){
            int y=scroll.getScrollY();
            refresh(false);
            scroll.post(()->scroll.scrollTo(0,y));
        }
    }
    @Override public void onBackPressed(){
        // Back from search results or another tab returns home instead of closing.
        if(!"Inicio".equals(tab)){changeTab("Inicio");return;}
        super.onBackPressed();
    }
    @Override protected void onDestroy(){
        ++token;
        executor.shutdownNow();
        super.onDestroy();
    }
}
