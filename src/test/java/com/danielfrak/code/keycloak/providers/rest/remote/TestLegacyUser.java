package com.danielfrak.code.keycloak.providers.rest.remote;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

public class TestLegacyUser {

    public static LegacyUser minimal() {
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
                null,
                null
        );
    }

    public static LegacyUser withBlankId() {
        return new LegacyUser(
                "\n",
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
                null,
                null
        );
    }

    public static LegacyUser withId() {
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
                emptyList(),
                emptyList()
        );
    }

    public static LegacyUser withOneAttribute() {
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
                emptyList(),
                emptyList()
        );
    }

    public static LegacyUser withNullAndEmptyRoles() {
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
                emptyList(),
                emptyList()
        );
    }

    public static LegacyUser withTwoRoles() {
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
                emptyList(),
                emptyList()
        );
    }

    public static LegacyUser withTwoGroups() {
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
                emptyList(),
                emptyList()
        );
    }

    public static LegacyUser withNullAndEmptyGroups() {
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
                emptyList(),
                emptyList()
        );
    }

    public static LegacyUser withTwoRequiredActions() {
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
                emptyList(),
                emptyList()
        );
    }

    public static LegacyUser withTwoTotps() {
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
                ),
                emptyList()
        );
    }

    private static LegacyTotp legacyTotp(String name, String secret) {
        return new LegacyTotp(secret, name, 0, 0, null, null);
    }

    public static LegacyUser withOneOrganization() {
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
                emptyList(),

                List.of(new LegacyOrganization("org-1", "org-1", List.of(new LegacyOrganizationDomain("org-1.local", true))))
        );
    }

    public static LegacyUser withOneOrganizationWithoutDomains() {
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
                emptyList(),
                List.of(new LegacyOrganization("org-1", "org-1", List.of()))
        );
    }

    public static LegacyUser withOneOrganizationWithNullDomains() {
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
                emptyList(),
                List.of(new LegacyOrganization("org-1", "org-1", null))
        );
    }
}