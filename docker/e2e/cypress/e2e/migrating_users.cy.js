// migrating_users.spec.js created with Cypress
//
// Start writing your Cypress tests below!
// If you're unfamiliar with how Cypress works,
// check out the link below and learn how to write your first test:
// https://on.cypress.io/writing-first-test

const data = require('./data');
const loginPage = require("./pages/loginPage");
const keycloak = require("./pages/keycloak");
const passwordPoliciesPage = require("./pages/passwordPoliciesPage");
const resetPasswordPage = require("./pages/resetPasswordPage");
const forgotPasswordPage = require("./pages/forgotPasswordPage");
const resetPasswordEmail = require("./pages/resetPasswordEmail");
const updatePasswordPage = require("./pages/updatePasswordPage");
const updateAccountInformationPage = require("./pages/updateAccountInformationPage");

describe('user migration plugin', () => {

    before(() => {
        cy.setupKeycloak();
    });

    beforeEach(() => {
        cy.resetState();
    });

    it('should migrate user', () => {
        keycloak.signInAsLegacyUser();
        updateAccountInformationPage.confirmAccountInformation();

        keycloak.assertIsLoggedInAsLegacyUser();
    });

    it('should reset password after inputting wrong credentials', () => {
        attemptLoginWithWrongPassword();
        triggerPasswordReset();
        resetPasswordViaEmail();

        keycloak.assertIsLoggedInAsLegacyUser();
    });

    function attemptLoginWithWrongPassword() {
        loginPage.visitForUser();
        loginPage.logIn(data.legacyUser.username, "wrongPassword");
    }

    function triggerPasswordReset() {
        forgotPasswordPage.visit();
        forgotPasswordPage.triggerPasswordReset(data.legacyUser.email);
    }

    function resetPasswordViaEmail() {
        resetPasswordEmail.visitResetPasswordPage();
        resetPasswordPage.chooseNewPassword(data.legacyUser.simplePassword)
        updateAccountInformationPage.confirmAccountInformation();
    }

    it('should reset password before user is migrated', () => {
        cy.visit('/realms/master/account');
        triggerPasswordReset();
        resetPasswordViaEmail();

        keycloak.assertIsLoggedInAsLegacyUser();
    });

    it('should migrate user when password breaks policy', () => {
        addSpecialCharactersPasswordPolicy();

        keycloak.signInAsLegacyUser();
        updatePasswordPage.chooseNewPassword(data.legacyUser.passwordWhichMeetsPolicy);
        updateAccountInformationPage.confirmAccountInformation();

        keycloak.assertIsLoggedInAsUser(data.legacyUser.firstName, data.legacyUser.lastName);
    });

    function addSpecialCharactersPasswordPolicy() {
        keycloak.signInAsAdmin();
        passwordPoliciesPage.visit();
        passwordPoliciesPage.addSpecialCharactersPasswordPolicy();
        keycloak.signOutViaUIAndClearCache();
    }
});
