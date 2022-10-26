package ok.dht.test.frolovm;

public interface CircuitBreaker {

    void incrementFail(String shard);

    void successRequest(String shard);

    boolean isReady(String shard);

}
