package net.unit8.wscl.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The decoder for query string.
 *
 * @author kawasima
 */
public class QueryStringDecoder {
    private final Map<String, List<String>> parameters;

    public QueryStringDecoder(String query) {
        parameters = new HashMap<>(16);
        if (query != null) {
            for (String pairStr : query.split("&")) {
                String[] pair = pairStr.split("=", 2);
                List<String> values = parameters.get(pair[0]);
                if (values == null) {
                    values = new ArrayList<>();
                    parameters.put(pair[0], values);
                }
                values.add(pair[1]);
            }
        }
    }

    public Map<String, List<String>> parameters() {
        return parameters;
    }
}
