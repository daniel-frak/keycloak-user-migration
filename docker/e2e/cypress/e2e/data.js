module.exports = {
    legacySystem: {
        url: "http://legacy-system-example:8080/user-migration-support",
        testSupportUrl: "http://localhost:8080/test-support"
    },
    providerName: "User migration using a REST client",
    pluginName: "Custom REST Client Provider",
    admin: {
        username: "admin",
        password: "admin",
        email: "admin@example.com"
    },
    smtp: {
        host: "mailhog",
        port: "1025",
        from: "admin@example.com"
    },
    legacyUser: {
        username: "lucy",
        simplePassword: "password",
        passwordWhichMeetsPolicy: "pa$$word",
        email: "lucy@example.com",
        firstName: "Lucy",
        lastName: "Brennan"
    },
    totpUser: {
        username: "mfa",
        simplePassword: "password",
        firstName: "Mfa",
        lastName: "Tester",
        totp: {
            secret: "ONSWG4TFOQ======",
            name: "E2E authenticator",
            digits: 6,
            period: 30,
            algorithm: "HmacSHA1",
            encoding: "BASE32"
        }
    }
}