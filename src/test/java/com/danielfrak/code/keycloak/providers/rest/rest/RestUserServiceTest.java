package com.danielfrak.code.keycloak.providers.rest.rest;

import com.danielfrak.code.keycloak.providers.rest.remote.LegacyUser;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.component.ComponentModel;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.ws.rs.client.Client;
import javax.ws.rs.core.Response;

import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.URI_PROPERTY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestUserServiceTest {

    private RestUserService restUserService;

    @Mock
    private ComponentModel model;

    @Mock
    private RestUserClient client;

    @BeforeEach
    void setUp() {
        var uri = "someUri";
        var config = new MultivaluedHashMap<String, String>();
        config.putSingle(URI_PROPERTY, uri);
        var restEasyClient = mock(Client.class);
        var resteasyWebTarget = mock(ResteasyWebTarget.class);

        when(model.getConfig()).thenReturn(config);
        when(restEasyClient.target(uri))
                .thenReturn(resteasyWebTarget);
        when(resteasyWebTarget.proxy(RestUserClient.class))
                .thenReturn(client);

        restUserService = new RestUserService(model, restEasyClient);
    }

    @Test
    void shouldFindByEmail() {
        var email = "someEmail";
        var expectedResult = new LegacyUser();
        var response = mock(Response.class);

        when(client.findByUsername(email))
                .thenReturn(response);
        when(response.getStatus())
                .thenReturn(200);
        when(response.readEntity(LegacyUser.class))
                .thenReturn(expectedResult);

        var result = restUserService.findByEmail(email);
        assertTrue(result.isPresent());
        assertEquals(expectedResult, result.get());
    }

    @Test
    void findByEmailShouldReturnEmptyOptionalIfNotFound() {
        var email = "someEmail";
        var response = mock(Response.class);

        when(client.findByUsername(email))
                .thenReturn(response);
        when(response.getStatus())
                .thenReturn(404);

        var result = restUserService.findByEmail(email);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldFindByUsername() {
        var username = "someUsername";
        var expectedResult = new LegacyUser();
        var response = mock(Response.class);

        when(client.findByUsername(username))
                .thenReturn(response);
        when(response.getStatus())
                .thenReturn(200);
        when(response.readEntity(LegacyUser.class))
                .thenReturn(expectedResult);

        var result = restUserService.findByUsername(username);
        assertTrue(result.isPresent());
        assertEquals(expectedResult, result.get());
    }

    @Test
    void findByUsernameShouldReturnEmptyOptionalIfNotFound() {
        var username = "someUsername";
        var response = mock(Response.class);

        when(client.findByUsername(username))
                .thenReturn(response);
        when(response.getStatus())
                .thenReturn(404);

        var result = restUserService.findByUsername(username);
        assertTrue(result.isEmpty());
    }

    @Test
    void isPasswordValidShouldReturnTrueForValidPassword() {
        var username = "someUsername";
        var somePassword = "somePassword";
        var response = mock(Response.class);

        when(client.validatePassword(username, new UserPasswordDto(somePassword)))
                .thenReturn(response);
        when(response.getStatus())
                .thenReturn(200);

        var result = restUserService.isPasswordValid(username, somePassword);
        assertTrue(result);
    }

    @Test
    void isPasswordValidShouldReturnFalseForInvalidPassword() {
        var username = "someUsername";
        var somePassword = "somePassword";
        var response = mock(Response.class);

        when(client.validatePassword(username, new UserPasswordDto(somePassword)))
                .thenReturn(response);
        when(response.getStatus())
                .thenReturn(403);

        var result = restUserService.isPasswordValid(username, somePassword);
        assertFalse(result);
    }
}