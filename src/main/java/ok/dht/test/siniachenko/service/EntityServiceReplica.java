package ok.dht.test.siniachenko.service;

import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.util.Utf8;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EntityServiceReplica implements EntityService {
    private static final Logger LOG = LoggerFactory.getLogger(EntityServiceReplica.class);

    public static final String ERROR_IN_DB_MESSAGE = "Error in DB";

    private final DB levelDb;

    public EntityServiceReplica(DB levelDb) {
        this.levelDb = levelDb;
    }

    public Response handleGet(Request request, String id) {
        try {
            byte[] value = levelDb.get(Utf8.toBytes(id));
            if (value == null) {
                return new Response(Response.NOT_FOUND, Response.EMPTY);
            }
            return new Response(Response.OK, value);
        } catch (DBException e) {
            LOG.error(ERROR_IN_DB_MESSAGE, e);
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }
    }

    public Response handlePut(Request request, String id) {
        byte[] body = request.getBody();
        try {
            levelDb.put(Utf8.toBytes(id), body);
            return new Response(Response.CREATED, Response.EMPTY);
        } catch (DBException e) {
            LOG.error(ERROR_IN_DB_MESSAGE, e);
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }
    }

    public Response handleDelete(Request request, String id) {
        byte[] body = request.getBody();
        try {
            levelDb.put(Utf8.toBytes(id), body);
            return new Response(Response.ACCEPTED, Response.EMPTY);
        } catch (DBException e) {
            LOG.error(ERROR_IN_DB_MESSAGE, e);
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }
    }
}
