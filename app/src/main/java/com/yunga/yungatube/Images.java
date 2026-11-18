package com.yunga.yungatube;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class Images {
    private static final ExecutorService POOL=Executors.newFixedThreadPool(3);
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    // Sized in bytes (1/8 of the heap) instead of a fixed count of large bitmaps.
    private static final LruCache<String, Bitmap> CACHE=new LruCache<String, Bitmap>(
            (int)Math.min(Integer.MAX_VALUE,Runtime.getRuntime().maxMemory()/8)) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };
    private Images() {}

    static void load(ImageView image, String videoId) {
        if (!videoId.matches("[a-zA-Z0-9_-]{11}")) return;
        image.setTag(videoId);
        Bitmap hit=CACHE.get(videoId);
        if(hit!=null) {image.setImageBitmap(hit);return;}
        image.setImageDrawable(null);
        POOL.execute(()->{
            Bitmap bmp=null;
            HttpURLConnection c=null;
            try {
                c=(HttpURLConnection)new URL("https://i.ytimg.com/vi/"+videoId+"/hqdefault.jpg").openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                if(c.getResponseCode()==200) {
                    BitmapFactory.Options opts=new BitmapFactory.Options();
                    opts.inPreferredConfig=Bitmap.Config.RGB_565; // thumbnails need no alpha; half the memory
                    try(java.io.InputStream in=c.getInputStream()) {bmp=BitmapFactory.decodeStream(in,null,opts);}
                }
            } catch(Exception ignored) {} finally {if(c!=null)c.disconnect();}
            Bitmap ready=bmp;
            if(ready!=null) {
                CACHE.put(videoId,ready);
                MAIN.post(()->{
                    if(videoId.equals(image.getTag())) image.setImageBitmap(ready);
                });
            }
        });
    }
}
