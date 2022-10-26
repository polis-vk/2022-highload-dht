package ok.dht.test.kurdyukov.http;

import ok.dht.test.kurdyukov.dao.model.DaoEntry;
import ok.dht.test.kurdyukov.dao.repository.DaoRepository;
import ok.dht.test.kurdyukov.sharding.Sharding;
import ok.dht.test.kurdyukov.utils.ObjectMapper;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.SelectorThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.nio.channels.ClosedSelectorException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class HttpShardServer extends HttpServer {
    private static final Logger logger = LoggerFactory.getLogger(HttpShardServer.class);
    private static final int AWAIT_TERMINATE_SECONDS = 1;
    private static final int OK = 200;
    private static final int CREATED = 201;
    private static final int ACCEPTED = 202;
    private static final int NOT_FOUND = 404;
    private static final Set<Integer> supportMethods = Set.of(
            Request.METHOD_GET,
            Request.METHOD_PUT,
            Request.METHOD_DELETE
    );
    private static final String ENDPOINT = "/v0/entity";
    private static final String NOT_ENOUGH_REPLICAS = "504 Not Enough Replicas";

    private final int clusterSize;
    private final HttpClientDao clientDao = new HttpClientDao();
    private final DaoRepository daoRepository;
    private final ExecutorService executorService;
    private final Sharding sharding;

    public HttpShardServer(
            HttpServerConfig config,
            List<String> urls,
            DaoRepository daoRepository,
            ExecutorService executorService,
            Sharding sharding,
            Object... routers
    ) throws IOException {
        super(config, routers);
        this.clusterSize = urls.size();
        this.daoRepository = daoRepository;
        this.executorService = executorService;
        this.sharding = sharding;
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        if (!request.getPath().equals(ENDPOINT)) {
            session.sendResponse(responseEmpty(Response.BAD_REQUEST));
            return;
        }

        int method = request.getMethod();

        if (!supportMethods.contains(method)) {
            session.sendResponse(responseEmpty(Response.METHOD_NOT_ALLOWED));
            return;
        }

        String id = request.getParameter("id=");

        if (id == null || id.isBlank()) {
            session.sendResponse(responseEmpty(Response.BAD_REQUEST));
            return;
        }

        try {
            executorService.execute(() -> doExecute(request, session, method, id));
        } catch (RejectedExecutionException e) {
            logger.warn("Reject request", e);
            session.sendResponse(responseEmpty(Response.SERVICE_UNAVAILABLE));
        }
    }

    @Override
    public synchronized void stop() {
        try {
            for (SelectorThread thread : selectors) {
                thread.selector.forEach(Session::close);
            }
        } catch (ClosedSelectorException e) {
            logger.error("Sockets were closed.", e);
        }

        super.stop();
        executorService.shutdown();

        try {
            if (executorService.awaitTermination(AWAIT_TERMINATE_SECONDS, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.error("Fail stopping thread pool workers", e);
            Thread.currentThread().interrupt();
        }

        daoRepository.close();
    }

    private void doExecute(Request request, HttpSession session, int method, String id) {
        try {
            boolean fromClusterRequest = request.getHeader(HttpClientDao.HEADER_NAME) != null;

            if (fromClusterRequest) {
                handleRequestInCluster(request, session, method, id);
                return;
            }

            String fromParam = request.getParameter("from=");
            String ackParam = request.getParameter("ack=");

            int from = (fromParam != null) ? Integer.parseInt(fromParam) : clusterSize;
            int ack = (ackParam != null) ? Integer.parseInt(ackParam) : (from + 1) / 2;

            if (ack <= 0 || ack > from) {
                session.sendResponse(responseEmpty(Response.BAD_REQUEST));
                return;
            }

            final List<String> urlsNode = sharding.getClusterUrlsByCount(id, from);

            List<CompletableFuture<HttpResponse<byte[]>>> multicast = multicast(request, urlsNode, method, id);

            ArrayList<HttpResponse<byte[]>> responses = new ArrayList<>();

            for (var res : multicast) {
                try {
                    responses.add(res.join());
                } catch (Exception e) {
                    logger.error("Fail connect with node", e);
                }
            }

            switch (request.getMethod()) {
                case Request.METHOD_GET -> {
                    if (ack <= responses.stream()
                            .filter(r -> r.statusCode() == OK || r.statusCode() == NOT_FOUND).count()) {
                        DaoEntry result = new DaoEntry(Instant.MIN, null);

                        for (var res : responses) {
                            try {
                                DaoEntry current = ObjectMapper.deserialize(res.body());
                                if (current != null) {
                                    if (result.compareTo(current) < 0) {
                                        result = current;
                                    }
                                }
                            } catch (ClassNotFoundException | IOException e) {
                                logger.error("Fail deserialize body!", e);
                            }
                        }

                        if (result.value != null) {
                            session.sendResponse(Response.ok(result.value));
                        } else {
                            session.sendResponse(responseEmpty(Response.NOT_FOUND));
                        }
                    } else {
                        session.sendResponse(responseEmpty(NOT_ENOUGH_REPLICAS));
                    }
                }
                case Request.METHOD_PUT -> {
                    if (ack <= responses.stream().filter(r -> r.statusCode() == CREATED).count()) {
                        session.sendResponse(responseEmpty(Response.CREATED));
                    } else {
                        session.sendResponse(responseEmpty(NOT_ENOUGH_REPLICAS));
                    }
                }
                case Request.METHOD_DELETE -> {
                    if (ack <= responses.stream().filter(r -> r.statusCode() == ACCEPTED).count()) {
                        session.sendResponse(responseEmpty(Response.ACCEPTED));
                    } else {
                        session.sendResponse(responseEmpty(NOT_ENOUGH_REPLICAS));
                    }
                }
            }
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private List<CompletableFuture<HttpResponse<byte[]>>> multicast(
            Request request,
            List<String> urlsNode,
            int method,
            String id
    ) throws URISyntaxException {
        final Instant timestamp = Instant.now();

        return urlsNode
                .stream()
                .map(urlNode -> clientDao
                        .requestNode(
                                String.format("%s%s%s", urlNode, ENDPOINT, "?id=" + id),
                                method,
                                new DaoEntry(timestamp, request.getBody())
                        )
                )
                .collect(Collectors.toList());
    }

    private void handleRequestInCluster(
            Request request,
            HttpSession session,
            int method,
            String id
    ) throws IOException {
        switch (method) {
            case Request.METHOD_GET -> {
                byte[] entry = daoRepository.get(id);

                if (entry == null) {
                    session.sendResponse(responseEmpty(Response.NOT_FOUND));
                } else {
                    session.sendResponse(new Response(Response.ok(entry)));
                }
            }
            case Request.METHOD_PUT -> {
                daoRepository.put(id, request.getBody());
                session.sendResponse(responseEmpty(Response.CREATED));
            }
            case Request.METHOD_DELETE -> {
                daoRepository.put(id, request.getBody());
                session.sendResponse(responseEmpty(Response.ACCEPTED));
            }
        }
    }

    private static Response responseEmpty(String status) {
        return new Response(status, Response.EMPTY);
    }
}
