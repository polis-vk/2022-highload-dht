package ok.dht.test.kovalenko;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.kovalenko.dao.LSMDao;
import ok.dht.test.kovalenko.dao.aliases.TypedBaseEntry;
import ok.dht.test.kovalenko.dao.aliases.TypedBaseTimedEntry;
import ok.dht.test.kovalenko.dao.aliases.TypedTimedEntry;
import ok.dht.test.kovalenko.dao.base.ByteBufferDaoFactoryB;
import ok.dht.test.kovalenko.utils.ReplicasUtils;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class MyServiceBase implements Service {
    private static final ByteBufferDaoFactoryB daoFactory = new ByteBufferDaoFactoryB();
    private static final Logger log = LoggerFactory.getLogger(MyServiceBase.class);
    // One LoadBalancer per Service, one Service per Server
    private final LoadBalancer loadBalancer = new LoadBalancer();
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

    public static Response emptyResponseFor(String code) {
        return new Response(code, Response.EMPTY);
    }

    @Override
    public CompletableFuture<?> start() {
        try {
            this.dao = new LSMDao(this.config);
            this.server = new MyServerBase(createConfigFromPort(selfPort()));
            this.server.addRequestHandlers(this);
            loadBalancer.add(this);
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
            loadBalancer.remove(this);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Path("/v0/entity")
    public void handle(Request request, HttpSession session)
            throws IOException, ExecutionException, InterruptedException {
        MyHttpSession myHttpSession = (MyHttpSession) session;
        ReplicasUtils.Replicas replicas = ReplicasUtils.recreate(myHttpSession.getReplicas(), clusterUrls().size());
        myHttpSession.setReplicas(replicas);
        loadBalancer.balance(this, myHttpSession.getRequestId(), request, myHttpSession);
    }

    public Response handleGet(String id, Request request, MyHttpSession session) throws IOException {
        ByteBuffer key = daoFactory.fromString(id);
        TypedBaseEntry found = TypedBaseTimedEntry.withoutTime(this.dao.get(key));
        return found == null
                ? emptyResponseFor(Response.NOT_FOUND)
                : Response.ok(daoFactory.toBytes(found.value()));
    }

    public Response handlePut(String id, Request request, MyHttpSession session) throws IOException {
        ByteBuffer key = daoFactory.fromString(id);
        ByteBuffer value = ByteBuffer.wrap(request.getBody());
        this.dao.upsert(entryFor(key, value));
        return emptyResponseFor(Response.CREATED);
    }

    public Response handleDelete(String id, Request request, MyHttpSession session) throws IOException {
        ByteBuffer key = daoFactory.fromString(id);
        this.dao.upsert(entryFor(key, null));
        return emptyResponseFor(Response.ACCEPTED);
    }

    private static TypedTimedEntry entryFor(ByteBuffer key, ByteBuffer value) {
        return new TypedBaseTimedEntry(System.currentTimeMillis(), key, value);
    }

    public String selfUrl() {
        return this.config.selfUrl();
    }

    public int selfPort() {
        return this.config.selfPort();
    }

    public List<String> clusterUrls() {
        return this.config.clusterUrls();
    }

    public LSMDao getDao() {
        return this.dao;
    }
}
