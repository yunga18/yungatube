package com.yunga.yungatube;

import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;

final class Downloads {
    private Downloads() {}
    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("yungatube_downloads",Context.MODE_PRIVATE);
    }
    static JSONArray entries(Context c) {
        try{return new JSONArray(prefs(c).getString("entries","[]"));}
        catch(Exception e){return new JSONArray();}
    }
    static long queue(Context c,JSONObject video,String source) throws Exception {
        if(source==null || !source.startsWith("https://"))
            throw new IllegalArgumentException("No hay enlace MP4 disponible para descargar");
        String id=Ui.id(video);
        if(id.isEmpty()) throw new IllegalArgumentException("Video inválido");
        String title=video.optString("title","Video");
        DownloadManager.Request request=new DownloadManager.Request(Uri.parse(source));
        request.setTitle(title);
        request.setDescription("YungaTube · guardando MP4 para usar sin conexión");
        request.setMimeType("video/mp4");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setAllowedOverMetered(true);
        request.setDestinationInExternalFilesDir(c,Environment.DIRECTORY_MOVIES,
            id+"_"+System.currentTimeMillis()+".mp4");
        DownloadManager dm=(DownloadManager)c.getSystemService(Context.DOWNLOAD_SERVICE);
        long downloadId=dm.enqueue(request);
        JSONArray entries=entries(c);
        JSONObject o=Store.small(video);
        o.put("downloadId",downloadId);
        entries.put(o);
        prefs(c).edit().putString("entries",entries.toString()).apply();
        return downloadId;
    }
    static int status(Context c,long id) {
        DownloadManager manager=(DownloadManager)c.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Query query=new DownloadManager.Query().setFilterById(id);
        try(Cursor cursor=manager.query(query)) {
            if(cursor!=null && cursor.moveToFirst())
                return cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
        }catch(Exception ignored){}
        return DownloadManager.STATUS_FAILED;
    }
    static Uri uri(Context c,long id) {
        DownloadManager manager=(DownloadManager)c.getSystemService(Context.DOWNLOAD_SERVICE);
        return manager.getUriForDownloadedFile(id);
    }
    static String description(int status) {
        if(status==DownloadManager.STATUS_SUCCESSFUL)return "Disponible sin conexión";
        if(status==DownloadManager.STATUS_RUNNING)return "Descargando...";
        if(status==DownloadManager.STATUS_PENDING)return "En espera";
        if(status==DownloadManager.STATUS_PAUSED)return "Descarga pausada";
        return "No se pudo descargar";
    }
    static void remove(Context c,long id) {
        DownloadManager manager=(DownloadManager)c.getSystemService(Context.DOWNLOAD_SERVICE);
        manager.remove(id);
        JSONArray input=entries(c),next=new JSONArray();
        for(int i=0;i<input.length();i++){
            JSONObject o=input.optJSONObject(i);
            if(o!=null && o.optLong("downloadId",-1)!=id)next.put(o);
        }
        prefs(c).edit().putString("entries",next.toString()).apply();
    }
}
