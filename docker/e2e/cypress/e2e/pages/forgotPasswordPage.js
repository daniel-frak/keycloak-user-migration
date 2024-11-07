class ForgotPasswordPage {

    elements = {
        userNameInput: () => cy.get('#username'),
        form: () => cy.get('#kc-reset-password-form')
    }

    visit() {
        cy.visit('/realms/master/login-actions/reset-credentials');
    }

    triggerPasswordReset(userEmail) {
        this.elements.userNameInput().clear().type(userEmail);
        this.elements.form().submit();
        cy.get('body').should('contain.text',
            'You should receive an email shortly with further instructions.');
        cy.mhGetMailsBySubject('Reset password')
            .should('have.length', 1);
    }
}

module.exports = new ForgotPasswordPage();