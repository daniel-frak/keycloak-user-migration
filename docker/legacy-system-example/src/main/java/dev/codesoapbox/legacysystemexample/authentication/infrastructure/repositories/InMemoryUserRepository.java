package dev.codesoapbox.legacysystemexample.authentication.infrastructure.repositories;

import dev.codesoapbox.legacysystemexample.authentication.domain.model.UserData;
import dev.codesoapbox.legacysystemexample.authentication.domain.repositories.UserRepository;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class InMemoryUserRepository implements UserRepository {

    private final List<UserData> users;

    public InMemoryUserRepository() {
        this.users = List.of(
                generateUser("Lucy", "Brennan"),
                generateUser("Mark", "Brown"),
                generateUser("Kate", "Thomson"),
                generateUser("John", "Doe")
        );
    }

    private UserData generateUser(String name, String lastName) {
        String username = name.toLowerCase(Locale.ROOT);

        return UserData.builder()
                .username(username)
                .email(username + "@localhost.com")
                .firstName(name)
                .lastName(lastName)
                .password("password")
                .build();
    }

    @Override
    public Optional<UserData> findByUsername(String username) {
        return users.stream()
                .filter(u -> u.getUsername().equals(username))
                .findFirst();
    }

    @Override
    public List<UserData> findAll() {
        return this.users;
    }
}
