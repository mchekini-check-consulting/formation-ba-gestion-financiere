# cabinets

Backend (Java 21 / Spring Boot) de la gestion des comptes cabinets.
Exposé par le gateway du projet sous `/cabinets` :
`https://gateway.formation-ba-gestion-financiere.<env>.check-consulting.net/cabinets/...`

## En local

- Base PostgreSQL : `docker compose up -d` (port **5433**, base `cabinets`)
- Application : `SERVER_PORT=8081 ./mvnw spring-boot:run` → http://localhost:8081/

## Contrôles (identiques à la CI)

- `make lint`
- `make test-unit` (couverture : `target/site/jacoco/jacoco.xml`)
- `make test-integration` (Docker requis)
