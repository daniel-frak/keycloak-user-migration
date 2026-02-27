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
        cy.get('body').then($body => {
            const actionSelectors = [
                '[data-testid="action-dropdown"]',
                '[data-testid="actions-dropdown"]',
                'button[aria-label="Actions"]'
            ];

            const actionSelector = actionSelectors.find(selector => $body.find(selector).length > 0);
            if (!actionSelector && !$body.text().includes('Remove imported')) {
                return;
            }

            if (actionSelector) {
                cy.get(actionSelector).first().click({force: true});
            }

            const hasButtonWithText = $body.find('button').toArray()
                .some(el => el.textContent?.includes('Remove imported'));

            if (hasButtonWithText) {
                cy.contains('button', 'Remove imported').first().click({force: true});
            } else {
                cy.contains('[role="menuitem"], button, a', 'Remove imported').first().click({force: true});
            }

            cy.intercept('POST', '/admin/realms/master/user-storage/*/remove-imported-users')
                .as('removeImportedUsers');

            cy.get('body').then($confirmBody => {
                const confirmSelectors = ['[data-testid="confirm"]', '#modal-confirm'];
                const confirmSelector = confirmSelectors.find(selector => $confirmBody.find(selector).length > 0);
                if (confirmSelector) {
                    cy.get(confirmSelector).first().click({force: true});
                } else {
                    cy.contains('button', 'Confirm').first().click({force: true});
                }
            });

            cy.wait('@removeImportedUsers');
        });
    }
}

module.exports = new UserMigrationProviderPage();
