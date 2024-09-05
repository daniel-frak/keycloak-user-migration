package com.danielfrak.code.keycloak.providers.rest.remote;

import java.util.Objects;

public class LegacyTotp {
    private String base32Secret;
    private String name;

    public String getSecret() {
        return this.base32Secret;
    }

    public String setSecret(String secret) {
        return this.base32Secret = secret;
    }

    public String getName() {
        return this.name;
    }

    public String setName(String name) {
        return this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        LegacyTotp legacyTotp = (LegacyTotp) o;

        return Objects.equals(base32Secret, legacyTotp.base32Secret) &&
               Objects.equals(name, legacyTotp.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, base32Secret);
    }
}
