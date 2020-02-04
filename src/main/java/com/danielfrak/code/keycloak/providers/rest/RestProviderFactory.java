package com.danielfrak.code.keycloak.providers.rest;

import com.danielfrak.code.keycloak.providers.rest.rest.RestUserService;
import org.jboss.logging.Logger;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.storage.UserStorageProviderFactory;

import java.util.List;

import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.PROVIDER_NAME;

public class RestProviderFactory implements UserStorageProviderFactory<RestProvider> {

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return ConfigurationProperties.getConfigProperties();
    }

    @Override
    public RestProvider create(KeycloakSession session, ComponentModel model) {
        return new RestProvider(session, model, new RestUserService(model));
    }

    @Override
    public String getId() {
        return PROVIDER_NAME;
    }
}
