package com.yunga.yungatube;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private LinearLayout root, feed, bottom;
    private ProgressBar spinner;
    private TextView heading, subtitle;
    private EditText search;
    private String tab = "Inicio";
    private String category = "default";
    private int sequence = 0;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        build();
        handleSharedLink(getIntent());
        showTab("Inicio");
    }

    @Override public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleSharedLink(intent);
    }

    private void handleSharedLink(Intent intent) {
        if (intent == null || intent.getData() == null) return;
        android.net.Uri uri = intent.getData();
        String id = uri.getQueryParameter("v");
        if (id == null && "youtu.be".equalsIgnoreCase(uri.getHost())) id = uri.getLastPathSegment();
        if (id != null && id.matches("[A-Za-z0-9_-]{11}")) {
            JSONObject obj = new JSONObject();
            try { obj.put("videoId",id);obj.put("title","Cargando video..."); }
            catch(Exception ignored){}
            openVideo(obj);
        }
    }

    private void build() {
        root=Ui.vertical(this);
        root.setBackgroundColor(Ui.BG);
        setContentView(root);
        Ui.insets(this,root);

        LinearLayout top=Ui.horizontal(this);
        top.setPadding(Ui.dp(this,18),Ui.dp(this,12),Ui.dp(this,12),Ui.dp(this,8));
        TextView logo=Ui.pill(this,"▶",true);
        logo.setTextSize(18);
        top.addView(logo,Ui.lp(this,49,44));
        TextView title=Ui.text(this,"  YungaTube",23,Ui.WHITE,true);
        top.addView(title,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));
        TextView reload=Ui.pill(this,"⟳",false);
        reload.setTextSize(22);
        reload.setContentDescription("Actualizar videos");
        reload.setOnClickListener(v->refresh());
        top.addView(reload,Ui.lp(this,48,44));
        TextView settings=Ui.pill(this,"⚙",false);
        settings.setTextSize(21);
        settings.setContentDescription("Configuración");
        LinearLayout.LayoutParams settingsLp=Ui.lp(this,48,44);
        settingsLp.leftMargin=Ui.dp(this,7);
        top.addView(settings,settingsLp);
        settings.setOnClickListener(v->settings());
        root.addView(top);

        LinearLayout bar=Ui.horizontal(this);
        bar.setPadding(Ui.dp(this,15),Ui.dp(this,4),Ui.dp(this,15),Ui.dp(this,12));
        search=new EditText(this);
        search.setSingleLine(true);
        search.setHint("Buscar videos o canales...");
        search.setHintTextColor(Ui.MUTED);
        search.setTextSize(15);
        search.setTextColor(Ui.WHITE);
        search.setPadding(Ui.dp(this,14),0,Ui.dp(this,8),0);
        search.setBackground(Ui.background(Ui.PANEL,18,this));
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setOnEditorActionListener((v, action, event)->{
            if(action==EditorInfo.IME_ACTION_SEARCH) { doSearch();return true; }
            return false;
        });
        bar.addView(search,new LinearLayout.LayoutParams(0,Ui.dp(this,47),1));
        TextView go=Ui.pill(this,"Buscar",true);
        LinearLayout.LayoutParams goLp=Ui.lp(this,-2,47);
        goLp.leftMargin=Ui.dp(this,8);
        bar.addView(go,goLp);
        go.setOnClickListener(v->doSearch());
        root.addView(bar);

        LinearLayout topTitle=Ui.vertical(this);
        topTitle.setPadding(Ui.dp(this,18),Ui.dp(this,5),Ui.dp(this,18),Ui.dp(this,7));
        heading=Ui.text(this,"Para ti",22,Ui.WHITE,true);
        subtitle=Ui.text(this,"Descubre videos sin iniciar sesión",12,Ui.MUTED,false);
        topTitle.addView(heading);
        topTitle.addView(subtitle);
        root.addView(topTitle);

        spinner=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        spinner.setIndeterminate(true);
        spinner.setIndeterminateTintList(ColorStateList.valueOf(Ui.RED));
        root.addView(spinner,Ui.lp(this,-1,3));

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        feed=Ui.vertical(this);
        feed.setPadding(Ui.dp(this,13),Ui.dp(this,9),Ui.dp(this,13),Ui.dp(this,20));
        scroll.addView(feed);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        bottom=Ui.horizontal(this);
        bottom.setPadding(Ui.dp(this,6),Ui.dp(this,9),Ui.dp(this,6),Ui.dp(this,9));
        bottom.setBackgroundColor(Ui.PANEL);
        root.addView(bottom);
        drawBottom();
    }

    private void drawBottom() {
        bottom.removeAllViews();
        String[][] choices = {
            {"Inicio","⌂"},{"Seguidos","♡"},{"Favoritos","★"},{"Historial","◷"}
        };
        for(String[] choice : choices) {
            boolean active=choice[0].equals(tab);
            LinearLayout cell=Ui.vertical(this);
            cell.setGravity(Gravity.CENTER);
            TextView icon=Ui.text(this,choice[1],23,active?Ui.RED:Ui.MUTED,true);
            icon.setGravity(Gravity.CENTER);
            TextView label=Ui.text(this,choice[0],11,active?Ui.WHITE:Ui.MUTED,true);
            label.setGravity(Gravity.CENTER);
            cell.addView(icon);
            cell.addView(label);
            cell.setPadding(0,Ui.dp(this,4),0,Ui.dp(this,4));
            cell.setOnClickListener(v->showTab(choice[0]));
            bottom.addView(cell,new LinearLayout.LayoutParams(0,Ui.dp(this,60),1));
        }
    }

    private interface FeedLoader {
        JSONArray get() throws Exception;
    }

    private void load(FeedLoader loader) {
        final int ticket=++sequence;
        feed.removeAllViews();
        spinner.setVisibility(View.VISIBLE);
        TextView pending=Ui.text(this,"Cargando videos...",14,Ui.MUTED,false);
        pending.setPadding(Ui.dp(this,8),Ui.dp(this,18),0,0);
        feed.addView(pending);
        worker.execute(()->{
            try {
                JSONArray items=loader.get();
                runOnUiThread(()->{
                    if(ticket!=sequence || isFinishing() || isDestroyed()) return;
                    spinner.setVisibility(View.GONE);
                    showItems(items);
                });
            } catch(Exception e) {
                runOnUiThread(()->{
                    if(ticket!=sequence || isFinishing() || isDestroyed()) return;
                    spinner.setVisibility(View.GONE);
                    empty("No se pudieron cargar los videos.\n" + e.getMessage()
                        + "\n\nPrueba cambiando de servidor en ⚙.");
                });
            }
        });
    }

    private void showTab(String page) {
        tab=page;
        drawBottom();
        refresh();
    }

    private void refresh() {
        switch(tab) {
            case "Favoritos":
                heading.setText("Tus favoritos");
                subtitle.setText("Guardados únicamente en este dispositivo");
                ++sequence;
                spinner.setVisibility(View.GONE);
                showItems(Store.list(this,"favorites"));
                break;
            case "Historial":
                heading.setText("Tu historial");
                subtitle.setText("Los videos que has abierto");
                ++sequence;
                spinner.setVisibility(View.GONE);
                showItems(Store.list(this,"history"));
                break;
            case "Seguidos":
                heading.setText("Canales seguidos");
                subtitle.setText("Suscripciones locales, sin cuenta de Google");
                if(Store.subscriptions(this).length()==0) {
                    ++sequence;
                    spinner.setVisibility(View.GONE);
                    empty("Todavía no sigues ningún canal.\nAbre un video y pulsa Seguir.");
                } else load(this::getSubscriptions);
                break;
            case "Buscar":
                doSearch();
                break;
            default:
                heading.setText("Descubre");
                subtitle.setText("Videos en tendencia en Ecuador");
                load(()->Api.get(this,"/api/v1/trending?region=EC" +
                    ("default".equals(category) ? "" : "&type="+category)).array());
                break;
        }
    }

    private JSONArray getSubscriptions() throws Exception {
        JSONArray subscriptions=Store.subscriptions(this);
        ArrayList<JSONObject> merged=new ArrayList<>();
        for(int i=0;i<subscriptions.length() && i<12;i++) {
            JSONObject sub=subscriptions.optJSONObject(i);
            if(sub==null) continue;
            String id=sub.optString("id","");
            if(!id.matches("[a-zA-Z0-9_-]{20,30}")) continue;
            try {
                JSONObject channel=Api.get(this,"/api/v1/channels/"+id).object();
                JSONArray videos=channel.optJSONArray("latestVideos");
                if(videos!=null) for(int j=0;j<videos.length() && j<8;j++) {
                    JSONObject video=videos.optJSONObject(j);
                    if(video!=null) {
                        video.put("authorId",id);
                        if(video.optString("author").isEmpty())
                            video.put("author",sub.optString("name","Canal"));
                        merged.add(video);
                    }
                }
            } catch(Exception ignored) { /* other followed channels may still load */ }
        }
        merged.sort((a,b)->Long.compare(b.optLong("published",0),a.optLong("published",0)));
        JSONArray result=new JSONArray();
        for(int i=0;i<merged.size() && i<80;i++) result.put(merged.get(i));
        return result;
    }

    private void doSearch() {
        String q=search.getText().toString().trim();
        if(q.isEmpty()) {
            search.setError("Escribe qué quieres buscar");
            return;
        }
        ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE))
            .hideSoftInputFromWindow(search.getWindowToken(),0);
        tab="Buscar";
        drawBottom();
        heading.setText("Resultados");
        subtitle.setText("Búsqueda: "+q);
        load(()->Api.get(this,"/api/v1/search?q="+Api.encode(q)+"&type=video").array());
    }

    private void showItems(JSONArray items) {
        feed.removeAllViews();
        if(items==null || items.length()==0) {
            empty("Todavía no hay videos aquí.\nPrueba buscando algo que te guste.");
            return;
        }
        for(int i=0;i<items.length();i++) {
            JSONObject video=items.optJSONObject(i);
            if(video!=null && !Ui.id(video).isEmpty()) addCard(video);
        }
        if(feed.getChildCount()==0) empty("No encontramos videos para mostrar.");
    }

    private void empty(String message) {
        feed.removeAllViews();
        TextView t=Ui.text(this,message,15,Ui.MUTED,false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(this,18),Ui.dp(this,58),Ui.dp(this,18),0);
        feed.addView(t,Ui.lp(this,-1,-2));
    }

    private void addCard(JSONObject video) {
        LinearLayout card=Ui.horizontal(this);
        card.setPadding(Ui.dp(this,8),Ui.dp(this,9),Ui.dp(this,8),Ui.dp(this,9));
        card.setBackground(Ui.background(Ui.PANEL,16,this));
        LinearLayout.LayoutParams cardLp=Ui.lp(this,-1,-2);
        cardLp.bottomMargin=Ui.dp(this,11);

        FrameLayout thumb=new FrameLayout(this);
        ImageView image=new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackground(Ui.background(Ui.SOFT,12,this));
        thumb.addView(image,new FrameLayout.LayoutParams(-1,-1));
        Images.load(image,Ui.id(video));
        int length=video.optInt("lengthSeconds",0);
        if(length>0) {
            TextView duration=Ui.text(this,Ui.duration(length),10,Color.WHITE,true);
            duration.setBackground(Ui.background(0xD9000000,5,this));
            duration.setPadding(Ui.dp(this,5),0,Ui.dp(this,5),0);
            FrameLayout.LayoutParams overlay=new FrameLayout.LayoutParams(-2,Ui.dp(this,21),
                Gravity.BOTTOM|Gravity.RIGHT);
            overlay.setMargins(0,0,Ui.dp(this,6),Ui.dp(this,5));
            thumb.addView(duration,overlay);
        }
        card.addView(thumb,Ui.lp(this,140,86));

        LinearLayout texts=Ui.vertical(this);
        texts.setPadding(Ui.dp(this,12),Ui.dp(this,3),0,0);
        TextView title=Ui.text(this,video.optString("title","Video"),14,Ui.WHITE,true);
        title.setMaxLines(3);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(title);
        TextView author=Ui.text(this,Ui.compact(video),12,Ui.MUTED,false);
        author.setMaxLines(2);
        author.setPadding(0,Ui.dp(this,8),0,0);
        texts.addView(author);
        card.addView(texts,new LinearLayout.LayoutParams(0,-2,1));
        card.setOnClickListener(v->openVideo(video));
        feed.addView(card,cardLp);
    }

    private void openVideo(JSONObject video) {
        Intent player=new Intent(this,PlayerActivity.class);
        player.putExtra("video",video.toString());
        startActivity(player);
    }

    private void settings() {
        String[] options={
            "Nadeko (Chile)", "NerdVPN (Canadá)", "ChocolateMoo (EE. UU.)",
            "Tiekoetter (Alemania)", "Servidor HTTPS personalizado..."
        };
        new AlertDialog.Builder(this)
            .setTitle("Servidor de videos")
            .setMessage("Actual: "+Api.configured(this)+
                "\n\nLos servidores públicos pueden dejar de funcionar. No necesitas una cuenta.")
            .setItems(options,(dialog,which)->{
                if(which < Api.INSTANCES.length) {
                    Api.setInstance(this,Api.INSTANCES[which]);
                    refresh();
                } else customServer();
            })
            .setNeutralButton("Privacidad",(d,w)->privacy())
            .setNegativeButton("Cerrar",null)
            .show();
    }

    private void customServer() {
        EditText field=new EditText(this);
        field.setSingleLine();
        field.setText(Api.configured(this));
        field.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_URI);
        new AlertDialog.Builder(this)
            .setTitle("Servidor Invidious propio")
            .setMessage("Introduce la URL principal del servidor, con https://")
            .setView(field)
            .setPositiveButton("Guardar",(d,w)->{
                String value=Api.validate(field.getText().toString());
                if(value==null) {
                    new AlertDialog.Builder(this).setMessage(
                        "La dirección debe ser HTTPS, sin ruta ni parámetros."
                    ).setPositiveButton("Entendido",null).show();
                } else {
                    Api.setInstance(this,value);
                    refresh();
                }
            })
            .setNegativeButton("Cancelar",null).show();
    }

    private void privacy() {
        new AlertDialog.Builder(this).setTitle("Privacidad y limitaciones")
            .setMessage("YungaTube no solicita usuario o contraseña de Google. " +
                "Los favoritos, canales seguidos e historial se guardan localmente. " +
                "Las búsquedas y videos se consultan a servidores públicos de Invidious y " +
                "las miniaturas se descargan de i.ytimg.com. Los administradores de esos " +
                "servicios pueden ver tu dirección IP. Ni YouTube ni Invidious garantizan " +
                "la disponibilidad o ausencia de publicidad en todas las reproducciones.")
            .setPositiveButton("Entendido",null).show();
    }

    @Override protected void onResume() {
        super.onResume();
        if(feed!=null && ("Historial".equals(tab) || "Favoritos".equals(tab)))
            refresh();
    }

    @Override protected void onDestroy() {
        ++sequence;
        worker.shutdownNow();
        super.onDestroy();
    }
}
