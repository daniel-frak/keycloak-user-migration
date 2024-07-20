class LoginPage {

    elements = {
        usernameField: () => cy.get('#username'),
        passwordField: () => cy.get('#password'),
        logInBtn: () => cy.get('#kc-login'),
        forgotPasswordBtn: () => cy.get("a").contains("Forgot Password?")
    }

    visitForAdmin() {
        cy.visit('/admin');
    }

    visitForUser() {
        cy.visit('/realms/master/account');
    }

    logIn(login, password) {
        this.elements.usernameField().type(login);
        this.elements.passwordField().type(password);

        cy.intercept('POST', '/realms/master/login-actions/authenticate*')
            .as("loginSubmit");
        this.elements.logInBtn().click();
        cy.wait("@loginSubmit");
    }
}

module.exports = LoginPage;