package com.yunga.yungatube;

import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.datasource.BaseDataSource;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;

/**
 * HTTP data source for YouTube "videoplayback" URLs, modelled on NewPipe's
 * YoutubeHttpDataSource. googlevideo servers throttle or reject plain GET
 * requests from non-browser clients; requesting with POST, a request number
 * ({@code rn}) and, for DASH segments, a {@code range} query parameter makes
 * them serve data at full speed. Any other URL is fetched with a normal GET.
 */
final class YoutubeDataSource extends BaseDataSource {
    static final String USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0";
    private static final byte[] POST_BODY = {0x78, 0};

    static final class Factory implements DataSource.Factory {
        private final boolean rangeParameter;
        /** @param rangeParameter send byte ranges as {@code &range=} (DASH) instead of a header. */
        Factory(boolean rangeParameter) { this.rangeParameter = rangeParameter; }
        @Override public DataSource createDataSource() { return new YoutubeDataSource(rangeParameter); }
    }

    private final boolean rangeParameter;
    private int requestNumber = 0;
    private HttpURLConnection connection;
    private InputStream input;
    private long bytesToRead, bytesRead;
    private boolean opened;

    private YoutubeDataSource(boolean rangeParameter) {
        super(true);
        this.rangeParameter = rangeParameter;
    }

    @Override public long open(DataSpec spec) throws IOException {
        transferInitializing(spec);
        bytesRead = 0;
        long position = spec.position, length = spec.length;
        String url = spec.uri.toString();
        String path = spec.uri.getPath();
        boolean playback = path != null && path.startsWith("/videoplayback");
        boolean useRangeParam = playback && rangeParameter;
        if (playback) {
            if (!url.contains("&rn=")) url += "&rn=" + (requestNumber++);
            if (useRangeParam && (position != 0 || length != C.LENGTH_UNSET)) {
                url += "&range=" + position + "-"
                    + (length != C.LENGTH_UNSET ? Long.toString(position + length - 1) : "");
            }
        }
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(12000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("Accept-Encoding", "identity");
        c.setRequestProperty("User-Agent", YoutubeParsingHelper.isVisionOsStreamingUrl(url)
            ? YoutubeParsingHelper.getVisionOsUserAgent(null) : USER_AGENT);
        if (YoutubeParsingHelper.isWebStreamingUrl(url)) {
            c.setRequestProperty("Origin", "https://www.youtube.com");
            c.setRequestProperty("Referer", "https://www.youtube.com");
        }
        if (!useRangeParam && (position != 0 || length != C.LENGTH_UNSET)) {
            c.setRequestProperty("Range", "bytes=" + position + "-"
                + (length != C.LENGTH_UNSET ? Long.toString(position + length - 1) : ""));
        }
        connection = c;
        if (playback) {
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setFixedLengthStreamingMode(POST_BODY.length);
            try (OutputStream out = c.getOutputStream()) { out.write(POST_BODY); }
        }
        int code = c.getResponseCode();
        if (code < 200 || code > 299) {
            closeQuietly();
            throw new IOException("HTTP " + code + " al cargar el video");
        }
        // A 200 for a ranged header request means the server ignored the range.
        long skip = (code == 200 && position != 0 && !useRangeParam) ? position : 0;
        long contentLength = c.getContentLengthLong();
        if (length != C.LENGTH_UNSET) bytesToRead = length;
        else bytesToRead = contentLength >= 0 ? contentLength - skip : C.LENGTH_UNSET;
        input = c.getInputStream();
        while (skip > 0) {
            long n = input.skip(skip);
            if (n <= 0) {
                if (input.read() < 0) throw new IOException("Fin inesperado del video");
                n = 1;
            }
            skip -= n;
        }
        opened = true;
        transferStarted(spec);
        return bytesToRead;
    }

    @Override public int read(byte[] buffer, int offset, int length) throws IOException {
        if (length == 0) return 0;
        if (bytesToRead != C.LENGTH_UNSET) {
            long remaining = bytesToRead - bytesRead;
            if (remaining <= 0) return C.RESULT_END_OF_INPUT;
            length = (int) Math.min(length, remaining);
        }
        int n = input.read(buffer, offset, length);
        if (n == -1) return C.RESULT_END_OF_INPUT;
        bytesRead += n;
        bytesTransferred(n);
        return n;
    }

    @Override public Uri getUri() {
        return connection == null ? null : Uri.parse(connection.getURL().toString());
    }

    @Override public Map<String, List<String>> getResponseHeaders() {
        return connection == null ? Collections.emptyMap() : connection.getHeaderFields();
    }

    @Override public void close() {
        closeQuietly();
        if (opened) {
            opened = false;
            transferEnded();
        }
    }

    private void closeQuietly() {
        if (input != null) {
            try { input.close(); } catch (IOException ignored) {}
            input = null;
        }
        if (connection != null) {
            connection.disconnect();
            connection = null;
        }
    }
}
