package com.danielfrak.code.keycloak.providers.rest.remote.usermodel;

import com.danielfrak.code.keycloak.providers.rest.MigrationConfiguration;
import com.danielfrak.code.keycloak.providers.rest.remote.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.component.ComponentModel;
import org.keycloak.credential.CredentialModel;
import org.keycloak.models.*;
import org.keycloak.models.credential.OTPCredentialModel;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.models.utils.TimeBasedOTP;
import org.keycloak.organization.OrganizationProvider;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Stream;

import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.*;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserModelFactoryTest {

    private static final String MODEL_ID = "modelId";

    private UserModelFactory userModelFactory;

    private MultivaluedHashMap<String, String> config;

    @Mock
    private KeycloakSession session;

    @Mock
    private ComponentModel model;

    @Mock
    private UserProvider userProvider;

    @Mock
    private OrganizationProvider organizationProvider;

    @Mock
    private TotpValidationService totpValidationService;

    @Mock
    private RealmModel realm;

    private WildcardPatternFactory wildcardPatternFactory;
    private LegacyMappingParser legacyMappingParser;

    private static void otpCredentialsAreStoredFor(SubjectCredentialManager failingCredentials, CredentialModel old) {
        when(failingCredentials.getStoredCredentialsByTypeStream(OTPCredentialModel.TYPE))
                .thenReturn(Stream.of(old));
    }

    private static void noOtpCredentialsAreStoredFor(SubjectCredentialManager failingCredentials) {
        when(failingCredentials.getStoredCredentialsByTypeStream(OTPCredentialModel.TYPE))
                .thenReturn(Stream.empty());
    }

    @BeforeEach
    void setUp() {
        config = new MultivaluedHashMap<>();
        modelIsConfigured();
        modelHasId();
        sessionReturnsProviders();
        totpCredentialsAreMapped();

        wildcardPatternFactory = new WildcardPatternFactory();
        legacyMappingParser = new LegacyMappingParser();
    }

    private void modelIsConfigured() {
        lenient().when(model.getConfig())
                .thenReturn(config);
    }

    private void modelHasId() {
        lenient().when(model.getId())
                .thenReturn(MODEL_ID);
    }

    private void sessionReturnsProviders() {
        lenient().when(session.users())
                .thenReturn(userProvider);
        lenient().when(session.getProvider(OrganizationProvider.class))
                .thenReturn(organizationProvider);
    }

    private void totpCredentialsAreMapped() {
        lenient().when(totpValidationService.toTotpCredential(any()))
                .thenAnswer(invocation ->
                        new TotpValidationService().toTotpCredential(invocation.getArgument(0)));
    }

    private UserModelFactory constructUserModelFactory() {
        var roleMigrationService = new RoleMigrationService(model, legacyMappingParser, wildcardPatternFactory);
        var groupMigrationService = new GroupMigrationService(model, legacyMappingParser, wildcardPatternFactory);
        var migrationConfiguration = new MigrationConfiguration(model);
        return new UserModelFactory(session, migrationConfiguration, roleMigrationService, groupMigrationService,
                totpValidationService);
    }

    private void configureMigrationOfUnmappedRoles() {
        config.putSingle(MIGRATE_UNMAPPED_ROLES_PROPERTY, "true");
    }

    private RoleModel mockRoleExistsInRealm(String roleName) {
        final RoleModel roleModel = mock(RoleModel.class);
        when(realm.getRole(roleName))
                .thenReturn(roleModel);
        return roleModel;
    }

    private RoleModel asRoleModel(String roleName) {
        RoleModel roleModel = mock(RoleModel.class);
        lenient().when(roleModel.getName())
                .thenReturn(roleName);
        return roleModel;
    }

    private void existInRealm(RoleModel... roleModels) {
        for (RoleModel roleModel : roleModels) {
            when(realm.getRole(roleModel.getName()))
                    .thenReturn(roleModel);
        }
    }

    private GroupModel asGroupModel(String groupName) {
        GroupModel groupModel = mock(GroupModel.class);
        lenient().when(groupModel.getName())
                .thenReturn(groupName);
        return groupModel;
    }

    private void existingGroupsInRealm(GroupModel... groupModels) {
        when(realm.getGroupsStream())
                .then(i -> Stream.of(groupModels));
    }

    private TestUserModel matchingUserModel(LegacyUser legacyUser) {
        return new TestUserModel(legacyUser.username(), legacyUser.id());
    }

    private void userModelWillBeCreatedSuccessfullyWithoutIdMigration(LegacyUser legacyUser) {
        when(userProvider.addUser(realm, null, legacyUser.username(), true,
                legacyUser.shouldAddDefaultRequiredActions()))
                .thenReturn(matchingUserModel(legacyUser));
    }

    private void configureMigrationOfUnmappedGroups() {
        config.putSingle(MIGRATE_UNMAPPED_GROUPS_PROPERTY, "true");
    }

    private void legacyTotpConfigurationIsInvalid(List<LegacyTotp> devices) {
        when(totpValidationService.validateTotps(devices))
                .thenThrow(new IllegalArgumentException("Invalid legacy TOTP configuration."));
    }

    private void totpCredentialsAreValidatedByRealTotpValidationService(List<LegacyTotp> devices) {
        when(totpValidationService.validateTotps(devices))
                .thenReturn(new TotpValidationService().validateTotps(devices));
    }

    private ComponentModel modelWithNullIgnoredLists() {
        ComponentModel modelWithNullIgnoredLists = mock(ComponentModel.class);
        MultivaluedHashMap<String, String> configWithNullIgnoredLists = configWithNullIgnoredLists();
        when(modelWithNullIgnoredLists.getConfig()).thenReturn(configWithNullIgnoredLists);
        when(modelWithNullIgnoredLists.getId()).thenReturn(MODEL_ID);
        return modelWithNullIgnoredLists;
    }

    private MultivaluedHashMap<String, String> configWithNullIgnoredLists() {
        @SuppressWarnings("unchecked")
        MultivaluedHashMap<String, String> configWithNullIgnoredLists = mock(MultivaluedHashMap.class);
        when(configWithNullIgnoredLists.getList(ROLE_MAP_PROPERTY)).thenReturn(emptyList());
        when(configWithNullIgnoredLists.getList(GROUP_MAP_PROPERTY)).thenReturn(emptyList());
        when(configWithNullIgnoredLists.getList(IGNORED_SYNC_ROLES_PROPERTY)).thenReturn(null);
        when(configWithNullIgnoredLists.getList(IGNORED_SYNC_GROUPS_PROPERTY)).thenReturn(null);
        when(configWithNullIgnoredLists.getFirst(MIGRATE_UNMAPPED_ROLES_PROPERTY)).thenReturn("true");
        when(configWithNullIgnoredLists.getFirst(MIGRATE_UNMAPPED_GROUPS_PROPERTY)).thenReturn("true");
        when(configWithNullIgnoredLists.getFirst(UPDATE_USER_ROLES_ON_LOGIN)).thenReturn("SYNC_FIRST_LOGIN");
        when(configWithNullIgnoredLists.getFirst(UPDATE_USER_GROUPS_ON_LOGIN)).thenReturn("SYNC_FIRST_LOGIN");

        return configWithNullIgnoredLists;
    }

    private void organizationWillBeSuccessfullyCreated(LegacyOrganization legacyOrganization, OrganizationModel orgMock) {
        when(organizationProvider.create(legacyOrganization.orgName(), legacyOrganization.orgAlias()))
                .thenReturn(orgMock);
    }

    private void noOrganizationsExist() {
        lenient().when(organizationProvider.getByAlias(any()))
                .thenReturn(null);
    }

    private void organizationIsFoundByAlias(OrganizationModel orgMock) {
        when(organizationProvider.getByAlias(anyString()))
                .thenReturn(orgMock);
    }

    private void organizationsAreEnabledInRealm() {
        when(realm.isOrganizationsEnabled()).thenReturn(true);
    }

    private RoleModel roleModelWithIdAndName(String roleId, String roleName) {
        RoleModel desiredRole = asRoleModel(roleName);
        when(desiredRole.getId())
                .thenReturn(roleId);
        return desiredRole;
    }

    @Nested
    class SynchronizeTotps {

        private static final LegacyTotp DEVICE =
                new LegacyTotp("secret", "Authenticator", 6, 30, "HmacSHA1", null);

        private final TestCredentialManager credentialManager = new TestCredentialManager();
        private final UserModel user = mock(UserModel.class);

        private static Stream<LegacyTotp> changedDevices() {
            return Stream.of(
                    new LegacyTotp("rotated-secret", DEVICE.name(), 6, 30, "HmacSHA1", null),
                    new LegacyTotp(DEVICE.secret(), "Renamed device", 6, 30, "HmacSHA1", null),
                    new LegacyTotp(DEVICE.secret(), DEVICE.name(), 8, 30, "HmacSHA1", null),
                    new LegacyTotp(DEVICE.secret(), DEVICE.name(), 6, 60, "HmacSHA1", null),
                    new LegacyTotp(DEVICE.secret(), DEVICE.name(), 6, 30, "HmacSHA256", null));
        }

        @BeforeEach
        void setUp() {
            userModelFactory = constructUserModelFactory();
            mockUserModelHasCredentialManager();
        }

        private void mockUserModelHasCredentialManager() {
            lenient().when(user.credentialManager()).thenReturn(credentialManager);
        }

        private LegacyUser legacyUser(List<LegacyTotp> devices) {
            return new LegacyUser("id", "username", null, null, null, true, false,
                    null, null, null, null, devices, null);
        }

        private OTPCredentialModel credential(LegacyTotp device) {
            var credential = OTPCredentialModel.createTOTP(
                    device.secret(), device.digits(), device.period(), device.algorithm(), device.encoding());
            credential.setUserLabel(device.name());
            return credential;
        }

        private List<OTPCredentialModel> credentials(List<LegacyTotp> devices) {
            return devices.stream().map(this::credential).toList();
        }

        private List<OTPCredentialModel> totps() {
            return credentialManager.getStoredCredentialsByTypeStream(OTPCredentialModel.TYPE)
                    .map(OTPCredentialModel::createFromCredentialModel)
                    .filter(otp -> OTPCredentialModel.TOTP.equals(otp.getOTPCredentialData().getSubType()))
                    .toList();
        }

        private CredentialModel store(LegacyTotp device) {
            return credentialManager.createStoredCredential(credential(device));
        }

        @Test
        void shouldImportTotpForAnExistingUser() {
            var devices = List.of(DEVICE);
            totpsAreValidated(devices);
            var legacyUser = legacyUser(devices);

            userModelFactory.synchronizeTotps(legacyUser, user);

            assertThat(totps()).singleElement().satisfies(otp -> {
                assertThat(otp.getOTPSecretData().getValue()).isEqualTo(DEVICE.secret());
                assertThat(otp.getUserLabel()).isEqualTo(DEVICE.name());
                assertThat(otp.getOTPCredentialData().getDigits()).isEqualTo(6);
                assertThat(otp.getOTPCredentialData().getPeriod()).isEqualTo(30);
            });
        }

        private void totpsAreValidated(List<LegacyTotp> devices) {
            when(totpValidationService.validateTotps(devices))
                    .thenReturn(credentials(devices));
        }

        @Test
        void shouldPreserveCredentialIdAndCreationTimeOnRepeatedLogins() {
            var existing = store(DEVICE);
            existing.setCreatedDate(123L);
            var devices = List.of(DEVICE);
            totpsAreValidated(devices);
            var legacyUser = legacyUser(devices);

            userModelFactory.synchronizeTotps(legacyUser, user);
            userModelFactory.synchronizeTotps(legacyUser, user);

            assertThat(totps()).singleElement().satisfies(otp -> {
                assertThat(otp.getId()).isEqualTo(existing.getId());
                assertThat(otp.getCreatedDate()).isEqualTo(123L);
            });
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"null"})
        void shouldPreserveCredentialsWithOptionalLabels(String label) {
            var device = new LegacyTotp(DEVICE.secret(), label, 6, 30, "HmacSHA1", null);
            var existing = store(device);
            var devices = List.of(device);
            totpsAreValidated(devices);
            var legacyUser = legacyUser(devices);

            userModelFactory.synchronizeTotps(legacyUser, user);

            assertThat(totps()).singleElement().satisfies(otp -> {
                assertThat(otp.getId()).isEqualTo(existing.getId());
                assertThat(otp.getUserLabel()).isEqualTo(label);
            });
        }

        @Test
        void shouldFailWithoutChangingCredentialsGivenSha256IsUnavailable() {
            var existing = store(DEVICE);
            var devices = List.of(DEVICE);
            totpsAreValidated(devices);
            var legacyUser = legacyUser(devices);
            var cause = new NoSuchAlgorithmException("SHA-256 unavailable");

            try (var ignored = sha256IsUnavailable(cause)) {
                assertThatThrownBy(() -> userModelFactory.synchronizeTotps(legacyUser, user))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("SHA-256 is unavailable.")
                        .hasCause(cause);
            }

            assertThat(totps()).singleElement().extracting(CredentialModel::getId).isEqualTo(existing.getId());
        }

        @Test
        void shouldMatchEquivalentSecretEncodings() {
            var existing = store(DEVICE);
            var devices = List.of(new LegacyTotp(
                    "ONSWG4TFOQ======", DEVICE.name(), 6, 30, "HmacSHA1", "BASE32"));
            totpCredentialsAreValidatedByRealTotpValidationService(devices);
            var legacyUser = legacyUser(devices);

            userModelFactory.synchronizeTotps(legacyUser, user);

            assertThat(totps()).singleElement().extracting(CredentialModel::getId).isEqualTo(existing.getId());
        }

        @Test
        void shouldRemoveStaleTotpsAndPreserveOtherCredentialTypes() {
            store(DEVICE);
            store(new LegacyTotp("local-secret", "Keycloak device", 6, 30, "HmacSHA1", null));
            var hotp = credentialManager.createStoredCredential(
                    OTPCredentialModel.createHOTP("hotp", 6, 0, "HmacSHA1"));
            var password = new CredentialModel();
            password.setType(PasswordCredentialModel.TYPE);
            credentialManager.createStoredCredential(password);
            var recoveryCodes = new CredentialModel();
            recoveryCodes.setType("recovery-authn-codes");
            credentialManager.createStoredCredential(recoveryCodes);
            var devices = List.<LegacyTotp>of();
            totpsAreValidated(devices);
            var legacyUser = legacyUser(devices);

            userModelFactory.synchronizeTotps(legacyUser, user);

            assertThat(totps()).isEmpty();
            assertThat(credentialManager.getStoredCredentialsByTypeStream(OTPCredentialModel.TYPE))
                    .containsExactly(hotp);
            assertThat(credentialManager.getStoredCredentialsByTypeStream(PasswordCredentialModel.TYPE))
                    .containsExactly(password);
            assertThat(credentialManager.getStoredCredentialsByTypeStream("recovery-authn-codes"))
                    .containsExactly(recoveryCodes);
        }

        @Test
        void shouldReconcileMultipleDevicesWithoutDuplicates() {
            var retained = store(DEVICE);
            store(new LegacyTotp("removed-secret", "Removed device", 6, 30, "HmacSHA1", null));
            var added = new LegacyTotp("new-secret", "New device", 8, 60, "HmacSHA512", null);
            var devices = List.of(DEVICE, added);
            totpsAreValidated(devices);
            var legacyUser = legacyUser(devices);

            userModelFactory.synchronizeTotps(legacyUser, user);
            userModelFactory.synchronizeTotps(legacyUser, user);

            assertThat(totps()).hasSize(2);
            assertThat(totps()).extracting(CredentialModel::getId).contains(retained.getId());
            assertThat(totps()).extracting(CredentialModel::getUserLabel)
                    .containsExactlyInAnyOrder(DEVICE.name(), added.name());
        }

        @Test
        void shouldAcceptRotatedCodeAndRejectOldCode() {
            store(DEVICE);
            var rotated = new LegacyTotp("rotated-secret", DEVICE.name(), 6, 30, "HmacSHA1", null);
            var devices = List.of(rotated);
            totpsAreValidated(devices);
            var legacyUser = legacyUser(devices);
            var otp = new TimeBasedOTP("HmacSHA1", 6, 30, 0);
            var calendar = new GregorianCalendar();
            calendar.setTimeInMillis(1234567890000L);
            otp.setCalendar(calendar);
            var oldCode = otp.generateTOTP(DEVICE.secret().getBytes(StandardCharsets.UTF_8));
            var newCode = otp.generateTOTP(rotated.secret().getBytes(StandardCharsets.UTF_8));

            userModelFactory.synchronizeTotps(legacyUser, user);

            var storedSecret = totps().getFirst().getDecodedSecret();
            assertThat(otp.validateTOTP(newCode, storedSecret)).isTrue();
            assertThat(otp.validateTOTP(oldCode, storedSecret)).isFalse();
        }

        @ParameterizedTest
        @MethodSource("changedDevices")
        void shouldReplaceChangedTotpConfiguration(LegacyTotp changedDevice) {
            var old = store(DEVICE);
            var devices = List.of(changedDevice);
            totpsAreValidated(devices);
            var legacyUser = legacyUser(devices);

            userModelFactory.synchronizeTotps(legacyUser, user);

            assertThat(totps()).singleElement().satisfies(otp -> {
                assertThat(otp.getId()).isNotEqualTo(old.getId());
                assertThat(otp.getOTPSecretData().getValue()).isEqualTo(changedDevice.secret());
                assertThat(otp.getUserLabel()).isEqualTo(changedDevice.name());
                assertThat(otp.getOTPCredentialData().getDigits()).isEqualTo(changedDevice.digits());
                assertThat(otp.getOTPCredentialData().getPeriod()).isEqualTo(changedDevice.period());
                assertThat(otp.getOTPCredentialData().getAlgorithm()).isEqualTo(changedDevice.algorithm());
            });
        }

        @Test
        void shouldNotChangeCredentialsGivenValidationFails() {
            var old = store(DEVICE);
            var devices = List.of(DEVICE);
            legacyTotpConfigurationIsInvalid(devices);
            var legacyUser = legacyUser(devices);

            assertThatThrownBy(() -> userModelFactory.synchronizeTotps(legacyUser, user))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThat(totps()).singleElement().extracting(CredentialModel::getId).isEqualTo(old.getId());
        }

        @Test
        void shouldFailGivenCredentialCannotBeCreated() {
            var failingCredentials = mock(SubjectCredentialManager.class);
            mockUserHasCredentials(failingCredentials);
            noOtpCredentialsAreStoredFor(failingCredentials);
            var devices = List.of(DEVICE);
            totpsAreValidated(devices);
            var legacyUser = legacyUser(devices);

            assertThatThrownBy(() -> userModelFactory.synchronizeTotps(legacyUser, user))
                    .isInstanceOf(IllegalStateException.class);
        }

        private void mockUserHasCredentials(SubjectCredentialManager failingCredentials) {
            when(user.credentialManager()).thenReturn(failingCredentials);
        }

        @Test
        void shouldFailGivenStaleCredentialCannotBeRemoved() {
            var old = store(DEVICE);
            var failingCredentials = mock(SubjectCredentialManager.class);
            mockUserHasCredentials(failingCredentials);
            otpCredentialsAreStoredFor(failingCredentials, old);
            var devices = List.<LegacyTotp>of();
            totpsAreValidated(devices);
            var legacyUser = legacyUser(devices);

            assertThatThrownBy(() -> userModelFactory.synchronizeTotps(legacyUser, user))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    private static MockedStatic<MessageDigest> sha256IsUnavailable(Throwable throwable) {
        MockedStatic<MessageDigest> messageDigest = mockStatic(MessageDigest.class);
        messageDigest.when(() -> MessageDigest.getInstance("SHA-256")).thenThrow(throwable);

        return messageDigest;
    }

    @Nested
    class Create {

        @Test
        void shouldCreateMinimalUser() {
            final LegacyUser legacyUser = TestLegacyUser.minimal();
            userModelWillBeCreatedSuccessfullyWithoutIdMigration(legacyUser);
            userModelFactory = constructUserModelFactory();

            UserModel result = userModelFactory.create(legacyUser, realm);

            assertThat(result).isNotNull();
        }

        @Test
        void shouldCreateMinimalUserGivenLegacyUserIdIsBlank() {
            final LegacyUser legacyUser = TestLegacyUser.withBlankId();
            userModelWillBeCreatedSuccessfullyWithoutIdMigration(legacyUser);
            userModelFactory = constructUserModelFactory();

            UserModel result = userModelFactory.create(legacyUser, realm);

            assertThat(result).isNotNull();
        }

        @Test
        void shouldThrowExceptionGivenUserModelCreatedWithUsernameDifferentThanLegacy() {
            final LegacyUser legacyUser = TestLegacyUser.minimal();
            mockUserModelCreatedWithWrongUsernameWithoutIdMigration(legacyUser);
            userModelFactory = constructUserModelFactory();

            assertThatThrownBy(() -> userModelFactory.create(legacyUser, realm))
                    .isInstanceOf(IllegalStateException.class);
        }

        private void mockUserModelCreatedWithWrongUsernameWithoutIdMigration(LegacyUser legacyUser) {
            when(userProvider.addUser(realm, null, legacyUser.username(), true,
                    legacyUser.shouldAddDefaultRequiredActions()))
                    .thenReturn(new TestUserModel("wrong_username"));
        }

        @Test
        void shouldNotThrowGivenKeycloakLowercasesTheUsername() {
            final LegacyUser legacyUser = TestLegacyUser.minimal();
            userModelWillBeCreatedSuccessfullyWithLowercasedUsername(legacyUser);
            userModelFactory = constructUserModelFactory();

            UserModel result = userModelFactory.create(legacyUser, realm);

            assertThat(result).isNotNull();
        }

        @Test
        void shouldMigrateBasicAttributes() {
            final LegacyUser legacyUser = TestLegacyUser.minimal();
            userModelWillBeCreatedSuccessfullyWithoutIdMigration(legacyUser);
            userModelFactory = constructUserModelFactory();

            UserModel result = userModelFactory.create(legacyUser, realm);

            assertBasicAttributesWereMigrated(result, legacyUser);
        }

        private void assertBasicAttributesWereMigrated(UserModel result, LegacyUser legacyUser) {
            assertThat(result.getUsername()).isEqualTo(legacyUser.username());
            assertThat(result.getEmail()).isEqualTo(legacyUser.email());
            assertThat(result.isEmailVerified()).isEqualTo(legacyUser.isEmailVerified());
            assertThat(result.isEnabled()).isEqualTo(legacyUser.isEnabled());
            assertThat(result.getFirstName()).isEqualTo(legacyUser.firstName());
            assertThat(result.getLastName()).isEqualTo(legacyUser.lastName());
        }

        @Test
        void shouldMigrateLegacyUserId() {
            final LegacyUser legacyUser = TestLegacyUser.withId();
            mockSuccessfulUserModelCreationWithIdMigration(legacyUser);
            userModelFactory = constructUserModelFactory();

            UserModel result = userModelFactory.create(legacyUser, realm);

            assertThat(result.getId()).isEqualTo(legacyUser.id());
        }

        private void mockSuccessfulUserModelCreationWithIdMigration(LegacyUser legacyUser) {
            final boolean addDefaultRoles = true;
            when(userProvider.addUser(realm, legacyUser.id(), legacyUser.username(),
                    addDefaultRoles, legacyUser.shouldAddDefaultRequiredActions()))
                    .thenReturn(matchingUserModel(legacyUser));
        }

        @Test
        void shouldDisableDefaultRequiredActionsWithoutLegacyUserId() {
            LegacyUser legacyUser = TestLegacyUser.withDefaultRequiredActions(false, false);
            userModelWillBeCreatedSuccessfullyWithoutIdMigration(legacyUser);
            userModelFactory = constructUserModelFactory();

            userModelFactory.create(legacyUser, realm);

            verify(userProvider).addUser(realm, null, legacyUser.username(), true, false);
        }

        @Test
        void shouldEnableDefaultRequiredActionsWithLegacyUserId() {
            LegacyUser legacyUser = TestLegacyUser.withDefaultRequiredActions(true, true);
            mockSuccessfulUserModelCreationWithIdMigration(legacyUser);
            userModelFactory = constructUserModelFactory();

            userModelFactory.create(legacyUser, realm);

            verify(userProvider).addUser(realm, legacyUser.id(), legacyUser.username(), true, true);
        }

        @Test
        void shouldMigrateMappedRolesAndIgnoreUnmappedRoles() {
            final LegacyUser legacyUser = TestLegacyUser.withTwoRoles();
            userModelWillBeCreatedSuccessfullyWithoutIdMigration(legacyUser);
            configureNoMigrationOfUnmappedRoles();
            final String newRoleName = configureMappingForFirstRole(legacyUser);
            mockRoleDoesNotExistInRealm(newRoleName);
            RoleModel newRoleModel = mockAddingRoleToRealm(newRoleName);
            userModelFactory = constructUserModelFactory();

            UserModel result = userModelFactory.create(legacyUser, realm);

            assertThat(result.getRoleMappingsStream().toList())
                    .containsExactly(newRoleModel);
        }

        private void configureNoMigrationOfUnmappedRoles() {
            config.putSingle(MIGRATE_UNMAPPED_ROLES_PROPERTY, "false");
        }

        private String configureMappingForFirstRole(LegacyUser legacyUser) {
            final String oldRole = legacyUser.roles().getFirst();
            final var newRole = "newRole";
            config.put(ROLE_MAP_PROPERTY, List.of(oldRole + ":" + newRole));

            return newRole;
        }

        private void mockRoleDoesNotExistInRealm(String roleName) {
            when(realm.getRole(roleName))
                    .thenReturn(null);
        }

        private RoleModel mockAddingRoleToRealm(String roleName) {
            final RoleModel roleModel = mock(RoleModel.class);
            when(realm.addRole(roleName))
                    .thenReturn(roleModel);

            return roleModel;
        }

        @Test
        void shouldMigrateMappedRolesAndUnmappedRoles() {
            final LegacyUser legacyUser = TestLegacyUser.withTwoRoles();
            userModelWillBeCreatedSuccessfullyWithoutIdMigration(legacyUser);
            configureMigrationOfUnmappedRoles();
            String mappedRoleName = configureMappingForFirstRole(legacyUser);
            String unmappedRoleName = legacyUser.roles().get(1);
            mockRoleDoesNotExistInRealm(mappedRoleName);
            mockRoleDoesNotExistInRealm(unmappedRoleName);
            RoleModel mappedRoleModel = mockAddingRoleToRealm(mappedRoleName);
            RoleModel unmappedRoleModel = mockAddingRoleToRealm(unmappedRoleName);
            userModelFactory = constructUserModelFactory();

            UserModel result = userModelFactory.create(legacyUser, realm);

            assertThat(result.getRoleMappingsStream().toList())
                    .containsExactlyInAnyOrder(mappedRoleModel, unmappedRoleModel);
        }

        @Test
        void shouldMigrateMappedClientRolesAndIgnoreUnmappedClientRoles() {
            final LegacyUser legacyUser = TestLegacyUser.withTwoRoles();
            userModelWillBeCreatedSuccessfullyWithoutIdMigration(legacyUser);
            configureNoMigrationOfUnmappedRoles();
            final var newRole = configureMappingForFirstRole(legacyUser);
            userModelFactory = constructUserModelFactory();

            final ClientModel clientModel1 = mockRealmHasClient();
            final RoleModel newRoleModel = mockClientHasRole(clientModel1, newRole);

            UserModel result = userModelFactory.create(legacyUser, realm);

            verify(clientModel1, times(1)).getRole(any());
            assertThat(result.getRoleMappingsStream().toList())
                    .containsExactlyInAnyOrder(newRoleModel);
        }

        private ClientModel mockRealmHasClient() {
            final ClientModel clientModel = mock(ClientModel.class);
            mockRealmHasClients(clientModel);

            return clientModel;
        }

        private void mockRealmHasClients(ClientModel... clientModels) {
            when(realm.getClientsStream())
                    .then(i -> Stream.of(clientModels));
        }

        private RoleModel mockClientHasRole(ClientModel clientModel, String role) {
            final RoleModel anotherRoleModel = mock(RoleModel.class);
            when(clientModel.getRole(role))
                    .thenReturn(anotherRoleModel);

            return anotherRoleModel;
        }

        @Test
        void shouldAssignExistingRoleToUser() {
            final LegacyUser legacyUser = TestLegacyUser.withTwoRoles();
            userModelWillBeCreatedSuccessfullyWithoutIdMigration(legacyUser);
            configureNoMigrationOfUnmappedRoles();
            String mappedRoleName = configureMappingForFirstRole(legacyUser);
            RoleModel mappedRoleModel = mockRoleExistsInRealm(mappedRoleName);
            userModelFactory = constructUserModelFactory();

            UserModel result = userModelFactory.create(legacyUser, realm);

            assertThat(result.getRoleMappingsStream().toList())
                    .containsExactly(mappedRoleModel);
        }

        @Test
        void shouldMigrateUserWithNullAndEmptyRoles() {
            final LegacyUser legacyUser = TestLegacyUser.withNullAndEmptyRoles();
            userModelWillBeCreatedSuccessfullyWithoutIdMigration(legacyUser);
            configureMigrationOfUnmappedRoles();
            userModelFactory = constructUserModelFactory();

            UserModel result = userModelFactory.create(legacyUser, realm);

            assertThat(result.getRoleMappingsStream().toList()).isEmpty();
        }


        @Test
        void shouldMigrateMappedAndUnmappedClientRoles() {
            final LegacyUser legacyUser = TestLegacyUser.withTwoRoles();
            userModelWillBeCreatedSuccessfullyWithoutIdMigration(legacyUser);
            configureMigrationOfUnmappedRoles();
            final String mappedRoleName = configureMappingForFirstRole(legacyUser);
            final String unmappedRoleName = legacyUser.roles().get(1);
            userModelFactory = constructUserModelFactory();

            final ClientModel clientModel1 = mock(ClientModel.class);
            final RoleModel roleModel1 = mockClientHasRole(clientModel1, mappedRoleName);
            final ClientModel clientModel2 = mock(ClientModel.class);
            final RoleModel roleModel2 = mockClientHasRole(clientModel2, unmappedRoleName);
            mockRealmHasClients(clientModel1, clientModel2);
            userModelFactory = constructUserModelFactory();

            UserModel result = userModelFactory.create(legacyUser, realm);

            verify(clientModel2, never()).getRole(mappedRoleName); // Only the first found client role will be used
            assertThat(result.getRoleMappingsStream().toList())
                    .containsExactlyInAnyOrder(roleModel1, roleModel2);
        }

        @Test
        void shouldNotMigrateClientRoleGivenNotFound() {
            final LegacyUser legacyUser = TestLegacyUser.withTwoRoles();
            userModelWillBeCreatedSuccessfullyWithoutIdMigration(legacyUser);
            configureMigrationOfUnmappedRoles();
            configureMappingForFirstRole(legacyUser);
            final ClientModel clientModel = mockRealmHasClient();
            userModelFactory = constructUserModelFactory();

            UserModel result = userModelFactory.create(legacyUser, realm);

            verify(clientModel, times(2)).getRole(any());
            assertThat(result.getRoleMappingsStream().toList())
                    .isEmpty();
        }

        @Test
        void shouldMigrateMappedGroupsAndIgnoreUnmappedGroups() {
            final LegacyUser legacyUser = TestLegacyUser.withTwoGroups();
            userModelWillBeCreatedSuccessfullyWithoutIdMigration(legacyUser);
            configureNoMigrationOfUnmappedGroups();
            final String newGroup = configureMappingForFirstGroup(legacyUser);
            mockRealmHasNoGroups();
            final GroupModel newGroupModel = mockGroupCreationInRealm(newGroup);
            userModelFactory = constructUserModelFactory();

            var result = userModelFactory.create(legacyUser, realm);

            assertThat(result.getGroupsStream().toList())
                    .containsExactly(newGroupModel);
        }

        private void configureNoMigrationOfUnmappedGroups() {
            config.putSingle(MIGRATE_UNMAPPED_GROUPS_PROPERTY, "false");
        }

        private String configureMappingForFirstGroup(LegacyUser legacyUser) {
            final String legacyGroupName = legacyUser.groups().getFirst();
            final var newGroupName = "newGroup";
            config.put(GROUP_MAP_PROPERTY, List.of(legacyGroupName + ":" + newGroupName));

            return newGroupName;
        }

        private GroupModel mockGroupCreationInRealm(String groupName) {
            final GroupModel newGroupModel = mock(GroupModel.class);
            when(realm.createGroup(groupName))
                    .thenReturn(newGroupModel);

            return newGroupModel;
        }

        private void mockRealmHasNoGroups() {
            when(realm.getGroupsStream()).then(i -> Stream.empty());
        }

        @Test
        void shouldMigrateMappedAndUnmappedGroups() {
            final LegacyUser legacyUser = TestLegacyUser.withTwoGroups();
            userModelWillBeCreatedSuccessfullyWithoutIdMigration(legacyUser);
            configureMigrationOfUnmappedGroups();
            String mappedGroupName = configureMappingForFirstGroup(legacyUser);
            String unmappedGroupName = legacyUser.groups().get(1);
            mockRealmHasNoGroups();
            final GroupModel mappedGroupModel = mockGroupCreationInRealm(mappedGroupName);
            final GroupModel unmappedGroupModel = mockGroupCreationInRealm(unmappedGroupName);
            userModelFactory = constructUserModelFactory();

            UserModel result = userModelFactory.create(legacyUser, realm);

            assertThat(result.getGroupsStream().toList())
                    .containsExactlyInAnyOrder(mappedGroupModel, unmappedGroupModel);
        }

        @Test
        void shouldAddUserToExistingGroup() {
            final LegacyUser legacyUser = TestLegacyUser.withTwoGroups();
            userModelWillBeCreatedSuccessfullyWithoutIdMigration(legacyUser);
            configureNoMigrationOfUnmappedGroups();
            final String mappedGroupName = configureMappingForFirstGroup(legacyUser);
            final GroupModel mappedGroupModel = mockGroupExistsInRealm(mappedGroupName);
            userModelFactory = constructUserModelFactory();

            var result = userModelFactory.create(legacyUser, realm);

            assertThat(result.getGroupsStream().toList())
                    .containsExactly(mappedGroupModel);
        }

        private GroupModel mockGroupExistsInRealm(String groupName) {
            final GroupModel groupModel = mock(GroupModel.class);
            when(groupModel.getName())
                    .thenReturn(groupName);
            when(realm.getGroupsStream())
                    .then(i -> Stream.of(groupModel));

            return groupModel;
        }

        @Test
        void shouldMigrateUserWithNullAndEmptyGroups() {
            final LegacyUser legacyUser = TestLegacyUser.withNullAndEmptyGroups();
            userModelWillBeCreatedSuccessfullyWithoutIdMigration(legacyUser);
            configureMigrationOfUnmappedGroups();
            userModelFactory = constructUserModelFactory();

            UserModel result = userModelFactory.create(legacyUser, realm);

            assertThat(result.getGroupsStream().toList()).isEmpty();
        }

        @Test
        void shouldHandleConfiguredIgnoredRolePatternsContainingNullAndBlankEntries() {
            final LegacyUser legacyUser = TestLegacyUser.withTwoRoles();
            userModelWillBeCreatedSuccessfullyWithoutIdMigration(legacyUser);
            configureMigrationOfUnmappedRoles();
            config.put(IGNORED_SYNC_ROLES_PROPERTY, java.util.Arrays.asList(null, "  ", "old*"));
            RoleModel ignoredRole = asRoleModel(legacyUser.roles().getFirst());
            RoleModel importedRole = asRoleModel(legacyUser.roles().get(1));
            existInRealm(ignoredRole, importedRole);
            userModelFactory = constructUserModelFactory();

            UserModel result = userModelFactory.create(legacyUser, realm);

            assertThat(result.getRoleMappingsStream().toList())
                    .containsExactly(importedRole);
        }

        @Test
        void shouldSkipOrganizationMigrationGivenFeatureEnabledButLegacyOrganizationsAreEmpty() {
            final LegacyUser legacyUser = TestLegacyUser.withId();
            mockSuccessfulUserModelCreationWithIdMigration(legacyUser);
            userModelFactory = constructUserModelFactory();
            organizationsAreEnabledInRealm();
            OrganizationProvider provider = mock(OrganizationProvider.class);

            UserModel result = userModelFactory.create(legacyUser, realm);

            assertThat(result).isNotNull();
            verifyNoInteractions(provider);
        }

        @Test
        void shouldSkipOrganizationMigrationGivenFeatureEnabledButLegacyOrganizationsAreNull() {
            final LegacyUser legacyUser = TestLegacyUser.minimal();
            userModelWillBeCreatedSuccessfullyWithoutIdMigration(legacyUser);
            userModelFactory = constructUserModelFactory();
            organizationsAreEnabledInRealm();
            OrganizationProvider provider = mock(OrganizationProvider.class);

            UserModel result = userModelFactory.create(legacyUser, realm);

            assertThat(result).isNotNull();
            verifyNoInteractions(provider);
        }

        @Test
        void shouldNotImportRolesOnFirstLoginGivenRoleSyncModeIsNoSync() {
            final LegacyUser legacyUser = TestLegacyUser.withTwoRoles();
            config.putSingle(UPDATE_USER_ROLES_ON_LOGIN, "NO_SYNC");
            configureMigrationOfUnmappedRoles();
            userModelWillBeCreatedSuccessfullyWithoutIdMigration(legacyUser);
            userModelFactory = constructUserModelFactory();

            UserModel result = userModelFactory.create(legacyUser, realm);

            assertThat(result.getRoleMappingsStream().toList()).isEmpty();
        }

        @Test
        void shouldNotImportIgnoredRolesOnFirstLogin() {
            final LegacyUser legacyUser = TestLegacyUser.withTwoRoles();
            userModelWillBeCreatedSuccessfullyWithoutIdMigration(legacyUser);
            configureMigrationOfUnmappedRoles();
            config.put(IGNORED_SYNC_ROLES_PROPERTY, List.of("another*"));
            RoleModel importedRole = asRoleModel(legacyUser.roles().getFirst());
            RoleModel ignoredRole = asRoleModel(legacyUser.roles().get(1));
            existInRealm(importedRole, ignoredRole);
            userModelFactory = constructUserModelFactory();

            UserModel result = userModelFactory.create(legacyUser, realm);

            assertThat(result.getRoleMappingsStream().toList())
                    .containsExactly(importedRole);
        }

        @Test
        void shouldNotImportGroupsOnFirstLoginGivenGroupSyncModeIsNoSync() {
            final LegacyUser legacyUser = TestLegacyUser.withTwoGroups();
            config.putSingle(UPDATE_USER_GROUPS_ON_LOGIN, "NO_SYNC");
            configureMigrationOfUnmappedGroups();
            userModelWillBeCreatedSuccessfullyWithoutIdMigration(legacyUser);
            userModelFactory = constructUserModelFactory();

            UserModel result = userModelFactory.create(legacyUser, realm);

            assertThat(result.getGroupsStream().toList()).isEmpty();
        }

        @Test
        void shouldNotImportIgnoredGroupsOnFirstLogin() {
            final LegacyUser legacyUser = TestLegacyUser.withTwoGroups();
            userModelWillBeCreatedSuccessfullyWithoutIdMigration(legacyUser);
            configureMigrationOfUnmappedGroups();
            config.put(IGNORED_SYNC_GROUPS_PROPERTY, List.of("another*"));

            GroupModel importedGroup = asGroupModel(legacyUser.groups().getFirst());
            GroupModel ignoredGroup = asGroupModel(legacyUser.groups().get(1));
            existingGroupsInRealm(importedGroup, ignoredGroup);
            userModelFactory = constructUserModelFactory();

            UserModel result = userModelFactory.create(legacyUser, realm);

            assertThat(result.getGroupsStream().toList())
                    .containsExactly(importedGroup);
        }

        @Test
        void shouldMigrateAdditionalAttributes() {
            final LegacyUser legacyUser = TestLegacyUser.withOneAttribute();
            userModelWillBeCreatedSuccessfullyWithoutIdMigration(legacyUser);
            userModelFactory = constructUserModelFactory();

            UserModel result = userModelFactory.create(legacyUser, realm);

            assertThat(result.getAttributes())
                    .isEqualTo(legacyUser.attributes());
        }

        @Test
        void shouldSetFederationLink() {
            final LegacyUser legacyUser = TestLegacyUser.minimal();
            userModelWillBeCreatedSuccessfullyWithoutIdMigration(legacyUser);
            userModelFactory = constructUserModelFactory();

            UserModel result = userModelFactory.create(legacyUser, realm);

            assertThat(result.getFederationLink()).isEqualTo(MODEL_ID);
        }

        @Test
        void shouldMigrateRequiredActions() {
            final LegacyUser legacyUser = TestLegacyUser.withTwoRequiredActions();
            userModelWillBeCreatedSuccessfullyWithoutIdMigration(legacyUser);
            userModelFactory = constructUserModelFactory();

            UserModel result = userModelFactory.create(legacyUser, realm);

            assertThat(result.getRequiredActionsStream().toList())
                    .containsExactlyInAnyOrderElementsOf(legacyUser.requiredActions());
        }

        @Test
        void shouldMigrateTotp() {
            final LegacyUser legacyUser = TestLegacyUser.withTwoTotps();
            userModelWillBeCreatedSuccessfullyWithoutIdMigration(legacyUser);
            final LegacyTotp legacyTotp1 = legacyUser.totps().getFirst();
            final LegacyTotp legacyTotp2 = legacyUser.totps().get(1);
            userModelFactory = constructUserModelFactory();

            UserModel result = userModelFactory.create(legacyUser, realm);

            List<CredentialModel> storedTotpCredentials = getStoredTotpCredentials(result);
            assertCredentialsMigratedSuccessfully(storedTotpCredentials, legacyTotp1, legacyTotp2);
        }

        private void assertCredentialsMigratedSuccessfully(
                List<CredentialModel> storedTotpCredentials, LegacyTotp legacyTotp1, LegacyTotp legacyTotp2) {
            assertThat(storedTotpCredentials).hasSize(2);
            assertThat(findTotpCredentialByName(storedTotpCredentials, legacyTotp1.name())).get()
                    .extracting(CredentialModel::getSecretData)
                    .isEqualTo(expectedTotpSecretData(legacyTotp1));
            assertThat(findTotpCredentialByName(storedTotpCredentials, legacyTotp2.name())).get()
                    .extracting(CredentialModel::getSecretData)
                    .isEqualTo(expectedTotpSecretData(legacyTotp2));
        }

        private Optional<CredentialModel> findTotpCredentialByName(List<CredentialModel> storedTotpCredentials,
                                                                   String anObject) {
            return storedTotpCredentials.stream()
                    .filter(item -> item.getUserLabel().equals(anObject))
                    .findFirst();
        }

        private String expectedTotpSecretData(LegacyTotp legacyTotp) {
            return "{\"value\":\"" + legacyTotp.secret() + "\"}";
        }

        private List<CredentialModel> getStoredTotpCredentials(UserModel result) {
            return result.credentialManager()
                    .getStoredCredentialsByTypeStream(OTPCredentialModel.TYPE)
                    .toList();
        }

        @Test
        void shouldCreateUserWithExistingOrganization() {
            final LegacyUser legacyUser = TestLegacyUser.withOneOrganization();
            userModelWillBeCreatedSuccessfullyWithoutIdMigration(legacyUser);
            userModelFactory = constructUserModelFactory();

            organizationsAreEnabledInRealm();
            OrganizationModel orgMock = mock(OrganizationModel.class);
            organizationIsFoundByAlias(orgMock);

            UserModel result = userModelFactory.create(legacyUser, realm);

            assertThat(result).isNotNull();
            verify(organizationProvider, times(1)).addManagedMember(orgMock, result);
        }

        @SuppressWarnings("OptionalGetWithoutIsPresent")
        @Test
        void shouldCreateUserWithNotExistingOrganization() {

            final LegacyUser legacyUser = TestLegacyUser.withOneOrganization();
            final LegacyOrganization legacyOrganization = legacyUser.organizations().getFirst();
            OrganizationModel orgMock = mock(OrganizationModel.class);

            organizationsAreEnabledInRealm();
            noOrganizationsExist();
            organizationWillBeSuccessfullyCreated(legacyOrganization, orgMock);

            userModelWillBeCreatedSuccessfullyWithoutIdMigration(legacyUser);

            userModelFactory = constructUserModelFactory();
            UserModel result = userModelFactory.create(legacyUser, realm);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Set<OrganizationDomainModel>> captor = ArgumentCaptor.forClass(Set.class);

            assertThat(result).isNotNull();
            verify(organizationProvider, times(1)).create(legacyOrganization.orgName(), legacyOrganization.orgAlias());
            verify(organizationProvider, times(1)).addManagedMember(orgMock, result);
            verify(orgMock).setDomains(captor.capture());
            Set<OrganizationDomainModel> organizationDomainModelSet = captor.getValue();
            assertThat(organizationDomainModelSet.stream().findFirst().get().getName())
                    .isEqualTo(legacyUser.organizations().getFirst().domains().getFirst().domainName());
        }

        @Test
        void shouldCreateUserWithNotExistingOrganizationButWithoutDomain() {

            final LegacyUser legacyUser = TestLegacyUser.withOneOrganizationWithoutDomains();
            final LegacyOrganization legacyOrganization = legacyUser.organizations().getFirst();
            OrganizationModel orgMock = mock(OrganizationModel.class);

            organizationsAreEnabledInRealm();
            noOrganizationsExist();
            organizationWillBeSuccessfullyCreated(legacyOrganization, orgMock);

            userModelWillBeCreatedSuccessfullyWithoutIdMigration(legacyUser);

            userModelFactory = constructUserModelFactory();
            UserModel result = userModelFactory.create(legacyUser, realm);

            assertThat(result).isNotNull();
            verify(organizationProvider, times(1))
                    .create(legacyOrganization.orgName(), legacyOrganization.orgAlias());
            verify(organizationProvider, times(1))
                    .addManagedMember(orgMock, result);
            verify(orgMock, never())
                    .setDomains(anySet());
        }

        @Test
        void shouldCreateUserWithNotExistingOrganizationButWithNullDomain() {

            final LegacyUser legacyUser = TestLegacyUser.withOneOrganizationWithNullDomains();
            final LegacyOrganization legacyOrganization = legacyUser.organizations().getFirst();
            OrganizationModel orgMock = mock(OrganizationModel.class);

            organizationsAreEnabledInRealm();
            noOrganizationsExist();
            organizationWillBeSuccessfullyCreated(legacyOrganization, orgMock);

            userModelWillBeCreatedSuccessfullyWithoutIdMigration(legacyUser);

            userModelFactory = constructUserModelFactory();
            UserModel result = userModelFactory.create(legacyUser, realm);

            assertThat(result).isNotNull();
            verify(organizationProvider, times(1))
                    .create(legacyOrganization.orgName(), legacyOrganization.orgAlias());
            verify(organizationProvider, times(1))
                    .addManagedMember(orgMock, result);
            verify(orgMock, never())
                    .setDomains(anySet());
        }

        @Test
        void shouldUseDefaultIgnoredRolesAndEmptyIgnoredGroupsGivenConfiguredListsAreNull() {
            ComponentModel modelWithNullIgnoredLists = modelWithNullIgnoredLists();
            var roleMigrationService = new RoleMigrationService(modelWithNullIgnoredLists,
                    legacyMappingParser, wildcardPatternFactory);
            var groupMigrationService = new GroupMigrationService(modelWithNullIgnoredLists,
                    legacyMappingParser, wildcardPatternFactory);
            var factory = new UserModelFactory(session, new MigrationConfiguration(modelWithNullIgnoredLists),
                    roleMigrationService, groupMigrationService, totpValidationService);

            var legacyUser = new LegacyUser(
                    null,
                    "someUserName",
                    "user@email.com",
                    "John",
                    "Smith",
                    true,
                    true,
                    Map.of(),
                    List.of("offline_access"),
                    List.of("manual-group"),
                    emptyList(),
                    emptyList(),
                    emptyList()
            );
            userModelWillBeCreatedSuccessfullyWithoutIdMigration(legacyUser);
            GroupModel manualGroup = asGroupModel("manual-group");
            existingGroupsInRealm(manualGroup);

            UserModel result = factory.create(legacyUser, realm);

            assertThat(result.getRoleMappingsStream().toList())
                    .isEmpty();
            assertThat(result.getGroupsStream().toList())
                    .containsExactly(manualGroup);
        }
    }

    private void userModelWillBeCreatedSuccessfullyWithLowercasedUsername(LegacyUser legacyUser) {
        when(userProvider.addUser(realm, null, legacyUser.username(), true,
                legacyUser.shouldAddDefaultRequiredActions()))
                .thenReturn(new TestUserModel(legacyUser.username().toLowerCase()));
    }

    @Nested
    class SynchronizeRoles {

        @Test
        void shouldSynchronizeRolesByAddingMissingAndRemovingStaleMappings() {
            final LegacyUser legacyUser = TestLegacyUser.withTwoRoles();
            configureMigrationOfUnmappedRoles();
            userModelFactory = constructUserModelFactory();

            RoleModel mappedRole = asRoleModel(legacyUser.roles().getFirst());
            RoleModel missingRole = asRoleModel(legacyUser.roles().get(1));
            RoleModel staleRole = staleRole();
            existInRealm(mappedRole, missingRole);
            UserModel userModel = userModelWithRoles(mappedRole, staleRole);

            userModelFactory.synchronizeRoles(legacyUser, realm, userModel);

            verify(userModel).grantRole(missingRole);
            verify(userModel).deleteRoleMapping(staleRole);
            verify(userModel, never()).deleteRoleMapping(mappedRole);
        }

        private UserModel userModelWithRoles(RoleModel... roleModels) {
            UserModel userModel = mock(UserModel.class);
            when(userModel.getRoleMappingsStream())
                    .thenReturn(Stream.of(roleModels));
            return userModel;
        }

        private RoleModel staleRole() {
            return asRoleModel("staleRole");
        }

        @Test
        void shouldSynchronizeRolesUsingRoleIdAsIdentityKey() {
            String roleName = "legacy-role";
            final LegacyUser legacyUser = aLegacyUserWithRoles(List.of(roleName));
            configureMigrationOfUnmappedRoles();
            userModelFactory = constructUserModelFactory();
            String roleId = "role-id-1";

            RoleModel desiredRole = roleModelWithIdAndName(roleId, roleName);
            existInRealm(desiredRole);

            RoleModel currentRole = roleModelWithIdAndName(roleId, roleName);
            UserModel userModel = userModelWithRoles(currentRole);

            userModelFactory.synchronizeRoles(legacyUser, realm, userModel);

            verify(userModel, never()).grantRole(any());
            verify(userModel, never()).deleteRoleMapping(any());
        }

        private LegacyUser aLegacyUserWithRoles(List<String> roles) {
            return new LegacyUser(
                    null,
                    "someUserName",
                    "user@email.com",
                    "John",
                    "Smith",
                    true,
                    true,
                    Map.of(),
                    roles,
                    emptyList(),
                    emptyList(),
                    emptyList(),
                    emptyList()
            );
        }

        @Test
        void shouldSynchronizeRolesUsingNameKeyGivenRoleIdIsBlank() {
            final LegacyUser legacyUser = aLegacyUserWithRoles(List.of("legacy-role"));
            configureMigrationOfUnmappedRoles();
            userModelFactory = constructUserModelFactory();

            RoleModel desiredRole = roleModelWithIdAndName(" ", "legacy-role");
            existInRealm(desiredRole);

            RoleModel currentRole = roleModelWithIdAndName("", "legacy-role");
            UserModel userModel = userModelWithRoles(currentRole);

            userModelFactory.synchronizeRoles(legacyUser, realm, userModel);

            verify(userModel, never()).grantRole(any());
            verify(userModel, never()).deleteRoleMapping(any());
        }

        @Test
        void shouldRemoveCurrentRoleWithBlankNameGivenAbsentFromLegacy() {
            final LegacyUser legacyUser = aLegacyUserWithRoles(emptyList());
            configureMigrationOfUnmappedRoles();
            userModelFactory = constructUserModelFactory();

            RoleModel currentRole = roleModelWithIdAndName(null, " ");
            UserModel userModel = userModelWithRoles(currentRole);

            userModelFactory.synchronizeRoles(legacyUser, realm, userModel);

            verify(userModel).deleteRoleMapping(currentRole);
        }

        @Test
        void shouldNotRemoveIgnoredRolesDuringSynchronization() {
            final LegacyUser legacyUser = TestLegacyUser.withTwoRoles();
            configureMigrationOfUnmappedRoles();
            config.put(IGNORED_SYNC_ROLES_PROPERTY, List.of("manage-*"));
            userModelFactory = constructUserModelFactory();

            RoleModel mappedRole = asRoleModel(legacyUser.roles().getFirst());
            RoleModel missingRole = asRoleModel(legacyUser.roles().get(1));
            RoleModel untrackedRole = asRoleModel("manage-account");
            existInRealm(mappedRole, missingRole);
            UserModel userModel = userModelWithRoles(mappedRole, untrackedRole);

            userModelFactory.synchronizeRoles(legacyUser, realm, userModel);

            verify(userModel, never()).deleteRoleMapping(untrackedRole);
        }

        @Test
        void shouldTreatRegexMetaCharactersAsLiteralsInIgnoredRolePatterns() {
            final LegacyUser legacyUser = TestLegacyUser.withTwoRoles();
            configureMigrationOfUnmappedRoles();
            config.put(IGNORED_SYNC_ROLES_PROPERTY, List.of("team.(ops)-*"));
            userModelFactory = constructUserModelFactory();

            RoleModel mappedRole = asRoleModel(legacyUser.roles().getFirst());
            RoleModel missingRole = asRoleModel(legacyUser.roles().get(1));
            RoleModel untrackedRole = asRoleModel("team.(ops)-admin");
            existInRealm(mappedRole, missingRole);
            UserModel userModel = userModelWithRoles(mappedRole, untrackedRole);

            userModelFactory.synchronizeRoles(legacyUser, realm, userModel);

            verify(userModel, never()).deleteRoleMapping(untrackedRole);
        }

        @Test
        void shouldSynchronizeRolesByOnlyAddingMissingMappings() {
            final LegacyUser legacyUser = TestLegacyUser.withTwoRoles();
            configureMigrationOfUnmappedRoles();
            userModelFactory = constructUserModelFactory();

            RoleModel mappedRole = asRoleModel(legacyUser.roles().getFirst());
            RoleModel missingRole = asRoleModel(legacyUser.roles().get(1));
            RoleModel staleRole = staleRole();
            existInRealm(mappedRole, missingRole);
            UserModel userModel = userModelWithRoles(mappedRole, staleRole);

            userModelFactory.synchronizeRolesAddOnly(legacyUser, realm, userModel);

            verify(userModel).grantRole(missingRole);
            verify(userModel, never()).deleteRoleMapping(any());
        }

        @Test
        void shouldHandleDuplicateRoleKeysInReconcileAndAddOnlyPaths() {
            final LegacyUser legacyUser = aLegacyUserWithRoles(List.of("legacy-role-1", "legacy-role-2"));
            configureMigrationOfUnmappedRoles();
            userModelFactory = constructUserModelFactory();

            RoleModel desiredRole1 = roleModelWithIdAndName("dup-role-id", "legacy-role-1");
            RoleModel desiredRole2 = roleModelWithIdAndName("dup-role-id", "legacy-role-2");
            existInRealm(desiredRole1, desiredRole2);

            RoleModel currentRole1 = roleModelWithIdAndName("dup-role-id", null);
            RoleModel currentRole2 = roleModelWithIdAndName("dup-role-id", null);
            UserModel userModel = userModelWithRoleMappings(currentRole1, currentRole2);

            userModelFactory.synchronizeRoles(legacyUser, realm, userModel);
            userModelFactory.synchronizeRolesAddOnly(legacyUser, realm, userModel);

            verify(userModel, never()).grantRole(any());
            verify(userModel, never()).deleteRoleMapping(any());
        }
    }

    private UserModel userModelWithRoleMappings(RoleModel... roleModels) {
        UserModel userModel = mock(UserModel.class);
        when(userModel.getRoleMappingsStream())
                .thenAnswer(invocation -> Stream.of(roleModels));
        return userModel;
    }

    @Nested
    class SynchronizeGroups {

        @Test
        void shouldSynchronizeGroupsByAddingMissingAndRemovingStaleMemberships() {
            final LegacyUser legacyUser = TestLegacyUser.withTwoGroups();
            configureMigrationOfUnmappedGroups();
            userModelFactory = constructUserModelFactory();

            GroupModel mappedGroup = asGroupModel(legacyUser.groups().getFirst());
            GroupModel missingGroup = asGroupModel(legacyUser.groups().get(1));
            GroupModel staleGroup = staleGroup();
            existingGroupsInRealm(mappedGroup, missingGroup, staleGroup);
            UserModel userModel = userModelWithGroups(mappedGroup, staleGroup);

            userModelFactory.synchronizeGroups(legacyUser, realm, userModel);

            verify(userModel).joinGroup(missingGroup);
            verify(userModel).leaveGroup(staleGroup);
            verify(userModel, never()).leaveGroup(mappedGroup);
        }

        private UserModel userModelWithGroups(GroupModel... groupModels) {
            UserModel userModel = mock(UserModel.class);
            when(userModel.getGroupsStream())
                    .thenReturn(Stream.of(groupModels));
            return userModel;
        }

        private GroupModel staleGroup() {
            return asGroupModel("staleGroup");
        }

        @Test
        void shouldSynchronizeGroupsByOnlyAddingMissingMemberships() {
            final LegacyUser legacyUser = TestLegacyUser.withTwoGroups();
            configureMigrationOfUnmappedGroups();
            userModelFactory = constructUserModelFactory();

            GroupModel mappedGroup = asGroupModel(legacyUser.groups().getFirst());
            GroupModel missingGroup = asGroupModel(legacyUser.groups().get(1));
            GroupModel staleGroup = staleGroup();
            existingGroupsInRealm(mappedGroup, missingGroup, staleGroup);
            UserModel userModel = userModelWithGroups(mappedGroup, staleGroup);

            userModelFactory.synchronizeGroupsAddOnly(legacyUser, realm, userModel);

            verify(userModel).joinGroup(missingGroup);
            verify(userModel, never()).leaveGroup(any());
        }

        @Test
        void shouldSynchronizeGroupsUsingGroupIdAsIdentityKey() {
            String groupName = "legacy-group";
            String groupId = "group-id-1";
            final LegacyUser legacyUser = aLegacyUserWithGroups(List.of(groupName));
            configureMigrationOfUnmappedGroups();
            userModelFactory = constructUserModelFactory();

            GroupModel desiredGroup = groupWithIdAndName(groupId, groupName);
            existingGroupsInRealm(desiredGroup);

            GroupModel currentGroup = groupWithIdAndName(groupId, groupName);
            UserModel userModel = userModelWithGroups(currentGroup);

            userModelFactory.synchronizeGroups(legacyUser, realm, userModel);

            verify(userModel, never()).joinGroup(any());
            verify(userModel, never()).leaveGroup(any());
        }

        private LegacyUser aLegacyUserWithGroups(List<String> groups) {
            return new LegacyUser(
                    null,
                    "someUserName",
                    "user@email.com",
                    "John",
                    "Smith",
                    true,
                    true,
                    Map.of(),
                    emptyList(),
                    groups,
                    emptyList(),
                    emptyList(),
                    emptyList()
            );
        }

        @Test
        void shouldSynchronizeGroupsUsingNameKeyGivenGroupIdIsBlank() {
            final LegacyUser legacyUser = aLegacyUserWithGroups(List.of("legacy-group"));
            configureMigrationOfUnmappedGroups();
            userModelFactory = constructUserModelFactory();

            GroupModel desiredGroup = groupWithIdAndName(" ", "legacy-group");
            existingGroupsInRealm(desiredGroup);

            GroupModel currentGroup = groupWithIdAndName("", "legacy-group");
            UserModel userModel = userModelWithGroups(currentGroup);

            userModelFactory.synchronizeGroups(legacyUser, realm, userModel);

            verify(userModel, never()).joinGroup(any());
            verify(userModel, never()).leaveGroup(any());
        }

        @Test
        void shouldHandleDuplicateGroupKeysInAddOnlyPath() {
            final LegacyUser legacyUser = aLegacyUserWithGroups(List.of("legacy-group-1", "legacy-group-2"));
            configureMigrationOfUnmappedGroups();
            userModelFactory = constructUserModelFactory();

            GroupModel desiredGroup1 = groupWithIdAndName("dup-group-id", "legacy-group-1");
            GroupModel desiredGroup2 = groupWithIdAndName("dup-group-id", "legacy-group-2");
            existingGroupsInRealm(desiredGroup1, desiredGroup2);

            GroupModel currentGroup1 = groupWithIdAndName("dup-group-id", null);
            GroupModel currentGroup2 = groupWithIdAndName("dup-group-id", null);
            UserModel userModel = userModelWithGroups(currentGroup1, currentGroup2);

            userModelFactory.synchronizeGroupsAddOnly(legacyUser, realm, userModel);

            verify(userModel, never()).joinGroup(any());
        }

        @Test
        void shouldRemoveCurrentGroupWithNullNameGivenAbsentFromLegacy() {
            final LegacyUser legacyUser = aLegacyUserWithGroups(emptyList());
            configureMigrationOfUnmappedGroups();
            userModelFactory = constructUserModelFactory();

            GroupModel currentGroup = groupWithIdAndName(null, null);
            UserModel userModel = userModelWithGroups(currentGroup);

            userModelFactory.synchronizeGroups(legacyUser, realm, userModel);

            verify(userModel).leaveGroup(currentGroup);
        }

        @Test
        void shouldRemoveCurrentGroupWithBlankNameGivenAbsentFromLegacy() {
            final LegacyUser legacyUser = aLegacyUserWithGroups(emptyList());
            configureMigrationOfUnmappedGroups();
            userModelFactory = constructUserModelFactory();

            GroupModel currentGroup = groupWithIdAndName(null, " ");
            UserModel userModel = userModelWithGroups(currentGroup);

            userModelFactory.synchronizeGroups(legacyUser, realm, userModel);

            verify(userModel).leaveGroup(currentGroup);
        }

        @Test
        void shouldNotRemoveIgnoredGroupsDuringSynchronization() {
            final LegacyUser legacyUser = TestLegacyUser.withTwoGroups();
            configureMigrationOfUnmappedGroups();
            config.put(IGNORED_SYNC_GROUPS_PROPERTY, List.of("manual-*"));
            userModelFactory = constructUserModelFactory();

            GroupModel mappedGroup = asGroupModel(legacyUser.groups().getFirst());
            GroupModel missingGroup = asGroupModel(legacyUser.groups().get(1));
            GroupModel untrackedGroup = asGroupModel("manual-group");
            existingGroupsInRealm(mappedGroup, missingGroup, untrackedGroup);
            UserModel userModel = userModelWithGroups(mappedGroup, untrackedGroup);

            userModelFactory.synchronizeGroups(legacyUser, realm, userModel);

            verify(userModel, never()).leaveGroup(untrackedGroup);
        }
    }

    private GroupModel groupWithIdAndName(String groupId, String groupName) {
        GroupModel desiredGroup = asGroupModel(groupName);
        when(desiredGroup.getId())
                .thenReturn(groupId);
        return desiredGroup;
    }

    @Nested
    class IsDuplicateUserId {

        @Test
        void isDuplicateUserIdShouldReturnFalseGivenNotMigratingUserId() {
            final LegacyUser legacyUser = TestLegacyUser.minimal();
            userModelFactory = constructUserModelFactory();

            var result = userModelFactory.isDuplicateUserId(legacyUser, realm);

            assertThat(result).isFalse();
        }

        @Test
        void isDuplicateUserIdShouldReturnTrueGivenMigratingIdAndItAlreadyExists() {
            final LegacyUser legacyUser = TestLegacyUser.withId();
            mockUserModelWithSameIdAlreadyExists(legacyUser);
            userModelFactory = constructUserModelFactory();

            var result = userModelFactory.isDuplicateUserId(legacyUser, realm);

            assertThat(result).isTrue();
        }

        private void mockUserModelWithSameIdAlreadyExists(LegacyUser legacyUser) {
            when(userProvider.getUserById(realm, legacyUser.id()))
                    .thenReturn(matchingUserModel(legacyUser));
        }

        @Test
        void isDuplicateUserIdShouldReturnFalseGivenMigratingIdAndItDoesntExist() {
            final LegacyUser legacyUser = TestLegacyUser.withId();
            mockNoExistingUserModelWithSameId(legacyUser);
            userModelFactory = constructUserModelFactory();

            var result = userModelFactory.isDuplicateUserId(legacyUser, realm);

            assertThat(result).isFalse();
        }

        private void mockNoExistingUserModelWithSameId(LegacyUser legacyUser) {
            when(userProvider.getUserById(realm, legacyUser.id()))
                    .thenReturn(null);
        }
    }

    @Nested
    class UpdateUserAttributes {

        @Test
        void shouldUpdateUsernameOnExistingUser() {
            var legacyUser = new LegacyUser(
                    null,
                    "newUsername",
                    "email@example.com",
                    "First",
                    "Last",
                    true, true,
                    Map.of(),
                    emptyList(),
                    emptyList(),
                    emptyList(),
                    emptyList(),
                    emptyList()
            );
            UserModel userModel = new TestUserModel("oldUsername");

            userModelFactory = constructUserModelFactory();
            userModelFactory.updateUserAttributes(legacyUser, userModel);

            assertThat(userModel.getUsername()).isEqualTo("newUsername");
        }

        @Test
        void shouldUpdateBasicAttributesOnExistingUser() {
            var legacyUser = new LegacyUser(
                    null,
                    "username",
                    "new@email.com",
                    "NewFirst",
                    "NewLast",
                    false, false,
                    Map.of(),
                    emptyList(),
                    emptyList(),
                    emptyList(),
                    emptyList(),
                    emptyList()
            );
            UserModel userModel = new TestUserModel("username");
            userModel.setEmail("old@email.com");
            userModel.setFirstName("OldFirst");
            userModel.setLastName("OldLast");
            userModel.setEnabled(true);
            userModel.setEmailVerified(true);

            userModelFactory = constructUserModelFactory();
            userModelFactory.updateUserAttributes(legacyUser, userModel);

            assertThat(userModel.getEmail()).isEqualTo("new@email.com");
            assertThat(userModel.getFirstName()).isEqualTo("NewFirst");
            assertThat(userModel.getLastName()).isEqualTo("NewLast");
            assertThat(userModel.isEnabled()).isFalse();
            assertThat(userModel.isEmailVerified()).isFalse();
        }

        @Test
        void shouldOverwriteExistingCustomAttributes() {
            var legacyUser = new LegacyUser(
                    null,
                    "username",
                    "email",
                    "first",
                    "last",
                    true,
                    true,
                    Map.of("attr1", List.of("newVal")),
                    emptyList(),
                    emptyList(),
                    emptyList(),
                    emptyList(),
                    emptyList()
            );
            UserModel userModel = new TestUserModel("username");
            userModel.setAttribute("attr1", List.of("oldVal"));

            userModelFactory = constructUserModelFactory();
            userModelFactory.updateUserAttributes(legacyUser, userModel);

            assertThat(userModel.getAttributeStream("attr1")).containsExactly("newVal");
        }

        @Test
        void shouldNotRemoveExtraLocalAttributes() {
            var legacyUser = new LegacyUser(
                    null,
                    "username",
                    "email",
                    "first",
                    "last",
                    true,
                    true,
                    Map.of("attr1", List.of("val1")),
                    emptyList(),
                    emptyList(),
                    emptyList(),
                    emptyList(),
                    emptyList()
            );
            UserModel userModel = new TestUserModel("username");
            userModel.setAttribute("localAttr", List.of("localVal"));

            userModelFactory = constructUserModelFactory();
            userModelFactory.updateUserAttributes(legacyUser, userModel);

            assertThat(userModel.getAttributeStream("attr1")).containsExactly("val1");
            assertThat(userModel.getAttributeStream("localAttr")).containsExactly("localVal");
        }

        @Test
        void shouldHandleNullAttributesInLegacyUserGracefully() {
            var legacyUser = new LegacyUser(
                    null,
                    "username",
                    "email",
                    "first",
                    "last",
                    true,
                    true,
                    null,
                    emptyList(),
                    emptyList(),
                    emptyList(),
                    emptyList(),
                    emptyList()
            );
            UserModel userModel = new TestUserModel("username");
            userModel.setAttribute("localAttr", List.of("localVal"));

            userModelFactory = constructUserModelFactory();
            // Should not throw NPE
            userModelFactory.updateUserAttributes(legacyUser, userModel);

            assertThat(userModel.getAttributeStream("localAttr")).containsExactly("localVal");
        }
    }
}
