package ok.dht.test.kazakov.service.http;

import one.nio.http.PathMapper;
import one.nio.http.Request;

import java.util.Arrays;
import java.util.HashMap;

/**
 * Copy-paste of one-nio {@link PathMapper} parameterized with {@link DaoHttpRequestHandler}.
 */
public class DaoHttpPathMapper extends HashMap<String, DaoHttpRequestHandler[]> {
    // Add a new mapping
    public void add(final String path, final int[] methods, final DaoHttpRequestHandler handler) {
        DaoHttpRequestHandler[] handlersByMethod = super.computeIfAbsent(path, p -> new DaoHttpRequestHandler[1]);
        if (methods == null) {
            handlersByMethod[0] = handler;
        } else {
            for (final int method : methods) {
                if (method <= 0 || method >= Request.NUMBER_OF_METHODS) {
                    throw new IllegalArgumentException("Invalid RequestMethod " + method + " for path " + path);
                }
                if (method >= handlersByMethod.length) {
                    handlersByMethod = Arrays.copyOf(handlersByMethod, method + 1);
                    super.put(path, handlersByMethod);
                }
                handlersByMethod[method] = handler;
            }
        }
    }

    // Return an existing handler for this HTTP request or null if not found
    public DaoHttpRequestHandler find(final String path, final int method) {
        final DaoHttpRequestHandler[] handlersByMethod = super.get(path);
        if (handlersByMethod == null) {
            return null;
        }

        if (method > 0 && method < handlersByMethod.length && handlersByMethod[method] != null) {
            return handlersByMethod[method];
        }

        // return the universal handler for all methods
        return handlersByMethod[0];
    }
}
