package ok.dht.test.frolovm;

import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.util.Utf8;
import org.iq80.leveldb.DB;

import java.nio.ByteBuffer;

public class RequestExecutor {

	private final DB dao;

	public RequestExecutor(DB dao) {
		this.dao = dao;
	}

	public Response entityHandlerSelf(String id, Request request, long timestamp) {
		if (!Utils.checkId(id)) {
			return new Response(Response.BAD_REQUEST, Utf8.toBytes(Utils.BAD_ID));
		}

		return switch (request.getMethod()) {
			case Request.METHOD_PUT -> putHandler(request, id, timestamp);
			case Request.METHOD_GET -> getHandler(id);
			case Request.METHOD_DELETE -> deleteHandler(id, timestamp);
			default -> new Response(Response.METHOD_NOT_ALLOWED, Utf8.toBytes(Utils.NO_SUCH_METHOD));
		};
	}

	private Response putHandler(Request request, String id, long timestamp) {
		dao.put(Utils.stringToByte(id), Utils.dataToBytes(timestamp, request.getBody()));
		return Utils.emptyResponse(Response.CREATED);
	}

	private Response deleteHandler(String id, long timestamp) {
		dao.put(Utils.stringToByte(id), Utils.dataToBytes(timestamp, null));
		return Utils.emptyResponse(Response.ACCEPTED);
	}

	private Response getHandler(String id) {
		byte[] res = dao.get(Utils.stringToByte(id));

		if (res == null) {
			return Utils.emptyResponse(Response.NOT_FOUND);
		}

		int sizeLong = Long.SIZE / Long.BYTES;

		ByteBuffer buffer = ByteBuffer.allocate(res.length);
		buffer.put(res);
		buffer.flip();
		long timestamp = buffer.getLong();

		if (res[sizeLong] == 1) {
			Response response = Utils.emptyResponse(Response.NOT_FOUND);
			response.addHeader(Utils.TIMESTAMP_ONE_NIO + timestamp);
			response.addHeader(Utils.TOMBSTONE_ONE_NIO + "true");
			return response;
		} else {
			byte[] resultData = new byte[res.length - sizeLong - 1];
			buffer.get(sizeLong + 1, resultData);
			Response response = new Response(Response.OK, resultData);
			response.addHeader(Utils.TIMESTAMP_ONE_NIO + timestamp);
			return response;
		}
	}
}
