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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void shouldReturnNoTimeoutsGivenNotConfigured() {
        assertThat(migrationConfiguration.getHttpConnectTimeoutMillis())
                .isEqualTo(OptionalInt.empty());
        assertThat(migrationConfiguration.getHttpReadTimeoutMillis())
                .isEqualTo(OptionalInt.empty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void shouldReturnNoTimeoutsGivenBlank(String value) {
        config.putSingle(HTTP_CONNECT_TIMEOUT_PROPERTY, value);
        config.putSingle(HTTP_READ_TIMEOUT_PROPERTY, value);

        assertThat(migrationConfiguration.getHttpConnectTimeoutMillis())
                .isEqualTo(OptionalInt.empty());
        assertThat(migrationConfiguration.getHttpReadTimeoutMillis())
                .isEqualTo(OptionalInt.empty());
    }

    @Test
    void shouldReturnConfiguredTimeouts() {
        config.putSingle(HTTP_CONNECT_TIMEOUT_PROPERTY, "3000");
        config.putSingle(HTTP_READ_TIMEOUT_PROPERTY, " 5000 ");

        assertThat(migrationConfiguration.getHttpConnectTimeoutMillis())
                .isEqualTo(OptionalInt.of(3000));
        assertThat(migrationConfiguration.getHttpReadTimeoutMillis())
                .isEqualTo(OptionalInt.of(5000));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-1", "1.5"})
    void shouldRejectInvalidTimeouts(String value) {
        config.putSingle(HTTP_CONNECT_TIMEOUT_PROPERTY, value);
        config.putSingle(HTTP_READ_TIMEOUT_PROPERTY, value);

        assertThatThrownBy(() -> migrationConfiguration.getHttpConnectTimeoutMillis())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> migrationConfiguration.getHttpReadTimeoutMillis())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldNameFieldLabelGivenTimeoutRejected() {
        config.putSingle(HTTP_CONNECT_TIMEOUT_PROPERTY, "5s");
        config.putSingle(HTTP_READ_TIMEOUT_PROPERTY, "5s");

        assertThatThrownBy(() -> migrationConfiguration.getHttpConnectTimeoutMillis())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("'Rest client connect timeout (ms)' must be a positive number of milliseconds");

        assertThatThrownBy(() -> migrationConfiguration.getHttpReadTimeoutMillis())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("'Rest client read timeout (ms)' must be a positive number of milliseconds");
    }
}
