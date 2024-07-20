class UserFederationPage {

    /**
     * @param {Keycloak} keycloak
     * @param {String} pluginName
     */
    constructor(keycloak, pluginName) {
        this.keycloak = keycloak;
        this.pluginName = pluginName;
    }

    elements = {
        header: () => cy.get("h1"),
        userMigrationProviderBtn: () =>
            cy.get('*[data-testid="User migration using a REST client-card"], ' +
                'div[class="pf-l-gallery pf-m-gutter"] *[data-testid="keycloak-card-title"] a'),
        pluginDropdown: () => cy.get('[data-testid="' + this.pluginName + '-dropdown"]')
    }

    visit() {
        cy.intercept('/admin/realms/master/components*')
            .as("components")
        cy.visit('/admin/master/console/#/master/user-federation');
        cy.wait('@components');
        cy.wait(2000); // The initial page will always claim there are no components, so we must wait to make sure.
    }

    removePluginIfExists() {
        this.elements.pluginDropdown()
            .should('have.length.gte', 0).then(userElement => {
            if (!userElement.length) {
                return;
            }
            this.elements.pluginDropdown().click();
            cy.get('.pf-c-dropdown__menu-item').contains("Delete").click();

            cy.intercept('DELETE', 'admin/realms/master/components/*')
                .as("deleteComponent");
            cy.get('#modal-confirm').click({force: true});
            cy.wait("@deleteComponent");
            this.keycloak.elements.notification()
                .should('contain', 'The user federation provider has been deleted');
            this.removePluginIfExists();
        });
    }

    goToUserMigrationPluginPage() {
        cy.intercept('/admin/realms/master/components*')
            .as("components")
        this.elements.userMigrationProviderBtn()
            .first()
            .click({force: true});
        cy.wait("@components");
        this.elements.header().should('contain', 'User migration using a REST client');
    }
}

module.exports = UserFederationPage;