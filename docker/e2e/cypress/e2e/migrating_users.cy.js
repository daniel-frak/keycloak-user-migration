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

const USER_DETAILS_EMAIL_ID = '#email';
const ADMIN_PERSONAL_INFO_EMAIL_ID = '#email';
const ADMIN_PERSONAL_INFO_FIRST_NAME_ID = '#firstName';
const ADMIN_PERSONAL_INFO_LAST_NAME_ID = '#lastName';
const ACCOUNT_INFORMATION_EMAIL_ID = '#email';
const ACCOUNT_INFORMATION_FIRST_NAME_ID = '#firstName';
const ACCOUNT_INFORMATION_LAST_NAME_ID = '#lastName';
const ACCOUNT_DROPDOWN_SELECTOR = '[data-testid="options"]';
const RESET_PASSWORD_EMAIL_ID = '#username';
const RESET_PASSWORD_NEW_PASSWORD_ID = '#password-new';
const RESET_PASSWORD_CONFIRM_NEW_PASSWORD_ID = '#password-confirm';
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
        cy.get('#user-dropdown').click()
        cy.get('#sign-out').get('a').contains('Sign out').click({force: true});
    }

    function configureLoginSettings() {
        cy.visit('/admin/master/console/#/master/realm-settings/login');

        cy.get('#kc-forgot-pw-switch').then(($checkbox) => {
            if (!$checkbox.prop('checked')) {
                cy.wrap($checkbox).check({ force: true });
                cy.get('.pf-c-alert__title').should('contain', "Forgot password changed successfully");
            }
        });
    }

    function configureMigrationPlugin() {
        visitMigrationConfigPage();
        cy.get('#kc-ui-display-name')
            .invoke('val', '') // clear() doesn't seem to work here for some reason
            .type('RESTclientprovider');
        cy.get('#URI').clear().type(LEGACY_SYSTEM_URL);
        cy.get('button').contains('Save').click()
        cy.get('.pf-c-alert__title').should('contain', "User federation provider successfully");
    }

    /**
     * Navigate to plugin config page.
     * Edit existing plugin config or create new migration config.
     */
    function visitMigrationConfigPage() {
        cy.intercept('GET', '/admin/realms/master')
            .as("realm");
        cy.visit('/admin/master/console/#/master/user-federation');
        cy.get("h1").should('contain', 'User federation');
        cy.wait("@realm");
        // Either add provider, or edit existing:
        cy.get('*[data-testid="User migration using a REST client-card"], ' +
            'div[class="pf-l-gallery pf-m-gutter"] *[data-testid="keycloak-card-title"] a')
            .first()
            .click({force: true});
        cy.get("h1").should('contain', 'User migration using a REST client');
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
        cy.get('*[placeholder="Search user"').clear().type('*');
        cy.get('.pf-c-input-group').get('.pf-c-button.pf-m-control').click();
        cy.get('table').get('td[data-label="Username"]').get('a').contains(ADMIN_USERNAME).click();
        cy.get(USER_DETAILS_EMAIL_ID).clear();
        cy.get(USER_DETAILS_EMAIL_ID).clear().type(ADMIN_EMAIL);
        cy.get('*[data-testid="firstName"]').clear().type(ADMIN_USERNAME);
        cy.get('*[data-testid="lastName"]').clear().type(ADMIN_USERNAME);
        cy.get('.pf-c-form__actions').get('button[type="submit"]').contains('Save').click({force: true});

    }

    function configureAdminPersonalInfo() {
        cy.intercept('GET', '/realms/master/account/**')
            .as("accountDetails");
        cy.visit('/realms/master/account');
        cy.wait('@accountDetails');

        // Wait a while, otherwise Keycloak overrides the inputs randomly
        cy.wait(2000);
        cy.get(ADMIN_PERSONAL_INFO_EMAIL_ID).clear();
        // Wait for email to be cleared
        cy.wait(2000);
        cy.get(ADMIN_PERSONAL_INFO_EMAIL_ID).clear().type(ADMIN_EMAIL);
        cy.get(ADMIN_PERSONAL_INFO_FIRST_NAME_ID).clear().type(ADMIN_USERNAME);
        cy.get(ADMIN_PERSONAL_INFO_LAST_NAME_ID).clear().type(ADMIN_USERNAME);

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
        // Will not find policies if not waiting here.
        cy.wait(2000);
        return cy.get('body')
            .then($body => {
                if ($body.find('.keycloak__policies_authentication__form').length) {
                    cy.log("Deleting password policies...");
                    let deleteButtons = cy.get('#pf-tab-section-1-passwordPolicy').get('.keycloak__policies_authentication__minus-icon');
                    return deleteButtons
                        .should('have.length.gte', 0).then(btn => {

                            if (!btn.length) {
                                return;
                            }
                            cy.wrap(btn).click({multiple: true});
                            cy.get('button[data-testid="save"]').contains('Save').click();
                            cy.get('.pf-c-alert__title').should('contain', "Password policies successfully updated");
                        });
                } else {
                    return 'OK';
                }
            });
    }

    function goToPasswordPoliciesPage() {
        // Can't get cypress to navigate to the policies page unless adding a "wait" here.
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

    function clickSignBtnInIfExists() {
        cy.get('body').then((body) => {
            const element = "#landingSignInButton";
            if (body.find(element).length > 0) {
                // Only click if exists
                cy.get(element).click();
            }
        });
    }

    function signInAsLegacyUser() {
        cy.visit('/realms/master/account');
        clickSignBtnInIfExists();
        submitCredentials(LEGACY_USER_USERNAME, LEGACY_USER_PASSWORD);
    }

    function updateAccountInformation() {
        cy.get(ACCOUNT_INFORMATION_EMAIL_ID).should('have.value', LEGACY_USER_EMAIL);
        cy.get(ACCOUNT_INFORMATION_FIRST_NAME_ID).should('have.value', LEGACY_USER_FIRST_NAME);
        cy.get(ACCOUNT_INFORMATION_LAST_NAME_ID).should('have.value', LEGACY_USER_LAST_NAME);
        cy.get("input").contains("Submit").click();
    }

    function assertIsLoggedInAsLegacyUser() {
        cy.get(ACCOUNT_DROPDOWN_SELECTOR)
            .should('contain', LEGACY_USER_FIRST_NAME + ' ' + LEGACY_USER_LAST_NAME);
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
        clickSignBtnInIfExists();
        submitCredentials(LEGACY_USER_USERNAME, "wrongPassword");
    }

    function triggerPasswordReset() {
        cy.intercept('GET', '/realms/master/login-actions/reset-credentials*')
            .as('resetCredentials');
        cy.get("a").contains("Forgot Password?").click();
        cy.wait('@resetCredentials');
        cy.get(RESET_PASSWORD_EMAIL_ID).clear().type(LEGACY_USER_EMAIL);
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
        cy.get(RESET_PASSWORD_NEW_PASSWORD_ID).type(LEGACY_USER_PASSWORD);
        cy.get(RESET_PASSWORD_CONFIRM_NEW_PASSWORD_ID).type(LEGACY_USER_PASSWORD);
        cy.get('input[type=submit]').click();
    }

    it('should reset password before user is migrated', () => {
        cy.visit('/realms/master/account');
        clickSignBtnInIfExists();
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