package ok.dht.test.siniachenko.service;

import one.nio.http.Response;

import java.util.Iterator;
import java.util.Map;

public class ChunkedTransferEncoder {
    public EntityChunkStreamQueueItem encodeEntityChunkStream(
        Iterator<Map.Entry<byte[], byte[]>> entryIterator
    ) {
        Response response = new Response(Response.OK);
        response.addHeader("Transfer-Encoding: chunked");
        response.addHeader("Connection: close");
        byte[] metaData = response.toBytes(false);
        return new EntityChunkStreamQueueItem(entryIterator, metaData);
    }
}
