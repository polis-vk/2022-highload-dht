package ok.dht.test.shik.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import ok.dht.ServiceConfig;
import ok.dht.test.shik.model.DBValue;
import one.nio.http.HttpServerConfig;
import one.nio.net.ConnectionString;
import one.nio.server.ServerConfig;

public class ServiceUtils {

    private ServiceUtils() {

    }

    public static HttpServerConfig createHttpConfig(ServiceConfig config) {
        ServerConfig serverConfig = ServerConfig.from(new ConnectionString(config.selfUrl()));
        HttpServerConfig httpServerConfig = new HttpServerConfig();
        httpServerConfig.acceptors = serverConfig.acceptors;
        Arrays.stream(httpServerConfig.acceptors).forEach(x -> x.reusePort = true);
        httpServerConfig.schedulingPolicy = serverConfig.schedulingPolicy;
        return httpServerConfig;
    }

    public static DBValue getActualValue(Map<String, DBValue> dbValues) {
        DBValue actualValue = null;
        for (Map.Entry<String, DBValue> entry : dbValues.entrySet()) {
            DBValue current = entry.getValue();
            if (current != null && (actualValue == null || DBValue.COMPARATOR.compare(actualValue, current) < 0)) {
                actualValue = current;
            }
        }
        return actualValue;
    }

    public static List<String> getInconsistentReplicas(Map<String, DBValue> dbValues, DBValue latestValue) {
        List<String> inconsistentReplicas = new ArrayList<>();
        for (Map.Entry<String, DBValue> entry : dbValues.entrySet()) {
            DBValue current = entry.getValue();
            if (current == null || DBValue.COMPARATOR.compare(latestValue, current) != 0) {
                inconsistentReplicas.add(entry.getKey());
            }
        }
        return inconsistentReplicas;
    }
}
