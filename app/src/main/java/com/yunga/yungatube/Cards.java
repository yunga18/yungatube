package com.yunga.yungatube;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Outline;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;

/** Video list building blocks shared by the home feed and the player screen. */
final class Cards {
    private Cards() {}

    /** Full-width card: 16:9 thumbnail, then avatar + title + metadata + menu. */
    static View video(Activity a, JSONObject video, long resumeMs, String extra,
                      Runnable open, Runnable menu, Runnable longPress) {
        LinearLayout card=Ui.vertical(a);
        int width=a.getResources().getDisplayMetrics().widthPixels;
        card.addView(thumbnail(a,video,resumeMs,0),
            new LinearLayout.LayoutParams(-1,width*9/16));

        LinearLayout row=Ui.horizontal(a);
        row.setGravity(Gravity.TOP);
        row.setPadding(Ui.dp(a,12),Ui.dp(a,10),Ui.dp(a,4),Ui.dp(a,18));
        row.addView(avatar(a,video),Ui.lp(a,36,36));
        LinearLayout texts=Ui.vertical(a);
        texts.setPadding(Ui.dp(a,12),0,Ui.dp(a,4),0);
        TextView title=Ui.text(a,video.optString("title","Video"),15,Ui.WHITE,true);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setLineSpacing(Ui.dp(a,2),1f);
        texts.addView(title);
        String meta=Ui.meta(video,true);
        if(extra!=null && !extra.isEmpty()) meta=meta.isEmpty()?extra:meta+" · "+extra;
        TextView sub=Ui.text(a,meta,12,Ui.MUTED,false);
        sub.setMaxLines(2);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        sub.setPadding(0,Ui.dp(a,5),0,0);
        texts.addView(sub);
        row.addView(texts,new LinearLayout.LayoutParams(0,-2,1));
        if(menu!=null) row.addView(menuButton(a,menu),Ui.lp(a,36,36));
        card.addView(row);

        card.setOnClickListener(v->open.run());
        if(longPress!=null) card.setOnLongClickListener(v->{longPress.run();return true;});
        return card;
    }

    /** Compact row: 168dp thumbnail beside the title (used for "A continuación"). */
    static View compact(Activity a, JSONObject video, Runnable open, Runnable menu) {
        LinearLayout row=Ui.horizontal(a);
        row.setGravity(Gravity.TOP);
        row.setPadding(Ui.dp(a,12),Ui.dp(a,6),Ui.dp(a,4),Ui.dp(a,6));
        FrameLayout thumb=thumbnail(a,video,0,8);
        row.addView(thumb,Ui.lp(a,168,94));
        LinearLayout texts=Ui.vertical(a);
        texts.setPadding(Ui.dp(a,10),Ui.dp(a,2),Ui.dp(a,2),0);
        TextView title=Ui.text(a,video.optString("title","Video"),14,Ui.WHITE,true);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(title);
        TextView channel=Ui.text(a,video.optString("author",""),12,Ui.MUTED,false);
        channel.setSingleLine(true);
        channel.setEllipsize(TextUtils.TruncateAt.END);
        channel.setPadding(0,Ui.dp(a,4),0,0);
        texts.addView(channel);
        String meta=Ui.meta(video,false);
        if(!meta.isEmpty()) {
            TextView info=Ui.text(a,meta,12,Ui.MUTED,false);
            info.setSingleLine(true);
            info.setEllipsize(TextUtils.TruncateAt.END);
            info.setPadding(0,Ui.dp(a,2),0,0);
            texts.addView(info);
        }
        row.addView(texts,new LinearLayout.LayoutParams(0,-2,1));
        if(menu!=null) row.addView(menuButton(a,menu),Ui.lp(a,30,32));
        row.setOnClickListener(v->open.run());
        return row;
    }

    /** Vertical 9:16 tile for the Shorts grid. */
    static View shortTile(Activity a, JSONObject video, Runnable open) {
        FrameLayout tile=new FrameLayout(a);
        ImageView image=new ImageView(a);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Ui.SOFT);
        tile.addView(image,new FrameLayout.LayoutParams(-1,-1));
        Images.load(image,Ui.id(video));
        View shade=new View(a);
        shade.setBackground(new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{0x00000000,0xCC000000}));
        tile.addView(shade,new FrameLayout.LayoutParams(-1,Ui.dp(a,110),Gravity.BOTTOM));
        LinearLayout texts=Ui.vertical(a);
        texts.setPadding(Ui.dp(a,8),0,Ui.dp(a,8),Ui.dp(a,9));
        TextView title=Ui.text(a,video.optString("title","Short"),13,Color.WHITE,true);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(title);
        long views=video.optLong("viewCount",-1);
        if(views>=0) {
            TextView v=Ui.text(a,Ui.views(views),11,0xDDFFFFFF,false);
            v.setPadding(0,Ui.dp(a,3),0,0);
            texts.addView(v);
        }
        tile.addView(texts,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        round(tile,10,a);
        tile.setOnClickListener(v->open.run());
        return tile;
    }

    /** Thumbnail with duration / EN VIVO badge and an optional resume bar. */
    static FrameLayout thumbnail(Activity a, JSONObject video, long resumeMs, int radiusDp) {
        FrameLayout thumb=new FrameLayout(a);
        ImageView image=new ImageView(a);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Ui.SOFT);
        thumb.addView(image,new FrameLayout.LayoutParams(-1,-1));
        Images.load(image,Ui.id(video));
        String badge=null;
        int badgeColor=0xCC000000;
        int length=video.optInt("lengthSeconds",0);
        if(Ui.live(video)) {badge="● EN VIVO";badgeColor=Ui.LIVE;}
        else if(length>0) badge=Ui.duration(length);
        if(badge!=null) {
            TextView t=Ui.text(a,badge,11,Color.WHITE,true);
            t.setPadding(Ui.dp(a,5),Ui.dp(a,2),Ui.dp(a,5),Ui.dp(a,2));
            t.setBackground(Ui.background(badgeColor,4,a));
            FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-2,-2,Gravity.RIGHT|Gravity.BOTTOM);
            p.setMargins(0,0,Ui.dp(a,6),Ui.dp(a,6));
            thumb.addView(t,p);
        }
        long totalMs=video.optLong("durationMs",0);
        if(totalMs<=0) totalMs=length*1000L;
        if(resumeMs>0 && totalMs>0) {
            View track=new View(a);
            track.setBackgroundColor(0x66FFFFFF);
            thumb.addView(track,new FrameLayout.LayoutParams(-1,Ui.dp(a,3),Gravity.BOTTOM));
            float fraction=Math.min(1f,Math.max(0.03f,resumeMs/(float)totalMs));
            View meter=new View(a);
            meter.setBackgroundColor(Ui.LIVE);
            meter.setPivotX(0);
            meter.setScaleX(fraction);
            thumb.addView(meter,new FrameLayout.LayoutParams(-1,Ui.dp(a,3),Gravity.BOTTOM));
        }
        if(radiusDp>0) round(thumb,radiusDp,a);
        return thumb;
    }

    /** Circular channel picture, or the first letter when there is none. */
    static View avatar(Activity a, JSONObject video) {
        String url=video.optString("authorThumbnail","");
        if(url.startsWith("https://")) {
            ImageView image=new ImageView(a);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackground(Ui.oval(Ui.SOFT));
            image.setClipToOutline(true);
            Images.loadUrl(image,url);
            return image;
        }
        TextView letter=Ui.text(a,Ui.initial(video.optString("author","Y")),15,Color.WHITE,true);
        letter.setGravity(Gravity.CENTER);
        letter.setBackground(Ui.oval(Ui.RED));
        return letter;
    }

    private static TextView menuButton(Activity a, Runnable menu) {
        TextView more=Ui.text(a,"⋮",20,Ui.WHITE,true);
        more.setGravity(Gravity.CENTER);
        more.setContentDescription("Más opciones");
        more.setOnClickListener(v->menu.run());
        return more;
    }

    static void round(View view,int radiusDp,Activity a) {
        final float radius=Ui.dp(a,radiusDp);
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline outline) {
                outline.setRoundRect(0,0,v.getWidth(),v.getHeight(),radius);
            }
        });
        view.setClipToOutline(true);
    }
}
