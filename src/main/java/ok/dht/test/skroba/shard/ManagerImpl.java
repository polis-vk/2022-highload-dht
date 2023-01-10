package ok.dht.test.skroba.shard;

import ok.dht.ServiceConfig;
import one.nio.util.Hash;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

public class ManagerImpl implements Manager {
    private static final int BUCKETS_FOR_ONE_NODE = 5;
    private final List<Node> nodes;
    private final int size;
    
    private final ServiceConfig config;
    
    public ManagerImpl(ServiceConfig serviceConfig) {
        config = serviceConfig;
        
        nodes = new ArrayList<>(serviceConfig.clusterUrls()
                .size() * BUCKETS_FOR_ONE_NODE);
        
        serviceConfig.clusterUrls()
                .forEach(url -> {
                    IntStream.range(0, BUCKETS_FOR_ONE_NODE)
                            .forEach(
                                    index -> nodes.add(new Node(url, Hash.murmur3("node:" + url + index)))
                            );
                });
        
        size = serviceConfig.clusterUrls()
                .size();
        
        nodes.sort(Comparator.comparingInt(Node::getHash));
    }
    
    @Override
    public String selfUrl() {
        return config.selfUrl();
    }
    
    @Override
    public Node getUrlById(String id) {
        int hash = Hash.murmur3(id);
        
        int left = -1;
        int right = nodes.size();
        
        while (right - left > 1) {
            int middle = right - (right - left) / 2;
            
            if (nodes.get(middle)
                    .getHash() < hash) {
                left = middle;
            } else {
                right = middle;
            }
        }
        
        return nodes.get(right % nodes.size());
    }
    
    @Override
    public int clusterSize() {
        return size;
    }
    
    @Override
    public List<String> getUrls(String id, int size) {
        List<String> result = new ArrayList<>();
        
        List<String> urls = config.clusterUrls();
        String url = getUrlById(id).getUrl();
        int index = urls.indexOf(url);
        
        while (result.size() != size) {
            result.add(urls.get(index++ % urls.size()));
        }
        
        return result;
    }
}
