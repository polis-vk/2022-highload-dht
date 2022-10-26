package ok.dht.test.panov;

import one.nio.http.Response;

import java.util.List;

public class ResponseResolver {

    private final ReplicasAcknowledgment replicasAcknowledgment;

    public ResponseResolver(ReplicasAcknowledgment replicasAcknowledgment) {
        this.replicasAcknowledgment = replicasAcknowledgment;
    }

    public Response resolve(List<Response> responses) {
        Response actualResponse = null;

        List<Response> filteredResponses = responses.stream().filter(resp -> resp.getStatus() < 500).toList();

        if (filteredResponses.size() < replicasAcknowledgment.ack) {
            return new Response("504 Not Enough Replicas", Response.EMPTY);
        }

        for (Response resp : filteredResponses) {
            actualResponse = moreRelevant(actualResponse, resp);
        }

        return actualResponse;
    }

    private static Response moreRelevant(Response first, Response second) {
        if (first == null) {
            return second;
        } else if (second == null) {
            return first;
        } else {
            String firstHeader = first.getHeader(ServiceImpl.DELEGATE_HEADER);
            String secondHeader = second.getHeader(ServiceImpl.DELEGATE_HEADER);

            if (firstHeader == null) {
                return second;
            } else if (secondHeader == null) {
                return first;
            } else {
                long firstTimestamp = Long.parseLong(firstHeader);
                long secondTimestamp = Long.parseLong(secondHeader);

                if (firstTimestamp > secondTimestamp) {
                    return first;
                } else {
                    return second;
                }
            }
        }
    }
}
