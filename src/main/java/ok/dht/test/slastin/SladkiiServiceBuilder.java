package ok.dht.test.slastin;

import ok.dht.ServiceConfig;
import ok.dht.test.slastin.sharding.ShardingManager;
import one.nio.http.HttpServerConfig;
import org.rocksdb.Options;

import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

public class SladkiiServiceBuilder {
    private final ServiceConfig serviceConfig;
    private Supplier<Options> dbOptionsSupplier;
    private Supplier<ExecutorService> heavyExecutorSupplier;
    private Supplier<ExecutorService> lightExecutorSupplier;
    private Supplier<ExecutorService> httpClientExecutorSupplier;
    private Supplier<ShardingManager> shardingManagerSupplier;
    private Supplier<HttpServerConfig> httpServerConfigSupplier;

    SladkiiServiceBuilder(ServiceConfig serviceConfig) {
        this.serviceConfig = serviceConfig;
        this.dbOptionsSupplier = SladkiiService.DEFAULT_OPTIONS_SUPPLIER;
        this.heavyExecutorSupplier = SladkiiService.makeDefaultHeavyExecutorSupplier(serviceConfig);
        this.lightExecutorSupplier = SladkiiService.makeDefaultLightExecutorSupplier(serviceConfig);
        this.httpClientExecutorSupplier = SladkiiService.makeDefaultHttpClientExecutorSupplier(serviceConfig);
        this.shardingManagerSupplier = SladkiiService.makeDefaultShardingManagerSupplier(serviceConfig);
        this.httpServerConfigSupplier = SladkiiService.makeDefaultHttpServerConfigSupplier(serviceConfig);
    }

    SladkiiServiceBuilder setDbOptionsSupplier(Supplier<Options> dbOptionsSupplier) {
        this.dbOptionsSupplier = dbOptionsSupplier;
        return this;
    }

    SladkiiServiceBuilder setHeavyExecutorSupplier(Supplier<ExecutorService> heavyExecutorSupplier) {
        this.heavyExecutorSupplier = heavyExecutorSupplier;
        return this;
    }

    SladkiiServiceBuilder setLightExecutorSupplier(Supplier<ExecutorService> lightExecutorSupplier) {
        this.lightExecutorSupplier = lightExecutorSupplier;
        return this;
    }

    SladkiiServiceBuilder setHttpClientExecutorSupplier(Supplier<ExecutorService> httpClientExecutorSupplier) {
        this.httpClientExecutorSupplier = httpClientExecutorSupplier;
        return this;
    }

    SladkiiServiceBuilder setShardingManagerSupplier(Supplier<ShardingManager> shardingManagerSupplier) {
        this.shardingManagerSupplier = shardingManagerSupplier;
        return this;
    }

    SladkiiServiceBuilder setHttpServerConfigSupplier(Supplier<HttpServerConfig> httpServerConfigSupplier) {
        this.httpServerConfigSupplier = httpServerConfigSupplier;
        return this;
    }

    SladkiiService build() {
        return new SladkiiService(
                serviceConfig,
                dbOptionsSupplier,
                heavyExecutorSupplier,
                lightExecutorSupplier,
                httpClientExecutorSupplier,
                shardingManagerSupplier,
                httpServerConfigSupplier
        );
    }
}
