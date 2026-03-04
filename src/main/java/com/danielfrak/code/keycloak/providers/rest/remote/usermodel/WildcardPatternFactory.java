package com.danielfrak.code.keycloak.providers.rest.remote.usermodel;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public class WildcardPatternFactory {

    public List<Pattern> create(List<String> patterns) {
        return patterns.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(pattern -> !pattern.isBlank())
                .map(this::compileWildcardPattern)
                .toList();
    }

    private Pattern compileWildcardPattern(String pattern) {
        String regex = "^" + Pattern.quote(pattern)
                .replace("*", "\\E.*\\Q")
                + "$";
        return Pattern.compile(regex);
    }
}
