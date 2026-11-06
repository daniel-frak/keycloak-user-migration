package com.danielfrak.code.keycloak.providers.rest;

import com.danielfrak.code.keycloak.providers.rest.exceptions.RestUserProviderException;
import com.danielfrak.code.keycloak.providers.rest.rest.http.HttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.BeforeEach;
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

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;

import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.HTTP_CONNECT_TIMEOUT_PROPERTY;
import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.HTTP_READ_TIMEOUT_PROPERTY;
import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.PROVIDER_NAME;
import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.URI_PROPERTY;
import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.UPDATE_USER_TOTPS_ON_LOGIN;
import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.SEVER_FEDERATION_LINK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static java.time.Duration.ofSeconds;
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

    @Test
    void shouldLeaveHttpTimeoutsAtHttpClientDefaultWhenNotConfigured() {
        when(model.getConfig()).thenReturn(new MultivaluedHashMap<>());

        var requestConfig = LegacyProviderFactory.createRequestConfig(new MigrationConfiguration(model));

        assertEquals(-1, requestConfig.getConnectTimeout());
        assertEquals(-1, requestConfig.getSocketTimeout());
    }

    @Test
    void shouldApplyConfiguredHttpTimeouts() {
        var config = new MultivaluedHashMap<String, String>();
        config.putSingle(HTTP_CONNECT_TIMEOUT_PROPERTY, "3000");
        config.putSingle(HTTP_READ_TIMEOUT_PROPERTY, "5000");
        when(model.getConfig()).thenReturn(config);

        var requestConfig = LegacyProviderFactory.createRequestConfig(new MigrationConfiguration(model));

        assertEquals(3000, requestConfig.getConnectTimeout());
        assertEquals(5000, requestConfig.getSocketTimeout());
    }

    @Test
    void shouldIgnoreInvalidStoredHttpTimeoutsInsteadOfFailing() {
        var config = new MultivaluedHashMap<String, String>();
        config.putSingle(HTTP_CONNECT_TIMEOUT_PROPERTY, "5s");
        config.putSingle(HTTP_READ_TIMEOUT_PROPERTY, "4000");
        when(model.getConfig()).thenReturn(config);

        var requestConfig = LegacyProviderFactory.createRequestConfig(new MigrationConfiguration(model));

        assertEquals(-1, requestConfig.getConnectTimeout());
        assertEquals(4000, requestConfig.getSocketTimeout());
    }

    @Test
    void shouldAbortRequestWhenLegacySystemDoesNotRespondInTime() throws IOException {
        var config = new MultivaluedHashMap<String, String>();
        config.putSingle(HTTP_READ_TIMEOUT_PROPERTY, "200");
        when(model.getConfig()).thenReturn(config);

        try (var mockWebServer = new MockWebServer()) {
            mockWebServer.enqueue(new MockResponse().setHeadersDelay(10, TimeUnit.SECONDS));
            mockWebServer.start();
            var uri = mockWebServer.url("/").toString();
            var httpClient = new HttpClient(
                    LegacyProviderFactory.createHttpClientBuilder(new MigrationConfiguration(model)));

            var exception = assertTimeoutPreemptively(ofSeconds(5),
                    () -> assertThrows(RestUserProviderException.class, () -> httpClient.get(uri)));
            assertInstanceOf(SocketTimeoutException.class, exception.getCause());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {HTTP_CONNECT_TIMEOUT_PROPERTY, HTTP_READ_TIMEOUT_PROPERTY})
    void shouldRejectInvalidHttpTimeouts(String property) {
        var config = new MultivaluedHashMap<String, String>();
        when(model.getConfig()).thenReturn(config);

        config.putSingle(property, "abc");
        assertThrows(ComponentValidationException.class,
                () -> legacyProviderFactory.validateConfiguration(session, null, model));

        config.putSingle(property, "0");
        assertThrows(ComponentValidationException.class,
                () -> legacyProviderFactory.validateConfiguration(session, null, model));

        config.putSingle(property, "3000");
        assertDoesNotThrow(() -> legacyProviderFactory.validateConfiguration(session, null, model));
    }
}
