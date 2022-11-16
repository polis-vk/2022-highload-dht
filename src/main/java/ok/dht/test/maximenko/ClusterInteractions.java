package ok.dht.test.maximenko;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.maximenko.dao.BaseEntry;
import ok.dht.test.maximenko.dao.Entry;
import one.nio.http.Request;
import one.nio.http.Response;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class ClusterInteractions {
    private static final String NOT_SPREAD_HEADER = "notSpread";
    private static final String NOT_SPREAD_HEADER_VALUE = "true";
    private static final String TIME_HEADER = "time";
    private static final Logger LOGGER = Logger.getLogger(String.valueOf(ClusterInteractions.class));

    static class ProxyRangeIterator implements Iterator<Entry<MemorySegment>> {
        private final BufferedReader reader;
        Entry<MemorySegment> nextValue;
        boolean hasNext = true;

        public ProxyRangeIterator(BufferedReader reader) {
            this.reader = reader;
            next();
        }

        @Override
        public boolean hasNext() {
            return hasNext;
        }

        @Override
        public final Entry<MemorySegment> next() {
            Entry<MemorySegment> tmp = nextValue;
            String key;
            String value;
            try {
                key = reader.readLine();
                value = reader.readLine();
            } catch (IOException e) {
                close();
                return tmp;
            }
            if (key == null || value == null) {
                close();
            } else {
                nextValue = new BaseEntry<>(MemorySegment.ofArray(key.getBytes(StandardCharsets.UTF_8)),
                        MemorySegment.ofArray(value.getBytes(StandardCharsets.UTF_8)));
            }
            return tmp;
        }

        private void close() {
            hasNext = false;
            nextValue = null;
        }
    }

    public static CompletableFuture<Response> proxyRequest(String url,
                                                           String key,
                                                           Request request,
                                                           long time,
                                                           HttpClient httpClient) {
        byte[] requestBody = request.getBody();
        if (requestBody == null) {
            requestBody = new byte[0];
        }
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url + "?id=" + key))
                .timeout(Duration.ofSeconds(1))
                .method(request.getMethodName(), HttpRequest.BodyPublishers.ofByteArray(requestBody))
                .header(NOT_SPREAD_HEADER, NOT_SPREAD_HEADER_VALUE)
                .header("time", String.valueOf(time))
                .build();

        try {
            return CompletableFuture.supplyAsync(() -> {
                HttpResponse<byte[]> response;
                try {
                    response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
                } catch (Exception e) {
                    LOGGER.log(Level.INFO, "Failed to send request to another node");
                    return new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY);
                }
                Optional<String> timeHeader = response.headers().firstValue(TIME_HEADER);
                Response result = new Response(HttpUtils.convertStatusCode(response.statusCode()),
                        response.body());
                timeHeader.ifPresent(s -> result.addHeader(TIME_HEADER + ": " + s));
                return result;
            });
        } catch (Exception e) {
            LOGGER.log(Level.INFO, "Inter node interaction failure");
            return CompletableFuture.completedFuture(null);
        }
    }

    public static Iterator<Entry<MemorySegment>> getProxyRange(String url,
                                                         String start,
                                                         String end,
                                                         HttpClient httpClient) {
        String path = end == null ? url + "?start=" + start :
                url + "?start=" + start + "&end=" + end;

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(path))
                .timeout(Duration.ofSeconds(1))
                .GET()
                .header(NOT_SPREAD_HEADER, NOT_SPREAD_HEADER_VALUE)
                .build();

        HttpResponse<InputStream> response;

        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Couldn't arrange range request to other node");
            return Collections.emptyIterator();
        }

        if (response.statusCode() != 200) {
            return Collections.emptyIterator();
        }

        InputStream value = response.body();
        BufferedReader reader = new BufferedReader(new InputStreamReader(value));
        return new ProxyRangeIterator(reader);
    }

    public static boolean isItRequestFRomOtherNode(Request request) {
        String spreadHeaderValue = request.getHeader(NOT_SPREAD_HEADER);
        return spreadHeaderValue != null && spreadHeaderValue.equals(": " + NOT_SPREAD_HEADER_VALUE);
    }
}
