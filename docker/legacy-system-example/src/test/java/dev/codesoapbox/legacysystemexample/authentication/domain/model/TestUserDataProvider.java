package dev.codesoapbox.legacysystemexample.authentication.domain.model;

public class TestUserDataProvider {

    public UserData full() {
        return UserData.builder()
                .username("username")
                .email("username@email.com")
                .firstName("Name")
                .lastName("Last Name")
                .password("password")
                .build();
    }
}
