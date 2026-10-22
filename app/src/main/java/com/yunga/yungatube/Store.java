package com.yunga.yungatube;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

final class Store {
    private Store() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("yungatube_local", Context.MODE_PRIVATE);
    }

    static JSONArray list(Context c, String key) {
        try { return new JSONArray(prefs(c).getString(key, "[]")); }
        catch (Exception ignore) { return new JSONArray(); }
    }

    private static void save(Context c, String key, JSONArray array) {
        prefs(c).edit().putString(key,array.toString()).apply();
    }

    static JSONObject small(JSONObject raw) {
        JSONObject o = new JSONObject();
        try {
            for (String key : new String[]{"videoId","title","author","authorId",
                "lengthSeconds","published","publishedText"}) {
                if (raw.has(key)) o.put(key,raw.get(key));
            }
        } catch (Exception ignored) {}
        return o;
    }

    static boolean favorite(Context c, String id) {
        JSONArray a = list(c,"favorites");
        for (int i=0;i<a.length();i++)
            if (id.equals(a.optJSONObject(i) == null ? "" :
                a.optJSONObject(i).optString("videoId"))) return true;
        return false;
    }

    static boolean toggleFavorite(Context c, JSONObject video) {
        String id = Ui.id(video);
        if (id.isEmpty()) return false;
        JSONArray input = list(c,"favorites"), out = new JSONArray();
        boolean found=false;
        for (int i=0;i<input.length();i++) {
            JSONObject o = input.optJSONObject(i);
            if (o != null && id.equals(o.optString("videoId"))) found=true;
            else if (o != null) out.put(o);
        }
        if (!found) out.put(small(video));
        save(c,"favorites",out);
        return !found;
    }

    static void visited(Context c, JSONObject video) {
        String id=Ui.id(video);
        if (id.isEmpty()) return;
        JSONArray older=list(c,"history"), out=new JSONArray();
        out.put(small(video));
        for (int i=0; i<older.length() && out.length()<150;i++) {
            JSONObject o=older.optJSONObject(i);
            if (o!=null && !id.equals(o.optString("videoId"))) out.put(o);
        }
        save(c,"history",out);
    }

    static JSONArray subscriptions(Context c) {
        return list(c,"channels");
    }

    static boolean subscribed(Context c, String id) {
        if (id==null || id.isEmpty()) return false;
        JSONArray a=subscriptions(c);
        for (int i=0;i<a.length();i++) {
            JSONObject o=a.optJSONObject(i);
            if(o!=null && id.equals(o.optString("id"))) return true;
        }
        return false;
    }

    static boolean toggleSubscription(Context c, String id, String title) {
        if(id==null || id.isEmpty()) return false;
        JSONArray a=subscriptions(c), out=new JSONArray();
        boolean found=false;
        for(int i=0;i<a.length();i++) {
            JSONObject o=a.optJSONObject(i);
            if(o!=null && id.equals(o.optString("id"))) found=true;
            else if(o!=null) out.put(o);
        }
        if(!found) {
            JSONObject channel=new JSONObject();
            try {channel.put("id",id);channel.put("name",title);} catch(Exception ignore){}
            out.put(channel);
        }
        save(c,"channels",out);
        return !found;
    }
    static boolean inWatchLater(Context c,String id) {
        JSONArray videos=list(c,"watch_later");
        for(int i=0;i<videos.length();i++) {
            JSONObject one=videos.optJSONObject(i);
            if(one!=null && id.equals(one.optString("videoId")))return true;
        }
        return false;
    }

    static boolean toggleWatchLater(Context c,JSONObject video) {
        String id=Ui.id(video);
        if(id.isEmpty())return false;
        JSONArray before=list(c,"watch_later"),after=new JSONArray();
        boolean found=false;
        for(int i=0;i<before.length();i++){
            JSONObject one=before.optJSONObject(i);
            if(one==null)continue;
            if(id.equals(one.optString("videoId")))found=true;
            else after.put(one);
        }
        if(!found)after.put(small(video));
        save(c,"watch_later",after);
        return !found;
    }


    /**
     * Most-recently-watched unfinished items. Stored as JSON in local
     * SharedPreferences so playback resumes even if Android kills the process.
     */
    static synchronized void saveProgress(Context context, JSONObject video,
                                          long positionMs, long durationMs) {
        String id=Ui.id(video);
        if(id.isEmpty() || positionMs<3000) return;
        if(durationMs>0 && durationMs-positionMs<=5000) {
            clearProgress(context,id);
            return;
        }
        JSONArray previous=list(context,"continue_watching");
        JSONArray updated=new JSONArray();
        JSONObject entry=small(video);
        try {
            entry.put("resumeMs",positionMs);
            entry.put("durationMs",Math.max(0,durationMs));
            entry.put("savedAt",System.currentTimeMillis());
        } catch(Exception ignored) {}
        updated.put(entry);
        for(int i=0;i<previous.length() && updated.length()<60;i++) {
            JSONObject old=previous.optJSONObject(i);
            if(old!=null && !id.equals(old.optString("videoId","")))
                updated.put(old);
        }
        save(context,"continue_watching",updated);
    }

    static synchronized long progressMs(Context context,String id) {
        JSONArray records=list(context,"continue_watching");
        for(int i=0;i<records.length();i++) {
            JSONObject entry=records.optJSONObject(i);
            if(entry!=null && id.equals(entry.optString("videoId","")))
                return Math.max(0,entry.optLong("resumeMs",0));
        }
        return 0;
    }

    static synchronized JSONArray continueWatching(Context context) {
        return list(context,"continue_watching");
    }

    static synchronized void clearProgress(Context context,String id) {
        JSONArray previous=list(context,"continue_watching"),updated=new JSONArray();
        for(int i=0;i<previous.length();i++) {
            JSONObject item=previous.optJSONObject(i);
            if(item!=null && !id.equals(item.optString("videoId","")))
                updated.put(item);
        }
        save(context,"continue_watching",updated);
    }

    static void clearHistory(Context c) {
        save(c,"history",new JSONArray());
    }

}
