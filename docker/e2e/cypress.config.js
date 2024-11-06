const {defineConfig} = require('cypress')

module.exports = defineConfig({
    defaultCommandTimeout: 20000,
    requestTimeout: 20000,
    responseTimeout: 20000,
    retries: {
        runMode: 2,
        openMode: 0,
    },
    mailHogUrl: 'http://localhost:8025',
    e2e: {
        // We've imported your old cypress plugins here.
        // You may want to clean this up later by importing these.
        setupNodeEvents(on, config) {
            return require('./cypress/plugins/index.js')(on, config)
        },
        baseUrl: 'http://localhost:8024'
    }
})
