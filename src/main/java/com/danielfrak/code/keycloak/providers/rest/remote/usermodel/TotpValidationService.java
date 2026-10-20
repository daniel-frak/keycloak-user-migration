package com.danielfrak.code.keycloak.providers.rest.remote.usermodel;

import com.danielfrak.code.keycloak.providers.rest.remote.LegacyTotp;
import org.keycloak.models.credential.OTPCredentialModel;
import org.keycloak.models.utils.Base32;

import java.util.List;

public class TotpValidationService {

    private static final int TOTP_DIGITS_SHORT = 6;
    private static final int TOTP_DIGITS_LONG = 8;
    private static final List<String> TOTP_ALGORITHMS = List.of("HmacSHA1", "HmacSHA256", "HmacSHA512");
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz234567";

    public List<OTPCredentialModel> validateTotps(List<LegacyTotp> totps) {
        if (totps == null) {
            throw new IllegalArgumentException("TOTP refresh requires an explicit totps list.");
        }
        return totps.stream().map(this::validatedTotpCredential).toList();
    }

    public OTPCredentialModel toTotpCredential(LegacyTotp totp) {
        var credential = OTPCredentialModel.createTOTP(
                totp.secret(), totp.digits(), totp.period(), totp.algorithm(), totp.encoding());
        credential.setUserLabel(totp.name());
        return credential;
    }

    private OTPCredentialModel validatedTotpCredential(LegacyTotp totp) {
        if (totp == null || totp.secret() == null || totp.secret().isBlank()) {
            throw new IllegalArgumentException("Invalid legacy TOTP configuration.");
        }
        validateTotpParameters(totp);
        if (totp.encoding() == null) {
            return toTotpCredential(totp);
        }
        if (!"BASE32".equalsIgnoreCase(totp.encoding())) {
            throw new IllegalArgumentException("Unsupported legacy TOTP secret encoding.");
        }
        var secret = validatedBase32Secret(totp.secret());
        return toTotpCredential(new LegacyTotp(
                secret, totp.name(), totp.digits(), totp.period(), totp.algorithm(), "BASE32"));
    }

    private void validateTotpParameters(LegacyTotp totp) {
        if (totp.digits() != TOTP_DIGITS_SHORT && totp.digits() != TOTP_DIGITS_LONG) {
            throw new IllegalArgumentException("Legacy TOTP digits must be 6 or 8.");
        }
        if (totp.period() <= 0) {
            throw new IllegalArgumentException("Legacy TOTP period must be positive.");
        }
        if (totp.algorithm() == null || !TOTP_ALGORITHMS.contains(totp.algorithm())) {
            throw new IllegalArgumentException("Legacy TOTP algorithm must be HmacSHA1, HmacSHA256 or HmacSHA512.");
        }
    }

    private String validatedBase32Secret(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '=') {
            end--;
        }
        var secret = value.substring(0, end);
        if (secret.length() < 2 || !hasBase32Characters(secret)
                || !Base32.encode(Base32.decode(secret)).equalsIgnoreCase(secret)) {
            throw new IllegalArgumentException("Invalid legacy Base32 TOTP secret.");
        }
        return secret;
    }

    private boolean hasBase32Characters(String secret) {
        for (int index = 0; index < secret.length(); index++) {
            if (BASE32_ALPHABET.indexOf(secret.charAt(index)) < 0) {
                return false;
            }
        }
        return true;
    }
}
