package ok.dht.test.gerasimov;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.ServiceConfig;
import ok.dht.test.gerasimov.lsm.Config;
import ok.dht.test.gerasimov.lsm.Dao;
import ok.dht.test.gerasimov.lsm.Entry;
import ok.dht.test.gerasimov.lsm.artyomdrozdov.MemorySegmentDao;
import one.nio.http.*;
import one.nio.net.Session;
import one.nio.server.AcceptorConfig;
import one.nio.server.SelectorThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Server extends HttpServer {
    private static final Logger LOG = LoggerFactory.getLogger(Server.class);
    private static final int FLUSH_THRESHOLD_BYTES = 4194304;
    private static final int DEFAULT_THREAD_POOL_SIZE = 16;

    private final ServiceConfig serviceConfig;
    private final ExecutorService executorService;

    private Dao<MemorySegment, Entry<MemorySegment>> dao;
    public Server(ServiceConfig serviceConfig) throws IOException {
        super(createHttpServerConfig(serviceConfig.selfPort()));
        this.serviceConfig = serviceConfig;
        this.executorService = Executors.newFixedThreadPool(DEFAULT_THREAD_POOL_SIZE);
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        Response response = new Response(Response.BAD_REQUEST, Response.EMPTY);
        session.sendResponse(response);
    }

    @Override
    public synchronized void start() {
        try {
            this.dao = new MemorySegmentDao(
                    new Config(serviceConfig.workingDir(), FLUSH_THRESHOLD_BYTES)
            );
            super.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public synchronized void stop() {
        try {
            for (SelectorThread thread : selectors) {
                for (Session session : thread.selector) {
                    session.socket().close();
                }
            }
            super.stop();
            dao.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        executorService.execute(() -> {
            try {
                super.handleRequest(request, session);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static HttpServerConfig createHttpServerConfig(int port) {
        HttpServerConfig httpServerConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();

        acceptor.port = port;
        acceptor.reusePort = true;
        httpServerConfig.acceptors = new AcceptorConfig[]{acceptor};

        return httpServerConfig;
    }
}
