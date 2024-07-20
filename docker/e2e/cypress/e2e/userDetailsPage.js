class UserDetailsPage {

    /**
     * @param {UsersPage} usersPage
     */
    constructor(usersPage) {
        this.usersPage = usersPage;
    }

    elements = {
        emailInput: () => cy.get('#email'),
        firstNameInput: () => cy.get('*[data-testid="firstName"]'),
        lastNameInput: () => cy.get('*[data-testid="lastName"]')
    }

    visit(userName) {
        this.usersPage.visit();
        this.usersPage.goToUserDetails(userName)
    }

    writePersonalInfo(email, username) {
        this.elements.emailInput().clear();
        this.elements.emailInput().clear().type(email);
        this.elements.firstNameInput().clear().type(username);
        this.elements.lastNameInput().clear().type(username);
        cy.get("form").submit();
    }
}

module.exports = UserDetailsPage;