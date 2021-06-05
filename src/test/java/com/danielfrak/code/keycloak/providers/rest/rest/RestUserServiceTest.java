package com.danielfrak.code.keycloak.providers.rest.rest;

import com.danielfrak.code.keycloak.providers.rest.remote.LegacyUser;
import org.jboss.resteasy.client.jaxrs.BasicAuthentication;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.component.ComponentModel;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.ws.rs.client.Client;
import javax.ws.rs.core.Response;

import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RestUserServiceTest {

    private RestUserService restUserService;

    @Mock
    private ComponentModel model;

    @Mock
    private RestUserClient client;

    @Mock
    private Client restEasyClient;

    @BeforeEach
    void setUp() {
        var uri = "someUri";
        var config = new MultivaluedHashMap<String, String>();
        config.putSingle(URI_PROPERTY, uri);
        var resteasyWebTarget = mock(ResteasyWebTarget.class);

        when(model.getConfig()).thenReturn(config);
        when(restEasyClient.target(uri))
                .thenReturn(resteasyWebTarget);
        when(resteasyWebTarget.proxy(RestUserClient.class))
                .thenReturn(client);

        restUserService = new RestUserService(model, restEasyClient);
    }

    @Test
    void shouldRegisterBasicAuthRequestFilterIfBasicAuthEnabledAndCredentialsNotEmpty() {
        model.getConfig().add(API_HTTP_BASIC_USERNAME_PROPERTY, "someUser");
        model.getConfig().add(API_HTTP_BASIC_PASSWORD_PROPERTY, "somePassword");
        model.getConfig().add(API_HTTP_BASIC_ENABLED_PROPERTY, "true");
        restUserService = new RestUserService(model, restEasyClient);
        ArgumentCaptor<Object> filterCaptor = ArgumentCaptor.forClass(Object.class);

        verify(restEasyClient).register(filterCaptor.capture());

        assertTrue(filterCaptor.getValue() instanceof BasicAuthentication);
    }



    @Nested
    class ShouldNotRegisterBasicAuthRequestFilter {

        @ParameterizedTest
        @CsvSource(
                value = {
                        "someUser,somePassword,false'", // deactivated
                        "someUser,'',true", // activated, password empty
                        "someUser,null,true", // activated, password null
                        "'',somePassword,true", // activated, user empty
                        "null,somePassword,true", // activated, user null
                        "'','',true", // activated, both empty
                        "null,null,true", // activated, both null
                },
                nullValues = {"null"}
        )
        void ifBasicAuthDisabledOrCredentialsEmptyOrNull(String userName, String password, String basicAuthEnabled ) {
            model.getConfig().add(API_HTTP_BASIC_USERNAME_PROPERTY, userName);
            model.getConfig().add(API_HTTP_BASIC_PASSWORD_PROPERTY, password);
            model.getConfig().add(API_HTTP_BASIC_ENABLED_PROPERTY, basicAuthEnabled);
            restUserService = new RestUserService(model, restEasyClient);

            verify(restEasyClient, never()).register(any());
        }

    }

    @Test
    void shouldRegisterBearerTokenRequestFilterIfTokenAuthEnabledAndTokenNotEmpty() {
        model.getConfig().add(API_TOKEN_PROPERTY, "someToken");
        model.getConfig().add(API_TOKEN_ENABLED_PROPERTY, "true");
        restUserService = new RestUserService(model, restEasyClient);
        ArgumentCaptor<Object> filterCaptor = ArgumentCaptor.forClass(Object.class);

        verify(restEasyClient).register(filterCaptor.capture());

        assertTrue(filterCaptor.getValue() instanceof BearerTokenRequestFilter);
    }

    @Nested
    class ShouldNotRegisterBearerTokenRequestFilter {

        @Test
        void ifTokenAuthDisabledAndTokenNotEmpty() {
            model.getConfig().add(API_TOKEN_PROPERTY, "someToken");
            model.getConfig().add(API_TOKEN_ENABLED_PROPERTY, "false");
            restUserService = new RestUserService(model, restEasyClient);

            verify(restEasyClient, never()).register(any());
        }

        @ParameterizedTest
        @CsvSource(
                value = {
                        "true,''", // empty value
                        "true,null", // null
                },
                nullValues = {"null"}
        )
        void ifTokenNullOrEmpty(String tokenEnabled, String tokenValue) {
            model.getConfig().add(API_TOKEN_PROPERTY, tokenValue);
            model.getConfig().add(API_TOKEN_ENABLED_PROPERTY, tokenEnabled);
            restUserService = new RestUserService(model, restEasyClient);

            verify(restEasyClient, never()).register(any());
        }

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
