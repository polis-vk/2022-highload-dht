package ok.dht.test.shik.consistency;

public class InconsistentStrategy implements InconsistencyResolutionStrategy {

    @Override
    public boolean shouldRepair() {
        return false;
    }

    @Override
    public boolean sendDigestRequest(String url) {
        return false;
    }
}
