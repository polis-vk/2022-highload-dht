package ok.dht.test.shestakova;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.ServiceConfig;
import ok.dht.test.shestakova.dao.MemorySegmentDao;
import ok.dht.test.shestakova.dao.base.BaseEntry;
import ok.dht.test.shestakova.exceptions.MethodNotAllowedException;
import one.nio.http.HttpSession;
import one.nio.http.Param;
import one.nio.http.Request;
import one.nio.http.Response;

import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class RequestsHandler {

    private final MemorySegmentDao dao;

    public RequestsHandler(MemorySegmentDao dao) {
        this.dao = dao;
    }

    public List<HttpRequest> getHttpRequests(Request request, String key, List<String> targetNodes,
                                             ServiceConfig serviceConfig) {
        List<HttpRequest> httpRequests = new ArrayList<>();
        for (String node : targetNodes) {
            if (node.equals(serviceConfig.selfUrl())) {
                httpRequests.add(null);
                continue;
            }
            HttpRequest tmpRequest = buildHttpRequest(key, node, request);
            httpRequests.add(tmpRequest);
        }
        return httpRequests;
    }

    private HttpRequest buildHttpRequest(String key, String targetCluster, Request request)
            throws MethodNotAllowedException {
        if (request.getMethod() != Request.METHOD_GET && request.getMethod() != Request.METHOD_PUT
                && request.getMethod() != Request.METHOD_DELETE) {
            throw new MethodNotAllowedException();
        }

        HttpRequest.Builder httpRequest = requestForKey(targetCluster, key);
        int requestMethod = request.getMethod();
        if (requestMethod == Request.METHOD_GET) {
            httpRequest.GET();
        } else if (requestMethod == Request.METHOD_PUT) {
            httpRequest.PUT(HttpRequest.BodyPublishers.ofByteArray(request.getBody()));
        } else if (requestMethod == Request.METHOD_DELETE) {
            httpRequest.DELETE();
        }
        httpRequest.setHeader("internal", "true");
        return httpRequest.build();
    }

    public Response handleInternalRequest(Request request, HttpSession session, CircuitBreakerImpl circuitBreaker) {
        int methodNum = request.getMethod();
        Response response;
        String id = request.getParameter("id=");
        if (methodNum == Request.METHOD_GET) {
            response = handleGet(id);
        } else if (methodNum == Request.METHOD_PUT) {
            String requestPath = request.getPath();
            if (requestPath.contains("/service/message")) {
                circuitBreaker.putNodesIllnessInfo(Arrays.toString(request.getBody()),
                        "/service/message/ill".equals(requestPath));
                return null;
            }
            response = handlePut(request, id);
        } else if (methodNum == Request.METHOD_DELETE) {
            response = handleDelete(id);
        } else {
            response = new Response(
                    Response.METHOD_NOT_ALLOWED,
                    Response.EMPTY
            );
        }
        return response;
    }

    public Response handleGet(@Param(value = "id") String id) {
        BaseEntry<MemorySegment> entry = dao.get(HttpServerUtils.INSTANCE.fromString(id));
        if (entry == null) {
            return new Response(
                    Response.NOT_FOUND,
                    Response.EMPTY
            );
        }
        boolean cond = entry.value() == null;
        ByteBuffer timestamp = ByteBuffer
                .allocate(Long.BYTES)
                .putLong(entry.timestamp());
        ByteBuffer value = ByteBuffer
                .allocate(cond ? 0 : (int) entry.value().byteSize());
        if (!cond) {
            value.put(entry.value().toByteArray());
        }
        return new Response(
                Response.OK,
                ByteBuffer
                        .allocate(timestamp.capacity() + value.capacity() + Integer.BYTES)
                        .put(timestamp.array())
                        .putInt(cond ? -1 : (int) entry.value().byteSize())
                        .put(value.array())
                        .array()
        );
    }

    public Response handleGetRange(@Param(value = "start") String start, @Param(value = "end") String end) {
        Iterator<BaseEntry<MemorySegment>> entryIterator = dao.get(HttpServerUtils.INSTANCE.fromString(start),
                end == null || end.isEmpty() ? null : HttpServerUtils.INSTANCE.fromString(end));
        if (entryIterator == null) {
            return new Response(
                    Response.NOT_FOUND,
                    Response.EMPTY
            );
        }

        if (!entryIterator.hasNext()) {
            return new Response(
                    Response.OK,
                    Response.EMPTY
            );
        }
        return new ChunkedResponse(Response.OK, entryIterator);
    }

    public Response handlePut(Request request, @Param(value = "id") String id) {
        dao.upsert(new BaseEntry<>(
                HttpServerUtils.INSTANCE.fromString(id),
                MemorySegment.ofArray(request.getBody()),
                System.currentTimeMillis()
        ));
        return new Response(
                Response.CREATED,
                Response.EMPTY
        );
    }

    public Response handleDelete(@Param(value = "id") String id) {
        dao.upsert(new BaseEntry<>(
                HttpServerUtils.INSTANCE.fromString(id),
                null,
                System.currentTimeMillis()
        ));
        return new Response(
                Response.ACCEPTED,
                Response.EMPTY
        );
    }

    private HttpRequest.Builder request(String nodeUrl, String path) {
        return HttpRequest.newBuilder(URI.create(nodeUrl + path));
    }

    private HttpRequest.Builder requestForKey(String nodeUrl, String key) {
        return request(nodeUrl, "/v0/entity?id=" + key);
    }
}
