package com.danielfrak.code.keycloak.providers.rest;

import com.danielfrak.code.keycloak.providers.rest.remote.UserModelFactory;
import com.danielfrak.code.keycloak.providers.rest.rest.RestUserService;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.storage.UserStorageProviderFactory;

import javax.ws.rs.client.ClientBuilder;
import java.util.List;

import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.PROVIDER_NAME;

public class LegacyProviderFactory implements UserStorageProviderFactory<LegacyProvider> {

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return ConfigurationProperties.getConfigProperties();
    }

    @Override
    public LegacyProvider create(KeycloakSession session, ComponentModel model) {
        var userModelFactory = new UserModelFactory(session, model);
        var restService = new RestUserService(model, ClientBuilder.newClient());
        return new LegacyProvider(session, restService, userModelFactory, model);
    }

    @Override
    public String getId() {
        return PROVIDER_NAME;
    }
}
