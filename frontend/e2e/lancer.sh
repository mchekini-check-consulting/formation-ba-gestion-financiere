#!/usr/bin/env bash
# Tests de bout en bout « tout compris » (make e2e-tests) : en CI (job e2e-test de pr.yaml) comme
# sur un poste. Démarre une base et un Mailpit jetables (Docker, projet compose « e2e »), le
# backend (Java 21), puis Playwright lance l'écran (npm start) et les tests ; tout est arrêté à la
# fin, même en cas d'échec. Sur un poste : arrêter d'abord sa propre base locale (ports 5432,
# 1025, 8025 et 8080 utilisés ici) ; pour garder l'application déjà lancée, utiliser make test-e2e.
set -euo pipefail

cd "$(dirname "$0")/.."                      # dossier frontend
BACKEND="$(cd ../backend && pwd)"
COMPOSE=(docker compose -f "$BACKEND/compose.yaml" -p e2e)
JOURNAL="$PWD/backend-e2e.log"

# Runner GitHub : Java 21 installé mais pas celui par défaut.
if [ -n "${JAVA_HOME_21_X64:-}" ]; then
  export JAVA_HOME="$JAVA_HOME_21_X64"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

arreter() {
  if [ -n "${PID_BACKEND:-}" ]; then kill "$PID_BACKEND" 2>/dev/null || true; fi
  "${COMPOSE[@]}" down -v >/dev/null 2>&1 || true
}
trap arreter EXIT

# Un backend déjà lancé sur le poste ferait passer les tests contre une autre version.
if curl -s -o /dev/null --max-time 2 http://localhost:8080/; then
  echo "Le port 8080 est déjà utilisé (backend lancé en local ?) : arrêtez-le, ou lancez make test-e2e."
  exit 1
fi

echo "== Base et Mailpit"
"${COMPOSE[@]}" up -d
# Contrôle par le réseau (-h localhost), comme le backend : au premier démarrage, PostgreSQL passe
# par un serveur d'initialisation qui répond « prêt » sans accepter encore de connexion réseau.
base_prete=""
for _ in $(seq 1 60); do
  if "${COMPOSE[@]}" exec -T postgres pg_isready -h localhost -U app -d gestion_financiere >/dev/null 2>&1; then
    base_prete=1; break
  fi
  sleep 1
done
if [ -z "$base_prete" ]; then
  echo "La base de test ne répond pas après 60 s :"; "${COMPOSE[@]}" logs postgres | tail -20; exit 1
fi

# Puis depuis le poste, comme le backend (le relais de port de Docker peut arriver un peu après).
for _ in $(seq 1 30); do
  (exec 3<>/dev/tcp/127.0.0.1/5432) 2>/dev/null && break
  sleep 1
done
sleep 2

echo "== Backend"
(cd "$BACKEND" && ./mvnw -B -q -DskipTests package)
JAR="$(ls "$BACKEND"/target/*.jar | grep -v -- '-plain' | head -1)"
# Mêmes réglages que ./mvnw spring-boot:run (pom.xml) : emails dans le Mailpit ci-dessus.
# Mémoire limitée : tient sur un poste de 8 Go avec le navigateur et le serveur de l'écran.
SMTP_HOST=localhost java -Xmx512m -XX:TieredStopAtLevel=1 -jar "$JAR" >"$JOURNAL" 2>&1 &
PID_BACKEND=$!
# Prêt quand son journal l'annonce (ligne « Started … ») ; arrêté avant : on montre pourquoi.
for _ in $(seq 1 120); do
  grep -q '"msg":"Started ' "$JOURNAL" && break
  if grep -q 'Application run failed' "$JOURNAL" || ! kill -0 "$PID_BACKEND" 2>/dev/null; then
    echo "Le backend s'est arrêté au démarrage :"; tail -50 "$JOURNAL"; exit 1
  fi
  sleep 2
done
if ! grep -q '"msg":"Started ' "$JOURNAL"; then
  echo "Le backend n'a pas démarré en 4 minutes :"; tail -50 "$JOURNAL"; exit 1
fi

echo "== Navigateur"
if [ -n "${CI:-}" ]; then
  npx playwright install --with-deps chromium
else
  npx playwright install chromium
fi

echo "== Tests"
npx playwright test
