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
}
