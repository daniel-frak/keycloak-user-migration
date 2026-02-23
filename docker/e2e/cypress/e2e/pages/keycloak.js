const loginPage = require("./loginPage");
const data = require("../data");

class Keycloak {

    isAccountPath(pathname) {
        return /^\/realms\/[^/]+\/account(?:\/|$)/.test(pathname);
    }

    isRequiredActionPath(pathname) {
        return /^\/realms\/[^/]+\/login-actions\/required-action(?:\/|$)/.test(pathname);
    }

    isOidcAuthPath(pathname) {
        return /^\/realms\/[^/]+\/protocol\/openid-connect\/auth(?:\/|$)/.test(pathname);
    }

    isKnownPostAuthPath(pathname) {
        return this.isAccountPath(pathname)
            || this.isRequiredActionPath(pathname)
            || this.isOidcAuthPath(pathname);
    }

    isTerminalPostAuthPath(pathname) {
        return this.isAccountPath(pathname) || this.isRequiredActionPath(pathname);
    }

    waitForPostAuthRedirectToSettle() {
        cy.location('pathname', {timeout: 30000}).should((pathname) => {
            expect(
                this.isKnownPostAuthPath(pathname),
                `Unexpected Keycloak post-auth path: ${pathname}`
            ).to.be.true;
        });

        cy.location('pathname', {timeout: 30000}).should((pathname) => {
            expect(
                this.isTerminalPostAuthPath(pathname),
                `Keycloak redirect chain did not settle on account or required-action page. Current path: ${pathname}`
            ).to.be.true;
        });
    }

    assertRequiredActionPageIsVisible() {
        cy.get('#kc-login').should('not.exist');
        cy.get('#username').should('not.exist');
        cy.get('#password').should('not.exist');
        cy.location('pathname').should((pathname) => {
            expect(this.isRequiredActionPath(pathname)).to.be.true;
        });

        cy.get('body').should(($body) => {
            const hasRequiredActionContent =
                $body.find('#kc-passwd-update-form').length > 0
                || $body.find('form[action*="/login-actions/required-action"]').length > 0
                || $body.find('#kc-content').length > 0
                || $body.find('#kc-form-wrapper').length > 0
                || $body.find('h1, h2, h3, [role="heading"]').length > 0;

            expect(hasRequiredActionContent, 'required action page or completion screen is rendered').to.be.true;
        });
    }

    elements = {
        userDropdown: () => cy.get('#user-dropdown'),
        accountDropdown: () => cy.getByTestId('options-toggle'),
        signOutButton: () => cy.get('#sign-out')
    }

    signInAsAdmin() {
        loginPage.visitForAdmin();
        loginPage.logIn(data.admin.username, data.admin.password);
    }

    signInAsLegacyUser() {
        loginPage.visitForUser();
        loginPage.logIn(data.legacyUser.username, data.legacyUser.simplePassword);
    }

    signOutViaUIAndClearCache() {
        cy.get('body').then($body => {
            const dropdownSelectors = [
                '#user-dropdown',
                '[data-testid="options-toggle"]',
                '[data-testid="avatar-toggle"]'
            ];
            const signOutSelectors = [
                '#sign-out',
                '[data-testid="logout"]',
                'a[href*="/logout"]'
            ];

            const dropdownSelector = dropdownSelectors.find(selector => $body.find(selector).length > 0);
            if (dropdownSelector) {
                cy.get(dropdownSelector).first().click({force: true});

                const signOutSelector = signOutSelectors.find(selector => $body.find(selector).length > 0);
                if (signOutSelector) {
                    cy.get(signOutSelector).first().click({force: true});
                } else if ($body.text().includes('Sign out')) {
                    cy.contains('button, a', 'Sign out').first().click({force: true});
                }
            }
        });

        cy.clearAllCookies();
        cy.clearAllLocalStorage();
        cy.clearAllSessionStorage();
    }

    assertIsLoggedInAsLegacyUser() {
        this.assertIsLoggedInAsUser(data.legacyUser.firstName, data.legacyUser.lastName);
    }

    assertIsLoggedInAsUser(userFirstName, userLastName) {
        const fullName = `${userFirstName} ${userLastName}`;

        cy.get('body', {timeout: 30000}).then($body => {
            const knownNameSelectors = [
                '[data-testid="options-toggle"]',
                '[data-testid="avatar-toggle"]',
                '#user-dropdown'
            ];
            const selector = knownNameSelectors.find(item => $body.find(item).length > 0);

            if (selector) {
                cy.get(selector).first().should('contain', fullName);
                return;
            }

            this.waitForPostAuthRedirectToSettle();

            cy.location('pathname').then((pathname) => {
                if (this.isRequiredActionPath(pathname)) {
                    this.assertRequiredActionPageIsVisible();
                    return;
                }

                cy.get('#kc-login').should('not.exist');
                cy.get('body').should('contain.text', userFirstName);
                cy.get('body').should('contain.text', userLastName);
            });
        });
    }
}

module.exports = new Keycloak();
