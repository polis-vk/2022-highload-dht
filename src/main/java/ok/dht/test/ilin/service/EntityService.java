package ok.dht.test.ilin.service;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.ilin.config.ConsistentHashingConfig;
import ok.dht.test.ilin.config.ExpandableHttpServerConfig;
import ok.dht.test.ilin.hashing.impl.ConsistentHashing;
import ok.dht.test.ilin.servers.ExpandableHttpServer;
import ok.dht.test.ilin.sharding.ShardHandler;
import one.nio.http.HttpServer;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.RequestMethod;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public class EntityService implements Service {
    private final ServiceConfig config;
    private HttpServer server;
    private RocksDB rocksDB;
    private final Logger logger = LoggerFactory.getLogger(EntityService.class);

    public EntityService(ServiceConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        RocksDB.loadLibrary();
        final Options options = new Options();
        options.setCreateIfMissing(true);
        try {
            this.rocksDB = RocksDB.open(options, config.workingDir().toString());
        } catch (RocksDBException ex) {
            logger.error("Error initializing RocksDB");
            throw new IOException(ex);
        }
        ConsistentHashingConfig consistentHashingConfig = new ConsistentHashingConfig();
        ConsistentHashing consistentHashing = new ConsistentHashing(config.clusterUrls(), consistentHashingConfig);
        ShardHandler shardHandler = new ShardHandler(config.selfUrl(), consistentHashing);
        server = new ExpandableHttpServer(shardHandler, createConfigFromPort(config.selfPort()));
        server.addRequestHandlers(this);
        server.start();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        rocksDB.close();
        server.stop();
        return CompletableFuture.completedFuture(null);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_GET)
    public Response getEntity(@Param(value = "id", required = true) String id) {
        if (id.isEmpty()) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }
        try {
            byte[] result = rocksDB.get(id.getBytes(StandardCharsets.UTF_8));
            if (result == null) {
                return new Response(Response.NOT_FOUND, Response.EMPTY);
            }
            return new Response(Response.OK, result);
        } catch (RocksDBException e) {
            logger.error("Error get entry in RocksDB");
        }
        return new Response(Response.NOT_FOUND, Response.EMPTY);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_PUT)
    public Response upsertEntity(@Param(value = "id", required = true) String id, Request request) {
        byte[] body = request.getBody();
        if (id.isEmpty() || body == null) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }
        try {
            rocksDB.put(id.getBytes(StandardCharsets.UTF_8), body);
        } catch (RocksDBException e) {
            logger.error("Error saving entry in RocksDB");
        }
        return new Response(Response.CREATED, Response.EMPTY);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_DELETE)
    public Response deleteEntity(@Param(value = "id", required = true) String id) {
        if (id.isEmpty()) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }
        try {
            rocksDB.delete(id.getBytes(StandardCharsets.UTF_8));
        } catch (RocksDBException e) {
            logger.error("Error delete entry in RocksDB");
        }
        return new Response(Response.ACCEPTED, Response.EMPTY);
    }

    private static ExpandableHttpServerConfig createConfigFromPort(int port) {
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        ExpandableHttpServerConfig httpConfig = new ExpandableHttpServerConfig();
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        httpConfig.selectors = 2;
        return httpConfig;
    }

    @ServiceFactory(stage = 3, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new EntityService(config);
        }
    }
}
