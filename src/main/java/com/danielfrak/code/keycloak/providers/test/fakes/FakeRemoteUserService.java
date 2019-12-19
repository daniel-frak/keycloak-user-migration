package com.danielfrak.code.keycloak.providers.test.fakes;

import com.danielfrak.code.keycloak.providers.test.fakes.FakeUserRepository;

import java.util.Optional;

public class FakeRemoteUserService {

    private FakeUserRepository userRepository;

    public FakeRemoteUserService(FakeUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<FakeUser> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public Optional<FakeUser> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public boolean userExistsByUsername(String username) {
        return userRepository.findByUsername(username).isPresent();
    }

    public boolean validatePassword(String username, String password) {
        var userPassword = userRepository.findByUsername(username).orElseThrow(RuntimeException::new)
                .getPassword();
        return password.equals(userPassword);
    }
}
