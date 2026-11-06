package com.danielfrak.code.keycloak.providers.rest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.HTTP_CONNECT_TIMEOUT_PROPERTY;
import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.HTTP_READ_TIMEOUT_PROPERTY;
import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.UPDATE_USER_TOTPS_ON_LOGIN;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.keycloak.provider.ProviderConfigProperty.BOOLEAN_TYPE;
import static org.keycloak.provider.ProviderConfigProperty.STRING_TYPE;

class ConfigurationPropertiesTest {

    @Test
    void shouldGetConfigProperties() {
        var result = ConfigurationProperties.getConfigProperties();
        assertNotNull(result);
    }

    @Test
    void shouldDisableTotpRefreshByDefault() {
        var property = ConfigurationProperties.getConfigProperties().stream()
                .filter(p -> UPDATE_USER_TOTPS_ON_LOGIN.equals(p.getName()))
                .findFirst().orElseThrow();

        assertEquals(BOOLEAN_TYPE, property.getType());
        assertFalse((Boolean) property.getDefaultValue());
    }

    @Test
    void shouldHaveNoHttpTimeoutsByDefault() {
        var timeoutProperties = ConfigurationProperties.getConfigProperties().stream()
                .filter(p -> HTTP_CONNECT_TIMEOUT_PROPERTY.equals(p.getName())
                        || HTTP_READ_TIMEOUT_PROPERTY.equals(p.getName()))
                .toList();

        assertEquals(2, timeoutProperties.size());
        timeoutProperties.forEach(property -> {
            assertEquals(STRING_TYPE, property.getType());
            assertNull(property.getDefaultValue());
        });
    }
}
