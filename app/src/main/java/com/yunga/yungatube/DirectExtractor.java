package com.yunga.yungatube;

import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.kiosk.KioskExtractor;
import org.schabi.newpipe.extractor.search.SearchExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import java.util.List;
import java.util.ArrayList;

final class DirectExtractor {
    private static volatile boolean initialized=false;
    private DirectExtractor() {}
    private static synchronized void setup() {
        if(initialized) return;
        NewPipe.init(new DirectDownloader(),new Localization("es","EC"),new ContentCountry("EC"));
        initialized=true;
    }
    static Api.Result get(String path) throws Exception {
        setup();
        Uri uri=Uri.parse("https://yungatube.local"+path);
        List<String> segments=uri.getPathSegments();
        if(segments.size()<3 || !"api".equals(segments.get(0)) || !"v1".equals(segments.get(1)))
            throw new IllegalArgumentException("Ruta no soportada");
        StreamingService service=NewPipe.getService("YouTube");
        String command=segments.get(2);
        switch(command) {
            case "trending": {
                KioskExtractor<StreamInfoItem> kiosk=service.getKioskList().getDefaultKioskExtractor();
                kiosk.fetchPage();
                return new Api.Result("direct",items(kiosk.getInitialPage().getItems()).toString());
            }
            case "search": {
                String q=uri.getQueryParameter("q");
                if(q==null || q.trim().isEmpty()) throw new Exception("Búsqueda vacía");
                SearchExtractor search=service.getSearchExtractor(q);
                search.fetchPage();
                return new Api.Result("direct",items(search.getInitialPage().getItems()).toString());
            }
            case "videos": {
                if(segments.size()!=4 || !segments.get(3).matches("[A-Za-z0-9_-]{11}"))
                    throw new Exception("ID de video inválido");
                String id=segments.get(3);
                StreamInfo video=StreamInfo.getInfo(service,"https://www.youtube.com/watch?v="+id);
                JSONObject data=videoJson(video);
                return new Api.Result("direct",data.toString());
            }
            case "channels": {
                if(segments.size()!=4 || !segments.get(3).matches("UC[A-Za-z0-9_-]{22}"))
                    throw new Exception("ID de canal inválido");
                String id=segments.get(3);
                ChannelInfo channel=ChannelInfo.getInfo(service,"https://www.youtube.com/channel/"+id);
                JSONObject result=new JSONObject();
                result.put("authorId",id);
                result.put("author",channel.getName());
                JSONArray videos=new JSONArray();
                for(ListLinkHandler tab:channel.getTabs()) {
                    try {
                        ChannelTabInfo info=ChannelTabInfo.getInfo(service,tab);
                        videos=items(info.getRelatedItems());
                        if(videos.length()>0) break;
                    } catch(Exception ignored) { /* Not all tabs contain videos */ }
                }
                result.put("latestVideos",videos);
                return new Api.Result("direct",result.toString());
            }
            default: throw new Exception("Ruta desconocida");
        }
    }
    private static JSONArray items(List<? extends InfoItem> things) {
        JSONArray result=new JSONArray();
        if(things==null) return result;
        for(InfoItem one:things) {
            if(!(one instanceof StreamInfoItem)) continue;
            StreamInfoItem item=(StreamInfoItem)one;
            String id=id(item.getUrl());
            if(id.isEmpty()) continue;
            JSONObject out=new JSONObject();
            try {
                out.put("videoId",id);
                out.put("title",item.getName());
                out.put("author",item.getUploaderName());
                out.put("authorId",channelId(item.getUploaderUrl()));
                out.put("lengthSeconds",Math.max(0,item.getDuration()));
                out.put("publishedText",item.getTextualUploadDate());
                result.put(out);
            } catch(Exception ignored) {}
        }
        return result;
    }
    private static String id(String address) {
        if(address==null) return "";
        Uri uri=Uri.parse(address);
        String id=uri.getQueryParameter("v");
        if(id==null && "youtu.be".equalsIgnoreCase(uri.getHost())) id=uri.getLastPathSegment();
        if(id==null && uri.getLastPathSegment()!=null
            && uri.getLastPathSegment().matches("[A-Za-z0-9_-]{11}"))
            id=uri.getLastPathSegment();
        return id!=null && id.matches("[A-Za-z0-9_-]{11}")? id:"";
    }
    private static String channelId(String url) {
        if(url==null) return "";
        String last=Uri.parse(url).getLastPathSegment();
        return last!=null && last.matches("UC[A-Za-z0-9_-]{22}") ? last : "";
    }
    private static JSONObject videoJson(StreamInfo info) throws Exception {
        JSONObject out=new JSONObject();
        out.put("videoId",id(info.getUrl()));
        out.put("title",info.getName());
        out.put("author",info.getUploaderName());
        out.put("authorId",channelId(info.getUploaderUrl()));
        out.put("lengthSeconds",info.getDuration());
        JSONArray related=items(info.getRelatedItems());
        out.put("recommendedVideos",related);
        JSONArray streams=new JSONArray();
        if(info.getVideoStreams()!=null) {
            for(VideoStream stream:info.getVideoStreams()) {
                if(stream.isVideoOnly() || !stream.isUrl()) continue;
                String content=stream.getContent();
                if(content==null || !content.startsWith("https://")) continue;
                JSONObject s=new JSONObject();
                s.put("itag",Integer.toString(stream.getItag()));
                String format=stream.getFormat()==null ? "" : stream.getFormat().getSuffix();
                s.put("type","mp4".equalsIgnoreCase(format)?"video/mp4":"video/"+format);
                s.put("url",content);
                streams.put(s);
            }
        }
        out.put("formatStreams",streams);
        JSONArray videoOnly=new JSONArray();
        if(info.getVideoOnlyStreams()!=null) {
            for(VideoStream stream:info.getVideoOnlyStreams()) {
                if(!stream.isUrl()) continue;
                String url=stream.getContent();
                if(url==null || !url.startsWith("https://")) continue;
                JSONObject s=new JSONObject();
                s.put("itag",stream.getItag());
                s.put("url",url);
                s.put("resolution",stream.getResolution());
                String ext=stream.getFormat()==null?"":stream.getFormat().getSuffix();
                s.put("type","mp4".equalsIgnoreCase(ext)?"video/mp4":"video/"+ext);
                videoOnly.put(s);
            }
        }
        out.put("videoOnlyStreams",videoOnly);
        JSONArray audioStreams=new JSONArray();
        if(info.getAudioStreams()!=null) {
            for(AudioStream stream:info.getAudioStreams()) {
                if(!stream.isUrl()) continue;
                String url=stream.getContent();
                if(url==null || !url.startsWith("https://")) continue;
                JSONObject s=new JSONObject();
                s.put("url",url);
                String ext=stream.getFormat()==null?"":stream.getFormat().getSuffix();
                s.put("type","m4a".equalsIgnoreCase(ext)?"audio/mp4":"audio/"+ext);
                s.put("bitrate",stream.getAverageBitrate());
                audioStreams.put(s);
            }
        }
        out.put("audioStreams",audioStreams);

        out.put("hlsUrl",info.getHlsUrl()==null?"":info.getHlsUrl());
        return out;
    }
}
