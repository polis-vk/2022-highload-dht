package ok.dht.test.vihnin;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.vihnin.dao.MemorySegmentDao;
import ok.dht.test.vihnin.dao.common.BaseEntry;
import ok.dht.test.vihnin.dao.common.Config;
import ok.dht.test.vihnin.dao.common.Entry;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.RequestMethod;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.AcceptorConfig;
import one.nio.server.SelectorThread;
import one.nio.util.Utf8;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class HighLoadService implements Service {
    public static final String ENDPOINT = "/v0/entity";
    private final ServiceConfig config;
    private HttpServer server;

    private DataStorage storage;

    public HighLoadService(ServiceConfig config) {
        this.config = config;
        this.storage = getDataStorage(config);
    }

    private static DataStorage getDataStorage(ServiceConfig config) {
        DataStorage storage;
        try {
            storage = new DataStorage(
                new Config(config.workingDir(), 1 << 21)
            );
        } catch (IOException e) {
            storage = null;
        }
        return storage;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        this.storage = getDataStorage(this.config);
        server = new HttpServer(createConfigFromPort(config.selfPort())) {
            @Override
            public void handleDefault(Request request, HttpSession session) throws IOException {
                session.sendResponse(emptyResponse(Response.BAD_REQUEST));
            }

            private boolean isMethodAllowed(int method) {
                return method == Request.METHOD_GET
                        || method == Request.METHOD_DELETE
                        || method == Request.METHOD_PUT;
            }

            @Override
            public void handleRequest(Request request, HttpSession session) throws IOException {
                if (isMethodAllowed(request.getMethod())) {
                    super.handleRequest(request, session);
                } else {
                    session.sendResponse(emptyResponse(Response.METHOD_NOT_ALLOWED));
                }
            }

            @Override
            public synchronized void stop() {
                for (SelectorThread selectorThread : selectors) {
                    for (Session session : selectorThread.selector) {
                        session.close();
                    }
                }
                super.stop();
            }

        };
        server.start();
        server.addRequestHandlers(this);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        server.stop();
        storage.close();
        return CompletableFuture.completedFuture(null);
    }

    @Path(ENDPOINT)
    @RequestMethod(Request.METHOD_GET)
    public Response handleGet(@Param("id") String id) {
        if (storage == null) return emptyResponse(Response.NOT_FOUND);
        if (id == null || id.isEmpty()) return emptyResponse(Response.BAD_REQUEST);

        var searchResult = storage.get(getKey(id));

        if (searchResult == null) return emptyResponse(Response.NOT_FOUND);

        return Response.ok(getValue(searchResult));
    }

    @Path(ENDPOINT)
    @RequestMethod(Request.METHOD_PUT)
    public Response handlePut(@Param("id") String id, Request request) {
        if (storage == null) return emptyResponse(Response.NOT_FOUND);

        var requestBody = request.getBody();
        if (id == null || id.isEmpty()) return emptyResponse(Response.BAD_REQUEST);

        storage.upsert(new BaseEntry<>(getKey(id), getSegment(requestBody)));

        return emptyResponse(Response.CREATED);
    }

    @Path(ENDPOINT)
    @RequestMethod(Request.METHOD_DELETE)
    public Response handleDelete(@Param("id") String id) {
        if (storage == null) return emptyResponse(Response.NOT_FOUND);
        if (id == null || id.isEmpty()) return emptyResponse(Response.BAD_REQUEST);

        var key = getKey(id);
        var value = storage.get(key);

        if (value != null) storage.upsert(new BaseEntry<>(key, null));

        return emptyResponse(Response.ACCEPTED);
    }

    private static byte[] getValue(@Nonnull Entry<MemorySegment> searchResult) {
        return searchResult.value().toByteArray();
    }

    private static MemorySegment getKey(String id) {
        return getSegment(Utf8.toBytes(id));
    }

    private static MemorySegment getSegment(byte[] bytes) {
        return MemorySegment.ofArray(bytes);
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    @ServiceFactory(stage = 1, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new HighLoadService(config);
        }
    }

    private static class DataStorage extends MemorySegmentDao {

        public DataStorage(Config config) throws IOException {
            super(config);
        }

    }

    private static Response emptyResponse(String code) {
        return new Response(code, Response.EMPTY);
    }

}
