package com.danielfrak.code.keycloak.providers.rest;

import com.danielfrak.code.keycloak.providers.rest.remote.LegacyUser;
import com.danielfrak.code.keycloak.providers.rest.remote.UserModelFactory;
import com.danielfrak.code.keycloak.providers.rest.rest.http.HttpClient;
import com.danielfrak.code.keycloak.providers.rest.rest.RestUserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.storage.UserStorageProviderFactory;

import java.util.List;

import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.PROVIDER_NAME;

public class LegacyProviderFactory implements UserStorageProviderFactory<LegacyProvider> {

    private Cache<String, LegacyUser> cache;
    private long cacheSize = 2000;
    private long cacheExp = 120;

    private static final Logger LOG = Logger.getLogger(LegacyProvider.class);
    @Override
    public void init(Config.Scope config) {
        String size = System.getProperty("migration.cache.size");
        if (size != null) {
            try {
                cacheSize = Long.valueOf(size);
            } catch(Exception e) {
                LOG.error("Unable to parse cache size: " + size);
            }
        }
        String exp = System.getProperty("migration.cache.expiry");
        if (exp != null) {
            try {
             cacheExp = Long.valueOf(exp);
            } catch(Exception e) {
                LOG.error("Unable to parse cache exp: " + exp);
            }
        }
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        LOG.debugf("User migration cache initialized with size: %s, exp: %s seconds..", cacheSize, cacheExp);
        cache = Caffeine.newBuilder()
            .maximumSize(cacheSize)
            .expireAfterWrite(cacheExp, TimeUnit.SECONDS)
            .build();
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return ConfigurationProperties.getConfigProperties();
    }

    @Override
    public LegacyProvider create(KeycloakSession session, ComponentModel model) {

        var userModelFactory = new UserModelFactory(session, model);
        var httpClient = new HttpClient(HttpClientBuilder.create().setRedirectStrategy(new LaxRedirectStrategy()));
        var restService = new RestUserService(model, httpClient, new ObjectMapper(), cache);
        return new LegacyProvider(session, restService, userModelFactory, model);
    }

    @Override
    public String getId() {
        return PROVIDER_NAME;
    }
}
