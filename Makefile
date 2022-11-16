#------------------------ INIT
ifeq (,$(wildcard ./scripts/Makefile.help))
$(shell git submodule update --init --recursive)
$(error Git submodule for Makefile has been initialized. Please run make again)
endif

USER=istvano

-include ./scripts/Makefile.help
-include ./scripts/Makefile.mk

# import env file
# You can change the default config with `make env="youfile.env" build`
env ?= .env
include $(env)
export $(shell sed 's/=.*//' $(env))

# === BEGIN USER OPTIONS ===
MFILECWD = $(shell pwd)

$(eval $(call defw,REGISTRY_HOST,$(REGISTRY_HOST)))
$(eval $(call defw,USERNAME,$(USER)))
$(eval $(call defw,NAME,$(NAME)))
$(eval $(call defw,VERSION,$(shell . $(RELEASE_SUPPORT) ; getVersion)))
$(eval $(call defw,TAG,$(shell . $(RELEASE_SUPPORT); getTag)))
$(eval $(call defw,DOCKER,docker))
$(eval $(call defw,COMPOSE,docker-compose))

PLATFORM ?= linux/amd64

ifeq ($(UNAME_S),Darwin)
	OPEN=open
else
	OPEN=xdg-open
endif

UID := $(shell id -u)
GID := $(shell id -g)

.PHONY: dev/migrate
dev/migrate: ##@dev Run migration container to create realm
	@echo "Creating keycloak realm"
	(cd docker && $(COMPOSE) run realm-migration)
	@echo "Completed..."

.PHONY: dev/build
dev/build: ##@dev Build keycloak image to run extension
	@echo "Building docker image"
	(cd docker && $(COMPOSE) build)
	@echo "Completed..."

.PHONY: dev/run
dev/run: ##@dev Run extension
	(cd docker && $(COMPOSE) up)

.PHONY: dev/admin
dev/admin: ##@dev open keycloak admin console
	$(OPEN) http://localhost:8024:/admin

.PHONY: dev/account
dev/account: ##@dev open keycloak user account to test user login
	$(OPEN) http://localhost:8024/realms/test/account/


