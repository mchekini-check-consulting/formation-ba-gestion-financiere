# Commandes appelées par la CI (formation-ba-gitops/ci-template) et utilisables en local.
.PHONY: lint test-unit test-integration test-e2e e2e-tests

lint:
	npx --yes oxlint src

test-unit:
	npx ng test --watch=false --coverage --coverage-reporters=lcov   # -> coverage/app/lcov.info

test-integration:
	@echo "pas de tests d'intégration"

# End-to-end (navigateur réel) sur l'application déjà lancée en local (skill lancer-en-local).
test-e2e:
	npx playwright test   # -> playwright-report/

# End-to-end « tout compris », appelé par la CI (job e2e-test de pr.yaml) : démarre base, Mailpit
# et backend, lance les tests, arrête tout (e2e/lancer.sh). Docker et Java 21 requis.
e2e-tests:
	bash e2e/lancer.sh
