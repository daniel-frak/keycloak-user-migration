const quotedPrintable = require("quoted-printable");
const RESET_PASSWORD_EMAIL_SUBJECT = 'Reset password';

class ResetPasswordEmail {

    /**
     * @returns {Cypress.Chainable<string>}
     */
    open() {
        return cy.mhGetMailsBySubject(RESET_PASSWORD_EMAIL_SUBJECT).mhFirst().mhGetBody();
    }

    visitResetPasswordPage() {
        this.getResetPasswordUrl()
            .then(resetPassUrl => cy.visit(resetPassUrl));
    }

    getResetPasswordUrl() {
        return this.open()
            .then(bodyQuotedPrintable => {
                const body = quotedPrintable.decode(bodyQuotedPrintable);
                return this.getUrlFromLink(body, 'Link to reset credentials');
            });
    }

    /**
     * @returns string
     */
    getUrlFromLink(body, linkText) {
        const linkPattern = new RegExp('<a href="([^"]*).+' + linkText + '.*?<\\/a>');
        return linkPattern.exec(body)[1]
            .toString()
            .replace(/(\r\n|\n|\r)/gm, "");
    }
}

module.exports = new ResetPasswordEmail();