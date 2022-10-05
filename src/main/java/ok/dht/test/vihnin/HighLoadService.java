package ok.dht.test.vihnin;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.vihnin.database.DataBase;
import ok.dht.test.vihnin.database.DataBaseRocksDBImpl;
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
import org.rocksdb.RocksDBException;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import static ok.dht.test.vihnin.ServiceUtils.ENDPOINT;
import static ok.dht.test.vihnin.ServiceUtils.emptyResponse;

public class HighLoadService implements Service {
    private final ServiceConfig config;
    private HttpServer server;

    private DataBase<String, byte[]> storage;

    public HighLoadService(ServiceConfig config) {
        this.config = config;
    }

    private static DataBase<String, byte[]> getDataStorage(ServiceConfig config) {
        DataBase<String, byte[]> storage;
        try {
            storage = new DataBaseRocksDBImpl(config.workingDir());
        } catch (RocksDBException e) {
            e.printStackTrace();
            storage = null;
        }
        return storage;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        storage = getDataStorage(this.config);
//        server = new HttpServer(createConfigFromPort(config.selfPort())) {
//            @Override
//            public void handleDefault(Request request, HttpSession session) throws IOException {
//                session.sendResponse(emptyResponse(Response.BAD_REQUEST));
//            }
//
//            @Override
//            public synchronized void stop() {
//                for (SelectorThread selectorThread : selectors) {
//                    for (Session session : selectorThread.selector) {
//                        session.close();
//                    }
//                }
//                super.stop();
//            }
//
//        };
        server = new ParallelHttpServer(createConfigFromPort(config.selfPort()));
        server.start();
        server.addRequestHandlers(this);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        server.stop();
        if (storage != null) {
            storage.close();
        }
        storage = null;
        return CompletableFuture.completedFuture(null);
    }

    @Path(ENDPOINT)
    @RequestMethod(Request.METHOD_GET)
    public Response handleGet(@Param(value = "id", required = true) String id) {
        if (storage == null) return emptyResponse(Response.NOT_FOUND);
        if (id == null || id.isEmpty()) return emptyResponse(Response.BAD_REQUEST);

        var searchResult = storage.get(id);

        if (searchResult == null) return emptyResponse(Response.NOT_FOUND);

        return Response.ok(searchResult);
    }

    @Path(ENDPOINT)
    @RequestMethod(Request.METHOD_PUT)
    public Response handlePut(@Param(value = "id", required = true) String id, Request request) {
        if (storage == null) return emptyResponse(Response.NOT_FOUND);

        var requestBody = request.getBody();

        if (id == null || id.isEmpty()) return emptyResponse(Response.BAD_REQUEST);

        storage.put(id, requestBody);

        return emptyResponse(Response.CREATED);
    }

    @Path(ENDPOINT)
    @RequestMethod(Request.METHOD_DELETE)
    public Response handleDelete(@Param(value = "id", required = true) String id) {
        if (storage == null) return emptyResponse(Response.NOT_FOUND);
        if (id == null || id.isEmpty()) return emptyResponse(Response.BAD_REQUEST);

        var value = storage.get(id);

        if (value != null) storage.delete(id);

        return emptyResponse(Response.ACCEPTED);
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    @ServiceFactory(stage = 2, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new HighLoadService(config);
        }
    }

}
