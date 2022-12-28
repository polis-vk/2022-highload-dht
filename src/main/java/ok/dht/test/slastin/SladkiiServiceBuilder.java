package ok.dht.test.slastin;

import ok.dht.ServiceConfig;
import ok.dht.test.slastin.node.NodeConfig;
import ok.dht.test.slastin.sharding.ShardingManager;
import one.nio.http.HttpServerConfig;
import org.rocksdb.Options;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import static ok.dht.test.slastin.SladkiiService.makeDefaultHttpServerConfigSupplier;
import static ok.dht.test.slastin.SladkiiService.makeDefaultNodeConfigs;
import static ok.dht.test.slastin.SladkiiService.makeDefaultProcessorsSupplier;
import static ok.dht.test.slastin.SladkiiService.makeDefaultShardingManagerSupplier;

public class SladkiiServiceBuilder {
    private final ServiceConfig serviceConfig;
    private Supplier<Options> dbOptionsSupplier;
    private Supplier<ExecutorService> processorsSupplier;
    private Supplier<ShardingManager> shardingManagerSupplier;
    private Supplier<HttpServerConfig> httpServerConfigSupplier;
    private List<NodeConfig> nodeConfigs;

    SladkiiServiceBuilder(ServiceConfig serviceConfig) {
        this.serviceConfig = serviceConfig;
        this.dbOptionsSupplier = SladkiiService.DEFAULT_OPTIONS_SUPPLIER;
        this.processorsSupplier = makeDefaultProcessorsSupplier(serviceConfig);
        this.shardingManagerSupplier = makeDefaultShardingManagerSupplier(serviceConfig);
        this.httpServerConfigSupplier = makeDefaultHttpServerConfigSupplier(serviceConfig);
        this.nodeConfigs = makeDefaultNodeConfigs(serviceConfig);
    }

    SladkiiServiceBuilder setDbOptionsSupplier(Supplier<Options> dbOptionsSupplier) {
        this.dbOptionsSupplier = dbOptionsSupplier;
        return this;
    }

    SladkiiServiceBuilder setProcessorsSupplier(Supplier<ExecutorService> processorsSupplier) {
        this.processorsSupplier = processorsSupplier;
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

    SladkiiServiceBuilder setNodeConfigs(List<NodeConfig> nodeConfigs) {
        this.nodeConfigs = nodeConfigs;
        return this;
    }

    SladkiiService build() {
        return new SladkiiService(
                serviceConfig,
                dbOptionsSupplier,
                processorsSupplier,
                shardingManagerSupplier,
                httpServerConfigSupplier,
                nodeConfigs
        );
    }
}
