package ok.dht.test.komissarov;

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
import one.nio.http.HttpServer;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

public class CourseService implements Service {

    private static final String NOT_ENOUGH_REPLICAS = "504 Not Enough Replicas";
    private static final String REPEATED = "Repeated";

    private static final Logger LOGGER = LoggerFactory.getLogger(CourseService.class);
    private final ServiceConfig config;
    private HttpServer server;
    private HttpClient client;
    private MemorySegmentDao dao;
    private MemorySegmentDao tsmpDao;

    public CourseService(ServiceConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        client = HttpClient.newHttpClient();
        dao = new MemorySegmentDao(new Config(
                config.workingDir(),
                1 << 20
        ));
        tsmpDao = new MemorySegmentDao(new Config(
                getPath(config.workingDir().toString() + "/timestamps"),
                1 << 20
        ));
        server = new CustomHttpServer(config, this);
        server.start();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        dao.close();
        tsmpDao.close();
        server.stop();
        return CompletableFuture.completedFuture(null);
    }

    public Response executeRequests(Request request, String id, PairParams params) {
        String[] urls = getNodes(id, params.from());
        Response[] responses = new Response[urls.length];
        int success = 0;
        for (int i = 0; i < urls.length; i++) {
            responses[i] = urls[i].equals(config.selfUrl())
                    ? getResponse(request, id) : getProxyResponse(request, urls[i]);
            int code = responses[i].getStatus();
            if (code == 200 || code == 201 || code == 202 || code == 404) {
                success++;
            }
        }
        boolean isSuccess = success >= params.ack();
        return summaryResponse(responses, request.getMethod(), isSuccess);
    }

    public Response executeSoloRequest(Request request, String id) {
        return getResponse(request, id);
    }

    private static MemorySegment fromString(String value) {
        return value == null ? null : MemorySegment.ofArray(Utf8.toBytes(value));
    }

    private Response getProxyResponse(Request request, String url) {
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
            HttpResponse<byte[]> response = client.send(proxyRequest, HttpResponse.BodyHandlers.ofByteArray());
            return new Response(mapCode(response.statusCode()), response.body());
        } catch (Exception e) {
            LOGGER.error("Unavailable error", e);
            return new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY);
        }
    }

    private Response getResponse(Request request, String id) {
        int method = request.getMethod();
        switch (method) {
            case Request.METHOD_GET -> {
                Entry<MemorySegment> entry = dao.get(fromString(id));
                if (entry == null) {
                    return new Response(Response.NOT_FOUND, Response.EMPTY);
                }
                Entry<MemorySegment> time = tsmpDao.get(fromString(id));
                if (entry.isTombstone()) {
                    return new Response(Response.NOT_FOUND, time.value().toByteArray());
                }
                return new Response(Response.OK, setBody(time.value(), entry.value()));
            }
            case Request.METHOD_PUT -> {
                MemorySegment segmentId = fromString(id);
                Entry<MemorySegment> entry = new BaseEntry<>(
                        segmentId,
                        MemorySegment.ofArray(request.getBody())
                );
                Entry<MemorySegment> time = new BaseEntry<>(
                        segmentId,
                        MemorySegment.ofArray(new long[]{System.currentTimeMillis()})
                );
                dao.upsert(entry);
                tsmpDao.upsert(time);
                return new Response(Response.CREATED, Response.EMPTY);
            }
            case Request.METHOD_DELETE -> {
                Entry<MemorySegment> removedEntry = new BaseEntry<>(
                        fromString(id),
                        null
                );
                dao.upsert(removedEntry);
                long time = System.currentTimeMillis();
                tsmpDao.upsert(
                        new BaseEntry<>(
                        fromString(id),
                        MemorySegment.ofArray(new long[]{time})
                        ));
                return new Response(Response.ACCEPTED, Response.EMPTY);
            }
            default -> {
                return new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
            }
        }
    }

    private Response summaryResponse(Response[] responses, int method, boolean isSuccess) {
        if (method == Request.METHOD_GET) {
            if (!isSuccess) {
                return new Response(NOT_ENOUGH_REPLICAS, Response.EMPTY);
            }

            boolean isTombstone = false;
            long maxTime = Long.MIN_VALUE;
            Response answer = null;
            for (Response response : responses) {
                long time = convertToLong(Arrays.copyOfRange(response.getBody(), 0, 8));
                if (time > maxTime) {
                    maxTime = time;
                    if (response.getStatus() == 404 && response.getBody().length > 0) {
                        isTombstone = true;
                        continue;
                    } else {
                        if (response.getStatus() == 200) {
                            answer = response;
                        }
                    }
                    isTombstone = false;
                }
            }
            if (isTombstone || answer == null) {
                return new Response(Response.NOT_FOUND, Response.EMPTY);
            }
            return new Response(mapCode(answer.getStatus()),
                    Arrays.copyOfRange(answer.getBody(), 8, answer.getBody().length));
        }

        return switch (method) {
            case Request.METHOD_PUT -> isSuccess
                    ? new Response(Response.CREATED, Response.EMPTY)
                    : new Response(NOT_ENOUGH_REPLICAS, Response.EMPTY);
            case Request.METHOD_DELETE -> isSuccess
                    ? new Response(Response.ACCEPTED, Response.EMPTY)
                    : new Response(NOT_ENOUGH_REPLICAS, Response.EMPTY);
            default -> new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
        };
    }

    private String mapCode(int code) {
        return switch (code) {
            case HttpURLConnection.HTTP_OK -> Response.OK;
            case HttpURLConnection.HTTP_CREATED -> Response.CREATED;
            case HttpURLConnection.HTTP_ACCEPTED -> Response.ACCEPTED;
            case HttpURLConnection.HTTP_BAD_REQUEST -> Response.BAD_REQUEST;
            case HttpURLConnection.HTTP_NOT_FOUND -> Response.NOT_FOUND;
            default -> throw new IllegalStateException("Unexpected value:" + code);
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

    private byte[] setBody(MemorySegment time, MemorySegment value) {
        byte[] bTime = time.toByteArray();
        byte[] bValue = value.toByteArray();

        byte[] result = Arrays.copyOf(bTime, bTime.length + bValue.length);
        System.arraycopy(bValue, 0, result, bTime.length, bValue.length);
        return result;
    }

    private static long convertToLong(byte[] bytes)
    {
        long value = 0L;
        for (byte b : bytes) {
            value = (value << 8) + (b & 255);
        }
        return value;
    }

    private static Path getPath(String name) {
        Path path = Path.of(name);
        if (Files.notExists(path)) {
            boolean isCreated = path.toFile().mkdirs();
            if (!isCreated) {
                throw new RuntimeException();
            }
        }
        return path;
    }

    @ServiceFactory(stage = 4, week = 1)
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new CourseService(config);
        }

    }

}
