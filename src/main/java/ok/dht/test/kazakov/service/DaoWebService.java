package ok.dht.test.kazakov.service;

import ok.dht.test.kazakov.service.http.DaoHttpServer;
import ok.dht.test.kazakov.service.http.InternalHttpClient;
import ok.dht.test.kazakov.service.sharding.Shard;
import ok.dht.test.kazakov.service.sharding.ShardDeterminer;
import ok.dht.test.kazakov.service.validation.DaoRequestsValidatorBuilder;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;

public class DaoWebService {

    private static final Logger LOG = LoggerFactory.getLogger(DaoWebService.class);

    private static final String ENTITY_API_PATH = "/v0/entity";
    public static final String INTERNAL_ENTITY_API_PATH = "/v0/entity/internal";

    private final DaoService daoService;
    private final DaoRequestsValidatorBuilder daoRequestsValidatorBuilder;
    private final ShardDeterminer<String> shardDeterminer;
    private final InternalHttpClient internalHttpClient;

    public DaoWebService(@Nonnull final DaoService daoService,
                         @Nonnull final DaoRequestsValidatorBuilder daoRequestsValidatorBuilder,
                         @Nonnull final ShardDeterminer<String> shardDeterminer,
                         @Nonnull final InternalHttpClient internalHttpClient) {
        this.daoService = daoService;
        this.daoRequestsValidatorBuilder = daoRequestsValidatorBuilder;
        this.shardDeterminer = shardDeterminer;
        this.internalHttpClient = internalHttpClient;
    }

    public void configure(@Nonnull final DaoHttpServer server) {
        server.addRequestHandlers(
                ENTITY_API_PATH,
                new int[]{Request.METHOD_GET, Request.METHOD_PUT, Request.METHOD_DELETE},
                this::handleRequest
        );
        server.addRequestHandlers(
                INTERNAL_ENTITY_API_PATH,
                new int[]{Request.METHOD_GET, Request.METHOD_PUT, Request.METHOD_DELETE},
                this::handleInternalRequest
        );
    }

    // Sending internal request to another server in background, so future is ignored
    @SuppressWarnings("FutureReturnValueIgnored")
    private void handleRequest(@Nonnull final Request request,
                               @Nonnull final HttpSession session) throws IOException {
        final String id = request.getParameter("id=");
        final DaoRequestsValidatorBuilder.Validator validator = daoRequestsValidatorBuilder.validate()
                .validateId(id);
        if (request.getMethod() == Request.METHOD_PUT) {
            validator.validateValue(request.getBody());
        }

        if (validator.isInvalid()) {
            session.sendResponse(new Response(Response.BAD_REQUEST, validator.getErrorMessage()));
            return;
        }

        final Shard shard = shardDeterminer.determineShard(id);
        if (shard.isSelf()) {
            routeInternalRequest(request, session, id);
            return;
        }

        internalHttpClient
                .resendDaoRequestToShard(request, id, shard)
                .handleAsync((httpResponse, throwable) -> {
                    try {
                        if (throwable != null) {
                            LOG.error("Unexpected error on request resending", throwable);
                            session.sendResponse(new Response(Response.INTERNAL_ERROR, Response.EMPTY));
                        } else {
                            final String resultCode = Integer.toString(httpResponse.statusCode());
                            session.sendResponse(new Response(resultCode, httpResponse.body()));
                        }
                    } catch (final IOException e) {
                        LOG.error(
                                "Could not respond to {} {}?id={}",
                                DaoHttpServer.METHODS.get(request.getMethod()),
                                request.getURI(),
                                id,
                                e
                        );
                    }
                    return null;
                    // Using http client executor as callback performs only I/O operations
                }, internalHttpClient.getRequestExecutor());
    }

    private void handleInternalRequest(@Nonnull final Request request,
                                       @Nonnull final HttpSession session) throws IOException {
        // expecting only internal queries here, so skipping validation
        // authorization can be implemented to prohibit non-internal queries
        routeInternalRequest(request, session, request.getParameter("id="));
    }

    private void routeInternalRequest(@Nonnull final Request request,
                                      @Nonnull final HttpSession session,
                                      @Nonnull final String id) throws IOException {
        switch (request.getMethod()) {
            case Request.METHOD_GET -> handleInternalGet(session, id);
            case Request.METHOD_PUT -> handleInternalUpsert(request, session, id);
            case Request.METHOD_DELETE -> handleInternalDelete(session, id);
            default -> throw new IllegalArgumentException("Unexpected method: " + request.getMethod());
        }
    }

    private void handleInternalGet(@Nonnull final HttpSession session,
                                   @Nonnull final String id) throws IOException {
        final byte[] response;
        try {
            response = daoService.get(id);
        } catch (final IOException e) {
            LOG.error("IOException occurred during entity get request", e);
            session.sendResponse(new Response(Response.INTERNAL_ERROR, Response.EMPTY));
            return;
        }

        if (response == null) {
            session.sendResponse(new Response(Response.NOT_FOUND, Response.EMPTY));
        } else {
            session.sendResponse(new Response(Response.OK, response));
        }
    }

    private void handleInternalUpsert(@Nonnull final Request request,
                                      @Nonnull final HttpSession session,
                                      @Nonnull final String id) throws IOException {
        final byte[] value = request.getBody();

        daoService.upsert(id, value);
        session.sendResponse(new Response(Response.CREATED, Response.EMPTY));
    }

    private void handleInternalDelete(@Nonnull final HttpSession session,
                                      @Nonnull final String id) throws IOException {
        daoService.delete(id);
        session.sendResponse(new Response(Response.ACCEPTED, Response.EMPTY));
    }
}
