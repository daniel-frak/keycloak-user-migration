package dev.codesoapbox.legacysystemexample.authentication.domain.model;

public class TestUserDataProvider {

    public User full() {
        return User.builder()
                .username("username")
                .email("username@email.com")
                .firstName("Name")
                .lastName("Last Name")
                .password("password")
                .build();
    }
}
