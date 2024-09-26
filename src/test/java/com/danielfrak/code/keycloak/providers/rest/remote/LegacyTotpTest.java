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
    void shouldGetAndSetSecret() {
        var totp = new LegacyTotp();
        var expectedValue = "value1";
        totp.setSecret(expectedValue);
        assertEquals(expectedValue, totp.getSecret());
    }

    @Test
    void shouldGetAndSetDigits() {
        var totp = new LegacyTotp();
        var expectedValue = 6;
        totp.setDigits(expectedValue);
        assertEquals(expectedValue, totp.getDigits());
    }

    @Test
    void shouldGetAndSetPeriod() {
        var totp = new LegacyTotp();
        var expectedValue = 30;
        totp.setPeriod(expectedValue);
        assertEquals(expectedValue, totp.getPeriod());
    }

    @Test
    void shouldGetAndSetAlgorithm() {
        var totp = new LegacyTotp();
        var expectedValue = "value1";
        totp.setAlgorithm(expectedValue);
        assertEquals(expectedValue, totp.getAlgorithm());
    }

    @Test
    void shouldGetAndSetEncpding() {
        var totp = new LegacyTotp();
        var expectedValue = "value1";
        totp.setEncoding(expectedValue);
        assertEquals(expectedValue, totp.getEncoding());
    }

    @Test
    void testEquals() {
        EqualsVerifier.simple().forClass(LegacyTotp.class)
                .verify();
    }
}