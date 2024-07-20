class UserMigrationProviderPage {

    /**
     * @param {Keycloak} keycloak
     * @param {UserFederationPage} userFederationPage
     * @param {String} pluginName
     */
    constructor(keycloak, userFederationPage, pluginName) {
        this.keycloak = keycloak;
        this.userFederationPage = userFederationPage;
        this.pluginName = pluginName;
    }

    elements = {
        header: () => cy.get("h1"),
        uiDisplayName: () => cy.get('#kc-ui-display-name'),
        restClientUri: () => cy.get('#URI'),
        saveBtn: () => cy.get('button').contains('Save')
    }

    visit() {
        this.userFederationPage.visit();
        this.userFederationPage.goToUserMigrationPluginPage();
    }

    addPlugin(legacySystemUrl) {
        this.elements.uiDisplayName()
            .invoke('val', '') // clear() doesn't seem to work here for some reason
            .type(this.pluginName);
        this.elements.restClientUri().clear().type(legacySystemUrl);
        this.elements.saveBtn().click()
        this.keycloak.elements.notification().should('contain', "User federation provider successfully");
    }
}

module.exports = UserMigrationProviderPage;