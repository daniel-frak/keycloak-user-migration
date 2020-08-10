package com.danielfrak.code.keycloak.providers.rest.rest;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserPasswordDtoTest {

    @Test
    void shouldConstructWithPassword() {
        var password = "somePassword";
        var dto = new UserPasswordDto(password);
        assertEquals(password, dto.getPassword());
    }

    @Test
    void shouldSetAndGetPassword() {
        var password = "somePassword";
        var dto = new UserPasswordDto();
        dto.setPassword(password);
        assertEquals(password, dto.getPassword());
    }

    @Test
    void equalsContract() {
        EqualsVerifier.simple().forClass(UserPasswordDto.class)
                .verify();
    }
}