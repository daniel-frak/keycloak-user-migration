class ResetPasswordPage {

    elements = {
        passwordInput: () => cy.get('#password-new'),
        confirmPasswordInput: () => cy.get('#password-confirm'),
        submitBtn: () => cy.get('input[type=submit]')
    }

    chooseNewPassword(newPassword) {
        this.elements.passwordInput().type(newPassword);
        this.elements.confirmPasswordInput().type(newPassword);
        this.elements.submitBtn().click();
    }
}

module.exports = new ResetPasswordPage();