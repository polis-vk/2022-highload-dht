package ok.dht.test.kazakov.service;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.kazakov.dao.Config;
import ok.dht.test.kazakov.dao.MemorySegmentDao;
import ok.dht.test.kazakov.service.http.DaoHttpServer;
import ok.dht.test.kazakov.service.validation.DaoRequestsValidatorBuilder;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.RequestMethod;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class DaoWebService implements Service {

    private static final Logger LOG = LoggerFactory.getLogger(DaoWebService.class);
    private static final int ASYNC_EXECUTOR_THREADS = 1;
    private static final int FLUSH_THRESHOLD_BYTES = 32 * 1024;

    private final ServiceConfig config;
    private final Clock clock;
    private volatile ExecutorService asyncExecutor;
    private volatile HttpServer server;
    private volatile DaoService daoService;
    private volatile DaoRequestsValidatorBuilder daoRequestsValidatorBuilder;

    public DaoWebService(@Nonnull final ServiceConfig config) {
        this.config = config;
        this.clock = Clock.systemUTC();
    }

    private CompletableFuture<?> runOnAsyncExecutor(@Nonnull final Runnable runnable) {
        if (asyncExecutor == null) {
            synchronized (this) {
                if (asyncExecutor == null) {
                    asyncExecutor = Executors.newFixedThreadPool(ASYNC_EXECUTOR_THREADS,
                            new DaoWebServiceThreadFactory());
                }
            }
        }

        return CompletableFuture.runAsync(runnable, asyncExecutor);
    }

    @Override
    public CompletableFuture<?> start() {
        return runOnAsyncExecutor(() -> {
            final long measureTimeFrom = clock.millis();
            try {
                final MemorySegmentDao dao = new MemorySegmentDao(
                        new Config(config.workingDir(), FLUSH_THRESHOLD_BYTES)
                );
                daoService = new DaoService(dao);
                daoRequestsValidatorBuilder = new DaoRequestsValidatorBuilder();

                server = new DaoHttpServer(createConfigFromPort(config.selfPort()));
                server.start();
                server.addRequestHandlers(this);
            } catch (final IOException e) {
                LOG.error("Unexpected IOException during DaoWebService.start()", e);
                throw new UncheckedIOException(e);
            }

            final long measureTimeTo = clock.millis();
            LOG.info("DaoWebService started in {}ms", measureTimeTo - measureTimeFrom);
        });
    }

    @Override
    public CompletableFuture<?> stop() {
        return runOnAsyncExecutor(() -> {
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
        });
    }

    private static HttpServerConfig createConfigFromPort(final int port) {
        final HttpServerConfig httpConfig = new HttpServerConfig();
        final AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    @Path("/v0/entity")
    public Response handleRequest(@Nonnull final Request request) {
        final String id = request.getParameter("id=");

        return switch (request.getMethod()) {
            case Request.METHOD_GET -> handleGet(id);
            case Request.METHOD_PUT -> handlePut(id, request);
            case Request.METHOD_DELETE -> handleDelete(id);
            default -> new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
        };
    }

    public Response handleGet(@Nonnull final String id) {
        final DaoRequestsValidatorBuilder.Validator validator = daoRequestsValidatorBuilder.validate()
                .validateId(id);
        if (validator.isInvalid()) {
            return new Response(Response.BAD_REQUEST, validator.getErrorMessage());
        }

        final byte[] response;
        try {
            response = daoService.get(id);
        } catch (final IOException e) {
            LOG.error("IOException occurred during entity get request", e);
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }

        if (response == null) {
            return new Response(Response.NOT_FOUND, Response.EMPTY);
        } else {
            return new Response(Response.OK, response);
        }
    }

    public Response handlePut(@Nullable final String id,
                              @Nonnull final Request request) {
        final byte[] value = request.getBody();

        final DaoRequestsValidatorBuilder.Validator validator = daoRequestsValidatorBuilder.validate()
                .validateId(id)
                .validateValue(value);
        if (validator.isInvalid()) {
            return new Response(Response.BAD_REQUEST, validator.getErrorMessage());
        }

        daoService.upsert(id, value);
        return new Response(Response.CREATED, Response.EMPTY);
    }

    public Response handleDelete(@Nullable final String id) {
        final DaoRequestsValidatorBuilder.Validator validator = daoRequestsValidatorBuilder.validate()
                .validateId(id);
        if (validator.isInvalid()) {
            return new Response(Response.BAD_REQUEST, validator.getErrorMessage());
        }

        daoService.delete(id);
        return new Response(Response.ACCEPTED, Response.EMPTY);
    }

    private static class DaoWebServiceThreadFactory implements ThreadFactory {

        private final AtomicInteger threadsCreated = new AtomicInteger(1);

        @Override
        public Thread newThread(@Nonnull final Runnable target) {
            return new Thread(target, "DaoWebServiceAsyncExecutor#" + threadsCreated.getAndIncrement());
        }
    }

    @ServiceFactory(stage = 1, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(@Nonnull final ServiceConfig config) {
            return new DaoWebService(config);
        }
    }
}
