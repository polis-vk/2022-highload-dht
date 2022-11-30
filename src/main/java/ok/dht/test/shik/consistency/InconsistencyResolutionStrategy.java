package ok.dht.test.shik.consistency;

public interface InconsistencyResolutionStrategy {

    boolean shouldRepair();

    boolean sendDigestRequest(String url);
}
