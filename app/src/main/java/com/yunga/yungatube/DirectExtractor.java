package com.yunga.yungatube;

import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.services.youtube.ItagItem;
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeProgressiveDashManifestCreator;
import org.schabi.newpipe.extractor.stream.AudioTrackType;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.StreamType;
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
                if(item.getViewCount()>=0) out.put("viewCount",item.getViewCount());
                String avatar=bestImage(item.getUploaderAvatars(),88);
                if(!avatar.isEmpty()) out.put("authorThumbnail",avatar);
                StreamType type=item.getStreamType();
                if(type==StreamType.LIVE_STREAM || type==StreamType.AUDIO_LIVE_STREAM)
                    out.put("liveNow",true);
                if(item.isShortFormContent()) out.put("isShort",true);
                result.put(out);
            } catch(Exception ignored) {}
        }
        return result;
    }
    /** Smallest image at least {@code minPx} tall (or the largest available). */
    private static String bestImage(List<Image> images,int minPx) {
        if(images==null || images.isEmpty()) return "";
        Image best=null;
        for(Image image:images) {
            if(image.getUrl()==null || !image.getUrl().startsWith("https://")) continue;
            if(best==null) {best=image;continue;}
            int h=image.getHeight(), b=best.getHeight();
            boolean bigEnough=h>=minPx, bestBigEnough=b>=minPx;
            if(bigEnough && (!bestBigEnough || h<b)) best=image;
            else if(!bigEnough && !bestBigEnough && h>b) best=image;
        }
        return best==null?"":best.getUrl();
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
    /** DASH manifest for a progressive stream so ExoPlayer requests bounded byte ranges. */
    private static String manifest(String url,ItagItem itag,long durationSeconds) {
        if(itag==null) return "";
        try {
            return YoutubeProgressiveDashManifestCreator.fromProgressiveStreamingUrl(
                url,itag,durationSeconds);
        } catch(Exception e) { return ""; }
    }
    private static JSONObject videoJson(StreamInfo info) throws Exception {
        JSONObject out=new JSONObject();
        out.put("videoId",id(info.getUrl()));
        out.put("title",info.getName());
        out.put("author",info.getUploaderName());
        out.put("authorId",channelId(info.getUploaderUrl()));
        long duration=Math.max(0,info.getDuration());
        out.put("lengthSeconds",duration);
        if(info.getViewCount()>=0) out.put("viewCount",info.getViewCount());
        if(info.getLikeCount()>=0) out.put("likeCount",info.getLikeCount());
        if(info.getUploaderSubscriberCount()>=0)
            out.put("subCount",info.getUploaderSubscriberCount());
        String published=info.getTextualUploadDate();
        if(published!=null) out.put("publishedText",published);
        String avatar=bestImage(info.getUploaderAvatars(),88);
        if(!avatar.isEmpty()) out.put("authorThumbnail",avatar);
        if(info.getDescription()!=null && info.getDescription().getContent()!=null)
            out.put("description",plain(info.getDescription().getContent()));
        StreamType type=info.getStreamType();
        boolean live=type==StreamType.LIVE_STREAM || type==StreamType.AUDIO_LIVE_STREAM;
        out.put("liveNow",live);
        out.put("recommendedVideos",items(info.getRelatedItems()));

        // Muxed (audio+video) progressive MP4, usually only 360p.
        JSONArray streams=new JSONArray();
        if(!live && info.getVideoStreams()!=null) {
            for(VideoStream stream:info.getVideoStreams()) {
                if(stream.isVideoOnly() || !stream.isUrl()
                    || stream.getDeliveryMethod()!=DeliveryMethod.PROGRESSIVE_HTTP) continue;
                String content=stream.getContent();
                if(content==null || !content.startsWith("https://")) continue;
                String format=stream.getFormat()==null ? "" : stream.getFormat().getSuffix();
                if(!"mp4".equalsIgnoreCase(format)) continue;
                JSONObject s=new JSONObject();
                s.put("itag",Integer.toString(stream.getItag()));
                s.put("type","video/mp4");
                s.put("url",content);
                s.put("resolution",stream.getResolution());
                streams.put(s);
            }
        }
        out.put("formatStreams",streams);

        // Video-only tracks. Only H.264 (avc1): AV1/VP9 often have no hardware
        // decoder, which made ExoPlayer silently drop the picture and play audio only.
        JSONArray videoOnly=new JSONArray();
        if(!live && info.getVideoOnlyStreams()!=null) {
            for(VideoStream stream:info.getVideoOnlyStreams()) {
                if(!stream.isUrl() || stream.getDeliveryMethod()!=DeliveryMethod.PROGRESSIVE_HTTP)
                    continue;
                String url=stream.getContent();
                String codec=stream.getCodec()==null?"":stream.getCodec();
                if(url==null || !url.startsWith("https://") || !codec.startsWith("avc1")) continue;
                int height=stream.getHeight()>0?stream.getHeight():parseHeight(stream.getResolution());
                if(height<=0 || height>1080) continue;
                JSONObject s=new JSONObject();
                s.put("itag",stream.getItag());
                s.put("url",url);
                s.put("height",height);
                s.put("fps",stream.getFps());
                s.put("resolution",stream.getResolution());
                s.put("type","video/mp4");
                s.put("manifest",manifest(url,stream.getItagItem(),duration));
                videoOnly.put(s);
            }
        }
        out.put("videoOnlyStreams",videoOnly);

        // Best AAC track in the original language (not dubbed/descriptive).
        JSONArray audioStreams=new JSONArray();
        AudioStream bestAudio=null, anyAudio=null;
        if(!live && info.getAudioStreams()!=null) {
            for(AudioStream stream:info.getAudioStreams()) {
                if(!stream.isUrl() || stream.getDeliveryMethod()!=DeliveryMethod.PROGRESSIVE_HTTP)
                    continue;
                String url=stream.getContent();
                if(url==null || !url.startsWith("https://")) continue;
                String ext=stream.getFormat()==null?"":stream.getFormat().getSuffix();
                if(!"m4a".equalsIgnoreCase(ext)) continue;
                if(anyAudio==null || stream.getAverageBitrate()>anyAudio.getAverageBitrate())
                    anyAudio=stream;
                AudioTrackType track=stream.getAudioTrackType();
                if(track!=null && track!=AudioTrackType.ORIGINAL) continue;
                if(bestAudio==null || stream.getAverageBitrate()>bestAudio.getAverageBitrate())
                    bestAudio=stream;
            }
        }
        if(bestAudio==null) bestAudio=anyAudio;
        if(bestAudio!=null) {
            JSONObject s=new JSONObject();
            s.put("url",bestAudio.getContent());
            s.put("type","audio/mp4");
            s.put("bitrate",bestAudio.getAverageBitrate());
            s.put("manifest",manifest(bestAudio.getContent(),bestAudio.getItagItem(),duration));
            audioStreams.put(s);
        }
        out.put("audioStreams",audioStreams);

        String hls=info.getHlsUrl();
        out.put("hlsUrl",hls==null?"":hls);
        return out;
    }
    private static int parseHeight(String resolution) {
        if(resolution==null) return 0;
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("(\\d{3,4})p").matcher(resolution);
        return m.find()?Integer.parseInt(m.group(1)):0;
    }
    private static String plain(String html) {
        if(html.indexOf('<')<0) return html;
        return android.text.Html.fromHtml(html.replace("\n","<br>"),
            android.text.Html.FROM_HTML_MODE_LEGACY).toString();
    }
}
