class UpdateAccountInformationPage {

    elements = {
        submitBtn: () => cy.get('input[type=submit]')
    }

    confirmAccountInformation() {
        this.elements.submitBtn().click();
    }
}

module.exports = new UpdateAccountInformationPage();