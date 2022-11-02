package ok.dht.test.vihnin;

import one.nio.http.HttpServerConfig;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static ok.dht.test.vihnin.ParallelHttpServer.INNER_HEADER_NAME;
import static ok.dht.test.vihnin.ParallelHttpServer.INNER_HEADER_VALUE;
import static ok.dht.test.vihnin.ParallelHttpServer.TIME_HEADER_NAME;

public final class ServiceUtils {
    private static final Logger logger = LoggerFactory.getLogger(ServiceUtils.class);
    // must be > 2
    public static final int VNODE_NUMBER_PER_SERVER = 5;
    public static final String ENDPOINT = "/v0/entity";

    private ServiceUtils() {

    }

    static Response emptyResponse(String code) {
        return new Response(code, Response.EMPTY);
    }

    static String getHeaderValue(Response response, String headerName) {
        var v = response.getHeader(headerName);
        if (v == null) {
            return null;
        }
        return v.substring(2);
    }

    static String getHeaderValue(Request request, String headerName) {
        var v = request.getHeader(headerName);
        if (v == null) {
            return null;
        }
        return v.substring(2);
    }

    static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    public static void distributeVNodes(ShardHelper shardHelper, int numberOfServers) {
        int gapPerNode = 2 * (Integer.MAX_VALUE / VNODE_NUMBER_PER_SERVER);
        int gapPerServer = gapPerNode / numberOfServers;

        for (int i = 0; i < numberOfServers; i++) {
            Set<Integer> vnodes = new HashSet<>();
            int currVNode = Integer.MIN_VALUE + i * gapPerServer;
            for (int j = 0; j < VNODE_NUMBER_PER_SERVER; j++) {
                vnodes.add(currVNode);
                currVNode += gapPerNode;
            }
            shardHelper.addShard(i, vnodes);
        }
    }

    static HttpRequest createJavaRequest(Request request, String destinationUrl) throws URISyntaxException {
        var builder = HttpRequest.newBuilder()
                .uri(new URI(destinationUrl + request.getURI()))
                .method(request.getMethodName(),
                        request.getBody() == null
                                ? HttpRequest.BodyPublishers.noBody()
                                : HttpRequest.BodyPublishers.ofByteArray(request.getBody()))
                .header(INNER_HEADER_NAME, INNER_HEADER_VALUE)
                .timeout(Duration.ofMillis(1200));

        Arrays.stream(request.getHeaders())
                .filter(Objects::nonNull)
                .map(s -> {
                    int index = s.indexOf(':');
                    return List.of(
                            s.substring(0, index),
                            s.substring(index + 1).strip()
                    );
                })
                .forEach(ls -> trySetHeader(builder, ls));

        return builder.build();
    }

    private static void trySetHeader(HttpRequest.Builder builder, List<String> ls) {
        try {
            builder.setHeader(ls.get(0), ls.get(1));
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    static HttpResponse<byte[]> handleSingleAcknowledgment(
            ResponseAccumulator responseAccumulator,
            HttpResponse<byte[]> httpResponse,
            Throwable throwable) {
        if (throwable == null) {
            if (httpResponse == null) {
                responseAccumulator.acknowledgeFailed();
            } else {
                Optional<String> time = httpResponse.headers()
                        .firstValue(TIME_HEADER_NAME);
                if (time.isPresent()) {
                    responseAccumulator.acknowledgeSucceed(
                            Long.parseLong(time.get()),
                            httpResponse.statusCode(),
                            httpResponse.body()
                    );
                } else {
                    responseAccumulator.acknowledgeFailed();
                }
            }
            return httpResponse;
        } else {
            responseAccumulator.acknowledgeMissed();
            return null;
        }
    }
}
