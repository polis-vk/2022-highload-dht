package ok.dht.test.shik.consistency;

import java.util.List;

public class DigestResolutionStrategy implements RepairResolutionStrategy {

    private final List<String> requestedReplicasUrls;
    private final String currentNodeUrl;

    private boolean isCurrentNodeRequested;

    public DigestResolutionStrategy(List<String> requestedReplicasUrls, String currentNodeUrl) {
        this.requestedReplicasUrls = requestedReplicasUrls;
        this.currentNodeUrl = currentNodeUrl;
        init();
    }

    private void init() {
        for (String url : requestedReplicasUrls) {
            if (currentNodeUrl.equals(url)) {
                isCurrentNodeRequested = true;
                break;
            }
        }
    }

    @Override
    public boolean sendDigestRequest(String url) {
        if (isCurrentNodeRequested) {
            return !currentNodeUrl.equals(url);
        } else {
            return !requestedReplicasUrls.get(0).equals(url);
        }
    }
}
