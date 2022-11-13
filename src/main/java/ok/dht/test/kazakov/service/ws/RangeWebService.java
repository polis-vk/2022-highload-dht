package ok.dht.test.kazakov.service.ws;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.kazakov.dao.Entry;
import ok.dht.test.kazakov.service.DaoService;
import ok.dht.test.kazakov.service.http.DaoHttpServer;
import ok.dht.test.kazakov.service.http.DaoHttpSession;
import ok.dht.test.kazakov.service.validation.RangeRequestsValidatorBuilder;
import one.nio.http.Request;
import one.nio.http.Response;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Iterator;

public class RangeWebService {

    private static final String API_PATH = "/v0/entities";

    private final DaoService daoService;
    private final RangeRequestsValidatorBuilder rangeRequestsValidatorBuilder;

    public RangeWebService(@Nonnull final DaoService daoService,
                           @Nonnull final RangeRequestsValidatorBuilder rangeRequestsValidatorBuilder) {
        this.daoService = daoService;
        this.rangeRequestsValidatorBuilder = rangeRequestsValidatorBuilder;
    }

    public void configure(@Nonnull final DaoHttpServer server) {
        server.addRequestHandlers(
                API_PATH,
                new int[]{Request.METHOD_GET},
                this::handleRequest
        );
    }

    private void handleRequest(@Nonnull final Request request,
                               @Nonnull final DaoHttpSession session) throws IOException {
        final String start = request.getParameter("start=");
        final String end = request.getParameter("end=");
        final RangeRequestsValidatorBuilder.Validator validator = rangeRequestsValidatorBuilder.validate()
                .validateRange(start, end);

        if (validator.isInvalid()) {
            session.sendResponse(new Response(Response.BAD_REQUEST, validator.getErrorMessage()));
            return;
        }

        handleParsedRequest(session, start, end);
    }

    private void handleParsedRequest(@Nonnull final DaoHttpSession session,
                                     @Nonnull final String start,
                                     @Nullable final String end) throws IOException {
        final Iterator<Entry<MemorySegment>> entryIterator = daoService.get(start, end);
        session.sendChunkedResponse(Response.OK, entryIterator);
    }
}
