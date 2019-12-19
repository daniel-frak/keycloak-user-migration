package com.danielfrak.code.keycloak.providers.test;

import java.util.regex.Pattern;

public class EmailValidator {

    private final Pattern pattern;

    public EmailValidator() {
        String emailRegex = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";

        pattern = Pattern.compile(emailRegex);
    }

    boolean isValid(String email) {
        if (email == null)
            return false;
        return pattern.matcher(email).matches();
    }
}
