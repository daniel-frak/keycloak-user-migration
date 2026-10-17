package com.danielfrak.code.keycloak.providers.rest.remote.usermodel;

import com.danielfrak.code.keycloak.providers.rest.remote.LegacyTotp;
import com.danielfrak.code.keycloak.providers.rest.remote.LegacyUser;
import com.danielfrak.code.keycloak.providers.rest.remote.TestCredentialManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.keycloak.credential.CredentialModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.credential.OTPCredentialModel;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.models.utils.TimeBasedOTP;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TotpSynchronizationTest {

    private static final LegacyTotp DEVICE =
            new LegacyTotp("secret", "Authenticator", 6, 30, "HmacSHA1", null);

    private final UserModelFactory factory = new UserModelFactory(null, null, null, null);
    private final TestCredentialManager credentials = new TestCredentialManager();
    private final UserModel user = mock(UserModel.class);

    @BeforeEach
    void setUp() {
        lenient().when(user.credentialManager()).thenReturn(credentials);
    }

    @Test
    void shouldImportTotpForAnExistingUser() {
        synchronize(List.of(DEVICE));

        assertThat(totps()).singleElement().satisfies(otp -> {
            assertThat(otp.getOTPSecretData().getValue()).isEqualTo(DEVICE.secret());
            assertThat(otp.getUserLabel()).isEqualTo(DEVICE.name());
            assertThat(otp.getOTPCredentialData().getDigits()).isEqualTo(6);
            assertThat(otp.getOTPCredentialData().getPeriod()).isEqualTo(30);
        });
    }

    private void synchronize(List<LegacyTotp> devices) {
        factory.synchronizeTotps(legacyUser(devices), user);
    }

    private LegacyUser legacyUser(List<LegacyTotp> devices) {
        return new LegacyUser("id", "username", null, null, null, true, false,
                null, null, null, null, devices, null);
    }

    private List<OTPCredentialModel> totps() {
        return credentials.getStoredCredentialsByTypeStream(OTPCredentialModel.TYPE)
                .map(OTPCredentialModel::createFromCredentialModel)
                .filter(otp -> OTPCredentialModel.TOTP.equals(otp.getOTPCredentialData().getSubType()))
                .toList();
    }

    @Test
    void shouldPreserveCredentialIdAndCreationTimeOnRepeatedLogins() {
        var existing = store(DEVICE);
        existing.setCreatedDate(123L);

        synchronize(List.of(DEVICE));
        synchronize(List.of(DEVICE));

        assertThat(totps()).singleElement().satisfies(otp -> {
            assertThat(otp.getId()).isEqualTo(existing.getId());
            assertThat(otp.getCreatedDate()).isEqualTo(123L);
        });
    }

    private CredentialModel store(LegacyTotp device) {
        var credential = OTPCredentialModel.createTOTP(device.secret(), device.digits(),
                device.period(), device.algorithm(), device.encoding());
        credential.setUserLabel(device.name());
        return credentials.createStoredCredential(credential);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ONSWG4TFOQ======", "ONSWG4TFOQ", "onswg4tfoq======"})
    void shouldMatchEquivalentSecretEncodings(String secret) {
        var existing = store(DEVICE);
        var base32Device = new LegacyTotp(secret, DEVICE.name(), 6, 30, "HmacSHA1", "BASE32");

        synchronize(List.of(base32Device));

        assertThat(totps()).singleElement().extracting(CredentialModel::getId).isEqualTo(existing.getId());
    }

    @Test
    void shouldRemovePreviouslyImportedAndLocallyEnrolledTotpsWhenDisabledInLegacy() {
        store(DEVICE);
        store(new LegacyTotp("local-secret", "Keycloak device", 6, 30, "HmacSHA1", null));
        var hotp = credentials.createStoredCredential(OTPCredentialModel.createHOTP("hotp", 6, 0, "HmacSHA1"));
        var password = new CredentialModel();
        password.setType(PasswordCredentialModel.TYPE);
        credentials.createStoredCredential(password);
        var recoveryCodes = new CredentialModel();
        recoveryCodes.setType("recovery-authn-codes");
        credentials.createStoredCredential(recoveryCodes);

        synchronize(List.of());

        assertThat(totps()).isEmpty();
        assertThat(credentials.getStoredCredentialsByTypeStream(OTPCredentialModel.TYPE)).containsExactly(hotp);
        assertThat(credentials.getStoredCredentialsByTypeStream(PasswordCredentialModel.TYPE)).containsExactly(password);
        assertThat(credentials.getStoredCredentialsByTypeStream("recovery-authn-codes")).containsExactly(recoveryCodes);
    }

    @Test
    void shouldReconcileMultipleDevicesWithoutDuplicates() {
        var retained = store(DEVICE);
        store(new LegacyTotp("removed-secret", "Removed device", 6, 30, "HmacSHA1", null));
        var added = new LegacyTotp("new-secret", "New device", 8, 60, "HmacSHA512", null);

        synchronize(List.of(DEVICE, added));
        synchronize(List.of(DEVICE, added));

        assertThat(totps()).hasSize(2);
        assertThat(totps()).extracting(CredentialModel::getId).contains(retained.getId());
        assertThat(totps()).extracting(CredentialModel::getUserLabel)
                .containsExactlyInAnyOrder(DEVICE.name(), added.name());
    }

    @Test
    void shouldAcceptTheRotatedCodeAndRejectTheOldCode() {
        store(DEVICE);
        var rotated = new LegacyTotp("rotated-secret", DEVICE.name(), 6, 30, "HmacSHA1", null);
        var otp = new TimeBasedOTP("HmacSHA1", 6, 30, 0);
        var calendar = new GregorianCalendar();
        calendar.setTimeInMillis(1234567890000L);
        otp.setCalendar(calendar);
        var oldCode = otp.generateTOTP(DEVICE.secret().getBytes(StandardCharsets.UTF_8));
        var newCode = otp.generateTOTP(rotated.secret().getBytes(StandardCharsets.UTF_8));

        synchronize(List.of(rotated));

        var storedSecret = totps().getFirst().getDecodedSecret();
        assertThat(otp.validateTOTP(newCode, storedSecret)).isTrue();
        assertThat(otp.validateTOTP(oldCode, storedSecret)).isFalse();
    }

    @ParameterizedTest
    @MethodSource("changedDevices")
    void shouldReplaceChangedTotpConfiguration(LegacyTotp changedDevice) {
        var old = store(DEVICE);

        synchronize(List.of(changedDevice));

        assertThat(totps()).singleElement().satisfies(otp -> {
            assertThat(otp.getId()).isNotEqualTo(old.getId());
            assertThat(otp.getOTPSecretData().getValue()).isEqualTo(changedDevice.secret());
            assertThat(otp.getUserLabel()).isEqualTo(changedDevice.name());
            assertThat(otp.getOTPCredentialData().getDigits()).isEqualTo(changedDevice.digits());
            assertThat(otp.getOTPCredentialData().getPeriod()).isEqualTo(changedDevice.period());
            assertThat(otp.getOTPCredentialData().getAlgorithm()).isEqualTo(changedDevice.algorithm());
        });
    }

    static Stream<LegacyTotp> changedDevices() {
        return Stream.of(
                new LegacyTotp("rotated-secret", DEVICE.name(), 6, 30, "HmacSHA1", null),
                new LegacyTotp(DEVICE.secret(), "Renamed device", 6, 30, "HmacSHA1", null),
                new LegacyTotp(DEVICE.secret(), DEVICE.name(), 8, 30, "HmacSHA1", null),
                new LegacyTotp(DEVICE.secret(), DEVICE.name(), 6, 60, "HmacSHA1", null),
                new LegacyTotp(DEVICE.secret(), DEVICE.name(), 6, 30, "HmacSHA256", null));
    }

    @Test
    void shouldRejectMissingTotpsRatherThanDisableMfa() {
        var old = store(DEVICE);

        assertThatThrownBy(() -> synchronize(null)).isInstanceOf(IllegalArgumentException.class);

        assertThat(totps()).singleElement().extracting(CredentialModel::getId).isEqualTo(old.getId());
    }

    @ParameterizedTest
    @MethodSource("invalidDevices")
    void shouldValidateAllDevicesBeforeChangingCredentials(LegacyTotp invalidDevice) {
        var old = store(DEVICE);
        var validNewDevice = new LegacyTotp("new-secret", "New device", 6, 30, "HmacSHA1", null);
        var devices = Arrays.asList(validNewDevice, invalidDevice);

        assertThatThrownBy(() -> synchronize(devices))
                .isInstanceOf(RuntimeException.class);

        assertThat(totps()).singleElement().extracting(CredentialModel::getId).isEqualTo(old.getId());
    }

    static Stream<LegacyTotp> invalidDevices() {
        return Stream.of(
                null,
                new LegacyTotp(null, "Device", 6, 30, "HmacSHA1", null),
                new LegacyTotp(" ", "Device", 6, 30, "HmacSHA1", null),
                new LegacyTotp("secret", "Device", 0, 30, "HmacSHA1", null),
                new LegacyTotp("secret", "Device", 6, 0, "HmacSHA1", null),
                new LegacyTotp("secret", "Device", 6, 30, null, null),
                new LegacyTotp("secret", "Device", 6, 30, "unsupported", null),
                new LegacyTotp("secret", "Device", 6, 30, "HmacSHA1", "unsupported"),
                new LegacyTotp("!invalid!", "Device", 6, 30, "HmacSHA1", "BASE32"),
                new LegacyTotp("ONSWG4TF=OQ", "Device", 6, 30, "HmacSHA1", "BASE32"),
                new LegacyTotp("\uFF2FNSWG4TFOQ", "Device", 6, 30, "HmacSHA1", "BASE32"),
                new LegacyTotp("AB", "Device", 6, 30, "HmacSHA1", "BASE32"),
                new LegacyTotp("========", "Device", 6, 30, "HmacSHA1", "BASE32"));
    }

    @Test
    void shouldFailIfCredentialCannotBeCreated() {
        var failingCredentials = mock(org.keycloak.models.SubjectCredentialManager.class);
        when(user.credentialManager()).thenReturn(failingCredentials);
        when(failingCredentials.getStoredCredentialsByTypeStream(OTPCredentialModel.TYPE))
                .thenReturn(Stream.empty());
        var devices = List.of(DEVICE);

        assertThatThrownBy(() -> synchronize(devices)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldFailIfStaleCredentialCannotBeRemoved() {
        var old = store(DEVICE);
        var failingCredentials = mock(org.keycloak.models.SubjectCredentialManager.class);
        when(user.credentialManager()).thenReturn(failingCredentials);
        when(failingCredentials.getStoredCredentialsByTypeStream(OTPCredentialModel.TYPE))
                .thenReturn(Stream.of(old));

        assertThatThrownBy(() -> synchronize(List.of())).isInstanceOf(IllegalStateException.class);
    }
}
