package com.danielfrak.code.keycloak.providers.rest.rest;

import com.danielfrak.code.keycloak.providers.rest.remote.LegacyUserService;
import com.danielfrak.code.keycloak.providers.rest.remote.LegacyUser;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.keycloak.component.ComponentModel;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;
import java.util.Optional;

import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.URI_PROPERTY;

public class RestUserService implements LegacyUserService {

    private final RestUserClient client;

    public RestUserService(ComponentModel model) {
        this.client = buildClient(model.getConfig().getFirst(URI_PROPERTY));
    }

    private RestUserClient buildClient(String uri) {
        var restEasyClient = ClientBuilder.newClient();
        ResteasyWebTarget target = (ResteasyWebTarget) restEasyClient.target(uri);

        return target.proxy(RestUserClient.class);
    }

    @Override
    public Optional<LegacyUser> findByEmail(String email) {
        return findByUsername(email);
    }

    @Override
    public Optional<LegacyUser> findByUsername(String username) {

        final Response response = client.findByUsername(username);
        if (response.getStatus() != 200) {
            return Optional.empty();
        }
        return Optional.ofNullable(response.readEntity(LegacyUser.class));
    }

    @Override
    public boolean validatePassword(String username, String password) {
        final Response response = client.validatePassword(username, new UserPasswordDto(password));
        return response.getStatus() == 200;
    }
}
