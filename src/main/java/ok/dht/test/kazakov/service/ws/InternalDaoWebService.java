package ok.dht.test.kazakov.service.ws;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.kazakov.dao.Entry;
import ok.dht.test.kazakov.service.DaoService;
import ok.dht.test.kazakov.service.LamportClock;
import ok.dht.test.kazakov.service.http.DaoHttpServer;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;

public class InternalDaoWebService {

    private static final Logger LOG = LoggerFactory.getLogger(InternalDaoWebService.class);

    public static final String INTERNAL_ENTITY_API_PATH = "/v0/entity/internal";

    private final DaoService daoService;
    private final LamportClock lamportClock;

    public InternalDaoWebService(@Nonnull final DaoService daoService,
                                 @Nonnull final LamportClock lamportClock) {
        this.daoService = daoService;
        this.lamportClock = lamportClock;
    }

    public void configure(@Nonnull final DaoHttpServer server) {
        server.addRequestHandlers(
                INTERNAL_ENTITY_API_PATH,
                new int[]{Request.METHOD_GET, Request.METHOD_PUT, Request.METHOD_DELETE},
                this::handleInternalRequest
        );
    }

    private void handleInternalRequest(@Nonnull final Request request,
                                       @Nonnull final HttpSession session) throws IOException {
        // expecting only internal queries here, so skipping validation
        // authorization can be implemented to prohibit non-internal queries

        lamportClock.getValueToReceive(Long.parseLong(request.getParameter("timestamp=")));

        final String id = request.getParameter("id=");
        final long newEntryTimestamp = Long.parseLong(request.getParameter("newEntryTimestamp="));
        switch (request.getMethod()) {
            case Request.METHOD_GET -> handleInternalGet(session, id);
            case Request.METHOD_PUT -> handleInternalUpsert(request, session, id, newEntryTimestamp);
            case Request.METHOD_DELETE -> handleInternalDelete(session, id, newEntryTimestamp);
            default -> throw new IllegalArgumentException("Unexpected method: " + request.getMethod());
        }
    }

    private void handleInternalGet(@Nonnull final HttpSession session,
                                   @Nonnull final String id) throws IOException {
        final Entry<MemorySegment> responseEntry;
        try {
            responseEntry = daoService.get(id);
        } catch (final IOException e) {
            LOG.error("IOException occurred during entity get request", e);
            session.sendResponse(new Response(Response.INTERNAL_ERROR, Response.EMPTY));
            return;
        }

        final byte[] response = DaoService.toRawBytes(
                responseEntry,
                lamportClock.getValueToSend(),
                LamportClock.INITIAL_VALUE
        );
        final String resultCode = responseEntry == null || responseEntry.isTombstone()
                ? Response.NOT_FOUND
                : Response.OK;
        session.sendResponse(new Response(resultCode, response));
        LOG.debug("Successfully responded GET for key {}", id);
    }

    private void handleInternalUpsert(@Nonnull final Request request,
                                      @Nonnull final HttpSession session,
                                      @Nonnull final String id,
                                      final long timestamp) throws IOException {
        final byte[] value = request.getBody();

        daoService.upsert(id, value, timestamp);

        final byte[] responseBody = new byte[Long.BYTES];
        DaoService.putLongIntoBytes(responseBody, 0, lamportClock.getValueToSend());
        session.sendResponse(new Response(Response.CREATED, responseBody));
        LOG.debug("Successfully responded PUT for key {}", id);
    }

    private void handleInternalDelete(@Nonnull final HttpSession session,
                                      @Nonnull final String id,
                                      final long timestamp) throws IOException {
        daoService.delete(id, timestamp);

        final byte[] responseBody = new byte[Long.BYTES];
        DaoService.putLongIntoBytes(responseBody, 0, lamportClock.getValueToSend());
        session.sendResponse(new Response(Response.ACCEPTED, responseBody));
        LOG.debug("Successfully responded DELETE for key {}", id);
    }
}
