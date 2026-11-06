package com.danielfrak.code.keycloak.providers.rest;

import com.danielfrak.code.keycloak.providers.rest.remote.usermodel.*;
import com.danielfrak.code.keycloak.providers.rest.rest.http.HttpClient;
import com.danielfrak.code.keycloak.providers.rest.rest.RestUserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.jboss.logging.Logger;
import org.keycloak.component.ComponentModel;
import org.keycloak.component.ComponentValidationException;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.storage.UserStorageProviderFactory;

import java.util.List;
import java.util.OptionalInt;
import java.util.function.Supplier;

import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.PROVIDER_NAME;

public class LegacyProviderFactory implements UserStorageProviderFactory<LegacyProvider> {

    private static final Logger LOG = Logger.getLogger(LegacyProviderFactory.class);

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return ConfigurationProperties.getConfigProperties();
    }

    @Override
    public void validateConfiguration(KeycloakSession session, RealmModel realm, ComponentModel model) {
        var config = new MigrationConfiguration(model);
        if (config.shouldUpdateUserTotpsOnLogin() && config.shouldSeverFederationLink()) {
            throw new ComponentValidationException(
                    "Disable 'Sever federation link after migration' before enabling TOTP refresh.");
        }
        try {
            config.getHttpConnectTimeoutMillis();
            config.getHttpReadTimeoutMillis();
        } catch (IllegalArgumentException e) {
            throw new ComponentValidationException(e.getMessage());
        }
    }

    @Override
    public LegacyProvider create(KeycloakSession session, ComponentModel model) {
        var legacyMappingParser = new LegacyMappingParser();
        var wildcardPatternFactory = new WildcardPatternFactory();
        var config = new MigrationConfiguration(model);
        var userModelFactory = new UserModelFactory(
                session,
                config,
                new RoleMigrationService(model, legacyMappingParser, wildcardPatternFactory),
                new GroupMigrationService(model, legacyMappingParser, wildcardPatternFactory),
                new TotpValidationService()
        );
        var httpClient = new HttpClient(createHttpClientBuilder(config));
        var restService = new RestUserService(model, httpClient, new ObjectMapper());

        var localUserLookup = new LocalUserLookup(session);
        var userMigrationService = new UserMigrationService(
                session, restService, localUserLookup, userModelFactory, config);
        var credentialValidationService = new CredentialValidationService(session, restService, config);

        return new LegacyProvider(userMigrationService, credentialValidationService, config);
    }

    static HttpClientBuilder createHttpClientBuilder(MigrationConfiguration config) {
        return HttpClientBuilder.create()
                .setRedirectStrategy(new LaxRedirectStrategy())
                .setDefaultRequestConfig(createRequestConfig(config));
    }

    static RequestConfig createRequestConfig(MigrationConfiguration config) {
        var requestConfig = RequestConfig.custom();
        timeoutOrDefault(config::getHttpConnectTimeoutMillis).ifPresent(requestConfig::setConnectTimeout);
        timeoutOrDefault(config::getHttpReadTimeoutMillis).ifPresent(requestConfig::setSocketTimeout);
        return requestConfig.build();
    }

    /**
     * A stored value that skipped {@link #validateConfiguration} (e.g. an old export) must not break every login:
     * it is ignored with a warning instead.
     */
    private static OptionalInt timeoutOrDefault(Supplier<OptionalInt> timeout) {
        try {
            return timeout.get();
        } catch (IllegalArgumentException e) {
            LOG.warnf("Ignoring invalid HTTP timeout, using the default instead: %s", e.getMessage());
            return OptionalInt.empty();
        }
    }

    @Override
    public String getId() {
        return PROVIDER_NAME;
    }
}
