package com.danielfrak.code.keycloak.providers.rest;

import java.util.Locale;

public enum UserSyncMode {
    SYNC_FIRST_LOGIN,
    SYNC_EVERY_LOGIN,
    SYNC_EVERY_LOGIN_ONLY_ADD,
    NO_SYNC;

    public boolean shouldImportOnFirstLogin() {
        return this != NO_SYNC;
    }

    public boolean shouldSyncOnLogin() {
        return this == SYNC_EVERY_LOGIN || this == SYNC_EVERY_LOGIN_ONLY_ADD;
    }

    public boolean shouldRemoveMissingOnLogin() {
        return this == SYNC_EVERY_LOGIN;
    }

    public static UserSyncMode fromConfig(String value, UserSyncMode defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        if ("true".equalsIgnoreCase(value.trim())) {
            return SYNC_EVERY_LOGIN;
        }
        if ("false".equalsIgnoreCase(value.trim())) {
            return SYNC_FIRST_LOGIN;
        }

        try {
            return UserSyncMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return defaultValue;
        }
    }
}
