package com.danielfrak.code.keycloak.providers.rest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserSyncModeTest {

    @Test
    void shouldReturnDefaultGivenNullOrBlankConfig() {
        assertThat(UserSyncMode.fromConfig(null, UserSyncMode.NO_SYNC))
                .isEqualTo(UserSyncMode.NO_SYNC);
        assertThat(UserSyncMode.fromConfig("   ", UserSyncMode.SYNC_EVERY_LOGIN))
                .isEqualTo(UserSyncMode.SYNC_EVERY_LOGIN);
    }

    @Test
    void shouldParseValidEnumValuesCaseInsensitive() {
        assertThat(UserSyncMode.fromConfig("sync_every_login", UserSyncMode.NO_SYNC))
                .isEqualTo(UserSyncMode.SYNC_EVERY_LOGIN);
        assertThat(UserSyncMode.fromConfig("Sync_Every_Login_Only_Add", UserSyncMode.NO_SYNC))
                .isEqualTo(UserSyncMode.SYNC_EVERY_LOGIN_ONLY_ADD);
    }

    @Test
    void shouldReturnDefaultGivenInvalidValue() {
        assertThat(UserSyncMode.fromConfig("not-a-mode", UserSyncMode.SYNC_FIRST_LOGIN))
                .isEqualTo(UserSyncMode.SYNC_FIRST_LOGIN);
    }
}
