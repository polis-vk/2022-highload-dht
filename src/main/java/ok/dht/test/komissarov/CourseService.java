package ok.dht.test.komissarov;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import jdk.incubator.foreign.MemorySegment;
import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.komissarov.database.MemorySegmentDao;
import ok.dht.test.komissarov.database.models.BaseEntry;
import ok.dht.test.komissarov.database.models.Config;
import ok.dht.test.komissarov.database.models.Entry;
import ok.dht.test.komissarov.utils.CustomHttpServer;
import ok.dht.test.komissarov.utils.PairParams;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.util.Hash;
import one.nio.util.Utf8;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class CourseService implements Service {

    private static final String REPEATED = "Repeated";

    private static final Logger LOGGER = LoggerFactory.getLogger(CourseService.class);
    private final ServiceConfig config;
    private CustomHttpServer server;
    private HttpClient client;
    private MemorySegmentDao dao;

    public CourseService(ServiceConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        dao = new MemorySegmentDao(new Config(
                config.workingDir(),
                1 << 20
        ));
        server = new CustomHttpServer(config, this);
        client = HttpClient.newBuilder()
                .executor(
                        Executors.newFixedThreadPool(
                                Runtime.getRuntime().availableProcessors(),
                                new ThreadFactoryBuilder()
                                        .setNameFormat("Client workers")
                                        .build())
                )
                .build();
        server.start();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        dao.close();
        server.stop();
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<List<Response>> executeRequests(Request request, String id, PairParams params) {
        List<Response> responses = new CopyOnWriteArrayList<>();
        CompletableFuture<List<Response>> result = new CompletableFuture<>();

        String[] urls = getNodes(id, params.from());
        AtomicInteger success = new AtomicInteger();
        for (String url : urls) {
            if (url.equals(config.selfUrl())) {
                Response current = getResponse(request, id);
                responses.add(current);
                if (checkCode(current.getStatus()) && success.incrementAndGet() == params.ack()) {
                    result.complete(responses);
                    break;
                }
                continue;
            }
            sendAsyncProxy(result, responses, success, request, url, params.ack());
        }
        return result;
    }

    private static MemorySegment fromString(String value) {
        return value == null ? null : MemorySegment.ofArray(Utf8.toBytes(value));
    }

    private static boolean checkCode(int code) {
        return switch (code) {
            case 200, 201, 202, 404 -> true;
            default -> false;
        };
    }

    private void sendAsyncProxy(CompletableFuture<List<Response>> result,
                                List<Response> responses,
                                AtomicInteger success,
                                Request request,
                                String url,
                                int ack) {
        try {
            HttpRequest proxyRequest = HttpRequest.newBuilder()
                    .uri(new URI(url + request.getURI()))
                    .header(REPEATED, REPEATED)
                    .method(
                            request.getMethodName(),
                            request.getBody() == null
                                    ? HttpRequest.BodyPublishers.noBody()
                                    : HttpRequest.BodyPublishers.ofByteArray(request.getBody())
                    )
                    .build();
            CompletableFuture<HttpResponse<byte[]>> future =
                    client.sendAsync(proxyRequest, HttpResponse.BodyHandlers.ofByteArray());

            future.whenComplete((response, throwable) -> {
                if (throwable != null) {
                    result.completeExceptionally(new RuntimeException());
                    return;
                }

                responses.add(new Response(mapCode(response.statusCode()), response.body()));
                if (checkCode(response.statusCode()) && success.incrementAndGet() == ack) {
                    result.complete(responses);
                }
            });
        } catch (Exception e) {
            LOGGER.error("URI error", e);
            throw new RuntimeException();
        }
    }

    public Response getResponse(Request request, String id) {
        switch (request.getMethod()) {
            case Request.METHOD_GET -> {
                Entry<MemorySegment> entry = dao.get(fromString(id));
                if (entry == null) {
                    return new Response(Response.NOT_FOUND, Response.EMPTY);
                }
                byte[] time = convertToBytes(entry.timestamp());
                if (entry.isTombstone()) {
                    return new Response(Response.NOT_FOUND, time);
                }
                return new Response(Response.OK, setBody(time, entry.value()));
            }
            case Request.METHOD_PUT -> {
                MemorySegment segmentId = fromString(id);
                Entry<MemorySegment> entry = new BaseEntry<>(
                        segmentId,
                        MemorySegment.ofArray(request.getBody()),
                        System.currentTimeMillis()
                );
                dao.upsert(entry);
                return new Response(Response.CREATED, Response.EMPTY);
            }
            case Request.METHOD_DELETE -> {
                Entry<MemorySegment> removedEntry = new BaseEntry<>(
                        fromString(id),
                        null,
                        System.currentTimeMillis()
                );
                dao.upsert(removedEntry);
                return new Response(Response.ACCEPTED, Response.EMPTY);
            }
            default -> {
                return new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
            }
        }
    }

    public String mapCode(int code) {
        return switch (code) {
            case HttpURLConnection.HTTP_OK -> Response.OK;
            case HttpURLConnection.HTTP_CREATED -> Response.CREATED;
            case HttpURLConnection.HTTP_ACCEPTED -> Response.ACCEPTED;
            case HttpURLConnection.HTTP_BAD_REQUEST -> Response.BAD_REQUEST;
            case HttpURLConnection.HTTP_NOT_FOUND -> Response.NOT_FOUND;
            default -> Response.SERVICE_UNAVAILABLE;
        };
    }

    private String[] getNodes(String id, int from) {
        SortedMap<Integer, String> hashes = new TreeMap<>();

        for (String url : config.clusterUrls()) {
            int hash = Hash.murmur3(url + id);
            hashes.put(hash, url);
        }
        return hashes.values().stream().limit(from).toArray(String[]::new);
    }

    private byte[] setBody(byte[] time, MemorySegment value) {
        byte[] valueBytes = value.toByteArray();

        byte[] result = Arrays.copyOf(time, time.length + valueBytes.length);
        System.arraycopy(valueBytes, 0, result, time.length, valueBytes.length);
        return result;
    }

    public byte[] convertToBytes(long x) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(x);
        return buffer.array();
    }

    @ServiceFactory(stage = 5, week = 1)
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new CourseService(config);
        }

    }

}
