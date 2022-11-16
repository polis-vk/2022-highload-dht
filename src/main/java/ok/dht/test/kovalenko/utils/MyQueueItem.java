package ok.dht.test.kovalenko.utils;

import ok.dht.test.kovalenko.dao.aliases.TypedTimedEntry;
import ok.dht.test.kovalenko.dao.utils.DaoUtils;
import one.nio.net.Session;
import one.nio.net.Socket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class MyQueueItem extends Session.QueueItem {

    private static final byte[] CRLF = "\r\n".getBytes(DaoUtils.BASE_CHARSET);
    private static final byte[] EOF = "0\r\n\r\n".getBytes(DaoUtils.BASE_CHARSET);
    private static final String KEY_VALUE_SEPARATOR = "\n";
    private final Iterator<TypedTimedEntry> mergeIterator;
    private boolean isDrained = false;
    private final List<byte[]> plannedToWrite = new ArrayList<byte[]>();

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

        int writtenUpPlanned = tryToWriteUpPlanned(socket);
        if (!plannedToWrite.isEmpty()) {
            return writtenUpPlanned;
        }

        if (!mergeIterator.hasNext()) {
            int written = tryToWrite(socket, EOF, 0, EOF.length);
            if (written == EOF.length) {
                isDrained = true;
            }
            return writtenUpPlanned + written;
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
        }

        int written = tryToWrite(socket, chunk, 0, chunk.length);
        if (written == chunk.length && !mergeIterator.hasNext()) {
            isDrained = true;
        }
        return writtenUpPlanned + written;
    }

    private int tryToWriteUpPlanned(Socket socket) throws IOException {
        int writtenUp = 0;
        while (!plannedToWrite.isEmpty()) {
            byte[] first = plannedToWrite.get(0);
            int written = Math.max(0, socket.write(first, 0, first.length));
            writtenUp += written;
            if (written != first.length) {
                int remained = first.length - written;
                byte[] bytesRemained = new byte[remained];
                System.arraycopy(first, written, bytesRemained, 0, remained);
                plannedToWrite.set(0, bytesRemained);
                break;
            }
            plannedToWrite.remove(0);
        }
        return writtenUp;
    }

    private int tryToWrite(Socket socket, byte[] data, int offset, int count) throws IOException {
        int written = socket.write(data, offset, count);
        if (written != data.length) {
            written = Math.max(0, written);
            int remained = data.length - written;
            byte[] bytesRemained = new byte[remained];
            System.arraycopy(data, written, bytesRemained, 0, remained);
            plannedToWrite.add(bytesRemained);
        }
        return written;
    }

}
