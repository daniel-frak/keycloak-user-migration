const data = require('./data');
const keycloak = require("./pages/keycloak");
const loginPage = require("./pages/loginPage");

describe('TOTP refresh on password login', () => {

    before(() => {
        cy.setupKeycloak();
    });

    beforeEach(() => {
        cy.resetState();
    });

    after(() => {
        clearBrowserSession();
        configureProviderTotpRefresh(false, true);
    });

    it('should add and remove a legacy TOTP for an already migrated user', () => {
        cy.request('PUT',
            `${data.legacySystem.testSupportUrl}/users/${data.totpUser.username}/totps`, []);

        configureProviderTotpRefresh(false, false);
        clearBrowserSession();

        keycloak.signInAsLegacyUser(data.totpUser);
        keycloak.assertIsLoggedInAsUser(data.totpUser.firstName, data.totpUser.lastName);
        clearBrowserSession();

        cy.request('PUT',
            `${data.legacySystem.testSupportUrl}/users/${data.totpUser.username}/totps`,
            [data.totpUser.totp]);

        configureProviderTotpRefresh(true, false);
        clearBrowserSession();

        keycloak.signInAsLegacyUser(data.totpUser);
        loginPage.elements.totpField().should('be.visible');

        clearBrowserSession();
        cy.request('PUT',
            `${data.legacySystem.testSupportUrl}/users/${data.totpUser.username}/totps`, []);

        keycloak.signInAsLegacyUser(data.totpUser);
        loginPage.elements.totpField().should('not.exist');
        keycloak.assertIsLoggedInAsUser(data.totpUser.firstName, data.totpUser.lastName);
    });

    function clearBrowserSession() {
        cy.clearAllCookies();
        cy.clearAllLocalStorage();
        cy.clearAllSessionStorage();
    }

    function configureProviderTotpRefresh(enabled, severFederationLink) {
        return cy.request({
            method: 'POST',
            url: '/realms/master/protocol/openid-connect/token',
            form: true,
            body: {
                grant_type: 'password',
                client_id: 'admin-cli',
                username: data.admin.username,
                password: data.admin.password
            }
        }).then(({body}) => {
            const headers = {Authorization: `Bearer ${body.access_token}`};
            return cy.request({
                method: 'GET',
                url: '/admin/realms/master/components',
                qs: {type: 'org.keycloak.storage.UserStorageProvider'},
                headers
            }).then(({body: components}) => {
                const component = components.find(item => item.name === data.pluginName);
                expect(component, 'user migration provider').to.exist;
                return cy.request({
                    method: 'PUT',
                    url: `/admin/realms/master/components/${component.id}`,
                    headers,
                    body: {
                        ...component,
                        config: {
                            ...component.config,
                            UPDATE_USER_TOTPS_ON_LOGIN: [String(enabled)],
                            SEVER_FEDERATION_LINK: [String(severFederationLink)]
                        }
                    }
                }).its('status').should('eq', 204);
            });
        });
    }
});
