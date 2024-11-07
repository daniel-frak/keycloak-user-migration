// ***********************************************
// This example commands.js shows you how to
// create various custom commands and overwrite
// existing commands.
//
// For more comprehensive examples of custom
// commands please read more here:
// https://on.cypress.io/custom-commands
// ***********************************************
//
//
// -- This is a parent command --
// Cypress.Commands.add('login', (email, password) => { ... })
//
//
// -- This is a child command --
// Cypress.Commands.add('drag', { prevSubject: 'element'}, (subject, options) => { ... })
//
//
// -- This is a dual command --
// Cypress.Commands.add('dismiss', { prevSubject: 'optional'}, (subject, options) => { ... })
//
//
// -- This will overwrite an existing command --
// Cypress.Commands.overwrite('visit', (originalFn, url, options) => { ... })

import 'cypress-mailhog';
import keycloak from "../e2e/pages/keycloak";
import data from "../e2e/data";
import realmSettingsLoginPage from "../e2e/pages/realmSettingsLoginPage";
import userFederationPage from "../e2e/pages/userFederationPage";
import userMigrationProviderPage from "../e2e/pages/userMigrationProviderPage";
import userDetailsPage from "../e2e/pages/userDetailsPage";
import realmSettingsEmailPage from "../e2e/pages/realmSettingsEmailPage";
import usersPage from "../e2e/pages/usersPage";
import passwordPoliciesPage from "../e2e/pages/passwordPoliciesPage";

Cypress.Commands.add("getByTestId", (testId, ...args) => {
    return cy.get(`[data-testid="${testId}"]`, ...args);
});

Cypress.Commands.add("setupKeycloak", () => {
    keycloak.signInAsAdmin();
    configureLoginSettings();
    configureAdminPersonalInfo();
    configureSmtpSettings();
    configureMigrationPlugin();
    keycloak.signOutViaUIAndClearCache();

    function configureLoginSettings() {
        realmSettingsLoginPage.visit();
        realmSettingsLoginPage.toggleForgotPasswordSwitchTo(true);
    }

    function configureMigrationPlugin() {
        userFederationPage.visit();
        userFederationPage.removePluginIfExists();
        userFederationPage.goToUserMigrationPluginPage();
        userMigrationProviderPage.configurePlugin(data.legacySystem.url);
    }

    /**
     * The admin e-mail must be configured for SMTP connection testing to become available
     */
    function configureAdminPersonalInfo() {
        userDetailsPage.visit(data.admin.username);
        userDetailsPage.writePersonalInfo(data.admin.email, data.admin.username);
    }

    function configureSmtpSettings() {
        realmSettingsEmailPage.visit();
        realmSettingsEmailPage.configureSmtpSettings(data.smtp.host, data.smtp.port, data.smtp.from);
    }
});

Cypress.Commands.add("resetState", () => {
    deleteEmails();
    keycloak.signInAsAdmin();

    return deleteTestUserIfExists()
        .then(() => deletePasswordPoliciesIfExist()
            .then(() => keycloak.signOutViaUIAndClearCache()));

    function deleteEmails() {
        cy.mhDeleteAll();
        cy.mhGetAllMails()
            .should('have.length', 0);
    }

    function deleteTestUserIfExists() {
        removeImportedUsers();
        usersPage.visit();
        return usersPage.deleteUserIfExists(data.legacyUser.username);
    }

    /*
    Users which still have a federation link (e.g. they have been imported but have not successfully logged in yet)
    cannot be removed using normal means. The "Remove imported users" option on the User Federation Plugin page
    removes those users (but will not remove users for whom the federation link has already been severed).
     */
    function removeImportedUsers() {
        userMigrationProviderPage.visit();
        userMigrationProviderPage.removeImportedUsers();
    }

    function deletePasswordPoliciesIfExist() {
        passwordPoliciesPage.visit();
        return passwordPoliciesPage.deleteEveryPasswordPolicy();
    }
});