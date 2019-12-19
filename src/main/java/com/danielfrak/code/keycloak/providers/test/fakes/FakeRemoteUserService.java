package com.danielfrak.code.keycloak.providers.test.fakes;

import com.danielfrak.code.keycloak.providers.test.ConfigurationProperties;
import org.keycloak.component.ComponentModel;

import java.util.Optional;

public class FakeRemoteUserService {

    private final FakeUserRepository userRepository;
    private final ComponentModel model;

    public FakeRemoteUserService(FakeUserRepository userRepository, ComponentModel model) {
        this.userRepository = userRepository;
        this.model = model;
    }

    public Optional<FakeUser> findByUsername(String username) {
        if(getCustomUserName().equals(username)) {
            return Optional.of(new FakeUser(getCustomUserName(), getCustomPassword(),
                    "Custom", "Customowski"));
        }
        return userRepository.findByUsername(username);
    }

    private String getCustomPassword() {
        return model.getConfig().getFirst(ConfigurationProperties.CUSTOM_USER_PASSWORD);
    }

    private String getCustomUserName() {
        return model.getConfig().getFirst(ConfigurationProperties.CUSTOM_USER_NAME);
    }

    public Optional<FakeUser> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public boolean validatePassword(String username, String password) {
        if(getCustomUserName().equals(username)) {
            return getCustomPassword().equals(password);
        }
        var userPassword = userRepository.findByUsername(username).orElseThrow(RuntimeException::new)
                .getPassword();
        return password.equals(userPassword);
    }
}
