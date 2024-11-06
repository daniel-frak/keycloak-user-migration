class RealmSettingsEmailPage {

    elements = {
        smtpHostInput: () => cy.getByTestId('smtpServer.host'),
        smtpPortInput: () => cy.getByTestId('smtpServer.port'),
        emailFromInput: () => cy.getByTestId('smtpServer.from'),
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
        this.testSmtpConnection();
        this.saveEmailSettings();
    }

    testSmtpConnection() {
        cy.intercept('POST', '/admin/realms/master/testSMTPConnection').as('testSMTPConnection');
        this.elements.testConnectionBtn()
            .should('not.be.disabled')
            .click();
        cy.wait('@testSMTPConnection')
            .its('response.statusCode').should('eq', 204);
    }

    saveEmailSettings() {
        cy.intercept('PUT', 'http://localhost:8024/admin/realms/master/ui-ext')
            .as('saveEmailSettings');
        this.elements.saveBtn().click();
        cy.wait('@saveEmailSettings');
    }
}

module.exports = new RealmSettingsEmailPage();