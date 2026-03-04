package com.danielfrak.code.keycloak.providers.rest.remote.usermodel;

import org.keycloak.component.ComponentModel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LegacyMappingParser {

    /**
     * Returns a map of legacy props to new ones
     */
    public Map<String, String> parse(ComponentModel model, String property) {
        Map<String, String> map = new HashMap<>();
        List<String> pairs = model.getConfig().getList(property);
        for (String pair : pairs) {
            String[] keyValue = pair.split(":");
            map.put(keyValue[0], keyValue[1]);
        }
        return map;
    }
}
