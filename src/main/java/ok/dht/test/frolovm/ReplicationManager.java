package ok.dht.test.frolovm;

import one.nio.http.Request;
import one.nio.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ReplicationManager {

	private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(1);
	private static final Logger LOGGER = LoggerFactory.getLogger(ReplicationManager.class);
	private final ShardingAlgorithm algorithm;
	private final String selfUrl;
	private final RequestExecutor requestExecutor;
	private final CircuitBreaker circuitBreaker;
	private final HttpClient client;


	public ReplicationManager(ShardingAlgorithm algorithm, String selfUrl, RequestExecutor requestExecutor, CircuitBreaker circuitBreaker,
							  HttpClient client) {
		this.algorithm = algorithm;
		this.selfUrl = selfUrl;
		this.requestExecutor = requestExecutor;
		this.circuitBreaker = circuitBreaker;
		this.client = client;
	}

	private boolean isInternal(Request request) {
		return request.getHeader(Utils.TIMESTAMP_ONE_NIO) != null;
	}

	public Response handle(String id, Request request, int ack, int from) {

		if (isInternal(request)) {
			return requestExecutor.entityHandlerSelf(id, request, Long.parseLong(request.getHeader(Utils.TIMESTAMP_ONE_NIO)));
		}

		final long timestamp = System.currentTimeMillis();
		request.addHeader(Utils.TIMESTAMP_ONE_NIO + timestamp);

		List<Response> collectedResponses = new ArrayList<>();

		int countAck = 0;

		int shardIndex = algorithm.chooseShard(id);

		for (int i = 0; i < from; ++i) {
			Shard shard = algorithm.getShardByIndex(shardIndex);

			if (shard.getName().equals(selfUrl)) {
				Response response = requestExecutor.entityHandlerSelf(id, request, timestamp);
				if (isSuccessful(response)) {
					countAck++;
					if (request.getMethod() == Request.METHOD_GET) {
						collectedResponses.add(response);
					}
				}
			} else {
				if (circuitBreaker.isReady(shard.getName())) {
					try {
						Response response = sendResponseToAnotherNode(request, shard);
						if (isSuccessful(response)) {
							countAck++;
							if (request.getMethod() == Request.METHOD_GET) {
								collectedResponses.add(response);
							}
						}
					} catch (IOException | InterruptedException e) {
						LOGGER.error("Something bad happens when client answer", e);
						circuitBreaker.incrementFail(shard.getName());
					}
				} else {
					LOGGER.error("Node is unavailable right now");
				}
			}
			if (countAck >= ack) {
				return generateResult(collectedResponses, request.getMethod());
			}
			shardIndex = (shardIndex + 1) % algorithm.getShards().size();
		}
		return Utils.emptyResponse(Response.GATEWAY_TIMEOUT);
	}

	private Response generateResult(List<Response> collectedResponses, int methodType) {

		switch (methodType) {
			case Request.METHOD_GET: {
				byte[] result = null;
				long mostRelevantTimestamp = 0L;
				boolean isTombstone = false;
				for (Response response : collectedResponses) {
					byte[] data = response.getBody();
					String timestampData = response.getHeader(Utils.TIMESTAMP_ONE_NIO);
					if (timestampData != null) {
						long timestamp = Long.parseLong(timestampData);

						if (mostRelevantTimestamp < timestamp && (response.getStatus() == HttpURLConnection.HTTP_OK
								|| response.getHeader(Utils.TOMBSTONE) != null)) {
							isTombstone = response.getHeader(Utils.TOMBSTONE) != null;
							mostRelevantTimestamp = timestamp;
							result = data;
						}
					}
				}
				if (result == null || isTombstone) {
					return Utils.emptyResponse(Response.NOT_FOUND);
				} else {
					return new Response(Response.OK, result);
				}
			}
			case Request.METHOD_PUT: {
				return Utils.emptyResponse(Response.CREATED);
			}
			case Request.METHOD_DELETE: {
				return Utils.emptyResponse(Response.ACCEPTED);
			}
			default:
				return Utils.emptyResponse(Response.METHOD_NOT_ALLOWED);
		}
	}

	private boolean isSuccessful(Response response) {
		return !Utils.isServerError(response.getStatus()) && response.getStatus() != 405;
	}

	private Response sendResponseToAnotherNode(Request request, Shard shard) throws IOException, InterruptedException {
		byte[] body = request.getBody() == null ? Response.EMPTY : request.getBody();
		HttpResponse<byte[]> response;
		String timestampHeader = request.getHeader(Utils.TIMESTAMP_ONE_NIO);
		try {
			response = client.send(HttpRequest.newBuilder().headers(Utils.TIMESTAMP, timestampHeader).
							uri(URI.create(shard.getName() + request.getURI())).method(
									request.getMethodName(),
									HttpRequest.BodyPublishers.ofByteArray(body)).timeout(RESPONSE_TIMEOUT).build(),
					HttpResponse.BodyHandlers.ofByteArray());
		} catch (HttpTimeoutException | SocketException exception) {
			circuitBreaker.incrementFail(shard.getName());
			LOGGER.debug("Can't connect to shard " + shard.getName());
			return Utils.emptyResponse(Response.GATEWAY_TIMEOUT);
		}
		if (Utils.isServerError(response.statusCode())) {
			circuitBreaker.incrementFail(shard.getName());
		} else {
			circuitBreaker.successRequest(shard.getName());
		}
		String responseStatus = Utils.STATUS_MAP.get(response.statusCode());
		byte[] answer = response.body();
		if (responseStatus == null) {
			throw new IllegalArgumentException("Unknown status code: " + response.statusCode());
		}
		Response resultResponse = new Response(responseStatus, answer);
		Optional<String> header = response.headers().firstValue("timestamp");
		Optional<String> headerTombstone = response.headers().firstValue("tombstone");
		if (headerTombstone.isPresent()) {
			resultResponse.addHeader(Utils.TOMBSTONE);
		}

		header.ifPresent(s -> resultResponse.addHeader(Utils.TIMESTAMP_ONE_NIO + s));
		return resultResponse;
	}


}
