const userFederationPage = require("../pages/userFederationPage");
const data = require('../data');

class UserMigrationProviderPage {

    elements = {
        header: () => cy.get("h1"),
        uiDisplayName: () => cy.getByTestId('name'),
        restClientUri: () => cy.getByTestId('URI'),
        actionDropdown: () => cy.getByTestId('action-dropdown'),
        actionDropdownRemoveImportedBtn: () => cy.get('button').contains('Remove imported'),
        modalConfirmButton: () => cy.getByTestId('confirm'),
        saveBtn: () => cy.get('button').contains('Save')
    }

    visit() {
        userFederationPage.visit();
        userFederationPage.goToUserMigrationPluginPage(data.providerName, data.pluginName);
    }

    configurePlugin(legacySystemUrl) {
        this.elements.uiDisplayName()
            .invoke('val', '') // clear() doesn't seem to work here for some reason
            .type(data.pluginName);
        this.elements.restClientUri().clear().type(legacySystemUrl);

        // POST && 201 response if new plugin, PUT && 204 response if existing plugin:
        cy.intercept('POST', '/admin/realms/master/components*').as('savePlugin');
        cy.intercept('PUT', '/admin/realms/master/components/*').as('savePlugin');
        this.elements.saveBtn().click()
        cy.wait('@savePlugin').its('response.statusCode').should('be.oneOf', [201, 204]);
    }

    removeImportedUsers() {
        this.elements.actionDropdown().click();
        this.elements.actionDropdownRemoveImportedBtn().click();
        cy.intercept('POST', '/admin/realms/master/user-storage/*/remove-imported-users')
            .as('removeImportedUsers')
        this.elements.modalConfirmButton().click();
        cy.wait('@removeImportedUsers');
    }
}

module.exports = new UserMigrationProviderPage();