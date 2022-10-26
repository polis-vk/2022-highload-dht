package ok.dht.test.frolovm;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.frolovm.hasher.Hasher;
import one.nio.http.*;
import one.nio.server.AcceptorConfig;
import one.nio.util.Hash;
import one.nio.util.Utf8;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.impl.Iq80DBFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.http.HttpClient;
import java.util.concurrent.CompletableFuture;

public class ServiceImpl implements Service {

    private static final String PATH_ENTITY = "/v0/entity";
    private static final String PARAM_ID_NAME = "id=";

    private static final String PARAM_ACK_NAME = "ack";

    private static final String PARAM_FROM_NAME = "from";

    private static final int MAX_REQUEST_TRIES = 100;

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceImpl.class);
    private final ServiceConfig config;
    private final ShardingAlgorithm algorithm;
    private final HttpClient client = HttpClient.newHttpClient();
    private ReplicationManager replicationManager;
    private HttpServerImpl server;

    private DB dao;

    public ServiceImpl(ServiceConfig config) {
        this(config, Hash::murmur3);
    }

    public ServiceImpl(ServiceConfig config, Hasher hasher) {
        this.config = config;
        this.algorithm = new RendezvousHashing(config.clusterUrls(), hasher);
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = createAcceptorConfig(port);
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    private static AcceptorConfig createAcceptorConfig(int port) {
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        return acceptor;
    }

    private void createDao() throws IOException {
        this.dao = Iq80DBFactory.factory.open(config.workingDir().toFile(), new Options());
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        if (dao == null) {
            createDao();
        }
        server = new HttpServerImpl(createConfigFromPort(config.selfPort()));
        server.addRequestHandlers(this);
        server.start();
        this.replicationManager = new ReplicationManager(
                algorithm,
                config.selfUrl(),
                new RequestExecutor(dao),
                new CircuitBreakerImpl(MAX_REQUEST_TRIES, config.clusterUrls()),
                client
        );
        return CompletableFuture.completedFuture(null);
    }

    @Path(PATH_ENTITY)
    public Response entityHandler(@Param(PARAM_ID_NAME) String id, Request request, HttpSession session,
                                  @Param(PARAM_ACK_NAME) String ackParam, @Param(PARAM_FROM_NAME) String fromParam) {
        if (!Utils.checkId(id)) {
            return new Response(Response.BAD_REQUEST, Utf8.toBytes(Utils.BAD_ID));
        }
        int ackNum;
        int from;

        if (fromParam == null || ackParam == null) {
            from = algorithm.getShards().size();
            ackNum = from / 2 + 1;
        } else {
            from = Integer.parseInt(fromParam);
            ackNum = Integer.parseInt(ackParam);
        }

        if (validateAcks(ackParam, fromParam, ackNum, from)) {
            return Utils.emptyResponse(Response.BAD_REQUEST);
        }

        switch (request.getMethod()) {
            case Request.METHOD_PUT, Request.METHOD_GET, Request.METHOD_DELETE -> {
                return replicationManager.handle(id, request, ackNum, from);
            }
            default -> {
                LOGGER.info("Method is not allowed: " + request.getMethod());
                return new Response(Response.METHOD_NOT_ALLOWED, Utf8.toBytes(Utils.NO_SUCH_METHOD));
            }
        }
    }

    private boolean validateAcks(String ackParam, String fromParam, int ackNum, int from) {
        return ackNum <= 0 || from <= 0 || from > config.clusterUrls().size() || ackNum > from
                || (ackParam != null && fromParam == null) || (ackParam == null && fromParam != null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        if (server != null) {
            server.close();
            server = null;
        }
        replicationManager = null;
        if (dao != null) {
            dao.close();
        }
        dao = null;
        return CompletableFuture.completedFuture(null);
    }

    @ServiceFactory(stage = 4, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {
        @Override
        public Service create(ServiceConfig config) {
            return new ServiceImpl(config);
        }
    }
}
