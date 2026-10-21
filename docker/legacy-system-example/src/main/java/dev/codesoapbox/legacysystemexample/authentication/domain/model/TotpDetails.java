package dev.codesoapbox.legacysystemexample.authentication.domain.model;

public record TotpDetails(
        String secret,
        String name,
        int digits,
        int period,
        String algorithm,
        String encoding
) {
}
