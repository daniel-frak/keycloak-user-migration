package com.danielfrak.code.keycloak.providers.rest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.component.ComponentModel;
import org.keycloak.component.ComponentValidationException;
import org.keycloak.models.KeycloakSession;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.*;
import static org.assertj.core.api.Assertions.*;
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

    @Nested
    class GetConfigProperties {

        @Test
        void getConfigPropertiesShouldReturnProperties() {
            assertThat(legacyProviderFactory.getConfigProperties())
                    .isEqualTo(ConfigurationProperties.getConfigProperties());
        }
    }

    @Nested
    class Create {

        @Test
        void shouldReturnProvider() {
            final MultivaluedHashMap<String, String> config = new MultivaluedHashMap<>();
            config.putSingle(URI_PROPERTY, "http://localhost");
            modelIsConfigured(config);

            var provider = legacyProviderFactory.create(session, model);

            assertThat(provider).isNotNull();
        }
    }

    private void modelIsConfigured(MultivaluedHashMap<String, String> config) {
        when(model.getConfig())
                .thenReturn(config);
    }

    @Nested
    class GetId {

        @Test
        void getIdShouldReturnProviderName() {
            assertThat(legacyProviderFactory.getId()).isEqualTo(PROVIDER_NAME);
        }
    }

    @Nested
    class ValidateConfiguration {

        @Test
        void shouldAllowExistingConfiguration() {
            modelIsConfigured(new MultivaluedHashMap<>());

            assertThatCode(() -> legacyProviderFactory.validateConfiguration(session, null, model))
                    .doesNotThrowAnyException();
        }

        @Test
        void shouldRequireRetainingFederationLinkForTotpRefresh() {
            var config = new MultivaluedHashMap<String, String>();
            config.putSingle(UPDATE_USER_TOTPS_ON_LOGIN, "true");
            modelIsConfigured(config);

            assertThatThrownBy(() -> legacyProviderFactory.validateConfiguration(session, null, model))
                    .isInstanceOf(ComponentValidationException.class);

            config.putSingle(SEVER_FEDERATION_LINK, "true");
            assertThatThrownBy(() -> legacyProviderFactory.validateConfiguration(session, null, model))
                    .isInstanceOf(ComponentValidationException.class);

            config.putSingle(SEVER_FEDERATION_LINK, "false");
            assertThatCode(() -> legacyProviderFactory.validateConfiguration(session, null, model))
                    .doesNotThrowAnyException();
        }

        @ParameterizedTest
        @ValueSource(strings = {HTTP_CONNECT_TIMEOUT_PROPERTY, HTTP_READ_TIMEOUT_PROPERTY})
        void shouldRejectInvalidHttpTimeouts(String property) {
            var config = new MultivaluedHashMap<String, String>();
            config.putSingle(property, "abc");
            modelIsConfigured(config);

            assertThatThrownBy(() -> legacyProviderFactory.validateConfiguration(session, null, model))
                    .hasCauseInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {HTTP_CONNECT_TIMEOUT_PROPERTY, HTTP_READ_TIMEOUT_PROPERTY})
        void shouldAcceptValidHttpTimeouts(String property) {
            var config = new MultivaluedHashMap<String, String>();
            config.putSingle(property, "3000");
            modelIsConfigured(config);

            assertThatCode(() -> legacyProviderFactory.validateConfiguration(session, null, model))
                    .doesNotThrowAnyException();
        }
    }
}
