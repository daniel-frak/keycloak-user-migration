const REMOVE_PASSWORD_POLICY_BTN_SELECTOR = '[data-testid^=remove-]';
const NO_POLICY_SELECTOR = '[data-testid="empty-state"]';

class PasswordPoliciesPage {

    elements = {
        header: () => cy.get('h1'),
        removePasswordPolicyBtn: () => cy.get(REMOVE_PASSWORD_POLICY_BTN_SELECTOR),
        saveBtn: () => cy.getByTestId('save'),
        addPolicyBtn: () => cy.getByTestId('add-policy'),
        addSpecialCharactersPolicyBtn: () => cy.get('button[role="option"]')
            .contains('Special Characters')
    }

    visit() {
        cy.visit('/admin/master/console/#/master/authentication/policies');
        cy.get(REMOVE_PASSWORD_POLICY_BTN_SELECTOR + "," + NO_POLICY_SELECTOR)
            .should('be.visible');
    }

    deleteEveryPasswordPolicy() {
        return cy.document().then((doc) => {
            if (doc.querySelectorAll(REMOVE_PASSWORD_POLICY_BTN_SELECTOR).length) {
                cy.log("Deleting password policies...");
                this.elements.removePasswordPolicyBtn().click({multiple: true});
                this.savePolicies();
            } else {
                cy.log("No password policies to remove.")
            }
        });
    }

    addSpecialCharactersPasswordPolicy() {
        this.elements.addPolicyBtn().click()
        this.elements.addSpecialCharactersPolicyBtn().click({force: true});
        this.savePolicies();
    }

    savePolicies() {
        cy.intercept('PUT', 'http://localhost:8024/admin/realms/master').as('savePolicies');
        this.elements.saveBtn().click();
        cy.wait('@savePolicies');
    }
}

module.exports = new PasswordPoliciesPage();