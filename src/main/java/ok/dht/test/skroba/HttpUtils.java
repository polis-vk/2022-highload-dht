package ok.dht.test.skroba;

import one.nio.http.Request;

import java.net.http.HttpResponse;
import java.util.Map;
import java.util.function.Predicate;

public class HttpUtils {
    private static final int OK = 200;
    private static final int CREATED = 201;
    private static final int ACCEPTED = 202;
    static final int NOT_FOUND = 404;
    public static final String NOT_ENOUGH_REPLICAS = "504 Not Enough Replicas";
    
    public static final Map<Integer, Predicate<? super HttpResponse<byte[]>>> PREDICATES = Map.of(Request.METHOD_GET,
            it -> it.statusCode() == OK || it.statusCode() == NOT_FOUND,
            Request.METHOD_PUT, it -> it.statusCode() == CREATED,
            Request.METHOD_DELETE, it -> it.statusCode() == ACCEPTED);
}
