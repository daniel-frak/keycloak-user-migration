package com.danielfrak.code.keycloak.providers.test.fakes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FakeUser {

    private final String username;
    private final String email;
    private final String password;
    private final String firstName;
    private final String lastName;
    private final boolean isEnabled;
    private final boolean isEmailVerified;
    private final Map<String, List<String>> attributes;
    private final List<String> roles;

    public FakeUser(String username, String password, String firstName, String lastName) {
        this.username = username;
        this.email = username;
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
        this.isEnabled = true;
        this.isEmailVerified = true;
        this.attributes = new HashMap<>();
        this.roles = new ArrayList<>();
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public boolean isEmailVerified() {
        return isEmailVerified;
    }

    public Map<String, List<String>> getAttributes() {
        return attributes;
    }

    public List<String> getRoles() {
        return roles;
    }
}
