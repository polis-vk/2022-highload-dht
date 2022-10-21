package ok.dht.test.kovalenko;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.kovalenko.dao.LSMDao;
import ok.dht.test.kovalenko.dao.aliases.TypedBaseEntry;
import ok.dht.test.kovalenko.dao.aliases.TypedEntry;
import ok.dht.test.kovalenko.dao.base.ByteBufferDaoFactoryB;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class MyServiceBase implements Service {
    private static final ByteBufferDaoFactoryB daoFactory = new ByteBufferDaoFactoryB();
    private static final Logger log = LoggerFactory.getLogger(MyServiceBase.class);
    private static final LoadBalancer loadBalancer = new LoadBalancer();
    private static final List<Integer> ports = List.of(19234, 19235, 19236);
    protected static final List<String> urls = ports.stream().map(p -> "http://localhost:" + p).toList();
    protected static final List<ServiceConfig> configs = new ArrayList<>(urls.size());

    static {
        for (int i = 0; i < urls.size(); ++i) {
            try {
                int port = ports.get(i);
                String url = urls.get(i);
                ServiceConfig cfg = new ServiceConfig(
                        port,
                        url,
                        urls,
                        java.nio.file.Path.of("/home/pavel/IntelliJIdeaProjects/tables/shard" + (i + 1))
                );
                configs.add(cfg);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private final ServiceConfig config;
    private LSMDao dao;
    private HttpServer server;

    public MyServiceBase(ServiceConfig config) throws IOException {
        this.config = config;
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    public static Response emptyResponseForCode(String code) {
        return new Response(code, Response.EMPTY);
    }

    protected static void main(int ordinal) {
        try {
            MyServiceBase service = new MyServiceBase(configs.get(ordinal - 1));
            service.start().get(1, TimeUnit.SECONDS);
            log.info("Socket is ready: {}", service.selfUrl());
        } catch (Exception e) {
            log.error("Socket wasn't started: {}", urls.get(ordinal - 1), e);
        }
    }

    @Override
    public CompletableFuture<?> start() {
        try {
            this.dao = new LSMDao(this.config);
            this.server = new MyServerBase(createConfigFromPort(selfPort()));
            this.server.addRequestHandlers(this);
            loadBalancer.addUrls(config.clusterUrls());
            this.server.start();
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public CompletableFuture<?> stop() {
        try {
            this.dao.close();
            this.server.stop();
            loadBalancer.remove(selfUrl());
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Path("/v0/entity")
    public void handle(Request request, HttpSession session)
            throws IOException, ExecutionException, InterruptedException {
        loadBalancer.balance(this, request, session);
    }

    public Response handleGet(String id, Request request) throws IOException {
        ByteBuffer key = daoFactory.fromString(id);
        TypedEntry res = this.dao.get(key);
        if (res == null) {
            return emptyResponseForCode(Response.NOT_FOUND);
        } else {
            return Response.ok(daoFactory.toBytes(res.value()));
        }
    }

    public Response handlePut(String id, Request request) throws IOException {
        ByteBuffer key = daoFactory.fromString(id);
        ByteBuffer value = ByteBuffer.wrap(request.getBody());
        this.dao.upsert(new TypedBaseEntry(key, value));
        return emptyResponseForCode(Response.CREATED);
    }

    public Response handleDelete(String id, Request request) throws IOException {
        ByteBuffer key = daoFactory.fromString(id);
        this.dao.upsert(new TypedBaseEntry(key, null));
        return emptyResponseForCode(Response.ACCEPTED);
    }

    public String selfUrl() {
        return this.config.selfUrl();
    }

    public int selfPort() {
        return this.config.selfPort();
    }

    public LSMDao getDao() {
        return this.dao;
    }
}
