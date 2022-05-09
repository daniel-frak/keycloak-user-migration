# Contributing guidelines

Thank you for considering contributing to this project! There's only a few simple rules to follow.

## Everything happens on GitHub
This project uses GitHub to host code, to track issues and feature requests, as well as accept pull requests.

## All discussion is welcome

This project can only meaningfully grow through community feedback.
Consider [opening an issue](https://GitHub.com/daniel-frak/keycloak-user-migration/issues/new/choose) if you find a bug,
want to request (or propose) a feature or simply want to discuss something related to it.

## Write bug reports with detail, background, and sample code

**Great Bug Reports** tend to have:

- A quick summary and/or background
- Steps to reproduce
    - Be specific!
    - Give sample code if you can.
- What you expected would happen
- What actually happens
- Notes (possibly including why you think this might be happening, or things you tried that didn't work)

## We use [GitHub Flow](https://guides.GitHub.com/introduction/flow/index.html), so all changes happen through Pull Requests

Pull Requests are the best way to propose changes to the codebase.
To create a Pull Request:

1. Fork the repo and create your branch from `master`.
2. Make sure all code you have added has corresponding tests.
3. If you have changed APIs, update the documentation.
4. Ensure the test suite passes.
5. Issue that pull request!

Once you have opened the Pull Request, your code will be analyzed by SonarQube.
You should fix all bugs and code smells found by the analysis, as well as write the necessary tests if code coverage
is found to be below the quality gate. If you find that some reported issues are false positives, let the
reviewer know in the comments of the PR.

In the meantime, one of the maintainers will perform a review of your code and give you tips on what (if anything)
should be improved.

When the code passes both the analysis and the code review, it will be merged into the `master` branch.
Thank you for your contribution!

## Coding style

Try to follow the coding style prevalent throughout the codebase - consistency is best!
If you feel like doing things another way would be better for the project, feel free to open an issue to discuss your
concerns.

## Tests

The quality gate for this project's test coverage is set at 100%. That is not a required value, it's an expected one.

A plugin should be trustworthy and the best way to generate trust is by having a complete suite of tests
proving that everything works correctly. This suite of tests also provides a way to document the code's behavior,
further increasing its value.

Finally, using Test-Driven Development is greatly encouraged. It is a practice which not only results in better code,
but also produces 100% test coverage "for free".

It must be said, of course, that a high coverage value does not indicate the quality of the tests.
The tests themselves must also be well-written and thought-out.
Coverage is, then, only a tool to find the obvious issue - code not covered by tests is guaranteed not to have good
tests.

Test what you can through unit tests. What can't be tested using unit tests, test via supplementary integration tests
or end-to-end tests (found in `docker/e2e/cypress/integration`).
Make sure your test cases are reasonable, readable and maintainable. Don't forget about edge cases!

## Quick guide to upgrading Keycloak compatibility

When updating the code to work with newer versions of Keycloak, remember to update the following:
* `keycloak.version` in [pom.xml](pom.xml)
* `KEYCLOAK_IMAGE` in [docker/.env](docker/.env)
* `Compatibility` table in [README.md](README.md)

To check if the plugin works correctly after the upgrade:
1) Run `mvn clean package` in the project's root directory to run unit tests and build the plugin
2) Run `docker-compose up -d` in `./docker` to create the dependencies necessary for end-to-end testing
3) Run `npx cypress run` in `./docker/e2e` to run end-to-end tests

## Any contributions you make will be under the MIT License
In short, when you submit code changes, your submissions are understood to be under the same
[MIT License](https://choosealicense.com/licenses/mit/) that covers the project.
Feel free to contact the maintainers if that's a concern.