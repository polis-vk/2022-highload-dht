package ok.dht.test.siniachenko;

import ok.dht.ServiceConfig;
import ok.dht.test.siniachenko.service.TycoonService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class TycoonServer {
    private static final Logger LOG = LoggerFactory.getLogger(TycoonServer.class);

    private TycoonServer() {
    }

    public static void main(String[] args)
        throws IOException, ExecutionException, InterruptedException, TimeoutException {
        String url = getUrl();
        List<String> clusterUrls = getClusterUrls(url);
        if (!clusterUrls.contains(url)) {
            throw new IllegalArgumentException(
                "Cluster urls [" + String.join(",", clusterUrls) + "] don't contain our URL " + url
            );
        }
        ServiceConfig config;
        try {
            config = new ServiceConfig(
                Integer.parseInt(url.split(":")[2]),
                url,
                clusterUrls,
                Files.createTempDirectory("server")
            );
        } catch (IOException e) {
            LOG.error("Cannot create server directory \"server\"");
            throw e;
        }
        TycoonService tycoonService = new TycoonService(config);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                tycoonService.stop().get(1, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException | IOException e) {
                LOG.error("Error while stopping server", e);
            }
        }));
        tycoonService.start().get(1, TimeUnit.SECONDS);
        LOG.info("Started Server on {}", url);
    }

    private static String getUrl() {
        String urlEnv = System.getenv("URL");
        if (urlEnv == null) {
            urlEnv = String.format("http://localhost:%d", 12345);
        }
        return urlEnv;
    }

    private static List<String> getClusterUrls(String ourUrl) {
        String clusterUrlsEnv = System.getenv("CLUSTER_URLS");
        if (clusterUrlsEnv == null) {
            clusterUrlsEnv = ourUrl;
        }
        return Arrays.asList(clusterUrlsEnv.split(","));
    }
}
