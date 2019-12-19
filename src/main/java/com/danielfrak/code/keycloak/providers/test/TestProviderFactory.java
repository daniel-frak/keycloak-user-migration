package com.danielfrak.code.keycloak.providers.test;

import com.danielfrak.code.keycloak.providers.test.fakes.FakeRemoteUserService;
import com.danielfrak.code.keycloak.providers.test.fakes.FakeUserRepository;
import org.jboss.logging.Logger;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.storage.UserStorageProviderFactory;

import java.util.List;

import static org.keycloak.provider.ProviderConfigProperty.STRING_TYPE;

public class TestProviderFactory implements UserStorageProviderFactory<TestProvider> {

    public static final String PROVIDER_NAME = "Test User Federation Provider";
    private static final Logger log = Logger.getLogger(TestProviderFactory.class);

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return List.of(
                new ProviderConfigProperty(ConfigurationProperties.CUSTOM_USER_NAME,
                        "Custom user name", "A custom user name to add to provider",
                        STRING_TYPE, null),
                new ProviderConfigProperty(ConfigurationProperties.CUSTOM_USER_PASSWORD,
                        "Custom user password", "Password of the custom user to add to provider",
                        STRING_TYPE, null)
        );
    }

    @Override
    public TestProvider create(KeycloakSession session, ComponentModel model) {
        return new TestProvider(session, model, new FakeRemoteUserService(new FakeUserRepository(), model));
    }

    @Override
    public String getId() {
        return PROVIDER_NAME;
    }
}
