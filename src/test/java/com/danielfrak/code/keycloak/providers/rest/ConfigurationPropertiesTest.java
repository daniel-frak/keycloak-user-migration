package com.danielfrak.code.keycloak.providers.rest;

import org.junit.jupiter.api.Test;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.List;

import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.keycloak.provider.ProviderConfigProperty.BOOLEAN_TYPE;
import static org.keycloak.provider.ProviderConfigProperty.STRING_TYPE;

class ConfigurationPropertiesTest {

    @Test
    void shouldGetConfigProperties() {
        List<ProviderConfigProperty> result = ConfigurationProperties.getConfigProperties();

        assertThat(result).isNotNull();
    }

    @Test
    void shouldDisableTotpRefreshByDefault() {
        ProviderConfigProperty property = ConfigurationProperties.getConfigProperties().stream()
                .filter(p -> UPDATE_USER_TOTPS_ON_LOGIN.equals(p.getName()))
                .findFirst().orElseThrow();

        assertThat(property.getType()).isEqualTo(BOOLEAN_TYPE);
        assertThat((Boolean) property.getDefaultValue()).isFalse();
    }

    @Test
    void shouldHaveNoHttpTimeoutsByDefault() {
        List<ProviderConfigProperty> timeoutProperties = ConfigurationProperties.getConfigProperties().stream()
                .filter(p -> HTTP_CONNECT_TIMEOUT_PROPERTY.equals(p.getName())
                        || HTTP_READ_TIMEOUT_PROPERTY.equals(p.getName()))
                .toList();

        assertThat(timeoutProperties).hasSize(2);
        timeoutProperties.forEach(property -> {
            assertThat(property.getType()).isEqualTo(STRING_TYPE);
            assertThat(property.getDefaultValue()).isNull();
        });
    }
}
