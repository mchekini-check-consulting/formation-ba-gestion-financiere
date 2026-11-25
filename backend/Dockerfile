# syntax=docker/dockerfile:1
# Backend Spring Boot (Java 21, projet Maven gestion-financiere) -> image distroless, non-root.

# ---- Build : Maven + JDK, jamais livré en production
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src

# Le pom d'abord : la couche des dépendances reste en cache tant qu'il ne change pas
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests \
 && cp target/gestion-financiere-*.jar /app.jar

# ---- Runtime : JRE + Tesseract (lecture des photos et PDF scannés, US-53/US-54), utilisateur 65532
FROM eclipse-temurin:21-jre-noble
RUN apt-get update \
 && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-fra tesseract-ocr-eng \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app.jar /app/app.jar
# La JVM prend 75 % de la limite mémoire du conteneur ; Tesseract sur un seul cœur par lecture
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0" OMP_THREAD_LIMIT=1
USER 65532:65532
EXPOSE 8080
ENTRYPOINT ["java", "-jar"]
CMD ["/app/app.jar"]
# Image construite par la CI (.github/workflows/ci.yaml), taguée avec le sha court du commit
