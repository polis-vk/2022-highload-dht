package ok.dht.test.shik.consistency;

public class SimpleResolutionStrategy implements RepairResolutionStrategy {

    @Override
    public boolean sendDigestRequest(String url) {
        return false;
    }
}
