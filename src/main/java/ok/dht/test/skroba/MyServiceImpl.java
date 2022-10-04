package ok.dht.test.skroba;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.skroba.dao.MemorySegmentDao;
import ok.dht.test.skroba.dao.base.BaseEntry;
import ok.dht.test.skroba.dao.base.Config;
import ok.dht.test.skroba.dao.base.Entry;
import one.nio.http.HttpServer;
import one.nio.http.HttpSession;
import one.nio.http.Param;
import one.nio.http.Request;
import one.nio.http.RequestMethod;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.SelectorThread;
import one.nio.util.Utf8;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import static ok.dht.test.skroba.MyServiceUtils.isBadId;

public class MyServiceImpl implements Service {
    
    private static final int FLUSH_THRESHOLD_BYTES = 8 * 1048;
    
    private static final Logger LOG = LoggerFactory.getLogger(
            MyServiceImpl.class
    );
    
    private final ServiceConfig config;
    
    private HttpServer server;
    
    private MemorySegmentDao dao;
    
    public MyServiceImpl(ServiceConfig config) {
        this.config = config;
    }
    
    private static MemorySegmentDao createDaoFromDir(
            Path workingDir
    ) throws IOException {
        Config configDao = new Config(workingDir, FLUSH_THRESHOLD_BYTES);
        
        try {
            return new MemorySegmentDao(configDao);
        } catch (IOException err) {
            LOG.error("Error while creating database.\n" + err.getMessage());
            
            throw err;
        }
    }
    
    @Override
    public CompletableFuture<?> start() throws IOException {
        server = new HttpServer(MyServiceUtils.createConfigFromPort(config.selfPort())) {
            @Override
            public void handleDefault(
                    Request request,
                    HttpSession session
            ) throws IOException {
                session.sendResponse(MyServiceUtils.getEmptyResponse(Response.BAD_REQUEST));
            }
            
            @Override
            public synchronized void stop() {
                for (SelectorThread thread : selectors) {
                    thread.selector.forEach(Session::close);
                }
                
                super.stop();
            }
        };
        dao = createDaoFromDir(config.workingDir());
        server.addRequestHandlers(this);
        server.start();
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<?> stop() throws IOException {
        server.stop();
        return CompletableFuture.completedFuture(null);
    }
    
    @one.nio.http.Path("/v0/entity")
    @RequestMethod(Request.METHOD_GET)
    public Response handleGet(
            @Param(value = "id", required = true) String id
    ) {
        if (isBadId(id)) {
            return MyServiceUtils.getResponseOnBadId();
        }
        
        Entry<MemorySegment> entry = dao.get(
                MemorySegment.ofArray(Utf8.toBytes(id))
        );
        
        if (entry == null) {
            return MyServiceUtils.getResponse(
                    Response.NOT_FOUND,
                    "There's no entity with id: " + id
            );
        } else {
            return new Response(
                    Response.OK,
                    entry.value().toByteArray()
            );
        }
    }
    
    private Response upsert(
            String id,
            MemorySegment value,
            String onSuccessStatus
    ) {
        MemorySegment key = MemorySegment.ofArray(Utf8.toBytes(id));
        BaseEntry<MemorySegment> entry = new BaseEntry<>(key, value);
        
        try {
            dao.upsert(entry);
        } catch (RuntimeException err) {
            LOG.error("Error while upsert operation:\n " + err);
            return MyServiceUtils.getEmptyResponse(Response.INTERNAL_ERROR);
        }
        
        return MyServiceUtils.getEmptyResponse(onSuccessStatus);
    }
    
    @one.nio.http.Path("/v0/entity")
    @RequestMethod(Request.METHOD_PUT)
    public Response handlePut(
            @Param(value = "id", required = true) String id,
            Request request
    ) {
        
        return isBadId(id) ? MyServiceUtils.getResponseOnBadId() : upsert(
                id,
                MemorySegment.ofArray(request.getBody()),
                Response.CREATED
        );
    }
    
    @one.nio.http.Path("/v0/entity")
    @RequestMethod(Request.METHOD_DELETE)
    public Response handleDelete(
            @Param(value = "id", required = true) String id
    ) {
        return isBadId(id) ? MyServiceUtils.getResponseOnBadId() : upsert(
                id,
                null,
                Response.ACCEPTED
        );
    }
    
    @ServiceFactory(stage = 1, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {
        
        @Override
        public Service create(ServiceConfig config) {
            return new MyServiceImpl(config);
        }
    }
}
