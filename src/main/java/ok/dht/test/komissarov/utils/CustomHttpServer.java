package ok.dht.test.komissarov.utils;

import ok.dht.ServiceConfig;
import ok.dht.test.komissarov.CourseService;
import ok.dht.test.komissarov.database.exceptions.BadParamException;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CustomHttpServer extends HttpServer {

    private static final String PATH = "/v0/entity";
    private static final String ID_PARAM = "id=";
    private static final String ACK = "ack=";
    private static final String FROM = "from=";
    private static final String REPEATED = "Repeated";

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomHttpServer.class);

    private final CourseService service;
    private final int size;

    private final ExecutorService nodeWorkers = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors() - 1
    );

    public CustomHttpServer(ServiceConfig config, CourseService service) throws IOException {
        super(createConfigFromPort(config.selfPort()));
        this.service = service;
        this.size = config.clusterUrls().size();
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        String path = request.getPath();
        if (!path.equals(PATH)) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }

        String id = request.getParameter(ID_PARAM);
        if (id == null || id.isEmpty()) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }

        PairParams params;
        try {
            params = parseParam(request);
        } catch (BadParamException e) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }
        handle(request, session, id, params);
    }

    @Override
    public synchronized void stop() {
        nodeWorkers.shutdown();
        super.stop();
        for (SelectorThread thread : selectors) {
            thread.selector.forEach(Session::close);
        }
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    private void handle(Request request, HttpSession session, String id, PairParams params) {
            nodeWorkers.execute(() -> {
                try {
                    Response response;
                    if (request.getHeader(REPEATED) == null) {
                        response = service.executeRequests(request, id, params);
                    } else {
                        response = service.executeSoloRequest(request, id);
                    }
                    send(session, response);
                } catch (Exception e) {
                    LOGGER.error("Unavailable error", e);
                    send(session, new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY));
                }
            });
    }

    private void send(HttpSession session, Response response) {
        try {
            session.sendResponse(response);
        } catch (IOException e) {
            LOGGER.error("Send response error", e);
            session.close();
        }
    }

    private PairParams parseParam(Request request) {
        String ackStr = request.getParameter(ACK);
        String fromStr = request.getParameter(FROM);

        int clusterSize = size;
        if (ackStr != null && fromStr != null) {
            try {
                int ack = Integer.parseInt(request.getParameter(ACK));
                int from = Integer.parseInt(request.getParameter(FROM));

                if (ack == 0 || from > clusterSize || ack > from) {
                    throw new BadParamException("Incorrect parameters");
                }
                return new PairParams(ack, from);
            } catch (NumberFormatException e) {
                LOGGER.error("Not a number");
                throw new BadParamException("Wrong number format", e);
            }
        }
        return new PairParams(quorum(clusterSize), clusterSize);
    }

    private int quorum(int from) {
        return from / 2 + 1;
    }

}
