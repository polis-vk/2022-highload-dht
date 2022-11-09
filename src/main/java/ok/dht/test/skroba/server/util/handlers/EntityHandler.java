package ok.dht.test.skroba.server.util.handlers;

import ok.dht.test.skroba.client.MyClient;
import ok.dht.test.skroba.client.MyClientImpl;
import ok.dht.test.skroba.db.EntityDao;
import ok.dht.test.skroba.db.base.Entity;
import ok.dht.test.skroba.shard.Manager;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static ok.dht.test.skroba.server.util.AcceptedPaths.INTERNAL_ENTITY;

public final class EntityHandler extends AbstractEntityHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(EntityHandler.class);
    
    private static final int OK = 200;
    private static final int CREATED = 201;
    private static final int ACCEPTED = 202;
    private static final String NOT_ENOUGH_REPLICAS = "504 Not Enough Replicas";
    private static final String ACK = "ack=";
    private static final String FROM = "from=";
    
    private final MyClient client = new MyClientImpl();
    private final int defaultFrom;
    private final int defaultAck;
    
    
    public EntityHandler(final Manager manager, final EntityDao dao) {
        super(manager, dao);
        this.defaultFrom = manager.clusterSize();
        this.defaultAck = defaultFrom / 2 + 1;
    }
    
    @Override
    public void handle(final Request request, final HttpSession session, final String id) throws IOException {
        final String ackValue = request.getParameter(ACK);
        final String fromValue = request.getParameter(FROM);
        
        int ack, from;
        try {
            ack = ackValue == null ? defaultAck : Integer.parseInt(ackValue);
            from = fromValue == null ? defaultFrom : Integer.parseInt(fromValue);
        } catch (NumberFormatException e) {
            LOGGER.error("Wrong parameter of request ack/from");
            
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }
        
        if (ack <= 0 || ack > from || from > defaultFrom) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }
        
        final List<String> nodes = manager.getUrls(id, from);
        
        final long timestamp = Instant.now()
                .toEpochMilli();
        final Entity localEntity = new Entity(timestamp, request.getBody());
        final AtomicInteger ok = new AtomicInteger(0);
        final AtomicInteger handled = new AtomicInteger(0);
        final AtomicReference<Entity> entity = new AtomicReference<>();
        final Map<Integer, CompletableFuture<HttpResponse<byte[]>>> futures = new ConcurrentHashMap<>();
        
        
        for (int i = 0; i < nodes.size(); i++) {
            final int index = i;
            
            final CompletableFuture<HttpResponse<byte[]>> result = client.sendRequest(
                    nodes.get(index) + INTERNAL_ENTITY.getPath() + "?id=" + id, request.getMethod(),
                    localEntity.serialize()
            );
            
            futures.put(index, result);
            
            result.handleAsync((response, throwable) -> {
                try {
                    if (throwable != null) {
                        LOGGER.error("Fail to send request: ", throwable);
                        
                        handleNotEnoughReplicas(session, ok, handled, ack, from);
                        return null;
                    }
                    
                    futures.remove(index);
                    
                    switch (request.getMethod()) {
                        case Request.METHOD_GET -> {
                            if (response.statusCode() == OK) {
                                Entity gotEntity = Entity.deserialize(response.body());
                                
                                while (true) {
                                    Entity old = entity.get();
                                    
                                    if (old != null && old.compareTo(gotEntity) >= 0 || entity.compareAndSet(old,
                                            gotEntity)) {
                                        break;
                                    }
                                }
                            }
                            
                            final Entity current = entity.get();
                            
                            if (ok.incrementAndGet() == ack) {
                                if (current == null || current.isTombstone()) {
                                    session.sendResponse(new Response(Response.NOT_FOUND, Response.EMPTY));
                                } else {
                                    session.sendResponse(Response.ok(current.getValue()));
                                }
                                cancelFutures(futures);
                            }
                        }
                        
                        case Request.METHOD_PUT -> {
                            if (response.statusCode() == CREATED && ok.incrementAndGet() == ack) {
                                session.sendResponse(new Response(Response.CREATED, Response.EMPTY));
                                cancelFutures(futures);
                            }
                        }
                        
                        case Request.METHOD_DELETE -> {
                            if (response.statusCode() == ACCEPTED && ok.incrementAndGet() == ack) {
                                session.sendResponse(new Response(Response.ACCEPTED, Response.EMPTY));
                                cancelFutures(futures);
                            }
                        }
                        
                        default -> throw new IllegalStateException("Unreachable state!");
                    }
                    
                    handleNotEnoughReplicas(session, ok, handled, ack, from);
                } catch (IOException e) {
                    LOGGER.error("Fail to send response: ", e);
                }
                
                
                return null;
            });
            
        }
    }
    
    private static void handleNotEnoughReplicas(
            HttpSession session,
            AtomicInteger ok,
            AtomicInteger handled,
            int ack,
            int from
    ) throws IOException {
        if (handled.incrementAndGet() == from && ok.get() < ack) {
            session.sendResponse(new Response(NOT_ENOUGH_REPLICAS, Response.EMPTY));
        }
    }
    
    private static void cancelFutures(final Map<Integer, CompletableFuture<HttpResponse<byte[]>>> futures) {
        futures.forEach((key, future) -> future.cancel(false));
    }
}
