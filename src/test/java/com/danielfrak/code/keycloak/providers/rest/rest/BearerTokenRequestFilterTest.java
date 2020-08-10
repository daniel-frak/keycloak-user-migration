package com.danielfrak.code.keycloak.providers.rest.rest;

import org.jboss.resteasy.specimpl.MultivaluedMapImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.core.MultivaluedMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BearerTokenRequestFilterTest {
    @Mock
    private ClientRequestContext context;

    @Test
    void filter() {
        String token = "secret-api-token";
        String expectedAuthorizationHeader = "Bearer " + token;

        BearerTokenRequestFilter requestFilter = new BearerTokenRequestFilter(token);
        MultivaluedMap<String, Object> headers = new MultivaluedMapImpl<>();
        when(context.getHeaders()).thenReturn(headers);

        requestFilter.filter(context);

        Object result = headers.getFirst("Authorization");
        assertNotNull(result);
        assertEquals(expectedAuthorizationHeader, result.toString());
    }
}
