const USER_ROW_SELECTOR = 'table td[data-label="Username"] a';

class UsersPage {

    /**
     * @param {Keycloak} keycloak
     */
    constructor(keycloak) {
        this.keycloak = keycloak;
    }

    elements = {
        searchUserInput: () => cy.get('input[placeholder="Search user"'),
        foundUserBtn: () => cy.get(USER_ROW_SELECTOR),
    }

    visit() {
        cy.intercept('/admin/realms/master/components*')
            .as("components")
        cy.visit('/admin/master/console/#/master/users');
        cy.wait('@components');
    }

    goToUserDetails(userName) {
        this.findByName(userName)
            .then(id => cy.visit("admin/master/console/#/master/users/" + id + "/settings"));
    }

    findByName(userName) {
        this.elements.searchUserInput().clear({force: true});
        this.elements.searchUserInput().type(userName);
        cy.intercept("/admin/realms/master/ui-ext/brute-force-user*")
            .as("findUsers");
        this.elements.searchUserInput().type("{enter}");
        return cy.wait("@findUsers")
            .then(response => response.response?.body[0]?.id);

    }

    deleteUserIfExists(userName) {
        this.findByName(userName);

        return cy.get(USER_ROW_SELECTOR).should('have.length.gte', 0).then(element => {
            if (element.length < 1) {
                return;
            }
            cy.log(`Found ${element.length} items`);
            this.clickDeleteInUserDropdown();
            this.confirmUserDeletion();
            return this.assertUserWasDeleted(userName);
        });
    }

    clickDeleteInUserDropdown() {
        cy.get('table .pf-c-dropdown__toggle').click();
        cy.get('.pf-c-dropdown__menu-item').contains('Delete').click();
    }

    confirmUserDeletion() {
        cy.intercept('DELETE', '/admin/realms/master/users/*')
            .as("deleteUser");
        cy.get('#modal-confirm').click();
        cy.wait("@deleteUser");
        this.keycloak.elements.notification().should('contain', 'The user has been deleted');
    }

    assertUserWasDeleted(userName) {
        this.findByName(userName);
        return this.elements.foundUserBtn().should('not.exist');
    }
}

module.exports = UsersPage;