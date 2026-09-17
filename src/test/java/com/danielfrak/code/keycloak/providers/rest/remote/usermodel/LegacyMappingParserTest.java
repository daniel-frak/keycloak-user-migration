package com.danielfrak.code.keycloak.providers.rest.remote.usermodel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.component.ComponentModel;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyMappingParserTest {

    private static final String PROPERTY = "ROLE_MAP";

    private LegacyMappingParser legacyMappingParser;
    private MultivaluedHashMap<String, String> config;

    @BeforeEach
    void setUp() {
        legacyMappingParser = new LegacyMappingParser();
        config = new MultivaluedHashMap<>();
    }

    @Test
    void shouldParseMappings() {
        config.put(PROPERTY, List.of("oldRole:newRole", "otherRole:otherNewRole"));

        Map<String, String> result = parse();

        assertThat(result)
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "oldRole", "newRole",
                        "otherRole", "otherNewRole"
                ));
    }

    @Test
    void shouldPreserveSeparatorsInTargetName() {
        config.put(PROPERTY, List.of("oldRole:new:Role"));

        Map<String, String> result = parse();

        assertThat(result)
                .containsExactly(Map.entry("oldRole", "new:Role"));
    }

    @Test
    void shouldPreserveEmptyTargetName() {
        config.put(PROPERTY, List.of("oldRole:"));

        Map<String, String> result = parse();

        assertThat(result)
                .containsExactly(Map.entry("oldRole", ""));
    }

    @Test
    void shouldIgnoreMappingsWithoutSeparator() {
        config.put(PROPERTY, List.of("", "oldRole", "validRole:newRole"));

        Map<String, String> result = parse();

        assertThat(result)
                .containsExactly(Map.entry("validRole", "newRole"));
    }

    @Test
    void shouldIgnoreNullMappings() {
        config.put(PROPERTY, Arrays.asList(null, "validRole:newRole"));

        Map<String, String> result = parse();

        assertThat(result)
                .containsExactly(Map.entry("validRole", "newRole"));
    }

    @Test
    void shouldReturnEmptyMapGivenPropertyIsNotConfigured() {
        Map<String, String> result = parse();

        assertThat(result)
                .isEmpty();
    }

    private Map<String, String> parse() {
        ComponentModel model = new ComponentModel();
        model.setConfig(config);
        return legacyMappingParser.parse(model, PROPERTY);
    }
}
