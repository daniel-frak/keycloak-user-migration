package com.danielfrak.code.keycloak.providers.rest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.component.ComponentModel;
import org.keycloak.component.ComponentValidationException;
import org.keycloak.models.KeycloakSession;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.PROVIDER_NAME;
import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.URI_PROPERTY;
import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.UPDATE_USER_TOTPS_ON_LOGIN;
import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.SEVER_FEDERATION_LINK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegacyProviderFactoryTest {

    private LegacyProviderFactory legacyProviderFactory;

    @Mock
    private KeycloakSession session;

    @Mock
    private ComponentModel model;

    @BeforeEach
    void setUp() {
        legacyProviderFactory = new LegacyProviderFactory();
    }

    @Test
    void getConfigProperties() {
        assertEquals(ConfigurationProperties.getConfigProperties(), legacyProviderFactory.getConfigProperties());
    }

    @Test
    void create() {
        final MultivaluedHashMap<String, String> config = new MultivaluedHashMap<>();
        config.putSingle(URI_PROPERTY, "http://localhost");
        when(model.getConfig())
                .thenReturn(config);
        var provider = legacyProviderFactory.create(session, model);
        assertNotNull(provider);
    }

    @Test
    void getId() {
        assertEquals(PROVIDER_NAME, legacyProviderFactory.getId());
    }

    @Test
    void shouldAllowExistingConfiguration() {
        when(model.getConfig()).thenReturn(new MultivaluedHashMap<>());

        assertDoesNotThrow(() -> legacyProviderFactory.validateConfiguration(session, null, model));
    }

    @Test
    void shouldRequireRetainingFederationLinkForTotpRefresh() {
        var config = new MultivaluedHashMap<String, String>();
        config.putSingle(UPDATE_USER_TOTPS_ON_LOGIN, "true");
        when(model.getConfig()).thenReturn(config);

        assertThrows(ComponentValidationException.class,
                () -> legacyProviderFactory.validateConfiguration(session, null, model));

        config.putSingle(SEVER_FEDERATION_LINK, "true");
        assertThrows(ComponentValidationException.class,
                () -> legacyProviderFactory.validateConfiguration(session, null, model));

        config.putSingle(SEVER_FEDERATION_LINK, "false");
        assertDoesNotThrow(() -> legacyProviderFactory.validateConfiguration(session, null, model));
    }
}