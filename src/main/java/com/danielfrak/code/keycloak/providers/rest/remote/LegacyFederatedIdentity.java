package com.danielfrak.code.keycloak.providers.rest.remote;

import java.util.Objects;

public class LegacyFederatedIdentity {

    private String identityProvider;
    private String userId;
    private String userName;
    private String token;

    public String getIdentityProvider() {
        return identityProvider;
    }

    public void setIdentityProvider(String identityProvider) {
        this.identityProvider = identityProvider;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        LegacyFederatedIdentity legacyFederatedIdentity = (LegacyFederatedIdentity) o;

        return Objects.equals(identityProvider, legacyFederatedIdentity.identityProvider)
                && Objects.equals(userId, legacyFederatedIdentity.userId)
                && Objects.equals(userName, legacyFederatedIdentity.userName)
                && Objects.equals(token, legacyFederatedIdentity.token);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                identityProvider,
                userId,
                userName,
                token
        );
    }
}
