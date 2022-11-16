package ok.dht.test.kovalenko.utils;

import ok.dht.test.kovalenko.dao.aliases.TypedTimedEntry;
import ok.dht.test.kovalenko.dao.base.Dao;
import ok.dht.test.kovalenko.dao.utils.DaoUtils;
import one.nio.net.Session;
import one.nio.net.Socket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

public class MyQueueItem extends Session.QueueItem {

    private static final byte[] CRLF = "\r\n".getBytes(DaoUtils.BASE_CHARSET);
    private static final byte[] EOF = "0\r\n\r\n".getBytes(DaoUtils.BASE_CHARSET);
    private static final String KEY_VALUE_SEPARATOR = "\n";
    private final Iterator<TypedTimedEntry> mergeIterator;
    private boolean isDrained = false;

    public MyQueueItem(Iterator<TypedTimedEntry> mergeIterator) {
        this.mergeIterator = mergeIterator;
    }

    @Override
    public int remaining() {
        return isDrained ? 0 : 1;
    }

    @Override
    public int write(Socket socket) throws IOException {
        if (isDrained) {
            throw new IllegalStateException("Content has already drained");
        }

        if (!mergeIterator.hasNext()) {
            isDrained = true;
            return socket.write(EOF, 0, EOF.length);
        }

        TypedTimedEntry e = mergeIterator.next();
        final byte[] encodedPair = (
                DaoUtils.DAO_FACTORY.toString(e.key())
                + KEY_VALUE_SEPARATOR
                + DaoUtils.DAO_FACTORY.toString(e.value())
        ).getBytes(DaoUtils.BASE_CHARSET);
        final byte[] encodedPairLength = Integer.toHexString(encodedPair.length).getBytes(DaoUtils.BASE_CHARSET);
        int contentLength = encodedPairLength.length + CRLF.length + encodedPair.length + CRLF.length;
        if (!mergeIterator.hasNext()) {
            contentLength += EOF.length;
        }

        byte[] chunk = new byte[contentLength];
        final ByteBuffer chunkBuffer = ByteBuffer.wrap(chunk);
        chunkBuffer.put(encodedPairLength);
        chunkBuffer.put(CRLF);
        chunkBuffer.put(encodedPair);
        chunkBuffer.put(CRLF);
        if (!mergeIterator.hasNext()) {
            chunkBuffer.put(EOF);
            isDrained = true;
        }

        return socket.write(chunk, 0, chunk.length);
    }

}
