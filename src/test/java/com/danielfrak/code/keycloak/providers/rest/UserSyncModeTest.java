package com.danielfrak.code.keycloak.providers.rest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserSyncModeTest {

    @Test
    void shouldImportOnFirstLoginForAllModesExceptNoSync() {
        assertThat(UserSyncMode.SYNC_FIRST_LOGIN.shouldImportOnFirstLogin()).isTrue();
        assertThat(UserSyncMode.SYNC_EVERY_LOGIN.shouldImportOnFirstLogin()).isTrue();
        assertThat(UserSyncMode.SYNC_EVERY_LOGIN_ONLY_ADD.shouldImportOnFirstLogin()).isTrue();
        assertThat(UserSyncMode.NO_SYNC.shouldImportOnFirstLogin()).isFalse();
    }

    @Test
    void shouldSyncOnLoginOnlyForEveryLoginModes() {
        assertThat(UserSyncMode.SYNC_FIRST_LOGIN.shouldSyncOnLogin()).isFalse();
        assertThat(UserSyncMode.SYNC_EVERY_LOGIN.shouldSyncOnLogin()).isTrue();
        assertThat(UserSyncMode.SYNC_EVERY_LOGIN_ONLY_ADD.shouldSyncOnLogin()).isTrue();
        assertThat(UserSyncMode.NO_SYNC.shouldSyncOnLogin()).isFalse();
    }

    @Test
    void shouldRemoveMissingOnLoginOnlyForSyncEveryLogin() {
        assertThat(UserSyncMode.SYNC_FIRST_LOGIN.shouldRemoveMissingOnLogin()).isFalse();
        assertThat(UserSyncMode.SYNC_EVERY_LOGIN.shouldRemoveMissingOnLogin()).isTrue();
        assertThat(UserSyncMode.SYNC_EVERY_LOGIN_ONLY_ADD.shouldRemoveMissingOnLogin()).isFalse();
        assertThat(UserSyncMode.NO_SYNC.shouldRemoveMissingOnLogin()).isFalse();
    }

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
        assertThat(UserSyncMode.fromConfig("  no_sync  ", UserSyncMode.SYNC_EVERY_LOGIN))
                .isEqualTo(UserSyncMode.NO_SYNC);
    }

    @Test
    void shouldReturnDefaultGivenInvalidValue() {
        assertThat(UserSyncMode.fromConfig("not-a-mode", UserSyncMode.SYNC_FIRST_LOGIN))
                .isEqualTo(UserSyncMode.SYNC_FIRST_LOGIN);
    }
}
