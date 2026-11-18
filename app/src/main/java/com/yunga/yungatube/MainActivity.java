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
    private LinearLayout searchBox;
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
        Ui.lightBars(this);
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

        // App bar: logo + name, then search / refresh / settings icons.
        LinearLayout brand=Ui.horizontal(this);
        brand.setPadding(Ui.dp(this,12),Ui.dp(this,6),Ui.dp(this,4),Ui.dp(this,6));
        ImageView logo=new ImageView(this);
        logo.setImageResource(R.drawable.logo_yungatube);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setContentDescription("Logo YungaTube");
        Cards.round(logo,8,this);
        brand.addView(logo,Ui.lp(this,32,32));
        TextView word=Ui.text(this,"YungaTube",20,Ui.WHITE,true);
        word.setTypeface(android.graphics.Typeface.create("sans-serif-condensed",
            android.graphics.Typeface.BOLD));
        word.setPadding(Ui.dp(this,6),0,0,0);
        brand.addView(word,new LinearLayout.LayoutParams(0,Ui.dp(this,44),1));
        TextView searchIcon=Ui.icon(this,"⌕","Buscar");
        searchIcon.setTextSize(26);
        searchIcon.setOnClickListener(v->toggleSearch(searchBox.getVisibility()!=View.VISIBLE));
        brand.addView(searchIcon,Ui.lp(this,44,44));
        TextView refresh=Ui.icon(this,"⟳","Actualizar videos");
        refresh.setOnClickListener(v->refresh(true));
        brand.addView(refresh,Ui.lp(this,44,44));
        TextView settings=Ui.icon(this,"⚙","Ajustes de YungaTube");
        settings.setOnClickListener(v->settings());
        brand.addView(settings,Ui.lp(this,44,44));
        root.addView(brand);

        // Search field, revealed from the app bar like a typical video app.
        searchBox=Ui.horizontal(this);
        searchBox.setPadding(Ui.dp(this,12),0,Ui.dp(this,12),Ui.dp(this,8));
        search=new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(Ui.WHITE);
        search.setHintTextColor(Ui.MUTED);
        search.setTextSize(15);
        search.setHint("Buscar en YouTube");
        search.setPadding(Ui.dp(this,16),0,Ui.dp(this,12),0);
        search.setBackground(Ui.background(Ui.PANEL,22,this));
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setOnEditorActionListener((v,action,event)->{
            if(action==EditorInfo.IME_ACTION_SEARCH){searchNow();return true;}
            return false;
        });
        searchBox.addView(search,new LinearLayout.LayoutParams(0,Ui.dp(this,42),1));
        TextView go=Ui.icon(this,"⌕","Buscar ahora");
        go.setTextColor(android.graphics.Color.WHITE);
        go.setBackground(Ui.oval(Ui.WHITE));
        LinearLayout.LayoutParams goLp=Ui.lp(this,42,42);
        goLp.leftMargin=Ui.dp(this,8);
        searchBox.addView(go,goLp);
        go.setOnClickListener(v->searchNow());
        searchBox.setVisibility(View.GONE);
        root.addView(searchBox);

        filterRow=Ui.horizontal(this);
        filterRow.setPadding(Ui.dp(this,8),0,Ui.dp(this,12),0);
        HorizontalScrollView filters=new HorizontalScrollView(this);
        filters.setHorizontalScrollBarEnabled(false);
        filters.setFillViewport(false);
        filters.addView(filterRow);
        root.addView(filters,Ui.lp(this,-1,48));

        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setIndeterminateTintList(ColorStateList.valueOf(Ui.LIVE));
        root.addView(progress,Ui.lp(this,-1,2));

        scroll=new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        LinearLayout container=Ui.vertical(this);
        heading=Ui.text(this,"",20,Ui.WHITE,true);
        heading.setPadding(Ui.dp(this,14),Ui.dp(this,10),Ui.dp(this,14),Ui.dp(this,10));
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

        root.addView(Ui.divider(this),Ui.lp(this,-1,1));
        bottom=Ui.horizontal(this);
        bottom.setBackgroundColor(Ui.BG);
        bottom.setPadding(0,Ui.dp(this,4),0,Ui.dp(this,4));
        root.addView(bottom);
        tabs();
    }

    private void toggleSearch(boolean show){
        searchBox.setVisibility(show?View.VISIBLE:View.GONE);
        InputMethodManager ime=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        if(show){
            search.requestFocus();
            ime.showSoftInput(search,InputMethodManager.SHOW_IMPLICIT);
        }else ime.hideSoftInputFromWindow(search.getWindowToken(),0);
    }

    private void tabs(){
        bottom.removeAllViews();
        String[][] tabs={
            {"Inicio","⌂"},{"Shorts","▶"},{"Seguidos","▤"},
            {"Biblioteca","☰"},{"Descargas","⤓"}
        };
        for(String[] pair:tabs){
            boolean active=pair[0].equals(tab);
            LinearLayout item=Ui.vertical(this);
            item.setGravity(Gravity.CENTER);
            TextView icon=Ui.text(this,pair[1],21,active?Ui.WHITE:Ui.MUTED,active);
            icon.setGravity(Gravity.CENTER);
            item.addView(icon);
            TextView label=Ui.text(this,pair[0],10,active?Ui.WHITE:Ui.MUTED,active);
            label.setGravity(Gravity.CENTER);
            label.setPadding(0,Ui.dp(this,3),0,0);
            item.addView(label);
            item.setContentDescription(pair[0]);
            item.setOnClickListener(v->changeTab(pair[0]));
            bottom.addView(item,new LinearLayout.LayoutParams(0,Ui.dp(this,50),1));
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
            ((View)filterRow.getParent()).setVisibility(View.GONE);
            return;
        }
        ((View)filterRow.getParent()).setVisibility(View.VISIBLE);
        String[] names={"Todo","Música","Videojuegos","En vivo","Noticias","Tecnología",
            "Ciencia","Matemáticas","Deportes","Comedia"};
        for(String name:names){
            TextView chip=Ui.pill(this,name,name.equals(selectedTopic));
            LinearLayout.LayoutParams p=Ui.lp(this,-2,34);
            p.leftMargin=Ui.dp(this,6);
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
        heading.setVisibility(View.VISIBLE);
        libraryTabs.removeAllViews();
        libraryScroll.setVisibility("Biblioteca".equals(tab)?View.VISIBLE:View.GONE);
        switch(tab){
            case "Inicio":
                heading.setVisibility(View.GONE);
                String topic=selectedTopic;
                if("Todo".equals(topic)){
                    load("trending",force,()->Api.get(this,"/api/v1/trending?region=EC").array());
                }else if("En vivo".equals(topic)){
                    load("topic:"+topic,force,()->Api.get(this,"/api/v1/search?q="+Api.encode("en vivo ahora")+"&type=video").array());
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
        toggleSearch(false);
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
            addCard(item,0,label,()->{
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
        if("Shorts".equals(tab)){shortsGrid(items);return;}
        HashMap<String,Long> resume=resumeMap();
        for(int i=0;i<Math.min(90,items.length());i++){
            JSONObject item=items.optJSONObject(i);
            if(item==null || Ui.id(item).isEmpty())continue;
            long at=item.optLong("resumeMs",0);
            if(at<=0 && resume.containsKey(Ui.id(item)))at=resume.get(Ui.id(item));
            addCard(item,at,"",()->play(item,null),null);
        }
        if(feed.getChildCount()==0)empty("No encontramos videos para mostrar.");
    }

    private HashMap<String,Long> resumeMap(){
        HashMap<String,Long> map=new HashMap<>();
        JSONArray saved=Store.continueWatching(this);
        for(int i=0;i<saved.length();i++){
            JSONObject o=saved.optJSONObject(i);
            if(o!=null)map.put(o.optString("videoId",""),o.optLong("resumeMs",0));
        }
        return map;
    }

    /** Two-column grid of vertical tiles. Prefers real Shorts (≤ 3 min) when there are enough. */
    private void shortsGrid(JSONArray items){
        List<JSONObject> picked=new ArrayList<>(), all=new ArrayList<>();
        for(int i=0;i<items.length();i++){
            JSONObject o=items.optJSONObject(i);
            if(o==null || Ui.id(o).isEmpty() || Ui.live(o))continue;
            all.add(o);
            int len=o.optInt("lengthSeconds",0);
            if(o.optBoolean("isShort",false) || (len>0 && len<=180))picked.add(o);
        }
        if(picked.size()<4)picked=all;
        int width=getResources().getDisplayMetrics().widthPixels;
        int gap=Ui.dp(this,4);
        int tileW=(width-gap*3)/2;
        LinearLayout row=null;
        for(int i=0;i<picked.size();i++){
            if(i%2==0){
                row=Ui.horizontal(this);
                row.setPadding(gap,0,gap,gap);
                feed.addView(row);
            }
            JSONObject item=picked.get(i);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(tileW,tileW*16/9);
            if(i%2==1)p.leftMargin=gap;
            row.addView(Cards.shortTile(this,item,()->play(item,null)),p);
        }
        if(feed.getChildCount()==0)empty("No encontramos Shorts para mostrar.");
    }

    private void addContinueSection() {
        JSONArray items=Store.continueWatching(this);
        if(items.length()==0) return;
        LinearLayout headingRow=Ui.horizontal(this);
        headingRow.setPadding(Ui.dp(this,14),Ui.dp(this,10),Ui.dp(this,14),Ui.dp(this,10));
        TextView title=Ui.text(this,"Continuar viendo",18,Ui.WHITE,true);
        headingRow.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView all=Ui.pill(this,"Ver todos",false);
        all.setTextSize(13);
        all.setOnClickListener(v->{
            librarySection="Continuar viendo";
            changeTab("Biblioteca");
        });
        headingRow.addView(all);
        feed.addView(headingRow);

        HorizontalScrollView strip=new HorizontalScrollView(this);
        strip.setHorizontalScrollBarEnabled(false);
        LinearLayout list=Ui.horizontal(this);
        list.setGravity(Gravity.TOP);
        list.setPadding(Ui.dp(this,10),0,Ui.dp(this,10),0);
        strip.addView(list);
        for(int i=0;i<Math.min(items.length(),12);i++) {
            JSONObject item=items.optJSONObject(i);
            if(item==null || Ui.id(item).isEmpty())continue;
            LinearLayout card=Ui.vertical(this);
            LinearLayout.LayoutParams cardParams=Ui.lp(this,176,-2);
            cardParams.leftMargin=Ui.dp(this,4);
            cardParams.rightMargin=Ui.dp(this,4);
            list.addView(card,cardParams);
            card.addView(Cards.thumbnail(this,item,item.optLong("resumeMs",0),8),Ui.lp(this,-1,99));
            TextView name=Ui.text(this,item.optString("title","Video"),13,Ui.WHITE,true);
            name.setMaxLines(2);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setPadding(0,Ui.dp(this,7),0,Ui.dp(this,3));
            card.addView(name);
            String time=Ui.duration((int)(item.optLong("resumeMs",0)/1000));
            TextView resume=Ui.text(this,item.optString("author","")+" · desde "+time,11,Ui.MUTED,false);
            resume.setSingleLine(true);
            resume.setEllipsize(android.text.TextUtils.TruncateAt.END);
            card.addView(resume);
            card.setOnClickListener(v->play(item,null));
            card.setOnLongClickListener(v->{videoMenu(item,()->play(item,null));return true;});
        }
        feed.addView(strip);
        View spacing=Ui.divider(this);
        LinearLayout.LayoutParams sp=Ui.lp(this,-1,4);
        sp.topMargin=Ui.dp(this,16);
        sp.bottomMargin=Ui.dp(this,4);
        feed.addView(spacing,sp);
    }

    private void addCard(JSONObject video,long resumeMs,String suffix,Runnable action,Runnable longPress){
        View card=Cards.video(this,video,resumeMs,suffix,action,
            ()->videoMenu(video,action),longPress);
        feed.addView(card,Ui.lp(this,-1,-2));
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
        view.setLineSpacing(Ui.dp(this,3),1f);
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
        if(searchBox.getVisibility()==View.VISIBLE){toggleSearch(false);return;}
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
