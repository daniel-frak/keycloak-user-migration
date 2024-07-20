class Keycloak {

    elements = {
        userDropdown: () => cy.get('#user-dropdown'),
        notification: () => cy.get('.pf-c-alert__title'),
        accountDropdown: () => cy.get('[data-testid="options"]')
    }

    signOutViaUIAndClearCache() {
        this.elements.userDropdown().click()
        cy.get('#sign-out').get('a').contains('Sign out').click({force: true});

        cy.clearAllCookies();
        cy.clearAllLocalStorage();
        cy.clearAllSessionStorage();
    }

    assertIsLoggedInAsUser(userFirstName, userLastName) {
        this.elements.accountDropdown().should('contain', userFirstName + ' ' + userLastName);
    }
}

module.exports = Keycloak;