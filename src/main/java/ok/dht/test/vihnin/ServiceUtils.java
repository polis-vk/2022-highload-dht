package ok.dht.test.vihnin;

import one.nio.http.HttpServerConfig;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;

import java.util.HashSet;
import java.util.Set;

public final class ServiceUtils {
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

}
