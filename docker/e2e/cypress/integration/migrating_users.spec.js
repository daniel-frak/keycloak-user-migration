// migrating_users.spec.js created with Cypress
//
// Start writing your Cypress tests below!
// If you're unfamiliar with how Cypress works,
// check out the link below and learn how to write your first test:
// https://on.cypress.io/writing-first-test

const REST_CLIENT_URI = "http://legacy-system-example:8080/user-migration-support";

describe('user migration plugin', () => {
    beforeEach(() => {
        signOutViaUrl();
        deleteTestUserIfExists();
    });

    function signOutViaUrl() {
        cy.visit('http://localhost:8024/auth/realms/master/protocol/openid-connect/logout' +
            '?redirect_uri=http%3A%2F%2Flocalhost%3A8024%2Fauth%2Frealms%2Fmaster%2Faccount%2F');
    }

    function deleteTestUserIfExists() {
        signInAsAdmin();
        cy.visit('/admin/master/console/#/realms/master/users');
        cy.intercept('GET', '/auth/admin/realms/master/users*').as("userGet");
        cy.get('#viewAllUsers').click();
        cy.wait('@userGet');

        cy.get('body').then($body => {
            if ($body.find('td:contains("lucy@localhost.com")').length > 0) {
                cy.contains('lucy@localhost.com').parent().contains('Delete').click();
                cy.get('.modal-dialog button').contains('Delete').click();
                cy.get('.alert').should('contain', "Success");
            }
            signOutViaUI();
        });
    }

    function signInAsAdmin() {
        cy.visit('/admin');
        submitCredentials("admin", "admin");
    }

    function submitCredentials(user, password) {
        cy.get('#username').type(user);
        cy.get('#password').type(password);
        cy.get('#kc-login').click();
    }

    function signOutViaUI() {
        cy.get('a').contains('Sign Out').click({force: true});
    }

    it('should migrate users', () => {
        signInAsAdmin();
        visitUserFederationPage();
        configureMigrationPlugin();
        signOutViaUI();
        signInAsLegacyUser();
        updateAccountInformation();
        cy.get('#landingLoggedInUser').should('contain', 'Lucy Brennan');
    });

    function visitUserFederationPage() {
        cy.intercept('GET',
            '/auth/admin/realms/master/components?parent=master&type=org.keycloak.storage.UserStorageProvider')
            .as('storageProviders');
        cy.visit('/admin/master/console/#/realms/master/user-federation/');
        cy.wait('@storageProviders');
    }

    function configureMigrationPlugin() {
        let providerDropdownSelector = '.blank-slate-pf-main-action select[ng-model="selectedProvider"]';
        cy.get(providerDropdownSelector)
            .then($providerDropdown => {
                goToPluginSettings($providerDropdown, providerDropdownSelector);
                cy.get('.form-group.ng-scope').contains('Rest client URI (required)').parent().within(() => {
                    cy.get('input').clear().type(REST_CLIENT_URI);
                });
                cy.get('button').filter(':visible').contains('Save').click();
            });
    }

    function goToPluginSettings($providerDropdown, providerDropdownSelector) {
        if ($providerDropdown.is(':visible')) {
            cy.get(providerDropdownSelector)
                .select('User migration using a REST client');
        } else {
            cy.contains('Edit').click();
        }
    }

    function signInAsLegacyUser() {
        cy.visit('/realms/master/account');
        cy.get('button').contains('Sign In').click();
        submitCredentials("lucy", "password");
    }

    function updateAccountInformation() {
        cy.get('#email').should('have.value', 'lucy@localhost.com');
        cy.get('#firstName').should('have.value', 'Lucy');
        cy.get('#lastName').should('have.value', 'Brennan');
        cy.get("input").contains("Submit").click();
    }
});