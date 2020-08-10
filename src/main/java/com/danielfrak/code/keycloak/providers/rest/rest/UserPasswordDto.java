package com.danielfrak.code.keycloak.providers.rest.rest;

import java.util.Objects;

public final class UserPasswordDto {

    private String password;

    public UserPasswordDto() {
    }

    public UserPasswordDto(String password) {
        this.password = password;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        UserPasswordDto that = (UserPasswordDto) o;

        return Objects.equals(password, that.password);
    }

    @Override
    public int hashCode() {
        return Objects.hash(password);
    }
}
