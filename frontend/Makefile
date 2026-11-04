# Commandes appelées par la CI (formation-ba-gitops/ci-template) et utilisables en local.
.PHONY: lint test-unit test-integration test-e2e

lint:
	npx --yes oxlint src

test-unit:
	npx ng test --watch=false --coverage --coverage-reporters=lcov   # -> coverage/app/lcov.info

test-integration:
	@echo "pas de tests d'intégration"

# End-to-end (navigateur réel) : base + backend lancés en local d'abord (skill lancer-en-local).
test-e2e:
	npx playwright test   # -> playwright-report/
