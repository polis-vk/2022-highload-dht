package ok.dht.test.siniachenko;

import ok.dht.test.siniachenko.service.EntityServiceReplica;
import ok.dht.test.siniachenko.service.TycoonService;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.AcceptorConfig;
import one.nio.server.SelectorThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class TycoonHttpServer extends HttpServer {
    private static final Logger LOG = LoggerFactory.getLogger(TycoonHttpServer.class);
    private static final int AVAILABLE_PROCESSORS = Runtime.getRuntime().availableProcessors();
    public static final String PATH = "/v0/entity";
    public static final String REQUEST_TO_REPLICA_HEADER = "Request-to-replica";

    private final TycoonService tycoonService;
    private final EntityServiceReplica entityServiceReplica;
    private boolean closed = true;

    public TycoonHttpServer(
        int port,
        TycoonService tycoonService,
        EntityServiceReplica entityServiceReplica
    ) throws IOException {
        super(createHttpConfigFromPort(port));
        this.tycoonService = tycoonService;
        this.entityServiceReplica = entityServiceReplica;
    }

    private static HttpServerConfig createHttpConfigFromPort(int port) {
        HttpServerConfig httpServerConfig = new HttpServerConfig();
        AcceptorConfig acceptorConfig = new AcceptorConfig();
        acceptorConfig.port = port;
        acceptorConfig.reusePort = true;
        httpServerConfig.acceptors = new AcceptorConfig[]{acceptorConfig};
        httpServerConfig.selectors = AVAILABLE_PROCESSORS;

        return httpServerConfig;
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        if (!PATH.equals(request.getPath())) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }

        String idParameter = request.getParameter("id=");
        if (idParameter == null || idParameter.isEmpty()) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }

        if (request.getHeader(REQUEST_TO_REPLICA_HEADER) == null) {
            tycoonService.executeRequest(request, session, idParameter);
        } else {
            entityServiceReplica.executeRequest(request, session, idParameter);
        }
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        sendResponse(session, new Response(Response.BAD_REQUEST, Response.EMPTY));
    }

    public static void sendResponse(HttpSession session, Response response) {
        try {
            session.sendResponse(response);
        } catch (IOException e1) {
            LOG.error("I/O error while sending response", e1);
            try {
                session.close();
            } catch (Exception e2) {
                e2.addSuppressed(e1);
                LOG.error("Exception while closing session", e2);
            }
        }
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public synchronized void start() {
        closed = false;
        super.start();
    }

    @Override
    public synchronized void stop() {
        for (SelectorThread selectorThread : selectors) {
            for (Session session : selectorThread.selector) {
                session.close();
            }
        }
        closed = true;
        super.stop();
    }
}