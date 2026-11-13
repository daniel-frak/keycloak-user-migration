package com.danielfrak.code.keycloak.providers.rest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.component.ComponentModel;

import java.util.OptionalInt;

import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.HTTP_CONNECT_TIMEOUT_PROPERTY;
import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.HTTP_READ_TIMEOUT_PROPERTY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MigrationConfigurationTest {

    private MultivaluedHashMap<String, String> config;
    private MigrationConfiguration migrationConfiguration;

    @BeforeEach
    void setUp() {
        config = new MultivaluedHashMap<>();
        var model = new ComponentModel();
        model.setConfig(config);
        migrationConfiguration = new MigrationConfiguration(model);
    }

    @Test
    void shouldReturnNoTimeoutsWhenNotConfigured() {
        assertEquals(OptionalInt.empty(), migrationConfiguration.getHttpConnectTimeoutMillis());
        assertEquals(OptionalInt.empty(), migrationConfiguration.getHttpReadTimeoutMillis());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void shouldReturnNoTimeoutsWhenBlank(String value) {
        config.putSingle(HTTP_CONNECT_TIMEOUT_PROPERTY, value);
        config.putSingle(HTTP_READ_TIMEOUT_PROPERTY, value);

        assertEquals(OptionalInt.empty(), migrationConfiguration.getHttpConnectTimeoutMillis());
        assertEquals(OptionalInt.empty(), migrationConfiguration.getHttpReadTimeoutMillis());
    }

    @Test
    void shouldReturnConfiguredTimeouts() {
        config.putSingle(HTTP_CONNECT_TIMEOUT_PROPERTY, "3000");
        config.putSingle(HTTP_READ_TIMEOUT_PROPERTY, " 5000 ");

        assertEquals(OptionalInt.of(3000), migrationConfiguration.getHttpConnectTimeoutMillis());
        assertEquals(OptionalInt.of(5000), migrationConfiguration.getHttpReadTimeoutMillis());
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-1", "1.5"})
    void shouldRejectInvalidTimeouts(String value) {
        config.putSingle(HTTP_CONNECT_TIMEOUT_PROPERTY, value);
        config.putSingle(HTTP_READ_TIMEOUT_PROPERTY, value);

        assertThrows(IllegalArgumentException.class, () -> migrationConfiguration.getHttpConnectTimeoutMillis());
        assertThrows(IllegalArgumentException.class, () -> migrationConfiguration.getHttpReadTimeoutMillis());
    }

    @Test
    void shouldNameFieldLabelWhenRejectingTimeout() {
        config.putSingle(HTTP_CONNECT_TIMEOUT_PROPERTY, "5s");
        config.putSingle(HTTP_READ_TIMEOUT_PROPERTY, "5s");

        var connectException = assertThrows(IllegalArgumentException.class,
                () -> migrationConfiguration.getHttpConnectTimeoutMillis());
        var readException = assertThrows(IllegalArgumentException.class,
                () -> migrationConfiguration.getHttpReadTimeoutMillis());

        assertEquals("'Rest client connect timeout (ms)' must be a positive number of milliseconds",
                connectException.getMessage());
        assertEquals("'Rest client read timeout (ms)' must be a positive number of milliseconds",
                readException.getMessage());
    }
}
