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

    private static final String CHUNK_SEPARATOR = "\r\n";
    private static final byte[] CRLF = CHUNK_SEPARATOR.getBytes(DaoUtils.BASE_CHARSET);
    private static final String EOF_R = "0" + CHUNK_SEPARATOR + CHUNK_SEPARATOR;
    private static final byte[] EOF = ("0" + CHUNK_SEPARATOR + CHUNK_SEPARATOR).getBytes(DaoUtils.BASE_CHARSET);
    private static final String KEY_VALUE_SEPARATOR = "\n";
    private final Iterator<TypedTimedEntry> mergeIterator;
    private boolean isDrained = false;
    private int count;

    public MyQueueItem(Iterator<TypedTimedEntry> mergeIterator) {
        this.mergeIterator = mergeIterator;
    }

    @Override
    public int remaining() {
        return hasRemaining() ? 0 : 1;
    }

    public boolean hasRemaining() {
        return count < 1;
    }

    @Override
    public int write(Socket socket) throws IOException {
//        byte[] chunk;
        ++count;
        String r = "3\r\n1\n2\r\n";
        if (!hasRemaining()) {
            r += "0\r\n\r\n";
        }
        byte[] bytes = r.getBytes(DaoUtils.BASE_CHARSET);
        return socket.write(bytes, 0, bytes.length);
//        if (hasRemaining()) {
//            byte[] bytes = (3 + CHUNK_SEPARATOR + 1 + "\n" + 2 + CHUNK_SEPARATOR).getBytes(DaoUtils.BASE_CHARSET);
//            return socket.write(bytes, 0, bytes.length);
//        } else {
//            return socket.write(EOF, 0, EOF.length);
//        }
//            TypedTimedEntry e = mergeIterator.next();
//            final byte[] encodedPair = (
//                    DaoUtils.DAO_FACTORY.toString(e.key())
//                    + KEY_VALUE_SEPARATOR
//                    + DaoUtils.DAO_FACTORY.toString(e.value())
//            ).getBytes(DaoUtils.BASE_CHARSET);
//            final byte[] encodedPairLength = Integer.toHexString(encodedPair.length).getBytes(DaoUtils.BASE_CHARSET);
//            int contentLength = encodedPairLength.length + CRLF.length + encodedPair.length + CRLF.length;
//            chunk = new byte[contentLength];
//            final ByteBuffer chunkBuffer = ByteBuffer.wrap(chunk);
//            chunkBuffer.put(encodedPairLength);
//            chunkBuffer.put(CRLF);
//            chunkBuffer.put(encodedPair);
//            chunkBuffer.put(CRLF);
//            if (!mergeIterator.hasNext()) {
//                chunkBuffer.put(EOF);
//                isDrained = true;
//            }
//        } else {
//            if (isDrained) {
//                throw new IllegalStateException("QueueItem has already drained");
//            }
//
//            chunk = EOF;
//            isDrained = true;
//        }
//        return socket.write(chunk, 0, chunk.length);
//        byte[] bytes = (count++ + "\n").getBytes(DaoUtils.BASE_CHARSET);
//        return socket.write(bytes, 0, bytes.length);
    }
}
