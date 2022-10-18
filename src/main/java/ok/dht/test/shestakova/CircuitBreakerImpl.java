package ok.dht.test.shestakova;

import ok.dht.ServiceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class CircuitBreakerImpl {

    private static final Logger LOGGER = LoggerFactory.getLogger(CircuitBreakerImpl.class);
    private final HttpClient httpClient;
    private static final long DELAY = 0L;
    private static final long PERIOD = 5L;
    private static final int MAX_FALLEN_REQUESTS_COUNT = 1000;
    private static final int MAX_ILL_PERIODS_COUNT = 10;
    private final ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1);
    private int illPeriodsCounter;
    private final AtomicLong fallenRequestCount = new AtomicLong();
    private final Map<String, Boolean> nodesIllness;
    private final AtomicBoolean isIll = new AtomicBoolean();
    private final ServiceConfig serviceConfig;

    public CircuitBreakerImpl(ServiceConfig serviceConfig, HttpClient httpClient) {
        this.serviceConfig = serviceConfig;
        this.httpClient = httpClient;
        this.timer.scheduleAtFixedRate(
                new BreakerTimerTask(),
                DELAY,
                PERIOD,
                TimeUnit.SECONDS
        );
        this.nodesIllness = new HashMap<>();
        for (String nodeUrl : this.serviceConfig.clusterUrls()) {
            nodesIllness.put(nodeUrl, false);
        }
    }

    protected void doShutdownNow() {
        timer.shutdownNow();
    }

    protected void putNodesIllnessInfo(String node, boolean isIll) {
        nodesIllness.put(node, isIll);
    }

    protected boolean isNodeIll(String nodeUrl) {
        return nodesIllness.get(nodeUrl);
    }

    protected void incrementFallenRequestsCount() {
        fallenRequestCount.incrementAndGet();
    }

    private HttpRequest.Builder request(String nodeUrl, String path) {
        return HttpRequest.newBuilder(URI.create(nodeUrl + path));
    }

    private class BreakerTimerTask implements Runnable {
        @Override
        public void run() {
            if (fallenRequestCount.get() > MAX_FALLEN_REQUESTS_COUNT) {
                isIll.set(true);
                nodesIllness.put(serviceConfig.selfUrl(), true);
                tellToOtherNodesAboutIllness(true);
            }
            fallenRequestCount.getAndSet(0);
            illPeriodsCounter++;
            // Пока что проверка здоровья ноды не придумана, и мы просто даём ноде 10 периодов по 5 секунд на
            // восстановление и снова начинаем с ней работать (если она все еще больна, мы это поймём через 1 период)
            if (isIll.get() && illPeriodsCounter > MAX_ILL_PERIODS_COUNT) {
                isIll.set(false);
                nodesIllness.put(serviceConfig.selfUrl(), false);
                illPeriodsCounter = 0;
                tellToOtherNodesAboutIllness(false);
            }
        }

        private void tellToOtherNodesAboutIllness(boolean isIll) {
            String path = "/service/message/" + (isIll ? "ill" : "healthy");
            for (String nodeUrl : serviceConfig.clusterUrls()) {
                if (nodeUrl.equals(serviceConfig.selfUrl())) {
                    continue;
                }
                try {
                    httpClient.send(
                            request(nodeUrl, path)
                                    .PUT(HttpRequest.BodyPublishers.ofString(nodeUrl))
                                    .build(),
                            HttpResponse.BodyHandlers.ofByteArray()
                    );
                } catch (IOException | InterruptedException e) {
                    LOGGER.error("Error while sending health-request from " + serviceConfig.selfUrl());
                }
            }
        }
    }
}
