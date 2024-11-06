const usersPage = require("../pages/usersPage");

class UserDetailsPage {

    elements = {
        emailInput: () => cy.get('#email'),
        firstNameInput: () => cy.getByTestId('firstName'),
        lastNameInput: () => cy.getByTestId('lastName'),
        form: () => cy.get("form")
    }

    visit(userName) {
        usersPage.visit();
        usersPage.goToUserDetails(userName)
    }

    writePersonalInfo(email, username) {
        this.elements.emailInput().clear();
        this.elements.emailInput().clear().type(email);
        this.elements.firstNameInput().clear().type(username);
        this.elements.lastNameInput().clear().type(username);
        this.submitForm();
    }

    submitForm() {
        cy.intercept('PUT', 'http://localhost:8024/admin/realms/master/users/*').as("saveUser");
        this.elements.form().submit();
        cy.wait('@saveUser');
    }
}

module.exports = new UserDetailsPage();