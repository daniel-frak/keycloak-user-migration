package com.danielfrak.code.keycloak.providers.rest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.UPDATE_USER_TOTPS_ON_LOGIN;
import static org.keycloak.provider.ProviderConfigProperty.BOOLEAN_TYPE;

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
}