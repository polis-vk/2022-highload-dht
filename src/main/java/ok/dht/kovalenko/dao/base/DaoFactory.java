package ok.dht.kovalenko.dao.base;

import ok.dht.ServiceConfig;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface DaoFactory {

    int stage() default 1;

    int week() default 1;

    interface Factory<D, E extends Entry<D>> {

        static ServiceConfig extractConfig(Dao<String, Entry<String>> dao) {
            return ((TestDao<?, ?>) dao).config;
        }

        static Dao<String, Entry<String>> reopen(Dao<String, Entry<String>> dao) throws IOException {
            return ((TestDao<?, ?>) dao).reopen();
        }

        default Dao<D, E> createDao() throws IOException {
            throw new UnsupportedOperationException("Need to override one of createDao methods");
        }

        default Dao<D, E> createDao(ServiceConfig config) throws IOException {
            return createDao();
        }

        String toString(D data);

        D fromString(String data);

        E fromBaseEntry(Entry<D> baseEntry);

        default Dao<String, Entry<String>> createStringDao(ServiceConfig config) throws IOException {
            return new TestDao<>(this, config);
        }
    }

}
