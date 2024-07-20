class RealmSettingsEmailPage {

    /**
     * @param {Keycloak} keycloak
     */
    constructor(keycloak) {
        this.keycloak = keycloak;
    }

    elements = {
        smtpHostInput: () => cy.get('#kc-host'),
        smtpPortInput: () => cy.get('#kc-port'),
        emailFromInput: () => cy.get('#kc-sender-email-address'),
        testConnectionBtn: () => cy.get('button').contains('Test connection'),
        saveBtn: () => cy.get('button').contains('Save')
    }

    visit = () => {
        cy.visit('/admin/master/console/#/master/realm-settings/email');
    }

    configureSmtpSettings(smtpHost, smtpPort, smtpFrom) {
        this.elements.smtpHostInput().clear().type(smtpHost);
        this.elements.smtpPortInput().clear().type(smtpPort);
        this.elements.emailFromInput().clear().type(smtpFrom);

        this.elements.testConnectionBtn().click();
        this.keycloak.elements.notification()
            .should('contain', "Success! SMTP connection successful. E-mail was sent!");

        this.elements.saveBtn().click();
    }
}

module.exports = RealmSettingsEmailPage;