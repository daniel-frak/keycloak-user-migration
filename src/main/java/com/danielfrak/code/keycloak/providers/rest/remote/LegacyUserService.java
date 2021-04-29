package com.danielfrak.code.keycloak.providers.rest.remote;

import java.util.Optional;

/**
 * Interface to be implemented by Legacy user provider.
 */
public interface LegacyUserService {

    /**
     * Find user by email address.
     *
     * @param email email address to search user by.
     * @return Optional of legacy user.
     */
    Optional<LegacyUser> findByEmail(String email);

    /**
     * Find user by username.
     *
     * @param username username to search user by.
     * @return Optional of legacy user.
     */
    Optional<LegacyUser> findByUsername(String username);

    /**
     * Validate given password in legacy user provider.
     *
     * @param username username to validate password for.
     * @param password the password to validate.
     * @return true if password is valid.
     */
    boolean isPasswordValid(String username, String password);
}
