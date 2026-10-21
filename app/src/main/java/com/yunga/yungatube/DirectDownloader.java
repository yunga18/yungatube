package com.yunga.yungatube;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;

final class DirectDownloader extends Downloader {
    @Override public Response execute(Request request) throws IOException {
        if (!request.url().startsWith("https://"))
            throw new IOException("Solo se permiten solicitudes HTTPS");
        HttpURLConnection connection = (HttpURLConnection)new URL(request.url()).openConnection();
        try {
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(18000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestMethod(request.httpMethod());
            connection.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 12; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36");
            connection.setRequestProperty("Accept-Language","es-EC,es;q=0.9,en;q=0.6");
            Map<String,List<String>> headers=request.headers();
            if(headers!=null) for(Map.Entry<String,List<String>> pair:headers.entrySet()) {
                if(pair.getKey()==null || pair.getValue()==null) continue;
                for(String value:pair.getValue())
                    connection.addRequestProperty(pair.getKey(),value);
            }
            byte[] body=request.dataToSend();
            if(body!=null && body.length>0) {
                connection.setDoOutput(true);
                try(java.io.OutputStream out=connection.getOutputStream()) {out.write(body);}
            }
            int code=connection.getResponseCode();
            String content="";
            if(!"HEAD".equalsIgnoreCase(request.httpMethod())) {
                InputStream source= code>=400 ? connection.getErrorStream() : connection.getInputStream();
                if(source!=null) try(InputStream in=source;ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                    byte[] bytes=new byte[8192];
                    int n;
                    while((n=in.read(bytes))!=-1) {
                        out.write(bytes,0,n);
                        if(out.size()>15_000_000) throw new IOException("Respuesta demasiado grande");
                    }
                    content=out.toString("UTF-8");
                }
            }
            return new Response(code,connection.getResponseMessage(),
                connection.getHeaderFields(),content,connection.getURL().toString());
        } finally {connection.disconnect();}
    }
}
