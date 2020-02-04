package com.danielfrak.code.keycloak.providers.rest.fakes;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class FakeUserRepository {

    private final List<FakeUser> users;

    public FakeUserRepository() {
        users = Arrays.asList(
                new FakeUser("demo", "demo", "John", "Smith"),
                new FakeUser("test@test.com", "test", "Adam", "Boater"),
                new FakeUser("other", "other", null, null)
        );
    }

    public Optional<FakeUser> findByUsername(String username) {
        return users.stream()
                .filter(u -> u.getUsername().equals(username))
                .findFirst();
    }

    public Optional<FakeUser> findByEmail(String email) {
        return users.stream()
                .filter(u -> u.getEmail().equals(email))
                .findFirst();
    }
}
