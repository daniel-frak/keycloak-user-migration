const USER_TABLE_SELECTOR = 'table';
const EMPTY_SEARCH_RESULTS_SELECTOR = '[data-testid="empty-state"]';

class UsersPage {

    elements = {
        header: () => cy.get('h1'),
        searchUserInput: () => cy.get('input[placeholder="Search user"]'),
        userSearchResult: () =>
            cy.get(USER_TABLE_SELECTOR + ', ' + EMPTY_SEARCH_RESULTS_SELECTOR),
        foundUserTable: () => cy.get(USER_TABLE_SELECTOR),
        firstUserSettingsLink: () => cy.get('table a[href*="/users/"][href*="/settings"]').first(),
        emptySearchResultsText: () => cy.get(EMPTY_SEARCH_RESULTS_SELECTOR),
        foundUserOptionsToggle: () => cy.get('table button'),
        foundUserDeleteButton: () => cy.get('.pf-v5-c-menu__list-item')
            .contains('Delete')
    }

    visit() {
        cy.visit('/admin/master/console/#/master/users');
        cy.location('hash', {timeout: 30000}).should('include', '/users');
        this.elements.header().should('be.visible');
        this.elements.searchUserInput().should('be.visible');
    }

    goToUserDetails(userName) {
        this.findByName(userName)
            .then(id => cy.visit("admin/master/console/#/master/users/" + id + "/settings"));
    }

    findByName(userName) {
        this.elements.searchUserInput().clear({force: true});
        this.elements.searchUserInput().type(userName);
        this.elements.searchUserInput().type("{enter}");
        return this.elements.userSearchResult().should('exist')
            .then(() => cy.get('body').then($body => {
                const emptyStateExists = $body.find(EMPTY_SEARCH_RESULTS_SELECTOR).length > 0;
                if (emptyStateExists) {
                    return undefined;
                }

                return this.elements.firstUserSettingsLink()
                    .invoke('attr', 'href')
                    .then(href => {
                        const match = href?.match(/\/users\/([^/]+)\/settings/);
                        return match?.[1];
                    });
            }));

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
