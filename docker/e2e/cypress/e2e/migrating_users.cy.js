// migrating_users.spec.js created with Cypress
//
// Start writing your Cypress tests below!
// If you're unfamiliar with how Cypress works,
// check out the link below and learn how to write your first test:
// https://on.cypress.io/writing-first-test

const LoginPage = require("./loginPage");
const Keycloak = require("./keycloak");
const RealmSettingsLoginPage = require("./realmSettingsLoginPage");
const UserMigrationProviderPage = require("./userMigrationProviderPage");
const UserFederationPage = require("./userFederationPage");
const UsersPage = require("./usersPage");
const UserDetailsPage = require("./userDetailsPage");
const AccountDetailsPage = require("./accountDetailsPage");
const RealmSettingsEmailPage = require("./realmSettingsEmailPage");
const PasswordPoliciesPage = require("./passwordPoliciesPage");
const ResetPasswordPage = require("./resetPasswordPage");
const ForgotPasswordPage = require("./forgotPasswordPage");
const ResetPasswordEmail = require("./resetPasswordEmail");
const UpdatePasswordPage = require("./updatePasswordPage");
const UpdateAccountInformationPage = require("./updateAccountInformationPage");

const LEGACY_SYSTEM_URL = "http://legacy-system-example:8080/user-migration-support";

const SMTP_HOST = "mailhog";
const SMTP_PORT = "1025";
const SMTP_FROM = "admin@example.com";

const ADMIN_USERNAME = "admin";
const ADMIN_PASSWORD = "admin";
const ADMIN_EMAIL = 'admin@example.com';

const LEGACY_USER_USERNAME = "lucy";
const LEGACY_USER_PASSWORD = "password";
const LEGACY_USER_PASSWORD_WHICH_MEETS_POLICY = "pa$$word";
const LEGACY_USER_EMAIL = 'lucy@example.com';
const LEGACY_USER_FIRST_NAME = 'Lucy';
const LEGACY_USER_LAST_NAME = 'Brennan';

const PLUGIN_NAME = 'Custom REST Client Provider';

const keycloak = new Keycloak();
const loginPage = new LoginPage();
const realmSettingsLoginPage = new RealmSettingsLoginPage();
const userFederationPage = new UserFederationPage(keycloak, PLUGIN_NAME);
const userMigrationProviderPage = new UserMigrationProviderPage(keycloak, userFederationPage,
    PLUGIN_NAME);
const usersPage = new UsersPage(keycloak);
const userDetailsPage = new UserDetailsPage(usersPage);
const accountDetailsPage = new AccountDetailsPage(keycloak);
const realmSettingsEmailPage = new RealmSettingsEmailPage(keycloak);
const passwordPoliciesPage = new PasswordPoliciesPage(keycloak);
const forgotPasswordPage = new ForgotPasswordPage();
const resetPasswordEmail = new ResetPasswordEmail();
const resetPasswordPage = new ResetPasswordPage();
const updatePasswordPage = new UpdatePasswordPage();
const updateAccountInformationPage = new UpdateAccountInformationPage();

describe('user migration plugin', () => {

    before(() => {
        signInAsAdmin();
        configureLoginSettings();
        configureEmails();
        configureMigrationPlugin();
        keycloak.signOutViaUIAndClearCache();
    });

    function signInAsAdmin() {
        loginPage.visitForAdmin();
        loginPage.logIn(ADMIN_USERNAME, ADMIN_PASSWORD)
    }

    function configureLoginSettings() {
        realmSettingsLoginPage.visit();
        realmSettingsLoginPage.toggleForgotPasswordSwitchTo(true);
    }

    function configureMigrationPlugin() {
        userFederationPage.visit();
        userFederationPage.removePluginIfExists();
        userFederationPage.goToUserMigrationPluginPage();
        userMigrationProviderPage.addPlugin(LEGACY_SYSTEM_URL);
    }

    function configureEmails() {
        writeAdminPersonalInfo();
        configureAdminPersonalInfo();
        configureSmtpSettings();
    }

    /**
     * Write Admin user info first, so it becomes visible in account console.
     * If fields are not populated here, they will not be visible in user account (KC Bug??)
     */
    function writeAdminPersonalInfo() {
        userDetailsPage.visit(ADMIN_USERNAME);
        userDetailsPage.writePersonalInfo(ADMIN_EMAIL, ADMIN_USERNAME);
    }

    function configureAdminPersonalInfo() {
        accountDetailsPage.visit();
        accountDetailsPage.configurePersonalInfo(ADMIN_EMAIL, ADMIN_USERNAME, ADMIN_USERNAME);
    }

    function configureSmtpSettings() {
        realmSettingsEmailPage.visit();
        realmSettingsEmailPage.configureSmtpSettings(SMTP_HOST, SMTP_PORT, SMTP_FROM);
    }

    beforeEach(() => {
        deleteEmails();
        signInAsAdmin();
        return deleteTestUserIfExists()
            .then(() => deletePasswordPoliciesIfExist()
                .then(() => keycloak.signOutViaUIAndClearCache()));
    });

    function deleteEmails() {
        cy.mhDeleteAll();
        cy.mhGetAllMails()
            .should('have.length', 0);
    }

    function deleteTestUserIfExists() {
        usersPage.visit();
        return usersPage.deleteUserIfExists(LEGACY_USER_USERNAME);
    }

    function deletePasswordPoliciesIfExist() {
        passwordPoliciesPage.visit();
        return passwordPoliciesPage.deleteEveryPasswordPolicy();
    }

    it('should migrate user', () => {
        signInAsLegacyUser();
        updateAccountInformationPage.confirmAccountInformation();
        updateAccountInformation();
        keycloak.assertIsLoggedInAsUser(LEGACY_USER_FIRST_NAME, LEGACY_USER_LAST_NAME);
    });

    function signInAsLegacyUser() {
        loginPage.visitForUser();
        loginPage.logIn(LEGACY_USER_USERNAME, LEGACY_USER_PASSWORD);
    }

    function updateAccountInformation() {
        accountDetailsPage.visit();
        accountDetailsPage.configurePersonalInfo(LEGACY_USER_EMAIL, LEGACY_USER_FIRST_NAME, LEGACY_USER_LAST_NAME);
    }

    it('should reset password after inputting wrong credentials', () => {
        attemptLoginWithWrongPassword();
        triggerPasswordReset();
        resetPasswordViaEmail();
    });

    function attemptLoginWithWrongPassword() {
        loginPage.visitForUser();
        loginPage.logIn(LEGACY_USER_USERNAME, "wrongPassword");
    }

    function triggerPasswordReset() {
        forgotPasswordPage.visit();
        forgotPasswordPage.triggerPasswordReset(LEGACY_USER_EMAIL);
    }

    function resetPasswordViaEmail() {
        resetPasswordEmail.getResetPasswordUrl()
            .then(resetPassUrl => {
                cy.visit(resetPassUrl);
                updateAccountInformationPage.confirmAccountInformation();
                resetPasswordPage.chooseNewPassword(LEGACY_USER_PASSWORD)
                keycloak.assertIsLoggedInAsUser(LEGACY_USER_FIRST_NAME, LEGACY_USER_LAST_NAME);
            });
    }

    it('should reset password before user is migrated', () => {
        cy.visit('/realms/master/account');
        // clickSignBtnInIfExists();
        triggerPasswordReset();
        resetPasswordViaEmail()
    });

    it('should migrate user when password breaks policy', () => {
        signInAsAdmin();
        addSpecialCharactersPasswordPolicy();
        keycloak.signOutViaUIAndClearCache();

        signInAsLegacyUser();
        updatePasswordPage.chooseNewPassword(LEGACY_USER_PASSWORD_WHICH_MEETS_POLICY);
        updateAccountInformationPage.confirmAccountInformation();
        keycloak.assertIsLoggedInAsUser(LEGACY_USER_FIRST_NAME, LEGACY_USER_LAST_NAME);
    });

    function addSpecialCharactersPasswordPolicy() {
        passwordPoliciesPage.visit();
        passwordPoliciesPage.addSpecialCharactersPasswordPolicy();
    }
});
