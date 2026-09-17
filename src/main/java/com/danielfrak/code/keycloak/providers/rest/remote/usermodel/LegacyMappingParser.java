package com.danielfrak.code.keycloak.providers.rest.remote.usermodel;

import org.jboss.logging.Logger;
import org.keycloak.component.ComponentModel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LegacyMappingParser {

    private static final Logger LOG = Logger.getLogger(LegacyMappingParser.class);

    /**
     * Returns a map of legacy props to new ones
     */
    public Map<String, String> parse(ComponentModel model, String property) {
        Map<String, String> map = new HashMap<>();
        List<String> pairs = model.getConfig().getList(property);
        for (String pair : pairs) {
            int separatorIndex = pair == null ? -1 : pair.indexOf(':');
            if (separatorIndex < 0) {
                LOG.warnf("Ignoring malformed mapping '%s' in %s. Expected format: 'legacyName:newName'",
                        pair, property);
                continue;
            }
            map.put(pair.substring(0, separatorIndex), pair.substring(separatorIndex + 1));
        }
        return map;
    }
}
