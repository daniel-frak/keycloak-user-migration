package com.danielfrak.code.keycloak.providers.rest.remote;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyUserTest {

    @Test
    void shouldGetAndSetUsername() {
        var user = new LegacyUser();
        var expectedValue = "someValue";
        user.setUsername(expectedValue);
        assertEquals(expectedValue, user.getUsername());
    }

    @Test
    void shouldGetAndSetEmail() {
        var user = new LegacyUser();
        var expectedValue = "someValue";
        user.setEmail(expectedValue);
        assertEquals(expectedValue, user.getEmail());
    }

    @Test
    void shouldGetAndSetFirstName() {
        var user = new LegacyUser();
        var expectedValue = "someValue";
        user.setFirstName(expectedValue);
        assertEquals(expectedValue, user.getFirstName());
    }

    @Test
    void shouldGetAndSetLastName() {
        var user = new LegacyUser();
        var expectedValue = "someValue";
        user.setLastName(expectedValue);
        assertEquals(expectedValue, user.getLastName());
    }

    @Test
    void shouldGetAndSetEnabled() {
        var user = new LegacyUser();
        user.setEnabled(true);
        assertTrue(user.isEnabled());
    }

    @Test
    void shouldGetAndSetEmailVerified() {
        var user = new LegacyUser();
        user.setEmailVerified(true);
        assertTrue(user.isEmailVerified());
    }

    @Test
    void shouldGetAndSetAttributes() {
        var user = new LegacyUser();
        var expectedValue = Map.of("attribute1", singletonList("value1"));
        user.setAttributes(expectedValue);
        assertEquals(expectedValue, user.getAttributes());
    }

    @Test
    void shouldGetAndSetRoles() {
        var user = new LegacyUser();
        var expectedValue = singletonList("value1");
        user.setRoles(expectedValue);
        assertEquals(expectedValue, user.getRoles());
    }

    @Test
    void shouldGetAndSetGroups() {
        var user = new LegacyUser();
        var expectedValue = singletonList("value1");
        user.setGroups(expectedValue);
        assertEquals(expectedValue, user.getGroups());
    }

    @Test
    void shouldGetAndSetRequiredActions() {
        var user = new LegacyUser();
        var expectedValue = singletonList("value1");
        user.setRequiredActions(expectedValue);
        assertEquals(expectedValue, user.getRequiredActions());
    }

    @Test
    void testEquals() {
        EqualsVerifier.simple().forClass(LegacyUser.class)
                .verify();
    }
}