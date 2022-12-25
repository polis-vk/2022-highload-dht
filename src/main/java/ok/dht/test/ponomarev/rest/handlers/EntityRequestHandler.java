package ok.dht.test.ponomarev.rest.handlers;

import java.io.IOException;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.ponomarev.dao.MemorySegmentDao;
import ok.dht.test.ponomarev.dao.TimestampEntry;
import ok.dht.test.ponomarev.dao.Utils;
import ok.dht.test.ponomarev.rest.conf.ServerConfiguration;
import ok.dht.test.ponomarev.rest.consts.DefaultResponse;
import one.nio.http.HttpSession;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.RequestHandler;
import one.nio.http.RequestMethod;
import one.nio.http.Response;
import one.nio.http.VirtualHost;

@VirtualHost(EntityRequestHandler.ROUTER_NAME)
public class EntityRequestHandler implements RequestHandler {
    public static final String ROUTER_NAME = "ENTITY";

    private final MemorySegmentDao dao;

    public EntityRequestHandler(MemorySegmentDao dao) {
        this.dao = dao;
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {}

    @RequestMethod(Request.METHOD_GET)
    @Path(ServerConfiguration.V_0_ENTITY_ENDPOINT)
    public Response getById(@Param(value = "id", required = true) String id) {
        if (id.isEmpty()) {
            return DefaultResponse.BAD_REQUEST;
        }

        try {
            final TimestampEntry entry = dao.get(
                Utils.toMemorySegment(id)
            );

            if (entry == null) {
                return DefaultResponse.NOT_FOUND;
            }

            return new Response(
                Response.OK,
                //TODO: Все ли тут ок?
                entry.value().asByteBuffer().array()
            );
        } catch (Exception e) {
            return DefaultResponse.SERVICE_UNAVAILABLE;
        }
    }

    // Это PUT и мы знаем ID, то так же его можно использовать для создания 
    // записи. Почему клиенту известен ID при создании, он должен аллоцироваться на сервере.
    @RequestMethod(Request.METHOD_PUT)
    @Path(ServerConfiguration.V_0_ENTITY_ENDPOINT)
    public Response put(Request request, @Param(value = "id", required = true) String id) {
        if (id.isEmpty()) {
            return DefaultResponse.BAD_REQUEST;
        }

        try {
            final byte[] body = request.getBody();
            if (body == null) {
                return DefaultResponse.BAD_REQUEST;
            }

            dao.upsert(
                new TimestampEntry(
                    Utils.toMemorySegment(id),
                    MemorySegment.ofArray(body),
                    System.currentTimeMillis()
                )
            );

            return DefaultResponse.CREATED;
        } catch (Exception e) {
            return DefaultResponse.SERVICE_UNAVAILABLE;
        }
    }

    @RequestMethod(Request.METHOD_DELETE)
    @Path(ServerConfiguration.V_0_ENTITY_ENDPOINT)
    public Response delete(@Param(value = "id", required = true) String id) {
        if (id.isEmpty()) {
            return DefaultResponse.BAD_REQUEST;
        }

        try {
            dao.upsert(
                new TimestampEntry(
                    Utils.toMemorySegment(id),
                    null,
                    System.currentTimeMillis()
                )
            );

            return DefaultResponse.ACCEPTED;
        } catch (Exception e) {
            return DefaultResponse.SERVICE_UNAVAILABLE;
        }
    }
}
