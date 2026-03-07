package com.danielfrak.code.keycloak.providers.rest;

import com.danielfrak.code.keycloak.providers.rest.remote.usermodel.*;
import com.danielfrak.code.keycloak.providers.rest.rest.http.HttpClient;
import com.danielfrak.code.keycloak.providers.rest.rest.RestUserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.storage.UserStorageProviderFactory;

import java.util.List;

import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.PROVIDER_NAME;

public class LegacyProviderFactory implements UserStorageProviderFactory<LegacyProvider> {

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return ConfigurationProperties.getConfigProperties();
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
                new GroupMigrationService(model, legacyMappingParser, wildcardPatternFactory)
        );
        var httpClient = new HttpClient(HttpClientBuilder.create().setRedirectStrategy(new LaxRedirectStrategy()));
        var restService = new RestUserService(model, httpClient, new ObjectMapper());

        var localUserLookup = new LocalUserLookup(session);
        var userMigrationService = new UserMigrationService(restService, localUserLookup, userModelFactory, config);
        var credentialValidationService = new CredentialValidationService(session, restService, config);

        return new LegacyProvider(userMigrationService, credentialValidationService, config);
    }

    @Override
    public String getId() {
        return PROVIDER_NAME;
    }
}
