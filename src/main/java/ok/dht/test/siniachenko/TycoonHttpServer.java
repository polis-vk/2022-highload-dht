package ok.dht.test.siniachenko;

import ok.dht.test.siniachenko.service.TycoonService;
import one.nio.http.*;
import one.nio.net.Session;
import one.nio.server.AcceptorConfig;
import one.nio.server.SelectorThread;

import java.io.IOException;

public class TycoonHttpServer extends HttpServer {
    private static final int AVAILABLE_PROCESSORS = Runtime.getRuntime().availableProcessors();

    private final TycoonService service;
    private boolean closed = true;

    public TycoonHttpServer(TycoonService service, int port) throws IOException {
        super(createHttpConfigFromPort(port));
        this.service = service;
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
        service.handleRequest(request, session);
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        Response response = new Response(Response.BAD_REQUEST, Response.EMPTY);
        session.sendResponse(response);
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
