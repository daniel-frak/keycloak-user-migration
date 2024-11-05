module.exports = {
    legacySystem: {
        url: "http://legacy-system-example:8080/user-migration-support"
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
    }
}