package com.danielfrak.code.keycloak.providers.rest.remote.usermodel;

import com.danielfrak.code.keycloak.providers.rest.remote.LegacyTotp;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.keycloak.models.credential.OTPCredentialModel;

import java.util.Arrays;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TotpValidationServiceTest {

    private final TotpValidationService service = new TotpValidationService();

    @Nested
    class ValidateTotps {

        @Test
        void shouldCreateCredentialForUtf8Secret() {
            var device = new LegacyTotp("secret", "Authenticator", 6, 30, "HmacSHA1", null);

            var result = service.validateTotps(List.of(device));

            assertThat(result).singleElement().satisfies(credential -> {
                assertThat(credential.getOTPSecretData().getValue()).isEqualTo(device.secret());
                assertThat(credential.getUserLabel()).isEqualTo(device.name());
                assertThat(credential.getOTPCredentialData().getDigits()).isEqualTo(device.digits());
                assertThat(credential.getOTPCredentialData().getPeriod()).isEqualTo(device.period());
                assertThat(credential.getOTPCredentialData().getAlgorithm()).isEqualTo(device.algorithm());
            });
        }

        @ParameterizedTest
        @ValueSource(strings = {"ONSWG4TFOQ======", "ONSWG4TFOQ", "onswg4tfoq======"})
        void shouldNormalizeEquivalentBase32SecretEncodings(String secret) {
            var device = new LegacyTotp(secret, "Authenticator", 6, 30, "HmacSHA1", "BASE32");

            var result = service.validateTotps(List.of(device));

            assertThat(result).singleElement().satisfies(credential -> {
                assertThat(credential.getDecodedSecret()).isEqualTo("secret".getBytes(StandardCharsets.UTF_8));
            });
        }

        @Test
        void shouldRejectMissingTotpList() {
            assertThatThrownBy(() -> service.validateTotps(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest
        @MethodSource("invalidDevices")
        void shouldRejectInvalidDevice(LegacyTotp device) {
            var devices = Arrays.asList(device);

            assertThatThrownBy(() -> service.validateTotps(devices))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        private static Stream<LegacyTotp> invalidDevices() {
            return Stream.of(
                    null,
                    new LegacyTotp(null, "Device", 6, 30, "HmacSHA1", null),
                    new LegacyTotp(" ", "Device", 6, 30, "HmacSHA1", null),
                    new LegacyTotp("secret", "Device", 0, 30, "HmacSHA1", null),
                    new LegacyTotp("secret", "Device", 6, 0, "HmacSHA1", null),
                    new LegacyTotp("secret", "Device", 6, 30, null, null),
                    new LegacyTotp("secret", "Device", 6, 30, "unsupported", null),
                    new LegacyTotp("secret", "Device", 6, 30, "HmacSHA1", "unsupported"),
                    new LegacyTotp("!invalid!", "Device", 6, 30, "HmacSHA1", "BASE32"),
                    new LegacyTotp("ONSWG4TF=OQ", "Device", 6, 30, "HmacSHA1", "BASE32"),
                    new LegacyTotp("\uFF2FNSWG4TFOQ", "Device", 6, 30, "HmacSHA1", "BASE32"),
                    new LegacyTotp("AB", "Device", 6, 30, "HmacSHA1", "BASE32"),
                    new LegacyTotp("========", "Device", 6, 30, "HmacSHA1", "BASE32"));
        }
    }
}
