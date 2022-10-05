package ok.dht.test;

import ok.dht.Service;
import ok.dht.ServiceConfig;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ServiceFactory {

    int stage();
    int week();
    String[] bonuses() default {};

    interface Factory {
        Service create(ServiceConfig config);
    }

}
