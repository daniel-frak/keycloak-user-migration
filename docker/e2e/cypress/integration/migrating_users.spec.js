// migrating_users.spec.js created with Cypress
//
// Start writing your Cypress tests below!
// If you're unfamiliar with how Cypress works,
// check out the link below and learn how to write your first test:
// https://on.cypress.io/writing-first-test

const quotedPrintable = require('quoted-printable');

const LEGACY_SYSTEM_URL = "http://legacy-system-example:8080/user-migration-support";

const SMTP_HOST = "mailhog";
const SMTP_PORT = "1025";
const SMTP_FROM = "admin@example.com";

const ADMIN_USERNAME = "admin";
const ADMIN_PASSWORD = "admin";
const ADMIN_EMAIL = 'admin@example.com';

const LEGACY_USER_USERNAME = "lucy";
const LEGACY_USER_PASSWORD = "password";
const LEGACY_USER_EMAIL = 'lucy@example.com';
const LEGACY_USER_FIRST_NAME = 'Lucy';
const LEGACY_USER_LAST_NAME = 'Brennan';

describe('user migration plugin', () => {

    before(() => {
        signInAsAdmin();
        configureLoginSettings();
        configureMigrationPlugin();
        configureEmails();
        signOutViaUI();
    })

    function signInAsAdmin() {
        cy.visit('/admin');
        submitCredentials(ADMIN_USERNAME, ADMIN_PASSWORD);
    }

    function submitCredentials(user, password) {
        cy.get('#username').type(user);
        cy.get('#password').type(password);
        cy.intercept('POST', 'http://localhost:8024/realms/master/login-actions/authenticate*')
            .as("loginSubmit");
        cy.get('#kc-login').click();
        cy.wait("@loginSubmit");
    }

    function signOutViaUI() {
        cy.get('#pf-dropdown-toggle-id-10').click()
        cy.get('#sign-out').get('a').contains('Sign out').click({force: true});
    }

    function configureLoginSettings() {
        cy.visit('/admin/master/console/#/master/realm-settings/login');

        cy.get('#kc-forgot-pw-switch')
            .uncheck({force: true})
            .check({force: true});
    }

    function configureMigrationPlugin() {
        // TODO delete existing configured plugins
        cy.visit('/admin/master/console/#/master/user-federation/User migration using a REST client/new');
        cy.wait(1000);
        cy.get('#kc-console-display-name').clear().type('REST client provider');
        cy.get('#URI').clear().type(LEGACY_SYSTEM_URL);
        cy.get('button').contains('Save').click()
        cy.get('.pf-c-alert__title').should('contain', "User federation provider successfully");
    }


    function goToPluginSettings($providerDropdown, providerDropdownSelector) {
        if ($providerDropdown.is(':visible')) {
            cy.get(providerDropdownSelector)
                .select('User migration using a REST client');
        } else {
            cy.contains('Edit').click();
        }
    }

    function configureEmails() {
        writeAdminPersonalInfo();
        configureAdminPersonalInfo();
        configureSmtpSettings();
    }

    /**
     * Write Admin user info first, so it becomes visible in account console.
     * If fields are not populated here, the will not be visible in user account (KC Bug??)
     */
    function writeAdminPersonalInfo() {
        cy.visit('/admin/master/console/#/master/users');
        cy.url().should('include', '/admin/master/console/#/master/users');
        cy.get('.pf-c-input-group').get('input').clear().type('*');
        cy.get('.pf-c-input-group').get('.pf-c-button.pf-m-control').click();
        cy.get('table').get('td[data-label="Username"]').get('a').contains(ADMIN_USERNAME).click();
        cy.get('#kc-email').clear();
        cy.wait(500);
        cy.get('#kc-email').clear().type(ADMIN_EMAIL);
        cy.get('#kc-firstname').clear().type(ADMIN_USERNAME);
        cy.get('#kc-lastname').clear().type(ADMIN_USERNAME);
        cy.get('.pf-c-form__actions').get('button[type="submit"]').contains('Save').click({force: true});

    }

    function configureAdminPersonalInfo() {
        cy.intercept('GET', '/realms/master/account/')
            .as("accountDetails");
        cy.visit('/realms/master/account/#/personal-info');
        cy.wait('@accountDetails');

        // Wait a while, otherwise Keycloak overrides the inputs randomly
        cy.wait(5000);

        cy.get('#email-address').clear();
        cy.wait(500);
        cy.get('#email-address').clear().type(ADMIN_EMAIL);
        cy.get('#first-name').clear().type(ADMIN_USERNAME);
        cy.get('#last-name').clear().type(ADMIN_USERNAME);

        cy.get('button').contains('Save').click();

        cy.get('.pf-c-alert__title').should('contain', "Your account has been updated");
    }

    function configureSmtpSettings() {
        cy.visit('/admin/master/console/#/master/realm-settings/email');

        cy.get('#kc-host').clear().type(SMTP_HOST);
        cy.get('#kc-port').clear().type(SMTP_PORT);
        cy.get('#kc-sender-email-address').clear().type(SMTP_FROM);

        cy.get('button').contains('Test connection').click();
        cy.get('.pf-c-alert__title').should('contain', "Success! SMTP connection successful. E-mail was sent!");

        cy.get('button').contains('Save').click();
    }

    beforeEach(() => {
        deleteEmails();
        signInAsAdmin();
        deleteTestUserIfExists().then(() => {
            deletePasswordPoliciesIfExist()
                .then(() => signOutViaUI());
        });
    });

    function deleteEmails() {
        cy.mhDeleteAll();
        cy.mhGetAllMails()
            .should('have.length', 0);
    }

    function deleteTestUserIfExists() {
        cy.visit('/admin/master/console/#/master/users');
        cy.get('.pf-c-input-group').get('input').clear().type('*');
        cy.get('.pf-c-input-group').get('.pf-c-button.pf-m-control').click();

        let userButton = cy.get('table').get('td[data-label="Username"]').get('a').contains(LEGACY_USER_USERNAME);
        return userButton
            .should('have.length.gte', 0).then(userElement => {
                if (!userElement.length) {
                    return;
                }
                userButton.click();
                cy.get('div[data-testid="action-dropdown"]').click();
                cy.get('.pf-c-dropdown__menu-item').contains('Delete').click();
                cy.get('#modal-confirm').click({force: true});
            });
    }

    function deletePasswordPoliciesIfExist() {
        goToPasswordPoliciesPage();
        return deleteEveryPasswordPolicyAndSave();
    }

    function deleteEveryPasswordPolicyAndSave() {
        cy.log("Deleting password policies...");
        return cy.get('.keycloak__policies_authentication__minus-icon')
            .should('have.length.gte', 0).then(btn => {
                if (!btn.length) {
                    return;
                }
                cy.wrap(btn).click({multiple: true});
                cy.get('button').contains('Save').click();
                cy.get('.pf-c-alert__title').should('contain', "Password policies successfully updated");
            });
    }

    function goToPasswordPoliciesPage() {
        cy.wait(1000);
        cy.intercept('GET', '/admin/realms/master/authentication/required-actions').as("masterGet");
        cy.visit('/admin/master/console/#/master/authentication/policies');
        cy.wait('@masterGet');
        cy.get("h1").should('contain', 'Authentication');
    }

    it('should migrate user', () => {
        signInAsLegacyUser();
        updateAccountInformation();
        assertIsLoggedInAsLegacyUser();
    });

    function signInAsLegacyUser() {
        cy.visit('/realms/master/account');
        cy.get('#landingSignInButton').click();
        submitCredentials(LEGACY_USER_USERNAME, LEGACY_USER_PASSWORD);
    }

    function updateAccountInformation() {
        cy.get('#email').should('have.value', LEGACY_USER_EMAIL);
        cy.get('#firstName').should('have.value', LEGACY_USER_FIRST_NAME);
        cy.get('#lastName').should('have.value', LEGACY_USER_LAST_NAME);
        cy.get("input").contains("Submit").click();
    }

    function assertIsLoggedInAsLegacyUser() {
        cy.get('#landingLoggedInUser').should('contain', LEGACY_USER_FIRST_NAME + ' ' + LEGACY_USER_LAST_NAME);
    }

    it('should reset password after inputting wrong credentials', () => {
        attemptLoginWithWrongPassword();
        triggerPasswordReset();
        resetPasswordViaEmail();
    });

    function resetPasswordViaEmail() {
        cy.mhGetMailsBySubject('Reset password').mhFirst().mhGetBody()
            .then(bodyQuotedPrintable => {
                clickPasswordResetLink(bodyQuotedPrintable);
                updateAccountInformation();
                inputNewPassword();
                assertIsLoggedInAsLegacyUser();
            });
    }

    function attemptLoginWithWrongPassword() {
        cy.visit('/realms/master/account');
        cy.get('#landingSignInButton').click();
        submitCredentials(LEGACY_USER_USERNAME, "wrongPassword");
    }

    function triggerPasswordReset() {
        cy.intercept('GET', '/realms/master/login-actions/reset-credentials*')
            .as('resetCredentials');
        cy.get("a").contains("Forgot Password?").click();
        cy.wait('@resetCredentials');
        cy.get('#username').clear().type(LEGACY_USER_EMAIL);
        cy.get('input[type=submit]').click();
        cy.get('body').should('contain.text',
            'You should receive an email shortly with further instructions.');
        cy.mhGetMailsBySubject('Reset password')
            .should('have.length', 1);
    }

    function clickPasswordResetLink(bodyQuotedPrintable) {
        const body = quotedPrintable.decode(bodyQuotedPrintable);
        const resetPassUrl = getUrlFromLink(body, 'Link to reset credentials');

        cy.visit(resetPassUrl);
    }

    function getUrlFromLink(body, linkText) {
        const linkPattern = new RegExp('<a href="([^"]*).+' + linkText + '.*?<\\/a>');
        return linkPattern.exec(body)[1]
            .toString()
            .replace(/(\r\n|\n|\r)/gm, "");
    }

    function inputNewPassword() {
        cy.get('#password-new').type(LEGACY_USER_PASSWORD);
        cy.get('#password-confirm').type(LEGACY_USER_PASSWORD);
        cy.get('input[type=submit]').click();
    }

    it('should reset password before user is migrated', () => {
        cy.visit('/realms/master/account');
        cy.get('#landingSignInButton').click();
        triggerPasswordReset();
        resetPasswordViaEmail()
    });

    it('should migrate user when password breaks policy', () => {
        signInAsAdmin();
        addSpecialCharactersPasswordPolicy();
        signOutViaUI();

        signInAsLegacyUser();
        provideNewPassword();
        updateAccountInformation();
        assertIsLoggedInAsLegacyUser();
    });

    function addSpecialCharactersPasswordPolicy() {
        cy.visit('/admin/master/console/#/master/authentication/policies');
        cy.get('.pf-c-select__toggle').click()
        cy.get('button[role="option"]').contains('Special Characters').click();
        cy.get('button[data-testid="save"]').contains('Save').click();
        cy.get('.pf-c-alert__title').should('contain', "Password policies successfully updated");
    }

    function provideNewPassword() {
        cy.get('#password-new').type("pa$$word");
        cy.get('#password-confirm').type("pa$$word");
        cy.get("input").contains("Submit").click();
    }
});