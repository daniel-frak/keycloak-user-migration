const data = require("../data");

class UserFederationPage {

    elements = {
        header: () => cy.get("h1"),
        userMigrationProviderBtn: (providerName, pluginName) => {
            return cy.get('body').then($body => {
                const editPluginBtn =
                    $body.find('*[data-testid="keycloak-card-title"] a')
                        .filter((index, el) => el.textContent.trim() === pluginName);
                if (editPluginBtn.length) {
                    // Edit an existing plugin
                    return cy.wrap(editPluginBtn);
                } else {
                    // Or create a new one
                    return cy.getByTestId(providerName + '-card');
                }
            });
        },
        pluginDropdown: () => cy.getByTestId(data.pluginName + '-dropdown'),
        deletePluginButton: () => cy.getByTestId('card-delete'),
        modalConfirmButton: () => cy.get('#modal-confirm')
    }

    visit() {
        cy.intercept('/admin/realms/master/components*&type=org.keycloak.storage.UserStorageProvider*')
            .as("getUserStorageProviders")
        cy.visit('/admin/master/console/#/master/user-federation');
        cy.wait('@getUserStorageProviders');

        /*
        The initial page will always claim there are no configured User Federation Providers,
        and there is no way to check whether it has updated the DOM using the @getUserStorageProviders request.
        Therefore, an artificial wait is introduced to make sure the DOM has updated.
         */
        cy.wait(5000);
    }

    removePluginIfExists() {
        this.elements.pluginDropdown()
            .should('have.length.gte', 0).then(userElement => {
            if (!userElement.length) {
                return;
            }
            this.elements.pluginDropdown().click();
            this.elements.deletePluginButton().click();

            cy.intercept('DELETE', 'admin/realms/master/components/*')
                .as("deleteComponent");
            cy.intercept('GET', 'admin/realms/master/components*')
                .as("refreshComponents");
            this.elements.modalConfirmButton().click({force: true});
            cy.wait("@deleteComponent")
                .its('response.statusCode').should('eq', 204);
            cy.wait("@refreshComponents")
                .its('response.statusCode').should('eq', 200);

            // Even though we're waiting for both the delete and the refresh response, the page will still
            // remain the same for a bit, so we must wait to make sure all plugins were removed:
            cy.wait(500);

            this.removePluginIfExists();
        });
    }

    goToUserMigrationPluginPage() {
        this.elements.userMigrationProviderBtn(data.providerName, data.pluginName)
            .first()
            .click({force: true});
        this.elements.header().should('contain', 'User migration using a REST client');
    }
}

module.exports = new UserFederationPage();