package ok.dht.test.kazakov.service.ws;

import ok.dht.test.kazakov.dao.Entry;
import ok.dht.test.kazakov.service.DaoService;
import ok.dht.test.kazakov.service.LamportClock;
import ok.dht.test.kazakov.service.http.DaoHttpServer;
import ok.dht.test.kazakov.service.http.DaoHttpSession;
import ok.dht.test.kazakov.service.http.InternalHttpClient;
import ok.dht.test.kazakov.service.sharding.Shard;
import ok.dht.test.kazakov.service.sharding.ShardDeterminer;
import ok.dht.test.kazakov.service.validation.EntityRequestsValidatorBuilder;
import ok.dht.test.kazakov.service.ws.MostRelevantResponseHandler.ResponseHolder;
import one.nio.http.Request;
import one.nio.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;

public class EntityWebService {

    private static final Logger LOG = LoggerFactory.getLogger(EntityWebService.class);

    private static final String API_PATH = "/v0/entity";

    private final DaoService daoService;
    private final EntityRequestsValidatorBuilder entityRequestsValidatorBuilder;
    private final ShardDeterminer<String> shardDeterminer;
    private final InternalHttpClient internalHttpClient;
    private final LamportClock lamportClock;

    public EntityWebService(@Nonnull final DaoService daoService,
                            @Nonnull final EntityRequestsValidatorBuilder entityRequestsValidatorBuilder,
                            @Nonnull final ShardDeterminer<String> shardDeterminer,
                            @Nonnull final InternalHttpClient internalHttpClient,
                            @Nonnull final LamportClock lamportClock) {
        this.daoService = daoService;
        this.entityRequestsValidatorBuilder = entityRequestsValidatorBuilder;
        this.shardDeterminer = shardDeterminer;
        this.internalHttpClient = internalHttpClient;
        this.lamportClock = lamportClock;
    }

    public void configure(@Nonnull final DaoHttpServer server) {
        server.addRequestHandlers(
                API_PATH,
                new int[]{Request.METHOD_GET, Request.METHOD_PUT, Request.METHOD_DELETE},
                this::handleRequest
        );
    }

    // Sending internal request to another server in background, so future is ignored
    @SuppressWarnings("FutureReturnValueIgnored")
    private void handleRequest(@Nonnull final Request request,
                               @Nonnull final DaoHttpSession session) throws IOException {
        final String id = request.getParameter("id=");
        final String ackString = request.getParameter("ack=");
        final String fromString = request.getParameter("from=");
        final EntityRequestsValidatorBuilder.Validator validator = entityRequestsValidatorBuilder.validate()
                .validateId(id)
                .validateReplicas(ackString, fromString, shardDeterminer.getTotalShards());
        if (request.getMethod() == Request.METHOD_PUT) {
            validator.validateValue(request.getBody());
        }

        if (validator.isInvalid()) {
            session.sendResponse(new Response(Response.BAD_REQUEST, validator.getErrorMessage()));
            return;
        }

        final int from;
        final int ack;
        if (ackString == null && fromString == null) {
            from = shardDeterminer.getTotalShards();
            ack = from / 2 + 1;
        } else {
            from = validator.getParsedFrom();
            ack = validator.getParsedAck();
        }

        handleParsedRequest(request, session, id, from, ack);
    }

    private void handleParsedRequest(@Nonnull final Request request,
                                     @Nonnull final DaoHttpSession session,
                                     @Nonnull final String id,
                                     final int from,
                                     final int ack) {
        Shard shard = shardDeterminer.determineShard(id);
        boolean shouldDoSelfRequest = false;
        final MostRelevantResponseHandler responseHandler = new MostRelevantResponseHandler(ack, from);

        final long newEntryTimestamp = lamportClock.getValue();
        for (int i = 0; i < from; i++) {
            if (shard.isSelf()) {
                // send all internal requests first
                shouldDoSelfRequest = true;
            } else {
                doInternalRequest(request, session, id, shard, newEntryTimestamp, responseHandler);
            }

            shard = shardDeterminer.getNextShardToReplicate(shard);

            if (request.getMethod() == Request.METHOD_GET && responseHandler.isRespondedToClient()) {
                return;
            }
        }

        if (shouldDoSelfRequest) {
            doSelfRequest(request, session, id, newEntryTimestamp, responseHandler);
        }
    }

    private void doSelfRequest(@Nonnull final Request request,
                               @Nonnull final DaoHttpSession session,
                               @Nonnull final String id,
                               final long newEntryTimestamp,
                               @Nonnull final MostRelevantResponseHandler responseHandler) {
        LOG.debug("Handling self request for id={}", id);
        switch (request.getMethod()) {
            case Request.METHOD_GET -> {
                final Entry<?> response;
                try {
                    response = daoService.get(id);
                } catch (final IOException e) {
                    responseHandler.handleResponse(session, null, e);
                    return;
                }

                final String responseCode = response == null || response.isTombstone()
                        ? Response.NOT_FOUND
                        : Response.OK;
                responseHandler.handleResponse(session, new ResponseHolder(responseCode, response), null);
            }
            case Request.METHOD_PUT -> {
                daoService.upsert(id, request.getBody(), newEntryTimestamp);
                responseHandler.handleResponse(session, new ResponseHolder(Response.CREATED, null), null);
            }
            case Request.METHOD_DELETE -> {
                daoService.delete(id, newEntryTimestamp);
                responseHandler.handleResponse(session, new ResponseHolder(Response.ACCEPTED, null), null);
            }
            default -> throw new IllegalArgumentException("Unexpected method: " + request.getMethod());
        }
    }

    private void doInternalRequest(@Nonnull final Request request,
                                   @Nonnull final DaoHttpSession session,
                                   @Nonnull final String id,
                                   @Nonnull final Shard shard,
                                   final long newEntryTimestamp,
                                   @Nonnull final MostRelevantResponseHandler responseHandler) {
        internalHttpClient
                .resendDaoRequestToShard(request, id, shard, lamportClock.getValueToSend(), newEntryTimestamp)
                .thenAccept((httpResponse) -> {
                    if (request.getMethod() == Request.METHOD_GET) {
                        final String responseCode = InternalHttpClient.convertHttpStatusCode(httpResponse.statusCode());

                        final DaoService.LazyByteArrayEntry responseEntry =
                                DaoService.fromRawBytes(httpResponse.body());
                        lamportClock.getValueToReceive(responseEntry.getAdditionalValue());

                        responseHandler.handleResponse(
                                session,
                                new ResponseHolder(responseCode, responseEntry.isAbsent() ? null : responseEntry),
                                null
                        );
                    } else {
                        final String responseCode = InternalHttpClient.convertHttpStatusCode(httpResponse.statusCode());

                        final long responseTimestamp = DaoService.extractLongFromBytes(httpResponse.body(), 0);
                        lamportClock.getValueToReceive(responseTimestamp);

                        responseHandler.handleResponse(session, new ResponseHolder(responseCode, null), null);
                    }
                })
                .exceptionally((throwable) -> {
                    if (throwable != null) {
                        responseHandler.handleResponse(session, null, throwable);
                    }
                    return null;
                });
    }
}
