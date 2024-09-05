package com.danielfrak.code.keycloak.providers.rest.remote;


import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LegacyTotpTest {
    @Test
    void shouldGetAndSetName() {
        var totp = new LegacyTotp();
        var expectedValue = "value1";
        totp.setName(expectedValue);
        assertEquals(expectedValue, totp.getName());
    }


    @Test
    void shouldGetAndSetBase32Secret() {
        var totp = new LegacyTotp();
        var expectedValue = "value1";
        totp.setSecret(expectedValue);
        assertEquals(expectedValue, totp.getSecret());
    }

    @Test
    void testEquals() {
        EqualsVerifier.simple().forClass(LegacyTotp.class)
                .verify();
    }
}
