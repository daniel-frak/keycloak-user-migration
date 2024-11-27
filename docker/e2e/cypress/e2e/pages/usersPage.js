const USER_TABLE_SELECTOR = 'table';
const EMPTY_SEARCH_RESULTS_SELECTOR = '[data-testid="empty-state"]';

class UsersPage {

    elements = {
        searchUserInput: () => cy.get('input[placeholder="Search user"]'),
        userSearchResult: () =>
            cy.get(USER_TABLE_SELECTOR + ', ' + EMPTY_SEARCH_RESULTS_SELECTOR),
        foundUserTable: () => cy.get(USER_TABLE_SELECTOR),
        emptySearchResultsText: () => cy.get(EMPTY_SEARCH_RESULTS_SELECTOR),
        foundUserOptionsToggle: () => cy.get('table button'),
        foundUserDeleteButton: () => cy.get('.pf-v5-c-menu__list-item')
            .contains('Delete')
    }

    visit() {
        cy.intercept('admin/realms/master/ui-ext/brute-force-user*')
            .as("userList")
        cy.visit('/admin/master/console/#/master/users');
        cy.wait('@userList');
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
            .then(response => {
                // Assert that the page was actually updated
                return this.elements.userSearchResult().should('exist')
                    .then(() => response.response?.body[0]?.id);
            })

    }

    deleteUserIfExists(userName) {
        return this.findByName(userName).then(() =>
            cy.get(USER_TABLE_SELECTOR).should('have.length.gte', 0).then(element => {
                if (element.length < 1) {
                    return;
                }
                cy.log(`Found ${element.length} items`);
                this.clickDeleteInUserDropdown();
                this.confirmUserDeletion();
                return this.assertUserWasDeleted(userName);
            }));
    }

    getUserDeleteButton(userName) {
        return this.findByName(userName).then(() =>
            cy.get(USER_TABLE_SELECTOR).should('have.length.gte', 0).then(element => {
                if (element.length < 1) {
                    return;
                }
                cy.log(`Found ${element.length} items`);
                this.elements.foundUserOptionsToggle().click();
                return this.elements.foundUserDeleteButton();
            }));
    }

    clickDeleteInUserDropdown() {
        this.elements.foundUserOptionsToggle().click();
        this.elements.foundUserDeleteButton().click();
    }

    confirmUserDeletion() {
        cy.intercept('DELETE', '/admin/realms/master/users/*')
            .as("deleteUser");
        cy.get('#modal-confirm').click();
        cy.wait("@deleteUser");
    }

    assertUserWasDeleted(userName) {
        return this.findByName(userName)
            .then((() => this.elements.foundUserTable(userName).should('not.exist')))
            .then((() => this.elements.emptySearchResultsText().should('exist')));
    }
}

module.exports = new UsersPage();