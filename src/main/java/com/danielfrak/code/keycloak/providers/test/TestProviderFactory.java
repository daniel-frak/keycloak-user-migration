package com.danielfrak.code.keycloak.providers.test;

import com.danielfrak.code.keycloak.providers.test.fakes.FakeRemoteUserService;
import com.danielfrak.code.keycloak.providers.test.fakes.FakeUserRepository;
import org.jboss.logging.Logger;
import org.keycloak.models.*;

import java.util.Date;
import java.util.Set;

public class TestProviderFactory implements UserFederationProviderFactory {

    private static final Logger log = Logger.getLogger(TestProviderFactory.class);
    public static final String PROVIDER_NAME = "Test User Federation Provider";

    public UserFederationProvider getInstance(KeycloakSession session, UserFederationProviderModel model) {
        return new TestProvider(session, model, new FakeRemoteUserService(new FakeUserRepository()));
    }

    public Set<String> getConfigurationOptions() {
        return Set.of(
                ConfigurationProperties.CUSTOM_USER_NAME,
                ConfigurationProperties.CUSTOM_USER_PASSWORD
        );
    }

    public String getId() {
        return PROVIDER_NAME;
    }

    public UserFederationSyncResult syncAllUsers(KeycloakSessionFactory keycloakSessionFactory, String s,
                                                 UserFederationProviderModel userFederationProviderModel) {
        // Sync all users from the provider storage to Keycloak storage. Alternatively can update existing users
        // or remove keycloak users, which are no longer available in federation storage (depends on the implementation)
        throw new UnsupportedOperationException("This federation provider doesn't support syncAllUsers()");
    }

    public UserFederationSyncResult syncChangedUsers(KeycloakSessionFactory keycloakSessionFactory, String s,
                                                     UserFederationProviderModel userFederationProviderModel,
                                                     Date date) {
        // Sync just changed (added / updated / removed) users from the provider storage to Keycloak storage.
        // This is useful in case your storage supports "changelogs" (Tracking what users changed since
        // a specified date).
        // It's implementation specific to decide what exactly will be changed.
        throw new UnsupportedOperationException("This federation provider doesn't support syncChangedUsers()");
    }

    public UserFederationProvider create(KeycloakSession keycloakSession) {
        // This method is never called and is only an artifact of ProviderFactory.
        // Returning null with no implementation is recommended
        return null;
    }

    public void init(org.keycloak.Config.Scope scope) {
        // Only called once when the factory is first created. This config is pulled from keycloak_server.json
    }

    public void postInit(KeycloakSessionFactory keycloakSessionFactory) {
        // Called after all provider factories have been initialized
    }

    public void close() {
        // This is called when the server shuts down.
    }
}
