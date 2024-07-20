class RealmSettingsLoginPage {
    elements = {
        forgotPasswordSwitch: () => cy.get('#kc-forgot-pw-switch')
    }

    visit = () => {
        cy.visit('/admin/master/console/#/master/realm-settings/login');
    }

    toggleForgotPasswordSwitchTo(state) {
        this.elements.forgotPasswordSwitch().then(($checkbox) => {
            if (!$checkbox.prop('checked')) {
                cy.intercept('PUT', 'http://localhost:8024/admin/realms/master')
                    .as("saveForgotPassword");
                cy.wrap($checkbox).check({ force: state });
                cy.wait("@saveForgotPassword");
            }
        });
    }
}

module.exports = RealmSettingsLoginPage;