class UpdatePasswordPage {

    elements = {
        passwordInput: () => cy.get('#password-new'),
        confirmPasswordInput: () => cy.get('#password-confirm'),
        form: () => cy.get('#kc-passwd-update-form'),
    }

    chooseNewPassword(newPassword) {
        this.elements.passwordInput().type(newPassword);
        this.elements.confirmPasswordInput().type(newPassword);
        this.elements.form().submit()
    }
}

module.exports = new UpdatePasswordPage();