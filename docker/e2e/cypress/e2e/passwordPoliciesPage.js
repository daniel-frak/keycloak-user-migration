const REMOVE_PASSWORD_POLICY_BTN_SELECTOR = '[data-testid^=remove-]';
const NO_POLICY_ICON_SELECTOR = '.pf-c-empty-state__icon';

class PasswordPoliciesPage {

    /**
     * @param {Keycloak} keycloak
     */
    constructor(keycloak) {
        this.keycloak = keycloak;
    }

    elements = {
        header: () => cy.get('h1'),
        removePasswordPolicyBtn: () => cy.get(REMOVE_PASSWORD_POLICY_BTN_SELECTOR),
        saveBtn: () => cy.get('button[data-testid="save"]'),
        addPolicyBtn: () => cy.get('.pf-c-select__toggle'),
        addSpecialCharactersPolicyBtn: () => cy.get('button[role="option"]')
            .contains('Special Characters')
    }

    visit() {
        cy.visit('/admin/master/console/#/master/authentication/policies');
        cy.get(REMOVE_PASSWORD_POLICY_BTN_SELECTOR + "," + NO_POLICY_ICON_SELECTOR)
            .should('be.visible');
    }

    deleteEveryPasswordPolicy() {
        return cy.document().then((doc) => {
            if (doc.querySelectorAll(REMOVE_PASSWORD_POLICY_BTN_SELECTOR).length) {
                cy.log("Deleting password policies...");
                this.elements.removePasswordPolicyBtn().click({multiple: true});
                this.elements.saveBtn().contains('Save').click();
                this.keycloak.elements.notification().should('contain',
                    "Password policies successfully updated");
            } else {
                cy.log("No password policies to remove.")
            }
        });
    }

    addSpecialCharactersPasswordPolicy() {
        this.elements.addPolicyBtn().click()
        this.elements.addSpecialCharactersPolicyBtn().click();
        this.elements.saveBtn().click();
        this.keycloak.elements.notification().should('contain', "Password policies successfully updated");
    }
}

module.exports = PasswordPoliciesPage;