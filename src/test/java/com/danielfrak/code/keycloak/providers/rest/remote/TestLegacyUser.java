package com.danielfrak.code.keycloak.providers.rest.remote;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

public class TestLegacyUser {

    public static LegacyUser aMinimalLegacyUser() {
        return new LegacyUser(
                null,
                "someUserName",
                "user@email.com",
                "John",
                "Smith",
                true,
                true,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static LegacyUser aLegacyUserWithId() {
        return new LegacyUser(
                "someLegacyUserId",
                "someUserName",
                "user@email.com",
                "John",
                "Smith",
                true,
                true,
                emptyMap(),
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList()
        );
    }

    public static LegacyUser aLegacyUserWithOneAttribute() {
        return new LegacyUser(
                null,
                "someUserName",
                "user@email.com",
                "John",
                "Smith",
                true,
                true,
                Map.of("someAttribute", List.of("someValue")),
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList()
        );
    }

    public static LegacyUser aLegacyUserWithNullAndEmptyRoles() {
        return new LegacyUser(
                null,
                "someUserName",
                "user@email.com",
                "John",
                "Smith",
                true,
                true,
                emptyMap(),
                Arrays.asList(null, ""),
                emptyList(),
                emptyList(),
                emptyList()
        );
    }

    public static LegacyUser aLegacyUserWithTwoRoles() {
        return new LegacyUser(
                null,
                "someUserName",
                "user@email.com",
                "John",
                "Smith",
                true,
                true,
                emptyMap(),
                List.of("oldRole", "anotherRole"),
                emptyList(),
                emptyList(),
                emptyList()
        );
    }

    public static LegacyUser aLegacyUserWithTwoGroups() {
        return new LegacyUser(
                null,
                "someUserName",
                "user@email.com",
                "John",
                "Smith",
                true,
                true,
                emptyMap(),
                emptyList(),
                List.of("oldGroup", "anotherGroup"),
                emptyList(),
                emptyList()
        );
    }

    public static LegacyUser aLegacyUserWithNullAndEmptyGroups() {
        return new LegacyUser(
                null,
                "someUserName",
                "user@email.com",
                "John",
                "Smith",
                true,
                true,
                emptyMap(),
                emptyList(),
                Arrays.asList(null, ""),
                emptyList(),
                emptyList()
        );
    }

    public static LegacyUser aLegacyUserWithTwoRequiredActions() {
        return new LegacyUser(
                null,
                "someUserName",
                "user@email.com",
                "John",
                "Smith",
                true,
                true,
                emptyMap(),
                emptyList(),
                emptyList(),
                List.of("CONFIGURE_TOTP", "UPDATE_PASSWORD"),
                emptyList()
        );
    }

    public static LegacyUser aLegacyUserWithTwoTotps() {
        return new LegacyUser(
                null,
                "someUserName",
                "user@email.com",
                "John",
                "Smith",
                true,
                true,
                emptyMap(),
                emptyList(),
                emptyList(),
                emptyList(),
                List.of(
                        legacyTotp("Device 1", "SECRET_1"),
                        legacyTotp("Device 2", "SECRET_2")
                )
        );
    }

    private static LegacyTotp legacyTotp(String name, String secret) {
        return new LegacyTotp(secret, name, 0, 0, null, null);
    }
}