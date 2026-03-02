const loginPage = require("./loginPage");
const data = require("../data");

class Keycloak {

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
        this.elements.userDropdown().click()
        this.elements.signOutButton().click({force: true});

        cy.clearAllCookies();
        cy.clearAllLocalStorage();
        cy.clearAllSessionStorage();
    }

    assertIsLoggedInAsLegacyUser() {
        this.assertIsLoggedInAsUser(data.legacyUser.firstName, data.legacyUser.lastName);
    }

    assertIsLoggedInAsUser(userFirstName, userLastName) {
        this.elements.accountDropdown().should('contain', userFirstName + ' ' + userLastName);
    }
}

module.exports = new Keycloak();