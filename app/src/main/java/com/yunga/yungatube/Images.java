package com.yunga.yungatube;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class Images {
    private static final ExecutorService POOL=Executors.newFixedThreadPool(4);
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    // Sized in bytes (1/8 of the heap) instead of a fixed count of large bitmaps.
    private static final LruCache<String, Bitmap> CACHE=new LruCache<String, Bitmap>(
            (int)Math.min(Integer.MAX_VALUE,Runtime.getRuntime().maxMemory()/8)) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };
    private Images() {}

    /** Video thumbnail by YouTube ID. */
    static void load(ImageView image, String videoId) {
        if (!videoId.matches("[a-zA-Z0-9_-]{11}")) return;
        fetch(image,"https://i.ytimg.com/vi/"+videoId+"/hqdefault.jpg",640);
    }

    /** Any HTTPS image (channel avatars). */
    static void loadUrl(ImageView image, String url) {
        if (url==null || !url.startsWith("https://")) return;
        fetch(image,url,176);
    }

    private static void fetch(ImageView image,String url,int maxWidth) {
        image.setTag(url);
        Bitmap hit=CACHE.get(url);
        if(hit!=null) {image.setImageBitmap(hit);return;}
        image.setImageDrawable(null);
        POOL.execute(()->{
            Bitmap bmp=null;
            HttpURLConnection c=null;
            try {
                c=(HttpURLConnection)new URL(url).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                if(c.getResponseCode()==200) {
                    byte[] data;
                    try(InputStream in=c.getInputStream();
                        ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                        byte[] buffer=new byte[16384];
                        int n;
                        while((n=in.read(buffer))!=-1) {
                            out.write(buffer,0,n);
                            if(out.size()>4_000_000) throw new java.io.IOException("too big");
                        }
                        data=out.toByteArray();
                    }
                    BitmapFactory.Options bounds=new BitmapFactory.Options();
                    bounds.inJustDecodeBounds=true;
                    BitmapFactory.decodeByteArray(data,0,data.length,bounds);
                    BitmapFactory.Options opts=new BitmapFactory.Options();
                    int sample=1;
                    while(bounds.outWidth/(sample*2)>=maxWidth) sample*=2;
                    opts.inSampleSize=sample;
                    opts.inPreferredConfig=Bitmap.Config.RGB_565; // no alpha needed; half the memory
                    bmp=BitmapFactory.decodeByteArray(data,0,data.length,opts);
                }
            } catch(Exception ignored) {} finally {if(c!=null)c.disconnect();}
            Bitmap ready=bmp;
            if(ready!=null) {
                CACHE.put(url,ready);
                MAIN.post(()->{
                    if(url.equals(image.getTag())) image.setImageBitmap(ready);
                });
            }
        });
    }
}
