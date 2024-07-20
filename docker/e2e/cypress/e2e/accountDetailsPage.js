class AccountDetailsPage {

    /**
     * @param {Keycloak} keycloak
     */
    constructor(keycloak) {
        this.keycloak = keycloak;
    }

    elements = {
        emailInput: () => cy.get('#email'),
        firstNameInput: () => cy.get('#firstName'),
        lastNameInput: () => cy.get('#lastName'),
        saveBtn: () => cy.get('button').contains('Save')
    }

    visit() {
        cy.intercept('GET', '/realms/master/account/**')
            .as("accountDetails");
        cy.visit('/realms/master/account');
        cy.wait('@accountDetails');
    }

    configurePersonalInfo(email, firstName, lastName) {
        this.elements.emailInput().clear();
        this.elements.emailInput().type(email);
        this.elements.firstNameInput().clear().type(firstName);
        this.elements.lastNameInput().clear().type(lastName);

        cy.intercept('POST', '/realms/master/account/**')
            .as("saveAccountDetails");
        this.elements.saveBtn().click();
        cy.wait("@saveAccountDetails");
    }
}

module.exports = AccountDetailsPage;