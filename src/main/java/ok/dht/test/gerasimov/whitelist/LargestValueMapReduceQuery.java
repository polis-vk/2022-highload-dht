package ok.dht.test.gerasimov.whitelist;

import ok.dht.test.gerasimov.exception.EntitiesServiceException;
import ok.dht.test.gerasimov.mapreduce.MapReduceQuery;
import ok.dht.test.gerasimov.model.DaoEntry;
import ok.dht.test.gerasimov.utils.ObjectMapper;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBIterator;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class LargestValueMapReduceQuery implements MapReduceQuery, Serializable {

    @Override
    public byte[] map(DB dao) {
        try {
            DBIterator iterator = dao.iterator();
            String maxValue = null;

            while (iterator.hasNext()) {
                Map.Entry<byte[], byte[]> entry = iterator.next();
                DaoEntry daoEntry = ObjectMapper.deserialize(entry.getValue());

                String valueString = new String(daoEntry.getValue());

                if (maxValue == null || valueString.compareTo(maxValue) > 0) {
                    maxValue = valueString;
                }
            }

            return maxValue == null ? new byte[0] : maxValue.getBytes();
        } catch (IOException e) {
            throw new EntitiesServiceException("Can not close iterator", e);
        } catch (ClassNotFoundException e) {
            throw new EntitiesServiceException("Can not deserialize DaoEntry", e);
        }
    }

    @Override
    public String reduce(List<byte[]> list) {
        return list.stream().max(Arrays::compare).stream().findFirst().map(String::new).orElse("");
    }
}
