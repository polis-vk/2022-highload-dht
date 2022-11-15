package ok.dht.test.kazakov.service;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.kazakov.dao.Config;
import ok.dht.test.kazakov.dao.MemorySegmentDao;
import ok.dht.test.kazakov.service.http.DaoHttpServer;
import ok.dht.test.kazakov.service.validation.DaoRequestsValidatorBuilder;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class DaoWebService implements Service {

    private static final Logger LOG = LoggerFactory.getLogger(DaoWebService.class);
    private static final int FLUSH_THRESHOLD_BYTES = 2 * 1024 * 1024;

    private static final int ASYNC_EXECUTOR_THREADS = 64;
    private static final int EXECUTOR_SERVICE_QUEUE_CAPACITY = ASYNC_EXECUTOR_THREADS;
    public static final String ENTITY_API_PATH = "/v0/entity";

    private final ServiceConfig config;
    private final Clock clock;
    private volatile ExecutorService asyncExecutor;
    private volatile DaoHttpServer server;
    private volatile DaoService daoService;
    private volatile DaoRequestsValidatorBuilder daoRequestsValidatorBuilder;

    public DaoWebService(@Nonnull final ServiceConfig config) {
        this.config = config;
        this.clock = Clock.systemUTC();
    }

    @Override
    public CompletableFuture<?> start() {
        if (asyncExecutor == null) {
            synchronized (this) {
                if (asyncExecutor == null) {
                    asyncExecutor = DaoExecutorServiceHelper.createDiscardOldestThreadPool(
                            ASYNC_EXECUTOR_THREADS,
                            EXECUTOR_SERVICE_QUEUE_CAPACITY
                    );
                }
            }
        }

        return CompletableFuture.runAsync(() -> {
            final long measureTimeFrom = clock.millis();
            try {
                final MemorySegmentDao dao = new MemorySegmentDao(
                        new Config(config.workingDir(), FLUSH_THRESHOLD_BYTES));
                daoService = new DaoService(dao);
                daoRequestsValidatorBuilder = new DaoRequestsValidatorBuilder();

                server = new DaoHttpServer(DaoHttpServer.createConfigFromPort(config.selfPort()), asyncExecutor, this);
                server.addRequestHandler(ENTITY_API_PATH, Request.METHOD_GET, this::handleGet);
                server.addRequestHandler(ENTITY_API_PATH, Request.METHOD_PUT, this::handleUpsert);
                server.addRequestHandler(ENTITY_API_PATH, Request.METHOD_DELETE, this::handleDelete);
                server.start();
            } catch (final IOException e) {
                LOG.error("Unexpected IOException during DaoWebService.start()", e);
                throw new UncheckedIOException(e);
            }

            final long measureTimeTo = clock.millis();
            LOG.info("DaoWebService started in {}ms at {}", measureTimeTo - measureTimeFrom, config.selfUrl());
        }, asyncExecutor);
    }

    @Override
    public CompletableFuture<?> stop() {
        if (asyncExecutor == null) {
            throw new IllegalStateException("DaoWebService is closing before start");
        }

        return CompletableFuture.runAsync(() -> {
            final long measureTimeFrom = clock.millis();
            try {
                asyncExecutor.shutdownNow();
                server.stop();
                daoService.close();
                asyncExecutor = null;
            } catch (final IOException e) {
                LOG.error("Unexpected IOException during DaoWebService.stop()", e);
                throw new UncheckedIOException(e);
            }

            final long measureTimeTo = clock.millis();
            LOG.info("DaoWebService stopped in {}ms", measureTimeTo - measureTimeFrom);
        }, asyncExecutor);
    }

    private void handleGet(@Nonnull final Request request,
                           @Nonnull final HttpSession session) throws IOException {
        final String id = request.getParameter("id=");

        final DaoRequestsValidatorBuilder.Validator validator = daoRequestsValidatorBuilder.validate()
                .validateId(id);
        if (validator.isInvalid()) {
            session.sendResponse(new Response(Response.BAD_REQUEST, validator.getErrorMessage()));
            return;
        }

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

    private void handleUpsert(@Nonnull final Request request,
                              @Nonnull final HttpSession session) throws IOException {
        final String id = request.getParameter("id=");
        final byte[] value = request.getBody();

        final DaoRequestsValidatorBuilder.Validator validator = daoRequestsValidatorBuilder.validate()
                .validateId(id)
                .validateValue(value);
        if (validator.isInvalid()) {
            session.sendResponse(new Response(Response.BAD_REQUEST, validator.getErrorMessage()));
            return;
        }

        daoService.upsert(id, value);
        session.sendResponse(new Response(Response.CREATED, Response.EMPTY));
    }

    private void handleDelete(@Nonnull final Request request,
                              @Nonnull final HttpSession session) throws IOException {
        final String id = request.getParameter("id=");

        final DaoRequestsValidatorBuilder.Validator validator = daoRequestsValidatorBuilder.validate()
                .validateId(id);
        if (validator.isInvalid()) {
            session.sendResponse(new Response(Response.BAD_REQUEST, validator.getErrorMessage()));
            return;
        }

        daoService.delete(id);
        session.sendResponse(new Response(Response.ACCEPTED, Response.EMPTY));
    }

    @ServiceFactory(stage = 2, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(@Nonnull final ServiceConfig config) {
            return new DaoWebService(config);
        }
    }
}
