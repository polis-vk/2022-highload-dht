package ok.dht.test.siniachenko.service;

import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.util.Utf8;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.RejectedExecutionException;

public class EntityServiceReplica {
    private static final Logger LOG = LoggerFactory.getLogger(EntityServiceReplica.class);

    private final DB levelDb;

    public EntityServiceReplica(DB levelDb) {
        this.levelDb = levelDb;
    }

    public void executeRequest(Request request, HttpSession session, String id) {
        try {
            Response response = switch (request.getMethod()) {
                case Request.METHOD_GET -> getEntity(id);
                case Request.METHOD_PUT -> upsertEntity(id, request.getBody());
                case Request.METHOD_DELETE -> deleteEntity(id);
                default -> new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
            };
            TycoonService.sendResponse(session, response);
        } catch (RejectedExecutionException e) {
            LOG.error("Cannot execute task", e);
            TycoonService.sendResponse(session, new Response(Response.INTERNAL_ERROR, Response.EMPTY));
        }
    }

    public Response getEntity(String id) {
        try {
            byte[] value = levelDb.get(Utf8.toBytes(id));
            boolean deleted = TycoonService.readFlagDeletedFromBytes(value);
            if (deleted) {
                return new Response(Response.GONE, value);
            }
            return new Response(Response.OK, value);
        } catch (DBException e) {
            LOG.error("Error in DB", e);
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }
    }

    public Response upsertEntity(String id, byte[] value) {
        try {
            levelDb.put(Utf8.toBytes(id), value);
            return new Response(Response.CREATED, Response.EMPTY);
        } catch (DBException e) {
            LOG.error("Error in DB", e);
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }
    }

    public Response deleteEntity(String id) {
        try {
            levelDb.delete(Utf8.toBytes(id));
            return new Response(Response.ACCEPTED, Response.EMPTY);
        } catch (DBException e) {
            LOG.error("Error in DB", e);
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }
    }

}
