package ok.dht.test.kovalenko.dao.base;

import ok.dht.ServiceConfig;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface DaoFactoryB {

    int stage() default 1;

    int week() default 1;

    interface Factory<D, E extends TimedEntry<D>> {

        static ServiceConfig extractConfig(Dao<String, TimedEntry<String>> dao) {
            return ((TestDaoB<?, ?>) dao).config;
        }

        static Dao<String, TimedEntry<String>> reopen(Dao<String, TimedEntry<String>> dao) throws IOException {
            return ((TestDaoB<?, ?>) dao).reopen();
        }

        default Dao<D, E> createDao() throws IOException {
            throw new UnsupportedOperationException("Need to override one of createDao methods");
        }

        default Dao<D, E> createDao(ServiceConfig config) throws IOException {
            return createDao();
        }

        String toString(D data);

        D fromString(String data);

        E fromBaseEntry(TimedEntry<D> baseTimedEntry);

        byte[] toBytes(D data);

        default Dao<String, TimedEntry<String>> createStringDao(ServiceConfig config) throws IOException {
            return new TestDaoB<>(this, config);
        }
    }

}
